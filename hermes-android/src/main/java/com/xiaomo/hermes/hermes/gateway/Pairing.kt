package com.xiaomo.hermes.hermes.gateway

/**
 * DM Pairing System.
 *
 * Code-based approval flow for authorizing new users on messaging platforms.
 * Instead of static allowlists with user IDs, unknown users receive a one-time
 * pairing code that the bot owner approves via the CLI.
 *
 * Ported from gateway/pairing.py
 */

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// Unambiguous alphabet — excludes 0/O, 1/I to prevent confusion
private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private const val CODE_LENGTH = 8

// Timing constants
private const val CODE_TTL_SECONDS = 3600L              // Codes expire after 1 hour
private const val RATE_LIMIT_SECONDS = 600L             // 1 request per user per 10 minutes
private const val LOCKOUT_SECONDS = 3600L               // Lockout duration after too many failures

// Limits
private const val MAX_PENDING_PER_PLATFORM = 3          // Max pending codes per platform
private const val MAX_FAILED_ATTEMPTS = 5               // Failed approvals before lockout

/**
 * Manages pairing codes and approved user lists.
 */
class PairingStore {
    companion object {
        private const val _TAG = "PairingStore"
    }

    private val _lock = ReentrantLock()
    private val _pairingDir: File by lazy {
        File(System.getProperty("java.io.tmpdir") ?: "/tmp", "hermes/pairing").apply { mkdirs() }
    }

    fun _pendingPath(platform: String): File = File(_pairingDir, "$platform-pending.json")

    fun _approvedPath(platform: String): File = File(_pairingDir, "$platform-approved.json")

    fun _rateLimitPath(): File = File(_pairingDir, "_rate_limits.json")

    fun _loadJson(path: File): JSONObject {
        if (!path.exists()) return JSONObject()
        return try {
            JSONObject(path.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            JSONObject()
        }
    }

    fun _saveJson(path: File, data: JSONObject) {
        try {
            path.parentFile?.mkdirs()
            path.writeText(data.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(_TAG, "Failed to save JSON to ${path.path}: ${e.message}")
        }
    }

    fun isApproved(platform: String, userId: String): Boolean {
        val approved = _loadJson(_approvedPath(platform))
        return approved.has(userId)
    }

    fun listApproved(platform: String? = null): List<Map<String, Any?>> {
        val results = mutableListOf<Map<String, Any?>>()
        val platforms = if (platform != null) listOf(platform) else _allPlatforms("approved")
        for (p in platforms) {
            val approved = _loadJson(_approvedPath(p))
            val keys = approved.keys()
            while (keys.hasNext()) {
                val uid = keys.next()
                val info = approved.optJSONObject(uid) ?: JSONObject()
                val entry = mutableMapOf<String, Any?>("platform" to p, "user_id" to uid)
                val infoKeys = info.keys()
                while (infoKeys.hasNext()) {
                    val k = infoKeys.next()
                    entry[k] = info.opt(k)
                }
                results.add(entry)
            }
        }
        return results
    }

    fun _approveUser(platform: String, userId: String, userName: String = "") {
        val approved = _loadJson(_approvedPath(platform))
        val entry = JSONObject().apply {
            put("user_name", userName)
            put("approved_at", System.currentTimeMillis() / 1000.0)
        }
        approved.put(userId, entry)
        _saveJson(_approvedPath(platform), approved)
    }

    fun revoke(platform: String, userId: String): Boolean {
        val path = _approvedPath(platform)
        return _lock.withLock {
            val approved = _loadJson(path)
            if (approved.has(userId)) {
                approved.remove(userId)
                _saveJson(path, approved)
                true
            } else {
                false
            }
        }
    }

    fun generateCode(platform: String, userId: String, userName: String = ""): String? {
        return _lock.withLock {
            _cleanupExpired(platform)

            if (_isLockedOut(platform)) return@withLock null
            if (_isRateLimited(platform, userId)) return@withLock null

            val pending = _loadJson(_pendingPath(platform))
            if (pending.length() >= MAX_PENDING_PER_PLATFORM) return@withLock null

            val code = buildString {
                repeat(CODE_LENGTH) {
                    append(ALPHABET[(Math.random() * ALPHABET.length).toInt()])
                }
            }

            val entry = JSONObject().apply {
                put("user_id", userId)
                put("user_name", userName)
                put("created_at", System.currentTimeMillis() / 1000.0)
            }
            pending.put(code, entry)
            _saveJson(_pendingPath(platform), pending)

            _recordRateLimit(platform, userId)

            code
        }
    }

    fun approveCode(platform: String, code: String): Map<String, Any?>? {
        return _lock.withLock {
            _cleanupExpired(platform)
            val normalized = code.uppercase().trim()

            val pending = _loadJson(_pendingPath(platform))
            if (!pending.has(normalized)) {
                _recordFailedAttempt(platform)
                return@withLock null
            }

            val entry = pending.getJSONObject(normalized)
            pending.remove(normalized)
            _saveJson(_pendingPath(platform), pending)

            val userId = entry.optString("user_id", "")
            val userName = entry.optString("user_name", "")
            _approveUser(platform, userId, userName)

            mapOf("user_id" to userId, "user_name" to userName)
        }
    }

    fun listPending(platform: String? = null): List<Map<String, Any?>> {
        val results = mutableListOf<Map<String, Any?>>()
        val platforms = if (platform != null) listOf(platform) else _allPlatforms("pending")
        for (p in platforms) {
            _cleanupExpired(p)
            val pending = _loadJson(_pendingPath(p))
            val codes = pending.keys()
            while (codes.hasNext()) {
                val code = codes.next()
                val info = pending.optJSONObject(code) ?: continue
                val createdAt = info.optDouble("created_at", 0.0)
                val ageMin = ((System.currentTimeMillis() / 1000.0 - createdAt) / 60).toInt()
                results.add(mapOf(
                    "platform" to p,
                    "code" to code,
                    "user_id" to info.optString("user_id", ""),
                    "user_name" to info.optString("user_name", ""),
                    "age_minutes" to ageMin,
                ))
            }
        }
        return results
    }

    fun clearPending(platform: String? = null): Int {
        return _lock.withLock {
            var count = 0
            val platforms = if (platform != null) listOf(platform) else _allPlatforms("pending")
            for (p in platforms) {
                val pending = _loadJson(_pendingPath(p))
                count += pending.length()
                _saveJson(_pendingPath(p), JSONObject())
            }
            count
        }
    }

    fun _isRateLimited(platform: String, userId: String): Boolean {
        val limits = _loadJson(_rateLimitPath())
        val key = "$platform:$userId"
        val lastRequest = limits.optDouble(key, 0.0)
        return (System.currentTimeMillis() / 1000.0 - lastRequest) < RATE_LIMIT_SECONDS
    }

    fun _recordRateLimit(platform: String, userId: String) {
        val limits = _loadJson(_rateLimitPath())
        val key = "$platform:$userId"
        limits.put(key, System.currentTimeMillis() / 1000.0)
        _saveJson(_rateLimitPath(), limits)
    }

    fun _isLockedOut(platform: String): Boolean {
        val limits = _loadJson(_rateLimitPath())
        val lockoutKey = "_lockout:$platform"
        val lockoutUntil = limits.optDouble(lockoutKey, 0.0)
        return System.currentTimeMillis() / 1000.0 < lockoutUntil
    }

    fun _recordFailedAttempt(platform: String) {
        val limits = _loadJson(_rateLimitPath())
        val failKey = "_failures:$platform"
        val fails = limits.optInt(failKey, 0) + 1
        limits.put(failKey, fails)
        if (fails >= MAX_FAILED_ATTEMPTS) {
            val lockoutKey = "_lockout:$platform"
            limits.put(lockoutKey, System.currentTimeMillis() / 1000.0 + LOCKOUT_SECONDS)
            limits.put(failKey, 0)
            Log.w(_TAG, "Platform $platform locked out for ${LOCKOUT_SECONDS}s after $MAX_FAILED_ATTEMPTS failed attempts")
        }
        _saveJson(_rateLimitPath(), limits)
    }

    fun _cleanupExpired(platform: String) {
        val path = _pendingPath(platform)
        val pending = _loadJson(path)
        val nowSec = System.currentTimeMillis() / 1000.0
        val expired = mutableListOf<String>()
        val codes = pending.keys()
        while (codes.hasNext()) {
            val code = codes.next()
            val info = pending.optJSONObject(code) ?: continue
            val createdAt = info.optDouble("created_at", 0.0)
            if ((nowSec - createdAt) > CODE_TTL_SECONDS) expired.add(code)
        }
        if (expired.isNotEmpty()) {
            for (code in expired) pending.remove(code)
            _saveJson(path, pending)
        }
    }

    fun _allPlatforms(suffix: String): List<String> {
        val platforms = mutableListOf<String>()
        val files = _pairingDir.listFiles() ?: return platforms
        for (f in files) {
            if (f.name.endsWith("-$suffix.json")) {
                val platform = f.name.removeSuffix("-$suffix.json")
                if (!platform.startsWith("_")) platforms.add(platform)
            }
        }
        return platforms
    }
}

/** Directory that stores platform pairing JSON files (Python `PAIRING_DIR`). */
val PAIRING_DIR: java.io.File by lazy {
    val env = (System.getenv("HERMES_HOME") ?: "").trim()
    val home = if (env.isNotEmpty()) java.io.File(env)
    else java.io.File(System.getProperty("user.home") ?: "/", ".hermes")
    java.io.File(home, "pairings")
}

/** Atomic write for pairing JSON (Python `_secure_write`). Stub: simple overwrite. */
@Suppress("UNUSED_PARAMETER")
private fun _secureWrite(file: java.io.File, contents: String): Boolean = try {
    file.parentFile?.mkdirs()
    file.writeText(contents)
    true
} catch (_: Exception) { false }

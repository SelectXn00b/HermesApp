package com.xiaomo.hermes.hermes.gateway

/**
 * Device pairing helpers.
 *
 * In the desktop / CLI version of Hermes the user pairs a new messaging
 * platform by scanning a QR code or entering a short pairing code.  On
 * Android we reuse the same protocol but drive it through the app UI.
 *
 * Ported from gateway/pairing.py
 */

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pairing state for a single platform.
 */
enum class PairingState {
    /** Not yet started. */
    IDLE,
    /** Waiting for the user to confirm on the remote device. */
    PENDING,
    /** Successfully paired. */
    PAIRED,
    /** Pairing failed or was cancelled. */
    FAILED,
    /** Pairing expired (timeout). */
    EXPIRED,
}

/**
 * A single pairing session.
 */
data class PairingSession(
    /** Unique session id. */
    val sessionId: String,
    /** Platform being paired (e.g. "whatsapp", "signal"). */
    val platform: String,
    /** Current state. */
    var state: PairingState = PairingState.IDLE,
    /** QR code data (if applicable). */
    var qrData: String? = null,
    /** Pairing code (if applicable). */
    var pairingCode: String? = null,
    /** Error message (if state == FAILED). */
    var error: String? = null,
    /** Timestamp when the session was created (epoch millis). */
    val createdAt: Long = System.currentTimeMillis(),
    /** Timestamp when the session expires (epoch millis). */
    var expiresAt: Long = 0L,
    /** Arbitrary metadata. */
    val metadata: ConcurrentHashMap<String, String> = ConcurrentHashMap()) {
    /** True when the session has expired. */
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt

    /** Convert to JSON. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("session_id", sessionId)
        put("platform", platform)
        put("state", state.name)
        qrData?.let { put("qr_data", it) }
        pairingCode?.let { put("pairing_code", it) }
        error?.let { put("error", it) }
        put("created_at", createdAt)
        put("expires_at", expiresAt)
        put("metadata", JSONObject(metadata as Map<*, *>))
    }
}

/**
 * Pairing manager — orchestrates device-pairing flows.
 *
 * Thread-safe.  Each platform adapter that supports pairing registers
 * its own handler; the manager dispatches to the right handler based
 * on the platform name.
 */
class PairingManager(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()) {
    companion object {
        private const val TAG = "PairingManager"
        /** Default pairing session timeout (5 minutes). */
        const val DEFAULT_TIMEOUT_MS: Long = 5 * 60 * 1000L
    }

    /** Active pairing sessions keyed by session id. */
    private val _sessions: ConcurrentHashMap<String, PairingSession> = ConcurrentHashMap()
    private val _pairingDir: String = ""

    /** Platform name → handler function. */
    private val _handlers: ConcurrentHashMap<String, suspend (PairingSession) -> Unit> = ConcurrentHashMap()

    /** Counter for generating unique session ids. */
    private val _counter = AtomicInteger(0)

    /** Register a pairing handler for a platform. */
    fun registerHandler(platform: String, handler: suspend (PairingSession) -> Unit) {
        _handlers[platform] = handler
    }

    /** Start a new pairing session. */
    suspend fun startPairing(
        platform: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS): PairingSession {
        val sessionId = "${platform}_${_counter.incrementAndGet()}"
        val session = PairingSession(
            sessionId = sessionId,
            platform = platform,
            state = PairingState.PENDING,
            expiresAt = System.currentTimeMillis() + timeoutMs)
        _sessions[sessionId] = session

        val handler = _handlers[platform]
        if (handler != null) {
            try {
                handler(session)
            } catch (e: Exception) {
                Log.w(TAG, "Pairing handler failed for $platform: ${e.message}")
                session.state = PairingState.FAILED
                session.error = e.message
            }
        } else {
            session.state = PairingState.FAILED
            session.error = "No pairing handler registered for platform: $platform"
        }

        return session
    }

    /** Get a pairing session by id. */
    fun getSession(sessionId: String): PairingSession? = _sessions[sessionId]

    /** Confirm a pairing session (called when the user approves on the remote device). */
    fun confirm(sessionId: String) {
        _sessions[sessionId]?.let {
            it.state = PairingState.PAIRED
            Log.i(TAG, "Pairing confirmed for session $sessionId")
        }
    }

    /** Fail a pairing session. */
    fun fail(sessionId: String, error: String) {
        _sessions[sessionId]?.let {
            it.state = PairingState.FAILED
            it.error = error
            Log.w(TAG, "Pairing failed for session $sessionId: $error")
        }
    }

    /** Cancel a pairing session. */
    fun cancel(sessionId: String) {
        _sessions[sessionId]?.let {
            it.state = PairingState.FAILED
            it.error = "Cancelled by user"
        }
    }

    /** Clean up expired sessions. */
    fun cleanupExpired() {
        val now = System.currentTimeMillis()
        _sessions.entries.removeIf { (_, session) ->
            session.isExpired && session.state == PairingState.PENDING
        }
    }

    /** Get all active sessions. */
    val activeSessions: Collection<PairingSession>
        get() = _sessions.values.filter { it.state == PairingState.PENDING }

    /** Clear all sessions. */
    fun clear() {
        _sessions.clear()
    }

    // ── Approval management (ported from gateway/pairing.py) ────────

    private val approvedUsers = ConcurrentHashMap<String, MutableSet<String>>() // platform -> set of userIds
    private val rateLimits = ConcurrentHashMap<String, Long>() // "$platform:$userId" -> lastAttemptMs
    private val failedAttempts = ConcurrentHashMap<String, Int>() // platform -> count
    private val pendingCodes = ConcurrentHashMap<String, Map<String, Any?>>() // code -> session info
    private var lockoutUntil = 0L
    private var ownerId: String = ""

    /** Check if a user is approved for a platform. */
    fun isApproved(platform: String, userId: String): Boolean {
        return approvedUsers[platform]?.contains(userId) == true
    }

    /** List approved users. */
    fun listApproved(platform: String? = null): List<Map<String, String>> {
        val result = mutableListOf<Map<String, String>>()
        val platforms = if (platform != null) listOf(platform) else approvedUsers.keys.toList()
        for (p in platforms) {
            approvedUsers[p]?.forEach { userId ->
                result.add(mapOf("platform" to p, "user_id" to userId))
            }
        }
        return result
    }

    /** Approve a user for a platform. */
    fun approveUser(platform: String, userId: String, userName: String = "") {
        approvedUsers.getOrPut(platform) { ConcurrentHashMap.newKeySet() }.add(userId)
        Log.i(TAG, "User $userId approved for $platform")
    }

    /** Revoke a user's approval. */
    fun revoke(platform: String, userId: String): Boolean {
        val removed = approvedUsers[platform]?.remove(userId) == true
        if (removed) Log.i(TAG, "User $userId revoked from $platform")
        return removed
    }

    /** Generate a pairing code. */
    fun generateCode(platform: String, userId: String, userName: String = ""): String? {
        // Check lockout
        if (isLockedOut(platform)) return null
        // Check rate limit for this specific user
        if (isRateLimited(platform, userId)) return null

        val code = (100000..999999).random().toString()
        val info = mapOf<String, Any?>(
            "code" to code, "platform" to platform,
            "user_id" to userId, "user_name" to userName,
            "created_at" to System.currentTimeMillis())
        pendingCodes[code] = info
        return code
    }

    /** Approve a pairing code. */
    fun approveCode(platform: String, code: String): Map<String, Any?>? {
        val info = pendingCodes.remove(code) ?: return null
        if (info["platform"] != platform) return null
        approveUser(platform, info["user_id"] as? String ?: "", info["user_name"] as? String ?: "")
        return info
    }

    /** List pending sessions. */
    fun listPending(platform: String? = null): List<PairingSession> {
        return _sessions.values.filter { s ->
            s.state == PairingState.PENDING && (platform == null || s.platform == platform)
        }
    }

    /** Clear pending sessions. */
    fun clearPending(platform: String? = null): Int {
        val toRemove = _sessions.values.filter { s ->
            s.state == PairingState.PENDING && (platform == null || s.platform == platform)
        }
        toRemove.forEach { _sessions.remove(it.sessionId) }
        return toRemove.size
    }

    /** Check rate limiting. */
    fun isRateLimited(platform: String, userId: String): Boolean {
        val key = "$platform:$userId"
        val last = rateLimits[key] ?: return false
        return System.currentTimeMillis() - last < 60_000 // 1 minute
    }

    /** Record rate limit attempt. */
    fun recordRateLimit(platform: String, userId: String) {
        rateLimits["$platform:$userId"] = System.currentTimeMillis()
    }

    /** Check if platform is locked out. */
    fun isLockedOut(): Boolean = System.currentTimeMillis() < lockoutUntil

    /** Check if platform is locked out (by platform). */
    fun isLockedOut(platform: String): Boolean = isLockedOut()

    /** Record a failed attempt. */
    fun recordFailedAttempt(platform: String) {
        failedAttempts[platform] = (failedAttempts[platform] ?: 0) + 1
        if ((failedAttempts[platform] ?: 0) >= 5) {
            lockoutUntil = System.currentTimeMillis() + 300_000 // 5 min lockout
            Log.w(TAG, "Platform $platform locked out after 5 failed attempts")
        }
    }


    /** Check if a user is approved on a platform. */
    fun isApprovedUser(platform: String, userId: String): Boolean {
        return approvedUsers[platform]?.contains(userId) == true
    }

    /** Get approved user IDs for a platform. */
    fun getApprovedUsers(platform: String): Set<String> {
        return approvedUsers[platform]?.toSet() ?: emptySet()
    }

    /** Get all approved users as platform -> userIds map. */
    fun getAllApprovedUsers(): Map<String, Set<String>> {
        return approvedUsers.mapValues { it.value.toSet() }
    }

    /** Check if a user is the owner. */
    fun isOwner(platform: String, userId: String): Boolean {
        return userId == ownerId
    }

    /** Remove an approved user. */
    fun removeApprovedUser(platform: String, userId: String): Boolean {
        return approvedUsers[platform]?.remove(userId) == true
    }

    /** Get the pairing timeout in seconds. */
    fun getTimeout(): Int = (DEFAULT_TIMEOUT_MS / 1000).toInt()

    /** Reset the lockout timer. */
    fun resetLockout() { lockoutUntil = 0 }

    /** Get pending code info by code. */
    fun getPendingCode(code: String): Map<String, Any?>? = pendingCodes[code]

    /** Remove a pending code. */
    fun removePendingCode(code: String) { pendingCodes.remove(code) }

    /** Clean up expired pending codes. */
    fun cleanExpiredCodes(maxAgeMs: Long = 300000) {
        val now = System.currentTimeMillis()
        pendingCodes.entries.removeAll { now - ((it.value["created_at"] as? Number)?.toLong() ?: 0) > maxAgeMs }
    }

    /** Get the pairing count for a platform. */
    fun getPairingCount(platform: String): Int {
        return approvedUsers[platform]?.size ?: 0
    }


    fun _pendingPath(platform: String): String {
        return "$_pairingDir/${platform}-pending.json"
    }
    fun _approvedPath(platform: String): String {
        return "$_pairingDir/${platform}-approved.json"
    }
    fun _rateLimitPath(): String {
        return "$_pairingDir/_rate_limits.json"
    }
    fun _loadJson(path: String): Any? {
        return try {
            val file = java.io.File(path)
            if (!file.exists()) return null
            val text = file.readText(Charsets.UTF_8)
            org.json.JSONObject(text)
        } catch (_: Exception) {
            null
        }
    }
    fun _saveJson(path: String, data: Any?): Unit {
        try {
            val file = java.io.File(path)
            file.parentFile?.mkdirs()
            val text = when (data) {
                is org.json.JSONObject -> data.toString(2)
                is org.json.JSONArray -> data.toString(2)
                else -> data?.toString() ?: "null"
            }
            file.writeText(text, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save JSON to $path: ${e.message}")
        }
    }
    /** Add a user to the approved list. Must be called under self._lock. */
    fun _approveUser(platform: String, userId: String, userName: String = ""): Unit {
        approveUser(platform, userId, userName)
    }
    /** Check if a user has requested a code too recently. */
    fun _isRateLimited(platform: String, userId: String): Boolean {
        return isRateLimited(platform, userId)
    }
    /** Record the time of a pairing request for rate limiting. */
    fun _recordRateLimit(platform: String, userId: String): Unit {
        recordRateLimit(platform, userId)
    }
    /** Check if a platform is in lockout due to failed approval attempts. */
    fun _isLockedOut(platform: String): Boolean {
        return isLockedOut(platform)
    }
    /** Record a failed approval attempt. Triggers lockout after MAX_FAILED_ATTEMPTS. */
    fun _recordFailedAttempt(platform: String): Unit {
        recordFailedAttempt(platform)
    }
    /** Remove expired pending codes. */
    fun _cleanupExpired(platform: String): Unit {
        cleanExpiredCodes()
    }
    /** List all platforms that have data files of a given suffix. */
    fun _allPlatforms(suffix: String): List<String> {
        val dir = java.io.File(_pairingDir)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.name.endsWith("-$suffix.json") }
            ?.map { it.name.removeSuffix("-$suffix.json") }
            ?.filter { !it.startsWith("_") }
            ?: emptyList()
    }

}

/** Alias for backward compatibility — PairingStore is now PairingManager. */
typealias PairingStore = PairingManager

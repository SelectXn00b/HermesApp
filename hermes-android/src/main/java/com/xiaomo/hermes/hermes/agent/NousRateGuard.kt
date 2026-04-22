/** 1:1 对齐 hermes/agent/nous_rate_guard.py */
package com.xiaomo.hermes.hermes.agent

import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Cross-session rate limit guard for Nous Portal.
 *
 * Writes rate limit state to a shared file so all sessions (CLI, gateway,
 * cron, auxiliary) can check whether Nous Portal is currently rate-limited
 * before making requests. Prevents retry amplification when RPH is tapped.
 *
 * Each 429 from Nous triggers up to 9 API calls per conversation turn
 * (3 SDK retries x 3 Hermes retries), and every one of those calls counts
 * against RPH. By recording the rate limit state on first 429 and checking
 * it before subsequent attempts, we eliminate the amplification effect.
 */
object NousRateGuard {

    private const val TAG = "NousRateGuard"

    private const val STATE_SUBDIR = "rate_limits"
    private const val STATE_FILENAME = "nous.json"

    private fun _statePath(): String {
        // Use app-specific directory on Android; fallback to ~/.hermes
        val base = try {
            val clazz = Class.forName("com.xiaomo.hermes.hermes.HermesConstants")
            val method = clazz.getMethod("getHermesHome")
            method.invoke(null) as String
        } catch (_: Exception) {
            File(System.getProperty("user.home", "/data"), ".hermes").absolutePath
        }
        return File(File(base, STATE_SUBDIR), STATE_FILENAME).absolutePath
    }

    /**
     * Extract the best available reset-time estimate from response headers.
     *
     * Priority:
     *   1. x-ratelimit-reset-requests-1h (hourly RPH window — most useful)
     *   2. x-ratelimit-reset-requests    (per-minute RPM window)
     *   3. retry-after                   (generic HTTP header)
     *
     * @return seconds-from-now, or null if no usable header found.
     */
    fun _parseResetSeconds(headers: Map<String, String>?): Double? {
        if (headers == null) return null

        val lowered = headers.mapKeys { it.key.lowercase() }

        for (key in listOf(
            "x-ratelimit-reset-requests-1h",
            "x-ratelimit-reset-requests",
            "retry-after",
        )) {
            val raw = lowered[key]
            if (raw != null) {
                try {
                    val value = raw.toDouble()
                    if (value > 0) return value
                } catch (_: NumberFormatException) {
                    // continue
                }
            }
        }
        return null
    }

    /**
     * Record that Nous Portal is rate-limited.
     *
     * Parses the reset time from response headers or error context.
     * Falls back to [defaultCooldown] (5 minutes) if no reset info
     * is available. Writes to a shared file that all sessions can read.
     *
     * @param headers HTTP response headers from the 429 error.
     * @param errorContext Structured error context from _extractApiErrorContext().
     * @param defaultCooldown Fallback cooldown in seconds when no header data.
     */
    fun recordNousRateLimit(
        headers: Map<String, String>? = null,
        errorContext: Map<String, Any?>? = null,
        defaultCooldown: Double = 300.0,
    ) {
        val now = System.currentTimeMillis() / 1000.0
        var resetAt: Double? = null

        // Try headers first (most accurate)
        val headerSeconds = _parseResetSeconds(headers)
        if (headerSeconds != null) {
            resetAt = now + headerSeconds
        }

        // Try errorContext reset_at (from body parsing)
        if (resetAt == null && errorContext != null) {
            val ctxReset = errorContext["reset_at"]
            if (ctxReset is Number) {
                val ctxResetDouble = ctxReset.toDouble()
                if (ctxResetDouble > now) {
                    resetAt = ctxResetDouble
                }
            }
        }

        // Default cooldown
        if (resetAt == null) {
            resetAt = now + defaultCooldown
        }

        val path = _statePath()
        try {
            val stateDir = File(path).parentFile
            stateDir?.mkdirs()

            val state = JSONObject().apply {
                put("reset_at", resetAt)
                put("recorded_at", now)
                put("reset_seconds", resetAt!! - now)
            }

            // Atomic write: write to temp file + rename
            val tmpFile = File.createTempFile("nous_", ".tmp", stateDir)
            try {
                tmpFile.writeText(state.toString())
                tmpFile.renameTo(File(path))
            } catch (e: Exception) {
                try {
                    tmpFile.delete()
                } catch (_: Exception) {
                    // ignore cleanup error
                }
                throw e
            }

            Log.i(TAG, "Nous rate limit recorded: resets in %.0fs (at %.0f)".format(
                resetAt!! - now, resetAt
            ))
        } catch (e: Exception) {
            Log.d(TAG, "Failed to write Nous rate limit state: $e")
        }
    }

    /**
     * Check if Nous Portal is currently rate-limited.
     *
     * @return Seconds remaining until reset, or null if not rate-limited.
     */
    fun nousRateLimitRemaining(): Double? {
        val path = _statePath()
        try {
            val file = File(path)
            if (!file.exists()) return null

            val state = JSONObject(file.readText())
            val resetAt = state.optDouble("reset_at", 0.0)
            val remaining = resetAt - (System.currentTimeMillis() / 1000.0)
            if (remaining > 0) {
                return remaining
            }
            // Expired — clean up
            try {
                file.delete()
            } catch (_: Exception) {
                // ignore
            }
            return null
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Clear the rate limit state (e.g., after a successful Nous request).
     */
    fun clearNousRateLimit() {
        try {
            File(_statePath()).delete()
        } catch (_: java.io.FileNotFoundException) {
            // already gone
        } catch (e: Exception) {
            Log.d(TAG, "Failed to clear Nous rate limit state: $e")
        }
    }

    /**
     * Format seconds remaining into human-readable duration.
     */
    fun formatRemaining(seconds: Double): String {
        val s = maxOf(0, seconds.toInt())
        if (s < 60) return "${s}s"
        if (s < 3600) {
            val m = s / 60
            val sec = s % 60
            return if (sec > 0) "${m}m ${sec}s" else "${m}m"
        }
        val h = s / 3600
        val m = (s % 3600) / 60
        return if (m > 0) "${h}h ${m}m" else "${h}h"
    }
}

package com.xiaomo.androidforclaw.cron

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/cron/session-reaper.ts
 *   (sweepCronRunSessions, resolveRetentionMs, DEFAULT_RETENTION_MS, MIN_SWEEP_INTERVAL_MS)
 *
 * AndroidForClaw adaptation: prune expired cron run sessions from session store.
 * Prevents unbounded growth of cron session files.
 */

import com.xiaomo.androidforclaw.logging.Log
import java.io.File

/**
 * CronSessionReaper — Prune expired cron run sessions.
 * Aligned with OpenClaw cron/session-reaper.ts.
 */
object CronSessionReaper {

    private const val TAG = "CronSessionReaper"

    /** Default retention: 24 hours */
    const val DEFAULT_RETENTION_MS = 24 * 3_600_000L

    /** Minimum sweep interval: 5 minutes */
    const val MIN_SWEEP_INTERVAL_MS = 5 * 60_000L

    /** Cron run session key pattern: ...cron:<jobId>:run:<uuid> */
    private val CRON_RUN_KEY_PATTERN = Regex("cron:[^:]+:run:[a-f0-9-]+", RegexOption.IGNORE_CASE)

    /** Last sweep timestamp for throttling */
    @Volatile
    private var lastSweepMs: Long = 0

    /**
     * Reaper result.
     * Aligned with OpenClaw ReaperResult.
     */
    data class ReaperResult(
        val swept: Boolean,
        val pruned: Int
    )

    /**
     * Resolve retention time from config.
     * Aligned with OpenClaw resolveRetentionMs.
     *
     * @return retention in ms, or null if disabled
     */
    fun resolveRetentionMs(sessionRetention: Any?): Long? {
        return when (sessionRetention) {
            false -> null  // disabled
            is Number -> sessionRetention.toLong()
            is String -> {
                // Parse duration strings like "24h", "7d"
                parseDuration(sessionRetention) ?: DEFAULT_RETENTION_MS
            }
            else -> DEFAULT_RETENTION_MS
        }
    }

    /**
     * Sweep expired cron run sessions.
     * Aligned with OpenClaw sweepCronRunSessions.
     *
     * Self-throttles to once per MIN_SWEEP_INTERVAL_MS.
     */
    fun sweep(
        sessionsDir: File,
        retentionMs: Long = DEFAULT_RETENTION_MS,
        force: Boolean = false
    ): ReaperResult {
        val now = System.currentTimeMillis()

        // Throttle
        if (!force && (now - lastSweepMs) < MIN_SWEEP_INTERVAL_MS) {
            return ReaperResult(swept = false, pruned = 0)
        }
        lastSweepMs = now

        if (!sessionsDir.exists() || !sessionsDir.isDirectory) {
            return ReaperResult(swept = true, pruned = 0)
        }

        var pruned = 0
        val cutoff = now - retentionMs

        // Scan session files for cron run patterns
        val sessionFiles = sessionsDir.listFiles { f ->
            f.isFile && f.name.endsWith(".jsonl") && f.lastModified() < cutoff
        } ?: emptyArray()

        for (file in sessionFiles) {
            // Check if file name matches cron run pattern
            val name = file.nameWithoutExtension
            if (CRON_RUN_KEY_PATTERN.containsMatchIn(name)) {
                try {
                    file.delete()
                    pruned++
                    // Also delete associated lock file
                    File(file.absolutePath + ".lock").delete()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete cron session file: ${file.name}: ${e.message}")
                }
            }
        }

        if (pruned > 0) {
            Log.i(TAG, "Reaped $pruned expired cron run sessions (retention=${retentionMs}ms)")
        }

        return ReaperResult(swept = true, pruned = pruned)
    }

    /**
     * Reset throttle (for testing).
     * Aligned with OpenClaw resetReaperThrottle.
     */
    fun resetThrottle() {
        lastSweepMs = 0
    }

    /**
     * Parse a simple duration string to milliseconds.
     * Supports: "24h", "7d", "30m", "60s"
     */
    private fun parseDuration(str: String): Long? {
        val match = Regex("^(\\d+)([hdms])$", RegexOption.IGNORE_CASE).find(str.trim()) ?: return null
        val value = match.groupValues[1].toLongOrNull() ?: return null
        return when (match.groupValues[2].lowercase()) {
            "h" -> value * 3_600_000
            "d" -> value * 86_400_000
            "m" -> value * 60_000
            "s" -> value * 1_000
            else -> null
        }
    }
}

package com.xiaomo.androidforclaw.agent.session

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/session-write-lock.ts
 *   (acquireSessionWriteLock, cleanStaleLockFiles, resolveSessionLockMaxHoldFromTimeout)
 *
 * AndroidForClaw adaptation: file-based advisory lock for session files.
 * Prevents concurrent writes from multiple coroutines/threads.
 */

import com.xiaomo.androidforclaw.logging.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * SessionWriteLock — File-based advisory lock for session files.
 * Aligned with OpenClaw session-write-lock.ts.
 *
 * On Android, we use in-memory locks (Mutex) per session file path since
 * all writes happen within the same process. The lock file on disk serves
 * as a crash-recovery indicator.
 */
object SessionWriteLock {

    private const val TAG = "SessionWriteLock"

    /** Default stale lock threshold */
    const val DEFAULT_STALE_MS = 30 * 60 * 1000L  // 30 minutes

    /** Default maximum lock hold time */
    const val DEFAULT_MAX_HOLD_MS = 5 * 60 * 1000L  // 5 minutes

    /** Default watchdog interval */
    const val DEFAULT_WATCHDOG_INTERVAL_MS = 60_000L  // 1 minute

    /** Default timeout grace period */
    const val DEFAULT_TIMEOUT_GRACE_MS = 2 * 60 * 1000L  // 2 minutes

    /** Lock file extension */
    const val LOCK_EXTENSION = ".lock"

    /** Process-scoped lock registry */
    private val locks = ConcurrentHashMap<String, HeldLock>()

    /** Global mutex for lock operations */
    private val globalMutex = Mutex()

    /**
     * Held lock state.
     * Aligned with OpenClaw HeldLock type.
     */
    data class HeldLock(
        val path: String,
        val lockPath: String,
        val acquiredAt: Long,
        val maxHoldMs: Long,
        var count: Int = 1  // reentrant count
    ) {
        val mutex = Mutex()
    }

    /**
     * Lock file payload written to disk.
     * Aligned with OpenClaw LockFilePayload.
     */
    data class LockFilePayload(
        val pid: Int? = null,
        val createdAt: String? = null,
        val threadId: Long? = null
    )

    /**
     * Lock inspection result.
     * Aligned with OpenClaw SessionLockInspection.
     */
    data class LockInspection(
        val lockPath: String,
        val createdAt: String?,
        val ageMs: Long,
        val stale: Boolean,
        val staleReasons: List<String>,
        val removed: Boolean
    )

    /**
     * Resolve max lock hold time from timeout parameters.
     * Aligned with OpenClaw resolveSessionLockMaxHoldFromTimeout.
     */
    fun resolveMaxHoldFromTimeout(
        timeoutMs: Long,
        graceMs: Long = DEFAULT_TIMEOUT_GRACE_MS,
        minMs: Long = DEFAULT_MAX_HOLD_MS
    ): Long {
        return maxOf(timeoutMs + graceMs, minMs)
    }

    /**
     * Acquire a write lock for a session file.
     * Aligned with OpenClaw acquireSessionWriteLock.
     *
     * Returns a release function that must be called when done.
     * Supports reentrant acquisition (same coroutine/thread).
     */
    suspend fun acquire(
        sessionFile: String,
        timeoutMs: Long = 30_000L,
        maxHoldMs: Long = DEFAULT_MAX_HOLD_MS,
        allowReentrant: Boolean = true
    ): suspend () -> Unit {
        val lockPath = "$sessionFile$LOCK_EXTENSION"
        val normalizedPath = File(sessionFile).canonicalPath

        // Check for reentrant lock
        if (allowReentrant) {
            val existing = locks[normalizedPath]
            if (existing != null) {
                existing.count++
                Log.d(TAG, "Reentrant lock acquired: $normalizedPath (count=${existing.count})")
                return { release(normalizedPath) }
            }
        }

        // Spin with exponential backoff
        val startTime = System.currentTimeMillis()
        var backoffMs = 50L

        while (true) {
            val acquired = tryAcquire(normalizedPath, lockPath, maxHoldMs)
            if (acquired) {
                return { release(normalizedPath) }
            }

            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed >= timeoutMs) {
                // Check if existing lock is stale
                val staleReclaimed = reclaimStaleLock(normalizedPath, lockPath, maxHoldMs)
                if (staleReclaimed) {
                    return { release(normalizedPath) }
                }
                throw SessionLockTimeoutException(
                    "Failed to acquire lock for $sessionFile after ${elapsed}ms"
                )
            }

            delay(backoffMs)
            backoffMs = minOf(backoffMs * 2, 1000L)
        }
    }

    /**
     * Try to acquire lock (non-blocking).
     */
    private suspend fun tryAcquire(
        normalizedPath: String,
        lockPath: String,
        maxHoldMs: Long
    ): Boolean = globalMutex.withLock {
        val existing = locks[normalizedPath]
        if (existing != null) {
            // Check if held lock has expired
            val heldMs = System.currentTimeMillis() - existing.acquiredAt
            if (heldMs > existing.maxHoldMs) {
                Log.w(TAG, "Reclaiming expired lock: $normalizedPath (held ${heldMs}ms > ${existing.maxHoldMs}ms)")
                forceRelease(normalizedPath)
            } else {
                return@withLock false
            }
        }

        val lock = HeldLock(
            path = normalizedPath,
            lockPath = lockPath,
            acquiredAt = System.currentTimeMillis(),
            maxHoldMs = maxHoldMs
        )
        locks[normalizedPath] = lock

        // Write lock file to disk
        try {
            File(lockPath).writeText(
                """{"pid":${android.os.Process.myPid()},"createdAt":"${java.time.Instant.now()}","threadId":${Thread.currentThread().id}}"""
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write lock file: $lockPath", e)
        }

        Log.d(TAG, "Lock acquired: $normalizedPath")
        true
    }

    /**
     * Release a held lock.
     */
    private suspend fun release(normalizedPath: String) = globalMutex.withLock {
        val lock = locks[normalizedPath] ?: return@withLock
        lock.count--
        if (lock.count <= 0) {
            forceRelease(normalizedPath)
        } else {
            Log.d(TAG, "Reentrant lock decremented: $normalizedPath (count=${lock.count})")
        }
    }

    /**
     * Force release a lock (remove from registry and delete lock file).
     */
    private fun forceRelease(normalizedPath: String) {
        val lock = locks.remove(normalizedPath) ?: return
        try {
            File(lock.lockPath).delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete lock file: ${lock.lockPath}", e)
        }
        Log.d(TAG, "Lock released: $normalizedPath")
    }

    /**
     * Try to reclaim a stale lock.
     */
    private suspend fun reclaimStaleLock(
        normalizedPath: String,
        lockPath: String,
        maxHoldMs: Long
    ): Boolean = globalMutex.withLock {
        val existing = locks[normalizedPath] ?: return@withLock false
        val heldMs = System.currentTimeMillis() - existing.acquiredAt
        if (heldMs > DEFAULT_STALE_MS) {
            Log.w(TAG, "Reclaiming stale lock: $normalizedPath (age ${heldMs}ms)")
            forceRelease(normalizedPath)
            return@withLock tryAcquire(normalizedPath, lockPath, maxHoldMs)
        }
        false
    }

    /**
     * Clean stale lock files from a sessions directory.
     * Aligned with OpenClaw cleanStaleLockFiles.
     */
    fun cleanStaleLockFiles(
        sessionsDir: File,
        staleMs: Long = DEFAULT_STALE_MS,
        removeStale: Boolean = true
    ): List<LockInspection> {
        val results = mutableListOf<LockInspection>()
        val lockFiles = sessionsDir.listFiles { f -> f.name.endsWith(LOCK_EXTENSION) } ?: return results

        val now = System.currentTimeMillis()

        for (lockFile in lockFiles) {
            val ageMs = now - lockFile.lastModified()
            val staleReasons = mutableListOf<String>()
            var stale = false

            if (ageMs > staleMs) {
                staleReasons.add("age ${ageMs}ms > stale threshold ${staleMs}ms")
                stale = true
            }

            var removed = false
            if (stale && removeStale) {
                try {
                    lockFile.delete()
                    removed = true
                    // Also clean in-memory lock
                    val sessionPath = lockFile.absolutePath.removeSuffix(LOCK_EXTENSION)
                    locks.remove(File(sessionPath).canonicalPath)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove stale lock: ${lockFile.path}", e)
                }
            }

            results.add(LockInspection(
                lockPath = lockFile.absolutePath,
                createdAt = null,
                ageMs = ageMs,
                stale = stale,
                staleReasons = staleReasons,
                removed = removed
            ))
        }

        if (results.any { it.removed }) {
            Log.i(TAG, "Cleaned ${results.count { it.removed }} stale lock files from ${sessionsDir.path}")
        }

        return results
    }

    /**
     * Get count of currently held locks (for diagnostics).
     */
    fun heldLockCount(): Int = locks.size
}

/**
 * Exception thrown when session lock acquisition times out.
 */
class SessionLockTimeoutException(message: String) : RuntimeException(message)

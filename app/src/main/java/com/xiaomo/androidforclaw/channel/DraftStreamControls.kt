package com.xiaomo.androidforclaw.channel

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/draft-stream-controls.ts
 *   (createFinalizableDraftStreamControls, FinalizableDraftStreamState)
 * - ../openclaw/src/channels/draft-stream-loop.ts
 *   (DraftStreamLoop, createDraftStreamLoop)
 *
 * AndroidForClaw adaptation: streaming draft/typing indicator management.
 */

import com.xiaomo.androidforclaw.logging.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * State for a finalizable draft stream.
 * Aligned with OpenClaw FinalizableDraftStreamState.
 */
data class DraftStreamState(
    var stopped: Boolean = false,
    var final: Boolean = false,
    var messageId: String? = null
)

/**
 * Draft stream controls for progressive message updates.
 * Aligned with OpenClaw createFinalizableDraftStreamControls + DraftStreamLoop.
 *
 * Key behaviors aligned:
 * - Flush loop with in-flight tracking
 * - send returning false puts text back to pending
 * - resetPending / resetThrottleWindow
 * - takeMessageIdAfterStop / clearFinalizableDraftMessage
 */
class DraftStreamControls(
    private val throttleMs: Long = 1000L,
    private val sendOrEditMessage: suspend (content: String, messageId: String?) -> String?
) {
    companion object {
        private const val TAG = "DraftStreamControls"
    }

    private val state = DraftStreamState()
    private val mutex = Mutex()
    private var pendingContent: String? = null
    private var lastSentAt: Long = 0L
    private var inFlightDeferred: CompletableDeferred<Unit>? = null
    private var throttleTimer: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Update the draft with new content.
     * Aligned with OpenClaw DraftStreamLoop.update.
     */
    suspend fun update(content: String) = mutex.withLock {
        if (state.stopped || state.final) return@withLock

        pendingContent = content

        // If nothing in-flight and throttle window elapsed, flush immediately
        if (inFlightDeferred == null) {
            val elapsed = System.currentTimeMillis() - lastSentAt
            if (elapsed >= throttleMs) {
                launchFlush()
            } else {
                scheduleFlush()
            }
        }
    }

    /**
     * Flush all pending content.
     * Aligned with OpenClaw DraftStreamLoop.flush.
     */
    suspend fun flush() {
        throttleTimer?.cancel()
        throttleTimer = null

        while (true) {
            // Wait for any in-flight operation
            inFlightDeferred?.let {
                try { it.await() } catch (_: Exception) {}
            }

            val content = mutex.withLock {
                if (state.stopped) return
                val c = pendingContent
                pendingContent = null
                c
            } ?: return

            doSend(content)
        }
    }

    /**
     * Stop the draft stream and send final content.
     * Aligned with OpenClaw stop (marks final, then flushes).
     */
    suspend fun stop(finalContent: String? = null) {
        mutex.withLock {
            state.final = true
            throttleTimer?.cancel()
            throttleTimer = null
        }

        // Flush remaining
        flush()

        // Send final content if provided
        if (finalContent != null) {
            doSend(finalContent)
        }

        mutex.withLock { state.stopped = true }
    }

    /**
     * Stop for clear — mark stopped, cancel pending, wait for in-flight.
     * Aligned with OpenClaw stopForClear.
     */
    suspend fun stopForClear() {
        mutex.withLock {
            state.stopped = true
            pendingContent = null
            throttleTimer?.cancel()
            throttleTimer = null
        }
        // Wait for in-flight
        waitForInFlight()
    }

    /**
     * Take message ID after stopping (for deletion).
     * Aligned with OpenClaw takeMessageIdAfterStop.
     */
    suspend fun takeMessageIdAfterStop(): String? {
        stopForClear()
        return mutex.withLock {
            val id = state.messageId
            state.messageId = null
            id
        }
    }

    /**
     * Clear the finalizable draft message (stop → read ID → delete).
     * Aligned with OpenClaw clearFinalizableDraftMessage.
     */
    suspend fun clearMessage(
        deleteMessage: suspend (messageId: String) -> Unit
    ) {
        val messageId = takeMessageIdAfterStop() ?: return
        try {
            deleteMessage(messageId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete draft message $messageId: ${e.message}")
        }
    }

    /** Reset pending content without stopping. */
    fun resetPending() {
        pendingContent = null
    }

    /** Reset throttle window to allow immediate flush. */
    fun resetThrottleWindow() {
        lastSentAt = 0L
        throttleTimer?.cancel()
        throttleTimer = null
    }

    /** Wait for current in-flight send to complete. */
    suspend fun waitForInFlight() {
        inFlightDeferred?.let {
            try { it.await() } catch (_: Exception) {}
        }
    }

    fun getMessageId(): String? = state.messageId
    fun isStopped(): Boolean = state.stopped

    fun dispose() {
        scope.cancel()
    }

    // ── Internal ──

    private fun launchFlush() {
        scope.launch { flushOnce() }
    }

    private fun scheduleFlush() {
        if (throttleTimer?.isActive == true) return
        val delay = maxOf(0L, throttleMs - (System.currentTimeMillis() - lastSentAt))
        throttleTimer = scope.launch {
            delay(delay)
            flushOnce()
        }
    }

    private suspend fun flushOnce() {
        val content = mutex.withLock {
            if (state.stopped) return
            val c = pendingContent
            pendingContent = null
            c
        } ?: return

        val success = doSend(content)

        // If send returned false (e.g., rate limited), put text back
        if (!success) {
            mutex.withLock {
                if (pendingContent == null) pendingContent = content
            }
        }
    }

    /**
     * Perform the actual send/edit, tracking in-flight state.
     * Returns true if send succeeded.
     */
    private suspend fun doSend(content: String): Boolean {
        val deferred = CompletableDeferred<Unit>()
        inFlightDeferred = deferred

        return try {
            val newId = sendOrEditMessage(content, state.messageId)
            if (newId != null) {
                state.messageId = newId
            }
            lastSentAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send/edit draft: ${e.message}")
            false
        } finally {
            deferred.complete(Unit)
            inFlightDeferred = null
        }
    }
}

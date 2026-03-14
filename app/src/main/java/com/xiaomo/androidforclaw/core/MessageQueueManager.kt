package com.xiaomo.androidforclaw.core

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/(all)
 *
 * AndroidForClaw adaptation: queue and dispatch inbound/outbound channel messages.
 */


import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Message Queue Manager
 *
 * Fully aligned with OpenClaw queue mechanisms:
 * - interrupt: New message immediately interrupts current run, clears queue
 * - steer: New message is passed to the running Agent
 * - followup: New message is added to queue, processed in order
 * - collect: Collect multiple messages, process in batch
 * - queue: Simple FIFO queue
 *
 * Reference:
 * - openclaw/src/auto-reply/reply/get-reply-run.ts
 * - openclaw/src/auto-reply/reply/queue/types.ts
 * - openclaw/src/utils/queue-helpers.ts
 */
class MessageQueueManager {

    companion object {
        private const val TAG = "MessageQueueManager"
    }

    /**
     * Queue mode (aligned with OpenClaw)
     */
    enum class QueueMode {
        INTERRUPT,   // Interrupt current run
        STEER,       // Steer current run
        FOLLOWUP,    // Followup queue
        COLLECT,     // Collect mode
        QUEUE        // Simple queue
    }

    /**
     * Drop policy (aligned with OpenClaw)
     */
    enum class DropPolicy {
        OLD,         // Drop oldest message
        NEW,         // Reject new message
        SUMMARIZE    // Drop but keep summary
    }

    /**
     * Message queue state
     */
    private data class QueueState(
        val key: String,
        val mode: QueueMode,
        val messages: MutableList<QueuedMessage> = mutableListOf(),
        val isProcessing: AtomicBoolean = AtomicBoolean(false),
        val currentJob: Job? = null,
        val droppedCount: Int = 0,
        val summaryLines: MutableList<String> = mutableListOf(),
        var cap: Int = 10,
        var dropPolicy: DropPolicy = DropPolicy.OLD
    )

    /**
     * Queued message
     */
    data class QueuedMessage(
        val messageId: String,
        val content: String,
        val senderId: String,
        val chatId: String,
        val chatType: String,
        val timestamp: Long = System.currentTimeMillis(),
        val metadata: Map<String, Any?> = emptyMap()
    )

    // State for each queue key
    private val queues = ConcurrentHashMap<String, QueueState>()

    // Base queue (for followup and queue modes)
    private val baseQueue = KeyedAsyncQueue()

    /**
     * Enqueue message
     *
     * @param key Queue key (usually chatId)
     * @param message Message
     * @param mode Queue mode
     * @param processor Message processor
     */
    suspend fun enqueue(
        key: String,
        message: QueuedMessage,
        mode: QueueMode = QueueMode.FOLLOWUP,
        processor: suspend (QueuedMessage) -> Unit
    ) {
        when (mode) {
            QueueMode.INTERRUPT -> handleInterrupt(key, message, processor)
            QueueMode.STEER -> handleSteer(key, message, processor)
            QueueMode.FOLLOWUP -> handleFollowup(key, message, processor)
            QueueMode.COLLECT -> handleCollect(key, message, processor)
            QueueMode.QUEUE -> handleQueue(key, message, processor)
        }
    }

    /**
     * INTERRUPT mode: Immediately interrupt current run, clear queue
     *
     * Aligned with OpenClaw logic:
     * ```typescript
     * if (resolvedQueue.mode === "interrupt" && laneSize > 0) {
     *   const cleared = clearCommandLane(sessionLaneKey);
     *   const aborted = abortEmbeddedPiRun(sessionIdFinal);
     * }
     * ```
     */
    private suspend fun handleInterrupt(
        key: String,
        message: QueuedMessage,
        processor: suspend (QueuedMessage) -> Unit
    ) {
        val state = queues.getOrPut(key) {
            QueueState(key = key, mode = QueueMode.INTERRUPT)
        }

        // 1. Cancel currently running task
        if (state.isProcessing.get()) {
            Log.d(TAG, "🛑 [INTERRUPT] Aborting current run for $key")
            state.currentJob?.cancel()
        }

        // 2. Clear queue
        val cleared = state.messages.size
        if (cleared > 0) {
            Log.d(TAG, "🗑️  [INTERRUPT] Clearing $cleared queued messages for $key")
            state.messages.clear()
        }

        // 3. Process new message immediately
        Log.d(TAG, "⚡ [INTERRUPT] Processing new message immediately for $key")
        state.isProcessing.set(true)
        try {
            processor(message)
        } finally {
            state.isProcessing.set(false)
        }
    }

    /**
     * STEER mode: Pass new message to running Agent
     *
     * Aligned with OpenClaw logic:
     * - If Agent is running, inject new message into Agent's message stream
     * - If Agent is not running, process normally
     */
    private suspend fun handleSteer(
        key: String,
        message: QueuedMessage,
        processor: suspend (QueuedMessage) -> Unit
    ) {
        val state = queues.getOrPut(key) {
            QueueState(key = key, mode = QueueMode.STEER)
        }

        if (state.isProcessing.get()) {
            // Agent is running, add message to steer queue
            Log.d(TAG, "🎯 [STEER] Injecting message into running Agent for $key")
            state.messages.add(message)
            // TODO: Notify AgentLoop of new message (requires AgentLoop support)
            notifyAgentLoop(key, message)
        } else {
            // Agent not running, process normally
            Log.d(TAG, "▶️  [STEER] Agent not running, processing normally for $key")
            baseQueue.enqueue(key) {
                state.isProcessing.set(true)
                try {
                    processor(message)
                } finally {
                    state.isProcessing.set(false)
                }
            }
        }
    }

    /**
     * FOLLOWUP mode: Add to queue, process in order
     *
     * This is the currently implemented basic behavior
     */
    private suspend fun handleFollowup(
        key: String,
        message: QueuedMessage,
        processor: suspend (QueuedMessage) -> Unit
    ) {
        val state = queues.getOrPut(key) {
            QueueState(key = key, mode = QueueMode.FOLLOWUP)
        }

        Log.d(TAG, "📝 [FOLLOWUP] Enqueueing message for $key (queue size: ${state.messages.size})")

        baseQueue.enqueue(key) {
            state.isProcessing.set(true)
            try {
                processor(message)
            } finally {
                state.isProcessing.set(false)
            }
        }
    }

    /**
     * COLLECT mode: Collect multiple messages, process in batch
     *
     * Aligned with OpenClaw logic:
     * - Messages are added to queue
     * - After current message processing completes, all queued messages are processed in batch
     */
    private suspend fun handleCollect(
        key: String,
        message: QueuedMessage,
        processor: suspend (QueuedMessage) -> Unit
    ) {
        val state = queues.getOrPut(key) {
            QueueState(key = key, mode = QueueMode.COLLECT)
        }

        // Apply drop policy
        if (!applyDropPolicy(state, message)) {
            Log.w(TAG, "🚫 [COLLECT] Message dropped due to drop policy for $key")
            return
        }

        state.messages.add(message)
        Log.d(TAG, "📦 [COLLECT] Collected message for $key (${state.messages.size} total)")

        // If not currently processing, trigger batch processing
        if (!state.isProcessing.get()) {
            baseQueue.enqueue(key) {
                state.isProcessing.set(true)
                try {
                    processBatch(state, processor)
                } finally {
                    state.isProcessing.set(false)
                }
            }
        }
    }

    /**
     * QUEUE mode: Simple FIFO queue
     */
    private suspend fun handleQueue(
        key: String,
        message: QueuedMessage,
        processor: suspend (QueuedMessage) -> Unit
    ) {
        // Same as FOLLOWUP, simple queuing
        handleFollowup(key, message, processor)
    }

    /**
     * Apply drop policy
     */
    private fun applyDropPolicy(state: QueueState, newMessage: QueuedMessage): Boolean {
        if (state.cap <= 0 || state.messages.size < state.cap) {
            return true
        }

        return when (state.dropPolicy) {
            DropPolicy.NEW -> {
                // Reject new message
                Log.w(TAG, "🚫 Drop policy: NEW - rejecting new message")
                false
            }
            DropPolicy.OLD -> {
                // Drop oldest message
                val dropped = state.messages.removeAt(0)
                Log.d(TAG, "🗑️  Drop policy: OLD - dropped message: ${dropped.messageId}")
                true
            }
            DropPolicy.SUMMARIZE -> {
                // Drop oldest message but keep summary
                val dropped = state.messages.removeAt(0)
                val summary = summarizeMessage(dropped)
                state.summaryLines.add(summary)
                Log.d(TAG, "📝 Drop policy: SUMMARIZE - dropped and summarized: ${dropped.messageId}")

                // Limit summary count
                if (state.summaryLines.size > state.cap) {
                    state.summaryLines.removeAt(0)
                }
                true
            }
        }
    }

    /**
     * Summarize message (for SUMMARIZE drop policy)
     */
    private fun summarizeMessage(message: QueuedMessage): String {
        val content = message.content.take(100)
        return "[${message.timestamp}] ${message.senderId}: $content${if (message.content.length > 100) "..." else ""}"
    }

    /**
     * Process batch of messages (COLLECT mode)
     */
    private suspend fun processBatch(
        state: QueueState,
        processor: suspend (QueuedMessage) -> Unit
    ) {
        if (state.messages.isEmpty()) return

        Log.d(TAG, "📦 [COLLECT] Processing batch of ${state.messages.size} messages")

        // Extract all messages
        val batch = state.messages.toList()
        state.messages.clear()

        // Build batch message prompt
        val batchMessage = buildBatchMessage(batch, state)

        // Process batch message
        processor(batchMessage)
    }

    /**
     * Build batch message (COLLECT mode)
     *
     * Aligned with OpenClaw's buildCollectPrompt
     */
    private fun buildBatchMessage(
        messages: List<QueuedMessage>,
        state: QueueState
    ): QueuedMessage {
        val content = buildString {
            appendLine("[Batch] Collected ${messages.size} message(s):")
            appendLine()

            // If there are dropped message summaries
            if (state.droppedCount > 0 && state.summaryLines.isNotEmpty()) {
                appendLine("[Queue overflow] Dropped ${state.droppedCount} message(s) due to cap.")
                appendLine("Summary:")
                state.summaryLines.forEach { line ->
                    appendLine("- $line")
                }
                appendLine()
                state.summaryLines.clear()
            }

            // List all messages
            messages.forEachIndexed { index, msg ->
                appendLine("Message ${index + 1}:")
                appendLine("From: ${msg.senderId}")
                appendLine("Content: ${msg.content}")
                appendLine()
            }
        }

        // Use metadata from last message
        val lastMessage = messages.last()
        return QueuedMessage(
            messageId = "batch_${System.currentTimeMillis()}",
            content = content,
            senderId = lastMessage.senderId,
            chatId = lastMessage.chatId,
            chatType = lastMessage.chatType,
            metadata = mapOf(
                "isBatch" to true,
                "batchSize" to messages.size,
                "messageIds" to messages.map { it.messageId }
            )
        )
    }

    /**
     * Notify AgentLoop of new message (STEER mode)
     *
     * TODO: Requires AgentLoop support for message injection
     */
    private fun notifyAgentLoop(key: String, message: QueuedMessage) {
        // Need to implement communication mechanism with AgentLoop
        // Possible approaches:
        // 1. Use SharedFlow to broadcast new messages
        // 2. Check queue before each AgentLoop iteration
        // 3. Use Channel to pass messages
        Log.d(TAG, "⚠️  [STEER] notifyAgentLoop not implemented yet")
    }

    /**
     * Set queue configuration
     */
    fun setQueueSettings(
        key: String,
        cap: Int? = null,
        dropPolicy: DropPolicy? = null
    ) {
        val state = queues.getOrPut(key) {
            QueueState(key = key, mode = QueueMode.FOLLOWUP)
        }

        if (cap != null) {
            state.cap = cap
        }
        if (dropPolicy != null) {
            state.dropPolicy = dropPolicy
        }
    }

    /**
     * Get queue state (for debugging)
     */
    fun getQueueState(key: String): Map<String, Any> {
        val state = queues[key] ?: return mapOf(
            "exists" to false
        )

        return mapOf(
            "exists" to true,
            "mode" to state.mode.name,
            "isProcessing" to state.isProcessing.get(),
            "queueSize" to state.messages.size,
            "droppedCount" to state.droppedCount,
            "cap" to state.cap,
            "dropPolicy" to state.dropPolicy.name
        )
    }

    /**
     * Clear specific queue
     */
    fun clearQueue(key: String) {
        val state = queues[key] ?: return
        state.messages.clear()
        state.summaryLines.clear()
        Log.d(TAG, "🗑️  Cleared queue for $key")
    }

    /**
     * Clear all queues
     */
    fun clearAllQueues() {
        queues.values.forEach { state ->
            state.messages.clear()
            state.summaryLines.clear()
        }
        queues.clear()
        Log.d(TAG, "🗑️  Cleared all queues")
    }
}

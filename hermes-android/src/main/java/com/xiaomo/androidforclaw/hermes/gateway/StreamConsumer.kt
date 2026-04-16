package com.xiaomo.androidforclaw.hermes.gateway

/**
 * Stream consumer — consumes the agent's streaming output and delivers
 * partial/final responses to the platform adapter.
 *
 * Ported from gateway/stream_consumer.py
 */

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A single chunk from the agent's streaming output.
 */
data class StreamChunk(
    /** The text content of this chunk. */
    val text: String = "",
    /** Whether this is the final chunk. */
    val isFinal: Boolean = false,
    /** Optional tool-call metadata (JSON). */
    val toolCall: JSONObject? = null,
    /** Optional usage metadata (tokens, etc.). */
    val usage: JSONObject? = null,
    /** Optional error message. */
    val error: String? = null,
    /** Optional annotation metadata. */
    val annotations: List<JSONObject> = emptyList())

/**
 * Configuration for the stream consumer.
 */
data class StreamConsumerConfig(
    /** Minimum interval between edit updates (milliseconds). */
    val minEditIntervalMs: Long = 1000,
    /** Maximum number of edits per message. */
    val maxEdits: Int = 50,
    /** Whether to use edit-in-place (true) or send new messages (false). */
    val useEditInPlace: Boolean = true,
    /** Whether to send typing indicator before streaming starts. */
    val sendTypingIndicator: Boolean = true,
    /** Whether to suppress empty chunks. */
    val suppressEmptyChunks: Boolean = true,
    /** Buffer threshold — force edit when accumulated text reaches this length. */
    val bufferThreshold: Int = 40,
    /** Streaming cursor character. */
    val cursor: String = " \u2589")

/**
 * Internal queue item types for the async run loop.
 */
private sealed class QueueItem {
    object Done : QueueItem()
    object SegmentBreak : QueueItem()
    data class Commentary(val text: String) : QueueItem()
    data class Text(val value: String) : QueueItem()
}

/**
 * Stream consumer — consumes streaming chunks and delivers them to the
 * platform adapter.
 *
 * Manages the lifecycle of a single streaming response: sends an initial
 * message, then updates it with progressive edits (if the platform supports
 * edit-in-place) or sends new messages for each significant update.
 *
 * Ported from gateway/stream_consumer.py (GatewayStreamConsumer).
 */
class StreamConsumer(
    private val deliveryRouter: DeliveryRouter,
    private val config: StreamConsumerConfig = StreamConsumerConfig()) {
    companion object {
        private const val TAG = "StreamConsumer"

        /** After this many consecutive flood-control failures, permanently disable progressive edits. */
        private const val MAX_FLOOD_STRIKES = 3

        /** Reasoning/thinking opening tags that models emit inline in content. */
        val OPEN_THINK_TAGS = listOf(
            "<REASONING_SCRATCHPAD>", "<think>", "<reasoning>",
            "<THINKING>", "<thinking>", "<thinking>", "<thought>")

        /** Reasoning/thinking closing tags. */
        val CLOSE_THINK_TAGS = listOf(
            "</REASONING_SCRATCHPAD>", "</think>", "</reasoning>",
            "</THINKING>", "</thinking>", "</thinking>", "</thought>")

        /** Minimum characters required for a standalone new message (with cursor). */
        private const val MIN_NEW_MSG_CHARS = 4

        /** Regex to strip MEDIA:<path> tags. */
        private val MEDIA_RE = Regex("""[`"']?MEDIA:\s*\S+[`"']?""")
    }

    // ── State ───────────────────────────────────────────────────────

    /** Whether streaming is currently active. */
    private val _isActive = AtomicBoolean(false)

    /** Accumulated text from all chunks (after think-block filtering). */
    private var _accumulated = ""

    /** The message id of the initial message (for edit-in-place). */
    private var _messageId: String? = null

    /** The platform name. */
    private var _platform = ""

    /** The chat id. */
    private var _chatId = ""

    /** Timestamp of the last edit (monotonic ms). */
    private var _lastEditTime = 0L

    /** The last text we actually sent/edited — used to skip redundant edits. */
    private var _lastSentText = ""

    /** Whether at least one message was sent or edited. */
    private var _alreadySent = false

    /** Whether the final response was delivered. */
    private var _finalResponseSent = false

    /** Whether progressive edits are still supported. */
    private var _editSupported = true

    /** True when we should send only the missing tail as a final fallback message. */
    private var _fallbackFinalSend = false

    /** The prefix the user already saw before fallback mode. */
    private var _fallbackPrefix = ""

    /** Consecutive flood-control edit failures. */
    private var _floodStrikes = 0

    /** Adaptive edit interval (starts from config, doubles on flood control). */
    private var _currentEditIntervalMs = 0L

    /** Think-block filter: are we currently inside a think block? */
    private var _inThinkBlock = false

    /** Think-block filter: partial tag text held back. */
    private var _thinkBuffer = ""

    /** Internal queue for async run loop. */
    private val _queue = Channel<QueueItem>(Channel.UNLIMITED)

    /** Optional metadata passed through to adapters. */
    private var _metadata: Map<String, Any>? = null

    // ── Public properties ───────────────────────────────────────────

    /** True if at least one message was sent or edited during the run. */
    fun alreadySent(): Boolean = _alreadySent

    /** True when the stream consumer delivered the final assistant reply. */
    fun finalResponseSent(): Boolean = _finalResponseSent

    /** True when streaming is active. */
    val isActive: Boolean get() = _isActive.get()

    // ── Public methods ──────────────────────────────────────────────

    /**
     * Finalize the current stream segment and start a fresh message.
     */
    fun onSegmentBreak() {
        _queue.trySend(QueueItem.SegmentBreak).isSuccess
    }

    /**
     * Queue a completed interim assistant commentary message.
     */
    fun onCommentary(text: String) {
        if (text.isNotEmpty()) {
            _queue.trySend(QueueItem.Commentary(text)).isSuccess
        }
    }

    /**
     * Thread-safe callback — called from the agent's worker thread.
     *
     * When [text] is null, signals a tool boundary: the current message is
     * finalized and subsequent text will be sent as a new message.
     */
    fun onDelta(text: String?) {
        if (text != null && text.isNotEmpty()) {
            _queue.trySend(QueueItem.Text(text)).isSuccess
        } else if (text == null) {
            onSegmentBreak()
        }
    }

    /**
     * Signal that the stream is complete.
     */
    fun finish() {
        _queue.trySend(QueueItem.Done).isSuccess
    }

    /**
     * Start consuming a stream of chunks (Flow-based).
     *
     * @param platform  Platform name.
     * @param chatId    Target chat/channel id.
     * @param replyTo   Optional message id to reply to.
     * @param chunks    Flow of StreamChunk from the agent.
     * @param metadata  Optional metadata map passed to adapters.
     * @return The final message id (if available).
     */
    suspend fun consume(
        platform: String,
        chatId: String,
        replyTo: String? = null,
        chunks: Flow<StreamChunk>,
        metadata: Map<String, Any>? = null): String? {
        if (_isActive.getAndSet(true)) {
            Log.w(TAG, "StreamConsumer is already active")
            return null
        }

        _platform = platform
        _chatId = chatId
        _metadata = metadata
        _accumulated = ""
        _editCount = 0
        _lastEditTime = 0L
        _messageId = null
        _alreadySent = false
        _finalResponseSent = false
        _editSupported = true
        _fallbackFinalSend = false
        _fallbackPrefix = ""
        _floodStrikes = 0
        _currentEditIntervalMs = config.minEditIntervalMs
        _inThinkBlock = false
        _thinkBuffer = ""
        _lastSentText = ""

        try {
            // Send typing indicator if configured
            if (config.sendTypingIndicator) {
                deliveryRouter.sendTyping(platform, chatId)
            }

            coroutineScope {
                // Start the async run loop
                val runJob = launch { run() }

                // Consume chunks and feed into the queue
                launch {
                    chunks.collect { chunk ->
                        if (chunk.error != null) {
                            Log.w(TAG, "Stream error: ${chunk.error}")
                            _handleError(chunk.error)
                            return@collect
                        }
                        if (chunk.isFinal) {
                            _handleFinalChunk(chunk)
                            return@collect
                        }
                        _handleProgressChunk(chunk)
                    }
                    // Signal completion
                    finish()
                }

                // Wait for run loop to finish
                runJob.join()
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "Stream cancelled")
            // Best-effort final edit on cancellation
            if (_accumulated.isNotEmpty() && _messageId != null) {
                try { _sendOrEdit(_accumulated) } catch (_: Exception) {}
            }
            if (_alreadySent) _finalResponseSent = true
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Stream consumer error: ${e.message}")
            _handleError(e.message ?: "Unknown error")
        } finally {
            _isActive.set(false)
        }

        return _messageId
    }

    /**
     * Consume from a Channel instead of a Flow.
     */
    suspend fun consumeChannel(
        platform: String,
        chatId: String,
        replyTo: String? = null,
        channel: Channel<StreamChunk>,
        metadata: Map<String, Any>? = null): String? {
        if (_isActive.getAndSet(true)) {
            Log.w(TAG, "StreamConsumer is already active")
            return null
        }

        _platform = platform
        _chatId = chatId
        _metadata = metadata
        _accumulated = ""
        _editCount = 0
        _lastEditTime = 0L
        _messageId = null
        _alreadySent = false
        _finalResponseSent = false
        _editSupported = true
        _fallbackFinalSend = false
        _fallbackPrefix = ""
        _floodStrikes = 0
        _currentEditIntervalMs = config.minEditIntervalMs
        _inThinkBlock = false
        _thinkBuffer = ""
        _lastSentText = ""

        try {
            if (config.sendTypingIndicator) {
                deliveryRouter.sendTyping(platform, chatId)
            }

            coroutineScope {
                val runJob = launch { run() }

                launch {
                    for (chunk in channel) {
                        if (chunk.error != null) {
                            _handleError(chunk.error)
                            break
                        }
                        if (chunk.isFinal) {
                            _handleFinalChunk(chunk)
                            break
                        }
                        _handleProgressChunk(chunk)
                    }
                    finish()
                }

                runJob.join()
            }
        } catch (e: CancellationException) {
            if (_accumulated.isNotEmpty() && _messageId != null) {
                try { _sendOrEdit(_accumulated) } catch (_: Exception) {}
            }
            if (_alreadySent) _finalResponseSent = true
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Stream consumer error: ${e.message}")
            _handleError(e.message ?: "Unknown error")
        } finally {
            _isActive.set(false)
        }

        return _messageId
    }

    /** Cancel streaming. */
    fun cancel() {
        _isActive.set(false)
        _queue.close()
    }

    // ------------------------------------------------------------------
    // Internal handlers (Flow/Channel consumption)
    // ------------------------------------------------------------------

    /** Number of edits sent so far. */
    private var _editCount = 0

    private suspend fun _handleProgressChunk(chunk: StreamChunk, replyTo: String? = null) {
        if (config.suppressEmptyChunks && chunk.text.isEmpty()) return
        _accumulated += chunk.text
    }

    private suspend fun _handleFinalChunk(chunk: StreamChunk, replyTo: String? = null) {
        if (chunk.text.isNotEmpty()) {
            _accumulated += chunk.text
        }
    }

    private suspend fun _handleError(error: String) {
        val errorText = if (_accumulated.isNotEmpty()) {
            "$_accumulated\n\n[Error: $error]"
        } else {
            "[Error: $error]"
        }
        if (_messageId != null) {
            _editMessage(errorText)
        } else {
            deliveryRouter.deliverText(_platform, _chatId, errorText)
        }
        _isActive.set(false)
    }

    private suspend fun _editMessage(text: String) {
        val adapter = deliveryRouter.getAdapter(_platform) ?: return
        try {
            val result = adapter.editMessage(_chatId, _messageId ?: return, text)
            if (result.success) {
                _lastEditTime = System.currentTimeMillis()
                _editCount++
            }
        } catch (e: Exception) {
            Log.w(TAG, "Edit failed: ${e.message}")
        }
    }

    // ------------------------------------------------------------------
    // Think-block filtering (ported from Python _filter_and_accumulate)
    // ------------------------------------------------------------------

    /**
     * Add a text delta to the accumulated buffer, suppressing think blocks.
     *
     * Uses a state machine that tracks whether we are inside a
     * reasoning/thinking block.  Text inside such blocks is silently
     * discarded.  Partial tags at buffer boundaries are held back in
     * [_thinkBuffer] until enough characters arrive to decide.
     */
    fun _filterAndAccumulate(text: String) {
        var buf = _thinkBuffer + text
        _thinkBuffer = ""

        while (buf.isNotEmpty()) {
            if (_inThinkBlock) {
                // Look for the earliest closing tag
                var bestIdx = -1
                var bestLen = 0
                for (tag in CLOSE_THINK_TAGS) {
                    val idx = buf.indexOf(tag)
                    if (idx != -1 && (bestIdx == -1 || idx < bestIdx)) {
                        bestIdx = idx
                        bestLen = tag.length
                    }
                }

                if (bestLen > 0) {
                    // Found closing tag — discard block, process remainder
                    _inThinkBlock = false
                    buf = buf.substring(bestIdx + bestLen)
                } else {
                    // No closing tag yet — hold tail that could be a partial closing tag
                    val maxTag = CLOSE_THINK_TAGS.maxOf { it.length }
                    _thinkBuffer = if (buf.length > maxTag) buf.takeLast(maxTag) else buf
                    return
                }
            } else {
                // Look for earliest opening tag at a block boundary
                var bestIdx = -1
                var bestLen = 0
                for (tag in OPEN_THINK_TAGS) {
                    var searchStart = 0
                    while (true) {
                        val idx = buf.indexOf(tag, searchStart)
                        if (idx == -1) break
                        // Block-boundary check
                        val isBoundary = if (idx == 0) {
                            _accumulated.isEmpty() || _accumulated.endsWith("\n")
                        } else {
                            val preceding = buf.substring(0, idx)
                            val lastNl = preceding.lastIndexOf('\n')
                            if (lastNl == -1) {
                                (_accumulated.isEmpty() || _accumulated.endsWith("\n"))
                                    && preceding.isBlank()
                            } else {
                                preceding.substring(lastNl + 1).isBlank()
                            }
                        }

                        if (isBoundary && (bestIdx == -1 || idx < bestIdx)) {
                            bestIdx = idx
                            bestLen = tag.length
                            break
                        }
                        searchStart = idx + 1
                    }
                }

                if (bestLen > 0) {
                    // Emit text before the tag, enter think block
                    _accumulated += buf.substring(0, bestIdx)
                    _inThinkBlock = true
                    buf = buf.substring(bestIdx + bestLen)
                } else {
                    // No opening tag — check for partial tag at tail
                    var heldBack = 0
                    for (tag in OPEN_THINK_TAGS) {
                        for (i in 1 until tag.length) {
                            if (buf.endsWith(tag.substring(0, i)) && i > heldBack) {
                                heldBack = i
                            }
                        }
                    }
                    if (heldBack > 0) {
                        _accumulated += buf.substring(0, buf.length - heldBack)
                        _thinkBuffer = buf.substring(buf.length - heldBack)
                    } else {
                        _accumulated += buf
                    }
                    return
                }
            }
        }
    }

    /**
     * Flush any held-back partial-tag buffer into accumulated text.
     *
     * Called when the stream ends so that partial text that was waiting
     * for a potential open tag is not lost.
     */
    fun _flushThinkBuffer() {
        if (_thinkBuffer.isNotEmpty() && !_inThinkBlock) {
            _accumulated += _thinkBuffer
            _thinkBuffer = ""
        }
    }

    // ------------------------------------------------------------------
    // Main async run loop (ported from Python run())
    // ------------------------------------------------------------------

    /**
     * Async task that drains the queue and edits the platform message.
     */
    suspend fun run() {
        val adapter = deliveryRouter.getAdapter(_platform)
        val rawLimit = 4096 // Default; adapters can override via MAX_MESSAGE_LENGTH metadata
        val safeLimit = maxOf(500, rawLimit - config.cursor.length - 100)

        try {
            while (true) {
                // Drain all available items from the queue
                var gotDone = false
                var gotSegmentBreak = false
                var commentaryText: String? = null

                while (true) {
                    val item = _queue.tryReceive().getOrNull() ?: break
                    when (item) {
                        is QueueItem.Done -> { gotDone = true; break }
                        is QueueItem.SegmentBreak -> { gotSegmentBreak = true; break }
                        is QueueItem.Commentary -> { commentaryText = item.text; break }
                        is QueueItem.Text -> _filterAndAccumulate(item.value)
                    }
                }

                // Flush think buffer on stream end
                if (gotDone) _flushThinkBuffer()

                // Decide whether to flush an edit
                val now = System.currentTimeMillis()
                val elapsed = now - _lastEditTime
                val shouldEdit = gotDone
                    || gotSegmentBreak
                    || commentaryText != null
                    || (elapsed >= _currentEditIntervalMs && _accumulated.isNotEmpty())
                    || _accumulated.length >= config.bufferThreshold

                var currentUpdateVisible = false
                if (shouldEdit && _accumulated.isNotEmpty()) {
                    // Split overflow for first message (no existing message to edit)
                    if (_accumulated.length > safeLimit && _messageId == null) {
                        val chunks = _splitTextChunks(_accumulated, safeLimit)
                        for (chunk in chunks) {
                            _sendNewChunk(chunk, _messageId)
                        }
                        _accumulated = ""
                        _lastSentText = ""
                        _lastEditTime = System.currentTimeMillis()
                        if (gotDone) {
                            _finalResponseSent = _alreadySent
                            return
                        }
                        if (gotSegmentBreak) {
                            _resetSegmentState()
                        }
                        continue
                    }

                    // Existing message: edit with first chunk, new message for overflow
                    while (_accumulated.length > safeLimit && _messageId != null && _editSupported) {
                        val splitAt = _accumulated.lastIndexOf('\n', safeLimit)
                            .let { if (it < safeLimit / 2) safeLimit else it }
                        val chunk = _accumulated.substring(0, splitAt)
                        val ok = _sendOrEdit(chunk)
                        if (_fallbackFinalSend || !ok) break
                        _accumulated = _accumulated.substring(splitAt).trimStart('\n')
                        _messageId = null
                        _lastSentText = ""
                    }

                    var displayText = _accumulated
                    if (!gotDone && !gotSegmentBreak && commentaryText == null) {
                        displayText += config.cursor
                    }

                    currentUpdateVisible = _sendOrEdit(displayText)
                    _lastEditTime = System.currentTimeMillis()
                }

                if (gotDone) {
                    // Final edit without cursor
                    if (_accumulated.isNotEmpty()) {
                        if (_fallbackFinalSend) {
                            _sendFallbackFinal(_accumulated)
                        } else if (currentUpdateVisible) {
                            _finalResponseSent = true
                        } else if (_messageId != null) {
                            _finalResponseSent = _sendOrEdit(_accumulated)
                        } else if (!_alreadySent) {
                            _finalResponseSent = _sendOrEdit(_accumulated)
                        }
                    }
                    return
                }

                if (commentaryText != null) {
                    _resetSegmentState()
                    _sendCommentary(commentaryText)
                    _lastEditTime = System.currentTimeMillis()
                    _resetSegmentState()
                }

                if (gotSegmentBreak) {
                    _resetSegmentState(preserveNoEdit = true)
                }

                delay(50) // Small yield to not busy-loop
            }
        } catch (e: CancellationException) {
            // Best-effort final edit on cancellation
            if (_accumulated.isNotEmpty() && _messageId != null) {
                try { _sendOrEdit(_accumulated) } catch (_: Exception) {}
            }
            if (_alreadySent) _finalResponseSent = true
        } catch (e: Exception) {
            Log.e(TAG, "Stream consumer run error: ${e.message}")
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Reset segment state for a new message segment.
     */
    fun _resetSegmentState(preserveNoEdit: Boolean = false) {
        if (preserveNoEdit && _messageId == "__no_edit__") return
        _messageId = null
        _accumulated = ""
        _lastSentText = ""
        _fallbackFinalSend = false
        _fallbackPrefix = ""
    }

    /**
     * Strip MEDIA: directives and internal markers from text before display.
     */
    fun _cleanForDisplay(text: String): String {
        if ("MEDIA:" !in text && "[[audio_as_voice]]" !in text) return text
        var cleaned = text.replace("[[audio_as_voice]]", "")
        cleaned = MEDIA_RE.replace(cleaned, "")
        // Collapse excessive blank lines
        cleaned = Regex("\n{3,}").replace(cleaned, "\n\n")
        return cleaned.trimEnd()
    }

    /**
     * Send a new message chunk, optionally threaded to a previous message.
     *
     * Returns the message id so callers can thread subsequent chunks.
     */
    suspend fun _sendNewChunk(text: String, replyToId: String?): String? {
        val cleaned = _cleanForDisplay(text)
        if (cleaned.isBlank()) return replyToId
        try {
            val result = deliveryRouter.deliverText(_platform, _chatId, cleaned, replyToId)
            if (result.success && result.messageId != null) {
                _messageId = result.messageId
                _alreadySent = true
                _lastSentText = cleaned
                return result.messageId
            } else {
                _editSupported = false
                return replyToId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stream send chunk error: ${e.message}")
            return replyToId
        }
    }

    /**
     * Return the visible text already shown in the streamed message.
     */
    fun _visiblePrefix(): String {
        var prefix = _lastSentText
        if (config.cursor.isNotEmpty() && prefix.endsWith(config.cursor)) {
            prefix = prefix.substring(0, prefix.length - config.cursor.length)
        }
        return _cleanForDisplay(prefix)
    }

    /**
     * Return only the part of [finalText] the user has not already seen.
     */
    fun _continuationText(finalText: String): String {
        val prefix = _fallbackPrefix.ifEmpty { _visiblePrefix() }
        if (prefix.isNotEmpty() && finalText.startsWith(prefix)) {
            return finalText.substring(prefix.length).trimStart()
        }
        return finalText
    }

    /**
     * Split text into reasonably sized chunks for fallback sends.
     */
    fun _splitTextChunks(text: String, limit: Int): List<String> {
        if (text.length <= limit) return listOf(text)
        val chunks = mutableListOf<String>()
        var remaining = text
        while (remaining.length > limit) {
            val splitAt = remaining.lastIndexOf('\n', limit)
                .let { if (it < limit / 2) limit else it }
            chunks.add(remaining.substring(0, splitAt))
            remaining = remaining.substring(splitAt).trimStart('\n')
        }
        if (remaining.isNotEmpty()) chunks.add(remaining)
        return chunks
    }

    /**
     * Send the final continuation after streaming edits stop working.
     *
     * Retries each chunk once on flood-control failures with a short delay.
     */
    suspend fun _sendFallbackFinal(text: String) {
        val finalText = _cleanForDisplay(text)
        val continuation = _continuationText(finalText)
        _fallbackFinalSend = false
        if (continuation.isBlank()) {
            _alreadySent = true
            _finalResponseSent = true
            return
        }

        val rawLimit = 4096
        val safeLimit = maxOf(500, rawLimit - 100)
        val chunks = _splitTextChunks(continuation, safeLimit)

        var lastMessageId: String? = null
        var lastSuccessfulChunk = ""
        var sentAnyChunk = false

        for (chunk in chunks) {
            var result: DeliveryResult? = null
            for (attempt in 0 until 2) {
                result = deliveryRouter.deliverText(_platform, _chatId, chunk, null)
                if (result.success) break
                if (attempt == 0 && _isFloodError(result)) {
                    Log.d(TAG, "Flood control on fallback send, retrying in 3s")
                    delay(3000)
                } else {
                    break
                }
            }

            if (result == null || !result.success) {
                if (sentAnyChunk) {
                    _alreadySent = true
                    _finalResponseSent = true
                    _messageId = lastMessageId
                    _lastSentText = lastSuccessfulChunk
                    _fallbackPrefix = ""
                    return
                }
                _alreadySent = false
                _messageId = null
                _lastSentText = ""
                _fallbackPrefix = ""
                return
            }
            sentAnyChunk = true
            lastSuccessfulChunk = chunk
            lastMessageId = result.messageId ?: lastMessageId
        }

        _messageId = lastMessageId
        _alreadySent = true
        _finalResponseSent = true
        _lastSentText = chunks.last()
        _fallbackPrefix = ""
    }

    /**
     * Check if a delivery failure is due to flood control / rate limiting.
     */
    fun _isFloodError(result: DeliveryResult): Boolean {
        val err = result.error?.lowercase() ?: return false
        return "flood" in err || "retry after" in err || "rate" in err
    }

    /**
     * Best-effort edit to remove the cursor from the last visible message.
     *
     * Called when entering fallback mode so the user doesn't see a stuck cursor.
     */
    suspend fun _tryStripCursor() {
        val msgId = _messageId ?: return
        if (msgId == "__no_edit__") return
        val prefix = _visiblePrefix()
        if (prefix.isBlank()) return
        try {
            val adapter = deliveryRouter.getAdapter(_platform) ?: return
            adapter.editMessage(_chatId, msgId, prefix)
            _lastSentText = prefix
        } catch (_: Exception) {} // best-effort
    }

    /**
     * Send a completed interim assistant commentary message.
     */
    suspend fun _sendCommentary(text: String): Boolean {
        val cleaned = _cleanForDisplay(text)
        if (cleaned.isBlank()) return false
        try {
            val result = deliveryRouter.deliverText(_platform, _chatId, cleaned)
            if (result.success) {
                _alreadySent = true
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Commentary send error: ${e.message}")
        }
        return false
    }

    /**
     * Send or edit the streaming message.
     *
     * Returns true if the text was successfully delivered (sent or edited),
     * false otherwise.
     */
    suspend fun _sendOrEdit(text: String): Boolean {
        var cleaned = _cleanForDisplay(text)

        // Check for cursor-only / whitespace-only content
        var visibleWithoutCursor = cleaned
        if (config.cursor.isNotEmpty()) {
            visibleWithoutCursor = visibleWithoutCursor.replace(config.cursor, "")
        }
        if (visibleWithoutCursor.isBlank()) return true
        if (cleaned.isBlank()) return true

        // Guard: don't create a tiny standalone message with cursor
        val visibleStripped = visibleWithoutCursor.trim()
        if (_messageId == null && config.cursor.isNotEmpty()
            && config.cursor in cleaned && visibleStripped.length < MIN_NEW_MSG_CHARS
        ) {
            return true // too short — accumulate more
        }

        try {
            if (_messageId != null) {
                if (_editSupported) {
                    // Skip redundant edit
                    if (cleaned == _lastSentText) return true

                    // Edit existing message
                    val adapter = deliveryRouter.getAdapter(_platform)
                    if (adapter != null) {
                        val result = adapter.editMessage(_chatId, _messageId!!, cleaned)
                        if (result.success) {
                            _alreadySent = true
                            _lastSentText = cleaned
                            _floodStrikes = 0
                            return true
                        } else {
                            // Edit failed — check flood control
                            if (_isFloodError(DeliveryResult(success = false, error = result.error))) {
                                _floodStrikes++
                                _currentEditIntervalMs = minOf(
                                    _currentEditIntervalMs * 2, 10000L
                                )
                                Log.d(TAG,
                                    "Flood control on edit (strike $_floodStrikes/$MAX_FLOOD_STRIKES), " +
                                    "backoff interval → ${_currentEditIntervalMs}ms"
                                )
                                if (_floodStrikes < MAX_FLOOD_STRIKES) {
                                    _lastEditTime = System.currentTimeMillis()
                                    return false
                                }
                            }
                            // Non-flood error or flood strikes exhausted → fallback mode
                            Log.d(TAG, "Edit failed (strikes=$_floodStrikes), entering fallback mode")
                            _fallbackPrefix = _visiblePrefix()
                            _fallbackFinalSend = true
                            _editSupported = false
                            _alreadySent = true
                            _tryStripCursor()
                            return false
                        }
                    }
                    return false
                } else {
                    // Editing not supported — skip intermediate updates
                    return false
                }
            } else {
                // First message — send new
                val result = deliveryRouter.deliverText(_platform, _chatId, cleaned)
                if (result.success) {
                    if (result.messageId != null) {
                        _messageId = result.messageId
                    } else {
                        _editSupported = false
                    }
                    _alreadySent = true
                    _lastSentText = cleaned
                    if (result.messageId == null) {
                        _fallbackPrefix = _visiblePrefix()
                        _fallbackFinalSend = true
                        _messageId = "__no_edit__"
                    }
                    return true
                } else {
                    _editSupported = false
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stream send/edit error: ${e.message}")
            return false
        }
    }
}

package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * Base platform adapter — abstract interface that all platform adapters implement.
 *
 * Ported from gateway/platforms/base.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Supported document types for platform adapters.
 */
val SUPPORTED_DOCUMENT_TYPES: Set<String> = setOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "text/plain",
    "text/csv",
    "application/json",
    "application/xml",
    "application/zip")

/**
 * Supported video types for platform adapters.
 */
val SUPPORTED_VIDEO_TYPES: Set<String> = setOf(
    "video/mp4", "video/quicktime", "video/x-msvideo", "video/x-matroska", "video/webm")

/**
 * Message types that can be received from a platform.
 */
enum class MessageType {
    /** Plain text message. */
    TEXT,
    /** Photo/image message. */
    PHOTO,
    /** Audio/voice message. */
    AUDIO,
    /** Video message. */
    VIDEO,
    /** Document/file message. */
    DOCUMENT,
    /** Sticker message. */
    STICKER,
    /** Location message. */
    LOCATION,
    /** Contact message. */
    CONTACT,
    /** Poll message. */
    POLL,
    /** Command message (e.g. /start). */
    COMMAND,
    /** System/event message. */
    SYSTEM,
    /** Reaction event. */
    REACTION,
    /** Edited message. */
    EDITED,
    /** Deleted message. */
    DELETED,
    /** Unknown type. */
    UNKNOWN,
}

/**
 * Processing outcome — returned by the agent handler.
 */
data class ProcessingOutcome(
    /** Whether the message was successfully processed. */
    val success: Boolean = true,
    /** The response text to send back. */
    val responseText: String = "",
    /** Media URLs to include in the response. */
    val mediaUrls: List<String> = emptyList(),
    /** Whether to suppress sending (e.g. for silent commands). */
    val suppressSend: Boolean = false,
    /** Error message (if success == false). */
    val error: String? = null)

/**
 * Result of a send operation.
 */
data class SendResult(
    /** Whether the send was successful. */
    val success: Boolean = true,
    /** Platform-specific message id. */
    val messageId: String? = null,
    /** Error message (if success == false). */
    val error: String? = null,
    /** Raw response from the platform. */
    val rawResponse: Any? = null)

/**
 * Incoming message event from a platform.
 */
data class MessageEvent(
    /** The message text. */
    val text: String,
    /** The message type. */
    val messageType: MessageType = MessageType.TEXT,
    /** Source metadata. */
    val source: SessionSource,
    /** Original message id from the platform. */
    val message_id: String = "",
    /** ID of the message this is replying to (if any). */
    val replyToMessageId: String? = null,
    /** Timestamp of the message. */
    val timestamp: Instant = Instant.now(),
    /** Raw message data from the platform. */
    val rawMessage: Any? = null,
    /** Media URLs (for photo/audio/video/document messages). */
    val mediaUrls: List<String> = emptyList(),
    /** Media types corresponding to mediaUrls. */
    val mediaTypes: List<String> = emptyList(),
    /** Sticker set name (if applicable). */
    val stickerSet: String? = null,
    /** Sticker file id (if applicable). */
    val stickerFileId: String? = null,
    /** Location latitude (if applicable). */
    val latitude: Double? = null,
    /** Location longitude (if applicable). */
    val longitude: Double? = null,
    /** Contact name (if applicable). */
    val contactName: String? = null,
    /** Contact phone (if applicable). */
    val contactPhone: String? = null,
    /** Poll question (if applicable). */
    val pollQuestion: String? = null,
    /** Poll options (if applicable). */
    val pollOptions: List<String> = emptyList(),
    /** Reaction emoji (if applicable). */
    val reactionEmoji: String? = null,
    /** Whether the reaction was added (true) or removed (false). */
    val reactionAdded: Boolean = true,
    /** Whether this is an edited message. */
    val isEdited: Boolean = false,
    /** Whether this is a deleted message. */
    val isDeleted: Boolean = false,
    /** Arbitrary metadata. */
    val metadata: JSONObject = JSONObject()) {
    /** Build a session key from this event. */
    val sessionKey: String get() = source.sessionKey

    /** True when the message has media attachments. */
    val hasMedia: Boolean get() = mediaUrls.isNotEmpty()

    /** True when the message is a command (starts with /). */
    val isCommand: Boolean get() = text.startsWith("/") || messageType == MessageType.COMMAND

    /** Extract the command name (e.g. "/start" → "start"). */
    val commandName: String?
        get() = if (isCommand) text.substringBefore(" ").removePrefix("/") else null

    /** Extract the command arguments. */
    val commandArgs: String?
        get() = if (isCommand) text.substringAfter(" ", "").ifEmpty { null } else null
}

/**
 * Abstract base class for all platform adapters.
 *
 * Platform adapters bridge between the gateway and an external messaging
 * platform (Telegram, Discord, Feishu, etc.).  They handle:
 * - Connecting to the platform (WebSocket, polling, webhook, etc.)
 * - Receiving inbound messages and converting them to [MessageEvent]
 * - Sending outbound messages (text, image, document, etc.)
 * - Typing indicators
 * - Managing connection lifecycle
 */
abstract class BasePlatformAdapter(
    /** Platform configuration. */
    val config: PlatformConfig,
    /** Platform name. */
    val platform: Platform) {
    companion object {
        private const val _TAG = "BasePlatformAdapter"
    }

    /** Platform name as a string. */
    val name: String get() = platform.value

    /** Whether the adapter is currently connected. */
    open val isConnected: AtomicBoolean = AtomicBoolean(false)

    /** Whether the adapter is running. */
    val isRunning: AtomicBoolean = AtomicBoolean(false)

    /** Background tasks managed by this adapter. */
    protected val _backgroundTasks: MutableSet<kotlinx.coroutines.Job> = java.util.Collections.synchronizedSet(LinkedHashSet())

    /** Message handler callback — set by the gateway runner. */
    var messageHandler: (suspend (MessageEvent) -> Unit)? = null

    /** Busy session handler — called when a message arrives for a session already processing. */
    private var _busySessionHandler: (suspend (MessageEvent, String) -> Boolean)? = null

    /** Session store reference for active session checks. */
    private var _sessionStore: Any? = null

    /** Set of chat IDs where typing indicator is paused. */
    private val _typingPaused: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    /** Coroutine scope for this adapter. */
    protected val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Home channel id for system notifications. */
    val homeChatId: String? get() = config.homeChannel?.chatId

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Connect to the platform.
     *
     * Returns true on success, false on failure.
     */
    abstract suspend fun connect(): Boolean

    /**
     * Disconnect from the platform.
     *
     * Should be idempotent — safe to call multiple times.
     */
    abstract suspend fun disconnect()

    /** Mark the adapter as connected. */
    protected fun markConnected() {
        isConnected.set(true)
        Log.i(_TAG, "[$name] Connected")
    }

    /** Mark the adapter as disconnected. */
    protected fun markDisconnected() {
        isConnected.set(false)
        Log.i(_TAG, "[$name] Disconnected")
    }

    // ------------------------------------------------------------------
    // Outbound messaging
    // ------------------------------------------------------------------

    /**
     * Send a text message.
     *
     * @param chatId   Target chat/channel id.
     * @param content  Message text.
     * @param replyTo  Optional message id to reply to.
     * @return SendResult
     */
    abstract suspend fun send(
        chatId: String,
        content: String,
        replyTo: String? = null,
        metadata: JSONObject? = null): SendResult

    /**
     * Send an image message.
     *
     * @param chatId    Target chat/channel id.
     * @param imageUrl  URL or local path to the image.
     * @param caption   Optional caption text.
     * @param replyTo   Optional message id to reply to.
     * @return SendResult
     */
    suspend open fun sendImage(
        chatId: String,
        imageUrl: String,
        caption: String? = null,
        replyTo: String? = null): SendResult = SendResult(success = false, error = "Not supported")

    /**
     * Send a document/file message.
     *
     * @param chatId    Target chat/channel id.
     * @param fileUrl   URL or local path to the file.
     * @param fileName  Optional display name.
     * @param caption   Optional caption text.
     * @param replyTo   Optional message id to reply to.
     * @return SendResult
     */
    suspend open fun sendDocument(
        chatId: String,
        fileUrl: String,
        fileName: String? = null,
        caption: String? = null,
        replyTo: String? = null): SendResult = SendResult(success = false, error = "Not supported")

    /**
     * Send a typing indicator.
     *
     * @param chatId  Target chat/channel id.
     */
    suspend open fun sendTyping(chatId: String, metadata: JSONObject? = null): Unit = Unit

    /**
     * Edit a previously sent message.
     *
     * @param chatId    Chat/channel id.
     * @param messageId Message id to edit.
     * @param newText   New message text.
     */
    suspend fun editMessage(
        chatId: String,
        messageId: String,
        newText: String): SendResult = SendResult(success = false, error = "Not supported")

    /**
     * Get info about a chat/channel.
     *
     * @param chatId  Chat/channel id.
     * @return JSON object with chat info.
     */
    open suspend fun getChatInfo(chatId: String): JSONObject = JSONObject()

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Dispatch an incoming message to the registered handler.
     */
    protected suspend fun handleMessage(event: MessageEvent) {
        val handler = messageHandler
        if (handler == null) {
            Log.w(_TAG, "[$name] No message handler registered, dropping message")
            return
        }
        try {
            handler(event)
        } catch (e: Exception) {
            Log.e(_TAG, "[$name] Message handler error: ${e.message}")
        }
    }

    /**
     * Build a [SessionSource] from common parameters.
     */
    fun buildSource(
        chatId: String,
        chatName: String = "",
        chatType: String = "dm",
        userId: String = "",
        userName: String = "",
        threadId: String? = null,
        isAdmin: Boolean = false): SessionSource = SessionSource(
        platform = name,
        chatId = chatId,
        chatName = chatName,
        chatType = chatType,
        userId = userId,
        userName = userName,
        threadId = threadId,
        isAdmin = isAdmin)

    /**
     * Cancel all background tasks.
     */
    protected fun cancelBackgroundTasks() {
        _backgroundTasks.forEach { it.cancel() }
        _backgroundTasks.clear()
    }

    // ── Fatal error handling (ported from gateway/platforms/base.py) ─

    @Volatile private var _fatalErrorCode: String? = null
    @Volatile private var _fatalErrorMessage: String? = null
    @Volatile private var _fatalErrorRetryable: Boolean = false
    private var _fatalErrorHandler: (suspend (BasePlatformAdapter) -> Unit)? = null

    /** Whether this adapter has a fatal error. */
    fun hasFatalError(): Boolean = _fatalErrorCode != null

    /** Fatal error message. */
    fun fatalErrorMessage(): String? = _fatalErrorMessage

    /** Fatal error code. */
    fun fatalErrorCode(): String? = _fatalErrorCode

    /** Whether the fatal error is retryable. */
    fun fatalErrorRetryable(): Boolean = _fatalErrorRetryable

    /** Set a handler for fatal errors. */
    fun setFatalErrorHandler(handler: suspend (BasePlatformAdapter) -> Unit) {
        _fatalErrorHandler = handler
    }

    /** Set a fatal error. */
    protected fun setFatalError(code: String, message: String, retryable: Boolean = false) {
        _fatalErrorCode = code
        _fatalErrorMessage = message
        _fatalErrorRetryable = retryable
        isConnected.set(false)
        Log.e(_TAG, "Fatal error on $name: [$code] $message (retryable=$retryable)")
    }

    /** Notify fatal error handler. */
    protected suspend fun notifyFatalError() {
        _fatalErrorHandler?.invoke(this)
    }

    // ── Platform locks ──────────────────────────────────────────────

    @Volatile private var _platformLockScope: String? = null

    /** Acquire a platform lock. */
    fun acquirePlatformLock(scope: String, identity: String, resourceDesc: String): Boolean {
        if (_platformLockScope != null) return false
        _platformLockScope = scope
        Log.d(_TAG, "Any? acquired: $scope/$identity for $resourceDesc")
        return true
    }

    /** Release the platform lock. */
    fun releasePlatformLock() {
        _platformLockScope = null
    }

    // ── Command detection ───────────────────────────────────────────

    /** Check if a message is a command (starts with /). */
    fun isCommand(event: MessageEvent): Boolean {
        val text = event.text.ifEmpty { return false }
        return text.startsWith("/")
    }

    /** Get the command name from a message. */
    fun getCommand(event: MessageEvent): String? {
        val text = event.text.ifEmpty { return null }
        if (!text.startsWith("/")) return null
        return text.substringBefore(' ').substring(1)
    }

    /** Get command arguments from a message. */
    fun getCommandArgs(event: MessageEvent): String {
        val text = event.text.ifEmpty { return "" }
        if (!text.startsWith("/")) return text
        val spaceIdx = text.indexOf(' ')
        return if (spaceIdx >= 0) text.substring(spaceIdx + 1) else ""
    }


    open fun name(): String {
        return platform.value.replaceFirstChar { it.uppercase() }
    }

    fun isConnected(): Boolean {
        return isConnected.get()
    }

    fun setBusySessionHandler(handler: (suspend (MessageEvent, String) -> Boolean)?) {
        // Store busy session handler for checking if a session is already processing.
        // On Android, this is a simplified callback interface.
        _busySessionHandler = handler
    }

    fun setSessionStore(sessionStore: Any?) {
        // Store a reference to the session store for active session checks.
        _sessionStore = sessionStore
    }

    suspend fun playTts(chatId: String, audioPath: String): SendResult {
        // Default: fall back to sending as voice message.
        return sendVoice(chatId, audioPath)
    }

    fun pauseTypingForChat(chatId: String) {
        _typingPaused.add(chatId)
    }

    fun resumeTypingForChat(chatId: String) {
        _typingPaused.remove(chatId)
    }

    /** Active session interrupt events — keyed by session_key. */
    protected val _activeSessions: java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicBoolean> =
        java.util.concurrent.ConcurrentHashMap()

    /** Deferred callbacks to fire after delivery — keyed by session_key. */
    protected val _postDeliveryCallbacks: java.util.concurrent.ConcurrentHashMap<String, Any> =
        java.util.concurrent.ConcurrentHashMap()

    /** Register an incoming-message handler (1:1 with Python set_message_handler). */
    @JvmName("setMessageHandlerImpl")
    fun setMessageHandler(handler: (suspend (MessageEvent) -> Unit)?) {
        this.messageHandler = handler
    }

    /** Signal the active session loop to stop and clear typing immediately. */
    suspend fun interruptSessionActivity(sessionKey: String, chatId: String) {
        if (sessionKey.isNotEmpty()) {
            _activeSessions[sessionKey]?.set(true)
        }
        try { stopTyping(chatId) } catch (_: Exception) {}
    }

    /**
     * Register a deferred callback to fire after the main response.
     *
     * `generation` lets callers tie the callback to a specific gateway run
     * generation so stale runs cannot clear callbacks owned by a fresher run.
     */
    fun registerPostDeliveryCallback(
        sessionKey: String,
        callback: (suspend () -> Unit)?,
        generation: Long? = null) {
        if (sessionKey.isEmpty() || callback == null) return
        _postDeliveryCallbacks[sessionKey] = if (generation == null) callback else (generation to callback)
    }

    /** Pop a deferred callback, optionally requiring generation ownership. */
    @Suppress("UNCHECKED_CAST")
    fun popPostDeliveryCallback(sessionKey: String, generation: Long? = null): (suspend () -> Unit)? {
        if (sessionKey.isEmpty()) return null
        val entry = _postDeliveryCallbacks[sessionKey] ?: return null
        if (entry is Pair<*, *>) {
            val (entryGen, cb) = entry as Pair<Long, suspend () -> Unit>
            if (generation != null && entryGen != generation) return null
            _postDeliveryCallbacks.remove(sessionKey)
            return cb
        }
        if (generation != null) return null
        _postDeliveryCallbacks.remove(sessionKey)
        return entry as? (suspend () -> Unit)
    }
}

// ── Module-level utility functions (ported from gateway/platforms/base.py) ──

/** Count UTF-16 code units (for LLM token counting). */
fun utf16Len(s: String): Int = s.fold(0) { acc, c ->
    acc + if (c.code > 0xFFFF) 2 else 1
}

/** Truncate string to UTF-16 limit. */
fun prefixWithinUtf16Limit(s: String, limit: Int): String {
    var count = 0
    for ((i, c) in s.withIndex()) {
        count += if (c.code > 0xFFFF) 2 else 1
        if (count > limit) return s.substring(0, i)
    }
    return s
}

/** Sanitize URL for logging (mask credentials). */
fun safeUrlForLog(url: String, maxLength: Int = 80): String {
    val sanitized = url.replace(Regex("://[^@]+@"), "://***@")
    return if (sanitized.length > maxLength) sanitized.take(maxLength - 3) + "..." else sanitized
}

/** Get image cache directory. */
fun getImageCacheDir(context: Context): File {
    return File(context.cacheDir, "media/images").also { it.mkdirs() }
}

/** Check if data looks like an image. */
fun looksLikeImage(data: ByteArray): Boolean {
    if (data.size < 4) return false
    return (data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte()) || // JPEG
           (data[0] == 0x89.toByte() && data[1] == 0x50.toByte()) || // PNG
           (data[0] == 0x47.toByte() && data[1] == 0x49.toByte())    // GIF
}

/** Cache image from URL. */
suspend fun cacheImageFromUrl(context: Context, url: String, ext: String = ".jpg"): String {
    return withContext(Dispatchers.IO) {
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val bytes = response.body?.bytes() ?: byteArrayOf()
        cacheImageFromBytes(context, bytes, ext)
    }
}

/** Clean up old cached images. */
fun cleanupImageCache(context: Context, maxAgeHours: Int = 24): Int {
    val dir = getImageCacheDir(context)
    val cutoff = System.currentTimeMillis() - maxAgeHours * 3600_000
    var count = 0
    dir.listFiles()?.forEach { file ->
        if (file.lastModified() < cutoff && file.delete()) count++
    }
    return count
}

/** Get audio cache directory. */
fun getAudioCacheDir(context: Context): File {
    return File(context.cacheDir, "media/audio").also { it.mkdirs() }
}

/** Cache audio from URL. */
suspend fun cacheAudioFromUrl(context: Context, url: String, ext: String = ".ogg"): String {
    return withContext(Dispatchers.IO) {
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val bytes = response.body?.bytes() ?: byteArrayOf()
        cacheAudioFromBytes(context, bytes, ext)
    }
}

/** Get document cache directory. */
fun getDocumentCacheDir(context: Context): File {
    return File(context.cacheDir, "media/documents").also { it.mkdirs() }
}

/** Clean up old cached documents. */
fun cleanupDocumentCache(context: Context, maxAgeHours: Int = 24): Int {
    val dir = getDocumentCacheDir(context)
    val cutoff = System.currentTimeMillis() - maxAgeHours * 3600_000
    var count = 0
    dir.listFiles()?.forEach { file ->
        if (file.lastModified() < cutoff && file.delete()) count++
    }
    return count
}

/**
 * Cache an image from bytes and return the local file path.
 */
fun cacheImageFromBytes(
    context: Context,
    data: ByteArray,
    extension: String = ".jpg"): String {
    val dir = File(context.cacheDir, "media/images")
    dir.mkdirs()
    val file = File(dir, "${System.currentTimeMillis()}_${data.hashCode()}$extension")
    file.writeBytes(data)
    return file.absolutePath
}

/**
 * Cache an audio file from bytes and return the local file path.
 */
fun cacheAudioFromBytes(
    context: Context,
    data: ByteArray,
    extension: String = ".mp3"): String {
    val dir = File(context.cacheDir, "media/audio")
    dir.mkdirs()
    val file = File(dir, "${System.currentTimeMillis()}_${data.hashCode()}$extension")
    file.writeBytes(data)
    return file.absolutePath
}

/**
 * Cache a document from bytes and return the local file path.
 */
fun cacheDocumentFromBytes(
    context: Context,
    data: ByteArray,
    filename: String = "document"): String {
    val dir = File(context.cacheDir, "media/documents")
    dir.mkdirs()
    val file = File(dir, "${System.currentTimeMillis()}_$filename")
    file.writeBytes(data)
    return file.absolutePath
}

/**
 * Check if the network is accessible (non-loopback, non-link-local).
 */
fun isNetworkAccessible(): Boolean {
    return try {
        val addr = java.net.InetAddress.getLocalHost()
        !addr.isLoopbackAddress && !addr.isLinkLocalAddress
    } catch (e: Exception) {
        false
    }
}

/**
 * Resolve proxy URL from environment or system settings.
 */
fun resolveProxyUrl(): String? {
    return System.getenv("HTTPS_PROXY")
        ?: System.getenv("https_proxy")
        ?: System.getenv("HTTP_PROXY")
        ?: System.getenv("http_proxy")
}

// ── Missing methods from base.py ──────────────────────────────────

/**
 * Send an audio/voice file natively.
 */
suspend fun BasePlatformAdapter.sendVoice(chatId: String, audioPath: String, caption: String = "", replyTo: String? = null): SendResult {
    return send(chatId, "[Voice: $audioPath]", replyTo = replyTo)
}

/**
 * Send a video natively.
 */
suspend fun BasePlatformAdapter.sendVideo(chatId: String, videoPath: String, caption: String = "", replyTo: String? = null): SendResult {
    return send(chatId, "[Video: $videoPath]", replyTo = replyTo)
}

/**
 * Send an animation/GIF natively.
 */
suspend fun BasePlatformAdapter.sendAnimation(chatId: String, animationUrl: String, caption: String = "", replyTo: String? = null): SendResult {
    return send(chatId, "[Animation: $animationUrl]", replyTo = replyTo)
}

/**
 * Send a local image file natively.
 */
suspend fun BasePlatformAdapter.sendImageFile(chatId: String, imagePath: String, caption: String = "", replyTo: String? = null): SendResult {
    return send(chatId, "[Image: $imagePath]", replyTo = replyTo)
}

/**
 * Stop a persistent typing indicator.
 */
fun BasePlatformAdapter.stopTyping(chatId: String) {
    // Override in platform-specific adapters
}

/**
 * Check if a URL points to an animated GIF.
 */
fun isAnimationUrl(url: String): Boolean {
    return url.endsWith(".gif", ignoreCase = true) ||
           url.contains("animated", ignoreCase = true) ||
           url.contains("tenor.com", ignoreCase = true) ||
           url.contains("giphy.com", ignoreCase = true)
}

/**
 * Send message with automatic retry for transient errors.
 */
suspend fun BasePlatformAdapter.sendWithRetry(
    chatId: String,
    content: String,
    replyTo: String? = null,
    metadata: Map<String, Any?> = emptyMap(),
    maxRetries: Int = 3,
    baseDelayMs: Long = 1000): SendResult {
    var lastError: Exception? = null
    for (attempt in 0 until maxRetries) {
        try {
            return send(chatId, content, replyTo)
        } catch (e: Exception) {
            lastError = e
            if (!isRetryableError(e.message ?: "")) break
            if (attempt < maxRetries - 1) {
                kotlinx.coroutines.delay(baseDelayMs * (attempt + 1))
            }
        }
    }
    throw lastError ?: RuntimeException("sendWithRetry failed")
}

/**
 * Check if an error string looks like a transient network failure.
 */
fun isRetryableError(error: String): Boolean {
    val lower = error.lowercase()
    return "timeout" in lower || "connection" in lower || "rate limit" in lower ||
           "429" in lower || "503" in lower || "502" in lower || "500" in lower
}

/**
 * Check if an error string indicates a timeout.
 */
fun isTimeoutError(error: String): Boolean {
    val lower = error.lowercase()
    return "timeout" in lower || "read timed out" in lower || "connect timed out" in lower
}

/**
 * Merge a new caption into existing text, avoiding duplicates.
 */
fun mergeCaption(existingText: String, newText: String): String {
    if (newText.isBlank()) return existingText
    if (existingText.isBlank()) return newText
    if (existingText.contains(newText, ignoreCase = true)) return existingText
    return "$existingText\n\n$newText"
}

/**
 * Truncate a long message into chunks, preserving code block boundaries.
 */
fun truncateMessage(
    content: String,
    maxLength: Int = 4096,
    lenFn: (String) -> Int = { it.length }): List<String> {
    if (lenFn(content) <= maxLength) return listOf(content)
    val chunks = mutableListOf<String>()
    var remaining = content
    while (lenFn(remaining) > maxLength) {
        // Find a good split point
        var splitAt = maxLength
        // Try to split at newline
        val newlineIdx = remaining.lastIndexOf('\n', maxLength)
        if (newlineIdx > maxLength / 2) {
            splitAt = newlineIdx + 1
        } else {
            // Try to split at space
            val spaceIdx = remaining.lastIndexOf(' ', maxLength)
            if (spaceIdx > maxLength / 2) {
                splitAt = spaceIdx + 1
            }
        }
        chunks.add(remaining.substring(0, splitAt))
        remaining = remaining.substring(splitAt)
    }
    if (remaining.isNotEmpty()) chunks.add(remaining)
    return chunks
}

/**
 * Format a message for the platform (identity in base).
 */
fun formatMessage(content: String): String = content

/**
 * Extract image URLs from markdown and HTML in a response.
 */
fun extractImages(content: String): List<String> {
    val urls = mutableListOf<String>()
    // Markdown images: ![alt](url)
    val mdPattern = Regex("""!\[.*?]\((https?://[^)]+)\)""")
    mdPattern.findAll(content).forEach { urls.add(it.groupValues[1]) }
    // HTML images: <img src="url">
    val htmlPattern = Regex("""<img[^>]+src="(https?://[^"]+)"[^>]*>""")
    htmlPattern.findAll(content).forEach { urls.add(it.groupValues[1]) }
    // Bare URLs that look like images
    val urlPattern = Regex("""https?://\S+\.(?:png|jpg|jpeg|gif|webp|svg)""")
    urlPattern.findAll(content).forEach { urls.add(it.value) }
    return urls.distinct()
}

/**
 * Extract MEDIA:<path> tags and [[audio_as_voice]] directives.
 */
fun extractMedia(content: String): Triple<String, List<String>, Boolean> {
    val mediaPaths = mutableListOf<String>()
    var isVoice = false
    var text = content

    // Extract MEDIA:<path> tags
    val mediaPattern = Regex("""MEDIA:(\S+)""")
    mediaPattern.findAll(content).forEach {
        mediaPaths.add(it.groupValues[1])
    }
    text = text.replace(mediaPattern, "")

    // Check for [[audio_as_voice]]
    if ("[[audio_as_voice]]" in text) {
        isVoice = true
        text = text.replace("[[audio_as_voice]]", "")
    }

    return Triple(text.trim(), mediaPaths, isVoice)
}

/**
 * Detect bare local file paths in response text.
 */
fun extractLocalFiles(content: String): List<String> {
    val paths = mutableListOf<String>()
    // Match /path/to/file or ~/path or relative paths
    val pattern = Regex("""(?:^|\s)([~/][\w\-./]+(?:\.[a-zA-Z0-9]+))(?:\s|$)""")
    pattern.findAll(content).forEach {
        paths.add(it.groupValues[1])
    }
    return paths.distinct()
}

/**
 * Random delay in seconds for human-like response pacing.
 */
fun getHumanDelay(): Double {
    return kotlin.random.Random.nextDouble(0.5, 2.0)
}

/**
 * Check if there's a pending interrupt for a session.
 */
fun hasPendingInterrupt(sessionKey: String): Boolean {
    return _pendingInterrupts.containsKey(sessionKey)
}

/** Pending interrupts storage. */
private val _pendingInterrupts = java.util.concurrent.ConcurrentHashMap<String, String>()

/**
 * Get and clear any pending message for a session.
 */
fun getPendingMessage(sessionKey: String): String? {
    return _pendingInterrupts.remove(sessionKey)
}

/**
 * Format duration in human-readable form.
 */
fun formatDuration(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        seconds < 86400 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        else -> "${seconds / 86400}d ${(seconds % 86400) / 3600}h"
    }
}

/**
 * Continuously send typing indicator until cancelled (2s refresh).
 * Paused chats are skipped via the adapter's [BasePlatformAdapter.pauseTypingForChat].
 */
suspend fun BasePlatformAdapter._keepTyping(
    chatId: String,
    intervalMs: Long = 2000L,
    metadata: JSONObject? = null) {
    try {
        while (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive == true) {
            try { sendTyping(chatId, metadata) } catch (_: Exception) {}
            kotlinx.coroutines.delay(intervalMs)
        }
    } catch (_: kotlinx.coroutines.CancellationException) {
        // Normal — parent cancelled the job
    } finally {
        try { stopTyping(chatId) } catch (_: Exception) {}
    }
}

/**
 * Run a lifecycle hook without letting failures break message flow.
 *
 * Catches all exceptions so a failed hook never propagates up to
 * the message-processing pipeline.
 */
suspend fun BasePlatformAdapter._runProcessingHook(hookName: String) {
    try {
        // Subclasses override lifecycle hooks; base does nothing.
    } catch (e: Exception) {
        Log.w("BasePlatformAdapter", "[$name] $hookName hook failed: ${e.message}")
    }
}

/**
 * Background task that actually processes the message — ported from
 * _process_message_background in gateway/platforms/base.py.
 *
 * Sends a continuous typing indicator, calls the message handler, sends
 * the text response with retry, and sends any local files / media detected
 * in the response.  Catches all errors and reports them to the user.
 */
suspend fun BasePlatformAdapter._processMessageBackground(
    event: MessageEvent,
    sessionKey: String) {
    // Use structured concurrency — typing job is a child of this scope
    kotlinx.coroutines.coroutineScope {
        val typingJob = launch { _keepTyping(event.source.chatId) }

        try {
        // ── Call the message handler ──
        val handler = messageHandler
        if (handler == null) {
            Log.w("BasePlatformAdapter", "[$name] No handler for session=$sessionKey")
            return@coroutineScope
        }
        handler(event)

        // ── Send text response ──
        val responseText = event.text
        if (responseText.isNotEmpty()) {
            sendWithRetry(
                chatId = event.source.chatId,
                content = responseText,
                replyTo = event.message_id.ifEmpty { null })
        }

        // ── Extract & send images from response ──
        val imageUrls = extractImages(responseText)
        for (imgUrl in imageUrls) {
            try {
                if (isAnimationUrl(imgUrl)) {
                    sendAnimation(event.source.chatId, imgUrl)
                } else {
                    sendImage(event.source.chatId, imgUrl)
                }
            } catch (e: Exception) {
                Log.w("BasePlatformAdapter", "[$name] Image send failed: ${e.message}")
            }
        }

        // ── Extract & send media files ──
        val (cleanText, mediaPaths, isVoice) = extractMedia(responseText)
        val audioExts = setOf(".ogg", ".opus", ".mp3", ".wav", ".m4a", ".aac")
        val videoExts = setOf(".mp4", ".mov", ".avi", ".mkv", ".webm")
        val imageExts = setOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp")

        for (mediaPath in mediaPaths) {
            try {
                val ext = ".${mediaPath.substringAfterLast('.', "").lowercase()}"
                when {
                    isVoice || ext in audioExts -> sendVoice(event.source.chatId, mediaPath)
                    ext in videoExts -> sendVideo(event.source.chatId, mediaPath)
                    ext in imageExts -> sendImageFile(event.source.chatId, mediaPath)
                    else -> sendDocument(event.source.chatId, mediaPath)
                }
            } catch (e: Exception) {
                Log.w("BasePlatformAdapter", "[$name] Media send failed: ${e.message}")
            }
        }

        // ── Extract & send bare local file paths ──
        val localFiles = extractLocalFiles(responseText)
        for (filePath in localFiles) {
            try {
                val ext = ".${filePath.substringAfterLast('.', "").lowercase()}"
                when {
                    ext in imageExts -> sendImageFile(event.source.chatId, filePath)
                    ext in videoExts -> sendVideo(event.source.chatId, filePath)
                    else -> sendDocument(event.source.chatId, filePath)
                }
            } catch (e: Exception) {
                Log.w("BasePlatformAdapter", "[$name] File send failed: ${e.message}")
            }
        }

    } catch (e: Exception) {
        Log.e("BasePlatformAdapter", "[$name] Processing error: ${e.message}", e)
        try {
            send(
                chatId = event.source.chatId,
                content = "Error: ${e.javaClass.simpleName} — ${e.message ?: "unknown"}\nUse /reset to start fresh.")
        } catch (_: Exception) { /* best-effort error delivery */ }
    } finally {
        typingJob.cancel()
        try { typingJob.join() } catch (_: Exception) {}
    }
    } // coroutineScope
}

/**
 * Lifecycle hook: called when background processing starts.
 */
fun onProcessingStart(sessionKey: String) {
    Log.d("BasePlatformAdapter", "Processing started: session=$sessionKey")
}

/**
 * Lifecycle hook: called when background processing completes.
 */
fun onProcessingComplete(sessionKey: String, outcome: String) {
    Log.d("BasePlatformAdapter", "Processing complete: session=$sessionKey outcome=$outcome")
}

// ── Constants ported from gateway/platforms/base.py ────────────────────────

const val GATEWAY_SECRET_CAPTURE_UNSUPPORTED_MESSAGE: String =
    "Secure secret entry is not supported over messaging. " +
        "Load this skill in the local CLI to be prompted, or add the key to ~/.hermes/.env manually."

/** Image cache directory (lazy — created on demand). */
val IMAGE_CACHE_DIR: File = File(System.getProperty("java.io.tmpdir") ?: "/tmp", "hermes_image_cache")

/** Audio cache directory. */
val AUDIO_CACHE_DIR: File = File(System.getProperty("java.io.tmpdir") ?: "/tmp", "hermes_audio_cache")

/** Video cache directory. */
val VIDEO_CACHE_DIR: File = File(System.getProperty("java.io.tmpdir") ?: "/tmp", "hermes_video_cache")

/** Document cache directory. */
val DOCUMENT_CACHE_DIR: File = File(System.getProperty("java.io.tmpdir") ?: "/tmp", "hermes_document_cache")

/**
 * Error substrings indicating a transient connection failure worth retrying.
 *
 * "timeout" / "timed out" are intentionally excluded because a read/write
 * timeout on a non-idempotent call may have reached the server.
 */
val _RETRYABLE_ERROR_PATTERNS: Array<String> = arrayOf(
    "connecterror",
    "connectionerror",
    "connectionreset",
    "connectionrefused",
    "connecttimeout",
    "network",
    "broken pipe",
    "remotedisconnected",
    "eoferror"
)

// ── Module-level helpers ported from gateway/platforms/base.py ─────────────

/**
 * Return the largest codepoint offset *n* such that `lenFn(s[:n]) <= budget`.
 *
 * Used by truncateMessage when lenFn measures length in units different from
 * Python codepoints (e.g. UTF-16 code units). Falls back to binary search.
 */
fun _customUnitToCp(s: String, budget: Int, lenFn: (String) -> Int): Int {
    if (lenFn(s) <= budget) return s.length
    var lo = 0
    var hi = s.length
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (lenFn(s.substring(0, mid)) <= budget) lo = mid else hi = mid - 1
    }
    return lo
}

/**
 * Read the macOS system HTTP(S) proxy via `scutil --proxy`.
 *
 * Android-side stub — always returns null (Android doesn't have scutil and
 * gateway traffic uses Android's own proxy chain).
 */
fun _detectMacosSystemProxy(): String? = null

/**
 * Build kwargs for a Discord-style bot client with proxy.
 *
 * Returns a map suitable for spreading into SDK constructors:
 *  - SOCKS URL  → {"connector": proxyConnector}
 *  - HTTP URL   → {"proxy": url}
 *  - null       → {}
 *
 * Android-side: SDK connectors aren't used, so this just forwards the URL.
 */
fun proxyKwargsForBot(proxyUrl: String?): Map<String, Any?> {
    if (proxyUrl.isNullOrEmpty()) return emptyMap()
    return if (proxyUrl.lowercase().startsWith("socks")) {
        mapOf("socks_proxy" to proxyUrl)
    } else {
        mapOf("proxy" to proxyUrl)
    }
}

/**
 * Build (sessionKwargs, requestKwargs) for aiohttp-style clients with proxy.
 *
 * Android-side: returns simplified map pair; real HTTP client layer handles
 * the proxy plumbing.
 */
fun proxyKwargsForAiohttp(proxyUrl: String?): Pair<Map<String, Any?>, Map<String, Any?>> {
    if (proxyUrl.isNullOrEmpty()) return emptyMap<String, Any?>() to emptyMap<String, Any?>()
    return if (proxyUrl.lowercase().startsWith("socks")) {
        mapOf<String, Any?>("socks_proxy" to proxyUrl) to emptyMap()
    } else {
        emptyMap<String, Any?>() to mapOf("proxy" to proxyUrl)
    }
}

/**
 * Re-validate each redirect target to prevent redirect-based SSRF.
 *
 * Python side: httpx response event hook that inspects `response.next_request`
 * and raises if it points to a private/internal address.
 *
 * Android-side stub — HTTP redirects are validated in the tool layer.
 */
suspend fun _ssrfRedirectGuard(response: Any?) {
    // No-op on Android; redirect guards are applied by UrlSafety at tool entry.
}

/** Return the video cache directory, creating it if it doesn't exist. */
fun getVideoCacheDir(): File {
    if (!VIDEO_CACHE_DIR.exists()) VIDEO_CACHE_DIR.mkdirs()
    return VIDEO_CACHE_DIR
}

/** Save raw video bytes to the cache and return the absolute file path. */
fun cacheVideoFromBytes(data: ByteArray, ext: String = ".mp4"): String {
    val dir = getVideoCacheDir()
    val filename = "video_${java.util.UUID.randomUUID().toString().replace("-", "").take(12)}$ext"
    val target = File(dir, filename)
    target.writeBytes(data)
    return target.absolutePath
}

/**
 * Store or merge a pending message event for a session.
 *
 * Photo bursts/albums often arrive as multiple near-simultaneous PHOTO
 * events; merge those into the existing queued event so the next turn sees
 * the whole burst. When `mergeText=true`, rapid follow-up TEXT events are
 * appended instead of replacing the pending turn.
 */
fun mergePendingMessageEvent(
    pendingMessages: MutableMap<String, MessageEvent>,
    sessionKey: String,
    event: MessageEvent,
    mergeText: Boolean = false) {
    val existing = pendingMessages[sessionKey]
    if (existing != null) {
        val existingIsPhoto = existing.messageType == MessageType.PHOTO
        val incomingIsPhoto = event.messageType == MessageType.PHOTO
        val existingHasMedia = existing.mediaUrls.isNotEmpty()
        val incomingHasMedia = event.mediaUrls.isNotEmpty()

        if (existingIsPhoto && incomingIsPhoto) {
            val mergedText = if (event.text.isNotEmpty())
                if (existing.text.isNotEmpty()) "${existing.text}\n${event.text}" else event.text
            else existing.text
            pendingMessages[sessionKey] = existing.copy(
                text = mergedText,
                mediaUrls = existing.mediaUrls + event.mediaUrls,
                mediaTypes = existing.mediaTypes + event.mediaTypes)
            return
        }

        if (existingHasMedia || incomingHasMedia) {
            val mergedText = if (event.text.isNotEmpty())
                if (existing.text.isNotEmpty()) "${existing.text}\n${event.text}" else event.text
            else existing.text
            val mergedType = if (existingIsPhoto || incomingIsPhoto) MessageType.PHOTO else existing.messageType
            val mergedUrls = if (incomingHasMedia) existing.mediaUrls + event.mediaUrls else existing.mediaUrls
            val mergedMimeTypes = if (incomingHasMedia) existing.mediaTypes + event.mediaTypes else existing.mediaTypes
            pendingMessages[sessionKey] = existing.copy(
                text = mergedText,
                messageType = mergedType,
                mediaUrls = mergedUrls,
                mediaTypes = mergedMimeTypes)
            return
        }

        if (mergeText && existing.messageType == MessageType.TEXT && event.messageType == MessageType.TEXT) {
            if (event.text.isNotEmpty()) {
                val mergedText = if (existing.text.isNotEmpty()) "${existing.text}\n${event.text}" else event.text
                pendingMessages[sessionKey] = existing.copy(text = mergedText)
            }
            return
        }
    }

    pendingMessages[sessionKey] = event
}

/**
 * Resolve a per-channel ephemeral prompt from platform config.
 *
 * Looks up `channel_prompts` in the adapter's `config.extra` dict.
 * Prefers an exact match on *channelId*; falls back to *parentId* (useful
 * for forum threads / child channels inheriting a parent prompt).
 */
fun resolveChannelPrompt(
    configExtra: Map<String, Any?>,
    channelId: String,
    parentId: String? = null): String? {
    val prompts = configExtra["channel_prompts"] as? Map<*, *> ?: return null
    for (key in listOf(channelId, parentId)) {
        if (key.isNullOrEmpty()) continue
        val raw = prompts[key] ?: continue
        val prompt = raw.toString().trim()
        if (prompt.isNotEmpty()) return prompt
    }
    return null
}

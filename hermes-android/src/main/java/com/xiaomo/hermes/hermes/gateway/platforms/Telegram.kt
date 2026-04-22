package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * Telegram platform adapter.
 *
 * Uses the Telegram Bot API for receiving messages via polling/WebSocket
 * and sending responses back. Supports text, images, documents, audio,
 * stickers, and more.
 *
 * Ported from gateway/platforms/telegram.py
 *
 * Note: On Android, we use the Telegram Bot REST API directly instead of
 * the python-telegram-bot library.
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Telegram adapter — bridges between the gateway and the Telegram Bot API.
 *
 * Features:
 * - Long polling for receiving messages
 * - Text, image, document, audio, sticker, location, contact sending
 * - Reply-to-message support
 * - Typing indicators
 * - Reactions
 * - Edit/delete messages
 * - Inline keyboards (basic)
 * - MarkdownV2 formatting
 */
class TelegramAdapter(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.TELEGRAM) {
    companion object {
        private const val _TAG = "Telegram"
        const val MAX_MESSAGE_LENGTH = 4096
        const val API_BASE = "https://api.telegram.org"

        /** Maximum UTF-16 code units for captions. */
        const val MAX_CAPTION_UTF16 = 1024

        /** Polling timeout in seconds. */
        const val POLLING_TIMEOUT = 30

        /** Maximum number of concurrent downloads. */
        const val MAX_CONCURRENT_DOWNLOADS = 3
    }

    private val _token: String = (config.token ?: "").ifEmpty {
        System.getenv("TELEGRAM_BOT_TOKEN") ?: ""
    }

    /** Whether to use polling (true) or webhooks (false). */
    private val _usePolling: Boolean = config.extraBool("use_polling", true)

    /** Webhook URL (if not using polling). */
    private val _webhookUrl: String = config.extra("webhook_url", "")

    /** Whether to drop pending updates on start. */
    private val _dropPendingUpdates: Boolean = config.extraBool("drop_pending_updates", true)

    /** Whether to parse markdown. */
    private val _parseMarkdown: Boolean = config.extraBool("parse_markdown", true)

    /** Whether to allow sending to groups. */
    private val _allowGroups: Boolean = config.extraBool("allow_groups", true)

    /** Allowed group IDs (empty = all groups). */
    private val _allowedGroups: Set<String> = config.extra("allowed_groups")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet() ?: emptySet()

    private val _httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(POLLING_TIMEOUT.toLong() + 10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _dedup: MessageDeduplicator = MessageDeduplicator()
    private val _chatQueues: ConcurrentHashMap<String, Channel<MessageEvent>> = ConcurrentHashMap()
    private val _chatJobs: ConcurrentHashMap<String, Job> = ConcurrentHashMap()

    /** Current update offset for polling. */
    private val _updateOffset = AtomicLong(0)

    /** Bot user info. */
    private var _botUserId: Long = 0
    private var _botUserName: String = ""

    /** Polling job. */
    private var _pollingJob: Job? = null

    /** File cache directory. */
    private val _fileCacheDir: File = File(context.cacheDir, "telegram_files")

    init {
        _fileCacheDir.mkdirs()
    }

    override val isConnected: AtomicBoolean
        get() = AtomicBoolean(_pollingJob?.isActive == true)

    override suspend fun connect(): Boolean {
        if (_token.isEmpty()) {
            Log.e(_TAG, "TELEGRAM_BOT_TOKEN not set")
            return false
        }

        // Get bot info
        try {
            val request = Request.Builder()
                .url("$API_BASE/bot$_token/getMe")
                .get()
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(_TAG, "Failed to get bot info: HTTP ${resp.code}")
                    return false
                }
                val data = JSONObject(resp.body!!.string())
                if (!data.optBoolean("ok", false)) {
                    Log.e(_TAG, "getMe failed: ${data.optString("description")}")
                    return false
                }
                val result = data.getJSONObject("result")
                _botUserId = result.getLong("id")
                _botUserName = result.getString("username")
                Log.i(_TAG, "Bot connected: @$_botUserName ($_botUserId)")
            }
        } catch (e: Exception) {
            Log.e(_TAG, "Connection failed: ${e.message}")
            return false
        }

        // Drop pending updates if configured
        if (_dropPendingUpdates) {
            _dropPendingUpdates()
        }

        // Start polling
        if (_usePolling) {
            _startPolling()
        } else {
            _setWebhook()
        }

        markConnected()
        return true
    }

    override suspend fun disconnect() {
        _pollingJob?.cancel()
        _pollingJob = null
        _chatJobs.values.forEach { it.cancel() }
        _chatJobs.clear()
        _chatQueues.clear()

        if (!_usePolling && _webhookUrl.isNotEmpty()) {
            _deleteWebhook()
        }

        markDisconnected()
        Log.i(_TAG, "Disconnected")
    }

    // ------------------------------------------------------------------
    // Polling
    // ------------------------------------------------------------------

    /**
     * Start long polling for updates.
     */
    private fun _startPolling() {
        _pollingJob = scope.launch {
            Log.i(_TAG, "Starting long polling...")
            while (isActive) {
                try {
                    _pollUpdates()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(_TAG, "Polling error: ${e.message}")
                    delay(5000) // Wait before retry
                }
            }
        }
    }

    /**
     * Poll for updates.
     */
    private suspend fun _pollUpdates() = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$API_BASE/bot$_token/getUpdates")
            append("?timeout=$POLLING_TIMEOUT")
            if (_updateOffset.get() > 0) {
                append("&offset=${_updateOffset.get()}")
            }
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        _httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(_TAG, "getUpdates HTTP ${resp.code}")
                return@withContext
            }

            val data = JSONObject(resp.body!!.string())
            if (!data.optBoolean("ok", false)) {
                Log.w(_TAG, "getUpdates failed: ${data.optString("description")}")
                return@withContext
            }

            val results = data.getJSONArray("result")
            for (i in 0 until results.length()) {
                val update = results.getJSONObject(i)
                _updateOffset.set(update.getLong("update_id") + 1)
                _handleUpdate(update)
            }
        }
    }

    /**
     * Handle a single update.
     */
    private suspend fun _handleUpdate(update: JSONObject) {
        try {
            when {
                update.has("message") -> _handleMessage(update.getJSONObject("message"))
                update.has("edited_message") -> _handleEditedMessage(update.getJSONObject("edited_message"))
                update.has("channel_post") -> _handleChannelPost(update.getJSONObject("channel_post"))
                update.has("callback_query") -> _handleCallbackQuery(update.getJSONObject("callback_query"))
                update.has("reaction") -> _handleReaction(update.getJSONArray("reaction"))
                else -> Log.d(_TAG, "Unknown update type")
            }
        } catch (e: Exception) {
            Log.w(_TAG, "Error handling update: ${e.message}")
        }
    }

    /**
     * Handle an incoming message.
     */
    private suspend fun _handleMessage(message: JSONObject) {
        val messageId = message.getLong("message_id").toString()
        val chat = message.getJSONObject("chat")
        val chatId = chat.getLong("id").toString()
        val chatType = chat.getString("type")
        val from = message.optJSONObject("from")
        val userId = from?.optLong("id", 0)?.toString() ?: ""
        val userName = from?.optString("username", "") ?: ""
        val firstName = from?.optString("first_name", "") ?: ""

        // Dedup
        if (_dedup.isDuplicate(messageId)) return

        // Determine message type and text
        val (text, messageType, mediaUrls, mediaTypes) = _parseTelegramMessage(message)

        // Build source
        val source = buildSource(
            chatId = chatId,
            chatName = chat.optString("title", chat.optString("first_name", "")),
            chatType = when (chatType) {
                "private" -> "dm"
                "group", "supergroup" -> "group"
                "channel" -> "channel"
                else -> "dm"
            },
            userId = userId,
            userName = if (firstName.isNotEmpty()) "$firstName (@$userName)" else userName)

        // Build event
        val event = MessageEvent(
            text = text,
            messageType = messageType,
            source = source,
            message_id = messageId,
            replyToMessageId = message.optJSONObject("reply_to_message")
                ?.getLong("message_id")?.toString(),
            mediaUrls = mediaUrls,
            mediaTypes = mediaTypes,
            stickerSet = message.optJSONObject("sticker")?.optJSONObject("set_name")?.toString(),
            stickerFileId = message.optJSONObject("sticker")?.optString("file_id"),
            latitude = message.optJSONObject("location")?.optDouble("latitude"),
            longitude = message.optJSONObject("location")?.optDouble("longitude"),
            contactName = message.optJSONObject("contact")?.optString("first_name"),
            contactPhone = message.optJSONObject("contact")?.optString("phone_number"))

        _queueForProcessing(chatId, event)
    }

    /**
     * Parse a Telegram message into text, type, and media.
     */
    private fun _parseTelegramMessage(message: JSONObject): Quadruple<String, MessageType, List<String>, List<String>> {
        return when {
            message.has("text") -> {
                val text = message.getString("text")
                if (text.startsWith("/")) {
                    Quadruple(text, MessageType.COMMAND, emptyList(), emptyList())
                } else {
                    Quadruple(text, MessageType.TEXT, emptyList(), emptyList())
                }
            }
            message.has("photo") -> {
                val photos = message.getJSONArray("photo")
                val largest = photos.getJSONObject(photos.length() - 1)
                val fileId = largest.getString("file_id")
                val caption = message.optString("caption", "")
                Quadruple(caption, MessageType.PHOTO, listOf(fileId), listOf("image/jpeg"))
            }
            message.has("document") -> {
                val doc = message.getJSONObject("document")
                val fileId = doc.getString("file_id")
                val fileName = doc.optString("file_name", "document")
                val mimeType = doc.optString("mime_type", "application/octet-stream")
                val caption = message.optString("caption", "")
                Quadruple(caption, MessageType.DOCUMENT, listOf(fileId), listOf(mimeType))
            }
            message.has("audio") -> {
                val audio = message.getJSONObject("audio")
                val fileId = audio.getString("file_id")
                val caption = message.optString("caption", "")
                Quadruple(caption, MessageType.AUDIO, listOf(fileId), listOf("audio/mpeg"))
            }
            message.has("voice") -> {
                val voice = message.getJSONObject("voice")
                val fileId = voice.getString("file_id")
                Quadruple("", MessageType.AUDIO, listOf(fileId), listOf("audio/ogg"))
            }
            message.has("video") -> {
                val video = message.getJSONObject("video")
                val fileId = video.getString("file_id")
                val caption = message.optString("caption", "")
                Quadruple(caption, MessageType.VIDEO, listOf(fileId), listOf("video/mp4"))
            }
            message.has("sticker") -> {
                val sticker = message.getJSONObject("sticker")
                val emoji = sticker.optString("emoji", "")
                val fileId = sticker.getString("file_id")
                Quadruple(emoji, MessageType.STICKER, listOf(fileId), listOf("image/webp"))
            }
            message.has("location") -> {
                val loc = message.getJSONObject("location")
                val lat = loc.getDouble("latitude")
                val lon = loc.getDouble("longitude")
                Quadruple("Location: $lat, $lon", MessageType.LOCATION, emptyList(), emptyList())
            }
            message.has("contact") -> {
                val contact = message.getJSONObject("contact")
                val name = contact.getString("first_name")
                val phone = contact.getString("phone_number")
                Quadruple("Contact: $name ($phone)", MessageType.CONTACT, emptyList(), emptyList())
            }
            message.has("poll") -> {
                val poll = message.getJSONObject("poll")
                val question = poll.getString("question")
                val options = poll.getJSONArray("options")
                val optionTexts = (0 until options.length()).map { options.getJSONObject(it).getString("text") }
                Quadruple("Poll: $question", MessageType.POLL, emptyList(), emptyList())
            }
            else -> {
                Quadruple("[Unsupported message]", MessageType.UNKNOWN, emptyList(), emptyList())
            }
        }
    }

    /**
     * Handle an edited message.
     */
    private suspend fun _handleEditedMessage(message: JSONObject) {
        val messageId = message.getLong("message_id").toString()
        val chat = message.getJSONObject("chat")
        val chatId = chat.getLong("id").toString()
        val from = message.optJSONObject("from")
        val userId = from?.optLong("id", 0)?.toString() ?: ""
        val userName = from?.optString("username", "") ?: ""

        val text = message.optString("text", "[edited]")

        val source = buildSource(
            chatId = chatId,
            chatName = chat.optString("title", ""),
            chatType = if (chat.getString("type") == "private") "dm" else "group",
            userId = userId,
            userName = userName)

        val event = MessageEvent(
            text = text,
            messageType = MessageType.EDITED,
            source = source,
            message_id = messageId,
            isEdited = true)

        _queueForProcessing(chatId, event)
    }

    /**
     * Handle a channel post.
     */
    private suspend fun _handleChannelPost(message: JSONObject) {
        val chat = message.getJSONObject("chat")
        val chatId = chat.getLong("id").toString()
        val text = message.optString("text", message.optString("caption", "[channel post]"))

        val source = buildSource(
            chatId = chatId,
            chatName = chat.optString("title", ""),
            chatType = "channel",
            userId = "channel",
            userName = "Channel")

        val event = MessageEvent(
            text = text,
            messageType = MessageType.TEXT,
            source = source,
            message_id = message.getLong("message_id").toString())

        _queueForProcessing(chatId, event)
    }

    /**
     * Handle a callback query (inline keyboard button press).
     */
    private suspend fun _handleCallbackQuery(query: JSONObject) {
        val data = query.optString("data", "")
        val message = query.optJSONObject("message")
        val chatId = message?.getJSONObject("chat")?.getLong("id")?.toString() ?: return
        val from = query.getJSONObject("from")
        val userId = from.getLong("id").toString()

        val source = buildSource(
            chatId = chatId,
            chatType = "group",
            userId = userId,
            userName = from.optString("username", ""))

        val event = MessageEvent(
            text = "/callback $data",
            messageType = MessageType.COMMAND,
            source = source)

        _queueForProcessing(chatId, event)
    }

    /**
     * Handle a reaction event.
     */
    private suspend fun _handleReaction(reactions: org.json.JSONArray) {
        // Telegram reactions are not standard — skip for now
    }

    /**
     * Queue a message for serial per-chat processing.
     */
    private fun _queueForProcessing(chatId: String, event: MessageEvent) {
        val queue = _chatQueues.getOrPut(chatId) { Channel(Channel.UNLIMITED) }
        queue.trySend(event)

        if (_chatJobs[chatId]?.isActive != true) {
            _chatJobs[chatId] = scope.launch {
                _processChatQueue(chatId, queue)
            }
        }
    }

    private suspend fun _processChatQueue(chatId: String, channel: Channel<MessageEvent>) {
        for (event in channel) {
            try {
                handleMessage(event)
            } catch (e: Exception) {
                Log.e(_TAG, "Error processing message in chat $chatId: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------------
    // Outbound messaging
    // ------------------------------------------------------------------

    override suspend fun send(
        chatId: String,
        content: String,
        replyTo: String?,
        metadata: JSONObject?): SendResult = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("text", content.take(MAX_MESSAGE_LENGTH))
                if (_parseMarkdown) put("parse_mode", "Markdown")
                if (replyTo != null) put("reply_to_message_id", replyTo.toInt())
            }

            val body = payload.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$API_BASE/bot$_token/sendMessage")
                .post(body)
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string() ?: ""
                    Log.e(_TAG, "Send failed: HTTP ${resp.code}: $errorBody")
                    return@withContext SendResult(success = false, error = "HTTP ${resp.code}")
                }

                val data = JSONObject(resp.body!!.string())
                if (!data.optBoolean("ok", false)) {
                    return@withContext SendResult(success = false, error = data.optString("description"))
                }

                val messageId = data.getJSONObject("result").getString("message_id")
                SendResult(success = true, messageId = messageId)
            }
        } catch (e: Exception) {
            SendResult(success = false, error = e.message)
        }
    }

    override suspend fun sendImage(
        chatId: String,
        imageUrl: String,
        caption: String?,
        replyTo: String?): SendResult = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("photo", imageUrl)
                if (caption != null) put("caption", caption.take(MAX_CAPTION_UTF16))
                if (_parseMarkdown) put("parse_mode", "Markdown")
                if (replyTo != null) put("reply_to_message_id", replyTo.toInt())
            }

            val body = payload.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$API_BASE/bot$_token/sendPhoto")
                .post(body)
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext SendResult(success = false, error = "HTTP ${resp.code}")
                val data = JSONObject(resp.body!!.string())
                if (!data.optBoolean("ok", false)) return@withContext SendResult(success = false, error = data.optString("description"))
                val messageId = data.getJSONObject("result").getString("message_id")
                SendResult(success = true, messageId = messageId)
            }
        } catch (e: Exception) {
            SendResult(success = false, error = e.message)
        }
    }

    override suspend fun sendDocument(
        chatId: String,
        fileUrl: String,
        fileName: String?,
        caption: String?,
        replyTo: String?): SendResult = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("document", fileUrl)
                if (caption != null) put("caption", caption.take(MAX_CAPTION_UTF16))
                if (_parseMarkdown) put("parse_mode", "Markdown")
                if (replyTo != null) put("reply_to_message_id", replyTo.toInt())
            }

            val body = payload.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$API_BASE/bot$_token/sendDocument")
                .post(body)
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext SendResult(success = false, error = "HTTP ${resp.code}")
                val data = JSONObject(resp.body!!.string())
                if (!data.optBoolean("ok", false)) return@withContext SendResult(success = false, error = data.optString("description"))
                val messageId = data.getJSONObject("result").getString("message_id")
                SendResult(success = true, messageId = messageId)
            }
        } catch (e: Exception) {
            SendResult(success = false, error = e.message)
        }
    }

    override suspend fun sendTyping(chatId: String, metadata: JSONObject?) {
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("chat_id", chatId)
                    put("action", "typing")
                }

                val body = payload.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("$API_BASE/bot$_token/sendChatAction")
                    .post(body)
                    .build()

                _httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(_TAG, "Typing indicator failed: HTTP ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(_TAG, "Typing indicator error: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------------
    // Webhook management
    // ------------------------------------------------------------------

    private suspend fun _dropPendingUpdates() {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$API_BASE/bot$_token/getUpdates?offset=-1")
                    .get()
                    .build()

                _httpClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        Log.i(_TAG, "Pending updates dropped")
                    }
                }
            } catch (e: Exception) {
                Log.w(_TAG, "Failed to drop pending updates: ${e.message}")
            }
        }
    }

    private suspend fun _setWebhook() {
        if (_webhookUrl.isEmpty()) return
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("url", _webhookUrl)
                }

                val body = payload.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("$API_BASE/bot$_token/setWebhook")
                    .post(body)
                    .build()

                _httpClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        Log.i(_TAG, "Webhook set to $_webhookUrl")
                    } else {
                        Log.e(_TAG, "Failed to set webhook: HTTP ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(_TAG, "Failed to set webhook: ${e.message}")
            }
        }
    }

    private suspend fun _deleteWebhook() {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$API_BASE/bot$_token/deleteWebhook")
                    .get()
                    .build()

                _httpClient.newCall(request).execute()
                Log.i(_TAG, "Webhook deleted")
            } catch (e: Exception) {
                Log.w(_TAG, "Failed to delete webhook: ${e.message}")
            }
        }
    }

    /**
     * Get the bot username.
     */
    val botUserName: String get() = _botUserName

    /**
     * Get the bot user id.
     */
    val botUserId: Long get() = _botUserId
}

/**
 * Helper data class for returning four values.
 */
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D)

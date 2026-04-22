package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * Feishu/Lark platform adapter.
 *
 * Supports WebSocket long connection and Webhook transport for receiving
 * messages, and the Feishu IM API for sending messages.
 *
 * Ported from gateway/platforms/feishu.py
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
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Feishu event types that the adapter handles.
 */
enum class FeishuEventType(val value: String) {
    /** New message event. */
    MESSAGE("im.message.receive_v1"),
    /** Message read event. */
    MESSAGE_READ("im.message.message_read_v1"),
    /** Reaction event. */
    REACTION("im.message.reaction.created_v1"),
    /** Card action event. */
    CARD_ACTION("card.action.trigger"),
    /** Chat joined event. */
    CHAT_JOINED("im.chat.member.bot.added_v1"),
    /** Chat left event. */
    CHAT_LEFT("im.chat.member.bot.deleted_v1"),
    ;

    companion object {
        fun fromValue(value: String): FeishuEventType? =
            entries.firstOrNull { it.value == value }
    }
}

/**
 * Feishu message types.
 */
enum class FeishuMessageType(val value: String) {
    TEXT("text"),
    POST("post"),
    IMAGE("image"),
    FILE("file"),
    AUDIO("audio"),
    MEDIA("media"),
    STICKER("sticker"),
    SHARE_CHAT("share_chat"),
    SHARE_USER("share_user"),
    INTERACTIVE("interactive"),
    SYSTEM("system"),
    ;

    companion object {
        fun fromValue(value: String): FeishuMessageType? =
            entries.firstOrNull { it.value == value }
    }
}

/**
 * Feishu adapter — bridges between the gateway and the Feishu/Lark API.
 *
 * Features:
 * - WebSocket long connection for real-time message reception
 * - Webhook transport as fallback
 * - Direct-message and group @mention-gated text receive/send
 * - Inbound image/file/audio/media caching
 * - Gateway allowlist integration via config
 * - Persistent dedup state across restarts
 * - Per-chat serial message processing
 * - Persistent ACK emoji reaction on inbound messages
 * - Reaction events routed as synthetic text events
 * - Interactive card button-click events routed as synthetic COMMAND events
 */
class Feishu(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.FEISHU) {
    companion object {
        private const val TAG = "Feishu"

        /** Maximum message length for Feishu. */
        const val MAX_MESSAGE_LENGTH = 30000

        /** Maximum number of retries for API calls. */
        const val MAX_RETRIES = 3

        /** Base delay for exponential backoff (milliseconds). */
        const val BASE_BACKOFF_MS = 1000L

        /** WebSocket reconnect delay (seconds). */
        const val WS_RECONNECT_DELAY = 5.0

        /** Maximum WebSocket reconnect delay (seconds). */
        const val WS_MAX_RECONNECT_DELAY = 60.0

        /** Maximum number of dedup entries. */
        const val MAX_DEDUP_ENTRIES = 5000

        /** Maximum number of pending reactions. */
        const val MAX_PENDING_REACTIONS = 1000

        /** Maximum number of processed card actions. */
        const val MAX_CARD_ACTIONS = 1000
    }

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    private val _appId: String = config.extra("app_id") ?: System.getenv("FEISHU_APP_ID") ?: ""
    private val _appSecret: String = config.extra("app_secret") ?: System.getenv("FEISHU_APP_SECRET") ?: ""
    private val _verificationToken: String = config.extra("verification_token") ?: System.getenv("FEISHU_VERIFICATION_TOKEN") ?: ""
    private val _encryptKey: String = config.extra("encrypt_key") ?: System.getenv("FEISHU_ENCRYPT_KEY") ?: ""

    /** Whether to use WebSocket (true) or Webhook (false). */
    private val _useWebSocket: Boolean = config.extraBool("use_websocket", true)

    /** WebSocket connection URL. */
    private val _wsUrl: String = config.extra("ws_url", "wss://open.feishu.cn/open-apis/ws/v2")

    /** Webhook server configuration. */
    private val _webhookHost: String = config.extra("webhook_host", "0.0.0.0")
    private val _webhookPort: Int = config.extraInt("webhook_port", 8645)
    private val _webhookPath: String = config.extra("webhook_path", "/feishu/webhook")

    /** Whether to require @mention in groups. */
    private val _requireMention: Boolean = config.extraBool("require_mention", true)

    /** Whether to send ACK reaction on inbound messages. */
    private val _sendAckReaction: Boolean = config.extraBool("send_ack_reaction", true)

    /** ACK reaction emoji (default: eyes 👀). */
    private val _ackEmoji: String = config.extra("ack_emoji", "ONLOOKER")

    /** Reply mode: "thread", "normal", "quote". */
    private val _replyMode: String = config.extra("reply_mode", "normal")

    /** Whether to auto-create threads for group messages. */
    private val _autoThread: Boolean = config.extraBool("auto_thread", false)

    /** Home channel (chat_id for system notifications). */
    private val _homeChatId: String = config.homeChannel?.chatId ?: ""

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    /** Feishu API client. */
    private val _httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Access token (tenant_access_token). */
    private var _accessToken: String = ""

    /** Access token expiry (epoch seconds). */
    private var _accessTokenExpiry: Long = 0L

    /** Bot user id (open_id). */
    private var _botUserId: String = ""

    /** Bot user name. */
    private var _botUserName: String = ""

    /** WebSocket connection job. */
    private var _wsJob: Job? = null

    /** WebSocket client. */
    private var _wsClient: okhttp3.WebSocket? = null

    /** Webhook server job. */
    private var _webhookJob: Job? = null

    /** Message deduplicator. */
    private val _dedup: MessageDeduplicator = MessageDeduplicator(maxSize = MAX_DEDUP_ENTRIES)

    /** Pending reactions (message_id → emoji). */
    private val _pendingReactions: ConcurrentHashMap<String, String> = ConcurrentHashMap()

    /** Processed card action IDs (for dedup). */
    private val _processedCardActions: MessageDeduplicator = MessageDeduplicator(maxSize = MAX_CARD_ACTIONS)

    /** Per-chat serial processing queues. */
    private val _chatQueues: ConcurrentHashMap<String, Channel<MessageEvent>> = ConcurrentHashMap()

    /** Per-chat processing jobs. */
    private val _chatJobs: ConcurrentHashMap<String, Job> = ConcurrentHashMap()

    /** Message sequence counter. */
    private val _sequence = AtomicLong(0)

    /** HTTP session counter. */
    private val _httpSessionCount = AtomicInteger(0)

    /** Domain (feishu.cn or larksuite.com). */
    private val _domain: String = config.extra("domain", "https://open.feishu.cn")

    /** User allowlist (open_ids). */
    private val _allowedUsers: Set<String> = config.extra("allowed_users")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet() ?: emptySet()

    /** Whether to allow all users (no allowlist). */
    private val _allowAllUsers: Boolean = config.extraBool("allow_all_users", _allowedUsers.isEmpty())

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override val isConnected: AtomicBoolean
        get() = AtomicBoolean(_wsClient != null || _webhookJob != null)

    /**
     * Connect to Feishu.
     *
     * 1. Obtain a tenant_access_token
     * 2. Resolve the bot's own identity
     * 3. Start the transport layer (WebSocket or Webhook)
     */
    override suspend fun connect(): Boolean {
        if (_appId.isEmpty() || _appSecret.isEmpty()) {
            Log.e(TAG, "FEISHU_APP_ID or FEISHU_APP_SECRET not set")
            return false
        }

        // Step 1: Get access token
        if (!_refreshAccessToken()) {
            Log.e(TAG, "Failed to obtain tenant_access_token")
            return false
        }

        // Step 2: Resolve bot identity
        if (!_resolveBotIdentity()) {
            Log.w(TAG, "Failed to resolve bot identity (continuing anyway)")
        }

        // Step 3: Start transport
        return if (_useWebSocket) {
            _startWebSocket()
        } else {
            _startWebhook()
        }
    }

    /**
     * Disconnect from Feishu.
     */
    override suspend fun disconnect() {
        _wsJob?.cancel()
        _wsJob = null
        _wsClient?.close(1000, "Disconnecting")
        _wsClient = null
        _webhookJob?.cancel()
        _webhookJob = null
        _chatJobs.values.forEach { it.cancel() }
        _chatJobs.clear()
        _chatQueues.clear()
        markDisconnected()
        Log.i(TAG, "Disconnected")
    }

    // ------------------------------------------------------------------
    // Access token management
    // ------------------------------------------------------------------

    /**
     * Refresh the tenant_access_token.
     *
     * Tokens expire after 2 hours; we refresh proactively 5 minutes before.
     */
    private suspend fun _refreshAccessToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("app_id", _appId)
                put("app_secret", _appSecret)
            }
            val body = payload.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$_domain/open-apis/auth/v3/tenant_access_token/internal")
                .post(body)
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "Token refresh HTTP ${resp.code}")
                    return@withContext false
                }
                val data = JSONObject(resp.body!!.string())
                if (data.optInt("code", -1) != 0) {
                    Log.e(TAG, "Token refresh error: ${data.optString("msg")}")
                    return@withContext false
                }
                _accessToken = data.getString("tenant_access_token")
                val expire = data.optInt("expire", 7200)
                _accessTokenExpiry = System.currentTimeMillis() / 1000 + expire - 300
                Log.i(TAG, "Access token refreshed (expires in ${expire}s)")
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Ensure we have a valid access token, refreshing if needed.
     */
    private suspend fun _ensureAccessToken() {
        if (_accessToken.isEmpty() || System.currentTimeMillis() / 1000 >= _accessTokenExpiry) {
            _refreshAccessToken()
        }
    }

    // ------------------------------------------------------------------
    // Bot identity
    // ------------------------------------------------------------------

    /**
     * Resolve the bot's own user_id and name.
     */
    private suspend fun _resolveBotIdentity(): Boolean = withContext(Dispatchers.IO) {
        try {
            _ensureAccessToken()
            val request = Request.Builder()
                .url("$_domain/open-apis/bot/v3/info")
                .header("Authorization", "Bearer $_accessToken")
                .get()
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val data = JSONObject(resp.body!!.string())
                if (data.optInt("code", -1) != 0) return@withContext false
                val bot = data.optJSONObject("bot") ?: return@withContext false
                _botUserId = bot.optString("open_id", "")
                _botUserName = bot.optString("name", "Bot")
                Log.i(TAG, "Bot identity: $_botUserName ($_botUserId)")
                return@withContext true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve bot identity: ${e.message}")
            return@withContext false
        }
    }

    // ------------------------------------------------------------------
    // WebSocket transport
    // ------------------------------------------------------------------

    /**
     * Start the WebSocket connection.
     */
    private fun _startWebSocket(): Boolean {
        _wsJob = scope.launch {
            var reconnectDelay = WS_RECONNECT_DELAY
            while (isActive) {
                try {
                    _connectWebSocket()
                    reconnectDelay = WS_RECONNECT_DELAY // Reset on clean disconnect
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "WebSocket error: ${e.message}, reconnecting in ${reconnectDelay}s")
                    delay((reconnectDelay * 1000).toLong())
                    reconnectDelay = minOf(reconnectDelay * 2, WS_MAX_RECONNECT_DELAY)
                }
            }
        }
        return true
    }

    /**
     * Establish a WebSocket connection.
     */
    private suspend fun _connectWebSocket() {
        _ensureAccessToken()

        val request = Request.Builder()
            .url(_wsUrl)
            .header("Authorization", "Bearer $_accessToken")
            .build()

        val listener = object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                Log.i(TAG, "WebSocket connected")
                markConnected()
            }

            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                scope.launch {
                    _handleWebSocketMessage(text)
                }
            }

            override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code $reason")
                webSocket.close(code, reason)
            }

            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
            }
        }

        _wsClient = _httpClient.newWebSocket(request, listener)
    }

    /**
     * Handle an incoming WebSocket message.
     */
    private suspend fun _handleWebSocketMessage(text: String) {
        try {
            val json = JSONObject(text)

            // Handle handshake
            if (json.has("type") && json.getString("type") == "handshake") {
                _handleHandshake(json)
                return
            }

            // Handle events
            val header = json.optJSONObject("header") ?: return
            val eventType = header.optString("event_type", "")

            when (FeishuEventType.fromValue(eventType)) {
                FeishuEventType.MESSAGE -> _handleMessageEvent(json)
                FeishuEventType.REACTION -> _handleReactionEvent(json)
                FeishuEventType.CARD_ACTION -> _handleCardAction(json)
                FeishuEventType.MESSAGE_READ -> { /* ignore read receipts */ }
                FeishuEventType.CHAT_JOINED -> { Log.i(TAG, "Bot added to chat") }
                FeishuEventType.CHAT_LEFT -> { Log.i(TAG, "Bot removed from chat") }
                null -> Log.d(TAG, "Unknown event type: $eventType")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling WebSocket message: ${e.message}")
        }
    }

    /**
     * Handle the WebSocket handshake response.
     */
    private suspend fun _handleHandshake(json: JSONObject) {
        val body = json.optJSONObject("body") ?: return
        val channel = body.optString("channel", "")
        val spTicket = body.optString("sp_ticket", "")
        if (channel == "client" && spTicket.isNotEmpty()) {
            // Send CONNECT message
            val connect = JSONObject().apply {
                put("header", JSONObject().apply {
                    put("app_id", _appId)
                })
                put("body", JSONObject().apply {
                    put("channel", "client")
                    put("ticket", spTicket)
                })
            }
            _wsClient?.send(connect.toString())
            Log.i(TAG, "WebSocket handshake completed")
        }
    }

    /**
     * Handle an incoming message event.
     */
    private suspend fun _handleMessageEvent(json: JSONObject) {
        val eventData = json.optJSONObject("event") ?: return
        val message = eventData.optJSONObject("message") ?: return
        val sender = eventData.optJSONObject("sender") ?: return
        val senderId = sender.optJSONObject("sender_id") ?: return

        val messageId = message.optString("message_id", "")
        val chatId = message.optString("chat_id", "")
        val msgType = message.optString("message_type", "")
        val content = message.optString("content", "")
        val chatType = message.optString("chat_type", "")
        val openId = senderId.optString("open_id", "")

        // Dedup check
        if (_dedup.isDuplicate(messageId)) {
            Log.d(TAG, "Duplicate message: $messageId")
            return
        }

        // Allowlist check
        if (!_allowAllUsers && openId !in _allowedUsers) {
            Log.d(TAG, "User not in allowlist: $openId")
            return
        }

        // Parse message content
        val (text, messageType) = _parseMessageContent(msgType, content, chatType)

        // Build source
        val source = buildSource(
            chatId = chatId,
            chatName = resolveChatName(chatId) ?: chatId,
            chatType = if (chatType == "p2p") "dm" else "group",
            userId = openId,
            userName = resolveUserName(openId) ?: openId)

        // Build event
        val event = MessageEvent(
            text = text,
            messageType = messageType,
            source = source,
            message_id = messageId,
            timestamp = Instant.now())

        // Send ACK reaction
        if (_sendAckReaction && chatType != "p2p") {
            _sendAckReactionAsync(messageId)
        }

        // Queue for serial processing
        _queueForProcessing(chatId, event)
    }

    /**
     * Parse Feishu message content into text + MessageType.
     */
    private fun _parseMessageContent(
        msgType: String,
        content: String,
        chatType: String): Pair<String, MessageType> {
        val feishuType = FeishuMessageType.fromValue(msgType)

        return when (feishuType) {
            FeishuMessageType.TEXT -> {
                val json = try { JSONObject(content) } catch (_unused: Exception) { JSONObject() }
                val text = json.optString("text", content)
                Pair(text, MessageType.TEXT)
            }
            FeishuMessageType.POST -> {
                // Rich text — extract plain text
                val json = try { JSONObject(content) } catch (_unused: Exception) { JSONObject() }
                val text = _extractPostText(json)
                Pair(text, MessageType.TEXT)
            }
            FeishuMessageType.IMAGE -> {
                val json = try { JSONObject(content) } catch (_unused: Exception) { JSONObject() }
                val imageKey = json.optString("image_key", "")
                Pair("[Image: $imageKey]", MessageType.PHOTO)
            }
            FeishuMessageType.FILE -> {
                val json = try { JSONObject(content) } catch (_unused: Exception) { JSONObject() }
                val fileName = json.optString("file_name", "file")
                Pair("[File: $fileName]", MessageType.DOCUMENT)
            }
            FeishuMessageType.AUDIO -> {
                val json = try { JSONObject(content) } catch (_unused: Exception) { JSONObject() }
                val fileKey = json.optString("file_key", "")
                Pair("[Audio: $fileKey]", MessageType.AUDIO)
            }
            FeishuMessageType.MEDIA -> {
                val json = try { JSONObject(content) } catch (_unused: Exception) { JSONObject() }
                val fileKey = json.optString("file_key", "")
                Pair("[Video: $fileKey]", MessageType.VIDEO)
            }
            FeishuMessageType.STICKER -> {
                val json = try { JSONObject(content) } catch (_unused: Exception) { JSONObject() }
                val fileKey = json.optString("file_key", "")
                Pair("[Sticker: $fileKey]", MessageType.STICKER)
            }
            FeishuMessageType.INTERACTIVE -> {
                // Card message — extract text from card body
                val json = try { JSONObject(content) } catch (_unused: Exception) { JSONObject() }
                val text = _extractCardText(json)
                Pair(text, MessageType.SYSTEM)
            }
            else -> Pair(content, MessageType.UNKNOWN)
        }
    }

    /**
     * Extract plain text from a Feishu post (rich text) message.
     */
    private fun _extractPostText(json: JSONObject): String {
        val content = json.optJSONArray("content") ?: return ""
        val lines = mutableListOf<String>()
        for (i in 0 until content.length()) {
            val line = content.getJSONArray(i)
            val parts = mutableListOf<String>()
            for (j in 0 until line.length()) {
                val elem = line.getJSONObject(j)
                val tag = elem.optString("tag", "")
                when (tag) {
                    "text" -> parts.add(elem.optString("text", ""))
                    "a" -> parts.add(elem.optString("text", "") + " " + elem.optString("href", ""))
                    "at" -> parts.add("@${elem.optString("user_name", elem.optString("user_id", ""))}")
                    "img" -> parts.add("[Image]")
                    else -> parts.add(elem.optString("text", ""))
                }
            }
            lines.add(parts.joinToString(""))
        }
        return lines.joinToString("\n")
    }

    /**
     * Extract text from a Feishu interactive card.
     */
    private fun _extractCardText(json: JSONObject): String {
        val elements = json.optJSONArray("elements") ?: return ""
        val parts = mutableListOf<String>()
        for (i in 0 until elements.length()) {
            val elem = elements.getJSONObject(i)
            val tag = elem.optString("tag", "")
            when (tag) {
                "markdown" -> parts.add(elem.optString("content", ""))
                "div" -> {
                    val text = elem.optJSONObject("text")
                    if (text != null) {
                        parts.add(text.optString("content", ""))
                    }
                }
                "action" -> {
                    val actions = elem.optJSONArray("actions")
                    if (actions != null) {
                        for (j in 0 until actions.length()) {
                            val action = actions.getJSONObject(j)
                            parts.add("[${action.optString("text", "Button")}]")
                        }
                    }
                }
            }
        }
        return parts.joinToString("\n")
    }

    /**
     * Handle a reaction event.
     */
    private suspend fun _handleReactionEvent(json: JSONObject) {
        val event = json.optJSONObject("event") ?: return
        val reactionType = event.optJSONObject("reaction_type") ?: return
        val emoji = reactionType.optString("emoji_type", "")
        val messageId = event.optString("message_id", "")
        val operatorId = event.optJSONObject("operator")?.optJSONObject("operator_id")?.optString("open_id", "") ?: ""

        if (operatorId == _botUserId) return // Ignore bot's own reactions

        // Route reaction as a synthetic text event
        val chatId = event.optString("chat_id", "")
        val source = buildSource(
            chatId = chatId,
            chatType = "group",
            userId = operatorId)

        val reactionEvent = MessageEvent(
            text = "/reaction $emoji $messageId",
            messageType = MessageType.REACTION,
            source = source,
            message_id = messageId,
            reactionEmoji = emoji,
            reactionAdded = true)

        _queueForProcessing(chatId, reactionEvent)
    }

    /**
     * Handle a card action (button click) event.
     */
    private suspend fun _handleCardAction(json: JSONObject) {
        val event = json.optJSONObject("event") ?: return
        val action = event.optJSONObject("action") ?: return
        val operator = event.optJSONObject("operator") ?: return
        val openId = operator.optString("open_id", "")

        // Dedup
        val actionId = "${openId}_${action.optString("value", "")}_${System.currentTimeMillis() / 1000}"
        if (_processedCardActions.isDuplicate(actionId)) return

        val chatId = event.optString("open_chat_id", event.optString("chat_id", ""))
        val buttonValue = action.optString("value", "")
        val tag = action.optString("tag", "")

        val source = buildSource(
            chatId = chatId,
            chatType = "group",
            userId = openId)

        val commandEvent = MessageEvent(
            text = "/$tag $buttonValue",
            messageType = MessageType.COMMAND,
            source = source)

        _queueForProcessing(chatId, commandEvent)
    }

    /**
     * Queue a message for serial per-chat processing.
     */
    private fun _queueForProcessing(chatId: String, event: MessageEvent) {
        val queue = _chatQueues.getOrPut(chatId) { Channel(Channel.UNLIMITED) }
        queue.trySend(event)

        // Start processing job if not already running
        if (_chatJobs[chatId]?.isActive != true) {
            _chatJobs[chatId] = scope.launch {
                _processChatQueue(chatId, queue)
            }
        }
    }

    /**
     * Process messages from a chat queue serially.
     */
    private suspend fun _processChatQueue(chatId: String, channel: Channel<MessageEvent>) {
        for (event in channel) {
            try {
                handleMessage(event)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message in chat $chatId: ${e.message}")
            }
        }
    }

    /**
     * Send an ACK reaction asynchronously.
     */
    private fun _sendAckReactionAsync(messageId: String) {
        scope.launch {
            try {
                _ensureAccessToken()
                val payload = JSONObject().apply {
                    put("reaction_type", JSONObject().apply {
                        put("emoji_type", _ackEmoji)
                    })
                }
                val body = payload.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("$_domain/open-apis/im/v1/messages/$messageId/reactions")
                    .header("Authorization", "Bearer $_accessToken")
                    .post(body)
                    .build()
                _httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "ACK reaction failed: HTTP ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ACK reaction error: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------------
    // Webhook transport
    // ------------------------------------------------------------------

    /**
     * Start the webhook server.
     */
    private fun _startWebhook(): Boolean {
        // On Android, we don't typically run HTTP servers.
        // This is a placeholder for the webhook transport.
        Log.w(TAG, "Webhook transport not supported on Android. Use WebSocket instead.")
        return false
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
            _ensureAccessToken()

            // Split long messages
            val chunks = _splitMessage(content, MAX_MESSAGE_LENGTH)
            var lastMessageId: String? = null

            for (chunk in chunks) {
                val result = _sendTextMessage(chatId, chunk, replyTo)
                if (result.success) {
                    lastMessageId = result.messageId
                } else {
                    return@withContext result
                }
            }

            SendResult(success = true, messageId = lastMessageId)
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}")
            SendResult(success = false, error = e.message)
        }
    }

    /**
     * Send a single text message via the Feishu IM API.
     */
    private suspend fun _sendTextMessage(
        chatId: String,
        text: String,
        replyTo: String? = null): SendResult = withContext(Dispatchers.IO) {
        try {
            _ensureAccessToken()

            val payload = JSONObject().apply {
                put("receive_id", chatId)
                put("msg_type", "text")
                put("content", JSONObject().apply { put("text", text) }.toString())
                put("uuid", _generateUuid())
            }

            val url = if (replyTo != null) {
                "$_domain/open-apis/im/v1/messages/$replyTo/reply"
            } else {
                "$_domain/open-apis/im/v1/messages?receive_id_type=chat_id"
            }

            val body = payload.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $_accessToken")
                .post(body)
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string() ?: ""
                    Log.e(TAG, "Send HTTP ${resp.code}: $errorBody")
                    return@withContext SendResult(success = false, error = "HTTP ${resp.code}")
                }

                val data = JSONObject(resp.body!!.string())
                if (data.optInt("code", -1) != 0) {
                    return@withContext SendResult(success = false, error = data.optString("msg"))
                }

                val messageId = data.optJSONObject("data")?.optString("message_id")
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
            _ensureAccessToken()

            // Upload image first
            val imageKey = _uploadImage(imageUrl) ?: return@withContext SendResult(
                success = false, error = "Failed to upload image"
            )

            val payload = JSONObject().apply {
                put("receive_id", chatId)
                put("msg_type", "image")
                put("content", JSONObject().apply { put("image_key", imageKey) }.toString())
                put("uuid", _generateUuid())
            }

            val body = payload.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$_domain/open-apis/im/v1/messages?receive_id_type=chat_id")
                .header("Authorization", "Bearer $_accessToken")
                .post(body)
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext SendResult(success = false, error = "HTTP ${resp.code}")
                val data = JSONObject(resp.body!!.string())
                if (data.optInt("code", -1) != 0) return@withContext SendResult(success = false, error = data.optString("msg"))
                val messageId = data.optJSONObject("data")?.optString("message_id")
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
            _ensureAccessToken()

            // Upload file first
            val fileKey = _uploadFile(fileUrl, fileName ?: "document") ?: return@withContext SendResult(
                success = false, error = "Failed to upload file"
            )

            val payload = JSONObject().apply {
                put("receive_id", chatId)
                put("msg_type", "file")
                put("content", JSONObject().apply { put("file_key", fileKey) }.toString())
                put("uuid", _generateUuid())
            }

            val body = payload.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$_domain/open-apis/im/v1/messages?receive_id_type=chat_id")
                .header("Authorization", "Bearer $_accessToken")
                .post(body)
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext SendResult(success = false, error = "HTTP ${resp.code}")
                val data = JSONObject(resp.body!!.string())
                if (data.optInt("code", -1) != 0) return@withContext SendResult(success = false, error = data.optString("msg"))
                val messageId = data.optJSONObject("data")?.optString("message_id")
                SendResult(success = true, messageId = messageId)
            }
        } catch (e: Exception) {
            SendResult(success = false, error = e.message)
        }
    }

    override suspend fun sendTyping(chatId: String, metadata: JSONObject?) {
        // Feishu doesn't have a typing indicator API
    }

    // ------------------------------------------------------------------
    // File upload helpers
    // ------------------------------------------------------------------

    /**
     * Upload an image to Feishu and return the image_key.
     */
    private suspend fun _uploadImage(imageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            _ensureAccessToken()

            // Download image data
            val imageData = if (imageUrl.startsWith("http")) {
                val request = Request.Builder().url(imageUrl).build()
                _httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    resp.body!!.bytes()
                }
            } else {
                File(imageUrl).readBytes()
            }

            // Determine image type
            val mimeType = when {
                imageData.size >= 2 && imageData[0] == 0xFF.toByte() && imageData[1] == 0xD8.toByte() -> "image/jpeg"
                imageData.size >= 4 && imageData[0] == 0x89.toByte() && imageData[1] == 0x50.toByte() -> "image/png"
                imageData.size >= 4 && imageData[0] == 0x47.toByte() && imageData[1] == 0x49.toByte() -> "image/gif"
                imageData.size >= 4 && imageData[0] == 0x52.toByte() && imageData[1] == 0x49.toByte() -> "image/webp"
                else -> "image/jpeg"
            }

            val imageType = when (mimeType) {
                "image/jpeg" -> "message"
                "image/png" -> "message"
                "image/gif" -> "message"
                "image/webp" -> "message"
                else -> "message"
            }

            // Upload via multipart form
            val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
            val bodyBuilder = StringBuilder()
            bodyBuilder.append("--$boundary\r\n")
            bodyBuilder.append("Content-Disposition: form-data; name=\"image_type\"\r\n\r\n")
            bodyBuilder.append("$imageType\r\n")
            bodyBuilder.append("--$boundary\r\n")
            bodyBuilder.append("Content-Disposition: form-data; name=\"image\"; filename=\"image.${mimeType.substringAfter("/")}\"\r\n")
            bodyBuilder.append("Content-Type: $mimeType\r\n\r\n")

            val headerBytes = bodyBuilder.toString().toByteArray(Charsets.UTF_8)
            val footerBytes = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

            val fullBody = headerBytes + imageData + footerBytes

            val request = Request.Builder()
                .url("$_domain/open-apis/im/v1/images")
                .header("Authorization", "Bearer $_accessToken")
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .post(fullBody.toRequestBody("multipart/form-data".toMediaType()))
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val data = JSONObject(resp.body!!.string())
                if (data.optInt("code", -1) != 0) return@withContext null
                return@withContext data.optJSONObject("data")?.optString("image_key")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image upload failed: ${e.message}")
            return@withContext null
        }
    }

    /**
     * Upload a file to Feishu and return the file_key.
     */
    private suspend fun _uploadFile(fileUrl: String, fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            _ensureAccessToken()

            // Download file data
            val fileData = if (fileUrl.startsWith("http")) {
                val request = Request.Builder().url(fileUrl).build()
                _httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    resp.body!!.bytes()
                }
            } else {
                File(fileUrl).readBytes()
            }

            // Determine file type
            val ext = fileName.substringAfterLast(".", "bin")
            val fileType = when (ext.lowercase()) {
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv" -> "stream"
                else -> "stream"
            }

            // Upload via multipart form
            val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
            val bodyBuilder = StringBuilder()
            bodyBuilder.append("--$boundary\r\n")
            bodyBuilder.append("Content-Disposition: form-data; name=\"file_type\"\r\n\r\n")
            bodyBuilder.append("$fileType\r\n")
            bodyBuilder.append("--$boundary\r\n")
            bodyBuilder.append("Content-Disposition: form-data; name=\"file_name\"\r\n\r\n")
            bodyBuilder.append("$fileName\r\n")
            bodyBuilder.append("--$boundary\r\n")
            bodyBuilder.append("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
            bodyBuilder.append("Content-Type: application/octet-stream\r\n\r\n")

            val headerBytes = bodyBuilder.toString().toByteArray(Charsets.UTF_8)
            val footerBytes = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

            val fullBody = headerBytes + fileData + footerBytes

            val request = Request.Builder()
                .url("$_domain/open-apis/im/v1/files")
                .header("Authorization", "Bearer $_accessToken")
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .post(fullBody.toRequestBody("multipart/form-data".toMediaType()))
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val data = JSONObject(resp.body!!.string())
                if (data.optInt("code", -1) != 0) return@withContext null
                return@withContext data.optJSONObject("data")?.optString("file_key")
            }
        } catch (e: Exception) {
            Log.e(TAG, "File upload failed: ${e.message}")
            return@withContext null
        }
    }

    // ------------------------------------------------------------------
    // Utility methods
    // ------------------------------------------------------------------

    /**
     * Split a long message into chunks.
     */
    private fun _splitMessage(text: String, maxLength: Int): List<String> {
        if (text.length <= maxLength) return listOf(text)

        val chunks = mutableListOf<String>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxLength) {
                chunks.add(remaining)
                break
            }

            // Try to split at a newline
            val splitAt = remaining.lastIndexOf('\n', maxLength)
            if (splitAt > maxLength / 2) {
                chunks.add(remaining.substring(0, splitAt))
                remaining = remaining.substring(splitAt + 1)
            } else {
                // Split at a space
                val spaceAt = remaining.lastIndexOf(' ', maxLength)
                if (spaceAt > maxLength / 2) {
                    chunks.add(remaining.substring(0, spaceAt))
                    remaining = remaining.substring(spaceAt + 1)
                } else {
                    // Hard split
                    chunks.add(remaining.substring(0, maxLength))
                    remaining = remaining.substring(maxLength)
                }
            }
        }

        return chunks
    }

    /**
     * Generate a UUID for message deduplication.
     */
    private fun _generateUuid(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Get the access token (for external use).
     */
    val accessToken: String get() = _accessToken

    /**
     * Get the bot user id.
     */
    val botUserId: String get() = _botUserId

    /**
     * Get the bot user name.
     */
    val botUserName: String get() = _botUserName

    /**
     * Resolve chat name from chat_id via API.
     * 对齐 feishu.py 的 chat name resolution.
     */
    fun resolveChatName(chatId: String): String? {
        return try {
            val url = "https://open.feishu.cn/open-apis/im/v1/chats/$chatId"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $_accessToken")
                .get().build()
            val response = _httpClient.newCall(request).execute()
            val body = JSONObject(response.body?.string() ?: "")
            body.optJSONObject("data")?.optString("name")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve chat name for $chatId: ${e.message}")
            null
        }
    }

    /**
     * Resolve user display name from open_id via API.
     * 对齐 feishu.py 的 user name resolution.
     */
    fun resolveUserName(openId: String): String? {
        return try {
            val url = "https://open.feishu.cn/open-apis/contact/v3/users/$openId?user_id_type=open_id"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $_accessToken")
                .get().build()
            val response = _httpClient.newCall(request).execute()
            val body = JSONObject(response.body?.string() ?: "")
            body.optJSONObject("data")?.optJSONObject("user")?.optString("name")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve user name for $openId: ${e.message}")
            null
        }
    }
}

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
class FeishuAdapter(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.FEISHU) {
    companion object {
        private const val _TAG = "Feishu"

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

    /** ACK reaction emoji (default: Typing — matches Androidclaw "正在输入"). */
    private val _ackEmoji: String = config.extra("ack_emoji", _FEISHU_REACTION_IN_PROGRESS)

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

    /** Pending processing-indicator reaction ids (message_id → reaction_id). */
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
            Log.e(_TAG, "FEISHU_APP_ID or FEISHU_APP_SECRET not set")
            return false
        }

        // Step 1: Get access token
        if (!_refreshAccessToken()) {
            Log.e(_TAG, "Failed to obtain tenant_access_token")
            return false
        }

        // Step 2: Resolve bot identity
        if (!_resolveBotIdentity()) {
            Log.w(_TAG, "Failed to resolve bot identity (continuing anyway)")
        }

        // Step 3: Start transport
        return if (_useWebSocket) {
            _startWebSocket()
        } else {
            _connectWebhook()
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
        Log.i(_TAG, "Disconnected")
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
                    Log.e(_TAG, "Token refresh HTTP ${resp.code}")
                    return@withContext false
                }
                val data = JSONObject(resp.body!!.string())
                if (data.optInt("code", -1) != 0) {
                    Log.e(_TAG, "Token refresh error: ${data.optString("msg")}")
                    return@withContext false
                }
                _accessToken = data.getString("tenant_access_token")
                val expire = data.optInt("expire", 7200)
                _accessTokenExpiry = System.currentTimeMillis() / 1000 + expire - 300
                Log.i(_TAG, "Access token refreshed (expires in ${expire}s)")
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(_TAG, "Token refresh failed: ${e.message}")
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
                Log.i(_TAG, "Bot identity: $_botUserName ($_botUserId)")
                return@withContext true
            }
        } catch (e: Exception) {
            Log.w(_TAG, "Failed to resolve bot identity: ${e.message}")
            return@withContext false
        }
    }

    // ------------------------------------------------------------------
    // WebSocket transport
    // ------------------------------------------------------------------

    /**
     * Start the WebSocket connection.
     *
     * The Lark SDK's [com.lark.oapi.ws.Client.start] manages its own reconnect
     * loop internally (ping + exponential backoff). Wrapping it in an outer
     * `while (isActive)` loop previously caused a tight reconnect spin because
     * SDK `start()` returns after completing its own setup, not on disconnect —
     * each spin burned a fresh `/gateway/v1/connect` handshake and quickly hit
     * Feishu's per-app connection limit. Androidclaw proves the single-call
     * pattern is correct (see `FeishuWebSocketHandler.kt:80`).
     */
    private fun _startWebSocket(): Boolean {
        _wsJob = scope.launch {
            try {
                _connectWebsocket()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(_TAG, "WebSocket start failed: ${e.message}", e)
            }
        }
        return true
    }

    /**
     * Establish a WebSocket connection using the official lark-oapi SDK.
     *
     * Mirrors Python `hermes-agent/gateway/platforms/feishu.py::_connect_websocket`
     * which delegates to `lark_oapi.ws.Client`. On Android we use the same upstream
     * SDK (`com.larksuite.oapi:oapi-sdk:2.4.4`) — it does the `/open-apis/gateway/v1/connect`
     * bootstrap + signed WSS URL + handshake internally, so the hand-rolled 404-prone
     * `wss://open.feishu.cn/open-apis/ws/v2` fallback is no longer used.
     *
     * Suspends until the SDK client's blocking `start()` returns (i.e. connection
     * closed) so the outer retry loop's backoff actually fires.
     */
    private suspend fun _connectWebsocket() {
        _ensureAccessToken()
        val closed = kotlinx.coroutines.CompletableDeferred<Unit>()

        val domainUrl: String = if (_domain.contains("larksuite.com")) {
            com.lark.oapi.core.enums.BaseUrlEnum.LarkSuite.url
        } else {
            com.lark.oapi.core.enums.BaseUrlEnum.FeiShu.url
        }

        val dispatcher = com.lark.oapi.event.EventDispatcher.newBuilder(
            _verificationToken.ifEmpty { "" },
            _encryptKey.ifEmpty { "" },
        )
            .onP2MessageReceiveV1(object : com.lark.oapi.service.im.ImService.P2MessageReceiveV1Handler() {
                override fun handle(data: com.lark.oapi.service.im.v1.model.P2MessageReceiveV1?) {
                    val ev = data?.event ?: return
                    scope.launch { _dispatchSdkMessage(ev) }
                }
            })
            .onP2MessageReactionCreatedV1(object : com.lark.oapi.service.im.ImService.P2MessageReactionCreatedV1Handler() {
                override fun handle(data: com.lark.oapi.service.im.v1.model.P2MessageReactionCreatedV1?) {
                    val ev = data?.event ?: return
                    scope.launch { _dispatchSdkReaction(ev) }
                }
            })
            .onP2MessageReadV1(object : com.lark.oapi.service.im.ImService.P2MessageReadV1Handler() {
                override fun handle(data: com.lark.oapi.service.im.v1.model.P2MessageReadV1?) {
                    /* ignore read receipts */
                }
            })
            .build()

        val sdkClient = com.lark.oapi.ws.Client.Builder(_appId, _appSecret)
            .domain(domainUrl)
            .eventHandler(dispatcher)
            .build()

        Log.i(_TAG, "Starting official Feishu WS client (domain=$domainUrl)")
        markConnected()
        scope.launch(Dispatchers.IO) {
            try {
                sdkClient.start()
                closed.complete(Unit)
            } catch (t: Throwable) {
                closed.completeExceptionally(t)
            }
        }
        closed.await()
    }

    /** Dispatch an SDK-parsed P2MessageReceiveV1 event into the existing pipeline. */
    private suspend fun _dispatchSdkMessage(
        event: com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data,
    ) {
        try {
            val sender = event.sender ?: return
            val message = event.message ?: return
            val senderInfo = JSONObject().apply {
                put("sender_id", JSONObject().apply {
                    put("open_id", sender.senderId?.openId ?: "")
                    put("user_id", sender.senderId?.userId ?: "")
                    put("union_id", sender.senderId?.unionId ?: "")
                })
                put("sender_type", sender.senderType ?: "user")
            }
            val mentions = org.json.JSONArray()
            message.mentions?.forEach { m ->
                mentions.put(JSONObject().apply {
                    put("key", m.key ?: "")
                    put("name", m.name ?: "")
                    put("id", JSONObject().apply {
                        put("open_id", m.id?.openId ?: "")
                        put("user_id", m.id?.userId ?: "")
                        put("union_id", m.id?.unionId ?: "")
                    })
                })
            }
            val msgObj = JSONObject().apply {
                put("message_id", message.messageId ?: "")
                put("root_id", message.rootId ?: "")
                put("parent_id", message.parentId ?: "")
                put("create_time", message.createTime ?: "")
                put("chat_id", message.chatId ?: "")
                put("chat_type", message.chatType ?: "")
                put("message_type", message.messageType ?: "")
                put("content", message.content ?: "")
                put("mentions", mentions)
                try { put("thread_id", message.threadId ?: "") } catch (_: Exception) { }
            }
            val synth = JSONObject().apply {
                put("header", JSONObject().apply { put("event_type", "im.message.receive_v1") })
                put("event", JSONObject().apply {
                    put("sender", senderInfo)
                    put("message", msgObj)
                })
            }
            _handleMessageEventData(synth)
        } catch (e: Exception) {
            Log.w(_TAG, "Error dispatching SDK message: ${e.message}")
        }
    }

    /** Dispatch an SDK-parsed P2MessageReactionCreatedV1 event into the existing pipeline. */
    private suspend fun _dispatchSdkReaction(
        event: com.lark.oapi.service.im.v1.model.P2MessageReactionCreatedV1Data,
    ) {
        try {
            val synth = JSONObject().apply {
                put("header", JSONObject().apply { put("event_type", "im.message.reaction.created_v1") })
                put("event", JSONObject().apply {
                    put("message_id", event.messageId ?: "")
                    put("reaction_type", JSONObject().apply {
                        put("emoji_type", event.reactionType?.emojiType ?: "")
                    })
                    put("operator_type", event.operatorType ?: "")
                    put("user_id", JSONObject().apply {
                        put("open_id", event.userId?.openId ?: "")
                    })
                })
            }
            _handleReactionEvent(synth)
        } catch (e: Exception) {
            Log.w(_TAG, "Error dispatching SDK reaction: ${e.message}")
        }
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

            when (eventType) {
                "im.message.receive_v1" -> _handleMessageEventData(json)
                "im.message.reaction.created_v1" -> _handleReactionEvent(json)
                "card.action.trigger" -> _handleCardActionEvent(json)
                "im.message.message_read_v1" -> { /* ignore read receipts */ }
                "im.chat.member.bot.added_v1" -> { Log.i(_TAG, "Bot added to chat") }
                "im.chat.member.bot.deleted_v1" -> { Log.i(_TAG, "Bot removed from chat") }
                else -> Log.d(_TAG, "Unknown event type: $eventType")
            }
        } catch (e: Exception) {
            Log.w(_TAG, "Error handling WebSocket message: ${e.message}")
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
            Log.i(_TAG, "WebSocket handshake completed")
        }
    }

    /**
     * Handle an incoming message event.
     */
    private suspend fun _handleMessageEventData(json: JSONObject) {
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
            Log.d(_TAG, "Duplicate message: $messageId")
            return
        }

        // Allowlist check
        if (!_allowAllUsers && openId !in _allowedUsers) {
            Log.d(_TAG, "User not in allowlist: $openId")
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

        // Send ACK reaction (processing-indicator) on message receive.
        // Androidclaw parity: fires in DMs too — "Typing" shows up immediately
        // to tell the user their message landed, independent of chatType.
        if (_sendAckReaction) {
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
        return when (msgType) {
            "text" -> {
                val json = try { JSONObject(content) } catch (_unused: Exception) { JSONObject() }
                val text = json.optString("text", content)
                Pair(text, MessageType.TEXT)
            }
            "post" -> {
                // Rich text — extract plain text
                val json = try { JSONObject(content) } catch (_unused: Exception) { JSONObject() }
                val text = _extractPostText(json)
                Pair(text, MessageType.TEXT)
            }
            "image" -> {
                val json = try { JSONObject(content) } catch (_unused: Exception) { JSONObject() }
                val imageKey = json.optString("image_key", "")
                Pair("[Image: $imageKey]", MessageType.PHOTO)
            }
            "file" -> {
                val json = try { JSONObject(content) } catch (_unused: Exception) { JSONObject() }
                val fileName = json.optString("file_name", "file")
                Pair("[File: $fileName]", MessageType.DOCUMENT)
            }
            "audio" -> {
                val json = try { JSONObject(content) } catch (_unused: Exception) { JSONObject() }
                val fileKey = json.optString("file_key", "")
                Pair("[Audio: $fileKey]", MessageType.AUDIO)
            }
            "media" -> {
                val json = try { JSONObject(content) } catch (_unused: Exception) { JSONObject() }
                val fileKey = json.optString("file_key", "")
                Pair("[Video: $fileKey]", MessageType.VIDEO)
            }
            "sticker" -> {
                val json = try { JSONObject(content) } catch (_unused: Exception) { JSONObject() }
                val fileKey = json.optString("file_key", "")
                Pair("[Sticker: $fileKey]", MessageType.STICKER)
            }
            "interactive" -> {
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
        // SDK path puts operator in event.user_id.open_id;
        // WebSocket path puts it in event.operator.operator_id.open_id.
        // Try both so we always resolve the operator correctly.
        val operatorId = event.optJSONObject("operator")
            ?.optJSONObject("operator_id")
            ?.optString("open_id", "")
            ?: event.optJSONObject("user_id")
                ?.optString("open_id", "")
            ?: ""

        if (operatorId.isEmpty() || operatorId == _botUserId) return // Ignore bot's own reactions

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
    private suspend fun _handleCardActionEvent(json: JSONObject) {
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
                Log.e(_TAG, "Error processing message in chat $chatId: ${e.message}")
            }
        }
    }

    /**
     * Send an ACK "processing-indicator" reaction asynchronously.
     *
     * The Feishu API returns a `reaction_id` we have to remember so we can
     * delete the badge once the agent replies. Parity with
     * feishu.py._add_reaction + _remember_processing_reaction and
     * Androidclaw FeishuReactions.addReaction.
     */
    private fun _sendAckReactionAsync(messageId: String) {
        if (messageId.isEmpty()) return
        if (_pendingReactions.containsKey(messageId)) return
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
                        Log.w(_TAG, "ACK reaction failed: HTTP ${resp.code}")
                        return@use
                    }
                    val bodyText = resp.body?.string() ?: return@use
                    val json = try { JSONObject(bodyText) } catch (_: Exception) { return@use }
                    val code = json.optInt("code", -1)
                    if (code != 0) {
                        Log.w(_TAG, "ACK reaction rejected: code=$code msg=${json.optString("msg")}")
                        return@use
                    }
                    val reactionId = json.optJSONObject("data")?.optString("reaction_id", "").orEmpty()
                    if (reactionId.isNotEmpty()) {
                        _pendingReactions[messageId] = reactionId
                    }
                }
            } catch (e: Exception) {
                Log.w(_TAG, "ACK reaction error: ${e.message}")
            }
        }
    }

    /**
     * Remove the processing-indicator reaction previously recorded for
     * [messageId]. Parity with feishu.py._remove_reaction +
     * _pop_processing_reaction.
     */
    private suspend fun _removeAckReaction(messageId: String) {
        val reactionId = _pendingReactions.remove(messageId) ?: return
        withContext(Dispatchers.IO) {
            try {
                _ensureAccessToken()
                val request = Request.Builder()
                    .url("$_domain/open-apis/im/v1/messages/$messageId/reactions/$reactionId")
                    .header("Authorization", "Bearer $_accessToken")
                    .delete()
                    .build()
                _httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(_TAG, "Remove reaction failed: HTTP ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(_TAG, "Remove reaction error: ${e.message}")
            }
        }
    }

    /**
     * Add a terminal "failure" reaction (CrossMark) when the agent loop
     * errored out. Parity with feishu.py on_processing_complete FAILURE branch.
     */
    private suspend fun _addFailureReaction(messageId: String) {
        if (messageId.isEmpty()) return
        withContext(Dispatchers.IO) {
            try {
                _ensureAccessToken()
                val payload = JSONObject().apply {
                    put("reaction_type", JSONObject().apply {
                        put("emoji_type", _FEISHU_REACTION_FAILURE)
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
                        Log.w(_TAG, "Failure reaction failed: HTTP ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(_TAG, "Failure reaction error: ${e.message}")
            }
        }
    }

    override suspend fun onProcessingComplete(event: MessageEvent, success: Boolean) {
        val messageId = event.message_id ?: return
        _removeAckReaction(messageId)
        if (!success) _addFailureReaction(messageId)
    }

    // ------------------------------------------------------------------
    // Webhook transport
    // ------------------------------------------------------------------

    /**
     * Start the webhook server.
     */
    private fun _connectWebhook(): Boolean {
        // On Android, we don't typically run HTTP servers.
        // This is a placeholder for the webhook transport.
        Log.w(_TAG, "Webhook transport not supported on Android. Use WebSocket instead.")
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
            Log.e(_TAG, "Send failed: ${e.message}")
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
                    Log.e(_TAG, "Send HTTP ${resp.code}: $errorBody")
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
            Log.e(_TAG, "Image upload failed: ${e.message}")
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
            Log.e(_TAG, "File upload failed: ${e.message}")
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
            Log.w(_TAG, "Failed to resolve chat name for $chatId: ${e.message}")
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
            Log.w(_TAG, "Failed to resolve user name for $openId: ${e.message}")
            null
        }
    }
}

// ---------------------------------------------------------------------------
// Module-level data classes (ported from feishu.py)
// ---------------------------------------------------------------------------

data class FeishuPostMediaRef(
    val file_key: String,
    val file_name: String = "",
    val resource_type: String = "file",
)

data class FeishuPostParseResult(
    val text_content: String,
    val image_keys: MutableList<String> = mutableListOf(),
    val media_refs: MutableList<FeishuPostMediaRef> = mutableListOf(),
    val mentioned_ids: MutableList<String> = mutableListOf(),
)

data class FeishuNormalizedMessage(
    val raw_type: String,
    val text_content: String,
    val preferred_message_type: String = "text",
    val image_keys: MutableList<String> = mutableListOf(),
    val media_refs: MutableList<FeishuPostMediaRef> = mutableListOf(),
    val mentioned_ids: MutableList<String> = mutableListOf(),
    val relation_kind: String = "plain",
    val metadata: MutableMap<String, Any> = mutableMapOf(),
)

// ---------------------------------------------------------------------------
// Module-level constants (ported from feishu.py)
// ---------------------------------------------------------------------------

const val FALLBACK_POST_TEXT = "[Rich text message]"
const val FALLBACK_FORWARD_TEXT = "[Merged forward message]"
const val FALLBACK_SHARE_CHAT_TEXT = "[Shared chat]"
const val FALLBACK_INTERACTIVE_TEXT = "[Interactive message]"
const val FALLBACK_IMAGE_TEXT = "[Image]"
const val FALLBACK_ATTACHMENT_TEXT = "[Attachment]"

/**
 * True when Python `websockets` is importable. Always true on Android —
 * OkHttp ships a native WebSocket client so the long-connection path is
 * always available.
 */
const val FEISHU_WEBSOCKET_AVAILABLE: Boolean = true

/**
 * True when Python `aiohttp` is importable. Always false on Android — we
 * have no stable inbound URL without a tunnel, so the webhook transport is
 * not supported. See CLAUDE.md §6 "Webhook mode degradation" decision.
 */
const val FEISHU_WEBHOOK_AVAILABLE: Boolean = false

internal val _MARKDOWN_HINT_RE: Regex = Regex(
    "(^#{1,6}\\s)|(^\\s*[-*]\\s)|(^\\s*\\d+\\.\\s)|(^\\s*---+\\s*$)|(```)|" +
        "(`[^`\n]+`)|(\\*\\*[^*\n].+?\\*\\*)|(~~[^~\n].+?~~)|(<u>.+?</u>)|" +
        "(\\*[^*\n]+\\*)|(\\[[^\\]]+\\]\\([^)]+\\))|(^>\\s)",
    RegexOption.MULTILINE,
)

internal val _MENTION_RE: Regex = Regex("@_user_\\d+")

internal val _POST_CONTENT_INVALID_RE: Regex = Regex(
    "content format of the post type is incorrect",
    RegexOption.IGNORE_CASE,
)

/** Reverse of [SUPPORTED_DOCUMENT_TYPES]: MIME → ext. */
internal val _DOCUMENT_MIME_TO_EXT: Map<String, String> =
    SUPPORTED_DOCUMENT_TYPES.entries.associate { (ext, mime) -> mime to ext }

internal const val _FEISHU_IMAGE_UPLOAD_TYPE: String = "message"
internal const val _FEISHU_FILE_UPLOAD_TYPE: String = "stream"
internal val _FEISHU_OPUS_UPLOAD_EXTENSIONS: Set<String> = setOf(".ogg", ".opus")
internal val _FEISHU_MEDIA_UPLOAD_EXTENSIONS: Set<String> = setOf(".mp4", ".mov", ".avi", ".m4v")
internal val _FEISHU_DOC_UPLOAD_TYPES: Map<String, String> = mapOf(
    ".pdf" to "pdf",
    ".doc" to "doc",
    ".docx" to "doc",
    ".xls" to "xls",
    ".xlsx" to "xls",
    ".ppt" to "ppt",
    ".pptx" to "ppt",
)

internal const val _MAX_TEXT_INJECT_BYTES: Int = 100 * 1024
internal const val _FEISHU_CONNECT_ATTEMPTS: Int = 3
internal const val _FEISHU_SEND_ATTEMPTS: Int = 3
internal const val _FEISHU_APP_LOCK_SCOPE: String = "feishu-app-id"
internal const val _DEFAULT_TEXT_BATCH_DELAY_SECONDS: Double = 0.6
internal const val _DEFAULT_TEXT_BATCH_MAX_MESSAGES: Int = 8
internal const val _DEFAULT_TEXT_BATCH_MAX_CHARS: Int = 4000
internal const val _DEFAULT_MEDIA_BATCH_DELAY_SECONDS: Double = 0.8
internal const val _DEFAULT_DEDUP_CACHE_SIZE: Int = 2048
internal const val _DEFAULT_WEBHOOK_HOST: String = "127.0.0.1"
internal const val _DEFAULT_WEBHOOK_PORT: Int = 8765
internal const val _DEFAULT_WEBHOOK_PATH: String = "/feishu/webhook"

// 24 hours — matches openclaw
internal const val _FEISHU_DEDUP_TTL_SECONDS: Int = 24 * 60 * 60
// 10 minutes sender-name cache
internal const val _FEISHU_SENDER_NAME_TTL_SECONDS: Int = 10 * 60
// 1 MB body limit
internal const val _FEISHU_WEBHOOK_MAX_BODY_BYTES: Int = 1 * 1024 * 1024
// sliding window for rate limiter
internal const val _FEISHU_WEBHOOK_RATE_WINDOW_SECONDS: Int = 60
// max requests per window per IP — matches openclaw
internal const val _FEISHU_WEBHOOK_RATE_LIMIT_MAX: Int = 120
// max tracked keys (prevents unbounded growth)
internal const val _FEISHU_WEBHOOK_RATE_MAX_KEYS: Int = 4096
// max seconds to read request body
internal const val _FEISHU_WEBHOOK_BODY_TIMEOUT_SECONDS: Int = 30
// consecutive error responses before WARNING log
internal const val _FEISHU_WEBHOOK_ANOMALY_THRESHOLD: Int = 25
// anomaly tracker TTL (6 hours) — matches openclaw
internal const val _FEISHU_WEBHOOK_ANOMALY_TTL_SECONDS: Int = 6 * 60 * 60
// card action token dedup window (15 min)
internal const val _FEISHU_CARD_ACTION_DEDUP_TTL_SECONDS: Int = 15 * 60
// LRU size for tracking sent message IDs
internal const val _FEISHU_BOT_MSG_TRACK_SIZE: Int = 512
// reply target withdrawn/missing → create fallback
internal val _FEISHU_REPLY_FALLBACK_CODES: Set<Int> = setOf(230011, 231003)
internal const val _FEISHU_REACTION_IN_PROGRESS: String = "Typing"
internal const val _FEISHU_REACTION_FAILURE: String = "CrossMark"
// LRU cache size for the processing-reaction dedup. Sized for typical
// burst throughput; tuned to bound memory on repeat delete-failures, not
// a capacity plan.
internal const val _FEISHU_PROCESSING_REACTION_CACHE_SIZE: Int = 1024

private val _PREFERRED_LOCALES = arrayOf("zh_cn", "en_us")
private val _MARKDOWN_SPECIAL_CHARS_RE = Regex("([\\\\`*_{}\\[\\]()#+\\-!|>~])")
private val _MARKDOWN_LINK_RE = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")
private val _MARKDOWN_FENCE_OPEN_RE = Regex("^```([^\n`]*)\\s*$")
private val _MARKDOWN_FENCE_CLOSE_RE = Regex("^```\\s*$")

// ---------------------------------------------------------------------------
// Markdown rendering helpers (ported from feishu.py 352-505)
// ---------------------------------------------------------------------------

internal fun _escapeMarkdownText(text: String): String =
    _MARKDOWN_SPECIAL_CHARS_RE.replace(text) { "\\${it.groupValues[1]}" }

internal fun _toBoolean(value: Any?): Boolean =
    value == true || value == 1 || value == "true"

internal fun _isStyleEnabled(style: Map<String, Any?>?, key: String): Boolean {
    if (style == null) return false
    return _toBoolean(style[key])
}

internal fun _wrapInlineCode(text: String): String {
    val runs = Regex("`+").findAll(text).map { it.value.length }.toList()
    val maxRun = (listOf(0) + runs).max()
    val fence = "`".repeat(maxRun + 1)
    val body = if (text.startsWith("`") || text.endsWith("`")) " $text " else text
    return "$fence$body$fence"
}

/**
 * Strip markdown formatting to plain text for Feishu text fallbacks.
 *
 * Delegates common markdown stripping to [stripMarkdown] and adds
 * Feishu-specific patterns (blockquotes, strikethrough, underline tags,
 * horizontal rules, CRLF normalisation).
 *
 * Mirrors `_strip_markdown_to_plain_text` (feishu.py 410).
 */
internal fun _stripMarkdownToPlainText(text: String): String {
    var plain = text.replace("\r\n", "\n")
    plain = _MARKDOWN_LINK_RE.replace(plain) { m ->
        "${m.groupValues[1]} (${m.groupValues[2].trim()})"
    }
    plain = Regex("^>\\s?", RegexOption.MULTILINE).replace(plain, "")
    plain = Regex("^\\s*---+\\s*$", RegexOption.MULTILINE).replace(plain, "---")
    plain = Regex("~~([^~\n]+)~~").replace(plain, "$1")
    plain = Regex("<u>([\\s\\S]*?)</u>").replace(plain, "$1")
    plain = stripMarkdown(plain)
    return plain
}

/**
 * Coerce value to int with optional default and minimum constraint.
 *
 * Mirrors `_coerce_int` (feishu.py 428).
 */
internal fun _coerceInt(value: Any?, default: Int? = null, minValue: Int = 0): Int? {
    val parsed: Int = when (value) {
        null -> return default
        is Int -> value
        is Long -> value.toInt()
        is Number -> {
            val d = value.toDouble()
            if (d.isNaN() || d.isInfinite()) return default
            d.toInt()
        }
        is Boolean -> if (value) 1 else 0
        is String -> value.trim().toIntOrNull() ?: return default
        else -> return default
    }
    return if (parsed >= minValue) parsed else default
}

/**
 * Coerce value to int, always returning a valid int (never null).
 *
 * Mirrors `_coerce_required_int` (feishu.py 437).
 */
internal fun _coerceRequiredInt(value: Any?, default: Int, minValue: Int = 0): Int {
    val parsed = _coerceInt(value, default = default, minValue = minValue)
    return parsed ?: default
}

/**
 * Check if Feishu/Lark dependencies are available. On Android always false
 * since lark_oapi cannot be dex'd.
 *
 * Mirrors `check_feishu_requirements` (feishu.py 1095).
 */
fun checkFeishuRequirements(): Boolean = FEISHU_AVAILABLE

internal fun _sanitizeFenceLanguage(language: String): String =
    language.trim().replace("\n", " ").replace("\r", " ")

@Suppress("UNCHECKED_CAST")
internal fun _renderTextElement(element: Map<String, Any?>): String {
    val text = (element["text"] ?: "").toString()
    val style = element["style"] as? Map<String, Any?>

    if (_isStyleEnabled(style, "code")) {
        return _wrapInlineCode(text)
    }

    var rendered = _escapeMarkdownText(text)
    if (rendered.isEmpty()) return ""
    if (_isStyleEnabled(style, "bold")) rendered = "**$rendered**"
    if (_isStyleEnabled(style, "italic")) rendered = "*$rendered*"
    if (_isStyleEnabled(style, "underline")) rendered = "<u>$rendered</u>"
    if (_isStyleEnabled(style, "strikethrough")) rendered = "~~$rendered~~"
    return rendered
}

internal fun _renderCodeBlockElement(element: Map<String, Any?>): String {
    val language = _sanitizeFenceLanguage(
        (element["language"] ?: "").toString().ifEmpty { (element["lang"] ?: "").toString() }
    )
    val rawCode = (element["text"] ?: "").toString().ifEmpty { (element["content"] ?: "").toString() }
    val code = rawCode.replace("\r\n", "\n")
    val trailingNewline = if (code.endsWith("\n")) "" else "\n"
    return "```$language\n$code$trailingNewline```"
}

internal fun _buildMarkdownPostPayload(content: String): String {
    val rows = _buildMarkdownPostRows(content)
    val outer = JSONObject()
    val zhCn = JSONObject()
    val contentArr = org.json.JSONArray()
    for (row in rows) {
        val rowArr = org.json.JSONArray()
        for (item in row) {
            val cell = JSONObject()
            for ((k, v) in item) cell.put(k, v)
            rowArr.put(cell)
        }
        contentArr.put(rowArr)
    }
    zhCn.put("content", contentArr)
    outer.put("zh_cn", zhCn)
    return outer.toString()
}

internal fun _buildMarkdownPostRows(content: String): List<List<Map<String, String>>> {
    if (content.isEmpty()) return listOf(listOf(mapOf("tag" to "md", "text" to "")))
    if (!content.contains("```")) return listOf(listOf(mapOf("tag" to "md", "text" to content)))

    val rows = mutableListOf<List<Map<String, String>>>()
    var current = mutableListOf<String>()
    var inCodeBlock = false

    fun flushCurrent() {
        if (current.isEmpty()) return
        val segment = current.joinToString("\n")
        if (segment.isNotBlank()) {
            rows.add(listOf(mapOf("tag" to "md", "text" to segment)))
        }
        current = mutableListOf()
    }

    for (rawLine in content.split("\n")) {
        val strippedLine = rawLine.trim()
        val isFence = if (inCodeBlock) {
            _MARKDOWN_FENCE_CLOSE_RE.matchEntire(strippedLine) != null
        } else {
            _MARKDOWN_FENCE_OPEN_RE.matchEntire(strippedLine) != null
        }

        if (isFence) {
            if (!inCodeBlock) flushCurrent()
            current.add(rawLine)
            inCodeBlock = !inCodeBlock
            if (!inCodeBlock) flushCurrent()
            continue
        }

        current.add(rawLine)
    }

    flushCurrent()
    return rows.ifEmpty { listOf(listOf(mapOf("tag" to "md", "text" to content))) }
}

@Suppress("UNCHECKED_CAST")
internal fun _renderPostElement(
    element: Any?,
    imageKeys: MutableList<String>,
    mediaRefs: MutableList<FeishuPostMediaRef>,
    mentionedIds: MutableList<String>,
): String {
    if (element is String) return element
    val elementMap = element as? Map<String, Any?> ?: return ""

    val tag = (elementMap["tag"] ?: "").toString().trim().lowercase()
    when (tag) {
        "text" -> return _renderTextElement(elementMap)
        "a" -> {
            val href = (elementMap["href"] ?: "").toString().trim()
            val label = (elementMap["text"] ?: href).toString().trim()
            if (label.isEmpty()) return ""
            val escapedLabel = _escapeMarkdownText(label)
            return if (href.isNotEmpty()) "[$escapedLabel]($href)" else escapedLabel
        }
        "at" -> {
            val mentionedId = (elementMap["open_id"] ?: "").toString().trim()
                .ifEmpty { (elementMap["user_id"] ?: "").toString().trim() }
            if (mentionedId.isNotEmpty() && mentionedId !in mentionedIds) {
                mentionedIds.add(mentionedId)
            }
            val displayName = (elementMap["user_name"] ?: "").toString().trim()
                .ifEmpty { (elementMap["name"] ?: "").toString().trim() }
                .ifEmpty { (elementMap["text"] ?: "").toString().trim() }
                .ifEmpty { mentionedId }
            return if (displayName.isNotEmpty()) "@${_escapeMarkdownText(displayName)}" else "@"
        }
        "img", "image" -> {
            val imageKey = (elementMap["image_key"] ?: "").toString().trim()
            if (imageKey.isNotEmpty() && imageKey !in imageKeys) imageKeys.add(imageKey)
            val alt = (elementMap["text"] ?: "").toString().trim()
                .ifEmpty { (elementMap["alt"] ?: "").toString().trim() }
            return if (alt.isNotEmpty()) "[Image: $alt]" else "[Image]"
        }
        "media", "file", "audio", "video" -> {
            val fileKey = (elementMap["file_key"] ?: "").toString().trim()
            val fileName = (elementMap["file_name"] ?: "").toString().trim()
                .ifEmpty { (elementMap["title"] ?: "").toString().trim() }
                .ifEmpty { (elementMap["text"] ?: "").toString().trim() }
            if (fileKey.isNotEmpty()) {
                mediaRefs.add(
                    FeishuPostMediaRef(
                        file_key = fileKey,
                        file_name = fileName,
                        resource_type = if (tag == "audio" || tag == "video") tag else "file",
                    )
                )
            }
            return if (fileName.isNotEmpty()) "[Attachment: $fileName]" else "[Attachment]"
        }
        "emotion", "emoji" -> {
            val label = (elementMap["text"] ?: "").toString().trim()
                .ifEmpty { (elementMap["emoji_type"] ?: "").toString().trim() }
            return if (label.isNotEmpty()) ":${_escapeMarkdownText(label)}:" else "[Emoji]"
        }
        "br" -> return "\n"
        "hr", "divider" -> return "\n\n---\n\n"
        "code" -> {
            val code = (elementMap["text"] ?: "").toString()
                .ifEmpty { (elementMap["content"] ?: "").toString() }
            return if (code.isNotEmpty()) _wrapInlineCode(code) else ""
        }
        "code_block", "pre" -> return _renderCodeBlockElement(elementMap)
    }

    val nestedParts = mutableListOf<String>()
    for (key in listOf("text", "title", "content", "children", "elements")) {
        val value = elementMap[key]
        val extracted = _renderNestedPost(value, imageKeys, mediaRefs, mentionedIds)
        if (extracted.isNotEmpty()) nestedParts.add(extracted)
    }
    return nestedParts.filter { it.isNotEmpty() }.joinToString(" ")
}

@Suppress("UNCHECKED_CAST")
internal fun _renderNestedPost(
    value: Any?,
    imageKeys: MutableList<String>,
    mediaRefs: MutableList<FeishuPostMediaRef>,
    mentionedIds: MutableList<String>,
): String {
    if (value is String) return _escapeMarkdownText(value)
    if (value is List<*>) {
        return value
            .asSequence()
            .map { _renderNestedPost(it, imageKeys, mediaRefs, mentionedIds) }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }
    if (value is Map<*, *>) {
        val direct = _renderPostElement(value, imageKeys, mediaRefs, mentionedIds)
        if (direct.isNotEmpty()) return direct
        return (value as Map<String, Any?>).values
            .asSequence()
            .map { _renderNestedPost(it, imageKeys, mediaRefs, mentionedIds) }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }
    return ""
}

// ---------------------------------------------------------------------------
// Text normalization constants (ported from feishu.py 234-270)
// ---------------------------------------------------------------------------

private val _MENTION_PLACEHOLDER_RE = Regex("@_user_\\d+")
private val _WHITESPACE_RE = Regex("\\s+")
private val _MULTISPACE_RE = Regex("[ \\t]{2,}")

private val _SUPPORTED_CARD_TEXT_KEYS = arrayOf(
    "title", "text", "content", "label", "value", "name",
    "summary", "subtitle", "description", "placeholder", "hint",
)
private val _SKIP_TEXT_KEYS = setOf(
    "tag", "type", "msg_type", "image_key", "user_id", "open_id",
    "union_id", "url", "href", "link", "token", "template", "locale",
)

// ---------------------------------------------------------------------------
// General text utilities (ported from feishu.py 1012-1029)
// ---------------------------------------------------------------------------

internal fun _normalizeFeishuText(text: String?): String {
    var cleaned = _MENTION_PLACEHOLDER_RE.replace(text ?: "", " ")
    cleaned = cleaned.replace("\r\n", "\n").replace("\r", "\n")
    cleaned = cleaned.split("\n").joinToString("\n") { _WHITESPACE_RE.replace(it, " ").trim() }
    cleaned = cleaned.split("\n").filter { it.isNotEmpty() }.joinToString("\n")
    cleaned = _MULTISPACE_RE.replace(cleaned, " ")
    return cleaned.trim()
}

internal fun _uniqueLines(lines: List<String>): List<String> {
    val seen = HashSet<String>()
    val unique = mutableListOf<String>()
    for (line in lines) {
        if (line.isEmpty() || line in seen) continue
        seen.add(line)
        unique.add(line)
    }
    return unique
}

internal fun _firstNonEmptyText(vararg values: Any?): String {
    for (value in values) {
        when (value) {
            is String -> {
                val normalized = _normalizeFeishuText(value)
                if (normalized.isNotEmpty()) return normalized
            }
            null, is Map<*, *>, is List<*> -> continue
            else -> {
                val normalized = _normalizeFeishuText(value.toString())
                if (normalized.isNotEmpty()) return normalized
            }
        }
    }
    return ""
}

internal fun _walkNodes(value: Any?): Sequence<Any?> = sequence {
    when (value) {
        is Map<*, *> -> {
            yield(value)
            for (item in value.values) yieldAll(_walkNodes(item))
        }
        is List<*> -> {
            for (item in value) yieldAll(_walkNodes(item))
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun _findFirstText(payload: Any?, keys: Array<String>): String {
    for (node in _walkNodes(payload)) {
        val dict = node as? Map<String, Any?> ?: continue
        for (key in keys) {
            val value = dict[key]
            if (value is String) {
                val normalized = _normalizeFeishuText(value)
                if (normalized.isNotEmpty()) return normalized
            }
        }
    }
    return ""
}

@Suppress("UNCHECKED_CAST")
internal fun _findHeaderTitle(payload: Any?): String {
    val dict = payload as? Map<String, Any?> ?: return ""
    val header = dict["header"] as? Map<String, Any?> ?: return ""
    val title = header["title"]
    if (title is Map<*, *>) {
        @Suppress("UNCHECKED_CAST")
        val titleMap = title as Map<String, Any?>
        return _firstNonEmptyText(titleMap["content"], titleMap["text"], titleMap["name"])
    }
    return _normalizeFeishuText(title?.toString() ?: "")
}

@Suppress("UNCHECKED_CAST")
internal fun _buildMediaRefFromPayload(payload: Map<String, Any?>, resourceType: String): FeishuPostMediaRef {
    val fileKey = (payload["file_key"] ?: "").toString().trim()
    val fileName = _firstNonEmptyText(
        payload["file_name"], payload["title"], payload["text"]
    )
    val effectiveType = if (resourceType == "audio" || resourceType == "video") resourceType else "file"
    return FeishuPostMediaRef(file_key = fileKey, file_name = fileName, resource_type = effectiveType)
}

internal fun _attachmentPlaceholder(fileName: String): String {
    val normalizedName = _normalizeFeishuText(fileName)
    return if (normalizedName.isNotEmpty()) "[Attachment: $normalizedName]" else FALLBACK_ATTACHMENT_TEXT
}

@Suppress("UNCHECKED_CAST")
internal fun _collectTextSegments(value: Any?, inRichBlock: Boolean): List<String> {
    if (value is String) {
        return if (inRichBlock) {
            val normalized = _normalizeFeishuText(value)
            if (normalized.isNotEmpty()) listOf(normalized) else emptyList()
        } else emptyList()
    }
    if (value is List<*>) {
        val segments = mutableListOf<String>()
        for (item in value) segments.addAll(_collectTextSegments(item, inRichBlock))
        return segments
    }
    val dict = value as? Map<String, Any?> ?: return emptyList()

    val tag = (dict["tag"] ?: dict["type"] ?: "").toString().trim().lowercase()
    val nextInRichBlock = inRichBlock || tag in setOf(
        "plain_text", "lark_md", "markdown", "note", "div",
        "column_set", "column", "action", "button", "select_static", "date_picker"
    )

    val segments = mutableListOf<String>()
    for (key in _SUPPORTED_CARD_TEXT_KEYS) {
        val item = dict[key]
        if (item is String && nextInRichBlock) {
            val normalized = _normalizeFeishuText(item)
            if (normalized.isNotEmpty()) segments.add(normalized)
        }
    }
    for ((k, item) in dict) {
        if (k in _SKIP_TEXT_KEYS) continue
        segments.addAll(_collectTextSegments(item, nextInRichBlock))
    }
    return segments
}

internal fun _collectCardLines(payload: Any?): List<String> {
    val lines = _collectTextSegments(payload, inRichBlock = false)
    val normalized = lines.map { _normalizeFeishuText(it) }
    return _uniqueLines(normalized.filter { it.isNotEmpty() })
}

@Suppress("UNCHECKED_CAST")
internal fun _collectActionLabels(payload: Any?): List<String> {
    val labels = mutableListOf<String>()
    for (item in _walkNodes(payload)) {
        val dict = item as? Map<String, Any?> ?: continue
        val tag = (dict["tag"] ?: dict["type"] ?: "").toString().trim().lowercase()
        if (tag !in setOf("button", "select_static", "overflow", "date_picker", "picker")) continue
        val label = _firstNonEmptyText(
            dict["text"], dict["name"], dict["value"],
            _findFirstText(dict, arrayOf("text", "content", "name", "value"))
        )
        if (label.isNotEmpty()) labels.add(label)
    }
    return _uniqueLines(labels)
}

@Suppress("UNCHECKED_CAST")
internal fun _collectForwardEntries(payload: Map<String, Any?>): List<String> {
    val candidates = mutableListOf<Any?>()
    for (key in listOf("messages", "items", "message_list", "records", "content")) {
        val value = payload[key]
        if (value is List<*>) candidates.addAll(value)
    }
    val entries = mutableListOf<String>()
    for (item in candidates) {
        if (item !is Map<*, *>) {
            val text = _normalizeFeishuText((item ?: "").toString())
            if (text.isNotEmpty()) entries.add("- $text")
            continue
        }
        val itemMap = item as Map<String, Any?>
        val sender = _firstNonEmptyText(
            itemMap["sender_name"], itemMap["user_name"], itemMap["sender"], itemMap["name"]
        )
        val nestedType = (itemMap["message_type"] ?: itemMap["msg_type"] ?: "").toString().trim().lowercase()
        val body = if (nestedType == "post") {
            parseFeishuPostPayload(itemMap["content"] ?: itemMap).text_content
        } else {
            _firstNonEmptyText(
                itemMap["text"], itemMap["summary"], itemMap["preview"], itemMap["content"],
                _findFirstText(itemMap, arrayOf("text", "content", "summary", "preview", "title"))
            )
        }
        val normalizedBody = _normalizeFeishuText(body)
        when {
            sender.isNotEmpty() && normalizedBody.isNotEmpty() -> entries.add("- $sender: $normalizedBody")
            normalizedBody.isNotEmpty() -> entries.add("- $normalizedBody")
        }
    }
    return _uniqueLines(entries)
}

// ---------------------------------------------------------------------------
// Post payload parsing (ported from feishu.py 508-580)
// ---------------------------------------------------------------------------

internal fun parseFeishuPostPayload(payload: Any?): FeishuPostParseResult {
    val resolved = _resolvePostPayload(payload)
    if (resolved.isEmpty()) {
        return FeishuPostParseResult(text_content = FALLBACK_POST_TEXT)
    }

    val imageKeys = mutableListOf<String>()
    val mediaRefs = mutableListOf<FeishuPostMediaRef>()
    val mentionedIds = mutableListOf<String>()
    val parts = mutableListOf<String>()

    val title = _normalizeFeishuText((resolved["title"] ?: "").toString().trim())
    if (title.isNotEmpty()) parts.add(title)

    val content = resolved["content"] as? List<*> ?: emptyList<Any?>()
    for (row in content) {
        if (row !is List<*>) continue
        val rowText = _normalizeFeishuText(
            row.joinToString("") { _renderPostElement(it, imageKeys, mediaRefs, mentionedIds) }
        )
        if (rowText.isNotEmpty()) parts.add(rowText)
    }

    val textContent = parts.joinToString("\n").trim().ifEmpty { FALLBACK_POST_TEXT }
    return FeishuPostParseResult(
        text_content = textContent,
        image_keys = imageKeys,
        media_refs = mediaRefs,
        mentioned_ids = mentionedIds,
    )
}

@Suppress("UNCHECKED_CAST")
internal fun _resolvePostPayload(payload: Any?): Map<String, Any?> {
    val direct = _toPostPayload(payload)
    if (direct.isNotEmpty()) return direct
    val dict = payload as? Map<String, Any?> ?: return emptyMap()

    val wrapped = dict["post"]
    val wrappedDirect = _resolveLocalePayload(wrapped)
    if (wrappedDirect.isNotEmpty()) return wrappedDirect
    return _resolveLocalePayload(dict)
}

@Suppress("UNCHECKED_CAST")
internal fun _resolveLocalePayload(payload: Any?): Map<String, Any?> {
    val direct = _toPostPayload(payload)
    if (direct.isNotEmpty()) return direct
    val dict = payload as? Map<String, Any?> ?: return emptyMap()

    for (key in _PREFERRED_LOCALES) {
        val candidate = _toPostPayload(dict[key])
        if (candidate.isNotEmpty()) return candidate
    }
    for (value in dict.values) {
        val candidate = _toPostPayload(value)
        if (candidate.isNotEmpty()) return candidate
    }
    return emptyMap()
}

@Suppress("UNCHECKED_CAST")
internal fun _toPostPayload(candidate: Any?): Map<String, Any?> {
    val dict = candidate as? Map<String, Any?> ?: return emptyMap()
    val content = dict["content"] as? List<*> ?: return emptyMap()
    return mapOf(
        "title" to (dict["title"] ?: "").toString(),
        "content" to content,
    )
}

// ---------------------------------------------------------------------------
// Message normalization (ported from feishu.py 695-831)
// ---------------------------------------------------------------------------

@Suppress("UNCHECKED_CAST")
internal fun _loadFeishuPayload(rawContent: String): Map<String, Any?> {
    if (rawContent.isEmpty()) return emptyMap()
    return try {
        val parsed = JSONObject(rawContent)
        _jsonObjectToMap(parsed)
    } catch (e: Exception) {
        mapOf("text" to rawContent)
    }
}

@Suppress("UNCHECKED_CAST")
private fun _jsonObjectToMap(json: JSONObject): Map<String, Any?> {
    val result = mutableMapOf<String, Any?>()
    val keys = json.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        result[key] = _jsonValueToKotlin(json.opt(key))
    }
    return result
}

private fun _jsonArrayToList(json: org.json.JSONArray): List<Any?> {
    val result = mutableListOf<Any?>()
    for (i in 0 until json.length()) result.add(_jsonValueToKotlin(json.opt(i)))
    return result
}

private fun _jsonValueToKotlin(value: Any?): Any? = when (value) {
    null, JSONObject.NULL -> null
    is JSONObject -> _jsonObjectToMap(value)
    is org.json.JSONArray -> _jsonArrayToList(value)
    else -> value
}

internal fun _normalizeMergeForwardMessage(payload: Map<String, Any?>): FeishuNormalizedMessage {
    val title = _firstNonEmptyText(
        payload["title"], payload["summary"], payload["preview"],
        _findFirstText(payload, arrayOf("title", "summary", "preview", "description"))
    )
    val entries = _collectForwardEntries(payload)
    val lines = mutableListOf<String>()
    if (title.isNotEmpty()) lines.add(title)
    lines.addAll(entries.take(8))
    val textContent = lines.joinToString("\n").trim().ifEmpty { FALLBACK_FORWARD_TEXT }
    return FeishuNormalizedMessage(
        raw_type = "merge_forward",
        text_content = textContent,
        relation_kind = "merge_forward",
        metadata = mutableMapOf("entry_count" to entries.size, "title" to title),
    )
}

internal fun _normalizeShareChatMessage(payload: Map<String, Any?>): FeishuNormalizedMessage {
    val chatName = _firstNonEmptyText(
        payload["chat_name"], payload["name"], payload["title"],
        _findFirstText(payload, arrayOf("chat_name", "name", "title"))
    )
    val shareId = _firstNonEmptyText(
        payload["chat_id"], payload["open_chat_id"], payload["share_chat_id"]
    )
    val lines = mutableListOf<String>()
    lines.add(if (chatName.isNotEmpty()) "Shared chat: $chatName" else FALLBACK_SHARE_CHAT_TEXT)
    if (shareId.isNotEmpty()) lines.add("Chat ID: $shareId")
    return FeishuNormalizedMessage(
        raw_type = "share_chat",
        text_content = lines.joinToString("\n"),
        relation_kind = "share_chat",
        metadata = mutableMapOf("chat_id" to shareId, "chat_name" to chatName),
    )
}

@Suppress("UNCHECKED_CAST")
internal fun _normalizeInteractiveMessage(messageType: String, payload: Map<String, Any?>): FeishuNormalizedMessage {
    val cardPayload = payload["card"] as? Map<String, Any?> ?: payload
    val title = _firstNonEmptyText(
        _findHeaderTitle(cardPayload),
        payload["title"],
        _findFirstText(cardPayload, arrayOf("title", "summary", "subtitle"))
    )
    val bodyLines = _collectCardLines(cardPayload)
    val actions = _collectActionLabels(cardPayload)

    val lines = mutableListOf<String>()
    if (title.isNotEmpty()) lines.add(title)
    for (line in bodyLines) if (line != title) lines.add(line)
    if (actions.isNotEmpty()) lines.add("Actions: ${actions.joinToString(", ")}")

    val textContent = lines.take(12).joinToString("\n").trim().ifEmpty { FALLBACK_INTERACTIVE_TEXT }
    return FeishuNormalizedMessage(
        raw_type = messageType,
        text_content = textContent,
        relation_kind = "interactive",
        metadata = mutableMapOf("title" to title, "actions" to actions),
    )
}

internal fun normalizeFeishuMessage(messageType: String, rawContent: String): FeishuNormalizedMessage {
    val normalizedType = (messageType).trim().lowercase()
    val payload = _loadFeishuPayload(rawContent)

    if (normalizedType == "text") {
        return FeishuNormalizedMessage(
            raw_type = normalizedType,
            text_content = _normalizeFeishuText((payload["text"] ?: "").toString()),
        )
    }
    if (normalizedType == "post") {
        val parsedPost = parseFeishuPostPayload(payload)
        return FeishuNormalizedMessage(
            raw_type = normalizedType,
            text_content = parsedPost.text_content,
            image_keys = parsedPost.image_keys.toMutableList(),
            media_refs = parsedPost.media_refs.toMutableList(),
            mentioned_ids = parsedPost.mentioned_ids.toMutableList(),
            relation_kind = "post",
        )
    }
    if (normalizedType == "image") {
        val imageKey = (payload["image_key"] ?: "").toString().trim()
        val altText = _normalizeFeishuText(
            (payload["text"] ?: "").toString().ifEmpty { (payload["alt"] ?: "").toString() }
                .ifEmpty { FALLBACK_IMAGE_TEXT }
        )
        return FeishuNormalizedMessage(
            raw_type = normalizedType,
            text_content = if (altText != FALLBACK_IMAGE_TEXT) altText else "",
            preferred_message_type = "photo",
            image_keys = if (imageKey.isNotEmpty()) mutableListOf(imageKey) else mutableListOf(),
            relation_kind = "image",
        )
    }
    if (normalizedType in setOf("file", "audio", "media")) {
        val mediaRef = _buildMediaRefFromPayload(payload, resourceType = normalizedType)
        val placeholder = _attachmentPlaceholder(mediaRef.file_name)
        return FeishuNormalizedMessage(
            raw_type = normalizedType,
            text_content = "",
            preferred_message_type = if (normalizedType == "audio") "audio" else "document",
            media_refs = if (mediaRef.file_key.isNotEmpty()) mutableListOf(mediaRef) else mutableListOf(),
            relation_kind = normalizedType,
            metadata = mutableMapOf("placeholder_text" to placeholder),
        )
    }
    if (normalizedType == "merge_forward") return _normalizeMergeForwardMessage(payload)
    if (normalizedType == "share_chat") return _normalizeShareChatMessage(payload)
    if (normalizedType in setOf("interactive", "card")) return _normalizeInteractiveMessage(normalizedType, payload)

    return FeishuNormalizedMessage(raw_type = normalizedType, text_content = "")
}

// -------------------------------------------------------------------------
// Media-type helpers (feishu.py 2647-3330)
// -------------------------------------------------------------------------

internal val _IMAGE_EXTENSIONS: Set<String> = setOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp")
internal val _AUDIO_EXTENSIONS: Set<String> = setOf(".ogg", ".mp3", ".wav", ".m4a", ".aac", ".flac", ".opus", ".webm")
internal val _VIDEO_EXTENSIONS: Set<String> = setOf(".mp4", ".mov", ".avi", ".mkv", ".webm", ".m4v", ".3gp")

/** Kotlin substitute for Python `mimetypes.guess_type` — minimal lookup by extension. */
private val _MIMETYPES_BY_EXT: Map<String, String> = mapOf(
    ".jpg" to "image/jpeg",
    ".jpeg" to "image/jpeg",
    ".png" to "image/png",
    ".gif" to "image/gif",
    ".webp" to "image/webp",
    ".bmp" to "image/bmp",
    ".mp3" to "audio/mpeg",
    ".ogg" to "audio/ogg",
    ".wav" to "audio/wav",
    ".m4a" to "audio/mp4",
    ".aac" to "audio/aac",
    ".flac" to "audio/flac",
    ".opus" to "audio/opus",
    ".mp4" to "video/mp4",
    ".mov" to "video/quicktime",
    ".avi" to "video/x-msvideo",
    ".mkv" to "video/x-matroska",
    ".webm" to "video/webm",
    ".m4v" to "video/mp4",
    ".3gp" to "video/3gpp",
    ".pdf" to "application/pdf",
    ".md" to "text/markdown",
    ".txt" to "text/plain",
    ".log" to "text/plain",
    ".zip" to "application/zip",
    ".docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ".xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    ".pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
)

/** Python mimetypes.guess_extension reverse lookup: mime-type → extension. */
private val _EXT_BY_MIMETYPE: Map<String, String> = _MIMETYPES_BY_EXT.entries.associate { (k, v) -> v to k }

internal fun _pathSuffix(path: String): String {
    val stripped = path.substringBefore('?')
    val name = stripped.substringAfterLast('/')
    val dotIdx = name.lastIndexOf('.')
    if (dotIdx <= 0) return ""
    return name.substring(dotIdx).lowercase()
}

internal fun _pathBaseName(path: String): String = path.substringBefore('?').substringAfterLast('/')

internal fun _mimetypesGuessType(filename: String): String {
    val ext = _pathSuffix(filename ?: "")
    return _MIMETYPES_BY_EXT[ext].orEmpty()
}

internal fun _mimetypesGuessExtension(mimeType: String): String {
    val key = (mimeType ?: "").substringBefore(';').trim().lowercase()
    if (key.isEmpty()) return ""
    return _EXT_BY_MIMETYPE[key].orEmpty()
}

/**
 * Normalizes a HTTP Content-Type header by stripping parameters and lowercasing.
 * Falls back to `default` when empty.
 */
internal fun _normalizeMediaType(contentType: String?, default: String): String {
    val normalized = (contentType ?: "").substringBefore(';').trim().lowercase()
    return normalized.ifEmpty { default }
}

/**
 * Infers the default image MIME type for a given extension.
 *  ".jpg" / ".jpeg" → "image/jpeg"; others → "image/<ext>".
 */
internal fun _defaultImageMediaType(ext: String?): String {
    val normalized = (ext ?: "").lowercase()
    if (normalized == ".jpg" || normalized == ".jpeg") return "image/jpeg"
    val stripped = normalized.trimStart('.')
    return "image/" + stripped.ifEmpty { "jpeg" }
}

/**
 * Picks a file extension, preferring the filename's suffix, then the
 * Content-Type's canonical extension, otherwise `default`. Only returns
 * extensions contained in `allowed`.
 */
internal fun _guessExtension(filename: String?, contentType: String?, default: String, allowed: Set<String>): String {
    val fromFile = _pathSuffix(filename ?: "")
    if (fromFile in allowed) return fromFile
    val guessed = _mimetypesGuessExtension(contentType ?: "")
    if (guessed in allowed) return guessed
    return default
}

/**
 * Infers a reasonable file extension from a remote URL. Accepts only
 * supported image/audio/video/document extensions, else returns `default`.
 */
internal fun _guessRemoteExtension(url: String?, default: String): String {
    val ext = _pathSuffix(url ?: "")
    val knownExts = _IMAGE_EXTENSIONS + _AUDIO_EXTENSIONS + _VIDEO_EXTENSIONS + SUPPORTED_DOCUMENT_TYPES.keys
    return if (ext in knownExts) ext else default
}

/**
 * Derives a sensible filename from a remote URL, ensuring it has an extension.
 * Appends `default_ext` (or a MIME-type-derived extension) if missing.
 */
internal fun _deriveRemoteFilename(fileUrl: String?, contentType: String?, defaultName: String, defaultExt: String): String {
    val stripped = (fileUrl ?: "").substringBefore('?')
    var candidate = stripped.substringAfterLast('/').ifEmpty { defaultName }
    val ext = _pathSuffix(candidate)
    if (ext.isEmpty()) {
        val guessed = _mimetypesGuessExtension(contentType ?: "").ifEmpty { defaultExt }
        candidate = "$candidate$guessed"
    }
    return candidate
}

/**
 * Returns the MIME type for a document filename, using
 * [SUPPORTED_DOCUMENT_TYPES] first, then mimetypes lookup,
 * then `application/octet-stream`.
 */
internal fun _guessDocumentMediaType(filename: String?): String {
    val ext = _pathSuffix(filename ?: "")
    SUPPORTED_DOCUMENT_TYPES[ext]?.let { return it }
    val guessed = _mimetypesGuessType(filename ?: "")
    return guessed.ifEmpty { "application/octet-stream" }
}

/**
 * Infers the MIME type from a filename's extension. Unlike
 * [_guessDocumentMediaType], this method understands video / audio / image
 * extensions that aren't in [SUPPORTED_DOCUMENT_TYPES].
 */
internal fun _guessMediaTypeFromFilename(filename: String?): String {
    val guessed = _mimetypesGuessType(filename ?: "").lowercase()
    if (guessed.isNotEmpty()) return guessed
    val ext = _pathSuffix(filename ?: "")
    return when {
        ext in _VIDEO_EXTENSIONS -> "video/${ext.trimStart('.')}"
        ext in _AUDIO_EXTENSIONS -> "audio/${ext.trimStart('.')}"
        ext in _IMAGE_EXTENSIONS -> _defaultImageMediaType(ext)
        else -> ""
    }
}

/**
 * Extracts the human-readable display name from a cached document path.
 * Mirrors Python regex `[^\w.\- ]` replacement with `_`.
 */
internal fun _displayNameFromCachedPath(path: String?): String {
    val baseName = _pathBaseName(path ?: "")
    val parts = baseName.split("_", limit = 3)
    val displayName = if (parts.size >= 3) parts[2] else baseName
    return Regex("[^\\w.\\- ]").replace(displayName, "_")
}

/**
 * Translates a raw Feishu chat_type (e.g. "p2p", "group", "topic") to the
 * internal chat-kind taxonomy used across the gateway.
 */
internal fun _mapChatType(rawChatType: String?): String {
    val normalized = (rawChatType ?: "").trim().lowercase()
    return when (normalized) {
        "p2p" -> "dm"
        "group", "topic" -> "group"
        else -> normalized.ifEmpty { "unknown" }
    }
}

/**
 * Returns the "source chat type" for a Feishu event, accounting for both
 * the chat_info payload (group/forum) and the event-level chat_type (p2p).
 * Mirrors `FeishuAdapter._resolve_source_chat_type` (feishu.py 3217).
 */
internal fun _resolveSourceChatType(chatInfo: Map<String, Any?>, eventChatType: String?): String {
    val resolved = (chatInfo["type"] as? String ?: "").trim().lowercase()
    if (resolved in setOf("group", "forum")) return resolved
    if ((eventChatType ?: "") == "p2p") return "dm"
    return "group"
}

/**
 * Extracts best-effort text from a Feishu raw message content.
 *
 * Runs [normalizeFeishuMessage] and returns `text_content` if populated,
 * else falls back to the metadata's `placeholder_text`. Returns empty
 * string when neither is available.
 *
 * Mirrors `FeishuAdapter._extract_text_from_raw_content` (feishu.py 3318).
 */
internal fun _extractTextFromRawContent(msgType: String, rawContent: String): String {
    val normalized = normalizeFeishuMessage(msgType, rawContent)
    if (normalized.text_content.isNotEmpty()) return normalized.text_content
    val placeholder = normalized.metadata["placeholder_text"]?.toString()?.trim().orEmpty()
    return placeholder
}

// ---------------------------------------------------------------------------
// QR onboarding helpers (ported from gateway/platforms/feishu.py:4047-4361)
// ---------------------------------------------------------------------------

/** Accounts-domain base URLs for the Feishu/Lark registration endpoint. */
internal val _ONBOARD_ACCOUNTS_URLS: Map<String, String> = mapOf(
    "feishu" to "https://accounts.feishu.cn",
    "lark" to "https://accounts.larksuite.com",
)

/** Open-platform base URLs for the Feishu/Lark OpenAPI. */
internal val _ONBOARD_OPEN_URLS: Map<String, String> = mapOf(
    "feishu" to "https://open.feishu.cn",
    "lark" to "https://open.larksuite.com",
)

internal const val _REGISTRATION_PATH: String = "/oauth/v1/app/registration"
internal const val _ONBOARD_REQUEST_TIMEOUT_S: Int = 10

/**
 * True when the lark_oapi SDK is importable. Always false on Android — the
 * SDK pulls in javax.servlet-api which cannot be dex'd. Mirrors the Python
 * module-level flag set by the conditional `import lark_oapi` at the top
 * of feishu.py (line 84).
 */
const val FEISHU_AVAILABLE: Boolean = false

/** Placeholder SDK domain constants — unused on Android since FEISHU_AVAILABLE=false. */
internal val FEISHU_DOMAIN: String? = null
internal val LARK_DOMAIN: String? = null

/** Dedicated OkHttp client for the onboarding flow (10s timeouts). */
private val _onboardingHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(_ONBOARD_REQUEST_TIMEOUT_S.toLong(), TimeUnit.SECONDS)
        .readTimeout(_ONBOARD_REQUEST_TIMEOUT_S.toLong(), TimeUnit.SECONDS)
        .writeTimeout(_ONBOARD_REQUEST_TIMEOUT_S.toLong(), TimeUnit.SECONDS)
        .build()
}

/**
 * Return the accounts-domain base URL for the given domain key. Unknown
 * domains fall back to the Feishu URL.
 *
 * Mirrors `_accounts_base_url` (feishu.py 4047).
 */
internal fun _accountsBaseUrl(domain: String): String =
    _ONBOARD_ACCOUNTS_URLS[domain] ?: _ONBOARD_ACCOUNTS_URLS.getValue("feishu")

/**
 * Return the open-platform base URL for the given domain key. Unknown
 * domains fall back to the Feishu URL.
 *
 * Mirrors `_onboard_open_base_url` (feishu.py 4051).
 */
internal fun _onboardOpenBaseUrl(domain: String): String =
    _ONBOARD_OPEN_URLS[domain] ?: _ONBOARD_OPEN_URLS.getValue("feishu")

/**
 * POST form-encoded data to the registration endpoint, return parsed JSON.
 *
 * The registration endpoint returns JSON even on 4xx (e.g. poll returns
 * `authorization_pending` as a 400). We always parse the body regardless of
 * HTTP status, and re-throw the HTTP error only when the body is empty or
 * unparseable.
 *
 * Mirrors `_post_registration` (feishu.py 4055).
 */
internal fun _postRegistration(baseUrl: String, body: Map<String, String>): Map<String, Any?> {
    val url = "$baseUrl$_REGISTRATION_PATH"
    val formBuilder = okhttp3.FormBody.Builder(Charsets.UTF_8)
    body.forEach { (k, v) -> formBuilder.add(k, v) }
    val req = Request.Builder()
        .url(url)
        .post(formBuilder.build())
        .build()
    _onboardingHttpClient.newCall(req).execute().use { resp ->
        val text = resp.body?.string().orEmpty()
        if (text.isNotEmpty()) {
            try {
                return _jsonObjectToMap(JSONObject(text))
            } catch (_: org.json.JSONException) {
                if (!resp.isSuccessful) {
                    throw java.io.IOException("HTTP ${resp.code}: $text")
                }
                throw java.io.IOException("Non-JSON registration response: $text")
            }
        }
        if (!resp.isSuccessful) {
            throw java.io.IOException("HTTP ${resp.code} with empty body")
        }
        return emptyMap()
    }
}

/**
 * Verify the environment supports client_secret auth.
 *
 * Throws RuntimeException if not supported.
 *
 * Mirrors `_init_registration` (feishu.py 4078).
 */
internal fun _initRegistration(domain: String = "feishu") {
    val baseUrl = _accountsBaseUrl(domain)
    val res = _postRegistration(baseUrl, mapOf("action" to "init"))
    @Suppress("UNCHECKED_CAST")
    val methods = (res["supported_auth_methods"] as? List<Any?>) ?: emptyList()
    if ("client_secret" !in methods) {
        throw RuntimeException(
            "Feishu / Lark registration environment does not support client_secret auth. " +
                "Supported: $methods"
        )
    }
}

/**
 * Start the device-code flow. Returns device_code, qr_url, user_code,
 * interval, expire_in.
 *
 * Mirrors `_begin_registration` (feishu.py 4093).
 */
internal fun _beginRegistration(domain: String = "feishu"): Map<String, Any?> {
    val baseUrl = _accountsBaseUrl(domain)
    val res = _postRegistration(
        baseUrl,
        mapOf(
            "action" to "begin",
            "archetype" to "PersonalAgent",
            "auth_method" to "client_secret",
            "request_user_info" to "open_id",
        ),
    )
    val deviceCode = res["device_code"] as? String
    if (deviceCode.isNullOrEmpty()) {
        throw RuntimeException("Feishu / Lark registration did not return a device_code")
    }
    var qrUrl = (res["verification_uri_complete"] as? String).orEmpty()
    qrUrl += if ("?" in qrUrl) "&from=hermes&tp=hermes" else "?from=hermes&tp=hermes"
    val interval = (res["interval"] as? Number)?.toInt() ?: 5
    val expireIn = (res["expire_in"] as? Number)?.toInt() ?: 600
    return mapOf(
        "device_code" to deviceCode,
        "qr_url" to qrUrl,
        "user_code" to ((res["user_code"] as? String) ?: ""),
        "interval" to interval,
        "expire_in" to expireIn,
    )
}

/**
 * Poll until the user scans the QR code, or timeout/denial.
 *
 * Returns map with `app_id`, `app_secret`, `domain`, `open_id` on success.
 * Returns null on failure (access_denied / expired_token / timeout).
 *
 * Mirrors `_poll_registration` (feishu.py 4119).
 */
internal fun _pollRegistration(
    deviceCode: String,
    interval: Int,
    expireIn: Int,
    domain: String = "feishu",
): Map<String, Any?>? {
    val deadline = System.currentTimeMillis() / 1000.0 + expireIn
    var currentDomain = domain
    var domainSwitched = false

    while ((System.currentTimeMillis() / 1000.0) < deadline) {
        val baseUrl = _accountsBaseUrl(currentDomain)
        val res = try {
            _postRegistration(
                baseUrl,
                mapOf(
                    "action" to "poll",
                    "device_code" to deviceCode,
                    "tp" to "ob_app",
                ),
            )
        } catch (_: java.io.IOException) {
            Thread.sleep(interval * 1000L)
            continue
        } catch (_: org.json.JSONException) {
            Thread.sleep(interval * 1000L)
            continue
        }

        @Suppress("UNCHECKED_CAST")
        val userInfo = (res["user_info"] as? Map<String, Any?>) ?: emptyMap()
        val tenantBrand = userInfo["tenant_brand"] as? String
        if (tenantBrand == "lark" && !domainSwitched) {
            currentDomain = "lark"
            domainSwitched = true
        }

        val clientId = res["client_id"] as? String
        val clientSecret = res["client_secret"] as? String
        if (!clientId.isNullOrEmpty() && !clientSecret.isNullOrEmpty()) {
            return mapOf(
                "app_id" to clientId,
                "app_secret" to clientSecret,
                "domain" to currentDomain,
                "open_id" to userInfo["open_id"],
            )
        }

        val error = (res["error"] as? String).orEmpty()
        if (error == "access_denied" || error == "expired_token") {
            Log.w("FeishuOnboard", "Registration $error")
            return null
        }

        Thread.sleep(interval * 1000L)
    }

    Log.w("FeishuOnboard", "Poll timed out after ${expireIn}s")
    return null
}

/**
 * Render a QR code to a textual sink. On Android we always return false —
 * the UI layer renders the QR via a Compose bitmap widget instead. Caller
 * should fall back to displaying [url] as plain text.
 *
 * Mirrors `_render_qr` (feishu.py 4196), where the Python reference tries
 * to use the `qrcode` CLI module to print to stdout.
 */
@Suppress("UNUSED_PARAMETER")
internal fun _renderQr(url: String): Boolean = false

/**
 * Verify bot connectivity via /open-apis/bot/v3/info.
 *
 * On Android the lark_oapi SDK is never available, so this always takes
 * the HTTP fallback path. Returns `{"bot_name", "bot_open_id"}` on success,
 * null on any failure.
 *
 * Mirrors `probe_bot` (feishu.py 4210).
 */
fun probeBot(appId: String, appSecret: String, domain: String): Map<String, Any?>? {
    if (FEISHU_AVAILABLE) {
        return _probeBotSdk(appId, appSecret, domain)
    }
    return _probeBotHttp(appId, appSecret, domain)
}

/**
 * Build a lark Client for the given credentials and domain.
 *
 * Always returns null on Android — the SDK has non-dexable dependencies.
 * Mirrors `_build_onboard_client` (feishu.py 4221).
 */
@Suppress("UNUSED_PARAMETER")
internal fun _buildOnboardClient(appId: String, appSecret: String, domain: String): Any? = null

/**
 * Extract `bot_name` and `bot_open_id` from a /bot/v3/info response.
 *
 * Returns null when the response code is non-zero. Otherwise returns a
 * map with two keys (either value may itself be null if missing).
 *
 * Mirrors `_parse_bot_response` (feishu.py 4234).
 */
internal fun _parseBotResponse(data: Map<String, Any?>): Map<String, Any?>? {
    val code = (data["code"] as? Number)?.toInt()
    if (code != 0) return null
    @Suppress("UNCHECKED_CAST")
    val bot: Map<String, Any?> = (data["bot"] as? Map<String, Any?>)
        ?: ((data["data"] as? Map<String, Any?>)?.get("bot") as? Map<String, Any?>)
        ?: emptyMap()
    return mapOf(
        "bot_name" to bot["bot_name"],
        "bot_open_id" to bot["open_id"],
    )
}

/**
 * Probe bot info using lark_oapi SDK. Always returns null on Android.
 *
 * Mirrors `_probe_bot_sdk` (feishu.py 4245).
 */
@Suppress("UNUSED_PARAMETER")
internal fun _probeBotSdk(appId: String, appSecret: String, domain: String): Map<String, Any?>? =
    null

/**
 * Fallback probe using raw HTTP (the only path used on Android).
 *
 * 1. POST /open-apis/auth/v3/tenant_access_token/internal to get a token
 * 2. GET /open-apis/bot/v3/info with that token
 * 3. Extract bot name + open_id
 *
 * Mirrors `_probe_bot_http` (feishu.py 4261).
 */
internal fun _probeBotHttp(appId: String, appSecret: String, domain: String): Map<String, Any?>? {
    val baseUrl = _onboardOpenBaseUrl(domain)
    return try {
        val tokenBody = JSONObject().apply {
            put("app_id", appId)
            put("app_secret", appSecret)
        }.toString().toRequestBody("application/json".toMediaType())
        val tokenReq = Request.Builder()
            .url("$baseUrl/open-apis/auth/v3/tenant_access_token/internal")
            .post(tokenBody)
            .header("Content-Type", "application/json")
            .build()
        val tokenRes = _onboardingHttpClient.newCall(tokenReq).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (text.isEmpty()) return null
            _jsonObjectToMap(JSONObject(text))
        }
        val accessToken = tokenRes["tenant_access_token"] as? String
        if (accessToken.isNullOrEmpty()) return null

        val botReq = Request.Builder()
            .url("$baseUrl/open-apis/bot/v3/info")
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .build()
        val botRes = _onboardingHttpClient.newCall(botReq).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (text.isEmpty()) return null
            _jsonObjectToMap(JSONObject(text))
        }
        _parseBotResponse(botRes)
    } catch (e: java.io.IOException) {
        Log.d("FeishuOnboard", "HTTP probe failed: ${e.message}")
        null
    } catch (e: org.json.JSONException) {
        Log.d("FeishuOnboard", "HTTP probe failed: ${e.message}")
        null
    }
}

/**
 * Run the Feishu / Lark scan-to-create QR registration flow.
 *
 * On success, returns a map with keys: app_id, app_secret, domain, open_id,
 * bot_name, bot_open_id. Returns null on expected failures (network, auth
 * denied, timeout). Unexpected errors propagate.
 *
 * Mirrors `qr_register` (feishu.py 4294).
 */
fun qrRegister(
    initialDomain: String = "feishu",
    timeoutSeconds: Int = 600,
    onQrCodeReady: (qrUrl: String, userCode: String) -> Unit = { _, _ -> },
    onStatusUpdate: (status: String) -> Unit = { _ -> },
): Map<String, Any?>? {
    return try {
        _qrRegisterInner(initialDomain, timeoutSeconds, onQrCodeReady, onStatusUpdate)
    } catch (e: RuntimeException) {
        Log.w("FeishuOnboard", "Registration failed: ${e.message}")
        onStatusUpdate("error")
        null
    } catch (e: java.io.IOException) {
        Log.w("FeishuOnboard", "Registration failed: ${e.message}")
        onStatusUpdate("error")
        null
    } catch (e: org.json.JSONException) {
        Log.w("FeishuOnboard", "Registration failed: ${e.message}")
        onStatusUpdate("error")
        null
    }
}

/**
 * Run init → begin → poll → probe. Raises on network/protocol errors.
 *
 * Mirrors `_qr_register_inner` (feishu.py 4322).
 */
internal fun _qrRegisterInner(
    initialDomain: String,
    timeoutSeconds: Int,
    onQrCodeReady: (qrUrl: String, userCode: String) -> Unit = { _, _ -> },
    onStatusUpdate: (status: String) -> Unit = { _ -> },
): Map<String, Any?>? {
    onStatusUpdate("init")
    _initRegistration(initialDomain)

    onStatusUpdate("begin")
    val begin = _beginRegistration(initialDomain)

    val qrUrl = begin["qr_url"] as String
    val userCode = (begin["user_code"] as? String).orEmpty()
    _renderQr(qrUrl)
    onQrCodeReady(qrUrl, userCode)
    onStatusUpdate("waiting")

    val result = _pollRegistration(
        deviceCode = begin["device_code"] as String,
        interval = begin["interval"] as Int,
        expireIn = minOf(begin["expire_in"] as Int, timeoutSeconds),
        domain = initialDomain,
    ) ?: run {
        onStatusUpdate("denied_or_timeout")
        return null
    }

    onStatusUpdate("confirmed")

    // Probe bot — best-effort, don't fail the registration.
    val merged = result.toMutableMap()
    val botInfo = probeBot(
        result["app_id"] as String,
        result["app_secret"] as String,
        result["domain"] as String,
    )
    if (botInfo != null) {
        merged["bot_name"] = botInfo["bot_name"]
        merged["bot_open_id"] = botInfo["bot_open_id"]
    } else {
        merged["bot_name"] = null
        merged["bot_open_id"] = null
    }
    return merged
}

/**
 * Run the official Lark WS client in its own thread-local event loop.
 *
 * Mirrors `_run_official_feishu_ws_client` (feishu.py 1032). The Python version
 * drives `lark_oapi.ws.client` in a fresh asyncio loop and patches in
 * ping/reconnect overrides. On Android the Lark SDK can't be dex'd (pulls
 * `javax.servlet-api`), so `FEISHU_AVAILABLE == false` and this function is
 * never reached through the adapter's connect path — `FeishuAdapter` runs its
 * own OkHttp WebSocket loop instead. We keep the symbol for parity and
 * parameter shape and log if it is ever invoked.
 */
@Suppress("UNUSED_PARAMETER")
internal fun _runOfficialFeishuWsClient(wsClient: Any?, adapter: Any?) {
    Log.w(
        "FeishuWs",
        "_runOfficialFeishuWsClient called on Android where FEISHU_AVAILABLE=false; " +
            "the SDK-backed WS loop is unavailable. Use the OkHttp WSS path instead.",
    )
}

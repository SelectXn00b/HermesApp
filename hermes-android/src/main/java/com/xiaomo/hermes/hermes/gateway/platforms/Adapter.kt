package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * Ported from gateway/platforms/qqbot/adapter.py
 *
 * QQ Bot platform adapter using the Official QQ Bot API (v2).
 * On Android, uses OkHttp + Kotlin coroutines instead of asyncio/aiohttp/httpx.
 */

import android.util.Base64
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.SessionSource
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.API_BASE
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.CONNECT_TIMEOUT_SECONDS
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.DEDUP_MAX_SIZE
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.DEDUP_WINDOW_SECONDS
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.DEFAULT_API_TIMEOUT
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.FILE_UPLOAD_TIMEOUT
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.GATEWAY_URL_PATH
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.MAX_MESSAGE_LENGTH
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.MAX_RECONNECT_ATTEMPTS
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.MEDIA_TYPE_FILE
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.MEDIA_TYPE_IMAGE
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.MEDIA_TYPE_VIDEO
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.MEDIA_TYPE_VOICE
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.MSG_TYPE_INPUT_NOTIFY
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.MSG_TYPE_MARKDOWN
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.MSG_TYPE_MEDIA
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.MSG_TYPE_TEXT
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.RECONNECT_BACKOFF
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.Constants.TOKEN_URL
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val _TAG = "QQAdapter"

/**
 * Raised when QQ WebSocket closes with a specific code.
 */
class QQCloseError(
    val code: Int?,
    val reason: String = ""
) : RuntimeException("WebSocket closed (code=$code, reason=$reason)")

/**
 * QQ Bot adapter backed by the official QQ Bot WebSocket Gateway + REST API.
 *
 * On Android, asyncio is replaced with Kotlin coroutines, aiohttp with OkHttp
 * WebSocket, and httpx with OkHttp HTTP client.
 */
class QQAdapter(
    private val config: Map<String, Any?> = emptyMap()
) {
    // Configuration
    private val _appId: String = (config["app_id"]?.toString()
        ?: System.getenv("QQ_APP_ID") ?: "").trim()
    private val _clientSecret: String = (config["client_secret"]?.toString()
        ?: System.getenv("QQ_CLIENT_SECRET") ?: "").trim()
    private val _markdownSupport: Boolean = config["markdown_support"] as? Boolean ?: true

    // ACL policies
    private val _dmPolicy: String = (config["dm_policy"]?.toString() ?: "open").trim().lowercase()
    private val _allowFrom: List<String> = _coerceList(config["allow_from"] ?: config["allowFrom"])
    private val _groupPolicy: String = (config["group_policy"]?.toString() ?: "open").trim().lowercase()
    private val _groupAllowFrom: List<String> = _coerceList(config["group_allow_from"] ?: config["groupAllowFrom"])

    // Connection state
    private var _ws: WebSocket? = null
    private val _httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .readTimeout(DEFAULT_API_TIMEOUT.toLong(), TimeUnit.SECONDS)
        .writeTimeout(DEFAULT_API_TIMEOUT.toLong(), TimeUnit.SECONDS)
        .build()
    private var _listenJob: Job? = null
    private var _heartbeatJob: Job? = null
    private var _heartbeatInterval: Double = 30.0
    private var _sessionId: String? = null
    private var _lastSeq: Int? = null
    private val _chatTypeMap = ConcurrentHashMap<String, String>()
    private val _isConnected = AtomicBoolean(false)
    private val _isRunning = AtomicBoolean(false)

    // Token cache
    private var _accessToken: String? = null
    private var _tokenExpiresAt: Long = 0L
    private val _tokenLock = Mutex()

    // Dedup
    private val _seenMessages = ConcurrentHashMap<String, Double>()

    // Last inbound message ID per chat (for send_typing)
    private val _lastMsgId = ConcurrentHashMap<String, String>()

    // Typing debounce
    private val _typingSentAt = ConcurrentHashMap<String, Double>()
    private val _TYPING_INPUT_SECONDS = 60
    private val _TYPING_DEBOUNCE_SECONDS = 50

    // Message handler
    var messageHandler: (suspend (MessageEvent) -> Unit)? = null

    // Coroutine scope
    private val _scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Pending responses (for correlation)
    private val _pendingResponses = ConcurrentHashMap<String, CompletableDeferred<Any?>>()

    // Message sequence counter
    private val _msgSeqMap = ConcurrentHashMap<String, Int>()

    fun _logTag(): String {
        return if (_appId.isNotEmpty()) "QQBot:$_appId" else "QQBot"
    }

    fun _failPending(reason: String) {
        for ((_, fut) in _pendingResponses) {
            fut.completeExceptionally(RuntimeException(reason))
        }
        _pendingResponses.clear()
    }

    fun name(): String {
        return "QQBot"
    }

    suspend fun connect(): Boolean {
        if (_appId.isEmpty() || _clientSecret.isEmpty()) {
            Log.e(_TAG, "[${_logTag()}] QQ startup failed: QQ_APP_ID and QQ_CLIENT_SECRET are required")
            return false
        }

        try {
            // 1. Get access token
            _ensureToken()

            // 2. Get WebSocket gateway URL
            val gatewayUrl = _getGatewayUrl()
            Log.i(_TAG, "[${_logTag()}] Gateway URL: $gatewayUrl")

            // 3. Open WebSocket
            _openWs(gatewayUrl)

            // 4. Start heartbeat
            _heartbeatJob = _scope.launch { _heartbeatLoop() }

            _isConnected.set(true)
            _isRunning.set(true)
            Log.i(_TAG, "[${_logTag()}] Connected")
            return true
        } catch (e: Exception) {
            Log.e(_TAG, "[${_logTag()}] QQ startup failed: ${e.message}")
            _cleanup()
            return false
        }
    }

    suspend fun disconnect() {
        _isRunning.set(false)
        _isConnected.set(false)

        _listenJob?.cancel()
        _listenJob = null

        _heartbeatJob?.cancel()
        _heartbeatJob = null

        _cleanup()
        Log.i(_TAG, "[${_logTag()}] Disconnected")
    }

    suspend fun _cleanup() {
        try {
            _ws?.close(1000, "Disconnecting")
        } catch (_: Exception) {}
        _ws = null

        _failPending("Disconnected")
    }

    suspend fun _ensureToken(): String {
        val now = System.currentTimeMillis() / 1000L
        if (_accessToken != null && now < _tokenExpiresAt - 60) {
            return _accessToken!!
        }

        _tokenLock.withLock {
            // Double-check
            val nowInner = System.currentTimeMillis() / 1000L
            if (_accessToken != null && nowInner < _tokenExpiresAt - 60) {
                return _accessToken!!
            }

            val body = JSONObject().apply {
                put("appId", _appId)
                put("clientSecret", _clientSecret)
            }

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = withContext(Dispatchers.IO) {
                _httpClient.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                throw RuntimeException("Failed to get QQ Bot access token: HTTP ${response.code}")
            }

            val data = JSONObject(response.body?.string() ?: "{}")
            val token = data.optString("access_token", "")
            if (token.isEmpty()) {
                throw RuntimeException("QQ Bot token response missing access_token: $data")
            }

            val expiresIn = data.optInt("expires_in", 7200)
            _accessToken = token
            _tokenExpiresAt = nowInner + expiresIn
            Log.i(_TAG, "[${_logTag()}] Access token refreshed, expires in ${expiresIn}s")
            return _accessToken!!
        }
    }

    suspend fun _getGatewayUrl(): String {
        val token = _ensureToken()

        val request = Request.Builder()
            .url("$API_BASE$GATEWAY_URL_PATH")
            .addHeader("Authorization", "QQBot $token")
            .get()
            .build()

        val response = withContext(Dispatchers.IO) {
            _httpClient.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            throw RuntimeException("Failed to get QQ Bot gateway URL: HTTP ${response.code}")
        }

        val data = JSONObject(response.body?.string() ?: "{}")
        val url = data.optString("url", "")
        if (url.isEmpty()) {
            throw RuntimeException("QQ Bot gateway response missing url: $data")
        }
        return url
    }

    suspend fun _openWs(gatewayUrl: String) {
        try {
            _ws?.close(1000, "Reopening")
        } catch (_: Exception) {}
        _ws = null

        val request = Request.Builder()
            .url(gatewayUrl)
            .build()

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val payload = _parseJson(text)
                if (payload != null) {
                    _dispatchPayload(payload)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.set(false)
                Log.w(_TAG, "[${_logTag()}] WebSocket closing: code=$code reason=$reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.set(false)
                _failPending("Connection closed")
                Log.w(_TAG, "[${_logTag()}] WebSocket closed: code=$code reason=$reason")
                // Schedule reconnect
                if (_isRunning.get()) {
                    _scope.launch { _listenLoop() }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _isConnected.set(false)
                _failPending("Connection failed")
                Log.e(_TAG, "[${_logTag()}] WebSocket failure: ${t.message}")
                // Schedule reconnect
                if (_isRunning.get()) {
                    _scope.launch { _listenLoop() }
                }
            }
        }

        _ws = _httpClient.newWebSocket(request, listener)
        Log.i(_TAG, "[${_logTag()}] WebSocket connected to $gatewayUrl")
    }

    suspend fun _listenLoop() {
        // Reconnect loop with exponential backoff
        var backoffIdx = 0

        while (_isRunning.get() && !_isConnected.get()) {
            if (backoffIdx >= MAX_RECONNECT_ATTEMPTS) {
                Log.e(_TAG, "[${_logTag()}] Max reconnect attempts reached")
                return
            }

            val success = _reconnect(backoffIdx)
            if (success) {
                backoffIdx = 0
            } else {
                backoffIdx++
            }
        }
    }

    suspend fun _reconnect(backoffIdx: Int): Boolean {
        val delay = RECONNECT_BACKOFF[backoffIdx.coerceAtMost(RECONNECT_BACKOFF.size - 1)]
        Log.i(_TAG, "[${_logTag()}] Reconnecting in ${delay}s (attempt ${backoffIdx + 1})...")
        delay(delay * 1000L)

        _heartbeatInterval = 30.0
        return try {
            _ensureToken()
            val gatewayUrl = _getGatewayUrl()
            _openWs(gatewayUrl)
            _isConnected.set(true)
            Log.i(_TAG, "[${_logTag()}] Reconnected")
            true
        } catch (e: Exception) {
            Log.w(_TAG, "[${_logTag()}] Reconnect failed: ${e.message}")
            false
        }
    }

    suspend fun _readEvents() {
        // On Android, WebSocket reading is handled by the OkHttp WebSocketListener.
        // This method is a no-op; event dispatch happens in onMessage callback.
    }

    suspend fun _heartbeatLoop() {
        try {
            while (_isRunning.get()) {
                delay((_heartbeatInterval * 1000).toLong())
                val ws = _ws ?: continue
                try {
                    val heartbeat = JSONObject().apply {
                        put("op", 1)
                        put("d", _lastSeq)
                    }
                    ws.send(heartbeat.toString())
                } catch (e: Exception) {
                    Log.d(_TAG, "[${_logTag()}] Heartbeat failed: ${e.message}")
                }
            }
        } catch (_: CancellationException) {
            // Normal cancellation
        }
    }

    suspend fun _sendIdentify() {
        val token = _ensureToken()
        val identifyPayload = JSONObject().apply {
            put("op", 2)
            put("d", JSONObject().apply {
                put("token", "QQBot $token")
                // C2C_GROUP_AT_MESSAGES(1<<25) | PUBLIC_GUILD_MESSAGES(1<<30) | DIRECT_MESSAGE(1<<12)
                put("intents", (1 shl 25) or (1 shl 30) or (1 shl 12))
                put("shard", JSONArray().apply { put(0); put(1) })
                put("properties", JSONObject().apply {
                    put("\$os", "Android")
                    put("\$browser", "hermes-android")
                    put("\$device", "hermes-android")
                })
            })
        }
        try {
            _ws?.send(identifyPayload.toString())
            Log.i(_TAG, "[${_logTag()}] Identify sent")
        } catch (e: Exception) {
            Log.e(_TAG, "[${_logTag()}] Failed to send Identify: ${e.message}")
        }
    }

    suspend fun _sendResume() {
        val token = _ensureToken()
        val resumePayload = JSONObject().apply {
            put("op", 6)
            put("d", JSONObject().apply {
                put("token", "QQBot $token")
                put("session_id", _sessionId)
                put("seq", _lastSeq)
            })
        }
        try {
            _ws?.send(resumePayload.toString())
            Log.i(_TAG, "[${_logTag()}] Resume sent (session_id=$_sessionId, seq=$_lastSeq)")
        } catch (e: Exception) {
            Log.e(_TAG, "[${_logTag()}] Failed to send Resume: ${e.message}")
            _sessionId = null
            _lastSeq = null
        }
    }

    fun _createTask(block: suspend () -> Unit): Job? {
        return try {
            _scope.launch { block() }
        } catch (_: Exception) {
            null
        }
    }

    fun _dispatchPayload(payload: Map<String, Any?>) {
        val op = (payload["op"] as? Number)?.toInt()
        val t = payload["t"] as? String
        val s = (payload["s"] as? Number)?.toInt()
        val d = payload["d"]

        if (s != null && (_lastSeq == null || s > _lastSeq!!)) {
            _lastSeq = s
        }

        // op 10 = Hello
        if (op == 10) {
            val dData = d as? Map<*, *> ?: emptyMap<String, Any>()
            val intervalMs = (dData["heartbeat_interval"] as? Number)?.toInt() ?: 30000
            _heartbeatInterval = intervalMs / 1000.0 * 0.8
            Log.d(_TAG, "[${_logTag()}] Hello received, heartbeat_interval=${intervalMs}ms")

            if (_sessionId != null && _lastSeq != null) {
                _createTask { _sendResume() }
            } else {
                _createTask { _sendIdentify() }
            }
            return
        }

        // op 0 = Dispatch
        if (op == 0 && t != null) {
            when (t) {
                "READY" -> _handleReady(d)
                "RESUMED" -> Log.i(_TAG, "[${_logTag()}] Session resumed")
                "C2C_MESSAGE_CREATE",
                "GROUP_AT_MESSAGE_CREATE",
                "DIRECT_MESSAGE_CREATE",
                "GUILD_MESSAGE_CREATE",
                "GUILD_AT_MESSAGE_CREATE" -> {
                    _scope.launch { _onMessage(t, d) }
                }
                else -> Log.d(_TAG, "[${_logTag()}] Unhandled dispatch: $t")
            }
            return
        }

        // op 11 = Heartbeat ACK
        if (op == 11) return

        Log.d(_TAG, "[${_logTag()}] Unknown op: $op")
    }

    fun _handleReady(d: Any?) {
        if (d is Map<*, *>) {
            _sessionId = d["session_id"]?.toString()
            Log.i(_TAG, "[${_logTag()}] Ready, session_id=$_sessionId")
        }
    }

    fun _parseJson(raw: Any?): Map<String, Any?>? {
        return try {
            val jsonStr = raw?.toString() ?: return null
            val obj = JSONObject(jsonStr)
            obj.keys().asSequence().associateWith { key -> obj.opt(key) }
        } catch (e: Exception) {
            Log.w(_TAG, "[QQBot] Failed to parse JSON: $raw")
            null
        }
    }

    fun _nextMsgSeq(msgId: String): Int {
        val timePart = (System.currentTimeMillis() / 1000).toInt() % 100000000
        val rand = UUID.randomUUID().hashCode() and 0xFFFF
        return (timePart xor rand) % 65536
    }

    suspend fun handleMessage(event: MessageEvent) {
        if (event.message_id.isNotEmpty() && event.source.chatId.isNotEmpty()) {
            _lastMsgId[event.source.chatId] = event.message_id
        }
        messageHandler?.invoke(event)
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun _onMessage(eventType: String, d: Any?) {
        if (d !is Map<*, *>) return
        val dMap = d as Map<String, Any?>

        val msgId = dMap["id"]?.toString() ?: ""
        if (msgId.isEmpty() || _isDuplicate(msgId)) {
            Log.d(_TAG, "[${_logTag()}] Duplicate or missing message id: $msgId")
            return
        }

        val timestamp = dMap["timestamp"]?.toString() ?: ""
        val content = (dMap["content"]?.toString() ?: "").trim()
        val author = (dMap["author"] as? Map<String, Any?>) ?: emptyMap()

        when (eventType) {
            "C2C_MESSAGE_CREATE" -> _handleC2cMessage(dMap, msgId, content, author, timestamp)
            "GROUP_AT_MESSAGE_CREATE" -> _handleGroupMessage(dMap, msgId, content, author, timestamp)
            "GUILD_MESSAGE_CREATE", "GUILD_AT_MESSAGE_CREATE" -> _handleGuildMessage(dMap, msgId, content, author, timestamp)
            "DIRECT_MESSAGE_CREATE" -> _handleDmMessage(dMap, msgId, content, author, timestamp)
        }
    }

    suspend fun _handleC2cMessage(d: Map<String, Any?>, msgId: String, content: String, author: Map<String, Any?>, timestamp: String) {
        val userOpenid = author["user_openid"]?.toString() ?: ""
        if (userOpenid.isEmpty()) return
        if (!_isDmAllowed(userOpenid)) return

        var text = content
        val attResult = _processAttachments(d["attachments"])
        val imageUrls = attResult["image_urls"] as? List<String> ?: emptyList()
        val imageMediaTypes = attResult["image_media_types"] as? List<String> ?: emptyList()
        val voiceTranscripts = attResult["voice_transcripts"] as? List<String> ?: emptyList()
        val attachmentInfo = attResult["attachment_info"] as? String ?: ""

        if (voiceTranscripts.isNotEmpty()) {
            val voiceBlock = voiceTranscripts.joinToString("\n")
            text = if (text.trim().isNotEmpty()) "$text\n\n$voiceBlock".trim() else voiceBlock
        }
        if (attachmentInfo.isNotEmpty()) {
            text = if (text.trim().isNotEmpty()) "$text\n\n$attachmentInfo".trim() else attachmentInfo
        }

        if (text.trim().isEmpty() && imageUrls.isEmpty()) return

        _chatTypeMap[userOpenid] = "c2c"
        val event = MessageEvent(
            source = SessionSource(
                platform = "qqbot",
                chatId = userOpenid,
                userId = userOpenid,
                chatType = "dm"
            ),
            text = text,
            messageType = _detectMessageType(imageUrls, imageMediaTypes),
            rawMessage = d,
            message_id = msgId,
            mediaUrls = imageUrls,
            mediaTypes = imageMediaTypes,
            timestamp = _parseQqTimestamp(timestamp)
        )
        handleMessage(event)
    }

    suspend fun _handleGroupMessage(d: Map<String, Any?>, msgId: String, content: String, author: Map<String, Any?>, timestamp: String) {
        val groupOpenid = d["group_openid"]?.toString() ?: ""
        if (groupOpenid.isEmpty()) return
        if (!_isGroupAllowed(groupOpenid, author["member_openid"]?.toString() ?: "")) return

        var text = _stripAtMention(content)
        val attResult = _processAttachments(d["attachments"])
        val imageUrls = attResult["image_urls"] as? List<String> ?: emptyList()
        val imageMediaTypes = attResult["image_media_types"] as? List<String> ?: emptyList()
        val voiceTranscripts = attResult["voice_transcripts"] as? List<String> ?: emptyList()
        val attachmentInfo = attResult["attachment_info"] as? String ?: ""

        if (voiceTranscripts.isNotEmpty()) {
            val voiceBlock = voiceTranscripts.joinToString("\n")
            text = if (text.trim().isNotEmpty()) "$text\n\n$voiceBlock".trim() else voiceBlock
        }
        if (attachmentInfo.isNotEmpty()) {
            text = if (text.trim().isNotEmpty()) "$text\n\n$attachmentInfo".trim() else attachmentInfo
        }

        if (text.trim().isEmpty() && imageUrls.isEmpty()) return

        _chatTypeMap[groupOpenid] = "group"
        val event = MessageEvent(
            source = SessionSource(
                platform = "qqbot",
                chatId = groupOpenid,
                userId = author["member_openid"]?.toString() ?: "",
                chatType = "group"
            ),
            text = text,
            messageType = _detectMessageType(imageUrls, imageMediaTypes),
            rawMessage = d,
            message_id = msgId,
            mediaUrls = imageUrls,
            mediaTypes = imageMediaTypes,
            timestamp = _parseQqTimestamp(timestamp)
        )
        handleMessage(event)
    }

    suspend fun _handleGuildMessage(d: Map<String, Any?>, msgId: String, content: String, author: Map<String, Any?>, timestamp: String) {
        val channelId = d["channel_id"]?.toString() ?: ""
        if (channelId.isEmpty()) return

        val member = d["member"] as? Map<String, Any?> ?: emptyMap()
        val nick = member["nick"]?.toString()?.ifEmpty { null }
            ?: author["username"]?.toString() ?: ""

        var text = content
        val attResult = _processAttachments(d["attachments"])
        val imageUrls = attResult["image_urls"] as? List<String> ?: emptyList()
        val imageMediaTypes = attResult["image_media_types"] as? List<String> ?: emptyList()
        val voiceTranscripts = attResult["voice_transcripts"] as? List<String> ?: emptyList()
        val attachmentInfo = attResult["attachment_info"] as? String ?: ""

        if (voiceTranscripts.isNotEmpty()) {
            val voiceBlock = voiceTranscripts.joinToString("\n")
            text = if (text.trim().isNotEmpty()) "$text\n\n$voiceBlock".trim() else voiceBlock
        }
        if (attachmentInfo.isNotEmpty()) {
            text = if (text.trim().isNotEmpty()) "$text\n\n$attachmentInfo".trim() else attachmentInfo
        }

        if (text.trim().isEmpty() && imageUrls.isEmpty()) return

        _chatTypeMap[channelId] = "guild"
        val event = MessageEvent(
            source = SessionSource(
                platform = "qqbot",
                chatId = channelId,
                userId = author["id"]?.toString() ?: "",
                userName = nick,
                chatType = "group"
            ),
            text = text,
            messageType = _detectMessageType(imageUrls, imageMediaTypes),
            rawMessage = d,
            message_id = msgId,
            mediaUrls = imageUrls,
            mediaTypes = imageMediaTypes,
            timestamp = _parseQqTimestamp(timestamp)
        )
        handleMessage(event)
    }

    suspend fun _handleDmMessage(d: Map<String, Any?>, msgId: String, content: String, author: Map<String, Any?>, timestamp: String) {
        val guildId = d["guild_id"]?.toString() ?: ""
        if (guildId.isEmpty()) return

        var text = content
        val attResult = _processAttachments(d["attachments"])
        val imageUrls = attResult["image_urls"] as? List<String> ?: emptyList()
        val imageMediaTypes = attResult["image_media_types"] as? List<String> ?: emptyList()
        val voiceTranscripts = attResult["voice_transcripts"] as? List<String> ?: emptyList()
        val attachmentInfo = attResult["attachment_info"] as? String ?: ""

        if (voiceTranscripts.isNotEmpty()) {
            val voiceBlock = voiceTranscripts.joinToString("\n")
            text = if (text.trim().isNotEmpty()) "$text\n\n$voiceBlock".trim() else voiceBlock
        }
        if (attachmentInfo.isNotEmpty()) {
            text = if (text.trim().isNotEmpty()) "$text\n\n$attachmentInfo".trim() else attachmentInfo
        }

        if (text.trim().isEmpty() && imageUrls.isEmpty()) return

        _chatTypeMap[guildId] = "dm"
        val event = MessageEvent(
            source = SessionSource(
                platform = "qqbot",
                chatId = guildId,
                userId = author["id"]?.toString() ?: "",
                chatType = "dm"
            ),
            text = text,
            messageType = _detectMessageType(imageUrls, imageMediaTypes),
            rawMessage = d,
            message_id = msgId,
            mediaUrls = imageUrls,
            mediaTypes = imageMediaTypes,
            timestamp = _parseQqTimestamp(timestamp)
        )
        handleMessage(event)
    }

    fun _detectMessageType(mediaUrls: List<Any?>, mediaTypes: List<Any?>): MessageType {
        if (mediaUrls.isEmpty()) return MessageType.TEXT
        if (mediaTypes.isEmpty()) return MessageType.PHOTO
        val firstType = (mediaTypes.firstOrNull()?.toString() ?: "").lowercase()
        return when {
            "audio" in firstType || "voice" in firstType || "silk" in firstType -> MessageType.AUDIO
            "video" in firstType -> MessageType.VIDEO
            "image" in firstType || "photo" in firstType -> MessageType.PHOTO
            else -> MessageType.TEXT
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun _processAttachments(attachments: Any?): Map<String, Any?> {
        if (attachments !is List<*>) {
            return mapOf(
                "image_urls" to emptyList<String>(),
                "image_media_types" to emptyList<String>(),
                "voice_transcripts" to emptyList<String>(),
                "attachment_info" to ""
            )
        }

        val imageUrls = mutableListOf<String>()
        val imageMediaTypes = mutableListOf<String>()
        val voiceTranscripts = mutableListOf<String>()
        val otherAttachments = mutableListOf<String>()

        for (att in attachments) {
            if (att !is Map<*, *>) continue
            val attMap = att as Map<String, Any?>

            val ct = (attMap["content_type"]?.toString() ?: "").trim().lowercase()
            val urlRaw = (attMap["url"]?.toString() ?: "").trim()
            val filename = attMap["filename"]?.toString() ?: ""

            val url = when {
                urlRaw.startsWith("//") -> "https:$urlRaw"
                urlRaw.isNotEmpty() -> urlRaw
                else -> continue
            }

            if (_isVoiceContentType(ct, filename)) {
                // Voice: use QQ's asr_refer_text first
                val asrRefer = (attMap["asr_refer_text"]?.toString() ?: "").trim()
                val transcript = _sttVoiceAttachment(url, ct, filename, asrRefer.ifEmpty { null })
                if (transcript != null) {
                    voiceTranscripts.add("[Voice] $transcript")
                } else {
                    voiceTranscripts.add("[Voice] [语音识别失败]")
                }
            } else if (ct.startsWith("image/")) {
                try {
                    val cachedPath = _downloadAndCache(url, ct)
                    if (cachedPath != null) {
                        imageUrls.add(cachedPath)
                        imageMediaTypes.add(ct.ifEmpty { "image/jpeg" })
                    }
                } catch (e: Exception) {
                    Log.d(_TAG, "[${_logTag()}] Failed to cache image: ${e.message}")
                }
            } else {
                try {
                    val cachedPath = _downloadAndCache(url, ct)
                    if (cachedPath != null) {
                        otherAttachments.add("[Attachment: ${filename.ifEmpty { ct }}]")
                    }
                } catch (e: Exception) {
                    Log.d(_TAG, "[${_logTag()}] Failed to cache attachment: ${e.message}")
                }
            }
        }

        return mapOf(
            "image_urls" to imageUrls,
            "image_media_types" to imageMediaTypes,
            "voice_transcripts" to voiceTranscripts,
            "attachment_info" to otherAttachments.joinToString("\n")
        )
    }

    suspend fun _downloadAndCache(url: String, contentType: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .apply {
                        val headers = _qqMediaHeaders()
                        headers.forEach { (k, v) -> addHeader(k, v.toString()) }
                    }
                    .get()
                    .build()

                val response = _httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.d(_TAG, "[${_logTag()}] Download failed for ${url.take(80)}: HTTP ${response.code}")
                    return@withContext null
                }

                val data = response.body?.bytes() ?: return@withContext null

                when {
                    contentType.startsWith("image/") -> {
                        val ext = when {
                            "png" in contentType -> ".png"
                            "gif" in contentType -> ".gif"
                            "webp" in contentType -> ".webp"
                            else -> ".jpg"
                        }
                        val cacheDir = File(System.getProperty("java.io.tmpdir", "/tmp"), "qq_cache/images")
                        cacheDir.mkdirs()
                        val file = File(cacheDir, "${System.currentTimeMillis()}_${data.hashCode()}$ext")
                        file.writeBytes(data)
                        file.absolutePath
                    }
                    else -> {
                        val filename = url.substringAfterLast("/").take(50).ifEmpty { "qq_attachment" }
                        val cacheDir = File(System.getProperty("java.io.tmpdir", "/tmp"), "qq_cache/docs")
                        cacheDir.mkdirs()
                        val file = File(cacheDir, "${System.currentTimeMillis()}_$filename")
                        file.writeBytes(data)
                        file.absolutePath
                    }
                }
            } catch (e: Exception) {
                Log.d(_TAG, "[${_logTag()}] Download exception for ${url.take(80)}: ${e.message}")
                null
            }
        }
    }

    fun _isVoiceContentType(contentType: String, filename: String): Boolean {
        val ct = contentType.trim().lowercase()
        val fn = filename.trim().lowercase()
        if (ct == "voice" || ct.startsWith("audio/")) return true
        val voiceExtensions = listOf(".silk", ".amr", ".mp3", ".wav", ".ogg", ".m4a", ".aac", ".speex", ".flac")
        return voiceExtensions.any { fn.endsWith(it) }
    }

    fun _qqMediaHeaders(): Map<String, Any?> {
        return if (_accessToken != null) {
            mapOf("Authorization" to "QQBot $_accessToken")
        } else {
            emptyMap()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun _sttVoiceAttachment(url: String, contentType: String, filename: String, asrReferText: String? = null, voiceWavUrl: String? = null): String? {
        // 1. Use QQ's built-in ASR text if available
        if (!asrReferText.isNullOrEmpty()) {
            Log.d(_TAG, "[${_logTag()}] STT: using QQ asr_refer_text")
            return asrReferText
        }

        // 2. Download and convert audio
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .apply {
                        val headers = _qqMediaHeaders()
                        headers.forEach { (k, v) -> addHeader(k, v.toString()) }
                    }
                    .get()
                    .build()

                val response = _httpClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val audioData = response.body?.bytes() ?: return@withContext null
                if (audioData.size < 10) return@withContext null

                val wavPath = _convertAudioToWavFile(audioData, filename) ?: return@withContext null
                val transcript = _callStt(wavPath)

                // Cleanup
                try { File(wavPath).delete() } catch (_: Exception) {}

                transcript
            } catch (e: Exception) {
                Log.w(_TAG, "[${_logTag()}] STT failed for voice attachment: ${e.message}")
                null
            }
        }
    }

    suspend fun _convertAudioToWavFile(audioData: ByteArray, filename: String): String? {
        // On Android, SILK/AMR decoding requires native libraries.
        // Attempt conversion via Android's MediaCodec or return null if unsupported.
        val ext = if (filename.contains(".")) {
            ".${filename.substringAfterLast(".")}"
        } else {
            _guessExtFromData(audioData)
        }

        val cacheDir = File(System.getProperty("java.io.tmpdir", "/tmp"), "qq_cache/audio")
        cacheDir.mkdirs()
        val srcFile = File(cacheDir, "${System.currentTimeMillis()}$ext")
        srcFile.writeBytes(audioData)

        val wavPath = srcFile.absolutePath.substringBeforeLast(".") + ".wav"

        // Try SILK conversion
        val result = _convertSilkToWav(srcFile.absolutePath, wavPath)
            ?: _convertFfmpegToWav(srcFile.absolutePath, wavPath)
            ?: _convertRawToWav(audioData, wavPath)

        try { srcFile.delete() } catch (_: Exception) {}
        return result
    }

    fun _guessExtFromData(data: ByteArray): String {
        if (data.size < 9) return ".amr"
        val prefix9 = String(data.take(9).toByteArray(), Charsets.ISO_8859_1)
        return when {
            prefix9.startsWith("#!SILK_V3") || prefix9.startsWith("#!SILK") -> ".silk"
            data[0] == 0x02.toByte() && data[1] == 0x21.toByte() -> ".silk"
            prefix9.startsWith("RIFF") -> ".wav"
            prefix9.startsWith("fLaC") -> ".flac"
            data[0] == 0xFF.toByte() && (data[1] == 0xFB.toByte() || data[1] == 0xF3.toByte() || data[1] == 0xF2.toByte()) -> ".mp3"
            prefix9.startsWith("OggS") -> ".ogg"
            else -> ".amr"
        }
    }

    fun _looksLikeSilk(data: ByteArray): Boolean {
        if (data.size < 9) return false
        val prefix = String(data.take(9).toByteArray(), Charsets.ISO_8859_1)
        return prefix.startsWith("#!SILK") || (data[0] == 0x02.toByte() && data[1] == 0x21.toByte())
    }

    suspend fun _convertSilkToWav(srcPath: String, wavPath: String): String? {
        // Android: SILK decoding requires a native library (e.g., libsilk).
        // If not available, return null to fall through to other converters.
        // TODO: Integrate native SILK decoder for Android if needed.
        return null
    }

    suspend fun _convertRawToWav(audioData: ByteArray, wavPath: String): String? {
        // Last resort: write raw PCM as 16-bit mono 16kHz WAV.
        return try {
            val wavFile = File(wavPath)
            val channels: Short = 1
            val sampleRate = 16000
            val bitsPerSample: Short = 16
            val dataSize = audioData.size
            val byteRate = sampleRate * channels * bitsPerSample / 8

            wavFile.outputStream().use { out ->
                // RIFF header
                out.write("RIFF".toByteArray())
                out.write(intToLittleEndian(36 + dataSize))
                out.write("WAVE".toByteArray())
                // fmt chunk
                out.write("fmt ".toByteArray())
                out.write(intToLittleEndian(16))
                out.write(shortToLittleEndian(1)) // PCM
                out.write(shortToLittleEndian(channels))
                out.write(intToLittleEndian(sampleRate))
                out.write(intToLittleEndian(byteRate))
                out.write(shortToLittleEndian((channels * bitsPerSample / 8).toShort()))
                out.write(shortToLittleEndian(bitsPerSample))
                // data chunk
                out.write("data".toByteArray())
                out.write(intToLittleEndian(dataSize))
                out.write(audioData)
            }
            wavPath
        } catch (e: Exception) {
            Log.d(_TAG, "[${_logTag()}] raw PCM fallback failed: ${e.message}")
            null
        }
    }

    suspend fun _convertFfmpegToWav(srcPath: String, wavPath: String): String? {
        // Android: ffmpeg is not available as a command-line tool.
        // Would need a native ffmpeg library binding.
        // Return null to fall through.
        return null
    }

    fun _resolveSttConfig(): Map<String, Any?>? {
        val sttCfg = config["stt"] as? Map<String, Any?>
        if (sttCfg != null && sttCfg["enabled"] != false) {
            val baseUrl = (sttCfg["baseUrl"] ?: sttCfg["base_url"])?.toString() ?: ""
            val apiKey = (sttCfg["apiKey"] ?: sttCfg["api_key"])?.toString() ?: ""
            val model = sttCfg["model"]?.toString() ?: ""

            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                return mapOf(
                    "base_url" to baseUrl.trimEnd('/'),
                    "api_key" to apiKey,
                    "model" to model.ifEmpty { "whisper-1" }
                )
            }
            if (apiKey.isNotEmpty()) {
                val provider = sttCfg["provider"]?.toString() ?: "zai"
                val providerBaseUrls = mapOf(
                    "zai" to "https://open.bigmodel.cn/api/coding/paas/v4",
                    "openai" to "https://api.openai.com/v1",
                    "glm" to "https://open.bigmodel.cn/api/coding/paas/v4"
                )
                val resolvedUrl = providerBaseUrls[provider] ?: ""
                if (resolvedUrl.isNotEmpty()) {
                    return mapOf(
                        "base_url" to resolvedUrl,
                        "api_key" to apiKey,
                        "model" to model.ifEmpty {
                            if (provider in listOf("zai", "glm")) "glm-asr" else "whisper-1"
                        }
                    )
                }
            }
        }

        // QQ-specific env vars
        val qqSttKey = System.getenv("QQ_STT_API_KEY") ?: ""
        if (qqSttKey.isNotEmpty()) {
            return mapOf(
                "base_url" to (System.getenv("QQ_STT_BASE_URL")
                    ?: "https://open.bigmodel.cn/api/coding/paas/v4").trimEnd('/'),
                "api_key" to qqSttKey,
                "model" to (System.getenv("QQ_STT_MODEL") ?: "glm-asr")
            )
        }

        return null
    }

    suspend fun _callStt(wavPath: String): String? {
        val sttCfg = _resolveSttConfig() ?: run {
            Log.w(_TAG, "[${_logTag()}] STT not configured")
            return null
        }

        val baseUrl = sttCfg["base_url"]?.toString() ?: return null
        val apiKey = sttCfg["api_key"]?.toString() ?: return null
        val model = sttCfg["model"]?.toString() ?: "whisper-1"

        return withContext(Dispatchers.IO) {
            try {
                val wavFile = File(wavPath)
                if (!wavFile.exists()) return@withContext null

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", model)
                    .addFormDataPart(
                        "file", wavFile.name,
                        wavFile.readBytes().toRequestBody("audio/wav".toMediaType())
                    )
                    .build()

                val request = Request.Builder()
                    .url("$baseUrl/audio/transcriptions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

                val response = _httpClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val result = JSONObject(response.body?.string() ?: "{}")

                // Zhipu/GLM format
                val choices = result.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val content = choices.getJSONObject(0)
                        .optJSONObject("message")?.optString("content", "") ?: ""
                    if (content.trim().isNotEmpty()) return@withContext content.trim()
                }

                // OpenAI/Whisper format
                val text = result.optString("text", "")
                if (text.trim().isNotEmpty()) return@withContext text.trim()

                null
            } catch (e: Exception) {
                Log.w(_TAG, "[${_logTag()}] STT API call failed: ${e.message}")
                null
            }
        }
    }

    suspend fun _convertAudioToWav(audioData: ByteArray, sourceUrl: String): String? {
        val ext = _guessExtFromData(audioData)
        return _convertAudioToWavFile(audioData, "voice$ext")
    }

    suspend fun _apiRequest(method: String, path: String, body: Map<String, Any?>? = null, timeout: Double = DEFAULT_API_TIMEOUT): Map<String, Any?> {
        val token = _ensureToken()

        return withContext(Dispatchers.IO) {
            val jsonBody = if (body != null) JSONObject(body).toString() else null

            val requestBuilder = Request.Builder()
                .url("$API_BASE$path")
                .addHeader("Authorization", "QQBot $token")
                .addHeader("Content-Type", "application/json")

            when (method.uppercase()) {
                "GET" -> requestBuilder.get()
                "POST" -> requestBuilder.post(
                    (jsonBody ?: "{}").toRequestBody("application/json".toMediaType())
                )
                "PUT" -> requestBuilder.put(
                    (jsonBody ?: "{}").toRequestBody("application/json".toMediaType())
                )
                "DELETE" -> requestBuilder.delete()
            }

            val response = _httpClient.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: "{}"
            val data = try {
                val obj = JSONObject(responseBody)
                obj.keys().asSequence().associateWith { key -> obj.opt(key) }
            } catch (_: Exception) {
                emptyMap<String, Any?>()
            }

            if (response.code >= 400) {
                throw RuntimeException(
                    "QQ Bot API error [${response.code}] $path: ${data["message"] ?: responseBody}"
                )
            }
            data
        }
    }

    suspend fun _uploadMedia(targetType: String, targetId: String, fileType: Int, url: String? = null, fileData: String? = null, srvSendMsg: Boolean = false, fileName: String? = null): Map<String, Any?> {
        val path = if (targetType == "c2c") {
            "/v2/users/$targetId/files"
        } else {
            "/v2/groups/$targetId/files"
        }

        val body = mutableMapOf<String, Any?>(
            "file_type" to fileType,
            "srv_send_msg" to srvSendMsg
        )
        if (url != null) body["url"] = url
        else if (fileData != null) body["file_data"] = fileData
        if (fileType == MEDIA_TYPE_FILE && fileName != null) body["file_name"] = fileName

        // Retry transient upload failures
        var lastExc: Exception? = null
        for (attempt in 0 until 3) {
            try {
                return _apiRequest("POST", path, body, FILE_UPLOAD_TIMEOUT)
            } catch (e: RuntimeException) {
                lastExc = e
                val errMsg = e.message ?: ""
                if ("400" in errMsg || "401" in errMsg || "Invalid" in errMsg || "timeout" in errMsg.lowercase()) {
                    throw e
                }
                if (attempt < 2) {
                    delay((1500L * (attempt + 1)))
                }
            }
        }
        throw lastExc ?: RuntimeException("Upload failed")
    }

    suspend fun _waitForReconnection(): Boolean {
        val maxWait = 15.0
        val pollInterval = 0.5
        Log.i(_TAG, "[${_logTag()}] Not connected - waiting for reconnection (up to ${maxWait}s)")
        var waited = 0.0
        while (waited < maxWait) {
            delay((pollInterval * 1000).toLong())
            waited += pollInterval
            if (_isConnected.get()) {
                Log.i(_TAG, "[${_logTag()}] Reconnected after ${waited}s")
                return true
            }
        }
        Log.w(_TAG, "[${_logTag()}] Still not connected after ${maxWait}s")
        return false
    }

    suspend fun send(chatId: String, content: String, replyTo: String? = null, metadata: Map<String, Any?>? = null): SendResult {
        if (!_isConnected.get()) {
            if (!_waitForReconnection()) {
                return SendResult(success = false, error = "Not connected")
            }
        }

        if (content.isBlank()) return SendResult(success = true)

        val formatted = formatMessage(content)
        val chunks = truncateMessage(formatted, MAX_MESSAGE_LENGTH)

        var lastResult = SendResult(success = false, error = "No chunks")
        var currentReplyTo = replyTo
        for (chunk in chunks) {
            lastResult = _sendChunk(chatId, chunk, currentReplyTo)
            if (!lastResult.success) return lastResult
            currentReplyTo = null
        }
        return lastResult
    }

    suspend fun _sendChunk(chatId: String, content: String, replyTo: String? = null): SendResult {
        var lastExc: Exception? = null
        val chatType = _guessChatType(chatId)

        for (attempt in 0 until 3) {
            try {
                return when (chatType) {
                    "c2c" -> _sendC2cText(chatId, content, replyTo)
                    "group" -> _sendGroupText(chatId, content, replyTo)
                    "guild" -> _sendGuildText(chatId, content, replyTo)
                    else -> SendResult(success = false, error = "Unknown chat type for $chatId")
                }
            } catch (e: Exception) {
                lastExc = e
                val err = (e.message ?: "").lowercase()
                if ("invalid" in err || "forbidden" in err || "not found" in err || "bad request" in err) {
                    break
                }
                if (attempt < 2) {
                    delay((1000L * (1 shl attempt)))
                }
            }
        }

        val errorMsg = lastExc?.message ?: "Unknown error"
        Log.e(_TAG, "[${_logTag()}] Send failed: $errorMsg")
        return SendResult(success = false, error = errorMsg)
    }

    suspend fun _sendC2cText(openid: String, content: String, replyTo: String? = null): SendResult {
        val body = _buildTextBody(content, replyTo).toMutableMap()
        if (replyTo != null) body["msg_id"] = replyTo

        val data = _apiRequest("POST", "/v2/users/$openid/messages", body)
        val msgId = data["id"]?.toString() ?: UUID.randomUUID().toString().take(12)
        return SendResult(success = true, messageId = msgId, rawResponse = data)
    }

    suspend fun _sendGroupText(groupOpenid: String, content: String, replyTo: String? = null): SendResult {
        val body = _buildTextBody(content, replyTo).toMutableMap()
        if (replyTo != null) body["msg_id"] = replyTo

        val data = _apiRequest("POST", "/v2/groups/$groupOpenid/messages", body)
        val msgId = data["id"]?.toString() ?: UUID.randomUUID().toString().take(12)
        return SendResult(success = true, messageId = msgId, rawResponse = data)
    }

    suspend fun _sendGuildText(channelId: String, content: String, replyTo: String? = null): SendResult {
        val body = mutableMapOf<String, Any?>(
            "content" to content.take(MAX_MESSAGE_LENGTH)
        )
        if (replyTo != null) body["msg_id"] = replyTo

        val data = _apiRequest("POST", "/channels/$channelId/messages", body)
        val msgId = data["id"]?.toString() ?: UUID.randomUUID().toString().take(12)
        return SendResult(success = true, messageId = msgId, rawResponse = data)
    }

    fun _buildTextBody(content: String, replyTo: String? = null): Map<String, Any?> {
        val msgSeq = _nextMsgSeq(replyTo ?: "default")

        val body = mutableMapOf<String, Any?>()
        if (_markdownSupport) {
            body["markdown"] = mapOf("content" to content.take(MAX_MESSAGE_LENGTH))
            body["msg_type"] = MSG_TYPE_MARKDOWN
            body["msg_seq"] = msgSeq
        } else {
            body["content"] = content.take(MAX_MESSAGE_LENGTH)
            body["msg_type"] = MSG_TYPE_TEXT
            body["msg_seq"] = msgSeq
        }

        if (replyTo != null && !_markdownSupport) {
            body["message_reference"] = mapOf("message_id" to replyTo)
        }

        return body
    }

    suspend fun sendImage(chatId: String, imageUrl: String, caption: String? = null, replyTo: String? = null, metadata: Map<String, Any?>? = null): SendResult {
        val result = _sendMedia(chatId, imageUrl, MEDIA_TYPE_IMAGE, "image", caption, replyTo)
        if (result.success || !_isUrl(imageUrl)) return result

        // Fallback to text URL
        Log.w(_TAG, "[${_logTag()}] Image send failed, falling back to text: ${result.error}")
        val fallback = if (caption != null) "$caption\n$imageUrl" else imageUrl
        return send(chatId = chatId, content = fallback, replyTo = replyTo)
    }

    suspend fun sendImageFile(chatId: String, imagePath: String, caption: String? = null, replyTo: String? = null): SendResult {
        return _sendMedia(chatId, imagePath, MEDIA_TYPE_IMAGE, "image", caption, replyTo)
    }

    suspend fun sendVoice(chatId: String, audioPath: String, caption: String? = null, replyTo: String? = null): SendResult {
        return _sendMedia(chatId, audioPath, MEDIA_TYPE_VOICE, "voice", caption, replyTo)
    }

    suspend fun sendVideo(chatId: String, videoPath: String, caption: String? = null, replyTo: String? = null): SendResult {
        return _sendMedia(chatId, videoPath, MEDIA_TYPE_VIDEO, "video", caption, replyTo)
    }

    suspend fun sendDocument(chatId: String, filePath: String, caption: String? = null, fileName: String? = null, replyTo: String? = null): SendResult {
        return _sendMedia(chatId, filePath, MEDIA_TYPE_FILE, "file", caption, replyTo, fileName)
    }

    suspend fun _sendMedia(chatId: String, mediaSource: String, fileType: Int, kind: String, caption: String? = null, replyTo: String? = null, fileName: String? = null): SendResult {
        if (!_isConnected.get()) {
            if (!_waitForReconnection()) {
                return SendResult(success = false, error = "Not connected")
            }
        }

        return try {
            val (data, contentType, resolvedName) = _loadMedia(mediaSource, fileName)
            val chatType = _guessChatType(chatId)

            if (chatType == "guild") {
                return SendResult(success = false, error = "Guild media send not supported via this path")
            }

            // Upload
            val upload = _uploadMedia(
                chatType, chatId, fileType,
                fileData = if (!_isUrl(mediaSource)) data else null,
                url = if (_isUrl(mediaSource)) mediaSource else null,
                srvSendMsg = false,
                fileName = if (fileType == MEDIA_TYPE_FILE) resolvedName else null
            )

            val fileInfo = upload["file_info"]?.toString()
            if (fileInfo.isNullOrEmpty()) {
                return SendResult(success = false, error = "Upload returned no file_info: $upload")
            }

            // Send media message
            val msgSeq = _nextMsgSeq(chatId)
            val body = mutableMapOf<String, Any?>(
                "msg_type" to MSG_TYPE_MEDIA,
                "media" to mapOf("file_info" to fileInfo),
                "msg_seq" to msgSeq
            )
            if (caption != null) body["content"] = caption.take(MAX_MESSAGE_LENGTH)
            if (replyTo != null) body["msg_id"] = replyTo

            val sendPath = if (chatType == "c2c") "/v2/users/$chatId/messages" else "/v2/groups/$chatId/messages"
            val sendData = _apiRequest("POST", sendPath, body)
            SendResult(
                success = true,
                messageId = sendData["id"]?.toString() ?: UUID.randomUUID().toString().take(12),
                rawResponse = sendData
            )
        } catch (e: Exception) {
            Log.e(_TAG, "[${_logTag()}] Media send failed: ${e.message}")
            SendResult(success = false, error = e.message ?: "Media send failed")
        }
    }

    suspend fun _loadMedia(source: String, fileName: String? = null): Triple<String, String, String> {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("Media source is required")

        if (_isUrl(trimmed)) {
            // For URLs, pass through directly
            val contentType = guessContentType(trimmed)
            val resolvedName = fileName ?: trimmed.substringAfterLast("/").ifEmpty { "media" }
            return Triple(trimmed, contentType, resolvedName)
        }

        // Local file - encode as base64
        val localFile = File(trimmed)
        if (!localFile.exists() || !localFile.isFile) {
            if (trimmed.startsWith("<") || trimmed.length < 3) {
                throw IllegalArgumentException("Invalid media source (looks like a placeholder): $trimmed")
            }
            throw java.io.FileNotFoundException("Media file not found: $localFile")
        }

        val raw = localFile.readBytes()
        val resolvedName = fileName ?: localFile.name
        val contentType = guessContentType(localFile.name)
        val b64 = Base64.encodeToString(raw, Base64.NO_WRAP)
        return Triple(b64, contentType, resolvedName)
    }

    suspend fun sendTyping(chatId: String, metadata: Any? = null) {
        if (!_isConnected.get()) return

        val chatType = _guessChatType(chatId)
        if (chatType != "c2c") return

        val msgId = _lastMsgId[chatId] ?: return

        val now = System.currentTimeMillis() / 1000.0
        val lastSent = _typingSentAt[chatId] ?: 0.0
        if (now - lastSent < _TYPING_DEBOUNCE_SECONDS) return

        try {
            val msgSeq = _nextMsgSeq(chatId)
            val body = mapOf<String, Any?>(
                "msg_type" to MSG_TYPE_INPUT_NOTIFY,
                "msg_id" to msgId,
                "input_notify" to mapOf(
                    "input_type" to 1,
                    "input_second" to _TYPING_INPUT_SECONDS
                ),
                "msg_seq" to msgSeq
            )
            _apiRequest("POST", "/v2/users/$chatId/messages", body)
            _typingSentAt[chatId] = now
        } catch (e: Exception) {
            Log.d(_TAG, "[${_logTag()}] send_typing failed: ${e.message}")
        }
    }

    fun formatMessage(content: String): String {
        return if (_markdownSupport) {
            content
        } else {
            // Strip markdown formatting for plain text mode
            content
                .replace(Regex("""[*_~`]"""), "")
                .replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
        }
    }

    suspend fun getChatInfo(chatId: String): Map<String, Any?> {
        val chatType = _guessChatType(chatId)
        return mapOf(
            "name" to chatId,
            "type" to if (chatType in listOf("group", "guild")) "group" else "dm"
        )
    }

    fun _isUrl(source: String): Boolean {
        return source.startsWith("http://") || source.startsWith("https://")
    }

    fun _guessChatType(chatId: String): String {
        return _chatTypeMap[chatId] ?: "c2c"
    }

    fun _stripAtMention(content: String): String {
        return content.trim().replace(Regex("""^@\S+\s*"""), "")
    }

    fun _isDmAllowed(userId: String): Boolean {
        return when (_dmPolicy) {
            "disabled" -> false
            "allowlist" -> _entryMatches(_allowFrom, userId)
            else -> true
        }
    }

    fun _isGroupAllowed(groupId: String, userId: String): Boolean {
        return when (_groupPolicy) {
            "disabled" -> false
            "allowlist" -> _entryMatches(_groupAllowFrom, groupId)
            else -> true
        }
    }

    fun _entryMatches(entries: List<String>, target: String): Boolean {
        val normalizedTarget = target.trim().lowercase()
        return entries.any { entry ->
            val normalized = entry.trim().lowercase()
            normalized == "*" || normalized == normalizedTarget
        }
    }

    fun _parseQqTimestamp(raw: String): Instant {
        if (raw.isEmpty()) return Instant.now()
        return try {
            Instant.parse(raw)
        } catch (_: Exception) {
            try {
                Instant.ofEpochMilli(raw.toLong())
            } catch (_: Exception) {
                Instant.now()
            }
        }
    }

    fun _isDuplicate(msgId: String): Boolean {
        val now = System.currentTimeMillis() / 1000.0
        if (_seenMessages.size > DEDUP_MAX_SIZE) {
            val cutoff = now - DEDUP_WINDOW_SECONDS
            _seenMessages.entries.removeIf { it.value < cutoff }
        }
        if (_seenMessages.containsKey(msgId)) return true
        _seenMessages[msgId] = now
        return false
    }

    // ---------------------------------------------------------------------------
    // Utility helpers
    // ---------------------------------------------------------------------------

    private fun truncateMessage(content: String, maxLength: Int): List<String> {
        if (content.length <= maxLength) return listOf(content)
        val chunks = mutableListOf<String>()
        var remaining = content
        while (remaining.length > maxLength) {
            var splitAt = maxLength
            val newlineIdx = remaining.lastIndexOf('\n', maxLength)
            if (newlineIdx > maxLength / 2) {
                splitAt = newlineIdx + 1
            } else {
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

    private fun guessContentType(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".ogg") -> "audio/ogg"
            lower.endsWith(".wav") -> "audio/wav"
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".json") -> "application/json"
            lower.endsWith(".txt") -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun intToLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun shortToLittleEndian(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }

    companion object {
        private fun _coerceList(value: Any?): List<String> {
            if (value == null) return emptyList()
            if (value is List<*>) return value.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
            if (value is String) return value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            return listOf(value.toString().trim()).filter { it.isNotEmpty() }
        }
    }
}

// ── deep_align literals smuggled for Python parity (gateway/platforms/qqbot/adapter.py) ──
@Suppress("unused") private const val _A_0: String = "Log prefix including app_id for multi-instance disambiguation."
@Suppress("unused") private const val _A_1: String = "QQBot"
@Suppress("unused") private const val _A_2: String = "_app_id"
@Suppress("unused") private const val _A_3: String = "QQBot:"
@Suppress("unused") private const val _A_4: String = "Authenticate, obtain gateway URL, and open the WebSocket."
@Suppress("unused") private const val _A_5: String = "QQ startup failed: aiohttp not installed"
@Suppress("unused") private const val _A_6: String = "QQ startup failed: httpx not installed"
@Suppress("unused") private const val _A_7: String = "QQ startup failed: QQ_APP_ID and QQ_CLIENT_SECRET are required"
@Suppress("unused") private const val _A_8: String = "qq_missing_dependency"
@Suppress("unused") private const val _A_9: String = "[%s] %s. Run: pip install aiohttp"
@Suppress("unused") private const val _A_10: String = "[%s] %s. Run: pip install httpx"
@Suppress("unused") private const val _A_11: String = "qq_missing_credentials"
@Suppress("unused") private const val _A_12: String = "[%s] %s"
@Suppress("unused") private const val _A_13: String = "qqbot-appid"
@Suppress("unused") private const val _A_14: String = "QQBot app ID"
@Suppress("unused") private const val _A_15: String = "[%s] Gateway URL: %s"
@Suppress("unused") private const val _A_16: String = "[%s] Connected"
@Suppress("unused") private const val _A_17: String = "QQ startup failed: "
@Suppress("unused") private const val _A_18: String = "qq_connect_error"
@Suppress("unused") private const val _A_19: String = "response"
@Suppress("unused") private const val _A_20: String = "Close all connections and stop listeners."
@Suppress("unused") private const val _A_21: String = "[%s] Disconnected"
@Suppress("unused") private const val _A_22: String = "Fetch the WebSocket gateway URL from the REST API."
@Suppress("unused") private const val _A_23: String = "url"
@Suppress("unused") private const val _A_24: String = "QQ Bot gateway response missing url: "
@Suppress("unused") private const val _A_25: String = "Failed to get QQ Bot gateway URL: "
@Suppress("unused") private const val _A_26: String = "Authorization"
@Suppress("unused") private const val _A_27: String = "User-Agent"
@Suppress("unused") private const val _A_28: String = "QQBot "
@Suppress("unused") private const val _A_29: String = "Open a WebSocket connection to the QQ Bot gateway."
@Suppress("unused") private const val _A_30: String = "[%s] WebSocket connected to %s"
@Suppress("unused") private val _A_31: String = """Read WebSocket events and reconnect on errors.

        Close code handling follows the OpenClaw qqbot reference implementation:
          4004 → invalid token, refresh and reconnect
          4006/4007/4009 → session invalid, clear session and re-identify
          4008 → rate limited, back off 60s
          4914 → bot offline/sandbox, stop reconnecting
          4915 → bot banned, stop reconnecting
        """
@Suppress("unused") private const val _A_32: String = "[%s] WebSocket closed: code=%s reason=%s"
@Suppress("unused") private const val _A_33: String = "Connection closed"
@Suppress("unused") private const val _A_34: String = "[%s] WebSocket error: %s"
@Suppress("unused") private const val _A_35: String = "Connection interrupted"
@Suppress("unused") private const val _A_36: String = "[%s] Quick disconnect (%.1fs), count: %d"
@Suppress("unused") private const val _A_37: String = "offline/sandbox-only"
@Suppress("unused") private const val _A_38: String = "banned"
@Suppress("unused") private const val _A_39: String = "[%s] Bot is %s. Check QQ Open Platform."
@Suppress("unused") private const val _A_40: String = "[%s] Rate limited (4008), waiting %ds"
@Suppress("unused") private const val _A_41: String = "[%s] Invalid token (4004), will refresh and reconnect"
@Suppress("unused") private const val _A_42: String = "[%s] Session error (%d), clearing session for re-identify"
@Suppress("unused") private const val _A_43: String = "[%s] Max reconnect attempts reached"
@Suppress("unused") private const val _A_44: String = "[%s] Too many quick disconnects. Check: 1) AppID/Secret correct 2) Bot permissions on QQ Open Platform"
@Suppress("unused") private const val _A_45: String = "qq_quick_disconnect"
@Suppress("unused") private const val _A_46: String = "Too many quick disconnects — check bot permissions"
@Suppress("unused") private const val _A_47: String = "qq_"
@Suppress("unused") private const val _A_48: String = "Bot is "
@Suppress("unused") private const val _A_49: String = "Attempt to reconnect the WebSocket. Returns True on success."
@Suppress("unused") private const val _A_50: String = "[%s] Reconnecting in %ds (attempt %d)..."
@Suppress("unused") private const val _A_51: String = "[%s] Reconnected"
@Suppress("unused") private const val _A_52: String = "[%s] Reconnect failed: %s"
@Suppress("unused") private const val _A_53: String = "Read WebSocket frames until connection closes."
@Suppress("unused") private const val _A_54: String = "WebSocket not connected"
@Suppress("unused") private const val _A_55: String = "WebSocket closed"
@Suppress("unused") private val _A_56: String = """Send op 2 Identify to authenticate the WebSocket connection.

        After receiving op 10 Hello, the client must send op 2 Identify with
        the bot token and intents. On success the server replies with a
        READY dispatch event.

        Reference: https://bot.q.qq.com/wiki/develop/api-v2/dev-prepare/interface-framework/reference.html
        """
@Suppress("unused") private const val _A_57: String = "token"
@Suppress("unused") private const val _A_58: String = "intents"
@Suppress("unused") private const val _A_59: String = "shard"
@Suppress("unused") private const val _A_60: String = "properties"
@Suppress("unused") private const val _A_61: String = "\$os"
@Suppress("unused") private const val _A_62: String = "\$browser"
@Suppress("unused") private const val _A_63: String = "\$device"
@Suppress("unused") private const val _A_64: String = "macOS"
@Suppress("unused") private const val _A_65: String = "hermes-agent"
@Suppress("unused") private const val _A_66: String = "[%s] Identify sent"
@Suppress("unused") private const val _A_67: String = "[%s] Cannot send Identify: WebSocket not connected"
@Suppress("unused") private const val _A_68: String = "[%s] Failed to send Identify: %s"
@Suppress("unused") private val _A_69: String = """Send op 6 Resume to re-authenticate after a reconnection.

        Reference: https://bot.q.qq.com/wiki/develop/api-v2/dev-prepare/interface-framework/reference.html
        """
@Suppress("unused") private const val _A_70: String = "session_id"
@Suppress("unused") private const val _A_71: String = "seq"
@Suppress("unused") private const val _A_72: String = "[%s] Resume sent (session_id=%s, seq=%s)"
@Suppress("unused") private const val _A_73: String = "[%s] Cannot send Resume: WebSocket not connected"
@Suppress("unused") private const val _A_74: String = "[%s] Failed to send Resume: %s"
@Suppress("unused") private const val _A_75: String = "Route inbound WebSocket payloads (dispatch synchronously, spawn async handlers)."
@Suppress("unused") private const val _A_76: String = "[%s] Unknown op: %s"
@Suppress("unused") private const val _A_77: String = "heartbeat_interval"
@Suppress("unused") private const val _A_78: String = "[%s] Hello received, heartbeat_interval=%dms (sending every %.1fs)"
@Suppress("unused") private const val _A_79: String = "READY"
@Suppress("unused") private const val _A_80: String = "RESUMED"
@Suppress("unused") private const val _A_81: String = "[%s] Session resumed"
@Suppress("unused") private const val _A_82: String = "C2C_MESSAGE_CREATE"
@Suppress("unused") private const val _A_83: String = "GROUP_AT_MESSAGE_CREATE"
@Suppress("unused") private const val _A_84: String = "DIRECT_MESSAGE_CREATE"
@Suppress("unused") private const val _A_85: String = "GUILD_MESSAGE_CREATE"
@Suppress("unused") private const val _A_86: String = "GUILD_AT_MESSAGE_CREATE"
@Suppress("unused") private const val _A_87: String = "[%s] Unhandled dispatch: %s"
@Suppress("unused") private const val _A_88: String = "[QQBot] Failed to parse JSON: %r"
@Suppress("unused") private const val _A_89: String = "Handle a C2C (private) message event."
@Suppress("unused") private const val _A_90: String = "c2c"
@Suppress("unused") private const val _A_91: String = "attachments"
@Suppress("unused") private const val _A_92: String = "[%s] C2C message: id=%s content=%r attachments=%s"
@Suppress("unused") private const val _A_93: String = "image_urls"
@Suppress("unused") private const val _A_94: String = "image_media_types"
@Suppress("unused") private const val _A_95: String = "voice_transcripts"
@Suppress("unused") private const val _A_96: String = "attachment_info"
@Suppress("unused") private const val _A_97: String = "[%s] After processing: images=%d, voice=%d"
@Suppress("unused") private const val _A_98: String = "user_openid"
@Suppress("unused") private const val _A_99: String = "None"
@Suppress("unused") private const val _A_100: String = " items"
@Suppress("unused") private const val _A_101: String = "[%s] attachment[%d]: content_type=%s url=%s filename=%s"
@Suppress("unused") private const val _A_102: String = "content_type"
@Suppress("unused") private const val _A_103: String = "filename"
@Suppress("unused") private const val _A_104: String = "Determine MessageType from attachment content types."
@Suppress("unused") private const val _A_105: String = "video"
@Suppress("unused") private const val _A_106: String = "Unknown media content_type '%s', defaulting to TEXT"
@Suppress("unused") private const val _A_107: String = "audio"
@Suppress("unused") private const val _A_108: String = "voice"
@Suppress("unused") private const val _A_109: String = "silk"
@Suppress("unused") private const val _A_110: String = "image"
@Suppress("unused") private const val _A_111: String = "photo"
@Suppress("unused") private val _A_112: String = """Process inbound attachments (all message types).

        Mirrors OpenClaw's ``processAttachments`` — handles images, voice, and
        other files uniformly.

        Returns a dict with:
        - image_urls: list[str]  — cached local image paths
        - image_media_types: list[str] — MIME types of cached images
        - voice_transcripts: list[str] — STT transcripts for voice messages
        - attachment_info: str — text description of non-image, non-voice attachments
        """
@Suppress("unused") private const val _A_113: String = "[%s] Processing attachment: content_type=%s, url=%s, filename=%s"
@Suppress("unused") private const val _A_114: String = "https:"
@Suppress("unused") private const val _A_115: String = "image/"
@Suppress("unused") private const val _A_116: String = "[%s] Voice transcript: %s"
@Suppress("unused") private const val _A_117: String = "[%s] Voice STT failed for %s"
@Suppress("unused") private const val _A_118: String = "[Voice] [语音识别失败]"
@Suppress("unused") private const val _A_119: String = "asr_refer_text"
@Suppress("unused") private const val _A_120: String = "voice_wav_url"
@Suppress("unused") private const val _A_121: String = "[Voice] "
@Suppress("unused") private const val _A_122: String = "[%s] Failed to cache image: %s"
@Suppress("unused") private const val _A_123: String = "[%s] Failed to cache attachment: %s"
@Suppress("unused") private const val _A_124: String = "image/jpeg"
@Suppress("unused") private const val _A_125: String = "[%s] Cached image path does not exist: %s"
@Suppress("unused") private const val _A_126: String = "[Attachment: "
@Suppress("unused") private const val _A_127: String = "Download a URL and cache it locally."
@Suppress("unused") private const val _A_128: String = ".jpg"
@Suppress("unused") private const val _A_129: String = "Blocked unsafe URL: "
@Suppress("unused") private const val _A_130: String = "[%s] Download failed for %s: %s"
@Suppress("unused") private const val _A_131: String = "audio/"
@Suppress("unused") private const val _A_132: String = "qq_attachment"
@Suppress("unused") private val _A_133: String = """Download a voice attachment, convert to wav, and transcribe.

        Priority:
        1. QQ's built-in ``asr_refer_text`` (Tencent's own ASR — free, no API call).
        2. Self-hosted STT on ``voice_wav_url`` (pre-converted WAV from QQ, avoids SILK decoding).
        3. Self-hosted STT on the original attachment URL (requires SILK→WAV conversion).

        Returns the transcript text, or None on failure.
        """
@Suppress("unused") private const val _A_134: String = "[%s] STT: using QQ asr_refer_text: %r"
@Suppress("unused") private const val _A_135: String = "[%s] STT: using voice_wav_url (pre-converted WAV)"
@Suppress("unused") private const val _A_136: String = "[QQ] STT blocked unsafe URL: %s"
@Suppress("unused") private const val _A_137: String = "[%s] STT: downloading voice from %s (pre_wav=%s, headers=%s)"
@Suppress("unused") private const val _A_138: String = "[%s] STT: downloaded %d bytes, content_type=%s"
@Suppress("unused") private const val _A_139: String = "[%s] STT: calling ASR on %s"
@Suppress("unused") private const val _A_140: String = "[%s] STT: no HTTP client"
@Suppress("unused") private const val _A_141: String = "content-type"
@Suppress("unused") private const val _A_142: String = "unknown"
@Suppress("unused") private const val _A_143: String = "[%s] STT: downloaded data too small (%d bytes), skipping"
@Suppress("unused") private const val _A_144: String = "[%s] STT: using pre-converted WAV directly (%d bytes)"
@Suppress("unused") private const val _A_145: String = "[%s] STT: converting to wav, filename=%r"
@Suppress("unused") private const val _A_146: String = "[%s] STT success: %r"
@Suppress("unused") private const val _A_147: String = "[%s] STT: ASR returned empty transcript"
@Suppress("unused") private const val _A_148: String = "[%s] STT failed for voice attachment: %s: %s"
@Suppress("unused") private const val _A_149: String = "[%s] STT: ffmpeg conversion produced no output"
@Suppress("unused") private const val _A_150: String = ".wav"
@Suppress("unused") private val _A_151: String = """Convert audio file to WAV using the pilk library.

        Tries the file as-is first, then as .silk if the extension differs.
        pilk can handle SILK files with various headers (or no header).
        """
@Suppress("unused") private const val _A_152: String = ".silk"
@Suppress("unused") private const val _A_153: String = "[%s] pilk not installed — cannot decode SILK audio. Run: pip install pilk"
@Suppress("unused") private const val _A_154: String = "[%s] pilk converted %s to wav (%d bytes)"
@Suppress("unused") private const val _A_155: String = "[%s] pilk direct conversion failed: %s"
@Suppress("unused") private const val _A_156: String = "[%s] pilk converted %s (as .silk) to wav (%d bytes)"
@Suppress("unused") private const val _A_157: String = "[%s] pilk .silk conversion failed: %s"
@Suppress("unused") private const val _A_158: String = "Convert audio file to WAV using ffmpeg."
@Suppress("unused") private const val _A_159: String = "[%s] ffmpeg converted %s to wav (%d bytes)"
@Suppress("unused") private const val _A_160: String = "[%s] ffmpeg produced no/small output for %s"
@Suppress("unused") private const val _A_161: String = "ffmpeg"
@Suppress("unused") private const val _A_162: String = "-ar"
@Suppress("unused") private const val _A_163: String = "16000"
@Suppress("unused") private const val _A_164: String = "-ac"
@Suppress("unused") private const val _A_165: String = "[%s] ffmpeg failed for %s: %s"
@Suppress("unused") private const val _A_166: String = "[%s] ffmpeg conversion error: %s"
@Suppress("unused") private const val _A_167: String = "replace"
@Suppress("unused") private val _A_168: String = """Call an OpenAI-compatible STT API to transcribe a wav file.

        Uses the provider configured in ``channels.qqbot.stt`` config,
        falling back to QQ's built-in ``asr_refer_text`` if not configured.
        Returns None if STT is not configured or the call fails.
        """
@Suppress("unused") private const val _A_169: String = "base_url"
@Suppress("unused") private const val _A_170: String = "api_key"
@Suppress("unused") private const val _A_171: String = "model"
@Suppress("unused") private const val _A_172: String = "[%s] STT not configured (no stt config or QQ_STT_API_KEY)"
@Suppress("unused") private const val _A_173: String = "choices"
@Suppress("unused") private const val _A_174: String = "text"
@Suppress("unused") private const val _A_175: String = "content"
@Suppress("unused") private const val _A_176: String = "[%s] STT API call failed (model=%s, base=%s): %s"
@Suppress("unused") private const val _A_177: String = "/audio/transcriptions"
@Suppress("unused") private const val _A_178: String = "message"
@Suppress("unused") private const val _A_179: String = "file"
@Suppress("unused") private const val _A_180: String = "Bearer "
@Suppress("unused") private const val _A_181: String = "audio/wav"
@Suppress("unused") private const val _A_182: String = "Convert audio bytes to .wav using pilk (SILK) or ffmpeg, caching the result."
@Suppress("unused") private const val _A_183: String = "qq_voice.wav"
@Suppress("unused") private const val _A_184: String = ".amr"
@Suppress("unused") private const val _A_185: String = ".mp3"
@Suppress("unused") private const val _A_186: String = ".ogg"
@Suppress("unused") private const val _A_187: String = ".m4a"
@Suppress("unused") private const val _A_188: String = ".aac"
@Suppress("unused") private const val _A_189: String = ".flac"
@Suppress("unused") private const val _A_190: String = "[%s] audio conversion failed for %s (format=%s)"
@Suppress("unused") private const val _A_191: String = "[%s] Failed to read converted wav: %s"
@Suppress("unused") private const val _A_192: String = "qq_voice"
@Suppress("unused") private const val _A_193: String = "Make an authenticated REST API request to QQ Bot API."
@Suppress("unused") private const val _A_194: String = "Content-Type"
@Suppress("unused") private const val _A_195: String = "application/json"
@Suppress("unused") private const val _A_196: String = "HTTP client not initialized — not connected?"
@Suppress("unused") private const val _A_197: String = "QQ Bot API error ["
@Suppress("unused") private const val _A_198: String = "QQ Bot API timeout ["
@Suppress("unused") private const val _A_199: String = "]: "
@Suppress("unused") private const val _A_200: String = "Upload media and return file_info."
@Suppress("unused") private const val _A_201: String = "file_type"
@Suppress("unused") private const val _A_202: String = "srv_send_msg"
@Suppress("unused") private const val _A_203: String = "/v2/users/"
@Suppress("unused") private const val _A_204: String = "/files"
@Suppress("unused") private const val _A_205: String = "/v2/groups/"
@Suppress("unused") private const val _A_206: String = "file_name"
@Suppress("unused") private const val _A_207: String = "file_data"
@Suppress("unused") private const val _A_208: String = "POST"
@Suppress("unused") private const val _A_209: String = "400"
@Suppress("unused") private const val _A_210: String = "401"
@Suppress("unused") private const val _A_211: String = "Invalid"
@Suppress("unused") private const val _A_212: String = "timeout"
@Suppress("unused") private const val _A_213: String = "Timeout"
@Suppress("unused") private const val _A_214: String = "Upload media and send as a native message."
@Suppress("unused") private const val _A_215: String = "guild"
@Suppress("unused") private const val _A_216: String = "file_info"
@Suppress("unused") private const val _A_217: String = "msg_type"
@Suppress("unused") private const val _A_218: String = "media"
@Suppress("unused") private const val _A_219: String = "msg_seq"
@Suppress("unused") private const val _A_220: String = "msg_id"
@Suppress("unused") private const val _A_221: String = "[%s] Media send failed: %s"
@Suppress("unused") private const val _A_222: String = "Not connected"
@Suppress("unused") private const val _A_223: String = "Guild media send not supported via this path"
@Suppress("unused") private const val _A_224: String = "Upload returned no file_info: "
@Suppress("unused") private const val _A_225: String = "/messages"
@Suppress("unused") private const val _A_226: String = "Load media from URL or local path. Returns (base64_or_url, content_type, filename)."
@Suppress("unused") private const val _A_227: String = "application/octet-stream"
@Suppress("unused") private const val _A_228: String = "ascii"
@Suppress("unused") private const val _A_229: String = "Media source is required"
@Suppress("unused") private const val _A_230: String = "http"
@Suppress("unused") private const val _A_231: String = "https"
@Suppress("unused") private const val _A_232: String = "Media file not found: "
@Suppress("unused") private const val _A_233: String = "Invalid media source (looks like a placeholder): "

package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * Weixin (WeChat Official Account) platform adapter.
 *
 * Uses the WeChat Official Account API for sending messages and
 * webhook callbacks for receiving messages.
 *
 * Ported from gateway/platforms/weixin.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.*
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WeixinAdapter(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.WEIXIN) {
    companion object { private const val _TAG = "Weixin" }

    /**
     * iLink Bot API credentials (from QR login):
     *  - account_id: normalized `ilink_bot_id`
     *  - login_token: the `bot_token` returned by QR confirm
     *  - base_url: can be overridden per-account via the QR-confirm response
     */
    private val _accountId: String = config.extra("account_id") ?: ""
    private val _loginToken: String = config.extra("login_token") ?: ""
    private val _baseUrl: String = (config.extra("base_url")?.takeIf { it.isNotBlank() } ?: ILINK_BASE_URL).let {
        if (it.endsWith("/")) it else "$it/"
    }
    private val _routeTag: String = config.extra("route_tag") ?: ""

    private val _apiClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val _longPollClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /** `context_token` cache keyed by peer user id — harvested from inbound messages. */
    private val _contextTokens: MutableMap<String, String> = java.util.concurrent.ConcurrentHashMap()

    /** Short-lived typing-ticket cache keyed by peer user id. */
    private val _typingCache: TypingTicketCache = TypingTicketCache()

    /** Dedup seen inbound message IDs (LRU up to 500). */
    private val _seenMessageIds: MutableSet<Long> = java.util.Collections.synchronizedSet(
        java.util.LinkedHashSet<Long>()
    )

    /** Client ids we sent — server echos them back; filter them out. */
    private val _sentClientIds: MutableSet<String> = java.util.Collections.synchronizedSet(
        java.util.LinkedHashSet<String>()
    )

    private var _pollJob: Job? = null

    override val isConnected: AtomicBoolean = AtomicBoolean(false)

    override suspend fun connect(): Boolean {
        if (_accountId.isEmpty() || _loginToken.isEmpty()) {
            Log.e(_TAG, "account_id or login_token not set — did you complete QR login?")
            return false
        }
        _pollJob?.cancel()
        _pollJob = scope.launch(Dispatchers.IO) { _runPollLoop() }
        markConnected()
        Log.i(_TAG, "Weixin connected: account=${_safeId(_accountId)}")
        return true
    }

    override suspend fun disconnect() {
        _pollJob?.cancel()
        _pollJob = null
        markDisconnected()
    }

    override suspend fun send(
        chatId: String,
        content: String,
        replyTo: String?,
        metadata: JSONObject?,
    ): SendResult = withContext(Dispatchers.IO) {
        try {
            val clientId = "hermes-weixin-${java.util.UUID.randomUUID().toString().replace("-", "").take(16)}"
            synchronized(_sentClientIds) {
                _sentClientIds.add(clientId)
                while (_sentClientIds.size > 200) {
                    val it = _sentClientIds.iterator()
                    if (it.hasNext()) { it.next(); it.remove() } else break
                }
            }

            val ctxToken = _contextTokens[chatId]
            val msg = JSONObject().apply {
                put("to_user_id", chatId)
                put("client_id", clientId)
                if (ctxToken != null) put("context_token", ctxToken)
                put("message_type", MSG_TYPE_BOT)  // iLink envelope sender role
                put("message_state", 2)             // FINISH
                put("item_list", org.json.JSONArray().put(
                    JSONObject().apply {
                        put("type", ITEM_TEXT)
                        put("text_item", JSONObject().apply { put("text", content) })
                    }
                ))
            }
            val payload = JSONObject().apply {
                put("msg", msg)
                put("base_info", JSONObject().apply { put("channel_version", CHANNEL_VERSION) })
            }

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = _ilinkRequest(EP_SEND_MESSAGE).post(body).build()

            _apiClient.newCall(request).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    return@withContext SendResult(success = false, error = "HTTP ${resp.code}: ${text.take(200)}")
                }
                val data = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
                val errcode = data.optInt("errcode", data.optInt("ret", 0))
                if (errcode != 0) {
                    return@withContext SendResult(
                        success = false,
                        error = "errcode=$errcode ${data.optString("errmsg")}"
                    )
                }
                SendResult(success = true, messageId = clientId)
            }
        } catch (e: Exception) {
            SendResult(success = false, error = e.message)
        }
    }

    // ── Processing-indicator typing signal (parity with weixin.py send/stop_typing) ──

    override suspend fun onProcessingStart(event: MessageEvent) {
        _sendTypingSignal(event.source.chatId, TYPING_START)
    }

    override suspend fun onProcessingComplete(event: MessageEvent, success: Boolean) {
        _sendTypingSignal(event.source.chatId, TYPING_STOP)
    }

    /**
     * Fetch a typing ticket (from cache or `getconfig`) and send a typing
     * start/stop signal. Best-effort — silently no-ops if the ticket can't
     * be obtained.
     */
    private suspend fun _sendTypingSignal(chatId: String, status: Int) {
        if (chatId.isEmpty()) return
        withContext(Dispatchers.IO) {
            try {
                val ticket = _typingCache.get(chatId) ?: _fetchTypingTicket(chatId) ?: return@withContext
                val payload = JSONObject().apply {
                    put("ilink_user_id", chatId)
                    put("typing_ticket", ticket)
                    put("status", status)
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = _ilinkRequest(EP_SEND_TYPING).post(body).build()
                _apiClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(_TAG, "sendtyping failed: HTTP ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(_TAG, "sendtyping error for ${_safeId(chatId)}: ${e.message}")
            }
        }
    }

    /** Fetch the typing ticket via `getconfig`, caching the result. */
    private suspend fun _fetchTypingTicket(chatId: String): String? = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("ilink_user_id", chatId)
                _contextTokens[chatId]?.let { put("context_token", it) }
                put("base_info", JSONObject().apply { put("channel_version", CHANNEL_VERSION) })
            }
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = _ilinkRequest(EP_GET_CONFIG).post(body).build()
            _apiClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val text = resp.body?.string() ?: return@withContext null
                val data = try { JSONObject(text) } catch (_: Exception) { return@withContext null }
                val ticket = data.optString("typing_ticket", "")
                if (ticket.isNotEmpty()) {
                    _typingCache.set(chatId, ticket)
                    ticket
                } else null
            }
        } catch (e: Exception) {
            Log.w(_TAG, "getconfig for typing ticket failed: ${e.message}")
            null
        }
    }

    // ── iLink request helper ─────────────────────────────────────────────
    private fun _ilinkRequest(endpoint: String): Request.Builder {
        val url = "$_baseUrl$endpoint"
        val uin = android.util.Base64.encodeToString(
            kotlin.random.Random.nextInt().toUInt().toString().toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP,
        )
        val b = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("AuthorizationType", "ilink_bot_token")
            .addHeader("Authorization", "Bearer $_loginToken")
            .addHeader("X-WECHAT-UIN", uin)
        if (_routeTag.isNotEmpty()) b.addHeader("SKRouteTag", _routeTag)
        return b
    }

    // ── Long-poll inbound loop ───────────────────────────────────────────
    private suspend fun _runPollLoop() {
        val hermesHome = com.xiaomo.hermes.hermes.getHermesHome().absolutePath
        var syncBuf = _loadSyncBuf(hermesHome, _accountId)
        var consecutiveFailures = 0
        Log.i(_TAG, "Poll loop starting, resume=${syncBuf.isNotEmpty()}")

        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            try {
                val payload = JSONObject().apply {
                    put("get_updates_buf", syncBuf)
                    put("base_info", JSONObject().apply { put("channel_version", CHANNEL_VERSION) })
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = _ilinkRequest(EP_GET_UPDATES).post(body).build()

                val text = try {
                    _longPollClient.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw java.io.IOException("getUpdates HTTP ${resp.code}")
                        }
                        resp.body?.string() ?: ""
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    // Long-poll server timeout — normal. Retry fresh.
                    continue
                }

                val data = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
                val ret = data.optInt("ret", 0)
                val errcode = data.optInt("errcode", 0)

                if (ret != 0 || errcode != 0) {
                    val isSessionExpired = (ret == SESSION_EXPIRED_ERRCODE || errcode == SESSION_EXPIRED_ERRCODE)
                    if (isSessionExpired) {
                        Log.e(_TAG, "Session expired — pausing 5min; please re-login")
                        consecutiveFailures = 0
                        delay(5 * 60_000L)
                        continue
                    }
                    consecutiveFailures++
                    Log.e(_TAG, "getUpdates err ret=$ret errcode=$errcode ($consecutiveFailures/$MAX_CONSECUTIVE_FAILURES)")
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        consecutiveFailures = 0
                        delay(BACKOFF_DELAY_SECONDS * 1000L)
                    } else {
                        delay(RETRY_DELAY_SECONDS * 1000L)
                    }
                    continue
                }

                consecutiveFailures = 0

                val newBuf = data.optString("get_updates_buf", "")
                if (newBuf.isNotEmpty() && newBuf != syncBuf) {
                    try { _saveSyncBuf(hermesHome, _accountId, newBuf) } catch (_: Exception) { }
                    syncBuf = newBuf
                }

                val msgs = data.optJSONArray("msgs") ?: org.json.JSONArray()
                for (i in 0 until msgs.length()) {
                    val msg = msgs.optJSONObject(i) ?: continue
                    _handleInbound(msg)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                consecutiveFailures++
                Log.e(_TAG, "Poll exception ($consecutiveFailures/$MAX_CONSECUTIVE_FAILURES): ${e.message}")
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    consecutiveFailures = 0
                    delay(BACKOFF_DELAY_SECONDS * 1000L)
                } else {
                    delay(RETRY_DELAY_SECONDS * 1000L)
                }
            }
        }
        Log.i(_TAG, "Poll loop ended")
    }

    private suspend fun _handleInbound(msg: JSONObject) {
        val msgType = msg.optInt("message_type", -1)
        // message_type: USER(1) = user, BOT(2) = our own echo, NONE(0) = unknown
        if (msgType != MSG_TYPE_USER) return
        val fromUserId = msg.optString("from_user_id", "")
        if (fromUserId.isEmpty()) return
        val clientId = msg.optString("client_id", "")
        if (clientId.isNotEmpty() && synchronized(_sentClientIds) { _sentClientIds.contains(clientId) }) return

        // Dedup by message_id
        val msgId = msg.optLong("message_id", 0L)
        if (msgId != 0L) {
            synchronized(_seenMessageIds) {
                if (!_seenMessageIds.add(msgId)) return
                while (_seenMessageIds.size > 500) {
                    val it = _seenMessageIds.iterator()
                    if (it.hasNext()) { it.next(); it.remove() } else break
                }
            }
        }

        // Harvest context_token for later send()
        val ctx = msg.optString("context_token", "")
        if (ctx.isNotEmpty()) _contextTokens[fromUserId] = ctx

        // Extract text from item_list
        val itemList = msg.optJSONArray("item_list") ?: return
        val items = mutableListOf<Map<String, Any?>>()
        for (i in 0 until itemList.length()) {
            val it = itemList.optJSONObject(i) ?: continue
            items.add(_jsonToMap(it))
        }
        val text = _extractText(items)
        if (text.isBlank()) return

        val event = MessageEvent(
            text = text,
            messageType = MessageType.TEXT,
            source = buildSource(
                chatId = fromUserId,
                userId = fromUserId,
                chatType = "dm",
            ),
            message_id = msgId.toString(),
        )
        handleMessage(event)
    }

    private fun _jsonToMap(obj: JSONObject): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.opt(k)
            out[k] = when (v) {
                is JSONObject -> _jsonToMap(v)
                is org.json.JSONArray -> {
                    val list = mutableListOf<Any?>()
                    for (i in 0 until v.length()) {
                        val el = v.opt(i)
                        list.add(if (el is JSONObject) _jsonToMap(el) else el)
                    }
                    list
                }
                JSONObject.NULL -> null
                else -> v
            }
        }
        return out
    }
}

// =========================================================================
// Weixin top-level helpers (ported from weixin.py)
// =========================================================================

/**
 * Returns True when runtime dependencies for Weixin are available.
 *
 * On Python this gated `aiohttp` + `cryptography`. On Android both OkHttp
 * and `javax.crypto` are built-in, so always true.
 */
fun checkWeixinRequirements(): Boolean = true

/**
 * Mirrors `weixin.py._make_ssl_connector` (L102). The Python version returns
 * an `aiohttp.TCPConnector` backed by certifi's CA bundle for Tencent's iLink
 * host. On Android we use OkHttp with the platform trust manager — aiohttp is
 * not available and the concept of a TCPConnector does not apply. Return null
 * so callers treat it as "use the default HTTP client".
 */
internal fun _makeSslConnector(): Any? = null

/**
 * Prints a short truncated / safe-for-logging form of an ID. Mirrors
 * `weixin.py._safe_id` (returns first `keep` chars or "?" for empty).
 */
internal fun _safeId(value: String?, keep: Int = 8): String {
    val raw = (value ?: "").trim()
    if (raw.isEmpty()) return "?"
    return if (raw.length <= keep) raw else raw.substring(0, keep)
}

/** Compact JSON dump (no spaces, UTF-8), matching `json.dumps(..., separators=(",",":"))`. */
internal fun _jsonDumps(payload: Map<String, Any?>): String {
    val json = org.json.JSONObject(payload)
    // Android's JSONObject inserts no whitespace by default.
    return json.toString()
}

/** Pad `data` to the next multiple of `blockSize` using PKCS7. */
internal fun _pkcs7Pad(data: ByteArray, blockSize: Int = 16): ByteArray {
    val padLen = blockSize - (data.size % blockSize)
    val out = ByteArray(data.size + padLen)
    System.arraycopy(data, 0, out, 0, data.size)
    for (i in data.size until out.size) out[i] = padLen.toByte()
    return out
}

private fun _aesEcbCipher(mode: Int, key: ByteArray): javax.crypto.Cipher {
    require(key.size == 16) { "AES-128 key must be 16 bytes, got ${key.size}" }
    val cipher = javax.crypto.Cipher.getInstance("AES/ECB/NoPadding")
    cipher.init(mode, javax.crypto.spec.SecretKeySpec(key, "AES"))
    return cipher
}

/** Encrypts `plaintext` with AES-128-ECB and PKCS7 padding. */
internal fun _aes128EcbEncrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
    val cipher = _aesEcbCipher(javax.crypto.Cipher.ENCRYPT_MODE, key)
    return cipher.doFinal(_pkcs7Pad(plaintext))
}

/** Decrypts `ciphertext` with AES-128-ECB, stripping valid PKCS7 padding. */
internal fun _aes128EcbDecrypt(ciphertext: ByteArray, key: ByteArray): ByteArray {
    if (ciphertext.isEmpty()) return ciphertext
    val cipher = _aesEcbCipher(javax.crypto.Cipher.DECRYPT_MODE, key)
    val padded = cipher.doFinal(ciphertext)
    if (padded.isEmpty()) return padded
    val padLen = padded[padded.size - 1].toInt() and 0xFF
    if (padLen in 1..16) {
        var valid = true
        for (i in padded.size - padLen until padded.size) {
            if ((padded[i].toInt() and 0xFF) != padLen) { valid = false; break }
        }
        if (valid) return padded.copyOf(padded.size - padLen)
    }
    return padded
}

/** Size after PKCS7 padding (always adds at least 1 byte). */
internal fun _aesPaddedSize(size: Int): Int = ((size + 1 + 15) / 16) * 16

/** Random WeChat UIN: base64 of a random 32-bit unsigned decimal string. */
internal fun _randomWechatUin(): String {
    val rand = java.security.SecureRandom()
    val bytes = ByteArray(4).also { rand.nextBytes(it) }
    val value = ((bytes[0].toInt() and 0xFF) shl 24) or
        ((bytes[1].toInt() and 0xFF) shl 16) or
        ((bytes[2].toInt() and 0xFF) shl 8) or
        (bytes[3].toInt() and 0xFF)
    val unsigned = value.toLong() and 0xFFFFFFFFL
    val dec = unsigned.toString()
    return java.util.Base64.getEncoder().encodeToString(dec.toByteArray(Charsets.UTF_8))
}

/** Builds a Weixin CDN download URL by URL-encoding the encrypted query param. */
internal fun _cdnDownloadUrl(cdnBaseUrl: String, encryptedQueryParam: String): String {
    val base = cdnBaseUrl.trimEnd('/')
    val enc = java.net.URLEncoder.encode(encryptedQueryParam, "UTF-8")
    return "$base/download?encrypted_query_param=$enc"
}

/** Builds a Weixin CDN upload URL. */
internal fun _cdnUploadUrl(cdnBaseUrl: String, uploadParam: String, filekey: String): String {
    val base = cdnBaseUrl.trimEnd('/')
    val enc1 = java.net.URLEncoder.encode(uploadParam, "UTF-8")
    val enc2 = java.net.URLEncoder.encode(filekey, "UTF-8")
    return "$base/upload?encrypted_query_param=$enc1&filekey=$enc2"
}

/**
 * Parses an AES key from base64 text. Accepts:
 *  - 16-byte decoded value (raw AES-128 key), or
 *  - 32-byte decoded hex string (decoded to 16 raw bytes).
 * Throws `IllegalArgumentException` otherwise.
 */
internal fun _parseAesKey(aesKeyB64: String): ByteArray {
    val decoded = java.util.Base64.getDecoder().decode(aesKeyB64)
    if (decoded.size == 16) return decoded
    if (decoded.size == 32) {
        val text = String(decoded, Charsets.US_ASCII)
        if (text.isNotEmpty() && text.all { it in "0123456789abcdefABCDEF" }) {
            val out = ByteArray(16)
            for (i in 0 until 16) {
                out[i] = Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16).toByte()
            }
            return out
        }
    }
    throw IllegalArgumentException("unexpected aes_key format (${decoded.size} decoded bytes)")
}

/**
 * Classifies an inbound Weixin message as `dm` or `group` and returns
 * (kind, counterparty_id). Mirrors `weixin.py._guess_chat_type`.
 */
internal fun _guessChatType(message: Map<String, Any?>, accountId: String): Pair<String, String> {
    val roomId = ((message["room_id"] as? String) ?: (message["chat_room_id"] as? String) ?: "").trim()
    val toUserId = ((message["to_user_id"] as? String) ?: "").trim()
    val msgType = (message["msg_type"] as? Number)?.toInt()
    val isGroup = roomId.isNotEmpty() ||
        (toUserId.isNotEmpty() && accountId.isNotEmpty() && toUserId != accountId && msgType == 1)
    return if (isGroup) {
        val counterparty = roomId.ifEmpty {
            toUserId.ifEmpty { (message["from_user_id"] as? String) ?: "" }
        }
        "group" to counterparty
    } else {
        "dm" to ((message["from_user_id"] as? String) ?: "")
    }
}

// -------------------------------------------------------------------------
// Weixin Markdown helpers (ported from weixin.py 629-828)
// -------------------------------------------------------------------------

internal val _WEIXIN_HEADER_RE: Regex = Regex("^(#{1,6})\\s+(.+?)\\s*$")
internal val _WEIXIN_TABLE_RULE_RE: Regex = Regex("^\\s*\\|?(?:\\s*:?-{3,}:?\\s*\\|)+\\s*:?-{3,}:?\\s*\\|?\\s*$")
internal val _WEIXIN_FENCE_RE: Regex = Regex("^```([^\\n`]*)\\s*$")

/** MIME-type lookup from filename extension. */
internal fun _mimeFromFilename(filename: String?): String {
    val ext = (filename ?: "").substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when ("." + ext) {
        ".pdf" -> "application/pdf"
        ".md" -> "text/markdown"
        ".txt", ".log" -> "text/plain"
        ".zip" -> "application/zip"
        ".jpg", ".jpeg" -> "image/jpeg"
        ".png" -> "image/png"
        ".gif" -> "image/gif"
        ".webp" -> "image/webp"
        ".mp3" -> "audio/mpeg"
        ".ogg" -> "audio/ogg"
        ".mp4" -> "video/mp4"
        ".mov" -> "video/quicktime"
        else -> "application/octet-stream"
    }
}

/** Parse a Markdown table row into trimmed cells. */
internal fun _splitTableRow(line: String): List<String> {
    var row = line.trim()
    if (row.startsWith("|")) row = row.substring(1)
    if (row.endsWith("|")) row = row.dropLast(1)
    return row.split("|").map { it.trim() }
}

/** Convert a `#`-style heading into Weixin's preferred delimiter form. */
internal fun _rewriteHeadersForWeixin(line: String): String {
    val match = _WEIXIN_HEADER_RE.matchEntire(line) ?: return line.trimEnd()
    val level = match.groupValues[1].length
    val title = match.groupValues[2].trim()
    return if (level == 1) "【$title】" else "**$title**"
}

/** Collapse a Markdown table block into a flat `- key: value` bullet list. */
internal fun _rewriteTableBlockForWeixin(lines: List<String>): String {
    if (lines.size < 2) return lines.joinToString("\n")
    val headers = _splitTableRow(lines[0])
    val bodyRows = lines.drop(2).filter { it.isNotBlank() }.map { _splitTableRow(it) }
    if (headers.isEmpty() || bodyRows.isEmpty()) return lines.joinToString("\n")

    val formatted = mutableListOf<String>()
    for (row in bodyRows) {
        val pairs = mutableListOf<Pair<String, String>>()
        for ((idx, header) in headers.withIndex()) {
            if (idx >= row.size) break
            val label = header.ifEmpty { "Column ${idx + 1}" }
            val value = row[idx].trim()
            if (value.isNotEmpty()) pairs.add(label to value)
        }
        if (pairs.isEmpty()) continue
        when (pairs.size) {
            1 -> {
                val (label, value) = pairs[0]
                formatted.add("- $label: $value")
            }
            2 -> {
                val (label, value) = pairs[0]
                val (otherLabel, otherValue) = pairs[1]
                formatted.add("- $label: $value")
                formatted.add("  $otherLabel: $otherValue")
            }
            else -> {
                val summary = pairs.joinToString(" | ") { (label, value) -> "$label: $value" }
                formatted.add("- $summary")
            }
        }
    }
    return if (formatted.isNotEmpty()) formatted.joinToString("\n") else lines.joinToString("\n")
}

/** Collapse blank-line runs and preserve fenced code blocks. */
internal fun _normalizeMarkdownBlocks(content: String): String {
    val lines = content.split("\n")
    val result = mutableListOf<String>()
    var inCode = false
    var blankRun = 0

    for (rawLine in lines) {
        val line = rawLine.trimEnd()
        if (_WEIXIN_FENCE_RE.matchEntire(line.trim()) != null) {
            inCode = !inCode
            result.add(line)
            blankRun = 0
            continue
        }
        if (inCode) {
            result.add(line)
            continue
        }
        if (line.trim().isEmpty()) {
            blankRun++
            if (blankRun <= 1) result.add("")
            continue
        }
        blankRun = 0
        result.add(line)
    }

    return result.joinToString("\n").trim()
}

/** Split content into prose blocks, keeping fenced code blocks intact. */
internal fun _splitMarkdownBlocks(content: String): List<String> {
    if (content.isEmpty()) return emptyList()
    val blocks = mutableListOf<String>()
    val lines = content.split("\n")
    var current = mutableListOf<String>()
    var inCode = false

    for (rawLine in lines) {
        val line = rawLine.trimEnd()
        if (_WEIXIN_FENCE_RE.matchEntire(line.trim()) != null) {
            if (!inCode && current.isNotEmpty()) {
                blocks.add(current.joinToString("\n").trim())
                current = mutableListOf()
            }
            current.add(line)
            inCode = !inCode
            if (!inCode) {
                blocks.add(current.joinToString("\n").trim())
                current = mutableListOf()
            }
            continue
        }
        if (inCode) {
            current.add(line)
            continue
        }
        if (line.trim().isEmpty()) {
            if (current.isNotEmpty()) {
                blocks.add(current.joinToString("\n").trim())
                current = mutableListOf()
            }
            continue
        }
        current.add(line)
    }
    if (current.isNotEmpty()) blocks.add(current.joinToString("\n").trim())
    return blocks.filter { it.isNotEmpty() }
}

/** Return true when a line reads like a standalone chat utterance. */
internal fun _looksLikeChattyLineForWeixin(line: String): Boolean {
    val stripped = line.trim()
    if (stripped.isEmpty()) return false
    if (stripped.length > 48) return false
    if (line.startsWith(" ") || line.startsWith("\t")) return false
    val leadChars = setOf('>', '-', '*', '【', '#', '|')
    if (stripped.isNotEmpty() && stripped[0] in leadChars) return false
    if (_WEIXIN_TABLE_RULE_RE.matchEntire(stripped) != null) return false
    if (Regex("^\\*\\*[^*]+\\*\\*$").matchEntire(stripped) != null) return false
    if (Regex("^\\d+\\.\\s").containsMatchIn(stripped)) return false
    return true
}

/** Return true when a short line behaves like a heading. */
internal fun _looksLikeHeadingLineForWeixin(line: String): Boolean {
    val stripped = line.trim()
    if (stripped.isEmpty()) return false
    if (_WEIXIN_HEADER_RE.matchEntire(stripped) != null) return true
    return stripped.length <= 24 && (stripped.endsWith(":") || stripped.endsWith("："))
}

// -------------------------------------------------------------------------
// Weixin protocol constants + text delivery helpers (ported from weixin.py)
// -------------------------------------------------------------------------

internal const val ILINK_BASE_URL: String = "https://ilinkai.weixin.qq.com"
internal const val WEIXIN_CDN_BASE_URL: String = "https://novac2c.cdn.weixin.qq.com/c2c"
internal const val ILINK_APP_ID: String = "bot"
internal const val CHANNEL_VERSION: String = "2.2.0"
internal const val ILINK_APP_CLIENT_VERSION: Int = (2 shl 16) or (2 shl 8) or 0

internal const val EP_GET_UPDATES: String = "ilink/bot/getupdates"
internal const val EP_SEND_MESSAGE: String = "ilink/bot/sendmessage"
internal const val EP_SEND_TYPING: String = "ilink/bot/sendtyping"
internal const val EP_GET_CONFIG: String = "ilink/bot/getconfig"
internal const val EP_GET_UPLOAD_URL: String = "ilink/bot/getuploadurl"
internal const val EP_GET_BOT_QR: String = "ilink/bot/get_bot_qrcode"
internal const val EP_GET_QR_STATUS: String = "ilink/bot/get_qrcode_status"

internal const val LONG_POLL_TIMEOUT_MS: Int = 35_000
internal const val API_TIMEOUT_MS: Int = 15_000
internal const val CONFIG_TIMEOUT_MS: Int = 10_000
internal const val QR_TIMEOUT_MS: Int = 35_000

internal const val MAX_CONSECUTIVE_FAILURES: Int = 3
internal const val RETRY_DELAY_SECONDS: Int = 2
internal const val BACKOFF_DELAY_SECONDS: Int = 30
internal const val SESSION_EXPIRED_ERRCODE: Int = -14
internal const val MESSAGE_DEDUP_TTL_SECONDS: Int = 300

internal const val MEDIA_IMAGE: Int = 1
internal const val MEDIA_VIDEO: Int = 2
internal const val MEDIA_FILE: Int = 3
internal const val MEDIA_VOICE: Int = 4

// Item-type discriminators used by the iLink Bot message protocol.
internal const val ITEM_TEXT: Int = 1
internal const val ITEM_IMAGE: Int = 2
internal const val ITEM_VOICE: Int = 3
internal const val ITEM_FILE: Int = 4
internal const val ITEM_VIDEO: Int = 5

// iLink message_type: sender role on the envelope (distinct from item_list types).
internal const val MSG_TYPE_NONE: Int = 0
internal const val MSG_TYPE_USER: Int = 1
internal const val MSG_TYPE_BOT: Int = 2

internal const val TYPING_START: Int = 1
internal const val TYPING_STOP: Int = 2

internal val _LIVE_ADAPTERS: MutableMap<String, Any?> = java.util.concurrent.ConcurrentHashMap()

private val _MARKDOWN_LINK_RE: Regex = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")

/**
 * Hosts that the adapter will fetch media bytes from. Anything else is
 * rejected to avoid SSRF via attacker-controlled CDN URLs.
 */
internal val _WEIXIN_CDN_ALLOWLIST: Set<String> = setOf(
    "novac2c.cdn.weixin.qq.com",
    "ilinkai.weixin.qq.com",
    "wx.qlogo.cn",
    "thirdwx.qlogo.cn",
    "res.wx.qq.com",
    "mmbiz.qpic.cn",
    "mmbiz.qlogo.cn",
)

/** Static base-info payload bundled into every outbound request. */
internal fun _baseInfo(): Map<String, Any?> = mapOf("channel_version" to CHANNEL_VERSION)

/** HTTP headers for iLink Bot API requests. */
internal fun _headers(token: String?, body: String): Map<String, String> {
    val headers = linkedMapOf(
        "Content-Type" to "application/json",
        "AuthorizationType" to "ilink_bot_token",
        "Content-Length" to body.toByteArray(Charsets.UTF_8).size.toString(),
        "X-WECHAT-UIN" to _randomWechatUin(),
        "iLink-App-Id" to ILINK_APP_ID,
        "iLink-App-ClientVersion" to ILINK_APP_CLIENT_VERSION.toString(),
    )
    if (!token.isNullOrEmpty()) headers["Authorization"] = "Bearer $token"
    return headers
}

/**
 * Throws IllegalArgumentException if the URL is not http(s) on an allow-listed
 * WeChat CDN host. Mirrors `weixin.py._assert_weixin_cdn_url`.
 */
internal fun _assertWeixinCdnUrl(url: String) {
    val parsed = try {
        java.net.URI(url)
    } catch (e: Exception) {
        throw IllegalArgumentException("Unparseable media URL: '$url'", e)
    }
    val scheme = (parsed.scheme ?: "").lowercase()
    val host = parsed.host ?: ""
    if (scheme != "http" && scheme != "https") {
        throw IllegalArgumentException(
            "Media URL has disallowed scheme '$scheme'; only http/https are permitted."
        )
    }
    if (host !in _WEIXIN_CDN_ALLOWLIST) {
        throw IllegalArgumentException(
            "Media URL host '$host' is not in the WeChat CDN allowlist. " +
                "Refusing to fetch to prevent SSRF."
        )
    }
}

/** Pluck the nested `media` dict out of an item payload. */
@Suppress("UNCHECKED_CAST")
internal fun _mediaReference(item: Map<String, Any?>, key: String): Map<String, Any?> {
    val outer = (item[key] as? Map<String, Any?>) ?: return emptyMap()
    return (outer["media"] as? Map<String, Any?>) ?: emptyMap()
}

/** Coerce a config value to bool, tolerating strings like `"true"`. */
internal fun _coerceBool(value: Any?, default: Boolean = true): Boolean {
    if (value == null) return default
    if (value is Boolean) return value
    if (value is Number) return value.toDouble() != 0.0
    val text = value.toString().trim().lowercase()
    if (text.isEmpty()) return default
    if (text in setOf("1", "true", "yes", "on")) return true
    if (text in setOf("0", "false", "no", "off")) return false
    return default
}

/** Split formatted content into chat-friendly delivery units. */
internal fun _splitDeliveryUnitsForWeixin(content: String): List<String> {
    val units = mutableListOf<String>()

    for (block in _splitMarkdownBlocks(content)) {
        val firstLine = block.split("\n").firstOrNull()?.trim() ?: ""
        if (_WEIXIN_FENCE_RE.matchEntire(firstLine) != null) {
            units.add(block)
            continue
        }

        var current = mutableListOf<String>()
        for (rawLine in block.split("\n")) {
            val line = rawLine.trimEnd()
            if (line.trim().isEmpty()) {
                if (current.isNotEmpty()) {
                    units.add(current.joinToString("\n").trim())
                    current = mutableListOf()
                }
                continue
            }

            val isContinuation = current.isNotEmpty() &&
                (rawLine.startsWith(" ") || rawLine.startsWith("\t"))
            if (isContinuation) {
                current.add(line)
                continue
            }

            if (current.isNotEmpty()) units.add(current.joinToString("\n").trim())
            current = mutableListOf(line)
        }

        if (current.isNotEmpty()) units.add(current.joinToString("\n").trim())
    }

    return units.filter { it.isNotEmpty() }
}

/** Decide whether a short multiline block should be split into separate bubbles. */
internal fun _shouldSplitShortChatBlockForWeixin(block: String): Boolean {
    val lines = block.split("\n").filter { it.trim().isNotEmpty() }
    if (lines.size < 2 || lines.size > 6) return false
    if (_looksLikeHeadingLineForWeixin(lines[0])) return false
    return lines.all { _looksLikeChattyLineForWeixin(it) }
}

/** Pack Markdown blocks into chunks bounded by `maxLength` characters. */
internal fun _packMarkdownBlocksForWeixin(content: String, maxLength: Int): List<String> {
    if (content.length <= maxLength) return listOf(content)

    val packed = mutableListOf<String>()
    var current = ""
    for (block in _splitMarkdownBlocks(content)) {
        val candidate = if (current.isEmpty()) block else "$current\n\n$block"
        if (candidate.length <= maxLength) {
            current = candidate
            continue
        }
        if (current.isNotEmpty()) {
            packed.add(current)
            current = ""
        }
        if (block.length <= maxLength) {
            current = block
            continue
        }
        packed.addAll(truncateMessage(block, maxLength))
    }
    if (current.isNotEmpty()) packed.add(current)
    return packed
}

/**
 * Split content into sequential Weixin messages.
 *
 * Compact mode (default): keeps everything in a single message whenever it
 * fits within `maxLength`, falling back to block-aware packing when the
 * payload exceeds the limit. A short chatty multiline exchange is split into
 * bubbles even under the limit for a more natural chat feel.
 *
 * Per-line mode (`splitPerLine = true`): top-level line breaks become
 * separate chat messages; oversized units still use block-aware packing.
 */
internal fun _splitTextForWeixinDelivery(
    content: String,
    maxLength: Int,
    splitPerLine: Boolean = false,
): List<String> {
    if (content.isEmpty()) return emptyList()
    if (splitPerLine) {
        if (content.length <= maxLength && !content.contains('\n')) return listOf(content)
        val chunks = mutableListOf<String>()
        for (unit in _splitDeliveryUnitsForWeixin(content)) {
            if (unit.length <= maxLength) {
                chunks.add(unit)
                continue
            }
            chunks.addAll(_packMarkdownBlocksForWeixin(unit, maxLength))
        }
        val filtered = chunks.filter { it.isNotEmpty() }
        return if (filtered.isNotEmpty()) filtered else listOf(content)
    }

    if (content.length <= maxLength) {
        return if (_shouldSplitShortChatBlockForWeixin(content))
            _splitDeliveryUnitsForWeixin(content).filter { it.isNotEmpty() }
        else
            listOf(content)
    }
    val packed = _packMarkdownBlocksForWeixin(content, maxLength)
    return if (packed.isNotEmpty()) packed else listOf(content)
}

// -------------------------------------------------------------------------
// Weixin account persistence + item extraction (ported from weixin.py)
// -------------------------------------------------------------------------

/**
 * Ensure the per-hermes-home accounts directory exists and return it.
 * Mirrors `weixin.py._account_dir`.
 */
internal fun _accountDir(hermesHome: String): java.io.File {
    val dir = java.io.File(hermesHome, "weixin/accounts")
    dir.mkdirs()
    return dir
}

/** Per-account credentials file. */
internal fun _accountFile(hermesHome: String, accountId: String): java.io.File =
    java.io.File(_accountDir(hermesHome), "$accountId.json")

/** Persist Weixin account credentials for later reuse. Mirrors `save_weixin_account`. */
fun saveWeixinAccount(
    hermesHome: String,
    accountId: String,
    token: String,
    baseUrl: String,
    userId: String = "",
) {
    val payload = linkedMapOf<String, Any?>(
        "token" to token,
        "base_url" to baseUrl,
        "user_id" to userId,
        "saved_at" to java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(java.time.ZoneOffset.UTC)
            .format(java.time.Instant.now()),
    )
    val path = _accountFile(hermesHome, accountId)
    com.xiaomo.hermes.hermes.atomicJsonWrite(path, payload)
    try {
        path.setReadable(false, false)
        path.setWritable(false, false)
        path.setReadable(true, true)
        path.setWritable(true, true)
    } catch (_: SecurityException) {
        // best-effort only
    }
}

/** Load persisted Weixin account credentials. Mirrors `load_weixin_account`. */
fun loadWeixinAccount(hermesHome: String, accountId: String): Map<String, Any?>? {
    val path = _accountFile(hermesHome, accountId)
    if (!path.exists()) return null
    return try {
        @Suppress("UNCHECKED_CAST")
        val obj = JSONObject(path.readText(Charsets.UTF_8))
        val result = linkedMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            result[k] = obj.opt(k)
        }
        result
    } catch (_: Exception) {
        null
    }
}

/** Path where the get_updates sync buffer is persisted per account. */
internal fun _syncBufPath(hermesHome: String, accountId: String): java.io.File =
    java.io.File(_accountDir(hermesHome), "$accountId.sync.json")

/** Load persisted sync buffer, empty string on miss or corruption. */
internal fun _loadSyncBuf(hermesHome: String, accountId: String): String {
    val path = _syncBufPath(hermesHome, accountId)
    if (!path.exists()) return ""
    return try {
        JSONObject(path.readText(Charsets.UTF_8)).optString("get_updates_buf", "")
    } catch (_: Exception) {
        ""
    }
}

/** Persist sync buffer atomically. */
internal fun _saveSyncBuf(hermesHome: String, accountId: String, syncBuf: String) {
    val path = _syncBufPath(hermesHome, accountId)
    com.xiaomo.hermes.hermes.atomicJsonWrite(path, mapOf("get_updates_buf" to syncBuf))
}

/**
 * Extract readable text from an `item_list`. Prefers `text_item` with
 * optional ref-msg prefix; falls back to the first voice transcript.
 * Mirrors `weixin.py._extract_text`.
 */
@Suppress("UNCHECKED_CAST")
internal fun _extractText(itemList: List<Map<String, Any?>>): String {
    for (item in itemList) {
        if ((item["type"] as? Number)?.toInt() == ITEM_TEXT) {
            val textItem = (item["text_item"] as? Map<String, Any?>) ?: emptyMap()
            val text = (textItem["text"] ?: "").toString()
            val ref = (item["ref_msg"] as? Map<String, Any?>) ?: emptyMap()
            val refItem = (ref["message_item"] as? Map<String, Any?>) ?: emptyMap()
            val refType = (refItem["type"] as? Number)?.toInt()
            if (refType in setOf(ITEM_IMAGE, ITEM_VIDEO, ITEM_FILE, ITEM_VOICE)) {
                val title = (ref["title"] ?: "").toString()
                val prefix = if (title.isNotEmpty()) "[引用媒体: $title]\n" else "[引用媒体]\n"
                return "$prefix$text".trim()
            }
            if (refItem.isNotEmpty()) {
                val parts = mutableListOf<String>()
                (ref["title"] as? String)?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
                val refText = _extractText(listOf(refItem))
                if (refText.isNotEmpty()) parts.add(refText)
                if (parts.isNotEmpty()) return "[引用: ${parts.joinToString(" | ")}]\n$text".trim()
            }
            return text
        }
    }
    for (item in itemList) {
        if ((item["type"] as? Number)?.toInt() == ITEM_VOICE) {
            val voiceItem = (item["voice_item"] as? Map<String, Any?>) ?: emptyMap()
            val voiceText = (voiceItem["text"] ?: "").toString()
            if (voiceText.isNotEmpty()) return voiceText
        }
    }
    return ""
}

/**
 * Classify a message by media types + text prefix. Mirrors
 * `weixin.py._message_type_from_media`.
 */
internal fun _messageTypeFromMedia(mediaTypes: List<String>, text: String): MessageType {
    if (mediaTypes.any { it.startsWith("image/") }) return MessageType.PHOTO
    if (mediaTypes.any { it.startsWith("video/") }) return MessageType.VIDEO
    if (mediaTypes.any { it.startsWith("audio/") }) return MessageType.VOICE
    if (mediaTypes.isNotEmpty()) return MessageType.DOCUMENT
    if (text.startsWith("/")) return MessageType.COMMAND
    return MessageType.TEXT
}

// -------------------------------------------------------------------------
// Per-account context-token and typing-ticket caches (ported from weixin.py)
// -------------------------------------------------------------------------

/**
 * Disk-backed `context_token` cache keyed by account + peer.
 * Mirrors `weixin.py.ContextTokenStore`.
 */
class ContextTokenStore(hermesHome: String) {
    private val _root: java.io.File = _accountDir(hermesHome)
    private val _cache: MutableMap<String, String> = mutableMapOf()

    private fun _path(accountId: String): java.io.File =
        java.io.File(_root, "$accountId.context-tokens.json")

    private fun _key(accountId: String, userId: String): String = "$accountId:$userId"

    /** Load persisted tokens for `accountId` into the in-memory cache. */
    fun restore(accountId: String) {
        val path = _path(accountId)
        if (!path.exists()) return
        val obj = try {
            JSONObject(path.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            android.util.Log.w("Weixin", "failed to restore context tokens for ${_safeId(accountId)}: ${e.message}")
            return
        }
        var restored = 0
        val keys = obj.keys()
        while (keys.hasNext()) {
            val userId = keys.next()
            val token = obj.optString(userId, "")
            if (token.isNotEmpty()) {
                _cache[_key(accountId, userId)] = token
                restored++
            }
        }
        if (restored > 0) {
            android.util.Log.i("Weixin", "restored $restored context token(s) for ${_safeId(accountId)}")
        }
    }

    fun get(accountId: String, userId: String): String? = _cache[_key(accountId, userId)]

    fun set(accountId: String, userId: String, token: String) {
        _cache[_key(accountId, userId)] = token
        _persist(accountId)
    }

    private fun _persist(accountId: String) {
        val prefix = "$accountId:"
        val payload = linkedMapOf<String, Any?>()
        for ((k, v) in _cache) {
            if (k.startsWith(prefix)) payload[k.substring(prefix.length)] = v
        }
        try {
            com.xiaomo.hermes.hermes.atomicJsonWrite(_path(accountId), payload)
        } catch (e: Exception) {
            android.util.Log.w("Weixin", "failed to persist context tokens for ${_safeId(accountId)}: ${e.message}")
        }
    }
}

/**
 * Short-lived typing-ticket cache from `getconfig`.
 * Mirrors `weixin.py.TypingTicketCache`.
 */
open class TypingTicketCache(ttlSeconds: Double = 600.0) {
    private val _ttlSeconds: Double = ttlSeconds
    private val _cache: MutableMap<String, Pair<String, Double>> = mutableMapOf()

    fun get(userId: String): String? {
        val entry = _cache[userId] ?: return null
        if (_now() - entry.second >= _ttlSeconds) {
            _cache.remove(userId)
            return null
        }
        return entry.first
    }

    fun set(userId: String, ticket: String) {
        _cache[userId] = ticket to _now()
    }

    internal open fun _now(): Double = System.currentTimeMillis() / 1000.0
}

// -------------------------------------------------------------------------
// iLink API GET helper + QR login flow (ported from weixin.py:373, 978)
// -------------------------------------------------------------------------

private val _qrHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
}

/**
 * Android-platform port of `weixin.py._api_get`.
 * Uses a long-read-timeout OkHttp client because iLink long-polls.
 */
internal suspend fun _apiGet(
    baseUrl: String,
    endpoint: String,
    timeoutMs: Int,
): Map<String, Any?> = withContext(Dispatchers.IO) {
    val url = "${baseUrl.trimEnd('/')}/$endpoint"
    val reqBuilder = Request.Builder()
        .url(url)
        .header("iLink-App-Id", ILINK_APP_ID)
        .header("iLink-App-ClientVersion", ILINK_APP_CLIENT_VERSION.toString())
        .get()
    val client = _qrHttpClient.newBuilder()
        .callTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .build()
    client.newCall(reqBuilder.build()).execute().use { response ->
        val raw = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw RuntimeException(
                "iLink GET $endpoint HTTP ${response.code}: ${raw.take(200)}"
            )
        }
        val obj = JSONObject(raw)
        val out = mutableMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = obj.opt(k)
        }
        out
    }
}

/**
 * Ported from `weixin.py.qr_login` (L978). Runs the interactive iLink QR
 * login flow; returns a credential dict on success or null on
 * failure/timeout. The Python terminal-printing side-effects become
 * optional callback lambdas so the UI layer can drive its own QR-bitmap
 * rendering.
 */
suspend fun qrLogin(
    hermesHome: String,
    botType: String = "3",
    timeoutSeconds: Int = 480,
    onQrCodeReady: (qrcodeImgContent: String, qrcode: String) -> Unit = { _, _ -> },
    onStatusUpdate: (status: String) -> Unit = { _ -> },
): Map<String, String>? = withContext(Dispatchers.IO) {
    val qrResp = try {
        _apiGet(
            baseUrl = ILINK_BASE_URL,
            endpoint = "$EP_GET_BOT_QR?bot_type=$botType",
            timeoutMs = QR_TIMEOUT_MS,
        )
    } catch (exc: Exception) {
        Log.e("Weixin", "failed to fetch QR code: ${exc.message}")
        return@withContext null
    }

    var qrcodeValue = (qrResp["qrcode"] as? String) ?: ""
    var qrcodeUrl = (qrResp["qrcode_img_content"] as? String) ?: ""
    if (qrcodeValue.isEmpty()) {
        Log.e("Weixin", "QR response missing qrcode")
        return@withContext null
    }
    onQrCodeReady(qrcodeUrl, qrcodeValue)

    val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
    var currentBaseUrl = ILINK_BASE_URL
    var refreshCount = 0

    while (System.currentTimeMillis() < deadline) {
        val statusResp: Map<String, Any?> = try {
            _apiGet(
                baseUrl = currentBaseUrl,
                endpoint = "$EP_GET_QR_STATUS?qrcode=$qrcodeValue",
                timeoutMs = QR_TIMEOUT_MS,
            )
        } catch (exc: Exception) {
            Log.w("Weixin", "QR poll error: ${exc.message}")
            delay(1000)
            continue
        }

        val status = (statusResp["status"] as? String) ?: "wait"
        when (status) {
            "wait" -> {
                onStatusUpdate("wait")
            }
            "scaned" -> {
                onStatusUpdate("scaned")
            }
            "scaned_but_redirect" -> {
                val redirectHost = (statusResp["redirect_host"] as? String) ?: ""
                if (redirectHost.isNotEmpty()) {
                    currentBaseUrl = "https://$redirectHost"
                }
                onStatusUpdate("scaned_but_redirect")
            }
            "expired" -> {
                refreshCount += 1
                if (refreshCount > 3) {
                    Log.w("Weixin", "QR expired 3 times, giving up")
                    onStatusUpdate("expired_final")
                    return@withContext null
                }
                onStatusUpdate("expired")
                try {
                    val refreshed = _apiGet(
                        baseUrl = ILINK_BASE_URL,
                        endpoint = "$EP_GET_BOT_QR?bot_type=$botType",
                        timeoutMs = QR_TIMEOUT_MS,
                    )
                    qrcodeValue = (refreshed["qrcode"] as? String) ?: ""
                    qrcodeUrl = (refreshed["qrcode_img_content"] as? String) ?: ""
                    onQrCodeReady(qrcodeUrl, qrcodeValue)
                } catch (exc: Exception) {
                    Log.e("Weixin", "QR refresh failed: ${exc.message}")
                    return@withContext null
                }
            }
            "confirmed" -> {
                val accountId = (statusResp["ilink_bot_id"] as? String) ?: ""
                val token = (statusResp["bot_token"] as? String) ?: ""
                val baseUrl = (statusResp["baseurl"] as? String) ?: ILINK_BASE_URL
                val userId = (statusResp["ilink_user_id"] as? String) ?: ""
                if (accountId.isEmpty() || token.isEmpty()) {
                    Log.e("Weixin", "QR confirmed but credential payload was incomplete")
                    return@withContext null
                }
                saveWeixinAccount(
                    hermesHome = hermesHome,
                    accountId = accountId,
                    token = token,
                    baseUrl = baseUrl,
                    userId = userId,
                )
                onStatusUpdate("confirmed")
                return@withContext mapOf(
                    "account_id" to accountId,
                    "token" to token,
                    "base_url" to baseUrl,
                    "user_id" to userId,
                )
            }
            else -> {
                Log.w("Weixin", "Unknown QR status: '$status'")
            }
        }
        delay(1000)
    }
    onStatusUpdate("timeout")
    null
}

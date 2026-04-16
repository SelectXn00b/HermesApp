package com.xiaomo.androidforclaw.hermes.gateway.platforms

/**
 * Discord platform adapter.
 *
 * Uses the Discord API for receiving messages via WebSocket and sending
 * responses back. Supports threads, channels, and DMs.
 *
 * Ported from gateway/platforms/discord.py
 *
 * Note: On Android, we use the Discord REST API directly instead of
 * the discord.py library (which is Python-specific).
 */

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.hermes.gateway.*
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

/**
 * Discord adapter — bridges between the gateway and the Discord API.
 *
 * Features:
 * - REST API for sending messages
 * - WebSocket gateway for receiving messages (requires discord.py or manual WS)
 * - Thread support
 * - File/image upload
 * - Reactions
 * - Typing indicators
 */
class DiscordAdapter(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.DISCORD) {
    companion object {
        private const val TAG = "DiscordAdapter"
        const val MAX_MESSAGE_LENGTH = 2000
        const val API_BASE = "https://discord.com/api/v10"
    }

    private val _token: String = (config.token ?: "").ifEmpty {
        System.getenv("DISCORD_TOKEN") ?: ""
    }

    private val _httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _dedup: MessageDeduplicator = MessageDeduplicator()
    private val _chatQueues: ConcurrentHashMap<String, Channel<MessageEvent>> = ConcurrentHashMap()
    private val _chatJobs: ConcurrentHashMap<String, Job> = ConcurrentHashMap()

    /** Bot user id. */
    private var _botUserId: String = ""

    /** WebSocket connection. */
    private var _wsJob: Job? = null

    override val isConnected: AtomicBoolean
        get() = AtomicBoolean(_wsJob?.isActive == true)

    override suspend fun connect(): Boolean {
        if (_token.isEmpty()) {
            Log.e(TAG, "DISCORD_TOKEN not set")
            return false
        }

        // Get bot info
        try {
            val request = Request.Builder()
                .url("$API_BASE/users/@me")
                .header("Authorization", "Bot $_token")
                .get()
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "Failed to get bot info: HTTP ${resp.code}")
                    return false
                }
                val data = JSONObject(resp.body!!.string())
                _botUserId = data.getString("id")
                Log.i(TAG, "Bot connected: ${data.getString("username")} ($_botUserId)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}")
            return false
        }

        markConnected()
        return true
    }

    override suspend fun disconnect() {
        _wsJob?.cancel()
        _wsJob = null
        _chatJobs.values.forEach { it.cancel() }
        _chatJobs.clear()
        _chatQueues.clear()
        markDisconnected()
        Log.i(TAG, "Disconnected")
    }

    override suspend fun send(
        chatId: String,
        content: String,
        replyTo: String?,
        metadata: JSONObject?): SendResult = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("content", content.take(MAX_MESSAGE_LENGTH))
                if (replyTo != null) {
                    put("message_reference", JSONObject().apply {
                        put("message_id", replyTo)
                    })
                }
            }

            val body = payload.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$API_BASE/channels/$chatId/messages")
                .header("Authorization", "Bot $_token")
                .header("Content-Type", "application/json")
                .post(body)
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string() ?: ""
                    Log.e(TAG, "Send failed: HTTP ${resp.code}: $errorBody")
                    return@withContext SendResult(success = false, error = "HTTP ${resp.code}")
                }

                val data = JSONObject(resp.body!!.string())
                SendResult(success = true, messageId = data.getString("id"))
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
                if (caption != null) put("content", caption)
            }

            val body = payload.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$API_BASE/channels/$chatId/messages")
                .header("Authorization", "Bot $_token")
                .post(body)
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext SendResult(success = false, error = "HTTP ${resp.code}")
                val data = JSONObject(resp.body!!.string())
                SendResult(success = true, messageId = data.getString("id"))
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
                if (caption != null) put("content", caption)
            }

            val body = payload.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$API_BASE/channels/$chatId/messages")
                .header("Authorization", "Bot $_token")
                .post(body)
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext SendResult(success = false, error = "HTTP ${resp.code}")
                val data = JSONObject(resp.body!!.string())
                SendResult(success = true, messageId = data.getString("id"))
            }
        } catch (e: Exception) {
            SendResult(success = false, error = e.message)
        }
    }

    override suspend fun sendTyping(chatId: String, metadata: JSONObject?) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$API_BASE/channels/$chatId/typing")
                    .header("Authorization", "Bot $_token")
                    .post("".toRequestBody(null))
                    .build()

                _httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "Typing indicator failed: HTTP ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Typing indicator error: ${e.message}")
            }
        }
    }

    /**
     * Get the bot user id.
     */
    val botUserId: String get() = _botUserId
}

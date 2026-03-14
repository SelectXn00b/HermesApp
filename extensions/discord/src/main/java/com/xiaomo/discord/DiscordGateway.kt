/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/discord/(all)
 *
 * AndroidForClaw adaptation: Discord channel runtime.
 */
package com.xiaomo.discord

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * Discord Gateway (WebSocket) 连接处理器
 * 基于 Discord Gateway API v10
 * https://discord.com/developers/docs/topics/gateway
 */
class DiscordGateway(
    private val token: String,
    private val intents: Int = DEFAULT_INTENTS,
    private val eventFlow: MutableSharedFlow<DiscordEvent>
) {
    companion object {
        private const val TAG = "DiscordGateway"
        private const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"

        // Gateway Intents
        const val INTENT_GUILDS = 1 shl 0
        const val INTENT_GUILD_MESSAGES = 1 shl 9
        const val INTENT_GUILD_MESSAGE_REACTIONS = 1 shl 10
        const val INTENT_DIRECT_MESSAGES = 1 shl 12
        const val INTENT_DIRECT_MESSAGE_REACTIONS = 1 shl 13
        const val INTENT_MESSAGE_CONTENT = 1 shl 15  // Privileged

        const val DEFAULT_INTENTS = INTENT_GUILDS or
                                   INTENT_GUILD_MESSAGES or
                                   INTENT_GUILD_MESSAGE_REACTIONS or
                                   INTENT_DIRECT_MESSAGES or
                                   INTENT_DIRECT_MESSAGE_REACTIONS or
                                   INTENT_MESSAGE_CONTENT

        // Opcodes
        private const val OP_DISPATCH = 0
        private const val OP_HEARTBEAT = 1
        private const val OP_IDENTIFY = 2
        private const val OP_HELLO = 10
        private const val OP_HEARTBEAT_ACK = 11
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No timeout for WebSocket
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var sequenceNumber: Int? = null
    private var sessionId: String? = null
    private var heartbeatInterval: Long = 0
    private var isConnected = false

    fun start() {
        scope.launch {
            try {
                Log.i(TAG, "🚀 启动 Discord Gateway 连接...")
                Log.i(TAG, "   Intents: $intents")

                connect()
            } catch (e: Exception) {
                Log.e(TAG, "❌ 启动 Gateway 失败", e)
                eventFlow.emit(DiscordEvent.Error(e))
            }
        }
    }

    fun stop() {
        try {
            isConnected = false
            heartbeatJob?.cancel()
            webSocket?.close(1000, "Normal closure")
            webSocket = null
            Log.i(TAG, "Gateway 已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止 Gateway 时出错", e)
        }
    }

    private fun connect() {
        val request = Request.Builder()
            .url(GATEWAY_URL)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "✅ WebSocket 已连接")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    handleMessage(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket 正在关闭: $code - $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket 已关闭: $code - $reason")
                isConnected = false
                heartbeatJob?.cancel()

                // 尝试重连 (除非是正常关闭)
                if (code != 1000 && isConnected) {
                    scope.launch {
                        delay(5000)
                        Log.i(TAG, "尝试重连...")
                        connect()
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket 连接失败: ${response?.code}", t)
                scope.launch {
                    eventFlow.emit(DiscordEvent.Error(t))
                }

                // 尝试重连
                if (isConnected) {
                    scope.launch {
                        delay(5000)
                        Log.i(TAG, "尝试重连...")
                        connect()
                    }
                }
            }
        })
    }

    private suspend fun handleMessage(text: String) {
        try {
            val payload = gson.fromJson(text, JsonObject::class.java)
            val op = payload.get("op")?.asInt ?: return
            val data = payload.get("d")?.takeIf { !it.isJsonNull }?.asJsonObject
            val eventName = payload.get("t")?.takeIf { !it.isJsonNull }?.asString
            val seq = payload.get("s")?.takeIf { !it.isJsonNull }?.asInt

            // 更新序列号
            seq?.let { sequenceNumber = it }

            when (op) {
                OP_HELLO -> handleHello(data)
                OP_DISPATCH -> handleDispatch(eventName, data)
                OP_HEARTBEAT_ACK -> Log.d(TAG, "💓 Heartbeat ACK")
                else -> Log.d(TAG, "未知 opcode: $op")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理消息失败", e)
        }
    }

    private suspend fun handleHello(data: JsonObject?) {
        try {
            heartbeatInterval = data?.get("heartbeat_interval")?.asLong ?: 41250
            Log.i(TAG, "👋 收到 HELLO，心跳间隔: ${heartbeatInterval}ms")

            // 启动心跳
            startHeartbeat()

            // 发送 IDENTIFY
            sendIdentify()
        } catch (e: Exception) {
            Log.e(TAG, "处理 HELLO 失败", e)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(heartbeatInterval)
                sendHeartbeat()
            }
        }
    }

    private fun sendHeartbeat() {
        try {
            val payload = JsonObject().apply {
                addProperty("op", OP_HEARTBEAT)
                if (sequenceNumber != null) {
                    addProperty("d", sequenceNumber)
                } else {
                    add("d", null)
                }
            }

            webSocket?.send(gson.toJson(payload))
            Log.d(TAG, "💓 发送心跳")
        } catch (e: Exception) {
            Log.e(TAG, "发送心跳失败", e)
        }
    }

    private fun sendIdentify() {
        try {
            val payload = JsonObject().apply {
                addProperty("op", OP_IDENTIFY)
                add("d", JsonObject().apply {
                    addProperty("token", token)
                    addProperty("intents", intents)
                    add("properties", JsonObject().apply {
                        addProperty("os", "android")
                        addProperty("browser", "AndroidForClaw")
                        addProperty("device", "AndroidForClaw")
                    })
                })
            }

            webSocket?.send(gson.toJson(payload))
            Log.i(TAG, "🔐 发送 IDENTIFY")
        } catch (e: Exception) {
            Log.e(TAG, "发送 IDENTIFY 失败", e)
        }
    }

    private suspend fun handleDispatch(eventName: String?, data: JsonObject?) {
        if (eventName == null || data == null) return

        try {
            when (eventName) {
                "READY" -> handleReady(data)
                "MESSAGE_CREATE" -> handleMessageCreate(data)
                "MESSAGE_REACTION_ADD" -> handleReactionAdd(data)
                "MESSAGE_REACTION_REMOVE" -> handleReactionRemove(data)
                "TYPING_START" -> handleTypingStart(data)
                else -> Log.d(TAG, "未处理的事件: $eventName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理事件 $eventName 失败", e)
        }
    }

    private suspend fun handleReady(data: JsonObject) {
        try {
            sessionId = data.get("session_id")?.asString
            val user = data.get("user")?.asJsonObject
            val username = user?.get("username")?.asString

            Log.i(TAG, "✅ READY - 已登录为: $username")
            Log.i(TAG, "   Session ID: $sessionId")

            isConnected = true
            eventFlow.emit(DiscordEvent.Connected)
        } catch (e: Exception) {
            Log.e(TAG, "处理 READY 失败", e)
        }
    }

    private suspend fun handleMessageCreate(data: JsonObject) {
        try {
            val messageId = data.get("id")?.asString ?: return
            val channelId = data.get("channel_id")?.asString ?: return
            val guildId = data.get("guild_id")?.asString
            val author = data.get("author")?.asJsonObject ?: return
            val authorId = author.get("id")?.asString ?: return
            val authorName = author.get("username")?.asString ?: "Unknown"
            val content = data.get("content")?.asString ?: ""
            val timestamp = data.get("timestamp")?.asString ?: ""

            // 忽略 Bot 自己的消息
            val isBot = author.get("bot")?.asBoolean ?: false
            if (isBot) return

            // 解析 mentions
            val mentionsArray = data.getAsJsonArray("mentions")
            val mentions = mutableListOf<String>()
            mentionsArray?.forEach { mention ->
                val mentionId = mention.asJsonObject.get("id")?.asString
                mentionId?.let { mentions.add(it) }
            }

            Log.d(TAG, "📨 收到消息: $messageId from $authorName")
            Log.d(TAG, "   内容: $content")

            eventFlow.emit(
                DiscordEvent.Message(
                    messageId = messageId,
                    channelId = channelId,
                    guildId = guildId,
                    authorId = authorId,
                    authorName = authorName,
                    content = content,
                    mentions = mentions,
                    timestamp = timestamp
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "处理 MESSAGE_CREATE 失败", e)
        }
    }

    private suspend fun handleReactionAdd(data: JsonObject) {
        try {
            val userId = data.get("user_id")?.asString ?: return
            val channelId = data.get("channel_id")?.asString ?: return
            val messageId = data.get("message_id")?.asString ?: return
            val emoji = data.get("emoji")?.asJsonObject
            val emojiName = emoji?.get("name")?.asString ?: return

            Log.d(TAG, "👍 反应添加: $emojiName by $userId")

            eventFlow.emit(
                DiscordEvent.ReactionAdd(
                    userId = userId,
                    channelId = channelId,
                    messageId = messageId,
                    emoji = emojiName
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "处理 MESSAGE_REACTION_ADD 失败", e)
        }
    }

    private suspend fun handleReactionRemove(data: JsonObject) {
        try {
            val userId = data.get("user_id")?.asString ?: return
            val channelId = data.get("channel_id")?.asString ?: return
            val messageId = data.get("message_id")?.asString ?: return
            val emoji = data.get("emoji")?.asJsonObject
            val emojiName = emoji?.get("name")?.asString ?: return

            Log.d(TAG, "👎 反应移除: $emojiName by $userId")

            eventFlow.emit(
                DiscordEvent.ReactionRemove(
                    userId = userId,
                    channelId = channelId,
                    messageId = messageId,
                    emoji = emojiName
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "处理 MESSAGE_REACTION_REMOVE 失败", e)
        }
    }

    private suspend fun handleTypingStart(data: JsonObject) {
        try {
            val userId = data.get("user_id")?.asString ?: return
            val channelId = data.get("channel_id")?.asString ?: return
            val timestamp = data.get("timestamp")?.asLong ?: return

            eventFlow.emit(
                DiscordEvent.TypingStart(
                    userId = userId,
                    channelId = channelId,
                    timestamp = timestamp
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "处理 TYPING_START 失败", e)
        }
    }
}

/**
 * Discord 事件
 */
sealed class DiscordEvent {
    object Connected : DiscordEvent()
    data class Error(val exception: Throwable) : DiscordEvent()

    data class Message(
        val messageId: String,
        val channelId: String,
        val guildId: String?,
        val authorId: String,
        val authorName: String,
        val content: String,
        val mentions: List<String>,
        val timestamp: String
    ) : DiscordEvent()

    data class ReactionAdd(
        val userId: String,
        val channelId: String,
        val messageId: String,
        val emoji: String
    ) : DiscordEvent()

    data class ReactionRemove(
        val userId: String,
        val channelId: String,
        val messageId: String,
        val emoji: String
    ) : DiscordEvent()

    data class TypingStart(
        val userId: String,
        val channelId: String,
        val timestamp: Long
    ) : DiscordEvent()
}

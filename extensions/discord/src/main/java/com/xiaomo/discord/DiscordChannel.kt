/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/discord/(all)
 *
 * AndroidForClaw adaptation: Discord channel runtime.
 */
package com.xiaomo.discord

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Discord Channel 核心类
 * 参考:
 * - OpenClaw discord/src/channel.ts
 * - Feishu FeishuChannel.kt
 *
 * 功能：
 * - Gateway (WebSocket) 连接管理
 * - 消息接收和发送
 * - 事件分发
 * - 会话管理
 * - 多账户支持
 */
class DiscordChannel private constructor(
    private val context: Context,
    private val config: DiscordConfig
) {
    companion object {
        private const val TAG = "DiscordChannel"
        private var instance: DiscordChannel? = null

        /**
         * 启动 Discord Channel
         */
        fun start(context: Context, config: DiscordConfig): Result<DiscordChannel> {
            return try {
                if (instance != null) {
                    Log.w(TAG, "Discord Channel already started")
                    return Result.success(instance!!)
                }

                val channel = DiscordChannel(context, config)
                instance = channel

                // 启动 Channel
                channel.scope.launch {
                    channel.startInternal()
                }

                Result.success(channel)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Discord Channel", e)
                Result.failure(e)
            }
        }

        /**
         * 停止 Discord Channel
         */
        fun stop() {
            instance?.stopInternal()
            instance = null
        }

        /**
         * 获取当前实例
         */
        fun getInstance(): DiscordChannel? = instance
    }

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // REST API 客户端
    private lateinit var client: DiscordClient

    // Gateway (WebSocket) 连接
    private var gateway: DiscordGateway? = null

    // 事件流
    private val _eventFlow = MutableSharedFlow<ChannelEvent>(replay = 0, extraBufferCapacity = 100)
    val eventFlow: SharedFlow<ChannelEvent> = _eventFlow.asSharedFlow()

    // 连接状态
    private var isConnected = false
    private var currentBotUserId: String? = null
    private var currentBotUsername: String? = null

    /**
     * 内部启动逻辑
     */
    private suspend fun startInternal() {
        try {
            // 验证配置
            val token = config.token?.trim()
            if (token.isNullOrBlank()) {
                throw IllegalArgumentException("Discord token is required")
            }

            Log.i(TAG, "🚀 Starting Discord Channel...")
            Log.i(TAG, "   Name: ${config.name ?: "default"}")
            Log.i(TAG, "   DM Policy: ${config.dm?.policy ?: "pairing"}")
            Log.i(TAG, "   Group Policy: ${config.groupPolicy ?: "open"}")
            Log.i(TAG, "   Reply Mode: ${config.replyToMode ?: "off"}")

            // 初始化 REST API 客户端
            client = DiscordClient(token)

            // 获取当前 Bot 信息
            val botInfoResult = client.getCurrentUser()
            if (botInfoResult.isSuccess) {
                val botInfo = botInfoResult.getOrNull()
                currentBotUserId = botInfo?.get("id")?.asString
                currentBotUsername = botInfo?.get("username")?.asString
                Log.i(TAG, "   Bot: $currentBotUsername ($currentBotUserId)")
            } else {
                Log.w(TAG, "   Failed to get bot info: ${botInfoResult.exceptionOrNull()?.message}")
            }

            // 计算 Intents
            val intents = calculateIntents()
            Log.i(TAG, "   Intents: $intents")

            // 启动 Gateway 连接
            val eventFlow = MutableSharedFlow<DiscordEvent>(replay = 0, extraBufferCapacity = 100)
            gateway = DiscordGateway(token, intents, eventFlow)
            gateway?.start()

            // 监听 Gateway 事件
            scope.launch {
                eventFlow.collect { event ->
                    handleGatewayEvent(event)
                }
            }

            Log.i(TAG, "✅ Discord Channel started successfully")
            isConnected = true
            _eventFlow.emit(ChannelEvent.Connected)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start Discord Channel", e)
            _eventFlow.emit(ChannelEvent.Error(e))
            throw e
        }
    }

    /**
     * 内部停止逻辑
     */
    private fun stopInternal() {
        Log.i(TAG, "Stopping Discord Channel...")
        try {
            gateway?.stop()
            gateway = null
            isConnected = false
            scope.cancel()
            Log.i(TAG, "Discord Channel stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Discord Channel", e)
        }
    }

    /**
     * 计算 Gateway Intents
     */
    private fun calculateIntents(): Int {
        return DiscordGateway.DEFAULT_INTENTS
    }

    /**
     * 处理 Gateway 事件
     */
    private suspend fun handleGatewayEvent(event: DiscordEvent) {
        try {
            when (event) {
                is DiscordEvent.Connected -> {
                    Log.i(TAG, "✅ Gateway connected")
                    isConnected = true
                    _eventFlow.emit(ChannelEvent.Connected)
                }

                is DiscordEvent.Message -> {
                    handleMessage(event)
                }

                is DiscordEvent.ReactionAdd -> {
                    handleReactionAdd(event)
                }

                is DiscordEvent.ReactionRemove -> {
                    handleReactionRemove(event)
                }

                is DiscordEvent.TypingStart -> {
                    handleTypingStart(event)
                }

                is DiscordEvent.Error -> {
                    Log.e(TAG, "Gateway error", event.exception)
                    _eventFlow.emit(ChannelEvent.Error(event.exception))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling gateway event", e)
        }
    }

    /**
     * 处理消息事件
     */
    private suspend fun handleMessage(event: DiscordEvent.Message) {
        try {
            // 忽略 Bot 自己的消息
            if (event.authorId == currentBotUserId) {
                Log.d(TAG, "Ignoring self message: ${event.messageId}")
                return
            }

            // 判断消息类型 (DM or Guild)
            val chatType = if (event.guildId == null) "direct" else "channel"

            // DM 权限检查
            if (chatType == "direct") {
                val dmPolicy = config.dm?.policy ?: "pairing"
                val allowFrom = config.dm?.allowFrom ?: emptyList()

                when (dmPolicy) {
                    "open" -> {
                        // 允许所有 DM
                    }
                    "pairing" -> {
                        // 需要配对
                        if (event.authorId !in allowFrom) {
                            Log.d(TAG, "DM from ${event.authorId} not in allowlist (pairing mode)")
                            sendPairingMessage(event.channelId)
                            return
                        }
                    }
                    "allowlist" -> {
                        // 仅允许白名单
                        if (event.authorId !in allowFrom) {
                            Log.d(TAG, "DM from ${event.authorId} not in allowlist")
                            return
                        }
                    }
                    "denylist" -> {
                        // 拒绝黑名单
                        if (event.authorId in allowFrom) {
                            Log.d(TAG, "DM from ${event.authorId} in denylist")
                            return
                        }
                    }
                }
            }

            // Guild 权限检查
            if (chatType == "channel" && event.guildId != null) {
                val guildConfig = config.guilds?.get(event.guildId)
                val groupPolicy = config.groupPolicy ?: "open"

                // 检查是否在白名单中
                val allowedChannels = guildConfig?.channels
                if (allowedChannels != null && event.channelId !in allowedChannels) {
                    Log.d(TAG, "Channel ${event.channelId} not in allowlist")
                    return
                }

                // 检查是否需要 @提及
                val requireMention = guildConfig?.requireMention ?: true
                if (requireMention) {
                    val botMentioned = currentBotUserId in event.mentions
                    if (!botMentioned) {
                        Log.d(TAG, "Bot not mentioned in channel message")
                        return
                    }
                }
            }

            Log.d(TAG, "📨 Received message: ${event.messageId} from ${event.authorName}")
            Log.d(TAG, "   Content: ${event.content}")
            Log.d(TAG, "   Type: $chatType")

            // 发出消息事件
            _eventFlow.emit(
                ChannelEvent.Message(
                    messageId = event.messageId,
                    channelId = event.channelId,
                    guildId = event.guildId,
                    authorId = event.authorId,
                    authorName = event.authorName,
                    content = event.content,
                    chatType = chatType,
                    mentions = event.mentions,
                    timestamp = event.timestamp
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message", e)
        }
    }

    /**
     * 发送配对消息
     */
    private suspend fun sendPairingMessage(channelId: String) {
        try {
            val message = """
                👋 Hello! I'm an AI assistant.

                To use me, please ask the admin to approve pairing by adding your user ID to the allowlist.
            """.trimIndent()

            client.sendMessage(channelId, message)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending pairing message", e)
        }
    }

    /**
     * 处理表情添加事件
     */
    private suspend fun handleReactionAdd(event: DiscordEvent.ReactionAdd) {
        try {
            Log.d(TAG, "👍 Reaction added: ${event.emoji}")
            _eventFlow.emit(
                ChannelEvent.ReactionAdd(
                    userId = event.userId,
                    channelId = event.channelId,
                    messageId = event.messageId,
                    emoji = event.emoji
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling reaction add", e)
        }
    }

    /**
     * 处理表情移除事件
     */
    private suspend fun handleReactionRemove(event: DiscordEvent.ReactionRemove) {
        try {
            Log.d(TAG, "👎 Reaction removed: ${event.emoji}")
            _eventFlow.emit(
                ChannelEvent.ReactionRemove(
                    userId = event.userId,
                    channelId = event.channelId,
                    messageId = event.messageId,
                    emoji = event.emoji
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling reaction remove", e)
        }
    }

    /**
     * 处理输入状态事件
     */
    private suspend fun handleTypingStart(event: DiscordEvent.TypingStart) {
        try {
            Log.d(TAG, "⌨️ User typing: ${event.userId}")
            _eventFlow.emit(
                ChannelEvent.TypingStart(
                    userId = event.userId,
                    channelId = event.channelId,
                    timestamp = event.timestamp
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling typing start", e)
        }
    }

    // ==================== Public API ====================

    /**
     * 发送消息
     */
    suspend fun sendMessage(
        channelId: String,
        content: String,
        embeds: List<Map<String, Any>>? = null,
        components: List<Map<String, Any>>? = null,
        replyToId: String? = null
    ): Result<String> {
        return try {
            val messageReference = replyToId?.let {
                mapOf("message_id" to it)
            }

            val result = client.sendMessage(channelId, content, embeds, components, messageReference)
            if (result.isSuccess) {
                val response = result.getOrNull()
                val messageId = response?.get("id")?.asString
                    ?: return Result.failure(Exception("Missing message_id in response"))
                Result.success(messageId)
            } else {
                result.map { "" }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            Result.failure(e)
        }
    }

    /**
     * 发送 DM (私聊消息)
     */
    suspend fun sendDirectMessage(userId: String, content: String): Result<String> {
        return try {
            val result = client.sendDirectMessage(userId, content)
            if (result.isSuccess) {
                val response = result.getOrNull()
                val messageId = response?.get("id")?.asString
                    ?: return Result.failure(Exception("Missing message_id in response"))
                Result.success(messageId)
            } else {
                result.map { "" }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending DM", e)
            Result.failure(e)
        }
    }

    /**
     * 添加反应 (表情)
     */
    suspend fun addReaction(channelId: String, messageId: String, emoji: String): Result<Unit> {
        return client.addReaction(channelId, messageId, emoji)
    }

    /**
     * 移除反应
     */
    suspend fun removeReaction(channelId: String, messageId: String, emoji: String): Result<Unit> {
        return client.removeReaction(channelId, messageId, emoji)
    }

    /**
     * 触发输入状态指示器
     */
    suspend fun triggerTyping(channelId: String): Result<Unit> {
        return client.triggerTyping(channelId)
    }

    /**
     * 获取 Guild 信息
     */
    suspend fun getGuild(guildId: String): Result<JsonObject> {
        return client.getGuild(guildId)
    }

    /**
     * 获取 Channel 信息
     */
    suspend fun getChannel(channelId: String): Result<JsonObject> {
        return client.getChannel(channelId)
    }

    /**
     * 是否已连接
     */
    fun isConnected(): Boolean = isConnected

    /**
     * 获取当前 Bot 用户 ID
     */
    fun getBotUserId(): String? = currentBotUserId

    /**
     * 获取当前 Bot 用户名
     */
    fun getBotUsername(): String? = currentBotUsername
}

/**
 * Discord Channel 事件
 */
sealed class ChannelEvent {
    object Connected : ChannelEvent()

    data class Error(val error: Throwable) : ChannelEvent()

    data class Message(
        val messageId: String,
        val channelId: String,
        val guildId: String?,
        val authorId: String,
        val authorName: String,
        val content: String,
        val chatType: String, // "direct" or "channel"
        val mentions: List<String>,
        val timestamp: String
    ) : ChannelEvent()

    data class ReactionAdd(
        val userId: String,
        val channelId: String,
        val messageId: String,
        val emoji: String
    ) : ChannelEvent()

    data class ReactionRemove(
        val userId: String,
        val channelId: String,
        val messageId: String,
        val emoji: String
    ) : ChannelEvent()

    data class TypingStart(
        val userId: String,
        val channelId: String,
        val timestamp: Long
    ) : ChannelEvent()
}

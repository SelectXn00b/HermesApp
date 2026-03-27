package com.xiaomo.feishu

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel runtime.
 */


import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * 飞书 Channel 核心类
 * 对齐 OpenClaw channel.ts
 *
 * 功能：
 * - WebSocket/Webhook 连接管理
 * - 消息接收和发送
 * - 事件分发
 * - 会话管理
 */
class FeishuChannel(private val config: FeishuConfig) {
    companion object {
        private const val TAG = "FeishuChannel"
    }

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client = FeishuClient(config)

    /**
     * Feishu Tool Registry — all feishu extension tools (doc, wiki, drive, bitable, etc.)
     * Aligned with OpenClaw: extension tools auto-register when channel starts
     */
    private val feishuToolRegistry by lazy {
        com.xiaomo.feishu.tools.FeishuToolRegistry(config, client)
    }

    /**
     * Get FeishuToolRegistry for bridging into main ToolRegistry
     */
    fun getToolRegistry(): com.xiaomo.feishu.tools.FeishuToolRegistry = feishuToolRegistry

    /**
     * Get FeishuClient for direct API access (media download, streaming card, etc.)
     */
    fun getClient(): FeishuClient = client

    /**
     * Create a new streaming card session (Card Kit API)
     * 对齐 OpenClaw FeishuStreamingSession
     */
    fun createStreamingCard(): com.xiaomo.feishu.messaging.FeishuStreamingCard =
        com.xiaomo.feishu.messaging.FeishuStreamingCard(client)

    /**
     * FeishuSender - 支持 Markdown 卡片渲染
     */
    val sender by lazy { com.xiaomo.feishu.messaging.FeishuSender(config, client) }

    // 事件流
    private val _eventFlow = MutableSharedFlow<FeishuEvent>(replay = 0, extraBufferCapacity = 100)
    val eventFlow: SharedFlow<FeishuEvent> = _eventFlow.asSharedFlow()

    // 连接状态
    private var isConnected = false
    private var connectionHandler: FeishuConnectionHandler? = null

    // 机器人自己的 open_id (用于检测 @mention)
    private var botOpenId: String? = null

    // 当前对话上下文 (用于 Agent 工具调用)
    private var currentChatContext: ChatContext? = null

    /**
     * 当前对话上下文
     */
    data class ChatContext(
        val receiveId: String,
        val receiveIdType: String = "chat_id",
        val messageId: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * 获取机器人的 open_id
     */
    fun getBotOpenId(): String? = botOpenId

    /**
     * 设置机器人的 open_id
     */
    fun setBotOpenId(openId: String) {
        botOpenId = openId
        Log.d(TAG, "Bot open_id set: $openId")
    }

    /**
     * Background retry for bot identity (aligned with OpenClaw monitor.account.ts).
     * When bot info probe fails at startup, retry with escalating delays
     * so the degraded state (not knowing bot open_id) is bounded rather than permanent.
     */
    private fun startBotIdentityRetry() {
        val retryDelaysMs = listOf(60_000L, 120_000L, 300_000L, 600_000L, 900_000L)
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
        )
        scope.launch {
            for ((i, delayMs) in retryDelaysMs.withIndex()) {
                if (botOpenId != null || !isConnected) return@launch
                delay(delayMs)
                if (botOpenId != null || !isConnected) return@launch
                try {
                    val botInfoResult = client.getBotInfo()
                    if (botInfoResult.isSuccess) {
                        val botInfo = botInfoResult.getOrNull()
                        val openId = botInfo?.openId
                        if (openId != null) {
                            botOpenId = openId
                            Log.i(TAG, "✅ Bot open_id recovered via background retry: $openId")
                            return@launch
                        }
                    }
                    val nextDelay = retryDelaysMs.getOrNull(i + 1)
                    Log.w(TAG, "Bot identity retry ${i + 1}/${retryDelaysMs.size} failed" +
                        if (nextDelay != null) "; next attempt in ${nextDelay / 1000}s" else "")
                } catch (e: Exception) {
                    Log.w(TAG, "Bot identity retry ${i + 1} error: ${e.message}")
                }
            }
            Log.e(TAG, "Bot identity retry exhausted; requireMention group messages may be skipped until restart")
        }
    }

    /**
     * 更新当前对话上下文 (从消息事件中更新)
     * 应在收到消息时调用,记录当前对话信息供 Agent 工具使用
     */
    fun updateCurrentChatContext(receiveId: String, receiveIdType: String = "chat_id", messageId: String? = null) {
        currentChatContext = ChatContext(
            receiveId = receiveId,
            receiveIdType = receiveIdType,
            messageId = messageId,
            timestamp = System.currentTimeMillis()
        )
        Log.d(TAG, "Current chat context updated: receiveId=$receiveId, type=$receiveIdType")
    }

    /**
     * 获取当前对话上下文
     */
    fun getCurrentChatContext(): ChatContext? = currentChatContext

    /**
     * 发送图片到当前对话
     * 供 Agent 工具调用 (FeishuSendImageSkill)
     */
    suspend fun sendImageToCurrentChat(imageFile: java.io.File): Result<String> {
        val context = currentChatContext
        if (context == null) {
            Log.e(TAG, "No active chat context")
            return Result.failure(Exception("No active chat context. Cannot determine recipient."))
        }

        // 检查上下文是否过期 (超过 5 分钟)
        val ageMs = System.currentTimeMillis() - context.timestamp
        if (ageMs > 5 * 60 * 1000) {
            Log.w(TAG, "Chat context is stale (${ageMs}ms old)")
            return Result.failure(Exception("Chat context is stale. Please send a message first."))
        }

        return try {
            // 使用新的 FeishuImageUploadTool 上传图片
            val uploadTool = com.xiaomo.feishu.tools.media.FeishuImageUploadTool(config, client)

            // 1. 上传图片
            Log.d(TAG, "Uploading image: ${imageFile.name} (${imageFile.length()} bytes)")
            val toolResult = uploadTool.execute(mapOf("image_path" to imageFile.absolutePath))

            if (!toolResult.success) {
                Log.e(TAG, "Failed to upload image: ${toolResult.error}")
                return Result.failure(Exception(toolResult.error ?: "Upload failed"))
            }

            val imageKey = toolResult.data as? String
                ?: return Result.failure(Exception("Upload succeeded but no image_key"))

            Log.d(TAG, "Image uploaded successfully. image_key: $imageKey")

            // 2. 发送图片消息 (仍使用 FeishuMedia)
            val media = com.xiaomo.feishu.messaging.FeishuMedia(config, client)
            Log.d(TAG, "Sending image to ${context.receiveId} (type: ${context.receiveIdType})")
            val sendResult = media.sendImage(
                receiveId = context.receiveId,
                imageKey = imageKey,
                receiveIdType = context.receiveIdType
            )

            if (sendResult.isFailure) {
                val error = sendResult.exceptionOrNull()
                Log.e(TAG, "Failed to send image", error)
                return Result.failure(error ?: Exception("Send failed"))
            }

            val messageId = sendResult.getOrNull()
                ?: return Result.failure(Exception("Send succeeded but no message_id"))

            Log.i(TAG, "✅ Image sent successfully. message_id: $messageId")
            Result.success(messageId)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send image to current chat", e)
            Result.failure(e)
        }
    }

    /**
     * 启动 Channel
     */
    suspend fun start(): Result<Unit> {
        return try {
            // 验证配置
            config.validate().getOrThrow()

            Log.i(TAG, "Starting Feishu Channel...")
            Log.i(TAG, "  Mode: ${config.connectionMode}")
            Log.i(TAG, "  Domain: ${config.domain}")
            Log.i(TAG, "  DM Policy: ${config.dmPolicy}")
            Log.i(TAG, "  Group Policy: ${config.groupPolicy}")

            // 获取机器人信息 (对齐 OpenClaw)
            val botInfoResult = client.getBotInfo()
            if (botInfoResult.isSuccess) {
                val botInfo = botInfoResult.getOrNull()
                botOpenId = botInfo?.openId
                Log.i(TAG, "  Bot open_id: ${botOpenId ?: "unknown"}")
                Log.i(TAG, "  Bot name: ${botInfo?.name ?: "unknown"}")
            } else {
                Log.w(TAG, "Failed to get bot info: ${botInfoResult.exceptionOrNull()?.message}")
                Log.w(TAG, "Will continue without bot open_id (mention check may not work correctly)")
                // Background retry with escalating delays (aligned with OpenClaw monitor.account.ts)
                startBotIdentityRetry()
            }

            // 根据连接模式创建 handler
            connectionHandler = when (config.connectionMode) {
                FeishuConfig.ConnectionMode.WEBSOCKET -> {
                    FeishuWebSocketHandler(config, client, _eventFlow)
                }
                FeishuConfig.ConnectionMode.WEBHOOK -> {
                    FeishuWebhookHandler(config, client, _eventFlow)
                }
            }

            // 启动连接
            connectionHandler?.start()
            isConnected = true

            Log.i(TAG, "✅ Feishu Channel started successfully")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Feishu Channel", e)
            Result.failure(e)
        }
    }

    /**
     * 停止 Channel
     */
    fun stop() {
        Log.i(TAG, "Stopping Feishu Channel...")
        connectionHandler?.stop()
        connectionHandler = null
        isConnected = false
        Log.i(TAG, "Feishu Channel stopped")
    }

    /**
     * 添加消息反应（表情）
     */
    suspend fun addReaction(messageId: String, emojiType: String): Result<String> {
        return try {
            val body = mapOf(
                "reaction_type" to mapOf(
                    "emoji_type" to emojiType
                )
            )

            val result = client.post("/open-apis/im/v1/messages/$messageId/reactions", body)
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull()!!)
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val reactionId = data?.get("reaction_id")?.asString
                ?: return Result.failure(Exception("Missing reaction_id in response"))

            Log.d(TAG, "Reaction added: $reactionId")
            Result.success(reactionId)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to add reaction", e)
            Result.failure(e)
        }
    }

    /**
     * 移除消息反应
     */
    suspend fun removeReaction(messageId: String, reactionId: String): Result<Unit> {
        return try {
            val result = client.delete("/open-apis/im/v1/messages/$messageId/reactions/$reactionId")
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull()!!)
            }

            Log.d(TAG, "Reaction removed: $reactionId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove reaction", e)
            Result.failure(e)
        }
    }

    /**
     * 发送文本消息
     */
    suspend fun sendMessage(
        receiveId: String,
        receiveIdType: String = "open_id",
        content: String,
        msgType: String = "text"
    ): Result<String> {
        return try {
            // 构建消息内容的JSON字符串
            val contentJson = when (msgType) {
                "text" -> {
                    val textContent = mapOf("text" to content)
                    gson.toJson(textContent)
                }
                else -> content
            }

            val body = mapOf(
                "receive_id" to receiveId,
                "msg_type" to msgType,
                "content" to contentJson
            )

            val result = client.post("/open-apis/im/v1/messages?receive_id_type=$receiveIdType", body)
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull()!!)
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val messageId = data?.get("message_id")?.asString
                ?: return Result.failure(Exception("Missing message_id in response"))

            Log.d(TAG, "Message sent: $messageId")
            Result.success(messageId)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            Result.failure(e)
        }
    }

    /**
     * 发送卡片消息
     */
    suspend fun sendCard(
        receiveId: String,
        receiveIdType: String = "open_id",
        card: String
    ): Result<String> {
        return sendMessage(receiveId, receiveIdType, card, "interactive")
    }

    /**
     * 上传并发送图片文件
     * 对齐 OpenClaw sendMediaFeishu
     *
     * @param imageFile 图片文件
     * @param receiveId 接收者 ID
     * @param receiveIdType 接收者 ID 类型
     * @return 消息 ID
     */
    suspend fun uploadAndSendImage(
        imageFile: java.io.File,
        receiveId: String,
        receiveIdType: String = "open_id"
    ): Result<String> {
        return try {
            // 使用新的 FeishuImageUploadTool 上传图片
            val uploadTool = com.xiaomo.feishu.tools.media.FeishuImageUploadTool(config, client)

            // 1. 上传图片
            val toolResult = uploadTool.execute(mapOf("image_path" to imageFile.absolutePath))
            if (!toolResult.success) {
                return Result.failure(Exception(toolResult.error ?: "Upload failed"))
            }
            val imageKey = toolResult.data as? String
                ?: return Result.failure(Exception("Upload succeeded but no image_key"))

            // 2. 发送图片消息
            val media = com.xiaomo.feishu.messaging.FeishuMedia(config, client)
            media.sendImage(receiveId, imageKey, receiveIdType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload and send image", e)
            Result.failure(e)
        }
    }

    /**
     * 获取用户信息
     */
    suspend fun getUserInfo(userId: String): Result<FeishuUser> {
        return try {
            val result = client.get("/open-apis/contact/v3/users/$userId")
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull()!!)
            }

            val data = result.getOrNull()?.getAsJsonObject("data")?.getAsJsonObject("user")
                ?: return Result.failure(Exception("Missing user data"))

            val user = FeishuUser(
                userId = data.get("user_id")?.asString ?: userId,
                name = data.get("name")?.asString ?: "",
                enName = data.get("en_name")?.asString,
                email = data.get("email")?.asString,
                mobile = data.get("mobile")?.asString
            )

            Result.success(user)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user info", e)
            Result.failure(e)
        }
    }

    /**
     * 获取群组信息
     */
    suspend fun getChatInfo(chatId: String): Result<FeishuChat> {
        return try {
            val result = client.get("/open-apis/im/v1/chats/$chatId")
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull()!!)
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
                ?: return Result.failure(Exception("Missing chat data"))

            val chat = FeishuChat(
                chatId = data.get("chat_id")?.asString ?: chatId,
                name = data.get("name")?.asString ?: "",
                description = data.get("description")?.asString,
                ownerId = data.get("owner_id")?.asString
            )

            Result.success(chat)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get chat info", e)
            Result.failure(e)
        }
    }

    /**
     * 是否已连接
     */
    fun isConnected(): Boolean = isConnected
}

/**
 * 飞书事件基类
 */
sealed class FeishuEvent {
    data class Message(
        val messageId: String,
        val senderId: String,
        val chatId: String,
        val chatType: String, // "p2p" or "group"
        val content: String,
        val msgType: String,
        val mentions: List<String> = emptyList(),
        // Thread/reply awareness (from Lark SDK message object)
        val rootId: String? = null,
        val parentId: String? = null,
        val threadId: String? = null,
        // Media keys for image/file/audio/video/sticker messages
        val mediaKeys: com.xiaomo.feishu.messaging.MediaKeys? = null,
        // Original message timestamp from Feishu (millisecond epoch string).
        // Aligned with OpenClaw: use create_time instead of processing time.
        val createTime: Long = System.currentTimeMillis()
    ) : FeishuEvent()

    data class Error(val error: Throwable) : FeishuEvent()

    object Connected : FeishuEvent()
    object Disconnected : FeishuEvent()
}

/**
 * 飞书用户信息
 */
data class FeishuUser(
    val userId: String,
    val name: String,
    val enName: String? = null,
    val email: String? = null,
    val mobile: String? = null
)

/**
 * 飞书群组信息
 */
data class FeishuChat(
    val chatId: String,
    val name: String,
    val description: String? = null,
    val ownerId: String? = null
)

/**
 * 连接 Handler 接口
 */
interface FeishuConnectionHandler {
    fun start()
    fun stop()
}

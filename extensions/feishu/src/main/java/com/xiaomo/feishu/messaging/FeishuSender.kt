package com.xiaomo.feishu.messaging

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 飞书消息发送器
 * 对齐 OpenClaw src/send.ts
 */
class FeishuSender(
    private val config: FeishuConfig,
    private val client: FeishuClient
) {
    companion object {
        private const val TAG = "FeishuSender"
    }

    private val gson = Gson()
    private val media = FeishuMedia(config, client)

    /**
     * 发送文本消息
     *
     * 对齐 OpenClaw 逻辑：
     * - 自动检测代码块和 Markdown 表格
     * - 使用 interactive card (schema 2.0) 渲染格式化内容
     */
    suspend fun sendTextMessage(
        receiveId: String,
        text: String,
        receiveIdType: String = "open_id",
        mentionTargets: List<MentionTarget> = emptyList(),
        renderMode: RenderMode = RenderMode.AUTO
    ): Result<SendResult> = withContext(Dispatchers.IO) {
        try {
            // 分块发送（如果超过限制）
            val chunks = chunkText(text, config.textChunkLimit)
            if (chunks.size > 1) {
                return@withContext sendChunkedMessages(receiveId, chunks, receiveIdType)
            }

            // 判断是否使用卡片（对齐 OpenClaw）
            val useCard = when (renderMode) {
                RenderMode.CARD -> true
                RenderMode.TEXT -> false
                RenderMode.AUTO -> shouldUseCard(text)
            }

            if (useCard) {
                // 使用 Markdown 卡片发送（正确渲染代码块和表格）
                Log.d(TAG, "Using markdown card for formatted content")
                val card = buildMarkdownCard(text, mentionTargets)
                return@withContext sendCard(receiveId, card, receiveIdType)
            } else {
                // 使用普通文本消息
                val content = if (mentionTargets.isNotEmpty()) {
                    buildMentionedTextContent(text, mentionTargets)
                } else {
                    buildTextContent(text)
                }
                return@withContext sendMessageInternal(receiveId, "text", content, receiveIdType)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send text message", e)
            Result.failure(e)
        }
    }

    /**
     * 发送卡片消息
     */
    suspend fun sendCard(
        receiveId: String,
        card: String,
        receiveIdType: String = "open_id"
    ): Result<SendResult> = withContext(Dispatchers.IO) {
        try {
            sendMessageInternal(receiveId, "interactive", card, receiveIdType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send card message", e)
            Result.failure(e)
        }
    }

    /**
     * 更新卡片消息
     */
    suspend fun updateCard(
        messageId: String,
        card: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = mapOf("content" to card)
            val result = client.patch("/open-apis/im/v1/messages/$messageId", body)

            if (result.isFailure) {
                return@withContext Result.failure(result.exceptionOrNull()!!)
            }

            Log.d(TAG, "Card updated: $messageId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to update card", e)
            Result.failure(e)
        }
    }

    /**
     * 编辑消息
     */
    suspend fun editMessage(
        messageId: String,
        text: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val content = buildTextContent(text)
            val body = mapOf(
                "content" to content,
                "msg_type" to "text"
            )

            val result = client.put("/open-apis/im/v1/messages/$messageId", body)

            if (result.isFailure) {
                return@withContext Result.failure(result.exceptionOrNull()!!)
            }

            Log.d(TAG, "Message edited: $messageId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to edit message", e)
            Result.failure(e)
        }
    }

    /**
     * 删除消息
     */
    suspend fun deleteMessage(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val result = client.delete("/open-apis/im/v1/messages/$messageId")

            if (result.isFailure) {
                return@withContext Result.failure(result.exceptionOrNull()!!)
            }

            Log.d(TAG, "Message deleted: $messageId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete message", e)
            Result.failure(e)
        }
    }

    /**
     * 发送图片消息
     * 对齐 OpenClaw src/send.ts sendImage()
     */
    suspend fun sendImage(
        receiveId: String,
        imageKey: String,
        receiveIdType: String = "open_id"
    ): Result<SendResult> = withContext(Dispatchers.IO) {
        try {
            val content = gson.toJson(mapOf("image_key" to imageKey))
            sendMessageInternal(receiveId, "image", content, receiveIdType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send image", e)
            Result.failure(e)
        }
    }

    /**
     * 上传图片并发送
     * 对齐 OpenClaw 逻辑：upload file -> get image_key -> send image
     */
    suspend fun uploadAndSendImage(
        receiveId: String,
        filePath: String,
        receiveIdType: String = "open_id"
    ): Result<SendResult> = withContext(Dispatchers.IO) {
        try {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("File not found: $filePath"))
            }

            // 1. 上传图片获取 image_key (使用新的 FeishuImageUploadTool)
            val uploadTool = com.xiaomo.feishu.tools.media.FeishuImageUploadTool(config, client)
            val toolResult = uploadTool.execute(mapOf("image_path" to file.absolutePath))

            if (!toolResult.success) {
                return@withContext Result.failure(Exception(toolResult.error ?: "Upload failed"))
            }

            val imageKey = toolResult.data as? String
                ?: return@withContext Result.failure(Exception("Missing image_key"))

            Log.d(TAG, "Image uploaded: $imageKey")

            // 2. 发送图片消息
            sendImage(receiveId, imageKey, receiveIdType)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload and send image", e)
            Result.failure(e)
        }
    }

    /**
     * 回复消息
     */
    suspend fun replyMessage(
        messageId: String,
        text: String
    ): Result<SendResult> = withContext(Dispatchers.IO) {
        try {
            val content = buildTextContent(text)
            val body = mapOf(
                "content" to content,
                "msg_type" to "text",
                "reply_in_thread" to true,
                "root_id" to messageId
            )

            val result = client.post("/open-apis/im/v1/messages", body)

            if (result.isFailure) {
                return@withContext Result.failure(result.exceptionOrNull()!!)
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val newMessageId = data?.get("message_id")?.asString
                ?: return@withContext Result.failure(Exception("Missing message_id"))

            Log.d(TAG, "Reply sent: $newMessageId")
            Result.success(SendResult(newMessageId, listOf(newMessageId)))

        } catch (e: Exception) {
            Log.e(TAG, "Failed to reply message", e)
            Result.failure(e)
        }
    }

    /**
     * 获取消息详情
     */
    suspend fun getMessage(messageId: String): Result<MessageInfo> = withContext(Dispatchers.IO) {
        try {
            val result = client.get("/open-apis/im/v1/messages/$messageId")

            if (result.isFailure) {
                return@withContext Result.failure(result.exceptionOrNull()!!)
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val items = data?.getAsJsonArray("items")

            if (items == null || items.size() == 0) {
                return@withContext Result.failure(Exception("Message not found"))
            }

            val item = items[0].asJsonObject
            val chatId = item.get("chat_id")?.asString ?: ""
            val msgType = item.get("msg_type")?.asString ?: ""
            val body = item.getAsJsonObject("body")
            val content = body?.get("content")?.asString ?: ""
            val senderId = item.getAsJsonObject("sender")?.get("id")?.asString

            val messageInfo = MessageInfo(
                messageId = messageId,
                chatId = chatId,
                senderId = senderId,
                content = extractPlainText(content, msgType),
                contentType = msgType,
                createTime = item.get("create_time")?.asLong
            )

            Result.success(messageInfo)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get message", e)
            Result.failure(e)
        }
    }

    // ===== 内部方法 =====

    /**
     * 发送消息（内部）
     */
    private suspend fun sendMessageInternal(
        receiveId: String,
        msgType: String,
        content: String,
        receiveIdType: String
    ): Result<SendResult> {
        val body = mapOf(
            "receive_id" to receiveId,
            "msg_type" to msgType,
            "content" to content
        )

        val result = client.post(
            "/open-apis/im/v1/messages?receive_id_type=$receiveIdType",
            body
        )

        if (result.isFailure) {
            return Result.failure(result.exceptionOrNull()!!)
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        val messageId = data?.get("message_id")?.asString
            ?: return Result.failure(Exception("Missing message_id"))

        Log.d(TAG, "Message sent: $messageId")
        return Result.success(SendResult(messageId, listOf(messageId)))
    }

    /**
     * 发送分块消息
     */
    private suspend fun sendChunkedMessages(
        receiveId: String,
        chunks: List<String>,
        receiveIdType: String
    ): Result<SendResult> {
        val messageIds = mutableListOf<String>()

        for ((index, chunk) in chunks.withIndex()) {
            val prefix = if (index > 0) "[续...]\n" else ""
            val suffix = if (index < chunks.size - 1) "\n[未完待续...]" else ""
            val text = "$prefix$chunk$suffix"

            val content = buildTextContent(text)
            val result = sendMessageInternal(receiveId, "text", content, receiveIdType)

            if (result.isSuccess) {
                messageIds.add(result.getOrNull()!!.messageId)
            } else {
                Log.e(TAG, "Failed to send chunk $index")
            }

            // 避免发送过快
            kotlinx.coroutines.delay(200)
        }

        if (messageIds.isEmpty()) {
            return Result.failure(Exception("All chunks failed to send"))
        }

        return Result.success(SendResult(messageIds.first(), messageIds))
    }

    /**
     * 构建文本内容
     */
    private fun buildTextContent(text: String): String {
        return gson.toJson(mapOf("text" to text))
    }

    /**
     * 构建带提及的文本内容
     */
    private fun buildMentionedTextContent(
        text: String,
        mentionTargets: List<MentionTarget>
    ): String {
        // 构建提及内容
        val mentionedText = StringBuilder()
        for (target in mentionTargets) {
            mentionedText.append("<at user_id=\"${target.userId}\"></at> ")
        }
        mentionedText.append(text)

        return gson.toJson(mapOf("text" to mentionedText.toString()))
    }

    /**
     * 文本分块
     */
    private fun chunkText(text: String, limit: Int): List<String> {
        if (text.length <= limit) {
            return listOf(text)
        }

        val chunks = mutableListOf<String>()
        when (config.chunkMode) {
            FeishuConfig.ChunkMode.LENGTH -> {
                // 按长度分块
                var start = 0
                while (start < text.length) {
                    val end = minOf(start + limit, text.length)
                    chunks.add(text.substring(start, end))
                    start = end
                }
            }
            FeishuConfig.ChunkMode.NEWLINE -> {
                // 按换行符分块
                val lines = text.split("\n")
                var currentChunk = StringBuilder()

                for (line in lines) {
                    if (currentChunk.length + line.length + 1 > limit) {
                        if (currentChunk.isNotEmpty()) {
                            chunks.add(currentChunk.toString())
                            currentChunk = StringBuilder()
                        }
                        // 如果单行超过限制，强制分块
                        if (line.length > limit) {
                            chunks.addAll(chunkText(line, limit))
                        } else {
                            currentChunk.append(line)
                        }
                    } else {
                        if (currentChunk.isNotEmpty()) {
                            currentChunk.append("\n")
                        }
                        currentChunk.append(line)
                    }
                }

                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString())
                }
            }
        }

        return chunks
    }

    /**
     * 提取纯文本
     */
    private fun extractPlainText(content: String, msgType: String): String {
        return try {
            val json = gson.fromJson(content, JsonObject::class.java)
            when (msgType) {
                "text" -> json.get("text")?.asString ?: content
                "post" -> {
                    // 富文本消息，提取所有文本
                    val contentObj = json.getAsJsonObject("content")
                    val zhCn = contentObj?.getAsJsonArray("zh_cn")
                    zhCn?.joinToString("\n") { element ->
                        element.asJsonArray.joinToString("") { node ->
                            val nodeObj = node.asJsonObject
                            nodeObj.get("text")?.asString ?: ""
                        }
                    } ?: content
                }
                else -> content
            }
        } catch (e: Exception) {
            content
        }
    }

    /**
     * 检测是否应该使用卡片格式
     * 对齐 OpenClaw: /```[\s\S]*?```/.test(text) || /\|.+\|[\r\n]+\|[-:| ]+\|/.test(text)
     */
    private fun shouldUseCard(text: String): Boolean {
        // 检测代码块 ```
        if (text.contains("```")) {
            return true
        }

        // 检测 Markdown 表格 |...|
        val tableCount = countMarkdownTables(text)
        if (tableCount > 0) {
            // 飞书卡片表格数量限制检查
            if (tableCount > config.maxTablesPerCard) {
                Log.w(TAG, "⚠️ 表格数量 ($tableCount) 超过飞书限制 (${config.maxTablesPerCard}),将使用纯文本")
                return false  // 超过限制,降级为纯文本
            }
            return true
        }

        return false
    }

    /**
     * 统计 Markdown 中的表格数量
     */
    private fun countMarkdownTables(text: String): Int {
        var tableCount = 0
        val lines = text.lines()
        var inTable = false

        for (i in 0 until lines.size - 1) {
            val line = lines[i]
            val nextLine = lines.getOrNull(i + 1) ?: continue

            // 检查是否是表格行（包含 |）
            if (line.contains("|") && !inTable) {
                // 检查下一行是否是分隔符（如 |---|---| 或 |:-:|:-:|）
                if (nextLine.matches(Regex("^\\s*\\|[-:| ]+\\|\\s*$"))) {
                    tableCount++
                    inTable = true
                }
            } else if (inTable && !line.contains("|")) {
                // 表格结束
                inTable = false
            }
        }

        return tableCount
    }

    /**
     * 构建 Markdown 卡片
     * 对齐 OpenClaw buildMarkdownCard()
     *
     * Uses schema 2.0 format for proper markdown rendering (code blocks, tables, etc.)
     */
    private fun buildMarkdownCard(
        text: String,
        mentionTargets: List<MentionTarget> = emptyList()
    ): String {
        // 处理 @提及
        val cardText = if (mentionTargets.isNotEmpty()) {
            buildMentionedCardContent(text, mentionTargets)
        } else {
            text
        }

        val card = mapOf(
            "schema" to "2.0",
            "config" to mapOf(
                "wide_screen_mode" to true
            ),
            "body" to mapOf(
                "elements" to listOf(
                    mapOf(
                        "tag" to "markdown",
                        "content" to cardText
                    )
                )
            )
        )

        return gson.toJson(card)
    }

    /**
     * 构建带提及的卡片内容
     * 对齐 OpenClaw buildMentionedCardContent()
     */
    private fun buildMentionedCardContent(
        text: String,
        mentionTargets: List<MentionTarget>
    ): String {
        // 在卡片中，使用 <at user_id="xxx"></at> 语法
        val mentionedText = StringBuilder()
        for (target in mentionTargets) {
            mentionedText.append("<at user_id=\"${target.userId}\"></at> ")
        }
        mentionedText.append(text)
        return mentionedText.toString()
    }
}

/**
 * 渲染模式（对齐 OpenClaw）
 */
enum class RenderMode {
    /** 自动检测（默认） */
    AUTO,
    /** 强制使用卡片 */
    CARD,
    /** 强制使用文本 */
    TEXT
}

/**
 * 提及目标
 */
data class MentionTarget(
    val userId: String,
    val userType: String = "open_id",
    val name: String? = null
)

/**
 * 发送结果
 */
data class SendResult(
    val messageId: String,
    val allMessageIds: List<String>
)

/**
 * 消息信息
 */
data class MessageInfo(
    val messageId: String,
    val chatId: String,
    val senderId: String?,
    val content: String,
    val contentType: String,
    val createTime: Long?
)

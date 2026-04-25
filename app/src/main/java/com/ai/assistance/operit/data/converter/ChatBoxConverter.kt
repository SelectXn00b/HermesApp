package com.ai.assistance.operit.data.converter

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * ChatBox 格式转换器
 * 支持 ChatBox 桌面应用的导出格式
 * 
 * ChatBox 导出格式参考：
 * {
 *   "sessions": [
 *     {
 *       "id": "session-id",
 *       "name": "会话名称",
 *       "messages": [
 *         {
 *           "id": "msg-id",
 *           "role": "user" | "assistant",
 *           "content": "消息内容",
 *           "createdAt": 1234567890
 *         }
 *       ],
 *       "createdAt": 1234567890,
 *       "model": "gpt-4"
 *     }
 *   ]
 * }
 */
class ChatBoxConverter(private val context: Context) : ChatFormatConverter {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun convert(content: String): List<ChatHistory> {
        return try {
            val rootElement = parseRootElement(content)

            when (rootElement) {
                is JsonObject -> parseObjectFormat(rootElement)
                is JsonArray -> throw ConversionException(context.getString(R.string.chatbox_unsupported_format) + "：" + context.getString(R.string.chatbox_unsupported_format_detail))
                else -> throw ConversionException(context.getString(R.string.chatbox_unsupported_format))
            }
        } catch (e: Exception) {
            throw ConversionException(context.getString(R.string.chatbox_parse_error, e.message ?: ""), e)
        }
    }
    
    override fun getSupportedFormat(): ChatFormat = ChatFormat.CHATBOX
    
    /**
     * 解析对象格式
     */
    private fun parseObjectFormat(obj: JsonObject): List<ChatHistory> {
        return when {
            isKvExportFormat(obj) -> {
                parseKvExportFormat(obj)
            }
            // 单个 session 对象
            obj.containsKey("messages") || obj.containsKey("name") -> {
                val session = parseSession(obj)
                if (session != null) listOf(session) else emptyList()
            }
            else -> throw ConversionException(context.getString(R.string.chatbox_invalid_export_file) + "（" + context.getString(R.string.chatbox_invalid_export_hint) + "）")
        }
    }
    
    /**
     * 解析单个 session
     */
    private fun parseSession(
        sessionObj: JsonObject,
        fallbackId: String? = null,
        fallbackTitle: String? = null
    ): ChatHistory? {
        try {
            // 基准时间戳
            val baseTimestamp = System.currentTimeMillis()
            val sessionModelName = sessionObj["model"]?.jsonPrimitive?.contentOrNull
                ?: sessionObj["modelName"]?.jsonPrimitive?.contentOrNull
            
            // 提取消息列表
            val messagesElement = sessionObj["messages"] ?: return null
            if (messagesElement !is JsonArray || messagesElement.isEmpty()) {
                return null
            }
            
            val messages = messagesElement.mapIndexedNotNull { index, element ->
                if (element is JsonObject) {
                    parseMessage(element, baseTimestamp, index, sessionModelName)
                } else {
                    null
                }
            }
            
            if (messages.isEmpty()) {
                return null
            }
            
            // 提取会话信息
            val title = sessionObj["name"]?.jsonPrimitive?.contentOrNull
                ?: sessionObj["title"]?.jsonPrimitive?.contentOrNull
                ?: fallbackTitle
                ?: "Imported from ChatBox"
            
            val id = sessionObj["id"]?.jsonPrimitive?.contentOrNull
                ?: fallbackId
                ?: UUID.randomUUID().toString()
            
            // 提取创建时间
            val createdAt = sessionObj["createdAt"]?.jsonPrimitive?.longOrNull?.let {
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(it),
                    ZoneId.systemDefault()
                )
            } ?: LocalDateTime.now()
            
            val updatedAt = sessionObj["updatedAt"]?.jsonPrimitive?.longOrNull?.let {
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(it),
                    ZoneId.systemDefault()
                )
            } ?: createdAt
            
            return ChatHistory(
                id = id,
                title = title,
                messages = messages,
                createdAt = createdAt,
                updatedAt = updatedAt,
                group = context.getString(R.string.chatbox_import_group)
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * 解析单条消息
     */
    private fun parseMessage(
        msgObj: JsonObject,
        baseTimestamp: Long,
        index: Int,
        fallbackModelName: String?
    ): ChatMessage? {
        try {
            val role = msgObj["role"]?.jsonPrimitive?.contentOrNull ?: return null
            val content = buildMessageContent(msgObj) ?: return null
            
            // 规范化角色
            val sender = normalizeRole(role)
            
            // 提取时间戳（如果有的话，否则使用递增时间戳）
            val rawTimestamp = msgObj["createdAt"]?.jsonPrimitive?.longOrNull
                ?: msgObj["timestamp"]?.jsonPrimitive?.longOrNull
            val timestamp = rawTimestamp?.let { normalizeTimestamp(it) }
                ?: (baseTimestamp + (index * 100L))
            
            // 提取模型信息
            val modelName = msgObj["model"]?.jsonPrimitive?.contentOrNull
                ?: msgObj["modelName"]?.jsonPrimitive?.contentOrNull
                ?: fallbackModelName
                ?: "chatbox"
            
            val provider = msgObj["provider"]?.jsonPrimitive?.contentOrNull
                ?: "ChatBox"
            
            return ChatMessage(
                sender = sender,
                content = content,
                timestamp = timestamp,
                provider = provider,
                modelName = modelName
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseRootElement(content: String): JsonElement {
        val normalizedContent = normalizeInput(content)
        return try {
            val element = json.parseToJsonElement(normalizedContent)
            extractJsonIfStringWrapper(element)
        } catch (e: Exception) {
            val repaired = repairEscapedJson(normalizedContent)
            if (repaired != normalizedContent) {
                val element = json.parseToJsonElement(repaired)
                extractJsonIfStringWrapper(element)
            } else {
                throw e
            }
        }
    }

    private fun normalizeInput(content: String): String {
        return content
            .removePrefix("\uFEFF")
            .trim()
    }

    private fun extractJsonIfStringWrapper(element: JsonElement): JsonElement {
        if (element is JsonPrimitive && element.isString) {
            val inner = element.content.trim()
            return json.parseToJsonElement(inner)
        }
        return element
    }

    private fun repairEscapedJson(content: String): String {
        val trimmed = content.trim()

        if (
            (trimmed.startsWith("{\\\"") && trimmed.contains("\\\"")) ||
            (trimmed.startsWith("[\\\"") && trimmed.contains("\\\""))
        ) {
            return trimmed.replace("\\\"", "\"")
        }

        return trimmed
    }

    private fun isKvExportFormat(obj: JsonObject): Boolean {
        if (!obj.containsKey("chat-sessions-list")) return false
        return obj.keys.any { it.startsWith("session:") }
    }

    private fun parseKvExportFormat(obj: JsonObject): List<ChatHistory> {
        val sessionsList = obj["chat-sessions-list"] as? JsonArray ?: return emptyList()

        return sessionsList.mapNotNull { metaElement ->
            val metaObj = metaElement as? JsonObject ?: return@mapNotNull null
            val sessionId = metaObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null

            val sessionObj = obj["session:$sessionId"] as? JsonObject ?: return@mapNotNull null

            val fallbackTitle = metaObj["title"]?.jsonPrimitive?.contentOrNull
                ?: metaObj["name"]?.jsonPrimitive?.contentOrNull

            parseSession(
                sessionObj = sessionObj,
                fallbackId = sessionId,
                fallbackTitle = fallbackTitle
            )
        }
    }

    private fun buildMessageContent(msgObj: JsonObject): String? {
        val content = extractMessageText(msgObj)
        val errorText = extractErrorText(msgObj)

        val finalContent = when {
            !content.isNullOrBlank() -> content
            !errorText.isNullOrBlank() -> context.getString(R.string.chatbox_message_generation_failed, errorText)
            else -> null
        } ?: return null

        val hasImages = (msgObj["contentParts"] as? JsonArray)
            ?.any { (it as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "image" }
            ?: false

        return if (hasImages) {
            "$finalContent\n${context.getString(R.string.chatbox_image_not_saved)}"
        } else {
            finalContent
        }
    }

    private fun extractMessageText(msgObj: JsonObject): String? {
        val content = msgObj["content"]?.jsonPrimitive?.contentOrNull
        if (!content.isNullOrBlank()) return content

        val parts = msgObj["contentParts"] as? JsonArray ?: return null
        val text = parts.mapNotNull { partElement ->
            val partObj = partElement as? JsonObject ?: return@mapNotNull null
            val type = partObj["type"]?.jsonPrimitive?.contentOrNull
            if (type != "text") return@mapNotNull null
            partObj["text"]?.jsonPrimitive?.contentOrNull
        }.joinToString("")

        return text.ifBlank { null }
    }

    private fun extractErrorText(msgObj: JsonObject): String? {
        val error = msgObj["error"]?.jsonPrimitive?.contentOrNull
        if (!error.isNullOrBlank()) return error

        val extra = msgObj["errorExtra"] as? JsonObject
        val responseBody = extra?.get("responseBody")?.jsonPrimitive?.contentOrNull
        if (!responseBody.isNullOrBlank()) return responseBody

        val errorCode = msgObj["errorCode"]?.jsonPrimitive?.contentOrNull
        if (!errorCode.isNullOrBlank()) return errorCode

        return null
    }

    private fun normalizeTimestamp(raw: Long): Long {
        return if (raw in 1..999_999_999_999L) {
            raw * 1000L
        } else {
            raw
        }
    }
    
    /**
     * 规范化角色名称
     */
    private fun normalizeRole(role: String): String {
        return when (role.lowercase()) {
            "user", "human", context.getString(R.string.chatbox_role_user).lowercase() -> "user"
            "assistant", "ai", "bot", "model", context.getString(R.string.chatbox_role_assistant).lowercase() -> "ai"
            "system", context.getString(R.string.chatbox_role_system).lowercase() -> "user"
            else -> "user"
        }
    }
}

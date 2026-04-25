package com.ai.assistance.operit.data.converter

import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * ChatGPT conversations.json 格式转换器
 * 支持 OpenAI 官方导出的格式
 */
class ChatGPTConverter : ChatFormatConverter {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override fun convert(content: String): List<ChatHistory> {
        return try {
            val conversations = json.decodeFromString<List<ChatGPTConversation>>(content)
            conversations.mapNotNull { convertConversation(it) }
        } catch (e: Exception) {
            throw ConversionException("Failed to parse ChatGPT format: ${e.message}", e)
        }
    }
    
    override fun getSupportedFormat(): ChatFormat = ChatFormat.CHATGPT
    
    private fun convertConversation(conv: ChatGPTConversation): ChatHistory? {
        try {
            // 从 mapping 中提取消息
            val messages = extractMessagesFromMapping(conv.mapping, conv.current_node)
            
            if (messages.isEmpty()) {
                return null
            }
            
            val createdAt = if (conv.create_time > 0) {
                Instant.ofEpochSecond(conv.create_time)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
            } else {
                LocalDateTime.now()
            }
            
            val updatedAt = if (conv.update_time > 0) {
                Instant.ofEpochSecond(conv.update_time)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
            } else {
                createdAt
            }
            
            return ChatHistory(
                id = conv.id ?: UUID.randomUUID().toString(),
                title = conv.title ?: "Untitled Conversation",
                messages = messages,
                createdAt = createdAt,
                updatedAt = updatedAt,
                group = "Imported from ChatGPT"
            )
        } catch (e: Exception) {
            // 跳过无法转换的对话
            return null
        }
    }
    
    /**
     * 从 ChatGPT 的 mapping 结构中提取消息链
     */
    private fun extractMessagesFromMapping(
        mapping: Map<String, ChatGPTNode>,
        currentNodeId: String?
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        
        // 从 current_node 开始回溯
        var nodeId = currentNodeId
        val visited = mutableSetOf<String>()
        
        while (nodeId != null && !visited.contains(nodeId)) {
            visited.add(nodeId)
            
            val node = mapping[nodeId] ?: break
            val message = node.message
            
            // 提取有效消息
            if (message != null && shouldIncludeMessage(message)) {
                val chatMessage = convertMessage(message)
                if (chatMessage != null) {
                    messages.add(0, chatMessage) // 添加到开头以保持顺序
                }
            }
            
            nodeId = node.parent
        }
        
        return messages
    }
    
    /**
     * 判断是否应该包含该消息
     */
    private fun shouldIncludeMessage(message: ChatGPTMessage): Boolean {
        // 跳过系统消息（除非是用户自定义的系统消息）
        if (message.author.role == "system" && 
            message.metadata?.is_user_system_message != true) {
            return false
        }
        
        // 必须有内容
        val content = message.content
        if (content == null) return false
        
        // 必须是文本类型且有内容
        if (content.content_type != "text") return false
        
        val parts = content.parts
        if (parts.isEmpty() || parts.first().isBlank()) return false
        
        return true
    }
    
    /**
     * 转换单条消息
     */
    private fun convertMessage(message: ChatGPTMessage): ChatMessage? {
        try {
            val content = message.content ?: return null
            val text = content.parts.firstOrNull() ?: return null
            
            val sender = when (message.author.role) {
                "user" -> "user"
                "assistant" -> "ai"
                "system" -> if (message.metadata?.is_user_system_message == true) "user" else return null
                else -> "user"
            }
            
            val timestamp = if (message.create_time > 0) {
                message.create_time * 1000
            } else {
                System.currentTimeMillis()
            }
            
            val modelName = message.metadata?.model_slug ?: "gpt-3.5-turbo"
            
            return ChatMessage(
                sender = sender,
                content = text,
                timestamp = timestamp,
                provider = "OpenAI",
                modelName = modelName
            )
        } catch (e: Exception) {
            return null
        }
    }
}

// ChatGPT 数据结构定义
@Serializable
data class ChatGPTConversation(
    val id: String? = null,
    val title: String? = null,
    val create_time: Long = 0,
    val update_time: Long = 0,
    val mapping: Map<String, ChatGPTNode> = emptyMap(),
    val current_node: String? = null
)

@Serializable
data class ChatGPTNode(
    val id: String? = null,
    val message: ChatGPTMessage? = null,
    val parent: String? = null,
    val children: List<String> = emptyList()
)

@Serializable
data class ChatGPTMessage(
    val id: String? = null,
    val author: ChatGPTAuthor,
    val content: ChatGPTContent? = null,
    val create_time: Long = 0,
    val metadata: ChatGPTMetadata? = null
)

@Serializable
data class ChatGPTAuthor(
    val role: String,
    val name: String? = null
)

@Serializable
data class ChatGPTContent(
    val content_type: String,
    val parts: List<String> = emptyList()
)

@Serializable
data class ChatGPTMetadata(
    val model_slug: String? = null,
    val is_user_system_message: Boolean? = null
)

package com.ai.assistance.operit.data.converter

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Markdown 格式转换器
 * 支持多种 Markdown 对话格式
 */
class MarkdownConverter(private val context: Context) : ChatFormatConverter {
    
    private val dateFormatters = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ISO_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    )
    
    override fun convert(content: String): List<ChatHistory> {
        return try {
            // 尝试分割多个对话（如果有分隔符）
            val conversations = splitConversations(content)
            conversations.mapNotNull { parseConversation(it) }
        } catch (e: Exception) {
            throw ConversionException(context.getString(R.string.markdown_parse_failed, e.message ?: ""), e)
        }
    }
    
    override fun getSupportedFormat(): ChatFormat = ChatFormat.MARKDOWN
    
    /**
     * 分割多个对话（如果存在）
     */
    private fun splitConversations(content: String): List<String> {
        // 不再支持通过 "---" 分割对话，整个文件视为一个对话
        return listOf(content)
    }
    
    /**
     * 解析单个对话
     */
    private fun parseConversation(content: String): ChatHistory? {
        val lines = content.lines()
        val messages = mutableListOf<ChatMessage>()
        
        var title = "Imported from Markdown"
        var createdAt = LocalDateTime.now()
        var currentRole: String? = null
        var currentContent = StringBuilder()
        var currentModel: String = "markdown"
        var currentTimestamp: Long = System.currentTimeMillis()
        var messageIndex = 0
        
        // 标记是否刚刚开始一条新消息（用于跳过紧随其后的装饰性标题）
        var justStartedMessage = false
        
        var i = 0
        
        // 1. 尝试查找 chat-info 注释
        // 格式: <!-- chat-info: title=xxx, created=xxx -->
        // 或者: <!-- chat-info: {"title":"xxx"} --> (兼容旧版/严谨模式，但我们主要支持简化版)
        
        // 2. 解析 YAML front matter 或 chat-info（如果存在）
        if (lines.firstOrNull()?.trim() == "---") {
            // 处理旧版 Front Matter
            i = 1
            val metadata = mutableMapOf<String, String>()
            while (i < lines.size && lines[i].trim() != "---") {
                val line = lines[i].trim()
                if (line.contains(":")) {
                    val (key, value) = line.split(":", limit = 2)
                    metadata[key.trim()] = value.trim()
                }
                i++
            }
            i++ // 跳过结束的 ---
            metadata["title"]?.let { title = it }
            metadata["created"]?.let { createdAt = parseDate(it) ?: createdAt }
        }
        
        // 基准时间戳
        val baseTimestamp = System.currentTimeMillis()
        
        // 主解析循环
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            
            // 检查 chat-info
            if (trimmed.startsWith("<!-- chat-info:")) {
                val props = parseSimpleProperties(trimmed, "chat-info")
                props["title"]?.let { title = it }
                props["created"]?.let { createdAt = parseDate(it) ?: createdAt }
                i++
                continue
            }
            
            // 检查 msg 注释
            // 格式: <!-- msg: user --> 或 <!-- msg: role=user, model=gpt-4 -->
            if (trimmed.startsWith("<!-- msg:")) {
                // 保存上一条消息
                if (currentRole != null && currentContent.isNotEmpty()) {
                    messages.add(createMessage(
                        currentRole!!, 
                        currentContent.toString(),
                        currentTimestamp,
                        currentModel
                    ))
                    currentContent.clear()
                }
                
                val props = parseSimpleProperties(trimmed, "msg")
                
                // 提取角色
                var role = props["role"]
                // 支持简写: 如果没有 key，但 value 是 user/ai 等，则认为是角色
                if (role == null) {
                    for ((key, value) in props) {
                        // 特殊处理：如果在解析时把无key的项当作了 key (value为空)，或者 key 本身就是 value
                        // 在 parseSimpleProperties 中我们把无等号的项作为 key，value 为空字符串
                        // 或者我们可以改进 parseSimpleProperties
                        // 这里假设 props 包含了 key="" value="user" 这种情况？
                        // 为了简单，我们在 parseSimpleProperties 里处理好
                        
                        // 检查简写角色
                        val potentialRole = parseRole(key)
                        if (potentialRole != null) {
                            role = potentialRole
                            break
                        }
                    }
                }
                
                currentRole = role ?: "user" // 默认为 user
                currentModel = props["model"] ?: "markdown"
                
                val tsStr = props["timestamp"]
                currentTimestamp = tsStr?.toLongOrNull() ?: (baseTimestamp + messageIndex * 100L)
                
                messageIndex++
                justStartedMessage = true
                i++
                continue
            }
            
            // 忽略紧随 msg 注释后的装饰性 ## Role 标题
            if (justStartedMessage && trimmed.startsWith("## ")) {
                // 检查是否是装饰性标题
                val roleText = trimmed.substring(3).trim()
                val parsedRole = parseRole(roleText)
                // 如果标题角色与当前消息角色一致，或者是装饰性的，跳过它
                if (parsedRole != null || currentRole != null) {
                    // 跳过这行装饰
                    justStartedMessage = false
                    i++
                    continue
                }
            }
            
            if (justStartedMessage && trimmed.isEmpty()) {
                // 跳过注释后的空行
                i++
                continue
            }
            
            justStartedMessage = false
            
            // 收集消息内容
            if (currentRole != null) {
                currentContent.append(line).append("\n")
            }
            
            i++
        }
        
        // 保存最后一条消息
        if (currentRole != null && currentContent.isNotEmpty()) {
            messages.add(createMessage(
                currentRole!!, 
                currentContent.toString(),
                currentTimestamp,
                currentModel
            ))
        }
        
        if (messages.isEmpty()) {
            return null
        }
        
        return ChatHistory(
            id = UUID.randomUUID().toString(),
            title = title,
            messages = messages,
            createdAt = createdAt,
            updatedAt = LocalDateTime.now(),
            group = context.getString(R.string.markdown_import_from)
        )
    }
    
    /**
     * 解析简单的属性字符串
     * 格式: key=value, key2=value2, simpleFlag
     */
    private fun parseSimpleProperties(comment: String, prefix: String): Map<String, String> {
        val content = comment.trim()
            .removePrefix("<!-- $prefix:")
            .removeSuffix("-->")
            .trim()
            
        if (content.isEmpty()) return emptyMap()
        
        val result = mutableMapOf<String, String>()
        // 按逗号或分号分割
        val parts = content.split(Regex("[,;]"))
        
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            
            if (trimmed.contains("=")) {
                val (key, value) = trimmed.split("=", limit = 2)
                result[key.trim()] = value.trim()
            } else {
                // 没有等号，作为 key 存在，value 为空，或者作为特定标记
                // 为了支持 "user" 这种简写，我们将整个字符串作为 key
                result[trimmed] = ""
            }
        }
        return result
    }
    
    /**
     * 解析角色标记
     * 只支持明确的角色名称，避免误判
     */
    private fun parseRole(roleText: String): String? {
        val lower = roleText.lowercase().trim()

        // 严格前缀匹配或完全匹配
        // 去除特殊符号（如 emoji）后再判断
        val cleanText = lower.replace(Regex("[^a-z0-9\u4e00-\u9fa5]"), "")

        val userText = context.getString(R.string.message_role_user)
        val assistantText = context.getString(R.string.role_assistant)
        val systemText = context.getString(R.string.role_system)
        val modelText = context.getString(R.string.role_model)

        return when {
            cleanText == "user" || cleanText == userText -> "user"
            cleanText == "assistant" || cleanText == "ai" || cleanText == assistantText -> "ai"
            cleanText == "system" || cleanText == systemText -> "user" // 系统消息映射为用户
            cleanText == "model" || cleanText == modelText -> "ai"
            else -> null // 无法识别时不默认为 user，而是返回 null
        }
    }
    
    /**
     * 创建消息对象
     */
    private fun createMessage(role: String, content: String, timestamp: Long, model: String): ChatMessage {
        return ChatMessage(
            sender = role,
            content = content.trim(),
            timestamp = timestamp,
            provider = "Imported",
            modelName = model
        )
    }
    
    /**
     * 尝试解析日期字符串
     */
    private fun parseDate(dateStr: String): LocalDateTime? {
        for (formatter in dateFormatters) {
            try {
                return LocalDateTime.parse(dateStr, formatter)
            } catch (_: DateTimeParseException) {
                continue
            }
        }
        return null
    }
}

package com.ai.assistance.operit.data.converter

import com.ai.assistance.operit.data.model.ChatHistory

/**
 * 聊天记录格式转换器接口
 */
interface ChatFormatConverter {
    /**
     * 将字符串内容转换为 ChatHistory 列表
     * @param content 文件内容
     * @return 转换后的聊天记录列表
     * @throws ConversionException 转换失败时抛出
     */
    fun convert(content: String): List<ChatHistory>
    
    /**
     * 获取转换器支持的格式
     */
    fun getSupportedFormat(): ChatFormat
}

/**
 * 转换异常
 */
class ConversionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 转换结果
 */
data class ConversionResult(
    val chatHistories: List<ChatHistory>,
    val format: ChatFormat,
    val totalMessages: Int,
    val warnings: List<String> = emptyList()
)

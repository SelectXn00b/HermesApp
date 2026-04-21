package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 消息实体类，用于Room数据库存储聊天消息 */
@Entity(
        tableName = "messages",
        foreignKeys =
                [
                        ForeignKey(
                                entity = ChatEntity::class,
                                parentColumns = ["id"],
                                childColumns = ["chatId"],
                                onDelete = ForeignKey.CASCADE
                        )],
        indices = [Index("chatId")]
)
data class MessageEntity(
        @PrimaryKey(autoGenerate = true) val messageId: Long = 0,
        val chatId: String,
        val sender: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        val orderIndex: Int, // 保持消息顺序
        val roleName: String = "", // 角色名字段
        val selectedVariantIndex: Int = 0,
        val provider: String = "", // 供应商
        val modelName: String = "", // 模型名称
        val inputTokens: Int = 0,
        val outputTokens: Int = 0,
        val cachedInputTokens: Int = 0,
        val sentAt: Long = 0L,
        val outputDurationMs: Long = 0L,
        val waitDurationMs: Long = 0L
) {
    /** 转换为ChatMessage对象（供UI层使用） */
    fun toChatMessage(): ChatMessage {
        return ChatMessage(
            sender = sender, 
            content = content, 
            timestamp = timestamp,
            roleName = roleName,
            selectedVariantIndex = selectedVariantIndex,
            provider = provider,
            modelName = modelName,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cachedInputTokens = cachedInputTokens,
            sentAt = sentAt,
            outputDurationMs = outputDurationMs,
            waitDurationMs = waitDurationMs
        )
    }

    companion object {
        /** 从ChatMessage创建MessageEntity */
        fun fromChatMessage(
            chatId: String,
            message: ChatMessage,
            orderIndex: Int,
            messageId: Long = 0
        ): MessageEntity {
            return MessageEntity(
                    messageId = messageId,
                    chatId = chatId,
                    sender = message.sender,
                    content = message.content,
                    timestamp = message.timestamp,
                    orderIndex = orderIndex,
                    roleName = message.roleName,
                    selectedVariantIndex = message.selectedVariantIndex,
                    provider = message.provider,
                    modelName = message.modelName,
                    inputTokens = message.inputTokens,
                    outputTokens = message.outputTokens,
                    cachedInputTokens = message.cachedInputTokens,
                    sentAt = message.sentAt,
                    outputDurationMs = message.outputDurationMs,
                    waitDurationMs = message.waitDurationMs
            )
        }
    }
}

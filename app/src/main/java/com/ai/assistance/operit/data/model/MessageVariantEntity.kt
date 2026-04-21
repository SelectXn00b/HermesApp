package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "message_variants",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chatId", "messageTimestamp"]),
        Index(value = ["chatId", "messageTimestamp", "variantIndex"], unique = true),
    ],
)
data class MessageVariantEntity(
    @PrimaryKey(autoGenerate = true) val variantId: Long = 0,
    val chatId: String,
    val messageTimestamp: Long,
    val variantIndex: Int,
    val content: String,
    val roleName: String = "",
    val provider: String = "",
    val modelName: String = "",
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cachedInputTokens: Int = 0,
    val sentAt: Long = 0L,
    val outputDurationMs: Long = 0L,
    val waitDurationMs: Long = 0L,
) {
    fun applyTo(baseMessage: ChatMessage, variantCount: Int): ChatMessage {
        return baseMessage.copy(
            content = content,
            roleName = roleName.ifBlank { baseMessage.roleName },
            selectedVariantIndex = variantIndex,
            variantCount = variantCount,
            provider = provider,
            modelName = modelName,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cachedInputTokens = cachedInputTokens,
            sentAt = sentAt,
            outputDurationMs = outputDurationMs,
            waitDurationMs = waitDurationMs,
        )
    }

    companion object {
        fun fromChatMessage(
            chatId: String,
            messageTimestamp: Long,
            variantIndex: Int,
            message: ChatMessage,
            variantId: Long = 0,
        ): MessageVariantEntity {
            return MessageVariantEntity(
                variantId = variantId,
                chatId = chatId,
                messageTimestamp = messageTimestamp,
                variantIndex = variantIndex,
                content = message.content,
                roleName = message.roleName,
                provider = message.provider,
                modelName = message.modelName,
                inputTokens = message.inputTokens,
                outputTokens = message.outputTokens,
                cachedInputTokens = message.cachedInputTokens,
                sentAt = message.sentAt,
                outputDurationMs = message.outputDurationMs,
                waitDurationMs = message.waitDurationMs,
            )
        }
    }
}

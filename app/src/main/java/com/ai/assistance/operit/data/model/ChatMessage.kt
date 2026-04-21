package com.ai.assistance.operit.data.model

import android.os.Parcel
import android.os.Parcelable
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ChatMessage(
        val sender: String, // "user" or "ai"
        var content: String = "",
        val timestamp: Long = ChatMessageTimestampAllocator.next(),
        val roleName: String = "", // 角色名字字段
        val selectedVariantIndex: Int = 0, // 当前选中的回答版本，0 表示原始回答
        val variantCount: Int = 1, // 当前消息可切换的回答版本数量
        val provider: String = "", // 供应商
        val modelName: String = "", // 模型名称
        val inputTokens: Int = 0, // 本轮输入 token
        val outputTokens: Int = 0, // 本轮输出 token
        val cachedInputTokens: Int = 0, // 本轮缓存命中的输入 token
        val sentAt: Long = 0L, // 本轮请求发送时间（时间戳）
        val outputDurationMs: Long = 0L, // 本轮输出耗时
        val waitDurationMs: Long = 0L, // 本轮等待首包耗时
        @Transient
        val isVariantPreview: Boolean = false,
        @Transient
        var contentStream: Stream<String>? =
                null // 修改为Stream<String>类型，与EnhancedAIService.sendMessage返回类型匹配
) : Parcelable {
    init {
        ChatMessageTimestampAllocator.observe(timestamp)
    }

    constructor(
            parcel: Parcel
    ) : this(
        parcel.readString() ?: "", 
        parcel.readString() ?: "", 
        parcel.readLong(),
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(sender)
        parcel.writeString(content)
        parcel.writeLong(timestamp)
        parcel.writeString(roleName)
        parcel.writeInt(selectedVariantIndex)
        parcel.writeInt(variantCount)
        parcel.writeString(provider)
        parcel.writeString(modelName)
        parcel.writeInt(inputTokens)
        parcel.writeInt(outputTokens)
        parcel.writeInt(cachedInputTokens)
        parcel.writeLong(sentAt)
        parcel.writeLong(outputDurationMs)
        parcel.writeLong(waitDurationMs)
        // 不需要序列化contentStream，因为它是暂时性的
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ChatMessage> {
        override fun createFromParcel(parcel: Parcel): ChatMessage {
            return ChatMessage(parcel)
        }

        override fun newArray(size: Int): Array<ChatMessage?> {
            return arrayOfNulls(size)
        }
    }
}

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: session persistence.
 */
package com.xiaomo.discord.session

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Discord 历史消息管理
 * 参考 Feishu FeishuHistoryManager.kt
 */
class DiscordHistoryManager(
    private val maxHistoryPerChannel: Int = 100
) {
    companion object {
        private const val TAG = "DiscordHistoryManager"
    }

    // 历史消息存储 (channelId -> List<Message>)
    private val history = ConcurrentHashMap<String, MutableList<HistoryMessage>>()

    /**
     * 添加消息到历史
     */
    fun addMessage(
        channelId: String,
        messageId: String,
        authorId: String,
        content: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val messages = history.getOrPut(channelId) { mutableListOf() }

        synchronized(messages) {
            // 添加消息
            messages.add(
                HistoryMessage(
                    messageId = messageId,
                    authorId = authorId,
                    content = content,
                    timestamp = timestamp
                )
            )

            // 保持历史大小限制
            if (messages.size > maxHistoryPerChannel) {
                val toRemove = messages.size - maxHistoryPerChannel
                repeat(toRemove) {
                    messages.removeAt(0)
                }
            }
        }

        Log.d(TAG, "Added message to history: $channelId (${messages.size} messages)")
    }

    /**
     * 获取频道历史
     */
    fun getHistory(channelId: String, limit: Int = maxHistoryPerChannel): List<HistoryMessage> {
        val messages = history[channelId] ?: return emptyList()

        synchronized(messages) {
            return messages.takeLast(limit)
        }
    }

    /**
     * 清除频道历史
     */
    fun clearHistory(channelId: String) {
        history.remove(channelId)
        Log.d(TAG, "Cleared history for channel: $channelId")
    }

    /**
     * 清除所有历史
     */
    fun clearAll() {
        val count = history.size
        history.clear()
        Log.i(TAG, "Cleared all history ($count channels)")
    }

    /**
     * 获取最近的消息
     */
    fun getRecentMessage(channelId: String): HistoryMessage? {
        val messages = history[channelId] ?: return null

        synchronized(messages) {
            return messages.lastOrNull()
        }
    }

    /**
     * 查找消息
     */
    fun findMessage(channelId: String, messageId: String): HistoryMessage? {
        val messages = history[channelId] ?: return null

        synchronized(messages) {
            return messages.find { it.messageId == messageId }
        }
    }

    /**
     * 获取频道消息数量
     */
    fun getMessageCount(channelId: String): Int {
        val messages = history[channelId] ?: return 0

        synchronized(messages) {
            return messages.size
        }
    }

    /**
     * 列出所有频道
     */
    fun listChannels(): List<String> {
        return history.keys.toList()
    }
}

/**
 * 历史消息
 */
data class HistoryMessage(
    val messageId: String,
    val authorId: String,
    val content: String,
    val timestamp: Long,
    val metadata: MutableMap<String, Any> = mutableMapOf()
) {
    /**
     * 设置元数据
     */
    fun setMetadata(key: String, value: Any) {
        metadata[key] = value
    }

    /**
     * 获取元数据
     */
    fun <T> getMetadata(key: String): T? {
        @Suppress("UNCHECKED_CAST")
        return metadata[key] as? T
    }
}

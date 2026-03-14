package com.xiaomo.feishu.session

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel runtime.
 */


import android.util.Log
import com.xiaomo.feishu.FeishuConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 飞书历史记录管理器
 * 对齐 OpenClaw 历史记录管理
 */
class FeishuHistoryManager(private val config: FeishuConfig) {
    companion object {
        private const val TAG = "FeishuHistoryManager"
    }

    private val histories = ConcurrentHashMap<String, MutableList<HistoryEntry>>()
    private val mutex = Mutex()

    /**
     * 添加历史记录
     */
    suspend fun addHistory(
        chatId: String,
        chatType: String,
        entry: HistoryEntry
    ) = mutex.withLock {
        val key = "$chatType:$chatId"
        val history = histories.getOrPut(key) { mutableListOf() }

        history.add(entry)

        // 限制历史记录数量
        val limit = if (chatType == "p2p") config.dmHistoryLimit else config.historyLimit
        while (history.size > limit) {
            history.removeAt(0)
        }

        Log.d(TAG, "Added history: $key (total: ${history.size}/$limit)")
    }

    /**
     * 获取历史记录
     */
    fun getHistory(chatId: String, chatType: String, limit: Int? = null): List<HistoryEntry> {
        val key = "$chatType:$chatId"
        val history = histories[key] ?: return emptyList()

        return if (limit != null && limit < history.size) {
            history.takeLast(limit)
        } else {
            history.toList()
        }
    }

    /**
     * 清除历史记录
     */
    suspend fun clearHistory(chatId: String, chatType: String) = mutex.withLock {
        val key = "$chatType:$chatId"
        histories.remove(key)
        Log.d(TAG, "Cleared history: $key")
    }

    /**
     * 清除所有历史记录
     */
    suspend fun clearAllHistory() = mutex.withLock {
        histories.clear()
        Log.d(TAG, "Cleared all history")
    }

    /**
     * 获取历史记录摘要
     */
    fun getHistorySummary(): Map<String, Int> {
        return histories.mapValues { it.value.size }
    }
}

/**
 * 历史记录条目
 */
data class HistoryEntry(
    val messageId: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

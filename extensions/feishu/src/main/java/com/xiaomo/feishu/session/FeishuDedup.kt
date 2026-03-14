package com.xiaomo.feishu.session

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel runtime.
 */


import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * 飞书消息去重
 * 对齐 OpenClaw src/dedup.ts
 */
class FeishuDedup {
    companion object {
        private const val TAG = "FeishuDedup"
        private const val CACHE_TTL_MS = 10 * 60 * 1000L // 10分钟
        private const val MAX_CACHE_SIZE = 10000
    }

    private val messageCache = ConcurrentHashMap<String, Long>()

    /**
     * 尝试记录消息（如果已存在则返回 false）
     */
    fun tryRecordMessage(messageId: String): Boolean {
        val now = System.currentTimeMillis()

        // 清理过期缓存
        cleanupExpired(now)

        // 检查是否已存在
        val existing = messageCache.putIfAbsent(messageId, now)
        if (existing != null) {
            Log.d(TAG, "Duplicate message detected: $messageId")
            return false
        }

        // 限制缓存大小
        if (messageCache.size > MAX_CACHE_SIZE) {
            Log.w(TAG, "Message cache size exceeded, clearing old entries")
            cleanupOldest(MAX_CACHE_SIZE / 2)
        }

        return true
    }

    /**
     * 检查消息是否已处理
     */
    fun isMessageProcessed(messageId: String): Boolean {
        return messageCache.containsKey(messageId)
    }

    /**
     * 清理过期缓存
     */
    private fun cleanupExpired(now: Long) {
        val expiredKeys = messageCache.entries
            .filter { (now - it.value) > CACHE_TTL_MS }
            .map { it.key }

        expiredKeys.forEach { key ->
            messageCache.remove(key)
        }

        if (expiredKeys.isNotEmpty()) {
            Log.d(TAG, "Cleaned up ${expiredKeys.size} expired message records")
        }
    }

    /**
     * 清理最旧的记录
     */
    private fun cleanupOldest(keepCount: Int) {
        val sorted = messageCache.entries
            .sortedBy { it.value }
            .take(messageCache.size - keepCount)

        sorted.forEach { entry ->
            messageCache.remove(entry.key)
        }

        Log.d(TAG, "Cleaned up ${sorted.size} oldest message records")
    }

    /**
     * 清除所有缓存
     */
    fun clearAll() {
        messageCache.clear()
        Log.d(TAG, "Cleared all message records")
    }

    /**
     * 获取缓存统计
     */
    fun getStats(): DedupStats {
        return DedupStats(
            totalMessages = messageCache.size,
            oldestTimestamp = messageCache.values.minOrNull(),
            newestTimestamp = messageCache.values.maxOrNull()
        )
    }
}

/**
 * 去重统计
 */
data class DedupStats(
    val totalMessages: Int,
    val oldestTimestamp: Long?,
    val newestTimestamp: Long?
)

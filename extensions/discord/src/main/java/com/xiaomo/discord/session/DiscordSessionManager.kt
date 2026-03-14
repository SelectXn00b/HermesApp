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
 * Discord 会话管理
 * 参考 Feishu FeishuSessionManager.kt
 */
class DiscordSessionManager {
    companion object {
        private const val TAG = "DiscordSessionManager"
    }

    // 会话存储 (channelId -> Session)
    private val sessions = ConcurrentHashMap<String, DiscordSession>()

    /**
     * 获取或创建会话
     */
    fun getOrCreateSession(channelId: String, chatType: String): DiscordSession {
        return sessions.getOrPut(channelId) {
            Log.d(TAG, "Creating new session: $channelId ($chatType)")
            DiscordSession(
                channelId = channelId,
                chatType = chatType,
                createdAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * 获取会话
     */
    fun getSession(channelId: String): DiscordSession? {
        return sessions[channelId]
    }

    /**
     * 移除会话
     */
    fun removeSession(channelId: String): DiscordSession? {
        Log.d(TAG, "Removing session: $channelId")
        return sessions.remove(channelId)
    }

    /**
     * 清除所有会话
     */
    fun clearAll() {
        Log.i(TAG, "Clearing all sessions (${sessions.size})")
        sessions.clear()
    }

    /**
     * 获取活跃会话数量
     */
    fun getActiveSessionCount(): Int {
        return sessions.size
    }

    /**
     * 列出所有会话
     */
    fun listSessions(): List<DiscordSession> {
        return sessions.values.toList()
    }

    /**
     * 清理过期会话
     */
    fun cleanupExpiredSessions(maxIdleMs: Long = 24 * 60 * 60 * 1000L) {
        val now = System.currentTimeMillis()
        val expired = sessions.filter { (_, session) ->
            now - session.lastActivityAt > maxIdleMs
        }

        expired.forEach { (channelId, _) ->
            Log.d(TAG, "Removing expired session: $channelId")
            sessions.remove(channelId)
        }

        if (expired.isNotEmpty()) {
            Log.i(TAG, "Cleaned up ${expired.size} expired sessions")
        }
    }
}

/**
 * Discord 会话
 */
data class DiscordSession(
    val channelId: String,
    val chatType: String, // "direct", "channel", "thread"
    val createdAt: Long,
    var lastActivityAt: Long = System.currentTimeMillis(),
    var messageCount: Int = 0,
    val context: MutableMap<String, Any> = mutableMapOf()
) {
    /**
     * 更新活跃时间
     */
    fun touch() {
        lastActivityAt = System.currentTimeMillis()
        messageCount++
    }

    /**
     * 设置上下文
     */
    fun setContext(key: String, value: Any) {
        context[key] = value
    }

    /**
     * 获取上下文
     */
    fun <T> getContext(key: String): T? {
        @Suppress("UNCHECKED_CAST")
        return context[key] as? T
    }

    /**
     * 清除上下文
     */
    fun clearContext() {
        context.clear()
    }
}

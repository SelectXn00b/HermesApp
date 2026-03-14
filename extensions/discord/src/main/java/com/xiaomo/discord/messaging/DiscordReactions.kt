/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/discord/(all)
 *
 * AndroidForClaw adaptation: Discord channel runtime.
 */
package com.xiaomo.discord.messaging

import android.util.Log
import com.xiaomo.discord.DiscordClient

/**
 * Discord 表情反应管理
 * 参考 Feishu FeishuReactions.kt
 */
class DiscordReactions(private val client: DiscordClient) {
    companion object {
        private const val TAG = "DiscordReactions"

        // 常用表情
        const val EMOJI_THUMBS_UP = "👍"
        const val EMOJI_THUMBS_DOWN = "👎"
        const val EMOJI_HEART = "❤️"
        const val EMOJI_FIRE = "🔥"
        const val EMOJI_CHECK = "✅"
        const val EMOJI_CROSS = "❌"
        const val EMOJI_EYES = "👀"
        const val EMOJI_THINKING = "🤔"
    }

    /**
     * 添加反应
     */
    suspend fun add(
        channelId: String,
        messageId: String,
        emoji: String
    ): Result<Unit> {
        return try {
            val result = client.addReaction(channelId, messageId, emoji)
            if (result.isSuccess) {
                Log.d(TAG, "Reaction added: $emoji")
            } else {
                Log.w(TAG, "Failed to add reaction: ${result.exceptionOrNull()?.message}")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error adding reaction", e)
            Result.failure(e)
        }
    }

    /**
     * 移除反应
     */
    suspend fun remove(
        channelId: String,
        messageId: String,
        emoji: String
    ): Result<Unit> {
        return try {
            val result = client.removeReaction(channelId, messageId, emoji)
            if (result.isSuccess) {
                Log.d(TAG, "Reaction removed: $emoji")
            } else {
                Log.w(TAG, "Failed to remove reaction: ${result.exceptionOrNull()?.message}")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error removing reaction", e)
            Result.failure(e)
        }
    }

    /**
     * 添加多个反应
     */
    suspend fun addMultiple(
        channelId: String,
        messageId: String,
        emojis: List<String>
    ): Result<Unit> {
        return try {
            for (emoji in emojis) {
                add(channelId, messageId, emoji)
                // 避免速率限制
                kotlinx.coroutines.delay(250)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding multiple reactions", e)
            Result.failure(e)
        }
    }

    /**
     * 添加确认标记 (✅)
     */
    suspend fun addCheck(channelId: String, messageId: String): Result<Unit> {
        return add(channelId, messageId, EMOJI_CHECK)
    }

    /**
     * 添加错误标记 (❌)
     */
    suspend fun addCross(channelId: String, messageId: String): Result<Unit> {
        return add(channelId, messageId, EMOJI_CROSS)
    }

    /**
     * 添加思考标记 (🤔)
     */
    suspend fun addThinking(channelId: String, messageId: String): Result<Unit> {
        return add(channelId, messageId, EMOJI_THINKING)
    }

    /**
     * 添加关注标记 (👀)
     */
    suspend fun addEyes(channelId: String, messageId: String): Result<Unit> {
        return add(channelId, messageId, EMOJI_EYES)
    }
}

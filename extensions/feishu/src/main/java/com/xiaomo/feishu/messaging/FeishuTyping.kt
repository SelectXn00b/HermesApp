package com.xiaomo.feishu.messaging

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu messaging transport.
 */


import android.util.Log
import com.xiaomo.feishu.FeishuClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 飞书打字指示器
 * 对齐 OpenClaw typing.ts
 *
 * 通过添加/移除 Typing 表情来显示"正在输入"状态
 */
object FeishuTyping {
    private const val TAG = "FeishuTyping"

    // Feishu emoji for typing indicator
    private const val TYPING_EMOJI = "Typing"

    // Feishu API backoff error codes (rate limit, quota exceeded)
    private val BACKOFF_CODES = setOf(99991400, 99991403, 429)

    /**
     * 打字指示器状态
     */
    data class TypingIndicatorState(
        val messageId: String,
        val reactionId: String?
    )

    /**
     * Feishu backoff 异常（限流、配额超限）
     */
    class FeishuBackoffError(val code: Int) : Exception("Feishu API backoff: code $code")

    /**
     * 检查是否为 backoff 错误
     */
    private fun isBackoffError(exception: Exception): Boolean {
        return exception is FeishuBackoffError
    }

    /**
     * 从响应中检查 backoff 错误码
     */
    private fun getBackoffCodeFromResponse(response: Map<String, Any?>): Int? {
        val code = response["code"] as? Int
        return if (code != null && BACKOFF_CODES.contains(code)) code else null
    }

    /**
     * 添加打字指示器（Typing 表情）
     *
     * @param client Feishu 客户端
     * @param messageId 消息 ID
     * @return 打字指示器状态
     * @throws FeishuBackoffError 当遇到限流/配额错误时抛出
     */
    suspend fun addTypingIndicator(
        client: FeishuClient,
        messageId: String
    ): Result<TypingIndicatorState> = withContext(Dispatchers.IO) {
        try {
            val body = mapOf(
                "reaction_type" to mapOf(
                    "emoji_type" to TYPING_EMOJI
                )
            )

            val response = client.post("/open-apis/im/v1/messages/$messageId/reactions", body)

            if (response.isFailure) {
                val exception = response.exceptionOrNull()
                if (exception is FeishuBackoffError) {
                    Log.d(TAG, "Typing indicator hit backoff, stopping keepalive")
                    throw exception
                }
                // 其他错误静默失败（消息可能已删除、权限问题等）
                Log.d(TAG, "Failed to add typing indicator (non-critical): ${exception?.message}")
                return@withContext Result.success(TypingIndicatorState(messageId, null))
            }

            val data = response.getOrNull() as? Map<*, *>
            val dataMap = data?.get("data") as? Map<*, *>
            val reactionId = dataMap?.get("reaction_id") as? String

            Result.success(TypingIndicatorState(messageId, reactionId))

        } catch (e: Exception) {
            if (e is FeishuBackoffError) {
                throw e // 重新抛出 backoff 错误
            }
            Log.d(TAG, "Failed to add typing indicator: ${e.message}")
            Result.success(TypingIndicatorState(messageId, null))
        }
    }

    /**
     * 移除打字指示器
     *
     * @param client Feishu 客户端
     * @param state 打字指示器状态
     * @throws FeishuBackoffError 当遇到限流/配额错误时抛出
     */
    suspend fun removeTypingIndicator(
        client: FeishuClient,
        state: TypingIndicatorState
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (state.reactionId == null) {
            return@withContext Result.success(Unit)
        }

        try {
            val response = client.delete(
                "/open-apis/im/v1/messages/${state.messageId}/reactions/${state.reactionId}"
            )

            if (response.isFailure) {
                val exception = response.exceptionOrNull()
                if (exception is FeishuBackoffError) {
                    Log.d(TAG, "Typing indicator removal hit backoff")
                    throw exception
                }
                // 其他错误静默失败
                Log.d(TAG, "Failed to remove typing indicator (non-critical): ${exception?.message}")
            }

            Result.success(Unit)

        } catch (e: Exception) {
            if (e is FeishuBackoffError) {
                throw e
            }
            Log.d(TAG, "Failed to remove typing indicator: ${e.message}")
            Result.success(Unit)
        }
    }
}

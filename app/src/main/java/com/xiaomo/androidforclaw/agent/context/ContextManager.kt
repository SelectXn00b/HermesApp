package com.xiaomo.androidforclaw.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: manage context growth and recovery.
 */


import android.util.Log
import com.xiaomo.androidforclaw.providers.UnifiedLLMProvider
import com.xiaomo.androidforclaw.providers.LegacyMessage
import com.xiaomo.androidforclaw.providers.llm.Message

/**
 * Context Manager - Handle context overflow
 * Aligned with OpenClaw's multi-layer recovery strategy
 *
 * Strategy order (refer to OpenClaw run.ts lines 687-1055):
 * 1. Detect context overflow error
 * 2. Try Compaction (max 3 times)
 * 3. Try truncating oversized tool results
 * 4. Give up and return error
 */
class ContextManager(
    private val llmProvider: UnifiedLLMProvider
) {
    companion object {
        private const val TAG = "ContextManager"
        private const val MAX_COMPACTION_ATTEMPTS = 3
        private const val DEFAULT_CONTEXT_WINDOW = 128_000  // Aligned with ContextWindowGuard default
    }

    private val compactor = MessageCompactor(llmProvider)
    private var compactionAttempts = 0
    private var toolResultTruncationAttempted = false

    /**
     * Reset counters (called at the start of a new run)
     */
    fun reset() {
        compactionAttempts = 0
        toolResultTruncationAttempted = false
        Log.d(TAG, "Context manager reset")
    }

    /**
     * Handle context overflow error
     * @return Returns fixed message list if recoverable, otherwise returns null
     */
    suspend fun handleContextOverflow(
        error: Throwable,
        messages: List<Message>,
        contextWindow: Int = DEFAULT_CONTEXT_WINDOW
    ): ContextRecoveryResult {
        val errorMessage = ContextErrors.extractErrorMessage(error)

        Log.e(TAG, "检测到上下文超限: $errorMessage")
        Log.d(TAG, "当前消息数: ${messages.size}")
        Log.d(TAG, "已尝试 compaction: $compactionAttempts 次")
        Log.d(TAG, "已尝试截断: $toolResultTruncationAttempted")

        // 1. Confirm it's a context overflow error
        if (!ContextErrors.isLikelyContextOverflowError(errorMessage)) {
            Log.w(TAG, "Not a context overflow error, cannot handle")
            return ContextRecoveryResult.CannotRecover(errorMessage)
        }

        // 2. Try Compaction
        if (compactionAttempts < MAX_COMPACTION_ATTEMPTS) {
            Log.d(TAG, "Trying Compaction (attempt ${compactionAttempts + 1})...")

            // Convert Message to LegacyMessage
            val legacyMessages = convertToLegacyMessages(messages)

            val compactionResult = compactor.compactMessages(
                messages = legacyMessages,
                keepLastN = 3
            )

            if (compactionResult.isSuccess) {
                compactionAttempts++
                val compacted = compactionResult.getOrNull()!!

                Log.d(TAG, "Compaction 成功: ${legacyMessages.size} -> ${compacted.size} 条消息")

                // 转换回 Message
                val newMessages = convertFromLegacyMessages(compacted)

                return ContextRecoveryResult.Recovered(
                    messages = newMessages,
                    strategy = "compaction",
                    attempt = compactionAttempts
                )
            } else {
                Log.e(TAG, "Compaction 失败: ${compactionResult.exceptionOrNull()?.message}")

                // Compaction failed, check if it's due to compaction itself causing overflow
                if (ContextErrors.isCompactionFailureError(errorMessage)) {
                    Log.e(TAG, "Compaction itself failed, cannot recover")
                    return ContextRecoveryResult.CannotRecover(
                        "Compaction failed: context overflow during summarization. " +
                        "Try /reset or use a larger-context model."
                    )
                }
            }
        }

        // 3. Try truncating oversized tool results
        if (!toolResultTruncationAttempted) {
            Log.d(TAG, "Trying to truncate oversized tool results...")

            val legacyMessages = convertToLegacyMessages(messages)

            if (ToolResultTruncator.hasOversizedToolResults(legacyMessages)) {
                toolResultTruncationAttempted = true

                val truncated = ToolResultTruncator.truncateToolResults(legacyMessages)
                val newMessages = convertFromLegacyMessages(truncated)

                Log.d(TAG, "工具结果截断完成")

                return ContextRecoveryResult.Recovered(
                    messages = newMessages,
                    strategy = "truncation",
                    attempt = 1
                )
            } else {
                Log.d(TAG, "没有检测到超大工具结果")
            }
        }

        // 4. Give up
        Log.e(TAG, "All recovery strategies failed")
        return ContextRecoveryResult.CannotRecover(
            "Context overflow: prompt too large for the model. " +
            "Compaction attempts: $compactionAttempts. " +
            "Try clearing session history or use a larger-context model."
        )
    }

    /**
     * Check if preemptive compaction should be performed
     */
    fun shouldPreemptivelyCompact(
        messages: List<Message>,
        contextWindow: Int = DEFAULT_CONTEXT_WINDOW
    ): Boolean {
        val legacyMessages = convertToLegacyMessages(messages)
        return compactor.shouldCompact(legacyMessages, contextWindow)
    }

    /**
     * Preemptive compaction (before sending request)
     */
    suspend fun preemptivelyCompact(
        messages: List<Message>
    ): List<Message> {
        Log.d(TAG, "预防性压缩...")

        val legacyMessages = convertToLegacyMessages(messages)
        val result = compactor.compactMessages(legacyMessages, keepLastN = 5)

        return if (result.isSuccess) {
            Log.d(TAG, "预防性压缩成功")
            convertFromLegacyMessages(result.getOrNull()!!)
        } else {
            Log.w(TAG, "预防性压缩失败，使用原消息")
            messages
        }
    }

    /**
     * Convert new format to legacy format (for compactor)
     */
    private fun convertToLegacyMessages(messages: List<Message>): List<LegacyMessage> {
        return messages.map { msg ->
            when (msg.role) {
                "system", "user" -> LegacyMessage(msg.role, msg.content)
                "assistant" -> {
                    if (msg.toolCalls != null) {
                        LegacyMessage(
                            role = "assistant",
                            content = msg.content,
                            toolCalls = msg.toolCalls.map { tc ->
                                com.xiaomo.androidforclaw.providers.LegacyToolCall(
                                    id = tc.id,
                                    type = "function",
                                    function = com.xiaomo.androidforclaw.providers.LegacyFunction(
                                        name = tc.name,
                                        arguments = tc.arguments
                                    )
                                )
                            }
                        )
                    } else {
                        LegacyMessage("assistant", msg.content)
                    }
                }
                "tool" -> LegacyMessage(
                    role = "tool",
                    content = msg.content,
                    toolCallId = msg.toolCallId,
                    name = msg.name
                )
                else -> LegacyMessage(msg.role, msg.content)
            }
        }
    }

    /**
     * Convert legacy format to new format
     */
    private fun convertFromLegacyMessages(legacyMessages: List<LegacyMessage>): List<Message> {
        return legacyMessages.map { msg ->
            when (msg.role) {
                "system" -> Message(
                    role = "system",
                    content = msg.content?.toString() ?: ""
                )
                "user" -> Message(
                    role = "user",
                    content = msg.content?.toString() ?: ""
                )
                "assistant" -> {
                    if (msg.toolCalls != null) {
                        Message(
                            role = "assistant",
                            content = msg.content?.toString() ?: "",
                            toolCalls = msg.toolCalls.map { tc ->
                                com.xiaomo.androidforclaw.providers.llm.ToolCall(
                                    id = tc.id,
                                    name = tc.function.name,
                                    arguments = tc.function.arguments
                                )
                            }
                        )
                    } else {
                        Message(
                            role = "assistant",
                            content = msg.content?.toString() ?: ""
                        )
                    }
                }
                "tool" -> Message(
                    role = "tool",
                    content = msg.content?.toString() ?: "",
                    toolCallId = msg.toolCallId,
                    name = msg.name
                )
                else -> Message(
                    role = msg.role,
                    content = msg.content?.toString() ?: ""
                )
            }
        }
    }
}

/**
 * Context Recovery Result
 */
sealed class ContextRecoveryResult {
    /**
     * Successfully recovered
     */
    data class Recovered(
        val messages: List<Message>,
        val strategy: String,  // "compaction" or "truncation"
        val attempt: Int
    ) : ContextRecoveryResult()

    /**
     * Cannot recover
     */
    data class CannotRecover(
        val reason: String
    ) : ContextRecoveryResult()
}

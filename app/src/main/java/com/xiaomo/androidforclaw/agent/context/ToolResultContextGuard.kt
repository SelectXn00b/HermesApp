package com.xiaomo.androidforclaw.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/pi-embedded-runner/tool-result-context-guard.ts
 *
 * AndroidForClaw adaptation: bound tool result size within context limits.
 *
 * Upstream commit a42ee69ad4:
 * - Removed CONTEXT_INPUT_HEADROOM_RATIO (0.75)
 * - Uses PREEMPTIVE_OVERFLOW_RATIO (0.9) directly
 * - Simplified threshold: exceedsPreemptiveOverflowThreshold()
 * - "Context overflow: estimated context size exceeds safe threshold..."
 */

import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.providers.llm.Message

/**
 * Tool Result Context Guard — Enforce context budget for tool results.
 *
 * Upstream: tool-result-context-guard.ts
 * - CHARS_PER_TOKEN_ESTIMATE = 4
 * - TOOL_RESULT_CHARS_PER_TOKEN_ESTIMATE = 2
 * - SINGLE_TOOL_RESULT_CONTEXT_SHARE = 0.5
 * - PREEMPTIVE_OVERFLOW_RATIO = 0.9
 */
object ToolResultContextGuard {
    private const val TAG = "ToolResultContextGuard"

    // Aligned with OpenClaw tool-result-char-estimator.ts
    const val CHARS_PER_TOKEN_ESTIMATE = 4
    const val TOOL_RESULT_CHARS_PER_TOKEN_ESTIMATE = 2
    const val IMAGE_CHAR_ESTIMATE = 8_000

    // Aligned with OpenClaw tool-result-context-guard.ts (upstream a42ee69ad4)
    const val SINGLE_TOOL_RESULT_CONTEXT_SHARE = 0.5
    const val PREEMPTIVE_OVERFLOW_RATIO = 0.9

    const val CONTEXT_LIMIT_TRUNCATION_NOTICE = "more characters truncated"
    const val PREEMPTIVE_COMPACTION_PLACEHOLDER = "[compacted: tool output removed to free context]"
    const val PREEMPTIVE_CONTEXT_OVERFLOW_MESSAGE =
        "Context overflow: estimated context size exceeds safe threshold during tool loop."

    private const val MIN_BUDGET_CHARS = 1_024

    /**
     * Format truncation notice suffix.
     * Upstream: formatContextLimitTruncationNotice()
     */
    fun formatContextLimitTruncationNotice(truncatedChars: Int): String {
        return "[... ${maxOf(1, truncatedChars)} $CONTEXT_LIMIT_TRUNCATION_NOTICE]"
    }

    /**
     * Check if estimated context exceeds preemptive overflow threshold.
     * Upstream: exceedsPreemptiveOverflowThreshold()
     *
     * @param estimatedContextChars Estimated total context characters
     * @param contextWindowTokens Context window size in tokens
     * @return true if overflow threshold exceeded
     */
    fun exceedsPreemptiveOverflowThreshold(
        estimatedContextChars: Int,
        contextWindowTokens: Int
    ): Boolean {
        val maxContextChars = contextWindowTokens * CHARS_PER_TOKEN_ESTIMATE
        return estimatedContextChars > maxContextChars * PREEMPTIVE_OVERFLOW_RATIO
    }

    /**
     * Enforce tool result context budget on a mutable list of messages.
     *
     * @param messages The message list (will be modified in-place)
     * @param contextWindowTokens The model's context window in tokens
     * @return The same list, modified
     */
    fun enforceContextBudget(
        messages: MutableList<Message>,
        contextWindowTokens: Int
    ): MutableList<Message> {
        val contextBudgetChars = maxOf(
            MIN_BUDGET_CHARS,
            (contextWindowTokens * CHARS_PER_TOKEN_ESTIMATE * PREEMPTIVE_OVERFLOW_RATIO).toInt()
        )
        val maxSingleToolResultChars = maxOf(
            MIN_BUDGET_CHARS,
            (contextWindowTokens * TOOL_RESULT_CHARS_PER_TOKEN_ESTIMATE * SINGLE_TOOL_RESULT_CONTEXT_SHARE).toInt()
        )

        Log.d(TAG, "Context budget: $contextBudgetChars chars, single tool max: $maxSingleToolResultChars chars")

        // Step 0: Hard max — truncate any tool result exceeding ToolResultTruncator limit
        val hardMaxChars = ToolResultTruncator.DEFAULT_MAX_LIVE_TOOL_RESULT_CHARS
        for (i in messages.indices) {
            val msg = messages[i]
            if (!isToolResultMessage(msg)) continue
            val contentStr = msg.content ?: continue
            if (contentStr.length > hardMaxChars) {
                messages[i] = msg.copy(
                    content = ToolResultTruncator.truncateText(contentStr, hardMaxChars)
                )
                Log.d(TAG, "Hard-max truncated tool result ${msg.name ?: msg.toolCallId}: ${contentStr.length} -> $hardMaxChars chars")
            }
        }

        // Step 1: Truncate individual oversized tool results (relative to context share)
        for (i in messages.indices) {
            val msg = messages[i]
            if (!isToolResultMessage(msg)) continue

            val contentStr = msg.content ?: continue
            val estimatedChars = estimateMessageChars(msg)

            if (estimatedChars > maxSingleToolResultChars) {
                val truncated = ToolResultTruncator.truncateText(contentStr, maxSingleToolResultChars)
                messages[i] = msg.copy(content = truncated)
                Log.d(TAG, "Truncated tool result ${msg.name ?: msg.toolCallId}: $estimatedChars -> ${truncated.length} chars")
            }
        }

        // Step 2: Check total context and compact oldest tool results if over budget
        var currentChars = estimateContextChars(messages)
        if (currentChars <= contextBudgetChars) {
            Log.d(TAG, "Context within budget: $currentChars / $contextBudgetChars chars")
            return messages
        }

        Log.d(TAG, PREEMPTIVE_CONTEXT_OVERFLOW_MESSAGE)

        val charsNeeded = currentChars - contextBudgetChars
        var reduced = 0

        // Compact from oldest to newest
        for (i in messages.indices) {
            if (reduced >= charsNeeded) break

            val msg = messages[i]
            if (!isToolResultMessage(msg)) continue

            val before = estimateMessageChars(msg)
            if (before <= PREEMPTIVE_COMPACTION_PLACEHOLDER.length) continue

            messages[i] = msg.copy(content = PREEMPTIVE_COMPACTION_PLACEHOLDER)
            val after = PREEMPTIVE_COMPACTION_PLACEHOLDER.length
            reduced += before - after

            Log.d(TAG, "Compacted tool result ${msg.name ?: msg.toolCallId}: $before -> $after chars (freed ${before - after})")
        }

        currentChars = estimateContextChars(messages)
        Log.d(TAG, "After compaction: $currentChars / $contextBudgetChars chars (reduced $reduced)")

        return messages
    }

    /**
     * Check if a message is a tool result.
     */
    private fun isToolResultMessage(msg: Message): Boolean {
        return msg.role == "tool"
    }

    /**
     * Estimate character count for a single message.
     * Aligned with OpenClaw tool-result-char-estimator.ts
     */
    fun estimateMessageChars(msg: Message): Int {
        return when (msg.role) {
            "tool" -> {
                val contentChars = msg.content?.length ?: 0
                // Tool results use TOOL_RESULT_CHARS_PER_TOKEN_ESTIMATE (2 chars/token)
                // Weight them for comparison with regular text (4 chars/token)
                val weightedChars = (contentChars * CHARS_PER_TOKEN_ESTIMATE / TOOL_RESULT_CHARS_PER_TOKEN_ESTIMATE)
                maxOf(contentChars, weightedChars)
            }
            "user" -> msg.content?.length ?: 0
            "assistant" -> {
                var chars = msg.content?.length ?: 0
                msg.toolCalls?.forEach { tc ->
                    chars += (tc.arguments?.length ?: 0) + (tc.name?.length ?: 0) + 20
                }
                chars
            }
            "system" -> msg.content?.length ?: 0
            else -> 256
        }
    }

    /**
     * Estimate total context chars for all messages.
     */
    fun estimateContextChars(messages: List<Message>): Int {
        return messages.sumOf { estimateMessageChars(it) }
    }
}

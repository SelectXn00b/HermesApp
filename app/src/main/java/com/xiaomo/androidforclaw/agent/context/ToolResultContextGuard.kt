package com.xiaomo.androidforclaw.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: bound tool result size within context limits.
 */


import android.util.Log
import com.xiaomo.androidforclaw.providers.llm.Message

/**
 * Tool Result Context Guard — Gap 1 alignment with OpenClaw tool-result-context-guard.ts
 *
 * Dynamically enforces context budget by:
 * 1. Truncating individual oversized tool results
 * 2. Compacting oldest tool results when total context exceeds budget
 *
 * Constants aligned with OpenClaw:
 * - CHARS_PER_TOKEN_ESTIMATE = 4
 * - TOOL_RESULT_CHARS_PER_TOKEN_ESTIMATE = 2
 * - CONTEXT_INPUT_HEADROOM_RATIO = 0.75
 * - SINGLE_TOOL_RESULT_CONTEXT_SHARE = 0.5
 */
object ToolResultContextGuard {
    private const val TAG = "ToolResultContextGuard"

    // Aligned with OpenClaw tool-result-char-estimator.ts
    const val CHARS_PER_TOKEN_ESTIMATE = 4
    const val TOOL_RESULT_CHARS_PER_TOKEN_ESTIMATE = 2
    const val IMAGE_CHAR_ESTIMATE = 8_000

    // Aligned with OpenClaw tool-result-context-guard.ts
    const val CONTEXT_INPUT_HEADROOM_RATIO = 0.75
    const val SINGLE_TOOL_RESULT_CONTEXT_SHARE = 0.5

    const val CONTEXT_LIMIT_TRUNCATION_NOTICE = "[truncated: output exceeded context limit]"
    const val PREEMPTIVE_COMPACTION_PLACEHOLDER = "[compacted: tool output removed to free context]"

    private const val MIN_BUDGET_CHARS = 1_024

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
            (contextWindowTokens * CHARS_PER_TOKEN_ESTIMATE * CONTEXT_INPUT_HEADROOM_RATIO).toInt()
        )
        val maxSingleToolResultChars = maxOf(
            MIN_BUDGET_CHARS,
            (contextWindowTokens * TOOL_RESULT_CHARS_PER_TOKEN_ESTIMATE * SINGLE_TOOL_RESULT_CONTEXT_SHARE).toInt()
        )

        Log.d(TAG, "Context budget: $contextBudgetChars chars, single tool max: $maxSingleToolResultChars chars")

        // Step 1: Truncate individual oversized tool results
        for (i in messages.indices) {
            val msg = messages[i]
            if (!isToolResultMessage(msg)) continue

            val contentStr = msg.content ?: continue
            val estimatedChars = estimateMessageChars(msg)

            if (estimatedChars > maxSingleToolResultChars) {
                val truncated = truncateTextToBudget(contentStr, maxSingleToolResultChars)
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

        Log.d(TAG, "Context over budget: $currentChars / $contextBudgetChars chars, compacting old tool results...")

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
                // but we need to weight them for comparison with regular text (4 chars/token)
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

    /**
     * Truncate text to a budget, trying to break at newline boundary.
     * Aligned with OpenClaw truncateTextToBudget.
     */
    private fun truncateTextToBudget(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        if (maxChars <= 0) return CONTEXT_LIMIT_TRUNCATION_NOTICE

        val suffix = "\n$CONTEXT_LIMIT_TRUNCATION_NOTICE"
        val bodyBudget = maxOf(0, maxChars - suffix.length)
        if (bodyBudget <= 0) return CONTEXT_LIMIT_TRUNCATION_NOTICE

        // Try to break at a newline for cleaner output
        var cutPoint = bodyBudget
        val newline = text.lastIndexOf('\n', bodyBudget)
        if (newline > bodyBudget * 0.7) {
            cutPoint = newline
        }

        return text.substring(0, cutPoint) + suffix
    }
}

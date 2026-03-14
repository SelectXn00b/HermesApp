package com.xiaomo.androidforclaw.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: truncate tool outputs before prompt injection.
 */


import android.util.Log
import com.xiaomo.androidforclaw.providers.LegacyMessage

/**
 * Tool Result Truncator
 * Aligned with OpenClaw's tool-result-truncation.ts implementation
 */
object ToolResultTruncator {
    private const val TAG = "ToolResultTruncator"

    // Configuration parameters
    private const val MAX_TOOL_RESULT_CHARS = 4000  // Max characters for a single tool result
    private const val HEAD_CHARS = 1500  // Keep head characters count
    private const val TAIL_CHARS = 1500  // Keep tail characters count
    private const val PLACEHOLDER = "\n\n... [truncated] ...\n\n"

    /**
     * Truncate oversized tool results in message list
     */
    fun truncateToolResults(messages: List<LegacyMessage>): List<LegacyMessage> {
        var truncatedCount = 0

        val result = messages.map { msg ->
            if (msg.role == "tool" && msg.content != null) {
                val content = msg.content.toString()
                if (content.length > MAX_TOOL_RESULT_CHARS) {
                    truncatedCount++
                    val truncated = truncateContent(content)
                    msg.copy(content = truncated)
                } else {
                    msg
                }
            } else {
                msg
            }
        }

        if (truncatedCount > 0) {
            Log.d(TAG, "截断了 $truncatedCount 个超大工具结果")
        }

        return result
    }

    /**
     * Truncate single content
     * Keep head and tail, replace middle with placeholder
     */
    private fun truncateContent(content: String): String {
        if (content.length <= MAX_TOOL_RESULT_CHARS) {
            return content
        }

        val head = content.take(HEAD_CHARS)
        val tail = content.takeLast(TAIL_CHARS)

        val originalSize = content.length
        val truncatedSize = head.length + PLACEHOLDER.length + tail.length

        return buildString {
            append(head)
            append(PLACEHOLDER)
            append("[Original: $originalSize chars, Truncated to: $truncatedSize chars]")
            append(PLACEHOLDER)
            append(tail)
        }
    }

    /**
     * Detect if there are oversized tool results in session
     */
    fun hasOversizedToolResults(messages: List<LegacyMessage>): Boolean {
        return messages.any { msg ->
            msg.role == "tool" &&
            msg.content != null &&
            msg.content.toString().length > MAX_TOOL_RESULT_CHARS
        }
    }

    /**
     * Calculate total size of tool results
     */
    fun calculateToolResultSize(messages: List<LegacyMessage>): Int {
        return messages
            .filter { it.role == "tool" }
            .sumOf { it.content?.toString()?.length ?: 0 }
    }
}

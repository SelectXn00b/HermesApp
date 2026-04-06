package com.xiaomo.androidforclaw.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/pi-embedded-runner/tool-result-truncation.ts
 *
 * AndroidForClaw adaptation: tool result truncation with overflow recovery.
 *
 * Upstream commit series: 7fc1a74ee9..a42ee69ad4
 * - Context-window-aware budget (not hardcoded)
 * - Aggregate tool-result budget
 * - Recovery-specific truncation (min-keep=0)
 * - Important-tail detection (errors, JSON closing)
 * - Suffix factory (shows truncated chars)
 */

import com.xiaomo.androidforclaw.logging.Log

/**
 * Truncates oversized tool results to fit within context budget.
 * Aligned with upstream tool-result-truncation.ts.
 */
object ToolResultTruncator {
    private const val TAG = "ToolResultTruncator"

    /**
     * Share of context window a single tool result should not exceed.
     * Upstream: MAX_TOOL_RESULT_CONTEXT_SHARE = 0.3
     */
    private const val MAX_TOOL_RESULT_CONTEXT_SHARE = 0.3

    /**
     * Default hard cap for a single live tool result.
     * Upstream: DEFAULT_MAX_LIVE_TOOL_RESULT_CHARS = 40_000
     */
    const val DEFAULT_MAX_LIVE_TOOL_RESULT_CHARS = 40_000

    /**
     * Minimum chars to keep when truncating.
     * Upstream: MIN_KEEP_CHARS = 2_000
     */
    private const val MIN_KEEP_CHARS = 2_000

    /**
     * Recovery min-keep: when truncating during overflow recovery,
     * we can truncate more aggressively (even to 0).
     * Upstream: RECOVERY_MIN_KEEP_CHARS = 0
     */
    private const val RECOVERY_MIN_KEEP_CHARS = 0

    /**
     * Marker between head and tail when using head+tail truncation.
     */
    private const val MIDDLE_OMISSION_MARKER =
        "\n\n⚠️ [... middle content omitted — showing head and tail ...]\n\n"

    /**
     * Calculate the maximum allowed chars for a single tool result
     * based on the model's context window tokens.
     *
     * Uses rough 4 chars ≈ 1 token heuristic.
     * Upstream: calculateMaxToolResultChars()
     *
     * @param contextWindowTokens Number of tokens in the context window
     * @return Maximum chars for a single tool result
     */
    fun calculateMaxToolResultChars(contextWindowTokens: Int): Int {
        val maxTokens = (contextWindowTokens * MAX_TOOL_RESULT_CONTEXT_SHARE).toInt()
        // Rough conversion: ~4 chars per token
        val maxChars = maxTokens * 4
        return minOf(maxChars, DEFAULT_MAX_LIVE_TOOL_RESULT_CHARS)
    }

    /**
     * Build the truncation suffix.
     * Upstream: formatContextLimitTruncationNotice()
     *
     * @param truncatedChars Number of characters truncated
     * @return Suffix string to append
     */
    fun buildSuffix(truncatedChars: Int): String {
        return "[... ${maxOf(1, truncatedChars)} more characters truncated]"
    }

    /**
     * Detect whether text likely contains error/diagnostic content near the end.
     * Upstream: hasImportantTail()
     *
     * Errors, JSON closing brackets, and summary lines often appear at the end
     * and should be preserved during truncation.
     *
     * @param text The text to check
     * @return true if the tail looks important
     */
    fun hasImportantTail(text: String): Boolean {
        // Check last ~2000 chars for error-like patterns
        val tail = text.takeLast(2000).lowercase()
        return tail.contains(Regex("\\b(error|exception|failed|fatal|traceback|panic|stack trace|errno|exit code)\\b")) ||
            tail.trimEnd().endsWith("}") ||
            tail.contains(Regex("\\b(total|summary|result|complete|finished|done)\\b"))
    }

    /**
     * Truncate a single text string to fit within maxChars.
     *
     * Uses a head+tail strategy when the tail contains important content
     * (errors, results, JSON structure), otherwise preserves the beginning.
     * Upstream: truncateToolResultText()
     *
     * @param text The text to truncate
     * @param maxChars Maximum characters to keep
     * @param minKeepChars Minimum characters to keep (default: MIN_KEEP_CHARS)
     * @return Truncated text with suffix
     */
    fun truncateText(
        text: String,
        maxChars: Int,
        minKeepChars: Int = MIN_KEEP_CHARS
    ): String {
        if (text.length <= maxChars) return text

        val suffix = buildSuffix(maxOf(1, text.length - maxChars))
        val budget = maxOf(minKeepChars, maxChars - suffix.length)

        // If tail looks important, split budget between head and tail
        if (hasImportantTail(text) && budget > minKeepChars * 2) {
            val tailBudget = minOf((budget * 0.3).toInt(), 4_000)
            val headBudget = budget - tailBudget - MIDDLE_OMISSION_MARKER.length

            if (headBudget > minKeepChars) {
                // Find clean cut points at newline boundaries
                var headCut = headBudget
                val headNewline = text.lastIndexOf('\n', headBudget)
                if (headNewline > headBudget * 0.8) {
                    headCut = headNewline
                }

                var tailStart = text.length - tailBudget
                val tailNewline = text.indexOf('\n', tailStart)
                if (tailNewline != -1 && tailNewline < tailStart + tailBudget * 0.2) {
                    tailStart = tailNewline + 1
                }

                val keptText = text.substring(0, headCut) + MIDDLE_OMISSION_MARKER + text.substring(tailStart)
                val finalSuffix = buildSuffix(maxOf(1, text.length - keptText.length))
                return keptText + finalSuffix
            }
        }

        // Default: keep the beginning
        var cutPoint = budget
        val lastNewline = text.lastIndexOf('\n', budget)
        if (lastNewline > budget * 0.8) {
            cutPoint = lastNewline
        }
        val keptText = text.substring(0, cutPoint)
        return keptText + suffix
    }

    /**
     * Truncate tool result for overflow recovery.
     * Uses RECOVERY_MIN_KEEP_CHARS (0) for aggressive truncation.
     * Upstream: recovery-specific truncation path
     *
     * @param text The text to truncate
     * @param maxChars Maximum characters to keep
     * @return Truncated text
     */
    fun truncateForRecovery(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        return truncateText(text, maxChars, minKeepChars = RECOVERY_MIN_KEEP_CHARS)
    }

    /**
     * Truncate a tool result with context window awareness.
     * Main entry point for tool result truncation.
     *
     * @param text Tool result text
     * @param contextWindowTokens Context window size in tokens
     * @return Truncated text (or original if within budget)
     */
    fun truncateForContext(text: String, contextWindowTokens: Int): String {
        val maxChars = calculateMaxToolResultChars(contextWindowTokens)
        return truncateText(text, maxChars)
    }

    /**
     * Calculate aggregate budget across all tool results.
     * Upstream: calculateRecoveryAggregateToolResultChars()
     *
     * @param contextWindowTokens Context window size in tokens
     * @return Aggregate budget in characters
     */
    fun calculateAggregateBudget(contextWindowTokens: Int): Int {
        return maxOf(
            calculateMaxToolResultChars(contextWindowTokens),
            RECOVERY_MIN_KEEP_CHARS + buildSuffix(1).length
        )
    }

    /**
     * Apply aggregate truncation across multiple tool results.
     * Upstream: buildAggregateToolResultReplacements()
     *
     * @param results List of (toolCallId, resultText) pairs
     * @param contextWindowTokens Context window size in tokens
     * @return List of (toolCallId, truncatedText) pairs
     */
    fun truncateAggregate(
        results: List<Pair<String, String>>,
        contextWindowTokens: Int
    ): List<Pair<String, String>> {
        val aggregateBudget = calculateAggregateBudget(contextWindowTokens)
        val totalChars = results.sumOf { it.second.length }

        if (totalChars <= aggregateBudget) return results
        if (results.size < 2) return results

        // Sort by size (largest first) for reduction
        val sorted = results.mapIndexed { idx, pair -> Triple(idx, pair.first, pair.second) }
            .sortedByDescending { it.third.length }

        var remainingReduction = totalChars - aggregateBudget
        val truncated = mutableMapOf<Int, String>()

        for ((origIdx, _, text) in sorted) {
            if (remainingReduction <= 0) break

            val minTruncatedText = RECOVERY_MIN_KEEP_CHARS + buildSuffix(1).length
            val reducibleChars = maxOf(0, text.length - minTruncatedText)
            if (reducibleChars <= 0) continue

            val requestedReduction = minOf(reducibleChars, remainingReduction)
            val targetChars = maxOf(minTruncatedText, text.length - requestedReduction)
            val truncatedText = truncateForRecovery(text, targetChars)
            val actualReduction = maxOf(0, text.length - truncatedText.length)

            if (actualReduction > 0) {
                truncated[origIdx] = truncatedText
                remainingReduction -= actualReduction
            }
        }

        return results.mapIndexed { idx, pair ->
            if (truncated.containsKey(idx)) {
                pair.first to truncated[idx]!!
            } else {
                pair
            }
        }
    }

    // ===== Legacy API (backwards compatible) =====

    /** @deprecated Use calculateMaxToolResultChars(contextWindowTokens) instead */
    @Deprecated("Use context-window-aware version", replaceWith = ReplaceWith("calculateMaxToolResultChars(contextWindowTokens)"))
    const val MAX_TOOL_RESULT_CHARS = 8_000

    /** @deprecated Use truncateForContext() instead */
    @Deprecated("Use context-window-aware version")
    fun truncate(text: String, maxChars: Int = MAX_TOOL_RESULT_CHARS): String {
        return truncateText(text, maxChars)
    }

    /**
     * Check if any tool result messages are oversized.
     * Legacy API used by ContextManager (LegacyMessage).
     */
    fun hasOversizedToolResults(messages: List<com.xiaomo.androidforclaw.providers.LegacyMessage>): Boolean {
        val maxChars = MAX_TOOL_RESULT_CHARS
        return messages.any { msg ->
            msg.role == "tool" && getContentLength(msg) > maxChars
        }
    }

    /**
     * Truncate all oversized tool results in a message list.
     * Legacy API used by ContextManager (LegacyMessage).
     */
    fun truncateToolResults(
        messages: List<com.xiaomo.androidforclaw.providers.LegacyMessage>
    ): List<com.xiaomo.androidforclaw.providers.LegacyMessage> {
        val maxChars = MAX_TOOL_RESULT_CHARS
        return messages.map { msg ->
            if (msg.role == "tool" && getContentLength(msg) > maxChars) {
                val text = getTextContent(msg)
                msg.copy(content = truncateText(text, maxChars))
            } else {
                msg
            }
        }
    }

    private fun getContentLength(msg: com.xiaomo.androidforclaw.providers.LegacyMessage): Int {
        return when (val content = msg.content) {
            is String -> content.length
            is List<*> -> content.filterIsInstance<com.xiaomo.androidforclaw.providers.ContentBlock>()
                .sumOf { (it.text?.length ?: 0) + (it.imageUrl?.url?.length ?: 0) }
            else -> 0
        }
    }

    private fun getTextContent(msg: com.xiaomo.androidforclaw.providers.LegacyMessage): String {
        return when (val content = msg.content) {
            is String -> content
            is List<*> -> content.filterIsInstance<com.xiaomo.androidforclaw.providers.ContentBlock>()
                .joinToString("\n") { it.text ?: "" }
            else -> ""
        }
    }
}

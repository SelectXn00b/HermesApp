package com.xiaomo.androidforclaw.agent.memory

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: compress message history to fit context windows.
 */


import android.util.Log
import com.xiaomo.androidforclaw.providers.LegacyMessage
import com.xiaomo.androidforclaw.providers.LegacyRepository

/**
 * Context Compressor
 * Aligned with OpenClaw src/agents/compaction.ts
 *
 * Features:
 * - Detect if compaction is needed
 * - Generate history message summary
 * - Identifier preservation policy
 */
class ContextCompressor(
    private val legacyRepository: LegacyRepository,
    private val config: CompactionConfig = CompactionConfig()
) {
    companion object {
        private const val TAG = "ContextCompressor"

        // Safety margin: 1.2x (compensate for token estimation inaccuracy)
        private const val SAFETY_MARGIN = 1.2

        // Summarization overhead
        private const val SUMMARIZATION_OVERHEAD_TOKENS = 4096
    }

    /**
     * Compaction Configuration
     */
    data class CompactionConfig(
        val mode: CompactionMode = CompactionMode.SAFEGUARD,
        val contextWindowTokens: Int = 200_000,        // Claude Opus 4.6 default
        val reserveTokensFloor: Int = 20_000,          // Minimum tokens forcibly reserved
        val softThresholdTokens: Int = 4_000,          // Soft threshold
        val keepRecentTokens: Int = 10_000,            // Budget for keeping recent content
        val maxHistoryShare: Double = 0.5,             // Maximum history share
        val identifierPolicy: IdentifierPolicy = IdentifierPolicy.STRICT
    )

    /**
     * Compaction Mode
     */
    enum class CompactionMode {
        SAFEGUARD,  // Conservative mode
        DEFAULT     // Default mode
    }

    /**
     * Identifier Preservation Policy
     */
    enum class IdentifierPolicy {
        STRICT,     // Preserve all identifiers
        OFF,        // Don't preserve
        CUSTOM      // Custom (TODO)
    }

    /**
     * Compaction threshold
     */
    private val compactionThreshold: Int
        get() = config.contextWindowTokens - config.reserveTokensFloor - config.softThresholdTokens

    /**
     * Check if compaction is needed
     *
     * @param messages Current message list
     * @return Whether compaction is needed
     */
    fun needsCompaction(messages: List<LegacyMessage>): Boolean {
        val totalTokens = TokenEstimator.estimateMessagesTokens(messages)
        val threshold = compactionThreshold

        Log.d(TAG, "Token check: $totalTokens / $threshold (threshold)")

        return totalTokens >= threshold
    }

    /**
     * Compact message history
     *
     * @param messages Original message list
     * @return Compacted message list
     */
    suspend fun compress(messages: List<LegacyMessage>): List<LegacyMessage> {
        if (messages.size <= 3) {
            Log.d(TAG, "Not enough messages to compress (${messages.size})")
            return messages
        }

        try {
            // 1. Separate system messages and history messages
            val systemMessages = messages.filter { it.role == "system" }
            val historyMessages = messages.filter { it.role != "system" }

            if (historyMessages.size <= 2) {
                return messages
            }

            // 2. Calculate count of recent messages to keep
            val recentTokenBudget = config.keepRecentTokens
            var recentCount = 0
            var recentTokens = 0

            for (i in historyMessages.indices.reversed()) {
                val msgTokens = TokenEstimator.estimateMessageTokens(historyMessages[i])
                if (recentTokens + msgTokens > recentTokenBudget) {
                    break
                }
                recentTokens += msgTokens
                recentCount++
            }

            // Keep at least last 2 messages
            recentCount = recentCount.coerceAtLeast(2)

            // 3. Split messages: to compress + recent messages to keep
            val toCompress = historyMessages.dropLast(recentCount)
            val toKeep = historyMessages.takeLast(recentCount)

            if (toCompress.isEmpty()) {
                Log.d(TAG, "No messages to compress")
                return messages
            }

            Log.d(TAG, "Compressing ${toCompress.size} messages, keeping ${toKeep.size} recent")

            // 4. Generate summary
            val summary = generateSummary(toCompress)

            // 5. Build compacted message list
            val compressedMessages = mutableListOf<LegacyMessage>()

            // Add system messages
            compressedMessages.addAll(systemMessages)

            // Add summary message
            compressedMessages.add(
                LegacyMessage(
                    role = "assistant",
                    content = """
                    [COMPACTED HISTORY]

                    This is a summary of ${toCompress.size} earlier messages in this conversation:

                    $summary

                    [END COMPACTED HISTORY]
                    """.trimIndent()
                )
            )

            // Add kept recent messages
            compressedMessages.addAll(toKeep)

            val originalTokens = TokenEstimator.estimateMessagesTokens(messages)
            val compressedTokens = TokenEstimator.estimateMessagesTokens(compressedMessages)
            val savedTokens = originalTokens - compressedTokens
            val compressionRatio = (savedTokens.toDouble() / originalTokens * 100).toInt()

            Log.d(TAG, "Compression complete: $originalTokens → $compressedTokens tokens (saved $savedTokens, ${compressionRatio}%)")

            return compressedMessages

        } catch (e: Exception) {
            Log.e(TAG, "Compression failed, returning original messages", e)
            return messages
        }
    }

    /**
     * Generate message summary
     */
    private suspend fun generateSummary(messages: List<LegacyMessage>): String {
        if (messages.isEmpty()) return ""

        // Build summary prompt
        val conversationText = messages.joinToString("\n\n") { message ->
            val role = message.role.uppercase()
            val content = message.content?.toString() ?: ""
            "[$role]: $content"
        }

        val summaryPrompt = buildSummaryPrompt(conversationText)

        try {
            // Use LLM to generate summary (using Extended Thinking)
            val summaryMessages = listOf(
                LegacyMessage(role = "user", content = summaryPrompt)
            )

            val response = legacyRepository.chatWithTools(
                messages = summaryMessages,
                tools = emptyList(),
                reasoningEnabled = true  // Use Extended Thinking to improve summary quality
            )

            val message = response.choices.firstOrNull()?.message
            if (message == null) {
                return "[Failed to generate summary: no response]"
            }

            return message.content?.toString() ?: "Summary generation failed"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate summary", e)
            return "[Failed to generate summary: ${e.message}]"
        }
    }

    /**
     * Build summary prompt
     */
    private fun buildSummaryPrompt(conversationText: String): String {
        val identifierGuidance = when (config.identifierPolicy) {
            IdentifierPolicy.STRICT -> """
            CRITICAL: Preserve ALL identifiers exactly as they appear:
            - UUIDs and hashes (e.g., a1b2c3d4-e5f6-7890)
            - API keys and tokens
            - File paths and URLs
            - Package names (e.g., com.example.app)
            - Hostnames and IP addresses
            - Port numbers
            - Database IDs
            - Any alphanumeric codes or identifiers

            Do NOT summarize, abbreviate, or replace identifiers with placeholders.
            """.trimIndent()

            IdentifierPolicy.OFF -> ""

            IdentifierPolicy.CUSTOM -> ""  // TODO: Custom guidance
        }

        return """
        Summarize the following conversation into a concise summary that captures the key information.

        $identifierGuidance

        Focus on preserving:
        - Active tasks and their current state
        - Bulk operation progress (e.g., "5/17 items completed")
        - User's last request
        - Decisions made and their rationales
        - TODOs, open questions, constraints
        - Any commitments or follow-up actions

        Prioritize recent context over older history.

        Conversation to summarize:

        $conversationText

        Provide a concise summary (aim for 30-50% of original length):
        """.trimIndent()
    }
}

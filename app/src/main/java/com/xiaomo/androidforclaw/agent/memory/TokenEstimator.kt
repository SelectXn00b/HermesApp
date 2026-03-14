package com.xiaomo.androidforclaw.agent.memory

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: estimate token usage for Android agent context.
 */


import com.xiaomo.androidforclaw.providers.LegacyMessage

/**
 * Token Estimator
 * Aligned with OpenClaw src/agents/compaction.ts
 *
 * Uses chars/4 heuristic + multibyte character correction
 */
object TokenEstimator {
    private const val TAG = "TokenEstimator"

    // Base estimate: 4 characters approximately equals 1 token
    private const val CHARS_PER_TOKEN = 4.0

    // Extra weight for multibyte characters (Chinese, Japanese, etc.)
    private const val MULTIBYTE_WEIGHT = 1.2

    /**
     * Estimate token count of text
     *
     * @param text Text to estimate
     * @return Estimated token count
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0

        val charCount = text.length
        var multibyteCount = 0

        // Count multibyte characters (non-ASCII)
        for (char in text) {
            if (char.code > 127) {
                multibyteCount++
            }
        }

        // Calculate adjusted character count
        val adjustedChars = charCount + (multibyteCount * (MULTIBYTE_WEIGHT - 1.0))

        // Convert to token count
        return (adjustedChars / CHARS_PER_TOKEN).toInt()
    }

    /**
     * Estimate token count of a message
     *
     * @param message Message to estimate
     * @return Estimated token count
     */
    fun estimateMessageTokens(message: LegacyMessage): Int {
        var total = 0

        // Estimate role field (about 5 tokens)
        total += 5

        // Estimate content
        when (val content = message.content) {
            is String -> {
                total += estimateTokens(content)
            }
            is List<*> -> {
                for (block in content) {
                    when (block) {
                        is Map<*, *> -> {
                            val type = block["type"] as? String
                            val text = block["text"] as? String
                            val imageUrl = block["image_url"] as? Map<*, *>

                            when (type) {
                                "text" -> {
                                    if (text != null) {
                                        total += estimateTokens(text)
                                    }
                                }
                                "image_url" -> {
                                    // Image is about 85-170 tokens, take middle value
                                    total += 127
                                }
                            }
                        }
                    }
                }
            }
        }

        // Estimate tool_calls
        if (message.toolCalls != null) {
            for (toolCall in message.toolCalls) {
                // Tool call structure overhead
                total += 10

                // Function name
                total += estimateTokens(toolCall.function.name)

                // Arguments
                total += estimateTokens(toolCall.function.arguments)
            }
        }

        // Estimate tool_call_id
        if (message.toolCallId != null) {
            total += estimateTokens(message.toolCallId)
        }

        return total
    }

    /**
     * Estimate total token count of message list
     *
     * @param messages Message list
     * @return Estimated total token count
     */
    fun estimateMessagesTokens(messages: List<LegacyMessage>): Int {
        return messages.sumOf { estimateMessageTokens(it) }
    }

    /**
     * Check if message list exceeds token limit
     *
     * @param messages Message list
     * @param maxTokens Maximum token count
     * @return Whether limit is exceeded
     */
    fun exceedsTokenLimit(messages: List<LegacyMessage>, maxTokens: Int): Boolean {
        return estimateMessagesTokens(messages) > maxTokens
    }

    /**
     * Calculate how many tokens can still be added to reach limit
     *
     * @param messages Current message list
     * @param maxTokens Maximum token count
     * @return Remaining available tokens
     */
    fun remainingTokens(messages: List<LegacyMessage>, maxTokens: Int): Int {
        val current = estimateMessagesTokens(messages)
        return maxTokens - current
    }
}

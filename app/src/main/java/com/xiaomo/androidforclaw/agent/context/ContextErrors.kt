package com.xiaomo.androidforclaw.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: agent context construction and budget control.
 */


/**
 * Context Error Detection Utilities
 * Aligned with OpenClaw's errors.ts implementation
 */
object ContextErrors {

    /**
     * Strict detection of context overflow errors
     * Reference: OpenClaw/src/agents/pi-embedded-helpers/errors.ts isContextOverflowError()
     */
    fun isContextOverflowError(errorMessage: String?): Boolean {
        if (errorMessage.isNullOrBlank()) return false

        val msg = errorMessage.lowercase()

        // Anthropic API specific errors
        if (msg.contains("request_too_large")) return true
        if (msg.contains("request size exceeds")) return true

        // Generic context window errors
        if (msg.contains("context window")) return true
        if (msg.contains("context length")) return true
        if (msg.contains("maximum context length")) return true

        // Prompt length errors
        if (msg.contains("prompt is too long")) return true
        if (msg.contains("exceeds model context window")) return true
        if (msg.contains("model token limit")) return true

        // Explicit context overflow markers
        if (msg.contains("context overflow:")) return true
        if (msg.contains("exceed context limit")) return true

        // Chinese error messages
        if (msg.contains("上下文过长")) return true
        if (msg.contains("上下文超出")) return true
        if (msg.contains("请压缩上下文")) return true

        // Token quantity related
        if (msg.contains("tokens") && (
            msg.contains("exceed") ||
            msg.contains("too many") ||
            msg.contains("limit")
        )) return true

        // HTTP 413 Payload Too Large
        if (msg.contains("413") || msg.contains("payload too large")) return true

        return false
    }

    /**
     * Heuristic detection of context overflow (more lenient)
     * Reference: OpenClaw/src/agents/pi-embedded-helpers/errors.ts isLikelyContextOverflowError()
     */
    fun isLikelyContextOverflowError(errorMessage: String?): Boolean {
        if (errorMessage.isNullOrBlank()) return false

        val msg = errorMessage.lowercase()

        // First check strict match
        if (isContextOverflowError(errorMessage)) return true

        // Exclude other error types
        if (isRateLimitError(msg)) return false
        if (isAuthError(msg)) return false
        if (isBillingError(msg)) return false

        // Regex match
        val contextPattern = Regex(
            "context.*(overflow|too|exceed|limit)|" +
            "prompt.*(too|exceed|limit)|" +
            "window.*(exceed|limit)",
            RegexOption.IGNORE_CASE
        )

        return contextPattern.containsMatchIn(msg)
    }

    /**
     * Detect Compaction failure error
     */
    fun isCompactionFailureError(errorMessage: String?): Boolean {
        if (errorMessage.isNullOrBlank()) return false

        val msg = errorMessage.lowercase()

        return (msg.contains("summarization failed") ||
                msg.contains("auto-compaction") ||
                msg.contains("compaction failed")) &&
                isLikelyContextOverflowError(errorMessage)
    }

    /**
     * Detect rate limit error
     */
    private fun isRateLimitError(msg: String): Boolean {
        return msg.contains("rate limit") ||
               msg.contains("too many requests") ||
               msg.contains("quota exceeded") ||
               msg.contains("tokens per minute")
    }

    /**
     * Detect authentication error
     */
    private fun isAuthError(msg: String): Boolean {
        return msg.contains("unauthorized") ||
               msg.contains("authentication") ||
               msg.contains("invalid api key") ||
               msg.contains("api key")
    }

    /**
     * Detect billing error
     */
    private fun isBillingError(msg: String): Boolean {
        return msg.contains("insufficient") ||
               msg.contains("balance") ||
               msg.contains("billing") ||
               msg.contains("payment")
    }

    /**
     * Extract error message from exception
     */
    fun extractErrorMessage(exception: Throwable): String {
        val message = exception.message ?: ""
        val cause = exception.cause?.message ?: ""
        return "$message $cause"
    }
}

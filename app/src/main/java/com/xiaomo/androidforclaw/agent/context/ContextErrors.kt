package com.xiaomo.androidforclaw.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/failover-error.ts
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

    // ==================== Transient HTTP Error ====================

    /**
     * Transient HTTP error codes.
     * Aligned with OpenClaw TRANSIENT_HTTP_ERROR_CODES.
     */
    private val TRANSIENT_HTTP_ERROR_CODES = setOf(499, 500, 502, 503, 504, 521, 522, 523, 524, 529)

    /**
     * Extract leading HTTP status code from error message.
     * Aligned with OpenClaw HTTP_STATUS_CODE_PREFIX_RE: /^(?:http\s*)?(\d{3})(?:\s+[\s\S]+)?$/i
     * Returns null if no status code found.
     */
    private val HTTP_STATUS_CODE_PREFIX_RE = Regex("^(?:http\\s*)?(\\d{3})(?:\\s+[\\s\\S]+)?$", RegexOption.IGNORE_CASE)

    private fun extractLeadingHttpStatus(message: String): Int? {
        val match = HTTP_STATUS_CODE_PREFIX_RE.find(message.trim()) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    /**
     * Detect transient HTTP errors (502, 521, etc.).
     * Aligned with OpenClaw isTransientHttpError.
     */
    fun isTransientHttpError(errorMessage: String?): Boolean {
        if (errorMessage.isNullOrBlank()) return false
        val trimmed = errorMessage.trim()
        val status = extractLeadingHttpStatus(trimmed) ?: return false
        return status in TRANSIENT_HTTP_ERROR_CODES
    }

    /**
     * Detect overloaded errors (HTTP 529 or 503+overloaded).
     * Aligned with OpenClaw classifyFailoverReason → "overloaded":
     * - 529 always overloaded
     * - 503 with "overload" in message body
     */
    fun isOverloadedError(errorMessage: String?): Boolean {
        if (errorMessage.isNullOrBlank()) return false
        val trimmed = errorMessage.trim()
        val status = extractLeadingHttpStatus(trimmed) ?: return false
        return when (status) {
            529 -> true
            503 -> trimmed.lowercase().contains("overload")
            else -> false
        }
    }

    // ==================== Role Ordering / Session Corruption ====================

    /**
     * Detect role ordering conflict errors.
     * Aligned with OpenClaw: /incorrect role information|roles must alternate/i
     */
    fun isRoleOrderingError(errorMessage: String?): Boolean {
        if (errorMessage.isNullOrBlank()) return false
        return Regex("incorrect role information|roles must alternate", RegexOption.IGNORE_CASE)
            .containsMatchIn(errorMessage)
    }

    /**
     * Detect Gemini session corruption errors.
     * Aligned with OpenClaw: /function call turn comes immediately after/i
     */
    fun isSessionCorruptionError(errorMessage: String?): Boolean {
        if (errorMessage.isNullOrBlank()) return false
        return Regex("function call turn comes immediately after", RegexOption.IGNORE_CASE)
            .containsMatchIn(errorMessage)
    }

    // ==================== Billing Error ====================

    /**
     * Detect billing error with user-facing message.
     * Aligned with OpenClaw isBillingErrorMessage.
     */
    fun isBillingErrorMessage(errorMessage: String?): Boolean {
        return isBillingError(errorMessage?.lowercase() ?: return false)
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

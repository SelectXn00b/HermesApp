package com.xiaomo.androidforclaw.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/usage.ts
 *
 * AndroidForClaw adaptation: token usage normalization across providers.
 */

import org.json.JSONObject

/**
 * Normalized token usage.
 * Aligned with OpenClaw NormalizedUsage.
 */
data class NormalizedUsage(
    val input: Int? = null,
    val output: Int? = null,
    val cacheRead: Int? = null,
    val cacheWrite: Int? = null,
    val total: Int? = null
) {
    /**
     * Compute total if not explicitly provided.
     */
    fun computedTotal(): Int {
        if (total != null) return total
        return (input ?: 0) + (output ?: 0)
    }
}

/**
 * Snapshot of assistant usage for a single turn.
 * Aligned with OpenClaw AssistantUsageSnapshot.
 */
data class AssistantUsageSnapshot(
    val usage: NormalizedUsage?,
    val model: String? = null,
    val provider: String? = null
)

/**
 * Token usage normalization — handles diverse provider/SDK naming conventions.
 * Aligned with OpenClaw usage.ts.
 */
object UsageNormalizer {

    /**
     * Normalize raw usage data from any provider format into canonical NormalizedUsage.
     *
     * Handles naming variants:
     * - camelCase: inputTokens, outputTokens, completionTokens, promptTokens
     * - snake_case: input_tokens, output_tokens, completion_tokens, prompt_tokens
     * - Anthropic: cache_read_input_tokens, cache_creation_input_tokens
     * - Moonshot/Kimi: cached_tokens, prompt_tokens_details.cached_tokens
     *
     * Aligned with OpenClaw normalizeUsage.
     */
    fun normalizeUsage(raw: JSONObject?): NormalizedUsage? {
        if (raw == null || raw.length() == 0) return null

        val rawInput = firstFiniteNumber(
            raw, "input", "inputTokens", "input_tokens", "promptTokens", "prompt_tokens"
        )
        // Clamp negative input to 0 (can happen when providers pre-subtract cached tokens)
        val input = if (rawInput != null && rawInput < 0) 0 else rawInput

        val output = firstFiniteNumber(
            raw, "output", "outputTokens", "output_tokens", "completionTokens", "completion_tokens"
        )

        val cacheRead = firstFiniteNumber(
            raw, "cacheRead", "cache_read", "cache_read_input_tokens", "cached_tokens"
        ) ?: raw.optJSONObject("prompt_tokens_details")?.let { firstFiniteNumber(it, "cached_tokens") }

        val cacheWrite = firstFiniteNumber(
            raw, "cacheWrite", "cache_write", "cache_creation_input_tokens"
        )

        val total = firstFiniteNumber(
            raw, "total", "totalTokens", "total_tokens"
        )

        // Return null if all fields are null
        if (input == null && output == null && cacheRead == null && cacheWrite == null && total == null) {
            return null
        }

        return NormalizedUsage(
            input = input,
            output = output,
            cacheRead = cacheRead,
            cacheWrite = cacheWrite,
            total = total
        )
    }

    /**
     * Normalize usage from a Map (for non-JSON sources).
     */
    fun normalizeUsage(raw: Map<String, Any?>?): NormalizedUsage? {
        if (raw.isNullOrEmpty()) return null

        val rawInput = firstFiniteNumber(
            raw, "input", "inputTokens", "input_tokens", "promptTokens", "prompt_tokens"
        )
        val input = if (rawInput != null && rawInput < 0) 0 else rawInput

        val output = firstFiniteNumber(
            raw, "output", "outputTokens", "output_tokens", "completionTokens", "completion_tokens"
        )

        val cacheRead = firstFiniteNumber(
            raw, "cacheRead", "cache_read", "cache_read_input_tokens", "cached_tokens"
        )

        val cacheWrite = firstFiniteNumber(
            raw, "cacheWrite", "cache_write", "cache_creation_input_tokens"
        )

        val total = firstFiniteNumber(
            raw, "total", "totalTokens", "total_tokens"
        )

        if (input == null && output == null && cacheRead == null && cacheWrite == null && total == null) {
            return null
        }

        return NormalizedUsage(
            input = input,
            output = output,
            cacheRead = cacheRead,
            cacheWrite = cacheWrite,
            total = total
        )
    }

    /**
     * Check if usage has any nonzero values.
     * Aligned with OpenClaw hasNonzeroUsage.
     */
    fun hasNonzeroUsage(usage: NormalizedUsage?): Boolean {
        if (usage == null) return false
        return (usage.input ?: 0) > 0 ||
            (usage.output ?: 0) > 0 ||
            (usage.cacheRead ?: 0) > 0 ||
            (usage.cacheWrite ?: 0) > 0 ||
            (usage.total ?: 0) > 0
    }

    /**
     * Create a zero usage snapshot.
     * Aligned with OpenClaw makeZeroUsageSnapshot.
     */
    fun makeZeroUsageSnapshot(): AssistantUsageSnapshot {
        return AssistantUsageSnapshot(
            usage = NormalizedUsage(input = 0, output = 0, cacheRead = 0, cacheWrite = 0, total = 0)
        )
    }

    /**
     * Derive prompt tokens from usage (input + cacheRead + cacheWrite).
     * Intentionally excludes output tokens.
     * Aligned with OpenClaw derivePromptTokens.
     */
    fun derivePromptTokens(usage: NormalizedUsage?): Int {
        if (usage == null) return 0
        return (usage.input ?: 0) + (usage.cacheRead ?: 0) + (usage.cacheWrite ?: 0)
    }

    /**
     * Derive session total tokens (prompt tokens only, excludes output).
     * Aligned with OpenClaw deriveSessionTotalTokens.
     */
    fun deriveSessionTotalTokens(usages: List<NormalizedUsage?>): Int {
        return usages.sumOf { derivePromptTokens(it) }
    }

    // ── Internal helpers ──

    private fun firstFiniteNumber(json: JSONObject, vararg keys: String): Int? {
        for (key in keys) {
            if (json.has(key)) {
                val value = json.opt(key)
                val num = when (value) {
                    is Number -> value.toInt()
                    is String -> value.toIntOrNull()
                    else -> null
                }
                if (num != null) return num
            }
        }
        return null
    }

    private fun firstFiniteNumber(map: Map<String, Any?>, vararg keys: String): Int? {
        for (key in keys) {
            val value = map[key]
            val num = when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
            if (num != null) return num
        }
        return null
    }
}

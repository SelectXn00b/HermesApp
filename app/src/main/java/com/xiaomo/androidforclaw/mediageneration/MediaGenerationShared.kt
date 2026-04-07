package com.xiaomo.androidforclaw.mediageneration

/**
 * OpenClaw module: media-generation
 * Source: OpenClaw/src/media-generation/runtime-shared.ts
 *
 * Shared runtime utilities for media generation capabilities:
 * - Model candidate resolution with override -> primary -> fallback chain
 * - Error formatting and failover tracking
 * - "No model configured" message builder
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

// ---------------------------------------------------------------------------
// ParsedProviderModelRef — a resolved "provider/model" pair
// ---------------------------------------------------------------------------

data class ParsedProviderModelRef(
    val provider: String,
    val model: String
)

// ---------------------------------------------------------------------------
// FallbackAttempt — tracks each attempt during failover
// ---------------------------------------------------------------------------

data class FallbackAttempt(
    val provider: String,
    val model: String,
    val error: Throwable? = null,
    val reason: String? = null,
    val status: Int? = null,
    val code: String? = null
)

// ---------------------------------------------------------------------------
// Model-config resolution helpers (aligned with runtime-shared.ts)
// ---------------------------------------------------------------------------

/**
 * Extract the primary model value from a model-config entry.
 *
 * The model config value can be:
 * - A plain [String] ("provider/model")
 * - A [Map] with a "primary" key
 */
fun resolveAgentModelPrimaryValue(modelConfig: Any?): String? {
    return when (modelConfig) {
        is String -> modelConfig
        is Map<*, *> -> modelConfig["primary"] as? String
        else -> null
    }
}

/**
 * Extract the fallback model values from a model-config entry.
 *
 * Only applicable when modelConfig is a [Map] with a "fallbacks" key
 * containing a [List] of strings.
 */
@Suppress("UNCHECKED_CAST")
fun resolveAgentModelFallbackValues(modelConfig: Any?): List<String> {
    if (modelConfig !is Map<*, *>) return emptyList()
    val fallbacks = modelConfig["fallbacks"] ?: return emptyList()
    return when (fallbacks) {
        is List<*> -> fallbacks.filterIsInstance<String>()
        else -> emptyList()
    }
}

/**
 * Build an ordered, deduplicated list of model candidates for a capability.
 *
 * Resolution order:
 * 1. [modelOverride] — explicit user/caller override
 * 2. Primary from [modelConfig]
 * 3. Fallbacks from [modelConfig]
 *
 * Each raw ref is parsed via [parseModelRef]. Duplicates (by "provider/model" key)
 * are suppressed, preserving first-seen order.
 *
 * @param cfg           Current OpenClaw config (reserved for future provider-aware resolution).
 * @param modelConfig   The raw model-config value from agent/config (String, Map, or null).
 * @param modelOverride An explicit override ref string ("provider/model"), highest priority.
 * @param parseModelRef Function that splits a raw string into [ParsedProviderModelRef].
 */
fun resolveCapabilityModelCandidates(
    cfg: OpenClawConfig?,
    modelConfig: Any?,
    modelOverride: String?,
    parseModelRef: (String) -> ParsedProviderModelRef?
): List<ParsedProviderModelRef> {
    val candidates = mutableListOf<ParsedProviderModelRef>()
    val seen = mutableSetOf<String>()

    fun add(raw: String?) {
        if (raw.isNullOrBlank()) return
        val parsed = parseModelRef(raw) ?: return
        val key = "${parsed.provider}/${parsed.model}"
        if (seen.add(key)) {
            candidates.add(parsed)
        }
    }

    // 1. Override (highest priority)
    add(modelOverride)

    // 2. Primary
    add(resolveAgentModelPrimaryValue(modelConfig))

    // 3. Fallbacks
    for (fb in resolveAgentModelFallbackValues(modelConfig)) {
        add(fb)
    }

    return candidates
}

// ---------------------------------------------------------------------------
// Error helpers
// ---------------------------------------------------------------------------

/**
 * Throw a consolidated capability-generation failure.
 *
 * If there was exactly 1 attempt and a [lastError] is available, rethrow it directly
 * so the caller sees the original stack trace. Otherwise build a summary message
 * listing every attempt.
 */
fun throwCapabilityGenerationFailure(
    capabilityLabel: String,
    attempts: List<FallbackAttempt>,
    lastError: Throwable? = null
): Nothing {
    if (attempts.size == 1 && lastError != null) {
        throw lastError
    }

    val summary = buildString {
        append("$capabilityLabel generation failed after ${attempts.size} attempt(s):")
        for ((i, a) in attempts.withIndex()) {
            append("\n  [${i + 1}] ${a.provider}/${a.model}")
            if (a.reason != null) append(" - ${a.reason}")
            if (a.status != null) append(" (status=${a.status})")
            if (a.code != null) append(" (code=${a.code})")
            if (a.error != null) append(" : ${a.error.message}")
        }
    }

    throw IllegalStateException(summary, lastError)
}

/**
 * Build the user-facing message when no model is configured for a capability.
 *
 * @param capabilityLabel  Human-readable capability name (e.g. "Image Generation").
 * @param modelConfigKey   Config path users should set (e.g. "agents.defaults.imageGeneration.model").
 * @param providers        List of known provider IDs for the capability.
 * @param fallbackSampleRef Optional sample model ref to show as an example.
 */
fun buildNoCapabilityModelConfiguredMessage(
    capabilityLabel: String,
    modelConfigKey: String,
    providers: List<String>,
    fallbackSampleRef: String? = null
): String {
    return buildString {
        append("No $capabilityLabel model configured.")
        append(" Set \"$modelConfigKey\" in your config")
        if (providers.isNotEmpty()) {
            append(" (available providers: ${providers.joinToString(", ")})")
        }
        append(".")
        if (fallbackSampleRef != null) {
            append(" Example: \"$fallbackSampleRef\"")
        }
    }
}

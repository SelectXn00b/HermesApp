package com.xiaomo.androidforclaw.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/provider-capabilities.ts
 *
 * AndroidForClaw adaptation: per-provider capability resolution.
 */

import com.xiaomo.androidforclaw.config.ProviderRegistry

/**
 * Per-provider capability flags.
 * Aligned with OpenClaw ProviderCapabilities type.
 */
data class ProviderCapabilities(
    val anthropicToolSchemaMode: String = "native",           // "native" | "openai-functions"
    val anthropicToolChoiceMode: String = "native",           // "native" | "openai-string-modes"
    val providerFamily: String = "default",                   // "default" | "openai" | "anthropic"
    val preserveAnthropicThinkingSignatures: Boolean = true,
    val openAiCompatTurnValidation: Boolean = true,
    val geminiThoughtSignatureSanitization: Boolean = false,
    val transcriptToolCallIdMode: String = "default",         // "default" | "strict9"
    val transcriptToolCallIdModelHints: List<String> = emptyList(),
    val geminiThoughtSignatureModelHints: List<String> = emptyList(),
    val dropThinkingBlockModelHints: List<String> = emptyList()
) {
    companion object {
        /**
         * Default capabilities.
         * Aligned with OpenClaw DEFAULT_PROVIDER_CAPABILITIES.
         */
        val DEFAULT = ProviderCapabilities()
    }
}

/**
 * Provider capability resolution.
 * Aligned with OpenClaw provider-capabilities.ts.
 */
object ProviderCapabilityResolver {

    /**
     * Core provider capabilities (hard-coded, not plugin-dependent).
     * Aligned with OpenClaw CORE_PROVIDER_CAPABILITIES.
     */
    private val CORE_PROVIDER_CAPABILITIES: Map<String, ProviderCapabilities> = mapOf(
        "anthropic-vertex" to ProviderCapabilities(
            providerFamily = "anthropic",
            dropThinkingBlockModelHints = listOf("claude")
        ),
        "amazon-bedrock" to ProviderCapabilities(
            providerFamily = "anthropic",
            dropThinkingBlockModelHints = listOf("claude")
        )
    )

    /**
     * Plugin capability fallbacks (used when no plugin provides capabilities).
     * Aligned with OpenClaw PLUGIN_CAPABILITIES_FALLBACKS.
     */
    private val PLUGIN_CAPABILITIES_FALLBACKS: Map<String, ProviderCapabilities> = mapOf(
        "anthropic" to ProviderCapabilities(
            providerFamily = "anthropic",
            dropThinkingBlockModelHints = listOf("claude")
        ),
        "mistral" to ProviderCapabilities(
            transcriptToolCallIdMode = "strict9",
            transcriptToolCallIdModelHints = listOf(
                "mistral", "mixtral", "codestral", "pixtral",
                "devstral", "ministral", "mistralai"
            )
        ),
        "opencode" to ProviderCapabilities(
            openAiCompatTurnValidation = false,
            geminiThoughtSignatureSanitization = true,
            geminiThoughtSignatureModelHints = listOf("gemini")
        ),
        "opencode-go" to ProviderCapabilities(
            openAiCompatTurnValidation = false,
            geminiThoughtSignatureSanitization = true,
            geminiThoughtSignatureModelHints = listOf("gemini")
        ),
        "openai" to ProviderCapabilities(
            providerFamily = "openai"
        )
    )

    /**
     * Resolve capabilities for a provider.
     * Merge order: DEFAULT → CORE → PLUGIN_FALLBACKS.
     *
     * Aligned with OpenClaw resolveProviderCapabilities.
     */
    fun resolveProviderCapabilities(provider: String): ProviderCapabilities {
        val normalized = ProviderRegistry.normalizeProviderId(provider)

        // Start with defaults
        var result = ProviderCapabilities.DEFAULT

        // Layer core capabilities
        CORE_PROVIDER_CAPABILITIES[normalized]?.let { core ->
            result = mergeCapabilities(result, core)
        }

        // Layer plugin fallbacks
        PLUGIN_CAPABILITIES_FALLBACKS[normalized]?.let { fallback ->
            result = mergeCapabilities(result, fallback)
        }

        return result
    }

    /**
     * Merge non-default fields from overlay onto base.
     */
    private fun mergeCapabilities(base: ProviderCapabilities, overlay: ProviderCapabilities): ProviderCapabilities {
        val default = ProviderCapabilities.DEFAULT
        return base.copy(
            anthropicToolSchemaMode = if (overlay.anthropicToolSchemaMode != default.anthropicToolSchemaMode) overlay.anthropicToolSchemaMode else base.anthropicToolSchemaMode,
            anthropicToolChoiceMode = if (overlay.anthropicToolChoiceMode != default.anthropicToolChoiceMode) overlay.anthropicToolChoiceMode else base.anthropicToolChoiceMode,
            providerFamily = if (overlay.providerFamily != default.providerFamily) overlay.providerFamily else base.providerFamily,
            preserveAnthropicThinkingSignatures = if (!overlay.preserveAnthropicThinkingSignatures) overlay.preserveAnthropicThinkingSignatures else base.preserveAnthropicThinkingSignatures,
            openAiCompatTurnValidation = if (!overlay.openAiCompatTurnValidation) overlay.openAiCompatTurnValidation else base.openAiCompatTurnValidation,
            geminiThoughtSignatureSanitization = if (overlay.geminiThoughtSignatureSanitization) overlay.geminiThoughtSignatureSanitization else base.geminiThoughtSignatureSanitization,
            transcriptToolCallIdMode = if (overlay.transcriptToolCallIdMode != default.transcriptToolCallIdMode) overlay.transcriptToolCallIdMode else base.transcriptToolCallIdMode,
            transcriptToolCallIdModelHints = if (overlay.transcriptToolCallIdModelHints.isNotEmpty()) overlay.transcriptToolCallIdModelHints else base.transcriptToolCallIdModelHints,
            geminiThoughtSignatureModelHints = if (overlay.geminiThoughtSignatureModelHints.isNotEmpty()) overlay.geminiThoughtSignatureModelHints else base.geminiThoughtSignatureModelHints,
            dropThinkingBlockModelHints = if (overlay.dropThinkingBlockModelHints.isNotEmpty()) overlay.dropThinkingBlockModelHints else base.dropThinkingBlockModelHints
        )
    }

    // ── Helper booleans ──

    /**
     * Whether the provider preserves Anthropic thinking signatures.
     * Aligned with OpenClaw preservesAnthropicThinkingSignatures.
     */
    fun preservesAnthropicThinkingSignatures(provider: String): Boolean {
        return resolveProviderCapabilities(provider).preserveAnthropicThinkingSignatures
    }

    /**
     * Whether the provider is in the OpenAI family.
     * Aligned with OpenClaw isOpenAiProviderFamily.
     */
    fun isOpenAiProviderFamily(provider: String): Boolean {
        return resolveProviderCapabilities(provider).providerFamily == "openai"
    }

    /**
     * Whether the provider is in the Anthropic family.
     * Aligned with OpenClaw isAnthropicProviderFamily.
     */
    fun isAnthropicProviderFamily(provider: String): Boolean {
        return resolveProviderCapabilities(provider).providerFamily == "anthropic"
    }

    /**
     * Whether thinking blocks should be dropped for a specific model.
     * Aligned with OpenClaw shouldDropThinkingBlocksForModel.
     */
    fun shouldDropThinkingBlocksForModel(provider: String, model: String): Boolean {
        val caps = resolveProviderCapabilities(provider)
        if (caps.dropThinkingBlockModelHints.isEmpty()) return false
        val modelLower = model.lowercase()
        return caps.dropThinkingBlockModelHints.any { hint -> modelLower.contains(hint) }
    }

    /**
     * Whether Gemini thought signatures should be sanitized for a specific model.
     * Aligned with OpenClaw shouldSanitizeGeminiThoughtSignaturesForModel.
     */
    fun shouldSanitizeGeminiThoughtSignatures(provider: String, model: String): Boolean {
        val caps = resolveProviderCapabilities(provider)
        if (!caps.geminiThoughtSignatureSanitization) return false
        if (caps.geminiThoughtSignatureModelHints.isEmpty()) return true
        val modelLower = model.lowercase()
        return caps.geminiThoughtSignatureModelHints.any { hint -> modelLower.contains(hint) }
    }

    /**
     * Resolve transcript tool call ID mode for a specific model.
     * Aligned with OpenClaw resolveTranscriptToolCallIdMode.
     */
    fun resolveTranscriptToolCallIdMode(provider: String, model: String): String {
        val caps = resolveProviderCapabilities(provider)
        if (caps.transcriptToolCallIdMode == "default") return "default"
        if (caps.transcriptToolCallIdModelHints.isEmpty()) return caps.transcriptToolCallIdMode
        val modelLower = model.lowercase()
        return if (caps.transcriptToolCallIdModelHints.any { hint -> modelLower.contains(hint) }) {
            caps.transcriptToolCallIdMode
        } else {
            "default"
        }
    }
}

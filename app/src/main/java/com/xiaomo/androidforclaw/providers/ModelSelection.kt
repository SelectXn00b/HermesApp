package com.xiaomo.androidforclaw.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/model-selection.ts
 * - ../openclaw/src/agents/model-alias-lines.ts
 *
 * AndroidForClaw adaptation: model reference parsing, alias resolution, and thinking level.
 */

import com.xiaomo.androidforclaw.agent.AgentDefaults
import com.xiaomo.androidforclaw.config.OpenClawConfig
import com.xiaomo.androidforclaw.config.ProviderRegistry

/**
 * Universal model reference.
 * Aligned with OpenClaw ModelRef.
 */
data class ModelRef(
    val provider: String,
    val model: String
)

/**
 * Thinking budget level.
 * Aligned with OpenClaw ThinkLevel.
 */
enum class ThinkLevel {
    OFF, MINIMAL, LOW, MEDIUM, HIGH, XHIGH, ADAPTIVE;

    companion object {
        fun fromString(value: String?): ThinkLevel? {
            if (value == null) return null
            return when (value.trim().lowercase()) {
                "off" -> OFF
                "minimal" -> MINIMAL
                "low" -> LOW
                "medium" -> MEDIUM
                "high" -> HIGH
                "xhigh" -> XHIGH
                "adaptive" -> ADAPTIVE
                else -> null
            }
        }
    }
}

/**
 * Model alias index entry.
 * Aligned with OpenClaw ModelAliasIndex.
 */
data class ModelAliasEntry(
    val alias: String,
    val ref: ModelRef
)

/**
 * Bidirectional model alias index.
 * Aligned with OpenClaw ModelAliasIndex.
 */
data class ModelAliasIndex(
    /** alias → entry */
    val byAlias: Map<String, ModelAliasEntry>,
    /** canonical key → list of aliases */
    val byKey: Map<String, List<String>>
)

/**
 * Model selection — reference parsing, alias resolution, thinking level.
 * Aligned with OpenClaw model-selection.ts + model-alias-lines.ts.
 */
object ModelSelection {

    /**
     * Build canonical model key: "provider/model".
     * Avoids double-prefixing if model already starts with provider/.
     *
     * Aligned with OpenClaw modelKey.
     */
    fun modelKey(provider: String, model: String): String {
        val providerId = provider.trim()
        val modelId = model.trim()
        if (providerId.isEmpty()) return modelId
        if (modelId.isEmpty()) return providerId
        return if (modelId.lowercase().startsWith("${providerId.lowercase()}/")) {
            modelId
        } else {
            "$providerId/$modelId"
        }
    }

    /**
     * Parse a raw model reference string into provider + model.
     * Format: "provider/model" or just "model" (uses defaultProvider).
     *
     * Aligned with OpenClaw parseModelRef.
     */
    fun parseModelRef(raw: String?, defaultProvider: String = AgentDefaults.DEFAULT_PROVIDER): ModelRef {
        if (raw.isNullOrBlank()) {
            return ModelRef(defaultProvider, AgentDefaults.DEFAULT_MODEL)
        }
        val trimmed = raw.trim()
        val slashIndex = trimmed.indexOf('/')
        return if (slashIndex > 0) {
            ModelRef(trimmed.substring(0, slashIndex), trimmed.substring(slashIndex + 1))
        } else {
            ModelRef(defaultProvider, trimmed)
        }
    }

    /**
     * Normalize Anthropic shorthand model IDs.
     * Aligned with OpenClaw normalizeAnthropicModelId.
     */
    fun normalizeAnthropicModelId(model: String): String {
        val trimmed = model.trim()
        if (trimmed.isEmpty()) return trimmed
        return when (trimmed.lowercase()) {
            "opus-4.6" -> "claude-opus-4-6"
            "opus-4.5" -> "claude-opus-4-5"
            "sonnet-4.6" -> "claude-sonnet-4-6"
            "sonnet-4.5" -> "claude-sonnet-4-5"
            else -> trimmed
        }
    }

    /**
     * Normalize a model reference: apply provider-specific ID normalization.
     * Aligned with OpenClaw normalizeModelRef.
     */
    fun normalizeModelRef(provider: String, model: String): ModelRef {
        val normalizedProvider = ProviderRegistry.normalizeProviderId(provider)
        var normalizedModel = model

        // Anthropic shorthand aliases
        if (normalizedProvider == "anthropic") {
            normalizedModel = normalizeAnthropicModelId(normalizedModel)
        }

        // Provider-specific ID normalization (Google, xAI, etc.)
        normalizedModel = ModelIdNormalization.normalizeModelId(normalizedProvider, normalizedModel)

        return ModelRef(normalizedProvider, normalizedModel)
    }

    /**
     * Resolve a model reference from a raw string, checking aliases first.
     * Aligned with OpenClaw resolveModelRefFromString.
     */
    fun resolveModelRefFromString(
        raw: String,
        defaultProvider: String = AgentDefaults.DEFAULT_PROVIDER,
        aliasIndex: ModelAliasIndex? = null
    ): ModelRef {
        val trimmed = raw.trim()

        // Check alias index first
        if (aliasIndex != null) {
            val aliasEntry = aliasIndex.byAlias[trimmed.lowercase()]
            if (aliasEntry != null) {
                return normalizeModelRef(aliasEntry.ref.provider, aliasEntry.ref.model)
            }
        }

        // Split off auth profile suffix
        val (modelPart, _) = ModelRefProfile.splitTrailingAuthProfile(trimmed)

        // Parse as provider/model
        val ref = parseModelRef(modelPart, defaultProvider)
        return normalizeModelRef(ref.provider, ref.model)
    }

    /**
     * Resolve the configured default model reference.
     * Aligned with OpenClaw resolveConfiguredModelRef.
     */
    fun resolveConfiguredModelRef(cfg: OpenClawConfig): ModelRef {
        // 1. Explicit primary model from agents.defaults.model
        val primary = cfg.agents?.defaults?.model?.primary
        if (!primary.isNullOrBlank()) {
            val aliasIndex = buildModelAliasIndex(cfg)
            return resolveModelRefFromString(primary, AgentDefaults.DEFAULT_PROVIDER, aliasIndex)
        }

        // 2. Fall back to first configured provider's first model
        val providers = cfg.resolveProviders()
        val first = providers.entries.firstOrNull()
        if (first != null) {
            val modelId = first.value.models.firstOrNull()?.id
            if (modelId != null) {
                return normalizeModelRef(first.key, modelId)
            }
        }

        // 3. Ultimate fallback
        return ModelRef(AgentDefaults.DEFAULT_PROVIDER, AgentDefaults.DEFAULT_MODEL)
    }

    /**
     * Resolve thinking level for a provider/model.
     * Priority: per-model config > global thinkingDefault > model-inherent default.
     *
     * Aligned with OpenClaw resolveThinkingDefault.
     */
    fun resolveThinkingDefault(
        cfg: OpenClawConfig,
        provider: String,
        model: String
    ): ThinkLevel {
        // Per-model thinking config (not yet in Android config, placeholder)
        // OpenClaw checks agents.defaults.models[canonicalKey].params.thinking

        // Global thinkingDefault — use thinking.enabled as proxy
        if (!cfg.thinking.enabled) {
            return ThinkLevel.OFF
        }

        // Default based on model capabilities
        return resolveThinkingDefaultForModel(provider, model)
    }

    /**
     * Resolve thinking default based on model capabilities.
     * Models known to support extended thinking get MEDIUM; others get OFF.
     */
    private fun resolveThinkingDefaultForModel(provider: String, model: String): ThinkLevel {
        val modelLower = model.lowercase()
        // Anthropic models with reasoning support
        if (modelLower.contains("claude") && (modelLower.contains("opus") || modelLower.contains("sonnet"))) {
            return ThinkLevel.MEDIUM
        }
        // OpenAI o-series reasoning models
        if (modelLower.startsWith("o1") || modelLower.startsWith("o3") || modelLower.startsWith("o4")) {
            return ThinkLevel.MEDIUM
        }
        // Google Gemini with thinking
        if (modelLower.contains("gemini") && modelLower.contains("thinking")) {
            return ThinkLevel.MEDIUM
        }
        // DeepSeek reasoning
        if (modelLower.contains("deepseek") && modelLower.contains("reason")) {
            return ThinkLevel.MEDIUM
        }
        return ThinkLevel.OFF
    }

    /**
     * Build model alias index from config.
     * Aligned with OpenClaw buildModelAliasIndex.
     */
    fun buildModelAliasIndex(
        cfg: OpenClawConfig,
        defaultProvider: String = AgentDefaults.DEFAULT_PROVIDER
    ): ModelAliasIndex {
        val byAlias = mutableMapOf<String, ModelAliasEntry>()
        val byKey = mutableMapOf<String, MutableList<String>>()

        for ((alias, target) in cfg.modelAliases) {
            val ref = parseModelRef(target, defaultProvider)
            val normalized = normalizeModelRef(ref.provider, ref.model)
            val entry = ModelAliasEntry(alias = alias, ref = normalized)
            byAlias[alias.lowercase()] = entry

            val key = modelKey(normalized.provider, normalized.model)
            byKey.getOrPut(key) { mutableListOf() }.add(alias)
        }

        return ModelAliasIndex(byAlias = byAlias, byKey = byKey)
    }

    /**
     * Build human-readable model alias lines for system prompt.
     * Aligned with OpenClaw buildModelAliasLines (model-alias-lines.ts).
     */
    fun buildModelAliasLines(cfg: OpenClawConfig): List<String> {
        if (cfg.modelAliases.isEmpty()) return emptyList()

        return cfg.modelAliases.entries
            .sortedBy { it.key.lowercase() }
            .map { (alias, target) -> "- $alias: $target" }
    }
}

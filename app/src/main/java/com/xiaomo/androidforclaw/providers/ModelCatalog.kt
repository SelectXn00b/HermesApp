package com.xiaomo.androidforclaw.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/model-catalog.ts
 *
 * AndroidForClaw adaptation: model catalog with capability tracking.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import com.xiaomo.androidforclaw.config.ProviderRegistry

/**
 * Model input types.
 * Aligned with OpenClaw ModelInputType.
 */
enum class ModelInputType {
    TEXT, IMAGE, DOCUMENT
}

/**
 * Model catalog entry.
 * Aligned with OpenClaw ModelCatalogEntry.
 */
data class ModelCatalogEntry(
    val id: String,
    val name: String,
    val provider: String,
    val contextWindow: Int? = null,
    val reasoning: Boolean? = null,
    val input: List<ModelInputType>? = null
)

/**
 * Model catalog — builds and queries a catalog of available models.
 * Aligned with OpenClaw model-catalog.ts.
 */
object ModelCatalog {

    /**
     * Providers whose models are not discovered via Pi SDK but read from config.
     * Aligned with OpenClaw NON_PI_NATIVE_MODEL_PROVIDERS.
     */
    private val NON_PI_NATIVE_MODEL_PROVIDERS = setOf("kilocode")

    @Volatile
    private var cachedCatalog: List<ModelCatalogEntry>? = null

    /**
     * Load or build the model catalog.
     * Merged from ProviderRegistry definitions + config-defined models.
     *
     * Aligned with OpenClaw loadModelCatalog.
     */
    fun loadModelCatalog(cfg: OpenClawConfig? = null): List<ModelCatalogEntry> {
        cachedCatalog?.let { return it }

        val entries = mutableListOf<ModelCatalogEntry>()

        // 1. From ProviderRegistry definitions
        for (providerDef in ProviderRegistry.ALL) {
            // Each provider definition has default models
            // We create catalog entries from config-defined models for this provider
        }

        // 2. From config providers
        if (cfg != null) {
            val providers = cfg.resolveProviders()
            for ((providerId, providerConfig) in providers) {
                for (modelDef in providerConfig.models) {
                    val inputTypes = mutableListOf(ModelInputType.TEXT)
                    // Infer vision support from model name
                    val modelLower = modelDef.id.lowercase()
                    if (modelLower.contains("vision") || modelLower.contains("vlm") ||
                        modelLower.contains("gpt-4") || modelLower.contains("claude") ||
                        modelLower.contains("gemini")
                    ) {
                        inputTypes.add(ModelInputType.IMAGE)
                    }

                    entries.add(
                        ModelCatalogEntry(
                            id = modelDef.id,
                            name = modelDef.name ?: modelDef.id,
                            provider = providerId,
                            contextWindow = modelDef.contextWindow,
                            reasoning = modelDef.reasoning,
                            input = inputTypes
                        )
                    )
                }
            }
        }

        // 3. Read configured opt-in provider models (kilocode, etc.)
        if (cfg != null) {
            readConfiguredOptInProviderModels(cfg, entries)
        }

        // Sort by provider then name
        val sorted = entries.sortedWith(compareBy({ it.provider }, { it.name }))
        cachedCatalog = sorted
        return sorted
    }

    /**
     * Read models from non-PI-native providers configured in models.providers.
     * Aligned with OpenClaw readConfiguredOptInProviderModels.
     */
    private fun readConfiguredOptInProviderModels(
        cfg: OpenClawConfig,
        entries: MutableList<ModelCatalogEntry>
    ) {
        val providers = cfg.resolveProviders()
        for ((providerId, providerConfig) in providers) {
            if (providerId !in NON_PI_NATIVE_MODEL_PROVIDERS) continue
            for (modelDef in providerConfig.models) {
                // Avoid duplicates
                if (entries.any { it.provider == providerId && it.id.equals(modelDef.id, ignoreCase = true) }) continue
                entries.add(
                    ModelCatalogEntry(
                        id = modelDef.id,
                        name = modelDef.name ?: modelDef.id,
                        provider = providerId,
                        contextWindow = modelDef.contextWindow,
                        reasoning = modelDef.reasoning,
                        input = listOf(ModelInputType.TEXT)
                    )
                )
            }
        }
    }

    /**
     * Check if a model supports vision (image input).
     * Aligned with OpenClaw modelSupportsVision.
     */
    fun modelSupportsVision(entry: ModelCatalogEntry?): Boolean {
        return entry?.input?.contains(ModelInputType.IMAGE) == true
    }

    /**
     * Check if a model supports document input.
     * Aligned with OpenClaw modelSupportsDocument.
     */
    fun modelSupportsDocument(entry: ModelCatalogEntry?): Boolean {
        return entry?.input?.contains(ModelInputType.DOCUMENT) == true
    }

    /**
     * Find a model in the catalog by provider and model ID (case-insensitive).
     * Aligned with OpenClaw findModelInCatalog.
     */
    fun findModelInCatalog(
        catalog: List<ModelCatalogEntry>,
        provider: String,
        modelId: String
    ): ModelCatalogEntry? {
        return catalog.find {
            it.provider.equals(provider, ignoreCase = true) &&
                it.id.equals(modelId, ignoreCase = true)
        }
    }

    /**
     * Find a model in the catalog using a ModelRef.
     */
    fun findModelInCatalog(catalog: List<ModelCatalogEntry>, ref: ModelRef): ModelCatalogEntry? {
        return findModelInCatalog(catalog, ref.provider, ref.model)
    }

    /**
     * Clear the cached catalog (e.g., after config reload).
     */
    fun clearCache() {
        cachedCatalog = null
    }
}

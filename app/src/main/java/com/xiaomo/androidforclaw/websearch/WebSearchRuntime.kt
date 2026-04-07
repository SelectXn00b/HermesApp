package com.xiaomo.androidforclaw.websearch

/**
 * OpenClaw module: web-search
 * Source: OpenClaw/src/web-search/runtime.ts (~217 LOC)
 *
 * Web search runtime: provider resolution, enablement checks, and search
 * execution that delegates to the configured search provider.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import com.xiaomo.androidforclaw.web.resolveWebProviderDefinition
import com.xiaomo.androidforclaw.web.hasWebProviderEntryCredential

object WebSearchRuntime {

    /** Known web search provider IDs. */
    private val SEARCH_PROVIDER_IDS = listOf("brave", "google", "tavily", "duckduckgo")

    // -----------------------------------------------------------------------
    // Enablement
    // -----------------------------------------------------------------------

    fun resolveWebSearchEnabled(
        searchConfig: Map<String, Any?>?,
        sandboxed: Boolean? = null
    ): Boolean {
        if (sandboxed == true) {
            return searchConfig?.get("enabled") == true
        }
        return searchConfig?.get("enabled") != false
    }

    // -----------------------------------------------------------------------
    // Provider resolution
    // -----------------------------------------------------------------------

    fun listWebSearchProviders(config: OpenClawConfig? = null): List<WebSearchProviderEntry> {
        return SEARCH_PROVIDER_IDS.mapNotNull { id ->
            val def = resolveWebProviderDefinition(id, config)
            if (def != null) {
                val configured = if (def.envKey != null) hasWebProviderEntryCredential(def.envKey) else true
                WebSearchProviderEntry(id = id, label = def.label, configured = configured)
            } else {
                WebSearchProviderEntry(id = id, label = id, configured = false)
            }
        }
    }

    fun listConfiguredWebSearchProviders(config: OpenClawConfig? = null): List<WebSearchProviderEntry> {
        return listWebSearchProviders(config).filter { it.configured }
    }

    fun resolveWebSearchProviderId(
        config: OpenClawConfig? = null,
        preferredId: String? = null
    ): String? {
        if (preferredId != null) {
            val providers = listConfiguredWebSearchProviders(config)
            if (providers.any { it.id == preferredId }) return preferredId
        }
        return listConfiguredWebSearchProviders(config).firstOrNull()?.id
    }

    fun resolveWebSearchDefinition(
        config: OpenClawConfig? = null
    ): WebSearchDefinitionResult? {
        val providerId = resolveWebSearchProviderId(config) ?: return null
        return WebSearchDefinitionResult(
            providerId = providerId,
            enabled = true
        )
    }

    // -----------------------------------------------------------------------
    // Search execution
    // -----------------------------------------------------------------------

    /**
     * Execute a web search using the resolved provider.
     *
     * Accepts a typed [WebSearchRequest] which carries query, maxResults,
     * preferred provider, and timeout.
     *
     * The actual API call is provider-specific; this method currently returns
     * an empty result set as a stub. Real implementations should delegate
     * to the provider's HTTP API.
     */
    suspend fun searchWeb(request: WebSearchRequest, config: OpenClawConfig? = null): WebSearchResult {
        val providerId = request.provider
            ?.let { resolveWebSearchProviderId(config, it) }
            ?: resolveWebSearchProviderId(config)
            ?: throw IllegalStateException("No web search provider configured")

        // Stub: real implementation delegates to the resolved provider's API.
        return WebSearchResult(
            query = request.query,
            results = emptyList(),
            providerId = providerId
        )
    }

    /**
     * Convenience overload accepting a raw query string.
     */
    suspend fun runWebSearch(query: String, config: OpenClawConfig? = null): WebSearchResult {
        return searchWeb(WebSearchRequest(query = query), config)
    }
}

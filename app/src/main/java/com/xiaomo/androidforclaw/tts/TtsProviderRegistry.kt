package com.xiaomo.androidforclaw.tts

/**
 * OpenClaw module: tts
 * Source: OpenClaw/src/tts/provider-registry.ts
 *
 * Thread-safe registry for SpeechProviderPlugin instances.
 * Supports lookup by canonical ID or alias.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import java.util.concurrent.ConcurrentHashMap

object TtsProviderRegistry {

    private val providers = ConcurrentHashMap<String, SpeechProviderPlugin>()

    /** Register a provider; also indexes all its aliases. */
    fun register(provider: SpeechProviderPlugin) {
        providers[provider.id] = provider
        provider.aliases.forEach { alias -> providers[alias.lowercase()] = provider }
    }

    /** Remove a provider and its alias entries. */
    fun unregister(providerId: String) {
        val provider = providers.remove(providerId)
        provider?.aliases?.forEach { providers.remove(it.lowercase()) }
    }

    /**
     * Canonicalize a free-form provider string to the registered canonical ID.
     * Returns null when no match is found.
     */
    fun canonicalizeSpeechProviderId(
        providerId: String?,
        config: OpenClawConfig? = null
    ): SpeechProviderId? {
        if (providerId == null) return null
        val normalized = providerId.lowercase().trim()
        // Direct match
        providers[normalized]?.let { return it.id }
        // Alias scan
        val byAlias = providers.values.find { plugin ->
            plugin.aliases.any { it.lowercase() == normalized }
        }
        return byAlias?.id
    }

    /** List all distinct registered providers (de-duplicates alias entries). */
    fun listSpeechProviders(config: OpenClawConfig? = null): List<SpeechProviderPlugin> {
        return providers.values.distinctBy { it.id }
    }

    /**
     * Resolve a provider by ID. When [providerId] is null, returns the first
     * registered provider (if any) as a sensible default.
     */
    fun getSpeechProvider(
        providerId: String?,
        config: OpenClawConfig? = null
    ): SpeechProviderPlugin? {
        if (providerId == null) return providers.values.firstOrNull()
        val canonical = canonicalizeSpeechProviderId(providerId, config) ?: return null
        return providers[canonical]
    }
}

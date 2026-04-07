package com.xiaomo.androidforclaw.imagegeneration

/**
 * OpenClaw module: image-generation
 * Source: OpenClaw/src/image-generation/provider-registry.ts
 *
 * Registry for image generation providers.
 * Maintains canonical-id and alias maps with case-insensitive lookup.
 * Plugin providers are resolved via [resolvePluginCapabilityProviders] stub.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------------------
// Builtin providers — empty; concrete providers register at app startup.
// ---------------------------------------------------------------------------

private val BUILTIN_IMAGE_GENERATION_PROVIDERS: List<ImageGenerationProvider> = emptyList()

// ---------------------------------------------------------------------------
// Internal maps
// ---------------------------------------------------------------------------

/** Canonical id -> provider */
private val canonicalMap = ConcurrentHashMap<String, ImageGenerationProvider>()

/** Alias (lowercase) -> provider */
private val aliasMap = ConcurrentHashMap<String, ImageGenerationProvider>()

// ---------------------------------------------------------------------------
// Normalisation
// ---------------------------------------------------------------------------

/**
 * Normalize a provider id for lookup: lowercase + trim.
 */
fun normalizeImageGenerationProviderId(id: String): String = id.trim().lowercase()

// ---------------------------------------------------------------------------
// Internal: build maps
// ---------------------------------------------------------------------------

private fun buildProviderMaps(providers: List<ImageGenerationProvider>) {
    for (provider in providers) {
        canonicalMap[provider.id] = provider
        for (alias in provider.aliases) {
            aliasMap[normalizeImageGenerationProviderId(alias)] = provider
        }
    }
}

// ---------------------------------------------------------------------------
// Plugin capability provider resolution (stub)
// ---------------------------------------------------------------------------

/**
 * Resolve plugin-contributed image-generation providers.
 *
 * Stub: returns empty list. To be implemented when the plugin SDK
 * supports capability provider registration.
 */
@Suppress("UNUSED_PARAMETER")
fun resolvePluginImageGenerationProviders(cfg: OpenClawConfig?): List<ImageGenerationProvider> {
    return emptyList()
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Register an image generation provider (canonical id + aliases).
 */
fun registerImageGenerationProvider(provider: ImageGenerationProvider) {
    canonicalMap[provider.id] = provider
    for (alias in provider.aliases) {
        aliasMap[normalizeImageGenerationProviderId(alias)] = provider
    }
}

/**
 * Unregister an image generation provider by id.
 */
fun unregisterImageGenerationProvider(providerId: String) {
    val provider = canonicalMap.remove(providerId)
    provider?.aliases?.forEach { aliasMap.remove(normalizeImageGenerationProviderId(it)) }
}

/**
 * List all registered image generation providers (builtin + plugin + manually registered).
 * Deduplicates by canonical [ImageGenerationProvider.id].
 */
fun listImageGenerationProviders(cfg: OpenClawConfig? = null): List<ImageGenerationProvider> {
    val all = mutableMapOf<String, ImageGenerationProvider>()

    // Builtins
    for (p in BUILTIN_IMAGE_GENERATION_PROVIDERS) {
        all[p.id] = p
    }

    // Plugin providers
    for (p in resolvePluginImageGenerationProviders(cfg)) {
        all[p.id] = p
    }

    // Manually registered (highest priority)
    for ((id, p) in canonicalMap) {
        all[id] = p
    }

    return all.values.toList()
}

/**
 * Get a specific image generation provider by id or alias.
 *
 * Lookup order:
 * 1. Canonical id (exact match)
 * 2. Alias (case-insensitive)
 * 3. Canonical id (case-insensitive)
 *
 * Returns `null` if no match.
 */
fun getImageGenerationProvider(providerId: String, cfg: OpenClawConfig? = null): ImageGenerationProvider? {
    // Exact canonical
    canonicalMap[providerId]?.let { return it }

    val normalized = normalizeImageGenerationProviderId(providerId)

    // Alias
    aliasMap[normalized]?.let { return it }

    // Case-insensitive canonical
    for ((id, p) in canonicalMap) {
        if (id.lowercase() == normalized) return p
    }

    // Try plugin providers
    for (p in resolvePluginImageGenerationProviders(cfg)) {
        if (p.id == providerId || p.id.lowercase() == normalized) return p
        if (p.aliases.any { normalizeImageGenerationProviderId(it) == normalized }) return p
    }

    return null
}

package com.xiaomo.androidforclaw.mediaunderstanding

/**
 * OpenClaw module: media-understanding
 * Source: OpenClaw/src/media-understanding/provider-registry.ts
 *
 * Registry for media understanding providers.
 * Supports registration, lookup by id/alias, and building a full provider map.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------------------
// Internal state
// ---------------------------------------------------------------------------

private val registeredProviders = ConcurrentHashMap<String, MediaUnderstandingProvider>()

// ---------------------------------------------------------------------------
// Normalisation
// ---------------------------------------------------------------------------

/**
 * Normalize a media understanding provider id: lowercase + trim.
 */
fun normalizeMediaProviderId(id: String): String = id.trim().lowercase()

// ---------------------------------------------------------------------------
// Registration
// ---------------------------------------------------------------------------

/**
 * Register a media understanding provider.
 * Both the canonical id and all aliases are indexed.
 */
fun registerMediaUnderstandingProvider(provider: MediaUnderstandingProvider) {
    registeredProviders[provider.id] = provider
    for (alias in provider.aliases) {
        registeredProviders[normalizeMediaProviderId(alias)] = provider
    }
}

/**
 * Unregister a media understanding provider by canonical id.
 */
fun unregisterMediaUnderstandingProvider(providerId: String) {
    val provider = registeredProviders.remove(providerId)
    provider?.aliases?.forEach { registeredProviders.remove(normalizeMediaProviderId(it)) }
}

// ---------------------------------------------------------------------------
// Lookup
// ---------------------------------------------------------------------------

/**
 * Get a specific media understanding provider by id or alias.
 *
 * Tries exact id first, then normalized alias, then case-insensitive canonical.
 *
 * @param id       Provider id or alias.
 * @param registry Optional override; if null, uses the global registry.
 */
fun getMediaUnderstandingProvider(
    id: String,
    registry: Map<String, MediaUnderstandingProvider>? = null
): MediaUnderstandingProvider? {
    val source = registry ?: registeredProviders

    // Exact match
    source[id]?.let { return it }

    // Normalized match
    val normalized = normalizeMediaProviderId(id)
    source[normalized]?.let { return it }

    // Case-insensitive canonical search
    for ((key, p) in source) {
        if (key.lowercase() == normalized) return p
    }

    return null
}

// ---------------------------------------------------------------------------
// Full registry construction
// ---------------------------------------------------------------------------

/**
 * Build a snapshot of all registered media understanding providers.
 * Keys are canonical [MediaUnderstandingProvider.id], values are providers.
 * Deduplicates by id.
 */
fun buildProviderRegistry(): Map<String, MediaUnderstandingProvider> {
    val result = mutableMapOf<String, MediaUnderstandingProvider>()
    for ((_, provider) in registeredProviders) {
        result[provider.id] = provider
    }
    return result
}

/**
 * List all registered providers for a specific capability.
 */
fun listMediaUnderstandingProviders(
    capability: MediaUnderstandingCapability? = null,
    cfg: OpenClawConfig? = null
): List<MediaUnderstandingProvider> {
    val all = buildProviderRegistry().values
    return if (capability != null) {
        all.filter { capability in it.capabilities }
    } else {
        all.toList()
    }
}

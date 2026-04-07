package com.xiaomo.androidforclaw.videogeneration

/**
 * OpenClaw module: video-generation
 * Source: OpenClaw/src/video-generation/provider-registry.ts
 *
 * Registry for video generation providers.
 * Maintains canonical-id and alias maps with case-insensitive lookup.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------------------
// Builtin providers -- empty; concrete providers register at app startup.
// ---------------------------------------------------------------------------

private val BUILTIN_VIDEO_GENERATION_PROVIDERS: List<VideoGenerationProvider> = emptyList()

// ---------------------------------------------------------------------------
// Internal maps
// ---------------------------------------------------------------------------

private val canonicalMap = ConcurrentHashMap<String, VideoGenerationProvider>()
private val aliasMap = ConcurrentHashMap<String, VideoGenerationProvider>()

// ---------------------------------------------------------------------------
// Normalisation
// ---------------------------------------------------------------------------

fun normalizeVideoGenerationProviderId(id: String): String = id.trim().lowercase()

// ---------------------------------------------------------------------------
// Plugin stub
// ---------------------------------------------------------------------------

@Suppress("UNUSED_PARAMETER")
fun resolvePluginVideoGenerationProviders(cfg: OpenClawConfig?): List<VideoGenerationProvider> {
    return emptyList()
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

fun registerVideoGenerationProvider(provider: VideoGenerationProvider) {
    canonicalMap[provider.id] = provider
    for (alias in provider.aliases) {
        aliasMap[normalizeVideoGenerationProviderId(alias)] = provider
    }
}

fun unregisterVideoGenerationProvider(providerId: String) {
    val provider = canonicalMap.remove(providerId)
    provider?.aliases?.forEach { aliasMap.remove(normalizeVideoGenerationProviderId(it)) }
}

fun listVideoGenerationProviders(cfg: OpenClawConfig? = null): List<VideoGenerationProvider> {
    val all = mutableMapOf<String, VideoGenerationProvider>()

    for (p in BUILTIN_VIDEO_GENERATION_PROVIDERS) all[p.id] = p
    for (p in resolvePluginVideoGenerationProviders(cfg)) all[p.id] = p
    for ((id, p) in canonicalMap) all[id] = p

    return all.values.toList()
}

fun getVideoGenerationProvider(providerId: String, cfg: OpenClawConfig? = null): VideoGenerationProvider? {
    canonicalMap[providerId]?.let { return it }

    val normalized = normalizeVideoGenerationProviderId(providerId)

    aliasMap[normalized]?.let { return it }

    for ((id, p) in canonicalMap) {
        if (id.lowercase() == normalized) return p
    }

    for (p in resolvePluginVideoGenerationProviders(cfg)) {
        if (p.id == providerId || p.id.lowercase() == normalized) return p
        if (p.aliases.any { normalizeVideoGenerationProviderId(it) == normalized }) return p
    }

    return null
}

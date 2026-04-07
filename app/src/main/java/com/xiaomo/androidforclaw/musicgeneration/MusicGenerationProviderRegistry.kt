package com.xiaomo.androidforclaw.musicgeneration

/**
 * OpenClaw module: music-generation
 * Source: OpenClaw/src/music-generation/provider-registry.ts
 *
 * Registry for music generation providers.
 * Maintains canonical-id and alias maps with case-insensitive lookup.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------------------
// Builtin providers -- empty; concrete providers register at app startup.
// ---------------------------------------------------------------------------

private val BUILTIN_MUSIC_GENERATION_PROVIDERS: List<MusicGenerationProvider> = emptyList()

// ---------------------------------------------------------------------------
// Internal maps
// ---------------------------------------------------------------------------

private val canonicalMap = ConcurrentHashMap<String, MusicGenerationProvider>()
private val aliasMap = ConcurrentHashMap<String, MusicGenerationProvider>()

// ---------------------------------------------------------------------------
// Normalisation
// ---------------------------------------------------------------------------

fun normalizeMusicGenerationProviderId(id: String): String = id.trim().lowercase()

// ---------------------------------------------------------------------------
// Plugin stub
// ---------------------------------------------------------------------------

@Suppress("UNUSED_PARAMETER")
fun resolvePluginMusicGenerationProviders(cfg: OpenClawConfig?): List<MusicGenerationProvider> {
    return emptyList()
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

fun registerMusicGenerationProvider(provider: MusicGenerationProvider) {
    canonicalMap[provider.id] = provider
    for (alias in provider.aliases) {
        aliasMap[normalizeMusicGenerationProviderId(alias)] = provider
    }
}

fun unregisterMusicGenerationProvider(providerId: String) {
    val provider = canonicalMap.remove(providerId)
    provider?.aliases?.forEach { aliasMap.remove(normalizeMusicGenerationProviderId(it)) }
}

fun listMusicGenerationProviders(cfg: OpenClawConfig? = null): List<MusicGenerationProvider> {
    val all = mutableMapOf<String, MusicGenerationProvider>()

    for (p in BUILTIN_MUSIC_GENERATION_PROVIDERS) all[p.id] = p
    for (p in resolvePluginMusicGenerationProviders(cfg)) all[p.id] = p
    for ((id, p) in canonicalMap) all[id] = p

    return all.values.toList()
}

fun getMusicGenerationProvider(providerId: String, cfg: OpenClawConfig? = null): MusicGenerationProvider? {
    canonicalMap[providerId]?.let { return it }

    val normalized = normalizeMusicGenerationProviderId(providerId)

    aliasMap[normalized]?.let { return it }

    for ((id, p) in canonicalMap) {
        if (id.lowercase() == normalized) return p
    }

    for (p in resolvePluginMusicGenerationProviders(cfg)) {
        if (p.id == providerId || p.id.lowercase() == normalized) return p
        if (p.aliases.any { normalizeMusicGenerationProviderId(it) == normalized }) return p
    }

    return null
}

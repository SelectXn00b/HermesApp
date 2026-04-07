package com.xiaomo.androidforclaw.plugins

/**
 * OpenClaw module: plugins
 * Source: OpenClaw/src/plugins/cache-controls.ts
 *
 * Plugin cache TTL and enablement controls.
 */

object PluginCacheControls {

    const val DEFAULT_PLUGIN_DISCOVERY_CACHE_MS = 1000L
    const val DEFAULT_PLUGIN_MANIFEST_CACHE_MS = 1000L

    /**
     * Resolve cache TTL from a raw string value.
     * Aligned with TS resolvePluginCacheMs().
     */
    fun resolvePluginCacheMs(rawValue: String?, defaultMs: Long): Long {
        val raw = rawValue?.trim()
        if (raw.isNullOrEmpty() || raw == "0") {
            return if (raw == "0") 0 else defaultMs
        }
        val parsed = raw.toLongOrNull() ?: return defaultMs
        return maxOf(0, parsed)
    }

    /**
     * Resolve the snapshot cache TTL.
     * Aligned with TS resolvePluginSnapshotCacheTtlMs().
     */
    fun resolveSnapshotCacheTtlMs(
        discoveryOverrideMs: String? = null,
        manifestOverrideMs: String? = null,
    ): Long {
        val discoveryCacheMs = resolvePluginCacheMs(
            discoveryOverrideMs,
            DEFAULT_PLUGIN_DISCOVERY_CACHE_MS,
        )
        val manifestCacheMs = resolvePluginCacheMs(
            manifestOverrideMs,
            DEFAULT_PLUGIN_MANIFEST_CACHE_MS,
        )
        return minOf(discoveryCacheMs, manifestCacheMs)
    }

    /**
     * Check if plugin snapshot caching should be used.
     * Aligned with TS shouldUsePluginSnapshotCache().
     */
    fun shouldUsePluginSnapshotCache(
        disableDiscoveryCache: Boolean = false,
        disableManifestCache: Boolean = false,
        discoveryCacheMsOverride: String? = null,
        manifestCacheMsOverride: String? = null,
    ): Boolean {
        if (disableDiscoveryCache) return false
        if (disableManifestCache) return false
        if (discoveryCacheMsOverride?.trim() == "0") return false
        if (manifestCacheMsOverride?.trim() == "0") return false
        return true
    }
}

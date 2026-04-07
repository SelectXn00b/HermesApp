package com.xiaomo.androidforclaw.plugins

import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.pluginsdk.PluginManifest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * OpenClaw module: plugins
 * Source: OpenClaw/src/plugins/loader.ts
 *
 * Discovers, validates, and loads plugins. Manages an LRU plugin cache.
 * Android adaptation: uses bundled manifests + APK/skill discovery
 * instead of filesystem scanning.
 */

// ---------------------------------------------------------------------------
// Cache
// ---------------------------------------------------------------------------
private const val MAX_PLUGIN_REGISTRY_CACHE_ENTRIES = 128

data class CachedPluginState(
    val snapshot: PluginRegistrySnapshot,
    val createdAt: Long = System.currentTimeMillis(),
)

// ---------------------------------------------------------------------------
// Load options
// ---------------------------------------------------------------------------
data class PluginLoadOptions(
    val config: Map<String, Any?>? = null,
    val workspaceDir: String? = null,
    val logger: PluginLogger? = null,
    val cache: Boolean = true,
    val mode: PluginRegistrationMode = PluginRegistrationMode.FULL,
    val onlyPluginIds: List<String>? = null,
    val activate: Boolean = true,
    val loadModules: Boolean = true,
    val throwOnLoadError: Boolean = false,
)

// ---------------------------------------------------------------------------
// Load failure error
// ---------------------------------------------------------------------------
class PluginLoadFailureError(
    val pluginIds: List<String>,
    val registry: PluginRegistrySnapshot,
) : Exception(
    "plugin load failed: ${pluginIds.joinToString("; ")}"
)

// ---------------------------------------------------------------------------
// Plugin Loader
// ---------------------------------------------------------------------------
object PluginLoader {

    private const val TAG = "PluginLoader"

    /** In-memory list of known/bundled plugin manifests. */
    private val bundledManifests = CopyOnWriteArrayList<PluginManifest>()

    /** In-memory list of known/bundled full manifests (parsed from JSON). */
    private val bundledFullManifests = CopyOnWriteArrayList<PluginManifestFull>()

    /** Registry cache keyed by config fingerprint. */
    private val registryCache = ConcurrentHashMap<String, CachedPluginState>()

    /** Plugin IDs imported in this process lifetime (for diagnostics). */
    private val importedPluginIds = ConcurrentHashMap.newKeySet<String>()

    // -----------------------------------------------------------------------
    // Bundled manifest registration
    // -----------------------------------------------------------------------

    fun registerBundledManifest(manifest: PluginManifest) {
        bundledManifests.add(manifest)
    }

    fun registerBundledFullManifest(manifest: PluginManifestFull) {
        bundledFullManifests.add(manifest)
        // Register alias lookup entries
        PluginIdNormalizer.registerAliases(
            manifest.id,
            buildList {
                addAll(manifest.providers.orEmpty())
                addAll(manifest.legacyPluginIds.orEmpty())
            }
        )
    }

    // -----------------------------------------------------------------------
    // Discovery  (aligned with TS discoverOpenClawPlugins)
    // -----------------------------------------------------------------------

    suspend fun discoverPlugins(): List<PluginManifest> {
        return bundledManifests.toList()
    }

    suspend fun discoverFullManifests(): List<PluginManifestFull> {
        return bundledFullManifests.toList()
    }

    // -----------------------------------------------------------------------
    // Load  (aligned with TS loadOpenClawPlugins / plugin loader)
    // -----------------------------------------------------------------------

    suspend fun loadPlugins(options: PluginLoadOptions = PluginLoadOptions()): PluginRegistrySnapshot {
        val cacheKey = buildCacheKey(options)
        if (options.cache) {
            val cached = registryCache[cacheKey]
            if (cached != null) {
                PluginRegistry.setActive(cached.snapshot, cacheKey)
                return cached.snapshot
            }
        }

        val logger = options.logger ?: defaultLogger()
        val builder = PluginRegistryBuilder()
        val normalizedConfig = PluginActivationResolver.normalizePluginsConfig(
            @Suppress("UNCHECKED_CAST")
            (options.config?.get("plugins") as? Map<String, Any?>)
        )
        val activationSource = PluginActivationResolver.createActivationSource(
            options.config, normalizedConfig
        )

        val manifests = discoverFullManifests()
        for (manifest in manifests) {
            val pluginId = manifest.id

            // Filter by onlyPluginIds if specified
            if (options.onlyPluginIds != null && pluginId !in options.onlyPluginIds) continue

            // Resolve activation state
            val enabledByDefault = manifest.enabledByDefault
            val activation = PluginActivationResolver.resolveActivationState(
                id = pluginId,
                origin = PluginOrigin.BUNDLED,
                config = normalizedConfig,
                rootConfig = options.config,
                enabledByDefault = enabledByDefault,
                activationSource = activationSource,
            )

            if (!activation.enabled && options.activate) {
                builder.addPlugin(
                    PluginRecord(
                        id = pluginId,
                        name = manifest.name,
                        origin = PluginOrigin.BUNDLED,
                        status = PluginRecordStatus.DISABLED,
                        manifest = manifest,
                        enabledByDefault = enabledByDefault,
                        activationState = activation,
                        kind = manifest.kind,
                        channels = manifest.channels.orEmpty(),
                        providers = manifest.providers.orEmpty(),
                        skills = manifest.skills.orEmpty(),
                    )
                )
                continue
            }

            // Validate config schema
            val entryConfig = normalizedConfig.entries[pluginId]?.config
            val validationErrors = if (entryConfig != null && manifest.configSchema.isNotEmpty()) {
                PluginSchemaValidator.validate(manifest.configSchema, pluginId, entryConfig)
            } else emptyList()

            if (validationErrors.isNotEmpty()) {
                logger.warn("plugin $pluginId: config validation errors: ${validationErrors.joinToString("; ")}")
                builder.addPlugin(
                    PluginRecord(
                        id = pluginId,
                        name = manifest.name,
                        origin = PluginOrigin.BUNDLED,
                        status = PluginRecordStatus.ERROR,
                        error = validationErrors.joinToString("; "),
                        manifest = manifest,
                        activationState = activation,
                    )
                )
                continue
            }

            importedPluginIds.add(pluginId)
            builder.addPlugin(
                PluginRecord(
                    id = pluginId,
                    name = manifest.name,
                    origin = PluginOrigin.BUNDLED,
                    status = PluginRecordStatus.LOADED,
                    manifest = manifest,
                    enabledByDefault = enabledByDefault,
                    activationState = activation,
                    kind = manifest.kind,
                    channels = manifest.channels.orEmpty(),
                    providers = manifest.providers.orEmpty(),
                    skills = manifest.skills.orEmpty(),
                )
            )

            logger.info("plugin loaded: $pluginId")
        }

        val snapshot = builder.toSnapshot()

        // Enforce throwOnLoadError
        if (options.throwOnLoadError) {
            val failedIds = snapshot.plugins
                .filter { it.status == PluginRecordStatus.ERROR }
                .map { it.id }
            if (failedIds.isNotEmpty()) {
                throw PluginLoadFailureError(failedIds, snapshot)
            }
        }

        // Cache the result
        if (options.cache) {
            evictCacheIfNeeded()
            registryCache[cacheKey] = CachedPluginState(snapshot)
        }

        PluginRegistry.setActive(snapshot, cacheKey)
        return snapshot
    }

    // -----------------------------------------------------------------------
    // Legacy API
    // -----------------------------------------------------------------------

    suspend fun loadPlugin(manifest: PluginManifest): PluginInfo {
        val errors = validateManifest(manifest)
        if (errors.isNotEmpty()) {
            val info = PluginInfo(
                manifest = manifest,
                state = PluginState.ERROR,
                errorMessage = errors.joinToString("; ")
            )
            PluginRegistry.register(info)
            return info
        }
        val info = PluginInfo(
            manifest = manifest,
            state = PluginState.LOADED,
            loadedAt = System.currentTimeMillis()
        )
        PluginRegistry.register(info)
        return info
    }

    suspend fun unloadPlugin(pluginId: String): Boolean {
        PluginRegistry.get(pluginId) ?: return false
        PluginRegistry.unregister(pluginId)
        return true
    }

    fun validateManifest(manifest: PluginManifest): List<String> {
        val errors = mutableListOf<String>()
        if (manifest.id.isBlank()) errors.add("Plugin ID must not be blank")
        if (manifest.name.isBlank()) errors.add("Plugin name must not be blank")
        if (manifest.version.isBlank()) errors.add("Plugin version must not be blank")
        return errors
    }

    // -----------------------------------------------------------------------
    // Cache management (aligned with TS LRU cache pattern)
    // -----------------------------------------------------------------------

    fun clearCache() {
        registryCache.clear()
        importedPluginIds.clear()
    }

    fun listImportedPluginIds(): List<String> {
        val ids = mutableSetOf<String>()
        ids.addAll(importedPluginIds)
        PluginRegistry.getActive()?.plugins?.forEach { plugin ->
            if (plugin.status == PluginRecordStatus.LOADED && plugin.format != PluginFormat.BUNDLE) {
                ids.add(plugin.id)
            }
        }
        return ids.sorted()
    }

    private fun evictCacheIfNeeded() {
        if (registryCache.size < MAX_PLUGIN_REGISTRY_CACHE_ENTRIES) return
        // Simple eviction: remove oldest entry
        val oldest = registryCache.entries
            .minByOrNull { it.value.createdAt }
        if (oldest != null) {
            registryCache.remove(oldest.key)
        }
    }

    private fun buildCacheKey(options: PluginLoadOptions): String {
        return buildString {
            append(options.workspaceDir ?: "")
            append("::")
            append(options.mode.value)
            append("::")
            append(options.onlyPluginIds?.sorted()?.joinToString(",") ?: "all")
        }
    }

    private fun defaultLogger(): PluginLogger = object : PluginLogger {
        override fun info(message: String) { Log.i(TAG, message) }
        override fun warn(message: String) { Log.w(TAG, message) }
        override fun error(message: String) { Log.e(TAG, message) }
        override fun debug(message: String) { Log.d(TAG, message) }
    }
}

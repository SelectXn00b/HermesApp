package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/runtime.ts (main runtime re-export surface)
 * - src/plugin-sdk/process-runtime.ts (process-scoped plugin runtime)
 * - src/plugin-sdk/lazy-runtime.ts (lazy loading facade)
 * - src/plugin-sdk/facade-runtime.ts (jiti module loading → skip)
 *
 * SDK entry point for plugin authors to register capabilities.
 * Android adaptation:
 * - Direct registry instead of jiti/ESM module loading
 * - Process-scoped runtime via resolveProcessScopedMap
 * - Lazy facade loading replaced with direct instantiation
 */

import com.xiaomo.androidforclaw.plugins.PluginInfo
import com.xiaomo.androidforclaw.plugins.PluginRegistry
import com.xiaomo.androidforclaw.plugins.PluginState
import java.util.concurrent.ConcurrentHashMap

// ---------- Plugin SDK Runtime ----------

/**
 * SDK runtime: manages plugin registration, lifecycle hooks, and runtime state.
 * Aligned with TS plugin-sdk runtime + process-runtime.
 */
object PluginSdkRuntime {

    private val hooks = ConcurrentHashMap<String, PluginLifecycleHook>()
    private val entries = ConcurrentHashMap<String, PluginEntry>()

    // ---------- Registration ----------

    /**
     * Register a plugin from its manifest and optional lifecycle hook.
     * Aligned with TS registerPlugin.
     */
    fun registerPlugin(manifest: PluginManifest, hook: PluginLifecycleHook? = null) {
        require(manifest.id.isNotBlank()) { "Plugin ID must not be blank" }
        require(manifest.name.isNotBlank()) { "Plugin name must not be blank" }

        val info = PluginInfo(
            manifest = manifest,
            state = PluginState.LOADED,
            capabilities = resolveCapabilities(manifest),
            loadedAt = System.currentTimeMillis(),
        )
        PluginRegistry.register(info)

        if (hook != null) {
            hooks[manifest.id] = hook
        }
    }

    /**
     * Register a plugin from a PluginEntry definition.
     * Aligned with TS definePluginEntry activation path.
     */
    fun registerEntry(entry: PluginEntry) {
        registerPlugin(entry.manifest)
        entries[entry.manifest.id] = entry
    }

    /**
     * Unregister a plugin by ID.
     * Aligned with TS unregisterPlugin.
     */
    fun unregisterPlugin(pluginId: String) {
        hooks.remove(pluginId)
        entries.remove(pluginId)
        PluginRegistry.unregister(pluginId)
    }

    // ---------- Queries ----------

    fun isPluginRegistered(pluginId: String): Boolean =
        PluginRegistry.isRegistered(pluginId)

    fun getPluginManifest(pluginId: String): PluginManifest? =
        PluginRegistry.get(pluginId)?.manifest

    fun getPluginEntry(pluginId: String): PluginEntry? =
        entries[pluginId]

    fun getLifecycleHook(pluginId: String): PluginLifecycleHook? =
        hooks[pluginId]

    fun listRegisteredPluginIds(): List<String> =
        PluginRegistry.listAll().map { it.manifest.id }

    // ---------- Lifecycle ----------

    /**
     * Activate a registered plugin entry (calls onActivate if present).
     */
    suspend fun activatePlugin(pluginId: String, context: PluginContext) {
        val entry = entries[pluginId]
        entry?.onActivate?.invoke(context)
        hooks[pluginId]?.onLoad(context)
    }

    /**
     * Deactivate a registered plugin entry (calls onDeactivate if present).
     */
    suspend fun deactivatePlugin(pluginId: String, context: PluginContext) {
        val entry = entries[pluginId]
        entry?.onDeactivate?.invoke(context)
        hooks[pluginId]?.onUnload(context)
    }

    /**
     * Clear all registered plugins.
     */
    fun clear() {
        hooks.clear()
        entries.clear()
        PluginRegistry.clear()
    }
}

// ---------- Lazy Runtime Facade ----------

/**
 * Lazy facade: defers plugin loading until first access.
 * Android: direct registration replaces TS jiti ESM loading.
 * Aligned with TS lazy-runtime.ts pattern.
 */
class LazyPluginFacade(
    private val loader: () -> PluginEntry,
) {
    @Volatile
    private var cached: PluginEntry? = null

    fun get(): PluginEntry {
        if (cached == null) {
            synchronized(this) {
                if (cached == null) {
                    cached = loader()
                }
            }
        }
        return cached!!
    }

    fun isLoaded(): Boolean = cached != null

    fun clear() {
        synchronized(this) {
            cached = null
        }
    }
}

// ---------- Process-Scoped Runtime ----------

/**
 * Process-scoped plugin runtime store.
 * Aligned with TS process-runtime.ts process-scoped singleton pattern.
 */
object ProcessPluginRuntime {
    private val store = ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T> getOrCreate(key: String, factory: () -> T): T {
        return store.getOrPut(key) { factory() as Any } as T
    }

    fun remove(key: String) {
        store.remove(key)
    }

    fun clear() {
        store.clear()
    }
}

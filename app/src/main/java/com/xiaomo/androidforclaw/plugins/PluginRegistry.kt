package com.xiaomo.androidforclaw.plugins

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * OpenClaw module: plugins
 * Source: OpenClaw/src/plugins/registry.ts
 *
 * Central plugin registry. Holds all loaded plugins, their registration
 * metadata (tools, channels, providers, hooks, services, http routes), and
 * provides query methods.
 *
 * Aligned with TS PluginRegistry + createPluginRegistry().
 */

// ---------------------------------------------------------------------------
// Registration records (aligned with TS PluginToolRegistration, etc.)
// ---------------------------------------------------------------------------

data class PluginToolRegistration(
    val pluginId: String,
    val pluginName: String? = null,
    val names: List<String> = emptyList(),
    val optional: Boolean = false,
    val source: String = "",
    val rootDir: String? = null,
)

data class PluginHookRegistration(
    val pluginId: String,
    val hookName: PluginHookName,
    val priority: Int = 0,
    val source: String = "",
    val rootDir: String? = null,
)

data class PluginServiceRegistration(
    val pluginId: String,
    val pluginName: String? = null,
    val serviceId: String,
    val source: String = "",
    val rootDir: String? = null,
)

data class PluginChannelRegistration(
    val pluginId: String,
    val pluginName: String? = null,
    val channelId: String,
    val source: String = "",
    val rootDir: String? = null,
)

data class PluginProviderRegistration(
    val pluginId: String,
    val pluginName: String? = null,
    val providerId: String,
    val source: String = "",
    val rootDir: String? = null,
)

data class PluginHttpRouteRegistration(
    val pluginId: String? = null,
    val path: String,
    val auth: String = "none",
    val match: String = "exact",
    val source: String? = null,
)

// ---------------------------------------------------------------------------
// Plugin record  (aligned with TS PluginRecord)
// ---------------------------------------------------------------------------
data class PluginRecord(
    val id: String,
    val name: String? = null,
    val origin: PluginOrigin = PluginOrigin.BUNDLED,
    val format: PluginFormat? = null,
    val bundleFormat: PluginBundleFormat? = null,
    val source: String = "",
    val rootDir: String? = null,
    val status: PluginRecordStatus = PluginRecordStatus.LOADED,
    val error: String? = null,
    val manifest: PluginManifestFull? = null,
    val enabledByDefault: Boolean = false,
    val activationState: PluginActivationState? = null,
    val kind: Any? = null, // PluginKind | List<PluginKind> | null
    val channels: List<String> = emptyList(),
    val providers: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val diagnostics: List<PluginDiagnostic> = emptyList(),
)

enum class PluginRecordStatus(val value: String) {
    LOADED("loaded"),
    ERROR("error"),
    DISABLED("disabled"),
    SKIPPED("skipped");

    companion object {
        fun fromString(raw: String): PluginRecordStatus? =
            entries.find { it.value.equals(raw.trim(), ignoreCase = true) }
    }
}

// ---------------------------------------------------------------------------
// Plugin Registry
// ---------------------------------------------------------------------------
data class PluginRegistrySnapshot(
    val plugins: List<PluginRecord> = emptyList(),
    val tools: List<PluginToolRegistration> = emptyList(),
    val hooks: List<PluginHookRegistration> = emptyList(),
    val services: List<PluginServiceRegistration> = emptyList(),
    val channels: List<PluginChannelRegistration> = emptyList(),
    val providers: List<PluginProviderRegistration> = emptyList(),
    val httpRoutes: List<PluginHttpRouteRegistration> = emptyList(),
    val diagnostics: List<PluginDiagnostic> = emptyList(),
)

/**
 * Mutable plugin registry.
 * Aligned with TS PluginRegistry (the mutable builder form).
 */
class PluginRegistryBuilder {
    val plugins = CopyOnWriteArrayList<PluginRecord>()
    val tools = CopyOnWriteArrayList<PluginToolRegistration>()
    val hooks = CopyOnWriteArrayList<PluginHookRegistration>()
    val services = CopyOnWriteArrayList<PluginServiceRegistration>()
    val channels = CopyOnWriteArrayList<PluginChannelRegistration>()
    val providers = CopyOnWriteArrayList<PluginProviderRegistration>()
    val httpRoutes = CopyOnWriteArrayList<PluginHttpRouteRegistration>()
    val diagnostics = CopyOnWriteArrayList<PluginDiagnostic>()

    fun addPlugin(record: PluginRecord) { plugins.add(record) }
    fun addTool(reg: PluginToolRegistration) { tools.add(reg) }
    fun addHook(reg: PluginHookRegistration) { hooks.add(reg) }
    fun addService(reg: PluginServiceRegistration) { services.add(reg) }
    fun addChannel(reg: PluginChannelRegistration) { channels.add(reg) }
    fun addProvider(reg: PluginProviderRegistration) { providers.add(reg) }
    fun addHttpRoute(reg: PluginHttpRouteRegistration) { httpRoutes.add(reg) }
    fun addDiagnostic(diag: PluginDiagnostic) { diagnostics.add(diag) }

    fun toSnapshot(): PluginRegistrySnapshot = PluginRegistrySnapshot(
        plugins = plugins.toList(),
        tools = tools.toList(),
        hooks = hooks.toList(),
        services = services.toList(),
        channels = channels.toList(),
        providers = providers.toList(),
        httpRoutes = httpRoutes.toList(),
        diagnostics = diagnostics.toList(),
    )
}

fun createEmptyPluginRegistrySnapshot(): PluginRegistrySnapshot = PluginRegistrySnapshot()

// ---------------------------------------------------------------------------
// Singleton registry holder  (aligned with TS runtime.ts state pattern)
// ---------------------------------------------------------------------------
object PluginRegistry {
    @Volatile
    private var activeSnapshot: PluginRegistrySnapshot? = null

    @Volatile
    private var activeVersion: Int = 0

    @Volatile
    private var cacheKey: String? = null

    private val pluginInfoMap = ConcurrentHashMap<String, PluginInfo>()

    // -- snapshot based API --

    fun setActive(snapshot: PluginRegistrySnapshot, cacheKey: String? = null) {
        this.activeSnapshot = snapshot
        this.activeVersion++
        this.cacheKey = cacheKey
    }

    fun getActive(): PluginRegistrySnapshot? = activeSnapshot

    fun requireActive(): PluginRegistrySnapshot {
        return activeSnapshot ?: createEmptyPluginRegistrySnapshot().also { setActive(it) }
    }

    fun getActiveVersion(): Int = activeVersion
    fun getActiveCacheKey(): String? = cacheKey

    // -- legacy PluginInfo-based API (backward compat) --

    fun register(info: PluginInfo) {
        pluginInfoMap[info.manifest.id] = info
    }

    fun unregister(pluginId: String): PluginInfo? = pluginInfoMap.remove(pluginId)

    fun get(pluginId: String): PluginInfo? = pluginInfoMap[pluginId]

    fun listAll(): List<PluginInfo> = pluginInfoMap.values.toList()

    fun listByCapability(capability: PluginCapability): List<PluginInfo> =
        pluginInfoMap.values.filter { capability in it.capabilities }

    fun listActive(): List<PluginInfo> =
        pluginInfoMap.values.filter { it.state == PluginState.ACTIVE }

    fun isRegistered(pluginId: String): Boolean = pluginInfoMap.containsKey(pluginId)

    fun clear() {
        pluginInfoMap.clear()
        activeSnapshot = null
        activeVersion++
        cacheKey = null
    }
}

package com.xiaomo.androidforclaw.plugins

import java.util.concurrent.ConcurrentHashMap

/**
 * OpenClaw module: plugins
 * Source: OpenClaw/src/plugins/config-state.ts
 *
 * Plugin activation state machine: determines whether each plugin is
 * enabled/disabled based on config, denylist, allowlist, slot selection,
 * and bundled defaults.
 */

// ---------------------------------------------------------------------------
// Activation cause
// ---------------------------------------------------------------------------
enum class PluginActivationCause(val value: String) {
    ENABLED_IN_CONFIG("enabled-in-config"),
    BUNDLED_CHANNEL_ENABLED_IN_CONFIG("bundled-channel-enabled-in-config"),
    SELECTED_MEMORY_SLOT("selected-memory-slot"),
    SELECTED_IN_ALLOWLIST("selected-in-allowlist"),
    PLUGINS_DISABLED("plugins-disabled"),
    BLOCKED_BY_DENYLIST("blocked-by-denylist"),
    DISABLED_IN_CONFIG("disabled-in-config"),
    WORKSPACE_DISABLED_BY_DEFAULT("workspace-disabled-by-default"),
    NOT_IN_ALLOWLIST("not-in-allowlist"),
    ENABLED_BY_EFFECTIVE_CONFIG("enabled-by-effective-config"),
    BUNDLED_CHANNEL_CONFIGURED("bundled-channel-configured"),
    BUNDLED_DEFAULT_ENABLEMENT("bundled-default-enablement"),
    BUNDLED_DISABLED_BY_DEFAULT("bundled-disabled-by-default");

    companion object {
        private val REASON_MAP = mapOf(
            ENABLED_IN_CONFIG to "enabled in config",
            BUNDLED_CHANNEL_ENABLED_IN_CONFIG to "channel enabled in config",
            SELECTED_MEMORY_SLOT to "selected memory slot",
            SELECTED_IN_ALLOWLIST to "selected in allowlist",
            PLUGINS_DISABLED to "plugins disabled",
            BLOCKED_BY_DENYLIST to "blocked by denylist",
            DISABLED_IN_CONFIG to "disabled in config",
            WORKSPACE_DISABLED_BY_DEFAULT to "workspace plugin (disabled by default)",
            NOT_IN_ALLOWLIST to "not in allowlist",
            ENABLED_BY_EFFECTIVE_CONFIG to "enabled by effective config",
            BUNDLED_CHANNEL_CONFIGURED to "channel configured",
            BUNDLED_DEFAULT_ENABLEMENT to "bundled default enablement",
            BUNDLED_DISABLED_BY_DEFAULT to "bundled (disabled by default)",
        )

        fun reasonFor(cause: PluginActivationCause): String =
            REASON_MAP[cause] ?: cause.value
    }
}

// ---------------------------------------------------------------------------
// Normalized plugins config  (aligned with TS NormalizedPluginsConfig)
// ---------------------------------------------------------------------------
data class NormalizedPluginsConfig(
    val enabled: Boolean = true,
    val allow: List<String> = emptyList(),
    val deny: List<String> = emptyList(),
    val entries: Map<String, PluginEntryConfig> = emptyMap(),
    val slots: PluginSlotsConfig = PluginSlotsConfig(),
)

data class PluginEntryConfig(
    val enabled: Boolean? = null,
    val config: Map<String, Any?>? = null,
)

data class PluginSlotsConfig(
    val memory: String? = null,
    val contextEngine: String? = null,
)

// ---------------------------------------------------------------------------
// Activation config source
// ---------------------------------------------------------------------------
data class PluginActivationConfigSource(
    val plugins: NormalizedPluginsConfig,
    val rootConfig: Map<String, Any?>? = null,
)

// ---------------------------------------------------------------------------
// Plugin ID normalization with bundled alias lookup
// ---------------------------------------------------------------------------
object PluginIdNormalizer {
    /**
     * Bundled plugin alias lookup: maps lowercased ids/provider-ids/legacy-ids
     * to the canonical plugin id. Populated by [PluginManifestRegistry].
     */
    private val aliasLookup = ConcurrentHashMap<String, String>()

    fun registerAlias(alias: String, canonicalId: String) {
        aliasLookup[alias.lowercase()] = canonicalId
    }

    fun registerAliases(canonicalId: String, aliases: Iterable<String>) {
        aliasLookup[canonicalId.lowercase()] = canonicalId
        for (alias in aliases) {
            aliasLookup[alias.lowercase()] = canonicalId
        }
    }

    fun clearAliases() {
        aliasLookup.clear()
    }

    fun normalizePluginId(id: String): String {
        val trimmed = id.trim()
        return aliasLookup[trimmed.lowercase()] ?: trimmed
    }
}

// ---------------------------------------------------------------------------
// Activation state machine  (aligned with TS resolvePluginActivationState)
// ---------------------------------------------------------------------------
object PluginActivationResolver {

    fun normalizePluginsConfig(raw: Map<String, Any?>?): NormalizedPluginsConfig {
        if (raw == null) return NormalizedPluginsConfig()
        val enabled = raw["enabled"] as? Boolean ?: true

        @Suppress("UNCHECKED_CAST")
        val allow = (raw["allow"] as? List<*>)
            ?.mapNotNull { (it as? String)?.trim()?.ifEmpty { null } }
            ?.map { PluginIdNormalizer.normalizePluginId(it) }
            ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        val deny = (raw["deny"] as? List<*>)
            ?.mapNotNull { (it as? String)?.trim()?.ifEmpty { null } }
            ?.map { PluginIdNormalizer.normalizePluginId(it) }
            ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        val entriesRaw = raw["entries"] as? Map<String, Any?> ?: emptyMap()
        val entries = entriesRaw.mapNotNull { (key, value) ->
            val normalizedKey = PluginIdNormalizer.normalizePluginId(key)
            val entryMap = value as? Map<*, *> ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val configMap = entryMap["config"] as? Map<String, Any?>
            normalizedKey to PluginEntryConfig(
                enabled = entryMap["enabled"] as? Boolean,
                config = configMap,
            )
        }.toMap()

        @Suppress("UNCHECKED_CAST")
        val slotsRaw = raw["slots"] as? Map<String, Any?> ?: emptyMap()
        val memorySlot = (slotsRaw["memory"] as? String)?.trim()?.ifEmpty { null }
            ?.let { PluginIdNormalizer.normalizePluginId(it) }
        val contextEngineSlot = (slotsRaw["contextEngine"] as? String)?.trim()?.ifEmpty { null }
            ?.let { PluginIdNormalizer.normalizePluginId(it) }

        return NormalizedPluginsConfig(
            enabled = enabled,
            allow = allow,
            deny = deny,
            entries = entries,
            slots = PluginSlotsConfig(memory = memorySlot, contextEngine = contextEngineSlot),
        )
    }

    fun createActivationSource(
        config: Map<String, Any?>?,
        plugins: NormalizedPluginsConfig? = null,
    ): PluginActivationConfigSource {
        @Suppress("UNCHECKED_CAST")
        val pluginsSection = config?.get("plugins") as? Map<String, Any?>
        return PluginActivationConfigSource(
            plugins = plugins ?: normalizePluginsConfig(pluginsSection),
            rootConfig = config,
        )
    }

    /**
     * Core activation state resolver.
     * Aligned 1:1 with TS resolvePluginActivationState().
     */
    fun resolveActivationState(
        id: String,
        origin: PluginOrigin,
        config: NormalizedPluginsConfig,
        rootConfig: Map<String, Any?>? = null,
        enabledByDefault: Boolean = false,
        activationSource: PluginActivationConfigSource? = null,
        autoEnabledReason: String? = null,
    ): PluginActivationState {
        val source = activationSource ?: createActivationSource(rootConfig, config)
        val explicitSelection = resolveExplicitPluginSelection(id, origin, source.plugins, source.rootConfig)

        // Plugins globally disabled
        if (!config.enabled) {
            return makeState(
                enabled = false, activated = false,
                explicitlyEnabled = explicitSelection.first,
                source = PluginActivationSource.DISABLED,
                cause = PluginActivationCause.PLUGINS_DISABLED,
            )
        }
        // Blocked by denylist
        if (config.deny.contains(id)) {
            return makeState(
                enabled = false, activated = false,
                explicitlyEnabled = explicitSelection.first,
                source = PluginActivationSource.DISABLED,
                cause = PluginActivationCause.BLOCKED_BY_DENYLIST,
            )
        }
        // Explicitly disabled in config
        val entry = config.entries[id]
        if (entry?.enabled == false) {
            return makeState(
                enabled = false, activated = false,
                explicitlyEnabled = explicitSelection.first,
                source = PluginActivationSource.DISABLED,
                cause = PluginActivationCause.DISABLED_IN_CONFIG,
            )
        }
        // Workspace plugins disabled by default unless explicitly allowed
        val explicitlyAllowed = config.allow.contains(id)
        if (origin == PluginOrigin.WORKSPACE && !explicitlyAllowed && entry?.enabled != true) {
            return makeState(
                enabled = false, activated = false,
                explicitlyEnabled = explicitSelection.first,
                source = PluginActivationSource.DISABLED,
                cause = PluginActivationCause.WORKSPACE_DISABLED_BY_DEFAULT,
            )
        }
        // Selected as memory slot
        if (config.slots.memory == id) {
            return makeState(
                enabled = true, activated = true,
                explicitlyEnabled = true,
                source = PluginActivationSource.EXPLICIT,
                cause = PluginActivationCause.SELECTED_MEMORY_SLOT,
            )
        }
        // Bundled channel enabled in config
        if (explicitSelection.second == PluginActivationCause.BUNDLED_CHANNEL_ENABLED_IN_CONFIG) {
            return makeState(
                enabled = true, activated = true,
                explicitlyEnabled = true,
                source = PluginActivationSource.EXPLICIT,
                cause = PluginActivationCause.BUNDLED_CHANNEL_ENABLED_IN_CONFIG,
            )
        }
        // Not in allowlist
        if (config.allow.isNotEmpty() && !explicitlyAllowed) {
            return makeState(
                enabled = false, activated = false,
                explicitlyEnabled = explicitSelection.first,
                source = PluginActivationSource.DISABLED,
                cause = PluginActivationCause.NOT_IN_ALLOWLIST,
            )
        }
        // Explicitly enabled
        if (explicitSelection.first) {
            return makeState(
                enabled = true, activated = true,
                explicitlyEnabled = true,
                source = PluginActivationSource.EXPLICIT,
                cause = explicitSelection.second,
            )
        }
        // Auto-enabled
        if (autoEnabledReason != null) {
            return makeState(
                enabled = true, activated = true,
                explicitlyEnabled = false,
                source = PluginActivationSource.AUTO,
                reason = autoEnabledReason,
            )
        }
        // Entry enabled = true (but not via explicit selection path)
        if (entry?.enabled == true) {
            return makeState(
                enabled = true, activated = true,
                explicitlyEnabled = false,
                source = PluginActivationSource.AUTO,
                cause = PluginActivationCause.ENABLED_BY_EFFECTIVE_CONFIG,
            )
        }
        // Bundled default enablement
        if (origin == PluginOrigin.BUNDLED && enabledByDefault) {
            return makeState(
                enabled = true, activated = true,
                explicitlyEnabled = false,
                source = PluginActivationSource.DEFAULT,
                cause = PluginActivationCause.BUNDLED_DEFAULT_ENABLEMENT,
            )
        }
        // Bundled but disabled by default
        if (origin == PluginOrigin.BUNDLED) {
            return makeState(
                enabled = false, activated = false,
                explicitlyEnabled = false,
                source = PluginActivationSource.DISABLED,
                cause = PluginActivationCause.BUNDLED_DISABLED_BY_DEFAULT,
            )
        }
        // Default: external/installed plugins are enabled
        return makeState(
            enabled = true, activated = true,
            explicitlyEnabled = explicitSelection.first,
            source = PluginActivationSource.DEFAULT,
        )
    }

    private fun resolveExplicitPluginSelection(
        id: String,
        origin: PluginOrigin,
        config: NormalizedPluginsConfig,
        rootConfig: Map<String, Any?>?,
    ): Pair<Boolean, PluginActivationCause?> {
        if (config.entries[id]?.enabled == true) {
            return true to PluginActivationCause.ENABLED_IN_CONFIG
        }
        if (origin == PluginOrigin.BUNDLED && isBundledChannelEnabledByChannelConfig(rootConfig, id)) {
            return true to PluginActivationCause.BUNDLED_CHANNEL_ENABLED_IN_CONFIG
        }
        if (config.slots.memory == id) {
            return true to PluginActivationCause.SELECTED_MEMORY_SLOT
        }
        if (origin != PluginOrigin.BUNDLED && config.allow.contains(id)) {
            return true to PluginActivationCause.SELECTED_IN_ALLOWLIST
        }
        return false to null
    }

    @Suppress("UNCHECKED_CAST")
    fun isBundledChannelEnabledByChannelConfig(
        cfg: Map<String, Any?>?,
        pluginId: String,
    ): Boolean {
        val channels = cfg?.get("channels") as? Map<String, Any?> ?: return false
        val channelEntry = channels[pluginId] as? Map<String, Any?> ?: return false
        return channelEntry.keys.any { it != "enabled" }
    }

    private fun makeState(
        enabled: Boolean,
        activated: Boolean,
        explicitlyEnabled: Boolean,
        source: PluginActivationSource,
        cause: PluginActivationCause? = null,
        reason: String? = null,
    ): PluginActivationState {
        val resolvedReason = reason ?: cause?.let { PluginActivationCause.reasonFor(it) }
        return PluginActivationState(
            enabled = enabled,
            activated = activated,
            explicitlyEnabled = explicitlyEnabled,
            source = source,
            reason = resolvedReason,
        )
    }
}

// ---------------------------------------------------------------------------
// Memory slot decision (aligned with TS resolveMemorySlotDecision)
// ---------------------------------------------------------------------------
data class MemorySlotDecision(
    val enabled: Boolean,
    val reason: String? = null,
    val selected: Boolean = false,
)

fun resolveMemorySlotDecision(
    id: String,
    kind: Any?, // PluginKind | List<PluginKind> | null
    slot: String?, // null = disabled, string = selected id
    selectedId: String?,
): MemorySlotDecision {
    val hasMemoryKind = when (kind) {
        is PluginKind -> kind == PluginKind.MEMORY
        is List<*> -> kind.any { it == PluginKind.MEMORY }
        else -> false
    }
    if (!hasMemoryKind) return MemorySlotDecision(enabled = true)

    val isMultiKind = kind is List<*> && kind.size > 1

    // slot == null means memory is disabled
    if (slot == null) {
        return if (isMultiKind) MemorySlotDecision(enabled = true)
        else MemorySlotDecision(enabled = false, reason = "memory slot disabled")
    }

    if (slot == id) {
        return MemorySlotDecision(enabled = true, selected = true)
    }
    if (slot.isNotEmpty()) {
        return if (isMultiKind) MemorySlotDecision(enabled = true)
        else MemorySlotDecision(enabled = false, reason = "memory slot set to \"$slot\"")
    }

    if (selectedId != null && selectedId != id) {
        return if (isMultiKind) MemorySlotDecision(enabled = true)
        else MemorySlotDecision(enabled = false, reason = "memory slot already filled by \"$selectedId\"")
    }
    return MemorySlotDecision(enabled = true, selected = true)
}

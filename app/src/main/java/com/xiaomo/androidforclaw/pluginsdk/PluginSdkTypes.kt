package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/types.ts (aggregate type surface)
 * - src/plugin-sdk/core.ts (channel plugin helpers, ProviderAuth types)
 * - src/plugin-sdk/index.ts (type re-exports)
 * - src/plugin-sdk/plugin-entry.ts (definePluginEntry)
 *
 * Types for the plugin SDK: manifests, contexts, lifecycle hooks,
 * provider auth, channel metadata, and plugin entry definition.
 * Android adaptation: data classes + interfaces; no TS generic mapped types.
 */

import com.xiaomo.androidforclaw.plugins.PluginCapability
import com.xiaomo.androidforclaw.plugins.PluginHookName
import com.xiaomo.androidforclaw.plugins.PluginLogger
import com.xiaomo.androidforclaw.plugins.PluginState

// ---------- Plugin Manifest ----------

/**
 * Plugin manifest describing a plugin's identity and capabilities.
 * Aligned with TS PluginManifest + OpenClawPluginDefinition.
 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String? = null,
    val author: String? = null,
    val capabilities: List<String> = emptyList(),
    val entryPoint: String? = null,
    val configSchema: Map<String, Any?> = emptyMap(),
    val homepage: String? = null,
    val license: String? = null,
    val keywords: List<String> = emptyList(),
    val repository: String? = null,
)

// ---------- Plugin Context ----------

/**
 * Context passed to plugin lifecycle hooks.
 * Aligned with TS PluginContext / OpenClawPluginContext.
 */
data class PluginContext(
    val pluginId: String,
    val sessionKey: String = "",
    val config: Map<String, Any?> = emptyMap(),
    val workspaceDir: String? = null,
    val stateDir: String? = null,
    val logger: PluginLogger? = null,
)

// ---------- Plugin Lifecycle ----------

/**
 * Plugin lifecycle hook interface.
 * Aligned with TS PluginLifecycleHook.
 */
interface PluginLifecycleHook {
    suspend fun onLoad(context: PluginContext)
    suspend fun onUnload(context: PluginContext)
}

// ---------- Provider Auth Types ----------

/**
 * Provider auth profile type.
 * Aligned with TS ProviderAuthProfileType from core.ts.
 */
enum class ProviderAuthProfileType(val value: String) {
    API_KEY("api_key"),
    OAUTH("oauth"),
    SERVICE_ACCOUNT("service_account"),
    NONE("none");

    companion object {
        fun fromString(s: String?): ProviderAuthProfileType? =
            entries.find { it.value == s?.trim()?.lowercase() }
    }
}

/**
 * Provider auth status.
 * Aligned with TS ProviderAuthStatus.
 */
enum class ProviderAuthStatus(val value: String) {
    CONFIGURED("configured"),
    UNCONFIGURED("unconfigured"),
    EXPIRED("expired"),
    ERROR("error");

    companion object {
        fun fromString(s: String?): ProviderAuthStatus? =
            entries.find { it.value == s?.trim()?.lowercase() }
    }
}

/**
 * Provider auth check result.
 * Aligned with TS ProviderAuthCheckResult.
 */
data class ProviderAuthCheckResult(
    val status: ProviderAuthStatus,
    val profileType: ProviderAuthProfileType? = null,
    val message: String? = null,
    val expiresAt: Long? = null,
)

// ---------- Channel Metadata ----------

/**
 * Chat channel metadata.
 * Aligned with TS ChatChannelMeta from core.ts.
 */
data class ChatChannelMeta(
    val channelId: String,
    val label: String,
    val description: String? = null,
    val icon: String? = null,
    val supportsGroups: Boolean = false,
    val supportsDm: Boolean = true,
    val supportsMedia: Boolean = false,
    val supportsPolls: Boolean = false,
    val supportsThreads: Boolean = false,
    val supportsReactions: Boolean = false,
    val supportsEdits: Boolean = false,
    val supportsDeletes: Boolean = false,
    val supportsVoice: Boolean = false,
    val supportsVideo: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsStickers: Boolean = false,
    val supportsLocations: Boolean = false,
    val supportsContacts: Boolean = false,
    val maxTextLength: Int? = null,
    val maxMediaSize: Long? = null,
    val textChunkLimit: Int? = null,
)

/**
 * Get chat channel meta from a channel's declared capabilities map.
 * Aligned with TS getChatChannelMeta.
 */
fun getChatChannelMeta(
    channelId: String,
    label: String,
    capabilities: Map<String, Boolean> = emptyMap(),
): ChatChannelMeta = ChatChannelMeta(
    channelId = channelId,
    label = label,
    supportsGroups = capabilities["groups"] == true,
    supportsDm = capabilities["dm"] != false,
    supportsMedia = capabilities["media"] == true,
    supportsPolls = capabilities["polls"] == true,
    supportsThreads = capabilities["threads"] == true,
    supportsReactions = capabilities["reactions"] == true,
    supportsEdits = capabilities["edits"] == true,
    supportsDeletes = capabilities["deletes"] == true,
    supportsVoice = capabilities["voice"] == true,
    supportsVideo = capabilities["video"] == true,
    supportsAudio = capabilities["audio"] == true,
    supportsStickers = capabilities["stickers"] == true,
    supportsLocations = capabilities["locations"] == true,
    supportsContacts = capabilities["contacts"] == true,
)

// ---------- Channel Target Prefix ----------

/**
 * Strip the channel target prefix from a string (e.g., "telegram:12345" -> "12345").
 * Aligned with TS stripChannelTargetPrefix.
 */
fun stripChannelTargetPrefix(target: String, channelId: String): String {
    val prefix = "$channelId:"
    return if (target.startsWith(prefix)) target.removePrefix(prefix) else target
}

// ---------- Plugin Entry ----------

/**
 * Plugin entry definition.
 * Aligned with TS definePluginEntry / OpenClawPluginEntry.
 */
data class PluginEntry(
    val manifest: PluginManifest,
    val hooks: List<PluginEntryHook> = emptyList(),
    val tools: List<PluginEntryTool> = emptyList(),
    val channels: List<PluginEntryChannel> = emptyList(),
    val providers: List<PluginEntryProvider> = emptyList(),
    val services: List<PluginEntryService> = emptyList(),
    val onActivate: (suspend (context: PluginContext) -> Unit)? = null,
    val onDeactivate: (suspend (context: PluginContext) -> Unit)? = null,
)

data class PluginEntryHook(
    val hookName: PluginHookName,
    val priority: Int = 0,
    val handler: suspend (event: Map<String, Any?>) -> Map<String, Any?>?,
)

data class PluginEntryTool(
    val name: String,
    val description: String? = null,
    val parameters: Map<String, Any?> = emptyMap(),
    val handler: suspend (args: Map<String, Any?>, context: PluginContext) -> Any?,
)

data class PluginEntryChannel(
    val channelId: String,
    val meta: ChatChannelMeta,
)

data class PluginEntryProvider(
    val providerId: String,
    val label: String? = null,
)

data class PluginEntryService(
    val serviceId: String,
    val handler: suspend (context: PluginContext) -> Unit,
)

/**
 * Define a plugin entry (factory function).
 * Aligned with TS definePluginEntry.
 */
fun definePluginEntry(
    manifest: PluginManifest,
    hooks: List<PluginEntryHook> = emptyList(),
    tools: List<PluginEntryTool> = emptyList(),
    channels: List<PluginEntryChannel> = emptyList(),
    providers: List<PluginEntryProvider> = emptyList(),
    services: List<PluginEntryService> = emptyList(),
    onActivate: (suspend (context: PluginContext) -> Unit)? = null,
    onDeactivate: (suspend (context: PluginContext) -> Unit)? = null,
): PluginEntry = PluginEntry(
    manifest = manifest,
    hooks = hooks,
    tools = tools,
    channels = channels,
    providers = providers,
    services = services,
    onActivate = onActivate,
    onDeactivate = onDeactivate,
)

// ---------- Capability Registration Helpers ----------

/**
 * Check if a plugin has a specific capability declared in its manifest.
 */
fun hasCapability(manifest: PluginManifest, capability: String): Boolean =
    manifest.capabilities.any { it.equals(capability, ignoreCase = true) }

/**
 * Resolve capabilities from manifest capability strings to enum set.
 */
fun resolveCapabilities(manifest: PluginManifest): Set<PluginCapability> {
    return manifest.capabilities.mapNotNull { cap ->
        try {
            PluginCapability.valueOf(cap.uppercase())
        } catch (_: Exception) {
            null
        }
    }.toSet()
}

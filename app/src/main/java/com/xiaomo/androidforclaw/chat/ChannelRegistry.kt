package com.xiaomo.androidforclaw.chat

import com.xiaomo.androidforclaw.plugins.PluginRegistry
import java.util.concurrent.CopyOnWriteArrayList

/**
 * OpenClaw module: channels
 * Source: OpenClaw/src/channels/registry.ts
 *
 * Channel plugin registry: normalizes channel IDs across bundled and
 * external channels, lists registered channels, and resolves channel metadata.
 */

// ---------------------------------------------------------------------------
// Channel meta  (aligned with TS ChatChannelMeta / ChannelMeta)
// ---------------------------------------------------------------------------

data class ChatChannelMeta(
    val id: String,
    val label: String = "",
    val selectionLabel: String? = null,
    val detailLabel: String? = null,
    val docsPath: String = "",
    val docsLabel: String? = null,
    val blurb: String = "",
    val order: Int = Int.MAX_VALUE,
    val aliases: List<String> = emptyList(),
    val markdownCapable: Boolean = false,
    val selectionDocsPrefix: String? = null,
    val selectionDocsOmitLabel: Boolean = false,
    val selectionExtras: List<String>? = null,
)

// ---------------------------------------------------------------------------
// Registered channel entry  (aligned with TS RegisteredChannelPluginEntry)
// ---------------------------------------------------------------------------

data class RegisteredChannelPluginEntry(
    val pluginId: String?,
    val aliases: List<String> = emptyList(),
    val markdownCapable: Boolean = false,
)

// ---------------------------------------------------------------------------
// Channel Registry  (aligned with TS registry.ts)
// ---------------------------------------------------------------------------

object ChannelRegistry {

    /** Extra channel entries registered outside of the plugin system. */
    private val extraEntries = CopyOnWriteArrayList<RegisteredChannelPluginEntry>()

    /** Registered channel metadata. */
    private val channelMetaMap = mutableMapOf<String, ChatChannelMeta>()

    fun registerChannelMeta(meta: ChatChannelMeta) {
        channelMetaMap[meta.id.lowercase()] = meta
    }

    fun registerExtraChannel(entry: RegisteredChannelPluginEntry) {
        extraEntries.add(entry)
    }

    fun clear() {
        extraEntries.clear()
        channelMetaMap.clear()
    }

    // -----------------------------------------------------------------------
    // Channel listing
    // -----------------------------------------------------------------------

    /**
     * List all registered channel plugin entries.
     * Combines entries from the plugin registry snapshot and extra entries.
     */
    private fun listRegisteredChannelPluginEntries(): List<RegisteredChannelPluginEntry> {
        val fromSnapshot = PluginRegistry.getActive()?.channels?.map { ch ->
            RegisteredChannelPluginEntry(
                pluginId = ch.pluginId,
                aliases = emptyList(), // aliases come from meta
            )
        }.orEmpty()
        return fromSnapshot + extraEntries
    }

    private fun findRegisteredEntry(normalizedKey: String): RegisteredChannelPluginEntry? {
        return listRegisteredChannelPluginEntries().find { entry ->
            val id = entry.pluginId?.trim()?.lowercase() ?: ""
            if (id == normalizedKey) return@find true
            entry.aliases.any { it.trim().lowercase() == normalizedKey }
        }
    }

    /**
     * Normalize a channel ID to a canonical channel ID.
     * First tries the built-in chat channel IDs, then registered plugin channels.
     * Aligned with TS normalizeChannelId().
     */
    fun normalizeChannelId(raw: String?): ChatChannelId? {
        return ChatChannelIds.normalizeChatChannelId(raw)
    }

    /**
     * Normalize any channel ID (bundled or external).
     * Aligned with TS normalizeAnyChannelId().
     */
    fun normalizeAnyChannelId(raw: String?): String? {
        val key = raw?.trim()?.lowercase()
        if (key.isNullOrEmpty()) return null
        return findRegisteredEntry(key)?.pluginId
    }

    /**
     * List all registered channel plugin IDs.
     * Aligned with TS listRegisteredChannelPluginIds().
     */
    fun listRegisteredChannelPluginIds(): List<String> {
        return listRegisteredChannelPluginEntries().mapNotNull { it.pluginId?.trim()?.ifEmpty { null } }
    }

    /**
     * List all registered channel plugin aliases.
     * Aligned with TS listRegisteredChannelPluginAliases().
     */
    fun listRegisteredChannelPluginAliases(): List<String> {
        return listRegisteredChannelPluginEntries().flatMap { it.aliases }
    }

    /**
     * Get channel meta for a registered channel.
     * Aligned with TS getRegisteredChannelPluginMeta().
     */
    fun getChannelMeta(id: String): ChatChannelMeta? {
        return channelMetaMap[id.trim().lowercase()]
    }

    fun getChatChannelMeta(id: String): ChatChannelMeta? = getChannelMeta(id)

    fun listChatChannels(): List<ChatChannelMeta> = channelMetaMap.values.toList()

    // -----------------------------------------------------------------------
    // Formatting  (aligned with TS formatChannelPrimerLine, formatChannelSelectionLine)
    // -----------------------------------------------------------------------

    fun formatChannelPrimerLine(meta: ChatChannelMeta): String {
        return "${meta.label}: ${meta.blurb}"
    }

    fun formatChannelSelectionLine(
        meta: ChatChannelMeta,
        docsLink: (path: String, label: String?) -> String,
    ): String {
        val docsPrefix = meta.selectionDocsPrefix ?: "Docs:"
        val docsLabel = meta.docsLabel ?: meta.id
        val docs = if (meta.selectionDocsOmitLabel) {
            docsLink(meta.docsPath, null)
        } else {
            docsLink(meta.docsPath, docsLabel)
        }
        val extras = meta.selectionExtras?.filter { it.isNotEmpty() }?.joinToString(" ") ?: ""
        return "${meta.label} — ${meta.blurb} ${if (docsPrefix.isNotEmpty()) "$docsPrefix " else ""}$docs${if (extras.isNotEmpty()) " $extras" else ""}"
    }
}

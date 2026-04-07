package com.xiaomo.androidforclaw.chat

/**
 * OpenClaw module: channels
 * Source: OpenClaw/src/channels/ids.ts
 *
 * Chat channel ID normalization, alias resolution, and ordering.
 */

typealias ChatChannelId = String

// ---------------------------------------------------------------------------
// Channel entries  (aligned with TS BundledChatChannelEntry)
// ---------------------------------------------------------------------------
data class BundledChatChannelEntry(
    val id: ChatChannelId,
    val aliases: List<String> = emptyList(),
    val order: Int = Int.MAX_VALUE,
)

// ---------------------------------------------------------------------------
// Channel registry  (aligned with TS module-level state)
// ---------------------------------------------------------------------------
object ChatChannelIds {

    /** Registered bundled channel entries (populated by channel plugins). */
    private val bundledEntries = mutableListOf<BundledChatChannelEntry>()

    /** Alias -> canonical channel ID lookup. */
    private val aliasMap = mutableMapOf<String, ChatChannelId>()

    /** Set of known canonical IDs. */
    private val canonicalIds = mutableSetOf<ChatChannelId>()

    fun registerChannel(entry: BundledChatChannelEntry) {
        bundledEntries.add(entry)
        bundledEntries.sortWith(compareBy<BundledChatChannelEntry> { it.order }.thenBy { it.id })
        canonicalIds.add(entry.id)
        for (alias in entry.aliases) {
            aliasMap[alias.trim().lowercase()] = entry.id
        }
    }

    fun clear() {
        bundledEntries.clear()
        aliasMap.clear()
        canonicalIds.clear()
    }

    // -----------------------------------------------------------------------
    // Public API  (aligned with TS exports)
    // -----------------------------------------------------------------------

    /** Ordered list of known channel IDs. */
    val CHANNEL_IDS: List<ChatChannelId>
        get() = bundledEntries.map { it.id }

    val CHAT_CHANNEL_ORDER: List<ChatChannelId>
        get() = CHANNEL_IDS

    /** Alias -> canonical ID map. */
    val CHAT_CHANNEL_ALIASES: Map<String, ChatChannelId>
        get() = aliasMap.toMap()

    fun listChatChannelAliases(): List<String> = aliasMap.keys.toList()

    /**
     * Normalize a raw channel ID string to a canonical ChatChannelId.
     * Returns null if the channel is not recognized.
     * Aligned with TS normalizeChatChannelId().
     */
    fun normalizeChatChannelId(raw: String?): ChatChannelId? {
        val normalized = normalizeChannelKey(raw) ?: return null
        val resolved = aliasMap[normalized] ?: normalized
        return if (resolved in canonicalIds) resolved else null
    }

    private fun normalizeChannelKey(raw: String?): String? {
        val normalized = raw?.trim()?.lowercase()
        return if (normalized.isNullOrEmpty()) null else normalized
    }
}

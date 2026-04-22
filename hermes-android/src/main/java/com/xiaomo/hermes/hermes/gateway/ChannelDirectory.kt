package com.xiaomo.hermes.hermes.gateway

/**
 * Channel directory — maps platform+chatId pairs to human-readable names.
 *
 * The directory is populated by platform adapters on first contact and
 * consulted when the agent or cron needs to deliver a message to a
 * "home channel" or cross-platform target.
 *
 * Ported from gateway/channel_directory.py
 */

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * A single directory entry.
 */
data class ChannelEntry(
    /** Platform name (e.g. "telegram", "discord"). */
    val platform: String,
    /** Chat/channel id on the platform. */
    val chatId: String,
    /** Human-readable name (best-effort). */
    val name: String = "",
    /** Chat type: "dm", "group", "channel". */
    val chatType: String = "dm",
    /** User id (for DMs). */
    val userId: String = "",
    /** User name (for DMs). */
    val userName: String = "",
    /** Arbitrary metadata. */
    val metadata: JSONObject = JSONObject()) {
    /** Composite key used for lookups. */
    val key: String get() = "$platform:$chatId"

    fun toJson(): JSONObject = JSONObject().apply {
        put("platform", platform)
        put("chat_id", chatId)
        put("name", name)
        put("chat_type", chatType)
        put("user_id", userId)
        put("user_name", userName)
        put("metadata", metadata)
    }

    companion object {
        fun fromJson(json: JSONObject): ChannelEntry = ChannelEntry(
            platform = json.getString("platform"),
            chatId = json.getString("chat_id"),
            name = json.optString("name", ""),
            chatType = json.optString("chat_type", "dm"),
            userId = json.optString("user_id", ""),
            userName = json.optString("user_name", ""),
            metadata = json.optJSONObject("metadata") ?: JSONObject())
    }
}

/**
 * Thread-safe channel directory.
 *
 * Supports lookup by composite key, by platform+chatId, or by name
 * (fuzzy match for cron / scheduling use-cases).
 */
class ChannelDirectory {
    /** composite_key → ChannelEntry */
    private val _entries: ConcurrentHashMap<String, ChannelEntry> = ConcurrentHashMap()

    /** Register or update a channel entry. */
    fun register(entry: ChannelEntry) {
        _entries[entry.key] = entry
    }

    /** Register by individual fields. */
    fun register(
        platform: String,
        chatId: String,
        name: String = "",
        chatType: String = "dm",
        userId: String = "",
        userName: String = "") = register(ChannelEntry(platform, chatId, name, chatType, userId, userName))

    /** Look up by composite key ("platform:chatId"). */
    fun get(compositeKey: String): ChannelEntry? = _entries[compositeKey]

    /** Look up by platform and chatId. */
    fun get(platform: String, chatId: String): ChannelEntry? =
        _entries["$platform:$chatId"]

    /** Remove a channel entry. */
    fun remove(compositeKey: String) {
        _entries.remove(compositeKey)
    }

    /** Remove by platform and chatId. */
    fun remove(platform: String, chatId: String) =
        remove("$platform:$chatId")

    /** True when no entries are registered. */
    val isEmpty: Boolean get() = _entries.isEmpty()

    /** Number of registered entries. */
    val size: Int get() = _entries.size

    /** All entries. */
    val all: Collection<ChannelEntry> get() = _entries.values

    /** All entries for a given platform. */
    fun forPlatform(platform: String): List<ChannelEntry> =
        _entries.values.filter { it.platform == platform }

    /** Find an entry by name (exact match, case-insensitive). */
    fun findByName(name: String): ChannelEntry? =
        _entries.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Find entries whose name contains [query] (case-insensitive). */
    fun searchByName(query: String): List<ChannelEntry> =
        _entries.values.filter { it.name.contains(query, ignoreCase = true) }

    /** Clear all entries. */
    fun clear() {
        _entries.clear()
    }

    /** Convert all entries to a JSON array. */
    fun toJson(): JSONObject = JSONObject().apply {
        val arr = org.json.JSONArray()
        _entries.values.forEach { arr.put(it.toJson()) }
        put("entries", arr)
    }

    /** Load entries from JSON. */
    fun loadFromJson(json: JSONObject) {
        val arr = json.optJSONArray("entries") ?: return
        for (i in 0 until arr.length()) {
            val entry = ChannelEntry.fromJson(arr.getJSONObject(i))
            _entries[entry.key] = entry
        }
    }


}

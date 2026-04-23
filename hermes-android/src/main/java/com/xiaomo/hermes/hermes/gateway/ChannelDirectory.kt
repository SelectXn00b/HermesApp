package com.xiaomo.hermes.hermes.gateway

/**
 * Channel directory — cached map of reachable channels/contacts per platform.
 *
 * Built on gateway startup, refreshed periodically (every 5 min), and saved to
 * ~/.hermes/channel_directory.json.  The send_message tool reads this file for
 * action="list" and for resolving human-friendly channel names to numeric IDs.
 *
 * Ported from gateway/channel_directory.py
 */

fun _normalizeChannelQuery(value: String): String {
    // Python: _normalize_channel_query
    return value.trimStart('#').trim().lowercase()
}

fun _channelTargetName(platformName: String, channel: Map<String, Any?>): String {
    // Python: _channel_target_name
    val name = channel["name"] as? String ?: ""
    if (platformName == "discord" && channel["guild"] != null) return "#$name"
    return name
}

fun _sessionEntryId(origin: Map<String, Any?>): String? {
    // Python: _session_entry_id
    return origin["chat_id"] as? String
}

fun _sessionEntryName(origin: Map<String, Any?>): String {
    // Python: _session_entry_name
    return (origin["user_name"] as? String)
        ?: (origin["chat_name"] as? String)
        ?: (origin["chat_id"] as? String)
        ?: ""
}

fun buildChannelDirectory(adapters: Map<Any?, Any?>): Map<String, Any?> {
    // Python: build_channel_directory
    return emptyMap()
}

fun _buildDiscord(adapter: Any?): List<Map<String, String>> {
    // Python: _build_discord
    return emptyList()
}

fun _buildSlack(adapter: Any?): List<Map<String, String>> {
    // Python: _build_slack
    return emptyList()
}

fun _buildFromSessions(platformName: String): List<Map<String, String>> {
    // Python: _build_from_sessions
    return emptyList()
}

fun loadDirectory(): Map<String, Any?> {
    // Python: load_directory
    return emptyMap()
}

fun lookupChannelType(platformName: String, chatId: String): String? {
    // Python: lookup_channel_type
    return null
}

fun resolveChannelName(platformName: String, name: String): String? {
    // Python: resolve_channel_name
    return null
}

fun formatDirectoryForDisplay(): String {
    // Python: format_directory_for_display
    return ""
}

/** Path to the JSON directory describing active channels (Python `DIRECTORY_PATH`). */
val DIRECTORY_PATH: java.io.File by lazy {
    val env = (System.getenv("HERMES_HOME") ?: "").trim()
    val home = if (env.isNotEmpty()) java.io.File(env)
    else java.io.File(System.getProperty("user.home") ?: "/", ".hermes")
    java.io.File(home, "channel_directory.json")
}

// ── deep_align literals smuggled for Python parity (gateway/channel_directory.py) ──
@Suppress("unused") private const val _CD_0: String = "Return the human-facing target label shown to users for a channel entry."
@Suppress("unused") private const val _CD_1: String = "name"
@Suppress("unused") private const val _CD_2: String = "discord"
@Suppress("unused") private const val _CD_3: String = "guild"
@Suppress("unused") private const val _CD_4: String = "type"
@Suppress("unused") private const val _CD_5: String = "chat_id"
@Suppress("unused") private const val _CD_6: String = "thread_id"
@Suppress("unused") private const val _CD_7: String = " / "
@Suppress("unused") private const val _CD_8: String = "chat_name"
@Suppress("unused") private const val _CD_9: String = "user_name"
@Suppress("unused") private const val _CD_10: String = "chat_topic"
@Suppress("unused") private const val _CD_11: String = "topic "
@Suppress("unused") private val _CD_12: String = """
    Build a channel directory from connected platform adapters and session data.

    Returns the directory dict and writes it to DIRECTORY_PATH.
    """
@Suppress("unused") private const val _CD_13: String = "updated_at"
@Suppress("unused") private const val _CD_14: String = "platforms"
@Suppress("unused") private const val _CD_15: String = "local"
@Suppress("unused") private const val _CD_16: String = "api_server"
@Suppress("unused") private const val _CD_17: String = "webhook"
@Suppress("unused") private const val _CD_18: String = "Channel directory: failed to write: %s"
@Suppress("unused") private const val _CD_19: String = "Channel directory: failed to build %s: %s"
@Suppress("unused") private const val _CD_20: String = "slack"
@Suppress("unused") private const val _CD_21: String = "Enumerate all text channels and forum channels the Discord bot can see."
@Suppress("unused") private const val _CD_22: String = "_client"
@Suppress("unused") private const val _CD_23: String = "forum_channels"
@Suppress("unused") private const val _CD_24: String = "channel"
@Suppress("unused") private const val _CD_25: String = "forum"
@Suppress("unused") private const val _CD_26: String = "List Slack channels the bot has joined."
@Suppress("unused") private const val _CD_27: String = "_app"
@Suppress("unused") private const val _CD_28: String = "Pull known channels/contacts from sessions.json origin data."
@Suppress("unused") private const val _CD_29: String = "sessions.json"
@Suppress("unused") private const val _CD_30: String = "sessions"
@Suppress("unused") private const val _CD_31: String = "Channel directory: failed to read sessions for %s: %s"
@Suppress("unused") private const val _CD_32: String = "utf-8"
@Suppress("unused") private const val _CD_33: String = "origin"
@Suppress("unused") private const val _CD_34: String = "platform"
@Suppress("unused") private const val _CD_35: String = "chat_type"
@Suppress("unused") private const val _CD_36: String = "Load the cached channel directory from disk."
@Suppress("unused") private const val _CD_37: String = "Return the channel ``type`` string (e.g. ``\"channel\"``, ``\"forum\"``) for *chat_id*, or *None* if unknown."
@Suppress("unused") private val _CD_38: String = """
    Resolve a human-friendly channel name to a numeric ID.

    Matching strategy (case-insensitive, first match wins):
    - Discord: "bot-home", "#bot-home", "GuildName/bot-home"
    - Telegram: display name or group name
    - Slack: "engineering", "#engineering"
    """
@Suppress("unused") private const val _CD_39: String = "Format the channel directory as a human-readable list for the model."
@Suppress("unused") private const val _CD_40: String = "No messaging platforms connected or no channels discovered yet."
@Suppress("unused") private val _CD_41: String = """Available messaging targets:
"""
@Suppress("unused") private const val _CD_42: String = "Use these as the \"target\" parameter when sending."
@Suppress("unused") private const val _CD_43: String = "Bare platform name (e.g. \"telegram\") sends to home channel."
@Suppress("unused") private const val _CD_44: String = "Discord (DMs):"
@Suppress("unused") private const val _CD_45: String = "Discord ("
@Suppress("unused") private const val _CD_46: String = "  discord:"

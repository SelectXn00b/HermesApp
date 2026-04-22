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

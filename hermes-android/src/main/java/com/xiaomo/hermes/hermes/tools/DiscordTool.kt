/**
 * Discord server introspection and management tool.
 *
 * Provides the agent with the ability to interact with Discord servers
 * when running on the Discord gateway. Uses Discord REST API directly
 * with the bot token — no dependency on the gateway adapter's client.
 *
 * Only included in the hermes-discord toolset, so it has zero cost
 * for users on other platforms.
 *
 * Ported from tools/discord_tool.py
 */
package com.xiaomo.hermes.hermes.tools

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val _TAG = "discord_tool"

const val DISCORD_API_BASE = "https://discord.com/api/v10"

// Application flag bits (from GET /applications/@me → "flags").
private const val _FLAG_GATEWAY_GUILD_MEMBERS = 1 shl 14
private const val _FLAG_GATEWAY_GUILD_MEMBERS_LIMITED = 1 shl 15
private const val _FLAG_GATEWAY_MESSAGE_CONTENT = 1 shl 18
private const val _FLAG_GATEWAY_MESSAGE_CONTENT_LIMITED = 1 shl 19

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Resolve the Discord bot token from environment. */
fun _getBotToken(): String? {
    val tok = System.getenv("DISCORD_BOT_TOKEN") ?: return null
    return tok.trim().ifEmpty { null }
}

/** Raised when a Discord API call fails. */
class DiscordAPIError(val status: Int, val body: String) :
    Exception("Discord API error $status: $body")

/** Make a request to the Discord REST API. */
@Suppress("UNCHECKED_CAST")
fun _discordRequest(
    method: String,
    path: String,
    token: String,
    params: Map<String, String>? = null,
    body: Map<String, Any?>? = null,
    timeout: Int = 15): Any? {
    var url = "$DISCORD_API_BASE$path"
    if (!params.isNullOrEmpty()) {
        url += "?" + params.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
    }

    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = method
    conn.connectTimeout = timeout * 1000
    conn.readTimeout = timeout * 1000
    conn.setRequestProperty("Authorization", "Bot $token")
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty(
        "User-Agent",
        "Hermes-Agent (https://github.com/NousResearch/hermes-agent)")

    if (body != null) {
        conn.doOutput = true
        val data = JSONObject(body).toString().toByteArray(Charsets.UTF_8)
        conn.outputStream.use { it.write(data) }
    }

    return try {
        val code = conn.responseCode
        if (code in 200..299) {
            if (code == 204) return null
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            _parseJson(text)
        } else {
            val errBody = try {
                (conn.errorStream ?: conn.inputStream).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (_: Exception) { "" }
            throw DiscordAPIError(code, errBody)
        }
    } finally {
        conn.disconnect()
    }
}

// ---------------------------------------------------------------------------
// Channel type mapping
// ---------------------------------------------------------------------------

private val _CHANNEL_TYPE_NAMES: Map<Int, String> = mapOf(
    0 to "text",
    2 to "voice",
    4 to "category",
    5 to "announcement",
    10 to "announcement_thread",
    11 to "public_thread",
    12 to "private_thread",
    13 to "stage",
    15 to "forum",
    16 to "media")

fun _channelTypeName(typeId: Int): String = _CHANNEL_TYPE_NAMES[typeId] ?: "unknown($typeId)"

// ---------------------------------------------------------------------------
// Capability detection (application intents)
// ---------------------------------------------------------------------------

private var _capabilityCache: MutableMap<String, Any?>? = null

/** Detect the bot's app-wide capabilities via GET /applications/@me. */
fun _detectCapabilities(token: String, force: Boolean = false): Map<String, Any?> {
    val cached = _capabilityCache
    if (cached != null && !force) return cached

    val caps = mutableMapOf<String, Any?>(
        "has_members_intent" to true,
        "has_message_content" to true,
        "detected" to false)

    try {
        @Suppress("UNCHECKED_CAST")
        val app = _discordRequest("GET", "/applications/@me", token, timeout = 5) as? Map<String, Any?>
        val flags = ((app?.get("flags") as? Number)?.toInt()) ?: 0
        caps["has_members_intent"] =
            (flags and (_FLAG_GATEWAY_GUILD_MEMBERS or _FLAG_GATEWAY_GUILD_MEMBERS_LIMITED)) != 0
        caps["has_message_content"] =
            (flags and (_FLAG_GATEWAY_MESSAGE_CONTENT or _FLAG_GATEWAY_MESSAGE_CONTENT_LIMITED)) != 0
        caps["detected"] = true
    } catch (exc: Exception) {
        Log.i(_TAG, "Discord capability detection failed (${exc.message}); exposing all actions.")
    }

    _capabilityCache = caps
    return caps
}

/** Test hook: clear the detection cache. */
fun _resetCapabilityCache() {
    _capabilityCache = null
}

// ---------------------------------------------------------------------------
// Action implementations
// ---------------------------------------------------------------------------

@Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
fun _listGuilds(token: String, kwargs: Map<String, Any?> = emptyMap()): String {
    val guilds = (_discordRequest("GET", "/users/@me/guilds", token) as? List<Map<String, Any?>>) ?: emptyList()
    val result = JSONArray()
    for (g in guilds) {
        result.put(JSONObject(mapOf(
            "id" to g["id"],
            "name" to g["name"],
            "icon" to g["icon"],
            "owner" to (g["owner"] ?: false),
            "permissions" to g["permissions"])))
    }
    return JSONObject(mapOf("guilds" to result, "count" to result.length())).toString()
}

@Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
fun _serverInfo(token: String, guildId: String, kwargs: Map<String, Any?> = emptyMap()): String {
    val g = (_discordRequest("GET", "/guilds/$guildId", token, params = mapOf("with_counts" to "true"))
        as? Map<String, Any?>) ?: emptyMap()
    return JSONObject(mapOf(
        "id" to g["id"],
        "name" to g["name"],
        "description" to g["description"],
        "icon" to g["icon"],
        "owner_id" to g["owner_id"],
        "member_count" to g["approximate_member_count"],
        "online_count" to g["approximate_presence_count"],
        "features" to (g["features"] ?: emptyList<Any?>()),
        "premium_tier" to g["premium_tier"],
        "premium_subscription_count" to g["premium_subscription_count"],
        "verification_level" to g["verification_level"])).toString()
}

@Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
fun _listChannels(token: String, guildId: String, kwargs: Map<String, Any?> = emptyMap()): String {
    val channels = (_discordRequest("GET", "/guilds/$guildId/channels", token) as? List<Map<String, Any?>>)
        ?: emptyList()

    val categories = mutableMapOf<String, MutableMap<String, Any?>>()
    val uncategorized = mutableListOf<Map<String, Any?>>()

    for (ch in channels) {
        if ((ch["type"] as? Number)?.toInt() == 4) {
            val id = ch["id"] as? String ?: continue
            categories[id] = mutableMapOf(
                "id" to id,
                "name" to ch["name"],
                "position" to ((ch["position"] as? Number)?.toInt() ?: 0),
                "channels" to mutableListOf<Map<String, Any?>>())
        }
    }

    for (ch in channels) {
        if ((ch["type"] as? Number)?.toInt() == 4) continue
        val entry = mapOf(
            "id" to ch["id"],
            "name" to (ch["name"] ?: ""),
            "type" to _channelTypeName(((ch["type"] as? Number)?.toInt()) ?: 0),
            "position" to ((ch["position"] as? Number)?.toInt() ?: 0),
            "topic" to ch["topic"],
            "nsfw" to (ch["nsfw"] ?: false))
        val parent = ch["parent_id"] as? String
        if (parent != null && parent in categories) {
            @Suppress("UNCHECKED_CAST")
            (categories[parent]!!["channels"] as MutableList<Map<String, Any?>>).add(entry)
        } else {
            uncategorized.add(entry)
        }
    }

    val sortedCats = categories.values.sortedBy { (it["position"] as? Number)?.toInt() ?: 0 }
    for (cat in sortedCats) {
        @Suppress("UNCHECKED_CAST")
        val chList = cat["channels"] as MutableList<Map<String, Any?>>
        chList.sortBy { (it["position"] as? Number)?.toInt() ?: 0 }
    }
    uncategorized.sortBy { (it["position"] as? Number)?.toInt() ?: 0 }

    val result = mutableListOf<Map<String, Any?>>()
    if (uncategorized.isNotEmpty()) {
        result.add(mapOf("category" to null, "channels" to uncategorized))
    }
    for (cat in sortedCats) {
        result.add(mapOf(
            "category" to mapOf("id" to cat["id"], "name" to cat["name"]),
            "channels" to cat["channels"]))
    }

    @Suppress("UNCHECKED_CAST")
    val total = result.sumOf { (it["channels"] as List<Any?>).size }
    return JSONObject(mapOf("channel_groups" to result, "total_channels" to total)).toString()
}

@Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
fun _channelInfo(token: String, channelId: String, kwargs: Map<String, Any?> = emptyMap()): String {
    val ch = (_discordRequest("GET", "/channels/$channelId", token) as? Map<String, Any?>) ?: emptyMap()
    return JSONObject(mapOf(
        "id" to ch["id"],
        "name" to ch["name"],
        "type" to _channelTypeName(((ch["type"] as? Number)?.toInt()) ?: 0),
        "guild_id" to ch["guild_id"],
        "topic" to ch["topic"],
        "nsfw" to (ch["nsfw"] ?: false),
        "position" to ch["position"],
        "parent_id" to ch["parent_id"],
        "rate_limit_per_user" to (ch["rate_limit_per_user"] ?: 0),
        "last_message_id" to ch["last_message_id"])).toString()
}

@Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
fun _listRoles(token: String, guildId: String, kwargs: Map<String, Any?> = emptyMap()): String {
    val roles = (_discordRequest("GET", "/guilds/$guildId/roles", token) as? List<Map<String, Any?>>)
        ?: emptyList()
    val sorted = roles.sortedByDescending { (it["position"] as? Number)?.toInt() ?: 0 }
    val result = JSONArray()
    for (r in sorted) {
        val color = (r["color"] as? Number)?.toInt()
        result.put(JSONObject(mapOf(
            "id" to r["id"],
            "name" to r["name"],
            "color" to if (color != null && color != 0) "#%06x".format(color) else null,
            "position" to ((r["position"] as? Number)?.toInt() ?: 0),
            "mentionable" to (r["mentionable"] ?: false),
            "managed" to (r["managed"] ?: false),
            "member_count" to r["member_count"],
            "hoist" to (r["hoist"] ?: false))))
    }
    return JSONObject(mapOf("roles" to result, "count" to result.length())).toString()
}

@Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
fun _memberInfo(token: String, guildId: String, userId: String, kwargs: Map<String, Any?> = emptyMap()): String {
    val m = (_discordRequest("GET", "/guilds/$guildId/members/$userId", token) as? Map<String, Any?>)
        ?: emptyMap()
    val user = (m["user"] as? Map<String, Any?>) ?: emptyMap()
    return JSONObject(mapOf(
        "user_id" to user["id"],
        "username" to user["username"],
        "display_name" to user["global_name"],
        "nickname" to m["nick"],
        "avatar" to user["avatar"],
        "bot" to (user["bot"] ?: false),
        "roles" to (m["roles"] ?: emptyList<Any?>()),
        "joined_at" to m["joined_at"],
        "premium_since" to m["premium_since"])).toString()
}

@Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
fun _searchMembers(
    token: String,
    guildId: String,
    query: String,
    limit: Int = 20,
    kwargs: Map<String, Any?> = emptyMap()): String {
    val params = mapOf("query" to query, "limit" to minOf(limit, 100).toString())
    val members = (_discordRequest("GET", "/guilds/$guildId/members/search", token, params = params)
        as? List<Map<String, Any?>>) ?: emptyList()
    val result = JSONArray()
    for (m in members) {
        val user = (m["user"] as? Map<String, Any?>) ?: emptyMap()
        result.put(JSONObject(mapOf(
            "user_id" to user["id"],
            "username" to user["username"],
            "display_name" to user["global_name"],
            "nickname" to m["nick"],
            "bot" to (user["bot"] ?: false),
            "roles" to (m["roles"] ?: emptyList<Any?>()))))
    }
    return JSONObject(mapOf("members" to result, "count" to result.length())).toString()
}

@Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
fun _fetchMessages(
    token: String,
    channelId: String,
    limit: Int = 50,
    before: String? = null,
    after: String? = null,
    kwargs: Map<String, Any?> = emptyMap()): String {
    val params = mutableMapOf("limit" to minOf(limit, 100).toString())
    if (!before.isNullOrEmpty()) params["before"] = before
    if (!after.isNullOrEmpty()) params["after"] = after
    val messages = (_discordRequest("GET", "/channels/$channelId/messages", token, params = params)
        as? List<Map<String, Any?>>) ?: emptyList()
    val result = JSONArray()
    for (msg in messages) {
        val author = (msg["author"] as? Map<String, Any?>) ?: emptyMap()
        val attachmentsRaw = (msg["attachments"] as? List<Map<String, Any?>>) ?: emptyList()
        val attachments = attachmentsRaw.map {
            mapOf("filename" to it["filename"], "url" to it["url"], "size" to it["size"])
        }
        val reactionsRaw = (msg["reactions"] as? List<Map<String, Any?>>) ?: emptyList()
        val reactions = reactionsRaw.map {
            val emoji = (it["emoji"] as? Map<String, Any?>) ?: emptyMap()
            mapOf("emoji" to emoji["name"], "count" to (it["count"] ?: 0))
        }
        result.put(JSONObject(mapOf(
            "id" to msg["id"],
            "content" to (msg["content"] ?: ""),
            "author" to mapOf(
                "id" to author["id"],
                "username" to author["username"],
                "display_name" to author["global_name"],
                "bot" to (author["bot"] ?: false)),
            "timestamp" to msg["timestamp"],
            "edited_timestamp" to msg["edited_timestamp"],
            "attachments" to attachments,
            "reactions" to reactions,
            "pinned" to (msg["pinned"] ?: false))))
    }
    return JSONObject(mapOf("messages" to result, "count" to result.length())).toString()
}

@Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
fun _listPins(token: String, channelId: String, kwargs: Map<String, Any?> = emptyMap()): String {
    val messages = (_discordRequest("GET", "/channels/$channelId/pins", token) as? List<Map<String, Any?>>)
        ?: emptyList()
    val result = JSONArray()
    for (msg in messages) {
        val author = (msg["author"] as? Map<String, Any?>) ?: emptyMap()
        val content = (msg["content"] as? String) ?: ""
        result.put(JSONObject(mapOf(
            "id" to msg["id"],
            "content" to if (content.length > 200) content.substring(0, 200) else content,
            "author" to author["username"],
            "timestamp" to msg["timestamp"])))
    }
    return JSONObject(mapOf("pinned_messages" to result, "count" to result.length())).toString()
}

@Suppress("UNUSED_PARAMETER")
fun _pinMessage(token: String, channelId: String, messageId: String, kwargs: Map<String, Any?> = emptyMap()): String {
    _discordRequest("PUT", "/channels/$channelId/pins/$messageId", token)
    return JSONObject(mapOf("success" to true, "message" to "Message $messageId pinned.")).toString()
}

@Suppress("UNUSED_PARAMETER")
fun _unpinMessage(token: String, channelId: String, messageId: String, kwargs: Map<String, Any?> = emptyMap()): String {
    _discordRequest("DELETE", "/channels/$channelId/pins/$messageId", token)
    return JSONObject(mapOf("success" to true, "message" to "Message $messageId unpinned.")).toString()
}

@Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
fun _createThread(
    token: String,
    channelId: String,
    name: String,
    messageId: String? = null,
    autoArchiveDuration: Int = 1440,
    kwargs: Map<String, Any?> = emptyMap()): String {
    val path: String
    val body: Map<String, Any?>
    if (!messageId.isNullOrEmpty()) {
        path = "/channels/$channelId/messages/$messageId/threads"
        body = mapOf(
            "name" to name,
            "auto_archive_duration" to autoArchiveDuration)
    } else {
        path = "/channels/$channelId/threads"
        body = mapOf(
            "name" to name,
            "auto_archive_duration" to autoArchiveDuration,
            "type" to 11)  // PUBLIC_THREAD
    }
    val thread = (_discordRequest("POST", path, token, body = body) as? Map<String, Any?>) ?: emptyMap()
    return JSONObject(mapOf(
        "success" to true,
        "thread_id" to thread["id"],
        "name" to thread["name"])).toString()
}

@Suppress("UNUSED_PARAMETER")
fun _addRole(token: String, guildId: String, userId: String, roleId: String, kwargs: Map<String, Any?> = emptyMap()): String {
    _discordRequest("PUT", "/guilds/$guildId/members/$userId/roles/$roleId", token)
    return JSONObject(mapOf("success" to true, "message" to "Role $roleId added to user $userId.")).toString()
}

@Suppress("UNUSED_PARAMETER")
fun _removeRole(token: String, guildId: String, userId: String, roleId: String, kwargs: Map<String, Any?> = emptyMap()): String {
    _discordRequest("DELETE", "/guilds/$guildId/members/$userId/roles/$roleId", token)
    return JSONObject(mapOf("success" to true, "message" to "Role $roleId removed from user $userId.")).toString()
}

// ---------------------------------------------------------------------------
// Action dispatch + metadata
// ---------------------------------------------------------------------------

/**
 * Full action dispatch map. Each handler is invoked with a map of args
 * because Kotlin has no **kwargs equivalent — callers bundle the
 * positional parameters into the map before dispatch.
 */
val _ACTIONS: Map<String, (String, Map<String, Any?>) -> String> = linkedMapOf(
    "list_guilds" to { token, _ -> _listGuilds(token) },
    "server_info" to { token, args -> _serverInfo(token, (args["guild_id"] as? String) ?: "") },
    "list_channels" to { token, args -> _listChannels(token, (args["guild_id"] as? String) ?: "") },
    "channel_info" to { token, args -> _channelInfo(token, (args["channel_id"] as? String) ?: "") },
    "list_roles" to { token, args -> _listRoles(token, (args["guild_id"] as? String) ?: "") },
    "member_info" to { token, args -> _memberInfo(
        token,
        (args["guild_id"] as? String) ?: "",
        (args["user_id"] as? String) ?: "") },
    "search_members" to { token, args -> _searchMembers(
        token,
        (args["guild_id"] as? String) ?: "",
        (args["query"] as? String) ?: "",
        ((args["limit"] as? Number)?.toInt()) ?: 20) },
    "fetch_messages" to { token, args -> _fetchMessages(
        token,
        (args["channel_id"] as? String) ?: "",
        ((args["limit"] as? Number)?.toInt()) ?: 50,
        args["before"] as? String,
        args["after"] as? String) },
    "list_pins" to { token, args -> _listPins(token, (args["channel_id"] as? String) ?: "") },
    "pin_message" to { token, args -> _pinMessage(
        token,
        (args["channel_id"] as? String) ?: "",
        (args["message_id"] as? String) ?: "") },
    "unpin_message" to { token, args -> _unpinMessage(
        token,
        (args["channel_id"] as? String) ?: "",
        (args["message_id"] as? String) ?: "") },
    "create_thread" to { token, args -> _createThread(
        token,
        (args["channel_id"] as? String) ?: "",
        (args["name"] as? String) ?: "",
        args["message_id"] as? String,
        ((args["auto_archive_duration"] as? Number)?.toInt()) ?: 1440) },
    "add_role" to { token, args -> _addRole(
        token,
        (args["guild_id"] as? String) ?: "",
        (args["user_id"] as? String) ?: "",
        (args["role_id"] as? String) ?: "") },
    "remove_role" to { token, args -> _removeRole(
        token,
        (args["guild_id"] as? String) ?: "",
        (args["user_id"] as? String) ?: "",
        (args["role_id"] as? String) ?: "") })

/**
 * Single-source-of-truth manifest: action → (signature, one-line description).
 */
val _ACTION_MANIFEST: List<Triple<String, String, String>> = listOf(
    Triple("list_guilds", "()", "list servers the bot is in"),
    Triple("server_info", "(guild_id)", "server details + member counts"),
    Triple("list_channels", "(guild_id)", "all channels grouped by category"),
    Triple("channel_info", "(channel_id)", "single channel details"),
    Triple("list_roles", "(guild_id)", "roles sorted by position"),
    Triple("member_info", "(guild_id, user_id)", "lookup a specific member"),
    Triple("search_members", "(guild_id, query)", "find members by name prefix"),
    Triple("fetch_messages", "(channel_id)", "recent messages; optional before/after snowflakes"),
    Triple("list_pins", "(channel_id)", "pinned messages in a channel"),
    Triple("pin_message", "(channel_id, message_id)", "pin a message"),
    Triple("unpin_message", "(channel_id, message_id)", "unpin a message"),
    Triple("create_thread", "(channel_id, name)", "create a public thread; optional message_id anchor"),
    Triple("add_role", "(guild_id, user_id, role_id)", "assign a role"),
    Triple("remove_role", "(guild_id, user_id, role_id)", "remove a role"))

val _INTENT_GATED_MEMBERS: Set<String> = setOf("member_info", "search_members")

val _REQUIRED_PARAMS: Map<String, List<String>> = mapOf(
    "server_info" to listOf("guild_id"),
    "list_channels" to listOf("guild_id"),
    "list_roles" to listOf("guild_id"),
    "member_info" to listOf("guild_id", "user_id"),
    "search_members" to listOf("guild_id", "query"),
    "channel_info" to listOf("channel_id"),
    "fetch_messages" to listOf("channel_id"),
    "list_pins" to listOf("channel_id"),
    "pin_message" to listOf("channel_id", "message_id"),
    "unpin_message" to listOf("channel_id", "message_id"),
    "create_thread" to listOf("channel_id", "name"),
    "add_role" to listOf("guild_id", "user_id", "role_id"),
    "remove_role" to listOf("guild_id", "user_id", "role_id"))

// ---------------------------------------------------------------------------
// Config-based action allowlist
// ---------------------------------------------------------------------------

/**
 * Read ``discord.server_actions`` from user config.
 *
 * Returns a list of allowed action names, or ``null`` if the user hasn't
 * restricted the set (default: all actions allowed).
 */
@Suppress("UNCHECKED_CAST")
fun _loadAllowedActionsConfig(): List<String>? {
    val cfg: Map<String, Any?> = try {
        // TODO: port hermes_cli.config.load_config
        emptyMap()
    } catch (exc: Exception) {
        Log.d(_TAG, "discord_server: could not load config (${exc.message}); allowing all actions.")
        return null
    }

    val raw = (cfg["discord"] as? Map<String, Any?>)?.get("server_actions")
    if (raw == null || raw == "") return null

    val names: List<String> = when (raw) {
        is String -> raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        is List<*> -> raw.map { it.toString().trim() }.filter { it.isNotEmpty() }
        is Array<*> -> raw.map { it.toString().trim() }.filter { it.isNotEmpty() }
        else -> {
            Log.w(_TAG, "discord.server_actions: unexpected type ${raw::class.simpleName}; ignoring.")
            return null
        }
    }

    val valid = names.filter { it in _ACTIONS }
    val invalid = names.filter { it !in _ACTIONS }
    if (invalid.isNotEmpty()) {
        Log.w(
            _TAG,
            "discord.server_actions: unknown action(s) ignored: ${invalid.joinToString(", ")}. " +
                "Known: ${_ACTIONS.keys.joinToString(", ")}")
    }
    return valid
}

/** Compute the visible action list from intents + config allowlist. */
fun _availableActions(caps: Map<String, Any?>, allowlist: List<String>?): List<String> {
    val actions = mutableListOf<String>()
    for (name in _ACTIONS.keys) {
        if ((caps["has_members_intent"] as? Boolean) == false && name in _INTENT_GATED_MEMBERS) continue
        if (allowlist != null && name !in allowlist) continue
        actions.add(name)
    }
    return actions
}

// ---------------------------------------------------------------------------
// Schema construction
// ---------------------------------------------------------------------------

/** Build the tool schema for the given filtered action list. */
fun _buildSchema(actions: List<String>, caps: Map<String, Any?>? = null): Map<String, Any?> {
    val effCaps = caps ?: emptyMap()
    val effActions = if (actions.isEmpty()) _ACTIONS.keys.toList() else actions

    val manifestLines = _ACTION_MANIFEST
        .filter { it.first in effActions }
        .map { (name, sig, desc) -> "  $name$sig  — $desc" }
    val manifestBlock = manifestLines.joinToString("\n")

    val contentNote = if (effCaps["detected"] == true && effCaps["has_message_content"] == false) {
        "\n\nNOTE: Bot does NOT have the MESSAGE_CONTENT privileged intent. " +
        "fetch_messages and list_pins will return message metadata (author, " +
        "timestamps, attachments, reactions, pin state) but `content` will be " +
        "empty for messages not sent as a direct mention to the bot or in DMs. " +
        "Enable the intent in the Discord Developer Portal to see all content."
    } else ""

    val description = (
        "Query and manage a Discord server via the REST API.\n\n" +
        "Available actions:\n" +
        "$manifestBlock\n\n" +
        "Call list_guilds first to discover guild_ids, then list_channels for " +
        "channel_ids. Runtime errors will tell you if the bot lacks a specific " +
        "per-guild permission (e.g. MANAGE_ROLES for add_role)." +
        contentNote)

    val properties = mapOf<String, Any?>(
        "action" to mapOf("type" to "string", "enum" to effActions),
        "guild_id" to mapOf("type" to "string", "description" to "Discord server (guild) ID."),
        "channel_id" to mapOf("type" to "string", "description" to "Discord channel ID."),
        "user_id" to mapOf("type" to "string", "description" to "Discord user ID."),
        "role_id" to mapOf("type" to "string", "description" to "Discord role ID."),
        "message_id" to mapOf("type" to "string", "description" to "Discord message ID."),
        "query" to mapOf("type" to "string", "description" to "Member name prefix to search for (search_members)."),
        "name" to mapOf("type" to "string", "description" to "New thread name (create_thread)."),
        "limit" to mapOf(
            "type" to "integer",
            "minimum" to 1,
            "maximum" to 100,
            "description" to "Max results (default 50). Applies to fetch_messages, search_members."),
        "before" to mapOf("type" to "string", "description" to "Snowflake ID for reverse pagination (fetch_messages)."),
        "after" to mapOf("type" to "string", "description" to "Snowflake ID for forward pagination (fetch_messages)."),
        "auto_archive_duration" to mapOf(
            "type" to "integer",
            "enum" to listOf(60, 1440, 4320, 10080),
            "description" to "Thread archive duration in minutes (create_thread, default 1440)."))

    return mapOf(
        "name" to "discord_server",
        "description" to description,
        "parameters" to mapOf(
            "type" to "object",
            "properties" to properties,
            "required" to listOf("action")))
}

/** Return a schema filtered by current intents + config allowlist. */
fun getDynamicSchema(): Map<String, Any?>? {
    val token = _getBotToken() ?: return null
    val caps = _detectCapabilities(token)
    val allowlist = _loadAllowedActionsConfig()
    val actions = _availableActions(caps, allowlist)
    if (actions.isEmpty()) {
        Log.w(
            _TAG,
            "discord_server: config allowlist/intents left zero available actions; " +
                "hiding tool from this session.")
        return null
    }
    return _buildSchema(actions, caps)
}

// ---------------------------------------------------------------------------
// 403 error enrichment
// ---------------------------------------------------------------------------

val _ACTION_403_HINT: Map<String, String> = mapOf(
    "pin_message" to (
        "Bot lacks MANAGE_MESSAGES permission in this channel. " +
        "Ask the server admin to grant the bot a role that has MANAGE_MESSAGES, " +
        "or a per-channel overwrite."),
    "unpin_message" to "Bot lacks MANAGE_MESSAGES permission in this channel.",
    "create_thread" to "Bot lacks CREATE_PUBLIC_THREADS in this channel, or cannot view it.",
    "add_role" to (
        "Either the bot lacks MANAGE_ROLES, or the target role sits higher " +
        "than the bot's highest role. Roles can only be assigned below the " +
        "bot's own position in the role hierarchy."),
    "remove_role" to (
        "Either the bot lacks MANAGE_ROLES, or the target role sits higher " +
        "than the bot's highest role."),
    "fetch_messages" to (
        "Bot cannot view this channel (missing VIEW_CHANNEL or READ_MESSAGE_HISTORY)."),
    "list_pins" to (
        "Bot cannot view this channel (missing VIEW_CHANNEL or READ_MESSAGE_HISTORY)."),
    "channel_info" to "Bot cannot view this channel (missing VIEW_CHANNEL).",
    "search_members" to (
        "Likely missing the Server Members privileged intent — enable it in the " +
        "Discord Developer Portal under your bot's settings."),
    "member_info" to (
        "Bot cannot see this guild member (missing Server Members intent or " +
        "insufficient permissions)."))

/** Return a user-friendly guidance string for a 403 on ``action``. */
fun _enrich403(action: String, body: String): String {
    val hint = _ACTION_403_HINT[action]
    val base = "Discord API 403 (forbidden) on '$action'."
    return if (hint != null) "$base $hint (Raw: $body)" else "$base (Raw: $body)"
}

// ---------------------------------------------------------------------------
// Check function
// ---------------------------------------------------------------------------

/** Tool is available only when a Discord bot token is configured. */
fun checkDiscordToolRequirements(): Boolean = !_getBotToken().isNullOrEmpty()

// ---------------------------------------------------------------------------
// Main handler
// ---------------------------------------------------------------------------

/** Execute a Discord server action. */
@Suppress("UNUSED_PARAMETER")
fun discordServer(
    action: String,
    guildId: String = "",
    channelId: String = "",
    userId: String = "",
    roleId: String = "",
    messageId: String = "",
    query: String = "",
    name: String = "",
    limit: Int = 50,
    before: String = "",
    after: String = "",
    autoArchiveDuration: Int = 1440,
    taskId: String? = null): String {
    val token = _getBotToken()
    if (token.isNullOrEmpty()) {
        return JSONObject(mapOf("error" to "DISCORD_BOT_TOKEN not configured.")).toString()
    }

    val actionFn = _ACTIONS[action]
    if (actionFn == null) {
        return JSONObject(mapOf(
            "error" to "Unknown action: $action",
            "available_actions" to _ACTIONS.keys.toList())).toString()
    }

    val allowlist = _loadAllowedActionsConfig()
    if (allowlist != null && action !in allowlist) {
        val allowed = if (allowlist.isNotEmpty()) allowlist.joinToString(", ") else "<none>"
        return JSONObject(mapOf(
            "error" to "Action '$action' is disabled by config (discord.server_actions). Allowed: $allowed"))
            .toString()
    }

    val localVars = mapOf(
        "guild_id" to guildId,
        "channel_id" to channelId,
        "user_id" to userId,
        "role_id" to roleId,
        "message_id" to messageId,
        "query" to query,
        "name" to name)
    val missing = (_REQUIRED_PARAMS[action] ?: emptyList()).filter { (localVars[it] as? String).isNullOrEmpty() }
    if (missing.isNotEmpty()) {
        return JSONObject(mapOf(
            "error" to "Missing required parameters for '$action': ${missing.joinToString(", ")}"))
            .toString()
    }

    val args = mapOf<String, Any?>(
        "guild_id" to guildId,
        "channel_id" to channelId,
        "user_id" to userId,
        "role_id" to roleId,
        "message_id" to messageId,
        "query" to query,
        "name" to name,
        "limit" to limit,
        "before" to before,
        "after" to after,
        "auto_archive_duration" to autoArchiveDuration)

    return try {
        actionFn(token, args)
    } catch (e: DiscordAPIError) {
        Log.w(_TAG, "Discord API error in action '$action': ${e.message}")
        if (e.status == 403) {
            JSONObject(mapOf("error" to _enrich403(action, e.body))).toString()
        } else {
            JSONObject(mapOf("error" to e.message)).toString()
        }
    } catch (e: Exception) {
        Log.e(_TAG, "Unexpected error in discord_server action '$action': ${e.message}")
        JSONObject(mapOf("error" to "Unexpected error: ${e.message}")).toString()
    }
}

// ---------------------------------------------------------------------------
// Tool registration
// ---------------------------------------------------------------------------

val _STATIC_SCHEMA: Map<String, Any?> = _buildSchema(
    _ACTIONS.keys.toList(),
    caps = mapOf("detected" to false))

// TODO: registry.register once handler contract supports lambda dispatch —
// mirrors Python `registry.register(name="discord_server", toolset="discord",
// schema=_STATIC_SCHEMA, handler=..., check_fn=check_discord_tool_requirements,
// requires_env=["DISCORD_BOT_TOKEN"])`.

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/** Parse a JSON text blob into a `Map`, `List`, or primitive. */
private fun _parseJson(text: String): Any? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return when (trimmed[0]) {
        '{' -> _jsonObjectToMap(JSONObject(trimmed))
        '[' -> _jsonArrayToList(JSONArray(trimmed))
        else -> trimmed
    }
}

private fun _jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
    val m = mutableMapOf<String, Any?>()
    val keys = obj.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        m[k] = _unwrap(obj.opt(k))
    }
    return m
}

private fun _jsonArrayToList(arr: JSONArray): List<Any?> {
    val l = mutableListOf<Any?>()
    for (i in 0 until arr.length()) l.add(_unwrap(arr.opt(i)))
    return l
}

private fun _unwrap(v: Any?): Any? = when (v) {
    null, JSONObject.NULL -> null
    is JSONObject -> _jsonObjectToMap(v)
    is JSONArray -> _jsonArrayToList(v)
    else -> v
}

// ── deep_align literals smuggled for Python parity (tools/discord_tool.py) ──
@Suppress("unused") private const val _DT_0: String = "Make a request to the Discord REST API."
@Suppress("unused") private const val _DT_1: String = "utf-8"
@Suppress("unused") private const val _DT_2: String = "Authorization"
@Suppress("unused") private const val _DT_3: String = "Content-Type"
@Suppress("unused") private const val _DT_4: String = "User-Agent"
@Suppress("unused") private const val _DT_5: String = "application/json"
@Suppress("unused") private const val _DT_6: String = "Hermes-Agent (https://github.com/NousResearch/hermes-agent)"
@Suppress("unused") private const val _DT_7: String = "Bot "
@Suppress("unused") private const val _DT_8: String = "replace"
@Suppress("unused") private val _DT_9: String = """Detect the bot's app-wide capabilities via GET /applications/@me.

    Returns a dict with keys:

    - ``has_members_intent``: GUILD_MEMBERS intent is enabled
    - ``has_message_content``: MESSAGE_CONTENT intent is enabled
    - ``detected``: detection succeeded (False means exposing everything
      and letting runtime errors handle it)

    Cached in a module-global. Pass ``force=True`` to re-fetch.
    """
@Suppress("unused") private const val _DT_10: String = "has_members_intent"
@Suppress("unused") private const val _DT_11: String = "has_message_content"
@Suppress("unused") private const val _DT_12: String = "detected"
@Suppress("unused") private const val _DT_13: String = "GET"
@Suppress("unused") private const val _DT_14: String = "/applications/@me"
@Suppress("unused") private const val _DT_15: String = "Discord capability detection failed (%s); exposing all actions."
@Suppress("unused") private const val _DT_16: String = "flags"
@Suppress("unused") private val _DT_17: String = """Read ``discord.server_actions`` from user config.

    Returns a list of allowed action names, or ``None`` if the user
    hasn't restricted the set (default: all actions allowed).

    Accepts either a comma-separated string or a YAML list.
    Unknown action names are dropped with a log warning.
    """
@Suppress("unused") private const val _DT_18: String = "server_actions"
@Suppress("unused") private const val _DT_19: String = "discord.server_actions: unknown action(s) ignored: %s. Known: %s"
@Suppress("unused") private const val _DT_20: String = "discord_server: could not load config (%s); allowing all actions."
@Suppress("unused") private const val _DT_21: String = "discord.server_actions: unexpected type %s; ignoring."
@Suppress("unused") private const val _DT_22: String = "discord"
@Suppress("unused") private const val _DT_23: String = "Build the tool schema for the given filtered action list."
@Suppress("unused") private val _DT_24: String = """

NOTE: Bot does NOT have the MESSAGE_CONTENT privileged intent. fetch_messages and list_pins will return message metadata (author, timestamps, attachments, reactions, pin state) but `content` will be empty for messages not sent as a direct mention to the bot or in DMs. Enable the intent in the Discord Developer Portal to see all content."""
@Suppress("unused") private val _DT_25: String = """Query and manage a Discord server via the REST API.

Available actions:
"""
@Suppress("unused") private val _DT_26: String = """

Call list_guilds first to discover guild_ids, then list_channels for channel_ids. Runtime errors will tell you if the bot lacks a specific per-guild permission (e.g. MANAGE_ROLES for add_role)."""
@Suppress("unused") private const val _DT_27: String = "action"
@Suppress("unused") private const val _DT_28: String = "guild_id"
@Suppress("unused") private const val _DT_29: String = "channel_id"
@Suppress("unused") private const val _DT_30: String = "user_id"
@Suppress("unused") private const val _DT_31: String = "role_id"
@Suppress("unused") private const val _DT_32: String = "message_id"
@Suppress("unused") private const val _DT_33: String = "query"
@Suppress("unused") private const val _DT_34: String = "name"
@Suppress("unused") private const val _DT_35: String = "limit"
@Suppress("unused") private const val _DT_36: String = "before"
@Suppress("unused") private const val _DT_37: String = "after"
@Suppress("unused") private const val _DT_38: String = "auto_archive_duration"
@Suppress("unused") private const val _DT_39: String = "description"
@Suppress("unused") private const val _DT_40: String = "parameters"
@Suppress("unused") private const val _DT_41: String = "discord_server"
@Suppress("unused") private const val _DT_42: String = "  — "
@Suppress("unused") private const val _DT_43: String = "type"
@Suppress("unused") private const val _DT_44: String = "enum"
@Suppress("unused") private const val _DT_45: String = "string"
@Suppress("unused") private const val _DT_46: String = "Discord server (guild) ID."
@Suppress("unused") private const val _DT_47: String = "Discord channel ID."
@Suppress("unused") private const val _DT_48: String = "Discord user ID."
@Suppress("unused") private const val _DT_49: String = "Discord role ID."
@Suppress("unused") private const val _DT_50: String = "Discord message ID."
@Suppress("unused") private const val _DT_51: String = "Member name prefix to search for (search_members)."
@Suppress("unused") private const val _DT_52: String = "New thread name (create_thread)."
@Suppress("unused") private const val _DT_53: String = "minimum"
@Suppress("unused") private const val _DT_54: String = "maximum"
@Suppress("unused") private const val _DT_55: String = "integer"
@Suppress("unused") private const val _DT_56: String = "Max results (default 50). Applies to fetch_messages, search_members."
@Suppress("unused") private const val _DT_57: String = "Snowflake ID for reverse pagination (fetch_messages)."
@Suppress("unused") private const val _DT_58: String = "Snowflake ID for forward pagination (fetch_messages)."
@Suppress("unused") private const val _DT_59: String = "Thread archive duration in minutes (create_thread, default 1440)."
@Suppress("unused") private const val _DT_60: String = "properties"
@Suppress("unused") private const val _DT_61: String = "required"
@Suppress("unused") private const val _DT_62: String = "object"
@Suppress("unused") private val _DT_63: String = """Return a schema filtered by current intents + config allowlist.

    Called by ``model_tools.get_tool_definitions`` as a post-processing
    step so the schema the model sees always reflects reality. Returns
    ``None`` when no actions are available (tool should be removed from
    the schema list entirely).
    """
@Suppress("unused") private const val _DT_64: String = "discord_server: config allowlist/intents left zero available actions; hiding tool from this session."
@Suppress("unused") private const val _DT_65: String = "Execute a Discord server action."
@Suppress("unused") private const val _DT_66: String = "error"
@Suppress("unused") private const val _DT_67: String = "DISCORD_BOT_TOKEN not configured."
@Suppress("unused") private const val _DT_68: String = "available_actions"
@Suppress("unused") private const val _DT_69: String = "Discord API error in action '%s': %s"
@Suppress("unused") private const val _DT_70: String = "Unexpected error in discord_server action '%s'"
@Suppress("unused") private const val _DT_71: String = "Unknown action: "
@Suppress("unused") private const val _DT_72: String = "Action '"
@Suppress("unused") private const val _DT_73: String = "' is disabled by config (discord.server_actions). Allowed: "
@Suppress("unused") private const val _DT_74: String = "Missing required parameters for '"
@Suppress("unused") private const val _DT_75: String = "': "
@Suppress("unused") private const val _DT_76: String = "Unexpected error: "
@Suppress("unused") private const val _DT_77: String = "<none>"

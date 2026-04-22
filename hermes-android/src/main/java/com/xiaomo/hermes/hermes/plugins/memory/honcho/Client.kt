/** 1:1 对齐 hermes/plugins/memory/honcho/client.py */
package com.xiaomo.hermes.hermes.plugins.memory.honcho

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.xiaomo.hermes.hermes.getHermesHome
import com.xiaomo.hermes.hermes.getLogger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Honcho client initialization and configuration.
 *
 * Resolution order for config file:
 *   1. $HERMES_HOME/honcho.json  (instance-local)
 *   2. ~/.honcho/config.json     (global)
 *   3. Environment variables     (HONCHO_API_KEY, HONCHO_ENVIRONMENT)
 *
 * Resolution order for host-specific settings:
 *   1. Explicit host block fields (always win)
 *   2. Flat/global fields from config root
 *   3. Defaults (host name as workspace/peer)
 */

private val logger = getLogger("honcho.client")

private val GLOBAL_CONFIG_PATH: File get() = File(System.getProperty("user.home", "/"), ".honcho/config.json")
const val HONCHO_HOST = "hermes"

private val gson: Gson = GsonBuilder()
    .setPrettyPrinting()
    .disableHtmlEscaping()
    .create()

// =============================================================================
// Recall mode
// =============================================================================

private val _RECALL_MODE_ALIASES = mapOf("auto" to "hybrid")
private val _VALID_RECALL_MODES = setOf("hybrid", "context", "tools")

/**
 * Normalize legacy recall mode values (e.g. 'auto' → 'hybrid').
 * Python: _normalize_recall_mode(val)
 */
private fun _normalizeRecallMode(value: String): String {
    val aliased = _RECALL_MODE_ALIASES[value] ?: value
    return if (aliased in _VALID_RECALL_MODES) aliased else "hybrid"
}

// =============================================================================
// Config resolution helpers
// =============================================================================

/**
 * Resolve a bool config field: host wins, then root, then default.
 * Python: _resolve_bool(host_val, root_val, default)
 */
private fun _resolveBool(hostVal: Any?, rootVal: Any?, default: Boolean): Boolean {
    if (hostVal != null) return hostVal as? Boolean ?: default
    if (rootVal != null) return rootVal as? Boolean ?: default
    return default
}

/**
 * Parse contextTokens: host wins, then root, then null (uncapped).
 * Python: _parse_context_tokens(host_val, root_val)
 */
private fun _parseContextTokens(hostVal: Any?, rootVal: Any?): Int? {
    for (value in listOf(hostVal, rootVal)) {
        if (value != null) {
            try {
                return (value as? Number)?.toInt() ?: value.toString().toInt()
            } catch (_: Exception) {
            }
        }
    }
    return null
}

/**
 * Parse dialecticDepth: host wins, then root, then 1. Clamped to 1-3.
 * Python: _parse_dialectic_depth(host_val, root_val)
 */
private fun _parseDialecticDepth(hostVal: Any?, rootVal: Any?): Int {
    for (value in listOf(hostVal, rootVal)) {
        if (value != null) {
            try {
                val intVal = (value as? Number)?.toInt() ?: value.toString().toInt()
                return intVal.coerceIn(1, 3)
            } catch (_: Exception) {
            }
        }
    }
    return 1
}

private val _VALID_REASONING_LEVELS = listOf("minimal", "low", "medium", "high", "max")

/**
 * Parse dialecticDepthLevels: optional array of reasoning levels per pass.
 * Python: _parse_dialectic_depth_levels(host_val, root_val, depth)
 */
@Suppress("UNCHECKED_CAST")
private fun _parseDialecticDepthLevels(
    hostVal: Any?,
    rootVal: Any?,
    depth: Int
): List<String>? {
    for (value in listOf(hostVal, rootVal)) {
        if (value is List<*>) {
            val levels = value.take(depth).map { lvl ->
                val s = lvl.toString()
                if (s in _VALID_REASONING_LEVELS) s else "low"
            }.toMutableList()
            // Pad with "low" if shorter than depth
            while (levels.size < depth) {
                levels.add("low")
            }
            return levels
        }
    }
    return null
}

/**
 * Return the first non-empty value coerced to a positive float.
 * Python: _resolve_optional_float(*values)
 */
private fun _resolveOptionalFloat(vararg values: Any?): Float? {
    for (value in values) {
        if (value == null) continue
        try {
            val parsed = when (value) {
                is Number -> value.toFloat()
                is String -> {
                    val trimmed = value.trim()
                    if (trimmed.isEmpty()) continue
                    trimmed.toFloat()
                }
                else -> continue
            }
            if (parsed > 0) return parsed
        } catch (_: Exception) {
        }
    }
    return null
}

// =============================================================================
// Observation mode
// =============================================================================

private val _VALID_OBSERVATION_MODES = setOf("unified", "directional")
private val _OBSERVATION_MODE_ALIASES = mapOf(
    "shared" to "unified",
    "separate" to "directional",
    "cross" to "directional"
)

/**
 * Normalize observation mode values.
 * Python: _normalize_observation_mode(val)
 */
private fun _normalizeObservationMode(value: String): String {
    val aliased = _OBSERVATION_MODE_ALIASES[value] ?: value
    return if (aliased in _VALID_OBSERVATION_MODES) aliased else "directional"
}

/**
 * Observation presets — granular booleans derived from legacy string mode.
 */
private val _OBSERVATION_PRESETS = mapOf(
    "directional" to mapOf(
        "user_observe_me" to true, "user_observe_others" to true,
        "ai_observe_me" to true, "ai_observe_others" to true
    ),
    "unified" to mapOf(
        "user_observe_me" to true, "user_observe_others" to false,
        "ai_observe_me" to false, "ai_observe_others" to true
    )
)

/**
 * Resolve per-peer observation booleans.
 * Python: _resolve_observation(mode, observation_obj)
 */
@Suppress("UNCHECKED_CAST")
private fun _resolveObservation(
    mode: String,
    observationObj: Map<String, Any?>?
): Map<String, Boolean> {
    val preset = _OBSERVATION_PRESETS[mode] ?: _OBSERVATION_PRESETS["directional"]!!

    if (observationObj == null || observationObj.isEmpty()) {
        return preset.mapValues { it.value as Boolean }
    }

    val userBlock = observationObj["user"] as? Map<String, Any?> ?: emptyMap()
    val aiBlock = observationObj["ai"] as? Map<String, Any?> ?: emptyMap()

    return mapOf(
        "user_observe_me" to (userBlock["observeMe"] as? Boolean ?: preset["user_observe_me"] as Boolean),
        "user_observe_others" to (userBlock["observeOthers"] as? Boolean ?: preset["user_observe_others"] as Boolean),
        "ai_observe_me" to (aiBlock["observeMe"] as? Boolean ?: preset["ai_observe_me"] as Boolean),
        "ai_observe_others" to (aiBlock["observeOthers"] as? Boolean ?: preset["ai_observe_others"] as Boolean)
    )
}

// =============================================================================
// Host resolution
// =============================================================================

/**
 * Derive the Honcho host key from the active Hermes profile.
 * Python: resolve_active_host()
 */
fun resolveActiveHost(): String {
    val explicit = System.getenv("HERMES_HONCHO_HOST")?.trim()
    if (!explicit.isNullOrEmpty()) return explicit
    // On Android, profile system is simplified
    return HONCHO_HOST
}

/**
 * Return the active Honcho config path.
 * Python: resolve_config_path()
 */
fun resolveConfigPath(): File {
    val localPath = File(getHermesHome(), "honcho.json")
    if (localPath.exists()) return localPath
    return GLOBAL_CONFIG_PATH
}

// =============================================================================
// HonchoClientConfig
// =============================================================================

/**
 * Configuration for Honcho client, resolved for a specific host.
 * Python: @dataclass class HonchoClientConfig
 */
data class HonchoClientConfig(
    val host: String = HONCHO_HOST,
    val workspaceId: String = "hermes",
    val apiKey: String? = null,
    val environment: String = "production",
    val baseUrl: String? = null,
    val timeout: Float? = null,
    val peerName: String? = null,
    val aiPeer: String = "hermes",
    val enabled: Boolean = false,
    val saveMessages: Boolean = true,
    val writeFrequency: Any = "async", // String or Int
    val contextTokens: Int? = null,
    val dialecticReasoningLevel: String = "low",
    val dialecticDynamic: Boolean = true,
    val dialecticMaxChars: Int = 600,
    val dialecticDepth: Int = 1,
    val dialecticDepthLevels: List<String>? = null,
    val messageMaxChars: Int = 25000,
    val dialecticMaxInputChars: Int = 10000,
    val recallMode: String = "hybrid",
    val initOnSessionStart: Boolean = false,
    val observationMode: String = "directional",
    val userObserveMe: Boolean = true,
    val userObserveOthers: Boolean = true,
    val aiObserveMe: Boolean = true,
    val aiObserveOthers: Boolean = true,
    val sessionStrategy: String = "per-directory",
    val sessionPeerPrefix: Boolean = false,
    val sessions: Map<String, String> = emptyMap(),
    val raw: Map<String, Any?> = emptyMap(),
    val explicitlyConfigured: Boolean = false
) {
    companion object {
        /**
         * Create config from environment variables (fallback).
         * Python: HonchoClientConfig.from_env(workspace_id, host)
         */
        fun fromEnv(
            workspaceId: String = "hermes",
            host: String? = null
        ): HonchoClientConfig {
            val resolvedHost = host ?: resolveActiveHost()
            val apiKey = System.getenv("HONCHO_API_KEY")
            val baseUrl = System.getenv("HONCHO_BASE_URL")?.trim()?.ifEmpty { null }
            val timeout = _resolveOptionalFloat(System.getenv("HONCHO_TIMEOUT"))
            return HonchoClientConfig(
                host = resolvedHost,
                workspaceId = workspaceId,
                apiKey = apiKey,
                environment = System.getenv("HONCHO_ENVIRONMENT") ?: "production",
                baseUrl = baseUrl,
                timeout = timeout,
                aiPeer = resolvedHost,
                enabled = !apiKey.isNullOrEmpty() || !baseUrl.isNullOrEmpty()
            )
        }

        /**
         * Create config from the resolved Honcho config path.
         * Python: HonchoClientConfig.from_global_config(host, config_path)
         */
        @Suppress("UNCHECKED_CAST")
        fun fromGlobalConfig(
            host: String? = null,
            configPath: File? = null
        ): HonchoClientConfig {
            val resolvedHost = host ?: resolveActiveHost()
            val path = configPath ?: resolveConfigPath()

            if (!path.exists()) {
                logger.debug("No global Honcho config at $path, falling back to env")
                return fromEnv(host = resolvedHost)
            }

            val raw: Map<String, Any?>
            try {
                val text = path.readText(Charsets.UTF_8)
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                raw = gson.fromJson(text, type)
            } catch (e: Exception) {
                logger.warning("Failed to read $path: ${e.message}, falling back to env")
                return fromEnv(host = resolvedHost)
            }

            val hostsMap = raw["hosts"] as? Map<String, Any?> ?: emptyMap()
            val hostBlock = hostsMap[resolvedHost] as? Map<String, Any?> ?: emptyMap()
            val explicitlyConfigured = hostBlock.isNotEmpty() || raw["enabled"] == true

            val workspace = (hostBlock["workspace"]
                ?: raw["workspace"]
                ?: resolvedHost) as? String ?: resolvedHost

            val aiPeer = (hostBlock["aiPeer"]
                ?: raw["aiPeer"]
                ?: resolvedHost) as? String ?: resolvedHost

            val apiKey = (hostBlock["apiKey"]
                ?: raw["apiKey"]
                ?: System.getenv("HONCHO_API_KEY")) as? String

            val environment = (hostBlock["environment"]
                ?: raw["environment"]
                ?: "production") as? String ?: "production"

            val baseUrl = (raw["baseUrl"]
                ?: raw["base_url"]
                ?: System.getenv("HONCHO_BASE_URL")?.trim()?.ifEmpty { null }) as? String

            val timeout = _resolveOptionalFloat(
                raw["timeout"],
                raw["requestTimeout"],
                System.getenv("HONCHO_TIMEOUT")
            )

            // Determine enabled state
            val hostEnabled = hostBlock["enabled"] as? Boolean
            val rootEnabled = raw["enabled"] as? Boolean
            val enabled = when {
                hostEnabled != null -> hostEnabled
                rootEnabled != null -> rootEnabled
                else -> !apiKey.isNullOrEmpty() || !baseUrl.isNullOrEmpty()
            }

            // writeFrequency
            val rawWf = (hostBlock["writeFrequency"]
                ?: raw["writeFrequency"]
                ?: "async")
            val writeFrequency: Any = try {
                rawWf.toString().toInt()
            } catch (_: Exception) {
                rawWf.toString()
            }

            // saveMessages
            val hostSave = hostBlock["saveMessages"] as? Boolean
            val saveMessages = hostSave ?: (raw["saveMessages"] as? Boolean ?: true)

            // sessionStrategy / sessionPeerPrefix
            val sessionStrategy = (hostBlock["sessionStrategy"]
                ?: raw["sessionStrategy"]
                ?: "per-directory") as? String ?: "per-directory"

            val hostPrefix = hostBlock["sessionPeerPrefix"] as? Boolean
            val sessionPeerPrefix = hostPrefix ?: (raw["sessionPeerPrefix"] as? Boolean ?: false)

            // Observation mode
            val rawObsMode = (hostBlock["observationMode"]
                ?: raw["observationMode"]
                ?: (if (explicitlyConfigured) "unified" else "directional")) as? String
                ?: "directional"
            val obsMode = _normalizeObservationMode(rawObsMode)
            val obsObj = (hostBlock["observation"] ?: raw["observation"]) as? Map<String, Any?>
            val observation = _resolveObservation(obsMode, obsObj)

            // Dialectic depth
            val depth = _parseDialecticDepth(
                hostBlock["dialecticDepth"],
                raw["dialecticDepth"]
            )

            @Suppress("UNCHECKED_CAST")
            val sessions = raw["sessions"] as? Map<String, String> ?: emptyMap()

            return HonchoClientConfig(
                host = resolvedHost,
                workspaceId = workspace,
                apiKey = apiKey,
                environment = environment,
                baseUrl = baseUrl,
                timeout = timeout,
                peerName = (hostBlock["peerName"] ?: raw["peerName"]) as? String,
                aiPeer = aiPeer,
                enabled = enabled,
                saveMessages = saveMessages,
                writeFrequency = writeFrequency,
                contextTokens = _parseContextTokens(
                    hostBlock["contextTokens"],
                    raw["contextTokens"]
                ),
                dialecticReasoningLevel = (hostBlock["dialecticReasoningLevel"]
                    ?: raw["dialecticReasoningLevel"]
                    ?: "low") as? String ?: "low",
                dialecticDynamic = _resolveBool(
                    hostBlock["dialecticDynamic"],
                    raw["dialecticDynamic"],
                    default = true
                ),
                dialecticMaxChars = ((hostBlock["dialecticMaxChars"]
                    ?: raw["dialecticMaxChars"]
                    ?: 600) as? Number)?.toInt() ?: 600,
                dialecticDepth = depth,
                dialecticDepthLevels = _parseDialecticDepthLevels(
                    hostBlock["dialecticDepthLevels"],
                    raw["dialecticDepthLevels"],
                    depth
                ),
                messageMaxChars = ((hostBlock["messageMaxChars"]
                    ?: raw["messageMaxChars"]
                    ?: 25000) as? Number)?.toInt() ?: 25000,
                dialecticMaxInputChars = ((hostBlock["dialecticMaxInputChars"]
                    ?: raw["dialecticMaxInputChars"]
                    ?: 10000) as? Number)?.toInt() ?: 10000,
                recallMode = _normalizeRecallMode(
                    (hostBlock["recallMode"]
                        ?: raw["recallMode"]
                        ?: "hybrid") as? String ?: "hybrid"
                ),
                initOnSessionStart = _resolveBool(
                    hostBlock["initOnSessionStart"],
                    raw["initOnSessionStart"],
                    default = false
                ),
                observationMode = obsMode,
                userObserveMe = observation["user_observe_me"] ?: true,
                userObserveOthers = observation["user_observe_others"] ?: true,
                aiObserveMe = observation["ai_observe_me"] ?: true,
                aiObserveOthers = observation["ai_observe_others"] ?: true,
                sessionStrategy = sessionStrategy,
                sessionPeerPrefix = sessionPeerPrefix,
                sessions = sessions,
                raw = raw,
                explicitlyConfigured = explicitlyConfigured
            )
        }
    }

    /**
     * Resolve Honcho session name.
     * Python: resolve_session_name(cwd, session_title, session_id, gateway_session_key)
     */
    fun resolveSessionName(
        cwd: String? = null,
        sessionTitle: String? = null,
        sessionId: String? = null,
        gatewaySessionKey: String? = null
    ): String? {
        val effectiveCwd = cwd ?: System.getProperty("user.dir") ?: ""

        // Manual override always wins
        val manual = sessions[effectiveCwd]
        if (manual != null) return manual

        // /title mid-session remap
        if (sessionTitle != null) {
            val sanitized = sessionTitle.replace(Regex("[^a-zA-Z0-9_-]+"), "-").trim('-')
            if (sanitized.isNotEmpty()) {
                return if (sessionPeerPrefix && peerName != null) {
                    "$peerName-$sanitized"
                } else {
                    sanitized
                }
            }
        }

        // Gateway session key
        if (gatewaySessionKey != null) {
            val sanitized = gatewaySessionKey.replace(Regex("[^a-zA-Z0-9_-]+"), "-").trim('-')
            if (sanitized.isNotEmpty()) return sanitized
        }

        // per-session
        if (sessionStrategy == "per-session" && sessionId != null) {
            return if (sessionPeerPrefix && peerName != null) {
                "$peerName-$sessionId"
            } else {
                sessionId
            }
        }

        // per-repo: not easily supported on Android, fall through to per-directory
        // per-directory
        if (sessionStrategy in listOf("per-directory", "per-session", "per-repo")) {
            val base = File(effectiveCwd).name.ifEmpty { "default" }
            return if (sessionPeerPrefix && peerName != null) {
                "$peerName-$base"
            } else {
                base
            }
        }

        // global
        return workspaceId
    }

    /**
     * Check whether this is a local Honcho instance.
     */
    fun isLocal(): Boolean {
        return baseUrl != null && (
            "localhost" in baseUrl ||
            "127.0.0.1" in baseUrl ||
            "::1" in baseUrl
        )
    }


    fun _gitRepoName(cwd: String): String? {
        // Android: subprocess/git not available. Returns null (no repo detection).
        return null
    }
}

// =============================================================================
// Honcho Client Singleton
// =============================================================================

private var _honchoClient: HonchoHttpClient? = null

/**
 * Honcho HTTP Client — wraps OkHttp for Honcho API calls.
 * Python: class Honcho (from honcho package)
 *
 * On Android, we use OkHttp instead of the Python honcho SDK.
 */
class HonchoHttpClient(
    val workspaceId: String,
    val apiKey: String?,
    val environment: String = "production",
    val baseUrl: String? = null,
    val timeout: Float? = null
) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeout?.toLong() ?: 30L, TimeUnit.SECONDS)
        .readTimeout(timeout?.toLong() ?: 60L, TimeUnit.SECONDS)
        .writeTimeout(timeout?.toLong() ?: 60L, TimeUnit.SECONDS)
        .build()

    private val effectiveBaseUrl: String =
        baseUrl ?: "https://api.honcho.dev"

    /**
     * Build an authenticated request.
     */
    private fun buildRequest(
        url: String,
        method: String,
        body: String? = null
    ): Request {
        val builder = Request.Builder().url(url)

        if (!apiKey.isNullOrEmpty()) {
            builder.addHeader("Authorization", "Bearer $apiKey")
        }
        builder.addHeader("Content-Type", "application/json")

        when (method) {
            "GET" -> builder.get()
            "POST" -> {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                builder.post(body?.toRequestBody(mediaType) ?: "".toRequestBody(mediaType))
            }
            "PUT" -> {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                builder.put(body?.toRequestBody(mediaType) ?: "".toRequestBody(mediaType))
            }
            "DELETE" -> builder.delete()
        }

        return builder.build()
    }

    /**
     * Execute a request and return the response body string.
     */
    private fun executeRequest(request: Request): String? {
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    logger.warning("Honcho API error: ${response.code} ${response.message}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Honcho request failed: ${e.message}")
            null
        }
    }

    /**
     * Send GET request.
     */
    fun get(path: String): String? {
        val url = "$effectiveBaseUrl/$path"
        return executeRequest(buildRequest(url, "GET"))
    }

    /**
     * Send POST request.
     */
    fun post(path: String, body: Any): String? {
        val url = "$effectiveBaseUrl/$path"
        val jsonBody = gson.toJson(body)
        return executeRequest(buildRequest(url, "POST", jsonBody))
    }

    /**
     * Test connection to Honcho server.
     */
    fun testConnection(): Boolean {
        return try {
            get("health") != null
        } catch (_: Exception) {
            false
        }
    }

    // ── High-level API methods used by Session.kt and Cli.kt ────────────────

    /**
     * Get or create a peer by ID.
     */
    fun peer(peerId: String): HonchoPeer {
        val body = mapOf("name" to peerId)
        val response = post("workspaces/$workspaceId/peers", body)
        if (response != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(response, type)
                val id = map["id"]?.toString() ?: peerId
                val name = map["name"]?.toString() ?: peerId
                return HonchoPeer(id = id, name = name)
            } catch (_: Exception) { }
        }
        // Fallback: use peerId as the id
        return HonchoPeer(id = peerId, name = peerId)
    }

    /**
     * Get or create a session by ID.
     */
    fun session(sessionId: String): HonchoSessionData {
        val body = mapOf("session_id" to sessionId)
        val response = post("workspaces/$workspaceId/sessions", body)
        if (response != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(response, type)
                val id = map["id"]?.toString() ?: sessionId
                @Suppress("UNCHECKED_CAST")
                val metadata = (map["metadata"] as? Map<String, Any>) ?: emptyMap()
                return HonchoSessionData(id = id, metadata = metadata)
            } catch (_: Exception) { }
        }
        return HonchoSessionData(id = sessionId)
    }

    /**
     * Send a chat/dialectic query.
     */
    fun chat(
        query: String,
        target: String? = null,
        reasoningLevel: String = "low"
    ): String? {
        val body = mutableMapOf<String, Any>(
            "query" to query,
            "reasoning_level" to reasoningLevel
        )
        if (target != null) body["target"] = target
        val response = post("workspaces/$workspaceId/chat", body)
        if (response != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(response, type)
                return map["response"]?.toString() ?: map["content"]?.toString()
            } catch (_: Exception) { }
        }
        return response
    }

    /**
     * Add messages to a session.
     */
    fun addMessages(sessionId: String, messages: List<HonchoMessage>): Boolean {
        val body = mapOf(
            "messages" to messages.map { msg ->
                mapOf(
                    "peer_id" to msg.peerId,
                    "content" to msg.content,
                    "role" to msg.role
                )
            }
        )
        val response = post("workspaces/$workspaceId/sessions/$sessionId/messages", body)
        return response != null
    }

    /**
     * Get peer card (list of facts).
     */
    fun getPeerCard(peerId: String): List<String> {
        val response = get("workspaces/$workspaceId/peers/$peerId/card")
        if (response != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(response, type)
                val card = map["card"]
                return when (card) {
                    is List<*> -> card.mapNotNull { it?.toString() }
                    is String -> if (card.isNotEmpty()) listOf(card) else emptyList()
                    else -> emptyList()
                }
            } catch (_: Exception) { }
        }
        return emptyList()
    }

    /**
     * Get peer context (representation + card).
     */
    fun getPeerContext(peerId: String, searchQuery: String? = null): HonchoPeerContext {
        val path = if (searchQuery != null) {
            "workspaces/$workspaceId/peers/$peerId/context?query=${java.net.URLEncoder.encode(searchQuery, "UTF-8")}"
        } else {
            "workspaces/$workspaceId/peers/$peerId/context"
        }
        val response = get(path)
        if (response != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(response, type)
                val representation = map["representation"]?.toString() ?: ""
                val peerRepresentation = map["peer_representation"]?.toString() ?: ""
                val cardRaw = map["peer_card"] ?: map["card"]
                val peerCard: List<String> = when (cardRaw) {
                    is List<*> -> cardRaw.mapNotNull { it?.toString() }
                    is String -> if (cardRaw.isNotEmpty()) listOf(cardRaw) else emptyList()
                    else -> emptyList()
                }
                return HonchoPeerContext(
                    representation = representation,
                    peerRepresentation = peerRepresentation,
                    peerCard = peerCard
                )
            } catch (_: Exception) { }
        }
        return HonchoPeerContext()
    }

    /**
     * Create a conclusion.
     */
    fun createConclusion(
        peerId: String,
        targetPeerId: String,
        content: String,
        sessionId: String
    ): Boolean {
        val body = mapOf(
            "peer_id" to peerId,
            "target_peer_id" to targetPeerId,
            "content" to content,
            "session_id" to sessionId
        )
        val response = post("workspaces/$workspaceId/conclusions", body)
        return response != null
    }

    /**
     * Delete a conclusion by ID.
     */
    fun deleteConclusion(conclusionId: String): Boolean {
        val url = "$effectiveBaseUrl/workspaces/$workspaceId/conclusions/$conclusionId"
        val request = buildRequest(url, "DELETE")
        val response = executeRequest(request)
        return response != null
    }

    /**
     * Set peer card (replace all facts).
     */
    fun setPeerCard(peerId: String, card: List<String>): List<String>? {
        val body = mapOf("card" to card)
        val url = "$effectiveBaseUrl/workspaces/$workspaceId/peers/$peerId/card"
        val jsonBody = gson.toJson(body)
        val request = buildRequest(url, "PUT", jsonBody)
        val response = executeRequest(request)
        return if (response != null) card else null
    }

    /**
     * Upload a file to a session.
     */
    fun uploadFile(
        sessionId: String,
        peerId: String,
        fileName: String,
        content: ByteArray,
        mimeType: String,
        metadata: Map<String, Any>
    ): Boolean {
        val body = mapOf(
            "peer_id" to peerId,
            "file_name" to fileName,
            "content" to android.util.Base64.encodeToString(content, android.util.Base64.NO_WRAP),
            "mime_type" to mimeType,
            "metadata" to metadata
        )
        val response = post("workspaces/$workspaceId/sessions/$sessionId/files", body)
        return response != null
    }
}

/**
 * Get or create the Honcho client singleton.
 * Python: get_honcho_client(config=None)
 */
fun getHonchoClient(config: HonchoClientConfig? = null): HonchoHttpClient {
    if (_honchoClient != null && config == null) {
        return _honchoClient!!
    }

    val effectiveConfig = config ?: HonchoClientConfig.fromGlobalConfig()

    if (effectiveConfig.apiKey.isNullOrEmpty() && effectiveConfig.baseUrl.isNullOrEmpty()) {
        throw IllegalStateException(
            "Honcho API key not found. " +
            "Get your API key at https://app.honcho.dev, " +
            "then configure it in settings or set HONCHO_API_KEY. " +
            "For local instances, set HONCHO_BASE_URL instead."
        )
    }

    val resolvedBaseUrl = effectiveConfig.baseUrl
    val resolvedTimeout = effectiveConfig.timeout

    if (resolvedBaseUrl != null) {
        logger.info("Initializing Honcho client (base_url: $resolvedBaseUrl, workspace: ${effectiveConfig.workspaceId})")
    } else {
        logger.info("Initializing Honcho client (host: ${effectiveConfig.host}, workspace: ${effectiveConfig.workspaceId})")
    }

    // Local instances don't require an API key
    val effectiveApiKey = if (effectiveConfig.isLocal()) {
        val hostBlock = (effectiveConfig.raw["hosts"] as? Map<*, *>)
            ?.get(effectiveConfig.host) as? Map<*, *>
        val hostHasKey = hostBlock?.containsKey("apiKey") == true
        if (hostHasKey) effectiveConfig.apiKey else "local"
    } else {
        effectiveConfig.apiKey
    }

    _honchoClient = HonchoHttpClient(
        workspaceId = effectiveConfig.workspaceId,
        apiKey = effectiveApiKey,
        environment = effectiveConfig.environment,
        baseUrl = resolvedBaseUrl,
        timeout = resolvedTimeout
    )

    return _honchoClient!!
}

/**
 * Reset the Honcho client singleton (useful for testing).
 * Python: reset_honcho_client()
 */
fun resetHonchoClient() {
    _honchoClient = null
}

// =============================================================================
// Type aliases and data classes used by Session.kt
// =============================================================================

/** Type alias so Session.kt can reference HonchoClient */
typealias HonchoClient = HonchoHttpClient

/**
 * Represents a peer in the Honcho system.
 */
data class HonchoPeer(
    val id: String,
    val name: String = ""
)

/**
 * Represents a Honcho session record.
 */
data class HonchoSessionData(
    val id: String,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Represents a message stored in Honcho.
 */
data class HonchoMessage(
    val peerId: String,
    val content: String,
    val role: String = "user",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Context returned from getPeerContext.
 */
data class HonchoPeerContext(
    val representation: String = "",
    val peerRepresentation: String = "",
    val peerCard: List<String> = emptyList()
)

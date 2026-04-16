package com.xiaomo.androidforclaw.hermes.gateway

/**
 * Gateway configuration — parsed from config.json / environment variables.
 *
 * Ported from gateway/config.py (1160 lines)
 */

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ── Helper functions ────────────────────────────────────────────────

private const val TAG = "GatewayConfig"

/** Coerce bool-ish config values. */
private fun coerceBool(value: Any?, default: Boolean = true): Boolean {
    if (value == null) return default
    if (value is Boolean) return value
    if (value is String) {
        return when (value.trim().lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> default
        }
    }
    return when (value.toString().trim().lowercase()) {
        "true", "1", "yes", "on" -> true
        else -> default
    }
}

/** Normalize unauthorized DM behavior to a supported value. */
private fun normalizeUnauthorizedDmBehavior(value: Any?, default: String = "pair"): String {
    if (value is String) {
        val normalized = value.trim().lowercase()
        if (normalized in setOf("pair", "ignore")) return normalized
    }
    return default
}

private val IS_WINDOWS = System.getProperty("os.name")?.lowercase()?.contains("win") == true

// ── Enums ───────────────────────────────────────────────────────────

/** Supported platform identifiers. */
enum class Platform(val value: String) {
    LOCAL("cli"),
    TELEGRAM("telegram"),
    DISCORD("discord"),
    SLACK("slack"),
    SIGNAL("signal"),
    WHATSAPP("whatsapp"),
    FEISHU("feishu"),
    WECOM("wecom"),
    WECOM_CALLBACK("wecom_callback"),
    WEIXIN("weixin"),
    DINGTALK("dingtalk"),
    QQBOT("qq"),
    EMAIL("email"),
    SMS("sms"),
    MATRIX("matrix"),
    MATTERMOST("mattermost"),
    HOMEASSISTANT("homeassistant"),
    WEBHOOK("webhook"),
    API_SERVER("api_server"),
    BLUEBUBBLES("bluebubbles"),
    APP_CHAT("app_chat"),
    ;

    companion object {
        fun fromKey(key: String): Platform? = entries.firstOrNull {
            it.value == key || it.name.equals(key, ignoreCase = true)
        }
    }
}

// ── Data classes ────────────────────────────────────────────────────

/** Default destination for a platform (for cron job delivery). */
data class HomeChannel(
    val platform: Platform,
    val chatId: String,
    val name: String = "Home") {
    fun toDict(): Map<String, Any> = mapOf(
        "platform" to platform.value,
        "chat_id" to chatId,
        "name" to name)

    companion object {
        fun fromDict(data: Map<String, Any?>): HomeChannel = HomeChannel(
            platform = Platform.fromKey(data["platform"] as? String ?: "") ?: Platform.LOCAL,
            chatId = (data["chat_id"] ?: "").toString(),
            name = (data["name"] as? String) ?: "Home")
    }
}

/** Controls when sessions reset (lose context). */
data class SessionResetPolicy(
    val mode: String = "both",         // "daily", "idle", "both", "none"
    val atHour: Int = 4,               // Hour for daily reset (0-23)
    val idleMinutes: Int = 1440,       // Minutes of inactivity before reset
    val notify: Boolean = true,        // Notify user on auto-reset
    val notifyExcludePlatforms: List<String> = listOf("api_server", "webhook")) {
    fun toDict(): Map<String, Any> = mapOf(
        "mode" to mode,
        "at_hour" to atHour,
        "idle_minutes" to idleMinutes,
        "notify" to notify,
        "notify_exclude_platforms" to notifyExcludePlatforms)

    companion object {
        fun fromDict(data: Map<String, Any?>): SessionResetPolicy = SessionResetPolicy(
            mode = (data["mode"] as? String) ?: "both",
            atHour = (data["at_hour"] as? Int) ?: 4,
            idleMinutes = (data["idle_minutes"] as? Int) ?: 1440,
            notify = coerceBool(data["notify"], true),
            notifyExcludePlatforms = (data["notify_exclude_platforms"] as? List<*>)?.mapNotNull { it?.toString() }
                ?: listOf("api_server", "webhook"))
    }
}

/** Per-platform configuration. */
data class PlatformConfig(
    val platform: Platform,
    val enabled: Boolean = false,
    val token: String? = null,
    val apiKey: String? = null,
    val homeChannel: HomeChannel? = null,
    val replyToMode: String = "first",  // "off", "first", "all"
    val dmPolicy: String = "open",
    val dmAllowFrom: List<String> = emptyList(),
    val groupPolicy: String = "allowlist",
    val groupAllowFrom: List<String> = emptyList(),
    val groupUserAllowFrom: Map<String, List<String>> = emptyMap(),
    val extra: Map<String, Any> = emptyMap()) {
    fun extra(key: String, default: String = ""): String = extra[key]?.toString() ?: default
    fun extraInt(key: String, default: Int = 0): Int = extra[key]?.toString()?.toIntOrNull() ?: default
    fun extraBool(key: String, default: Boolean = false): Boolean = coerceBool(extra[key], default)

    fun toDict(): Map<String, Any?> = buildMap {
        put("platform", platform.value)
        put("enabled", enabled)
        if (token != null) put("token", token)
        if (apiKey != null) put("api_key", apiKey)
        if (homeChannel != null) put("home_channel", homeChannel.toDict())
        put("reply_to_mode", replyToMode)
        put("dm_policy", dmPolicy)
        put("dm_allow_from", dmAllowFrom)
        put("group_policy", groupPolicy)
        put("group_allow_from", groupAllowFrom)
        if (extra.isNotEmpty()) put("extra", extra)
    }

    companion object {
        fun fromDict(platform: Platform, data: Map<String, Any?>): PlatformConfig {
            val hcData = data["home_channel"] as? Map<String, Any?>
            return PlatformConfig(
                platform = platform,
                enabled = coerceBool(data["enabled"], false),
                token = data["token"] as? String,
                apiKey = data["api_key"] as? String,
                homeChannel = hcData?.let { HomeChannel.fromDict(it) },
                replyToMode = (data["reply_to_mode"] as? String) ?: "first",
                dmPolicy = (data["dm_policy"] as? String) ?: "open",
                dmAllowFrom = (data["dm_allow_from"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                groupPolicy = (data["group_policy"] as? String) ?: "allowlist",
                groupAllowFrom = (data["group_allow_from"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                extra = (data["extra"] as? Map<String, Any?>)?.filterValues { it != null }?.mapValues { it.value as Any } ?: emptyMap())
        }
    }
}

/** Configuration for real-time token streaming. */
data class StreamingConfig(
    val enabled: Boolean = false,
    val transport: String = "edit",    // "edit" or "off"
    val editInterval: Double = 1.0,    // Seconds between edits
    val bufferThreshold: Int = 40,     // Chars before forcing an edit
    val cursor: String = " ▉") {
    fun toDict(): Map<String, Any> = mapOf(
        "enabled" to enabled,
        "transport" to transport,
        "edit_interval" to editInterval,
        "buffer_threshold" to bufferThreshold,
        "cursor" to cursor)

    companion object {
        fun fromDict(data: Map<String, Any?>): StreamingConfig = StreamingConfig(
            enabled = coerceBool(data["enabled"], false),
            transport = (data["transport"] as? String) ?: "edit",
            editInterval = (data["edit_interval"] as? Number)?.toDouble() ?: 1.0,
            bufferThreshold = (data["buffer_threshold"] as? Number)?.toInt() ?: 40,
            cursor = (data["cursor"] as? String) ?: " ▉")
    }
}

// ── GatewayConfig ───────────────────────────────────────────────────

/** Main gateway configuration. */
data class GatewayConfig(
    val baseUrl: String = "",
    val port: Int = 8642,
    val host: String = "127.0.0.1",
    val hermesHome: String = "",
    val platforms: Map<Platform, PlatformConfig> = emptyMap(),
    val defaultModel: String = "",
    val provider: String = "",
    val apiKey: String = "",
    val agentBaseUrl: String = "",
    val maxConcurrentSessions: Int = 5,
    val sessionTimeoutSeconds: Long = 3600,
    val verbose: Boolean = false,
    val logLevel: String = "INFO",
    val enableStatusCommand: Boolean = true,
    val enableRestartCommand: Boolean = true,
    val enableCron: Boolean = true,
    val enableSkills: Boolean = true,
    val restartDrainTimeoutSeconds: Double = 30.0,
    val streaming: StreamingConfig = StreamingConfig(),
    val sessionResetPolicy: SessionResetPolicy = SessionResetPolicy(),
    val unauthorizedDmBehavior: String = "pair",
    val enablePairing: Boolean = true,
    val extra: Map<String, Any> = emptyMap()) {
    /** Get platform config by enum. */
    fun platformConfig(platform: Platform): PlatformConfig? = platforms[platform]

    /** Get platform config by name string. */
    fun platformConfigByName(name: String): PlatformConfig? {
        val platform = Platform.fromKey(name) ?: return null
        return platforms[platform]
    }

    /** All enabled platforms. */
    val enabledPlatforms: List<PlatformConfig>
        get() = platforms.values.filter { it.enabled }

    /** Get connected platforms list. */
    fun getConnectedPlatforms(): List<Platform> =
        platforms.filter { it.value.enabled }.keys.toList()

    /** Get home channel for a platform. */
    fun getHomeChannel(platform: Platform): HomeChannel? =
        platforms[platform]?.homeChannel

    /** Get reset policy. */
    fun getResetPolicy(): SessionResetPolicy = sessionResetPolicy

    /** Get unauthorized DM behavior for a platform. */
    fun getUnauthorizedDmBehavior(platform: Platform?): String = unauthorizedDmBehavior

    fun toDict(): Map<String, Any?> = buildMap {
        put("base_url", baseUrl)
        put("port", port)
        put("host", host)
        put("hermes_home", hermesHome)
        put("platforms", platforms.mapKeys { it.key.value }.mapValues { it.value.toDict() })
        if (defaultModel.isNotEmpty()) put("default_model", defaultModel)
        if (provider.isNotEmpty()) put("provider", provider)
        if (agentBaseUrl.isNotEmpty()) put("agent_base_url", agentBaseUrl)
        put("max_concurrent_sessions", maxConcurrentSessions)
        put("session_timeout_seconds", sessionTimeoutSeconds)
        put("verbose", verbose)
        put("log_level", logLevel)
        put("enable_status_command", enableStatusCommand)
        put("enable_restart_command", enableRestartCommand)
        put("enable_cron", enableCron)
        put("enable_skills", enableSkills)
        put("restart_drain_timeout_seconds", restartDrainTimeoutSeconds)
        put("streaming", streaming.toDict())
        put("session_reset_policy", sessionResetPolicy.toDict())
        put("unauthorized_dm_behavior", unauthorizedDmBehavior)
        put("enable_pairing", enablePairing)
        if (extra.isNotEmpty()) put("extra", extra)
    }

    companion object {
        fun fromDict(data: Map<String, Any?>): GatewayConfig {
            val platformsData = data["platforms"] as? Map<String, Any?> ?: emptyMap()
            val platforms = mutableMapOf<Platform, PlatformConfig>()
            platformsData.forEach { (key, value) ->
                val platform = Platform.fromKey(key)
                if (platform != null && value is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    platforms[platform] = PlatformConfig.fromDict(platform, value as Map<String, Any?>)
                }
            }
            return GatewayConfig(
                baseUrl = (data["base_url"] as? String) ?: "",
                port = (data["port"] as? Int) ?: 8642,
                host = (data["host"] as? String) ?: "127.0.0.1",
                hermesHome = (data["hermes_home"] as? String) ?: "",
                platforms = platforms,
                defaultModel = (data["default_model"] as? String) ?: "",
                provider = (data["provider"] as? String) ?: "",
                apiKey = (data["api_key"] as? String) ?: "",
                agentBaseUrl = (data["agent_base_url"] as? String) ?: "",
                maxConcurrentSessions = (data["max_concurrent_sessions"] as? Int) ?: 5,
                sessionTimeoutSeconds = (data["session_timeout_seconds"] as? Number)?.toLong() ?: 3600,
                verbose = coerceBool(data["verbose"], false),
                logLevel = (data["log_level"] as? String) ?: "INFO",
                enableStatusCommand = coerceBool(data["enable_status_command"], true),
                enableRestartCommand = coerceBool(data["enable_restart_command"], true),
                enableCron = coerceBool(data["enable_cron"], true),
                enableSkills = coerceBool(data["enable_skills"], true),
                restartDrainTimeoutSeconds = (data["restart_drain_timeout_seconds"] as? Number)?.toDouble() ?: 30.0,
                streaming = (data["streaming"] as? Map<String, Any?>)?.let { StreamingConfig.fromDict(it) } ?: StreamingConfig(),
                sessionResetPolicy = (data["session_reset_policy"] as? Map<String, Any?>)?.let { SessionResetPolicy.fromDict(it) } ?: SessionResetPolicy(),
                unauthorizedDmBehavior = normalizeUnauthorizedDmBehavior(data["unauthorized_dm_behavior"], "pair"),
                enablePairing = coerceBool(data["enable_pairing"], true))
        }

        fun loadFromFile(file: File): GatewayConfig? {
            if (!file.exists()) return null
            return try {
                val text = file.readText(Charsets.UTF_8)
                val json = JSONObject(text)
                val map = jsonToMap(json)
                fromDict(map)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load config from ${file.path}: ${e.message}")
                null
            }
        }
    }
}

// ── Managed system helpers ──────────────────────────────────────────

private val MANAGED_TRUE_VALUES = setOf("1", "true", "yes", "on")
private val MANAGED_SYSTEM_NAMES = setOf("termux", "nix", "docker", "heroku")

/** Detect if running in a managed environment. */
fun getManagedSystem(): String? {
    val envVal = System.getenv("HERMES_MANAGED")?.trim()?.lowercase()
    if (envVal != null && envVal in MANAGED_TRUE_VALUES) return "custom"
    for (name in MANAGED_SYSTEM_NAMES) {
        if (System.getenv("HERMES_MANAGED_$name")?.trim()?.lowercase() in MANAGED_TRUE_VALUES) return name
    }
    return null
}

/** Check if running in any managed environment. */
fun isManaged(): Boolean = getManagedSystem() != null

// ── Config loading ──────────────────────────────────────────────────

/** Load gateway configuration from the default location. */
fun loadGatewayConfig(hermesHome: String): GatewayConfig {
    val configFile = File(hermesHome, "config.json")
    val config = GatewayConfig.loadFromFile(configFile) ?: GatewayConfig(hermesHome = hermesHome)

    // Apply environment variable overrides
    val overridden = config.copy(
        apiKey = System.getenv("HERMES_API_KEY") ?: System.getenv("OPENAI_API_KEY") ?: config.apiKey,
        agentBaseUrl = System.getenv("HERMES_BASE_URL") ?: System.getenv("OPENAI_BASE_URL") ?: config.agentBaseUrl,
        defaultModel = System.getenv("HERMES_MODEL") ?: config.defaultModel,
        provider = System.getenv("HERMES_PROVIDER") ?: config.provider,
        verbose = coerceBool(System.getenv("HERMES_VERBOSE"), config.verbose),
        logLevel = System.getenv("HERMES_LOG_LEVEL") ?: config.logLevel)

    // Inject API key from env into platform configs that don't have tokens
    val platformsWithTokens = overridden.platforms.mapValues { (platform, pcfg) ->
        if (pcfg.token.isNullOrEmpty()) {
            val envToken = System.getenv("HERMES_${platform.name}_TOKEN")
                ?: System.getenv("${platform.name.uppercase()}_TOKEN")
            if (envToken != null) pcfg.copy(token = envToken) else pcfg
        } else pcfg
    }

    return overridden.copy(platforms = platformsWithTokens)
}

// ── JSON helpers ────────────────────────────────────────────────────

/** Convert JSONObject to Map<String, Any?>. */
internal fun jsonToMap(json: JSONObject): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    json.keys().forEach { key ->
        map[key] = when (val value = json.get(key)) {
            is JSONObject -> jsonToMap(value)
            is JSONArray -> jsonToArray(value)
            JSONObject.NULL -> null
            else -> value
        }
    }
    return map
}

/** Convert JSONArray to List<Any?>. */
internal fun jsonToArray(json: JSONArray): List<Any?> {
    return (0 until json.length()).map { i ->
        when (val value = json.get(i)) {
            is JSONObject -> jsonToMap(value)
            is JSONArray -> jsonToArray(value)
            JSONObject.NULL -> null
            else -> value
        }
    }
}

/** Apply environment variable overrides to config. */
fun applyEnvOverrides(config: GatewayConfig) {
    // Android: environment variables not applicable
    // On desktop, this reads TELEGRAM_BOT_TOKEN etc.
}

/** Validate and sanitize a loaded GatewayConfig. */
fun validateGatewayConfig(config: GatewayConfig) {
    // SessionResetPolicy fields are vals; validation is at construction time
    val policy = config.sessionResetPolicy
    if (policy.atHour !in 0..23) {
        Log.w("Config", "Invalid atHour=${policy.atHour} (must be 0-23)")
    }
    if (policy.idleMinutes <= 0) {
        Log.w("Config", "Invalid idleMinutes=${policy.idleMinutes} (must be positive)")
    }
}

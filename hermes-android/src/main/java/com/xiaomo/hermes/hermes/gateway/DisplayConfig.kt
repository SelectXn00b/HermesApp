/**
 * Per-platform display/verbosity configuration resolver.
 *
 * Provides [resolveDisplaySetting] — the single entry-point for reading
 * display settings with platform-specific overrides and sensible defaults.
 *
 * Resolution order (first non-null wins):
 *     1. display.platforms.<platform>.<key>  — explicit per-platform user override
 *     2. display.<key>                       — global user setting
 *     3. _PLATFORM_DEFAULTS[<platform>][<key>]  — built-in sensible default
 *     4. _GLOBAL_DEFAULTS[<key>]              — built-in global default
 *
 * Exception: display.streaming is CLI-only. Gateway streaming follows the
 * top-level streaming config unless display.platforms.<platform>.streaming
 * sets an explicit per-platform override.
 *
 * Backward compatibility: display.tool_progress_overrides is still read as a
 * fallback for tool_progress when no display.platforms entry exists.
 *
 * Ported from gateway/display_config.py
 */
package com.xiaomo.hermes.hermes.gateway

// ---------------------------------------------------------------------------
// Overrideable display settings and their global defaults
// ---------------------------------------------------------------------------

private val _GLOBAL_DEFAULTS: Map<String, Any?> = mapOf(
    "tool_progress" to "all",
    "show_reasoning" to false,
    "tool_preview_length" to 0,
    "streaming" to null)  // null = follow top-level streaming config

// ---------------------------------------------------------------------------
// Sensible per-platform defaults — tiered by platform capability
// ---------------------------------------------------------------------------

private val _TIER_HIGH: Map<String, Any?> = mapOf(
    "tool_progress" to "all",
    "show_reasoning" to false,
    "tool_preview_length" to 40,
    "streaming" to null)

private val _TIER_MEDIUM: Map<String, Any?> = mapOf(
    "tool_progress" to "new",
    "show_reasoning" to false,
    "tool_preview_length" to 40,
    "streaming" to null)

private val _TIER_LOW: Map<String, Any?> = mapOf(
    "tool_progress" to "off",
    "show_reasoning" to false,
    "tool_preview_length" to 40,
    "streaming" to false)

private val _TIER_MINIMAL: Map<String, Any?> = mapOf(
    "tool_progress" to "off",
    "show_reasoning" to false,
    "tool_preview_length" to 0,
    "streaming" to false)

private val _PLATFORM_DEFAULTS: Map<String, Map<String, Any?>> = mapOf(
    "telegram" to _TIER_HIGH,
    "discord" to _TIER_HIGH,
    "slack" to _TIER_MEDIUM,
    "mattermost" to _TIER_MEDIUM,
    "matrix" to _TIER_MEDIUM,
    "feishu" to _TIER_MEDIUM,
    "signal" to _TIER_LOW,
    "whatsapp" to _TIER_MEDIUM,
    "bluebubbles" to _TIER_LOW,
    "weixin" to _TIER_LOW,
    "wecom" to _TIER_LOW,
    "wecom_callback" to _TIER_LOW,
    "dingtalk" to _TIER_LOW,
    "email" to _TIER_MINIMAL,
    "sms" to _TIER_MINIMAL,
    "webhook" to _TIER_MINIMAL,
    "homeassistant" to _TIER_MINIMAL,
    "api_server" to _TIER_HIGH + mapOf("tool_preview_length" to 0))

/** Canonical set of per-platform overrideable keys (for validation). */
val OVERRIDEABLE_KEYS: Set<String> = _GLOBAL_DEFAULTS.keys

/**
 * Resolve a display setting with per-platform override support.
 */
fun resolveDisplaySetting(
    userConfig: Map<String, Any?>,
    platformKey: String,
    setting: String,
    fallback: Any? = null,
): Any? {
    @Suppress("UNCHECKED_CAST")
    val displayCfg = (userConfig["display"] as? Map<String, Any?>) ?: emptyMap()

    // 1. Explicit per-platform override (display.platforms.<platform>.<key>)
    @Suppress("UNCHECKED_CAST")
    val platforms = (displayCfg["platforms"] as? Map<String, Any?>) ?: emptyMap()
    val platOverrides = platforms[platformKey]
    if (platOverrides is Map<*, *>) {
        val v = platOverrides[setting]
        if (v != null) return _normalise(setting, v)
    }

    // 1b. Backward compat: display.tool_progress_overrides.<platform>
    if (setting == "tool_progress") {
        val legacy = displayCfg["tool_progress_overrides"]
        if (legacy is Map<*, *>) {
            val v = legacy[platformKey]
            if (v != null) return _normalise(setting, v)
        }
    }

    // 2. Global user setting (display.<key>).  Skip display.streaming because
    // that key controls only CLI terminal streaming; gateway token streaming is
    // governed by the top-level streaming config plus per-platform overrides.
    if (setting != "streaming") {
        val v = displayCfg[setting]
        if (v != null) return _normalise(setting, v)
    }

    // 3. Built-in platform default
    val platDefaults = _PLATFORM_DEFAULTS[platformKey]
    if (platDefaults != null) {
        val v = platDefaults[setting]
        if (v != null) return v
    }

    // 4. Built-in global default
    val v = _GLOBAL_DEFAULTS[setting]
    if (v != null) return v

    return fallback
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Normalise YAML quirks (bare `off` → False in YAML 1.1). */
private fun _normalise(setting: String, value: Any?): Any? {
    if (setting == "tool_progress") {
        if (value == false) return "off"
        if (value == true) return "all"
        return value.toString().lowercase()
    }
    if (setting in setOf("show_reasoning", "streaming")) {
        if (value is String) return value.lowercase() in setOf("true", "1", "yes", "on")
        return value == true
    }
    if (setting == "tool_preview_length") {
        return try {
            when (value) {
                is Number -> value.toInt()
                is String -> value.toInt()
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }
    return value
}

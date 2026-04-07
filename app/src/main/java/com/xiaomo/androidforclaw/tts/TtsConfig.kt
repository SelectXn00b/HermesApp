package com.xiaomo.androidforclaw.tts

/**
 * OpenClaw module: tts
 * Source: OpenClaw/src/tts/tts-config.ts, status-config.ts
 *
 * Configuration resolution: reads TTS settings from OpenClawConfig.skills["tts"]
 * and exposes typed accessors.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

data class TtsConfig(
    val enabled: Boolean = false,
    val mode: TtsMode = TtsMode.OFF,
    val provider: String? = null,
    val voice: String? = null,
    val maxLength: Int = 4000
)

/** Check whether the TTS skill is enabled in config. */
fun isTtsEnabled(config: OpenClawConfig): Boolean {
    val ttsSkill = config.skills.entries["tts"]
    return ttsSkill?.enabled ?: false
}

/** Check whether a TTS provider is registered and configured. */
fun isTtsProviderConfigured(config: OpenClawConfig): Boolean {
    val ttsSkill = config.skills.entries["tts"]
    val providerName = ttsSkill?.config?.get("provider") as? String
    return !providerName.isNullOrBlank() &&
        TtsProviderRegistry.getSpeechProvider(providerName) != null
}

/** Resolve TTS mode from raw config string. */
fun resolveTtsMode(raw: String?): TtsMode = TtsMode.fromRaw(raw)

/** Build a typed TtsConfig from the OpenClawConfig skills section. */
fun resolveTtsConfig(config: OpenClawConfig): TtsConfig {
    val ttsSkill = config.skills.entries["tts"]
    val skillConfig = ttsSkill?.config ?: emptyMap()
    val rawMode = (skillConfig["mode"] as? String)
        ?: (skillConfig["autoMode"] as? String) // backward compat
    return TtsConfig(
        enabled = ttsSkill?.enabled ?: false,
        mode = resolveTtsMode(rawMode),
        provider = skillConfig["provider"] as? String,
        voice = skillConfig["voice"] as? String,
        maxLength = (skillConfig["maxLength"] as? Number)?.toInt() ?: 4000
    )
}

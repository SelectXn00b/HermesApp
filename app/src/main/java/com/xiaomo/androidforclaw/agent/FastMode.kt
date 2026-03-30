package com.xiaomo.androidforclaw.agent

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/fast-mode.ts
 *
 * AndroidForClaw adaptation: fast mode resolution.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

/**
 * Source of fast mode state.
 */
enum class FastModeSource {
    SESSION, CONFIG, DEFAULT
}

/**
 * Fast mode state.
 * Aligned with OpenClaw FastModeState.
 */
data class FastModeState(
    val enabled: Boolean,
    val source: FastModeSource
)

/**
 * Fast mode resolution — determines if reduced thinking/reasoning is enabled.
 * Aligned with OpenClaw fast-mode.ts.
 */
object FastMode {

    /**
     * Resolve fast mode parameter.
     * Aligned with OpenClaw resolveFastModeParam.
     */
    fun resolveFastModeParam(value: Any?): Boolean? {
        return when (value) {
            is Boolean -> value
            is String -> value.trim().lowercase().let {
                when (it) {
                    "true", "1", "yes", "on" -> true
                    "false", "0", "no", "off" -> false
                    else -> null
                }
            }
            is Number -> value.toInt() != 0
            else -> null
        }
    }

    /**
     * Resolve configured fast mode from config.
     * Aligned with OpenClaw resolveConfiguredFastMode.
     */
    fun resolveConfiguredFastMode(cfg: OpenClawConfig, provider: String, model: String): Boolean? {
        // Per-model config (agents.defaults.models[key].params.fastMode)
        // Not yet in Android config schema — placeholder for future
        return null
    }

    /**
     * Resolve full fast mode state with priority chain.
     * Priority: session override > per-model config > default (false).
     *
     * Aligned with OpenClaw resolveFastModeState.
     */
    fun resolveFastModeState(
        cfg: OpenClawConfig,
        provider: String = "",
        model: String = "",
        sessionOverride: Boolean? = null
    ): FastModeState {
        // Level 1: session override
        if (sessionOverride != null) {
            return FastModeState(enabled = sessionOverride, source = FastModeSource.SESSION)
        }

        // Level 2: per-model config
        val configValue = resolveConfiguredFastMode(cfg, provider, model)
        if (configValue != null) {
            return FastModeState(enabled = configValue, source = FastModeSource.CONFIG)
        }

        // Level 3: default
        return FastModeState(enabled = false, source = FastModeSource.DEFAULT)
    }
}

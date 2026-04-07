package com.xiaomo.androidforclaw.hooks

/**
 * OpenClaw Source Reference:
 * - src/hooks/config.ts
 *
 * Hook eligibility evaluation: checks OS, binary requirements, env vars, config paths.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

/**
 * Default config values used when evaluating hook eligibility.
 * Aligned with OpenClaw DEFAULT_CONFIG_VALUES.
 */
private val DEFAULT_CONFIG_VALUES: Map<String, Boolean> = mapOf(
    "browser.enabled" to true,
    "browser.evaluateEnabled" to true,
    "workspace.dir" to true
)

/**
 * Check if a config path is truthy, with defaults.
 * Aligned with OpenClaw isConfigPathTruthy.
 */
fun isConfigPathTruthy(config: OpenClawConfig?, pathStr: String): Boolean {
    // On Android, we check the default values map first
    val defaultValue = DEFAULT_CONFIG_VALUES[pathStr]
    if (defaultValue != null) return defaultValue
    // If no explicit config exists, treat unknown paths as false
    return false
}

/**
 * Determine if a hook should be included based on eligibility.
 * Aligned with OpenClaw shouldIncludeHook.
 */
fun shouldIncludeHook(
    entry: HookEntry,
    config: OpenClawConfig? = null,
    eligibility: HookEligibilityContext? = null
): Boolean {
    val hookKey = entry.metadata?.hookKey ?: entry.hook.name
    val hookConfig = resolveHookConfig(config, hookKey)

    // Check enable state
    val enableState = resolveHookEnableState(entry, config, hookConfig)
    if (!enableState.enabled) return false

    // Check runtime eligibility
    return evaluateHookRuntimeEligibility(entry, config, hookConfig, eligibility)
}

/**
 * Evaluate runtime eligibility for a hook.
 * Aligned with OpenClaw evaluateHookRuntimeEligibility.
 *
 * Checks: OS platform, binary requirements, env vars, config paths.
 */
private fun evaluateHookRuntimeEligibility(
    entry: HookEntry,
    config: OpenClawConfig?,
    hookConfig: Map<String, Any?>?,
    eligibility: HookEligibilityContext?
): Boolean {
    val metadata = entry.metadata ?: return true

    // Always-on hooks bypass checks
    if (metadata.always == true) return true

    // OS check: on Android, platform is "android" (not darwin/linux/win32)
    val osList = metadata.os
    if (osList != null && osList.isNotEmpty()) {
        val currentPlatform = "android"
        if (!osList.any { it.equals(currentPlatform, ignoreCase = true) }) {
            return false
        }
    }

    // Requires checks
    val requires = metadata.requires
    if (requires != null) {
        // Binary requirements (on Android, most CLI binaries won't be available)
        if (requires.bins != null && requires.bins.isNotEmpty()) {
            for (bin in requires.bins) {
                if (!hasBinary(bin)) return false
            }
        }
        if (requires.anyBins != null && requires.anyBins.isNotEmpty()) {
            if (!requires.anyBins.any { hasBinary(it) }) return false
        }

        // Environment variable requirements
        if (requires.env != null && requires.env.isNotEmpty()) {
            for (envName in requires.env) {
                val envValue = System.getenv(envName)
                    ?: (hookConfig?.get("env") as? Map<*, *>)?.get(envName)?.toString()
                if (envValue.isNullOrBlank()) return false
            }
        }

        // Config path requirements
        if (requires.config != null && requires.config.isNotEmpty()) {
            for (configPath in requires.config) {
                if (!isConfigPathTruthy(config, configPath)) return false
            }
        }
    }

    return true
}

/**
 * Check if a binary is available on the system.
 * On Android, we check common locations or use Runtime.exec to test.
 */
fun hasBinary(bin: String): Boolean {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("which", bin))
        process.waitFor() == 0
    } catch (_: Exception) {
        false
    }
}

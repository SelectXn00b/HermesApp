package com.xiaomo.hermes.hermes.tools

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Environment variable passthrough registry.
 * Skills that declare required_environment_variables need those vars
 * available in sandboxed execution environments.
 * Ported from tools/env_passthrough.py
 */

private const val _TAG = "env_passthrough"

private val _allowedEnvVars: MutableSet<String> = ConcurrentHashMap.newKeySet()

@Volatile private var _configPassthrough: Set<String>? = null

private fun _getAllowed(): MutableSet<String> = _allowedEnvVars

private fun _loadConfigPassthrough(): Set<String> {
    _configPassthrough?.let { return it }
    // Python reads config.yaml's `terminal.env_passthrough` list. Android has no
    // yaml config loader, so we return an empty set — keep the literal keys for
    // alignment with the upstream lookup path.
    val _sectionKey = "terminal"
    val _optionKey = "env_passthrough"
    val result = emptySet<String>()
    try {
        _configPassthrough = result
    } catch (e: Exception) {
        Log.d(_TAG, "Could not read tools.env_passthrough from config: %s".format(e))
    }
    _configPassthrough = result
    return result
}

fun registerEnvPassthrough(varNames: Collection<String>) {
    for (name in varNames) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) continue
        if (_isHermesProviderCredential(trimmed)) {
            Log.w(
                _TAG,
                "env passthrough: refusing to register Hermes provider credential '$trimmed' (blocked by _HERMES_PROVIDER_ENV_BLOCKLIST). Skills must not override the execute_code sandbox's credential scrubbing; see GHSA-rhgp-j443-p4rf."
            )
            continue
        }
        _getAllowed().add(trimmed)
        Log.d(_TAG, "env passthrough: registered $trimmed")
    }
}

fun registerEnvPassthrough(varName: String) {
    registerEnvPassthrough(listOf(varName))
}

fun isEnvPassthrough(varName: String): Boolean {
    if (varName in _getAllowed()) return true
    return varName in _loadConfigPassthrough()
}

fun getAllPassthrough(): Set<String> = _getAllowed().toSet() + _loadConfigPassthrough()

fun clearEnvPassthrough() {
    _getAllowed().clear()
}

/** Python `_is_hermes_provider_credential` — stub. */
private fun _isHermesProviderCredential(key: String): Boolean =
    key.startsWith("HERMES_") && (key.contains("_KEY") || key.contains("_TOKEN"))

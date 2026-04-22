package com.xiaomo.hermes.hermes.tools

import java.util.concurrent.ConcurrentHashMap

/**
 * Environment variable passthrough registry.
 * Skills that declare required_environment_variables need those vars
 * available in sandboxed execution environments.
 * Ported from env_passthrough.py
 */
object EnvPassthrough {

    // Session-scoped set of env var names allowed to pass through
    private val _allowedEnvVars = ConcurrentHashMap.newKeySet<String>()

    // Config-based passthrough (loaded once)
    @Volatile
    private var _configPassthrough: Set<String>? = null

    /**
     * Register environment variable names as allowed in sandboxed environments.
     */
    fun registerEnvPassthrough(varNames: Collection<String>) {
        for (name in varNames) {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) {
                _allowedEnvVars.add(trimmed)
            }
        }
    }

    /**
     * Register a single environment variable.
     */
    fun registerEnvPassthrough(varName: String) {
        val trimmed = varName.trim()
        if (trimmed.isNotEmpty()) {
            _allowedEnvVars.add(trimmed)
        }
    }

    /**
     * Check whether a variable is allowed to pass through to sandboxes.
     */
    fun isEnvPassthrough(varName: String): Boolean {
        if (varName in _allowedEnvVars) return true
        return varName in loadConfigPassthrough()
    }

    /**
     * Return the union of skill-registered and config-based passthrough vars.
     */
    fun getAllPassthrough(): Set<String> {
        return _allowedEnvVars.toSet() + loadConfigPassthrough()
    }

    /**
     * Reset the skill-scoped allowlist.
     */
    fun clearEnvPassthrough() {
        _allowedEnvVars.clear()
    }

    /**
     * Load config-based passthrough. Override this to provide your own config source.
     */
    private fun loadConfigPassthrough(): Set<String> {
        _configPassthrough?.let { return it }
        // Android: no config.yaml by default; provide empty set
        val result = emptySet<String>()
        _configPassthrough = result
        return result
    }

    /**
     * Allow external configuration of passthrough vars.
     */
    fun setConfigPassthrough(vars: Set<String>) {
        _configPassthrough = vars
    }


}

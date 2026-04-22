package com.xiaomo.hermes.hermes.tools

import java.util.concurrent.ConcurrentHashMap

/**
 * Environment variable passthrough registry.
 * Skills that declare required_environment_variables need those vars
 * available in sandboxed execution environments.
 * Ported from tools/env_passthrough.py
 */

private val _allowedEnvVars: MutableSet<String> = ConcurrentHashMap.newKeySet()

@Volatile private var _configPassthrough: Set<String>? = null

private fun _getAllowed(): MutableSet<String> = _allowedEnvVars

private fun _loadConfigPassthrough(): Set<String> {
    _configPassthrough?.let { return it }
    val result = emptySet<String>()
    _configPassthrough = result
    return result
}

fun registerEnvPassthrough(varNames: Collection<String>) {
    for (name in varNames) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) _getAllowed().add(trimmed)
    }
}

fun registerEnvPassthrough(varName: String) {
    val trimmed = varName.trim()
    if (trimmed.isNotEmpty()) _getAllowed().add(trimmed)
}

fun isEnvPassthrough(varName: String): Boolean {
    if (varName in _getAllowed()) return true
    return varName in _loadConfigPassthrough()
}

fun getAllPassthrough(): Set<String> = _getAllowed().toSet() + _loadConfigPassthrough()

fun clearEnvPassthrough() {
    _getAllowed().clear()
}

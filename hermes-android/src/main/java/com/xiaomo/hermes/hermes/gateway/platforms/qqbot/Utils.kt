/** 1:1 对齐 hermes/gateway/platforms/qqbot/utils.py */
package com.xiaomo.hermes.hermes.gateway.platforms.qqbot

import android.os.Build

private const val QQBOT_VERSION = "1.0"

// ---------------------------------------------------------------------------
// User-Agent
// ---------------------------------------------------------------------------

private fun _getHermesVersion(): String {
    return try {
        val clazz = Class.forName("com.xiaomo.hermes.BuildConfig")
        clazz.getField("VERSION_NAME").get(null) as? String ?: "dev"
    } catch (_: Exception) {
        "dev"
    }
}

fun buildUserAgent(): String {
    val osName = "android"
    val hermesVersion = _getHermesVersion()
    return "QQBotAdapter/$QQBOT_VERSION (Android/${Build.VERSION.SDK_INT}; $osName; Hermes/$hermesVersion)"
}

fun getApiHeaders(): Map<String, String> {
    return mapOf(
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "User-Agent" to buildUserAgent()
    )
}

// ---------------------------------------------------------------------------
// Config helpers
// ---------------------------------------------------------------------------

fun coerceList(value: Any?): List<String> {
    if (value == null) return emptyList()
    if (value is String) {
        return value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    if (value is Collection<*>) {
        return value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
    }
    val s = value.toString().trim()
    return if (s.isNotEmpty()) listOf(s) else emptyList()
}

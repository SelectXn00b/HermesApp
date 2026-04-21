package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.core.application.OperitApplication
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal data class ToolPkgAppLifecycleHookRegistration(
    val containerPackageName: String,
    val hookId: String,
    val event: String,
    val functionName: String,
    val functionSource: String? = null
)

internal data class ToolPkgMessageProcessingHookRegistration(
    val containerPackageName: String,
    val pluginId: String,
    val functionName: String,
    val functionSource: String? = null
)

internal data class ToolPkgXmlRenderHookRegistration(
    val containerPackageName: String,
    val pluginId: String,
    val tag: String,
    val functionName: String,
    val functionSource: String? = null
)

internal data class ToolPkgInputMenuToggleHookRegistration(
    val containerPackageName: String,
    val pluginId: String,
    val functionName: String,
    val functionSource: String? = null
)

internal data class ToolPkgToolLifecycleHookRegistration(
    val containerPackageName: String,
    val hookId: String,
    val functionName: String,
    val functionSource: String? = null
)

internal data class ToolPkgPromptHookRegistration(
    val containerPackageName: String,
    val hookId: String,
    val functionName: String,
    val functionSource: String? = null
)

internal fun toolPkgPackageManager(): PackageManager {
    val application = OperitApplication.instance.applicationContext
    return PackageManager.getInstance(application, AIToolHandler.getInstance(application))
}

internal fun decodeToolPkgHookResult(raw: Any?): Any? {
    val text = raw?.toString().orEmpty()
    if (text.isEmpty()) {
        return null
    }
    val normalized = text.trim()
    if (normalized.isEmpty()) {
        return text
    }
    if (normalized.startsWith("Error:", ignoreCase = true)) {
        throw IllegalStateException(normalized.substringAfter(":", normalized).trim().ifEmpty { normalized })
    }
    return try {
        JSONTokener(normalized).nextValue()
    } catch (_: Exception) {
        text
    }
}

internal fun jsonObjectToMap(jsonObject: JSONObject): Map<String, Any?> {
    val result = linkedMapOf<String, Any?>()
    val keys = jsonObject.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        result[key] = jsonValueToKotlin(jsonObject.opt(key))
    }
    return result
}

internal fun jsonArrayToList(jsonArray: JSONArray): List<Any?> {
    val result = mutableListOf<Any?>()
    for (index in 0 until jsonArray.length()) {
        result.add(jsonValueToKotlin(jsonArray.opt(index)))
    }
    return result
}

internal fun jsonValueToKotlin(value: Any?): Any? {
    return when (value) {
        null,
        JSONObject.NULL -> null
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> jsonArrayToList(value)
        else -> value
    }
}

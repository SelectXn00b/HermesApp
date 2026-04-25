package com.ai.assistance.fbx

import org.json.JSONArray
import org.json.JSONObject

data class FbxModelInfo(
    val modelName: String,
    val animationNames: List<String>,
    val animationDurationMillisByName: Map<String, Long>,
    val requiredExternalFiles: List<String>,
    val missingExternalFiles: List<String>
) {
    val defaultAnimation: String?
        get() = animationNames.firstOrNull()
}

object FbxInspector {

    fun isAvailable(): Boolean = FbxNative.nativeIsAvailable()

    fun unavailableReason(): String = FbxNative.nativeGetUnavailableReason()

    fun getLastError(): String = FbxNative.nativeGetLastError()

    fun inspectModel(pathModel: String): FbxModelInfo? {
        val rawJson = FbxNative.nativeInspectModel(pathModel) ?: return null
        return runCatching {
            val root = JSONObject(rawJson)
            val animationNames = root.optJSONArray("animationNames").toStringList()
            val animationDurations = root.optJSONArray("animationDurationsMillis")
            val durationMap =
                buildMap {
                    animationNames.forEachIndexed { index, animationName ->
                        val durationMillis = animationDurations?.optLong(index, 0L)?.coerceAtLeast(0L) ?: 0L
                        if (durationMillis > 0L) {
                            put(animationName, durationMillis)
                        }
                    }
                }

            FbxModelInfo(
                modelName = root.optString("modelName").ifBlank {
                    pathModel.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
                },
                animationNames = animationNames,
                animationDurationMillisByName = durationMap,
                requiredExternalFiles = root.optJSONArray("requiredExternalFiles").toStringList(),
                missingExternalFiles = root.optJSONArray("missingExternalFiles").toStringList()
            )
        }.getOrNull()
    }
}

internal fun JSONArray?.toStringList(): List<String> {
    if (this == null) {
        return emptyList()
    }

    return buildList(length()) {
        for (index in 0 until length()) {
            val value = optString(index).trim()
            if (value.isNotEmpty()) {
                add(value)
            }
        }
    }
}

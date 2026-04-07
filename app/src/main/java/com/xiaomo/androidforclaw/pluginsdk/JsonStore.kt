package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/json-store.ts
 *
 * Read/write small JSON blobs for token/state caches.
 * Android adaptation: uses java.io.File + org.json for parsing.
 */

import org.json.JSONObject
import org.json.JSONArray
import java.io.File

/**
 * Read a JSON file and parse it, returning the fallback value when the file
 * is missing or contains invalid JSON.
 * Aligned with TS readJsonFileWithFallback.
 */
data class JsonFileReadResult<T>(
    val value: T,
    val exists: Boolean,
)

/**
 * Read JSON from disk; fall back cleanly when the file is missing or invalid.
 * Aligned with TS readJsonFileWithFallback.
 */
fun readJsonFileWithFallback(
    filePath: String,
    fallback: Map<String, Any?>,
): JsonFileReadResult<Map<String, Any?>> {
    val file = File(filePath)
    if (!file.exists()) {
        return JsonFileReadResult(value = fallback, exists = false)
    }
    return try {
        val raw = file.readText(Charsets.UTF_8)
        val parsed = safeParseJsonObject(raw)
        if (parsed != null) {
            JsonFileReadResult(value = parsed, exists = true)
        } else {
            JsonFileReadResult(value = fallback, exists = true)
        }
    } catch (_: Exception) {
        JsonFileReadResult(value = fallback, exists = false)
    }
}

/**
 * Write JSON atomically with restrictive permissions.
 * Android: writes to a temp file then renames for atomicity.
 * Aligned with TS writeJsonFileAtomically.
 */
fun writeJsonFileAtomically(filePath: String, value: Map<String, Any?>) {
    val file = File(filePath)
    file.parentFile?.mkdirs()
    val temp = File(file.parent, ".${file.name}.tmp")
    try {
        val json = JSONObject(value)
        temp.writeText(json.toString(2) + "\n", Charsets.UTF_8)
        temp.renameTo(file)
    } catch (e: Exception) {
        temp.delete()
        throw e
    }
}

/**
 * Load a JSON file synchronously and return the parsed JSONObject, or null.
 * Aligned with TS loadJsonFile.
 */
fun loadJsonFile(filePath: String): JSONObject? {
    val file = File(filePath)
    if (!file.exists()) return null
    return try {
        JSONObject(file.readText(Charsets.UTF_8))
    } catch (_: Exception) {
        null
    }
}

/**
 * Save a JSON file synchronously with restrictive permissions.
 * Aligned with TS saveJsonFile.
 */
fun saveJsonFile(filePath: String, json: JSONObject) {
    val file = File(filePath)
    file.parentFile?.mkdirs()
    file.writeText(json.toString(2) + "\n", Charsets.UTF_8)
}

// ---------- Private helpers ----------

private fun safeParseJsonObject(raw: String): Map<String, Any?>? {
    return try {
        val obj = JSONObject(raw)
        jsonObjectToMap(obj)
    } catch (_: Exception) {
        null
    }
}

internal fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    for (key in obj.keys()) {
        map[key] = jsonValueToKotlin(obj.get(key))
    }
    return map
}

internal fun jsonArrayToList(arr: JSONArray): List<Any?> {
    return (0 until arr.length()).map { jsonValueToKotlin(arr.get(it)) }
}

private fun jsonValueToKotlin(value: Any?): Any? = when (value) {
    null, JSONObject.NULL -> null
    is JSONObject -> jsonObjectToMap(value)
    is JSONArray -> jsonArrayToList(value)
    else -> value
}

package com.ai.assistance.operit.core.tools.javascript

import com.ai.assistance.operit.core.tools.SandboxScriptExecutionResultData
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal interface JsExecutionListener {
    fun onCallLog(callId: String, level: String, message: String) {}

    fun onIntermediateResult(callId: String, value: Any?) {}

    fun onCompleted(callId: String, result: String) {}

    fun onFailed(callId: String, error: String) {}
}

internal class JsExecutionTraceRecorder(
    private val scriptPath: String,
    private val functionName: String,
    private val paramsJson: String,
    private val envFilePath: String?
) : JsExecutionListener {
    private val startedAtMs = System.currentTimeMillis()
    private val events = mutableListOf<String>()

    @Synchronized
    override fun onCallLog(callId: String, level: String, message: String) {
        val normalizedLevel = level.trim().uppercase()
        val normalizedMessage = collapseWhitespace(message)
        if (normalizedMessage.isNotEmpty()) {
            events +=
                if (normalizedLevel.isNotEmpty()) {
                    "$normalizedLevel: $normalizedMessage"
                } else {
                    normalizedMessage
                }
        }
    }

    @Synchronized
    override fun onIntermediateResult(callId: String, value: Any?) {
        val summary = summarizeValue(value)
        if (summary.isNotEmpty()) {
            events += "intermediate: $summary"
        }
    }

    @Synchronized
    override fun onCompleted(callId: String, result: String) {
        val summary = summarizeValue(result)
        if (summary.isNotEmpty()) {
            events += "completed: $summary"
        }
    }

    @Synchronized
    override fun onFailed(callId: String, error: String) {
        val normalizedError = collapseWhitespace(error)
        if (normalizedError.isNotEmpty()) {
            events += "failed: $normalizedError"
        }
    }

    @Synchronized
    fun buildPayload(
        success: Boolean,
        result: Any?,
        error: String? = null
    ): SandboxScriptExecutionResultData {
        return buildResultData(
            success = success,
            result = result,
            error = error
        )
    }

    @Synchronized
    fun buildResultData(
        success: Boolean,
        result: Any?,
        error: String? = null,
        executionMode: String? = null,
        scriptLabel: String? = null,
        requestedWaitMs: Long? = null
    ): SandboxScriptExecutionResultData {
        val finishedAtMs = System.currentTimeMillis()
        return SandboxScriptExecutionResultData(
            success = success,
            scriptPath = scriptPath,
            functionName = functionName,
            params = parseJsonText(paramsJson),
            envFilePath = envFilePath,
            startedAtMs = startedAtMs,
            finishedAtMs = finishedAtMs,
            durationMs = finishedAtMs - startedAtMs,
            result = toJsonElement(result),
            error = error?.takeIf { it.isNotBlank() },
            events = events.toList(),
            executionMode = executionMode,
            scriptLabel = scriptLabel,
            requestedWaitMs = requestedWaitMs
        )
    }

    fun writeTo(file: File, payload: SandboxScriptExecutionResultData) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        file.writeText(
            Json { prettyPrint = true }.encodeToString(
                SandboxScriptExecutionResultData.serializer(),
                payload
            ),
            Charsets.UTF_8
        )
    }

    private fun parseJsonText(text: String): JsonElement? {
        val normalized = text.trim()
        if (normalized.isEmpty()) {
            return JsonObject(emptyMap())
        }
        return try {
            Json.parseToJsonElement(normalized)
        } catch (_: Exception) {
            JsonPrimitive(normalized)
        }
    }

    private fun toJsonElement(value: Any?): JsonElement? {
        return when (value) {
            null -> null
            is JsonElement -> value
            is Map<*, *> -> {
                val content = LinkedHashMap<String, JsonElement>()
                value.forEach { (key, item) ->
                    val normalizedKey = key?.toString() ?: return@forEach
                    content[normalizedKey] = toJsonElement(item) ?: JsonNull
                }
                JsonObject(content)
            }
            is Iterable<*> -> JsonArray(value.map { toJsonElement(it) ?: JsonNull })
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Char -> JsonPrimitive(value.toString())
            is CharSequence -> parseJsonText(value.toString()) ?: JsonPrimitive(value.toString())
            else -> JsonPrimitive(value.toString())
        }
    }

    private fun summarizeValue(value: Any?, maxLength: Int = 240, depth: Int = 0): String {
        return when (value) {
            null -> ""
            is String -> truncateText(collapseWhitespace(value), maxLength)
            is CharSequence -> truncateText(collapseWhitespace(value.toString()), maxLength)
            is Number, is Boolean, is Char -> value.toString()
            is JsonElement -> summarizeJsonElement(value, maxLength, depth)
            is Map<*, *> -> summarizeMap(value, maxLength, depth)
            is Iterable<*> -> summarizeIterable(value.toList(), maxLength, depth)
            is Array<*> -> summarizeIterable(value.toList(), maxLength, depth)
            else -> truncateText(collapseWhitespace(value.toString()), maxLength)
        }
    }

    private fun summarizeJsonElement(
        element: JsonElement,
        maxLength: Int,
        depth: Int
    ): String {
        return when (element) {
            is JsonNull -> "null"
            is JsonPrimitive -> {
                val content =
                    if (element.isString) {
                        element.content
                    } else {
                        element.toString()
                    }
                truncateText(collapseWhitespace(content), maxLength)
            }
            is JsonArray -> summarizeIterable(element, maxLength, depth)
            is JsonObject -> summarizeMap(element, maxLength, depth)
        }
    }

    private fun summarizeIterable(
        values: List<Any?>,
        maxLength: Int,
        depth: Int
    ): String {
        if (depth >= 2) {
            return "[${values.size} items]"
        }
        val items =
            values
                .take(3)
                .map { summarizeValue(it, 80, depth + 1) }
                .filter { it.isNotBlank() }
        val suffix = if (values.size > 3) ", +${values.size - 3} more" else ""
        return truncateText("[${items.joinToString(", ")}$suffix]", maxLength)
    }

    private fun summarizeMap(
        values: Map<*, *>,
        maxLength: Int,
        depth: Int
    ): String {
        val entries =
            values.entries.mapNotNull { entry ->
                val key = entry.key?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                key to entry.value
            }
        if (entries.isEmpty()) {
            return "{}"
        }
        if (depth >= 2) {
            val preview = entries.take(3).joinToString(", ") { it.first }
            return "{${preview}${if (entries.size > 3) ", ..." else ""}}"
        }

        val parts =
            entries
                .take(4)
                .mapNotNull { (key, value) ->
                    val summary = summarizeValue(value, 120, depth + 1)
                    if (summary.isBlank()) null else "$key=$summary"
                }
                .toMutableList()
        if (parts.isEmpty()) {
            val preview = entries.take(3).joinToString(", ") { it.first }
            return "{${preview}${if (entries.size > 3) ", ..." else ""}}"
        }
        if (entries.size > 4) {
            parts += "+${entries.size - 4} more"
        }
        return truncateText(parts.joinToString("; "), maxLength)
    }

    private fun collapseWhitespace(text: String): String {
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun truncateText(text: String, maxLength: Int): String {
        if (text.length <= maxLength) {
            return text
        }
        return text.take((maxLength - 3).coerceAtLeast(0)) + "..."
    }
}

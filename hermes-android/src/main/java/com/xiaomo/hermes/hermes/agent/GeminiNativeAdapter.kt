package com.xiaomo.hermes.hermes.agent

/**
 * OpenAI-compatible facade over Google AI Studio's native Gemini API.
 *
 * Hermes keeps `api_mode='chat_completions'` for the `gemini` provider so the
 * main agent loop can keep using its existing OpenAI-shaped message flow.
 * This adapter is the transport shim that converts those OpenAI-style
 * `messages[]` / `tools[]` requests into Gemini's native
 * `models/{model}:generateContent` schema and converts the responses back.
 *
 * Ported from agent/gemini_native_adapter.py
 */

import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val _TAG = "gemini_native"

const val DEFAULT_GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

/** Return true when the endpoint speaks Gemini's native REST API. */
fun isNativeGeminiBaseUrl(baseUrl: String?): Boolean {
    val normalized = (baseUrl ?: "").trim().trimEnd('/').lowercase()
    if (normalized.isEmpty()) return false
    if ("generativelanguage.googleapis.com" !in normalized) return false
    return !normalized.endsWith("/openai")
}

/** Error shape compatible with Hermes retry/error classification. */
class GeminiAPIError(
    message: String,
    val code: String = "gemini_api_error",
    val statusCode: Int? = null,
    val response: Any? = null,
    val retryAfter: Double? = null,
    val details: Map<String, Any?> = emptyMap()) : Exception(message)

// ---------------------------------------------------------------------------
// Content helpers
// ---------------------------------------------------------------------------

@Suppress("UNCHECKED_CAST")
private fun _coerceContentToText(content: Any?): String {
    if (content == null) return ""
    if (content is String) return content
    if (content is List<*>) {
        val pieces = mutableListOf<String>()
        for (part in content) {
            if (part is String) {
                pieces.add(part)
            } else if (part is Map<*, *>) {
                val partMap = part as Map<String, Any?>
                if (partMap["type"] == "text") {
                    val text = partMap["text"]
                    if (text is String) pieces.add(text)
                }
            }
        }
        return pieces.joinToString("\n")
    }
    return content.toString()
}

@Suppress("UNCHECKED_CAST")
private fun _extractMultimodalParts(content: Any?): List<Map<String, Any?>> {
    if (content !is List<*>) {
        val text = _coerceContentToText(content)
        return if (text.isNotEmpty()) listOf(mapOf("text" to text)) else emptyList()
    }

    val parts = mutableListOf<Map<String, Any?>>()
    for (item in content) {
        if (item is String) {
            parts.add(mapOf("text" to item))
            continue
        }
        if (item !is Map<*, *>) continue
        val itemMap = item as Map<String, Any?>
        when (itemMap["type"]) {
            "text" -> {
                val text = itemMap["text"]
                if (text is String && text.isNotEmpty()) {
                    parts.add(mapOf("text" to text))
                }
            }
            "image_url" -> {
                val imageRef = itemMap["image_url"] as? Map<String, Any?> ?: emptyMap()
                val url = imageRef["url"] as? String ?: ""
                if (!url.startsWith("data:")) continue
                try {
                    val commaIdx = url.indexOf(",")
                    if (commaIdx < 0) continue
                    val header = url.substring(0, commaIdx)
                    val encoded = url.substring(commaIdx + 1)
                    val colonIdx = header.indexOf(":")
                    if (colonIdx < 0) continue
                    val afterColon = header.substring(colonIdx + 1)
                    val semiIdx = afterColon.indexOf(";")
                    val mime = if (semiIdx >= 0) afterColon.substring(0, semiIdx) else afterColon
                    val raw = Base64.decode(encoded, Base64.DEFAULT)
                    parts.add(mapOf(
                        "inlineData" to mapOf(
                            "mimeType" to mime,
                            "data" to Base64.encodeToString(raw, Base64.NO_WRAP))))
                } catch (_: Exception) {
                    continue
                }
            }
        }
    }
    return parts
}

@Suppress("UNCHECKED_CAST")
private fun _toolCallExtraSignature(toolCall: Map<String, Any?>): String? {
    val extra = toolCall["extra_content"] as? Map<String, Any?> ?: return null
    val google = extra["google"] ?: extra["thought_signature"]
    if (google is Map<*, *>) {
        val googleMap = google as Map<String, Any?>
        val sig = googleMap["thought_signature"] ?: googleMap["thoughtSignature"]
        return if (sig is String && sig.isNotEmpty()) sig else null
    }
    if (google is String && google.isNotEmpty()) return google
    return null
}

@Suppress("UNCHECKED_CAST")
private fun _translateToolCallToGemini(toolCall: Map<String, Any?>): Map<String, Any?> {
    val fn = (toolCall["function"] as? Map<String, Any?>) ?: emptyMap()
    val argsRaw = fn["arguments"] ?: ""
    var args: Any? = try {
        if (argsRaw is String && argsRaw.isNotEmpty()) {
            _parseJsonToMap(argsRaw)
        } else emptyMap<String, Any?>()
    } catch (_: Exception) {
        mapOf("_raw" to argsRaw)
    }
    if (args !is Map<*, *>) args = mapOf("_value" to args)

    val part = mutableMapOf<String, Any?>(
        "functionCall" to mapOf(
            "name" to (fn["name"]?.toString() ?: ""),
            "args" to args))
    val sig = _toolCallExtraSignature(toolCall)
    if (sig != null) part["thoughtSignature"] = sig
    return part
}

@Suppress("UNCHECKED_CAST")
private fun _translateToolResultToGemini(
    message: Map<String, Any?>,
    toolNameByCallId: Map<String, String>? = null): Map<String, Any?> {
    val byId = toolNameByCallId ?: emptyMap()
    val toolCallId = (message["tool_call_id"]?.toString() ?: "")
    val name = (message["name"] as? String)
        ?: byId[toolCallId]
        ?: toolCallId.ifEmpty { "tool" }
    val content = _coerceContentToText(message["content"])
    val parsed: Any? = try {
        val trimmed = content.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) _parseJsonToAny(content)
        else null
    } catch (_: Exception) {
        null
    }
    val response: Map<String, Any?> = if (parsed is Map<*, *>) parsed as Map<String, Any?>
        else mapOf("output" to content)
    return mapOf(
        "functionResponse" to mapOf(
            "name" to name,
            "response" to response))
}

@Suppress("UNCHECKED_CAST")
private fun _buildGeminiContents(
    messages: List<Map<String, Any?>>): Pair<List<Map<String, Any?>>, Map<String, Any?>?> {
    val systemTextParts = mutableListOf<String>()
    val contents = mutableListOf<Map<String, Any?>>()
    val toolNameByCallId = mutableMapOf<String, String>()

    for (msg in messages) {
        val role = msg["role"]?.toString() ?: "user"

        if (role == "system") {
            systemTextParts.add(_coerceContentToText(msg["content"]))
            continue
        }

        if (role == "tool" || role == "function") {
            contents.add(mapOf(
                "role" to "user",
                "parts" to listOf(_translateToolResultToGemini(msg, toolNameByCallId))))
            continue
        }

        val geminiRole = if (role == "assistant") "model" else "user"
        val parts = mutableListOf<Map<String, Any?>>()

        parts.addAll(_extractMultimodalParts(msg["content"]))

        val toolCalls = msg["tool_calls"]
        if (toolCalls is List<*>) {
            for (toolCall in toolCalls) {
                if (toolCall is Map<*, *>) {
                    val tcMap = toolCall as Map<String, Any?>
                    val toolCallId = (tcMap["id"]?.toString() ?: tcMap["call_id"]?.toString() ?: "")
                    val toolName = ((tcMap["function"] as? Map<String, Any?>)?.get("name")?.toString() ?: "")
                    if (toolCallId.isNotEmpty() && toolName.isNotEmpty()) {
                        toolNameByCallId[toolCallId] = toolName
                    }
                    parts.add(_translateToolCallToGemini(tcMap))
                }
            }
        }

        if (parts.isNotEmpty()) {
            contents.add(mapOf("role" to geminiRole, "parts" to parts))
        }
    }

    val joinedSystem = systemTextParts.filter { it.isNotEmpty() }.joinToString("\n").trim()
    val systemInstruction: Map<String, Any?>? = if (joinedSystem.isNotEmpty()) {
        mapOf("parts" to listOf(mapOf("text" to joinedSystem)))
    } else null
    return contents to systemInstruction
}

@Suppress("UNCHECKED_CAST")
private fun _translateToolsToGemini(tools: Any?): List<Map<String, Any?>> {
    if (tools !is List<*>) return emptyList()
    val declarations = mutableListOf<Map<String, Any?>>()
    for (tool in tools) {
        if (tool !is Map<*, *>) continue
        val toolMap = tool as Map<String, Any?>
        val fn = toolMap["function"] as? Map<String, Any?> ?: continue
        val name = fn["name"]
        if (name !is String || name.isEmpty()) continue
        val decl = mutableMapOf<String, Any?>("name" to name)
        val description = fn["description"]
        if (description is String && description.isNotEmpty()) decl["description"] = description
        val parameters = fn["parameters"]
        if (parameters is Map<*, *>) {
            decl["parameters"] = sanitizeGeminiToolParameters(parameters)
        }
        declarations.add(decl)
    }
    return if (declarations.isNotEmpty()) listOf(mapOf("functionDeclarations" to declarations)) else emptyList()
}

@Suppress("UNCHECKED_CAST")
private fun _translateToolChoiceToGemini(toolChoice: Any?): Map<String, Any?>? {
    if (toolChoice == null) return null
    if (toolChoice is String) {
        return when (toolChoice) {
            "auto" -> mapOf("functionCallingConfig" to mapOf("mode" to "AUTO"))
            "required" -> mapOf("functionCallingConfig" to mapOf("mode" to "ANY"))
            "none" -> mapOf("functionCallingConfig" to mapOf("mode" to "NONE"))
            else -> null
        }
    }
    if (toolChoice is Map<*, *>) {
        val tcMap = toolChoice as Map<String, Any?>
        val fn = tcMap["function"] as? Map<String, Any?> ?: emptyMap()
        val name = fn["name"]
        if (name is String && name.isNotEmpty()) {
            return mapOf("functionCallingConfig" to mapOf(
                "mode" to "ANY",
                "allowedFunctionNames" to listOf(name)))
        }
    }
    return null
}

@Suppress("UNCHECKED_CAST")
private fun _normalizeThinkingConfig(config: Any?): Map<String, Any?>? {
    if (config !is Map<*, *> || (config as Map<String, Any?>).isEmpty()) return null
    val cfg = config
    val budget = cfg["thinkingBudget"] ?: cfg["thinking_budget"]
    val include = cfg["includeThoughts"] ?: cfg["include_thoughts"]
    val level = cfg["thinkingLevel"] ?: cfg["thinking_level"]
    val normalized = mutableMapOf<String, Any?>()
    if (budget is Number) normalized["thinkingBudget"] = budget.toInt()
    if (include is Boolean) normalized["includeThoughts"] = include
    if (level is String && level.trim().isNotEmpty()) {
        normalized["thinkingLevel"] = level.trim().lowercase()
    }
    return if (normalized.isNotEmpty()) normalized else null
}

private fun buildGeminiRequest(
    messages: List<Map<String, Any?>>,
    tools: Any? = null,
    toolChoice: Any? = null,
    temperature: Double? = null,
    maxTokens: Int? = null,
    topP: Double? = null,
    stop: Any? = null,
    thinkingConfig: Any? = null): Map<String, Any?> {
    val (contents, systemInstruction) = _buildGeminiContents(messages)
    val request = mutableMapOf<String, Any?>("contents" to contents)
    if (systemInstruction != null) request["systemInstruction"] = systemInstruction

    val geminiTools = _translateToolsToGemini(tools)
    if (geminiTools.isNotEmpty()) request["tools"] = geminiTools

    val toolConfig = _translateToolChoiceToGemini(toolChoice)
    if (toolConfig != null) request["toolConfig"] = toolConfig

    val generationConfig = mutableMapOf<String, Any?>()
    if (temperature != null) generationConfig["temperature"] = temperature
    if (maxTokens != null) generationConfig["maxOutputTokens"] = maxTokens
    if (topP != null) generationConfig["topP"] = topP
    if (stop != null) {
        generationConfig["stopSequences"] = if (stop is List<*>) stop else listOf(stop.toString())
    }
    val normalizedThinking = _normalizeThinkingConfig(thinkingConfig)
    if (normalizedThinking != null) generationConfig["thinkingConfig"] = normalizedThinking
    if (generationConfig.isNotEmpty()) request["generationConfig"] = generationConfig

    return request
}

private fun _mapGeminiFinishReason(reason: String?): String {
    val key = (reason ?: "").uppercase()
    return when (key) {
        "STOP" -> "stop"
        "MAX_TOKENS" -> "length"
        "SAFETY", "RECITATION" -> "content_filter"
        "OTHER" -> "stop"
        else -> "stop"
    }
}

private fun _toolCallExtraFromPart(part: Map<String, Any?>): Map<String, Any?>? {
    val sig = part["thoughtSignature"]
    if (sig is String && sig.isNotEmpty()) {
        return mapOf("google" to mapOf("thought_signature" to sig))
    }
    return null
}

private fun _emptyResponse(model: String): Map<String, Any?> {
    val message = mapOf<String, Any?>(
        "role" to "assistant",
        "content" to "",
        "tool_calls" to null,
        "reasoning" to null,
        "reasoning_content" to null,
        "reasoning_details" to null)
    val choice = mapOf<String, Any?>(
        "index" to 0,
        "message" to message,
        "finish_reason" to "stop")
    val usage = mapOf<String, Any?>(
        "prompt_tokens" to 0,
        "completion_tokens" to 0,
        "total_tokens" to 0,
        "prompt_tokens_details" to mapOf("cached_tokens" to 0))
    return mapOf(
        "id" to "chatcmpl-${UUID.randomUUID().toString().replace("-", "").substring(0, 12)}",
        "object" to "chat.completion",
        "created" to (System.currentTimeMillis() / 1000).toInt(),
        "model" to model,
        "choices" to listOf(choice),
        "usage" to usage)
}

@Suppress("UNCHECKED_CAST")
private fun translateGeminiResponse(resp: Map<String, Any?>, model: String): Map<String, Any?> {
    val candidates = resp["candidates"]
    if (candidates !is List<*> || candidates.isEmpty()) {
        return _emptyResponse(model)
    }

    val cand = (candidates[0] as? Map<String, Any?>) ?: emptyMap()
    val contentObj = cand["content"] as? Map<String, Any?> ?: emptyMap()
    val parts = contentObj["parts"] as? List<*> ?: emptyList<Any?>()

    val textPieces = mutableListOf<String>()
    val reasoningPieces = mutableListOf<String>()
    val toolCalls = mutableListOf<Map<String, Any?>>()

    for ((index, part) in parts.withIndex()) {
        if (part !is Map<*, *>) continue
        val partMap = part as Map<String, Any?>
        if (partMap["thought"] == true && partMap["text"] is String) {
            reasoningPieces.add(partMap["text"] as String)
            continue
        }
        if (partMap["text"] is String) {
            textPieces.add(partMap["text"] as String)
            continue
        }
        val fc = partMap["functionCall"] as? Map<String, Any?>
        if (fc != null && fc["name"] != null) {
            val argsStr = try {
                JSONObject((fc["args"] as? Map<String, Any?>) ?: emptyMap<String, Any?>()).toString()
            } catch (_: Exception) {
                "{}"
            }
            val toolCall = mutableMapOf<String, Any?>(
                "id" to "call_${UUID.randomUUID().toString().replace("-", "").substring(0, 12)}",
                "type" to "function",
                "index" to index,
                "function" to mapOf(
                    "name" to fc["name"].toString(),
                    "arguments" to argsStr))
            val extraContent = _toolCallExtraFromPart(partMap)
            if (extraContent != null) toolCall["extra_content"] = extraContent
            toolCalls.add(toolCall)
        }
    }

    val finishReason = if (toolCalls.isNotEmpty()) "tool_calls"
        else _mapGeminiFinishReason(cand["finishReason"]?.toString())
    val usageMeta = resp["usageMetadata"] as? Map<String, Any?> ?: emptyMap()
    val usage = mapOf<String, Any?>(
        "prompt_tokens" to ((usageMeta["promptTokenCount"] as? Number)?.toInt() ?: 0),
        "completion_tokens" to ((usageMeta["candidatesTokenCount"] as? Number)?.toInt() ?: 0),
        "total_tokens" to ((usageMeta["totalTokenCount"] as? Number)?.toInt() ?: 0),
        "prompt_tokens_details" to mapOf(
            "cached_tokens" to ((usageMeta["cachedContentTokenCount"] as? Number)?.toInt() ?: 0)))
    val reasoning = if (reasoningPieces.isNotEmpty()) reasoningPieces.joinToString("") else null
    val message = mapOf<String, Any?>(
        "role" to "assistant",
        "content" to if (textPieces.isNotEmpty()) textPieces.joinToString("") else null,
        "tool_calls" to if (toolCalls.isNotEmpty()) toolCalls else null,
        "reasoning" to reasoning,
        "reasoning_content" to reasoning,
        "reasoning_details" to null)
    val choice = mapOf<String, Any?>(
        "index" to 0,
        "message" to message,
        "finish_reason" to finishReason)
    return mapOf(
        "id" to "chatcmpl-${UUID.randomUUID().toString().replace("-", "").substring(0, 12)}",
        "object" to "chat.completion",
        "created" to (System.currentTimeMillis() / 1000).toInt(),
        "model" to model,
        "choices" to listOf(choice),
        "usage" to usage)
}

/** Stream chunk type; mirrors Python's `_GeminiStreamChunk`.
 *  Nested to avoid redeclaration conflict with GeminiCloudcodeAdapter.kt
 *  which also ports a `_GeminiStreamChunk` from its own Python file. */
private object _GeminiNativeTypes {
    class _GeminiStreamChunk
}

private fun _makeStreamChunk(
    model: String,
    content: String = "",
    toolCallDelta: Map<String, Any?>? = null,
    finishReason: String? = null,
    reasoning: String = ""): Map<String, Any?> {
    val deltaKwargs = mutableMapOf<String, Any?>(
        "role" to "assistant",
        "content" to null,
        "tool_calls" to null,
        "reasoning" to null,
        "reasoning_content" to null)
    if (content.isNotEmpty()) deltaKwargs["content"] = content
    if (toolCallDelta != null) {
        val toolDelta = mutableMapOf<String, Any?>(
            "index" to (toolCallDelta["index"] ?: 0),
            "id" to ((toolCallDelta["id"] as? String)
                ?: "call_${UUID.randomUUID().toString().replace("-", "").substring(0, 12)}"),
            "type" to "function",
            "function" to mapOf(
                "name" to (toolCallDelta["name"] ?: ""),
                "arguments" to (toolCallDelta["arguments"] ?: "")))
        val extraContent = toolCallDelta["extra_content"]
        if (extraContent is Map<*, *>) toolDelta["extra_content"] = extraContent
        deltaKwargs["tool_calls"] = listOf(toolDelta)
    }
    if (reasoning.isNotEmpty()) {
        deltaKwargs["reasoning"] = reasoning
        deltaKwargs["reasoning_content"] = reasoning
    }
    val choice = mapOf<String, Any?>(
        "index" to 0,
        "delta" to deltaKwargs,
        "finish_reason" to finishReason)
    return mapOf(
        "id" to "chatcmpl-${UUID.randomUUID().toString().replace("-", "").substring(0, 12)}",
        "object" to "chat.completion.chunk",
        "created" to (System.currentTimeMillis() / 1000).toInt(),
        "model" to model,
        "choices" to listOf(choice),
        "usage" to null)
}

/**
 * Iterate SSE events from a streaming response body.
 *
 * Python uses `response.iter_text()` from httpx. The Kotlin caller must
 * supply a lazy line sequence — we emit parsed JSON Maps until `[DONE]`.
 */
private fun _iterSseEvents(lines: Sequence<String>): Sequence<Map<String, Any?>> = sequence {
    for (rawLine in lines) {
        val line = rawLine.trimEnd('\r')
        if (line.isEmpty()) continue
        if (!line.startsWith("data: ")) continue
        val data = line.substring(6)
        if (data == "[DONE]") return@sequence
        try {
            val payload = _parseJsonToAny(data)
            if (payload is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                yield(payload as Map<String, Any?>)
            }
        } catch (_: Exception) {
            Log.d(_TAG, "Non-JSON Gemini SSE line: ${data.take(200)}")
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun translateStreamEvent(
    event: Map<String, Any?>,
    model: String,
    toolCallIndices: MutableMap<String, MutableMap<String, Any?>>): List<Map<String, Any?>> {
    val candidates = event["candidates"] as? List<*> ?: return emptyList()
    if (candidates.isEmpty()) return emptyList()
    val cand = (candidates[0] as? Map<String, Any?>) ?: emptyMap()
    val parts = ((cand["content"] as? Map<String, Any?>)?.get("parts") as? List<*>) ?: emptyList<Any?>()
    val chunks = mutableListOf<Map<String, Any?>>()

    for ((partIndex, part) in parts.withIndex()) {
        if (part !is Map<*, *>) continue
        val partMap = part as Map<String, Any?>
        if (partMap["thought"] == true && partMap["text"] is String) {
            chunks.add(_makeStreamChunk(model = model, reasoning = partMap["text"] as String))
            continue
        }
        val text = partMap["text"]
        if (text is String && text.isNotEmpty()) {
            chunks.add(_makeStreamChunk(model = model, content = text))
        }
        val fc = partMap["functionCall"] as? Map<String, Any?>
        if (fc != null && fc["name"] != null) {
            val name = fc["name"].toString()
            val argsStr = try {
                // sort_keys=True parity: use sorted-key JSON string
                _stableJson(fc["args"] ?: emptyMap<String, Any?>())
            } catch (_: Exception) {
                "{}"
            }
            val thoughtSignature = if (partMap["thoughtSignature"] is String) partMap["thoughtSignature"] as String else ""
            val callKey = _stableJson(mapOf(
                "part_index" to partIndex,
                "name" to name,
                "thought_signature" to thoughtSignature))
            val slot = toolCallIndices.getOrPut(callKey) {
                mutableMapOf(
                    "index" to toolCallIndices.size,
                    "id" to "call_${UUID.randomUUID().toString().replace("-", "").substring(0, 12)}",
                    "last_arguments" to "")
            }
            var emittedArguments: String = argsStr
            val lastArguments = slot["last_arguments"]?.toString() ?: ""
            if (lastArguments.isNotEmpty()) {
                if (argsStr == lastArguments) {
                    emittedArguments = ""
                } else if (argsStr.startsWith(lastArguments)) {
                    emittedArguments = argsStr.substring(lastArguments.length)
                }
            }
            slot["last_arguments"] = argsStr
            chunks.add(_makeStreamChunk(
                model = model,
                toolCallDelta = mapOf(
                    "index" to (slot["index"] ?: 0),
                    "id" to (slot["id"] ?: ""),
                    "name" to name,
                    "arguments" to emittedArguments,
                    "extra_content" to _toolCallExtraFromPart(partMap))))
        }
    }

    val finishReasonRaw = cand["finishReason"]?.toString() ?: ""
    if (finishReasonRaw.isNotEmpty()) {
        val mapped = if (toolCallIndices.isNotEmpty()) "tool_calls" else _mapGeminiFinishReason(finishReasonRaw)
        chunks.add(_makeStreamChunk(model = model, finishReason = mapped))
    }
    return chunks
}

/**
 * Build a [GeminiAPIError] from an HTTP response.
 *
 * Kotlin caller passes status code, body, and headers (Python's httpx.Response
 * is unavailable; we accept the decomposed fields).
 */
@Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
private fun geminiHttpError(
    response: Any?): GeminiAPIError {
    val status: Int = when (response) {
        is Map<*, *> -> ((response["statusCode"] as? Int) ?: (response["status_code"] as? Int) ?: (response["status"] as? Int)) ?: 0
        else -> 0
    }
    val bodyText: String = when (response) {
        is Map<*, *> -> (response["bodyText"] as? String) ?: (response["body_text"] as? String) ?: (response["body"] as? String) ?: ""
        is String -> response
        else -> ""
    }
    @Suppress("UNCHECKED_CAST")
    val headers: Map<String, String> = when (response) {
        is Map<*, *> -> (response["headers"] as? Map<String, String>) ?: emptyMap()
        else -> emptyMap()
    }
    var bodyJson: Map<String, Any?> = emptyMap()
    if (bodyText.isNotEmpty()) {
        try {
            val parsed = _parseJsonToAny(bodyText)
            if (parsed is Map<*, *>) bodyJson = parsed as Map<String, Any?>
        } catch (_: Exception) {
            bodyJson = emptyMap()
        }
    }

    val errObj = (bodyJson["error"] as? Map<String, Any?>) ?: emptyMap()
    val errStatus = (errObj["status"]?.toString() ?: "").trim()
    val errMessage = (errObj["message"]?.toString() ?: "").trim()
    val detailsList = errObj["details"] as? List<*> ?: emptyList<Any?>()

    var reason = ""
    var retryAfter: Double? = null
    var metadata: Map<String, Any?> = emptyMap()
    for (detail in detailsList) {
        if (detail !is Map<*, *>) continue
        val detailMap = detail as Map<String, Any?>
        val typeUrl = detailMap["@type"]?.toString() ?: ""
        if (reason.isEmpty() && typeUrl.endsWith("/google.rpc.ErrorInfo")) {
            val reasonValue = detailMap["reason"]
            if (reasonValue is String) reason = reasonValue
            val md = detailMap["metadata"]
            if (md is Map<*, *>) metadata = md as Map<String, Any?>
        }
    }
    val headerRetry = headers["Retry-After"] ?: headers["retry-after"]
    if (!headerRetry.isNullOrEmpty()) {
        retryAfter = try { headerRetry.toDouble() } catch (_: Exception) { null }
    }

    val code = when (status) {
        401 -> "gemini_unauthorized"
        429 -> "gemini_rate_limited"
        404 -> "gemini_model_not_found"
        else -> "gemini_http_$status"
    }

    val message = if (errMessage.isNotEmpty()) {
        "Gemini HTTP $status (${errStatus.ifEmpty { "error" }}): $errMessage"
    } else {
        "Gemini returned HTTP $status: ${bodyText.take(500)}"
    }

    return GeminiAPIError(
        message = message,
        code = code,
        statusCode = status,
        retryAfter = retryAfter,
        details = mapOf(
            "status" to errStatus,
            "reason" to reason,
            "metadata" to metadata,
            "message" to errMessage))
}

// ---------------------------------------------------------------------------
// Client stubs
// ---------------------------------------------------------------------------

/**
 * Minimal OpenAI-SDK-compatible facade over Gemini's native REST API.
 *
 * Android port is a structural stub — HTTP transport (OkHttp / ktor) is not
 * yet wired. See the `createChatCompletion` TODOs for how to connect this
 * once a transport is chosen.
 */
class GeminiNativeClient(
    val apiKey: String,
    baseUrl: String? = null,
    val defaultHeaders: Map<String, String> = emptyMap()) {

    val baseUrl: String
    var isClosed: Boolean = false
        private set

    init {
        var normalized = (baseUrl ?: DEFAULT_GEMINI_BASE_URL).trimEnd('/')
        if (normalized.endsWith("/openai")) {
            normalized = normalized.substring(0, normalized.length - "/openai".length)
        }
        this.baseUrl = normalized
    }

    fun close() {
        isClosed = true
        // TODO: close underlying HTTP client
    }

    /** Advance a stream iterator with parity to Python's generator pattern. */
    @Suppress("UNUSED_PARAMETER")
    private fun _advanceStreamIterator(iterator: Iterator<Map<String, Any?>>): Pair<Boolean, Map<String, Any?>?> {
        if (!iterator.hasNext()) return false to null
        return true to iterator.next()
    }

    /** Stream a chat completion. Android: HTTP transport not yet wired. */
    @Suppress("UNUSED_PARAMETER")
    private fun _streamCompletion(
        model: String,
        request: Map<String, Any?>,
        timeout: Any? = null): Sequence<Map<String, Any?>> = emptySequence()

    private fun _headers(): Map<String, String> {
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "x-goog-api-key" to apiKey,
            "User-Agent" to "hermes-agent (gemini-native)")
        headers.putAll(defaultHeaders)
        return headers
    }

    /**
     * Create a chat completion.
     *
     * Non-streaming path returns a Map shaped like an OpenAI Chat
     * Completion response. Streaming is not yet implemented.
     */
    @Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")
    fun createChatCompletion(
        model: String = "gemini-2.5-flash",
        messages: List<Map<String, Any?>>? = null,
        stream: Boolean = false,
        tools: Any? = null,
        toolChoice: Any? = null,
        temperature: Double? = null,
        maxTokens: Int? = null,
        topP: Double? = null,
        stop: Any? = null,
        extraBody: Map<String, Any?>? = null,
        timeout: Any? = null): Any {
        val thinkingConfig: Any? = extraBody?.let {
            it["thinking_config"] ?: it["thinkingConfig"]
        }

        val request = buildGeminiRequest(
            messages = messages ?: emptyList(),
            tools = tools,
            toolChoice = toolChoice,
            temperature = temperature,
            maxTokens = maxTokens,
            topP = topP,
            stop = stop,
            thinkingConfig = thinkingConfig)

        if (stream) {
            // TODO: port _stream_completion via OkHttp SSE.
            throw GeminiAPIError("Gemini streaming not yet ported", code = "gemini_stream_error")
        }

        // TODO: port httpx POST via OkHttp.
        throw GeminiAPIError("Gemini native HTTP transport not yet ported", code = "gemini_transport_missing")
    }
}

/**
 * Async wrapper used by auxiliary_client for native Gemini calls.
 *
 * Stub: Kotlin has first-class coroutines so we could just `suspend` the
 * sync methods directly. Kept as a separate class for 1:1 parity with
 * Python's sync/async split.
 */
class AsyncGeminiNativeClient(private val sync: GeminiNativeClient) {
    val apiKey: String = sync.apiKey
    val baseUrl: String = sync.baseUrl

    @Suppress("UNUSED_PARAMETER")
    suspend fun createChatCompletion(
        model: String = "gemini-2.5-flash",
        messages: List<Map<String, Any?>>? = null,
        stream: Boolean = false,
        tools: Any? = null,
        toolChoice: Any? = null,
        temperature: Double? = null,
        maxTokens: Int? = null,
        topP: Double? = null,
        stop: Any? = null,
        extraBody: Map<String, Any?>? = null,
        timeout: Any? = null): Any {
        return sync.createChatCompletion(
            model, messages, stream, tools, toolChoice,
            temperature, maxTokens, topP, stop, extraBody, timeout)
    }

    suspend fun close() {
        sync.close()
    }
}

// ---------------------------------------------------------------------------
// JSON helpers
// ---------------------------------------------------------------------------

@Suppress("UNCHECKED_CAST")
private fun _parseJsonToAny(text: String): Any? {
    val trimmed = text.trim()
    if (trimmed.startsWith("{")) return _parseJsonToMap(trimmed)
    if (trimmed.startsWith("[")) return _parseJsonArrayToList(JSONArray(trimmed))
    return null
}

private fun _parseJsonToMap(text: String): Map<String, Any?> {
    val obj = JSONObject(text)
    val out = mutableMapOf<String, Any?>()
    val keys = obj.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        out[k] = _unwrapJsonValue(obj.opt(k))
    }
    return out
}

@Suppress("UNCHECKED_CAST")
private fun _parseJsonArrayToList(arr: JSONArray): List<Any?> {
    val list = mutableListOf<Any?>()
    for (i in 0 until arr.length()) list.add(_unwrapJsonValue(arr.opt(i)))
    return list
}

private fun _unwrapJsonValue(v: Any?): Any? = when (v) {
    null, JSONObject.NULL -> null
    is JSONObject -> {
        val m = mutableMapOf<String, Any?>()
        val keys = v.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            m[k] = _unwrapJsonValue(v.opt(k))
        }
        m
    }
    is JSONArray -> _parseJsonArrayToList(v)
    else -> v
}

/** JSON encoder matching Python's `json.dumps(..., sort_keys=True)` behaviour. */
@Suppress("UNCHECKED_CAST")
private fun _stableJson(value: Any?): String {
    return when (value) {
        null -> "null"
        is Boolean -> value.toString()
        is Number -> JSONObject.numberToString(value)
        is String -> JSONObject.quote(value)
        is Map<*, *> -> {
            val entries = (value as Map<String, Any?>).entries
                .sortedBy { it.key }
                .joinToString(",") { "${JSONObject.quote(it.key)}:${_stableJson(it.value)}" }
            "{$entries}"
        }
        is List<*> -> value.joinToString(",", "[", "]") { _stableJson(it) }
        else -> JSONObject.quote(value.toString())
    }
}

// ---------------------------------------------------------------------------
// Chat namespace facades (OpenAI-SDK shape)
// ---------------------------------------------------------------------------
//
// These classes live inside a private wrapper object so they can mirror the
// Python `_GeminiChatCompletions` / `_GeminiChatNamespace` etc. names without
// colliding with top-level declarations of the same name in
// `GeminiCloudcodeAdapter.kt` (same package).

private object _GeminiChatFacades {
    /** Sync `client.chat.completions.create(...)` facade. Mirrors Python's
     *  `_GeminiChatCompletions`. */
    class _GeminiChatCompletions(private val client: GeminiNativeClient) {
        @Suppress("UNCHECKED_CAST")
        fun create(kwargs: Map<String, Any?> = emptyMap()): Any {
            return client.createChatCompletion(
                model = (kwargs["model"] as? String) ?: "gemini-2.5-flash",
                messages = kwargs["messages"] as? List<Map<String, Any?>>,
                stream = (kwargs["stream"] as? Boolean) ?: false,
                tools = kwargs["tools"],
                toolChoice = kwargs["tool_choice"],
                temperature = kwargs["temperature"] as? Double,
                maxTokens = (kwargs["max_tokens"] as? Number)?.toInt(),
                topP = kwargs["top_p"] as? Double,
                stop = kwargs["stop"],
                extraBody = kwargs["extra_body"] as? Map<String, Any?>,
                timeout = kwargs["timeout"])
        }
    }

    /** Async `client.chat.completions.create(...)` facade. */
    class _AsyncGeminiChatCompletions(private val client: AsyncGeminiNativeClient) {
        @Suppress("UNCHECKED_CAST")
        suspend fun create(kwargs: Map<String, Any?> = emptyMap()): Any {
            return client.createChatCompletion(
                model = (kwargs["model"] as? String) ?: "gemini-2.5-flash",
                messages = kwargs["messages"] as? List<Map<String, Any?>>,
                stream = (kwargs["stream"] as? Boolean) ?: false,
                tools = kwargs["tools"],
                toolChoice = kwargs["tool_choice"],
                temperature = kwargs["temperature"] as? Double,
                maxTokens = (kwargs["max_tokens"] as? Number)?.toInt(),
                topP = kwargs["top_p"] as? Double,
                stop = kwargs["stop"],
                extraBody = kwargs["extra_body"] as? Map<String, Any?>,
                timeout = kwargs["timeout"])
        }
    }

    /** `client.chat` namespace (sync). Mirrors Python's `_GeminiChatNamespace`. */
    class _GeminiChatNamespace(client: GeminiNativeClient) {
        val completions: _GeminiChatCompletions = _GeminiChatCompletions(client)
    }

    /** `client.chat` namespace (async). */
    class _AsyncGeminiChatNamespace(client: AsyncGeminiNativeClient) {
        val completions: _AsyncGeminiChatCompletions = _AsyncGeminiChatCompletions(client)
    }
}

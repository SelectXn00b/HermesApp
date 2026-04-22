package com.xiaomo.hermes.hermes.agent

/**
 * Codex Responses API adapter.
 *
 * Pure format-conversion and normalization logic for the OpenAI Responses
 * API (used by OpenAI Codex, xAI, GitHub Models, and other
 * Responses-compatible endpoints).
 *
 * Extracted from run_agent.py to isolate Responses API-specific logic from
 * the core agent loop. All functions are stateless — they operate on the
 * data passed in and return transformed results.
 *
 * Ported from agent/codex_responses_adapter.py
 */

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

private const val _TAG = "codex_responses"

// DEFAULT_AGENT_IDENTITY lives in PromptBuilder.kt (aligned 1:1 with Python).

// ---------------------------------------------------------------------------
// Attribute / field access helper
// ---------------------------------------------------------------------------

/**
 * Read a field from either a Map or an object with Python-style attributes.
 *
 * Python's `getattr(obj, name, None)` transparently handles SDK Pydantic
 * models; our Kotlin port may see parsed JSON (Map) or SDK-like objects.
 */
@Suppress("UNCHECKED_CAST")
private fun _getAttr(obj: Any?, name: String): Any? {
    if (obj == null) return null
    if (obj is Map<*, *>) return (obj as Map<String, Any?>)[name]
    return try {
        val field = obj.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.get(obj)
    } catch (_: Exception) {
        try {
            val method = obj.javaClass.getMethod("get" + name.replaceFirstChar { it.uppercase() })
            method.invoke(obj)
        } catch (_: Exception) {
            null
        }
    }
}

// ---------------------------------------------------------------------------
// Multimodal content helpers
// ---------------------------------------------------------------------------

/**
 * Convert chat-style multimodal content to Responses API input parts.
 *
 * Input:  `[{"type":"text"|"image_url", ...}]` (native OpenAI Chat format)
 * Output: `[{"type":"input_text"|"input_image", ...}]` (Responses format)
 *
 * Returns an empty list when [content] is not a list or contains no
 * recognized parts — callers fall back to the string path.
 */
@Suppress("UNCHECKED_CAST")
fun _chatContentToResponsesParts(content: Any?): List<Map<String, Any?>> {
    if (content !is List<*>) return emptyList()
    val converted = mutableListOf<Map<String, Any?>>()
    for (part in content) {
        if (part is String) {
            if (part.isNotEmpty()) {
                converted.add(mapOf("type" to "input_text", "text" to part))
            }
            continue
        }
        if (part !is Map<*, *>) continue
        val partMap = part as Map<String, Any?>
        val ptype = (partMap["type"]?.toString() ?: "").trim().lowercase()
        if (ptype in setOf("text", "input_text", "output_text")) {
            val text = partMap["text"]
            if (text is String && text.isNotEmpty()) {
                converted.add(mapOf("type" to "input_text", "text" to text))
            }
            continue
        }
        if (ptype in setOf("image_url", "input_image")) {
            val imageRef = partMap["image_url"]
            var detail = partMap["detail"]
            val url: Any? = if (imageRef is Map<*, *>) {
                val refMap = imageRef as Map<String, Any?>
                detail = refMap["detail"] ?: detail
                refMap["url"]
            } else imageRef
            if (url !is String || url.isEmpty()) continue
            val imagePart = mutableMapOf<String, Any?>(
                "type" to "input_image",
                "image_url" to url)
            if (detail is String && detail.trim().isNotEmpty()) {
                imagePart["detail"] = detail.trim()
            }
            converted.add(imagePart)
        }
    }
    return converted
}

/**
 * Return a short text summary of a user message for logging/trajectory.
 */
@Suppress("UNCHECKED_CAST")
fun _summarizeUserMessageForLog(content: Any?): String {
    if (content == null) return ""
    if (content is String) return content
    if (content is List<*>) {
        val textBits = mutableListOf<String>()
        var imageCount = 0
        for (part in content) {
            if (part is String) {
                if (part.isNotEmpty()) textBits.add(part)
                continue
            }
            if (part !is Map<*, *>) continue
            val partMap = part as Map<String, Any?>
            val ptype = (partMap["type"]?.toString() ?: "").trim().lowercase()
            if (ptype in setOf("text", "input_text", "output_text")) {
                val text = partMap["text"]
                if (text is String && text.isNotEmpty()) textBits.add(text)
            } else if (ptype in setOf("image_url", "input_image")) {
                imageCount++
            }
        }
        var summary = textBits.joinToString(" ").trim()
        if (imageCount > 0) {
            val plural = if (imageCount != 1) "s" else ""
            val note = "[$imageCount image$plural]"
            summary = if (summary.isNotEmpty()) "$note $summary" else note
        }
        return summary
    }
    return try {
        content.toString()
    } catch (_: Exception) {
        ""
    }
}

// ---------------------------------------------------------------------------
// ID helpers
// ---------------------------------------------------------------------------

/**
 * Generate a deterministic call_id from tool call content.
 *
 * Used as a fallback when the API doesn't provide a call_id. Deterministic
 * IDs prevent cache invalidation — random UUIDs would make every API
 * call's prefix unique, breaking OpenAI's prompt cache.
 */
fun _deterministicCallId(fnName: String, arguments: String, index: Int = 0): String {
    val seed = "$fnName:$arguments:$index"
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(seed.toByteArray(Charsets.UTF_8))
    val hex = digest.joinToString("") { "%02x".format(it) }.substring(0, 12)
    return "call_$hex"
}

/** Split a stored tool id into (call_id, response_item_id). */
fun _splitResponsesToolId(rawId: Any?): Pair<String?, String?> {
    if (rawId !is String) return null to null
    val value = rawId.trim()
    if (value.isEmpty()) return null to null
    if ("|" in value) {
        val parts = value.split("|", limit = 2)
        val callId = parts[0].trim().ifEmpty { null }
        val responseItemId = parts[1].trim().ifEmpty { null }
        return callId to responseItemId
    }
    if (value.startsWith("fc_")) return null to value
    return value to null
}

/** Build a valid Responses `function_call.id` (must start with `fc_`). */
fun _deriveResponsesFunctionCallId(callId: String?, responseItemId: String? = null): String {
    if (responseItemId is String) {
        val candidate = responseItemId.trim()
        if (candidate.startsWith("fc_")) return candidate
    }

    val source = (callId ?: "").trim()
    if (source.startsWith("fc_")) return source
    if (source.startsWith("call_") && source.length > "call_".length) {
        return "fc_" + source.substring("call_".length)
    }

    val sanitized = Regex("[^A-Za-z0-9_-]").replace(source, "")
    if (sanitized.startsWith("fc_")) return sanitized
    if (sanitized.startsWith("call_") && sanitized.length > "call_".length) {
        return "fc_" + sanitized.substring("call_".length)
    }
    if (sanitized.isNotEmpty()) {
        return "fc_" + sanitized.substring(0, minOf(48, sanitized.length))
    }

    val seed = if (source.isNotEmpty()) source
        else (responseItemId ?: "").ifEmpty { UUID.randomUUID().toString().replace("-", "") }
    val md = MessageDigest.getInstance("SHA-1")
    val digest = md.digest(seed.toByteArray(Charsets.UTF_8))
    val hex = digest.joinToString("") { "%02x".format(it) }.substring(0, 24)
    return "fc_$hex"
}

// ---------------------------------------------------------------------------
// Schema conversion
// ---------------------------------------------------------------------------

/** Convert chat-completions tool schemas to Responses function-tool schemas. */
@Suppress("UNCHECKED_CAST")
fun _responsesTools(tools: List<Map<String, Any?>>? = null): List<Map<String, Any?>>? {
    if (tools.isNullOrEmpty()) return null

    val converted = mutableListOf<Map<String, Any?>>()
    for (item in tools) {
        val fn = (item["function"] as? Map<String, Any?>) ?: emptyMap()
        val name = fn["name"]
        if (name !is String || name.trim().isEmpty()) continue
        converted.add(mapOf(
            "type" to "function",
            "name" to name,
            "description" to (fn["description"] ?: ""),
            "strict" to false,
            "parameters" to (fn["parameters"] ?: mapOf("type" to "object", "properties" to emptyMap<String, Any?>()))))
    }
    return if (converted.isNotEmpty()) converted else null
}

// ---------------------------------------------------------------------------
// Message format conversion
// ---------------------------------------------------------------------------

/** Convert internal chat-style messages to Responses input items. */
@Suppress("UNCHECKED_CAST")
fun _chatMessagesToResponsesInput(messages: List<Map<String, Any?>>): List<Map<String, Any?>> {
    val items = mutableListOf<Map<String, Any?>>()
    val seenItemIds = mutableSetOf<String>()

    for (msg in messages) {
        val role = msg["role"]
        if (role == "system") continue

        if (role == "user" || role == "assistant") {
            val content = msg["content"] ?: ""
            val contentParts: List<Map<String, Any?>>
            val contentText: String
            if (content is List<*>) {
                contentParts = _chatContentToResponsesParts(content)
                contentText = contentParts
                    .filter { it["type"] == "input_text" }
                    .joinToString("") { (it["text"] as? String) ?: "" }
            } else {
                contentParts = emptyList()
                contentText = content.toString()
            }

            if (role == "assistant") {
                val codexReasoning = msg["codex_reasoning_items"]
                var hasCodexReasoning = false
                if (codexReasoning is List<*>) {
                    for (ri in codexReasoning) {
                        if (ri !is Map<*, *>) continue
                        val riMap = ri as Map<String, Any?>
                        val encrypted = riMap["encrypted_content"]
                        if (encrypted == null || (encrypted is String && encrypted.isEmpty())) continue
                        val itemId = riMap["id"] as? String
                        if (itemId != null && itemId in seenItemIds) continue
                        // Strip the "id" field — with store=False the
                        // Responses API cannot look up items by ID and
                        // returns 404.  The encrypted_content blob is
                        // self-contained for reasoning chain continuity.
                        val replayItem = riMap.filterKeys { it != "id" }
                        items.add(replayItem)
                        if (itemId != null) seenItemIds.add(itemId)
                        hasCodexReasoning = true
                    }
                }

                if (contentParts.isNotEmpty()) {
                    items.add(mapOf("role" to "assistant", "content" to contentParts))
                } else if (contentText.trim().isNotEmpty()) {
                    items.add(mapOf("role" to "assistant", "content" to contentText))
                } else if (hasCodexReasoning) {
                    // The Responses API requires a following item after each
                    // reasoning item (otherwise: missing_following_item error).
                    items.add(mapOf("role" to "assistant", "content" to ""))
                }

                val toolCalls = msg["tool_calls"]
                if (toolCalls is List<*>) {
                    for (tc in toolCalls) {
                        if (tc !is Map<*, *>) continue
                        val tcMap = tc as Map<String, Any?>
                        val fn = (tcMap["function"] as? Map<String, Any?>) ?: emptyMap()
                        val fnName = fn["name"]
                        if (fnName !is String || fnName.trim().isEmpty()) continue

                        val (embeddedCallId, embeddedResponseItemId) = _splitResponsesToolId(tcMap["id"])
                        var callId = tcMap["call_id"]
                        if (callId !is String || callId.trim().isEmpty()) callId = embeddedCallId
                        if (callId !is String || callId.trim().isEmpty()) {
                            callId = if (embeddedResponseItemId is String
                                && embeddedResponseItemId.startsWith("fc_")
                                && embeddedResponseItemId.length > "fc_".length) {
                                "call_" + embeddedResponseItemId.substring("fc_".length)
                            } else {
                                val rawArgs = fn["arguments"]?.toString() ?: "{}"
                                _deterministicCallId(fnName, rawArgs, items.size)
                            }
                        }
                        callId = (callId as String).trim()

                        var arguments: Any = fn["arguments"] ?: "{}"
                        if (arguments is Map<*, *>) {
                            arguments = JSONObject(arguments as Map<String, Any?>).toString()
                        } else if (arguments !is String) {
                            arguments = arguments.toString()
                        }
                        val argsStr = (arguments as String).trim().ifEmpty { "{}" }

                        items.add(mapOf(
                            "type" to "function_call",
                            "call_id" to callId,
                            "name" to fnName,
                            "arguments" to argsStr))
                    }
                }
                continue
            }

            // Non-assistant (user) role: emit multimodal parts when present,
            // otherwise fall back to the text payload.
            if (contentParts.isNotEmpty()) {
                items.add(mapOf("role" to role, "content" to contentParts))
            } else {
                items.add(mapOf("role" to role, "content" to contentText))
            }
            continue
        }

        if (role == "tool") {
            val rawToolCallId = msg["tool_call_id"]
            var (callId, _) = _splitResponsesToolId(rawToolCallId).let { it.first to it.second }
            if (callId.isNullOrBlank()) {
                if (rawToolCallId is String && rawToolCallId.trim().isNotEmpty()) {
                    callId = rawToolCallId.trim()
                }
            }
            if (callId.isNullOrBlank()) continue
            items.add(mapOf(
                "type" to "function_call_output",
                "call_id" to callId,
                "output" to ((msg["content"] as? String) ?: (msg["content"]?.toString() ?: ""))))
        }
    }

    return items
}

// ---------------------------------------------------------------------------
// Input preflight / validation
// ---------------------------------------------------------------------------

@Suppress("UNCHECKED_CAST")
fun _preflightCodexInputItems(rawItems: Any?): List<Map<String, Any?>> {
    if (rawItems !is List<*>) {
        throw IllegalArgumentException("Codex Responses input must be a list of input items.")
    }

    val normalized = mutableListOf<Map<String, Any?>>()
    val seenIds = mutableSetOf<String>()
    for ((idx, item) in rawItems.withIndex()) {
        if (item !is Map<*, *>) {
            throw IllegalArgumentException("Codex Responses input[$idx] must be an object.")
        }
        val itemMap = item as Map<String, Any?>
        val itemType = itemMap["type"]

        if (itemType == "function_call") {
            val callId = itemMap["call_id"]
            val name = itemMap["name"]
            if (callId !is String || callId.trim().isEmpty()) {
                throw IllegalArgumentException("Codex Responses input[$idx] function_call is missing call_id.")
            }
            if (name !is String || name.trim().isEmpty()) {
                throw IllegalArgumentException("Codex Responses input[$idx] function_call is missing name.")
            }

            var arguments: Any = itemMap["arguments"] ?: "{}"
            if (arguments is Map<*, *>) {
                arguments = JSONObject(arguments as Map<String, Any?>).toString()
            } else if (arguments !is String) {
                arguments = arguments.toString()
            }
            val argsStr = (arguments as String).trim().ifEmpty { "{}" }

            normalized.add(mapOf(
                "type" to "function_call",
                "call_id" to callId.trim(),
                "name" to name.trim(),
                "arguments" to argsStr))
            continue
        }

        if (itemType == "function_call_output") {
            val callId = itemMap["call_id"]
            if (callId !is String || callId.trim().isEmpty()) {
                throw IllegalArgumentException("Codex Responses input[$idx] function_call_output is missing call_id.")
            }
            var output: Any? = itemMap["output"] ?: ""
            if (output == null) output = ""
            if (output !is String) output = output.toString()

            normalized.add(mapOf(
                "type" to "function_call_output",
                "call_id" to callId.trim(),
                "output" to output))
            continue
        }

        if (itemType == "reasoning") {
            val encrypted = itemMap["encrypted_content"]
            if (encrypted is String && encrypted.isNotEmpty()) {
                val itemId = itemMap["id"] as? String
                if (itemId != null && itemId.isNotEmpty()) {
                    if (itemId in seenIds) continue
                    seenIds.add(itemId)
                }
                val reasoningItem = mutableMapOf<String, Any?>(
                    "type" to "reasoning",
                    "encrypted_content" to encrypted)
                val summary = itemMap["summary"]
                reasoningItem["summary"] = if (summary is List<*>) summary else emptyList<Any?>()
                normalized.add(reasoningItem)
            }
            continue
        }

        val role = itemMap["role"]
        if (role == "user" || role == "assistant") {
            var content: Any? = itemMap["content"] ?: ""
            if (content == null) content = ""
            if (content is List<*>) {
                // Multimodal content from _chatMessagesToResponsesInput
                // is already in Responses format (input_text / input_image).
                val validated = mutableListOf<Map<String, Any?>>()
                for ((partIdx, part) in content.withIndex()) {
                    if (part is String) {
                        if (part.isNotEmpty()) {
                            validated.add(mapOf("type" to "input_text", "text" to part))
                        }
                        continue
                    }
                    if (part !is Map<*, *>) {
                        throw IllegalArgumentException(
                            "Codex Responses input[$idx].content[$partIdx] must be an object or string.")
                    }
                    val partMap = part as Map<String, Any?>
                    val ptype = (partMap["type"]?.toString() ?: "").trim().lowercase()
                    if (ptype in setOf("input_text", "text", "output_text")) {
                        var text: Any? = partMap["text"] ?: ""
                        if (text !is String) text = (text?.toString() ?: "")
                        validated.add(mapOf("type" to "input_text", "text" to text))
                    } else if (ptype in setOf("input_image", "image_url")) {
                        val imageRef = partMap["image_url"] ?: ""
                        var detail = partMap["detail"]
                        val url: Any? = if (imageRef is Map<*, *>) {
                            val refMap = imageRef as Map<String, Any?>
                            detail = refMap["detail"] ?: detail
                            refMap["url"] ?: ""
                        } else imageRef
                        val urlStr = if (url is String) url else (url?.toString() ?: "")
                        val imagePart = mutableMapOf<String, Any?>(
                            "type" to "input_image",
                            "image_url" to urlStr)
                        if (detail is String && detail.trim().isNotEmpty()) {
                            imagePart["detail"] = detail.trim()
                        }
                        validated.add(imagePart)
                    } else {
                        throw IllegalArgumentException(
                            "Codex Responses input[$idx].content[$partIdx] has unsupported type '${partMap["type"]}'.")
                    }
                }
                normalized.add(mapOf("role" to role, "content" to validated))
                continue
            }
            if (content !is String) content = content.toString()
            normalized.add(mapOf("role" to role, "content" to content))
            continue
        }

        throw IllegalArgumentException(
            "Codex Responses input[$idx] has unsupported item shape (type=$itemType, role=$role).")
    }

    return normalized
}

@Suppress("UNCHECKED_CAST")
fun _preflightCodexApiKwargs(
    apiKwargs: Any?,
    allowStream: Boolean = false): Map<String, Any?> {
    if (apiKwargs !is Map<*, *>) {
        throw IllegalArgumentException("Codex Responses request must be a dict.")
    }
    val kwargs = apiKwargs as Map<String, Any?>

    val required = setOf("model", "instructions", "input")
    val missing = required.filter { it !in kwargs }.sorted()
    if (missing.isNotEmpty()) {
        throw IllegalArgumentException(
            "Codex Responses request missing required field(s): ${missing.joinToString(", ")}.")
    }

    var model = kwargs["model"]
    if (model !is String || model.trim().isEmpty()) {
        throw IllegalArgumentException("Codex Responses request 'model' must be a non-empty string.")
    }
    model = model.trim()

    var instructions: Any? = kwargs["instructions"] ?: ""
    if (instructions == null) instructions = ""
    if (instructions !is String) instructions = instructions.toString()
    instructions = (instructions as String).trim().ifEmpty { DEFAULT_AGENT_IDENTITY }

    val normalizedInput = _preflightCodexInputItems(kwargs["input"])

    val tools = kwargs["tools"]
    var normalizedTools: List<Map<String, Any?>>? = null
    if (tools != null) {
        if (tools !is List<*>) {
            throw IllegalArgumentException("Codex Responses request 'tools' must be a list when provided.")
        }
        val outList = mutableListOf<Map<String, Any?>>()
        for ((idx, tool) in tools.withIndex()) {
            if (tool !is Map<*, *>) {
                throw IllegalArgumentException("Codex Responses tools[$idx] must be an object.")
            }
            val toolMap = tool as Map<String, Any?>
            if (toolMap["type"] != "function") {
                throw IllegalArgumentException("Codex Responses tools[$idx] has unsupported type '${toolMap["type"]}'.")
            }
            val name = toolMap["name"]
            val parameters = toolMap["parameters"]
            if (name !is String || name.trim().isEmpty()) {
                throw IllegalArgumentException("Codex Responses tools[$idx] is missing a valid name.")
            }
            if (parameters !is Map<*, *>) {
                throw IllegalArgumentException("Codex Responses tools[$idx] is missing valid parameters.")
            }
            var description: Any? = toolMap["description"] ?: ""
            if (description == null) description = ""
            if (description !is String) description = description.toString()
            val strictRaw = toolMap["strict"] ?: false
            val strict = if (strictRaw is Boolean) strictRaw else (strictRaw != false && strictRaw != null && strictRaw != 0)

            outList.add(mapOf(
                "type" to "function",
                "name" to name.trim(),
                "description" to description,
                "strict" to strict,
                "parameters" to parameters))
        }
        normalizedTools = outList
    }

    val store = kwargs["store"] ?: false
    if (store != false) {
        throw IllegalArgumentException("Codex Responses contract requires 'store' to be false.")
    }

    val allowedKeys = mutableSetOf(
        "model", "instructions", "input", "tools", "store",
        "reasoning", "include", "max_output_tokens", "temperature",
        "tool_choice", "parallel_tool_calls", "prompt_cache_key", "service_tier",
        "extra_headers")

    val normalized = mutableMapOf<String, Any?>(
        "model" to model,
        "instructions" to instructions,
        "input" to normalizedInput,
        "store" to false)
    if (normalizedTools != null) normalized["tools"] = normalizedTools

    val reasoning = kwargs["reasoning"]
    if (reasoning is Map<*, *>) normalized["reasoning"] = reasoning
    val include = kwargs["include"]
    if (include is List<*>) normalized["include"] = include
    val serviceTier = kwargs["service_tier"]
    if (serviceTier is String && serviceTier.trim().isNotEmpty()) {
        normalized["service_tier"] = serviceTier.trim()
    }

    val maxOutputTokens = kwargs["max_output_tokens"]
    if (maxOutputTokens is Number && maxOutputTokens.toDouble() > 0) {
        normalized["max_output_tokens"] = maxOutputTokens.toInt()
    }
    val temperature = kwargs["temperature"]
    if (temperature is Number) {
        normalized["temperature"] = temperature.toDouble()
    }

    for (key in listOf("tool_choice", "parallel_tool_calls", "prompt_cache_key")) {
        val v = kwargs[key]
        if (v != null) normalized[key] = v
    }

    val extraHeaders = kwargs["extra_headers"]
    if (extraHeaders != null) {
        if (extraHeaders !is Map<*, *>) {
            throw IllegalArgumentException("Codex Responses request 'extra_headers' must be an object.")
        }
        val normalizedHeaders = mutableMapOf<String, String>()
        for ((k, v) in (extraHeaders as Map<String, Any?>)) {
            if (k !is String || k.trim().isEmpty()) {
                throw IllegalArgumentException("Codex Responses request 'extra_headers' keys must be non-empty strings.")
            }
            if (v == null) continue
            normalizedHeaders[k.trim()] = v.toString()
        }
        if (normalizedHeaders.isNotEmpty()) normalized["extra_headers"] = normalizedHeaders
    }

    if (allowStream) {
        val stream = kwargs["stream"]
        if (stream != null && stream != true) {
            throw IllegalArgumentException("Codex Responses 'stream' must be true when set.")
        }
        if (stream == true) normalized["stream"] = true
        allowedKeys.add("stream")
    } else if ("stream" in kwargs) {
        throw IllegalArgumentException("Codex Responses stream flag is only allowed in fallback streaming requests.")
    }

    val unexpected = kwargs.keys.filter { it !in allowedKeys }.sorted()
    if (unexpected.isNotEmpty()) {
        throw IllegalArgumentException(
            "Codex Responses request has unsupported field(s): ${unexpected.joinToString(", ")}.")
    }

    return normalized
}

// ---------------------------------------------------------------------------
// Response extraction helpers
// ---------------------------------------------------------------------------

/** Extract assistant text from a Responses message output item. */
fun _extractResponsesMessageText(item: Any?): String {
    val content = _getAttr(item, "content")
    if (content !is List<*>) return ""

    val chunks = mutableListOf<String>()
    for (part in content) {
        val ptype = _getAttr(part, "type")
        if (ptype != "output_text" && ptype != "text") continue
        val text = _getAttr(part, "text")
        if (text is String && text.isNotEmpty()) chunks.add(text)
    }
    return chunks.joinToString("").trim()
}

/** Extract a compact reasoning text from a Responses reasoning item. */
fun _extractResponsesReasoningText(item: Any?): String {
    val summary = _getAttr(item, "summary")
    if (summary is List<*>) {
        val chunks = mutableListOf<String>()
        for (part in summary) {
            val text = _getAttr(part, "text")
            if (text is String && text.isNotEmpty()) chunks.add(text)
        }
        if (chunks.isNotEmpty()) return chunks.joinToString("\n").trim()
    }
    val text = _getAttr(item, "text")
    if (text is String && text.isNotEmpty()) return text.trim()
    return ""
}

// ---------------------------------------------------------------------------
// Full response normalization
// ---------------------------------------------------------------------------

/**
 * Normalize a Responses API object to an assistant_message-like Map.
 *
 * The Python version returns a `SimpleNamespace` with attributes; Kotlin
 * returns a Map with the same keys plus a `finish_reason` value.
 */
@Suppress("UNCHECKED_CAST", "UNUSED_VARIABLE")
fun _normalizeCodexResponse(response: Any?): Pair<Map<String, Any?>, String> {
    var output = _getAttr(response, "output")
    if (output !is List<*> || (output as List<*>).isEmpty()) {
        val outText = _getAttr(response, "output_text")
        if (outText is String && outText.trim().isNotEmpty()) {
            Log.d(_TAG, "Codex response has empty output but output_text is present " +
                "(${outText.trim().length} chars); synthesizing output item.")
            output = listOf(mapOf(
                "type" to "message",
                "role" to "assistant",
                "status" to "completed",
                "content" to listOf(mapOf("type" to "output_text", "text" to outText.trim()))))
            // TODO: mutate response.output when an SDK object model exists.
        } else {
            throw RuntimeException("Responses API returned no output items")
        }
    }

    val responseStatusRaw = _getAttr(response, "status")
    val responseStatus: String? = if (responseStatusRaw is String) {
        responseStatusRaw.trim().lowercase()
    } else null

    if (responseStatus == "failed" || responseStatus == "cancelled") {
        val errorObj = _getAttr(response, "error")
        val errorMsg = when {
            errorObj is Map<*, *> -> {
                val em = errorObj as Map<String, Any?>
                (em["message"] as? String) ?: em.toString()
            }
            errorObj != null -> errorObj.toString()
            else -> "Responses API returned status '$responseStatus'"
        }
        throw RuntimeException(errorMsg)
    }

    val contentParts = mutableListOf<String>()
    val reasoningParts = mutableListOf<String>()
    val reasoningItemsRaw = mutableListOf<Map<String, Any?>>()
    val toolCalls = mutableListOf<Map<String, Any?>>()
    var hasIncompleteItems = responseStatus in setOf("queued", "in_progress", "incomplete")
    var sawCommentaryPhase = false
    var sawFinalAnswerPhase = false

    for (item in output as List<*>) {
        val itemType = _getAttr(item, "type")
        val itemStatusRaw = _getAttr(item, "status")
        val itemStatus: String? = if (itemStatusRaw is String) {
            itemStatusRaw.trim().lowercase()
        } else null

        if (itemStatus in setOf("queued", "in_progress", "incomplete")) {
            hasIncompleteItems = true
        }

        when (itemType) {
            "message" -> {
                val itemPhase = _getAttr(item, "phase")
                if (itemPhase is String) {
                    val normalizedPhase = itemPhase.trim().lowercase()
                    if (normalizedPhase in setOf("commentary", "analysis")) {
                        sawCommentaryPhase = true
                    } else if (normalizedPhase in setOf("final_answer", "final")) {
                        sawFinalAnswerPhase = true
                    }
                }
                val messageText = _extractResponsesMessageText(item)
                if (messageText.isNotEmpty()) contentParts.add(messageText)
            }
            "reasoning" -> {
                val reasoningText = _extractResponsesReasoningText(item)
                if (reasoningText.isNotEmpty()) reasoningParts.add(reasoningText)
                val encrypted = _getAttr(item, "encrypted_content")
                if (encrypted is String && encrypted.isNotEmpty()) {
                    val rawItem = mutableMapOf<String, Any?>(
                        "type" to "reasoning",
                        "encrypted_content" to encrypted)
                    val itemId = _getAttr(item, "id")
                    if (itemId is String && itemId.isNotEmpty()) rawItem["id"] = itemId
                    val summary = _getAttr(item, "summary")
                    if (summary is List<*>) {
                        val rawSummary = mutableListOf<Map<String, Any?>>()
                        for (part in summary) {
                            val text = _getAttr(part, "text")
                            if (text is String) {
                                rawSummary.add(mapOf("type" to "summary_text", "text" to text))
                            }
                        }
                        rawItem["summary"] = rawSummary
                    }
                    reasoningItemsRaw.add(rawItem)
                }
            }
            "function_call" -> {
                if (itemStatus in setOf("queued", "in_progress", "incomplete")) continue
                val fnName = (_getAttr(item, "name") as? String) ?: ""
                var arguments: Any? = _getAttr(item, "arguments") ?: "{}"
                if (arguments !is String) {
                    arguments = JSONObject((arguments as? Map<String, Any?>) ?: emptyMap<String, Any?>()).toString()
                }
                val rawCallId = _getAttr(item, "call_id")
                val rawItemId = _getAttr(item, "id")
                val (embeddedCallId, _) = _splitResponsesToolId(rawItemId)
                var callId: String? = if (rawCallId is String && rawCallId.trim().isNotEmpty()) rawCallId
                    else embeddedCallId
                if (callId.isNullOrBlank()) {
                    callId = _deterministicCallId(fnName, arguments as String, toolCalls.size)
                }
                callId = callId.trim()
                val responseItemIdStr = if (rawItemId is String) rawItemId else null
                val responseItemId = _deriveResponsesFunctionCallId(callId, responseItemIdStr)
                toolCalls.add(mapOf(
                    "id" to callId,
                    "call_id" to callId,
                    "response_item_id" to responseItemId,
                    "type" to "function",
                    "function" to mapOf("name" to fnName, "arguments" to arguments)))
            }
            "custom_tool_call" -> {
                val fnName = (_getAttr(item, "name") as? String) ?: ""
                var arguments: Any? = _getAttr(item, "input") ?: "{}"
                if (arguments !is String) {
                    arguments = JSONObject((arguments as? Map<String, Any?>) ?: emptyMap<String, Any?>()).toString()
                }
                val rawCallId = _getAttr(item, "call_id")
                val rawItemId = _getAttr(item, "id")
                val (embeddedCallId, _) = _splitResponsesToolId(rawItemId)
                var callId: String? = if (rawCallId is String && rawCallId.trim().isNotEmpty()) rawCallId
                    else embeddedCallId
                if (callId.isNullOrBlank()) {
                    callId = _deterministicCallId(fnName, arguments as String, toolCalls.size)
                }
                callId = callId.trim()
                val responseItemIdStr = if (rawItemId is String) rawItemId else null
                val responseItemId = _deriveResponsesFunctionCallId(callId, responseItemIdStr)
                toolCalls.add(mapOf(
                    "id" to callId,
                    "call_id" to callId,
                    "response_item_id" to responseItemId,
                    "type" to "function",
                    "function" to mapOf("name" to fnName, "arguments" to arguments)))
            }
        }
    }

    var finalText = contentParts.filter { it.isNotEmpty() }.joinToString("\n").trim()
    if (finalText.isEmpty()) {
        val outText = _getAttr(response, "output_text")
        if (outText is String) finalText = outText.trim()
    }

    val assistantMessage = mapOf<String, Any?>(
        "content" to finalText,
        "tool_calls" to toolCalls,
        "reasoning" to if (reasoningParts.isNotEmpty()) reasoningParts.joinToString("\n\n").trim() else null,
        "reasoning_content" to null,
        "reasoning_details" to null,
        "codex_reasoning_items" to reasoningItemsRaw.ifEmpty { null })

    val finishReason = when {
        toolCalls.isNotEmpty() -> "tool_calls"
        hasIncompleteItems || (sawCommentaryPhase && !sawFinalAnswerPhase) -> "incomplete"
        reasoningItemsRaw.isNotEmpty() && finalText.isEmpty() -> "incomplete"
        else -> "stop"
    }
    return assistantMessage to finishReason
}

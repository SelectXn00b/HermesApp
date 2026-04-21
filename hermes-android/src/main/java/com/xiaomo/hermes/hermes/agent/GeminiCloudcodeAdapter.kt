/** 1:1 对齐 hermes/agent/gemini_cloudcode_adapter.py */
package com.xiaomo.hermes.hermes.agent

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * OpenAI-compatible facade that talks to Google's Cloud Code Assist backend.
 *
 * Android 简化版：保留完整的请求/响应转换逻辑，
 * HTTP 调用使用 HttpURLConnection。
 */

private const val TAG = "GeminiCloudcodeAdapter"

// =============================================================================
// Request translation: OpenAI → Gemini
// =============================================================================

private val _ROLE_MAP_OPENAI_TO_GEMINI = mapOf(
    "user" to "user",
    "assistant" to "model",
    "system" to "user",
    "tool" to "user",
    "function" to "user",
)

private fun _coerceContentToText(content: Any?): String {
    if (content == null) return ""
    if (content is String) return content
    if (content is List<*>) {
        val pieces = mutableListOf<String>()
        for (p in content) {
            if (p is String) {
                pieces.add(p)
            } else if (p is Map<*, *>) {
                if (p["type"] == "text" && p["text"] is String) {
                    pieces.add(p["text"] as String)
                } else if (p["type"] in listOf("image_url", "input_audio")) {
                    Log.d(TAG, "Dropping multimodal part (not yet supported): ${p["type"]}")
                }
            }
        }
        return pieces.joinToString("\n")
    }
    return content.toString()
}

private fun _translateToolCallToGemini(toolCall: Map<String, Any>): Map<String, Any> {
    @Suppress("UNCHECKED_CAST")
    val fn = toolCall["function"] as? Map<String, Any> ?: emptyMap()
    val argsRaw = fn["arguments"] as? String ?: ""
    val args: Map<String, Any> = try {
        if (argsRaw.isNotEmpty()) {
            val jo = JSONObject(argsRaw)
            val map = mutableMapOf<String, Any>()
            for (key in jo.keys()) map[key] = jo.get(key)
            map
        } else emptyMap()
    } catch (_: Exception) {
        mapOf("_raw" to argsRaw)
    }
    return mapOf(
        "functionCall" to mapOf(
            "name" to (fn["name"] as? String ?: ""),
            "args" to args,
        ),
        "thoughtSignature" to "skip_thought_signature_validator",
    )
}

private fun _translateToolResultToGemini(message: Map<String, Any>): Map<String, Any> {
    val name = (message["name"] as? String)
        ?: (message["tool_call_id"] as? String)
        ?: "tool"
    val content = _coerceContentToText(message["content"])
    val response: Map<String, Any> = try {
        if (content.trimStart().startsWith("{") || content.trimStart().startsWith("[")) {
            val jo = JSONObject(content)
            val map = mutableMapOf<String, Any>()
            for (key in jo.keys()) map[key] = jo.get(key)
            map
        } else {
            mapOf("output" to content)
        }
    } catch (_: Exception) {
        mapOf("output" to content)
    }
    return mapOf(
        "functionResponse" to mapOf(
            "name" to name,
            "response" to response,
        )
    )
}

data class GeminiContentsResult(
    val contents: List<Map<String, Any>>,
    val systemInstruction: Map<String, Any>?
)

private fun _buildGeminiContents(
    messages: List<Map<String, Any>>
): GeminiContentsResult {
    val systemTextParts = mutableListOf<String>()
    val contents = mutableListOf<Map<String, Any>>()

    for (msg in messages) {
        if (msg !is Map<*, *>) continue
        val role = (msg["role"] as? String) ?: "user"

        if (role == "system") {
            systemTextParts.add(_coerceContentToText(msg["content"]))
            continue
        }

        if (role == "tool" || role == "function") {
            contents.add(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(_translateToolResultToGemini(msg)),
                )
            )
            continue
        }

        val geminiRole = _ROLE_MAP_OPENAI_TO_GEMINI[role] ?: "user"
        val parts = mutableListOf<Map<String, Any>>()

        val text = _coerceContentToText(msg["content"])
        if (text.isNotEmpty()) {
            parts.add(mapOf("text" to text))
        }

        @Suppress("UNCHECKED_CAST")
        val toolCalls = msg["tool_calls"] as? List<Map<String, Any>> ?: emptyList()
        for (tc in toolCalls) {
            parts.add(_translateToolCallToGemini(tc))
        }

        if (parts.isEmpty()) continue

        contents.add(mapOf("role" to geminiRole, "parts" to parts))
    }

    val joinedSystem = systemTextParts.filter { it.isNotEmpty() }.joinToString("\n").trim()
    val systemInstruction: Map<String, Any>? = if (joinedSystem.isNotEmpty()) {
        mapOf(
            "role" to "system",
            "parts" to listOf(mapOf("text" to joinedSystem)),
        )
    } else null

    return GeminiContentsResult(contents, systemInstruction)
}

private fun _translateToolsToGemini(tools: Any?): List<Map<String, Any>> {
    if (tools !is List<*> || tools.isEmpty()) return emptyList()
    val declarations = mutableListOf<Map<String, Any>>()
    for (t in tools) {
        if (t !is Map<*, *>) continue
        @Suppress("UNCHECKED_CAST")
        val fn = t["function"] as? Map<String, Any> ?: continue
        val name = fn["name"] as? String ?: continue
        val decl = mutableMapOf<String, Any>("name" to name)
        val desc = fn["description"] as? String
        if (desc != null) decl["description"] = desc
        @Suppress("UNCHECKED_CAST")
        val params = fn["parameters"] as? Map<String, Any>
        if (params != null) decl["parameters"] = params
        declarations.add(decl)
    }
    if (declarations.isEmpty()) return emptyList()
    return listOf(mapOf("functionDeclarations" to declarations))
}

private fun _translateToolChoiceToGemini(toolChoice: Any?): Map<String, Any>? {
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
        @Suppress("UNCHECKED_CAST")
        val fn = toolChoice["function"] as? Map<String, Any> ?: return null
        val name = fn["name"] as? String ?: return null
        return mapOf(
            "functionCallingConfig" to mapOf(
                "mode" to "ANY",
                "allowedFunctionNames" to listOf(name),
            )
        )
    }
    return null
}

private fun _normalizeThinkingConfig(config: Any?): Map<String, Any>? {
    if (config !is Map<*, *> || config.isEmpty()) return null
    val budget = config["thinkingBudget"] ?: config["thinking_budget"]
    val level = config["thinkingLevel"] ?: config["thinking_level"]
    val include = config["includeThoughts"] ?: config["include_thoughts"]
    val normalized = mutableMapOf<String, Any>()
    if (budget is Number) normalized["thinkingBudget"] = budget.toInt()
    if (level is String && level.isNotBlank()) normalized["thinkingLevel"] = level.trim().lowercase()
    if (include is Boolean) normalized["includeThoughts"] = include
    return normalized.ifEmpty { null }
}

fun buildGeminiRequest(
    messages: List<Map<String, Any>>,
    tools: Any? = null,
    toolChoice: Any? = null,
    temperature: Float? = null,
    maxTokens: Int? = null,
    topP: Float? = null,
    stop: Any? = null,
    thinkingConfig: Any? = null,
): Map<String, Any> {
    val (contents, systemInstruction) = _buildGeminiContents(messages)

    val body = mutableMapOf<String, Any>("contents" to contents)
    if (systemInstruction != null) body["systemInstruction"] = systemInstruction

    val geminiTools = _translateToolsToGemini(tools)
    if (geminiTools.isNotEmpty()) body["tools"] = geminiTools
    val toolCfg = _translateToolChoiceToGemini(toolChoice)
    if (toolCfg != null) body["toolConfig"] = toolCfg

    val generationConfig = mutableMapOf<String, Any>()
    if (temperature != null) generationConfig["temperature"] = temperature
    if (maxTokens != null && maxTokens > 0) generationConfig["maxOutputTokens"] = maxTokens
    if (topP != null) generationConfig["topP"] = topP
    if (stop is String && stop.isNotEmpty()) {
        generationConfig["stopSequences"] = listOf(stop)
    } else if (stop is List<*> && stop.isNotEmpty()) {
        generationConfig["stopSequences"] = stop.filterNotNull().map { it.toString() }.filter { it.isNotEmpty() }
    }
    val normalizedThinking = _normalizeThinkingConfig(thinkingConfig)
    if (normalizedThinking != null) generationConfig["thinkingConfig"] = normalizedThinking
    if (generationConfig.isNotEmpty()) body["generationConfig"] = generationConfig

    return body
}

fun wrapCodeAssistRequest(
    projectId: String,
    model: String,
    innerRequest: Map<String, Any>,
    userPromptId: String? = null,
): Map<String, Any> {
    return mapOf(
        "project" to projectId,
        "model" to model,
        "user_prompt_id" to (userPromptId ?: UUID.randomUUID().toString()),
        "request" to innerRequest,
    )
}

// =============================================================================
// Response translation: Gemini → OpenAI
// =============================================================================

data class GeminiUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val cachedTokens: Int = 0
)

data class GeminiToolCall(
    val id: String,
    val type: String = "function",
    val index: Int = 0,
    val function: GeminiToolCallFunction
)

data class GeminiToolCallFunction(
    val name: String,
    val arguments: String
)

data class GeminiMessage(
    val role: String = "assistant",
    val content: String?,
    val toolCalls: List<GeminiToolCall>?,
    val reasoning: String? = null,
    val reasoningContent: String? = null,
)

data class GeminiChoice(
    val index: Int = 0,
    val message: GeminiMessage,
    val finishReason: String
)

data class GeminiChatCompletionResponse(
    val id: String,
    val objectType: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<GeminiChoice>,
    val usage: GeminiUsage
)

private fun _translateGeminiResponse(
    resp: Map<String, Any>,
    model: String,
): GeminiChatCompletionResponse {
    @Suppress("UNCHECKED_CAST")
    val inner = (resp["response"] as? Map<String, Any>) ?: resp

    @Suppress("UNCHECKED_CAST")
    val candidates = inner["candidates"] as? List<Map<String, Any>> ?: emptyList()
    if (candidates.isEmpty()) return _emptyResponse(model)

    val cand = candidates[0]
    @Suppress("UNCHECKED_CAST")
    val contentObj = cand["content"] as? Map<String, Any> ?: emptyMap()
    @Suppress("UNCHECKED_CAST")
    val parts = contentObj["parts"] as? List<Map<String, Any>> ?: emptyList()

    val textPieces = mutableListOf<String>()
    val reasoningPieces = mutableListOf<String>()
    val toolCalls = mutableListOf<GeminiToolCall>()

    for ((i, part) in parts.withIndex()) {
        if (part["thought"] == true) {
            val text = part["text"] as? String
            if (text != null) reasoningPieces.add(text)
            continue
        }
        if (part["text"] is String) {
            textPieces.add(part["text"] as String)
            continue
        }
        @Suppress("UNCHECKED_CAST")
        val fc = part["functionCall"] as? Map<String, Any>
        if (fc != null && fc["name"] != null) {
            val argsStr = try {
                JSONObject(fc["args"] as? Map<*, *> ?: emptyMap<String, Any>()).toString()
            } catch (_: Exception) {
                "{}"
            }
            toolCalls.add(
                GeminiToolCall(
                    id = "call_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                    index = i,
                    function = GeminiToolCallFunction(
                        name = fc["name"].toString(),
                        arguments = argsStr,
                    )
                )
            )
        }
    }

    val finishReason = if (toolCalls.isNotEmpty()) "tool_calls"
    else _mapGeminiFinishReason(cand["finishReason"] as? String ?: "")

    @Suppress("UNCHECKED_CAST")
    val usageMeta = inner["usageMetadata"] as? Map<String, Any> ?: emptyMap()
    val usage = GeminiUsage(
        promptTokens = (usageMeta["promptTokenCount"] as? Number)?.toInt() ?: 0,
        completionTokens = (usageMeta["candidatesTokenCount"] as? Number)?.toInt() ?: 0,
        totalTokens = (usageMeta["totalTokenCount"] as? Number)?.toInt() ?: 0,
        cachedTokens = (usageMeta["cachedContentTokenCount"] as? Number)?.toInt() ?: 0,
    )

    val message = GeminiMessage(
        content = if (textPieces.isNotEmpty()) textPieces.joinToString("") else null,
        toolCalls = toolCalls.ifEmpty { null },
        reasoning = reasoningPieces.joinToString("").ifEmpty { null },
        reasoningContent = reasoningPieces.joinToString("").ifEmpty { null },
    )

    return GeminiChatCompletionResponse(
        id = "chatcmpl-${UUID.randomUUID().toString().replace("-", "").take(12)}",
        created = System.currentTimeMillis() / 1000,
        model = model,
        choices = listOf(GeminiChoice(message = message, finishReason = finishReason)),
        usage = usage,
    )
}

private fun _emptyResponse(model: String): GeminiChatCompletionResponse {
    return GeminiChatCompletionResponse(
        id = "chatcmpl-${UUID.randomUUID().toString().replace("-", "").take(12)}",
        created = System.currentTimeMillis() / 1000,
        model = model,
        choices = listOf(
            GeminiChoice(
                message = GeminiMessage(content = "", toolCalls = null),
                finishReason = "stop",
            )
        ),
        usage = GeminiUsage(0, 0, 0),
    )
}

private fun _mapGeminiFinishReason(reason: String): String {
    val mapping = mapOf(
        "STOP" to "stop",
        "MAX_TOKENS" to "length",
        "SAFETY" to "content_filter",
        "RECITATION" to "content_filter",
        "OTHER" to "stop",
    )
    return mapping[reason.uppercase()] ?: "stop"
}

// =============================================================================
// Streaming SSE data classes
// =============================================================================

data class GeminiStreamDelta(
    val role: String = "assistant",
    val content: String? = null,
    val toolCalls: List<GeminiToolCall>? = null,
    val reasoning: String? = null,
    val reasoningContent: String? = null,
)

data class GeminiStreamChoice(
    val index: Int = 0,
    val delta: GeminiStreamDelta,
    val finishReason: String? = null
)

data class GeminiStreamChunk(
    val id: String,
    val objectType: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<GeminiStreamChoice>,
    val usage: GeminiUsage? = null,
)

private fun _makeStreamChunk(
    model: String,
    content: String = "",
    toolCallDelta: Map<String, Any>? = null,
    finishReason: String? = null,
    reasoning: String = "",
): GeminiStreamChunk {
    val toolCalls = if (toolCallDelta != null) {
        listOf(
            GeminiToolCall(
                index = (toolCallDelta["index"] as? Number)?.toInt() ?: 0,
                id = (toolCallDelta["id"] as? String)
                    ?: "call_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                function = GeminiToolCallFunction(
                    name = toolCallDelta["name"] as? String ?: "",
                    arguments = toolCallDelta["arguments"] as? String ?: "",
                )
            )
        )
    } else null

    val delta = GeminiStreamDelta(
        content = content.ifEmpty { null },
        toolCalls = toolCalls,
        reasoning = reasoning.ifEmpty { null },
        reasoningContent = reasoning.ifEmpty { null },
    )

    return GeminiStreamChunk(
        id = "chatcmpl-${UUID.randomUUID().toString().replace("-", "").take(12)}",
        created = System.currentTimeMillis() / 1000,
        model = model,
        choices = listOf(GeminiStreamChoice(delta = delta, finishReason = finishReason)),
    )
}

fun _translateStreamEvent(
    event: Map<String, Any>,
    model: String,
    toolCallIndices: MutableMap<String, Int>,
): List<GeminiStreamChunk> {
    @Suppress("UNCHECKED_CAST")
    val inner = (event["response"] as? Map<String, Any>) ?: event
    @Suppress("UNCHECKED_CAST")
    val candidates = inner["candidates"] as? List<Map<String, Any>> ?: return emptyList()
    if (candidates.isEmpty()) return emptyList()
    val cand = candidates[0]

    val chunks = mutableListOf<GeminiStreamChunk>()

    @Suppress("UNCHECKED_CAST")
    val content = cand["content"] as? Map<String, Any> ?: emptyMap()
    @Suppress("UNCHECKED_CAST")
    val parts = content["parts"] as? List<Map<String, Any>> ?: emptyList()

    for (part in parts) {
        if (part["thought"] == true && part["text"] is String) {
            chunks.add(_makeStreamChunk(model = model, reasoning = part["text"] as String))
            continue
        }
        if (part["text"] is String && (part["text"] as String).isNotEmpty()) {
            chunks.add(_makeStreamChunk(model = model, content = part["text"] as String))
        }
        @Suppress("UNCHECKED_CAST")
        val fc = part["functionCall"] as? Map<String, Any>
        if (fc != null && fc["name"] != null) {
            val name = fc["name"].toString()
            val idx = toolCallIndices.getOrPut(name) { toolCallIndices.size }
            val argsStr = try {
                JSONObject(fc["args"] as? Map<*, *> ?: emptyMap<String, Any>()).toString()
            } catch (_: Exception) {
                "{}"
            }
            chunks.add(
                _makeStreamChunk(
                    model = model,
                    toolCallDelta = mapOf(
                        "index" to idx,
                        "name" to name,
                        "arguments" to argsStr,
                    ),
                )
            )
        }
    }

    val finishReasonRaw = cand["finishReason"] as? String ?: ""
    if (finishReasonRaw.isNotEmpty()) {
        var mapped = _mapGeminiFinishReason(finishReasonRaw)
        if (toolCallIndices.isNotEmpty()) mapped = "tool_calls"
        chunks.add(_makeStreamChunk(model = model, finishReason = mapped))
    }

    return chunks
}

// =============================================================================
// GeminiCloudCodeClient — OpenAI-compatible facade
// =============================================================================

const val MARKER_BASE_URL = "cloudcode-pa://google"

class GeminiCloudCodeClient(
    val apiKey: String = "google-oauth",
    val baseUrl: String = MARKER_BASE_URL,
    private val defaultHeaders: Map<String, String> = emptyMap(),
    private val configuredProjectId: String = "",
) {
    private var _projectContext: ProjectContext? = null
    var isClosed: Boolean = false
        private set

    fun close() {
        isClosed = true
    }

    private fun _ensureProjectContext(accessToken: String, model: String): ProjectContext {
        _projectContext?.let { return it }

        val envProject = resolveProjectIdFromEnv()
        val creds = loadGoogleCredentials()
        val storedProject = creds?.projectId ?: ""

        if (storedProject.isNotEmpty()) {
            val ctx = ProjectContext(
                projectId = storedProject,
                managedProjectId = creds?.managedProjectId ?: "",
                source = "stored",
            )
            _projectContext = ctx
            return ctx
        }

        val ctx = resolveProjectContext(
            accessToken,
            configuredProjectId = configuredProjectId,
            envProjectId = envProject,
            userAgentModel = model,
        )
        if (ctx.projectId.isNotEmpty() || ctx.managedProjectId.isNotEmpty()) {
            updateProjectIds(
                projectId = ctx.projectId,
                managedProjectId = ctx.managedProjectId,
            )
        }
        _projectContext = ctx
        return ctx
    }

    fun createChatCompletion(
        model: String = "gemini-2.5-flash",
        messages: List<Map<String, Any>> = emptyList(),
        stream: Boolean = false,
        tools: Any? = null,
        toolChoice: Any? = null,
        temperature: Float? = null,
        maxTokens: Int? = null,
        topP: Float? = null,
        stop: Any? = null,
        extraBody: Map<String, Any>? = null,
    ): GeminiChatCompletionResponse {
        val accessToken = getValidAccessToken()
        val ctx = _ensureProjectContext(accessToken, model)

        var thinkingConfig: Any? = null
        if (extraBody != null) {
            thinkingConfig = extraBody["thinking_config"] ?: extraBody["thinkingConfig"]
        }

        val inner = buildGeminiRequest(
            messages = messages,
            tools = tools,
            toolChoice = toolChoice,
            temperature = temperature,
            maxTokens = maxTokens,
            topP = topP,
            stop = stop,
            thinkingConfig = thinkingConfig,
        )
        val wrapped = wrapCodeAssistRequest(
            projectId = ctx.projectId,
            model = model,
            innerRequest = inner,
        )

        val url = "${CODE_ASSIST_ENDPOINT}/v1internal:generateContent"
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("User-Agent", "hermes-agent (gemini-cli-compat)")
            connection.setRequestProperty("X-Goog-Api-Client", "gl-python/hermes")
            connection.setRequestProperty("x-activity-request-id", UUID.randomUUID().toString())
            for ((k, v) in defaultHeaders) {
                connection.setRequestProperty(k, v)
            }
            connection.connectTimeout = 15_000
            connection.readTimeout = 600_000

            val body = JSONObject(wrapped).toString()
            connection.outputStream.use { it.write(body.toByteArray()) }

            if (connection.responseCode != 200) {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.readText() ?: ""
                } catch (_: Exception) { "" }
                throw CodeAssistError(
                    "Code Assist HTTP ${connection.responseCode}: $errorBody",
                    code = "code_assist_http_${connection.responseCode}",
                    statusCode = connection.responseCode,
                )
            }

            val responseBody = connection.inputStream.bufferedReader().readText()
            val payload = JSONObject(responseBody)
            @Suppress("UNCHECKED_CAST")
            val payloadMap = jsonObjectToMap(payload)
            return _translateGeminiResponse(payloadMap, model)
        } finally {
            connection.disconnect()
        }
    }


    fun _streamCompletion(
        model: String = "",
        wrapped: Map<String, Any> = emptyMap(),
        headers: Map<String, String> = emptyMap()
    ): Iterator<_GeminiStreamChunk> {
        // Android: streaming via SSE requires OkHttp EventSource;
        // actual implementation in createChatCompletion handles non-streaming.
        // Return empty iterator as placeholder.
        return emptyList<_GeminiStreamChunk>().iterator()
    }
}

/**
 * Translate an httpx/HTTP response into a CodeAssistError with rich metadata.
 */
fun _geminiHttpError(
    statusCode: Int,
    bodyText: String,
): CodeAssistError {
    val bodyJson: Map<String, Any> = try {
        val jo = JSONObject(bodyText)
        jsonObjectToMap(jo)
    } catch (_: Exception) {
        emptyMap()
    }

    @Suppress("UNCHECKED_CAST")
    val errObj = (bodyJson["error"] as? Map<String, Any>) ?: emptyMap()
    val errStatus = (errObj["status"] as? String) ?: ""
    val errMessage = (errObj["message"] as? String) ?: ""

    val code = when (statusCode) {
        401 -> "code_assist_unauthorized"
        429 -> "code_assist_rate_limited"
        else -> "code_assist_http_$statusCode"
    }

    val message = if (errMessage.isNotEmpty()) {
        "Code Assist HTTP $statusCode ($errStatus): $errMessage"
    } else {
        "Code Assist returned HTTP $statusCode: ${bodyText.take(500)}"
    }

    return CodeAssistError(
        message = message,
        code = code,
        statusCode = statusCode,
    )
}

// Helper to convert JSONObject to Map
private fun jsonObjectToMap(jo: JSONObject): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    for (key in jo.keys()) {
        val value = jo.get(key)
        map[key] = when (value) {
            is JSONObject -> jsonObjectToMap(value)
            is JSONArray -> jsonArrayToList(value)
            JSONObject.NULL -> ""
            else -> value
        }
    }
    return map
}

private fun jsonArrayToList(ja: JSONArray): List<Any> {
    val list = mutableListOf<Any>()
    for (i in 0 until ja.length()) {
        val value = ja.get(i)
        list.add(
            when (value) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> ""
                else -> value
            }
        )
    }
    return list
}

/** Mimics an OpenAI ChatCompletionChunk with .choices[0].delta. */
class _GeminiStreamChunk(
    val id: String = "",
    val objectType: String = "chat.completion.chunk",
    val created: Long = 0L,
    val model: String = "",
    val choices: List<GeminiStreamChoice> = emptyList(),
    val usage: GeminiUsage? = null
)

class _GeminiChatCompletions(private val _client: GeminiCloudCodeClient) {
    fun create(kwargs: Map<String, Any?> = emptyMap()): Any? {
        return _client.createChatCompletion()
    }
}

class _GeminiChatNamespace(client: GeminiCloudCodeClient) {
    val completions = _GeminiChatCompletions(client)
}

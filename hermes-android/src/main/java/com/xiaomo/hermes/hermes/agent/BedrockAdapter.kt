/** 1:1 对齐 hermes/agent/bedrock_adapter.py */
package com.xiaomo.hermes.hermes.agent

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * AWS Bedrock Converse API adapter for Hermes Agent.
 *
 * Provides native integration with Amazon Bedrock using the Converse API.
 * Android 简化版：保留接口定义和转换逻辑，AWS SDK 调用由 app 模块实现。
 */

private const val _TAG = "BedrockAdapter"

// ---------------------------------------------------------------------------
// Client cache (placeholder for Android — actual AWS SDK calls delegated)
// ---------------------------------------------------------------------------

private val _bedrockRuntimeClientCache = ConcurrentHashMap<String, Any>()
private val _bedrockControlClientCache = ConcurrentHashMap<String, Any>()

fun resetClientCache() {
    _bedrockRuntimeClientCache.clear()
    _bedrockControlClientCache.clear()
}

// ---------------------------------------------------------------------------
// AWS credential detection
// ---------------------------------------------------------------------------

private val _AWS_CREDENTIAL_ENV_VARS = listOf(
    "AWS_BEARER_TOKEN_BEDROCK",
    "AWS_ACCESS_KEY_ID",
    "AWS_PROFILE",
    "AWS_CONTAINER_CREDENTIALS_RELATIVE_URI",
    "AWS_WEB_IDENTITY_TOKEN_FILE",
)

fun resolveAwsAuthEnvVar(env: Map<String, String>? = null): String? {
    val e = env ?: System.getenv()
    // Bearer token takes highest priority
    if (e["AWS_BEARER_TOKEN_BEDROCK"]?.isNotBlank() == true) {
        return "AWS_BEARER_TOKEN_BEDROCK"
    }
    // Explicit access key pair
    if (e["AWS_ACCESS_KEY_ID"]?.isNotBlank() == true &&
        e["AWS_SECRET_ACCESS_KEY"]?.isNotBlank() == true
    ) {
        return "AWS_ACCESS_KEY_ID"
    }
    // Named profile (SSO, assume-role, etc.)
    if (e["AWS_PROFILE"]?.isNotBlank() == true) {
        return "AWS_PROFILE"
    }
    // Container credentials (ECS, CodeBuild)
    if (e["AWS_CONTAINER_CREDENTIALS_RELATIVE_URI"]?.isNotBlank() == true) {
        return "AWS_CONTAINER_CREDENTIALS_RELATIVE_URI"
    }
    // Web identity (EKS IRSA)
    if (e["AWS_WEB_IDENTITY_TOKEN_FILE"]?.isNotBlank() == true) {
        return "AWS_WEB_IDENTITY_TOKEN_FILE"
    }
    // On Android, no botocore fallback — return null
    return null
}

fun hasAwsCredentials(env: Map<String, String>? = null): Boolean {
    return resolveAwsAuthEnvVar(env) != null
}

fun resolveBedrockRegion(env: Map<String, String>? = null): String {
    val e = env ?: System.getenv()
    return e["AWS_REGION"]?.takeIf { it.isNotBlank() }
        ?: e["AWS_DEFAULT_REGION"]?.takeIf { it.isNotBlank() }
        ?: "us-east-1"
}

// ---------------------------------------------------------------------------
// Tool-calling capability detection
// ---------------------------------------------------------------------------

private val _NON_TOOL_CALLING_PATTERNS = listOf(
    "deepseek.r1",
    "deepseek-r1",
    "stability.",
    "cohere.embed",
    "amazon.titan-embed",
)

fun _modelSupportsToolUse(modelId: String): Boolean {
    val modelLower = modelId.lowercase()
    return _NON_TOOL_CALLING_PATTERNS.none { pattern -> pattern in modelLower }
}

fun isAnthropicBedrockModel(modelId: String): Boolean {
    var modelLower = modelId.lowercase()
    for (prefix in listOf("us.", "global.", "eu.", "ap.", "jp.")) {
        if (modelLower.startsWith(prefix)) {
            modelLower = modelLower.substring(prefix.length)
            break
        }
    }
    return modelLower.startsWith("anthropic.claude")
}

// ---------------------------------------------------------------------------
// Message format conversion: OpenAI → Bedrock Converse
// ---------------------------------------------------------------------------

fun convertToolsToConverse(tools: List<Map<String, Any>>): List<Map<String, Any>> {
    if (tools.isEmpty()) return emptyList()
    return tools.map { t ->
        @Suppress("UNCHECKED_CAST")
        val fn = t["function"] as? Map<String, Any> ?: emptyMap()
        val name = fn["name"] as? String ?: ""
        val description = fn["description"] as? String ?: ""
        @Suppress("UNCHECKED_CAST")
        val parameters = fn["parameters"] as? Map<String, Any>
            ?: mapOf("type" to "object", "properties" to emptyMap<String, Any>())
        mapOf(
            "toolSpec" to mapOf(
                "name" to name,
                "description" to description,
                "inputSchema" to mapOf("json" to parameters),
            )
        )
    }
}

fun _convertContentToConverse(content: Any?): List<Map<String, Any>> {
    if (content == null) return listOf(mapOf("text" to " "))
    if (content is String) {
        return if (content.isBlank()) listOf(mapOf("text" to " "))
        else listOf(mapOf("text" to content))
    }
    if (content is List<*>) {
        val blocks = mutableListOf<Map<String, Any>>()
        for (part in content) {
            if (part is String) {
                blocks.add(mapOf("text" to part))
                continue
            }
            if (part !is Map<*, *>) continue
            val partType = part["type"] as? String ?: ""
            when (partType) {
                "text" -> {
                    val text = part["text"] as? String ?: ""
                    blocks.add(mapOf("text" to text.ifEmpty { " " }))
                }
                "image_url" -> {
                    @Suppress("UNCHECKED_CAST")
                    val imageUrl = part["image_url"] as? Map<String, Any> ?: emptyMap()
                    val url = imageUrl["url"] as? String ?: ""
                    if (url.startsWith("data:")) {
                        val commaIdx = url.indexOf(",")
                        val header = if (commaIdx > 0) url.substring(0, commaIdx) else ""
                        val data = if (commaIdx > 0) url.substring(commaIdx + 1) else ""
                        var mediaType = "image/jpeg"
                        if (header.startsWith("data:")) {
                            val mimePart = header.substring(5).split(";").firstOrNull() ?: ""
                            if (mimePart.isNotEmpty()) mediaType = mimePart
                        }
                        val format = if ("/" in mediaType) mediaType.split("/").last() else "jpeg"
                        blocks.add(
                            mapOf(
                                "image" to mapOf(
                                    "format" to format,
                                    "source" to mapOf("bytes" to data),
                                )
                            )
                        )
                    } else {
                        blocks.add(mapOf("text" to "[Image: $url]"))
                    }
                }
            }
        }
        return blocks.ifEmpty { listOf(mapOf("text" to " ")) }
    }
    return listOf(mapOf("text" to content.toString()))
}

data class ConverseMessagesResult(
    val systemPrompt: List<Map<String, Any>>?,
    val converseMessages: List<MutableMap<String, Any>>
)

fun convertMessagesToConverse(
    messages: List<Map<String, Any>>
): ConverseMessagesResult {
    val systemBlocks = mutableListOf<Map<String, Any>>()
    val converseMsgs = mutableListOf<MutableMap<String, Any>>()

    for (msg in messages) {
        val role = msg["role"] as? String ?: ""
        val content = msg["content"]

        if (role == "system") {
            if (content is String && content.isNotBlank()) {
                systemBlocks.add(mapOf("text" to content))
            } else if (content is List<*>) {
                for (part in content) {
                    if (part is Map<*, *> && part["type"] == "text") {
                        systemBlocks.add(mapOf("text" to (part["text"] as? String ?: "")))
                    } else if (part is String) {
                        systemBlocks.add(mapOf("text" to part))
                    }
                }
            }
            continue
        }

        if (role == "tool") {
            val toolCallId = msg["tool_call_id"] as? String ?: ""
            val resultContent = if (content is String) content else JSONObject(content as? Map<*, *> ?: emptyMap<String, Any>()).toString()
            val toolResultBlock = mapOf(
                "toolResult" to mapOf(
                    "toolUseId" to toolCallId,
                    "content" to listOf(mapOf("text" to resultContent)),
                )
            )
            if (converseMsgs.isNotEmpty() && converseMsgs.last()["role"] == "user") {
                @Suppress("UNCHECKED_CAST")
                val lastContent = converseMsgs.last()["content"] as MutableList<Map<String, Any>>
                lastContent.add(toolResultBlock)
            } else {
                converseMsgs.add(
                    mutableMapOf(
                        "role" to "user" as Any,
                        "content" to mutableListOf(toolResultBlock) as Any,
                    )
                )
            }
            continue
        }

        if (role == "assistant") {
            val contentBlocks = mutableListOf<Map<String, Any>>()
            if (content is String && content.isNotBlank()) {
                contentBlocks.add(mapOf("text" to content))
            } else if (content is List<*>) {
                contentBlocks.addAll(_convertContentToConverse(content))
            }

            @Suppress("UNCHECKED_CAST")
            val toolCalls = msg["tool_calls"] as? List<Map<String, Any>> ?: emptyList()
            for (tc in toolCalls) {
                @Suppress("UNCHECKED_CAST")
                val fn = tc["function"] as? Map<String, Any> ?: emptyMap()
                val argsStr = fn["arguments"] as? String ?: "{}"
                val argsDict = try {
                    val jo = JSONObject(argsStr)
                    val map = mutableMapOf<String, Any>()
                    for (key in jo.keys()) {
                        map[key] = jo.get(key)
                    }
                    map
                } catch (_: Exception) {
                    emptyMap()
                }
                contentBlocks.add(
                    mapOf(
                        "toolUse" to mapOf(
                            "toolUseId" to (tc["id"] as? String ?: ""),
                            "name" to (fn["name"] as? String ?: ""),
                            "input" to argsDict,
                        )
                    )
                )
            }

            if (contentBlocks.isEmpty()) {
                contentBlocks.add(mapOf("text" to " "))
            }

            if (converseMsgs.isNotEmpty() && converseMsgs.last()["role"] == "assistant") {
                @Suppress("UNCHECKED_CAST")
                val lastContent = converseMsgs.last()["content"] as MutableList<Map<String, Any>>
                lastContent.addAll(contentBlocks)
            } else {
                converseMsgs.add(
                    mutableMapOf(
                        "role" to "assistant" as Any,
                        "content" to contentBlocks.toMutableList() as Any,
                    )
                )
            }
            continue
        }

        if (role == "user") {
            val contentBlocks = _convertContentToConverse(content)
            if (converseMsgs.isNotEmpty() && converseMsgs.last()["role"] == "user") {
                @Suppress("UNCHECKED_CAST")
                val lastContent = converseMsgs.last()["content"] as MutableList<Map<String, Any>>
                lastContent.addAll(contentBlocks)
            } else {
                converseMsgs.add(
                    mutableMapOf(
                        "role" to "user" as Any,
                        "content" to contentBlocks.toMutableList() as Any,
                    )
                )
            }
            continue
        }
    }

    // Converse requires the first message to be from the user
    if (converseMsgs.isNotEmpty() && converseMsgs.first()["role"] != "user") {
        converseMsgs.add(0, mutableMapOf("role" to "user" as Any, "content" to mutableListOf(mapOf("text" to " ")) as Any))
    }
    // Converse requires the last message to be from the user
    if (converseMsgs.isNotEmpty() && converseMsgs.last()["role"] != "user") {
        converseMsgs.add(mutableMapOf("role" to "user" as Any, "content" to mutableListOf(mapOf("text" to " ")) as Any))
    }

    return ConverseMessagesResult(
        systemPrompt = systemBlocks.ifEmpty { null },
        converseMessages = converseMsgs,
    )
}

// ---------------------------------------------------------------------------
// Response format conversion: Bedrock Converse → OpenAI
// ---------------------------------------------------------------------------

fun _converseStopReasonToOpenai(stopReason: String): String {
    val mapping = mapOf(
        "end_turn" to "stop",
        "stop_sequence" to "stop",
        "tool_use" to "tool_calls",
        "max_tokens" to "length",
        "content_filtered" to "content_filter",
        "guardrail_intervened" to "content_filter",
    )
    return mapping[stopReason] ?: "stop"
}

data class ConverseToolCall(
    val id: String,
    val type: String = "function",
    val function: ConverseToolCallFunction
)

data class ConverseToolCallFunction(
    val name: String,
    val arguments: String
)

data class ConverseMessage(
    val role: String = "assistant",
    val content: String?,
    val toolCalls: List<ConverseToolCall>?
)

data class ConverseUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

data class ConverseChoice(
    val index: Int = 0,
    val message: ConverseMessage,
    val finishReason: String
)

data class ConverseResponse(
    val choices: List<ConverseChoice>,
    val usage: ConverseUsage,
    val model: String = ""
)

fun normalizeConverseResponse(response: Map<String, Any>): ConverseResponse {
    @Suppress("UNCHECKED_CAST")
    val output = response["output"] as? Map<String, Any> ?: emptyMap()
    @Suppress("UNCHECKED_CAST")
    val message = output["message"] as? Map<String, Any> ?: emptyMap()
    @Suppress("UNCHECKED_CAST")
    val contentBlocks = message["content"] as? List<Map<String, Any>> ?: emptyList()
    val stopReason = response["stopReason"] as? String ?: "end_turn"

    val textParts = mutableListOf<String>()
    val toolCalls = mutableListOf<ConverseToolCall>()

    for (block in contentBlocks) {
        if (block.containsKey("text")) {
            textParts.add(block["text"] as? String ?: "")
        } else if (block.containsKey("toolUse")) {
            @Suppress("UNCHECKED_CAST")
            val tu = block["toolUse"] as? Map<String, Any> ?: emptyMap()
            toolCalls.add(
                ConverseToolCall(
                    id = tu["toolUseId"] as? String ?: "",
                    function = ConverseToolCallFunction(
                        name = tu["name"] as? String ?: "",
                        arguments = JSONObject(tu["input"] as? Map<*, *> ?: emptyMap<String, Any>()).toString(),
                    )
                )
            )
        }
    }

    val msg = ConverseMessage(
        content = if (textParts.isNotEmpty()) textParts.joinToString("\n") else null,
        toolCalls = toolCalls.ifEmpty { null },
    )

    @Suppress("UNCHECKED_CAST")
    val usageData = response["usage"] as? Map<String, Any> ?: emptyMap()
    val inputTokens = (usageData["inputTokens"] as? Number)?.toInt() ?: 0
    val outputTokens = (usageData["outputTokens"] as? Number)?.toInt() ?: 0
    val usage = ConverseUsage(
        promptTokens = inputTokens,
        completionTokens = outputTokens,
        totalTokens = inputTokens + outputTokens,
    )

    var finishReason = _converseStopReasonToOpenai(stopReason)
    if (toolCalls.isNotEmpty() && finishReason == "stop") {
        finishReason = "tool_calls"
    }

    return ConverseResponse(
        choices = listOf(ConverseChoice(message = msg, finishReason = finishReason)),
        usage = usage,
        model = response["modelId"] as? String ?: "",
    )
}

// ---------------------------------------------------------------------------
// Streaming response conversion
// ---------------------------------------------------------------------------

fun normalizeConverseStreamEvents(eventStream: Map<String, Any>): ConverseResponse {
    return streamConverseWithCallbacks(eventStream)
}

fun streamConverseWithCallbacks(
    eventStream: Map<String, Any>,
    onTextDelta: ((String) -> Unit)? = null,
    onToolStart: ((String) -> Unit)? = null,
    onReasoningDelta: ((String) -> Unit)? = null,
    onInterruptCheck: (() -> Boolean)? = null,
): ConverseResponse {
    val textParts = mutableListOf<String>()
    val toolCalls = mutableListOf<ConverseToolCall>()
    var currentTool: MutableMap<String, String>? = null
    val currentTextBuffer = mutableListOf<String>()
    var hasToolUse = false
    var stopReason = "end_turn"
    var usageData = mutableMapOf<String, Int>()

    @Suppress("UNCHECKED_CAST")
    val stream = eventStream["stream"] as? List<Map<String, Any>> ?: emptyList()

    for (event in stream) {
        if (onInterruptCheck?.invoke() == true) break

        if (event.containsKey("contentBlockStart")) {
            @Suppress("UNCHECKED_CAST")
            val start = (event["contentBlockStart"] as? Map<String, Any>)?.get("start") as? Map<String, Any> ?: emptyMap()
            if (start.containsKey("toolUse")) {
                hasToolUse = true
                if (currentTextBuffer.isNotEmpty()) {
                    textParts.add(currentTextBuffer.joinToString(""))
                    currentTextBuffer.clear()
                }
                @Suppress("UNCHECKED_CAST")
                val toolUse = start["toolUse"] as? Map<String, Any> ?: emptyMap()
                currentTool = mutableMapOf(
                    "toolUseId" to (toolUse["toolUseId"] as? String ?: ""),
                    "name" to (toolUse["name"] as? String ?: ""),
                    "input_json" to "",
                )
                onToolStart?.invoke(currentTool!!["name"]!!)
            }
        } else if (event.containsKey("contentBlockDelta")) {
            @Suppress("UNCHECKED_CAST")
            val delta = (event["contentBlockDelta"] as? Map<String, Any>)?.get("delta") as? Map<String, Any> ?: emptyMap()
            if (delta.containsKey("text")) {
                val text = delta["text"] as? String ?: ""
                currentTextBuffer.add(text)
                if (onTextDelta != null && !hasToolUse) {
                    onTextDelta(text)
                }
            } else if (delta.containsKey("toolUse")) {
                if (currentTool != null) {
                    @Suppress("UNCHECKED_CAST")
                    val toolUseDelta = delta["toolUse"] as? Map<String, Any> ?: emptyMap()
                    currentTool["input_json"] = (currentTool["input_json"] ?: "") + (toolUseDelta["input"] as? String ?: "")
                }
            } else if (delta.containsKey("reasoningContent")) {
                @Suppress("UNCHECKED_CAST")
                val reasoning = delta["reasoningContent"] as? Map<String, Any>
                if (reasoning != null) {
                    val thinkingText = reasoning["text"] as? String ?: ""
                    if (thinkingText.isNotEmpty()) {
                        onReasoningDelta?.invoke(thinkingText)
                    }
                }
            }
        } else if (event.containsKey("contentBlockStop")) {
            if (currentTool != null) {
                val inputJson = currentTool["input_json"] ?: ""
                val inputDict = try {
                    if (inputJson.isNotEmpty()) JSONObject(inputJson).toString() else "{}"
                } catch (_: Exception) {
                    "{}"
                }
                toolCalls.add(
                    ConverseToolCall(
                        id = currentTool["toolUseId"] ?: "",
                        function = ConverseToolCallFunction(
                            name = currentTool["name"] ?: "",
                            arguments = inputDict,
                        )
                    )
                )
                currentTool = null
            } else if (currentTextBuffer.isNotEmpty()) {
                textParts.add(currentTextBuffer.joinToString(""))
                currentTextBuffer.clear()
            }
        } else if (event.containsKey("messageStop")) {
            @Suppress("UNCHECKED_CAST")
            val messageStop = event["messageStop"] as? Map<String, Any> ?: emptyMap()
            stopReason = messageStop["stopReason"] as? String ?: "end_turn"
        } else if (event.containsKey("metadata")) {
            @Suppress("UNCHECKED_CAST")
            val metaUsage = (event["metadata"] as? Map<String, Any>)?.get("usage") as? Map<String, Any> ?: emptyMap()
            usageData["inputTokens"] = (metaUsage["inputTokens"] as? Number)?.toInt() ?: 0
            usageData["outputTokens"] = (metaUsage["outputTokens"] as? Number)?.toInt() ?: 0
        }
    }

    // Flush remaining text
    if (currentTextBuffer.isNotEmpty()) {
        textParts.add(currentTextBuffer.joinToString(""))
    }

    val msg = ConverseMessage(
        content = if (textParts.isNotEmpty()) textParts.joinToString("\n") else null,
        toolCalls = toolCalls.ifEmpty { null },
    )

    val inputTokens = usageData["inputTokens"] ?: 0
    val outputTokens = usageData["outputTokens"] ?: 0
    val usage = ConverseUsage(
        promptTokens = inputTokens,
        completionTokens = outputTokens,
        totalTokens = inputTokens + outputTokens,
    )

    var finishReason = _converseStopReasonToOpenai(stopReason)
    if (toolCalls.isNotEmpty() && finishReason == "stop") {
        finishReason = "tool_calls"
    }

    return ConverseResponse(
        choices = listOf(ConverseChoice(message = msg, finishReason = finishReason)),
        usage = usage,
    )
}

// ---------------------------------------------------------------------------
// High-level API: build Converse kwargs
// ---------------------------------------------------------------------------

fun buildConverseKwargs(
    model: String,
    messages: List<Map<String, Any>>,
    tools: List<Map<String, Any>>? = null,
    maxTokens: Int = 4096,
    temperature: Float? = null,
    topP: Float? = null,
    stopSequences: List<String>? = null,
    guardrailConfig: Map<String, Any>? = null,
): Map<String, Any> {
    val (systemPrompt, converseMessages) = convertMessagesToConverse(messages)

    val inferenceConfig = mutableMapOf<String, Any>("maxTokens" to maxTokens)
    if (temperature != null) inferenceConfig["temperature"] = temperature
    if (topP != null) inferenceConfig["topP"] = topP
    if (stopSequences != null) inferenceConfig["stopSequences"] = stopSequences

    val kwargs = mutableMapOf<String, Any>(
        "modelId" to model,
        "messages" to converseMessages,
        "inferenceConfig" to inferenceConfig,
    )

    if (systemPrompt != null) {
        kwargs["system"] = systemPrompt
    }

    if (!tools.isNullOrEmpty()) {
        val converseTools = convertToolsToConverse(tools)
        if (converseTools.isNotEmpty()) {
            if (_modelSupportsToolUse(model)) {
                kwargs["toolConfig"] = mapOf("tools" to converseTools)
            } else {
                Log.w(
                    _TAG,
                    "Model $model does not support tool calling — tools stripped. " +
                        "The agent will operate in text-only mode."
                )
            }
        }
    }

    if (guardrailConfig != null) {
        kwargs["guardrailConfig"] = guardrailConfig
    }

    return kwargs
}

// ---------------------------------------------------------------------------
// Model discovery
// ---------------------------------------------------------------------------

private val _discoveryCache = ConcurrentHashMap<String, Pair<Long, List<Map<String, Any>>>>()
private const val _DISCOVERY_CACHE_TTL_SECONDS = 3600L

fun resetDiscoveryCache() {
    _discoveryCache.clear()
}

data class BedrockModelInfo(
    val id: String,
    val name: String,
    val provider: String,
    val inputModalities: List<String>,
    val outputModalities: List<String>,
    val streaming: Boolean
)

/**
 * Discover available Bedrock foundation models and inference profiles.
 *
 * Android 简化版：返回空列表（实际发现逻辑需要 AWS SDK）。
 * 保留接口以便 app 模块扩展实现。
 */
fun discoverBedrockModels(
    region: String,
    providerFilter: List<String>? = null,
): List<Map<String, Any>> {
    val cacheKey = "$region:${(providerFilter ?: emptyList()).sorted().joinToString(",")}"
    val cached = _discoveryCache[cacheKey]
    if (cached != null && (System.currentTimeMillis() / 1000 - cached.first) < _DISCOVERY_CACHE_TTL_SECONDS) {
        return cached.second
    }
    // On Android, actual discovery requires AWS SDK — return empty
    Log.d(_TAG, "Bedrock model discovery not implemented on Android for region: $region")
    return emptyList()
}

fun _extractProviderFromArn(arn: String): String {
    val regex = Regex("""foundation-model/([^.]+)""")
    val match = regex.find(arn)
    return match?.groupValues?.getOrNull(1) ?: ""
}

fun getBedrockModelIds(region: String): List<String> {
    return discoverBedrockModels(region).map { it["id"] as? String ?: "" }
}

// ---------------------------------------------------------------------------
// Error classification — Bedrock-specific exceptions
// ---------------------------------------------------------------------------

private val CONTEXT_OVERFLOW_PATTERNS = listOf(
    Regex("""ValidationException.*(?:input is too long|max input token|input token.*exceed)""", RegexOption.IGNORE_CASE),
    Regex("""ValidationException.*(?:exceeds? the (?:maximum|max) (?:number of )?(?:input )?tokens)""", RegexOption.IGNORE_CASE),
    Regex("""ModelStreamErrorException.*(?:Input is too long|too many input tokens)""", RegexOption.IGNORE_CASE),
)

private val THROTTLE_PATTERNS = listOf(
    Regex("""ThrottlingException""", RegexOption.IGNORE_CASE),
    Regex("""Too many concurrent requests""", RegexOption.IGNORE_CASE),
    Regex("""ServiceQuotaExceededException""", RegexOption.IGNORE_CASE),
)

private val OVERLOAD_PATTERNS = listOf(
    Regex("""ModelNotReadyException""", RegexOption.IGNORE_CASE),
    Regex("""ModelTimeoutException""", RegexOption.IGNORE_CASE),
    Regex("""InternalServerException""", RegexOption.IGNORE_CASE),
)

fun isContextOverflowError(errorMessage: String): Boolean {
    return CONTEXT_OVERFLOW_PATTERNS.any { it.containsMatchIn(errorMessage) }
}

fun classifyBedrockError(errorMessage: String): String {
    if (isContextOverflowError(errorMessage)) return "context_overflow"
    if (THROTTLE_PATTERNS.any { it.containsMatchIn(errorMessage) }) return "rate_limit"
    if (OVERLOAD_PATTERNS.any { it.containsMatchIn(errorMessage) }) return "overloaded"
    return "unknown"
}

// ---------------------------------------------------------------------------
// Bedrock model context lengths
// ---------------------------------------------------------------------------

val BEDROCK_CONTEXT_LENGTHS: Map<String, Int> = mapOf(
    // Anthropic Claude models on Bedrock
    "anthropic.claude-opus-4-6" to 200_000,
    "anthropic.claude-sonnet-4-6" to 200_000,
    "anthropic.claude-sonnet-4-5" to 200_000,
    "anthropic.claude-haiku-4-5" to 200_000,
    "anthropic.claude-opus-4" to 200_000,
    "anthropic.claude-sonnet-4" to 200_000,
    "anthropic.claude-3-5-sonnet" to 200_000,
    "anthropic.claude-3-5-haiku" to 200_000,
    "anthropic.claude-3-opus" to 200_000,
    "anthropic.claude-3-sonnet" to 200_000,
    "anthropic.claude-3-haiku" to 200_000,
    // Amazon Nova
    "amazon.nova-pro" to 300_000,
    "amazon.nova-lite" to 300_000,
    "amazon.nova-micro" to 128_000,
    // Meta Llama
    "meta.llama4-maverick" to 128_000,
    "meta.llama4-scout" to 128_000,
    "meta.llama3-3-70b-instruct" to 128_000,
    // Mistral
    "mistral.mistral-large" to 128_000,
    // DeepSeek
    "deepseek.v3" to 128_000,
)

const val BEDROCK_DEFAULT_CONTEXT_LENGTH = 128_000

fun getBedrockContextLength(modelId: String): Int {
    val modelLower = modelId.lowercase()
    var bestKey = ""
    var bestVal = BEDROCK_DEFAULT_CONTEXT_LENGTH
    for ((key, value) in BEDROCK_CONTEXT_LENGTHS) {
        if (key in modelLower && key.length > bestKey.length) {
            bestKey = key
            bestVal = value
        }
    }
    return bestVal
}

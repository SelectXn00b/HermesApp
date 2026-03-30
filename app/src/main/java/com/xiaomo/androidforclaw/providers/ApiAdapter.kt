package com.xiaomo.androidforclaw.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/pi-embedded-payloads.ts
 *
 * AndroidForClaw adaptation: provider auth/header/body request shaping.
 */


import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.config.ModelApi
import com.xiaomo.androidforclaw.config.ModelDefinition
import com.xiaomo.androidforclaw.config.ProviderConfig
import com.xiaomo.androidforclaw.providers.llm.Message
import com.xiaomo.androidforclaw.providers.llm.ToolDefinition as NewToolDefinition
import okhttp3.Headers
import org.json.JSONArray
import org.json.JSONObject

/**
 * API 适配器
 * Responsible for converting generic request format to specific formats of different API providers
 *
 * Reference: OpenClaw src/agents/llm-adapters/
 */
object ApiAdapter {

    internal fun shouldUseNullContentForAssistantToolCall(message: Message): Boolean {
        return message.role == "assistant" &&
            !message.toolCalls.isNullOrEmpty() &&
            message.content.isEmpty()
    }

    /**
     * 构建请求体
     */
    fun buildRequestBody(
        provider: ProviderConfig,
        model: ModelDefinition,
        messages: List<Message>,
        tools: List<NewToolDefinition>?,
        temperature: Double,
        maxTokens: Int?,
        reasoningEnabled: Boolean
    ): JSONObject {
        val api = model.api ?: provider.api

        return when (api) {
            ModelApi.ANTHROPIC_MESSAGES -> buildAnthropicRequest(
                model, messages, tools, temperature, maxTokens, reasoningEnabled
            )
            ModelApi.OPENAI_COMPLETIONS -> buildOpenAIRequest(
                model, messages, tools, temperature, maxTokens, reasoningEnabled
            )
            ModelApi.OPENAI_RESPONSES,
            ModelApi.OPENAI_CODEX_RESPONSES -> buildOpenAIResponsesRequest(
                model, messages, tools, temperature, maxTokens, reasoningEnabled
            )
            ModelApi.GOOGLE_GENERATIVE_AI -> buildGeminiRequest(
                model, messages, tools, temperature, maxTokens
            )
            ModelApi.OLLAMA -> buildOllamaRequest(
                provider, model, messages, tools, temperature, maxTokens
            )
            ModelApi.GITHUB_COPILOT -> buildCopilotRequest(
                model, messages, tools, temperature, maxTokens
            )
            else -> {
                // 默认使用 OpenAI 兼容格式
                buildOpenAIRequest(model, messages, tools, temperature, maxTokens, reasoningEnabled)
            }
        }
    }

    /**
     * OpenRouter app attribution headers.
     * OpenRouter uses HTTP-Referer and X-Title to identify the calling app.
     * When AppName=OpenClaw, certain models (e.g. MiMo) are free.
     *
     * Aligned with OpenClaw OPENROUTER_APP_HEADERS (proxy-stream-wrappers.ts).
     */
    private val OPENROUTER_APP_HEADERS = mapOf(
        "HTTP-Referer" to "https://openclaw.ai",
        "X-Title" to "OpenClaw"
    )

    /**
     * 构建请求头
     */
    fun buildHeaders(
        provider: ProviderConfig,
        model: ModelDefinition
    ): Headers {
        val builder = Headers.Builder()

        // OpenRouter app attribution headers (must be present on ALL requests
        // including compaction, image analysis, etc. to avoid "Unknown" app name
        // and unexpected billing). Aligned with OpenClaw createOpenRouterWrapper().
        if (isOpenRouterProvider(provider)) {
            OPENROUTER_APP_HEADERS.forEach { (key, value) ->
                builder.add(key, value)
            }
        }

        // Provider-level custom headers
        provider.headers?.forEach { (key, value) ->
            builder.add(key, value)
        }

        // Model-level custom headers (higher priority)
        model.headers?.forEach { (key, value) ->
            builder.add(key, value)
        }

        // Add API Key (if authHeader is configured)
        android.util.Log.d("ApiAdapter", "🔑 authHeader=${provider.authHeader}, apiKey=${provider.apiKey?.take(10)}")
        if (provider.authHeader && provider.apiKey != null) {
            val api = model.api ?: provider.api
            when (api) {
                ModelApi.ANTHROPIC_MESSAGES -> {
                    builder.add("x-api-key", provider.apiKey)
                    builder.add("anthropic-version", "2023-06-01")
                }
                ModelApi.GOOGLE_GENERATIVE_AI -> {
                    // Google uses ?key= query param, not Authorization header
                }
                else -> {
                    // OpenAI-style Authorization header
                    builder.add("Authorization", "Bearer ${provider.apiKey}")
                }
            }
        }

        // Set Content-Type
        builder.add("Content-Type", "application/json")

        return builder.build()
    }

    /**
     * Detect if a provider is OpenRouter based on its baseUrl.
     */
    private fun isOpenRouterProvider(provider: ProviderConfig): Boolean {
        return provider.baseUrl.contains("openrouter.ai", ignoreCase = true)
    }

    /**
     * 解析响应
     */
    fun parseResponse(
        api: String,
        responseBody: String
    ): ParsedResponse {
        return when (api) {
            ModelApi.ANTHROPIC_MESSAGES -> parseAnthropicResponse(responseBody)
            ModelApi.OPENAI_COMPLETIONS,
            ModelApi.GITHUB_COPILOT -> parseOpenAIResponse(responseBody)
            ModelApi.OLLAMA -> parseOllamaResponse(responseBody)
            ModelApi.OPENAI_RESPONSES,
            ModelApi.OPENAI_CODEX_RESPONSES -> parseOpenAIResponsesResponse(responseBody)
            ModelApi.GOOGLE_GENERATIVE_AI -> parseGeminiResponse(responseBody)
            else -> parseOpenAIResponse(responseBody)  // Parse as OpenAI format by default
        }
    }

    // ============ Anthropic Messages API ============

    private fun buildAnthropicRequest(
        model: ModelDefinition,
        messages: List<Message>,
        tools: List<NewToolDefinition>?,
        temperature: Double,
        maxTokens: Int?,
        reasoningEnabled: Boolean
    ): JSONObject {
        val json = JSONObject()

        json.put("model", model.id)
        json.put("max_tokens", maxTokens ?: model.maxTokens)
        json.put("temperature", temperature)

        // Convert message format
        val anthropicMessages = JSONArray()
        var systemMessage: String? = null

        messages.forEach { message ->
            when (message.role) {
                "system" -> {
                    systemMessage = message.content
                }
                "user", "assistant" -> {
                    val msg = JSONObject()
                    msg.put("role", message.role)
                    // Multimodal: if user message has images, build content array
                    if (message.role == "user" && !message.images.isNullOrEmpty()) {
                        val contentArray = JSONArray()
                        // Images first (aligned with Anthropic best practice)
                        for (img in message.images!!) {
                            contentArray.put(JSONObject().apply {
                                put("type", "image")
                                put("source", JSONObject().apply {
                                    put("type", "base64")
                                    put("media_type", img.mimeType)
                                    put("data", img.base64)
                                })
                            })
                        }
                        // Then text
                        if (message.content.isNotBlank()) {
                            contentArray.put(JSONObject().apply {
                                put("type", "text")
                                put("text", message.content)
                            })
                        }
                        msg.put("content", contentArray)
                    } else {
                        msg.put("content", message.content)
                    }
                    anthropicMessages.put(msg)
                }
                "tool" -> {
                    // Anthropic 使用 tool_result 格式，支持多模态（文本+图片）
                    val toolResultContent = if (!message.images.isNullOrEmpty()) {
                        // Multimodal tool result: text block + image blocks
                        JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", message.content)
                            })
                            message.images.forEach { img ->
                                put(JSONObject().apply {
                                    put("type", "image")
                                    put("source", JSONObject().apply {
                                        put("type", "base64")
                                        put("media_type", img.mimeType)
                                        put("data", img.base64)
                                    })
                                })
                            }
                        }
                    } else {
                        // Plain text tool result
                        message.content
                    }
                    val msg = JSONObject()
                    msg.put("role", "user")
                    msg.put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "tool_result")
                            put("tool_use_id", message.toolCallId ?: "")
                            put("content", toolResultContent)
                        })
                    })
                    anthropicMessages.put(msg)
                }
            }
        }

        json.put("messages", anthropicMessages)

        // Add system message
        if (systemMessage != null) {
            json.put("system", systemMessage)
        }

        // Add tools (use buildToolJson for proper JSON escaping)
        if (!tools.isNullOrEmpty()) {
            val anthropicTools = JSONArray()
            tools.forEach { tool ->
                val toolJson = JSONObject()
                toolJson.put("name", tool.function.name)
                toolJson.put("description", tool.function.description)
                toolJson.put("input_schema", buildParametersJson(tool.function.parameters))
                anthropicTools.put(toolJson)
            }
            json.put("tools", anthropicTools)
        }

        // Extended Thinking support
        if (reasoningEnabled && model.reasoning) {
            json.put("thinking", JSONObject().apply {
                put("type", "enabled")
                put("budget_tokens", 10000)
            })
        }

        return json
    }

    private fun parseAnthropicResponse(responseBody: String): ParsedResponse {
        val json = JSONObject(responseBody)

        var content: String? = null
        val toolCalls = mutableListOf<ToolCall>()
        var thinkingContent: String? = null

        // Parse content array
        val contentArray = json.optJSONArray("content")
        if (contentArray != null) {
            for (i in 0 until contentArray.length()) {
                val block = contentArray.getJSONObject(i)
                when (block.getString("type")) {
                    "text" -> {
                        content = block.getString("text")
                    }
                    "thinking" -> {
                        thinkingContent = block.getString("thinking")
                    }
                    "tool_use" -> {
                        toolCalls.add(
                            ToolCall(
                                id = block.getString("id"),
                                name = block.getString("name"),
                                arguments = block.getJSONObject("input").toString()
                            )
                        )
                    }
                }
            }
        }

        // Parse usage
        val usage = json.optJSONObject("usage")?.let {
            Usage(
                promptTokens = it.optInt("input_tokens", 0),
                completionTokens = it.optInt("output_tokens", 0)
            )
        }

        return ParsedResponse(
            content = content,
            toolCalls = toolCalls.ifEmpty { null },
            thinkingContent = thinkingContent,
            usage = usage,
            finishReason = json.optString("stop_reason")
        )
    }

    // ============ OpenAI Chat Completions API ============

    private fun buildOpenAIRequest(
        model: ModelDefinition,
        messages: List<Message>,
        tools: List<NewToolDefinition>?,
        temperature: Double,
        maxTokens: Int?,
        reasoningEnabled: Boolean
    ): JSONObject {
        val json = JSONObject()

        json.put("model", model.id)
        json.put("temperature", temperature)

        // maxTokens field name (based on compatibility config + safe defaults)
        val modelIdLower = model.id.lowercase()
        val defaultMaxTokensField = when {
            modelIdLower.startsWith("gpt-5") -> "max_completion_tokens"
            modelIdLower.startsWith("o1") -> "max_completion_tokens"
            modelIdLower.startsWith("o3") -> "max_completion_tokens"
            modelIdLower.startsWith("gpt-4.1") -> "max_completion_tokens"
            else -> "max_tokens"
        }
        val maxTokensField = model.compat?.maxTokensField ?: defaultMaxTokensField
        json.put(maxTokensField, maxTokens ?: model.maxTokens)

        // Convert message format
        // Defensive: merge all system messages into one at position 0.
        // OpenAI-compatible APIs require system message(s) at the beginning.
        val openaiMessages = JSONArray()
        val systemContents = mutableListOf<String>()
        val nonSystemMessages = mutableListOf<Message>()
        messages.forEach { message ->
            if (message.role == "system") {
                systemContents.add(message.content ?: "")
            } else {
                nonSystemMessages.add(message)
            }
        }
        if (systemContents.isNotEmpty()) {
            val mergedMsg = JSONObject()
            mergedMsg.put("role", "system")
            mergedMsg.put("content", systemContents.joinToString("\n\n"))
            openaiMessages.put(mergedMsg)
        }
        nonSystemMessages.forEach { message ->
            val msg = JSONObject()
            msg.put("role", message.role)

            val hasToolCalls = !message.toolCalls.isNullOrEmpty()
            if (shouldUseNullContentForAssistantToolCall(message)) {
                // OpenAI-compatible tool call turns should send content=null rather than empty string.
                // Some providers reject the following tool result if the preceding assistant tool_calls
                // message used content="", then report: tool result's tool id not found.
                msg.put("content", JSONObject.NULL)
            } else if (message.role == "user" && !message.images.isNullOrEmpty()) {
                // Multimodal: OpenAI vision format with image_url
                val contentArray = JSONArray()
                // Images first
                for (img in message.images!!) {
                    contentArray.put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:${img.mimeType};base64,${img.base64}")
                        })
                    })
                }
                // Then text
                if (message.content.isNotBlank()) {
                    contentArray.put(JSONObject().apply {
                        put("type", "text")
                        put("text", message.content)
                    })
                }
                msg.put("content", contentArray)
            } else {
                msg.put("content", message.content)
            }

            if (hasToolCalls) {
                val toolCallsArray = JSONArray()
                message.toolCalls!!.forEach { toolCall ->
                    toolCallsArray.put(JSONObject().apply {
                        put("id", toolCall.id)
                        put("type", "function")
                        put("function", JSONObject().apply {
                            put("name", toolCall.name)
                            put("arguments", toolCall.arguments)
                        })
                    })
                }
                msg.put("tool_calls", toolCallsArray)
            }

            if (message.toolCallId != null) {
                msg.put("tool_call_id", message.toolCallId)
            }

            openaiMessages.put(msg)
        }

        json.put("messages", openaiMessages)

        // Add tools (use Gson for proper JSON escaping — fixes description with special chars)
        if (!tools.isNullOrEmpty()) {
            val openaiTools = JSONArray()
            tools.forEach { tool ->
                openaiTools.put(buildToolJson(tool))
            }
            json.put("tools", openaiTools)
        }

        // Reasoning support (OpenAI o1/o3 models)
        if (reasoningEnabled && model.reasoning) {
            if (model.compat?.supportsReasoningEffort == true) {
                json.put("reasoning_effort", "medium")
            }
        }

        return json
    }

    private fun parseOpenAIResponse(responseBody: String): ParsedResponse {
        val json = JSONObject(responseBody)

        // Handle API error responses that lack 'choices'
        if (!json.has("choices")) {
            val error = json.optJSONObject("error")
            if (error != null) {
                val msg = error.optString("message", "Unknown API error")
                val code = error.optString("code", "")
                Log.e("ApiAdapter", "API returned error instead of choices: [$code] $msg")
                throw LLMException("API error: $msg")
            }
            // Log raw response for debugging
            val truncated = if (responseBody.length > 500) responseBody.substring(0, 500) + "..." else responseBody
            Log.e("ApiAdapter", "API response missing 'choices': $truncated")
            throw LLMException("API response missing 'choices' field")
        }

        val choices = json.getJSONArray("choices")
        if (choices.length() == 0) {
            return ParsedResponse(content = null)
        }

        val choice = choices.getJSONObject(0)
        val message = choice.getJSONObject("message")

        val content = if (message.isNull("content")) null else message.optString("content")
        val toolCallsArray = message.optJSONArray("tool_calls")
        val toolCalls = if (toolCallsArray != null) {
            mutableListOf<ToolCall>().apply {
                for (i in 0 until toolCallsArray.length()) {
                    val tc = toolCallsArray.getJSONObject(i)
                    val function = tc.getJSONObject("function")
                    add(
                        ToolCall(
                            id = tc.getString("id"),
                            name = function.getString("name"),
                            arguments = function.getString("arguments")
                        )
                    )
                }
            }
        } else null

        // Parse usage
        val usage = json.optJSONObject("usage")?.let {
            Usage(
                promptTokens = it.optInt("prompt_tokens", 0),
                completionTokens = it.optInt("completion_tokens", 0)
            )
        }

        return ParsedResponse(
            content = content,
            toolCalls = toolCalls,
            usage = usage,
            finishReason = choice.optString("finish_reason")
        )
    }

    private fun buildOpenAIResponsesRequest(
        model: ModelDefinition,
        messages: List<Message>,
        tools: List<NewToolDefinition>?,
        temperature: Double,
        maxTokens: Int?,
        reasoningEnabled: Boolean
    ): JSONObject {
        val json = JSONObject()
        json.put("model", model.id)
        json.put("temperature", temperature)
        json.put("max_output_tokens", maxTokens ?: model.maxTokens)

        val input = JSONArray()
        messages.forEach { message ->
            when (message.role) {
                "system" -> {
                    if (message.content.isNotBlank()) {
                        input.put(JSONObject().apply {
                            put("type", "message")
                            put("role", "system")
                            put("content", message.content)
                        })
                    }
                }
                "user" -> {
                    if (message.content.isNotBlank()) {
                        input.put(JSONObject().apply {
                            put("type", "message")
                            put("role", "user")
                            put("content", message.content)
                        })
                    }
                }
                "assistant" -> {
                    if (message.content.isNotBlank()) {
                        input.put(JSONObject().apply {
                            put("type", "message")
                            put("role", "assistant")
                            put("content", message.content)
                        })
                    }
                    buildResponsesFunctionCallItems(message).forEach { input.put(it) }
                }
                "tool" -> {
                    buildResponsesFunctionCallOutputItem(message)?.let { input.put(it) }
                }
            }
        }
        json.put("input", input)

        if (!tools.isNullOrEmpty()) {
            val responsesTools = JSONArray()
            tools.forEach { tool ->
                responsesTools.put(JSONObject().apply {
                    put("type", "function")
                    put("name", tool.function.name)
                    put("description", tool.function.description)
                    put("parameters", buildParametersJson(tool.function.parameters))
                })
            }
            json.put("tools", responsesTools)
        }

        if (reasoningEnabled && model.reasoning && model.compat?.supportsReasoningEffort == true) {
            json.put("reasoning", JSONObject().apply {
                put("effort", "medium")
            })
        }

        return json
    }

    internal data class ResponsesFunctionCallItem(
        val type: String,
        val callId: String,
        val name: String,
        val arguments: String
    )

    internal data class ResponsesFunctionCallOutputItem(
        val type: String,
        val callId: String,
        val output: String
    )

    internal fun buildResponsesFunctionCallItemsSpec(message: Message): List<ResponsesFunctionCallItem> {
        return message.toolCalls?.map { toolCall ->
            ResponsesFunctionCallItem(
                type = "function_call",
                callId = toolCall.id,
                name = toolCall.name,
                arguments = toolCall.arguments
            )
        } ?: emptyList()
    }

    internal fun buildResponsesFunctionCallOutputItemSpec(message: Message): ResponsesFunctionCallOutputItem? {
        if (message.role != "tool" || message.toolCallId.isNullOrBlank()) return null
        return ResponsesFunctionCallOutputItem(
            type = "function_call_output",
            callId = message.toolCallId,
            output = message.content
        )
    }

    internal fun buildResponsesFunctionCallItems(message: Message): List<JSONObject> {
        return buildResponsesFunctionCallItemsSpec(message).map { item ->
            JSONObject().apply {
                put("type", item.type)
                put("call_id", item.callId)
                put("name", item.name)
                put("arguments", item.arguments)
            }
        }
    }

    internal fun buildResponsesFunctionCallOutputItem(message: Message): JSONObject? {
        val item = buildResponsesFunctionCallOutputItemSpec(message) ?: return null
        return JSONObject().apply {
            put("type", item.type)
            put("call_id", item.callId)
            put("output", item.output)
        }
    }

    private fun parseOpenAIResponsesResponse(responseBody: String): ParsedResponse {
        val json = JSONObject(responseBody)

        val error = json.optJSONObject("error")
        if (error != null) {
            val msg = error.optString("message", "Unknown API error")
            throw LLMException("API error: $msg")
        }

        val output = json.optJSONArray("output") ?: JSONArray()
        var content: String? = null
        val toolCalls = mutableListOf<ToolCall>()

        for (i in 0 until output.length()) {
            val item = output.getJSONObject(i)
            when (item.optString("type")) {
                "message" -> {
                    val role = item.optString("role")
                    if (role == "assistant") {
                        val contentArray = item.optJSONArray("content")
                        if (contentArray != null) {
                            val text = buildString {
                                for (j in 0 until contentArray.length()) {
                                    val part = contentArray.getJSONObject(j)
                                    if (part.optString("type") == "output_text") {
                                        append(part.optString("text"))
                                    }
                                }
                            }.trim()
                            if (text.isNotEmpty()) {
                                content = if (content.isNullOrEmpty()) text else content + text
                            }
                        }
                    }
                }
                "function_call" -> {
                    val callId = item.optString("call_id")
                    val name = item.optString("name")
                    val arguments = item.optString("arguments", "{}")
                    if (callId.isNotBlank() && name.isNotBlank()) {
                        toolCalls.add(
                            ToolCall(
                                id = callId,
                                name = name,
                                arguments = arguments
                            )
                        )
                    }
                }
            }
        }

        val usage = json.optJSONObject("usage")?.let {
            Usage(
                promptTokens = it.optInt("input_tokens", 0),
                completionTokens = it.optInt("output_tokens", 0)
            )
        }

        val finishReason = when {
            toolCalls.isNotEmpty() -> "tool_calls"
            else -> json.optString("status")
        }

        return ParsedResponse(
            content = content,
            toolCalls = toolCalls.ifEmpty { null },
            usage = usage,
            finishReason = finishReason
        )
    }

    // ============ Google Gemini API ============

    private fun buildGeminiRequest(
        model: ModelDefinition,
        messages: List<Message>,
        tools: List<NewToolDefinition>?,
        temperature: Double,
        maxTokens: Int?
    ): JSONObject {
        val json = JSONObject()

        // Extract system message → systemInstruction
        val systemMessage = messages
            .filter { it.role == "system" }
            .joinToString("\n") { it.content }
            .takeIf { it.isNotBlank() }
        if (systemMessage != null) {
            json.put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemMessage) })
                })
            })
        }

        // Build contents array (skip system messages)
        val contents = JSONArray()
        messages.filter { it.role != "system" }.forEach { message ->
            val parts = JSONArray()

            when (message.role) {
                "assistant" -> {
                    // Assistant message with tool calls → functionCall parts
                    if (!message.toolCalls.isNullOrEmpty()) {
                        message.toolCalls.forEach { toolCall ->
                            parts.put(JSONObject().apply {
                                put("functionCall", JSONObject().apply {
                                    put("name", toolCall.name)
                                    put("args", JSONObject(toolCall.arguments))
                                })
                            })
                        }
                    }
                    // Text content
                    if (message.content.isNotBlank()) {
                        parts.put(JSONObject().apply { put("text", message.content) })
                    }
                    contents.put(JSONObject().apply {
                        put("role", "model")
                        put("parts", parts)
                    })
                }
                "tool" -> {
                    // Tool result → functionResponse part (role=user in Gemini)
                    val toolName = message.name ?: message.toolCallId ?: "unknown"
                    parts.put(JSONObject().apply {
                        put("functionResponse", JSONObject().apply {
                            put("name", toolName)
                            put("response", JSONObject().apply {
                                put("result", message.content)
                            })
                        })
                    })
                    contents.put(JSONObject().apply {
                        put("role", "user")
                        put("parts", parts)
                    })
                }
                else -> {
                    // User message
                    // Multimodal: add inline images
                    if (!message.images.isNullOrEmpty()) {
                        for (img in message.images) {
                            parts.put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", img.mimeType)
                                    put("data", img.base64)
                                })
                            })
                        }
                    }
                    if (message.content.isNotBlank()) {
                        parts.put(JSONObject().apply { put("text", message.content) })
                    }
                    contents.put(JSONObject().apply {
                        put("role", "user")
                        put("parts", parts)
                    })
                }
            }
        }
        json.put("contents", contents)

        // Tools → function_declarations
        if (!tools.isNullOrEmpty()) {
            val declarations = JSONArray()
            tools.forEach { tool ->
                declarations.put(JSONObject().apply {
                    put("name", tool.function.name)
                    put("description", tool.function.description)
                    put("parameters", buildParametersJson(tool.function.parameters))
                })
            }
            json.put("tools", JSONArray().apply {
                put(JSONObject().apply {
                    put("function_declarations", declarations)
                })
            })
        }

        // Generation config
        json.put("generationConfig", JSONObject().apply {
            put("temperature", temperature)
            put("maxOutputTokens", maxTokens ?: model.maxTokens)
        })

        return json
    }

    private fun parseGeminiResponse(responseBody: String): ParsedResponse {
        val json = JSONObject(responseBody)

        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            return ParsedResponse(content = null)
        }

        val candidate = candidates.getJSONObject(0)
        val content = candidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")

        // Parse all parts: text + functionCall
        val textParts = mutableListOf<String>()
        val toolCalls = mutableListOf<ToolCall>()

        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                // Text part
                part.optString("text", "").takeIf { it.isNotEmpty() }?.let {
                    textParts.add(it)
                }
                // functionCall part
                part.optJSONObject("functionCall")?.let { fc ->
                    toolCalls.add(ToolCall(
                        id = "call_${System.currentTimeMillis()}_$i",
                        name = fc.getString("name"),
                        arguments = fc.optJSONObject("args")?.toString() ?: "{}"
                    ))
                }
            }
        }

        // Parse usage metadata
        val usageMeta = json.optJSONObject("usageMetadata")
        val usage = if (usageMeta != null) {
            Usage(
                promptTokens = usageMeta.optInt("promptTokenCount", 0),
                completionTokens = usageMeta.optInt("candidatesTokenCount", 0)
            )
        } else null

        return ParsedResponse(
            content = textParts.joinToString("").takeIf { it.isNotEmpty() },
            toolCalls = toolCalls.takeIf { it.isNotEmpty() },
            usage = usage,
            finishReason = candidate.optString("finishReason")
        )
    }

    // ============ Ollama API ============

    private fun buildOllamaRequest(
        provider: ProviderConfig,
        model: ModelDefinition,
        messages: List<Message>,
        tools: List<NewToolDefinition>?,
        temperature: Double,
        maxTokens: Int?
    ): JSONObject {
        // Ollama /api/chat uses its own format: model, messages, stream, tools, options
        val json = JSONObject()
        json.put("model", model.id)
        json.put("stream", false)

        val ollamaMessages = JSONArray()
        messages.forEach { message ->
            val msg = JSONObject()
            msg.put("role", message.role)
            msg.put("content", message.content)

            // Ollama multimodal: images as base64 array
            if (message.role == "user" && !message.images.isNullOrEmpty()) {
                msg.put("images", JSONArray().apply {
                    for (img in message.images!!) {
                        put(img.base64)
                    }
                })
            }

            // tool call results use role="tool"
            if (message.toolCallId != null) {
                msg.put("tool_call_id", message.toolCallId)
            }

            // assistant tool_calls
            if (!message.toolCalls.isNullOrEmpty()) {
                val toolCallsArray = JSONArray()
                message.toolCalls.forEach { toolCall ->
                    toolCallsArray.put(JSONObject().apply {
                        put("id", toolCall.id)
                        put("type", "function")
                        put("function", JSONObject().apply {
                            put("name", toolCall.name)
                            put("arguments", JSONObject(toolCall.arguments))
                        })
                    })
                }
                msg.put("tool_calls", toolCallsArray)
            }

            ollamaMessages.put(msg)
        }
        json.put("messages", ollamaMessages)

        // Tools
        if (!tools.isNullOrEmpty()) {
            val ollamaTools = JSONArray()
            tools.forEach { tool ->
                ollamaTools.put(buildToolJson(tool))
            }
            json.put("tools", ollamaTools)
        }

        // Options
        val options = JSONObject()
        options.put("temperature", temperature)
        if (maxTokens != null) {
            options.put("num_predict", maxTokens)
        } else if (model.maxTokens > 0) {
            options.put("num_predict", model.maxTokens)
        }
        if (provider.injectNumCtxForOpenAICompat == true && model.contextWindow > 0) {
            options.put("num_ctx", model.contextWindow)
        }
        json.put("options", options)

        return json
    }

    /**
     * 解析 Ollama /api/chat 响应
     * Ollama 格式: { "model": "...", "message": { "role": "assistant", "content": "...", "tool_calls": [...] }, "done": true }
     */
    private fun parseOllamaResponse(responseBody: String): ParsedResponse {
        val json = JSONObject(responseBody)

        // Check for error
        val error = json.optString("error", "")
        if (error.isNotBlank()) {
            Log.e("ApiAdapter", "Ollama error: $error")
            throw LLMException("Ollama error: $error")
        }

        // Ollama may also support OpenAI-compatible format (if using /v1/chat/completions)
        // Fall back to OpenAI parser if 'choices' field exists
        if (json.has("choices")) {
            return parseOpenAIResponse(responseBody)
        }

        val message = json.optJSONObject("message")
            ?: return ParsedResponse(content = null)

        val content = message.optString("content", "").ifBlank { null }

        // Parse tool calls
        val toolCallsArray = message.optJSONArray("tool_calls")
        val toolCalls = if (toolCallsArray != null && toolCallsArray.length() > 0) {
            mutableListOf<ToolCall>().apply {
                for (i in 0 until toolCallsArray.length()) {
                    val tc = toolCallsArray.getJSONObject(i)
                    val function = tc.optJSONObject("function")
                    if (function != null) {
                        add(
                            ToolCall(
                                id = tc.optString("id", "call_${System.currentTimeMillis()}_$i"),
                                name = function.getString("name"),
                                arguments = function.optJSONObject("arguments")?.toString()
                                    ?: function.optString("arguments", "{}")
                            )
                        )
                    }
                }
            }
        } else null

        // Parse usage (Ollama provides prompt_eval_count / eval_count)
        val promptEval = json.optInt("prompt_eval_count", 0)
        val evalCount = json.optInt("eval_count", 0)
        val usage = if (promptEval > 0 || evalCount > 0) {
            Usage(promptTokens = promptEval, completionTokens = evalCount)
        } else null

        val finishReason = when {
            toolCalls != null && toolCalls.isNotEmpty() -> "tool_calls"
            json.optBoolean("done", false) -> "stop"
            else -> null
        }

        return ParsedResponse(
            content = content,
            toolCalls = toolCalls?.ifEmpty { null },
            usage = usage,
            finishReason = finishReason
        )
    }

    // ============ GitHub Copilot API ============

    private fun buildCopilotRequest(
        model: ModelDefinition,
        messages: List<Message>,
        tools: List<NewToolDefinition>?,
        temperature: Double,
        maxTokens: Int?
    ): JSONObject {
        // GitHub Copilot uses OpenAI compatible format
        return buildOpenAIRequest(model, messages, tools, temperature, maxTokens, false)
    }

    /**
     * Build tool JSON with proper escaping (fixes description with special chars like quotes)
     * Replaces the broken tool.toString() → JSONObject approach
     */
    private fun buildToolJson(tool: NewToolDefinition): JSONObject {
        val json = JSONObject()
        json.put("type", tool.type)

        val funcJson = JSONObject()
        funcJson.put("name", tool.function.name)
        funcJson.put("description", tool.function.description)  // JSONObject.put handles escaping
        val parametersJson = buildParametersJson(tool.function.parameters)
        funcJson.put("parameters", parametersJson)

        json.put("function", funcJson)
        return json
    }

    /**
     * Build parameters schema JSON with proper escaping
     */
    private fun buildParametersJson(params: com.xiaomo.androidforclaw.providers.llm.ParametersSchema): JSONObject {
        val json = JSONObject()
        json.put("type", params.type)

        val propsJson = JSONObject()
        params.properties.forEach { (key, prop) ->
            val propJson = JSONObject()
            propJson.put("type", prop.type)
            propJson.put("description", prop.description)  // Properly escaped
            prop.enum?.let { enumList ->
                val enumArray = JSONArray()
                enumList.forEach { enumArray.put(it) }
                propJson.put("enum", enumArray)
            }
            prop.items?.let { items ->
                val itemsJson = JSONObject()
                itemsJson.put("type", items.type)
                itemsJson.put("description", items.description)
                propJson.put("items", itemsJson)
            }
            prop.properties?.let { nested ->
                val nestedJson = JSONObject()
                nested.forEach { (nk, nv) ->
                    val nvJson = JSONObject()
                    nvJson.put("type", nv.type)
                    nvJson.put("description", nv.description)
                    nestedJson.put(nk, nvJson)
                }
                propJson.put("properties", nestedJson)
            }
            propsJson.put(key, propJson)
        }
        json.put("properties", propsJson)

        if (params.required.isNotEmpty()) {
            val reqArray = JSONArray()
            params.required.forEach { reqArray.put(it) }
            json.put("required", reqArray)
        }

        return json
    }
}

/**
 * 解析后的响应
 */
data class ParsedResponse(
    val content: String?,
    val toolCalls: List<ToolCall>? = null,
    val thinkingContent: String? = null,
    val usage: Usage? = null,
    val finishReason: String? = null
)

/**
 * Tool Call
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

/**
 * Token 使用统计
 */
data class Usage(
    val promptTokens: Int,
    val completionTokens: Int
) {
    val totalTokens: Int get() = promptTokens + completionTokens
}

package com.xiaomo.androidforclaw.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/pi-embedded-runner/(all)
 *
 * AndroidForClaw adaptation: provider auth/header/body request shaping.
 */


import android.util.Log
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
            ModelApi.OPENAI_COMPLETIONS,
            ModelApi.OPENAI_RESPONSES,
            ModelApi.OPENAI_CODEX_RESPONSES -> buildOpenAIRequest(
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
     * 构建请求头
     */
    fun buildHeaders(
        provider: ProviderConfig,
        model: ModelDefinition
    ): Headers {
        val builder = Headers.Builder()

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
     * 解析响应
     */
    fun parseResponse(
        api: String,
        responseBody: String
    ): ParsedResponse {
        return when (api) {
            ModelApi.ANTHROPIC_MESSAGES -> parseAnthropicResponse(responseBody)
            ModelApi.OPENAI_COMPLETIONS,
            ModelApi.OPENAI_RESPONSES,
            ModelApi.OPENAI_CODEX_RESPONSES,
            ModelApi.OLLAMA,
            ModelApi.GITHUB_COPILOT -> parseOpenAIResponse(responseBody)
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
                    msg.put("content", message.content)
                    anthropicMessages.put(msg)
                }
                "tool" -> {
                    // Anthropic 使用 tool_result 格式
                    val msg = JSONObject()
                    msg.put("role", "user")
                    msg.put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "tool_result")
                            put("tool_use_id", message.toolCallId ?: "")
                            put("content", message.content)
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
        val openaiMessages = JSONArray()
        messages.forEach { message ->
            val msg = JSONObject()
            msg.put("role", message.role)
            msg.put("content", message.content)

            if (message.toolCalls != null) {
                val toolCallsArray = JSONArray()
                message.toolCalls.forEach { toolCall ->
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

        val content = if (message.isNull("content")) null else message.optString("content", null)
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

    // ============ Google Gemini API ============

    private fun buildGeminiRequest(
        model: ModelDefinition,
        messages: List<Message>,
        tools: List<NewToolDefinition>?,
        temperature: Double,
        maxTokens: Int?
    ): JSONObject {
        val json = JSONObject()

        // Gemini uses contents array
        val contents = JSONArray()
        messages.filter { it.role != "system" }.forEach { message ->
            val content = JSONObject()
            content.put("role", when (message.role) {
                "assistant" -> "model"
                else -> "user"
            })
            content.put("parts", JSONArray().apply {
                put(JSONObject().apply {
                    put("text", message.content)
                })
            })
            contents.put(content)
        }

        json.put("contents", contents)

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
        val text = parts?.optJSONObject(0)?.optString("text")

        return ParsedResponse(
            content = text,
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
        val json = buildOpenAIRequest(model, messages, tools, temperature, maxTokens, false)

        // Ollama special handling: may need to inject num_ctx
        if (provider.injectNumCtxForOpenAICompat == true) {
            json.put("options", JSONObject().apply {
                put("num_ctx", model.contextWindow)
            })
        }

        return json
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

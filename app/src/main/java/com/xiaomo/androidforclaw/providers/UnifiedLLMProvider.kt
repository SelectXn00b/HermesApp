package com.xiaomo.androidforclaw.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/pi-embedded-runner/(all)
 * - ../openclaw/src/agents/model-(all)
 *
 * AndroidForClaw adaptation: unified provider dispatch for Android.
 */


import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.config.ModelApi
import com.xiaomo.androidforclaw.config.ModelDefinition
import com.xiaomo.androidforclaw.config.ProviderConfig
import com.xiaomo.androidforclaw.providers.llm.Message
import com.xiaomo.androidforclaw.providers.llm.ToolDefinition as NewToolDefinition
import com.xiaomo.androidforclaw.providers.llm.FunctionDefinition as NewFunctionDefinition
import com.xiaomo.androidforclaw.providers.llm.ParametersSchema as NewParametersSchema
import com.xiaomo.androidforclaw.providers.llm.PropertySchema as NewPropertySchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 统一 LLM Provider
 * Supports all OpenClaw compatible API types
 *
 * Features:
 * 1. Automatically load provider and model info from config files
 * 2. Support multiple API formats (OpenAI, Anthropic, Gemini, Ollama, etc.)
 * 3. Use ApiAdapter to handle differences between different APIs
 * 4. Support Extended Thinking / Reasoning
 * 5. Support custom headers and authentication methods
 *
 * Reference: OpenClaw src/agents/llm-client.ts
 */
class UnifiedLLMProvider(private val context: Context) {

    companion object {
        private const val TAG = "UnifiedLLMProvider"
        private const val DEFAULT_TIMEOUT_SECONDS = 120L
        private const val DEFAULT_TEMPERATURE = 0.7
    }

    private val configLoader = ConfigLoader(context)
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * 转换旧的 ToolDefinition 到新格式
     */
    private fun convertToolDefinition(old: ToolDefinition): NewToolDefinition {
        return NewToolDefinition(
            type = old.type,
            function = NewFunctionDefinition(
                name = old.function.name,
                description = old.function.description,
                parameters = NewParametersSchema(
                    type = old.function.parameters.type,
                    properties = old.function.parameters.properties.mapValues { (_, prop) ->
                        convertPropertySchema(prop)
                    },
                    required = old.function.parameters.required
                )
            )
        )
    }

    private fun convertPropertySchema(old: PropertySchema): NewPropertySchema {
        return NewPropertySchema(
            type = old.type,
            description = old.description,
            enum = old.enum,
            items = old.items?.let { convertPropertySchema(it) },
            properties = old.properties?.mapValues { (_, child) -> convertPropertySchema(child) }
        )
    }

    /**
     * 带工具调用的聊天
     *
     * @param messages Message list
     * @param tools Tool definition list (old format)
     * @param modelRef Model reference, format: provider/model-id or just model-id
     * @param temperature Temperature parameter
     * @param maxTokens Maximum generated tokens
     * @param reasoningEnabled Whether to enable reasoning mode
     */
    suspend fun chatWithTools(
        messages: List<Message>,
        tools: List<ToolDefinition>?,
        modelRef: String? = null,
        temperature: Double = DEFAULT_TEMPERATURE,
        maxTokens: Int? = null,
        reasoningEnabled: Boolean = false,
        maxRetries: Int = 3
    ): LLMResponse = withContext(Dispatchers.IO) {
        // Convert tool definitions to new format
        val newTools = tools?.map { convertToolDefinition(it) }

        // Retry logic
        var lastException: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                return@withContext performRequest(
                    messages, newTools, modelRef, temperature, maxTokens, reasoningEnabled
                )
            } catch (e: LLMException) {
                lastException = e

                // Check if retryable
                if (!isRetryable(e) || attempt == maxRetries) {
                    throw e
                }

                // Exponential backoff — longer for rate limit (429)
                val isRateLimit = e.message?.contains("429") == true || e.message?.contains("rate limit", ignoreCase = true) == true
                val baseDelay = if (isRateLimit) 5000L else 1000L  // 5s for 429, 1s for others
                val delayMs = baseDelay * attempt  // 5s, 10s, 15s (429) or 1s, 2s, 3s (others)
                Log.w(TAG, "⚠️ LLM request failed (attempt $attempt/$maxRetries), retrying in ${delayMs}ms: ${e.message}")
                delay(delayMs)
            }
        }

        // Should not reach here
        throw lastException!!
    }

    /**
     * 执行实际的 LLM 请求
     */
    private suspend fun performRequest(
        messages: List<Message>,
        tools: List<com.xiaomo.androidforclaw.providers.llm.ToolDefinition>?,
        modelRef: String?,
        temperature: Double,
        maxTokens: Int?,
        reasoningEnabled: Boolean
    ): LLMResponse {
        try {
            // Parse model reference
            val (providerName, modelId) = parseModelRef(modelRef)

            // Load provider and model config
            val provider = configLoader.getProviderConfig(providerName)
                ?: throw IllegalArgumentException("Provider not found: $providerName")

            val model = provider.models.find { it.id == modelId }
                ?: throw IllegalArgumentException("Model not found: $modelId in provider: $providerName")

            Log.d(TAG, "📡 LLM Request:")
            Log.d(TAG, "  Provider: $providerName")
            Log.d(TAG, "  Model: $modelId")
            Log.d(TAG, "  API: ${model.api ?: provider.api}")
            Log.d(TAG, "  Messages: ${messages.size}")
            Log.d(TAG, "  Tools: ${tools?.size ?: 0}")
            Log.d(TAG, "  Reasoning: $reasoningEnabled")

            // Build request (using converted new format tools)
            val requestBody = ApiAdapter.buildRequestBody(
                provider = provider,
                model = model,
                messages = messages,
                tools = tools,
                temperature = temperature,
                maxTokens = maxTokens,
                reasoningEnabled = reasoningEnabled
            )

            val headers = ApiAdapter.buildHeaders(provider, model)

            // Build complete API endpoint
            val apiUrl = buildApiUrl(provider, model)

            Log.d(TAG, "  URL: $apiUrl")
            Log.d(TAG, "  Headers: ${headers.names()}")
            headers.names().forEach { name ->
                if (name.lowercase() == "authorization") {
                    Log.d(TAG, "    $name: Bearer ${provider.apiKey?.take(10)}...")
                } else {
                    Log.d(TAG, "    $name: ${headers[name]}")
                }
            }

            val finalRequestBody = normalizeOpenAiTokenField(model, requestBody)

            // Send request
            val request = Request.Builder()
                .url(apiUrl)
                .headers(headers)
                .post(finalRequestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            // Log request details for debugging
            val reqStr = finalRequestBody.toString()
            val reqTrunc = if (reqStr.length > 1500) reqStr.substring(0, 1500) + "..." else reqStr
            Log.d(TAG, "📤 Request to $apiUrl: $reqTrunc")

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "❌ API Error (${response.code}): $errorBody")
                throw LLMException("API request failed: ${response.code} - $errorBody")
            }

            val responseBody = response.body?.string()
                ?: throw LLMException("Empty response body")

            Log.d(TAG, "✅ LLM Response received (${responseBody.length} bytes)")

            // Log raw response for debugging (truncated)
            val truncated = if (responseBody.length > 2000) responseBody.substring(0, 2000) + "..." else responseBody
            Log.d(TAG, "📥 Raw response: $truncated")

            // Guard: detect non-JSON responses (HTML pages, login redirects, etc.)
            val trimmed = responseBody.trimStart()
            if (trimmed.startsWith("<") || trimmed.startsWith("<!")) {
                Log.e(TAG, "❌ API returned HTML instead of JSON — check baseUrl and API key")
                throw LLMException(
                    "API returned an HTML page instead of JSON. " +
                    "This usually means the baseUrl is wrong or the API key is invalid. " +
                    "URL: $apiUrl"
                )
            }

            // Parse response
            val api = model.api ?: provider.api
            val parsed = ApiAdapter.parseResponse(api, responseBody)

            return LLMResponse(
                content = parsed.content,
                toolCalls = parsed.toolCalls?.map { tc ->
                    LLMToolCall(
                        id = tc.id,
                        name = tc.name,
                        arguments = tc.arguments
                    )
                },
                thinkingContent = parsed.thinkingContent,
                usage = parsed.usage?.let {
                    LLMUsage(
                        promptTokens = it.promptTokens,
                        completionTokens = it.completionTokens,
                        totalTokens = it.totalTokens
                    )
                },
                finishReason = parsed.finishReason
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ LLM request failed", e)
            throw LLMException("LLM request failed: ${e.message}", e)
        }
    }

    private fun normalizeOpenAiTokenField(model: ModelDefinition, requestBody: JSONObject): JSONObject {
        val modelIdLower = model.id.lowercase()
        val requiresMaxCompletionTokens = modelIdLower.startsWith("gpt-5") ||
            modelIdLower.startsWith("o1") ||
            modelIdLower.startsWith("o3") ||
            modelIdLower.startsWith("gpt-4.1")

        if (!requiresMaxCompletionTokens) return requestBody
        if (requestBody.has("max_tokens")) {
            val value = requestBody.get("max_tokens")
            requestBody.remove("max_tokens")
            if (!requestBody.has("max_completion_tokens")) {
                requestBody.put("max_completion_tokens", value)
            }
        }
        return requestBody
    }

    /**
     * 判断错误是否可重试
     */
    private fun isRetryable(exception: LLMException): Boolean {
        val message = exception.message?.lowercase() ?: ""

        return when {
            // Rate limiting
            message.contains("rate limit") || message.contains("429") -> true
            // Service unavailable
            message.contains("503") || message.contains("service unavailable") -> true
            // Timeout
            message.contains("timeout") || message.contains("timed out") -> true
            // Server errors
            message.contains("500") || message.contains("502") || message.contains("504") -> true
            // Connection issues
            message.contains("connection") || message.contains("network") -> true
            // Overloaded
            message.contains("overloaded") -> true
            // Default: not retryable
            else -> false
        }
    }

    /**
     * 简单聊天（无工具）
     */
    suspend fun simpleChat(
        userMessage: String,
        systemPrompt: String? = null,
        modelRef: String? = null,
        temperature: Double = DEFAULT_TEMPERATURE,
        maxTokens: Int? = null
    ): String {
        val messages = mutableListOf<Message>()

        if (systemPrompt != null) {
            messages.add(Message(role = "system", content = systemPrompt))
        }

        messages.add(Message(role = "user", content = userMessage))

        val response = chatWithTools(
            messages = messages,
            tools = null,
            modelRef = modelRef,
            temperature = temperature,
            maxTokens = maxTokens,
            reasoningEnabled = false
        )

        return response.content ?: throw LLMException("No content in response")
    }

    /**
     * 解析模型引用
     * Format: "provider/model-id" or "model-id"
     *
     * @return Pair(providerName, modelId)
     */
    private fun parseModelRef(modelRef: String?): Pair<String, String> {
        // If not specified, use default model
        if (modelRef == null) {
            val defaultModel = configLoader.loadOpenClawConfig().resolveDefaultModel()
            return parseModelRef(defaultModel)
        }

        // Step 1: Try to find complete modelRef as model ID
        // This supports cases where model ID itself contains "/" (eg "anthropic/claude-opus-4-6")
        val providerForFullId = configLoader.findProviderByModelId(modelRef)
        if (providerForFullId != null) {
            return Pair(providerForFullId, modelRef)
        }

        // Step 2: Parse as "provider/model-id" format
        val parts = modelRef.split("/", limit = 2)
        return when (parts.size) {
            2 -> {
                // "provider/model-id" format (eg "openrouter/anthropic/claude-opus-4-6")
                Pair(parts[0], parts[1])
            }
            1 -> {
                // "model-id" format, find corresponding provider
                val providerName = configLoader.findProviderByModelId(parts[0])
                    ?: throw IllegalArgumentException("Cannot find provider for model: ${parts[0]}")
                Pair(providerName, parts[0])
            }
            else -> throw IllegalArgumentException("Invalid model reference: $modelRef")
        }
    }

    /**
     * 构建 API URL
     */
    private fun buildApiUrl(provider: ProviderConfig, model: ModelDefinition): String {
        val baseUrl = provider.baseUrl.trimEnd('/')
        val api = model.api ?: provider.api

        return when (api) {
            ModelApi.ANTHROPIC_MESSAGES -> {
                "$baseUrl/messages"
            }
            ModelApi.OPENAI_COMPLETIONS -> {
                "$baseUrl/chat/completions"
            }
            ModelApi.OPENAI_RESPONSES,
            ModelApi.OPENAI_CODEX_RESPONSES -> {
                "$baseUrl/responses"
            }
            ModelApi.GOOGLE_GENERATIVE_AI -> {
                "$baseUrl/models/${model.id}:generateContent"
            }
            ModelApi.OLLAMA -> {
                "$baseUrl/api/chat"
            }
            ModelApi.GITHUB_COPILOT -> {
                "$baseUrl/chat/completions"
            }
            ModelApi.BEDROCK_CONVERSE_STREAM -> {
                // AWS Bedrock needs special handling
                "$baseUrl/model/${model.id}/converse-stream"
            }
            else -> {
                // Default to OpenAI compatible endpoint
                "$baseUrl/chat/completions"
            }
        }
    }
}

/**
 * LLM 响应
 */
data class LLMResponse(
    val content: String?,
    val toolCalls: List<LLMToolCall>? = null,
    val thinkingContent: String? = null,
    val usage: LLMUsage? = null,
    val finishReason: String? = null
)

/**
 * LLM Tool Call
 */
data class LLMToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

/**
 * LLM Token 使用统计
 */
data class LLMUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

/**
 * LLM 异常
 */

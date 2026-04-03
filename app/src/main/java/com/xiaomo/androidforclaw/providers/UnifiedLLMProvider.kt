package com.xiaomo.androidforclaw.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/pi-embedded-runner/run/attempt.ts (LLM call: session create, stream, tool dispatch)
 * - ../openclaw/src/agents/pi-embedded-runner/run/payloads.ts (request payload construction)
 * - ../openclaw/src/agents/pi-embedded-payloads.ts (provider-specific payload formatting)
 *
 * Note: pi-embedded-runner.ts is a barrel re-export; actual logic is in pi-embedded-runner/run/attempt.ts etc.
 *
 * AndroidForClaw adaptation: unified provider dispatch for Android (batch + SSE streaming).
 */


import android.content.Context
import com.xiaomo.androidforclaw.logging.Log
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
        .addNetworkInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "AndroidForClaw/${com.xiaomo.androidforclaw.BuildConfig.VERSION_NAME}")
                    .build()
            )
        }
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

        // Parse primary model reference
        val (primaryProvider, primaryModel) = parseModelRef(modelRef)

        // Use model fallback chain (OpenClaw model-fallback.ts)
        val config = configLoader.loadOpenClawConfig()
        val fallbackResult = ModelFallback.runWithModelFallback(
            config = config,
            configLoader = configLoader,
            provider = primaryProvider,
            model = primaryModel,
            run = { provider, model ->
                performRequestForModel(
                    messages, newTools, provider, model, temperature, maxTokens, reasoningEnabled, maxRetries
                )
            },
            onError = { provider, model, error, attempt, total ->
                Log.w(TAG, "⚠️ Fallback attempt $attempt/$total failed for $provider/$model: ${error.message}")
            }
        )

        return@withContext fallbackResult.result
    }

    /**
     * 流式聊天 — 返回 Flow<StreamChunk>，实时 emit SSE 增量
     * 对齐 OpenClaw streamSimple / pi-embedded-subscribe.handlers.messages.ts
     */
    fun chatWithToolsStreaming(
        messages: List<Message>,
        tools: List<ToolDefinition>?,
        modelRef: String? = null,
        temperature: Double = DEFAULT_TEMPERATURE,
        maxTokens: Int? = null,
        reasoningEnabled: Boolean = false,
        maxRetries: Int = 3
    ): Flow<StreamChunk> = flow {
        val newTools = tools?.map { convertToolDefinition(it) }
        val (resolvedProviderName, resolvedModelId) = parseModelRef(modelRef)
        val config = configLoader.loadOpenClawConfig()

        // === Layer 1: Model Fallback ===
        val candidates = ModelFallback.resolveFallbackCandidates(
            config, configLoader, resolvedProviderName, resolvedModelId, null
        )
        var lastException: Exception? = null

        for (candidate in candidates) {
            try {
                // Resolve candidate provider/model config
                val aliasResolved = configLoader.resolveModelId(candidate.model)
                val normalizedModelId = ModelIdNormalization.normalizeModelId(candidate.provider, aliasResolved)
                val providerRaw = configLoader.getProviderConfig(candidate.provider)
                    ?: throw LLMException("Provider not found: ${candidate.provider}")
                val modelRaw = providerRaw.models.find { it.id == normalizedModelId }
                    ?: providerRaw.models.find { it.id == candidate.model }
                    ?: throw LLMException("Model not found: $normalizedModelId in provider: ${candidate.provider}")
                val (provider, model) = ModelCompat.normalizeModelCompat(providerRaw, modelRaw, candidate.provider)
                val api = model.api ?: provider.api

                // Non-streaming APIs → batch fallback (already has full retry/rotation via performRequestForModel)
                if (api == ModelApi.GOOGLE_GENERATIVE_AI || api == ModelApi.OPENAI_RESPONSES || api == ModelApi.OPENAI_CODEX_RESPONSES) {
                    Log.d(TAG, "⚠️ API $api does not support streaming, falling back to batch")
                    val batchResponse = performRequestForModel(
                        messages, newTools, candidate.provider, candidate.model,
                        temperature, maxTokens, reasoningEnabled, maxRetries
                    )
                    batchResponse.thinkingContent?.let { emit(StreamChunk(type = ChunkType.THINKING_DELTA, text = it)) }
                    batchResponse.content?.let { emit(StreamChunk(type = ChunkType.TEXT_DELTA, text = it)) }
                    emit(StreamChunk(type = ChunkType.DONE, finishReason = batchResponse.finishReason))
                    return@flow
                }

                val apiKeys = ApiKeyRotation.splitApiKeys(provider.apiKey)

                // === Layer 2: Retry with Backoff ===
                for (attempt in 1..maxRetries) {
                    try {
                        // === Layer 3: API Key Rotation ===
                        var keyException: Exception? = null
                        for ((keyIdx, apiKey) in apiKeys.withIndex()) {
                            try {
                                val activeProvider = provider.copy(apiKey = apiKey)

                                val requestBody = ApiAdapter.buildRequestBody(
                                    provider = activeProvider, model = model,
                                    messages = messages, tools = newTools,
                                    temperature = temperature, maxTokens = maxTokens,
                                    reasoningEnabled = reasoningEnabled, stream = true
                                )
                                val headers = ApiAdapter.buildHeaders(activeProvider, model)
                                val apiUrl = buildApiUrl(activeProvider, model)
                                val finalRequestBody = normalizeOpenAiTokenField(model, requestBody)

                                Log.d(TAG, "📤 Streaming request to $apiUrl (candidate=${candidate.provider}/${candidate.model}, attempt=$attempt, key=${keyIdx + 1}/${apiKeys.size})")

                                val request = Request.Builder()
                                    .url(apiUrl)
                                    .headers(headers)
                                    .post(finalRequestBody.toString().toRequestBody("application/json".toMediaType()))
                                    .build()

                                val response = httpClient.newCall(request).execute()

                                if (!response.isSuccessful) {
                                    val errorBody = response.body?.string() ?: "Unknown error"
                                    response.close()
                                    throw LLMException("Streaming API request failed: ${response.code} - $errorBody")
                                }

                                // === Connection established — stream SSE chunks ===
                                val source = response.body?.source()
                                    ?: throw LLMException("Empty streaming response body")

                                try {
                                    var currentEventType: String? = null
                                    val isAnthropic = api == ModelApi.ANTHROPIC_MESSAGES

                                    while (!source.exhausted()) {
                                        val line = source.readUtf8Line() ?: break

                                        if (line.startsWith("event: ")) {
                                            currentEventType = line.removePrefix("event: ").trim()
                                            continue
                                        }

                                        if (line.startsWith("data: ")) {
                                            val data = line.removePrefix("data: ").trim()
                                            if (data == "[DONE]") {
                                                emit(StreamChunk(type = ChunkType.DONE))
                                                break
                                            }
                                            if (data.isEmpty()) continue

                                            val chunk = ApiAdapter.parseStreamChunk(
                                                api = api,
                                                eventType = if (isAnthropic) currentEventType else null,
                                                dataLine = data
                                            )
                                            if (chunk != null && chunk.type != ChunkType.PING) {
                                                emit(chunk)
                                            }
                                            currentEventType = null
                                            continue
                                        }
                                    }
                                } finally {
                                    source.close()
                                    response.close()
                                }

                                // Streaming completed successfully
                                return@flow

                            } catch (e: Exception) {
                                keyException = e
                                if (!ApiKeyRotation.isApiKeyRateLimitError(e) || keyIdx + 1 >= apiKeys.size) throw e
                                Log.w(TAG, "⚠️ Streaming: key #${keyIdx + 1} rate limited, rotating to next key")
                            }
                        }
                        throw keyException!!

                    } catch (e: LLMException) {
                        if (!isRetryable(e) || attempt == maxRetries) throw e
                        val isRateLimit = e.message?.lowercase()?.let {
                            it.contains("429") || it.contains("rate limit")
                        } == true
                        val baseDelay = if (isRateLimit) 5000L else 1000L
                        val delayMs = baseDelay * attempt
                        Log.w(TAG, "⚠️ Streaming retry $attempt/$maxRetries in ${delayMs}ms: ${e.message}")
                        delay(delayMs)
                    }
                }

            } catch (e: Exception) {
                lastException = e
                if (ModelFallback.isLikelyContextOverflowError(e)) throw e
                if (!ModelFallback.isRetryableForFallback(e)) throw e
                Log.w(TAG, "⚠️ Streaming fallback: ${candidate.provider}/${candidate.model} failed: ${e.message}, trying next candidate")
            }
        }

        throw lastException ?: LLMException("All streaming models failed")
    }.flowOn(Dispatchers.IO)

    /**
     * Execute LLM request for a specific provider/model with retry and API key rotation.
     * Called by the fallback chain for each candidate.
     */
    private suspend fun performRequestForModel(
        messages: List<Message>,
        tools: List<com.xiaomo.androidforclaw.providers.llm.ToolDefinition>?,
        providerName: String,
        modelId: String,
        temperature: Double,
        maxTokens: Int?,
        reasoningEnabled: Boolean,
        maxRetries: Int
    ): LLMResponse {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                return performRequest(messages, tools, providerName, modelId, temperature, maxTokens, reasoningEnabled)
            } catch (e: LLMException) {
                lastException = e
                if (!isRetryable(e) || attempt == maxRetries) throw e
                val isRateLimit = e.message?.contains("429") == true || e.message?.contains("rate limit", ignoreCase = true) == true
                val baseDelay = if (isRateLimit) 5000L else 1000L
                val delayMs = baseDelay * attempt
                Log.w(TAG, "⚠️ LLM request failed (attempt $attempt/$maxRetries), retrying in ${delayMs}ms: ${e.message}")
                delay(delayMs)
            }
        }
        throw lastException!!
    }

    /**
     * 执行实际的 LLM 请求
     */
    private suspend fun performRequest(
        messages: List<Message>,
        tools: List<com.xiaomo.androidforclaw.providers.llm.ToolDefinition>?,
        providerName: String,
        modelId: String,
        temperature: Double,
        maxTokens: Int?,
        reasoningEnabled: Boolean
    ): LLMResponse {
        try {
            // Resolve model aliases (OpenClaw model-selection.ts)
            val aliasResolved = configLoader.resolveModelId(modelId)

            // Model allowlist check (OpenClaw model-selection.ts)
            val config = configLoader.loadOpenClawConfig()
            if (!com.xiaomo.androidforclaw.config.ModelAllowlist.isModelAllowed(aliasResolved, config.modelAllowlist)) {
                throw LLMException("Model '$aliasResolved' is not allowed by the model allowlist configuration")
            }

            // Normalize model ID per provider (OpenClaw model-id-normalization.ts)
            val normalizedModelId = ModelIdNormalization.normalizeModelId(providerName, aliasResolved)

            // Load provider and model config
            val providerRaw = configLoader.getProviderConfig(providerName)
                ?: throw IllegalArgumentException("Provider not found: $providerName")

            val modelRaw = providerRaw.models.find { it.id == normalizedModelId }
                ?: providerRaw.models.find { it.id == modelId }  // fallback to original ID
                ?: throw IllegalArgumentException("Model not found: $normalizedModelId in provider: $providerName")

            // Apply model compat normalization (OpenClaw model-compat.ts)
            val (provider, model) = ModelCompat.normalizeModelCompat(providerRaw, modelRaw, providerName)

            Log.d(TAG, "📡 LLM Request:")
            Log.d(TAG, "  Provider: $providerName")
            Log.d(TAG, "  Model: ${model.id}${if (model.id != modelId) " (normalized from $modelId)" else ""}")
            Log.d(TAG, "  API: ${model.api ?: provider.api}")
            Log.d(TAG, "  Messages: ${messages.size}")
            Log.d(TAG, "  Tools: ${tools?.size ?: 0}")
            Log.d(TAG, "  Reasoning: $reasoningEnabled")

            // API key rotation (OpenClaw api-key-rotation.ts)
            // Split comma-separated keys and try each on rate limit
            val apiKeys = ApiKeyRotation.splitApiKeys(provider.apiKey)

            val responseBody = if (apiKeys.size > 1) {
                ApiKeyRotation.executeWithApiKeyRotation(
                    apiKeys = apiKeys,
                    provider = providerName,
                    execute = { apiKey ->
                        executeHttpRequest(provider.copy(apiKey = apiKey), model, messages, tools, temperature, maxTokens, reasoningEnabled)
                    }
                )
            } else {
                executeHttpRequest(provider, model, messages, tools, temperature, maxTokens, reasoningEnabled)
            }

            Log.d(TAG, "✅ LLM Response received (${responseBody.length} bytes)")

            // Log raw response for debugging (truncated)
            val truncated = if (responseBody.length > 2000) responseBody.substring(0, 2000) + "..." else responseBody
            Log.d(TAG, "📥 Raw response: $truncated")

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

    /**
     * Execute the HTTP request to the LLM API and return the raw response body.
     * Extracted to support API key rotation.
     */
    private fun executeHttpRequest(
        provider: ProviderConfig,
        model: ModelDefinition,
        messages: List<Message>,
        tools: List<com.xiaomo.androidforclaw.providers.llm.ToolDefinition>?,
        temperature: Double,
        maxTokens: Int?,
        reasoningEnabled: Boolean
    ): String {
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

        val request = Request.Builder()
            .url(apiUrl)
            .headers(headers)
            .post(finalRequestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

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

        return responseBody
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
            val config = configLoader.loadOpenClawConfigFresh() // 强制从磁盘读取，避免缓存导致换模型不生效
            val defaultModel = config.resolveDefaultModel()
            // If the default model's provider exists, use it
            val parsed = tryParseModelRef(defaultModel)
            if (parsed != null) return parsed

            // Fallback: use the first available provider/model
            val providers = config.resolveProviders()
            val firstEntry = providers.entries.firstOrNull()
            if (firstEntry != null) {
                val firstModel = firstEntry.value.models.firstOrNull()
                if (firstModel != null) {
                    Log.w(TAG, "⚠️ 默认模型 '$defaultModel' 的 provider 不存在，fallback 到 '${firstEntry.key}/${firstModel.id}'")
                    return Pair(firstEntry.key, firstModel.id)
                }
            }
            throw IllegalArgumentException("没有可用的模型配置，请先配置模型")
        }

        return tryParseModelRef(modelRef)
            ?: throw IllegalArgumentException("Invalid model reference: $modelRef")
    }

    /**
     * 尝试解析模型引用，找不到时返回 null 而不是抛异常
     */
    private fun tryParseModelRef(modelRef: String): Pair<String, String>? {
        // Step 1: Try to find complete modelRef as model ID
        val providerForFullId = configLoader.findProviderByModelId(modelRef)
        if (providerForFullId != null) {
            return Pair(providerForFullId, modelRef)
        }

        // Step 2: Parse as "provider/model-id" format
        val parts = modelRef.split("/", limit = 2)
        return when (parts.size) {
            2 -> {
                // Verify provider exists
                val providerConfig = configLoader.getProviderConfig(parts[0])
                if (providerConfig != null) Pair(parts[0], parts[1]) else null
            }
            1 -> {
                // "model-id" format, find corresponding provider
                val providerName = configLoader.findProviderByModelId(parts[0])
                if (providerName != null) Pair(providerName, parts[0]) else null
            }
            else -> null
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
                "$baseUrl/v1/messages"
            }
            ModelApi.OPENAI_COMPLETIONS -> {
                "$baseUrl/chat/completions"
            }
            ModelApi.OPENAI_RESPONSES,
            ModelApi.OPENAI_CODEX_RESPONSES -> {
                "$baseUrl/responses"
            }
            ModelApi.GOOGLE_GENERATIVE_AI -> {
                val keyParam = if (provider.apiKey != null) "?key=${provider.apiKey}" else ""
                "$baseUrl/models/${model.id}:generateContent$keyParam"
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


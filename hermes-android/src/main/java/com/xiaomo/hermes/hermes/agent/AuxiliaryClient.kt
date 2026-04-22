package com.xiaomo.hermes.hermes.agent

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Auxiliary Client - 核心 provider 链 + failover
 * 1:1 对齐 hermes/agent/auxiliary_client.py
 *
 * 管理多个 provider 的 API 调用，支持 failover、credential 轮转、
 * 流式响应等。
 */

// ── Data Classes ─────────────────────────────────────────────────────────

data class ProviderConfig(
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val apiMode: String = "chat",  // "chat", "responses", "anthropic"
    val model: String = "",
    val maxTokens: Int = 4096,
    val temperature: Double? = null,
    val headers: Map<String, String> = emptyMap(),
    val priority: Int = 0,  // 优先级，数字越小优先级越高
    val enabled: Boolean = true,
    val credentialPool: CredentialPool? = null,
    val retryConfig: RetryConfig = RetryConfig(),
    val extraParams: Map<String, Any> = emptyMap()
)

data class RetryConfig(
    val maxRetries: Int = 3,
    val baseDelayMs: Long = 1000L,
    val maxDelayMs: Long = 60000L,
    val retryOnCodes: Set<Int> = setOf(429, 500, 502, 503, 529)
)

data class ChatRequest(
    val messages: List<Map<String, Any>>,
    val model: String = "",
    val systemPrompt: String = "",
    val tools: List<Map<String, Any>> = emptyList(),
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    val stream: Boolean = false,
    val metadata: Map<String, Any>? = null
)

data class ChatResponse(
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val model: String = "",
    val provider: String = "",
    val usage: TokenUsage? = null,
    val finishReason: String = "",
    val raw: Map<String, Any>? = null
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String  // JSON string
)

data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int = inputTokens + outputTokens,
    val cacheReadTokens: Int = 0,
    val cacheWriteTokens: Int = 0
)

data class FailoverResult(
    val response: ChatResponse?,
    val providerUsed: String,
    val attempts: Int,
    val errors: List<FailoverError> = emptyList(),
    val success: Boolean = response != null
)

data class FailoverError(
    val provider: String,
    val errorType: String,
    val message: String,
    val statusCode: Int? = null
)

data class StreamChunk(
    val content: String = "",
    val toolCall: ToolCall? = null,
    val finishReason: String? = null,
    val usage: TokenUsage? = null,
    val done: Boolean = false
)

// ── Provider Chain ───────────────────────────────────────────────────────

class ProviderChain(
    private val providers: List<ProviderConfig>
) {

    /**
     * 获取按优先级排序的可用 provider 列表
     *
     * @param excludeNames 排除的 provider 名称
     * @return 排序后的 provider 列表
     */
    fun getAvailableProviders(excludeNames: Set<String> = emptySet()): List<ProviderConfig> {
        return providers
            .filter { it.enabled && it.name !in excludeNames }
            .sortedBy { it.priority }
    }

    /**
     * 获取下一个可用的 provider
     *
     * @param currentName 当前 provider 名称
     * @return 下一个 provider，无可用返回 null
     */
    fun getNextProvider(currentName: String): ProviderConfig? {
        val excluded = mutableSetOf(currentName)
        return getAvailableProviders(excluded).firstOrNull()
    }
}

// ── Request Builder ──────────────────────────────────────────────────────

class RequestBuilder(
    private val gson: Gson = Gson()
) {

    /**
     * 构建 OpenAI 兼容格式的请求体
     */
    fun buildOpenAIRequestBody(
        request: ChatRequest,
        provider: ProviderConfig
    ): Map<String, Any> {
        val body = mutableMapOf<String, Any>(
            "model" to (request.model.ifEmpty { provider.model }),
            "messages" to buildMessages(request)
        )

        val maxTokens = request.maxTokens ?: provider.maxTokens
        body["max_tokens"] = maxTokens

        val temperature = request.temperature ?: provider.temperature
        if (temperature != null) {
            body["temperature"] = temperature
        }

        if (request.tools.isNotEmpty()) {
            body["tools"] = request.tools.map { tool ->
                mapOf(
                    "type" to "function",
                    "function" to tool
                )
            }
        }

        if (request.stream) {
            body["stream"] = true
        }

        // 添加 provider 额外参数
        body.putAll(provider.extraParams)

        return body
    }

    /**
     * 构建 Anthropic 格式的请求体
     */
    fun buildAnthropicRequestBody(
        request: ChatRequest,
        provider: ProviderConfig
    ): Map<String, Any> {
        val body = mutableMapOf<String, Any>(
            "model" to (request.model.ifEmpty { provider.model }),
            "max_tokens" to (request.maxTokens ?: provider.maxTokens),
            "messages" to buildAnthropicMessages(request)
        )

        if (request.systemPrompt.isNotEmpty()) {
            body["system"] = listOf(
                mapOf("type" to "text", "text" to request.systemPrompt, "cache_control" to mapOf("type" to "ephemeral"))
            )
        }

        if (request.tools.isNotEmpty()) {
            body["tools"] = request.tools.map { tool ->
                mapOf(
                    "name" to (tool["name"] ?: ""),
                    "description" to (tool["description"] ?: ""),
                    "input_schema" to (tool["parameters"] ?: emptyMap<String, Any>())
                )
            }
        }

        if (request.stream) {
            body["stream"] = true
        }

        val temperature = request.temperature ?: provider.temperature
        if (temperature != null) {
            body["temperature"] = temperature
        }

        return body
    }

    private fun buildMessages(request: ChatRequest): List<Map<String, Any>> {
        val messages = mutableListOf<Map<String, Any>>()

        if (request.systemPrompt.isNotEmpty()) {
            messages.add(mapOf("role" to "system", "content" to request.systemPrompt))
        }

        messages.addAll(request.messages)

        return messages
    }

    private fun buildAnthropicMessages(request: ChatRequest): List<Map<String, Any>> {
        val messages = mutableListOf<Map<String, Any>>()

        for (msg in request.messages) {
            val role = msg["role"] as? String ?: continue
            if (role == "system") continue // Anthropic system 是顶层参数
            messages.add(msg)
        }

        return messages
    }
}

// ── Response Parser ──────────────────────────────────────────────────────

class ResponseParser(
    private val gson: Gson = Gson()
) {

    /**
     * 解析 OpenAI 兼容格式的响应
     */
    fun parseOpenAIResponse(json: Map<String, Any>, provider: String): ChatResponse {
        val choices = json["choices"] as? List<*>
        val choice = choices?.firstOrNull() as? Map<*, *>
        val message = choice?.get("message") as? Map<*, *>

        val content = message?.get("content") as? String ?: ""
        val finishReason = choice?.get("finish_reason") as? String ?: ""

        val toolCalls = mutableListOf<ToolCall>()
        val rawToolCalls = message?.get("tool_calls") as? List<*>
        if (rawToolCalls != null) {
            for (tc in rawToolCalls) {
                if (tc is Map<*, *>) {
                    val function = tc["function"] as? Map<*, *>
                    toolCalls.add(
                        ToolCall(
                            id = tc["id"] as? String ?: "",
                            name = function?.get("name") as? String ?: "",
                            arguments = function?.get("arguments") as? String ?: "{}"
                        )
                    )
                }
            }
        }

        val usage = parseOpenAIUsage(json["usage"])

        return ChatResponse(
            content = content,
            toolCalls = toolCalls,
            model = json["model"] as? String ?: "",
            provider = provider,
            usage = usage,
            finishReason = finishReason,
            raw = json
        )
    }

    /**
     * 解析 Anthropic 格式的响应
     */
    fun parseAnthropicResponse(json: Map<String, Any>, provider: String): ChatResponse {
        val content = mutableListOf<String>()
        val toolCalls = mutableListOf<ToolCall>()

        val contentBlocks = json["content"] as? List<*>
        if (contentBlocks != null) {
            for (block in contentBlocks) {
                if (block is Map<*, *>) {
                    when (block["type"]) {
                        "text" -> content.add(block["text"] as? String ?: "")
                        "tool_use" -> {
                            val input = block["input"]
                            val inputJson = if (input is Map<*, *>) gson.toJson(input) else "{}"
                            toolCalls.add(
                                ToolCall(
                                    id = block["id"] as? String ?: "",
                                    name = block["name"] as? String ?: "",
                                    arguments = inputJson
                                )
                            )
                        }
                    }
                }
            }
        }

        val usageJson = json["usage"] as? Map<*, *>
        val usage = if (usageJson != null) {
            TokenUsage(
                inputTokens = (usageJson["input_tokens"] as? Number)?.toInt() ?: 0,
                outputTokens = (usageJson["output_tokens"] as? Number)?.toInt() ?: 0,
                cacheReadTokens = (usageJson["cache_read_input_tokens"] as? Number)?.toInt() ?: 0,
                cacheWriteTokens = (usageJson["cache_creation_input_tokens"] as? Number)?.toInt() ?: 0
            )
        } else null

        return ChatResponse(
            content = content.joinToString(""),
            toolCalls = toolCalls,
            model = json["model"] as? String ?: "",
            provider = provider,
            usage = usage,
            finishReason = json["stop_reason"] as? String ?: "",
            raw = json
        )
    }

    private fun parseOpenAIUsage(usage: Any?): TokenUsage? {
        if (usage !is Map<*, *>) return null
        return TokenUsage(
            inputTokens = (usage["prompt_tokens"] as? Number)?.toInt() ?: 0,
            outputTokens = (usage["completion_tokens"] as? Number)?.toInt() ?: 0,
            totalTokens = (usage["total_tokens"] as? Number)?.toInt() ?: 0
        )
    }
}

// ── Stream Parser ────────────────────────────────────────────────────────

class StreamParser(
    private val gson: Gson = Gson()
) {

    /**
     * 解析 OpenAI SSE 流式响应
     *
     * @param line SSE 行（不含 "data: " 前缀）
     * @return 流式数据块
     */
    fun parseOpenAIStreamLine(line: String): StreamChunk? {
        if (line == "[DONE]") return StreamChunk(done = true)
        if (line.isBlank()) return null

        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val json: Map<String, Any> = gson.fromJson(line, type)

            val choices = json["choices"] as? List<*>
            val choice = choices?.firstOrNull() as? Map<*, *>
            val delta = choice?.get("delta") as? Map<*, *>

            val content = delta?.get("content") as? String ?: ""
            val finishReason = choice?.get("finish_reason") as? String

            var toolCall: ToolCall? = null
            val rawToolCalls = delta?.get("tool_calls") as? List<*>
            if (rawToolCalls != null && rawToolCalls.isNotEmpty()) {
                val tc = rawToolCalls.first() as? Map<*, *>
                val function = tc?.get("function") as? Map<*, *>
                toolCall = ToolCall(
                    id = tc?.get("id") as? String ?: "",
                    name = function?.get("name") as? String ?: "",
                    arguments = function?.get("arguments") as? String ?: ""
                )
            }

            val usage = if (json.containsKey("usage")) parseOpenAIUsage(json["usage"]) else null

            StreamChunk(
                content = content,
                toolCall = toolCall,
                finishReason = finishReason,
                usage = usage,
                done = finishReason != null
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析 Anthropic SSE 流式响应
     *
     * @param eventType 事件类型
     * @param data 事件数据 JSON
     * @return 流式数据块
     */
    fun parseAnthropicStreamLine(eventType: String, data: String): StreamChunk? {
        if (data.isBlank()) return null

        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val json: Map<String, Any> = gson.fromJson(data, type)

            when (eventType) {
                "content_block_delta" -> {
                    val delta = json["delta"] as? Map<*, *>
                    val text = delta?.get("text") as? String
                    if (text != null) {
                        StreamChunk(content = text)
                    } else {
                        // tool_use delta
                        val partialJson = delta?.get("partial_json") as? String
                        StreamChunk(
                            toolCall = ToolCall(id = "", name = "", arguments = partialJson ?: "")
                        )
                    }
                }
                "message_delta" -> {
                    val delta = json["delta"] as? Map<*, *>
                    val stopReason = delta?.get("stop_reason") as? String
                    StreamChunk(finishReason = stopReason, done = stopReason != null)
                }
                "message_start" -> null
                "content_block_start" -> null
                "content_block_stop" -> null
                "message_stop" -> StreamChunk(done = true)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseOpenAIUsage(usage: Any?): TokenUsage? {
        if (usage !is Map<*, *>) return null
        return TokenUsage(
            inputTokens = (usage["prompt_tokens"] as? Number)?.toInt() ?: 0,
            outputTokens = (usage["completion_tokens"] as? Number)?.toInt() ?: 0,
            totalTokens = (usage["total_tokens"] as? Number)?.toInt() ?: 0
        )
    }
}

// ── Main Auxiliary Client ────────────────────────────────────────────────

class AuxiliaryClient(
    private val providerChain: ProviderChain,
    private val config: AuxiliaryClientConfig = AuxiliaryClientConfig()
) {

    data class AuxiliaryClientConfig(
        val defaultMaxRetries: Int = 3,
        val defaultTimeoutMs: Long = 120_000L,
        val enableFailover: Boolean = true,
        val enableStreaming: Boolean = true,
        val enableContextCompression: Boolean = true
    )

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val requestBuilder = RequestBuilder(gson)
    private val responseParser = ResponseParser(gson)
    private val streamParser = StreamParser(gson)
    private val compressor = ContextCompressor()
    private val rateLimitTracker = RateLimitTracker()
    private val insights = Insights()

    /**
     * 发送聊天请求（带 failover）
     *
     * @param request 聊天请求
     * @param preferredProvider 首选 provider（可选）
     * @return failover 结果
     */
    fun chat(
        request: ChatRequest,
        preferredProvider: String = ""
    ): FailoverResult {
        val errors = mutableListOf<FailoverError>()
        val excludedProviders = mutableSetOf<String>()

        // 确定 provider 顺序
        var providers = providerChain.getAvailableProviders(excludedProviders)
        if (preferredProvider.isNotEmpty()) {
            val preferred = providers.find { it.name == preferredProvider }
            if (preferred != null) {
                providers = listOf(preferred) + providers.filter { it.name != preferredProvider }
            }
        }

        for (provider in providers) {
            if (!config.enableFailover && errors.isNotEmpty()) break

            for (attempt in 0 until provider.retryConfig.maxRetries) {
                try {
                    val response = executeRequest(request, provider)
                    rateLimitTracker.recordRequest(provider.name)
                    insights.record(
                        UsageEntry(
                            provider = provider.name,
                            model = response.model,
                            inputTokens = response.usage?.inputTokens ?: 0,
                            outputTokens = response.usage?.outputTokens ?: 0,
                            success = true
                        )
                    )
                    return FailoverResult(
                        response = response,
                        providerUsed = provider.name,
                        attempts = attempt + 1,
                        errors = errors
                    )
                } catch (e: Exception) {
                    val classified = classifyApiError(e, provider = provider.name)
                    val statusCode = extractStatusCode(e)

                    errors.add(
                        FailoverError(
                            provider = provider.name,
                            errorType = classified.reason.value,
                            message = e.message ?: "Unknown error",
                            statusCode = statusCode
                        )
                    )

                    insights.record(
                        UsageEntry(
                            provider = provider.name,
                            model = request.model,
                            success = false,
                            errorType = classified.reason.value
                        )
                    )

                    // 判断是否需要 failover
                    if (classified.shouldFallback) {
                        excludedProviders.add(provider.name)
                        break // 切换到下一个 provider
                    }

                    // 可重试的错误
                    if (classified.retryable) {
                        val delay = 2000L * (attempt + 1)
                        Thread.sleep(delay)
                        continue // 重试
                    }

                    // 不可重试的错误
                    break
                }
            }
        }

        return FailoverResult(
            response = null,
            providerUsed = "",
            attempts = errors.size,
            errors = errors
        )
    }

    /**
     * 发送流式聊天请求
     *
     * @param request 聊天请求
     * @param provider provider 配置
     * @param onChunk 接收每个 chunk 的回调
     */
    fun chatStream(
        request: ChatRequest,
        provider: ProviderConfig,
        onChunk: (StreamChunk) -> Unit
    ) {
        val streamRequest = request.copy(stream = true)
        val body = when (provider.apiMode) {
            "anthropic" -> requestBuilder.buildAnthropicRequestBody(streamRequest, provider)
            else -> requestBuilder.buildOpenAIRequestBody(streamRequest, provider)
        }

        val url = buildUrl(provider)
        val headers = buildHeaders(provider)
        val requestBody = gson.toJson(body).toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
        for ((key, value) in headers) {
            requestBuilder.addHeader(key, value)
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.body?.string()}")
            }

            val inputStream = response.body?.byteStream() ?: return
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.isBlank()) continue

                when (provider.apiMode) {
                    "anthropic" -> {
                        if (l.startsWith("event: ")) {
                            val eventType = l.removePrefix("event: ")
                            val dataLine = reader.readLine() ?: continue
                            val data = dataLine.removePrefix("data: ")
                            val chunk = streamParser.parseAnthropicStreamLine(eventType, data)
                            if (chunk != null) {
                                onChunk(chunk)
                                if (chunk.done) return
                            }
                        }
                    }
                    else -> {
                        if (l.startsWith("data: ")) {
                            val data = l.removePrefix("data: ")
                            val chunk = streamParser.parseOpenAIStreamLine(data)
                            if (chunk != null) {
                                onChunk(chunk)
                                if (chunk.done) return
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 执行单个 API 请求
     */
    private fun executeRequest(request: ChatRequest, provider: ProviderConfig): ChatResponse {
        // 获取 API key
        val apiKey = provider.credentialPool?.getKey(provider.name) ?: provider.apiKey

        val body = when (provider.apiMode) {
            "anthropic" -> requestBuilder.buildAnthropicRequestBody(request, provider)
            else -> requestBuilder.buildOpenAIRequestBody(request, provider)
        }

        val url = buildUrl(provider)
        val headers = buildHeaders(provider, apiKey)
        val requestBody = gson.toJson(body).toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBody)
        for ((key, value) in headers) {
            httpRequest.addHeader(key, value)
        }

        httpClient.newCall(httpRequest.build()).execute().use { response ->
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorJson = try {
                    gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
                } catch (e: Exception) {
                    mapOf("error" to mapOf("message" to responseBody))
                }
                throw ApiException(response.code, responseBody, errorJson)
            }

            val type = object : TypeToken<Map<String, Any>>() {}.type
            val json: Map<String, Any> = gson.fromJson(responseBody, type)

            return when (provider.apiMode) {
                "anthropic" -> responseParser.parseAnthropicResponse(json, provider.name)
                else -> responseParser.parseOpenAIResponse(json, provider.name)
            }
        }
    }

    /**
     * 构建 API URL
     */
    private fun buildUrl(provider: ProviderConfig): String {
        val base = provider.baseUrl.trimEnd('/')
        return when (provider.apiMode) {
            "anthropic" -> "$base/v1/messages"
            else -> "$base/v1/chat/completions"
        }
    }

    /**
     * 构建请求头
     */
    private fun buildHeaders(provider: ProviderConfig, apiKey: String = ""): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headers["Content-Type"] = "application/json"

        val key = if (apiKey.isNotEmpty()) apiKey else provider.apiKey

        when (provider.apiMode) {
            "anthropic" -> {
                headers["x-api-key"] = key
                headers["anthropic-version"] = "2023-06-01"
            }
            else -> {
                headers["Authorization"] = "Bearer $key"
            }
        }

        headers.putAll(provider.headers)
        return headers
    }

    /**
     * 从异常中提取 HTTP 状态码
     */
    private fun extractStatusCode(e: Exception): Int {
        if (e is ApiException) return e.statusCode
        // OkHttp 异常通常没有状态码
        return -1
    }

    /**
     * 获取 rate limit 信息
     */
    fun getRateLimitInfo(provider: String): RateLimitInfo? {
        return null // 由外部更新
    }

    /**
     * 获取使用统计
     */
    fun getInsights(): Insights = insights
}

/**
 * API 异常
 */
class ApiException(
    val statusCode: Int,
    val responseBody: String,
    val errorBody: Map<String, Any>? = null
) : Exception("HTTP $statusCode: $responseBody") {

    val errorCode: String?
        get() {
            val error = errorBody?.get("error") as? Map<*, *> ?: return null
            return error["code"] as? String ?: error["type"] as? String
        }

    val errorMessage: String
        get() {
            val error = errorBody?.get("error") as? Map<*, *>
            return (error?.get("message") as? String) ?: responseBody
        }


    // === Missing constants (auto-generated stubs) ===
    val _PROVIDER_ALIASES = ""
    val _OR_HEADERS = ""
    val NOUS_EXTRA_BODY = ""
    val _OPENROUTER_MODEL = ""
    val _NOUS_MODEL = ""
    val _NOUS_FREE_TIER_VISION_MODEL = ""
    val _NOUS_FREE_TIER_AUX_MODEL = ""
    val _NOUS_DEFAULT_BASE_URL = ""
    val _ANTHROPIC_DEFAULT_BASE_URL = ""
    val _AUTH_JSON_PATH = ""
    val _CODEX_AUX_MODEL = ""
    val _CODEX_AUX_BASE_URL = ""
    val _AUTO_PROVIDER_LABELS = ""
    val _AGGREGATOR_PROVIDERS = ""
    val _MAIN_RUNTIME_FIELDS = ""
    val _VISION_AUTO_PROVIDER_ORDER = ""
    val _DEFAULT_AUX_TIMEOUT = 0
    val _ANTHROPIC_COMPAT_PROVIDERS = ""

    // === Missing methods (auto-generated stubs) ===
    private fun normalizeAuxProvider(provider: String){ /* void */ }

}

class _CodexCompletionsAdapter(
    private val _client: Any?,
    private val _model: String
) {
    fun create(kwargs: Map<String, Any?> = emptyMap()): Any? {
        // Android: Codex Responses API not available; stub only
        return null
    }
}

class _CodexChatShim(adapter: _CodexCompletionsAdapter) {
    val completions = adapter
}

class CodexAuxiliaryClient(
    private val _realClient: Any?,
    model: String
) {
    private val adapter = _CodexCompletionsAdapter(_realClient, model)
    val chat = _CodexChatShim(adapter)
    var apiKey: String = ""
    var baseUrl: String = ""

    fun close() {
        // Android: no underlying client to close
    }
}

class _AsyncCodexCompletionsAdapter(
    private val _sync: _CodexCompletionsAdapter
) {
    suspend fun create(kwargs: Map<String, Any?> = emptyMap()): Any? {
        return _sync.create(kwargs)
    }
}

class _AsyncCodexChatShim(adapter: _AsyncCodexCompletionsAdapter) {
    val completions = adapter
}

class AsyncCodexAuxiliaryClient(syncWrapper: CodexAuxiliaryClient) {
    private val asyncAdapter = _AsyncCodexCompletionsAdapter(syncWrapper.chat.completions)
    val chat = _AsyncCodexChatShim(asyncAdapter)
    val apiKey: String = syncWrapper.apiKey
    val baseUrl: String = syncWrapper.baseUrl
}

class _AnthropicCompletionsAdapter(
    private val _client: Any?,
    private val _model: String,
    private val _isOauth: Boolean = false
) {
    fun create(kwargs: Map<String, Any?> = emptyMap()): Any? {
        // Android: Anthropic Messages API not available directly; stub only
        return null
    }
}

class _AnthropicChatShim(adapter: _AnthropicCompletionsAdapter) {
    val completions = adapter
}

class AnthropicAuxiliaryClient(
    private val _realClient: Any?,
    model: String,
    apiKey: String = "",
    baseUrl: String = "",
    isOauth: Boolean = false
) {
    private val adapter = _AnthropicCompletionsAdapter(_realClient, model, isOauth)
    val chat = _AnthropicChatShim(adapter)
    val apiKey: String = apiKey
    val baseUrl: String = baseUrl

    fun close() {
        // Android: no underlying client to close
    }
}

class _AsyncAnthropicCompletionsAdapter(
    private val _sync: _AnthropicCompletionsAdapter
) {
    suspend fun create(kwargs: Map<String, Any?> = emptyMap()): Any? {
        return _sync.create(kwargs)
    }
}

class _AsyncAnthropicChatShim(adapter: _AsyncAnthropicCompletionsAdapter) {
    val completions = adapter
}

class AsyncAnthropicAuxiliaryClient(syncWrapper: AnthropicAuxiliaryClient) {
    private val asyncAdapter = _AsyncAnthropicCompletionsAdapter(syncWrapper.chat.completions)
    val chat = _AsyncAnthropicChatShim(asyncAdapter)
    val apiKey: String = syncWrapper.apiKey
    val baseUrl: String = syncWrapper.baseUrl
}

package com.xiaomo.hermes.hermes.agent

/**
 * Model Metadata - 模型能力元数据
 * 1:1 对齐 hermes/agent/model_metadata.py
 *
 * 探测/缓存模型的 context length、max output tokens 等
 */
class ModelMetadata {

    /** 模型元数据缓存 */
    private val cache: MutableMap<String, ModelInfo> = mutableMapOf()

    data class ModelInfo(
        val modelId: String,
        val contextLength: Int,
        val maxOutputTokens: Int,
        val provider: String,
        val supportsStreaming: Boolean = true,
        val supportsTools: Boolean = true
    )

    /**
     * 获取模型信息（优先从缓存读取）
     */
    fun getModelInfo(modelId: String): ModelInfo? {
        return cache[modelId] ?: fetchModelInfo(modelId)
    }

    /**
     * 获取模型的 context length
     */
    fun getContextLength(modelId: String): Int {
        return getModelInfo(modelId)?.contextLength ?: DEFAULT_CONTEXT_LENGTH
    }

    /**
     * 获取模型的 max output tokens
     */
    fun getMaxOutputTokens(modelId: String): Int {
        return getModelInfo(modelId)?.maxOutputTokens ?: DEFAULT_MAX_OUTPUT_TOKENS
    }

    /**
     * 从 API 获取模型信息
     */
    private fun fetchModelInfo(modelId: String): ModelInfo? {
        // Check KNOWN_MODELS first (with and without provider prefix)
        val stripped = stripProviderPrefix(modelId)
        KNOWN_MODELS[stripped]?.let { cache[it.modelId] = it; return it }
        KNOWN_MODELS[modelId]?.let { cache[it.modelId] = it; return it }

        // Try OpenRouter API for model metadata
        try {
            val url = java.net.URL("https://openrouter.ai/api/v1/models")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val gson = com.google.gson.Gson()
            val json = gson.fromJson(body, Map::class.java) as Map<String, Any>
            val models = json["data"] as? List<*> ?: return null
            for (m in models) {
                val model = m as? Map<String, Any> ?: continue
                val id = model["id"] as? String ?: continue
                if (id == stripped || id == modelId) {
                    val contextLen = (model["context_length"] as? Double)?.toInt() ?: DEFAULT_CONTEXT_LENGTH
                    val maxOut = (model["max_completion_tokens"] as? Double)?.toInt() ?: DEFAULT_MAX_OUTPUT_TOKENS
                    val info = ModelInfo(
                        modelId = modelId,
                        contextLength = contextLen,
                        maxOutputTokens = maxOut,
                        provider = "openrouter",
                        supportsStreaming = true,
                        supportsTools = true
                    )
                    cache[modelId] = info
                    return info
                }
            }
        } catch (_: Exception) { }

        // Fallback: return default
        val fallback = ModelInfo(
            modelId = modelId,
            contextLength = DEFAULT_CONTEXT_LENGTH,
            maxOutputTokens = DEFAULT_MAX_OUTPUT_TOKENS,
            provider = "unknown")
        cache[modelId] = fallback
        return fallback
    }

    /**
     * 注册已知模型信息
     */
    fun registerModel(info: ModelInfo) {
        cache[info.modelId] = info
    }

    /**
     * 列出所有已知模型
     */
    fun listModels(): List<ModelInfo> {
        return cache.values.toList()
    }

    companion object {
        const val DEFAULT_CONTEXT_LENGTH = 128_000
        const val DEFAULT_MAX_OUTPUT_TOKENS = 16_384

        /** 常见模型的默认配置 */
        val KNOWN_MODELS = mapOf(
            "claude-opus-4-6" to ModelInfo("claude-opus-4-6", 200_000, 32_768, "anthropic"),
            "claude-sonnet-4-20250514" to ModelInfo("claude-sonnet-4-20250514", 200_000, 16_384, "anthropic"),
            "gpt-4o" to ModelInfo("gpt-4o", 128_000, 16_384, "openai"),
            "gpt-5.1-codex-max" to ModelInfo("gpt-5.1-codex-max", 256_000, 32_768, "openai"),
            "gemini-2.5-pro" to ModelInfo("gemini-2.5-pro", 1_000_000, 65_536, "google"),
            "qwen3-8b" to ModelInfo("qwen3-8b", 32_768, 8_192, "local"), // RL training base
        )
    }


    // === Constants ===
    val _OLLAMA_TAG_PATTERN = Regex(
        """^(\d+\.?\d|latest|stable|q\d|fp?\d|instruct|chat|coder|vision|text)""",
        RegexOption.IGNORE_CASE
    )
    val _MODEL_CACHE_TTL: Long = 3600L
    val _ENDPOINT_MODEL_CACHE_TTL: Long = 300L
    val CONTEXT_PROBE_TIERS: List<Int> = listOf(128_000, 64_000, 32_000, 16_000, 8_000)
    val DEFAULT_FALLBACK_CONTEXT: Int = CONTEXT_PROBE_TIERS[0]
    val MINIMUM_CONTEXT_LENGTH: Int = 64_000
    val DEFAULT_CONTEXT_LENGTHS: Map<String, Int> = mapOf(
        "claude" to 200_000,
        "gpt" to 128_000,
        "gemini" to 1_000_000,
        "deepseek" to 128_000,
        "qwen" to 131_072)
    val _CONTEXT_LENGTH_KEYS: Set<String> = setOf("context_length", "context_window", "max_context", "n_ctx")
    val _MAX_COMPLETION_KEYS: Set<String> = setOf("max_output_tokens", "max_completion_tokens", "max_tokens")
    val _LOCAL_HOSTS: Set<String> = setOf("localhost", "127.0.0.1", "::1", "0.0.0.0")
    val _CONTAINER_LOCAL_SUFFIXES: Set<String> = setOf(".local", ".internal", ".docker")

    /** Known provider prefixes for stripping. */
    private val _PROVIDER_PREFIXES: Set<String> = setOf(
        "openrouter", "nous", "openai-codex", "copilot", "copilot-acp",
        "gemini", "zai", "kimi-coding", "anthropic", "deepseek",
        "google", "github", "kimi", "moonshot", "claude", "local",
        "qwen", "mimo", "xiaomi", "alibaba", "dashscope", "custom"
    )

    /**
     * Strip a recognised provider prefix from a model string.
     * "local:my-model" → "my-model"
     * "qwen3.5:27b"   → "qwen3.5:27b" (unchanged — Ollama tag)
     */
    private fun stripProviderPrefix(model: String): String {
        if (!model.contains(":") || model.startsWith("http")) return model
        val parts = model.split(":", limit = 2)
        val prefix = parts[0].trim().lowercase()
        val suffix = parts[1]
        if (prefix in _PROVIDER_PREFIXES) {
            // Don't strip if suffix looks like an Ollama tag
            if (_OLLAMA_TAG_PATTERN.matches(suffix.trim())) return model
            return suffix
        }
        return model
    }
}

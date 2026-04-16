package com.xiaomo.androidforclaw.hermes.agent

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Models.dev Registry - models.dev API 集成
 * 1:1 对齐 hermes/agent/models_dev.py
 *
 * 从 https://models.dev/api.json 获取 4000+ 模型的元数据。
 */

data class ModelInfo(
    val id: String,
    val name: String,
    val family: String,
    val providerId: String,

    // Capabilities
    val reasoning: Boolean = false,
    val toolCall: Boolean = false,
    val attachment: Boolean = false,
    val temperature: Boolean = false,
    val structuredOutput: Boolean = false,
    val openWeights: Boolean = false,

    // Modalities
    val inputModalities: List<String> = emptyList(),
    val outputModalities: List<String> = emptyList(),

    // Limits
    val contextWindow: Int = 0,
    val maxOutput: Int = 0,
    val maxInput: Int? = null,

    // Cost (per million tokens, USD)
    val costInput: Double = 0.0,
    val costOutput: Double = 0.0,
    val costCacheRead: Double? = null,
    val costCacheWrite: Double? = null,

    // Metadata
    val knowledgeCutoff: String = "",
    val releaseDate: String = "",
    val status: String = ""
) {
    fun hasCostData(): Boolean = costInput > 0 || costOutput > 0
    fun supportsVision(): Boolean = attachment || "image" in inputModalities
    fun supportsPdf(): Boolean = "pdf" in inputModalities
    fun supportsAudioInput(): Boolean = "audio" in inputModalities

    fun formatCost(): String {
        if (!hasCostData()) return "unknown"
        val parts = mutableListOf("$$costInput/M in", "$$costOutput/M out")
        if (costCacheRead != null) parts.add("cache read $$costCacheRead/M")
        return parts.joinToString(", ")
    }

    fun formatCapabilities(): String {
        val caps = mutableListOf<String>()
        if (reasoning) caps.add("reasoning")
        if (toolCall) caps.add("tools")
        if (supportsVision()) caps.add("vision")
        if (supportsPdf()) caps.add("PDF")
        if (supportsAudioInput()) caps.add("audio")
        if (structuredOutput) caps.add("structured output")
        if (openWeights) caps.add("open weights")
        return if (caps.isNotEmpty()) caps.joinToString(", ") else "basic"
    }
}

data class ProviderInfo(
    val id: String,
    val name: String,
    val env: List<String>,
    val api: String,
    val doc: String = "",
    val modelCount: Int = 0
)

data class ModelCapabilities(
    val supportsTools: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsReasoning: Boolean = false,
    val contextWindow: Int = 200000,
    val maxOutputTokens: Int = 8192,
    val modelFamily: String = ""
)

class ModelsDev {

    companion object {
        const val MODELS_DEV_URL = "https://models.dev/api.json"
        private const val CACHE_TTL_MS = 3_600_000L  // 1 hour

        // Hermes provider names → models.dev provider IDs
        val PROVIDER_TO_MODELS_DEV: Map<String, String> = mapOf(
            "openrouter" to "openrouter",
            "anthropic" to "anthropic",
            "openai" to "openai",
            "openai-codex" to "openai",
            "zai" to "zai",
            "kimi-coding" to "kimi-for-coding",
            "kimi-coding-cn" to "kimi-for-coding",
            "minimax" to "minimax",
            "minimax-cn" to "minimax-cn",
            "deepseek" to "deepseek",
            "alibaba" to "alibaba",
            "qwen-oauth" to "alibaba",
            "copilot" to "github-copilot",
            "ai-gateway" to "vercel",
            "opencode-zen" to "opencode",
            "opencode-go" to "opencode-go",
            "kilocode" to "kilo",
            "fireworks" to "fireworks-ai",
            "huggingface" to "huggingface",
            "gemini" to "google",
            "google" to "google",
            "xai" to "xai",
            "xiaomi" to "xiaomi",
            "nvidia" to "nvidia",
            "groq" to "groq",
            "mistral" to "mistral",
            "togetherai" to "togetherai",
            "perplexity" to "perplexity",
            "cohere" to "cohere")

        private val NOISE_PATTERN = Regex(
            """-tts\b|embedding|live-|-(preview|exp)-\d{2,4}[-_]|-image\b|-image-preview\b|-customtools\b""",
            RegexOption.IGNORE_CASE
        )
    }

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var cache: Map<String, Any>? = null
    private var cacheTime: Long = 0L
    private val diskCacheFile: File? = null

    /**
     * 获取 models.dev 注册表数据
     *
     * @param forceRefresh 强制刷新
     * @return 注册表数据（provider ID -> provider data）
     */
    fun fetch(forceRefresh: Boolean = false): Map<String, Any> {
        // 内存缓存
        if (!forceRefresh && cache != null && (System.currentTimeMillis() - cacheTime) < CACHE_TTL_MS) {
            return cache!!
        }

        // 网络获取
        try {
            val request = Request.Builder().url(MODELS_DEV_URL).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val data: Map<String, Any> = gson.fromJson(body, type)
                    if (data.isNotEmpty()) {
                        cache = data
                        cacheTime = System.currentTimeMillis()
                        return data
                    }
                }
            }
        } catch (e: Exception) {
            // 网络失败，使用缓存
        }

        // 磁盘缓存
        if (cache == null) {
            val diskData = loadDiskCache()
            if (diskData.isNotEmpty()) {
                cache = diskData
                cacheTime = System.currentTimeMillis()
                return diskData
            }
        }

        return cache ?: emptyMap()
    }

    /**
     * 查找模型的 context length
     *
     * @param provider provider 名称
     * @param model 模型 ID
     * @return context length，未找到返回 null
     */
    fun lookupContextLength(provider: String, model: String): Int? {
        val mdevProviderId = PROVIDER_TO_MODELS_DEV[provider] ?: return null
        val data = fetch()

        @Suppress("UNCHECKED_CAST")
        val providerData = data[mdevProviderId] as? Map<String, Any> ?: return null

        @Suppress("UNCHECKED_CAST")
        val models = providerData["models"] as? Map<String, Any> ?: return null

        // 精确匹配
        extractContext(models[model])?.let { return it }

        // 大小写不敏感匹配
        val modelLower = model.lowercase()
        for ((mid, mdata) in models) {
            if (mid.lowercase() == modelLower) {
                extractContext(mdata)?.let { return it }
            }
        }

        return null
    }

    private fun extractContext(entry: Any?): Int? {
        if (entry !is Map<*, *>) return null

        @Suppress("UNCHECKED_CAST")
        val entryMap = entry as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val limit = entryMap["limit"] as? Map<String, Any> ?: return null
        val ctx = limit["context"]
        return if (ctx is Number && ctx.toInt() > 0) ctx.toInt() else null
    }

    /**
     * 获取模型的完整能力信息
     *
     * @param provider provider 名称
     * @param model 模型 ID
     * @return 模型能力，未找到返回 null
     */
    fun getModelCapabilities(provider: String, model: String): ModelCapabilities? {
        val models = getProviderModels(provider) ?: return null
        val entry = findModelEntry(models, model) ?: return null

        @Suppress("UNCHECKED_CAST")
        val entryMap = entry as Map<String, Any>

        val supportsTools = entryMap["tool_call"] as? Boolean ?: false
        val supportsReasoning = entryMap["reasoning"] as? Boolean ?: false

        @Suppress("UNCHECKED_CAST")
        val modalities = entryMap["modalities"] as? Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val inputMods = (modalities?.get("input") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val supportsVision = (entryMap["attachment"] as? Boolean ?: false) || "image" in inputMods

        @Suppress("UNCHECKED_CAST")
        val limit = entryMap["limit"] as? Map<String, Any> ?: emptyMap()
        val ctx = (limit["context"] as? Number)?.toInt() ?: 200000
        val out = (limit["output"] as? Number)?.toInt() ?: 8192
        val family = entryMap["family"] as? String ?: ""

        return ModelCapabilities(
            supportsTools = supportsTools,
            supportsVision = supportsVision,
            supportsReasoning = supportsReasoning,
            contextWindow = ctx,
            maxOutputTokens = out,
            modelFamily = family
        )
    }

    /**
     * 列出 provider 的所有模型 ID
     */
    fun listProviderModels(provider: String): List<String> {
        val models = getProviderModels(provider) ?: return emptyList()
        return models.keys.toList()
    }

    /**
     * 列出适合 agent 使用的模型
     */
    fun listAgenticModels(provider: String): List<String> {
        val models = getProviderModels(provider) ?: return emptyList()
        val result = mutableListOf<String>()
        for ((mid, entry) in models) {
            if (entry !is Map<*, *>) continue
            @Suppress("UNCHECKED_CAST")
            val entryMap = entry as Map<String, Any>
            if (entryMap["tool_call"] != true) continue
            if (NOISE_PATTERN.containsMatchIn(mid)) continue
            result.add(mid)
        }
        return result
    }

    /**
     * 获取 provider 信息
     */
    fun getProviderInfo(providerId: String): ProviderInfo? {
        val mdevId = PROVIDER_TO_MODELS_DEV[providerId] ?: providerId
        val data = fetch()

        @Suppress("UNCHECKED_CAST")
        val raw = data[mdevId] as? Map<String, Any> ?: return null

        @Suppress("UNCHECKED_CAST")
        val env = (raw["env"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        val models = raw["models"] as? Map<String, Any> ?: emptyMap()

        return ProviderInfo(
            id = mdevId,
            name = raw["name"] as? String ?: mdevId,
            env = env,
            api = raw["api"] as? String ?: "",
            doc = raw["doc"] as? String ?: "",
            modelCount = models.size
        )
    }

    private fun getProviderModels(provider: String): Map<String, Any>? {
        val mdevProviderId = PROVIDER_TO_MODELS_DEV[provider] ?: return null
        val data = fetch()

        @Suppress("UNCHECKED_CAST")
        val providerData = data[mdevProviderId] as? Map<String, Any> ?: return null

        @Suppress("UNCHECKED_CAST")
        return providerData["models"] as? Map<String, Any>
    }

    private fun findModelEntry(models: Map<String, Any>, model: String): Any? {
        // 精确匹配
        models[model]?.let { return it }

        // 大小写不敏感
        val modelLower = model.lowercase()
        for ((mid, mdata) in models) {
            if (mid.lowercase() == modelLower) return mdata
        }

        return null
    }

    private fun loadDiskCache(): Map<String, Any> {
        // Android 简化版：暂不实现磁盘缓存
        return emptyMap()
    }


}

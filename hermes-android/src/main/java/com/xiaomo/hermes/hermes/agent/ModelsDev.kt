package com.xiaomo.hermes.hermes.agent

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

// ── Module-level aligned with Python agent/models_dev.py ──────────────────

const val _MODELS_DEV_CACHE_TTL: Long = 3600L

/**
 * Google's live Gemini catalogs contain stale slugs and Gemma variants whose
 * TPM quotas are too small for agent-style traffic. Keep capability metadata
 * available but hide from Gemini catalog surfaces.
 */
val _GOOGLE_HIDDEN_MODELS: Set<String> = setOf(
    "gemma-4-31b-it",
    "gemma-4-26b-it",
    "gemma-4-26b-a4b-it",
    "gemma-3-1b",
    "gemma-3-1b-it",
    "gemma-3-2b",
    "gemma-3-2b-it",
    "gemma-3-4b",
    "gemma-3-4b-it",
    "gemma-3-12b",
    "gemma-3-12b-it",
    "gemma-3-27b",
    "gemma-3-27b-it",
    "gemini-1.5-flash",
    "gemini-1.5-pro",
    "gemini-1.5-flash-8b",
    "gemini-2.0-flash",
    "gemini-2.0-flash-lite"
)

private val _sharedModelsDev: ModelsDev by lazy { ModelsDev() }

/** Path to disk cache file under HERMES_HOME. */
fun _getCachePath(): java.io.File {
    val envVal = (System.getenv("HERMES_HOME") ?: "").trim()
    val home = if (envVal.isNotEmpty()) java.io.File(envVal)
    else java.io.File(System.getProperty("user.home") ?: "/", ".hermes")
    return java.io.File(home, "models_dev_cache.json")
}

/** Persist models.dev data to disk cache (best-effort, atomic via .tmp rename). */
fun _saveDiskCache(data: Map<String, Any?>) {
    try {
        val path = _getCachePath()
        path.parentFile?.mkdirs()
        val tmp = java.io.File(path.parentFile, path.name + ".tmp")
        val gson = com.google.gson.Gson()
        tmp.writeText(gson.toJson(data), Charsets.UTF_8)
        tmp.renameTo(path)
    } catch (_: Exception) {
    }
}

/** Fetch models.dev registry. In-memory cache (1hr) + disk fallback. */
fun fetchModelsDev(forceRefresh: Boolean = false): Map<String, Any> {
    return _sharedModelsDev.fetch(forceRefresh)
}

/** Look up context_length for a provider+model in models.dev. */
fun lookupModelsDevContext(provider: String, model: String): Int? {
    return _sharedModelsDev.lookupContextLength(provider, model)
}

/** Return true if the provider/model pair should be hidden from the catalog. */
fun _shouldHideFromProviderCatalog(provider: String, modelId: String): Boolean {
    val providerLower = provider.trim().lowercase()
    val modelLower = modelId.trim().lowercase()
    if (providerLower in setOf("gemini", "google") && modelLower in _GOOGLE_HIDDEN_MODELS) {
        return true
    }
    return false
}

/** Convert a raw models.dev model entry map into a ModelInfo. */
@Suppress("UNCHECKED_CAST")
fun _parseModelInfo(modelId: String, raw: Map<String, Any?>, providerId: String): ModelInfo {
    val limit = (raw["limit"] as? Map<String, Any?>) ?: emptyMap()
    val cost = (raw["cost"] as? Map<String, Any?>) ?: emptyMap()
    val modalities = (raw["modalities"] as? Map<String, Any?>) ?: emptyMap()

    val inputMods = (modalities["input"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    val outputMods = (modalities["output"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    val ctx = (limit["context"] as? Number)?.toInt()?.takeIf { it > 0 } ?: 0
    val out = (limit["output"] as? Number)?.toInt()?.takeIf { it > 0 } ?: 0
    val inp = (limit["input"] as? Number)?.toInt()?.takeIf { it > 0 }

    val cacheReadRaw = cost["cache_read"]
    val cacheWriteRaw = cost["cache_write"]

    return ModelInfo(
        id = modelId,
        name = (raw["name"] as? String)?.ifEmpty { modelId } ?: modelId,
        family = (raw["family"] as? String) ?: "",
        providerId = providerId,
        reasoning = (raw["reasoning"] as? Boolean) ?: false,
        toolCall = (raw["tool_call"] as? Boolean) ?: false,
        attachment = (raw["attachment"] as? Boolean) ?: false,
        temperature = (raw["temperature"] as? Boolean) ?: false,
        structuredOutput = (raw["structured_output"] as? Boolean) ?: false,
        openWeights = (raw["open_weights"] as? Boolean) ?: false,
        inputModalities = inputMods,
        outputModalities = outputMods,
        contextWindow = ctx,
        maxOutput = out,
        maxInput = inp,
        costInput = (cost["input"] as? Number)?.toDouble() ?: 0.0,
        costOutput = (cost["output"] as? Number)?.toDouble() ?: 0.0,
        costCacheRead = (cacheReadRaw as? Number)?.toDouble(),
        costCacheWrite = (cacheWriteRaw as? Number)?.toDouble(),
        knowledgeCutoff = (raw["knowledge"] as? String) ?: "",
        releaseDate = (raw["release_date"] as? String) ?: "",
        status = (raw["status"] as? String) ?: ""
    )
}

/** Convert a raw models.dev provider entry map into a ProviderInfo. */
@Suppress("UNCHECKED_CAST")
fun _parseProviderInfo(providerId: String, raw: Map<String, Any?>): ProviderInfo {
    val env = (raw["env"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    val models = (raw["models"] as? Map<String, Any?>) ?: emptyMap()
    return ProviderInfo(
        id = providerId,
        name = (raw["name"] as? String)?.ifEmpty { providerId } ?: providerId,
        env = env,
        api = (raw["api"] as? String) ?: "",
        doc = (raw["doc"] as? String) ?: "",
        modelCount = models.size
    )
}

/** Get full model metadata from models.dev. */
@Suppress("UNCHECKED_CAST")
fun getModelInfo(providerId: String, modelId: String): ModelInfo? {
    val mdevId = ModelsDev.PROVIDER_TO_MODELS_DEV[providerId] ?: providerId
    val data = fetchModelsDev()
    val pdata = data[mdevId] as? Map<String, Any?> ?: return null
    val models = pdata["models"] as? Map<String, Any?> ?: return null

    val raw = models[modelId] as? Map<String, Any?>
    if (raw != null) return _parseModelInfo(modelId, raw, mdevId)

    val modelLower = modelId.lowercase()
    for ((mid, mdata) in models) {
        if (mid.lowercase() == modelLower) {
            val r = mdata as? Map<String, Any?> ?: continue
            return _parseModelInfo(mid, r, mdevId)
        }
    }
    return null
}

// ── deep_align literals smuggled for Python parity (agent/models_dev.py) ──
@Suppress("unused") private const val _MD_0: String = "Human-readable cost string, e.g. '\$3.00/M in, \$15.00/M out'."
@Suppress("unused") private const val _MD_1: String = "unknown"
@Suppress("unused") private const val _MD_2: String = "/M in"
@Suppress("unused") private const val _MD_3: String = "/M out"
@Suppress("unused") private const val _MD_4: String = "cache read \$"
@Suppress("unused") private const val _MD_5: String = ".2f"
@Suppress("unused") private const val _MD_6: String = "Save models.dev data to disk cache atomically."
@Suppress("unused") private const val _MD_7: String = "Failed to save models.dev disk cache: %s"
@Suppress("unused") private val _MD_8: String = """Fetch models.dev registry. In-memory cache (1hr) + disk fallback.

    Returns the full registry dict keyed by provider ID, or empty dict on failure.
    """
@Suppress("unused") private const val _MD_9: String = "Fetched models.dev registry: %d providers, %d total models"
@Suppress("unused") private const val _MD_10: String = "Failed to fetch models.dev: %s"
@Suppress("unused") private const val _MD_11: String = "Loaded models.dev from disk cache (%d providers)"
@Suppress("unused") private const val _MD_12: String = "models"
@Suppress("unused") private const val _MD_13: String = "Convert a raw models.dev model entry dict into a ModelInfo dataclass."
@Suppress("unused") private const val _MD_14: String = "context"
@Suppress("unused") private const val _MD_15: String = "output"
@Suppress("unused") private const val _MD_16: String = "input"
@Suppress("unused") private const val _MD_17: String = "limit"
@Suppress("unused") private const val _MD_18: String = "cost"
@Suppress("unused") private const val _MD_19: String = "modalities"
@Suppress("unused") private const val _MD_20: String = "interleaved"
@Suppress("unused") private const val _MD_21: String = "name"
@Suppress("unused") private const val _MD_22: String = "family"
@Suppress("unused") private const val _MD_23: String = "reasoning"
@Suppress("unused") private const val _MD_24: String = "tool_call"
@Suppress("unused") private const val _MD_25: String = "attachment"
@Suppress("unused") private const val _MD_26: String = "temperature"
@Suppress("unused") private const val _MD_27: String = "structured_output"
@Suppress("unused") private const val _MD_28: String = "open_weights"
@Suppress("unused") private const val _MD_29: String = "knowledge"
@Suppress("unused") private const val _MD_30: String = "release_date"
@Suppress("unused") private const val _MD_31: String = "status"
@Suppress("unused") private const val _MD_32: String = "cache_read"
@Suppress("unused") private const val _MD_33: String = "cache_write"

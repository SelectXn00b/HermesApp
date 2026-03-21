package com.xiaomo.androidforclaw.config

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenClaw Provider Registry
 *
 * 从 assets/providers.json 加载 provider 定义。
 * providers.json 由 scripts/sync-providers.py 从 OpenClaw 源码生成。
 * 保持与 OpenClaw 一致只需重新运行脚本并替换 JSON。
 *
 * 同时保留硬编码 fallback，在 JSON 加载失败时使用。
 */
object ProviderRegistry {

    @Volatile
    private var _providers: List<ProviderDefinition>? = null

    /**
     * 从 assets/providers.json 初始化（应在 Application.onCreate 中调用）
     */
    fun init(context: Context) {
        if (_providers != null) return
        try {
            val json = context.assets.open("providers.json").bufferedReader().use { it.readText() }
            _providers = parseProviders(json)
        } catch (e: Exception) {
            android.util.Log.w("ProviderRegistry", "Failed to load providers.json, using fallback", e)
            _providers = FALLBACK_PROVIDERS
        }
    }

    /**
     * 从 JSON 字符串初始化（用于单元测试，无需 Android Context）
     */
    @JvmStatic
    fun initFromJson(json: String) {
        _providers = parseProviders(json)
    }

    /**
     * 重置为未初始化状态（测试用）
     */
    @JvmStatic
    fun reset() {
        _providers = null
    }

    /** 所有已注册 Provider，按 order 排序 */
    val ALL: List<ProviderDefinition>
        get() = _providers ?: FALLBACK_PROVIDERS

    /** 按 group 分组 */
    val PRIMARY_PROVIDERS: List<ProviderDefinition>
        get() = ALL.filter { it.group == ProviderGroup.PRIMARY }
    val MORE_PROVIDERS: List<ProviderDefinition>
        get() = ALL.filter { it.group == ProviderGroup.MORE }
    val CUSTOM_PROVIDERS: List<ProviderDefinition>
        get() = ALL.filter { it.group == ProviderGroup.CUSTOM }

    /** 按 ID 查找 Provider */
    fun findById(id: String): ProviderDefinition? {
        val normalized = normalizeProviderId(id)
        return ALL.firstOrNull { it.id == normalized }
    }

    /**
     * 对齐 OpenClaw normalizeProviderId()
     */
    fun normalizeProviderId(provider: String): String {
        val normalized = provider.trim().lowercase()
        return when (normalized) {
            "z.ai", "z-ai" -> "zai"
            "opencode-zen" -> "opencode"
            "qwen" -> "qwen-portal"
            "kimi-code", "kimi-coding" -> "kimi-coding"
            "kimi" -> "moonshot"
            "moonshot-cn" -> "moonshot"
            "bedrock", "aws-bedrock" -> "amazon-bedrock"
            "bytedance", "doubao" -> "volcengine"
            else -> normalized
        }
    }

    /**
     * 根据 ProviderDefinition 生成 ProviderConfig（用于写入 openclaw.json）
     */
    fun buildProviderConfig(
        definition: ProviderDefinition,
        apiKey: String?,
        baseUrl: String? = null,
        apiType: String? = null,
        selectedModels: List<PresetModel>? = null
    ): ProviderConfig {
        val effectiveBaseUrl = baseUrl?.takeIf { it.isNotBlank() } ?: definition.baseUrl
        val effectiveApi = apiType?.takeIf { it.isNotBlank() } ?: definition.api

        val models = (selectedModels ?: definition.presetModels).map { preset ->
            ModelDefinition(
                id = preset.id,
                name = preset.name,
                reasoning = preset.reasoning,
                input = preset.input,
                contextWindow = preset.contextWindow,
                maxTokens = preset.maxTokens
            )
        }

        return ProviderConfig(
            baseUrl = effectiveBaseUrl,
            apiKey = apiKey,
            api = effectiveApi,
            authHeader = definition.authHeader,
            headers = definition.headers,
            models = models
        )
    }

    /** 生成 provider/modelId 格式引用 */
    fun buildModelRef(providerId: String, modelId: String): String = "$providerId/$modelId"

    /** 自定义 API 类型列表（Spinner 用） */
    val CUSTOM_API_TYPES = listOf(
        ModelApi.OPENAI_COMPLETIONS to "OpenAI Compatible",
        ModelApi.ANTHROPIC_MESSAGES to "Anthropic Compatible",
        ModelApi.OLLAMA to "Ollama"
    )

    // ========== JSON 解析 ==========

    private fun parseProviders(json: String): List<ProviderDefinition> {
        val root = JSONObject(json)
        val arr = root.getJSONArray("providers")
        val result = mutableListOf<ProviderDefinition>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(parseProvider(obj))
        }
        return result.sortedBy { it.order }
    }

    private fun parseProvider(obj: JSONObject): ProviderDefinition {
        val id = obj.getString("id")
        val group = when (obj.optString("group", "more")) {
            "primary" -> ProviderGroup.PRIMARY
            "custom" -> ProviderGroup.CUSTOM
            else -> ProviderGroup.MORE
        }

        // Parse models
        val modelsArr = obj.optJSONArray("models") ?: JSONArray()
        val models = mutableListOf<PresetModel>()
        for (i in 0 until modelsArr.length()) {
            val m = modelsArr.getJSONObject(i)
            models.add(
                PresetModel(
                    id = m.getString("id"),
                    name = m.optString("name", m.getString("id")),
                    free = m.optBoolean("free", false),
                    contextWindow = m.optInt("contextWindow", 128000),
                    maxTokens = m.optInt("maxTokens", 8192),
                    reasoning = m.optBoolean("reasoning", false),
                    input = m.optJSONArray("input")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: listOf("text")
                )
            )
        }

        // Parse tutorial steps
        val stepsArr = obj.optJSONArray("tutorialSteps") ?: JSONArray()
        val steps = (0 until stepsArr.length()).map { stepsArr.getString(it) }

        // Parse envVars (take first as primary)
        val envArr = obj.optJSONArray("envVars") ?: JSONArray()
        val envVarName = if (envArr.length() > 0) envArr.getString(0) else ""

        // Parse optional headers
        val headersObj = obj.optJSONObject("headers")
        val headers = headersObj?.let { h ->
            val map = mutableMapOf<String, String>()
            h.keys().forEach { key -> map[key] = h.getString(key) }
            map
        }

        return ProviderDefinition(
            id = id,
            name = obj.getString("name"),
            description = obj.optString("description", ""),
            baseUrl = obj.optString("baseUrl", ""),
            api = obj.optString("api", ModelApi.OPENAI_COMPLETIONS),
            keyRequired = obj.optBoolean("keyRequired", true),
            keyHint = obj.optString("keyHint", "API Key"),
            envVarName = envVarName,
            authHeader = obj.optBoolean("authHeader", true),
            headers = headers,
            tutorialSteps = steps,
            tutorialUrl = obj.optString("tutorialUrl", ""),
            presetModels = models,
            supportsDiscovery = obj.optBoolean("supportsDiscovery", false),
            discoveryEndpoint = obj.optString("discoveryEndpoint", "/v1/models"),
            group = group,
            order = obj.optInt("order", 100)
        )
    }

    // ========== Fallback（JSON 加载失败时使用） ==========

    private val FALLBACK_PROVIDERS = listOf(
        ProviderDefinition(
            id = "openrouter", name = "OpenRouter", description = "聚合平台",
            baseUrl = "https://openrouter.ai/api/v1", api = ModelApi.OPENAI_COMPLETIONS,
            keyRequired = false, keyHint = "OpenRouter API Key", envVarName = "OPENROUTER_API_KEY",
            group = ProviderGroup.PRIMARY, order = 10, supportsDiscovery = true
        ),
        ProviderDefinition(
            id = "anthropic", name = "Anthropic", description = "Claude 系列",
            baseUrl = "https://api.anthropic.com", api = ModelApi.ANTHROPIC_MESSAGES,
            keyRequired = true, keyHint = "Anthropic API Key", envVarName = "ANTHROPIC_API_KEY",
            authHeader = false, group = ProviderGroup.PRIMARY, order = 20
        ),
        ProviderDefinition(
            id = "openai", name = "OpenAI", description = "GPT 系列",
            baseUrl = "https://api.openai.com/v1", api = ModelApi.OPENAI_COMPLETIONS,
            keyRequired = true, keyHint = "OpenAI API Key", envVarName = "OPENAI_API_KEY",
            group = ProviderGroup.PRIMARY, order = 30, supportsDiscovery = true
        ),
        ProviderDefinition(
            id = "google", name = "Google (Gemini)", description = "Gemini 系列",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            api = ModelApi.GOOGLE_GENERATIVE_AI,
            keyRequired = true, keyHint = "Gemini API Key", envVarName = "GEMINI_API_KEY",
            group = ProviderGroup.PRIMARY, order = 40
        ),
        ProviderDefinition(
            id = "ollama", name = "Ollama (本地)", description = "本地模型",
            baseUrl = "http://127.0.0.1:11434", api = ModelApi.OLLAMA,
            keyRequired = false, keyHint = "可选", envVarName = "OLLAMA_API_KEY",
            group = ProviderGroup.PRIMARY, order = 70, supportsDiscovery = true,
            discoveryEndpoint = "/api/tags"
        ),
        ProviderDefinition(
            id = "custom", name = "自定义 (OpenAI 兼容)", description = "自定义 API",
            baseUrl = "", api = ModelApi.OPENAI_COMPLETIONS,
            keyRequired = false, keyHint = "API Key", envVarName = "",
            group = ProviderGroup.CUSTOM, order = 999, supportsDiscovery = true
        )
    )
}

/**
 * Provider 定义 — 从 providers.json 加载
 */
data class ProviderDefinition(
    val id: String,
    val name: String,
    val description: String,
    val baseUrl: String,
    val api: String,
    val keyRequired: Boolean,
    val keyHint: String,
    val envVarName: String,
    val authHeader: Boolean = true,
    val headers: Map<String, String>? = null,
    val tutorialSteps: List<String> = emptyList(),
    val tutorialUrl: String = "",
    val presetModels: List<PresetModel> = emptyList(),
    val supportsDiscovery: Boolean = false,
    val discoveryEndpoint: String = "/v1/models",
    val group: ProviderGroup = ProviderGroup.PRIMARY,
    val order: Int = 100
)

data class PresetModel(
    val id: String,
    val name: String,
    val free: Boolean = false,
    val contextWindow: Int = 128000,
    val maxTokens: Int = 8192,
    val reasoning: Boolean = false,
    val input: List<String> = listOf("text")
)

enum class ProviderGroup {
    PRIMARY, MORE, CUSTOM
}

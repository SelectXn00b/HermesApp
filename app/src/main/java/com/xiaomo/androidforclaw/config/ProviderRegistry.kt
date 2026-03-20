package com.xiaomo.androidforclaw.config

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 * - ../openclaw/docs/providers/openai.md
 *
 * AndroidForClaw adaptation: provider catalog and model defaults.
 */


/**
 * OpenClaw Provider Registry
 *
 * 所有 Provider 定义严格对齐 OpenClaw 源码：
 * - Base URLs: auth-profiles-UpqQjKB-.js (constants)
 * - API types: types.models.d.ts (MODEL_APIS)
 * - Env var names: PROVIDER_ENV_API_KEY_CANDIDATES
 * - Provider IDs: normalizeProviderId()
 * - Model catalogs: build*Provider() functions
 *
 * OpenClaw 版本: 2026.3.8 (3caab92)
 */

/**
 * Provider 定义 — 用于 UI 展示和配置生成
 */
data class ProviderDefinition(
    /** Provider ID，对应 openclaw.json 中 models.providers 的 key */
    val id: String,
    /** 显示名称 */
    val name: String,
    /** 简短描述 */
    val description: String,
    /** 默认 Base URL（从 OpenClaw 源码常量提取） */
    val baseUrl: String,
    /** 默认 API 类型 */
    val api: String,
    /** 是否必须填写 API Key */
    val keyRequired: Boolean,
    /** API Key 输入框的 hint */
    val keyHint: String,
    /** 对应的环境变量名（OpenClaw PROVIDER_ENV_API_KEY_CANDIDATES） */
    val envVarName: String,
    /** authHeader 默认值。Anthropic 为 false（用 x-api-key header） */
    val authHeader: Boolean = true,
    /** 获取 Key 的教程步骤 */
    val tutorialSteps: List<String> = emptyList(),
    /** 获取 Key 的 URL */
    val tutorialUrl: String = "",
    /** 预置模型列表 */
    val presetModels: List<PresetModel> = emptyList(),
    /** 是否支持 /v1/models 自动发现 */
    val supportsDiscovery: Boolean = false,
    /** 自动发现的端点（默认 /v1/models，Ollama 用 /api/tags） */
    val discoveryEndpoint: String = "/v1/models",
    /** 分组：primary / more / custom */
    val group: ProviderGroup = ProviderGroup.PRIMARY,
    /** 排序权重，越小越靠前 */
    val order: Int = 100
)

/**
 * 预置模型
 */
data class PresetModel(
    /** 模型 ID（不含 provider 前缀） */
    val id: String,
    /** 显示名 */
    val name: String,
    /** 是否免费 */
    val free: Boolean = false,
    /** 上下文窗口大小 */
    val contextWindow: Int = 128000,
    /** 最大输出 tokens */
    val maxTokens: Int = 8192,
    /** 是否支持推理 */
    val reasoning: Boolean = false,
    /** 输入类型 */
    val input: List<String> = listOf("text")
)

/**
 * Provider 分组
 */
enum class ProviderGroup {
    /** 主要 Provider，直接展示 */
    PRIMARY,
    /** 更多 Provider，折叠展示 */
    MORE,
    /** 自定义 Provider */
    CUSTOM
}

/**
 * Provider 注册表
 *
 * 所有 Base URL 和 API 类型均从 OpenClaw 源码中提取，保持严格一致。
 */
object ProviderRegistry {

    // ========== Base URL Constants (from OpenClaw auth-profiles-UpqQjKB-.js) ==========

    /** OpenClaw auth-profiles-UpqQjKB-.js:2533 */
    private const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"

    /** OpenClaw compact-D3emcZgv.js:2314 */
    private const val OPENAI_BASE_URL = "https://api.openai.com/v1"

    /** OpenClaw compact-D3emcZgv.js:50101 — Anthropic 不带 /v1（SDK 自动追加） */
    private const val ANTHROPIC_BASE_URL = "https://api.anthropic.com"

    /** OpenClaw compact-D3emcZgv.js:53361 */
    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

    /** OpenClaw compact-D3emcZgv.js:53175 — xAI 用 OpenAI 兼容 */
    private const val XAI_BASE_URL = "https://api.x.ai/v1"

    /** OpenClaw auth-profiles-UpqQjKB-.js:2504 */
    private const val MOONSHOT_BASE_URL = "https://api.moonshot.ai/v1"

    /** OpenClaw auth-profiles-UpqQjKB-.js:2090 */
    private const val VOLCENGINE_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"

    /** OpenClaw auth-profiles-UpqQjKB-.js:2543 */
    private const val QIANFAN_BASE_URL = "https://qianfan.baidubce.com/v2"

    /** MiMo — 对齐 OpenClaw 官方配置 */
    private const val XIAOMI_BASE_URL = "https://api.xiaomimimo.com/anthropic"

    /** OpenClaw auth-profiles-UpqQjKB-.js:2337 */
    private const val TOGETHER_BASE_URL = "https://api.together.xyz/v1"

    /** OpenClaw auth-profiles-UpqQjKB-.js:301 */
    private const val HUGGINGFACE_BASE_URL = "https://router.huggingface.co/v1"

    /** OpenClaw auth-profiles-UpqQjKB-.js:1808 + 734 */
    private const val OLLAMA_BASE_URL = "http://127.0.0.1:11434"

    /** DeepSeek — 行业标准 URL，OpenClaw 通过 OpenRouter/custom 访问 */
    private const val DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1"

    /** Mistral — 行业标准 URL */
    private const val MISTRAL_BASE_URL = "https://api.mistral.ai/v1"

    /** OpenClaw auth-profiles-UpqQjKB-.js:2553 */
    private const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"

    /** OpenClaw auth-profiles-UpqQjKB-.js:2145 */
    private const val SYNTHETIC_BASE_URL = "https://api.synthetic.new/anthropic"

    /** OpenClaw auth-profiles-UpqQjKB-.js:1115 */
    private const val VENICE_BASE_URL = "https://api.venice.ai/api/v1"

    /** OpenClaw auth-profiles-UpqQjKB-.js:2514 */
    private const val KIMI_CODING_BASE_URL = "https://api.kimi.com/coding/"

    /** OpenClaw auth-profiles-UpqQjKB-.js:2058 */
    private const val BYTEPLUS_BASE_URL = "https://ark.ap-southeast.bytepluses.com/api/v3"

    // ========== Provider Definitions ==========

    val OPENROUTER = ProviderDefinition(
        id = "openrouter",
        name = "OpenRouter",
        description = "聚合平台，免费+付费模型",
        baseUrl = OPENROUTER_BASE_URL,
        api = ModelApi.OPENAI_COMPLETIONS,
        keyRequired = false, // 有内置免费 key
        keyHint = "OpenRouter API Key (sk-or-v1-...)",
        envVarName = "OPENROUTER_API_KEY",
        tutorialSteps = listOf(
            "打开 openrouter.ai/keys",
            "登录或注册账号",
            "点击 \"Create Key\"",
            "复制 API Key"
        ),
        tutorialUrl = "https://openrouter.ai/keys",
        presetModels = listOf(
            PresetModel(
                id = "openrouter/hunter-alpha",
                name = "Hunter Alpha (免费)",
                free = true,
                contextWindow = 1048576,
                maxTokens = 65536,
                reasoning = false,
                input = listOf("text", "image")
            ),
            PresetModel(
                id = "deepseek/deepseek-r1:free",
                name = "DeepSeek R1 (免费)",
                free = true,
                contextWindow = 163840,
                maxTokens = 8192,
                reasoning = true,
                input = listOf("text")
            ),
            PresetModel(
                id = "google/gemini-2.5-flash-preview:thinking",
                name = "Gemini 2.5 Flash (免费)",
                free = true,
                contextWindow = 1048576,
                maxTokens = 65536,
                reasoning = true,
                input = listOf("text", "image")
            ),
            PresetModel(
                id = "anthropic/claude-sonnet-4",
                name = "Claude Sonnet 4",
                free = false,
                contextWindow = 200000,
                maxTokens = 16384,
                reasoning = true,
                input = listOf("text", "image")
            ),
            PresetModel(
                id = "openai/gpt-4.1",
                name = "GPT-4.1",
                free = false,
                contextWindow = 1048576,
                maxTokens = 32768,
                reasoning = false,
                input = listOf("text", "image")
            )
        ),
        supportsDiscovery = true,
        group = ProviderGroup.PRIMARY,
        order = 10
    )

    val ANTHROPIC = ProviderDefinition(
        id = "anthropic",
        name = "Anthropic",
        description = "Claude 系列",
        baseUrl = ANTHROPIC_BASE_URL,
        api = ModelApi.ANTHROPIC_MESSAGES,
        keyRequired = true,
        keyHint = "Anthropic API Key (sk-ant-...)",
        envVarName = "ANTHROPIC_API_KEY",
        authHeader = false, // Anthropic 用 x-api-key header
        tutorialSteps = listOf(
            "打开 console.anthropic.com",
            "登录或注册账号",
            "进入 API Keys 页面",
            "点击 \"Create Key\" 并复制"
        ),
        tutorialUrl = "https://console.anthropic.com/settings/keys",
        presetModels = listOf(
            PresetModel(
                id = "claude-opus-4",
                name = "Claude Opus 4",
                contextWindow = 200000,
                maxTokens = 32768,
                reasoning = true,
                input = listOf("text", "image")
            ),
            PresetModel(
                id = "claude-sonnet-4",
                name = "Claude Sonnet 4",
                contextWindow = 200000,
                maxTokens = 16384,
                reasoning = true,
                input = listOf("text", "image")
            ),
            PresetModel(
                id = "claude-haiku-3.5",
                name = "Claude Haiku 3.5",
                contextWindow = 200000,
                maxTokens = 8192,
                reasoning = false,
                input = listOf("text", "image")
            )
        ),
        supportsDiscovery = false, // Anthropic 无 /v1/models 端点
        group = ProviderGroup.PRIMARY,
        order = 20
    )

    val OPENAI = ProviderDefinition(
        id = "openai",
        name = "OpenAI",
        description = "GPT 系列",
        baseUrl = OPENAI_BASE_URL,
        api = ModelApi.OPENAI_COMPLETIONS,
        keyRequired = true,
        keyHint = "OpenAI API Key (sk-...)",
        envVarName = "OPENAI_API_KEY",
        tutorialSteps = listOf(
            "打开 platform.openai.com",
            "登录或注册账号",
            "进入 API Keys 页面",
            "点击 \"Create new secret key\" 并复制"
        ),
        tutorialUrl = "https://platform.openai.com/api-keys",
        presetModels = listOf(
            PresetModel(
                id = "gpt-4.1",
                name = "GPT-4.1",
                contextWindow = 1048576,
                maxTokens = 32768,
                reasoning = false,
                input = listOf("text", "image")
            ),
            PresetModel(
                id = "gpt-4.1-mini",
                name = "GPT-4.1 Mini",
                contextWindow = 1048576,
                maxTokens = 32768,
                reasoning = false,
                input = listOf("text", "image")
            ),
            PresetModel(
                id = "o4-mini",
                name = "o4 Mini",
                contextWindow = 200000,
                maxTokens = 100000,
                reasoning = true,
                input = listOf("text", "image")
            )
        ),
        supportsDiscovery = true,
        group = ProviderGroup.PRIMARY,
        order = 30
    )

    val GOOGLE = ProviderDefinition(
        id = "google",
        name = "Google (Gemini)",
        description = "Gemini 系列",
        baseUrl = GEMINI_BASE_URL,
        api = ModelApi.GOOGLE_GENERATIVE_AI,
        keyRequired = true,
        keyHint = "Gemini API Key",
        envVarName = "GEMINI_API_KEY",
        tutorialSteps = listOf(
            "打开 aistudio.google.com",
            "登录 Google 账号",
            "点击 \"Get API key\"",
            "创建或选择项目并复制 Key"
        ),
        tutorialUrl = "https://aistudio.google.com/apikey",
        presetModels = listOf(
            PresetModel(
                id = "gemini-2.5-pro",
                name = "Gemini 2.5 Pro",
                contextWindow = 1048576,
                maxTokens = 65536,
                reasoning = true,
                input = listOf("text", "image")
            ),
            PresetModel(
                id = "gemini-2.5-flash",
                name = "Gemini 2.5 Flash",
                contextWindow = 1048576,
                maxTokens = 65536,
                reasoning = true,
                input = listOf("text", "image")
            )
        ),
        supportsDiscovery = false, // Google 用自己的 API 格式
        group = ProviderGroup.PRIMARY,
        order = 40
    )

    val DEEPSEEK = ProviderDefinition(
        id = "deepseek",
        name = "DeepSeek",
        description = "DeepSeek 系列",
        baseUrl = DEEPSEEK_BASE_URL,
        api = ModelApi.OPENAI_COMPLETIONS,
        keyRequired = true,
        keyHint = "DeepSeek API Key",
        envVarName = "DEEPSEEK_API_KEY",
        tutorialSteps = listOf(
            "打开 platform.deepseek.com",
            "登录或注册账号",
            "进入 API Keys 页面",
            "创建并复制 API Key"
        ),
        tutorialUrl = "https://platform.deepseek.com/api_keys",
        presetModels = listOf(
            PresetModel(
                id = "deepseek-chat",
                name = "DeepSeek Chat (V3)",
                contextWindow = 65536,
                maxTokens = 8192,
                reasoning = false,
                input = listOf("text")
            ),
            PresetModel(
                id = "deepseek-reasoner",
                name = "DeepSeek R1",
                contextWindow = 65536,
                maxTokens = 8192,
                reasoning = true,
                input = listOf("text")
            )
        ),
        supportsDiscovery = true,
        group = ProviderGroup.PRIMARY,
        order = 50
    )

    val XAI = ProviderDefinition(
        id = "xai",
        name = "xAI",
        description = "Grok 系列",
        baseUrl = XAI_BASE_URL,
        api = ModelApi.OPENAI_COMPLETIONS,
        keyRequired = true,
        keyHint = "xAI API Key",
        envVarName = "XAI_API_KEY",
        tutorialSteps = listOf(
            "打开 console.x.ai",
            "登录 X/xAI 账号",
            "创建 API Key 并复制"
        ),
        tutorialUrl = "https://console.x.ai/",
        presetModels = listOf(
            PresetModel(
                id = "grok-3",
                name = "Grok 3",
                contextWindow = 131072,
                maxTokens = 8192,
                reasoning = false,
                input = listOf("text", "image")
            ),
            PresetModel(
                id = "grok-3-mini",
                name = "Grok 3 Mini",
                contextWindow = 131072,
                maxTokens = 8192,
                reasoning = true,
                input = listOf("text")
            )
        ),
        supportsDiscovery = true,
        group = ProviderGroup.PRIMARY,
        order = 60
    )

    val OLLAMA = ProviderDefinition(
        id = "ollama",
        name = "Ollama (本地)",
        description = "本地模型，无需 API Key",
        baseUrl = OLLAMA_BASE_URL,
        api = ModelApi.OLLAMA,
        keyRequired = false,
        keyHint = "API Key (可选)",
        envVarName = "OLLAMA_API_KEY",
        tutorialSteps = listOf(
            "安装 Ollama: ollama.com/download",
            "运行模型: ollama run qwen2.5:7b",
            "确保 Ollama 在同一局域网内运行",
            "如在其他设备上运行，修改 Base URL"
        ),
        tutorialUrl = "https://ollama.com/download",
        presetModels = listOf(), // 通过 discovery 自动获取
        supportsDiscovery = true,
        discoveryEndpoint = "/api/tags", // Ollama 专用端点
        group = ProviderGroup.PRIMARY,
        order = 70
    )

    // ========== MORE Group (折叠) ==========

    val VOLCENGINE = ProviderDefinition(
        id = "volcengine",
        name = "火山引擎 (豆包)",
        description = "字节跳动大模型平台",
        baseUrl = VOLCENGINE_BASE_URL,
        api = ModelApi.OPENAI_COMPLETIONS,
        keyRequired = true,
        keyHint = "火山引擎 API Key",
        envVarName = "VOLCANO_ENGINE_API_KEY",
        tutorialSteps = listOf(
            "打开 console.volcengine.com",
            "进入 AI 大模型平台",
            "创建 API Key 并复制"
        ),
        tutorialUrl = "https://console.volcengine.com/ark",
        presetModels = listOf(
            PresetModel(
                id = "doubao-1.5-pro-256k",
                name = "豆包 1.5 Pro",
                contextWindow = 262144,
                maxTokens = 12288,
                reasoning = false,
                input = listOf("text")
            )
        ),
        supportsDiscovery = true,
        group = ProviderGroup.MORE,
        order = 110
    )

    val MOONSHOT = ProviderDefinition(
        id = "moonshot",
        name = "Moonshot (Kimi)",
        description = "月之暗面 Kimi 大模型",
        baseUrl = MOONSHOT_BASE_URL,
        api = ModelApi.OPENAI_COMPLETIONS,
        keyRequired = true,
        keyHint = "Moonshot API Key",
        envVarName = "MOONSHOT_API_KEY",
        tutorialSteps = listOf(
            "打开 platform.moonshot.cn",
            "登录或注册账号",
            "进入 API 管理页面",
            "创建并复制 API Key"
        ),
        tutorialUrl = "https://platform.moonshot.cn/console/api-keys",
        presetModels = listOf(
            PresetModel(
                id = "moonshot-v1-auto",
                name = "Moonshot V1 Auto",
                contextWindow = 128000,
                maxTokens = 8192,
                reasoning = false,
                input = listOf("text")
            )
        ),
        supportsDiscovery = true,
        group = ProviderGroup.MORE,
        order = 120
    )

    val QIANFAN = ProviderDefinition(
        id = "qianfan",
        name = "百度千帆",
        description = "百度文心大模型平台",
        baseUrl = QIANFAN_BASE_URL,
        api = ModelApi.OPENAI_COMPLETIONS,
        keyRequired = true,
        keyHint = "千帆 API Key",
        envVarName = "QIANFAN_API_KEY",
        tutorialSteps = listOf(
            "打开 qianfan.cloud.baidu.com",
            "登录百度账号",
            "进入 API Key 管理",
            "创建并复制 API Key"
        ),
        tutorialUrl = "https://qianfan.cloud.baidu.com/",
        presetModels = listOf(
            PresetModel(
                id = "ernie-4.5-8k",
                name = "ERNIE 4.5",
                contextWindow = 8192,
                maxTokens = 4096,
                reasoning = false,
                input = listOf("text")
            )
        ),
        supportsDiscovery = true,
        group = ProviderGroup.MORE,
        order = 130
    )

    val XIAOMI = ProviderDefinition(
        id = "xiaomi", // 对齐 OpenClaw 官方配置
        name = "小米 (MiMo)",
        description = "小米 MiMo 大模型",
        baseUrl = XIAOMI_BASE_URL,
        api = ModelApi.ANTHROPIC_MESSAGES, // 对齐 OpenClaw 官方配置
        keyRequired = true,
        keyHint = "小米 API Key",
        envVarName = "XIAOMI_API_KEY",
        tutorialSteps = listOf(
            "打开 api.xiaomimimo.com",
            "登录小米账号",
            "获取 API Key"
        ),
        tutorialUrl = "https://api.xiaomimimo.com/",
        presetModels = listOf(
            PresetModel("mimo-v2-flash", "MiMo V2 Flash (免费，262K)", reasoning = false, contextWindow = 262144, maxTokens = 8192),
            PresetModel("mimo-v2-pro", "MiMo V2 Pro (128K)", reasoning = false, contextWindow = 128000, maxTokens = 16384)
        ),
        supportsDiscovery = false,
        group = ProviderGroup.PRIMARY,
        order = 140
    )

    val MISTRAL = ProviderDefinition(
        id = "mistral",
        name = "Mistral",
        description = "Mistral AI 系列",
        baseUrl = MISTRAL_BASE_URL,
        api = ModelApi.OPENAI_COMPLETIONS,
        keyRequired = true,
        keyHint = "Mistral API Key",
        envVarName = "MISTRAL_API_KEY",
        tutorialSteps = listOf(
            "打开 console.mistral.ai",
            "登录或注册账号",
            "进入 API Keys 页面",
            "创建并复制 API Key"
        ),
        tutorialUrl = "https://console.mistral.ai/api-keys",
        presetModels = listOf(
            PresetModel(
                id = "mistral-large-latest",
                name = "Mistral Large",
                contextWindow = 131072,
                maxTokens = 8192,
                reasoning = false,
                input = listOf("text")
            )
        ),
        supportsDiscovery = true,
        group = ProviderGroup.MORE,
        order = 150
    )

    val TOGETHER = ProviderDefinition(
        id = "together",
        name = "Together AI",
        description = "开源模型云平台",
        baseUrl = TOGETHER_BASE_URL,
        api = ModelApi.OPENAI_COMPLETIONS,
        keyRequired = true,
        keyHint = "Together API Key",
        envVarName = "TOGETHER_API_KEY",
        tutorialSteps = listOf(
            "打开 api.together.ai",
            "登录或注册账号",
            "进入 Settings → API Keys",
            "复制 API Key"
        ),
        tutorialUrl = "https://api.together.ai/settings/api-keys",
        presetModels = emptyList(), // 太多了，用 discovery
        supportsDiscovery = true,
        group = ProviderGroup.MORE,
        order = 160
    )

    val HUGGINGFACE = ProviderDefinition(
        id = "huggingface",
        name = "Hugging Face",
        description = "开源模型社区推理 API",
        baseUrl = HUGGINGFACE_BASE_URL,
        api = ModelApi.OPENAI_COMPLETIONS,
        keyRequired = true,
        keyHint = "Hugging Face Token (hf_...)",
        envVarName = "HF_TOKEN",
        tutorialSteps = listOf(
            "打开 huggingface.co/settings/tokens",
            "登录或注册账号",
            "创建 Access Token (Read scope)",
            "复制 Token"
        ),
        tutorialUrl = "https://huggingface.co/settings/tokens",
        presetModels = emptyList(),
        supportsDiscovery = true,
        group = ProviderGroup.MORE,
        order = 170
    )

    val NVIDIA = ProviderDefinition(
        id = "nvidia",
        name = "NVIDIA NIM",
        description = "NVIDIA 推理微服务",
        baseUrl = NVIDIA_BASE_URL,
        api = ModelApi.OPENAI_COMPLETIONS,
        keyRequired = true,
        keyHint = "NVIDIA API Key",
        envVarName = "NVIDIA_API_KEY",
        tutorialSteps = listOf(
            "打开 build.nvidia.com",
            "登录 NVIDIA 账号",
            "获取 API Key"
        ),
        tutorialUrl = "https://build.nvidia.com/",
        presetModels = emptyList(),
        supportsDiscovery = true,
        group = ProviderGroup.MORE,
        order = 180
    )

    // ========== CUSTOM Group ==========

    val CUSTOM = ProviderDefinition(
        id = "custom",
        name = "自定义 (OpenAI 兼容)",
        description = "vLLM, LiteLLM, LocalAI 等 OpenAI 兼容服务",
        baseUrl = "", // 用户填入
        api = ModelApi.OPENAI_COMPLETIONS, // 默认，可改
        keyRequired = false,
        keyHint = "API Key (可选)",
        envVarName = "",
        tutorialSteps = listOf(
            "填入你的 API Base URL",
            "输入 API Key（如需要）",
            "手动填入模型 ID",
            "选择 API 类型（OpenAI 兼容或 Anthropic 兼容）"
        ),
        tutorialUrl = "",
        presetModels = emptyList(),
        supportsDiscovery = true,
        group = ProviderGroup.CUSTOM,
        order = 999
    )

    // ========== Registry ==========

    /** 所有已注册 Provider，按 order 排序 */
    val ALL: List<ProviderDefinition> = listOf(
        OPENROUTER, ANTHROPIC, OPENAI, GOOGLE, DEEPSEEK, XAI, OLLAMA,
        VOLCENGINE, MOONSHOT, QIANFAN, XIAOMI, MISTRAL, TOGETHER,
        HUGGINGFACE, NVIDIA, CUSTOM
    ).sortedBy { it.order }

    /** 按 ID 查找 Provider */
    private val BY_ID: Map<String, ProviderDefinition> = ALL.associateBy { it.id }

    /** 按 group 分组 */
    val PRIMARY_PROVIDERS: List<ProviderDefinition> = ALL.filter { it.group == ProviderGroup.PRIMARY }
    val MORE_PROVIDERS: List<ProviderDefinition> = ALL.filter { it.group == ProviderGroup.MORE }
    val CUSTOM_PROVIDERS: List<ProviderDefinition> = ALL.filter { it.group == ProviderGroup.CUSTOM }

    /**
     * 根据 ID 查找 Provider
     * 支持 OpenClaw 的 normalizeProviderId 别名
     */
    fun findById(id: String): ProviderDefinition? {
        val normalized = normalizeProviderId(id)
        return BY_ID[normalized]
    }

    /**
     * 对齐 OpenClaw normalizeProviderId()
     * 来源: auth-profiles-UpqQjKB-.js normalizeProviderId()
     */
    fun normalizeProviderId(provider: String): String {
        val normalized = provider.trim().lowercase()
        return when (normalized) {
            "z.ai", "z-ai" -> "zai"
            "opencode-zen" -> "opencode"
            "qwen" -> "qwen-portal"
            "kimi-code" -> "kimi-coding"
            "bedrock", "aws-bedrock" -> "amazon-bedrock"
            "bytedance", "doubao" -> "volcengine"
            else -> normalized
        }
    }

    /**
     * 根据 ProviderDefinition 生成 ProviderConfig
     * 用于写入 openclaw.json
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
            models = models
        )
    }

    /**
     * 生成完整的模型引用 ID
     * 格式: "provider/modelId"
     */
    fun buildModelRef(providerId: String, modelId: String): String {
        return "$providerId/$modelId"
    }

    /**
     * 自定义 Provider 支持的 API 类型列表（用于 Spinner）
     */
    val CUSTOM_API_TYPES = listOf(
        ModelApi.OPENAI_COMPLETIONS to "OpenAI Compatible",
        ModelApi.ANTHROPIC_MESSAGES to "Anthropic Compatible",
        ModelApi.OLLAMA to "Ollama"
    )
}

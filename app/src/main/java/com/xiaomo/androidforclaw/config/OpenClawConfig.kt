package com.xiaomo.androidforclaw.config

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/config/config.ts, types.openclaw.ts, types.agents.ts, types.channels.ts, types.gateway.ts, types.models.ts, types.skills.ts, types.memory.ts, types.tools.ts, types.cron.ts
 *
 * AndroidForClaw adaptation: Kotlin config model aligned to OpenClaw schema.
 */

import com.xiaomo.androidforclaw.workspace.StoragePaths

/**
 * OpenClaw Config — 对齐 OpenClaw types.openclaw.d.ts
 *
 * 用户只写想覆盖的字段，其余全用默认值。
 * 解析由 ConfigLoader 的 JSONObject 处理。
 */

data class OpenClawConfig(
    // ======= OpenClaw 标准段 =======
    val thinking: ThinkingConfig = ThinkingConfig(),
    val models: ModelsConfig? = null,
    val agents: AgentsConfig? = null,
    val channels: ChannelsConfig = ChannelsConfig(),
    val gateway: GatewayConfig = GatewayConfig(),
    val skills: SkillsConfig = SkillsConfig(),
    val plugins: PluginsConfig = PluginsConfig(),
    val tools: ToolsConfig = ToolsConfig(),
    val memory: MemoryConfig = MemoryConfig(),
    val messages: MessagesConfig = MessagesConfig(),
    val session: SessionConfig = SessionConfig(),
    val hooks: HooksConfig? = null,
    val logging: LoggingConfig = LoggingConfig(),
    val ui: UIConfig = UIConfig(),

    // ======= Model Aliases & Allowlist (OpenClaw model-selection.ts) =======
    val modelAliases: Map<String, String> = emptyMap(),
    val modelAllowlist: ModelAllowlistConfig? = null,

    // ======= Android 扩展 =======
    val agent: AgentConfig = AgentConfig(),

    // ======= Legacy =======
    val providers: Map<String, ProviderConfig> = emptyMap()
) {
    /** 解析 providers：优先 models.providers；若为空则 fallback 到顶层 providers */
    fun resolveProviders(): Map<String, ProviderConfig> {
        val modelProviders = models?.providers
        return if (!modelProviders.isNullOrEmpty()) modelProviders else providers
    }

    /** 解析默认模型 */
    fun resolveDefaultModel(): String {
        // 1. Explicit primary model
        agents?.defaults?.model?.primary?.let { return it }
        // 2. Fall back to first configured provider's first model (not hardcoded openrouter)
        val providers = resolveProviders()
        val first = providers.entries.firstOrNull()
        if (first != null) {
            val modelId = first.value.models.firstOrNull()?.id
            if (modelId != null) return "${first.key}/$modelId"
        }
        // 3. Ultimate fallback
        return agent.defaultModel
    }

    /** 兼容旧代码：gateway.feishu → channels.feishu */
    val feishuConfig: FeishuChannelConfig get() = channels.feishu
}

// ============ channels（对齐 types.channels.d.ts）============

data class ChannelsConfig(
    val feishu: FeishuChannelConfig = FeishuChannelConfig(),
    val discord: DiscordChannelConfig? = null,
    val slack: SlackChannelConfig? = null,
    val telegram: TelegramChannelConfig? = null,
    val whatsapp: WhatsAppChannelConfig? = null,
    val signal: SignalChannelConfig? = null,
    val weixin: WeixinChannelConfig? = null,
)

data class FeishuChannelConfig(
    // 基础
    val enabled: Boolean = false,
    val appId: String = "",
    val appSecret: String = "",
    val encryptKey: String? = null,
    val verificationToken: String? = null,
    val domain: String = "feishu",
    val connectionMode: String = "websocket",
    val webhookPath: String = "/feishu/events",
    val webhookHost: String? = null,
    val webhookPort: Int? = null,
    // 策略
    val dmPolicy: String = "open",
    val allowFrom: List<String> = emptyList(),
    val groupPolicy: String = "open",
    val groupAllowFrom: List<String> = emptyList(),
    val requireMention: Boolean? = null, // null = use groupPolicy-based default (open→false, else→true)
    val groupCommandMentionBypass: String = "never",
    val allowMentionlessInMultiBotGroup: Boolean = false,
    val groupSessionScope: String? = null,
    val topicSessionMode: String = "disabled",
    val replyInThread: String = "disabled",
    // 历史 (aligned with OpenClaw: optional, no limit if not configured)
    val historyLimit: Int? = null,
    val dmHistoryLimit: Int? = null,
    // 消息
    val textChunkLimit: Int = 4000,
    val chunkMode: String = "length",
    val renderMode: String = "auto",
    val streaming: Boolean? = null,
    // 媒体
    val mediaMaxMb: Double = 20.0,
    // 工具
    val tools: FeishuToolsConfig = FeishuToolsConfig(),
    // 队列（Android 扩展）
    val queueMode: String? = "followup",
    val queueCap: Int = 10,
    val queueDropPolicy: String = "old",
    val queueDebounceMs: Int = 100,
    // UX
    val typingIndicator: Boolean = true,
    val resolveSenderNames: Boolean = true,
    val reactionNotifications: String = "own",
    val reactionDedup: Boolean = true,
    // 调试
    val debugMode: Boolean = false,
    // 多账号
    val accounts: Map<String, FeishuAccountConfig>? = null,
    val defaultAccount: String? = null
)

data class FeishuToolsConfig(
    val doc: Boolean = true,
    val chat: Boolean = true,
    val wiki: Boolean = true,
    val drive: Boolean = true,
    val perm: Boolean = false,
    val scopes: Boolean = true,
    val bitable: Boolean = true,
    val task: Boolean = true,
    val urgent: Boolean = true
)

data class FeishuAccountConfig(
    val enabled: Boolean = true,
    val name: String? = null,
    val appId: String? = null,
    val appSecret: String? = null,
    val domain: String? = null,
    val connectionMode: String? = null,
    val webhookPath: String? = null
)

data class DiscordChannelConfig(
    val enabled: Boolean = false,
    val token: String? = null,
    val name: String? = null,
    val dm: DmPolicyConfig? = null,
    val groupPolicy: String? = null,
    val guilds: Map<String, GuildPolicyConfig>? = null,
    val replyToMode: String? = null,
    val accounts: Map<String, DiscordAccountPolicyConfig>? = null,
    val historyLimit: Int? = null,
    val dmHistoryLimit: Int? = null
)

data class DmPolicyConfig(
    val policy: String? = "pairing",
    val allowFrom: List<String>? = null
)

data class GuildPolicyConfig(
    val channels: List<String>? = null,
    val requireMention: Boolean? = true,
    val toolPolicy: String? = null
)

data class DiscordAccountPolicyConfig(
    val enabled: Boolean? = true,
    val token: String? = null,
    val name: String? = null,
    val dm: DmPolicyConfig? = null,
    val guilds: Map<String, GuildPolicyConfig>? = null
)

data class SlackChannelConfig(
    // 对齐 OpenClaw types.slack.ts SlackAccountConfig
    val enabled: Boolean = false,
    /** Bot Token (xoxb-...) */
    val botToken: String = "",
    /** App-Level Token (xapp-...) — socket 模式必填 */
    val appToken: String? = null,
    /** Signing Secret — http 模式必填 */
    val signingSecret: String? = null,
    /** 连接模式: "socket"(默认) 或 "http" */
    val mode: String = "socket",
    val dmPolicy: String = "open",
    val groupPolicy: String = "open",
    val requireMention: Boolean = true,
    val historyLimit: Int? = null,
    val dmHistoryLimit: Int? = null,
    /** 流式回复模式: off / partial / block / progress */
    val streaming: String = "partial",
    /** Android 扩展：覆盖该渠道使用的模型，格式 "providerId/modelId"，为空则使用全局默认 */
    val model: String? = null
)

data class TelegramChannelConfig(
    // 对齐 OpenClaw types.telegram.ts TelegramAccountConfig
    val enabled: Boolean = false,
    /** Bot Token (from @BotFather) */
    val botToken: String = "",
    val dmPolicy: String = "open",
    val groupPolicy: String = "open",
    val requireMention: Boolean = true,
    val historyLimit: Int? = null,
    val dmHistoryLimit: Int? = null,
    /** 流式回复模式: off / partial / block / progress */
    val streaming: String = "partial",
    /** Webhook URL (可选，不填则使用长轮询) */
    val webhookUrl: String? = null,
    /** Android 扩展：覆盖该渠道使用的模型，格式 "providerId/modelId"，为空则使用全局默认 */
    val model: String? = null
)

data class WhatsAppChannelConfig(
    // 对齐 OpenClaw types.whatsapp.ts
    val enabled: Boolean = false,
    /** 注册 WhatsApp 的手机号 (E.164 格式，如 +8613800138000) */
    val phoneNumber: String = "",
    val dmPolicy: String = "open",
    val groupPolicy: String = "open",
    val requireMention: Boolean = true,
    val historyLimit: Int? = null,
    val dmHistoryLimit: Int? = null,
    /** Android 扩展：覆盖该渠道使用的模型，格式 "providerId/modelId"，为空则使用全局默认 */
    val model: String? = null
)

data class SignalChannelConfig(
    // 对齐 OpenClaw types.signal.ts SignalAccountConfig
    val enabled: Boolean = false,
    /** signal-cli 注册的手机号 (E.164，对应 OpenClaw account 字段) */
    val phoneNumber: String = "",
    /** signal-cli HTTP daemon 完整 URL，如 http://127.0.0.1:8080 (可选) */
    val httpUrl: String? = null,
    /** signal-cli HTTP daemon 端口，默认 8080 */
    val httpPort: Int = 8080,
    val dmPolicy: String = "open",
    val groupPolicy: String = "open",
    val requireMention: Boolean = true,
    val historyLimit: Int? = null,
    val dmHistoryLimit: Int? = null,
    /** Android 扩展：覆盖该渠道使用的模型，格式 "providerId/modelId"，为空则使用全局默认 */
    val model: String? = null
)

data class WeixinChannelConfig(
    // 对齐 @tencent-weixin/openclaw-weixin
    val enabled: Boolean = false,
    /** API base URL (default: https://ilinkai.weixin.qq.com) */
    val baseUrl: String = "https://ilinkai.weixin.qq.com",
    /** CDN base URL */
    val cdnBaseUrl: String = "https://novac2c.cdn.weixin.qq.com/c2c",
    /** Route tag for API requests */
    val routeTag: String? = null,
    /** Android 扩展：覆盖该渠道使用的模型，格式 "providerId/modelId"，为空则使用全局默认 */
    val model: String? = null
)

// ============ gateway（对齐 types.gateway.d.ts）============

data class GatewayConfig(
    val port: Int = 19789,
    val mode: String = "local",
    val bind: String = "loopback",
    val auth: GatewayAuthConfig? = null,
    val controlUi: GatewayControlUiConfig? = null
)

data class GatewayAuthConfig(
    val mode: String = "token",
    val token: String? = null
)

data class GatewayControlUiConfig(
    val allowInsecureAuth: Boolean? = null,
    val dangerouslyAllowHostHeaderOriginFallback: Boolean? = null,
    val dangerouslyDisableDeviceAuth: Boolean? = null
)

// ============ agents（对齐 types.agents.d.ts）============

data class AgentsConfig(
    val defaults: AgentDefaultsConfig = AgentDefaultsConfig()
)

data class AgentDefaultsConfig(
    val model: ModelSelectionConfig? = null,
    val bootstrapMaxChars: Int = 20_000,
    val bootstrapTotalMaxChars: Int = 150_000,
    val maxConcurrent: Int = 5,
    val subagents: SubagentsConfig = SubagentsConfig()
)

/**
 * Subagent configuration — aligned with OpenClaw agents.defaults.subagents
 * (src/config/types.agent-defaults.ts)
 */
data class SubagentsConfig(
    /** Max concurrent subagent runs per parent session */
    val maxConcurrent: Int = 1,
    /** Max nesting depth (1 = first-level subagents are leaves, cannot spawn further) */
    val maxSpawnDepth: Int = 1,
    /** Max children a single parent session can spawn */
    val maxChildrenPerAgent: Int = 5,
    /** Default per-run timeout in seconds (0 = no timeout) */
    val defaultTimeoutSeconds: Int = 300,
    /** Default model override for subagents (null = use parent model) */
    val model: String? = null,
    /** Default thinking level for subagents */
    val thinking: String? = null,
    /** Master switch */
    val enabled: Boolean = true
)

data class ModelSelectionConfig(
    val primary: String? = null,
    val fallbacks: List<String>? = null
)

// ============ agent（Android 扩展，非 OpenClaw 标准）============

data class AgentConfig(
    val maxIterations: Int = 40,
    val defaultModel: String = "openrouter/hunter-alpha",
    val timeout: Long = 300000,
    val retryOnError: Boolean = true,
    val maxRetries: Int = 3,
    val mode: String = "exploration"
)

// ============ skills（对齐 types.skills.d.ts）============

data class SkillsConfig(
    val allowBundled: List<String>? = null,
    val extraDirs: List<String> = emptyList(),
    val watch: Boolean = true,
    val watchDebounceMs: Long = 250,
    val entries: Map<String, SkillConfig> = emptyMap()
)

data class SkillConfig(
    val enabled: Boolean = true,
    val apiKey: Any? = null,
    val env: Map<String, String>? = null,
    val config: Map<String, Any>? = null
) {
    fun resolveApiKey(): String? {
        return when (apiKey) {
            is String -> apiKey
            is Map<*, *> -> {
                val source = apiKey["source"] as? String
                val id = apiKey["id"] as? String
                if (source == "env" && id != null) System.getenv(id) else null
            }
            else -> null
        }
    }
}

// ============ plugins（对齐 types.plugins.d.ts）============

data class PluginsConfig(
    val entries: Map<String, PluginEntry> = emptyMap()
)

data class PluginEntry(
    val enabled: Boolean = false,
    val skills: List<String> = emptyList()
)

// ============ tools（对齐 types.tools.d.ts）============

data class ToolsConfig(
    val screenshot: ScreenshotToolConfig = ScreenshotToolConfig(),
    val exec: ToolsExecConfig? = null
)

data class ToolsExecConfig(
    val applyPatch: ToolsApplyPatchConfig? = null
)

data class ToolsApplyPatchConfig(
    val workspaceOnly: Boolean? = null
)

data class ScreenshotToolConfig(
    val enabled: Boolean = true,
    val quality: Int = 85,
    val maxWidth: Int = 1080,
    val format: String = "jpeg"
)

// ============ hooks（对齐 types.hooks.d.ts）============

data class HooksConfig(
    val gmail: HooksGmailConfig? = null,
    val mappings: List<HooksMappingConfig> = emptyList()
)

data class HooksGmailConfig(
    val allowUnsafeExternalContent: Boolean? = null
)

data class HooksMappingConfig(
    val allowUnsafeExternalContent: Boolean? = null
)

// ============ messages ============

data class MessagesConfig(
    val ackReactionScope: String = "own"
)

// ============ memory（对齐 types.memory.d.ts）============

data class MemoryConfig(
    val enabled: Boolean = true,
    val path: String = StoragePaths.workspaceMemory.absolutePath
)

// ============ session / logging / ui ============

data class SessionConfig(
    val maxMessages: Int = 100,
    // OpenClaw store-maintenance.ts alignment
    val maxAgeDays: Int = 30,
    val maxEntries: Int = 500,
    val maxDiskBytes: Long = 100_000_000L,  // 100MB default
    val highWaterRatio: Float = 0.8f
)

data class LoggingConfig(
    val level: String = "INFO",
    val logToFile: Boolean = true
)

data class UIConfig(
    val theme: String = "auto",
    val language: String = "zh"
)

// ============ thinking ============

data class ThinkingConfig(
    val enabled: Boolean = true,
    val budgetTokens: Int = 10000
)

// ============ 配置常量 ============

object ConfigDefaults {
    const val DEFAULT_MAX_ITERATIONS = 20
    const val DEFAULT_TIMEOUT_MS = 300000L
    const val DEFAULT_SCREENSHOT_QUALITY = 85
    const val DEFAULT_SCREENSHOT_MAX_WIDTH = 1080
    const val DEFAULT_GATEWAY_PORT = 19789
}

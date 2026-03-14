package com.xiaomo.feishu

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel runtime.
 */


/**
 * 飞书配置
 * 对齐 OpenClaw feishu plugin 配置结构
 */
data class FeishuConfig(
    // ===== 基础配置 =====
    val enabled: Boolean = false,
    val appId: String,
    val appSecret: String,
    val encryptKey: String? = null,
    val verificationToken: String? = null,

    // ===== 域名配置 =====
    val domain: String = "feishu", // "feishu", "lark", or custom domain

    // ===== 连接模式 =====
    val connectionMode: ConnectionMode = ConnectionMode.WEBSOCKET,
    val webhookPath: String = "/feishu/webhook",
    val webhookPort: Int = 8765,

    // ===== DM 策略 =====
    val dmPolicy: DmPolicy = DmPolicy.PAIRING,
    val allowFrom: List<String> = emptyList(),

    // ===== 群组策略 =====
    val groupPolicy: GroupPolicy = GroupPolicy.ALLOWLIST,
    val groupAllowFrom: List<String> = emptyList(),
    val requireMention: Boolean = true,
    val groupCommandMentionBypass: MentionBypass = MentionBypass.NEVER,
    val allowMentionlessInMultiBotGroup: Boolean = false,

    // ===== 会话模式 =====
    val topicSessionMode: TopicSessionMode = TopicSessionMode.DISABLED,

    // ===== 历史记录 =====
    val historyLimit: Int = 20,
    val dmHistoryLimit: Int = 10,

    // ===== 消息分块 =====
    val textChunkLimit: Int = 4000,
    val chunkMode: ChunkMode = ChunkMode.LENGTH,
    val maxTablesPerCard: Int = 3,  // 飞书卡片最多支持的表格数量 (根据 API 限制)

    // ===== 媒体配置 =====
    val mediaMaxMb: Double = 20.0,
    val audioMaxDurationSec: Int = 300,

    // ===== 工具配置 =====
    val enableDocTools: Boolean = true,
    val enableWikiTools: Boolean = true,
    val enableDriveTools: Boolean = true,
    val enableBitableTools: Boolean = true,
    val enableTaskTools: Boolean = true,
    val enableChatTools: Boolean = true,
    val enablePermTools: Boolean = true,
    val enableUrgentTools: Boolean = true,

    // ===== 其他配置 =====
    val typingIndicator: Boolean = true,
    val reactionDedup: Boolean = true,
    val debugMode: Boolean = false
) {
    enum class ConnectionMode {
        WEBSOCKET, WEBHOOK
    }

    enum class DmPolicy {
        OPEN, PAIRING, ALLOWLIST
    }

    enum class GroupPolicy {
        OPEN, ALLOWLIST, DISABLED
    }

    enum class MentionBypass {
        NEVER, SINGLE_BOT, ALWAYS
    }

    enum class TopicSessionMode {
        DISABLED, ENABLED
    }

    enum class ChunkMode {
        LENGTH, NEWLINE
    }

    /**
     * 获取 API 基础 URL
     */
    fun getApiBaseUrl(): String {
        return when (domain.lowercase()) {
            "feishu" -> "https://open.feishu.cn"
            "lark" -> "https://open.larksuite.com"
            else -> domain // 自定义域名
        }
    }

    /**
     * 验证配置
     */
    fun validate(): Result<Unit> {
        if (appId.isBlank()) {
            return Result.failure(IllegalArgumentException("appId is required"))
        }
        if (appSecret.isBlank()) {
            return Result.failure(IllegalArgumentException("appSecret is required"))
        }
        if (connectionMode == ConnectionMode.WEBHOOK && verificationToken.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("verificationToken is required for webhook mode"))
        }
        return Result.success(Unit)
    }
}

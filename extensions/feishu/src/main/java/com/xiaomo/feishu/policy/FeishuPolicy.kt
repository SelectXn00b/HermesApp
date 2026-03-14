package com.xiaomo.feishu.policy

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel runtime.
 */


import android.util.Log
import com.xiaomo.feishu.FeishuConfig

/**
 * 飞书策略管理
 * 对齐 OpenClaw src/policy.ts
 */
class FeishuPolicy(private val config: FeishuConfig) {
    companion object {
        private const val TAG = "FeishuPolicy"
    }

    /**
     * 检查 DM 是否允许
     */
    fun isDmAllowed(senderId: String, isPaired: Boolean = false): Boolean {
        return when (config.dmPolicy) {
            FeishuConfig.DmPolicy.OPEN -> {
                Log.d(TAG, "DM allowed: OPEN policy")
                true
            }
            FeishuConfig.DmPolicy.PAIRING -> {
                val allowed = isPaired
                Log.d(TAG, "DM allowed=$allowed: PAIRING policy (isPaired=$isPaired)")
                allowed
            }
            FeishuConfig.DmPolicy.ALLOWLIST -> {
                val allowed = isInAllowlist(senderId, config.allowFrom)
                Log.d(TAG, "DM allowed=$allowed: ALLOWLIST policy")
                allowed
            }
        }
    }

    /**
     * 检查群组是否允许
     */
    fun isGroupAllowed(chatId: String): Boolean {
        return when (config.groupPolicy) {
            FeishuConfig.GroupPolicy.OPEN -> {
                Log.d(TAG, "Group allowed: OPEN policy")
                true
            }
            FeishuConfig.GroupPolicy.ALLOWLIST -> {
                val allowed = isInAllowlist(chatId, config.groupAllowFrom)
                Log.d(TAG, "Group allowed=$allowed: ALLOWLIST policy")
                allowed
            }
            FeishuConfig.GroupPolicy.DISABLED -> {
                Log.d(TAG, "Group disabled: DISABLED policy")
                false
            }
        }
    }

    /**
     * 检查群组消息是否需要 @
     */
    fun requiresMention(chatType: String, isMentioned: Boolean, isSingleBot: Boolean): Boolean {
        if (chatType != "group") {
            return false // DM 不需要 @
        }

        if (!config.requireMention) {
            return false // 配置为不需要 @
        }

        // 检查 bypass 规则
        val bypass = when (config.groupCommandMentionBypass) {
            FeishuConfig.MentionBypass.NEVER -> false
            FeishuConfig.MentionBypass.SINGLE_BOT -> isSingleBot
            FeishuConfig.MentionBypass.ALWAYS -> true
        }

        if (bypass) {
            Log.d(TAG, "Mention bypass: ${config.groupCommandMentionBypass}")
            return false
        }

        // 需要 @
        val required = !isMentioned
        if (required) {
            Log.d(TAG, "Mention required but not found")
        }
        return required
    }

    /**
     * 检查是否在白名单中
     */
    private fun isInAllowlist(id: String, allowlist: List<String>): Boolean {
        if (allowlist.isEmpty()) {
            return false
        }

        return allowlist.any { pattern ->
            when {
                pattern == id -> true
                pattern.endsWith("*") -> {
                    val prefix = pattern.dropLast(1)
                    id.startsWith(prefix)
                }
                else -> false
            }
        }
    }

    /**
     * 解析工具策略
     */
    fun resolveToolPolicy(chatType: String): ToolPolicy {
        return ToolPolicy(
            allowTools = true, // 默认允许所有工具
            allowedToolNames = null // null 表示全部允许
        )
    }

    /**
     * 检查工具是否允许使用
     */
    fun isToolAllowed(toolName: String, chatType: String): Boolean {
        val policy = resolveToolPolicy(chatType)

        if (!policy.allowTools) {
            return false
        }

        if (policy.allowedToolNames == null) {
            return true // 全部允许
        }

        return policy.allowedToolNames.contains(toolName)
    }
}

/**
 * 工具策略
 */
data class ToolPolicy(
    val allowTools: Boolean,
    val allowedToolNames: List<String>? = null
)

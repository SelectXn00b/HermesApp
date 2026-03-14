/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/discord/(all)
 *
 * AndroidForClaw adaptation: Discord channel runtime.
 */
package com.xiaomo.discord.policy

import android.util.Log
import com.xiaomo.discord.DiscordConfig

/**
 * Discord 权限策略管理
 * 参考:
 * - OpenClaw Discord security.resolveDmPolicy
 * - Feishu FeishuPolicy.kt
 *
 * 支持:
 * - DM (私聊) 策略: open, pairing, allowlist, denylist
 * - Guild (服务器) 策略: open, allowlist, denylist
 * - Channel 白名单
 * - 提及要求 (requireMention)
 */
object DiscordPolicy {
    private const val TAG = "DiscordPolicy"

    /**
     * DM 策略类型
     */
    enum class DmPolicyType {
        OPEN,       // 接受所有 DM
        PAIRING,    // 需要配对/审批
        ALLOWLIST,  // 仅允许白名单
        DENYLIST    // 拒绝黑名单
    }

    /**
     * 群组策略类型
     */
    enum class GroupPolicyType {
        OPEN,       // 接受所有群组消息 (需提及)
        ALLOWLIST,  // 仅允许白名单群组/频道
        DENYLIST    // 拒绝黑名单群组/频道
    }

    /**
     * 解析 DM 策略
     */
    fun resolveDmPolicy(config: DiscordConfig): DmPolicy {
        val policyStr = config.dm?.policy ?: "pairing"
        val policyType = when (policyStr.lowercase()) {
            "open" -> DmPolicyType.OPEN
            "pairing" -> DmPolicyType.PAIRING
            "allowlist" -> DmPolicyType.ALLOWLIST
            "denylist" -> DmPolicyType.DENYLIST
            else -> {
                Log.w(TAG, "Unknown DM policy: $policyStr, defaulting to PAIRING")
                DmPolicyType.PAIRING
            }
        }

        return DmPolicy(
            type = policyType,
            allowFrom = config.dm?.allowFrom ?: emptyList()
        )
    }

    /**
     * 解析群组策略
     */
    fun resolveGroupPolicy(config: DiscordConfig): GroupPolicy {
        val policyStr = config.groupPolicy ?: "open"
        val policyType = when (policyStr.lowercase()) {
            "open" -> GroupPolicyType.OPEN
            "allowlist" -> GroupPolicyType.ALLOWLIST
            "denylist" -> GroupPolicyType.DENYLIST
            else -> {
                Log.w(TAG, "Unknown group policy: $policyStr, defaulting to OPEN")
                GroupPolicyType.OPEN
            }
        }

        return GroupPolicy(
            type = policyType,
            guilds = config.guilds ?: emptyMap()
        )
    }

    /**
     * 检查 DM 是否被允许
     */
    fun isDmAllowed(policy: DmPolicy, userId: String): Boolean {
        return when (policy.type) {
            DmPolicyType.OPEN -> true
            DmPolicyType.PAIRING -> userId in policy.allowFrom
            DmPolicyType.ALLOWLIST -> userId in policy.allowFrom
            DmPolicyType.DENYLIST -> userId !in policy.allowFrom
        }
    }

    /**
     * 检查 Guild 消息是否被允许
     */
    fun isGuildMessageAllowed(
        policy: GroupPolicy,
        guildId: String,
        channelId: String,
        botMentioned: Boolean
    ): Boolean {
        val guildConfig = policy.guilds[guildId]

        return when (policy.type) {
            GroupPolicyType.OPEN -> {
                // Open 模式：需要提及
                if (!botMentioned && guildConfig?.requireMention != false) {
                    return false
                }

                // 检查频道白名单 (如果配置了)
                val allowedChannels = guildConfig?.channels
                if (allowedChannels != null && channelId !in allowedChannels) {
                    return false
                }

                true
            }

            GroupPolicyType.ALLOWLIST -> {
                // Allowlist 模式：必须在白名单中
                if (guildConfig == null) {
                    return false
                }

                // 检查频道白名单
                val allowedChannels = guildConfig.channels
                if (allowedChannels != null && channelId !in allowedChannels) {
                    return false
                }

                // 检查提及要求
                if (!botMentioned && guildConfig.requireMention != false) {
                    return false
                }

                true
            }

            GroupPolicyType.DENYLIST -> {
                // Denylist 模式：不在黑名单中
                if (guildConfig != null) {
                    val deniedChannels = guildConfig.channels
                    if (deniedChannels != null && channelId in deniedChannels) {
                        return false
                    }
                }

                // 检查提及要求
                if (!botMentioned && guildConfig?.requireMention != false) {
                    return false
                }

                true
            }
        }
    }

    /**
     * 解析 Guild 的提及要求
     */
    fun resolveRequireMention(config: DiscordConfig, guildId: String): Boolean {
        return config.guilds?.get(guildId)?.requireMention ?: true
    }

    /**
     * 解析 Guild 的工具策略
     */
    fun resolveToolPolicy(config: DiscordConfig, guildId: String): String {
        return config.guilds?.get(guildId)?.toolPolicy ?: "default"
    }

    /**
     * DM 策略
     */
    data class DmPolicy(
        val type: DmPolicyType,
        val allowFrom: List<String>
    )

    /**
     * 群组策略
     */
    data class GroupPolicy(
        val type: GroupPolicyType,
        val guilds: Map<String, DiscordConfig.GuildConfig>
    )
}

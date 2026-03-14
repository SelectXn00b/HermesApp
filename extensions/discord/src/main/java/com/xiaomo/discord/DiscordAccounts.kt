/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/discord/(all)
 *
 * AndroidForClaw adaptation: Discord channel runtime.
 */
package com.xiaomo.discord

import android.util.Log

/**
 * Discord 多账户管理
 * 参考 OpenClaw Discord 和 Feishu FeishuAccounts.kt
 */
object DiscordAccounts {
    private const val TAG = "DiscordAccounts"

    /**
     * 解析 Discord 账户配置
     * 支持多账户和默认账户
     */
    fun resolveAccount(config: DiscordConfig, accountId: String? = null): DiscordAccount {
        val targetAccountId = accountId ?: DiscordConfig.DEFAULT_ACCOUNT_ID

        // 如果是默认账户，返回主配置
        if (targetAccountId == DiscordConfig.DEFAULT_ACCOUNT_ID) {
            val token = config.token ?: throw IllegalArgumentException("Discord token is required")
            return DiscordAccount(
                accountId = DiscordConfig.DEFAULT_ACCOUNT_ID,
                token = token,
                name = config.name,
                config = config,
                enabled = config.enabled
            )
        }

        // 查找指定账户
        val accountConfig = config.accounts?.get(targetAccountId)
            ?: throw IllegalArgumentException("Discord account not found: $targetAccountId")

        // 合并配置 (账户配置优先)
        val mergedConfig = mergeAccountConfig(config, accountConfig)

        val token = accountConfig.token
            ?: throw IllegalArgumentException("Discord token is required for account: $targetAccountId")

        return DiscordAccount(
            accountId = targetAccountId,
            token = token,
            name = accountConfig.name ?: config.name,
            config = mergedConfig,
            enabled = accountConfig.enabled
        )
    }

    /**
     * 合并账户配置和默认配置
     * 账户配置优先级高于默认配置
     */
    private fun mergeAccountConfig(
        baseConfig: DiscordConfig,
        accountConfig: DiscordConfig.DiscordAccountConfig
    ): DiscordConfig {
        return DiscordConfig(
            enabled = accountConfig.enabled,
            token = accountConfig.token,
            name = accountConfig.name ?: baseConfig.name,
            dm = accountConfig.dm ?: baseConfig.dm,
            groupPolicy = baseConfig.groupPolicy, // 不继承账户级别的 groupPolicy
            guilds = accountConfig.guilds ?: baseConfig.guilds,
            replyToMode = baseConfig.replyToMode,
            accounts = null // 不嵌套
        )
    }

    /**
     * 列出所有账户 ID
     */
    fun listAccountIds(config: DiscordConfig): List<String> {
        val accountIds = mutableListOf<String>()

        // 添加默认账户
        if (config.token != null) {
            accountIds.add(DiscordConfig.DEFAULT_ACCOUNT_ID)
        }

        // 添加子账户
        config.accounts?.keys?.forEach { accountId ->
            accountIds.add(accountId)
        }

        return accountIds
    }

    /**
     * 解析默认账户 ID
     */
    fun resolveDefaultAccountId(config: DiscordConfig): String {
        return if (config.token != null) {
            DiscordConfig.DEFAULT_ACCOUNT_ID
        } else {
            // 如果没有默认账户，返回第一个子账户
            config.accounts?.keys?.firstOrNull() ?: DiscordConfig.DEFAULT_ACCOUNT_ID
        }
    }

    /**
     * 检查账户是否已配置
     */
    fun isAccountConfigured(config: DiscordConfig, accountId: String? = null): Boolean {
        return try {
            val account = resolveAccount(config, accountId)
            account.token.isNotBlank() && account.enabled
        } catch (e: Exception) {
            Log.w(TAG, "Account not configured: $accountId", e)
            false
        }
    }

    /**
     * 获取账户描述信息
     */
    fun describeAccount(account: DiscordAccount): Map<String, Any?> {
        return mapOf(
            "accountId" to account.accountId,
            "name" to account.name,
            "enabled" to account.enabled,
            "configured" to account.token.isNotBlank(),
            "dmPolicy" to account.config.dm?.policy,
            "groupPolicy" to account.config.groupPolicy
        )
    }

    /**
     * 规范化账户 ID
     */
    fun normalizeAccountId(accountId: String?): String {
        return accountId?.takeIf { it.isNotBlank() } ?: DiscordConfig.DEFAULT_ACCOUNT_ID
    }
}

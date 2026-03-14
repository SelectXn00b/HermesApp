package com.xiaomo.feishu

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel runtime.
 */


import android.util.Log

/**
 * 飞书账号管理
 * 对齐 OpenClaw accounts.ts
 *
 * 功能：
 * - 多账号配置管理
 * - 账号选择和解析
 * - 凭证验证
 */
object FeishuAccounts {
    private const val TAG = "FeishuAccounts"
    private const val DEFAULT_ACCOUNT_ID = "default"

    /**
     * 账号配置
     */
    data class AccountConfig(
        val name: String? = null,
        val enabled: Boolean = true,
        val appId: String,
        val appSecret: String,
        val encryptKey: String? = null,
        val verificationToken: String? = null,
        val domain: String = "feishu",
        val config: FeishuConfig? = null
    )

    /**
     * 多账号配置
     */
    data class MultiAccountConfig(
        val defaultAccount: String? = null,
        val accounts: Map<String, AccountConfig> = emptyMap(),
        val baseConfig: FeishuConfig? = null
    )

    /**
     * 账号选择来源
     */
    enum class AccountSelectionSource {
        EXPLICIT,           // 明确指定的账号
        EXPLICIT_DEFAULT,   // 配置中明确指定的默认账号
        MAPPED_DEFAULT,     // 映射到 default 账号
        FALLBACK            // 回退到第一个账号
    }

    /**
     * 解析后的账号
     */
    data class ResolvedAccount(
        val accountId: String,
        val selectionSource: AccountSelectionSource,
        val enabled: Boolean,
        val configured: Boolean,
        val name: String? = null,
        val appId: String? = null,
        val appSecret: String? = null,
        val encryptKey: String? = null,
        val verificationToken: String? = null,
        val domain: String = "feishu",
        val config: FeishuConfig? = null
    )

    /**
     * 规范化账号 ID
     */
    fun normalizeAccountId(accountId: String?): String {
        return accountId?.trim()?.ifEmpty { DEFAULT_ACCOUNT_ID } ?: DEFAULT_ACCOUNT_ID
    }

    /**
     * 列出配置的所有账号 ID
     */
    fun listAccountIds(config: MultiAccountConfig): List<String> {
        val ids = config.accounts.keys.filter { it.isNotBlank() }
        if (ids.isEmpty()) {
            // 向后兼容：没有配置账号时返回 default
            return listOf(DEFAULT_ACCOUNT_ID)
        }
        return ids.sorted()
    }

    /**
     * 解析默认账号选择
     */
    fun resolveDefaultAccountSelection(config: MultiAccountConfig): Pair<String, AccountSelectionSource> {
        // 1. 明确指定的默认账号
        val preferred = config.defaultAccount?.trim()
        if (!preferred.isNullOrEmpty()) {
            return Pair(normalizeAccountId(preferred), AccountSelectionSource.EXPLICIT_DEFAULT)
        }

        // 2. 映射到 default 账号
        val ids = listAccountIds(config)
        if (ids.contains(DEFAULT_ACCOUNT_ID)) {
            return Pair(DEFAULT_ACCOUNT_ID, AccountSelectionSource.MAPPED_DEFAULT)
        }

        // 3. 回退到第一个账号
        return Pair(ids.firstOrNull() ?: DEFAULT_ACCOUNT_ID, AccountSelectionSource.FALLBACK)
    }

    /**
     * 解析默认账号 ID
     */
    fun resolveDefaultAccountId(config: MultiAccountConfig): String {
        return resolveDefaultAccountSelection(config).first
    }

    /**
     * 获取账号特定配置
     */
    private fun getAccountConfig(
        config: MultiAccountConfig,
        accountId: String
    ): AccountConfig? {
        return config.accounts[accountId]
    }

    /**
     * 合并账号配置
     * 账号特定配置覆盖基础配置
     */
    private fun mergeAccountConfig(
        config: MultiAccountConfig,
        accountId: String
    ): FeishuConfig? {
        val base = config.baseConfig
        val account = getAccountConfig(config, accountId)

        // 如果没有基础配置，使用账号配置
        if (base == null) {
            return account?.config
        }

        // 如果没有账号配置，使用基础配置
        if (account?.config == null) {
            return base
        }

        // 合并配置（账号配置优先）
        return base.copy(
            enabled = account.config.enabled,
            appId = account.appId,
            appSecret = account.appSecret,
            encryptKey = account.encryptKey ?: base.encryptKey,
            verificationToken = account.verificationToken ?: base.verificationToken,
            domain = account.domain
        )
    }

    /**
     * 验证凭证
     */
    fun validateCredentials(
        appId: String?,
        appSecret: String?
    ): Boolean {
        return !appId.isNullOrBlank() && !appSecret.isNullOrBlank()
    }

    /**
     * 解析账号
     *
     * @param config 多账号配置
     * @param accountId 账号 ID（null 表示使用默认账号）
     * @return 解析后的账号
     */
    fun resolveAccount(
        config: MultiAccountConfig,
        accountId: String? = null
    ): ResolvedAccount {
        val hasExplicitAccountId = !accountId.isNullOrBlank()

        val defaultSelection = if (hasExplicitAccountId) {
            null
        } else {
            resolveDefaultAccountSelection(config)
        }

        val resolvedAccountId = if (hasExplicitAccountId) {
            normalizeAccountId(accountId)
        } else {
            defaultSelection?.first ?: DEFAULT_ACCOUNT_ID
        }

        val selectionSource = if (hasExplicitAccountId) {
            AccountSelectionSource.EXPLICIT
        } else {
            defaultSelection?.second ?: AccountSelectionSource.FALLBACK
        }

        // 获取账号配置
        val accountConfig = getAccountConfig(config, resolvedAccountId)
        val baseEnabled = config.baseConfig?.enabled ?: true
        val accountEnabled = accountConfig?.enabled ?: true
        val enabled = baseEnabled && accountEnabled

        // 合并配置
        val mergedConfig = mergeAccountConfig(config, resolvedAccountId)

        // 验证凭证
        val configured = validateCredentials(accountConfig?.appId, accountConfig?.appSecret)

        Log.d(TAG, "Resolved account: id=$resolvedAccountId, source=$selectionSource, " +
                "enabled=$enabled, configured=$configured")

        return ResolvedAccount(
            accountId = resolvedAccountId,
            selectionSource = selectionSource,
            enabled = enabled,
            configured = configured,
            name = accountConfig?.name,
            appId = accountConfig?.appId,
            appSecret = accountConfig?.appSecret,
            encryptKey = accountConfig?.encryptKey,
            verificationToken = accountConfig?.verificationToken,
            domain = accountConfig?.domain ?: "feishu",
            config = mergedConfig
        )
    }

    /**
     * 列出所有启用且配置完整的账号
     */
    fun listEnabledAccounts(config: MultiAccountConfig): List<ResolvedAccount> {
        return listAccountIds(config)
            .map { accountId -> resolveAccount(config, accountId) }
            .filter { it.enabled && it.configured }
    }

    /**
     * 从单一配置创建多账号配置（向后兼容）
     */
    fun fromSingleConfig(config: FeishuConfig): MultiAccountConfig {
        return MultiAccountConfig(
            defaultAccount = DEFAULT_ACCOUNT_ID,
            accounts = mapOf(
                DEFAULT_ACCOUNT_ID to AccountConfig(
                    name = "Default Account",
                    enabled = config.enabled,
                    appId = config.appId,
                    appSecret = config.appSecret,
                    encryptKey = config.encryptKey,
                    verificationToken = config.verificationToken,
                    domain = config.domain,
                    config = config
                )
            ),
            baseConfig = config
        )
    }
}

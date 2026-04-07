package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/channel-config-helpers.ts
 *
 * Config write authorization, channel config adapter builders for
 * scoped/top-level/hybrid channel accounts, and DM security policy resolution.
 * Android adaptation: uses Map<String, Any?> instead of OpenClawConfig type params.
 */

// ---------- Config Write Types ----------

/**
 * Scope for a config write operation.
 * Aligned with TS ConfigWriteScope.
 */
data class ConfigWriteScope(
    val channelId: String? = null,
    val accountId: String? = null,
)

/**
 * Target of a config write operation.
 * Aligned with TS ConfigWriteTarget.
 */
sealed class ConfigWriteTarget {
    data object Global : ConfigWriteTarget()
    data class Channel(val scope: ConfigWriteScope) : ConfigWriteTarget()
    data class Account(val scope: ConfigWriteScope) : ConfigWriteTarget()
    data class Ambiguous(val scopes: List<ConfigWriteScope>) : ConfigWriteTarget()
}

/**
 * Result of authorizing a config write.
 * Aligned with TS ConfigWriteAuthorizationResult.
 */
sealed class ConfigWriteAuthorizationResult {
    data object Allowed : ConfigWriteAuthorizationResult()
    data class Denied(
        val reason: String, // "ambiguous-target" | "origin-disabled" | "target-disabled"
        val blockedScope: BlockedScope? = null,
    ) : ConfigWriteAuthorizationResult()

    data class BlockedScope(
        val kind: String, // "origin" | "target"
        val scope: ConfigWriteScope,
    )
}

// ---------- Internal Channel Config ----------

private const val INTERNAL_MESSAGE_CHANNEL = "webchat"

// ---------- Config Write Checks ----------

/**
 * Resolve whether config writes are enabled for a channel/account.
 * Aligned with TS resolveChannelConfigWrites.
 */
@Suppress("UNCHECKED_CAST")
fun resolveChannelConfigWrites(
    cfg: Map<String, Any?>,
    channelId: String?,
    accountId: String? = null,
): Boolean {
    if (channelId.isNullOrEmpty()) return true
    val channels = cfg["channels"] as? Map<String, Any?> ?: return true
    val channelConfig = channels[channelId] as? Map<String, Any?> ?: return true

    // Check account-level configWrites
    val normalizedAccount = normalizeAccountId(accountId)
    val accounts = channelConfig["accounts"] as? Map<String, Any?>
    val accountConfig = accounts?.get(normalizedAccount) as? Map<String, Any?>
    val accountWrites = accountConfig?.get("configWrites")
    val channelWrites = channelConfig["configWrites"]
    val value = accountWrites ?: channelWrites
    return value != false
}

private fun listConfigWriteTargetScopes(target: ConfigWriteTarget?): List<ConfigWriteScope> =
    when (target) {
        null, is ConfigWriteTarget.Global -> emptyList()
        is ConfigWriteTarget.Ambiguous -> target.scopes
        is ConfigWriteTarget.Channel -> listOf(target.scope)
        is ConfigWriteTarget.Account -> listOf(target.scope)
    }

/**
 * Authorize a config write operation.
 * Aligned with TS authorizeConfigWrite.
 */
fun authorizeConfigWrite(
    cfg: Map<String, Any?>,
    origin: ConfigWriteScope? = null,
    target: ConfigWriteTarget? = null,
    allowBypass: Boolean = false,
): ConfigWriteAuthorizationResult {
    if (allowBypass) return ConfigWriteAuthorizationResult.Allowed

    if (target is ConfigWriteTarget.Ambiguous) {
        return ConfigWriteAuthorizationResult.Denied(reason = "ambiguous-target")
    }

    if (origin?.channelId != null) {
        if (!resolveChannelConfigWrites(cfg, origin.channelId, origin.accountId)) {
            return ConfigWriteAuthorizationResult.Denied(
                reason = "origin-disabled",
                blockedScope = ConfigWriteAuthorizationResult.BlockedScope(
                    kind = "origin",
                    scope = origin,
                ),
            )
        }
    }

    val seen = mutableSetOf<String>()
    for (scope in listConfigWriteTargetScopes(target)) {
        if (scope.channelId == null) continue
        val key = "${scope.channelId}:${normalizeAccountId(scope.accountId)}"
        if (!seen.add(key)) continue
        if (!resolveChannelConfigWrites(cfg, scope.channelId, scope.accountId)) {
            return ConfigWriteAuthorizationResult.Denied(
                reason = "target-disabled",
                blockedScope = ConfigWriteAuthorizationResult.BlockedScope(
                    kind = "target",
                    scope = scope,
                ),
            )
        }
    }

    return ConfigWriteAuthorizationResult.Allowed
}

/**
 * Check if the origin can bypass config write policy (webchat + operator.admin).
 * Aligned with TS canBypassConfigWritePolicy.
 */
fun canBypassConfigWritePolicy(
    channel: String?,
    gatewayClientScopes: List<String>? = null,
): Boolean {
    return channel?.trim()?.lowercase() == INTERNAL_MESSAGE_CHANNEL &&
        gatewayClientScopes?.contains("operator.admin") == true
}

/**
 * Format the message shown when a config write is denied.
 * Aligned with TS formatConfigWriteDeniedMessage.
 */
fun formatConfigWriteDeniedMessage(
    result: ConfigWriteAuthorizationResult.Denied,
    fallbackChannelId: String? = null,
): String {
    if (result.reason == "ambiguous-target") {
        return "Channel-initiated /config writes cannot replace channels, channel roots, or accounts collections. Use a more specific path or gateway operator.admin."
    }
    val blocked = result.blockedScope?.scope
    val channelLabel = blocked?.channelId ?: fallbackChannelId ?: "this channel"
    val hint = when {
        blocked?.channelId != null && blocked.accountId != null ->
            "channels.${blocked.channelId}.accounts.${blocked.accountId}.configWrites=true"
        blocked?.channelId != null ->
            "channels.${blocked.channelId}.configWrites=true"
        fallbackChannelId != null ->
            "channels.$fallbackChannelId.configWrites=true"
        else ->
            "channels.<channel>.configWrites=true"
    }
    return "Config writes are disabled for $channelLabel. Set $hint to enable."
}

// ---------- Allowlist Helpers ----------

/**
 * Coerce mixed allowlist config values into plain strings without trimming or deduping.
 * Aligned with TS mapAllowFromEntries.
 */
fun mapAllowFromEntries(
    allowFrom: List<Any?>?,
): List<String> = (allowFrom ?: emptyList()).map { it.toString() }

/**
 * Collapse nullable config scalars into a trimmed optional string.
 * Aligned with TS resolveOptionalConfigString.
 */
fun resolveOptionalConfigString(value: Any?): String? {
    if (value == null) return null
    val normalized = value.toString().trim()
    return normalized.takeIf { it.isNotEmpty() }
}

// ---------- DM Security Policy ----------

/**
 * Build an account-scoped DM security policy for channels.
 * Aligned with TS buildAccountScopedDmSecurityPolicy.
 */
@Suppress("UNCHECKED_CAST")
fun buildAccountScopedDmSecurityPolicy(
    cfg: Map<String, Any?>,
    channelKey: String,
    accountId: String? = null,
    fallbackAccountId: String? = null,
    policy: String? = null,
    allowFrom: List<Any>? = null,
    defaultPolicy: String = "pairing",
    allowFromPathSuffix: String = "",
    policyPathSuffix: String? = null,
    approveChannelId: String? = null,
    approveHint: String? = null,
    normalizeEntry: ((String) -> String)? = null,
): DmSecurityPolicyResult {
    val resolvedAccountId = accountId ?: fallbackAccountId ?: DEFAULT_ACCOUNT_ID
    val channels = cfg["channels"] as? Map<String, Any?>
    val channelConfig = channels?.get(channelKey) as? Map<String, Any?>
    val accounts = channelConfig?.get("accounts") as? Map<String, Any?>
    val useAccountPath = accounts?.containsKey(resolvedAccountId) == true
    val basePath = if (useAccountPath) {
        "channels.$channelKey.accounts.$resolvedAccountId."
    } else {
        "channels.$channelKey."
    }
    val allowFromPath = "$basePath$allowFromPathSuffix"
    val resolvedPolicyPath = if (policyPathSuffix != null) "$basePath$policyPathSuffix" else null

    val targetChannel = approveChannelId ?: channelKey
    val resolvedApproveHint = approveHint
        ?: "Approve via: openclaw pairing list $targetChannel / openclaw pairing approve $targetChannel <code>"

    return DmSecurityPolicyResult(
        policy = policy ?: defaultPolicy,
        allowFrom = allowFrom ?: emptyList(),
        policyPath = resolvedPolicyPath,
        allowFromPath = allowFromPath,
        approveHint = resolvedApproveHint,
        normalizeEntry = normalizeEntry,
    )
}

data class DmSecurityPolicyResult(
    val policy: String,
    val allowFrom: List<Any>,
    val policyPath: String?,
    val allowFromPath: String,
    val approveHint: String,
    val normalizeEntry: ((String) -> String)?,
)

// ---------- Channel Config Section Helpers ----------

/**
 * Set account enabled flag in a config section.
 * Aligned with TS setAccountEnabledInConfigSection.
 */
@Suppress("UNCHECKED_CAST")
fun setAccountEnabledInConfigSection(
    cfg: Map<String, Any?>,
    sectionKey: String,
    accountId: String,
    enabled: Boolean,
    allowTopLevel: Boolean = true,
): Map<String, Any?> {
    val channels = (cfg["channels"] as? Map<String, Any?>) ?: emptyMap()
    val section = (channels[sectionKey] as? Map<String, Any?>) ?: emptyMap()
    val normalizedId = normalizeAccountId(accountId)

    // If default account and allowTopLevel, set at channel root
    if (normalizedId == DEFAULT_ACCOUNT_ID && allowTopLevel) {
        return cfg + mapOf(
            "channels" to channels + mapOf(
                sectionKey to section + mapOf("enabled" to enabled)
            )
        )
    }

    val accounts = (section["accounts"] as? Map<String, Any?>) ?: emptyMap()
    val account = (accounts[normalizedId] as? Map<String, Any?>) ?: emptyMap()
    return cfg + mapOf(
        "channels" to channels + mapOf(
            sectionKey to section + mapOf(
                "accounts" to accounts + mapOf(
                    normalizedId to account + mapOf("enabled" to enabled)
                )
            )
        )
    )
}

/**
 * Delete an account from a config section.
 * Aligned with TS deleteAccountFromConfigSection.
 */
@Suppress("UNCHECKED_CAST")
fun deleteAccountFromConfigSection(
    cfg: Map<String, Any?>,
    sectionKey: String,
    accountId: String,
    clearBaseFields: List<String> = emptyList(),
): Map<String, Any?> {
    val channels = (cfg["channels"] as? Map<String, Any?>) ?: return cfg
    val section = (channels[sectionKey] as? Map<String, Any?>) ?: return cfg
    val normalizedId = normalizeAccountId(accountId)

    val accounts = (section["accounts"] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
    accounts.remove(normalizedId)

    val nextSection = section.toMutableMap()
    if (accounts.isEmpty()) {
        nextSection.remove("accounts")
    } else {
        nextSection["accounts"] = accounts
    }

    // Clear base fields
    for (field in clearBaseFields) {
        nextSection.remove(field)
    }

    return cfg + mapOf(
        "channels" to channels + mapOf(sectionKey to nextSection)
    )
}

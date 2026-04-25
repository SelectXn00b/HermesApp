package com.ai.assistance.operit.hermes.gateway

import android.content.Context
import com.xiaomo.hermes.hermes.gateway.GatewayConfig
import com.xiaomo.hermes.hermes.gateway.Platform
import com.xiaomo.hermes.hermes.gateway.PlatformConfig
import com.xiaomo.hermes.hermes.getHermesHome
import kotlinx.coroutines.flow.first

/**
 * Builds a [GatewayConfig] snapshot from [HermesGatewayPreferences].
 *
 * Only Feishu + Weixin are wired to functional parity in this release;
 * other enabled-but-unwired platforms are translated to [PlatformConfig]s
 * and surfaced to [com.xiaomo.hermes.hermes.gateway.GatewayRunner] exactly
 * as the Python Hermes gateway expects.
 */
object HermesGatewayConfigBuilder {

    suspend fun build(context: Context): GatewayConfig {
        val prefs = HermesGatewayPreferences.getInstance(context)
        val platforms = mutableMapOf<Platform, PlatformConfig>()

        buildFeishu(prefs)?.let { platforms[Platform.FEISHU] = it }
        buildWeixin(prefs)?.let { platforms[Platform.WEIXIN] = it }

        return GatewayConfig(
            hermesHome = getHermesHome().absolutePath,
            platforms = platforms,
            maxConcurrentSessions = 5,
        )
    }

    private suspend fun buildFeishu(prefs: HermesGatewayPreferences): PlatformConfig? {
        val appId = prefs.readSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.SECRET_FEISHU_APP_ID,
        )
        val appSecret = prefs.readSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.SECRET_FEISHU_APP_SECRET,
        )
        if (appId.isEmpty() || appSecret.isEmpty()) return null

        val verificationToken = prefs.readSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.SECRET_FEISHU_VERIFICATION_TOKEN,
        )
        val encryptKey = prefs.readSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.SECRET_FEISHU_ENCRYPT_KEY,
        )

        val extra = buildMap<String, Any> {
            put("app_id", appId)
            put("app_secret", appSecret)
            if (verificationToken.isNotEmpty()) put("verification_token", verificationToken)
            if (encryptKey.isNotEmpty()) put("encrypt_key", encryptKey)
        }

        return PlatformConfig(
            platform = Platform.FEISHU,
            enabled = true,
            dmPolicy = readPolicy(prefs, HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.FIELD_DM_POLICY, "open"),
            dmAllowFrom = readCsv(prefs, HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.FIELD_DM_ALLOW_FROM),
            groupPolicy = readPolicy(prefs, HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.FIELD_GROUP_POLICY, "allowlist"),
            groupAllowFrom = readCsv(prefs, HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.FIELD_GROUP_ALLOW_FROM),
            replyToMode = readPolicy(prefs, HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.FIELD_REPLY_TO_MODE, "first"),
            extra = extra,
        )
    }

    private suspend fun buildWeixin(prefs: HermesGatewayPreferences): PlatformConfig? {
        val accountId = prefs.readSecret(
            HermesGatewayPreferences.PLATFORM_WEIXIN,
            HermesGatewayPreferences.SECRET_WEIXIN_ACCOUNT_ID,
        )
        val loginToken = prefs.readSecret(
            HermesGatewayPreferences.PLATFORM_WEIXIN,
            HermesGatewayPreferences.SECRET_WEIXIN_LOGIN_TOKEN,
        )
        if (accountId.isEmpty()) return null

        val extra = buildMap<String, Any> {
            put("account_id", accountId)
            if (loginToken.isNotEmpty()) put("login_token", loginToken)
        }

        return PlatformConfig(
            platform = Platform.WEIXIN,
            enabled = true,
            dmPolicy = readPolicy(prefs, HermesGatewayPreferences.PLATFORM_WEIXIN, HermesGatewayPreferences.FIELD_DM_POLICY, "open"),
            dmAllowFrom = readCsv(prefs, HermesGatewayPreferences.PLATFORM_WEIXIN, HermesGatewayPreferences.FIELD_DM_ALLOW_FROM),
            groupPolicy = readPolicy(prefs, HermesGatewayPreferences.PLATFORM_WEIXIN, HermesGatewayPreferences.FIELD_GROUP_POLICY, "allowlist"),
            groupAllowFrom = readCsv(prefs, HermesGatewayPreferences.PLATFORM_WEIXIN, HermesGatewayPreferences.FIELD_GROUP_ALLOW_FROM),
            replyToMode = readPolicy(prefs, HermesGatewayPreferences.PLATFORM_WEIXIN, HermesGatewayPreferences.FIELD_REPLY_TO_MODE, "first"),
            extra = extra,
        )
    }

    private suspend fun readPolicy(
        prefs: HermesGatewayPreferences,
        platform: String,
        field: String,
        default: String,
    ): String = prefs.platformPolicyFieldFlow(platform, field, default).first()

    private suspend fun readCsv(
        prefs: HermesGatewayPreferences,
        platform: String,
        field: String,
    ): List<String> = prefs.platformPolicyFieldFlow(platform, field, "").first()
        .split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
}

package com.ai.assistance.operit.integrations.intent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.hermes.gateway.HermesGatewayPreferences
import com.ai.assistance.operit.services.gateway.GatewayForegroundService
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Adb broadcast entry to seed Hermes Gateway prefs + start/stop the service
 * without navigating the UI. Used by the pending Gateway E2E (task #34) and
 * for diagnosis when UI-driven enablement is inconvenient.
 *
 * 典型用法：
 *   adb shell am broadcast \
 *     -n com.xiaomo.androidforclaw/com.ai.assistance.operit.integrations.intent.GatewayConfigReceiver \
 *     -a com.ai.assistance.operit.SET_GATEWAY \
 *     --es feishu_app_id 'cli_xxxx' --es feishu_app_secret 'xxxx' \
 *     --es weixin_account_id 'wxid_xxx' \
 *     --ez service_enabled true --ez start true
 *
 * Extras (all optional):
 *   --es feishu_app_id           Feishu 应用 app_id
 *   --es feishu_app_secret       Feishu 应用 app_secret
 *   --es feishu_verification_token
 *   --es feishu_encrypt_key
 *   --ez feishu_enabled true     启用 Feishu 平台开关
 *   --es weixin_account_id       Weixin iLink account_id
 *   --es weixin_login_token      Weixin iLink login_token
 *   --ez weixin_enabled true     启用 Weixin 平台开关
 *   --ez service_enabled true    写入 service_enabled=true（Settings UI 观测到）
 *   --ez start true              立即 startForegroundService（需 service_enabled=true 语义）
 *   --ez stop true               立即 stop
 */
class GatewayConfigReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GatewayConfigReceiver"
        const val ACTION_SET_GATEWAY = "com.ai.assistance.operit.SET_GATEWAY"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_GATEWAY) return

        val feishuAppId = intent.getStringExtra("feishu_app_id")
        val feishuAppSecret = intent.getStringExtra("feishu_app_secret")
        val feishuVt = intent.getStringExtra("feishu_verification_token")
        val feishuEk = intent.getStringExtra("feishu_encrypt_key")
        val feishuEnabled = extraBool(intent, "feishu_enabled")

        val weixinAccountId = intent.getStringExtra("weixin_account_id")
        val weixinLoginToken = intent.getStringExtra("weixin_login_token")
        val weixinEnabled = extraBool(intent, "weixin_enabled")

        val serviceEnabled = extraBool(intent, "service_enabled")
        val doStart = intent.getBooleanExtra("start", false)
        val doStop = intent.getBooleanExtra("stop", false)

        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = HermesGatewayPreferences.getInstance(appContext)
                feishuAppId?.let { prefs.writeSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_APP_ID, it) }
                feishuAppSecret?.let { prefs.writeSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_APP_SECRET, it) }
                feishuVt?.let { prefs.writeSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_VERIFICATION_TOKEN, it) }
                feishuEk?.let { prefs.writeSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_ENCRYPT_KEY, it) }
                feishuEnabled?.let { prefs.savePlatformEnabled(HermesGatewayPreferences.PLATFORM_FEISHU, it) }

                weixinAccountId?.let { prefs.writeSecret(HermesGatewayPreferences.PLATFORM_WEIXIN, HermesGatewayPreferences.SECRET_WEIXIN_ACCOUNT_ID, it) }
                weixinLoginToken?.let { prefs.writeSecret(HermesGatewayPreferences.PLATFORM_WEIXIN, HermesGatewayPreferences.SECRET_WEIXIN_LOGIN_TOKEN, it) }
                weixinEnabled?.let { prefs.savePlatformEnabled(HermesGatewayPreferences.PLATFORM_WEIXIN, it) }

                serviceEnabled?.let { prefs.saveServiceEnabled(it) }

                if (doStart) GatewayForegroundService.start(appContext)
                if (doStop) GatewayForegroundService.stop(appContext)

                AppLogger.i(
                    TAG,
                    "applied: feishuEnabled=$feishuEnabled weixinEnabled=$weixinEnabled " +
                        "serviceEnabled=$serviceEnabled start=$doStart stop=$doStop"
                )
            } catch (e: Throwable) {
                AppLogger.e(TAG, "broadcast apply failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun extraBool(intent: Intent, name: String): Boolean? =
        if (intent.hasExtra(name)) intent.getBooleanExtra(name, false) else null
}

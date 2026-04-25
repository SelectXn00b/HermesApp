package com.ai.assistance.operit.services.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.hermes.gateway.HermesGatewayPreferences
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Starts [GatewayForegroundService] after device boot when the user has
 * enabled auto-start in Hermes settings.
 *
 * Registered for `BOOT_COMPLETED` and HTC / MIUI `QUICKBOOT_POWERON`.
 * The receiver itself does no work beyond reading preferences on a
 * background dispatcher and then invoking the service.
 */
class GatewayBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != ACTION_QUICKBOOT_POWERON &&
            action != ACTION_HTC_QUICKBOOT_POWERON
        ) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = HermesGatewayPreferences.getInstance(context)
                val autoStart = prefs.autoStartOnBootFlow.first()
                val serviceEnabled = prefs.serviceEnabledFlow.first()
                if (autoStart && serviceEnabled) {
                    AppLogger.i(TAG, "boot: auto-starting Hermes gateway service")
                    GatewayForegroundService.start(context.applicationContext)
                }
            } catch (e: Throwable) {
                AppLogger.w(TAG, "boot start failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "HermesGatewayBoot"
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        private const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}

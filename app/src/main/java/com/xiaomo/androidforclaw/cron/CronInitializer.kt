/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/cron/(all)
 *
 * AndroidForClaw adaptation: cron scheduling.
 */
package com.xiaomo.androidforclaw.cron

import android.content.Context
import android.util.Log

object CronInitializer {
    private const val TAG = "CronInitializer"
    private var cronService: CronService? = null
    private var isInitialized = false

    fun initialize(context: Context, config: CronConfig? = null) {
        if (isInitialized) return

        try {
            val cronConfig = config ?: CronConfig(
                enabled = true,
                storePath = "/sdcard/.androidforclaw/config/cron/jobs.json",
                maxConcurrentRuns = 1
            )

            cronService = CronService(context, cronConfig)
            com.xiaomo.androidforclaw.gateway.methods.CronMethods.initialize(cronService!!)

            if (cronConfig.enabled) {
                cronService?.start()
            }

            isInitialized = true
            Log.d(TAG, "CronService initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize", e)
        }
    }

    fun shutdown() {
        cronService?.stop()
        cronService = null
        isInitialized = false
    }

    fun getService() = cronService
}

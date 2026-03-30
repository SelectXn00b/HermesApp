/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/cron/service.ts (startup)
 */
package com.xiaomo.androidforclaw.cron

import android.content.Context
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.workspace.StoragePaths
import java.io.File
import java.util.UUID

object CronInitializer {
    private const val TAG = "CronInitializer"
    private var cronService: CronService? = null
    private var isInitialized = false

    fun initialize(context: Context, config: CronConfig? = null) {
        if (isInitialized) return

        try {
            val cronConfig = config ?: CronConfig(
                enabled = true,
                storePath = StoragePaths.cronJobs.absolutePath,
                maxConcurrentRuns = 1
            )

            cronService = CronService(context, cronConfig)
            com.xiaomo.androidforclaw.gateway.methods.CronMethods.initialize(cronService!!)

            // Create default heartbeat job if no jobs exist
            ensureDefaultHeartbeatJob(context, cronService!!)

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

    /**
     * Create a default heartbeat job if the cron store is empty.
     * Reads HEARTBEAT.md for the heartbeat message.
     */
    private fun ensureDefaultHeartbeatJob(context: Context, service: CronService) {
        val existingJobs = service.list()
        if (existingJobs.isNotEmpty()) return

        try {
            // Read heartbeat instructions from HEARTBEAT.md
            val workspaceDir = context.filesDir
            val heartbeatFile = File(workspaceDir, "openclaw/workspace/HEARTBEAT.md")
            val heartbeatContent = if (heartbeatFile.exists()) {
                heartbeatFile.readText().trim()
            } else {
                ""
            }

            // Default heartbeat message
            val heartbeatMessage = if (heartbeatContent.isNotEmpty() && !heartbeatContent.startsWith("#")) {
                // Use file content directly if it's not just comments
                "Read HEARTBEAT.md and follow it. File content:\n\n$heartbeatContent"
            } else {
                // Default heartbeat check
                "Perform a heartbeat check. Read HEARTBEAT.md if it exists and follow its instructions. If nothing needs attention, reply HEARTBEAT_OK."
            }

            // Create heartbeat job: runs every 30 minutes
            val job = CronJob(
                id = UUID.randomUUID().toString(),
                name = "heartbeat",
                description = "Default heartbeat monitor",
                schedule = CronSchedule.Every(
                    everyMs = 30 * 60 * 1000L  // 30 minutes
                ),
                sessionTarget = SessionTarget.MAIN,
                wakeMode = WakeMode.NOW,
                payload = CronPayload.AgentTurn(
                    message = heartbeatMessage,
                    channel = "feishu",
                    deliver = true
                ),
                delivery = CronDelivery(
                    mode = DeliveryMode.ANNOUNCE,
                    channel = "feishu"
                ),
                enabled = true,
                createdAtMs = System.currentTimeMillis(),
                updatedAtMs = System.currentTimeMillis()
            )

            service.add(job)
            Log.i(TAG, "✅ Default heartbeat job created (every 30min)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create default heartbeat job", e)
        }
    }
}

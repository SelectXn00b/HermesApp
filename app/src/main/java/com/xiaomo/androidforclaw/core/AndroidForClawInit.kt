package com.xiaomo.androidforclaw.core

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: application init sequence and startup glue.
 */


import android.content.Context
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * AndroidForClaw Initialization
 * Fully aligned with OpenClaw directory structure and files
 *
 * OpenClaw directory structure:
 * ~/.openclaw/
 * ├── .builtin-mimo-provider
 * ├── .device-id
 * ├── agents/
 * │   └── main/
 * │       └── sessions/
 * │           └── sessions.json
 * ├── app.log
 * ├── canvas/
 * │   └── index.html
 * ├── config-backups/
 * ├── cron/
 * │   └── jobs.json
 * ├── devices/
 * │   ├── paired.json
 * │   └── pending.json
 * ├── gateway.log
 * ├── openclaw.json
 * ├── openclaw.last-known-good.json
 * ├── skills/
 * ├── update-check.json
 * └── workspace/
 */
object AndroidForClawInit {

    private const val TAG = "AndroidForClawInit"
    private const val BASE_DIR = "/sdcard/.androidforclaw"

    /**
     * Initialize all directories and files
     */
    fun initialize(context: Context) {
        try {
            Log.i(TAG, "========================================")
            Log.i(TAG, "初始化 AndroidForClaw 目录结构")
            Log.i(TAG, "========================================")

            // 1. Create root directory
            val baseDir = File(BASE_DIR)
            if (!baseDir.exists()) {
                baseDir.mkdirs()
                Log.i(TAG, "✅ 创建根目录: $BASE_DIR")
            }

            // 2. Create .device-id
            initDeviceId()

            // 3. Create agents/main/sessions/
            initAgentsDirectory()

            // 4. Create canvas/
            initCanvasDirectory()

            // 5. Create config-backups/
            File(BASE_DIR, "config-backups").mkdirs()
            Log.d(TAG, "✅ 创建 config-backups/")

            // 6. Create cron/
            initCronDirectory()

            // 7. Create devices/
            initDevicesDirectory()

            // 8. Create skills/
            File(BASE_DIR, "skills").mkdirs()
            Log.d(TAG, "✅ 创建 skills/")

            // 9. Create workspace/
            File(BASE_DIR, "workspace").apply {
                mkdirs()
                // workspace subdirectories
                File(this, "skills").mkdirs()
            }
            Log.d(TAG, "✅ 创建 workspace/")

            // 10. Create update-check.json
            initUpdateCheck()

            // 11. Create log file placeholders
            initLogFiles()

            // 12. openclaw.json and openclaw.last-known-good.json are handled by ConfigLoader

            Log.i(TAG, "========================================")
            Log.i(TAG, "✅ AndroidForClaw 初始化完成")
            Log.i(TAG, "========================================")
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败", e)
        }
    }

    /**
     * Initialize .device-id
     */
    private fun initDeviceId() {
        val deviceIdFile = File(BASE_DIR, ".device-id")
        if (!deviceIdFile.exists()) {
            val deviceId = UUID.randomUUID().toString()
            deviceIdFile.writeText(deviceId)
            Log.i(TAG, "✅ 创建 .device-id: $deviceId")
        } else {
            val deviceId = deviceIdFile.readText().trim()
            Log.d(TAG, "Device ID: $deviceId")
        }
    }

    /**
     * Initialize agents/main/sessions/
     */
    private fun initAgentsDirectory() {
        val sessionsDir = File(BASE_DIR, "agents/main/sessions")
        sessionsDir.mkdirs()
        Log.d(TAG, "✅ 创建 agents/main/sessions/")

        // Create sessions.json index file
        val sessionsIndex = File(sessionsDir, "sessions.json")
        if (!sessionsIndex.exists()) {
            sessionsIndex.writeText("[]")
            Log.d(TAG, "✅ 创建 sessions.json")
        }
    }

    /**
     * Initialize canvas/
     */
    private fun initCanvasDirectory() {
        val canvasDir = File(BASE_DIR, "canvas")
        canvasDir.mkdirs()
        Log.d(TAG, "✅ 创建 canvas/")

        // Create index.html placeholder
        val indexHtml = File(canvasDir, "index.html")
        if (!indexHtml.exists()) {
            indexHtml.writeText("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>AndroidForClaw Canvas</title>
                </head>
                <body>
                    <h1>AndroidForClaw Canvas</h1>
                    <p>Agent workspace canvas placeholder</p>
                </body>
                </html>
            """.trimIndent())
            Log.d(TAG, "✅ 创建 canvas/index.html")
        }
    }

    /**
     * Initialize cron/
     */
    private fun initCronDirectory() {
        val cronDir = File(BASE_DIR, "cron")
        cronDir.mkdirs()
        Log.d(TAG, "✅ 创建 cron/")

        // Create jobs.json
        val jobsFile = File(cronDir, "jobs.json")
        if (!jobsFile.exists()) {
            jobsFile.writeText("[]")
            Log.d(TAG, "✅ 创建 cron/jobs.json")
        }
    }

    /**
     * Initialize devices/
     */
    private fun initDevicesDirectory() {
        val devicesDir = File(BASE_DIR, "devices")
        devicesDir.mkdirs()
        Log.d(TAG, "✅ 创建 devices/")

        // Create paired.json
        val pairedFile = File(devicesDir, "paired.json")
        if (!pairedFile.exists()) {
            pairedFile.writeText("{}")
            Log.d(TAG, "✅ 创建 devices/paired.json")
        }

        // Create pending.json
        val pendingFile = File(devicesDir, "pending.json")
        if (!pendingFile.exists()) {
            pendingFile.writeText("[]")
            Log.d(TAG, "✅ 创建 devices/pending.json")
        }
    }

    /**
     * Initialize update-check.json
     */
    private fun initUpdateCheck() {
        val updateCheckFile = File(BASE_DIR, "update-check.json")
        if (!updateCheckFile.exists()) {
            updateCheckFile.writeText("""{"lastCheck":"never","version":"0.0.0"}""")
            Log.d(TAG, "✅ 创建 update-check.json")
        }
    }

    /**
     * Initialize log file placeholders
     */
    private fun initLogFiles() {
        val appLog = File(BASE_DIR, "app.log")
        if (!appLog.exists()) {
            appLog.createNewFile()
            Log.d(TAG, "✅ 创建 app.log")
        }

        val gatewayLog = File(BASE_DIR, "gateway.log")
        if (!gatewayLog.exists()) {
            gatewayLog.createNewFile()
            Log.d(TAG, "✅ 创建 gateway.log")
        }
    }

    /**
     * Get Device ID
     */
    fun getDeviceId(): String {
        val deviceIdFile = File(BASE_DIR, ".device-id")
        return if (deviceIdFile.exists()) {
            deviceIdFile.readText().trim()
        } else {
            "unknown"
        }
    }

    /**
     * Check if already initialized
     */
    fun isInitialized(): Boolean {
        val baseDir = File(BASE_DIR)
        val deviceIdFile = File(BASE_DIR, ".device-id")
        return baseDir.exists() && deviceIdFile.exists()
    }
}

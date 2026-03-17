package com.xiaomo.androidforclaw.accessibility.service

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: in-app accessibility/observer service layer.
 */


import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.xiaomo.androidforclaw.accessibility.MediaProjectionHelper
import java.io.File

class AccessibilityBinderService : Service() {
    companion object {
        private const val TAG = "AccessibilityBinderService"
        var serviceInstance: PhoneAccessibilityService? = null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AccessibilityBinderService onCreate()")

        // 初始化 MediaProjectionHelper (使用工作空间)
        val workspace = File("/sdcard/.androidforclaw/workspace")
        val screenshotDir = File(workspace, "screenshots")
        MediaProjectionHelper.initialize(this, screenshotDir)
        Log.d(TAG, "MediaProjectionHelper initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AccessibilityBinderService destroyed")
    }
}

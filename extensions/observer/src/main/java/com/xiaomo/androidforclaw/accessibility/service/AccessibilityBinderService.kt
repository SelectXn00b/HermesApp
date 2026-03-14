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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Separate service for AIDL binding
 * This allows external apps to bind and control the accessibility service
 */
class AccessibilityBinderService : Service() {
    companion object {
        private const val TAG = "AccessibilityBinderService"
        var serviceInstance: PhoneAccessibilityService? = null

        // CountDownLatch 用于同步等待 serviceInstance 初始化
        private var readyLatch: CountDownLatch? = null

        // Binder instance reference (if already created)
        private var binderInstance: AccessibilityBinder? = null

        /**
         * 通知 serviceInstance 已准备好
         */
        fun notifyServiceReady() {
            Log.d(TAG, "notifyServiceReady called, latch=${readyLatch != null}")
            readyLatch?.countDown()
            Log.d(TAG, "countDown completed")
        }

        /**
         * 更新已存在的 binder 的 service 引用
         * 用于处理 bind 在 accessibility service 启动之前发生的情况
         */
        fun updateBinderService(service: PhoneAccessibilityService) {
            binderInstance?.setService(service)
            Log.d(TAG, "Binder service reference updated")
        }
    }

    private lateinit var binder: AccessibilityBinder

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "========== AccessibilityBinderService onCreate() called ==========")
        Log.e(TAG, "onCreate: serviceInstance = ${serviceInstance != null}")

        // 初始化 MediaProjectionHelper (使用工作空间)
        val workspace = File("/sdcard/.androidforclaw/workspace")
        val screenshotDir = File(workspace, "screenshots")
        MediaProjectionHelper.initialize(this, screenshotDir)
        Log.d(TAG, "MediaProjectionHelper initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e(TAG, "========== onStartCommand called ==========")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.e(TAG, "========== onBind called with intent: $intent ==========")
        Log.e(TAG, "onBind: serviceInstance = ${serviceInstance != null}")

        // 如果 onCreate 没被调用，手动初始化
        if (!this::binder.isInitialized) {
            Log.e(TAG, "onCreate was not called, initializing manually...")
            try {
                // 初始化 MediaProjectionHelper (使用工作空间)
                val workspace = File("/sdcard/.androidforclaw/workspace")
                val screenshotDir = File(workspace, "screenshots")
                MediaProjectionHelper.initialize(this, screenshotDir)
                Log.d(TAG, "Manual initialization complete")
            } catch (e: Exception) {
                Log.e(TAG, "Manual initialization failed", e)
            }
        }

        // IMPORTANT: Always return a binder, even if serviceInstance is not ready yet!
        // The binder will handle "not ready" state internally by returning errors.
        // Returning null here causes Android to kill the service.

        // Check if serviceInstance is ready
        var accessibilityService = serviceInstance
        if (accessibilityService != null) {
            Log.i(TAG, "✅ Accessibility service already ready, creating binder")
            if (!this::binder.isInitialized) {
                binder = AccessibilityBinder(accessibilityService)
                binderInstance = binder
            }
        } else {
            Log.w(TAG, "⚠️ Accessibility service not ready yet, creating placeholder binder")
            Log.w(TAG, "   Binder methods will return errors until PhoneAccessibilityService starts")
            // Create binder with null service - methods will return errors until ready
            if (!this::binder.isInitialized) {
                binder = AccessibilityBinder(null)
                binderInstance = binder
            }
        }

        Log.i(TAG, "✅ Returning binder (ready=${serviceInstance != null})")
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AccessibilityBinderService destroyed")
    }
}

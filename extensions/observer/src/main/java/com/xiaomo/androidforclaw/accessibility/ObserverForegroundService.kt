package com.xiaomo.androidforclaw.accessibility

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: observer permission and projection flow.
 */


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * 前台服务 (重构版)
 *
 * 用于维持 MediaProjection 录屏权限
 *
 * 主要改进:
 * 1. 简化启动流程 - 启动即进入前台模式
 * 2. 移除广播机制 - 降低复杂度
 * 3. 添加健康检查
 * 4. 改进通知内容
 */
class ObserverForegroundService : Service() {
    companion object {
        private const val TAG = "S4ClawForeground"
        private const val NOTIFICATION_ID = 10086
        private const val CHANNEL_ID = "s4claw_media_projection"
        private const val CHANNEL_NAME = "S4Claw 录屏服务"

        @Volatile
        private var isRunning = false

        fun isServiceRunning(): Boolean = isRunning
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "========== ForegroundService onCreate ==========")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "========== onStartCommand called ==========")

        try {
            // 立即进入前台模式
            startForegroundWithType()
            isRunning = true

            Log.i(TAG, "✅ Foreground service started successfully")
            Log.d(TAG, "   Notification ID: $NOTIFICATION_ID")
            Log.d(TAG, "   Channel ID: $CHANNEL_ID")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start foreground service", e)
            // 启动失败,停止服务
            stopSelf()
            isRunning = false
        }

        return START_STICKY  // 系统杀掉后会自动重启
    }

    /**
     * 使用正确的 foregroundServiceType 启动前台服务
     */
    private fun startForegroundWithType() {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 需要明确指定 foregroundServiceType
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
            Log.d(TAG, "Started with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION (Android 14+)")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-13
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Started as foreground service (Android 10-13)")
        } else {
            // Android 9-
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Started as foreground service (Android 9-)")
        }
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "S4Claw 需要前台服务来维持录屏权限"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "✅ Notification channel created")
        }
    }

    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        // 点击通知打开权限管理页面
        val notificationIntent = Intent(this, PermissionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            pendingIntentFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("S4Claw 录屏服务")
            .setContentText("正在维持录屏权限 · 点击查看状态")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(true)
            .setOngoing(true)  // 不可滑动删除
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null  // 不支持绑定
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.w(TAG, "========== ForegroundService onDestroy ==========")
        Log.w(TAG, "   Service stopped - MediaProjection may be invalidated")
    }
}

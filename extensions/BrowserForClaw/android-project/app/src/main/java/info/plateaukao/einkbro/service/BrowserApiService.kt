/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: Android service layer.
 */
package info.plateaukao.einkbro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import info.plateaukao.einkbro.R
import info.plateaukao.einkbro.browser.control.server.SimpleBrowserHttpServer

/**
 * BrowserForClaw HTTP API 前台服务
 *
 * 确保 HTTP Server (端口 8766) 持续运行在后台
 * 即使应用在后台或被系统回收,API 仍可响应
 */
class BrowserApiService : Service() {

    companion object {
        private const val TAG = "BrowserApiService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "browser_api_channel"
        private const val PORT = 8766

        fun start(context: Context) {
            val intent = Intent(context, BrowserApiService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "Starting BrowserApiService...")
        }

        fun stop(context: Context) {
            val intent = Intent(context, BrowserApiService::class.java)
            context.stopService(intent)
            Log.d(TAG, "Stopping BrowserApiService...")
        }
    }

    private var httpServer: SimpleBrowserHttpServer? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        // 创建通知渠道
        createNotificationChannel()

        // 启动前台服务通知
        startForeground(NOTIFICATION_ID, createNotification())

        // 启动 HTTP Server
        startHttpServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        return START_STICKY // 服务被杀后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        stopHttpServer()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Browser API Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps BrowserForClaw HTTP API running"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // 点击通知打开主 Activity
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BrowserForClaw API")
            .setContentText("HTTP API running on port $PORT")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 使用系统图标
            .setOngoing(true) // 不可滑动删除
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startHttpServer() {
        try {
            if (httpServer == null) {
                httpServer = SimpleBrowserHttpServer(PORT)
                httpServer?.start()
                Log.i(TAG, "✅ HTTP Server started on port $PORT")
            } else {
                Log.w(TAG, "HTTP Server already running")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start HTTP Server", e)
        }
    }

    private fun stopHttpServer() {
        try {
            httpServer?.stop()
            httpServer = null
            Log.i(TAG, "✅ HTTP Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop HTTP Server", e)
        }
    }
}

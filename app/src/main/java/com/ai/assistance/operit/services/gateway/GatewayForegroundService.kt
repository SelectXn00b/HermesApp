package com.ai.assistance.operit.services.gateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.application.ForegroundServiceCompat
import com.ai.assistance.operit.hermes.gateway.HermesGatewayController
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Long-lived foreground service that owns the Hermes gateway runner.
 *
 * Lives as long as the user keeps Hermes connections on. Starts and stops
 * the [HermesGatewayController] in response to [Intent] actions from the
 * Settings UI ([ACTION_START] / [ACTION_STOP]) and from [GatewayBootReceiver]
 * at boot. Survives configuration changes and app backgrounding.
 */
class GatewayForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var controller: HermesGatewayController
    private var wakeLock: PowerManager.WakeLock? = null
    private var statusJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        controller = HermesGatewayController.getInstance(applicationContext)
        createNotificationChannel()
        startForegroundWithStatus(status = getString(R.string.hermes_gateway_service_notification_text_starting))
        acquireWakeLock()
        observeStatus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    controller.stop()
                    stopSelf()
                }
            }
            else -> {
                serviceScope.launch { controller.start() }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        statusJob?.cancel()
        try {
            runBlocking { controller.stop() }
        } catch (_: Throwable) {
        }
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeStatus() {
        statusJob = serviceScope.launch {
            controller.status.collectLatest { status ->
                val text = when (status) {
                    HermesGatewayController.Status.STARTING -> getString(R.string.hermes_gateway_service_notification_text_starting)
                    HermesGatewayController.Status.RUNNING -> getString(R.string.hermes_gateway_service_notification_text_running)
                    HermesGatewayController.Status.STOPPING -> getString(R.string.hermes_gateway_service_notification_text_stopping)
                    HermesGatewayController.Status.STOPPED -> getString(R.string.hermes_gateway_service_notification_text_stopped)
                    HermesGatewayController.Status.FAILED -> {
                        val reason = controller.error.value.orEmpty()
                        if (reason.isEmpty()) getString(R.string.hermes_gateway_service_notification_text_failed)
                        else getString(R.string.hermes_gateway_service_notification_text_failed_with_reason, reason)
                    }
                }
                updateNotification(text)
            }
        }
    }

    private fun startForegroundWithStatus(status: String) {
        val notification = buildNotification(status)
        ForegroundServiceCompat.startForeground(
            service = this,
            notificationId = NOTIFICATION_ID,
            notification = notification,
            types = ForegroundServiceCompat.buildTypes(dataSync = true),
        )
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(status: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.hermes_gateway_service_notification_title))
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.hermes_gateway_service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.hermes_gateway_service_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) wakeLock?.acquire(WAKE_LOCK_MAX_MILLIS)
        } catch (e: Exception) {
            AppLogger.w(TAG, "acquireWakeLock failed: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val TAG = "HermesGatewayService"
        const val CHANNEL_ID = "hermes_gateway_service"
        const val NOTIFICATION_ID = 71_642
        private const val WAKE_LOCK_TAG = "HermesApp:GatewayWakeLock"
        private const val WAKE_LOCK_MAX_MILLIS = 60L * 60L * 1000L

        const val ACTION_START = "com.ai.assistance.operit.HERMES_GATEWAY_START"
        const val ACTION_STOP = "com.ai.assistance.operit.HERMES_GATEWAY_STOP"

        fun start(context: Context) {
            val intent = Intent(context, GatewayForegroundService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, GatewayForegroundService::class.java).apply { action = ACTION_STOP }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}

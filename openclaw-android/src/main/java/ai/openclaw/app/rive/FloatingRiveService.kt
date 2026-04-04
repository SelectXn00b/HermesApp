package ai.openclaw.app.rive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import app.rive.runtime.kotlin.RiveAnimationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingRiveService : Service() {

    private var windowManager: WindowManager? = null
    private var riveView: RiveAnimationView? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        createFloatingWindow()
        observeRiveState()
        isRunning = true
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        val view = riveView
        riveView = null
        if (view != null) {
            try { windowManager?.removeView(view) } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val channelId = "floating_rive_avatar"
        val channel = NotificationChannel(
            channelId, "Rive \u5316\u8eab", NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_slideshow)
            .setContentTitle("Rive \u5316\u8eab")
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(
            NOTIFICATION_ID, notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private fun createFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val size = (160 * density).toInt()

        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = (16 * density).toInt()
            y = (200 * density).toInt()
        }

        val view = RiveAnimationView(this).apply {
            setRiveResource(
                ai.openclaw.app.R.raw.robot_expressions,
                stateMachineName = STATE_MACHINE_NAME,
                autoplay = true,
            )
            setBackgroundColor(Color.TRANSPARENT)
        }
        riveView = view

        // Drag handling (same pattern as FloatingAvatarService)
        var initX = 0; var initY = 0; var touchX = 0f; var touchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX - (event.rawX - touchX).toInt()
                    params.y = initY - (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(view, params)
    }

    private fun observeRiveState() {
        // Trigger inputs
        scope.launch {
            RiveStateHolder.triggers.collect { triggerName ->
                try {
                    riveView?.fireState(STATE_MACHINE_NAME, triggerName)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fire trigger: $triggerName", e)
                }
            }
        }
        // Number inputs
        scope.launch {
            RiveStateHolder.numberInputs.collect { inputs ->
                inputs.forEach { (name, value) ->
                    try {
                        riveView?.setNumberState(STATE_MACHINE_NAME, name, value)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to set number input: $name=$value", e)
                    }
                }
            }
        }
        // Boolean inputs
        scope.launch {
            RiveStateHolder.booleanInputs.collect { inputs ->
                inputs.forEach { (name, value) ->
                    try {
                        riveView?.setBooleanState(STATE_MACHINE_NAME, name, value)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to set boolean input: $name=$value", e)
                    }
                }
            }
        }
        // Pause / resume
        scope.launch {
            RiveStateHolder.paused.collect { paused ->
                val view = riveView ?: return@collect
                if (paused) view.pause() else view.play()
            }
        }
    }

    companion object {
        private const val TAG = "FloatingRiveService"
        private const val NOTIFICATION_ID = 9528
        private const val STATE_MACHINE_NAME = "State Machine 1"

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, FloatingRiveService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingRiveService::class.java))
        }

        fun toggle(context: Context) {
            if (isRunning) stop(context) else start(context)
        }
    }
}

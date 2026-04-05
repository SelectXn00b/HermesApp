package ai.openclaw.app.rive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Outline
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import app.rive.runtime.kotlin.RiveAnimationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingRiveService : Service() {

    private var windowManager: WindowManager? = null
    private var containerView: FrameLayout? = null
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
        val container = containerView
        riveView = null
        containerView = null
        if (view != null) {
            try { view.stop() } catch (_: Exception) {}
        }
        if (container != null) {
            try { windowManager?.removeView(container) } catch (_: Exception) {}
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

        // Safety: remove any stale view from a previous instance (Android 12 race condition)
        val oldView = riveView
        if (oldView != null) {
            try { oldView.stop() } catch (_: Exception) {}
            try { windowManager?.removeView(oldView) } catch (_: Exception) {}
        }

        val density = resources.displayMetrics.density
        val cfg = RiveStateHolder.displayConfig.value
        val containerSize = (cfg.containerSizeDp * density).toInt()
        val riveSize = (cfg.containerSizeDp * cfg.zoomFactor * density).toInt()
        val offsetX = -(riveSize - containerSize) / 2 + (cfg.offsetXDp * density).toInt()
        val offsetY = -(riveSize - containerSize) / 2 + (cfg.offsetYDp * density).toInt()

        val params = WindowManager.LayoutParams(
            containerSize, containerSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (16 * density).toInt()
            y = (120 * density).toInt()
        }

        val view = try {
            RiveAnimationView(this).apply {
                setRiveResource(
                    ai.openclaw.app.R.raw.robot_expressions,
                    stateMachineName = STATE_MACHINE_NAME,
                    autoplay = true,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load .riv (stateMachine=$STATE_MACHINE_NAME)", e)
            stopSelf()
            return
        }
        riveView = view
        // Disable cursor tracking — we don't need it in the floating window
        try { view.setBooleanState(STATE_MACHINE_NAME, "IsTracking", false) } catch (_: Exception) {}
        Log.i(TAG, "Rive loaded: stateMachine=$STATE_MACHINE_NAME, container=${cfg.containerSizeDp}dp, zoom=${cfg.zoomFactor}")

        // Container: intercepts all touches for drag
        val container = object : FrameLayout(this@FloatingRiveService) {
            override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = true
        }.apply {
            clipChildren = true
            clipToPadding = true
        }
        val riveLayoutParams = FrameLayout.LayoutParams(riveSize, riveSize).apply {
            leftMargin = offsetX
            topMargin = offsetY
        }
        container.addView(view, riveLayoutParams)

        // Circular clipping — crop to just the robot face
        container.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setOval(0, 0, containerSize, containerSize)
            }
        }
        container.clipToOutline = true

        containerView = container

        // Drag handling
        var initX = 0; var initY = 0; var touchX = 0f; var touchY = 0f
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX - (event.rawX - touchX).toInt()
                    params.y = initY - (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(container, params)
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(container, params)
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

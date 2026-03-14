package com.xiaomo.androidforclaw.accessibility

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: observer permission and projection flow.
 */


import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MediaProjection 录屏权限管理器 (重构版)
 *
 * 主要改进:
 * 1. 正确的生命周期管理
 * 2. 自动启动前台服务
 * 3. 完善的回调处理
 * 4. 错误恢复机制
 * 5. 线程安全
 */
object MediaProjectionHelper {
    private const val TAG = "MediaProjectionHelper"
    private const val REQUEST_CODE = 10086

    // 状态常量
    const val STATUS_NOT_INITIALIZED = "未初始化"
    const val STATUS_WAITING_PERMISSION = "等待授权"
    const val STATUS_AUTHORIZED = "已授权"
    const val STATUS_ERROR = "出现错误"

    // 状态管理
    private enum class State {
        IDLE,           // 空闲
        REQUESTING,     // 正在请求权限
        AUTHORIZED,     // 已授权
        ERROR           // 错误状态
    }

    @Volatile
    private var currentState = State.IDLE

    // MediaProjection 相关
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    // 屏幕参数
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    // 上下文和配置
    private var appContext: Context? = null
    private var screenshotDir: File? = null

    // 前台服务状态
    private val isForegroundServiceRunning = AtomicBoolean(false)

    // 主线程 Handler
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 初始化 - 必须在使用前调用
     */
    fun initialize(context: Context, screenshotDirectory: File) {
        appContext = context.applicationContext
        screenshotDir = screenshotDirectory

        if (!screenshotDirectory.exists()) {
            screenshotDirectory.mkdirs()
        }

        // 设置目录权限
        try {
            screenshotDirectory.setReadable(true, false)
            screenshotDirectory.setWritable(true, false)
            screenshotDirectory.setExecutable(true, false)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set directory permissions", e)
        }

        // 获取屏幕参数
        updateScreenParameters(context)

        Log.i(TAG, "✅ MediaProjectionHelper initialized")
        Log.d(TAG, "   Screenshot dir: ${screenshotDirectory.absolutePath}")
        Log.d(TAG, "   Screen: ${screenWidth}x${screenHeight} @${screenDensity}dpi")
    }

    /**
     * 更新屏幕参数
     */
    private fun updateScreenParameters(context: Context) {
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = DisplayMetrics()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Service Context 没有 display,尝试获取,失败则使用默认 display
                val display = try {
                    context.display
                } catch (e: UnsupportedOperationException) {
                    Log.w(TAG, "Context.display not available (Service Context), using default display")
                    wm.defaultDisplay
                }
                display?.getRealMetrics(displayMetrics)
            } else {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(displayMetrics)
            }

            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
            screenDensity = displayMetrics.densityDpi

            Log.d(TAG, "Screen parameters updated: ${screenWidth}x${screenHeight} @${screenDensity}dpi")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update screen parameters, using defaults", e)
            // 使用常见的默认值
            screenWidth = 1080
            screenHeight = 2400
            screenDensity = 480
        }
    }

    /**
     * 请求录屏权限
     *
     * @return false 表示需要等待用户授权, true 表示已经授权
     */
    fun requestPermission(activity: Activity): Boolean {
        Log.d(TAG, "requestPermission called, current state: $currentState")

        // 如果已经授权,直接返回
        if (currentState == State.AUTHORIZED && mediaProjection != null) {
            Log.d(TAG, "Already authorized")
            return true
        }

        // 标记状态为正在请求
        currentState = State.REQUESTING

        try {
            // 启动前台服务 (立即进入前台模式避免 ANR)
            startForegroundService(activity)

            // 请求 MediaProjection 权限
            val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val intent = manager.createScreenCaptureIntent()
            activity.startActivityForResult(intent, REQUEST_CODE)

            Log.i(TAG, "📋 Screen capture permission request started")
            return false

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to request permission", e)
            currentState = State.ERROR
            stopForegroundService(activity)
            return false
        }
    }

    /**
     * 处理权限授予结果
     * 必须在 Activity.onActivityResult 中调用
     */
    fun handlePermissionResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CODE) {
            return false
        }

        Log.d(TAG, "handlePermissionResult: resultCode=$resultCode")

        if (resultCode == Activity.RESULT_OK && data != null) {
            try {
                // 创建 MediaProjection
                val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = manager.getMediaProjection(resultCode, data)

                if (mediaProjection == null) {
                    Log.e(TAG, "❌ MediaProjection is null despite RESULT_OK")
                    currentState = State.ERROR
                    stopForegroundService(activity)
                    return false
                }

                // 设置回调
                setupMediaProjectionCallbacks()

                // 初始化 ImageReader
                initializeImageReader()

                // 标记为已授权
                currentState = State.AUTHORIZED
                isForegroundServiceRunning.set(true)

                Log.i(TAG, "✅ MediaProjection permission granted successfully")
                Log.d(TAG, "   Foreground service: running")
                Log.d(TAG, "   ImageReader: ${imageReader != null}")
                return true

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to setup MediaProjection", e)
                currentState = State.ERROR
                cleanup()
                stopForegroundService(activity)
                return false
            }
        } else {
            Log.w(TAG, "⚠️ Permission denied by user")
            currentState = State.IDLE
            stopForegroundService(activity)
            return false
        }
    }

    /**
     * 设置 MediaProjection 回调
     */
    private fun setupMediaProjectionCallbacks() {
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                Log.w(TAG, "⚠️ MediaProjection stopped by system")
                mainHandler.post {
                    currentState = State.IDLE
                    cleanup()
                }
            }
        }, mainHandler)
    }

    /**
     * 初始化 ImageReader
     */
    private fun initializeImageReader() {
        try {
            // 关闭旧的 ImageReader
            imageReader?.close()
            virtualDisplay?.release()

            // 创建新的 ImageReader
            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2  // 缓冲区数量
            )

            // 创建 VirtualDisplay
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )

            Log.d(TAG, "✅ ImageReader and VirtualDisplay initialized")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize ImageReader", e)
            throw e
        }
    }

    /**
     * 捕获屏幕截图
     *
     * @return Pair<Bitmap, String> 或 null (失败)
     */
    fun captureScreen(): Pair<Bitmap, String>? {
        if (currentState != State.AUTHORIZED || mediaProjection == null || imageReader == null) {
            Log.w(TAG, "Cannot capture: not authorized (state=$currentState, projection=${mediaProjection != null}, reader=${imageReader != null})")
            return null
        }

        try {
            // 获取最新的 Image
            val image = imageReader?.acquireLatestImage()
            if (image == null) {
                Log.w(TAG, "Failed to acquire image")
                return null
            }

            try {
                // 从 Image 创建 Bitmap
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * screenWidth

                val bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)

                // 裁剪掉 padding
                val croppedBitmap = if (rowPadding > 0) {
                    Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                } else {
                    bitmap
                }

                // 保存到文件
                val timestamp = System.currentTimeMillis()
                val file = File(screenshotDir, "screenshot_$timestamp.png")
                FileOutputStream(file).use { out ->
                    croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                Log.i(TAG, "✅ Screenshot captured: ${file.absolutePath}")
                return Pair(croppedBitmap, file.absolutePath)

            } finally {
                image.close()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to capture screen", e)
            return null
        }
    }

    /**
     * 检查权限是否已授予
     */
    fun isAuthorized(): Boolean {
        return currentState == State.AUTHORIZED && mediaProjection != null
    }

    /**
     * 获取详细状态
     */
    fun getDetailedStatus(): String {
        return when (currentState) {
            State.IDLE -> STATUS_NOT_INITIALIZED
            State.REQUESTING -> STATUS_WAITING_PERMISSION
            State.AUTHORIZED -> {
                if (mediaProjection != null && imageReader != null) {
                    "$STATUS_AUTHORIZED\n前台服务: ${if (isForegroundServiceRunning.get()) "运行中" else "已停止"}"
                } else {
                    "$STATUS_ERROR: 对象未初始化"
                }
            }
            State.ERROR -> STATUS_ERROR
        }
    }

    /**
     * 重置权限 (释放资源但保留前台服务)
     */
    fun reset() {
        Log.i(TAG, "Resetting MediaProjection...")
        currentState = State.IDLE
        cleanup()
    }

    /**
     * 完全释放 (包括停止前台服务)
     */
    fun releaseCompletely(context: Context) {
        Log.i(TAG, "Releasing MediaProjection completely...")
        currentState = State.IDLE
        cleanup()
        stopForegroundService(context)
    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null

            imageReader?.close()
            imageReader = null

            mediaProjection?.stop()
            mediaProjection = null

            Log.d(TAG, "✅ Resources cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    /**
     * 启动前台服务
     */
    private fun startForegroundService(context: Context) {
        try {
            val intent = Intent(context, ObserverForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "✅ Foreground service started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start foreground service", e)
        }
    }

    /**
     * 停止前台服务
     */
    private fun stopForegroundService(context: Context) {
        try {
            val intent = Intent(context, ObserverForegroundService::class.java)
            context.stopService(intent)
            isForegroundServiceRunning.set(false)
            Log.i(TAG, "✅ Foreground service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground service", e)
        }
    }

    // ========== 向后兼容的旧 API (废弃) ==========

    @Deprecated("Use initialize() instead", ReplaceWith("initialize(context, screenshotDirectory)"))
    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    @Deprecated("Use initialize() instead", ReplaceWith("initialize(context, screenshotDirectory)"))
    fun setScreenshotDirectory(dir: File) {
        screenshotDir = dir
        if (!dir.exists()) dir.mkdirs()
    }

    @Deprecated("Use requestPermission() instead", ReplaceWith("requestPermission(activity)"))
    fun requestMediaProjection(activity: Activity): Boolean {
        return requestPermission(activity)
    }

    @Deprecated("Use handlePermissionResult() instead")
    fun handleActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return handlePermissionResult(activity, requestCode, resultCode, data)
    }

    @Deprecated("Use isAuthorized() instead", ReplaceWith("isAuthorized()"))
    fun isMediaProjectionGranted(): Boolean {
        return isAuthorized()
    }

    @Deprecated("Use getDetailedStatus() instead", ReplaceWith("getDetailedStatus()"))
    fun getPermissionStatus(): String {
        return getDetailedStatus()
    }

    @Deprecated("Use reset() instead", ReplaceWith("reset()"))
    fun resetPermission() {
        reset()
    }

    @Deprecated("Use releaseCompletely() instead", ReplaceWith("releaseCompletely(context)"))
    fun releasePermissionCompletely(context: Context) {
        releaseCompletely(context)
    }
}

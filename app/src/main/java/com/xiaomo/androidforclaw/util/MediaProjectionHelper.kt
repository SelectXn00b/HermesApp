/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: utility helpers.
 */
package com.xiaomo.androidforclaw.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Context.MEDIA_PROJECTION_SERVICE
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.graphics.createBitmap
import com.xiaomo.androidforclaw.core.ForegroundService
import com.xiaomo.androidforclaw.core.MyApplication
import com.xiaomo.androidforclaw.util.LayoutExceptionLogger
import java.io.File
import java.io.FileOutputStream

object MediaProjectionHelper {
    private const val REQUEST_CODE = 10086

    // 录屏权限状态常量
    const val STATUS_AUTHORIZED = "已授权"
    const val STATUS_OBJECT_NULL = "权限已获取但对象为空"
    const val STATUS_NOT_AUTHORIZED = "未授权"

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var isPermissionGranted = false

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    fun requestMediaProjection(activity: Activity): Boolean {
        android.util.Log.d("MediaProjectionHelper", "requestMediaProjection: isPermissionGranted=$isPermissionGranted, mediaProjection=$mediaProjection")
        if (isPermissionGranted && mediaProjection != null) {
            android.util.Log.d("MediaProjectionHelper", "Already granted, returning true")
            return true
        }

        // 启动前台服务 (MediaProjection 需要前台服务)
        val foregroundServiceIntent = Intent(activity, ForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(foregroundServiceIntent)
        } else {
            activity.startService(foregroundServiceIntent)
        }
        android.util.Log.d("MediaProjectionHelper", "Foreground service started")

        val mpm =
            activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mpm.createScreenCaptureIntent()
        android.util.Log.d("MediaProjectionHelper", "Starting activity for result with REQUEST_CODE=$REQUEST_CODE")
        activity.startActivityForResult(intent, REQUEST_CODE)
        android.util.Log.d("MediaProjectionHelper", "startActivityForResult called")
        return false
    }
    
    /**
     * 检查录屏权限是否已授权
     */
    fun isMediaProjectionGranted(): Boolean {
        return isPermissionGranted && mediaProjection != null
    }
    
    /**
     * 获取录屏权限状态
     */
    fun getPermissionStatus(): String {
        return when {
            isPermissionGranted && mediaProjection != null -> STATUS_AUTHORIZED
            isPermissionGranted -> STATUS_OBJECT_NULL
            else -> STATUS_NOT_AUTHORIZED
        }
    }
    
    /**
     * 重置录屏权限状态
     */
    fun resetPermission() {
        isPermissionGranted = false
        mediaProjection?.stop()
        mediaProjection = null
        imageReader?.close()
        imageReader = null
    }
    
    /**
     * 强制重新申请录屏权限
     */
    fun forceRequestPermission(activity: Activity) {
        resetPermission()
        requestMediaProjection(activity)
    }

    fun handleActivityResult(
        context: Context,
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            initScreenCapture(context, resultCode, data)
            isPermissionGranted = true
            return true
        } else if (requestCode == REQUEST_CODE) {
            // 用户拒绝了权限
            isPermissionGranted = false
            mediaProjection = null
            imageReader = null
        }
        return false
    }

    // 初始化截屏
    private fun initScreenCapture(context: Context, resultCode: Int, data: Intent) {
        val mediaProjectionManager =
            context.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Android 14+ 需要先注册 callback
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            mediaProjection?.registerCallback(object : android.media.projection.MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    mediaProjection?.stop()
                    mediaProjection = null
                    imageReader?.close()
                    imageReader = null
                }
            }, null)
        } else {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        }

        // 获取屏幕信息
        // 使用真实物理分辨率避免虚拟显示缩放导致坐标错位
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val realMetrics = DisplayMetrics()
        display.getRealMetrics(realMetrics)
        screenWidth = realMetrics.widthPixels
        screenHeight = realMetrics.heightPixels
        screenDensity = realMetrics.densityDpi

        // 创建 ImageReader
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight, PixelFormat.RGBA_8888, 1
        )

        mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    fun captureScreen(): Pair<Bitmap, String>? {
        return try {
            val image = imageReader?.acquireLatestImage() ?: return null
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = createBitmap(screenWidth + rowPadding / pixelStride, screenHeight)
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            var path = ""
            // 裁剪为实际屏幕大小
            val bitmap2 = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)?.also {
                path = saveBitmap(it) ?: ""
            }
            if (bitmap2 == null) {
                null
            } else {
                Pair(bitmap2, path)
            }
        } catch (e: Exception) {
            LayoutExceptionLogger.log("MediaProjectionHelper#takeScreenshot", e)
            e.printStackTrace()
            null
        }
    }

    private fun saveBitmap(bitmap: Bitmap): String? {
        val dir = File(MyApplication.application.getExternalFilesDir(null), "Screenshots")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "screenshot_${System.currentTimeMillis()}.png")
        return try {
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            file.absolutePath
        } catch (e: Exception) {
            LayoutExceptionLogger.log("MediaProjectionHelper#saveScreenshot", e)
            e.printStackTrace()
            null
        }
    }

}
/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: utility helpers.
 */
package com.xiaomo.androidforclaw.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import com.xiaomo.androidforclaw.util.LayoutExceptionLogger

object ScreenshotUtil {

    var mediaProjection1: MediaProjection? = null
    fun takeScreenshot(
        context: Context,
        mediaProjection: MediaProjection = mediaProjection1!!
    ): Bitmap? {
        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        val virtualDisplay: VirtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )
        val image = imageReader.acquireLatestImage()
        if (image != null) {
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()
            virtualDisplay.release()
            return bitmap
        } else {
            virtualDisplay.release()
        }
        return null
    }

    private fun saveBitmap(bitmap: Bitmap): String? {
        val dir = File(Environment.getExternalStorageDirectory(), "Screenshots")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "screenshot_${System.currentTimeMillis()}.png")
        return try {
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            file.absolutePath
        } catch (e: Exception) {
            LayoutExceptionLogger.log("ScreenshotUtil#saveScreenshot", e)
            e.printStackTrace()
            null
        }
    }
}
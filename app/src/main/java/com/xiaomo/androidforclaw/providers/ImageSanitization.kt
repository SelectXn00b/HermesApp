package com.xiaomo.androidforclaw.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/image-sanitization.ts
 * - ../openclaw/src/agents/tool-images.ts
 *
 * AndroidForClaw adaptation: image sanitization limits and tool result image processing.
 */

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.xiaomo.androidforclaw.logging.Log
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Image sanitization limits.
 * Aligned with OpenClaw image-sanitization.ts.
 */
data class ImageSanitizationLimits(
    val maxDimensionPx: Int = DEFAULT_IMAGE_MAX_DIMENSION_PX,
    val maxBytes: Long = DEFAULT_IMAGE_MAX_BYTES
)

/**
 * Default image sanitization constants.
 * Aligned with OpenClaw image-sanitization.ts defaults.
 */
const val DEFAULT_IMAGE_MAX_DIMENSION_PX = 1200
const val DEFAULT_IMAGE_MAX_BYTES = 5L * 1024 * 1024  // 5MB

/**
 * Image sanitization — downscale and recompress images for API limits.
 * Aligned with OpenClaw image-sanitization.ts + tool-images.ts.
 */
object ImageSanitization {

    private const val TAG = "ImageSanitization"

    /**
     * Resolve image sanitization limits from config.
     * Aligned with OpenClaw resolveImageSanitizationLimits.
     */
    fun resolveImageSanitizationLimits(
        configMaxDimension: Int? = null
    ): ImageSanitizationLimits {
        val maxDim = if (configMaxDimension != null && configMaxDimension > 0) {
            configMaxDimension
        } else {
            DEFAULT_IMAGE_MAX_DIMENSION_PX
        }
        return ImageSanitizationLimits(maxDimensionPx = maxDim)
    }

    /**
     * Sanitize a base64-encoded image: downscale and recompress to fit limits.
     * Returns the sanitized base64 string, or null if the image cannot be processed.
     *
     * Aligned with OpenClaw sanitizeToolResultImages (core logic).
     */
    fun sanitizeBase64Image(
        base64Data: String,
        limits: ImageSanitizationLimits = ImageSanitizationLimits()
    ): String? {
        // Decode base64
        val imageBytes = try {
            Base64.decode(base64Data, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid base64 image data: ${e.message}")
            return null
        }

        // Check if already within limits
        if (imageBytes.size <= limits.maxBytes) {
            val dimensions = getImageDimensions(imageBytes)
            if (dimensions != null && dimensions.first <= limits.maxDimensionPx && dimensions.second <= limits.maxDimensionPx) {
                return base64Data  // Already within limits
            }
        }

        // Decode bitmap
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: run {
                Log.w(TAG, "Failed to decode image")
                return null
            }

        // Calculate target dimensions
        val (targetWidth, targetHeight) = calculateTargetDimensions(
            bitmap.width, bitmap.height, limits.maxDimensionPx
        )

        // Try different quality levels
        val qualities = listOf(85, 70, 50, 30)
        for (quality in qualities) {
            val scaled = if (targetWidth != bitmap.width || targetHeight != bitmap.height) {
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            } else {
                bitmap
            }

            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)

            if (scaled != bitmap) scaled.recycle()

            if (output.size() <= limits.maxBytes) {
                val result = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                if (!bitmap.isRecycled) bitmap.recycle()
                return result
            }
        }

        // Final attempt: aggressive downscale
        val aggressiveScale = sqrt(limits.maxBytes.toDouble() / imageBytes.size).coerceAtMost(0.5)
        val aggressiveWidth = (bitmap.width * aggressiveScale).toInt().coerceAtLeast(100)
        val aggressiveHeight = (bitmap.height * aggressiveScale).toInt().coerceAtLeast(100)

        val scaled = Bitmap.createScaledBitmap(bitmap, aggressiveWidth, aggressiveHeight, true)
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 30, output)
        scaled.recycle()
        if (!bitmap.isRecycled) bitmap.recycle()

        return if (output.size() <= limits.maxBytes) {
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } else {
            Log.w(TAG, "Image too large even after aggressive compression: ${output.size()} bytes")
            null
        }
    }

    /**
     * Get image dimensions without decoding the full image.
     */
    private fun getImageDimensions(imageBytes: ByteArray): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    }

    /**
     * Calculate target dimensions preserving aspect ratio within max dimension.
     */
    private fun calculateTargetDimensions(
        width: Int, height: Int, maxDimension: Int
    ): Pair<Int, Int> {
        if (width <= maxDimension && height <= maxDimension) {
            return width to height
        }
        val ratio = min(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
        return (width * ratio).toInt().coerceAtLeast(1) to
            (height * ratio).toInt().coerceAtLeast(1)
    }
}

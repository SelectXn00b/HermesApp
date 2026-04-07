package com.xiaomo.androidforclaw.media

/**
 * OpenClaw Source Reference:
 * - src/media/image-ops.ts
 *
 * Image operations: metadata extraction, resize, format conversion, EXIF handling.
 *
 * Android adaptation: uses android.graphics.Bitmap instead of sharp/sips.
 * BitmapFactory for decoding, Bitmap.createScaledBitmap for resizing,
 * ExifInterface for orientation.
 */

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.xiaomo.androidforclaw.logging.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

private const val TAG = "ImageOps"

// ============================================================================
// Constants (aligned with OpenClaw)
// ============================================================================

val IMAGE_REDUCE_QUALITY_STEPS = intArrayOf(85, 75, 65, 55, 45, 35)
const val MAX_IMAGE_INPUT_PIXELS = 25_000_000

// ============================================================================
// Image metadata
// ============================================================================

/**
 * Image dimensions metadata.
 * Aligned with OpenClaw ImageMetadata.
 */
data class ImageMetadata(
    val width: Int,
    val height: Int
)

/**
 * Build resize side grid.
 * Aligned with OpenClaw buildImageResizeSideGrid.
 */
fun buildImageResizeSideGrid(maxSide: Int, sideStart: Int): List<Int> {
    return listOf(sideStart, 1800, 1600, 1400, 1200, 1000, 800)
        .map { min(maxSide, it) }
        .filter { it > 0 }
        .distinct()
        .sortedDescending()
}

// ============================================================================
// Metadata reading from buffer headers (aligned with OpenClaw)
// ============================================================================

private fun isPositiveImageDimension(value: Int): Boolean = value > 0

private fun buildImageMetadataOrNull(width: Int, height: Int): ImageMetadata? {
    if (!isPositiveImageDimension(width) || !isPositiveImageDimension(height)) return null
    return ImageMetadata(width, height)
}

/**
 * Read PNG dimensions from header bytes.
 */
private fun readPngMetadata(buffer: ByteArray): ImageMetadata? {
    if (buffer.size < 24) return null
    if (buffer[0] != 0x89.toByte() || buffer[1] != 0x50.toByte() ||
        buffer[2] != 0x4E.toByte() || buffer[3] != 0x47.toByte()
    ) return null
    // Check IHDR chunk at offset 12
    if (String(buffer, 12, 4, Charsets.US_ASCII) != "IHDR") return null
    val width = readUInt32BE(buffer, 16)
    val height = readUInt32BE(buffer, 20)
    return buildImageMetadataOrNull(width, height)
}

/**
 * Read GIF dimensions from header bytes.
 */
private fun readGifMetadata(buffer: ByteArray): ImageMetadata? {
    if (buffer.size < 10) return null
    val sig = String(buffer, 0, 6, Charsets.US_ASCII)
    if (sig != "GIF87a" && sig != "GIF89a") return null
    val width = readUInt16LE(buffer, 6)
    val height = readUInt16LE(buffer, 8)
    return buildImageMetadataOrNull(width, height)
}

/**
 * Read WebP dimensions from header bytes.
 */
private fun readWebpMetadata(buffer: ByteArray): ImageMetadata? {
    if (buffer.size < 30) return null
    if (String(buffer, 0, 4, Charsets.US_ASCII) != "RIFF") return null
    if (String(buffer, 8, 4, Charsets.US_ASCII) != "WEBP") return null

    val chunkType = String(buffer, 12, 4, Charsets.US_ASCII)
    return when (chunkType) {
        "VP8X" -> {
            if (buffer.size < 30) return null
            buildImageMetadataOrNull(1 + readUInt24LE(buffer, 24), 1 + readUInt24LE(buffer, 27))
        }
        "VP8 " -> {
            if (buffer.size < 30) return null
            buildImageMetadataOrNull(readUInt16LE(buffer, 26) and 0x3FFF, readUInt16LE(buffer, 28) and 0x3FFF)
        }
        "VP8L" -> {
            if (buffer.size < 25 || buffer[20] != 0x2F.toByte()) return null
            val bits = (buffer[21].toInt() and 0xFF) or
                ((buffer[22].toInt() and 0xFF) shl 8) or
                ((buffer[23].toInt() and 0xFF) shl 16) or
                ((buffer[24].toInt() and 0xFF) shl 24)
            buildImageMetadataOrNull((bits and 0x3FFF) + 1, ((bits shr 14) and 0x3FFF) + 1)
        }
        else -> null
    }
}

/**
 * Read JPEG dimensions from header bytes.
 */
private fun readJpegMetadata(buffer: ByteArray): ImageMetadata? {
    if (buffer.size < 4 || buffer[0] != 0xFF.toByte() || buffer[1] != 0xD8.toByte()) return null

    var offset = 2
    while (offset + 8 < buffer.size) {
        while (offset < buffer.size && buffer[offset] == 0xFF.toByte()) offset++
        if (offset >= buffer.size) return null

        val marker = buffer[offset].toInt() and 0xFF
        offset++
        if (marker == 0xD8 || marker == 0xD9) continue
        if (marker == 0x01 || (marker in 0xD0..0xD7)) continue
        if (offset + 1 >= buffer.size) return null

        val segmentLength = readUInt16BE(buffer, offset)
        if (segmentLength < 2 || offset + segmentLength > buffer.size) return null

        val isStartOfFrame = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
        if (isStartOfFrame) {
            if (segmentLength < 7 || offset + 6 >= buffer.size) return null
            return buildImageMetadataOrNull(readUInt16BE(buffer, offset + 5), readUInt16BE(buffer, offset + 3))
        }

        offset += segmentLength
    }

    return null
}

/**
 * Read image metadata from buffer header bytes.
 * Aligned with OpenClaw readImageMetadataFromHeader.
 */
fun readImageMetadataFromHeader(buffer: ByteArray): ImageMetadata? {
    return readPngMetadata(buffer)
        ?: readGifMetadata(buffer)
        ?: readWebpMetadata(buffer)
        ?: readJpegMetadata(buffer)
}

/**
 * Check if image exceeds pixel limit.
 */
fun exceedsImagePixelLimit(meta: ImageMetadata): Boolean {
    return meta.width.toLong() * meta.height.toLong() > MAX_IMAGE_INPUT_PIXELS
}

/**
 * Validate and return metadata, throwing if pixel limit exceeded.
 */
fun validateImagePixelLimit(meta: ImageMetadata): ImageMetadata {
    if (exceedsImagePixelLimit(meta)) {
        val pixels = meta.width.toLong() * meta.height.toLong()
        throw Exception(
            "Image dimensions exceed the ${String.format("%,d", MAX_IMAGE_INPUT_PIXELS)} pixel input limit: " +
                "${meta.width}x${meta.height} ($pixels pixels)"
        )
    }
    return meta
}

// ============================================================================
// Image operations (Android Bitmap-based)
// ============================================================================

/**
 * Get image metadata from a byte buffer.
 * Aligned with OpenClaw getImageMetadata.
 */
fun getImageMetadata(buffer: ByteArray): ImageMetadata? {
    // Try fast header-based extraction first
    val headerMeta = readImageMetadataFromHeader(buffer)
    if (headerMeta != null) {
        return try {
            validateImagePixelLimit(headerMeta)
        } catch (_: Exception) {
            null
        }
    }

    // Fall back to BitmapFactory bounds-only decode
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(buffer, 0, buffer.size, opts)
        val width = opts.outWidth
        val height = opts.outHeight
        if (width <= 0 || height <= 0) return null
        val meta = ImageMetadata(width, height)
        validateImagePixelLimit(meta)
    } catch (_: Exception) {
        null
    }
}

/**
 * Resize image buffer to JPEG.
 * Aligned with OpenClaw resizeToJpeg.
 *
 * Android adaptation: uses Bitmap operations instead of sharp.
 * Auto-rotates based on EXIF before resizing.
 */
fun resizeToJpeg(
    buffer: ByteArray,
    maxSide: Int,
    quality: Int,
    withoutEnlargement: Boolean = true
): ByteArray {
    assertImagePixelLimit(buffer)

    // Decode with EXIF orientation applied
    val bitmap = decodeBitmapWithOrientation(buffer)
        ?: throw Exception("Failed to decode image")

    return try {
        val w = bitmap.width
        val h = bitmap.height
        val maxDim = max(w, h)

        val scaled = if (maxDim > maxSide) {
            val scale = maxSide.toFloat() / maxDim
            val newW = (w * scale).toInt().coerceAtLeast(1)
            val newH = (h * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        } else if (withoutEnlargement) {
            bitmap
        } else {
            val scale = maxSide.toFloat() / maxDim
            val newW = (w * scale).toInt().coerceAtLeast(1)
            val newH = (h * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        }

        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        if (scaled !== bitmap) scaled.recycle()
        stream.toByteArray()
    } finally {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

/**
 * Normalize EXIF orientation in an image buffer.
 * Aligned with OpenClaw normalizeExifOrientation.
 */
fun normalizeExifOrientation(buffer: ByteArray): ByteArray {
    assertImagePixelLimit(buffer)
    return try {
        val orientation = readExifOrientation(buffer) ?: return buffer
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) return buffer

        val bitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.size) ?: return buffer
        val matrix = orientationToMatrix(orientation)
        if (matrix == null) {
            bitmap.recycle()
            return buffer
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()

        val stream = ByteArrayOutputStream()
        rotated.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        rotated.recycle()
        stream.toByteArray()
    } catch (_: Exception) {
        buffer
    }
}

/**
 * Check if image has an alpha channel.
 * Aligned with OpenClaw hasAlphaChannel.
 */
fun hasAlphaChannel(buffer: ByteArray): Boolean {
    assertImagePixelLimit(buffer)
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(buffer, 0, buffer.size, opts)
        // Check MIME type for alpha support
        val mime = opts.outMimeType ?: return false
        mime == "image/png" || mime == "image/webp"
    } catch (_: Exception) {
        false
    }
}

/**
 * Resize image to PNG format, preserving alpha.
 * Aligned with OpenClaw resizeToPng.
 */
fun resizeToPng(
    buffer: ByteArray,
    maxSide: Int,
    compressionLevel: Int = 6,
    withoutEnlargement: Boolean = true
): ByteArray {
    assertImagePixelLimit(buffer)

    val bitmap = decodeBitmapWithOrientation(buffer) ?: throw Exception("Failed to decode image")

    return try {
        val w = bitmap.width
        val h = bitmap.height
        val maxDim = max(w, h)

        val scaled = if (maxDim > maxSide) {
            val scale = maxSide.toFloat() / maxDim
            val newW = (w * scale).toInt().coerceAtLeast(1)
            val newH = (h * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        } else if (withoutEnlargement) {
            bitmap
        } else {
            val scale = maxSide.toFloat() / maxDim
            val newW = (w * scale).toInt().coerceAtLeast(1)
            val newH = (h * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        }

        // PNG compress quality param is ignored on Android but we set it anyway
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100 - (compressionLevel * 10), stream)
        if (scaled !== bitmap) scaled.recycle()
        stream.toByteArray()
    } finally {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

/**
 * Optimize image to JPEG within a byte budget.
 * Aligned with OpenClaw optimizeImageToJpeg from web-media.ts.
 */
fun optimizeImageToJpeg(
    buffer: ByteArray,
    maxBytes: Int,
    contentType: String? = null,
    fileName: String? = null
): OptimizedImageResult {
    val sides = intArrayOf(2048, 1536, 1280, 1024, 800)
    val qualities = intArrayOf(80, 70, 60, 50, 40)
    var smallest: OptimizedImageResult? = null

    for (side in sides) {
        for (quality in qualities) {
            try {
                val out = resizeToJpeg(buffer, side, quality, withoutEnlargement = true)
                val size = out.size
                if (smallest == null || size < smallest.optimizedSize) {
                    smallest = OptimizedImageResult(out, size, side, "jpeg", quality)
                }
                if (size <= maxBytes) {
                    return OptimizedImageResult(out, size, side, "jpeg", quality)
                }
            } catch (e: Exception) {
                if (e.message?.contains("pixel input limit") == true) throw e
                // Continue trying
            }
        }
    }

    if (smallest != null) return smallest
    throw Exception("Failed to optimize image")
}

/**
 * Optimize image to PNG within a byte budget.
 * Aligned with OpenClaw optimizeImageToPng.
 */
fun optimizeImageToPng(
    buffer: ByteArray,
    maxBytes: Int
): OptimizedImageResult {
    val sides = intArrayOf(2048, 1536, 1280, 1024, 800)
    val compressionLevels = intArrayOf(6, 7, 8, 9)
    var smallest: OptimizedImageResult? = null

    for (side in sides) {
        for (compressionLevel in compressionLevels) {
            try {
                val out = resizeToPng(buffer, side, compressionLevel, withoutEnlargement = true)
                val size = out.size
                if (smallest == null || size < smallest.optimizedSize) {
                    smallest = OptimizedImageResult(out, size, side, "png", compressionLevel = compressionLevel)
                }
                if (size <= maxBytes) {
                    return OptimizedImageResult(out, size, side, "png", compressionLevel = compressionLevel)
                }
            } catch (_: Exception) {
                // Continue
            }
        }
    }

    if (smallest != null) return smallest
    throw Exception("Failed to optimize PNG image")
}

data class OptimizedImageResult(
    val buffer: ByteArray,
    val optimizedSize: Int,
    val resizeSide: Int,
    val format: String,
    val quality: Int? = null,
    val compressionLevel: Int? = null
)

// ============================================================================
// Internal helpers
// ============================================================================

private fun assertImagePixelLimit(buffer: ByteArray) {
    val meta = readImageMetadataFromHeader(buffer) ?: return
    validateImagePixelLimit(meta)
}

/**
 * Decode a bitmap with EXIF orientation correction.
 */
private fun decodeBitmapWithOrientation(buffer: ByteArray): Bitmap? {
    val bitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.size) ?: return null
    val orientation = readExifOrientation(buffer)
    if (orientation == null || orientation == ExifInterface.ORIENTATION_NORMAL ||
        orientation == ExifInterface.ORIENTATION_UNDEFINED
    ) return bitmap

    val matrix = orientationToMatrix(orientation) ?: return bitmap
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

/**
 * Read EXIF orientation from buffer.
 */
private fun readExifOrientation(buffer: ByteArray): Int? {
    return try {
        val exif = ExifInterface(ByteArrayInputStream(buffer))
        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
    } catch (_: Exception) {
        null
    }
}

/**
 * Convert EXIF orientation to a Matrix transform.
 */
private fun orientationToMatrix(orientation: Int): Matrix? {
    val matrix = Matrix()
    return when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
            matrix.setScale(-1f, 1f); matrix
        }
        ExifInterface.ORIENTATION_ROTATE_180 -> {
            matrix.setRotate(180f); matrix
        }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setScale(1f, -1f); matrix
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f); matrix.postScale(-1f, 1f); matrix
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> {
            matrix.setRotate(90f); matrix
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f); matrix.postScale(-1f, 1f); matrix
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> {
            matrix.setRotate(-90f); matrix
        }
        else -> null
    }
}

// ============================================================================
// Byte reading helpers
// ============================================================================

private fun readUInt32BE(buffer: ByteArray, offset: Int): Int {
    return ((buffer[offset].toInt() and 0xFF) shl 24) or
        ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
        ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
        (buffer[offset + 3].toInt() and 0xFF)
}

private fun readUInt16BE(buffer: ByteArray, offset: Int): Int {
    return ((buffer[offset].toInt() and 0xFF) shl 8) or
        (buffer[offset + 1].toInt() and 0xFF)
}

private fun readUInt16LE(buffer: ByteArray, offset: Int): Int {
    return (buffer[offset].toInt() and 0xFF) or
        ((buffer[offset + 1].toInt() and 0xFF) shl 8)
}

private fun readUInt24LE(buffer: ByteArray, offset: Int): Int {
    return (buffer[offset].toInt() and 0xFF) or
        ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
        ((buffer[offset + 2].toInt() and 0xFF) shl 16)
}

package com.xiaomo.androidforclaw.agent.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.xiaomo.androidforclaw.providers.llm.ImageBlock
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Aligned with OpenClaw run/images.ts:
 * - detectImageReferences(): scan prompt for image file path references
 * - loadImageFromPath(): load image file → base64 ImageBlock
 * - detectAndLoadPromptImages(): detect + load images for LLM
 */
object ImageLoader {
    private const val TAG = "ImageLoader"
    private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024 // 20MB
    private const val MAX_DIMENSION = 1024
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "tiff", "tif", "heic", "heif")

    /**
     * Detect image file path references in a prompt string.
     * Aligned with OpenClaw detectImageReferences():
     * - [Image: source: /path/to/image.jpg]
     * - [media attached: /path/to/image.png (image/png) | ...]
     * - /absolute/path/to/image.png
     * - ./relative/image.jpg
     * - ~/home/image.jpg
     */
    fun detectImageReferences(prompt: String): List<String> {
        val refs = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        fun addRef(path: String) {
            val trimmed = path.trim()
            if (trimmed.isEmpty() || seen.contains(trimmed)) return
            if (!isImageFile(trimmed)) return
            seen.add(trimmed)
            refs.add(trimmed)
        }

        // Pattern: [Image: source: /path/to/image.ext]
        val imageSourcePattern = Regex("""\[Image:\s*source:\s*([^\]]+\.(?:${IMAGE_EXTENSIONS.joinToString("|")}))]""", RegexOption.IGNORE_CASE)
        imageSourcePattern.findAll(prompt).forEach { addRef(it.groupValues[1]) }

        // Pattern: [media attached: /path/to/image.ext (type) | url]
        val mediaAttachedPattern = Regex("""\[media attached[^]]*?:\s*([^\]]+\.(?:${IMAGE_EXTENSIONS.joinToString("|")}))""", RegexOption.IGNORE_CASE)
        mediaAttachedPattern.findAll(prompt).forEach { addRef(it.groupValues[1]) }

        // Pattern: absolute/relative/home paths
        val pathPattern = Regex("""(?:^|\s|["'`(])((?:\.\.?/|~/|/)[^\s"'`()\[\]]*\.(?:${IMAGE_EXTENSIONS.joinToString("|")}))""", RegexOption.IGNORE_CASE)
        pathPattern.findAll(prompt).forEach {
            if (it.groupValues.size > 1) addRef(it.groupValues[1])
        }

        return refs
    }

    /**
     * Load an image file, resize to max dimension, compress to JPEG, return as ImageBlock.
     * Aligned with OpenClaw loadImageFromRef() + resizeToJpeg.
     */
    fun loadImageFromPath(filePath: String): ImageBlock? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            Log.w(TAG, "Image file not found: $filePath")
            return null
        }
        if (file.length() > MAX_IMAGE_BYTES) {
            Log.w(TAG, "Image too large: ${file.length()} bytes (max $MAX_IMAGE_BYTES)")
            return null
        }

        return try {
            val bitmap = BitmapFactory.decodeFile(filePath) ?: run {
                Log.w(TAG, "Failed to decode image: $filePath")
                return null
            }

            // Resize to MAX_DIMENSION on longest side
            val scale = minOf(MAX_DIMENSION.toFloat() / bitmap.width, MAX_DIMENSION.toFloat() / bitmap.height, 1f)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true).also {
                    if (it != bitmap) bitmap.recycle()
                }
            } else bitmap

            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            scaled.recycle()

            val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            Log.d(TAG, "Loaded image: $filePath (${stream.size()} bytes)")
            ImageBlock(base64 = base64, mimeType = "image/jpeg")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load image $filePath: ${e.message}")
            null
        }
    }

    /**
     * Check if a file path points to an image by extension.
     */
    private fun isImageFile(filePath: String): Boolean {
        val ext = filePath.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }
}

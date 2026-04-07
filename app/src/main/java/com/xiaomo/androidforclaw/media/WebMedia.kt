package com.xiaomo.androidforclaw.media

/**
 * OpenClaw Source Reference:
 * - src/media/web-media.ts
 *
 * Load web/local media: fetch, optimize images, enforce per-kind byte limits.
 *
 * Android adaptation:
 * - Uses OkHttp for remote fetch (via fetchWithGuard / MediaStore)
 * - Uses java.io.File for local reads
 * - No SSRF policy (not applicable on mobile)
 * - Simplified local-root checks (Android sandbox handles this)
 */

import com.xiaomo.androidforclaw.logging.Log
import java.io.File

private const val TAG = "WebMedia"

// ============================================================================
// Types (aligned with OpenClaw web-media.ts)
// ============================================================================

/**
 * Result of loading web/local media.
 * Aligned with OpenClaw WebMediaResult.
 */
data class WebMediaResult(
    val buffer: ByteArray,
    val contentType: String? = null,
    val kind: MediaKind? = null,
    val fileName: String? = null
)

/**
 * Options for loading web media.
 * Aligned with OpenClaw WebMediaOptions.
 */
data class WebMediaOptions(
    val maxBytes: Int? = null,
    val optimizeImages: Boolean = true,
    /** Agent workspace directory for resolving relative MEDIA: paths. */
    val workspaceDir: String? = null
)

// ============================================================================
// Constants
// ============================================================================

private val HEIC_MIME_RE = Regex("^image/hei[cf]$", RegexOption.IGNORE_CASE)
private val HEIC_EXT_RE = Regex("\\.(heic|heif)$", RegexOption.IGNORE_CASE)

private val HOST_READ_ALLOWED_DOCUMENT_MIMES = setOf(
    "application/msword",
    "application/pdf",
    "application/vnd.ms-excel",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
)

private val HOST_READ_ALLOWED_DOCUMENT_EXTS = setOf(
    ".doc", ".docx", ".pdf", ".ppt", ".pptx", ".xls", ".xlsx"
)

private const val MB = 1024 * 1024

// ============================================================================
// Formatting helpers (aligned with OpenClaw)
// ============================================================================

private fun formatMb(bytes: Int, digits: Int = 2): String {
    return String.format("%.${digits}f", bytes.toFloat() / MB)
}

private fun formatCapLimit(label: String, cap: Int, size: Int): String {
    return "$label exceeds ${formatMb(cap, 0)}MB limit (got ${formatMb(size)}MB)"
}

private fun formatCapReduce(label: String, cap: Int, size: Int): String {
    return "$label could not be reduced below ${formatMb(cap, 0)}MB (got ${formatMb(size)}MB)"
}

private fun isPixelLimitError(error: Exception): Boolean {
    return error.message?.contains("pixel input limit") == true
}

// ============================================================================
// HEIC detection helpers (aligned with OpenClaw)
// ============================================================================

private fun isHeicSource(contentType: String? = null, fileName: String? = null): Boolean {
    if (contentType != null && HEIC_MIME_RE.containsMatchIn(contentType.trim())) return true
    if (fileName != null && HEIC_EXT_RE.containsMatchIn(fileName.trim())) return true
    return false
}

private fun toJpegFileName(fileName: String?): String? {
    if (fileName == null) return null
    val trimmed = fileName.trim()
    if (trimmed.isEmpty()) return fileName
    val file = File(trimmed)
    val ext = file.extension
    val nameNoExt = file.nameWithoutExtension
    return if (ext.isEmpty() || HEIC_EXT_RE.containsMatchIn(".$ext")) {
        "${nameNoExt.ifEmpty { trimmed }}.jpg"
    } else {
        "$nameNoExt.jpg"
    }
}

// ============================================================================
// Image optimization (aligned with OpenClaw optimizeImageWithFallback)
// ============================================================================

/**
 * Optimize an image, falling back from PNG to JPEG if needed.
 * Aligned with OpenClaw optimizeImageWithFallback.
 */
fun optimizeImageWithFallback(
    buffer: ByteArray,
    cap: Int,
    contentType: String? = null,
    fileName: String? = null
): OptimizedImageResult {
    val isPng = contentType == "image/png" || fileName?.lowercase()?.endsWith(".png") == true
    val hasAlpha = isPng && hasAlphaChannel(buffer)

    if (hasAlpha) {
        try {
            val optimized = optimizeImageToPng(buffer, cap)
            if (optimized.buffer.size <= cap) {
                return optimized
            }
            Log.d(TAG, "PNG with alpha still exceeds ${formatMb(cap, 0)}MB after optimization; falling back to JPEG")
        } catch (_: Exception) {
            // Fall through to JPEG
        }
    }

    return optimizeImageToJpeg(buffer, cap, contentType, fileName)
}

// ============================================================================
// Core load functions (aligned with OpenClaw loadWebMedia / loadWebMediaRaw)
// ============================================================================

/**
 * Load media from URL or local path with image optimization.
 * Aligned with OpenClaw loadWebMedia.
 */
suspend fun loadWebMedia(
    mediaUrl: String,
    options: WebMediaOptions = WebMediaOptions()
): WebMediaResult {
    return loadWebMediaInternal(mediaUrl, options.copy(optimizeImages = true))
}

/**
 * Load media from URL or local path without image optimization.
 * Aligned with OpenClaw loadWebMediaRaw.
 */
suspend fun loadWebMediaRaw(
    mediaUrl: String,
    options: WebMediaOptions = WebMediaOptions()
): WebMediaResult {
    return loadWebMediaInternal(mediaUrl, options.copy(optimizeImages = false))
}

/**
 * Internal implementation for loading web/local media.
 * Aligned with OpenClaw loadWebMediaInternal.
 */
private suspend fun loadWebMediaInternal(
    rawMediaUrl: String,
    options: WebMediaOptions = WebMediaOptions()
): WebMediaResult {
    val maxBytes = options.maxBytes
    val optimizeImages = options.optimizeImages
    val workspaceDir = options.workspaceDir

    // Strip MEDIA: prefix used by agent tools
    var mediaUrl = rawMediaUrl.replace(Regex("^\\s*MEDIA\\s*:\\s*", RegexOption.IGNORE_CASE), "")

    // Helper: optimize and clamp image
    suspend fun optimizeAndClampImage(
        buffer: ByteArray,
        cap: Int,
        metaContentType: String? = null,
        metaFileName: String? = null
    ): WebMediaResult {
        val originalSize = buffer.size
        val optimized = optimizeImageWithFallback(buffer, cap, metaContentType, metaFileName)

        // Log optimization
        if (optimized.optimizedSize < originalSize) {
            if (optimized.format == "png") {
                Log.d(TAG, "Optimized PNG (preserving alpha) from ${formatMb(originalSize)}MB to ${formatMb(optimized.optimizedSize)}MB (side<=${optimized.resizeSide}px)")
            } else {
                Log.d(TAG, "Optimized media from ${formatMb(originalSize)}MB to ${formatMb(optimized.optimizedSize)}MB (side<=${optimized.resizeSide}px, q=${optimized.quality})")
            }
        }

        if (optimized.buffer.size > cap) {
            throw Exception(formatCapReduce("Media", cap, optimized.buffer.size))
        }

        val contentType = if (optimized.format == "png") "image/png" else "image/jpeg"
        val resultFileName = if (optimized.format == "jpeg" && isHeicSource(metaContentType, metaFileName)) {
            toJpegFileName(metaFileName)
        } else {
            metaFileName
        }

        return WebMediaResult(
            buffer = optimized.buffer,
            contentType = contentType,
            kind = MediaKind.IMAGE,
            fileName = resultFileName
        )
    }

    // Helper: clamp and finalize
    suspend fun clampAndFinalize(
        buffer: ByteArray,
        contentType: String?,
        kind: MediaKind?,
        fileName: String?
    ): WebMediaResult {
        val cap = maxBytes ?: maxBytesForKind(kind ?: MediaKind.DOCUMENT)

        if (kind == MediaKind.IMAGE) {
            val isGif = contentType == "image/gif"
            if (isGif || !optimizeImages) {
                if (buffer.size > cap) {
                    throw Exception(formatCapLimit(if (isGif) "GIF" else "Media", cap, buffer.size))
                }
                return WebMediaResult(
                    buffer = buffer,
                    contentType = contentType,
                    kind = kind,
                    fileName = fileName
                )
            }
            return optimizeAndClampImage(buffer, cap, contentType, fileName)
        }

        if (buffer.size > cap) {
            throw Exception(formatCapLimit("Media", cap, buffer.size))
        }

        return WebMediaResult(
            buffer = buffer,
            contentType = contentType,
            kind = kind,
            fileName = fileName
        )
    }

    // Remote URL
    if (mediaUrl.matches(Regex("^https?://.*", RegexOption.IGNORE_CASE))) {
        val defaultFetchCap = maxBytesForKind(MediaKind.DOCUMENT)
        val fetchCap = if (maxBytes == null) {
            defaultFetchCap
        } else if (optimizeImages) {
            maxOf(maxBytes, defaultFetchCap)
        } else {
            maxBytes
        }
        val fetched = fetchWithGuard(mediaUrl, fetchCap, 10_000, 3)
        val kind = kindFromMime(fetched.mimeType)
        return clampAndFinalize(fetched.buffer, fetched.mimeType, kind, null)
    }

    // Resolve relative paths against workspace dir
    if (workspaceDir != null && !File(mediaUrl).isAbsolute) {
        mediaUrl = File(workspaceDir, mediaUrl).absolutePath
    }

    // Local path
    val file = File(mediaUrl)
    if (!file.exists()) {
        throw Exception("Local media file not found: $mediaUrl")
    }
    if (!file.isFile) {
        throw Exception("Local media path is not a file: $mediaUrl")
    }

    val data = file.readBytes()
    val mime = detectMime(buffer = data, filePath = mediaUrl)
    val kind = kindFromMime(mime)
    var fileName = file.name.ifEmpty { null }
    if (fileName != null && !fileName.contains('.') && mime != null) {
        val ext = extensionForMime(mime)
        if (ext != null) {
            fileName = "$fileName$ext"
        }
    }

    return clampAndFinalize(data, mime, kind, fileName)
}

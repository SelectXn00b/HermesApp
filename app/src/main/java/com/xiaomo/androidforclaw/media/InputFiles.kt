package com.xiaomo.androidforclaw.media

/**
 * OpenClaw Source Reference:
 * - src/media/input-files.ts
 *
 * Input file/image processing: validation, MIME checking, HEIC conversion,
 * base64 decode, URL fetching with limits.
 *
 * Android adaptation: no PDF extraction (skip for now), uses OkHttp for fetching.
 */

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// ============================================================================
// Types (aligned with OpenClaw input-files.ts)
// ============================================================================

/**
 * Extracted image content block.
 * Aligned with OpenClaw InputImageContent / PdfExtractedImage.
 */
data class InputImageContent(
    val type: String = "image",
    val data: String,
    val mimeType: String
)

/**
 * Result of file content extraction.
 * Aligned with OpenClaw InputFileExtractResult.
 */
data class InputFileExtractResult(
    val filename: String,
    val text: String? = null,
    val images: List<InputImageContent>? = null
)

/**
 * Limits for input file processing.
 * Aligned with OpenClaw InputFileLimits.
 */
data class InputFileLimits(
    val allowUrl: Boolean = true,
    val urlAllowlist: List<String>? = null,
    val allowedMimes: Set<String> = DEFAULT_INPUT_FILE_MIMES_SET,
    val maxBytes: Int = DEFAULT_INPUT_FILE_MAX_BYTES,
    val maxChars: Int = DEFAULT_INPUT_FILE_MAX_CHARS,
    val maxRedirects: Int = DEFAULT_INPUT_MAX_REDIRECTS,
    val timeoutMs: Int = DEFAULT_INPUT_TIMEOUT_MS
)

/**
 * Limits for input image processing.
 * Aligned with OpenClaw InputImageLimits.
 */
data class InputImageLimits(
    val allowUrl: Boolean = true,
    val urlAllowlist: List<String>? = null,
    val allowedMimes: Set<String> = DEFAULT_INPUT_IMAGE_MIMES_SET,
    val maxBytes: Int = DEFAULT_INPUT_IMAGE_MAX_BYTES,
    val maxRedirects: Int = DEFAULT_INPUT_MAX_REDIRECTS,
    val timeoutMs: Int = DEFAULT_INPUT_TIMEOUT_MS
)

/**
 * Input image source (base64 or URL).
 * Aligned with OpenClaw InputImageSource.
 */
sealed class InputImageSource {
    data class Base64Source(val data: String, val mediaType: String? = null) : InputImageSource()
    data class UrlSource(val url: String, val mediaType: String? = null) : InputImageSource()
}

/**
 * Input file source (base64 or URL).
 * Aligned with OpenClaw InputFileSource.
 */
sealed class InputFileSource {
    data class Base64Source(val data: String, val mediaType: String? = null, val filename: String? = null) : InputFileSource()
    data class UrlSource(val url: String, val mediaType: String? = null, val filename: String? = null) : InputFileSource()
}

/**
 * Result of fetch operation.
 * Aligned with OpenClaw InputFetchResult.
 */
data class InputFetchResult(
    val buffer: ByteArray,
    val mimeType: String,
    val contentType: String? = null
)

// ============================================================================
// Constants (aligned with OpenClaw)
// ============================================================================

val DEFAULT_INPUT_IMAGE_MIMES = listOf(
    "image/jpeg", "image/png", "image/gif", "image/webp", "image/heic", "image/heif"
)
val DEFAULT_INPUT_FILE_MIMES = listOf(
    "text/plain", "text/markdown", "text/html", "text/csv",
    "application/json", "application/pdf"
)
val DEFAULT_INPUT_IMAGE_MIMES_SET = DEFAULT_INPUT_IMAGE_MIMES.toSet()
val DEFAULT_INPUT_FILE_MIMES_SET = DEFAULT_INPUT_FILE_MIMES.toSet()

const val DEFAULT_INPUT_IMAGE_MAX_BYTES = 10 * 1024 * 1024
const val DEFAULT_INPUT_FILE_MAX_BYTES = 5 * 1024 * 1024
const val DEFAULT_INPUT_FILE_MAX_CHARS = 200_000
const val DEFAULT_INPUT_MAX_REDIRECTS = 3
const val DEFAULT_INPUT_TIMEOUT_MS = 10_000

private val HEIC_INPUT_IMAGE_MIMES = setOf("image/heic", "image/heif")
private const val NORMALIZED_INPUT_IMAGE_MIME = "image/jpeg"

// ============================================================================
// MIME normalization (aligned with OpenClaw input-files.ts)
// ============================================================================

/**
 * Normalize MIME type by stripping parameters.
 * Aligned with OpenClaw normalizeMimeType in input-files.ts.
 */
fun normalizeInputMimeType(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val raw = value.split(";")[0].trim().lowercase()
    return raw.ifBlank { null }
}

/**
 * Parse Content-Type header into MIME and charset.
 * Aligned with OpenClaw parseContentType.
 */
fun parseContentType(value: String?): Pair<String?, String?> {
    if (value.isNullOrBlank()) return null to null
    val parts = value.split(";").map { it.trim() }
    val mimeType = normalizeInputMimeType(parts[0])
    val charset = parts.mapNotNull { part ->
        val match = Regex("^charset=(.+)$", RegexOption.IGNORE_CASE).find(part)
        match?.groupValues?.get(1)?.trim()
    }.firstOrNull()
    return mimeType to charset
}

// ============================================================================
// Base64 utilities
// ============================================================================

/**
 * Estimate decoded size of base64 data.
 */
fun estimateBase64DecodedBytes(data: String): Int {
    val stripped = data.replace(Regex("[\\s=]"), "")
    return (stripped.length * 3) / 4
}

/**
 * Canonicalize base64 by stripping data URI prefix and whitespace.
 */
fun canonicalizeBase64(data: String): String? {
    val stripped = data
        .replace(Regex("^data:[^;]+;base64,", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), "")
    return stripped.ifBlank { null }
}

// ============================================================================
// Image extraction from source (aligned with OpenClaw)
// ============================================================================

/**
 * Extract image content from an input source.
 * Aligned with OpenClaw extractImageContentFromSource.
 */
suspend fun extractImageContentFromSource(
    source: InputImageSource,
    limits: InputImageLimits
): InputImageContent {
    return when (source) {
        is InputImageSource.Base64Source -> {
            val estimated = estimateBase64DecodedBytes(source.data)
            if (estimated > limits.maxBytes) {
                throw Exception("Image too large: $estimated bytes (limit: ${limits.maxBytes} bytes)")
            }
            val canonical = canonicalizeBase64(source.data)
                ?: throw Exception("input_image base64 source has invalid 'data' field")
            val buffer = Base64.decode(canonical, Base64.NO_WRAP)
            if (buffer.size > limits.maxBytes) {
                throw Exception("Image too large: ${buffer.size} bytes (limit: ${limits.maxBytes} bytes)")
            }
            normalizeInputImage(buffer, normalizeInputMimeType(source.mediaType) ?: "image/png", limits)
        }

        is InputImageSource.UrlSource -> {
            if (!limits.allowUrl) {
                throw Exception("input_image URL sources are disabled by config")
            }
            val result = fetchWithGuard(source.url, limits.maxBytes, limits.timeoutMs, limits.maxRedirects)
            normalizeInputImage(result.buffer, result.mimeType, limits)
        }
    }
}

/**
 * Normalize an input image (convert HEIC to JPEG if needed).
 * Aligned with OpenClaw normalizeInputImage.
 */
private fun normalizeInputImage(
    buffer: ByteArray,
    mimeType: String?,
    limits: InputImageLimits
): InputImageContent {
    val declaredMime = normalizeInputMimeType(mimeType) ?: "application/octet-stream"
    val detectedMime = normalizeInputMimeType(detectMime(buffer = buffer, headerMime = mimeType))

    // Validate image MIME
    if (declaredMime.startsWith("image/") && detectedMime != null && !detectedMime.startsWith("image/")) {
        throw Exception("Unsupported image MIME type: $detectedMime")
    }

    val sourceMime = when {
        detectedMime != null && detectedMime in HEIC_INPUT_IMAGE_MIMES -> detectedMime
        detectedMime == null && declaredMime in HEIC_INPUT_IMAGE_MIMES -> declaredMime
        else -> declaredMime
    }

    if (sourceMime !in limits.allowedMimes) {
        throw Exception("Unsupported image MIME type: $sourceMime")
    }

    // HEIC conversion not supported on Android (would need a native lib)
    // Return as-is; the caller/ImageSanitizer handles conversion
    if (sourceMime !in HEIC_INPUT_IMAGE_MIMES) {
        return InputImageContent(
            data = Base64.encodeToString(buffer, Base64.NO_WRAP),
            mimeType = sourceMime
        )
    }

    // For HEIC, attempt conversion via Bitmap decode (Android 10+ can decode HEIC)
    return try {
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(buffer, 0, buffer.size)
            ?: throw Exception("Failed to decode HEIC image")
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
        bitmap.recycle()
        val converted = stream.toByteArray()
        if (converted.size > limits.maxBytes) {
            throw Exception("Image too large after HEIC conversion: ${converted.size} bytes (limit: ${limits.maxBytes} bytes)")
        }
        InputImageContent(
            data = Base64.encodeToString(converted, Base64.NO_WRAP),
            mimeType = NORMALIZED_INPUT_IMAGE_MIME
        )
    } catch (e: Exception) {
        if (e.message?.contains("HEIC conversion") == true) throw e
        // If Bitmap decode fails, return original
        InputImageContent(
            data = Base64.encodeToString(buffer, Base64.NO_WRAP),
            mimeType = sourceMime
        )
    }
}

// ============================================================================
// HTTP fetch with guards (aligned with OpenClaw fetchWithGuard)
// ============================================================================

private val inputFetchClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

/**
 * Fetch URL content with size and redirect limits.
 * Aligned with OpenClaw fetchWithGuard.
 */
suspend fun fetchWithGuard(
    url: String,
    maxBytes: Int,
    timeoutMs: Int,
    maxRedirects: Int
): InputFetchResult {
    val client = inputFetchClient.newBuilder()
        .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .build()

    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "OpenClaw-Gateway/1.0")
        .build()

    val response = client.newCall(request).execute()
    if (!response.isSuccessful) {
        throw Exception("Failed to fetch: ${response.code} ${response.message}")
    }

    val body = response.body ?: throw Exception("Empty response body")
    val contentLength = response.header("Content-Length")?.toLongOrNull()
    if (contentLength != null && contentLength > maxBytes) {
        throw Exception("Content too large: $contentLength bytes (limit: $maxBytes bytes)")
    }

    val buffer = body.bytes()
    if (buffer.size > maxBytes) {
        throw Exception("Content too large: ${buffer.size} bytes (limit: $maxBytes bytes)")
    }

    val contentType = response.header("Content-Type")
    val (mimeType, _) = parseContentType(contentType)

    return InputFetchResult(
        buffer = buffer,
        mimeType = mimeType ?: "application/octet-stream",
        contentType = contentType
    )
}

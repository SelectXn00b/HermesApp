package com.xiaomo.androidforclaw.media

/**
 * OpenClaw Source Reference:
 * - src/media/store.ts
 *
 * Media store: save, resolve, delete, and clean up media files.
 *
 * Android adaptation: uses java.io.File instead of node:fs,
 * OkHttp for downloads, app-specific storage directory.
 */

import com.xiaomo.androidforclaw.logging.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "MediaStore"

const val MEDIA_MAX_BYTES = 5 * 1024 * 1024  // 5MB default
private const val DEFAULT_TTL_MS = 2 * 60 * 1000L  // 2 minutes
private const val SNIFF_BUFFER_SIZE = 16384

// ============================================================================
// Media directory
// ============================================================================

private var mediaBaseDirOverride: String? = null

/**
 * Set the base media directory (call from app init).
 */
fun setMediaBaseDir(dir: String) {
    mediaBaseDirOverride = dir
}

/**
 * Resolve the media directory path.
 */
fun getMediaDir(): String {
    return mediaBaseDirOverride ?: "/data/local/tmp/openclaw/media"
}

/**
 * Ensure the media directory exists.
 */
fun ensureMediaDir(): String {
    val dir = getMediaDir()
    File(dir).mkdirs()
    return dir
}

// ============================================================================
// Filename utilities
// ============================================================================

private val UUID_SUFFIX_REGEX = Regex(
    "^(.+)---[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$",
    RegexOption.IGNORE_CASE
)

/**
 * Sanitize a filename for cross-platform safety.
 * Aligned with OpenClaw sanitizeFilename.
 */
fun sanitizeFilename(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return ""
    val sanitized = trimmed.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
    return sanitized.replace(Regex("_+"), "_").trimStart('_').trimEnd('_').take(60)
}

/**
 * Extract original filename from path if it matches the embedded format.
 * Pattern: {original}---{uuid}.{ext} -> "{original}.{ext}"
 * Aligned with OpenClaw extractOriginalFilename.
 */
fun extractOriginalFilename(filePath: String): String {
    val basename = File(filePath).name
    if (basename.isEmpty()) return "file.bin"

    val dotIdx = basename.lastIndexOf('.')
    val ext = if (dotIdx >= 0) basename.substring(dotIdx) else ""
    val nameWithoutExt = if (dotIdx >= 0) basename.substring(0, dotIdx) else basename

    val match = UUID_SUFFIX_REGEX.find(nameWithoutExt)
    if (match != null) {
        val original = match.groupValues[1]
        return "$original$ext"
    }

    return basename
}

// ============================================================================
// Saved media types
// ============================================================================

/**
 * Aligned with OpenClaw SavedMedia.
 */
data class SavedMedia(
    val id: String,
    val path: String,
    val size: Long,
    val contentType: String? = null
)

/**
 * Error codes for save media operations.
 * Aligned with OpenClaw SaveMediaSourceErrorCode.
 */
enum class SaveMediaSourceErrorCode {
    INVALID_PATH,
    NOT_FOUND,
    NOT_FILE,
    PATH_MISMATCH,
    TOO_LARGE
}

class SaveMediaSourceError(
    val code: SaveMediaSourceErrorCode,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

// ============================================================================
// Build media IDs and results
// ============================================================================

private fun buildSavedMediaId(baseId: String, ext: String, originalFilename: String? = null): String {
    if (originalFilename == null) {
        return if (ext.isNotEmpty()) "$baseId$ext" else baseId
    }
    val nameWithoutExt = File(originalFilename).nameWithoutExtension
    val sanitized = sanitizeFilename(nameWithoutExt)
    return if (sanitized.isNotEmpty()) {
        "$sanitized---$baseId$ext"
    } else {
        "$baseId$ext"
    }
}

private fun buildSavedMediaResult(dir: String, id: String, size: Long, contentType: String? = null): SavedMedia {
    return SavedMedia(
        id = id,
        path = File(dir, id).absolutePath,
        size = size,
        contentType = contentType
    )
}

// ============================================================================
// Clean old media (aligned with OpenClaw cleanOldMedia)
// ============================================================================

/**
 * Remove expired media files older than ttlMs.
 * Aligned with OpenClaw cleanOldMedia.
 */
suspend fun cleanOldMedia(ttlMs: Long = DEFAULT_TTL_MS, recursive: Boolean = false) {
    val mediaDir = ensureMediaDir()
    val now = System.currentTimeMillis()
    val dir = File(mediaDir)
    val entries = dir.listFiles() ?: return

    for (entry in entries) {
        if (entry.isDirectory) {
            if (recursive) {
                cleanDirRecursive(entry, now, ttlMs)
            }
            continue
        }
        if (entry.isFile && now - entry.lastModified() > ttlMs) {
            entry.delete()
        }
    }
}

private fun cleanDirRecursive(dir: File, now: Long, ttlMs: Long) {
    val entries = dir.listFiles() ?: return
    for (entry in entries) {
        if (entry.isDirectory) {
            cleanDirRecursive(entry, now, ttlMs)
            if (entry.listFiles()?.isEmpty() == true) {
                entry.delete()
            }
        } else if (entry.isFile && now - entry.lastModified() > ttlMs) {
            entry.delete()
        }
    }
}

// ============================================================================
// Save media from source (URL or local path)
// ============================================================================

private fun looksLikeUrl(src: String): Boolean = src.matches(Regex("^https?://.*", RegexOption.IGNORE_CASE))

private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

/**
 * Save media from a URL or local path.
 * Aligned with OpenClaw saveMediaSource.
 */
suspend fun saveMediaSource(
    source: String,
    headers: Map<String, String>? = null,
    subdir: String = ""
): SavedMedia {
    val baseDir = getMediaDir()
    val dir = if (subdir.isNotEmpty()) File(baseDir, subdir).absolutePath else baseDir
    File(dir).mkdirs()

    // Clean old media first
    cleanOldMedia(DEFAULT_TTL_MS)

    val baseId = UUID.randomUUID().toString()

    if (looksLikeUrl(source)) {
        return downloadAndSave(source, dir, baseId, headers)
    }

    // Local path
    val srcFile = File(source)
    if (!srcFile.exists()) {
        throw SaveMediaSourceError(SaveMediaSourceErrorCode.NOT_FOUND, "Media path does not exist")
    }
    if (!srcFile.isFile) {
        throw SaveMediaSourceError(SaveMediaSourceErrorCode.NOT_FILE, "Media path is not a file")
    }
    if (srcFile.length() > MEDIA_MAX_BYTES) {
        throw SaveMediaSourceError(SaveMediaSourceErrorCode.TOO_LARGE, "Media exceeds 5MB limit")
    }

    val buffer = srcFile.readBytes()
    val mime = detectMime(buffer = buffer, filePath = source)
    val ext = extensionForMime(mime) ?: getFileExtension(source) ?: ""
    val id = buildSavedMediaId(baseId, ext)
    val dest = File(dir, id)
    dest.writeBytes(buffer)
    return buildSavedMediaResult(dir, id, srcFile.length(), mime)
}

private suspend fun downloadAndSave(
    url: String,
    dir: String,
    baseId: String,
    headers: Map<String, String>?
): SavedMedia {
    val requestBuilder = Request.Builder().url(url)
    headers?.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
    val request = requestBuilder.build()

    val response = httpClient.newCall(request).execute()
    if (!response.isSuccessful) {
        throw Exception("HTTP ${response.code} downloading media")
    }

    val body = response.body ?: throw Exception("Empty response body")
    val tempFile = File(dir, "$baseId.tmp")
    val sniffBytes = mutableListOf<Byte>()
    var totalSize = 0L

    try {
        FileOutputStream(tempFile).use { out ->
            val buffer = ByteArray(8192)
            val inputStream = body.byteStream()
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                totalSize += bytesRead
                if (totalSize > MEDIA_MAX_BYTES) {
                    throw Exception("Media exceeds 5MB limit")
                }
                out.write(buffer, 0, bytesRead)
                if (sniffBytes.size < SNIFF_BUFFER_SIZE) {
                    for (i in 0 until minOf(bytesRead, SNIFF_BUFFER_SIZE - sniffBytes.size)) {
                        sniffBytes.add(buffer[i])
                    }
                }
            }
        }

        val sniffBuffer = sniffBytes.toByteArray()
        val headerMime = response.header("Content-Type")
        val mime = detectMime(buffer = sniffBuffer, headerMime = headerMime, filePath = url)
        val urlExt = try {
            getFileExtension(java.net.URI(url).path)
        } catch (_: Exception) {
            null
        }
        val ext = extensionForMime(mime) ?: urlExt ?: ""
        val id = buildSavedMediaId(baseId, ext)
        val finalDest = File(dir, id)
        tempFile.renameTo(finalDest)

        return buildSavedMediaResult(dir, id, totalSize, mime)
    } catch (e: Exception) {
        tempFile.delete()
        throw e
    } finally {
        body.close()
    }
}

// ============================================================================
// Save media from buffer (aligned with OpenClaw saveMediaBuffer)
// ============================================================================

/**
 * Save media from a byte buffer.
 * Aligned with OpenClaw saveMediaBuffer.
 */
suspend fun saveMediaBuffer(
    buffer: ByteArray,
    contentType: String? = null,
    subdir: String = "inbound",
    maxBytes: Int = MEDIA_MAX_BYTES,
    originalFilename: String? = null
): SavedMedia {
    if (buffer.size > maxBytes) {
        throw Exception("Media exceeds ${maxBytes / (1024 * 1024)}MB limit")
    }

    val dir = File(getMediaDir(), subdir).absolutePath
    File(dir).mkdirs()

    val uuid = UUID.randomUUID().toString()
    val headerExt = extensionForMime(contentType?.split(";")?.get(0)?.trim())
    val mime = detectMime(buffer = buffer, headerMime = contentType)
    val ext = headerExt ?: extensionForMime(mime) ?: ""
    val id = buildSavedMediaId(uuid, ext, originalFilename)

    val dest = File(dir, id)
    dest.writeBytes(buffer)

    return buildSavedMediaResult(dir, id, buffer.size.toLong(), mime)
}

// ============================================================================
// Resolve and delete media (aligned with OpenClaw resolveMediaBufferPath / deleteMediaBuffer)
// ============================================================================

/**
 * Resolve a media ID to its absolute physical path.
 * Aligned with OpenClaw resolveMediaBufferPath.
 */
suspend fun resolveMediaBufferPath(id: String, subdir: String = "inbound"): String {
    // Guard against path traversal
    if (id.isEmpty() || id.contains('/') || id.contains('\\') || id.contains('\u0000') || id == "..") {
        throw Exception("resolveMediaBufferPath: unsafe media ID: $id")
    }

    val dir = File(getMediaDir(), subdir).absolutePath
    val resolved = File(dir, id).absolutePath

    // Double-check containment
    if (!resolved.startsWith(dir + File.separator) && resolved != dir) {
        throw Exception("resolveMediaBufferPath: path escapes media directory: $id")
    }

    val file = File(resolved)
    if (!file.exists()) {
        throw Exception("resolveMediaBufferPath: media file not found: $id")
    }
    // Check for symlinks
    if (file.canonicalPath != file.absolutePath) {
        throw Exception("resolveMediaBufferPath: refusing to follow symlink for media ID: $id")
    }
    if (!file.isFile) {
        throw Exception("resolveMediaBufferPath: media ID does not resolve to a file: $id")
    }

    return resolved
}

/**
 * Delete a previously saved media file.
 * Aligned with OpenClaw deleteMediaBuffer.
 */
suspend fun deleteMediaBuffer(id: String, subdir: String = "inbound") {
    val path = resolveMediaBufferPath(id, subdir)
    File(path).delete()
}

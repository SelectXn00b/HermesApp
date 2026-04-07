package com.xiaomo.androidforclaw.media

/**
 * OpenClaw Source Reference:
 * - src/media/mime.ts
 *
 * MIME type detection, extension mapping, and media classification.
 *
 * Android adaptation: uses magic-byte sniffing instead of file-type npm module.
 */

import java.io.File
import java.net.URI

// ============================================================================
// MIME <-> Extension maps (aligned with OpenClaw)
// ============================================================================

private val EXT_BY_MIME: Map<String, String> = mapOf(
    "image/heic" to ".heic",
    "image/heif" to ".heif",
    "image/jpeg" to ".jpg",
    "image/png" to ".png",
    "image/webp" to ".webp",
    "image/gif" to ".gif",
    "audio/ogg" to ".ogg",
    "audio/mpeg" to ".mp3",
    "audio/wav" to ".wav",
    "audio/flac" to ".flac",
    "audio/aac" to ".aac",
    "audio/opus" to ".opus",
    "audio/x-m4a" to ".m4a",
    "audio/mp4" to ".m4a",
    "video/mp4" to ".mp4",
    "video/quicktime" to ".mov",
    "application/pdf" to ".pdf",
    "application/json" to ".json",
    "application/zip" to ".zip",
    "application/gzip" to ".gz",
    "application/x-tar" to ".tar",
    "application/x-7z-compressed" to ".7z",
    "application/vnd.rar" to ".rar",
    "application/msword" to ".doc",
    "application/vnd.ms-excel" to ".xls",
    "application/vnd.ms-powerpoint" to ".ppt",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to ".docx",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to ".xlsx",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation" to ".pptx",
    "text/csv" to ".csv",
    "text/plain" to ".txt",
    "text/markdown" to ".md",
    "text/html" to ".html",
    "text/xml" to ".xml",
    "text/css" to ".css",
    "application/xml" to ".xml"
)

private val MIME_BY_EXT: Map<String, String> = buildMap {
    EXT_BY_MIME.forEach { (mime, ext) -> put(ext, mime) }
    // Additional extension aliases
    put(".jpeg", "image/jpeg")
    put(".js", "text/javascript")
    put(".htm", "text/html")
    put(".xml", "text/xml")
}

private val AUDIO_FILE_EXTENSIONS = setOf(
    ".aac", ".caf", ".flac", ".m4a", ".mp3",
    ".oga", ".ogg", ".opus", ".wav"
)

// ============================================================================
// Normalization
// ============================================================================

/**
 * Normalize a MIME type by stripping parameters (charset, etc).
 * Aligned with OpenClaw normalizeMimeType.
 */
fun normalizeMimeType(mime: String?): String? {
    if (mime.isNullOrBlank()) return null
    val cleaned = mime.split(";")[0].trim().lowercase()
    return cleaned.ifBlank { null }
}

// ============================================================================
// Extension helpers
// ============================================================================

/**
 * Get file extension from a path or URL.
 * Aligned with OpenClaw getFileExtension.
 */
fun getFileExtension(filePath: String?): String? {
    if (filePath.isNullOrBlank()) return null
    return try {
        if (filePath.matches(Regex("^https?://.*", RegexOption.IGNORE_CASE))) {
            val uri = URI(filePath)
            val path = uri.path
            val dotIdx = path.lastIndexOf('.')
            if (dotIdx >= 0) path.substring(dotIdx).lowercase().ifBlank { null }
            else null
        } else {
            val name = File(filePath).name
            val dotIdx = name.lastIndexOf('.')
            if (dotIdx >= 0) name.substring(dotIdx).lowercase().ifBlank { null }
            else null
        }
    } catch (_: Exception) {
        val name = filePath.substringAfterLast('/')
        val dotIdx = name.lastIndexOf('.')
        if (dotIdx >= 0) name.substring(dotIdx).lowercase().ifBlank { null }
        else null
    }
}

/**
 * Check if a filename has an audio extension.
 * Aligned with OpenClaw isAudioFileName.
 */
fun isAudioFileName(fileName: String?): Boolean {
    val ext = getFileExtension(fileName) ?: return false
    return ext in AUDIO_FILE_EXTENSIONS
}

// ============================================================================
// MIME detection
// ============================================================================

/**
 * Detect MIME type from buffer, header, and file path hints.
 * Aligned with OpenClaw detectMime.
 *
 * Android adaptation: uses magic-byte sniffing instead of file-type npm module.
 */
fun detectMime(
    buffer: ByteArray? = null,
    headerMime: String? = null,
    filePath: String? = null
): String? {
    val ext = getFileExtension(filePath)
    val extMime = if (ext != null) MIME_BY_EXT[ext] else null
    val normalizedHeader = normalizeMimeType(headerMime)
    val sniffed = if (buffer != null) sniffMime(buffer) else null

    // Prefer sniffed types, but don't let generic container types override specific extension mapping
    if (sniffed != null && (!isGenericMime(sniffed) || extMime == null)) return sniffed
    if (extMime != null) return extMime
    if (normalizedHeader != null && !isGenericMime(normalizedHeader)) return normalizedHeader
    if (sniffed != null) return sniffed
    if (normalizedHeader != null) return normalizedHeader

    return null
}

/**
 * Get extension for a MIME type.
 * Aligned with OpenClaw extensionForMime.
 */
fun extensionForMime(mime: String?): String? {
    val normalized = normalizeMimeType(mime) ?: return null
    return EXT_BY_MIME[normalized]
}

/**
 * Check if media is GIF.
 * Aligned with OpenClaw isGifMedia.
 */
fun isGifMedia(contentType: String? = null, fileName: String? = null): Boolean {
    if (contentType?.lowercase() == "image/gif") return true
    return getFileExtension(fileName) == ".gif"
}

/**
 * Get image MIME from format string.
 * Aligned with OpenClaw imageMimeFromFormat.
 */
fun imageMimeFromFormat(format: String?): String? {
    if (format == null) return null
    return when (format.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> null
    }
}

/**
 * Get media kind from MIME type.
 * Aligned with OpenClaw kindFromMime.
 */
fun kindFromMime(mime: String?): MediaKind? = mediaKindFromMime(normalizeMimeType(mime))

// ============================================================================
// Magic-byte sniffing (Android replacement for file-type npm module)
// ============================================================================

private fun isGenericMime(mime: String?): Boolean {
    if (mime == null) return true
    val m = mime.lowercase()
    return m == "application/octet-stream" || m == "application/zip"
}

/**
 * Sniff MIME type from buffer magic bytes.
 */
private fun sniffMime(buffer: ByteArray): String? {
    if (buffer.size < 4) return null

    // PNG: 89 50 4E 47
    if (buffer.size >= 8 &&
        buffer[0] == 0x89.toByte() && buffer[1] == 0x50.toByte() &&
        buffer[2] == 0x4E.toByte() && buffer[3] == 0x47.toByte()
    ) return "image/png"

    // JPEG: FF D8 FF
    if (buffer[0] == 0xFF.toByte() && buffer[1] == 0xD8.toByte() && buffer[2] == 0xFF.toByte()) {
        return "image/jpeg"
    }

    // GIF: GIF87a or GIF89a
    if (buffer.size >= 6) {
        val sig = String(buffer, 0, 6, Charsets.US_ASCII)
        if (sig == "GIF87a" || sig == "GIF89a") return "image/gif"
    }

    // WebP: RIFF....WEBP
    if (buffer.size >= 12) {
        val riff = String(buffer, 0, 4, Charsets.US_ASCII)
        val webp = String(buffer, 8, 4, Charsets.US_ASCII)
        if (riff == "RIFF" && webp == "WEBP") return "image/webp"
    }

    // PDF: %PDF
    if (buffer.size >= 4) {
        val sig = String(buffer, 0, 4, Charsets.US_ASCII)
        if (sig == "%PDF") return "application/pdf"
    }

    // MP4: ....ftyp
    if (buffer.size >= 8) {
        val ftyp = String(buffer, 4, 4, Charsets.US_ASCII)
        if (ftyp == "ftyp") return "video/mp4"
    }

    // ZIP: PK\x03\x04
    if (buffer.size >= 4 &&
        buffer[0] == 0x50.toByte() && buffer[1] == 0x4B.toByte() &&
        buffer[2] == 0x03.toByte() && buffer[3] == 0x04.toByte()
    ) return "application/zip"

    // HEIC/HEIF: ....ftypheic or ftypheif or ftypmif1
    if (buffer.size >= 12) {
        val ftyp = String(buffer, 4, 4, Charsets.US_ASCII)
        if (ftyp == "ftyp") {
            val brand = String(buffer, 8, 4, Charsets.US_ASCII)
            if (brand == "heic" || brand == "heis" || brand == "heix") return "image/heic"
            if (brand == "heif" || brand == "mif1") return "image/heif"
        }
    }

    // OGG: OggS
    if (buffer.size >= 4) {
        val sig = String(buffer, 0, 4, Charsets.US_ASCII)
        if (sig == "OggS") return "audio/ogg"
    }

    // FLAC: fLaC
    if (buffer.size >= 4) {
        val sig = String(buffer, 0, 4, Charsets.US_ASCII)
        if (sig == "fLaC") return "audio/flac"
    }

    // WAV: RIFF....WAVE
    if (buffer.size >= 12) {
        val riff = String(buffer, 0, 4, Charsets.US_ASCII)
        val wave = String(buffer, 8, 4, Charsets.US_ASCII)
        if (riff == "RIFF" && wave == "WAVE") return "audio/wav"
    }

    // MP3: ID3 or sync word FF FB/FA/F3/F2
    if (buffer.size >= 3) {
        val sig = String(buffer, 0, 3, Charsets.US_ASCII)
        if (sig == "ID3") return "audio/mpeg"
    }
    if (buffer.size >= 2 && buffer[0] == 0xFF.toByte()) {
        val b1 = buffer[1].toInt() and 0xFF
        if (b1 == 0xFB || b1 == 0xFA || b1 == 0xF3 || b1 == 0xF2) return "audio/mpeg"
    }

    return null
}

package com.xiaomo.androidforclaw.media

/**
 * OpenClaw Source Reference:
 * - src/media/constants.ts
 *
 * Media kind classification and per-kind byte limits.
 */

const val MAX_IMAGE_BYTES = 6 * 1024 * 1024        // 6MB
const val MAX_AUDIO_BYTES = 16 * 1024 * 1024       // 16MB
const val MAX_VIDEO_BYTES = 16 * 1024 * 1024       // 16MB
const val MAX_DOCUMENT_BYTES = 100 * 1024 * 1024   // 100MB

/**
 * Aligned with OpenClaw MediaKind.
 */
enum class MediaKind(val value: String) {
    IMAGE("image"),
    AUDIO("audio"),
    VIDEO("video"),
    DOCUMENT("document");

    companion object {
        fun fromString(value: String?): MediaKind? = entries.find { it.value == value }
    }
}

/**
 * Determine media kind from MIME type.
 * Aligned with OpenClaw mediaKindFromMime.
 */
fun mediaKindFromMime(mime: String?): MediaKind? {
    if (mime == null) return null
    return when {
        mime.startsWith("image/") -> MediaKind.IMAGE
        mime.startsWith("audio/") -> MediaKind.AUDIO
        mime.startsWith("video/") -> MediaKind.VIDEO
        mime == "application/pdf" -> MediaKind.DOCUMENT
        mime.startsWith("text/") -> MediaKind.DOCUMENT
        mime.startsWith("application/") -> MediaKind.DOCUMENT
        else -> null
    }
}

/**
 * Get the max allowed bytes for a media kind.
 * Aligned with OpenClaw maxBytesForKind.
 */
fun maxBytesForKind(kind: MediaKind): Int = when (kind) {
    MediaKind.IMAGE -> MAX_IMAGE_BYTES
    MediaKind.AUDIO -> MAX_AUDIO_BYTES
    MediaKind.VIDEO -> MAX_VIDEO_BYTES
    MediaKind.DOCUMENT -> MAX_DOCUMENT_BYTES
}

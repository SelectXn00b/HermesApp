package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * Platform adapter helpers — shared utilities used across adapters.
 *
 * Ported from gateway/platforms/helpers.py
 */

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

/**
 * Message deduplicator — prevents the same message from being processed
 * twice when a platform adapter reconnects and redelivers events.
 *
 * Uses a fixed-size LRU cache keyed by message id.
 */
class MessageDeduplicator(
    /** Maximum number of entries to keep. */
    private val maxSize: Int = 1000) {
    /** message_id → timestamp (epoch millis). */
    private val _seen: ConcurrentHashMap<String, Long> = ConcurrentHashMap()

    /**
     * Check whether [messageId] has been seen before.
     *
     * Returns true if this is a duplicate (already processed).
     * Returns false if this is a new message (and records it).
     */
    fun isDuplicate(messageId: String): Boolean {
        val now = System.currentTimeMillis()
        val existing = _seen.putIfAbsent(messageId, now)
        if (existing != null) return true
        // Evict oldest entries if we've exceeded maxSize
        if (_seen.size > maxSize) {
            val oldest = _seen.entries.sortedBy { it.value }.take(_seen.size - maxSize)
            oldest.forEach { _seen.remove(it.key) }
        }
        return false
    }

    /** Check without recording. */
    fun hasSeen(messageId: String): Boolean = _seen.containsKey(messageId)

    /** Manually record a message id as seen. */
    fun markSeen(messageId: String) {
        _seen[messageId] = System.currentTimeMillis()
    }

    /** Remove a specific message id from the seen set. */
    fun forget(messageId: String) {
        _seen.remove(messageId)
    }

    /** Clear all entries. */
    fun clear() {
        _seen.clear()
    }

    /** Number of entries. */
    val size: Int get() = _seen.size
}

/**
 * Thread participation tracker — tracks which threads the bot has
 * participated in, for platforms that support threaded conversations.
 */
class ThreadParticipationTracker(
    /** Maximum number of threads to track. */
    private val maxSize: Int = 500) {
    /** thread_id → timestamp of last participation. */
    private val _threads: ConcurrentHashMap<String, Long> = ConcurrentHashMap()

    /** Record participation in a thread. */
    fun record(threadId: String) {
        _threads[threadId] = System.currentTimeMillis()
        if (_threads.size > maxSize) {
            val oldest = _threads.entries.sortedBy { it.value }.take(_threads.size - maxSize)
            oldest.forEach { _threads.remove(it.key) }
        }
    }

    /** Check whether the bot has participated in [threadId]. */
    fun hasParticipated(threadId: String): Boolean = _threads.containsKey(threadId)

    /** Get the timestamp of last participation in [threadId]. */
    fun lastParticipation(threadId: String): Long? = _threads[threadId]

    /** Remove a thread from tracking. */
    fun forget(threadId: String) {
        _threads.remove(threadId)
    }

    /** Clear all entries. */
    fun clear() {
        _threads.clear()
    }

    /** All tracked thread ids. */
    val threadIds: Set<String> get() = _threads.keys.toSet()
}

/**
 * Strip markdown formatting from text.
 *
 * Removes common markdown syntax: bold, italic, code, links, etc.
 * Used when the target platform doesn't support markdown.
 */
fun stripMarkdown(text: String): String {
    var result = text
    // Remove code blocks
    result = result.replace(Regex("```[\\s\\S]*?```"), "")
    // Remove inline code
    result = result.replace(Regex("`[^`]+`"), "")
    // Remove bold/italic
    result = result.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
    result = result.replace(Regex("\\*([^*]+)\\*"), "$1")
    result = result.replace(Regex("__([^_]+)__"), "$1")
    result = result.replace(Regex("_([^_]+)_"), "$1")
    // Remove strikethrough
    result = result.replace(Regex("~~([^~]+)~~"), "$1")
    // Remove links (keep text)
    result = result.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
    // Remove images
    result = result.replace(Regex("!\\[([^\\]]*)\\]\\([^)]+\\)"), "$1")
    // Remove headers
    result = result.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
    // Remove blockquotes
    result = result.replace(Regex("^>\\s*", RegexOption.MULTILINE), "")
    // Remove horizontal rules
    result = result.replace(Regex("^[-]{3,}\\s*$", RegexOption.MULTILINE), "")
    // Collapse multiple newlines
    result = result.replace(Regex("\n{3,}"), "\n\n")
    return result.trim()
}

/**
 * Redact phone numbers from text for logging.
 */
fun redactPhone(text: String): String =
    text.replace(Regex("\\+?\\d{7,15}"), "[REDACTED]")

/**
 * Redact email addresses from text for logging.
 */
fun redactEmail(text: String): String =
    text.replace(Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+"), "[REDACTED]")

/**
 * Make a URL safe for logging (truncate query params).
 */
fun safeUrlForLog(url: String): String {
    val idx = url.indexOf('?')
    return if (idx > 0) url.substring(0, idx) + "?..." else url
}

/**
 * Get the file extension from a filename or URL.
 */
fun getExtension(filename: String): String {
    val dot = filename.lastIndexOf('.')
    return if (dot >= 0) filename.substring(dot).lowercase() else ""
}

/**
 * Check if a file extension is an image type.
 */
fun isImageExtension(ext: String): Boolean =
    ext.lowercase() in listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg", ".ico")

/**
 * Check if a file extension is an audio type.
 */
fun isAudioExtension(ext: String): Boolean =
    ext.lowercase() in listOf(".mp3", ".wav", ".ogg", ".m4a", ".aac", ".flac", ".wma", ".opus")

/**
 * Check if a file extension is a video type.
 */
fun isVideoExtension(ext: String): Boolean =
    ext.lowercase() in listOf(".mp4", ".avi", ".mov", ".mkv", ".webm", ".flv", ".wmv", ".m4v")

/**
 * Check if a file extension is a document type.
 */
fun isDocumentExtension(ext: String): Boolean =
    ext.lowercase() in listOf(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".rtf", ".odt", ".csv")

/** MIME type mapping for common extensions. */
val EXT_TO_MIME: Map<String, String> = mapOf(
    ".jpg" to "image/jpeg",
    ".jpeg" to "image/jpeg",
    ".png" to "image/png",
    ".gif" to "image/gif",
    ".webp" to "image/webp",
    ".bmp" to "image/bmp",
    ".svg" to "image/svg+xml",
    ".mp3" to "audio/mpeg",
    ".wav" to "audio/wav",
    ".ogg" to "audio/ogg",
    ".m4a" to "audio/mp4",
    ".aac" to "audio/aac",
    ".mp4" to "video/mp4",
    ".avi" to "video/x-msvideo",
    ".mov" to "video/quicktime",
    ".mkv" to "video/x-matroska",
    ".webm" to "video/webm",
    ".pdf" to "application/pdf",
    ".doc" to "application/msword",
    ".docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ".xls" to "application/vnd.ms-excel",
    ".xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    ".ppt" to "application/vnd.ms-powerpoint",
    ".pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    ".txt" to "text/plain",
    ".csv" to "text/csv",
    ".json" to "application/json",
    ".xml" to "application/xml",
    ".zip" to "application/zip",
    ".gz" to "application/gzip",
    ".tar" to "application/x-tar")

/**
 * Get the MIME type for a file extension.
 */
fun getMimeType(ext: String): String =
    EXT_TO_MIME[ext.lowercase()] ?: "application/octet-stream"

// SUPPORTED_DOCUMENT_TYPES is defined in Base.kt

/**
 * UTF-16 aware string length (for platforms that count in UTF-16 code units).
 */
fun utf16Length(text: String): Int = text.toByteArray(Charsets.UTF_16).size / 2

/**
 * Truncate a string to [maxUtf16Len] UTF-16 code units.
 */
fun truncateUtf16(text: String, maxUtf16Len: Int): String {
    if (utf16Length(text) <= maxUtf16Len) return text
    var truncated = text
    while (utf16Length(truncated) > maxUtf16Len && truncated.isNotEmpty()) {
        truncated = truncated.dropLast(1)
    }
    return truncated
}

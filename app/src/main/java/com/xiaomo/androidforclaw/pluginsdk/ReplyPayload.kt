package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/reply-payload.ts
 *
 * Outbound reply payload normalization, media URL resolution,
 * chunked text/media delivery, and attachment formatting.
 * Android adaptation: uses Kotlin suspend functions instead of Promise chains.
 */

// ---------- Payload Types ----------

/**
 * Outbound reply payload.
 * Aligned with TS OutboundReplyPayload.
 */
data class OutboundReplyPayload(
    val text: String? = null,
    val mediaUrls: List<String>? = null,
    val mediaUrl: String? = null,
    val replyToId: String? = null,
)

/**
 * Sendable outbound reply parts after normalization.
 * Aligned with TS SendableOutboundReplyParts.
 */
data class SendableOutboundReplyParts(
    val text: String,
    val trimmedText: String,
    val mediaUrls: List<String>,
    val mediaCount: Int,
    val hasText: Boolean,
    val hasMedia: Boolean,
    val hasContent: Boolean,
)

// ---------- Normalization ----------

/**
 * Extract the supported outbound reply fields from loose tool or agent payload objects.
 * Aligned with TS normalizeOutboundReplyPayload.
 */
@Suppress("UNCHECKED_CAST")
fun normalizeOutboundReplyPayload(payload: Map<String, Any?>): OutboundReplyPayload {
    val text = payload["text"] as? String
    val mediaUrls = (payload["mediaUrls"] as? List<*>)
        ?.filterIsInstance<String>()
        ?.filter { it.isNotEmpty() }
    val mediaUrl = payload["mediaUrl"] as? String
    val replyToId = payload["replyToId"] as? String
    return OutboundReplyPayload(
        text = text,
        mediaUrls = mediaUrls,
        mediaUrl = mediaUrl,
        replyToId = replyToId,
    )
}

// ---------- Media URL Resolution ----------

/**
 * Prefer multi-attachment payloads, then fall back to the legacy single-media field.
 * Aligned with TS resolveOutboundMediaUrls.
 */
fun resolveOutboundMediaUrls(payload: OutboundReplyPayload): List<String> {
    if (!payload.mediaUrls.isNullOrEmpty()) return payload.mediaUrls
    if (!payload.mediaUrl.isNullOrEmpty()) return listOf(payload.mediaUrl)
    return emptyList()
}

/**
 * Count outbound media items after legacy single-media fallback normalization.
 * Aligned with TS countOutboundMedia.
 */
fun countOutboundMedia(payload: OutboundReplyPayload): Int =
    resolveOutboundMediaUrls(payload).size

/**
 * Check whether an outbound payload includes any media after normalization.
 * Aligned with TS hasOutboundMedia.
 */
fun hasOutboundMedia(payload: OutboundReplyPayload): Boolean =
    countOutboundMedia(payload) > 0

/**
 * Check whether an outbound payload includes text, optionally trimming whitespace first.
 * Aligned with TS hasOutboundText.
 */
fun hasOutboundText(payload: OutboundReplyPayload, trim: Boolean = false): Boolean {
    val text = if (trim) payload.text?.trim() else payload.text
    return !text.isNullOrEmpty()
}

/**
 * Check whether an outbound payload includes any sendable text or media.
 * Aligned with TS hasOutboundReplyContent.
 */
fun hasOutboundReplyContent(payload: OutboundReplyPayload, trimText: Boolean = false): Boolean =
    hasOutboundText(payload, trim = trimText) || hasOutboundMedia(payload)

// ---------- Sendable Parts ----------

/**
 * Normalize reply payload text/media into a trimmed, sendable shape for delivery paths.
 * Aligned with TS resolveSendableOutboundReplyParts.
 */
fun resolveSendableOutboundReplyParts(
    payload: OutboundReplyPayload,
    overrideText: String? = null,
): SendableOutboundReplyParts {
    val text = overrideText ?: payload.text ?: ""
    val trimmedText = text.trim()
    val mediaUrls = resolveOutboundMediaUrls(payload)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val mediaCount = mediaUrls.size
    val hasText = trimmedText.isNotEmpty()
    val hasMedia = mediaCount > 0
    return SendableOutboundReplyParts(
        text = text,
        trimmedText = trimmedText,
        mediaUrls = mediaUrls,
        mediaCount = mediaCount,
        hasText = hasText,
        hasMedia = hasMedia,
        hasContent = hasText || hasMedia,
    )
}

/**
 * Preserve caller-provided chunking, but fall back to the full text when chunkers return nothing.
 * Aligned with TS resolveTextChunksWithFallback.
 */
fun resolveTextChunksWithFallback(text: String, chunks: List<String>): List<String> {
    if (chunks.isNotEmpty()) return chunks.toList()
    if (text.isEmpty()) return emptyList()
    return listOf(text)
}

// ---------- Attachment Formatting ----------

/**
 * Detect numeric-looking target ids for channels that distinguish ids from handles.
 * Aligned with TS isNumericTargetId.
 */
fun isNumericTargetId(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return false
    return Regex("^\\d{3,}$").matches(trimmed)
}

/**
 * Append attachment links to plain text when the channel cannot send media inline.
 * Aligned with TS formatTextWithAttachmentLinks.
 */
fun formatTextWithAttachmentLinks(
    text: String?,
    mediaUrls: List<String>,
): String {
    val trimmedText = text?.trim() ?: ""
    if (trimmedText.isEmpty() && mediaUrls.isEmpty()) return ""
    val mediaBlock = if (mediaUrls.isNotEmpty()) {
        mediaUrls.joinToString("\n") { "Attachment: $it" }
    } else {
        ""
    }
    if (trimmedText.isEmpty()) return mediaBlock
    if (mediaBlock.isEmpty()) return trimmedText
    return "$trimmedText\n\n$mediaBlock"
}

// ---------- Media Delivery Helpers ----------

/**
 * Send a caption with only the first media item, mirroring caption-limited channel transports.
 * Aligned with TS sendMediaWithLeadingCaption.
 */
suspend fun sendMediaWithLeadingCaption(
    mediaUrls: List<String>,
    caption: String,
    send: suspend (mediaUrl: String, caption: String?) -> Unit,
    onError: (suspend (error: Exception, mediaUrl: String, caption: String?, index: Int, isFirst: Boolean) -> Unit)? = null,
): Boolean {
    if (mediaUrls.isEmpty()) return false
    for ((index, mediaUrl) in mediaUrls.withIndex()) {
        val isFirst = index == 0
        val cap = if (isFirst) caption else null
        try {
            send(mediaUrl, cap)
        } catch (e: Exception) {
            if (onError != null) {
                onError(e, mediaUrl, cap, index, isFirst)
                continue
            }
            throw e
        }
    }
    return true
}

/**
 * Result of delivering text or media reply.
 * Aligned with TS deliverTextOrMediaReply return type.
 */
enum class DeliverReplyResult { EMPTY, TEXT, MEDIA }

/**
 * Deliver text or media reply payload.
 * Aligned with TS deliverTextOrMediaReply.
 */
suspend fun deliverTextOrMediaReply(
    payload: OutboundReplyPayload,
    text: String,
    chunkText: ((String) -> List<String>)? = null,
    sendText: suspend (String) -> Unit,
    sendMedia: suspend (mediaUrl: String, caption: String?) -> Unit,
    onMediaError: (suspend (error: Exception, mediaUrl: String, caption: String?, index: Int, isFirst: Boolean) -> Unit)? = null,
): DeliverReplyResult {
    val parts = resolveSendableOutboundReplyParts(payload, overrideText = text)
    val sentMedia = sendMediaWithLeadingCaption(
        mediaUrls = parts.mediaUrls,
        caption = text,
        send = sendMedia,
        onError = onMediaError,
    )
    if (sentMedia) return DeliverReplyResult.MEDIA
    if (text.isEmpty()) return DeliverReplyResult.EMPTY
    val chunks = chunkText?.invoke(text) ?: listOf(text)
    var sentAny = false
    for (chunk in chunks) {
        if (chunk.isEmpty()) continue
        sendText(chunk)
        sentAny = true
    }
    return if (sentAny) DeliverReplyResult.TEXT else DeliverReplyResult.EMPTY
}

/**
 * Deliver formatted text with attachment links.
 * Aligned with TS deliverFormattedTextWithAttachments.
 */
suspend fun deliverFormattedTextWithAttachments(
    payload: OutboundReplyPayload,
    send: suspend (text: String, replyToId: String?) -> Unit,
): Boolean {
    val text = formatTextWithAttachmentLinks(
        payload.text,
        resolveOutboundMediaUrls(payload),
    )
    if (text.isEmpty()) return false
    send(text, payload.replyToId)
    return true
}

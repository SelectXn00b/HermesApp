package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/boolean-param.ts
 * - src/plugin-sdk/text-chunking.ts
 * - src/plugin-sdk/resolution-notes.ts
 * - src/plugin-sdk/webhook-path.ts
 * - src/plugin-sdk/request-url.ts
 * - src/plugin-sdk/config-paths.ts
 * - src/plugin-sdk/account-id.ts
 * - src/plugin-sdk/tool-send.ts
 *
 * Core utilities for plugin-sdk: boolean param parsing, text chunking,
 * resolution notes, webhook path normalization, request URL extraction,
 * config path resolution, account ID normalization, and tool send extraction.
 */

// ---------- Boolean Param (boolean-param.ts) ----------

/**
 * Read loose boolean params from tool input that may arrive as booleans or "true"/"false" strings.
 * Aligned with TS readBooleanParam.
 */
fun readBooleanParam(params: Map<String, Any?>, key: String): Boolean? {
    val raw = params[key] ?: return null
    if (raw is Boolean) return raw
    if (raw is String) {
        return when (raw.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
    return null
}

// ---------- Text Chunking (text-chunking.ts) ----------

/**
 * Chunk text by break resolver while preferring newline boundaries over spaces.
 * Aligned with TS chunkTextByBreakResolver + chunkTextForOutbound.
 */
fun chunkTextByBreakResolver(
    text: String,
    limit: Int,
    resolveBreak: (window: String) -> Int,
): List<String> {
    if (text.length <= limit) return if (text.isEmpty()) emptyList() else listOf(text)

    val chunks = mutableListOf<String>()
    var start = 0

    while (start < text.length) {
        val remaining = text.length - start
        if (remaining <= limit) {
            chunks.add(text.substring(start))
            break
        }

        val window = text.substring(start, start + limit)
        val breakIdx = resolveBreak(window)
        val splitAt = if (breakIdx > 0) breakIdx else limit

        chunks.add(text.substring(start, start + splitAt))
        start += splitAt
    }

    return chunks
}

/**
 * Chunk outbound text while preferring newline boundaries over spaces.
 * Aligned with TS chunkTextForOutbound.
 */
fun chunkTextForOutbound(text: String, limit: Int): List<String> {
    return chunkTextByBreakResolver(text, limit) { window ->
        val lastNewline = window.lastIndexOf('\n')
        val lastSpace = window.lastIndexOf(' ')
        if (lastNewline > 0) lastNewline else lastSpace
    }
}

// ---------- Resolution Notes (resolution-notes.ts) ----------

/**
 * Format a short note that separates successfully resolved targets from unresolved passthrough values.
 * Aligned with TS formatResolvedUnresolvedNote.
 */
fun formatResolvedUnresolvedNote(
    resolved: List<String>,
    unresolved: List<String>,
): String? {
    if (resolved.isEmpty() && unresolved.isEmpty()) return null
    return listOfNotNull(
        if (resolved.isNotEmpty()) "Resolved: ${resolved.joinToString(", ")}" else null,
        if (unresolved.isNotEmpty()) "Unresolved (kept as typed): ${unresolved.joinToString(", ")}" else null,
    ).joinToString("\n")
}

// ---------- Webhook Path (webhook-path.ts) ----------

/**
 * Normalize webhook paths into the canonical registry form used by route lookup.
 * Aligned with TS normalizeWebhookPath.
 */
fun normalizeWebhookPath(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "/"
    val withSlash = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    return if (withSlash.length > 1 && withSlash.endsWith("/")) {
        withSlash.dropLast(1)
    } else {
        withSlash
    }
}

/**
 * Resolve the effective webhook path from explicit path, URL, or default fallback.
 * Aligned with TS resolveWebhookPath.
 */
fun resolveWebhookPath(
    webhookPath: String? = null,
    webhookUrl: String? = null,
    defaultPath: String? = null,
): String? {
    val trimmedPath = webhookPath?.trim()
    if (!trimmedPath.isNullOrEmpty()) {
        return normalizeWebhookPath(trimmedPath)
    }
    val trimmedUrl = webhookUrl?.trim()
    if (!trimmedUrl.isNullOrEmpty()) {
        return try {
            val url = java.net.URL(trimmedUrl)
            normalizeWebhookPath(url.path.ifEmpty { "/" })
        } catch (_: Exception) {
            null
        }
    }
    return defaultPath
}

// ---------- Request URL (request-url.ts) ----------

/**
 * Extract a string URL from common request-like inputs.
 * Simplified for Android (no Request objects).
 * Aligned with TS resolveRequestUrl.
 */
fun resolveRequestUrl(input: Any?): String = when (input) {
    is String -> input
    is java.net.URL -> input.toString()
    else -> ""
}

// ---------- Config Paths (config-paths.ts) ----------

/**
 * Resolve the config path prefix for a channel account, falling back to the root channel section.
 * Aligned with TS resolveChannelAccountConfigBasePath.
 */
@Suppress("UNCHECKED_CAST")
fun resolveChannelAccountConfigBasePath(
    cfg: Map<String, Any?>,
    channelKey: String,
    accountId: String,
): String {
    val channels = cfg["channels"] as? Map<String, Any?> ?: return "channels.$channelKey."
    val channelSection = channels[channelKey] as? Map<String, Any?> ?: return "channels.$channelKey."
    val accounts = channelSection["accounts"] as? Map<String, Any?>
    val useAccountPath = accounts?.containsKey(accountId) == true
    return if (useAccountPath) {
        "channels.$channelKey.accounts.$accountId."
    } else {
        "channels.$channelKey."
    }
}

// ---------- Account ID (account-id.ts) ----------

/** Default account ID for channels with a single unnamed account. */
const val DEFAULT_ACCOUNT_ID = "default"

/** Normalize an account ID string, falling back to the default. */
fun normalizeAccountId(accountId: String?): String {
    val trimmed = accountId?.trim()?.lowercase()
    return if (trimmed.isNullOrEmpty()) DEFAULT_ACCOUNT_ID else trimmed
}

/** Normalize an optional account ID, returning null when blank. */
fun normalizeOptionalAccountId(accountId: String?): String? {
    val trimmed = accountId?.trim()?.lowercase()
    return if (trimmed.isNullOrEmpty()) null else trimmed
}

// ---------- Tool Send (tool-send.ts) ----------

/**
 * Tool send extraction result.
 * Aligned with TS extractToolSend return type.
 */
data class ToolSendTarget(
    val to: String,
    val accountId: String? = null,
    val threadId: String? = null,
)

/**
 * Extract the canonical send target fields from tool arguments when the action matches.
 * Aligned with TS extractToolSend.
 */
fun extractToolSend(
    args: Map<String, Any?>,
    expectedAction: String = "sendMessage",
): ToolSendTarget? {
    val action = (args["action"] as? String)?.trim() ?: ""
    if (action != expectedAction) return null
    val to = args["to"] as? String ?: return null
    val accountId = (args["accountId"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
    val threadIdRaw = when (val raw = args["threadId"]) {
        is String -> raw.trim()
        is Number -> raw.toString()
        else -> ""
    }
    val threadId = threadIdRaw.takeIf { it.isNotEmpty() }
    return ToolSendTarget(to = to, accountId = accountId, threadId = threadId)
}

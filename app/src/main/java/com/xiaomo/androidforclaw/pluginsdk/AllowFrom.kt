package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/allow-from.ts
 *
 * Allowlist matching helpers for channel plugins.
 * Android adaptation: standalone functions without TS module re-exports.
 */

// ---------- Types ----------

/**
 * Basic allowlist resolution entry.
 * Aligned with TS BasicAllowlistResolutionEntry.
 */
data class BasicAllowlistResolutionEntry(
    val input: String,
    val resolved: Boolean,
    val id: String? = null,
    val name: String? = null,
    val note: String? = null,
)

// ---------- Formatting ----------

/**
 * Lowercase and optionally strip prefixes from allowlist entries before sender comparisons.
 * Aligned with TS formatAllowFromLowercase.
 */
fun formatAllowFromLowercase(
    allowFrom: List<Any>,
    stripPrefixRe: Regex? = null,
): List<String> {
    return allowFrom
        .map { it.toString().trim() }
        .filter { it.isNotEmpty() }
        .map { entry ->
            if (stripPrefixRe != null) entry.replace(stripPrefixRe, "") else entry
        }
        .map { it.lowercase() }
}

/**
 * Normalize allowlist entries through a channel-provided parser or canonicalizer.
 * Aligned with TS formatNormalizedAllowFromEntries.
 */
fun formatNormalizedAllowFromEntries(
    allowFrom: List<Any>,
    normalizeEntry: (String) -> String?,
): List<String> {
    return allowFrom
        .map { it.toString().trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { normalizeEntry(it) }
}

// ---------- Sender Matching ----------

/**
 * Check whether a sender id matches a simple normalized allowlist with wildcard support.
 * Aligned with TS isNormalizedSenderAllowed.
 */
fun isNormalizedSenderAllowed(
    senderId: Any,
    allowFrom: List<Any>,
    stripPrefixRe: Regex? = null,
): Boolean {
    val normalizedAllow = formatAllowFromLowercase(allowFrom, stripPrefixRe)
    if (normalizedAllow.isEmpty()) return false
    if ("*" in normalizedAllow) return true
    val sender = senderId.toString().trim().lowercase()
    return sender in normalizedAllow
}

// ---------- Chat-aware Matching ----------

/**
 * Parsed chat allow target kind.
 * Aligned with TS ParsedChatAllowTarget.
 */
sealed class ParsedChatAllowTarget {
    data class ChatId(val chatId: Long) : ParsedChatAllowTarget()
    data class ChatGuid(val chatGuid: String) : ParsedChatAllowTarget()
    data class ChatIdentifier(val chatIdentifier: String) : ParsedChatAllowTarget()
    data class Handle(val handle: String) : ParsedChatAllowTarget()
}

/**
 * Match chat-aware allowlist entries against sender, chat id, guid, or identifier fields.
 * Aligned with TS isAllowedParsedChatSender.
 */
fun isAllowedParsedChatSender(
    allowFrom: List<Any>,
    sender: String,
    chatId: Long? = null,
    chatGuid: String? = null,
    chatIdentifier: String? = null,
    normalizeSender: (String) -> String,
    parseAllowTarget: (String) -> ParsedChatAllowTarget,
): Boolean {
    val entries = allowFrom.map { it.toString().trim() }
    if (entries.isEmpty()) return false
    if ("*" in entries) return true

    val senderNormalized = normalizeSender(sender)
    val trimmedGuid = chatGuid?.trim()
    val trimmedIdentifier = chatIdentifier?.trim()

    for (entry in entries) {
        if (entry.isEmpty()) continue
        val parsed = parseAllowTarget(entry)
        when {
            parsed is ParsedChatAllowTarget.ChatId && chatId != null -> {
                if (parsed.chatId == chatId) return true
            }
            parsed is ParsedChatAllowTarget.ChatGuid && !trimmedGuid.isNullOrEmpty() -> {
                if (parsed.chatGuid == trimmedGuid) return true
            }
            parsed is ParsedChatAllowTarget.ChatIdentifier && !trimmedIdentifier.isNullOrEmpty() -> {
                if (parsed.chatIdentifier == trimmedIdentifier) return true
            }
            parsed is ParsedChatAllowTarget.Handle && senderNormalized.isNotEmpty() -> {
                if (parsed.handle == senderNormalized) return true
            }
        }
    }
    return false
}

/**
 * Clone allowlist resolution entries into a plain serializable shape.
 * Aligned with TS mapBasicAllowlistResolutionEntries.
 */
fun mapBasicAllowlistResolutionEntries(
    entries: List<BasicAllowlistResolutionEntry>,
): List<BasicAllowlistResolutionEntry> = entries.map { it.copy() }

/**
 * Map allowlist inputs sequentially so resolver side effects stay ordered and predictable.
 * Aligned with TS mapAllowlistResolutionInputs.
 */
suspend fun <T> mapAllowlistResolutionInputs(
    inputs: List<String>,
    mapInput: suspend (String) -> T,
): List<T> {
    val results = mutableListOf<T>()
    for (input in inputs) {
        results.add(mapInput(input))
    }
    return results
}

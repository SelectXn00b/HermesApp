package com.xiaomo.androidforclaw.sessions

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/sessions/input-provenance.ts
 *
 * AndroidForClaw adaptation: input provenance tagging for user messages.
 */

/**
 * Describes the origin kind of a user message.
 */
enum class InputProvenanceKind(val value: String) {
    EXTERNAL_USER("external_user"),
    INTER_SESSION("inter_session"),
    INTERNAL_SYSTEM("internal_system");

    companion object {
        private val BY_VALUE = entries.associateBy { it.value }

        fun fromValue(raw: String?): InputProvenanceKind? = BY_VALUE[raw]
    }
}

/**
 * Provenance metadata attached to a user message.
 */
data class InputProvenance(
    val kind: InputProvenanceKind,
    val originSessionId: String? = null,
    val sourceSessionKey: String? = null,
    val sourceChannel: String? = null,
    val sourceTool: String? = null
)

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

private fun normalizeOptionalString(value: Any?): String? {
    if (value !is String) return null
    val trimmed = value.trim()
    return trimmed.ifEmpty { null }
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Attempt to normalize an arbitrary value into an [InputProvenance].
 *
 * @return A normalized [InputProvenance] or `null` if the value is not valid.
 */
fun normalizeInputProvenance(value: Any?): InputProvenance? {
    if (value == null || value !is Map<*, *>) return null
    @Suppress("UNCHECKED_CAST")
    val record = value as Map<String, Any?>

    val kind = InputProvenanceKind.fromValue(record["kind"] as? String) ?: return null
    return InputProvenance(
        kind = kind,
        originSessionId = normalizeOptionalString(record["originSessionId"]),
        sourceSessionKey = normalizeOptionalString(record["sourceSessionKey"]),
        sourceChannel = normalizeOptionalString(record["sourceChannel"]),
        sourceTool = normalizeOptionalString(record["sourceTool"])
    )
}

/**
 * Apply input provenance to a user message map.
 *
 * Returns a new map with the `provenance` field set if the message is a
 * `user` role message and does not already carry provenance.
 */
fun applyInputProvenanceToUserMessage(
    message: Map<String, Any?>,
    provenance: InputProvenance?
): Map<String, Any?> {
    if (provenance == null) return message
    if (message["role"] != "user") return message

    // Do not overwrite existing provenance.
    val existing = normalizeInputProvenance(message["provenance"])
    if (existing != null) return message

    return message.toMutableMap().apply {
        put("provenance", mapOf(
            "kind" to provenance.kind.value,
            "originSessionId" to provenance.originSessionId,
            "sourceSessionKey" to provenance.sourceSessionKey,
            "sourceChannel" to provenance.sourceChannel,
            "sourceTool" to provenance.sourceTool
        ))
    }
}

/**
 * Returns `true` when [value] represents an inter-session input provenance.
 */
fun isInterSessionInputProvenance(value: Any?): Boolean {
    return normalizeInputProvenance(value)?.kind == InputProvenanceKind.INTER_SESSION
}

/**
 * Returns `true` when [message] is a user message with inter-session provenance.
 */
fun hasInterSessionUserProvenance(message: Map<String, Any?>?): Boolean {
    if (message == null || message["role"] != "user") return false
    return isInterSessionInputProvenance(message["provenance"])
}

package com.xiaomo.androidforclaw.channel

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/sender-identity.ts (validateSenderIdentity)
 * - ../openclaw/src/channels/sender-label.ts (resolveSenderLabel, listSenderLabelCandidates)
 * - ../openclaw/src/channels/chat-type.ts (ChatType, normalizeChatType)
 *
 * AndroidForClaw adaptation: sender identity validation and label resolution.
 */

/**
 * Sender identity fields from inbound messages.
 * Aligned with OpenClaw MsgContext sender fields.
 */
data class SenderIdentity(
    val senderId: String? = null,
    val senderName: String? = null,
    val senderUsername: String? = null,
    val senderE164: String? = null,  // E.164 phone number
    val chatType: String? = null     // "direct" / "group" / "channel"
)

/**
 * Normalized chat types.
 * Aligned with OpenClaw ChatType.
 */
object ChatTypes {
    const val DIRECT = "direct"
    const val GROUP = "group"
    const val CHANNEL = "channel"

    /**
     * Normalize raw chat type string.
     * Aligned with OpenClaw normalizeChatType.
     */
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        return when (raw.trim().lowercase()) {
            "direct", "dm" -> DIRECT
            "group" -> GROUP
            "channel" -> CHANNEL
            else -> null
        }
    }
}

/**
 * SenderIdentity validation and label resolution.
 * Aligned with OpenClaw sender-identity.ts and sender-label.ts.
 */
object SenderIdentityValidator {

    private val E164_PATTERN = Regex("^\\+\\d{3,}$")
    private val USERNAME_INVALID_PATTERN = Regex("[@\\s]")

    /**
     * Validate sender identity fields.
     * Aligned with OpenClaw validateSenderIdentity.
     */
    fun validate(identity: SenderIdentity): List<String> {
        val issues = mutableListOf<String>()
        val normalizedChatType = ChatTypes.normalize(identity.chatType)

        // Non-direct chats must have at least one sender field
        if (normalizedChatType != null && normalizedChatType != ChatTypes.DIRECT) {
            val hasSender = !identity.senderId.isNullOrBlank() ||
                !identity.senderName.isNullOrBlank() ||
                !identity.senderUsername.isNullOrBlank() ||
                !identity.senderE164.isNullOrBlank()

            if (!hasSender) {
                issues.add("missing sender identity (SenderId/SenderName/SenderUsername/SenderE164)")
            }
        }

        // E.164 validation
        if (!identity.senderE164.isNullOrBlank()) {
            if (!E164_PATTERN.matches(identity.senderE164)) {
                issues.add("SenderE164 must match E.164 format (e.g., +8613800138000), got: ${identity.senderE164}")
            }
        }

        // Username validation
        if (!identity.senderUsername.isNullOrBlank()) {
            if (USERNAME_INVALID_PATTERN.containsMatchIn(identity.senderUsername)) {
                issues.add("SenderUsername must not contain @ or whitespace, got: ${identity.senderUsername}")
            }
        }

        // SenderId must not be set-but-empty
        if (identity.senderId != null && identity.senderId.isBlank()) {
            issues.add("SenderId is set but empty")
        }

        return issues
    }

    /**
     * Resolve a display label for a sender.
     * Aligned with OpenClaw resolveSenderLabel.
     *
     * Priority:
     * - Display part: name > username > tag (no tag in Android, skip)
     * - ID part: e164 > id
     * - Combined: "display (idPart)" if both and different, otherwise whichever is present
     */
    fun resolveSenderLabel(identity: SenderIdentity): String? {
        val display = identity.senderName?.takeIf { it.isNotBlank() }
            ?: identity.senderUsername?.takeIf { it.isNotBlank() }

        val idPart = identity.senderE164?.takeIf { it.isNotBlank() }
            ?: identity.senderId?.takeIf { it.isNotBlank() }

        return when {
            display != null && idPart != null && display != idPart -> "$display ($idPart)"
            display != null -> display
            idPart != null -> idPart
            else -> null
        }
    }

    /**
     * Build a display label, with fallback to "Unknown".
     * Convenience wrapper over resolveSenderLabel.
     */
    fun buildLabel(identity: SenderIdentity): String {
        return resolveSenderLabel(identity) ?: "Unknown"
    }

    /**
     * List all non-empty sender label candidates (for deduplication).
     * Aligned with OpenClaw listSenderLabelCandidates.
     */
    fun listSenderLabelCandidates(identity: SenderIdentity): List<String> {
        val candidates = mutableListOf<String>()
        identity.senderName?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        identity.senderUsername?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        identity.senderE164?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        identity.senderId?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        resolveSenderLabel(identity)?.let { candidates.add(it) }
        return candidates.distinct()
    }

    /**
     * Build a unique sender key for deduplication.
     */
    fun buildSenderKey(identity: SenderIdentity): String {
        return identity.senderId
            ?: identity.senderUsername
            ?: identity.senderE164
            ?: identity.senderName
            ?: "anonymous"
    }
}

package com.xiaomo.androidforclaw.chat

/**
 * OpenClaw module: channels
 * Source: OpenClaw/src/channels/sender-identity.ts
 *
 * Validates sender identity fields from inbound message context.
 */

/**
 * Validate sender identity fields.
 * Aligned with TS validateSenderIdentity().
 *
 * @param senderId  ctx.SenderId
 * @param senderName ctx.SenderName
 * @param senderUsername ctx.SenderUsername
 * @param senderE164 ctx.SenderE164
 * @param chatType ctx.ChatType (raw)
 * @param senderIdExplicitlySet  whether SenderId was explicitly set (even if empty)
 */
fun validateSenderIdentity(
    senderId: String? = null,
    senderName: String? = null,
    senderUsername: String? = null,
    senderE164: String? = null,
    chatType: String? = null,
    senderIdExplicitlySet: Boolean = false,
): List<String> {
    val issues = mutableListOf<String>()

    val normalizedChatType = ChatType.normalize(chatType)
    val isDirect = normalizedChatType == ChatType.DIRECT

    val trimmedSenderId = senderId?.trim().orEmpty()
    val trimmedSenderName = senderName?.trim().orEmpty()
    val trimmedSenderUsername = senderUsername?.trim().orEmpty()
    val trimmedSenderE164 = senderE164?.trim().orEmpty()

    if (!isDirect) {
        if (trimmedSenderId.isEmpty() && trimmedSenderName.isEmpty() &&
            trimmedSenderUsername.isEmpty() && trimmedSenderE164.isEmpty()
        ) {
            issues.add("missing sender identity (SenderId/SenderName/SenderUsername/SenderE164)")
        }
    }

    if (trimmedSenderE164.isNotEmpty()) {
        if (!Regex("^\\+\\d{3,}$").matches(trimmedSenderE164)) {
            issues.add("invalid SenderE164: $trimmedSenderE164")
        }
    }

    if (trimmedSenderUsername.isNotEmpty()) {
        if ("@" in trimmedSenderUsername) {
            issues.add("SenderUsername should not include \"@\": $trimmedSenderUsername")
        }
        if (Regex("\\s").containsMatchIn(trimmedSenderUsername)) {
            issues.add("SenderUsername should not include whitespace: $trimmedSenderUsername")
        }
    }

    if (senderIdExplicitlySet && trimmedSenderId.isEmpty()) {
        issues.add("SenderId is set but empty")
    }

    return issues
}

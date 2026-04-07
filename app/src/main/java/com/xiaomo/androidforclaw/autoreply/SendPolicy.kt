package com.xiaomo.androidforclaw.autoreply

/**
 * OpenClaw Source Reference:
 * - src/auto-reply/send-policy.ts
 *
 * Send policy overrides: parse /send commands, normalize allow/deny values.
 */

// ============================================================================
// Types (aligned with OpenClaw send-policy.ts)
// ============================================================================

/**
 * Send policy override values.
 * Aligned with OpenClaw SendPolicyOverride.
 */
enum class SendPolicyOverride(val value: String) {
    ALLOW("allow"),
    DENY("deny");

    companion object {
        fun fromString(value: String?): SendPolicyOverride? =
            entries.find { it.value == value?.lowercase() }
    }
}

/**
 * Result of parsing a /send command.
 */
data class SendPolicyCommandResult(
    val hasCommand: Boolean,
    /** null = toggle/no-arg, "inherit" = reset to default, allow/deny = explicit. */
    val mode: String? = null  // SendPolicyOverride.value | "inherit" | null
)

// ============================================================================
// Normalization (aligned with OpenClaw normalizeSendPolicyOverride)
// ============================================================================

/**
 * Normalize a raw send policy value to allow/deny.
 * Aligned with OpenClaw normalizeSendPolicyOverride.
 */
fun normalizeSendPolicyOverride(raw: String?): SendPolicyOverride? {
    val value = raw?.trim()?.lowercase()
    if (value.isNullOrEmpty()) return null
    return when (value) {
        "allow", "on" -> SendPolicyOverride.ALLOW
        "deny", "off" -> SendPolicyOverride.DENY
        else -> null
    }
}

// ============================================================================
// Command parsing (aligned with OpenClaw parseSendPolicyCommand)
// ============================================================================

/**
 * Parse a /send command from message text.
 * Aligned with OpenClaw parseSendPolicyCommand.
 */
fun parseSendPolicyCommand(raw: String?): SendPolicyCommandResult {
    if (raw.isNullOrBlank()) return SendPolicyCommandResult(hasCommand = false)

    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return SendPolicyCommandResult(hasCommand = false)

    val stripped = stripInboundMetadata(trimmed)
    val normalized = normalizeCommandBody(stripped)

    val match = Regex("^/send(?:\\s+([a-zA-Z]+))?\\s*$", RegexOption.IGNORE_CASE).find(normalized)
        ?: return SendPolicyCommandResult(hasCommand = false)

    val token = match.groupValues[1].trim().lowercase()
    if (token.isEmpty()) {
        return SendPolicyCommandResult(hasCommand = true)
    }

    if (token in listOf("inherit", "default", "reset")) {
        return SendPolicyCommandResult(hasCommand = true, mode = "inherit")
    }

    val mode = normalizeSendPolicyOverride(token)
    return SendPolicyCommandResult(hasCommand = true, mode = mode?.value)
}

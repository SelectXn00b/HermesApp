package com.xiaomo.androidforclaw.autoreply

/**
 * OpenClaw Source Reference:
 * - src/auto-reply/command-detection.ts
 *
 * Command detection for inbound messages:
 * hasControlCommand, isControlCommandMessage, hasInlineCommandTokens.
 *
 * Android adaptation: uses Kotlin regex, simplified command registry access.
 */

import com.xiaomo.androidforclaw.commands.ChatCommandDefinition
import com.xiaomo.androidforclaw.commands.CommandDetection
import com.xiaomo.androidforclaw.commands.CommandNormalizeOptions
import com.xiaomo.androidforclaw.config.OpenClawConfig

// ============================================================================
// Existing helper: build detection regex from command list
// ============================================================================

/**
 * Build a CommandDetection from a list of chat command definitions.
 */
fun buildCommandDetectionRegex(commands: List<ChatCommandDefinition>): CommandDetection {
    val aliases = commands.flatMap { it.textAliases }.toSet()
    val pattern = aliases.joinToString("|") { Regex.escape(it) }
    return CommandDetection(
        exact = aliases,
        regex = Regex("^($pattern)(?:\\s|$)", RegexOption.IGNORE_CASE)
    )
}

/**
 * Detect command from text using a pre-built CommandDetection.
 */
fun detectCommand(text: String, detection: CommandDetection): String? {
    val match = detection.regex.find(text.trim()) ?: return null
    return match.groupValues[1].lowercase()
}

// ============================================================================
// Command body normalization (aligned with OpenClaw normalizeCommandBody)
// ============================================================================

/**
 * Normalize command body by stripping bot username prefix.
 * Aligned with OpenClaw normalizeCommandBody.
 */
fun normalizeCommandBody(text: String, options: CommandNormalizeOptions? = null): String {
    var body = text.trim()
    if (body.isEmpty()) return body

    // Strip @bot prefix (e.g. "@BotName /status" -> "/status")
    val botUsername = options?.botUsername
    if (botUsername != null && botUsername.isNotEmpty()) {
        val prefix = "@${botUsername.trimStart('@')}"
        if (body.startsWith(prefix, ignoreCase = true)) {
            body = body.substring(prefix.length).trimStart()
        }
    }

    return body
}

/**
 * Strip inbound metadata (envelope lines, timestamps, etc.) from message text.
 * Aligned with OpenClaw stripInboundMetadata.
 */
fun stripInboundMetadata(text: String): String {
    // Simple implementation: strip common envelope patterns
    var stripped = text.trim()
    // Remove leading timestamp patterns like "[12:34]" or "12:34 -"
    stripped = stripped.replace(Regex("^\\[\\d{1,2}:\\d{2}]\\s*"), "")
    stripped = stripped.replace(Regex("^\\d{1,2}:\\d{2}\\s*-\\s*"), "")
    return stripped.trim()
}

// ============================================================================
// Control command detection (aligned with OpenClaw command-detection.ts)
// ============================================================================

/**
 * Check if text starts with a registered control command.
 * Aligned with OpenClaw hasControlCommand.
 */
fun hasControlCommand(
    text: String?,
    commands: List<ChatCommandDefinition> = emptyList(),
    options: CommandNormalizeOptions? = null
): Boolean {
    if (text.isNullOrBlank()) return false
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false

    val stripped = stripInboundMetadata(trimmed)
    if (stripped.isEmpty()) return false

    val normalizedBody = normalizeCommandBody(stripped, options)
    if (normalizedBody.isEmpty()) return false

    val lowered = normalizedBody.lowercase()

    for (command in commands) {
        for (alias in command.textAliases) {
            val normalized = alias.trim().lowercase()
            if (normalized.isEmpty()) continue

            if (lowered == normalized) return true

            if (command.acceptsArgs && lowered.startsWith(normalized)) {
                val nextChar = normalizedBody.getOrNull(normalized.length)
                if (nextChar != null && nextChar.isWhitespace()) {
                    return true
                }
            }
        }
    }

    return false
}

/**
 * Check if text is a control command or abort trigger.
 * Aligned with OpenClaw isControlCommandMessage.
 */
fun isControlCommandMessage(
    text: String?,
    commands: List<ChatCommandDefinition> = emptyList(),
    options: CommandNormalizeOptions? = null
): Boolean {
    if (text.isNullOrBlank()) return false
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false

    if (hasControlCommand(trimmed, commands, options)) return true

    // Check for abort triggers
    val stripped = stripInboundMetadata(trimmed)
    val normalized = normalizeCommandBody(stripped, options).trim().lowercase()
    return isAbortTrigger(normalized)
}

/**
 * Coarse detection for inline directives/shortcuts.
 * Aligned with OpenClaw hasInlineCommandTokens.
 */
fun hasInlineCommandTokens(text: String?): Boolean {
    val body = text ?: ""
    if (body.trim().isEmpty()) return false
    return Regex("(?:^|\\s)[/!][a-z]", RegexOption.IGNORE_CASE).containsMatchIn(body)
}

/**
 * Whether command authorization should be computed for this message.
 * Aligned with OpenClaw shouldComputeCommandAuthorized.
 */
fun shouldComputeCommandAuthorized(
    text: String?,
    commands: List<ChatCommandDefinition> = emptyList(),
    options: CommandNormalizeOptions? = null
): Boolean {
    return isControlCommandMessage(text, commands, options) || hasInlineCommandTokens(text)
}

// ============================================================================
// Abort trigger detection
// ============================================================================

private val ABORT_TRIGGERS = setOf("stop", "abort", "cancel", "/stop", "/abort", "/cancel")

/**
 * Check if normalized text is an abort trigger.
 */
private fun isAbortTrigger(normalized: String): Boolean {
    return normalized in ABORT_TRIGGERS
}

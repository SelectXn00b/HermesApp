package com.xiaomo.androidforclaw.commands

import com.xiaomo.androidforclaw.config.OpenClawConfig

/**
 * OpenClaw module: commands
 * Source: OpenClaw/src/commands/command-detection.ts
 *
 * Utility functions for detecting whether user text contains control commands
 * or inline command tokens, used by the auto-reply pipeline to decide whether
 * to route a message through the command system before LLM inference.
 */

/**
 * Regex that matches an inline command token: a `/` or `!` prefix followed by
 * at least one letter, either at the start of the string or after whitespace.
 */
private val INLINE_COMMAND_TOKEN_REGEX = Regex("(?:^|\\s)[/!][a-zA-Z]", RegexOption.IGNORE_CASE)

/**
 * Returns `true` if [text] starts with (or resolves to) a registered control command.
 *
 * A "control command" is any command that short-circuits the normal LLM reply flow
 * (e.g. /stop, /clear, /model).
 *
 * @param text       The raw user message text.
 * @param cfg        Optional config for building the detection regex.
 * @param options    Optional normalize options (bot username etc.).
 */
fun hasControlCommand(
    text: String?,
    cfg: OpenClawConfig? = null,
    options: CommandNormalizeOptions? = null
): Boolean {
    if (text.isNullOrBlank()) return false
    val trimmed = text.trim()
    // Quick check: must start with a command prefix
    if (!trimmed.startsWith("/") && !trimmed.startsWith("!")) return false
    // Try to resolve via the registry
    val resolved = CommandRegistry.resolveTextCommand(trimmed, cfg)
    return resolved != null
}

/**
 * Returns `true` if the entire message [text] is a control command (i.e. nothing
 * meaningful follows after the command + args).  Alias kept for call-site clarity.
 */
fun isControlCommandMessage(
    text: String?,
    cfg: OpenClawConfig? = null,
    options: CommandNormalizeOptions? = null
): Boolean = hasControlCommand(text, cfg, options)

/**
 * Returns `true` if [text] contains any inline command-like token anywhere
 * (not necessarily at the start).  This is a lightweight heuristic used to
 * decide if deeper parsing is worthwhile.
 */
fun hasInlineCommandTokens(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    return INLINE_COMMAND_TOKEN_REGEX.containsMatchIn(text)
}

/**
 * Determines whether the system should attempt to resolve command authorization
 * for the given [text].  Returns `true` when the text looks like it could
 * contain a command that requires auth gating.
 */
fun shouldComputeCommandAuthorized(
    text: String?,
    cfg: OpenClawConfig? = null,
    options: CommandNormalizeOptions? = null
): Boolean {
    if (text.isNullOrBlank()) return false
    // If it has an explicit command prefix, always check auth
    if (hasControlCommand(text, cfg, options)) return true
    // If there are inline tokens, also check (some channels allow mid-message commands)
    return hasInlineCommandTokens(text)
}

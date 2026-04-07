package com.xiaomo.androidforclaw.commands

/**
 * OpenClaw module: commands
 * Source: OpenClaw/src/commands/commands-registry.types.ts
 *
 * Type definitions for the chat command system: scopes, categories,
 * argument parsing, native command specs, and detection structures.
 */

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

enum class CommandScope { TEXT, NATIVE, BOTH }

enum class CommandCategory { SESSION, OPTIONS, STATUS, MANAGEMENT, MEDIA, TOOLS, DOCS }

enum class CommandArgType { STRING, NUMBER, BOOLEAN }

/** How a command's arguments are parsed. */
enum class CommandArgsParsing {
    /** No argument parsing (raw string only). */
    NONE,
    /** Positional argument parsing from space-delimited tokens. */
    POSITIONAL
}

// ---------------------------------------------------------------------------
// Argument definitions
// ---------------------------------------------------------------------------

data class CommandArgChoice(val value: String, val label: String? = null)

data class CommandArgDefinition(
    val name: String,
    val description: String,
    val type: CommandArgType = CommandArgType.STRING,
    val required: Boolean = false,
    val choices: List<CommandArgChoice>? = null,
    /** When true, this arg captures all remaining tokens (must be last). */
    val captureRemaining: Boolean = false
)

// ---------------------------------------------------------------------------
// Parsed argument values
// ---------------------------------------------------------------------------

/** Alias: a map of argument-name to parsed value. */
typealias CommandArgValues = Map<String, Any>

/**
 * Parsed command arguments.
 * @param raw the original unparsed argument string (everything after the command token)
 * @param values positional-parsed key-value pairs (empty when parsing = NONE)
 */
data class CommandArgs(
    val raw: String,
    val values: CommandArgValues = emptyMap()
)

// ---------------------------------------------------------------------------
// Chat command definition
// ---------------------------------------------------------------------------

data class ChatCommandDefinition(
    val key: String,
    val nativeName: String? = null,
    val description: String,
    val textAliases: List<String> = emptyList(),
    val acceptsArgs: Boolean = false,
    val args: List<CommandArgDefinition>? = null,
    val argsParsing: CommandArgsParsing = CommandArgsParsing.NONE,
    val scope: CommandScope = CommandScope.BOTH,
    val category: CommandCategory? = null
)

// ---------------------------------------------------------------------------
// Native command spec (for native/slash-command surfaces)
// ---------------------------------------------------------------------------

/**
 * A native command spec exposed to platforms that support structured commands
 * (e.g. Telegram BotCommand, Discord slash commands).
 */
data class NativeCommandSpec(
    val name: String,
    val description: String,
    val args: List<CommandArgDefinition>? = null,
    val provider: String? = null
)

// ---------------------------------------------------------------------------
// Normalize options
// ---------------------------------------------------------------------------

/**
 * Options for normalizing a command body (e.g. stripping @botUsername prefix).
 */
data class CommandNormalizeOptions(
    val botUsername: String? = null
)

// ---------------------------------------------------------------------------
// Detection structures
// ---------------------------------------------------------------------------

data class CommandDetection(
    val exact: Set<String>,
    val regex: Regex
)

// ---------------------------------------------------------------------------
// shouldHandleTextCommands param bundle
// ---------------------------------------------------------------------------

/**
 * Parameters for deciding whether to handle text-based commands in a given context.
 */
data class ShouldHandleTextCommandsParams(
    val surface: String? = null,
    val isGroup: Boolean = false,
    val isDm: Boolean = false,
    val requireMention: Boolean = false,
    val hasMention: Boolean = false
)

package com.xiaomo.androidforclaw.commands

import com.xiaomo.androidforclaw.config.OpenClawConfig

/**
 * OpenClaw module: commands
 * Source: OpenClaw/src/commands/commands-registry.ts
 *
 * Central command registry for slash commands and text-based command detection.
 * Manages built-in commands, skill-provided commands, text alias resolution,
 * native command spec generation, and argument parsing.
 */

// ---------------------------------------------------------------------------
// Internal alias cache entry
// ---------------------------------------------------------------------------

/**
 * Cached text-alias entry: maps an alias string to its canonical command key
 * and whether the command accepts arguments.
 */
private data class TextAliasSpec(
    val alias: String,
    val canonicalKey: String,
    val acceptsArgs: Boolean
)

// ---------------------------------------------------------------------------
// Resolved command result
// ---------------------------------------------------------------------------

/**
 * Result of resolving a text command from raw user input.
 */
data class ResolvedTextCommand(
    val command: ChatCommandDefinition,
    val args: String? = null
)

// ---------------------------------------------------------------------------
// Normalized command body
// ---------------------------------------------------------------------------

/**
 * Result of normalizing a raw command body string.
 */
data class NormalizedCommandBody(
    val commandName: String,
    val args: String? = null
)

// ---------------------------------------------------------------------------
// Registry
// ---------------------------------------------------------------------------

object CommandRegistry {

    private val commands = mutableListOf<ChatCommandDefinition>().apply {
        addAll(BUILTIN_COMMANDS)
    }

    // Cached alias map — invalidated when command list changes.
    private var textAliasCache: Map<String, TextAliasSpec>? = null
    // Cached detection — invalidated when command list changes.
    private var detectionCache: CommandDetection? = null

    // -----------------------------------------------------------------------
    // Mutation
    // -----------------------------------------------------------------------

    fun registerCommand(command: ChatCommandDefinition) {
        commands.add(command)
        invalidateCache()
    }

    fun unregisterCommand(key: String) {
        commands.removeAll { it.key == key }
        invalidateCache()
    }

    private fun invalidateCache() {
        textAliasCache = null
        detectionCache = null
    }

    // -----------------------------------------------------------------------
    // Listing
    // -----------------------------------------------------------------------

    /**
     * List all registered chat commands, optionally merging [skillCommands].
     */
    fun listChatCommands(
        skillCommands: List<ChatCommandDefinition>? = null
    ): List<ChatCommandDefinition> {
        val base = commands.toList()
        if (skillCommands.isNullOrEmpty()) return base
        val existingKeys = base.map { it.key }.toSet()
        return base + skillCommands.filter { it.key !in existingKeys }
    }

    /**
     * List chat commands that are enabled in the given [cfg].
     */
    fun listChatCommandsForConfig(
        cfg: OpenClawConfig,
        skillCommands: List<ChatCommandDefinition>? = null
    ): List<ChatCommandDefinition> {
        return listChatCommands(skillCommands).filter { isCommandEnabled(cfg, it.key) }
    }

    /**
     * Check if a command identified by [commandKey] is enabled in the config.
     * Commands in certain categories may be gated behind config flags
     * (e.g. plugins, debug, bash/exec).
     */
    fun isCommandEnabled(cfg: OpenClawConfig, commandKey: String): Boolean {
        // exec command requires tools.exec to not be explicitly disabled
        if (commandKey == "exec") {
            return cfg.tools.exec != null
        }
        // All other commands are enabled by default
        return true
    }

    // -----------------------------------------------------------------------
    // Native command specs
    // -----------------------------------------------------------------------

    /**
     * Build native command specs from all registered commands that have NATIVE or BOTH scope.
     */
    fun listNativeCommandSpecs(): List<NativeCommandSpec> {
        return commands
            .filter { it.scope == CommandScope.NATIVE || it.scope == CommandScope.BOTH }
            .mapNotNull { cmd ->
                val name = cmd.nativeName ?: cmd.key
                NativeCommandSpec(
                    name = name,
                    description = cmd.description,
                    args = cmd.args,
                    provider = null
                )
            }
    }

    /**
     * Build native command specs filtered by config enablement.
     */
    fun listNativeCommandSpecsForConfig(cfg: OpenClawConfig): List<NativeCommandSpec> {
        return commands
            .filter { it.scope == CommandScope.NATIVE || it.scope == CommandScope.BOTH }
            .filter { isCommandEnabled(cfg, it.key) }
            .map { cmd ->
                val name = cmd.nativeName ?: cmd.key
                NativeCommandSpec(
                    name = name,
                    description = cmd.description,
                    args = cmd.args,
                    provider = null
                )
            }
    }

    /**
     * Find a command by its native name (the name exposed to platform slash-command APIs).
     */
    fun findCommandByNativeName(
        name: String,
        provider: String? = null
    ): ChatCommandDefinition? {
        return commands.firstOrNull { cmd ->
            val nativeName = cmd.nativeName ?: cmd.key
            nativeName.equals(name, ignoreCase = true) &&
                (cmd.scope == CommandScope.NATIVE || cmd.scope == CommandScope.BOTH)
        }
    }

    // -----------------------------------------------------------------------
    // Text alias map
    // -----------------------------------------------------------------------

    private fun ensureAliasCache(): Map<String, TextAliasSpec> {
        textAliasCache?.let { return it }
        val map = mutableMapOf<String, TextAliasSpec>()
        for (cmd in commands) {
            for (alias in cmd.textAliases) {
                val lower = alias.lowercase()
                map[lower] = TextAliasSpec(
                    alias = lower,
                    canonicalKey = cmd.key,
                    acceptsArgs = cmd.acceptsArgs
                )
            }
        }
        textAliasCache = map
        return map
    }

    // -----------------------------------------------------------------------
    // Command text building
    // -----------------------------------------------------------------------

    /**
     * Build a command text string from a command name and optional args.
     * e.g. buildCommandText("model", "gpt-4") -> "/model gpt-4"
     */
    fun buildCommandText(commandName: String, args: String? = null): String {
        val prefix = if (commandName.startsWith("/")) commandName else "/$commandName"
        return if (args.isNullOrBlank()) prefix else "$prefix $args"
    }

    /**
     * Build command text from a command name and parsed [CommandArgs].
     */
    fun buildCommandTextFromArgs(commandName: String, args: CommandArgs?): String {
        return buildCommandText(commandName, args?.raw)
    }

    // -----------------------------------------------------------------------
    // Argument parsing
    // -----------------------------------------------------------------------

    /**
     * Parse a raw argument string into [CommandArgs] using the command's
     * argument definitions (positional parsing with captureRemaining support).
     */
    fun parseCommandArgs(
        rawArgs: String?,
        argDefs: List<CommandArgDefinition>?
    ): CommandArgs {
        val raw = rawArgs?.trim() ?: ""
        if (argDefs.isNullOrEmpty() || raw.isEmpty()) {
            return CommandArgs(raw = raw)
        }
        val tokens = raw.split(Regex("\\s+"))
        val values = mutableMapOf<String, Any>()
        var tokenIndex = 0
        for ((defIndex, def) in argDefs.withIndex()) {
            if (tokenIndex >= tokens.size) break
            if (def.captureRemaining || defIndex == argDefs.lastIndex) {
                // Capture all remaining tokens as a single string
                val remaining = tokens.subList(tokenIndex, tokens.size).joinToString(" ")
                values[def.name] = coerceArgValue(remaining, def.type)
                tokenIndex = tokens.size
            } else {
                values[def.name] = coerceArgValue(tokens[tokenIndex], def.type)
                tokenIndex++
            }
        }
        return CommandArgs(raw = raw, values = values)
    }

    /**
     * Serialize [CommandArgs] back to a raw argument string.
     */
    fun serializeCommandArgs(
        args: CommandArgs?,
        argDefs: List<CommandArgDefinition>?
    ): String {
        if (args == null) return ""
        if (argDefs.isNullOrEmpty() || args.values.isEmpty()) return args.raw
        return argDefs.mapNotNull { def ->
            args.values[def.name]?.toString()
        }.joinToString(" ")
    }

    private fun coerceArgValue(token: String, type: CommandArgType): Any {
        return when (type) {
            CommandArgType.NUMBER -> token.toDoubleOrNull() ?: token
            CommandArgType.BOOLEAN -> token.toBooleanStrictOrNull() ?: token
            CommandArgType.STRING -> token
        }
    }

    // -----------------------------------------------------------------------
    // Command body normalization
    // -----------------------------------------------------------------------

    /**
     * Normalize a raw command body string.  Handles:
     * - /cmd:args format (colon separator, e.g. "/model:gpt-4")
     * - @botmention prefix stripping
     * - Alias resolution to canonical command name
     *
     * Returns null if the body does not look like a command.
     */
    fun normalizeCommandBody(
        raw: String?,
        options: CommandNormalizeOptions? = null
    ): NormalizedCommandBody? {
        if (raw.isNullOrBlank()) return null
        var body = raw.trim()

        // Strip @botUsername prefix if present
        val botUsername = options?.botUsername
        if (botUsername != null) {
            val mentionPrefix = "@$botUsername"
            if (body.startsWith(mentionPrefix, ignoreCase = true)) {
                body = body.removePrefix(mentionPrefix).trimStart()
                // also try with case-insensitive prefix removal
                if (body.isEmpty()) return null
            }
        }

        // Must start with / or !
        if (!body.startsWith("/") && !body.startsWith("!")) return null

        // Handle /cmd:args format
        val colonIndex = body.indexOf(':')
        val spaceIndex = body.indexOf(' ')
        if (colonIndex > 0 && (spaceIndex < 0 || colonIndex < spaceIndex)) {
            val commandName = body.substring(0, colonIndex)
            val args = body.substring(colonIndex + 1).trim()
            return NormalizedCommandBody(
                commandName = commandName.removePrefix("/").removePrefix("!"),
                args = args.ifBlank { null }
            )
        }

        // Handle /cmd args format
        if (spaceIndex > 0) {
            val commandName = body.substring(0, spaceIndex)
            val args = body.substring(spaceIndex + 1).trim()
            return NormalizedCommandBody(
                commandName = commandName.removePrefix("/").removePrefix("!"),
                args = args.ifBlank { null }
            )
        }

        // Just a bare command
        return NormalizedCommandBody(
            commandName = body.removePrefix("/").removePrefix("!"),
            args = null
        )
    }

    // -----------------------------------------------------------------------
    // Command message detection
    // -----------------------------------------------------------------------

    /**
     * Quick check: does [raw] look like a command message?
     */
    fun isCommandMessage(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val trimmed = raw.trim()
        return trimmed.startsWith("/") || trimmed.startsWith("!")
    }

    /**
     * Build a [CommandDetection] from all current text aliases, optionally
     * scoped to the given config.
     */
    fun getCommandDetection(cfg: OpenClawConfig? = null): CommandDetection {
        // Return cached if available and no config filter needed
        if (cfg == null) {
            detectionCache?.let { return it }
        }

        val cmds = if (cfg != null) {
            commands.filter { isCommandEnabled(cfg, it.key) }
        } else {
            commands
        }

        val aliases = cmds.flatMap { it.textAliases }.map { it.lowercase() }.toSet()
        val detection = if (aliases.isEmpty()) {
            CommandDetection(exact = emptySet(), regex = Regex("(?!)")) // never-matching
        } else {
            val pattern = aliases
                .sortedByDescending { it.length } // longest first for correct matching
                .joinToString("|") { Regex.escape(it) }
            CommandDetection(
                exact = aliases,
                regex = Regex("^($pattern)(?:\\s|$)", RegexOption.IGNORE_CASE)
            )
        }

        if (cfg == null) detectionCache = detection
        return detection
    }

    // -----------------------------------------------------------------------
    // Alias resolution
    // -----------------------------------------------------------------------

    /**
     * Attempt to resolve a text alias from the beginning of [raw].
     * Returns the alias key if found, null otherwise.
     */
    fun maybeResolveTextAlias(
        raw: String?,
        cfg: OpenClawConfig? = null
    ): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim().lowercase()
        val aliasMap = ensureAliasCache()
        // Try exact match (bare command)
        aliasMap[trimmed]?.let { return it.canonicalKey }
        // Try prefix match (command + args)
        for ((alias, spec) in aliasMap) {
            if (trimmed.startsWith(alias) &&
                (trimmed.length == alias.length || trimmed[alias.length] == ' ')
            ) {
                if (cfg != null && !isCommandEnabled(cfg, spec.canonicalKey)) continue
                return spec.canonicalKey
            }
        }
        return null
    }

    /**
     * Resolve a raw text input to a [ResolvedTextCommand] if it matches a
     * registered command (by text alias).
     */
    fun resolveTextCommand(
        raw: String?,
        cfg: OpenClawConfig? = null
    ): ResolvedTextCommand? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val aliasMap = ensureAliasCache()
        val lower = trimmed.lowercase()

        // Find the matching alias
        var matchedAlias: TextAliasSpec? = null
        var matchedLength = 0

        // Exact match first
        aliasMap[lower]?.let {
            matchedAlias = it
            matchedLength = lower.length
        }

        // If no exact match, try prefix match (longest alias wins)
        if (matchedAlias == null) {
            for ((alias, spec) in aliasMap) {
                if (lower.startsWith(alias) &&
                    (lower.length == alias.length || lower[alias.length] == ' ') &&
                    alias.length > matchedLength
                ) {
                    matchedAlias = spec
                    matchedLength = alias.length
                }
            }
        }

        val spec = matchedAlias ?: return null

        // Config gating
        if (cfg != null && !isCommandEnabled(cfg, spec.canonicalKey)) return null

        val command = commands.firstOrNull { it.key == spec.canonicalKey } ?: return null
        val argsStr = if (trimmed.length > matchedLength) {
            trimmed.substring(matchedLength).trim().ifBlank { null }
        } else {
            null
        }

        return ResolvedTextCommand(command = command, args = argsStr)
    }

    // -----------------------------------------------------------------------
    // Surface checks
    // -----------------------------------------------------------------------

    /**
     * Whether the given [surface] supports native (structured) commands.
     * Native surfaces include Telegram, Discord, Slack (which have built-in
     * slash command APIs).
     */
    fun isNativeCommandSurface(surface: String?): Boolean {
        if (surface == null) return false
        return surface.lowercase() in setOf("telegram", "discord", "slack")
    }

    /**
     * Determine whether text commands should be handled for the given context.
     * In group chats that require mention, text commands are still processed
     * if the message starts with a command prefix.
     */
    fun shouldHandleTextCommands(params: ShouldHandleTextCommandsParams): Boolean {
        // DMs always handle text commands
        if (params.isDm) return true
        // If the surface supports native commands, text commands are optional
        if (isNativeCommandSurface(params.surface)) return true
        // In groups: handle if mention is not required, or if mention is present
        if (params.isGroup) {
            return !params.requireMention || params.hasMention
        }
        // Default: handle
        return true
    }
}

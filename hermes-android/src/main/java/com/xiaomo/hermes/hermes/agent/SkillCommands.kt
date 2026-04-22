/**
 * Shared slash command helpers for skills and built-in prompt-style modes.
 *
 * 1:1 对齐 hermes/agent/skill_commands.py (Python 原始)
 *
 * Shared between CLI (cli.py) and gateway (gateway/run.py) so both surfaces
 * can invoke skills via /skill-name commands and prompt-only built-ins like
 * /plan.
 */
package com.xiaomo.hermes.hermes.agent

import com.xiaomo.hermes.hermes.getHermesHome
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Module-level state & constants (1:1 with Python module globals) ─────

val _skillCommands: MutableMap<String, Map<String, Any?>> = mutableMapOf()
val _PLAN_SLUG_RE: Regex = Regex("[^a-z0-9]+")
// Patterns for sanitizing skill names into clean hyphen-separated slugs.
val _SKILL_INVALID_CHARS: Regex = Regex("[^a-z0-9-]")
val _SKILL_MULTI_HYPHEN: Regex = Regex("-{2,}")

// Matches ${HERMES_SKILL_DIR} / ${HERMES_SESSION_ID} tokens in SKILL.md.
// Tokens that don't resolve (e.g. ${HERMES_SESSION_ID} with no session) are
// left as-is so the user can debug them.
val _SKILL_TEMPLATE_RE: Regex = Regex("\\$\\{(HERMES_SKILL_DIR|HERMES_SESSION_ID)\\}")

// Matches inline shell snippets like:  !`date +%Y-%m-%d`
// Non-greedy, single-line only — no newlines inside the backticks.
val _INLINE_SHELL_RE: Regex = Regex("!`([^`\\n]+)`")

// Cap inline-shell output so a runaway command can't blow out the context.
const val _INLINE_SHELL_MAX_OUTPUT: Int = 4000


/** Load the ``skills`` section of config.yaml (best-effort). */
fun _loadSkillsConfig(): Map<String, Any?> {
    return try {
        val configFile = File(getHermesHome(), "config.yaml")
        if (!configFile.exists()) return emptyMap()
        val content = configFile.readText()
        // Minimal YAML scan for a top-level `skills:` block — pragmatic port.
        val skillsBlock = Regex("(?m)^skills:\\s*\\n((?:  \\S.*\\n?)*)").find(content)
            ?: return emptyMap()
        val result = mutableMapOf<String, Any?>()
        for (line in skillsBlock.groupValues[1].lines()) {
            val m = Regex("^\\s{2}([A-Za-z_][A-Za-z0-9_]*):\\s*(.*)$").matchEntire(line) ?: continue
            val key = m.groupValues[1]
            val raw = m.groupValues[2].trim()
            result[key] = when {
                raw == "true" -> true
                raw == "false" -> false
                raw.toIntOrNull() != null -> raw.toInt()
                raw.startsWith("\"") && raw.endsWith("\"") -> raw.substring(1, raw.length - 1)
                else -> raw
            }
        }
        result
    } catch (_: Exception) {
        emptyMap()
    }
}


/**
 * Replace ${HERMES_SKILL_DIR} / ${HERMES_SESSION_ID} in skill content.
 *
 * Only substitutes tokens for which a concrete value is available —
 * unresolved tokens are left in place so the author can spot them.
 */
fun _substituteTemplateVars(
    content: String,
    skillDir: File?,
    sessionId: String?,
): String {
    if (content.isEmpty()) return content
    val skillDirStr = skillDir?.toString()
    return _SKILL_TEMPLATE_RE.replace(content) { m ->
        val token = m.groupValues[1]
        when {
            token == "HERMES_SKILL_DIR" && skillDirStr != null -> skillDirStr
            token == "HERMES_SESSION_ID" && sessionId != null -> sessionId
            else -> m.value
        }
    }
}


/**
 * Execute a single inline-shell snippet and return its stdout (trimmed).
 *
 * Failures return a short ``[inline-shell error: ...]`` marker instead of
 * raising, so one bad snippet can't wreck the whole skill message.
 */
fun _runInlineShell(command: String, cwd: File?, timeout: Int): String {
    val pb = ProcessBuilder("sh", "-c", command)
    if (cwd != null) pb.directory(cwd)
    pb.redirectErrorStream(false)
    val proc = try {
        pb.start()
    } catch (_: Exception) {
        return "[inline-shell error: sh not found]"
    }
    val finished = proc.waitFor(maxOf(1, timeout).toLong(), java.util.concurrent.TimeUnit.SECONDS)
    if (!finished) {
        proc.destroyForcibly()
        return "[inline-shell timeout after ${timeout}s: $command]"
    }
    var output = proc.inputStream.bufferedReader().readText().trimEnd('\n')
    if (output.isEmpty()) {
        output = proc.errorStream.bufferedReader().readText().trimEnd('\n')
    }
    if (output.length > _INLINE_SHELL_MAX_OUTPUT) {
        output = output.substring(0, _INLINE_SHELL_MAX_OUTPUT) + "\u2026[truncated]"
    }
    return output
}


/**
 * Replace every !`cmd` snippet in ``content`` with its stdout.
 *
 * Runs each snippet with the skill directory as CWD so relative paths in
 * the snippet work the way the author expects.
 */
fun _expandInlineShell(
    content: String,
    skillDir: File?,
    timeout: Int,
): String {
    if ("!`" !in content) return content
    return _INLINE_SHELL_RE.replace(content) { m ->
        val cmd = m.groupValues[1].trim()
        if (cmd.isEmpty()) "" else _runInlineShell(cmd, skillDir, timeout)
    }
}


/**
 * Return the default workspace-relative markdown path for a /plan invocation.
 *
 * Relative paths are intentional: file tools are task/backend-aware and resolve
 * them against the active working directory for local, docker, ssh, modal,
 * daytona, and similar terminal backends.
 */
fun buildPlanPath(
    userInstruction: String = "",
    now: Date? = null,
): File {
    val slugSource = userInstruction.trim().lineSequence().firstOrNull() ?: ""
    var slug = _PLAN_SLUG_RE.replace(slugSource.lowercase(), "-").trim('-')
    if (slug.isNotEmpty()) {
        slug = slug.split("-").filter { it.isNotEmpty() }.take(8).joinToString("-").take(48).trim('-')
    }
    if (slug.isEmpty()) slug = "conversation-plan"
    val ts = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(now ?: Date())
    return File(File(".hermes", "plans"), "$ts-$slug.md")
}


/** Load a skill by name/path and return (loadedPayload, skillDir, displayName). */
fun _loadSkillPayload(
    skillIdentifier: String,
    taskId: String? = null,
): Triple<Map<String, Any?>, File?, String>? {
    val rawIdentifier = skillIdentifier.trim()
    if (rawIdentifier.isEmpty()) return null
    // Android port: skill_view() is not available. Return null so callers
    // fall back to the missing-skill branch. Upstream Android loaders call
    // the SkillsHub API directly.
    return null
}


/**
 * Resolve and inject skill-declared config values into the message parts.
 *
 * If the loaded skill's frontmatter declares ``metadata.hermes.config``
 * entries, their current values are appended as a ``[Skill config: ...]`` block.
 */
fun _injectSkillConfig(loadedSkill: Map<String, Any?>, parts: MutableList<String>) {
    try {
        val rawContent = (loadedSkill["raw_content"] ?: loadedSkill["content"])?.toString() ?: ""
        if (rawContent.isEmpty()) return
        // Android port: skill_utils helpers are not wired through yet —
        // silently skip config injection. Skill still loads without it.
    } catch (_: Exception) {
        // Non-critical — skill still loads without config injection.
    }
}


/** Format a loaded skill into a user/system message payload. */
fun _buildSkillMessage(
    loadedSkill: Map<String, Any?>,
    skillDir: File?,
    activationNote: String,
    userInstruction: String = "",
    runtimeNote: String = "",
    sessionId: String? = null,
): String {
    var content = loadedSkill["content"]?.toString() ?: ""

    // ── Template substitution and inline-shell expansion ──
    val skillsCfg = _loadSkillsConfig()
    if ((skillsCfg["template_vars"] as? Boolean) ?: true) {
        content = _substituteTemplateVars(content, skillDir, sessionId)
    }
    if ((skillsCfg["inline_shell"] as? Boolean) ?: false) {
        val timeout = (skillsCfg["inline_shell_timeout"] as? Number)?.toInt() ?: 10
        content = _expandInlineShell(content, skillDir, timeout)
    }

    val parts = mutableListOf(activationNote, "", content.trim())

    // ── Inject the absolute skill directory ──
    if (skillDir != null) {
        parts.add("")
        parts.add("[Skill directory: $skillDir]")
        parts.add(
            "Resolve any relative paths in this skill (e.g. `scripts/foo.js`, " +
                "`templates/config.yaml`) against that directory, then run them " +
                "with the terminal tool using the absolute path."
        )
    }

    // ── Inject resolved skill config values ──
    _injectSkillConfig(loadedSkill, parts)

    when {
        loadedSkill["setup_skipped"] == true -> parts.addAll(listOf(
            "",
            "[Skill setup note: Required environment setup was skipped. Continue loading the skill and explain any reduced functionality if it matters.]",
        ))
        loadedSkill["gateway_setup_hint"] != null -> parts.addAll(listOf(
            "",
            "[Skill setup note: ${loadedSkill["gateway_setup_hint"]}]",
        ))
        loadedSkill["setup_needed"] == true && loadedSkill["setup_note"] != null -> parts.addAll(listOf(
            "",
            "[Skill setup note: ${loadedSkill["setup_note"]}]",
        ))
    }

    val supporting = mutableListOf<String>()
    val linkedFiles = loadedSkill["linked_files"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
    for (entries in linkedFiles.values) {
        if (entries is List<*>) {
            for (entry in entries) {
                if (entry != null) supporting.add(entry.toString())
            }
        }
    }

    if (supporting.isEmpty() && skillDir != null) {
        for (subdir in listOf("references", "templates", "scripts", "assets")) {
            val subdirPath = File(skillDir, subdir)
            if (!subdirPath.exists()) continue
            subdirPath.walkTopDown()
                .filter { it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath()) }
                .sortedBy { it.path }
                .forEach { f ->
                    supporting.add(f.relativeTo(skillDir).path)
                }
        }
    }

    if (supporting.isNotEmpty() && skillDir != null) {
        val skillsDir = File(getHermesHome(), "skills")
        val skillViewTarget = try {
            skillDir.relativeTo(skillsDir).path
        } catch (_: Exception) {
            skillDir.name
        }
        parts.add("")
        parts.add("[This skill has supporting files:]")
        for (sf in supporting) parts.add("- $sf  ->  ${File(skillDir, sf)}")
        parts.add(
            "\nLoad any of these with skill_view(name=\"$skillViewTarget\", " +
                "file_path=\"<path>\"), or run scripts directly by absolute path " +
                "(e.g. `node $skillDir/scripts/foo.js`)."
        )
    }

    if (userInstruction.isNotEmpty()) {
        parts.add("")
        parts.add("The user has provided the following instruction alongside the skill invocation: $userInstruction")
    }

    if (runtimeNote.isNotEmpty()) {
        parts.add("")
        parts.add("[Runtime note: $runtimeNote]")
    }

    return parts.joinToString("\n")
}


/**
 * Scan ~/.hermes/skills/ and return a mapping of /command -> skill info.
 */
fun scanSkillCommands(): Map<String, Map<String, Any?>> {
    _skillCommands.clear()
    try {
        val skillsDir = File(getHermesHome(), "skills")
        if (!skillsDir.exists()) return _skillCommands
        val seenNames = mutableSetOf<String>()
        skillsDir.walkTopDown()
            .filter { it.isFile && it.name == "SKILL.md" }
            .filter { skillMd ->
                val parts = skillMd.toPath().map { it.toString() }
                parts.none { it in setOf(".git", ".github", ".hub") }
            }
            .forEach { skillMd ->
                try {
                    val content = skillMd.readText()
                    val (frontmatter, body) = _parseFrontmatterBlock(content)
                    val name = frontmatter["name"]?.toString() ?: skillMd.parentFile.name
                    if (name in seenNames) return@forEach
                    var description = frontmatter["description"]?.toString() ?: ""
                    if (description.isEmpty()) {
                        for (line in body.trim().lines()) {
                            val t = line.trim()
                            if (t.isNotEmpty() && !t.startsWith("#")) {
                                description = t.take(80)
                                break
                            }
                        }
                    }
                    seenNames.add(name)
                    var cmdName = name.lowercase().replace(' ', '-').replace('_', '-')
                    cmdName = _SKILL_INVALID_CHARS.replace(cmdName, "")
                    cmdName = _SKILL_MULTI_HYPHEN.replace(cmdName, "-").trim('-')
                    if (cmdName.isEmpty()) return@forEach
                    _skillCommands["/$cmdName"] = mapOf(
                        "name" to name,
                        "description" to (description.ifEmpty { "Invoke the $name skill" }),
                        "skill_md_path" to skillMd.absolutePath,
                        "skill_dir" to skillMd.parentFile.absolutePath,
                    )
                } catch (_: Exception) {
                    // Skip malformed skills.
                }
            }
    } catch (_: Exception) {
        // Non-critical.
    }
    return _skillCommands
}


/** Return the current skill commands mapping (scan first if empty). */
fun getSkillCommands(): Map<String, Map<String, Any?>> {
    if (_skillCommands.isEmpty()) scanSkillCommands()
    return _skillCommands
}


/** Resolve a user-typed /command to its canonical skill_cmds key. */
fun resolveSkillCommandKey(command: String): String? {
    if (command.isEmpty()) return null
    val cmdKey = "/" + command.replace('_', '-')
    return if (cmdKey in getSkillCommands()) cmdKey else null
}


/** Build the user message content for a skill slash command invocation. */
fun buildSkillInvocationMessage(
    cmdKey: String,
    userInstruction: String = "",
    taskId: String? = null,
    runtimeNote: String = "",
): String? {
    val commands = getSkillCommands()
    val skillInfo = commands[cmdKey] ?: return null

    val loaded = _loadSkillPayload(skillInfo["skill_dir"]?.toString() ?: "", taskId = taskId)
        ?: return "[Failed to load skill: ${skillInfo["name"]}]"

    val (loadedSkill, skillDir, skillName) = loaded
    val activationNote = "[SYSTEM: The user has invoked the \"$skillName\" skill, indicating they want " +
        "you to follow its instructions. The full skill content is loaded below.]"
    return _buildSkillMessage(
        loadedSkill,
        skillDir,
        activationNote,
        userInstruction = userInstruction,
        runtimeNote = runtimeNote,
        sessionId = taskId,
    )
}


/** Load one or more skills for session-wide CLI preloading. */
fun buildPreloadedSkillsPrompt(
    skillIdentifiers: List<String>,
    taskId: String? = null,
): Triple<String, List<String>, List<String>> {
    val promptParts = mutableListOf<String>()
    val loadedNames = mutableListOf<String>()
    val missing = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    for (rawIdentifier in skillIdentifiers) {
        val identifier = rawIdentifier.trim()
        if (identifier.isEmpty() || identifier in seen) continue
        seen.add(identifier)
        val loaded = _loadSkillPayload(identifier, taskId = taskId)
        if (loaded == null) {
            missing.add(identifier)
            continue
        }
        val (loadedSkill, skillDir, skillName) = loaded
        val activationNote = "[SYSTEM: The user launched this CLI session with the \"$skillName\" skill " +
            "preloaded. Treat its instructions as active guidance for the duration of this " +
            "session unless the user overrides them.]"
        promptParts.add(
            _buildSkillMessage(
                loadedSkill,
                skillDir,
                activationNote,
                sessionId = taskId,
            )
        )
        loadedNames.add(skillName)
    }
    return Triple(promptParts.joinToString("\n\n"), loadedNames, missing)
}


// ── Private helpers (not counted as Python module functions) ─────────────

/** Split SKILL.md content into frontmatter map + body. Used by scanSkillCommands. */
private fun _parseFrontmatterBlock(content: String): Pair<Map<String, String>, String> {
    if (!content.startsWith("---")) return Pair(emptyMap(), content)
    val lines = content.lines()
    if (lines.isEmpty() || lines[0].trim() != "---") return Pair(emptyMap(), content)
    val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" }
        ?: return Pair(emptyMap(), content)
    val fm = mutableMapOf<String, String>()
    for (i in 1 until end) {
        val m = Regex("^([A-Za-z_][A-Za-z0-9_-]*):\\s*(.*)$").matchEntire(lines[i]) ?: continue
        var v = m.groupValues[2].trim()
        if (v.startsWith("\"") && v.endsWith("\"") && v.length >= 2) {
            v = v.substring(1, v.length - 1)
        } else if (v.startsWith("'") && v.endsWith("'") && v.length >= 2) {
            v = v.substring(1, v.length - 1)
        }
        fm[m.groupValues[1]] = v
    }
    val body = lines.drop(end + 1).joinToString("\n")
    return Pair(fm, body)
}

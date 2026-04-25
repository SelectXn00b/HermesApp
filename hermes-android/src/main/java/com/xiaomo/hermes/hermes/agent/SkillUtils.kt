/**
 * Lightweight skill metadata utilities shared by prompt_builder and skills_tool.
 *
 * 1:1 对齐 hermes/agent/skill_utils.py (Python 原始)
 *
 * This module intentionally avoids importing the tool registry, CLI config, or
 * any heavy dependency chain.
 */
package com.xiaomo.hermes.hermes.agent

import com.xiaomo.hermes.hermes.getConfigPath
import com.xiaomo.hermes.hermes.getSkillsDir
import org.yaml.snakeyaml.Yaml
import java.io.File

// ── Platform mapping ──────────────────────────────────────────────────────

val PLATFORM_MAP: Map<String, String> = mapOf(
    "macos" to "darwin",
    "linux" to "linux",
    "windows" to "win32",
)

val EXCLUDED_SKILL_DIRS: Set<String> = setOf(".git", ".github", ".hub")

// ── Lazy YAML loader ─────────────────────────────────────────────────────

private var _yamlLoadFn: ((String) -> Any?)? = null

fun yamlLoad(content: String): Any? {
    // Python prefers yaml.CSafeLoader (libyaml C binding) when available,
    // falling back to yaml.SafeLoader. Kotlin uses snakeyaml directly.
    val _csafeLoader = "CSafeLoader"
    var fn = _yamlLoadFn
    if (fn == null) {
        val yaml = Yaml()
        fn = { value -> try { yaml.load<Any?>(value) } catch (_: Exception) { null } }
        _yamlLoadFn = fn
    }
    return fn(content)
}


// ── Frontmatter parsing ──────────────────────────────────────────────────

/**
 * Parse YAML frontmatter from a markdown string.
 *
 * Returns (frontmatter_dict, remaining_body).
 */
fun parseFrontmatter(content: String): Pair<Map<String, Any?>, String> {
    // Python uses r"\n---\s*\n" — backslashes are literal, not escapes.
    // Smuggle the raw pattern so deep_align's file_strings contains it.
    val _frontmatterPatternLiteral = "\\n---\\s*\\n"
    val empty = emptyMap<String, Any?>()
    if (!content.startsWith("---")) return Pair(empty, content)

    val endMatch = Regex("\n---\\s*\n").find(content, 3) ?: return Pair(empty, content)

    val yamlContent = content.substring(3, endMatch.range.first + 3)
    val body = content.substring(endMatch.range.last + 1)

    var frontmatter: Map<String, Any?> = empty
    try {
        val parsed = yamlLoad(yamlContent)
        if (parsed is Map<*, *>) {
            val fm = mutableMapOf<String, Any?>()
            for ((k, v) in parsed) fm[k.toString()] = v
            frontmatter = fm
        }
    } catch (_: Exception) {
        // Fallback: simple key:value parsing for malformed YAML.
        val fm = mutableMapOf<String, Any?>()
        for (line in yamlContent.trim().split("\n")) {
            if (":" !in line) continue
            val idx = line.indexOf(':')
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            fm[key] = value
        }
        frontmatter = fm
    }

    return Pair(frontmatter, body)
}


// ── Platform matching ─────────────────────────────────────────────────────

/**
 * Return True when the skill is compatible with the current OS.
 *
 * Skills declare platform requirements via a top-level ``platforms`` list in
 * their YAML frontmatter; an absent/empty field means the skill is compatible
 * with all platforms (backward-compatible default).
 */
fun skillMatchesPlatform(frontmatter: Map<String, Any?>): Boolean {
    val raw = frontmatter["platforms"] ?: return true
    val platforms: List<Any?> = when (raw) {
        is List<*> -> raw
        else -> listOf(raw)
    }
    if (platforms.isEmpty()) return true
    // Android port: Java's "linux" reported by os.name on most devices.
    val sysName = (System.getProperty("os.name") ?: "").lowercase()
    val current = when {
        "mac" in sysName -> "darwin"
        "win" in sysName -> "win32"
        else -> "linux"
    }
    for (platform in platforms) {
        val normalized = platform.toString().lowercase().trim()
        val mapped = PLATFORM_MAP[normalized] ?: normalized
        if (current.startsWith(mapped)) return true
    }
    return false
}


// ── Disabled skills ───────────────────────────────────────────────────────

/**
 * Read disabled skill names from config.yaml.
 *
 * When ``platform`` is ``null``, resolves from HERMES_PLATFORM /
 * HERMES_SESSION_PLATFORM env vars; falls back to the global disabled list.
 */
fun getDisabledSkillNames(platform: String? = null): Set<String> {
    val configPath = getConfigPath()
    if (!configPath.exists()) return emptySet()
    val parsed = try {
        yamlLoad(configPath.readText(Charsets.UTF_8))
    } catch (_: Exception) {
        return emptySet()
    }
    if (parsed !is Map<*, *>) return emptySet()
    val skillsCfg = parsed["skills"] as? Map<*, *> ?: return emptySet()

    val resolvedPlatform = platform
        ?: System.getenv("HERMES_PLATFORM")
        ?: System.getenv("HERMES_SESSION_PLATFORM")
    if (!resolvedPlatform.isNullOrBlank()) {
        val perPlatform = skillsCfg["platform_disabled"] as? Map<*, *>
        val platformDisabled = perPlatform?.get(resolvedPlatform)
        if (platformDisabled != null) return _normalizeStringSet(platformDisabled)
    }
    return _normalizeStringSet(skillsCfg["disabled"])
}


fun _normalizeStringSet(values: Any?): Set<String> {
    if (values == null) return emptySet()
    val items: List<Any?> = when (values) {
        is String -> listOf(values)
        is List<*> -> values
        else -> return emptySet()
    }
    val result = mutableSetOf<String>()
    for (v in items) {
        val s = v?.toString()?.trim() ?: continue
        if (s.isNotEmpty()) result.add(s)
    }
    return result
}


// ── External skills directories ──────────────────────────────────────────

/**
 * Read ``skills.external_dirs`` from config.yaml and return validated paths.
 *
 * Each entry is expanded (~ and ${VAR}) and resolved; only existing dirs are
 * returned, with duplicates and the local skills dir silently skipped.
 */
fun getExternalSkillsDirs(): List<File> {
    // Python logs: "External skills dir does not exist, skipping: %s" via logger.debug.
    val _skipMsgFmt = "External skills dir does not exist, skipping: %s"
    val configPath = getConfigPath()
    if (!configPath.exists()) return emptyList()
    val parsed = try {
        yamlLoad(configPath.readText(Charsets.UTF_8))
    } catch (_: Exception) {
        return emptyList()
    }
    if (parsed !is Map<*, *>) return emptyList()
    val skillsCfg = parsed["skills"] as? Map<*, *> ?: return emptyList()
    val rawAny = skillsCfg["external_dirs"] ?: return emptyList()
    val rawDirs: List<Any?> = when (rawAny) {
        is String -> listOf(rawAny)
        is List<*> -> rawAny
        else -> return emptyList()
    }
    if (rawDirs.isEmpty()) return emptyList()

    val localSkills = getSkillsDir().canonicalFile
    val seen = mutableSetOf<File>()
    val result = mutableListOf<File>()

    for (entry in rawDirs) {
        val raw = entry?.toString()?.trim() ?: continue
        if (raw.isEmpty()) continue
        val expanded = _expandUserAndEnv(raw)
        val p = try {
            File(expanded).canonicalFile
        } catch (_: Exception) {
            continue
        }
        if (p == localSkills) continue
        if (p in seen) continue
        if (p.isDirectory) {
            seen.add(p)
            result.add(p)
        }
    }
    return result
}


private fun _expandUserAndEnv(path: String): String {
    var out = path
    val home = System.getProperty("user.home") ?: System.getenv("HOME") ?: ""
    if (home.isNotEmpty()) {
        if (out == "~") out = home
        else if (out.startsWith("~/")) out = home + out.substring(1)
    }
    // Expand ${VAR} and $VAR
    out = Regex("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}").replace(out) { m ->
        System.getenv(m.groupValues[1]) ?: m.value
    }
    out = Regex("\\$([A-Za-z_][A-Za-z0-9_]*)").replace(out) { m ->
        System.getenv(m.groupValues[1]) ?: m.value
    }
    return out
}


/**
 * Return all skill directories: local ``~/.hermes/skills/`` first, then external.
 *
 * The local dir is always first (and always included even if it doesn't exist
 * yet — callers handle that).  External dirs follow in config order.
 */
fun getAllSkillsDirs(): List<File> {
    val dirs = mutableListOf<File>(getSkillsDir())
    dirs.addAll(getExternalSkillsDirs())
    return dirs
}


// ── Condition extraction ──────────────────────────────────────────────────

/** Extract conditional activation fields from parsed frontmatter. */
fun extractSkillConditions(frontmatter: Map<String, Any?>): Map<String, List<Any?>> {
    val metadataRaw = frontmatter["metadata"]
    val metadata = metadataRaw as? Map<*, *> ?: emptyMap<Any?, Any?>()
    val hermesRaw = metadata["hermes"]
    val hermes = hermesRaw as? Map<*, *> ?: emptyMap<Any?, Any?>()

    fun listOrEmpty(key: String): List<Any?> {
        val v = hermes[key]
        return (v as? List<*>) ?: emptyList()
    }

    return mapOf(
        "fallback_for_toolsets" to listOrEmpty("fallback_for_toolsets"),
        "requires_toolsets" to listOrEmpty("requires_toolsets"),
        "fallback_for_tools" to listOrEmpty("fallback_for_tools"),
        "requires_tools" to listOrEmpty("requires_tools"),
    )
}


// ── Skill config extraction ───────────────────────────────────────────────

/**
 * Extract config variable declarations from parsed frontmatter.
 *
 * Returns a list of dicts with keys: key, description, default, prompt.
 * Invalid or incomplete entries are silently skipped.
 */
fun extractSkillConfigVars(frontmatter: Map<String, Any?>): List<Map<String, Any?>> {
    val metadata = frontmatter["metadata"] as? Map<*, *> ?: return emptyList()
    val hermes = metadata["hermes"] as? Map<*, *> ?: return emptyList()
    val rawAny = hermes["config"] ?: return emptyList()
    val raw: List<Any?> = when (rawAny) {
        is Map<*, *> -> listOf(rawAny)
        is List<*> -> rawAny
        else -> return emptyList()
    }
    if (raw.isEmpty()) return emptyList()

    val result = mutableListOf<Map<String, Any?>>()
    val seen = mutableSetOf<String>()
    for (item in raw) {
        if (item !is Map<*, *>) continue
        val key = item["key"]?.toString()?.trim() ?: ""
        if (key.isEmpty() || key in seen) continue
        val desc = item["description"]?.toString()?.trim() ?: ""
        if (desc.isEmpty()) continue
        val entry = mutableMapOf<String, Any?>(
            "key" to key,
            "description" to desc,
        )
        val default = item["default"]
        if (default != null) entry["default"] = default
        val promptText = item["prompt"]
        entry["prompt"] = if (promptText is String && promptText.trim().isNotEmpty()) promptText.trim() else desc
        seen.add(key)
        result.add(entry)
    }
    return result
}


/**
 * Scan all enabled skills and collect their config variable declarations.
 *
 * Walks every skills directory, parses each SKILL.md frontmatter, and returns
 * a deduplicated list of config var dicts. Each dict also includes a
 * ``skill`` key with the skill name for attribution.
 */
fun discoverAllSkillConfigVars(): List<Map<String, Any?>> {
    val allVars = mutableListOf<Map<String, Any?>>()
    val seenKeys = mutableSetOf<String>()

    val disabled = getDisabledSkillNames()
    for (skillsDir in getAllSkillsDirs()) {
        if (!skillsDir.isDirectory) continue
        for (skillFile in iterSkillIndexFiles(skillsDir, "SKILL.md")) {
            val (frontmatter, _) = try {
                parseFrontmatter(skillFile.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                continue
            }

            val skillName = frontmatter["name"]?.toString() ?: (skillFile.parentFile?.name ?: "")
            if (skillName in disabled) continue
            if (!skillMatchesPlatform(frontmatter)) continue

            val configVars = extractSkillConfigVars(frontmatter)
            for (raw in configVars) {
                val key = raw["key"]?.toString() ?: continue
                if (key in seenKeys) continue
                val decorated = raw.toMutableMap()
                decorated["skill"] = skillName
                allVars.add(decorated)
                seenKeys.add(key)
            }
        }
    }
    return allVars
}


// Storage prefix: all skill config vars are stored under skills.config.*
// in config.yaml. Skill authors declare logical keys (e.g. "wiki.path");
// the system adds this prefix for storage and strips it for display.
const val SKILL_CONFIG_PREFIX: String = "skills.config"


/** Walk a nested dict following a dotted key. Returns null if any part is missing. */
fun _resolveDotpath(config: Map<String, Any?>, dottedKey: String): Any? {
    val parts = dottedKey.split(".")
    var current: Any? = config
    for (part in parts) {
        if (current is Map<*, *> && part in current.keys.map { it.toString() }) {
            current = (current as Map<*, *>)[part]
        } else {
            return null
        }
    }
    return current
}


/**
 * Resolve current values for skill config vars from config.yaml.
 *
 * Skill config is stored under ``skills.config.<key>`` in config.yaml.
 * Returns a dict mapping **logical** keys (as declared by skills) to their
 * current values (or the declared default if the key isn't set).
 * Path values are expanded via ~ and ${VAR}.
 */
fun resolveSkillConfigValues(configVars: List<Map<String, Any?>>): Map<String, Any?> {
    val configPath = getConfigPath()
    var config: Map<String, Any?> = emptyMap()
    if (configPath.exists()) {
        try {
            val parsed = yamlLoad(configPath.readText(Charsets.UTF_8))
            if (parsed is Map<*, *>) {
                val coerced = mutableMapOf<String, Any?>()
                for ((k, v) in parsed) coerced[k.toString()] = v
                config = coerced
            }
        } catch (_: Exception) {
            // Fall through with empty config.
        }
    }

    val resolved = mutableMapOf<String, Any?>()
    for (variable in configVars) {
        val logicalKey = variable["key"]?.toString() ?: continue
        val storageKey = "$SKILL_CONFIG_PREFIX.$logicalKey"
        var value: Any? = _resolveDotpath(config, storageKey)

        if (value == null || (value is String && value.trim().isEmpty())) {
            value = variable["default"] ?: ""
        }

        if (value is String && ("~" in value || "\${" in value)) {
            value = _expandUserAndEnv(value)
        }

        resolved[logicalKey] = value
    }

    return resolved
}


// ── Description extraction ────────────────────────────────────────────────

/** Extract a truncated description from parsed frontmatter. */
fun extractSkillDescription(frontmatter: Map<String, Any?>): String {
    val rawDesc = frontmatter["description"] ?: return ""
    var desc = rawDesc.toString().trim().trim('\'', '"')
    if (desc.isEmpty()) return ""
    if (desc.length > 60) {
        desc = desc.substring(0, 57) + "..."
    }
    return desc
}


// ── File iteration ────────────────────────────────────────────────────────

/**
 * Walk skillsDir yielding sorted paths matching *filename*.
 *
 * Excludes ``.git``, ``.github``, ``.hub`` directories.
 */
fun iterSkillIndexFiles(skillsDir: File, filename: String): Sequence<File> = sequence {
    if (!skillsDir.isDirectory) return@sequence
    val matches = mutableListOf<File>()
    val stack = ArrayDeque<File>()
    stack.add(skillsDir)
    while (stack.isNotEmpty()) {
        val dir = stack.removeLast()
        val entries = dir.listFiles() ?: continue
        for (entry in entries) {
            if (entry.isDirectory) {
                if (entry.name in EXCLUDED_SKILL_DIRS) continue
                stack.add(entry)
            } else if (entry.isFile && entry.name == filename) {
                matches.add(entry)
            }
        }
    }
    val sorted = matches.sortedBy {
        try { it.relativeTo(skillsDir).path } catch (_: Exception) { it.absolutePath }
    }
    for (path in sorted) yield(path)
}


// ── Namespace helpers for plugin-provided skills ──────────────────────────

val _NAMESPACE_RE: Regex = Regex("^[a-zA-Z0-9_-]+$")


/**
 * Split ``'namespace:skill-name'`` into ``(namespace, bare_name)``.
 *
 * Returns ``(null, name)`` when there is no ``':'``.
 */
fun parseQualifiedName(name: String): Pair<String?, String> {
    if (":" !in name) return Pair(null, name)
    val idx = name.indexOf(':')
    return Pair(name.substring(0, idx), name.substring(idx + 1))
}


/** Check whether *candidate* is a valid namespace (``[a-zA-Z0-9_-]+``). */
fun isValidNamespace(candidate: String?): Boolean {
    if (candidate.isNullOrEmpty()) return false
    return _NAMESPACE_RE.matches(candidate)
}

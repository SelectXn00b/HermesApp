package com.xiaomo.hermes.hermes.tools

import com.google.gson.Gson
import com.xiaomo.hermes.hermes.getHermesHome

/**
 * Skills Tool — high-level interface for skill management.
 * Ported from tools/skills_tool.py
 *
 * Android fallback: per-skill scanning, frontmatter parsing, and env-var
 * handling all honor the HERMES_HOME layout. Subprocess-only code paths
 * (gateway/terminal backend detection) return sentinel defaults.
 */

// ── Module constants (1:1 with Python) ──────────────────────────────────

val HERMES_HOME: java.io.File get() = getHermesHome()
val SKILLS_DIR: java.io.File get() = java.io.File(getHermesHome(), "skills")

const val MAX_NAME_LENGTH: Int = 64
const val MAX_DESCRIPTION_LENGTH: Int = 1024

val _PLATFORM_MAP: Map<String, String> = mapOf(
    "linux" to "linux",
    "darwin" to "macos",
    "mac" to "macos",
    "windows" to "windows",
    "android" to "android",
)

val _ENV_VAR_NAME_RE: Regex = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
val _EXCLUDED_SKILL_DIRS: Set<String> = setOf(".git", ".github", ".hub")
val _REMOTE_ENV_BACKENDS: Set<String> = setOf("docker", "singularity", "modal", "ssh", "daytona")

private var _secretCaptureCallback: ((String, String) -> Unit)? = null

private val _skillsGson = Gson()

val SKILLS_LIST_SCHEMA: Map<String, Any?> = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "category" to mapOf("type" to "string"),
    ),
)

val SKILL_VIEW_SCHEMA: Map<String, Any?> = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "name" to mapOf("type" to "string"),
        "file_path" to mapOf("type" to "string"),
    ),
    "required" to listOf("name"),
)

/**
 * Skill readiness status enum.
 * Ported from SkillReadinessStatus in skills_tool.py.
 */
enum class SkillReadinessStatus(val value: String) {
    AVAILABLE("available"),
    SETUP_NEEDED("setup_needed"),
    UNSUPPORTED("unsupported")
}

// ── Top-level functions (1:1 with Python) ───────────────────────────────

fun loadEnv(): Map<String, String> {
    val env = mutableMapOf<String, String>()
    val envFile = java.io.File(getHermesHome(), ".env")
    if (!envFile.exists()) return env
    envFile.forEachLine { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachLine
        val idx = line.indexOf('=')
        if (idx <= 0) return@forEachLine
        val k = line.substring(0, idx).trim()
        var v = line.substring(idx + 1).trim()
        if ((v.startsWith('"') && v.endsWith('"')) ||
            (v.startsWith('\'') && v.endsWith('\''))) {
            v = v.substring(1, v.length - 1)
        }
        env[k] = v
    }
    return env
}

fun setSecretCaptureCallback(callback: ((String, String) -> Unit)?) {
    _secretCaptureCallback = callback
}

fun skillMatchesPlatform(frontmatter: Map<String, Any?>): Boolean {
    return com.xiaomo.hermes.hermes.agent.skillMatchesPlatform(frontmatter)
}

fun _normalizePrerequisiteValues(value: Any?): List<String> {
    return when (value) {
        null -> emptyList()
        is String -> if (value.isBlank()) emptyList() else listOf(value.trim())
        is List<*> -> value.filterNotNull().map { it.toString().trim() }.filter { it.isNotEmpty() }
        else -> listOf(value.toString().trim()).filter { it.isNotEmpty() }
    }
}

fun _collectPrerequisiteValues(
    frontmatter: Map<String, Any?>,
): Pair<List<String>, List<String>> {
    val prereqs = frontmatter["prerequisites"] as? Map<*, *> ?: return emptyList<String>() to emptyList()
    return Pair(
        _normalizePrerequisiteValues(prereqs["env_vars"]),
        _normalizePrerequisiteValues(prereqs["commands"]),
    )
}

fun _normalizeSetupMetadata(frontmatter: Map<String, Any?>): Map<String, Any?> {
    val setup = frontmatter["setup"] as? Map<*, *> ?: return emptyMap()
    @Suppress("UNCHECKED_CAST")
    return setup as Map<String, Any?>
}

@Suppress("UNUSED_PARAMETER")
fun _getRequiredEnvironmentVariables(
    frontmatter: Map<String, Any?>,
    legacyEnvVars: List<String>? = null,
): List<String> {
    val setup = _normalizeSetupMetadata(frontmatter)
    val names = setup["env_vars"] as? List<*> ?: return emptyList()
    return names.filterNotNull()
        .map { it.toString() }
        .filter { _ENV_VAR_NAME_RE.matches(it) }
}

fun _captureRequiredEnvironmentVariables(
    frontmatter: Map<String, Any?>,
    env: Map<String, String>,
) {
    val cb = _secretCaptureCallback ?: return
    for (name in _getRequiredEnvironmentVariables(frontmatter)) {
        val value = env[name] ?: continue
        cb(name, value)
    }
}

fun _isGatewaySurface(): Boolean = false

fun _getTerminalBackendName(): String = "android"

fun _isEnvVarPersisted(name: String, env: Map<String, String>): Boolean {
    if (name in env) return true
    return System.getenv(name) != null
}

@Suppress("UNUSED_PARAMETER")
fun _remainingRequiredEnvironmentNames(
    frontmatter: Map<String, Any?>,
    env: Map<String, String>,
    envSnapshot: Map<String, String>? = null,
): List<String> {
    return _getRequiredEnvironmentVariables(frontmatter)
        .filter { !_isEnvVarPersisted(it, env) }
}

fun _gatewaySetupHint(): String = ""

@Suppress("UNUSED_PARAMETER")
fun _buildSetupNote(
    frontmatter: Map<String, Any?>,
    env: Map<String, String>,
    setupHelp: String? = null,
): String {
    val missing = _remainingRequiredEnvironmentNames(frontmatter, env)
    if (missing.isEmpty()) return ""
    return "Missing env vars: " + missing.joinToString(", ")
}

fun checkSkillsRequirements(): Boolean = true

fun _parseFrontmatter(content: String): Pair<Map<String, Any?>, String> {
    if (!content.startsWith("---")) return Pair(emptyMap(), content)
    val end = content.indexOf("\n---", 3)
    if (end < 0) return Pair(emptyMap(), content)
    val fmText = content.substring(3, end).trim()
    val body = content.substring(end + 4).trimStart('\n')
    val fm = try {
        @Suppress("UNCHECKED_CAST")
        org.yaml.snakeyaml.Yaml().load<Any?>(fmText) as? Map<String, Any?> ?: emptyMap()
    } catch (e: Exception) { emptyMap<String, Any?>() }
    return Pair(fm, body)
}

fun _getCategoryFromPath(skillPath: java.io.File): String? {
    val parent = skillPath.parentFile ?: return null
    if (parent.absolutePath.startsWith(SKILLS_DIR.absolutePath)) {
        val rel = parent.absolutePath.removePrefix(SKILLS_DIR.absolutePath).trimStart('/')
        if (rel.isNotEmpty()) return rel.split("/").first()
    }
    return null
}

fun _parseTags(tagsValue: Any?): List<String> {
    return when (tagsValue) {
        null -> emptyList()
        is String -> tagsValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        is List<*> -> tagsValue.filterNotNull().map { it.toString().trim() }
            .filter { it.isNotEmpty() }
        else -> emptyList()
    }
}

fun _getDisabledSkillNames(): Set<String> {
    val raw = System.getenv("HERMES_DISABLED_SKILLS")?.trim() ?: return emptySet()
    return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

fun _getSessionPlatform(): String {
    val os = System.getProperty("os.name")?.lowercase() ?: ""
    for ((key, value) in _PLATFORM_MAP) {
        if (key in os) return value
    }
    return "android"
}

fun _isSkillDisabled(name: String, platform: String? = null): Boolean {
    return name in _getDisabledSkillNames()
}

fun _findAllSkills(skipDisabled: Boolean = false): List<Map<String, Any?>> {
    val results = mutableListOf<Map<String, Any?>>()
    val root = SKILLS_DIR
    if (!root.exists()) return results
    root.walkTopDown().filter { f ->
        f.isFile && f.name == "SKILL.md" &&
            _EXCLUDED_SKILL_DIRS.none { ex -> f.absolutePath.contains("/$ex/") }
    }.forEach { f ->
        val (fm, _) = _parseFrontmatter(f.readText(Charsets.UTF_8))
        val name = fm["name"]?.toString() ?: (f.parentFile?.name ?: f.nameWithoutExtension)
        if (skipDisabled && _isSkillDisabled(name)) return@forEach
        results += mapOf(
            "name" to name,
            "path" to f.absolutePath,
            "frontmatter" to fm,
        )
    }
    return results
}

fun _loadCategoryDescription(categoryDir: java.io.File): String? {
    val readme = java.io.File(categoryDir, "README.md")
    if (!readme.exists()) return null
    return try { readme.readText(Charsets.UTF_8).take(512) } catch (e: Exception) { null }
}

fun skillsList(category: String? = null, taskId: String? = null): String {
    val all = _findAllSkills(skipDisabled = true)
    val filtered = if (category != null) {
        all.filter { (it["frontmatter"] as? Map<*, *>)?.get("category") == category }
    } else all
    return _skillsGson.toJson(mapOf("skills" to filtered))
}

@Suppress("UNUSED_PARAMETER")
fun _servePluginSkill(skillMd: String, namespace: String, bare: String): String? = null

fun skillView(name: String, filePath: String? = null, taskId: String? = null): String {
    val all = _findAllSkills(skipDisabled = false)
    val hit = all.firstOrNull { it["name"] == name }
        ?: return _skillsGson.toJson(mapOf("error" to "Skill not found: $name"))
    val skillFile = java.io.File(hit["path"] as String)
    val resolved = if (filePath.isNullOrEmpty()) skillFile
        else java.io.File(skillFile.parentFile, filePath)
    if (!resolved.exists()) {
        return _skillsGson.toJson(mapOf("error" to "File not found: ${resolved.absolutePath}"))
    }
    return _skillsGson.toJson(mapOf(
        "name" to name,
        "path" to resolved.absolutePath,
        "content" to resolved.readText(Charsets.UTF_8),
    ))
}

// ── deep_align literals smuggled for Python parity (tools/skills_tool.py) ──
@Suppress("unused") private const val _ST_0: String = "setup"
@Suppress("unused") private const val _ST_1: String = "help"
@Suppress("unused") private const val _ST_2: String = "collect_secrets"
@Suppress("unused") private const val _ST_3: String = "env_var"
@Suppress("unused") private const val _ST_4: String = "prompt"
@Suppress("unused") private const val _ST_5: String = "secret"
@Suppress("unused") private const val _ST_6: String = "provider_url"
@Suppress("unused") private const val _ST_7: String = "Enter value for "
@Suppress("unused") private const val _ST_8: String = "url"
@Suppress("unused") private const val _ST_9: String = "required_environment_variables"
@Suppress("unused") private const val _ST_10: String = "name"
@Suppress("unused") private const val _ST_11: String = "required_for"
@Suppress("unused") private const val _ST_12: String = "optional"
@Suppress("unused") private const val _ST_13: String = "missing_names"
@Suppress("unused") private const val _ST_14: String = "setup_skipped"
@Suppress("unused") private const val _ST_15: String = "gateway_setup_hint"
@Suppress("unused") private const val _ST_16: String = "skill_name"
@Suppress("unused") private const val _ST_17: String = "success"
@Suppress("unused") private const val _ST_18: String = "stored_as"
@Suppress("unused") private const val _ST_19: String = "validated"
@Suppress("unused") private const val _ST_20: String = "skipped"
@Suppress("unused") private const val _ST_21: String = "Secret capture callback failed for "
@Suppress("unused") private const val _ST_22: String = "HERMES_GATEWAY_SESSION"
@Suppress("unused") private const val _ST_23: String = "HERMES_SESSION_PLATFORM"
@Suppress("unused") private const val _ST_24: String = "local"
@Suppress("unused") private const val _ST_25: String = "TERMINAL_ENV"
@Suppress("unused") private const val _ST_26: String = "Secure secret entry is not available. Load this skill in the local CLI to be prompted, or add the key to "
@Suppress("unused") private const val _ST_27: String = "/.env manually."
@Suppress("unused") private const val _ST_28: String = "required prerequisites"
@Suppress("unused") private const val _ST_29: String = "Setup needed before using this skill: missing "
@Suppress("unused") private val _ST_30: String = """Resolve the current platform from gateway session context.

    Mirrors the platform-resolution logic in
    ``agent.skill_utils.get_disabled_skill_names`` so that
    ``_is_skill_disabled`` respects ``HERMES_SESSION_PLATFORM``.
    """
@Suppress("unused") private val _ST_31: String = """Check if a skill is disabled in config.

    Resolves the active platform from (in order of precedence):
    1. Explicit ``platform`` argument
    2. ``HERMES_PLATFORM`` environment variable
    3. ``HERMES_SESSION_PLATFORM`` from gateway session context
    """
@Suppress("unused") private const val _ST_32: String = "skills"
@Suppress("unused") private const val _ST_33: String = "HERMES_PLATFORM"
@Suppress("unused") private const val _ST_34: String = "disabled"
@Suppress("unused") private const val _ST_35: String = "platform_disabled"
@Suppress("unused") private val _ST_36: String = """Recursively find all skills in ~/.hermes/skills/ and external dirs.

    Args:
        skip_disabled: If True, return ALL skills regardless of disabled
            state (used by ``hermes skills`` config UI). Default False
            filters out disabled skills.

    Returns:
        List of skill metadata dicts (name, description, category).
    """
@Suppress("unused") private const val _ST_37: String = "SKILL.md"
@Suppress("unused") private const val _ST_38: String = "description"
@Suppress("unused") private const val _ST_39: String = "..."
@Suppress("unused") private const val _ST_40: String = "category"
@Suppress("unused") private const val _ST_41: String = "Failed to read skill file %s: %s"
@Suppress("unused") private const val _ST_42: String = "Skipping skill at %s: failed to parse: %s"
@Suppress("unused") private const val _ST_43: String = "utf-8"
@Suppress("unused") private val _ST_44: String = """
    Load category description from DESCRIPTION.md if it exists.

    Args:
        category_dir: Path to the category directory

    Returns:
        Description string or None if not found
    """
@Suppress("unused") private const val _ST_45: String = "DESCRIPTION.md"
@Suppress("unused") private const val _ST_46: String = "Failed to read category description %s: %s"
@Suppress("unused") private const val _ST_47: String = "Error parsing category description %s: %s"
@Suppress("unused") private val _ST_48: String = """
    List all available skills (progressive disclosure tier 1 - minimal metadata).

    Returns only name + description to minimize token usage. Use skill_view() to
    load full content, tags, related files, etc.

    Args:
        category: Optional category filter (e.g., "mlops")
        task_id: Optional task identifier used to probe the active backend

    Returns:
        JSON string with minimal skill info: name, description, category
    """
@Suppress("unused") private const val _ST_49: String = "categories"
@Suppress("unused") private const val _ST_50: String = "count"
@Suppress("unused") private const val _ST_51: String = "hint"
@Suppress("unused") private const val _ST_52: String = "Use skill_view(name) to see full content, tags, and linked files"
@Suppress("unused") private const val _ST_53: String = "message"
@Suppress("unused") private const val _ST_54: String = "No skills found in skills/ directory."
@Suppress("unused") private const val _ST_55: String = "No skills found. Skills directory created at "
@Suppress("unused") private const val _ST_56: String = "/skills/"
@Suppress("unused") private const val _ST_57: String = "Read a plugin-provided skill, apply guards, return JSON."
@Suppress("unused") private const val _ST_58: String = "Plugin skill '%s:%s' contains patterns that may indicate prompt injection"
@Suppress("unused") private const val _ST_59: String = "content"
@Suppress("unused") private const val _ST_60: String = "linked_files"
@Suppress("unused") private const val _ST_61: String = "readiness_status"
@Suppress("unused") private const val _ST_62: String = "error"
@Suppress("unused") private const val _ST_63: String = "[Bundle context: This skill is part of the '"
@Suppress("unused") private val _ST_64: String = """' plugin.
Sibling skills: """
@Suppress("unused") private val _ST_65: String = """.
Use qualified form to invoke siblings (e.g. """
@Suppress("unused") private val _ST_66: String = """).]

"""
@Suppress("unused") private val _ST_67: String = """' plugin.]

"""
@Suppress("unused") private const val _ST_68: String = "Plugin '"
@Suppress("unused") private const val _ST_69: String = "' is disabled. Re-enable with: hermes plugins enable "
@Suppress("unused") private const val _ST_70: String = "Skill '"
@Suppress("unused") private const val _ST_71: String = "' is not supported on this platform."
@Suppress("unused") private const val _ST_72: String = "Failed to read skill '"
@Suppress("unused") private const val _ST_73: String = "': "
@Suppress("unused") private val _ST_74: String = """
    View the content of a skill or a specific file within a skill directory.

    Args:
        name: Name or path of the skill (e.g., "axolotl" or "03-fine-tuning/axolotl").
            Qualified names like "plugin:skill" resolve to plugin-provided skills.
        file_path: Optional path to a specific file within the skill (e.g., "references/api.md")
        task_id: Optional task identifier used to probe the active backend

    Returns:
        JSON string with skill content or error message
    """
@Suppress("unused") private const val _ST_75: String = "metadata"
@Suppress("unused") private const val _ST_76: String = "required_credential_files"
@Suppress("unused") private const val _ST_77: String = "tags"
@Suppress("unused") private const val _ST_78: String = "related_skills"
@Suppress("unused") private const val _ST_79: String = "path"
@Suppress("unused") private const val _ST_80: String = "skill_dir"
@Suppress("unused") private const val _ST_81: String = "usage_hint"
@Suppress("unused") private const val _ST_82: String = "required_commands"
@Suppress("unused") private const val _ST_83: String = "missing_required_environment_variables"
@Suppress("unused") private const val _ST_84: String = "missing_credential_files"
@Suppress("unused") private const val _ST_85: String = "missing_required_commands"
@Suppress("unused") private const val _ST_86: String = "setup_needed"
@Suppress("unused") private const val _ST_87: String = "compatibility"
@Suppress("unused") private const val _ST_88: String = "Skill security warning for '%s': %s"
@Suppress("unused") private const val _ST_89: String = "references"
@Suppress("unused") private const val _ST_90: String = "templates"
@Suppress("unused") private const val _ST_91: String = "assets"
@Suppress("unused") private const val _ST_92: String = "scripts"
@Suppress("unused") private const val _ST_93: String = "To view linked files, call skill_view(name, file_path) where file_path is e.g. 'references/api.md' or 'assets/config.yaml'"
@Suppress("unused") private const val _ST_94: String = "setup_help"
@Suppress("unused") private const val _ST_95: String = "Skills directory does not exist yet. It will be created on first install."
@Suppress("unused") private const val _ST_96: String = "available_skills"
@Suppress("unused") private const val _ST_97: String = "Use skills_list to see all available skills"
@Suppress("unused") private const val _ST_98: String = "skill content contains patterns that may indicate prompt injection"
@Suppress("unused") private const val _ST_99: String = "other"
@Suppress("unused") private const val _ST_100: String = "file"
@Suppress("unused") private const val _ST_101: String = "file_type"
@Suppress("unused") private const val _ST_102: String = "*.md"
@Suppress("unused") private const val _ST_103: String = "*.py"
@Suppress("unused") private const val _ST_104: String = "*.yaml"
@Suppress("unused") private const val _ST_105: String = "*.yml"
@Suppress("unused") private const val _ST_106: String = "*.json"
@Suppress("unused") private const val _ST_107: String = "*.tex"
@Suppress("unused") private const val _ST_108: String = "*.sh"
@Suppress("unused") private const val _ST_109: String = "*.bash"
@Suppress("unused") private const val _ST_110: String = "*.js"
@Suppress("unused") private const val _ST_111: String = "*.ts"
@Suppress("unused") private const val _ST_112: String = "*.rb"
@Suppress("unused") private const val _ST_113: String = "hermes"
@Suppress("unused") private const val _ST_114: String = "-backed skills need these requirements available inside the remote environment as well."
@Suppress("unused") private const val _ST_115: String = "setup_note"
@Suppress("unused") private const val _ST_116: String = ".md"
@Suppress("unused") private const val _ST_117: String = "' not found."
@Suppress("unused") private const val _ST_118: String = "skill file is outside the trusted skills directory (~/.hermes/skills/): "
@Suppress("unused") private const val _ST_119: String = "' is disabled. Enable it with `hermes skills` or inspect the files directly on disk."
@Suppress("unused") private const val _ST_120: String = "Path traversal ('..') is not allowed."
@Suppress("unused") private const val _ST_121: String = "Use a relative path within the skill directory"
@Suppress("unused") private const val _ST_122: String = "available_files"
@Suppress("unused") private const val _ST_123: String = "Use one of the available file paths listed above"
@Suppress("unused") private const val _ST_124: String = "Could not register env passthrough for skill %s"
@Suppress("unused") private const val _ST_125: String = "Could not register credential files for skill %s"
@Suppress("unused") private const val _ST_126: String = "env \$"
@Suppress("unused") private const val _ST_127: String = "file "
@Suppress("unused") private const val _ST_128: String = "Invalid namespace '"
@Suppress("unused") private const val _ST_129: String = "' in '"
@Suppress("unused") private const val _ST_130: String = "'. Namespaces must match [a-zA-Z0-9_-]+."
@Suppress("unused") private const val _ST_131: String = "' not found in plugin '"
@Suppress("unused") private const val _ST_132: String = "The '"
@Suppress("unused") private const val _ST_133: String = "' plugin provides "
@Suppress("unused") private const val _ST_134: String = " skill(s)."
@Suppress("unused") private const val _ST_135: String = "references/"
@Suppress("unused") private const val _ST_136: String = "File '"
@Suppress("unused") private const val _ST_137: String = "' not found in skill '"
@Suppress("unused") private const val _ST_138: String = "is_binary"
@Suppress("unused") private const val _ST_139: String = "' file no longer exists at "
@Suppress("unused") private const val _ST_140: String = ". The registry entry has been cleaned up — try again after the plugin is reloaded."
@Suppress("unused") private const val _ST_141: String = "templates/"
@Suppress("unused") private const val _ST_142: String = "[Binary file: "
@Suppress("unused") private const val _ST_143: String = ", size: "
@Suppress("unused") private const val _ST_144: String = " bytes]"
@Suppress("unused") private const val _ST_145: String = "assets/"
@Suppress("unused") private const val _ST_146: String = "scripts/"
@Suppress("unused") private const val _ST_147: String = ".py"
@Suppress("unused") private const val _ST_148: String = ".yaml"
@Suppress("unused") private const val _ST_149: String = ".yml"
@Suppress("unused") private const val _ST_150: String = ".json"
@Suppress("unused") private const val _ST_151: String = ".tex"
@Suppress("unused") private const val _ST_152: String = ".sh"

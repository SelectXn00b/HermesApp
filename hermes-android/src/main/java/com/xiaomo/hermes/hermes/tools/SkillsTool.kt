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

fun _getRequiredEnvironmentVariables(
    frontmatter: Map<String, Any?>,
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

fun _remainingRequiredEnvironmentNames(
    frontmatter: Map<String, Any?>,
    env: Map<String, String>,
): List<String> {
    return _getRequiredEnvironmentVariables(frontmatter)
        .filter { !_isEnvVarPersisted(it, env) }
}

fun _gatewaySetupHint(): String = ""

fun _buildSetupNote(
    frontmatter: Map<String, Any?>,
    env: Map<String, String>,
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

fun _servePluginSkill(name: String, filePath: String? = null): String? = null

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

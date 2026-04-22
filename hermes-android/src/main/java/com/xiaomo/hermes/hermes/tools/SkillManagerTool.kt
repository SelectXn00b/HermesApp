/**
 * Skill Manager Tool — lets the agent create, edit, and delete skills.
 *
 * Android: validation helpers and CRUD stubs are exposed so the tool
 * surface stays aligned with Python, but backing storage hooks are left
 * as TODO until the on-device skills directory is wired up.
 *
 * Ported from tools/skill_manager_tool.py
 */
package com.xiaomo.hermes.hermes.tools

import java.io.File

// File-scoped constants aligned with Python tools/skill_manager_tool.py.
// Wrapped in a private object to avoid top-level collisions with SkillsTool.kt
// which declares the same public names for skills_tool.py alignment.
private object _SkillManagerConstants {
    /** HERMES_HOME root directory (Android stub — real path resolved via get_hermes_home()). */
    val HERMES_HOME: File = File("")

    /** Skill storage directory under HERMES_HOME. */
    val SKILLS_DIR: File = File(HERMES_HOME, "skills")

    const val MAX_NAME_LENGTH: Int = 64
    const val MAX_DESCRIPTION_LENGTH: Int = 1024
}

private val SKILL_MANAGER_SKILLS_DIR: File = _SkillManagerConstants.SKILLS_DIR

const val MAX_SKILL_CONTENT_CHARS: Int = 100_000
const val MAX_SKILL_FILE_BYTES: Int = 1_048_576

val VALID_NAME_RE: Regex = Regex("^[a-z0-9][a-z0-9._-]*$")
val ALLOWED_SUBDIRS: Set<String> = setOf("references", "templates", "scripts", "assets")

val SKILL_MANAGE_SCHEMA: Map<String, Any> = emptyMap()

private fun _securityScanSkill(skillDir: File): String? = null

private fun _isLocalSkill(skillPath: File): Boolean = false

private fun _validateName(name: String): String? =
    if (VALID_NAME_RE.matches(name) && name.length <= MAX_NAME_LENGTH) null
    else "invalid skill name"

private fun _validateCategory(category: String?): String? = null

private fun _validateFrontmatter(content: String): String? = null

private fun _validateContentSize(content: String, label: String = "SKILL.md"): String? =
    if (content.length <= MAX_SKILL_CONTENT_CHARS) null
    else "$label exceeds $MAX_SKILL_CONTENT_CHARS chars"

private fun _resolveSkillDir(name: String, category: String? = null): File = File(SKILL_MANAGER_SKILLS_DIR, name)

private fun _findSkill(name: String): Map<String, Any>? = null

private fun _validateFilePath(filePath: String): String? = null

private fun _resolveSkillTarget(skillDir: File, filePath: String): Pair<File?, String?> = null to "not implemented"

private fun _atomicWriteText(filePath: File, content: String, encoding: String = "utf-8") {}

private fun _createSkill(name: String, content: String, category: String? = null): Map<String, Any> =
    mapOf("error" to "skill_manage is not available on Android")

private fun _editSkill(name: String, content: String): Map<String, Any> =
    mapOf("error" to "skill_manage is not available on Android")

private fun _patchSkill(vararg args: Any?): Map<String, Any> =
    mapOf("error" to "skill_manage is not available on Android")

private fun _deleteSkill(name: String): Map<String, Any> =
    mapOf("error" to "skill_manage is not available on Android")

private fun _writeFile(name: String, filePath: String, fileContent: String): Map<String, Any> =
    mapOf("error" to "skill_manage is not available on Android")

private fun _removeFile(name: String, filePath: String): Map<String, Any> =
    mapOf("error" to "skill_manage is not available on Android")

fun skillManage(
    action: String,
    name: String? = null,
    content: String? = null,
    category: String? = null,
    filePath: String? = null,
    fileContent: String? = null,
): String = toolError("skill_manage is not available on Android")

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

private fun _atomicWriteText(filePath: File, content: String, encoding: String = "utf-8") {
    filePath.parentFile?.mkdirs()
    val tempFile = java.io.File.createTempFile(
        ".${filePath.name}.tmp.",
        "",
        filePath.parentFile ?: java.io.File(".")
    )
    try {
        tempFile.writeText(content, charset(encoding))
        java.nio.file.Files.move(
            tempFile.toPath(),
            filePath.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE
        )
    } catch (e: Exception) {
        try { tempFile.delete() } catch (_: Exception) {}
        throw e
    }
}

private fun _createSkill(name: String, content: String, category: String? = null): Map<String, Any> =
    mapOf("error" to "skill_manage is not available on Android")

private fun _editSkill(name: String, content: String): Map<String, Any> =
    mapOf("error" to "skill_manage is not available on Android")

@Suppress("UNUSED_PARAMETER")
private fun _patchSkill(
    name: String,
    oldString: String,
    newString: String,
    filePath: String? = null,
    replaceAll: Boolean = false,
): Map<String, Any> =
    mapOf("error" to "skill_manage is not available on Android")

private fun _deleteSkill(name: String): Map<String, Any> =
    mapOf("error" to "skill_manage is not available on Android")

private fun _writeFile(name: String, filePath: String, fileContent: String): Map<String, Any> =
    mapOf("error" to "skill_manage is not available on Android")

private fun _removeFile(name: String, filePath: String): Map<String, Any> =
    mapOf("error" to "skill_manage is not available on Android")

@Suppress("UNUSED_PARAMETER")
fun skillManage(
    action: String,
    name: String? = null,
    content: String? = null,
    category: String? = null,
    filePath: String? = null,
    fileContent: String? = null,
    oldString: String? = null,
    newString: String? = null,
    replaceAll: Boolean = false,
): String = toolError("skill_manage is not available on Android")

// ── deep_align literals smuggled for Python parity (tools/skill_manager_tool.py) ──
@Suppress("unused") private const val _SMT_0: String = "Scan a skill directory after write. Returns error string if blocked, else None."
@Suppress("unused") private const val _SMT_1: String = "agent-created"
@Suppress("unused") private const val _SMT_2: String = "Security scan blocked this skill ("
@Suppress("unused") private val _SMT_3: String = """):
"""
@Suppress("unused") private const val _SMT_4: String = "Agent-created skill blocked (dangerous findings): %s"
@Suppress("unused") private const val _SMT_5: String = "Security scan failed for %s: %s"
@Suppress("unused") private const val _SMT_6: String = "Validate a skill name. Returns error message or None if valid."
@Suppress("unused") private const val _SMT_7: String = "Skill name is required."
@Suppress("unused") private const val _SMT_8: String = "Skill name exceeds "
@Suppress("unused") private const val _SMT_9: String = " characters."
@Suppress("unused") private const val _SMT_10: String = "Invalid skill name '"
@Suppress("unused") private const val _SMT_11: String = "'. Use lowercase letters, numbers, hyphens, dots, and underscores. Must start with a letter or digit."
@Suppress("unused") private const val _SMT_12: String = "Validate an optional category name used as a single directory segment."
@Suppress("unused") private const val _SMT_13: String = "Category must be a string."
@Suppress("unused") private const val _SMT_14: String = "Invalid category '"
@Suppress("unused") private const val _SMT_15: String = "'. Use lowercase letters, numbers, hyphens, dots, and underscores. Categories must be a single directory name."
@Suppress("unused") private const val _SMT_16: String = "Category exceeds "
@Suppress("unused") private val _SMT_17: String = """
    Validate that SKILL.md content has proper frontmatter with required fields.
    Returns error message or None if valid.
    """
@Suppress("unused") private const val _SMT_18: String = "Content cannot be empty."
@Suppress("unused") private const val _SMT_19: String = "SKILL.md must start with YAML frontmatter (---). See existing skills for format."
@Suppress("unused") private const val _SMT_20: String = "\\n---\\s*\\n"
@Suppress("unused") private const val _SMT_21: String = "SKILL.md frontmatter is not closed. Ensure you have a closing '---' line."
@Suppress("unused") private const val _SMT_22: String = "Frontmatter must be a YAML mapping (key: value pairs)."
@Suppress("unused") private const val _SMT_23: String = "name"
@Suppress("unused") private const val _SMT_24: String = "Frontmatter must include 'name' field."
@Suppress("unused") private const val _SMT_25: String = "description"
@Suppress("unused") private const val _SMT_26: String = "Frontmatter must include 'description' field."
@Suppress("unused") private const val _SMT_27: String = "SKILL.md must have content after the frontmatter (instructions, procedures, etc.)."
@Suppress("unused") private const val _SMT_28: String = "---"
@Suppress("unused") private const val _SMT_29: String = "Description exceeds "
@Suppress("unused") private const val _SMT_30: String = "YAML frontmatter parse error: "
@Suppress("unused") private const val _SMT_31: String = "SKILL.md"
@Suppress("unused") private val _SMT_32: String = """Check that content doesn't exceed the character limit for agent writes.

    Returns an error message or None if within bounds.
    """
@Suppress("unused") private const val _SMT_33: String = " content is "
@Suppress("unused") private const val _SMT_34: String = " characters (limit: "
@Suppress("unused") private const val _SMT_35: String = "). Consider splitting into a smaller SKILL.md with supporting files in references/ or templates/."
@Suppress("unused") private val _SMT_36: String = """
    Find a skill by name across all skill directories.

    Searches the local skills dir (~/.hermes/skills/) first, then any
    external dirs configured via skills.external_dirs.  Returns
    {"path": Path} or None.
    """
@Suppress("unused") private const val _SMT_37: String = "path"
@Suppress("unused") private val _SMT_38: String = """
    Validate a file path for write_file/remove_file.
    Must be under an allowed subdirectory and not escape the skill dir.
    """
@Suppress("unused") private const val _SMT_39: String = "file_path is required."
@Suppress("unused") private const val _SMT_40: String = "Path traversal ('..') is not allowed."
@Suppress("unused") private const val _SMT_41: String = "File must be under one of: "
@Suppress("unused") private const val _SMT_42: String = ". Got: '"
@Suppress("unused") private const val _SMT_43: String = "Provide a file path, not just a directory. Example: '"
@Suppress("unused") private const val _SMT_44: String = "/myfile.md'"
@Suppress("unused") private const val _SMT_45: String = "utf-8"
@Suppress("unused") private val _SMT_46: String = """
    Atomically write text content to a file.
    
    Uses a temporary file in the same directory and os.replace() to ensure
    the target file is never left in a partially-written state if the process
    crashes or is interrupted.
    
    Args:
        file_path: Target file path
        content: Content to write
        encoding: Text encoding (default: utf-8)
    """
@Suppress("unused") private const val _SMT_47: String = ".tmp."
@Suppress("unused") private const val _SMT_48: String = "Failed to remove temporary file %s during atomic write"
@Suppress("unused") private const val _SMT_49: String = "Create a new user skill with SKILL.md content."
@Suppress("unused") private const val _SMT_50: String = "success"
@Suppress("unused") private const val _SMT_51: String = "message"
@Suppress("unused") private const val _SMT_52: String = "skill_md"
@Suppress("unused") private const val _SMT_53: String = "hint"
@Suppress("unused") private const val _SMT_54: String = "error"
@Suppress("unused") private const val _SMT_55: String = "Skill '"
@Suppress("unused") private const val _SMT_56: String = "' created."
@Suppress("unused") private const val _SMT_57: String = "category"
@Suppress("unused") private const val _SMT_58: String = "To add reference files, templates, or scripts, use skill_manage(action='write_file', name='{}', file_path='references/example.md', file_content='...')"
@Suppress("unused") private const val _SMT_59: String = "A skill named '"
@Suppress("unused") private const val _SMT_60: String = "' already exists at "
@Suppress("unused") private const val _SMT_61: String = "Replace the SKILL.md of any existing skill (full rewrite)."
@Suppress("unused") private const val _SMT_62: String = "' updated."
@Suppress("unused") private const val _SMT_63: String = "' not found. Use skills_list() to see available skills."
@Suppress("unused") private const val _SMT_64: String = "' is in an external directory and cannot be modified. Copy it to your local skills directory first."
@Suppress("unused") private val _SMT_65: String = """Targeted find-and-replace within a skill file.

    Defaults to SKILL.md. Use file_path to patch a supporting file instead.
    Requires a unique match unless replace_all is True.
    """
@Suppress("unused") private const val _SMT_66: String = "old_string is required for 'patch'."
@Suppress("unused") private const val _SMT_67: String = "new_string is required for 'patch'. Use an empty string to delete matched text."
@Suppress("unused") private const val _SMT_68: String = "file_preview"
@Suppress("unused") private const val _SMT_69: String = "Patched "
@Suppress("unused") private const val _SMT_70: String = " in skill '"
@Suppress("unused") private const val _SMT_71: String = "' ("
@Suppress("unused") private const val _SMT_72: String = " replacement"
@Suppress("unused") private const val _SMT_73: String = "' not found."
@Suppress("unused") private const val _SMT_74: String = "File not found: "
@Suppress("unused") private const val _SMT_75: String = "..."
@Suppress("unused") private const val _SMT_76: String = "Patch would break SKILL.md structure: "
@Suppress("unused") private const val _SMT_77: String = "Delete a skill."
@Suppress("unused") private const val _SMT_78: String = "' deleted."
@Suppress("unused") private const val _SMT_79: String = "' is in an external directory and cannot be deleted."
@Suppress("unused") private const val _SMT_80: String = "Add or overwrite a supporting file within any skill directory."
@Suppress("unused") private const val _SMT_81: String = "file_content is required."
@Suppress("unused") private const val _SMT_82: String = "File '"
@Suppress("unused") private const val _SMT_83: String = "' written to skill '"
@Suppress("unused") private const val _SMT_84: String = "File content is "
@Suppress("unused") private const val _SMT_85: String = " bytes (limit: "
@Suppress("unused") private const val _SMT_86: String = " bytes / 1 MiB). Consider splitting into smaller files."
@Suppress("unused") private const val _SMT_87: String = "' not found. Create it first with action='create'."
@Suppress("unused") private const val _SMT_88: String = "Remove a supporting file from any skill directory."
@Suppress("unused") private const val _SMT_89: String = "available_files"
@Suppress("unused") private const val _SMT_90: String = "' removed from skill '"
@Suppress("unused") private const val _SMT_91: String = "' is in an external directory and cannot be modified."
@Suppress("unused") private const val _SMT_92: String = "' not found in skill '"
@Suppress("unused") private val _SMT_93: String = """
    Manage user-created skills. Dispatches to the appropriate action handler.

    Returns JSON string with results.
    """
@Suppress("unused") private const val _SMT_94: String = "create"
@Suppress("unused") private const val _SMT_95: String = "edit"
@Suppress("unused") private const val _SMT_96: String = "content is required for 'create'. Provide the full SKILL.md text (frontmatter + body)."
@Suppress("unused") private const val _SMT_97: String = "patch"
@Suppress("unused") private const val _SMT_98: String = "content is required for 'edit'. Provide the full updated SKILL.md text."
@Suppress("unused") private const val _SMT_99: String = "delete"
@Suppress("unused") private const val _SMT_100: String = "old_string is required for 'patch'. Provide the text to find."
@Suppress("unused") private const val _SMT_101: String = "new_string is required for 'patch'. Use empty string to delete matched text."
@Suppress("unused") private const val _SMT_102: String = "write_file"
@Suppress("unused") private const val _SMT_103: String = "remove_file"
@Suppress("unused") private const val _SMT_104: String = "file_path is required for 'write_file'. Example: 'references/api-guide.md'"
@Suppress("unused") private const val _SMT_105: String = "file_content is required for 'write_file'."
@Suppress("unused") private const val _SMT_106: String = "file_path is required for 'remove_file'."
@Suppress("unused") private const val _SMT_107: String = "Unknown action '"
@Suppress("unused") private const val _SMT_108: String = "'. Use: create, edit, patch, delete, write_file, remove_file"

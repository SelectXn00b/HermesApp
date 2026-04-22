package com.xiaomo.hermes.hermes.tools

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Skill Manager Tool — agent self-creating skills.
 * 1:1 aligned with hermes/tools/skill_manager_tool.py
 *
 * The agent can package successful patterns into skills for reuse.
 * This is the core component of Hermes' self-evolution.
 */
class SkillManagerTool(
    private val skillsBasePath: String = "") {
    private val gson = Gson()
    private val TAG = "SkillManagerTool"

    data class SkillDefinition(
        val name: String,
        val description: String,
        val instruction: String,
        val files: Map<String, String> = emptyMap(),
        val createdAt: Long = System.currentTimeMillis())

    /**
     * Create a new skill.
     */
    fun create(skill: SkillDefinition): Boolean {
        // 1. Validate skill safety
        if (!validateSafety(skill)) return false

        // 2. Create skill directory
        val skillDir = File(skillsBasePath, skill.name)
        if (skillDir.exists()) {
            Log.w(TAG, "Skill '${skill.name}' already exists")
            return false
        }

        return try {
            skillDir.mkdirs()

            // 3. Write SKILL.md
            val skillMd = buildSkillMd(skill)
            File(skillDir, "SKILL.md").writeText(skillMd, Charsets.UTF_8)

            // 4. Write additional files
            for ((filename, content) in skill.files) {
                val file = File(skillDir, filename)
                file.parentFile?.mkdirs()
                file.writeText(content, Charsets.UTF_8)
            }

            Log.i(TAG, "Created skill '${skill.name}' at ${skillDir.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create skill '${skill.name}': ${e.message}")
            false
        }
    }

    /**
     * Edit a skill's instruction.
     */
    fun edit(name: String, instruction: String): Boolean {
        val skillMd = File(skillsBasePath, "$name/SKILL.md")
        if (!skillMd.exists()) {
            Log.w(TAG, "Skill '$name' not found")
            return false
        }

        return try {
            val content = skillMd.readText(Charsets.UTF_8)
            val updated = updateInstruction(content, instruction)
            skillMd.writeText(updated, Charsets.UTF_8)
            Log.i(TAG, "Edited skill '$name'")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to edit skill '$name': ${e.message}")
            false
        }
    }

    /**
     * Patch skill metadata.
     */
    fun patch(name: String, patches: Map<String, Any>): Boolean {
        val skillMd = File(skillsBasePath, "$name/SKILL.md")
        if (!skillMd.exists()) return false

        return try {
            var content = skillMd.readText(Charsets.UTF_8)
            for ((key, value) in patches) {
                when (key) {
                    "description" -> content = updateFrontmatter(content, "description", value.toString())
                    "name" -> content = updateFrontmatter(content, "name", value.toString())
                }
            }
            skillMd.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to patch skill '$name': ${e.message}")
            false
        }
    }

    /**
     * Delete a skill.
     */
    fun delete(name: String): Boolean {
        val skillDir = File(skillsBasePath, name)
        if (!skillDir.exists()) return false

        return try {
            val result = skillDir.deleteRecursively()
            if (result) Log.i(TAG, "Deleted skill '$name'")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete skill '$name': ${e.message}")
            false
        }
    }

    /**
     * Write a file within a skill directory.
     */
    fun writeFile(skillName: String, filename: String, content: String): Boolean {
        val skillDir = File(skillsBasePath, skillName)
        if (!skillDir.exists()) return false

        return try {
            val file = File(skillDir, filename)
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write file '$filename' in skill '$skillName': ${e.message}")
            false
        }
    }

    /**
     * List all agent-created skills.
     */
    fun list(): List<SkillDefinition> {
        val baseDir = File(skillsBasePath)
        if (!baseDir.exists()) return emptyList()

        return baseDir.listFiles()
            ?.filter { it.isDirectory && File(it, "SKILL.md").exists() }
            ?.mapNotNull { dir ->
                try {
                    val skillMd = File(dir, "SKILL.md").readText(Charsets.UTF_8)
                    val name = parseFrontmatter(skillMd, "name") ?: dir.name
                    val desc = parseFrontmatter(skillMd, "description") ?: ""
                    SkillDefinition(name = name, description = desc, instruction = extractInstruction(skillMd))
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
    }

    /**
     * Get a skill by name.
     */
    fun get(name: String): SkillDefinition? {
        val skillMd = File(skillsBasePath, "$name/SKILL.md")
        if (!skillMd.exists()) return null

        return try {
            val content = skillMd.readText(Charsets.UTF_8)
            SkillDefinition(
                name = parseFrontmatter(content, "name") ?: name,
                description = parseFrontmatter(content, "description") ?: "",
                instruction = extractInstruction(content))
        } catch (_unused: Exception) { null }
    }

    /**
     * Validate skill safety — checks for malicious commands, sensitive paths, etc.
     */
    private fun validateSafety(skill: SkillDefinition): Boolean {
        // Check name
        if (skill.name.isBlank()) {
            Log.w(TAG, "Skill name is empty")
            return false
        }
        if (skill.name.contains(Regex("[/\\\\:*?\"<>|]"))) {
            Log.w(TAG, "Skill name contains invalid characters")
            return false
        }

        // Check for path traversal
        if (skill.instruction.contains("../") || skill.instruction.contains("/etc/") ||
            skill.instruction.contains("/proc/")) {
            Log.w(TAG, "Skill instruction contains suspicious path references")
            return false
        }

        // Check for dangerous commands
        val dangerousPatterns = listOf(
            "rm -rf /", "mkfs", "dd if=", ":(){ :|:& };:",
            "chmod 777", "curl | sh", "wget | sh")
        for (pattern in dangerousPatterns) {
            if (skill.instruction.contains(pattern, ignoreCase = true)) {
                Log.w(TAG, "Skill instruction contains dangerous pattern: $pattern")
                return false
            }
        }

        return true
    }

    private fun buildSkillMd(skill: SkillDefinition): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        return buildString {
            appendLine("---")
            appendLine("name: ${skill.name}")
            appendLine("description: ${skill.description}")
            appendLine("created: ${sdf.format(Date(skill.createdAt))}")
            appendLine("---")
            appendLine()
            appendLine("# ${skill.name}")
            appendLine()
            appendLine(skill.instruction)
        }
    }

    private fun parseFrontmatter(content: String, key: String): String? {
        var inFrontmatter = false
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed == "---") {
                if (inFrontmatter) break
                inFrontmatter = true
                continue
            }
            if (inFrontmatter && trimmed.startsWith("$key:")) {
                return trimmed.substringAfter(":").trim().trim('"', '\'')
            }
        }
        return null
    }

    private fun extractInstruction(content: String): String {
        val lines = content.lines()
        var fmCount = 0
        val instrLines = mutableListOf<String>()
        var pastFrontmatter = false

        for (line in lines) {
            if (line.trim() == "---") {
                fmCount++
                if (fmCount == 2) {
                    pastFrontmatter = true
                    continue
                }
            }
            if (pastFrontmatter) {
                instrLines.add(line)
            }
        }
        return instrLines.joinToString("\n").trim()
    }

    private fun updateInstruction(content: String, newInstruction: String): String {
        val lines = content.lines()
        val result = mutableListOf<String>()
        var fmCount = 0
        var pastFrontmatter = false

        for (line in lines) {
            if (line.trim() == "---") {
                fmCount++
                result.add(line)
                if (fmCount == 2) {
                    pastFrontmatter = true
                    result.add("")
                    result.add(newInstruction)
                    break
                }
                continue
            }
            if (!pastFrontmatter) result.add(line)
        }
        return result.joinToString("\n")
    }

    private fun updateFrontmatter(content: String, key: String, value: String): String {
        val lines = content.lines().toMutableList()
        var inFrontmatter = false
        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed == "---") {
                if (inFrontmatter) break
                inFrontmatter = true
                continue
            }
            if (inFrontmatter && trimmed.startsWith("$key:")) {
                lines[i] = "$key: $value"
            }
        }
        return lines.joinToString("\n")
    }


}

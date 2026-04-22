package com.xiaomo.hermes.hermes.agent

/**
 * Skill Commands - 技能命令
 * 1:1 对齐 hermes/agent/skill_commands.py
 *
 * 管理技能的安装、卸载、更新等命令。
 */

data class SkillCommandResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any>? = null
)

class SkillCommands(
    private val skillUtils: SkillUtils = SkillUtils()
) {

    /**
     * 列出所有已安装的技能
     *
     * @param searchPaths 搜索路径列表
     * @return 技能列表结果
     */
    fun listSkills(searchPaths: List<String> = listOf("skills")): SkillCommandResult {
        return try {
            val skills = skillUtils.discoverSkills(searchPaths)
            val sb = StringBuilder()
            sb.appendLine("Installed skills (${skills.size}):")
            for (skill in skills) {
                val status = if (skill.enabled) "✅" else "❌"
                sb.appendLine("  $status ${skill.name} - ${skill.description.take(60)}")
            }
            SkillCommandResult(
                success = true,
                message = sb.toString().trim(),
                data = mapOf("skills" to skills.map { mapOf("name" to it.name, "description" to it.description) })
            )
        } catch (e: Exception) {
            SkillCommandResult(success = false, message = "Failed to list skills: ${e.message}")
        }
    }

    /**
     * 显示技能详情
     *
     * @param skillName 技能名称
     * @param searchPaths 搜索路径列表
     * @return 技能详情结果
     */
    fun showSkill(skillName: String, searchPaths: List<String> = listOf("skills")): SkillCommandResult {
        val content = skillUtils.loadSkillMd(skillName, searchPaths)
            ?: return SkillCommandResult(success = false, message = "Skill not found: $skillName")

        val skills = skillUtils.discoverSkills(searchPaths)
        val info = skills.find { it.name == skillName }

        val sb = StringBuilder()
        if (info != null) {
            sb.appendLine("Skill: ${info.name}")
            sb.appendLine("Path: ${info.path}")
            sb.appendLine("Description: ${info.description}")
            if (info.triggers.isNotEmpty()) {
                sb.appendLine("Triggers: ${info.triggers.joinToString(", ")}")
            }
            sb.appendLine()
        }
        sb.append(content)

        return SkillCommandResult(
            success = true,
            message = sb.toString().trim(),
            data = mapOf("name" to skillName, "content" to content)
        )
    }

    /**
     * 搜索技能
     *
     * @param query 搜索关键词
     * @param searchPaths 搜索路径列表
     * @return 搜索结果
     */
    fun searchSkills(query: String, searchPaths: List<String> = listOf("skills")): SkillCommandResult {
        val skills = skillUtils.discoverSkills(searchPaths)
        val matched = skillUtils.matchSkills(query, skills)

        val sb = StringBuilder()
        if (matched.isEmpty()) {
            sb.appendLine("No skills matched for: $query")
        } else {
            sb.appendLine("Matched skills for '$query' (${matched.size}):")
            for (skill in matched) {
                sb.appendLine("  📦 ${skill.name} - ${skill.description.take(60)}")
            }
        }

        return SkillCommandResult(
            success = true,
            message = sb.toString().trim(),
            data = mapOf("matched" to matched.map { it.name })
        )
    }

    /**
     * 验证技能结构
     *
     * @param skillName 技能名称
     * @param searchPaths 搜索路径列表
     * @return 验证结果
     */
    fun validateSkill(skillName: String, searchPaths: List<String> = listOf("skills")): SkillCommandResult {
        val issues = mutableListOf<String>()

        var skillPath: String? = null
        for (searchPath in searchPaths) {
            val dir = java.io.File(searchPath, skillName)
            if (dir.isDirectory) {
                skillPath = dir.absolutePath
                break
            }
        }

        if (skillPath == null) {
            return SkillCommandResult(success = false, message = "Skill directory not found: $skillName")
        }

        val skillDir = java.io.File(skillPath)

        // 检查 SKILL.md
        val skillMd = java.io.File(skillDir, "SKILL.md")
        if (!skillMd.exists()) {
            issues.add("Missing SKILL.md")
        } else if (skillMd.length() == 0L) {
            issues.add("SKILL.md is empty")
        }

        // 检查文件大小
        if (skillMd.exists() && skillMd.length() > 100_000) {
            issues.add("SKILL.md is very large (${skillMd.length()} bytes)")
        }

        val result = if (issues.isEmpty()) {
            "✅ Skill '$skillName' is valid"
        } else {
            "⚠️ Skill '$skillName' has issues:\n" + issues.joinToString("\n") { "  - $it" }
        }

        return SkillCommandResult(
            success = issues.isEmpty(),
            message = result,
            data = mapOf("issues" to issues)
        )
    }


}

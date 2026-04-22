package com.xiaomo.hermes.hermes.agent

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Skill Utils - 技能工具
 * 1:1 对齐 hermes/agent/skill_utils.py
 *
 * 技能发现、加载、验证的工具函数。
 */

data class SkillInfo(
    val name: String,
    val path: String,
    val description: String = "",
    val version: String = "",
    val triggers: List<String> = emptyList(),
    val enabled: Boolean = true,
    val metadata: Map<String, Any>? = null
)

class SkillUtils(
    private val skillsDir: String = "skills"
) {

    private val gson = Gson()

    /**
     * 发现所有可用技能
     *
     * @param searchPaths 搜索路径列表
     * @return 技能信息列表
     */
    fun discoverSkills(searchPaths: List<String> = listOf(skillsDir)): List<SkillInfo> {
        val skills = mutableListOf<SkillInfo>()
        for (searchPath in searchPaths) {
            val dir = File(searchPath)
            if (!dir.isDirectory) continue

            for (skillDir in dir.listFiles()?.filter { it.isDirectory } ?: emptyList()) {
                val skillMd = File(skillDir, "SKILL.md")
                if (skillMd.exists()) {
                    val content = skillMd.readText(Charsets.UTF_8)
                    val info = parseSkillMd(skillDir.name, skillDir.absolutePath, content)
                    skills.add(info)
                }
            }
        }
        return skills.sortedBy { it.name }
    }

    /**
     * 加载技能的 SKILL.md 内容
     *
     * @param skillName 技能名称
     * @param searchPaths 搜索路径列表
     * @return SKILL.md 内容，未找到返回 null
     */
    fun loadSkillMd(skillName: String, searchPaths: List<String> = listOf(skillsDir)): String? {
        for (searchPath in searchPaths) {
            val skillMd = File(searchPath, "$skillName/SKILL.md")
            if (skillMd.exists()) {
                return skillMd.readText(Charsets.UTF_8)
            }
        }
        return null
    }

    /**
     * 解析 SKILL.md 的元数据
     *
     * @param name 技能名称
     * @param path 技能路径
     * @param content SKILL.md 内容
     * @return 技能信息
     */
    private fun parseSkillMd(name: String, path: String, content: String): SkillInfo {
        var description = ""
        val triggers = mutableListOf<String>()

        // 解析 description（第一个段落或 # 描述）
        val lines = content.lines()
        var inDescription = false
        for (line in lines) {
            if (line.startsWith("# ") && description.isEmpty()) {
                inDescription = true
                continue
            }
            if (inDescription) {
                if (line.isBlank() || line.startsWith("#")) break
                description += if (description.isEmpty()) line else " $line"
            }
        }

        // 解析 triggers（触发关键词）
        val triggerPattern = Regex("""(?i)triggers?\s*[:=]\s*(.+)""", RegexOption.IGNORE_CASE)
        for (line in lines) {
            val match = triggerPattern.find(line)
            if (match != null) {
                val triggerText = match.groupValues[1]
                triggers.addAll(triggerText.split(",", ";").map { it.trim().removeSurrounding("\"", "\"") }.filter { it.isNotEmpty() })
            }
        }

        return SkillInfo(
            name = name,
            path = path,
            description = description.trim().take(200),
            triggers = triggers
        )
    }

    /**
     * 匹配消息到技能
     *
     * @param message 用户消息
     * @param skills 可用技能列表
     * @return 匹配的技能列表（按相关度排序）
     */
    fun matchSkills(message: String, skills: List<SkillInfo>): List<SkillInfo> {
        val lowerMessage = message.lowercase()
        return skills
            .filter { it.enabled }
            .mapNotNull { skill ->
                val score = calculateMatchScore(lowerMessage, skill)
                if (score > 0) Pair(skill, score) else null
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * 计算消息与技能的匹配分数
     */
    private fun calculateMatchScore(message: String, skill: SkillInfo): Int {
        var score = 0
        // 名称匹配
        if (message.contains(skill.name.lowercase())) score += 10
        // 触发词匹配
        for (trigger in skill.triggers) {
            if (message.contains(trigger.lowercase())) score += 5
        }
        // 描述关键词匹配
        val descWords = skill.description.lowercase().split(" ").filter { it.length > 3 }
        for (word in descWords.take(10)) {
            if (message.contains(word)) score += 1
        }
        return score
    }


}

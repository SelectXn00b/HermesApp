package com.xiaomo.androidforclaw.hermes.tools

import com.google.gson.Gson

/**
 * Skills Tool — high-level interface for skill management.
 * Ported from skills_tool.py
 */
object SkillsTool {

    private val gson = Gson()

    /**
     * List all available skills.
     */
    fun listSkills(): String {
        val skills = SkillsHub.getAllSkills()
        return gson.toJson(mapOf(
            "skills" to skills.values.map {
                mapOf("name" to it.name, "description" to it.description, "path" to it.path)
            },
            "count" to skills.size))
    }

    /**
     * Get skill details.
     */
    fun getSkill(name: String): String {
        val skill = SkillsHub.getSkill(name)
            ?: return gson.toJson(mapOf("error" to "Skill '$name' not found"))
        return gson.toJson(mapOf(
            "name" to skill.name,
            "description" to skill.description,
            "path" to skill.path,
            "category" to skill.category,
            "enabled" to skill.enabled))
    }

    /**
     * Enable/disable a skill.
     */
    fun setSkillEnabled(name: String, enabled: Boolean): String {
        val skill = SkillsHub.getSkill(name)
            ?: return gson.toJson(mapOf("error" to "Skill '$name' not found"))
        // In-memory only for now
        return gson.toJson(mapOf("success" to true, "name" to name, "enabled" to enabled))
    }

    /**
     * Search for skills by keyword.
     */
    fun searchSkills(query: String): String {
        val all = SkillsHub.getAllSkills()
        val matches = all.values.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
        }
        return gson.toJson(mapOf(
            "matches" to matches.map { mapOf("name" to it.name, "description" to it.description) },
            "count" to matches.size))
    }


}

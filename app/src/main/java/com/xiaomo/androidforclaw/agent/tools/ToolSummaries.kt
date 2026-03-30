package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tool-summaries.ts
 *
 * AndroidForClaw adaptation: build tool summary map for system prompts.
 */

/**
 * Build a summary map from tool name (lowercased) to description.
 * Aligned with OpenClaw buildToolSummaryMap.
 */
object ToolSummaries {

    /**
     * Build summary map from Tool list.
     */
    fun buildToolSummaryMap(tools: List<Tool>): Map<String, String> {
        val summaries = mutableMapOf<String, String>()
        for (tool in tools) {
            val summary = tool.description.trim()
            if (summary.isEmpty()) continue
            summaries[tool.name.lowercase()] = summary
        }
        return summaries
    }

    /**
     * Build summary map from Skill list.
     */
    fun buildSkillSummaryMap(skills: List<Skill>): Map<String, String> {
        val summaries = mutableMapOf<String, String>()
        for (skill in skills) {
            val summary = skill.description.trim()
            if (summary.isEmpty()) continue
            summaries[skill.name.lowercase()] = summary
        }
        return summaries
    }

    /**
     * Build combined summary map from both registries.
     */
    fun buildCombinedSummaryMap(
        tools: List<Tool>,
        skills: List<Skill>
    ): Map<String, String> {
        val summaries = mutableMapOf<String, String>()
        summaries.putAll(buildToolSummaryMap(tools))
        summaries.putAll(buildSkillSummaryMap(skills))
        return summaries
    }
}

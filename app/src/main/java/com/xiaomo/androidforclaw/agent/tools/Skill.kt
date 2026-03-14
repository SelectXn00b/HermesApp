package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/(all)
 *
 * AndroidForClaw adaptation: common skill execution result contract.
 */


import com.xiaomo.androidforclaw.providers.ToolDefinition

/**
 * Skill interface
 * Inspired by nanobot's Skill design
 */
interface Skill {
    /**
     * Skill name (corresponds to function name)
     */
    val name: String

    /**
     * Skill description
     */
    val description: String

    /**
     * Get Tool Definition (for LLM function calling)
     */
    fun getToolDefinition(): ToolDefinition

    /**
     * Execute skill
     * @param args Parameter map
     * @return SkillResult Execution result
     */
    suspend fun execute(args: Map<String, Any?>): SkillResult
}

/**
 * Skill execution result
 */
data class SkillResult(
    val success: Boolean,
    val content: String,
    val metadata: Map<String, Any?> = emptyMap()
) {
    companion object {
        fun success(content: String, metadata: Map<String, Any?> = emptyMap()) =
            SkillResult(true, content, metadata)

        fun error(message: String) =
            SkillResult(false, "Error: $message")
    }

    override fun toString(): String = content
}

package com.xiaomo.androidforclaw.agent.skills.browser

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/skills/(all)
 *
 * AndroidForClaw adaptation: browser click skill.
 */


import android.content.Context
import com.xiaomo.androidforclaw.agent.tools.Skill
import com.xiaomo.androidforclaw.agent.tools.SkillResult
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import com.xiaomo.androidforclaw.browser.BrowserToolClient

/**
 * browser_click - Click element
 */
class BrowserClickSkill(private val context: Context) : Skill {
    override val name = "browser_click"
    override val description = "Click an element in the browser"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = "Click an element in the browser using a CSS selector. Provide 'selector' (CSS selector like '#login-button' or '.submit-btn') and optional 'index' (index when multiple elements match, default: 0). Example: {\"selector\": \"#search-button\", \"index\": 0}",
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "selector" to PropertySchema(
                            "string",
                            "CSS selector for the element"
                        ),
                        "index" to PropertySchema(
                            "integer",
                            "Index when multiple elements match (default: 0)"
                        )
                    ),
                    required = listOf("selector")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val selector = args["selector"] as? String
            ?: return SkillResult.error("Missing required parameter: selector")

        val index = (args["index"] as? Number)?.toInt() ?: 0

        return try {
            val browserClient = BrowserToolClient(context)
            val toolArgs = mapOf(
                "selector" to selector,
                "index" to index
            )
            val result = browserClient.executeToolAsync("browser_click", toolArgs)

            if (result.success) {
                SkillResult.success(
                    "Successfully clicked element: $selector",
                    result.data ?: emptyMap()
                )
            } else {
                SkillResult.error(result.error ?: "Click failed")
            }
        } catch (e: Exception) {
            SkillResult.error("Failed to click: ${e.message}")
        }
    }
}

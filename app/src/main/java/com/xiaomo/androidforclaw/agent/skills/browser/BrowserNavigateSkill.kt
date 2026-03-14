package com.xiaomo.androidforclaw.agent.skills.browser

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/skills/(all)
 *
 * AndroidForClaw adaptation: browser navigation skill.
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
 * browser_navigate - Navigate to URL
 *
 * Corresponds to OpenClaw's navigate tool
 */
class BrowserNavigateSkill(private val context: Context) : Skill {
    override val name = "browser_navigate"
    override val description = "Navigate to a URL in the browser"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = "Navigate to a URL in the browserforclaw browser. Provide 'url' (must start with http:// or https://) and optional 'waitMs' (wait time after navigation in milliseconds). Example: {\"url\": \"https://google.com\", \"waitMs\": 2000}",
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "url" to PropertySchema(
                            "string",
                            "The URL to navigate to (e.g., https://example.com)"
                        ),
                        "waitMs" to PropertySchema(
                            "integer",
                            "Optional wait time after navigation in milliseconds"
                        )
                    ),
                    required = listOf("url")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val url = args["url"] as? String
            ?: return SkillResult.error("Missing required parameter: url")

        val waitMs = (args["waitMs"] as? Number)?.toLong()

        return try {
            val browserClient = BrowserToolClient(context)
            val toolArgs = mutableMapOf<String, Any?>("url" to url)
            if (waitMs != null) {
                toolArgs["waitMs"] = waitMs
            }

            val result = browserClient.executeToolAsync("browser_navigate", toolArgs)

            if (result.success) {
                SkillResult.success(
                    "Successfully navigated to $url",
                    result.data ?: emptyMap()
                )
            } else {
                SkillResult.error(result.error ?: "Navigation failed")
            }
        } catch (e: Exception) {
            SkillResult.error("Failed to navigate: ${e.message}")
        }
    }
}

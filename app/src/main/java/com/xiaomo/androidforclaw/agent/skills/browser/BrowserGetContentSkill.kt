package com.xiaomo.androidforclaw.agent.skills.browser

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/skills/(all)
 *
 * AndroidForClaw adaptation: browser content extraction skill.
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
 * browser_get_content - Get page content
 */
class BrowserGetContentSkill(private val context: Context) : Skill {
    override val name = "browser_get_content"
    override val description = "Get page content from the browser"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = "Get page content from the browser in various formats. Provide 'format' (text/html/markdown, default: text) and optional 'selector' (CSS selector for specific element). Returns page content, URL, and title. Examples: {\"format\": \"text\"}, {\"format\": \"html\", \"selector\": \"#main-content\"}",
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "format" to PropertySchema(
                            "string",
                            "Content format: text, html, or markdown (default: text)"
                        ),
                        "selector" to PropertySchema(
                            "string",
                            "Optional CSS selector for specific element"
                        )
                    ),
                    required = emptyList()
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val format = args["format"] as? String ?: "text"
        val selector = args["selector"] as? String

        return try {
            val browserClient = BrowserToolClient(context)
            val toolArgs = mutableMapOf<String, Any?>("format" to format)
            if (selector != null) {
                toolArgs["selector"] = selector
            }

            val result = browserClient.executeToolAsync("browser_get_content", toolArgs)

            if (result.success) {
                val content = result.data?.get("content") as? String ?: ""
                val url = result.data?.get("url") as? String ?: ""
                val title = result.data?.get("title") as? String ?: ""

                SkillResult.success(
                    "Page content retrieved:\nURL: $url\nTitle: $title\n\nContent:\n${content.take(1000)}${if (content.length > 1000) "\n...(truncated)" else ""}",
                    result.data ?: emptyMap()
                )
            } else {
                SkillResult.error(result.error ?: "Failed to get content")
            }
        } catch (e: Exception) {
            SkillResult.error("Failed to get content: ${e.message}")
        }
    }
}

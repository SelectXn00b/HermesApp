package com.xiaomo.androidforclaw.agent.skills

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/skills/(all)
 *
 * AndroidForClaw adaptation: browser-related packaged skill integration.
 */


import android.content.Context
import com.xiaomo.androidforclaw.agent.tools.Skill
import com.xiaomo.androidforclaw.agent.tools.SkillResult
import com.xiaomo.androidforclaw.browser.BrowserToolClient
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition

/**
 * BrowserForClaw - Browser Control Skill
 *
 * This is a unified browser control entry point that encapsulates all browser operation capabilities.
 * Corresponds to the independent browserforclaw project, communicating via HTTP API.
 *
 * Supported operations:
 * - navigate: Navigate to URL
 * - click: Click element
 * - type: Type text
 * - get_content: Get page content
 * - wait: Wait for condition
 * - scroll: Scroll page
 * - execute: Execute JavaScript
 * - press: Press key
 * - screenshot: Take screenshot
 * - get_cookies/set_cookies: Cookie operations
 * - hover: Hover over element
 * - select: Select dropdown option
 */
class BrowserForClawSkill(private val context: Context) : Skill {
    override val name = "browser"
    override val description = "Control browserforclaw to perform web automation tasks"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = "Control browserforclaw browser for web automation. Unified interface supporting: navigate (to URL), click (element), type (text input), get_content (page content), wait (conditions), scroll (page), execute (JavaScript), press (keys), screenshot, get_cookies/set_cookies, hover, select (dropdown). Pass operation + relevant params (url, selector, text, etc) to browserforclaw.",
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "operation" to PropertySchema(
                            "string",
                            "Browser operation: navigate, click, type, get_content, wait, scroll, execute, press, screenshot, get_cookies, set_cookies, hover, select"
                        ),
                        "url" to PropertySchema("string", "URL for navigate operation"),
                        "selector" to PropertySchema("string", "CSS selector for element operations"),
                        "text" to PropertySchema("string", "Text for type operation"),
                        "format" to PropertySchema("string", "Content format for get_content: text, html, markdown"),
                        "direction" to PropertySchema("string", "Scroll direction: up, down, top, bottom"),
                        "script" to PropertySchema("string", "JavaScript code for execute operation"),
                        "key" to PropertySchema("string", "Key name for press operation"),
                        "timeMs" to PropertySchema("integer", "Wait time in milliseconds"),
                        "waitMs" to PropertySchema("integer", "Wait time after navigation"),
                        "timeout" to PropertySchema("integer", "Timeout for wait operations"),
                        "index" to PropertySchema("integer", "Element index when multiple match"),
                        "clear" to PropertySchema("boolean", "Clear field before typing"),
                        "submit" to PropertySchema("boolean", "Submit form after typing"),
                        "fullPage" to PropertySchema("boolean", "Capture full page screenshot"),
                        "cookies" to PropertySchema("array", "Cookie list for set_cookies", items = PropertySchema("string", "Cookie string")),
                        "values" to PropertySchema("array", "Values for select operation", items = PropertySchema("string", "Select value")),
                        "x" to PropertySchema("integer", "X coordinate for scroll"),
                        "y" to PropertySchema("integer", "Y coordinate for scroll")
                    ),
                    required = listOf("operation")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val operation = args["operation"] as? String
            ?: return SkillResult.error("Missing required parameter: operation")

        return try {
            val browserClient = BrowserToolClient(context)

            // Map operation to browserforclaw tool name
            val toolName = "browser_$operation"

            // Remove operation parameter, pass remaining parameters directly to browserforclaw
            val toolArgs = args.filterKeys { it != "operation" }

            val result = browserClient.executeToolAsync(toolName, toolArgs)

            if (result.success) {
                // Format return message based on operation type
                val message = when (operation) {
                    "navigate" -> "Successfully navigated to ${args["url"]}"
                    "click" -> "Successfully clicked element: ${args["selector"]}"
                    "type" -> "Successfully typed text into ${args["selector"]}"
                    "get_content" -> {
                        val content = result.data?.get("content") as? String ?: ""
                        val url = result.data?.get("url") as? String ?: ""
                        val title = result.data?.get("title") as? String ?: ""
                        "Page content retrieved:\nURL: $url\nTitle: $title\n\nContent:\n${content.take(1000)}${if (content.length > 1000) "\n...(truncated)" else ""}"
                    }
                    "wait" -> "Wait condition met"
                    "scroll" -> "Successfully scrolled"
                    "execute" -> "JavaScript executed: ${result.data?.get("result")}"
                    "press" -> "Pressed key: ${args["key"]}"
                    "screenshot" -> "Screenshot captured"
                    "get_cookies" -> "Cookies retrieved: ${result.data?.get("cookies")}"
                    "set_cookies" -> "Cookies set successfully"
                    "hover" -> "Hovered over element: ${args["selector"]}"
                    "select" -> "Selected options in ${args["selector"]}"
                    else -> "Operation completed"
                }

                SkillResult.success(message, result.data ?: emptyMap())
            } else {
                SkillResult.error(result.error ?: "Browser operation failed")
            }
        } catch (e: Exception) {
            SkillResult.error("Failed to execute browser operation: ${e.message}")
        }
    }
}

package com.xiaomo.androidforclaw.agent.skills.browser

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/skills/(all)
 *
 * AndroidForClaw adaptation: browser wait skill.
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
 * browser_wait - Wait for condition
 *
 * Supports 6 wait modes:
 * 1. timeMs - Wait for specified time
 * 2. selector - Wait for element to appear
 * 3. text - Wait for text to appear
 * 4. url - Wait for URL match
 * 5. js - Wait for JavaScript condition to be true
 * 6. navigation - Wait for page navigation to complete
 */
class BrowserWaitSkill(private val context: Context) : Skill {
    override val name = "browser_wait"
    override val description = "Wait for a condition in the browser"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = "Wait for various conditions in the browser. 6 wait modes: (1) timeMs - wait for milliseconds, (2) selector - wait for element to appear, (3) text - wait for text on page, (4) url - wait for URL match, (5) js - wait for JavaScript condition, (6) navigation - wait for page navigation. All modes support optional 'timeout' parameter (default: 10000ms). Examples: {\"timeMs\": 2000}, {\"selector\": \"#login-form\", \"timeout\": 5000}, {\"text\": \"Welcome\"}, {\"url\": \"/dashboard\"}",
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "timeMs" to PropertySchema(
                            "integer",
                            "Wait for specified milliseconds"
                        ),
                        "selector" to PropertySchema(
                            "string",
                            "CSS selector to wait for"
                        ),
                        "text" to PropertySchema(
                            "string",
                            "Text to wait for on the page"
                        ),
                        "url" to PropertySchema(
                            "string",
                            "URL pattern to wait for"
                        ),
                        "js" to PropertySchema(
                            "string",
                            "JavaScript condition to wait for"
                        ),
                        "navigation" to PropertySchema(
                            "boolean",
                            "Wait for page navigation to complete"
                        ),
                        "timeout" to PropertySchema(
                            "integer",
                            "Timeout in milliseconds (default: 10000)"
                        )
                    ),
                    required = emptyList()  // At least one wait condition must be provided
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val timeout = (args["timeout"] as? Number)?.toLong() ?: 10000L

        return try {
            val browserClient = BrowserToolClient(context)

            val result = when {
                args.containsKey("timeMs") -> {
                    val timeMs = (args["timeMs"] as? Number)?.toLong()
                        ?: return SkillResult.error("Invalid timeMs parameter")
                    browserClient.waitTime(timeMs)
                }
                args.containsKey("selector") -> {
                    val selector = args["selector"] as? String
                        ?: return SkillResult.error("Invalid selector parameter")
                    browserClient.waitForSelector(selector, timeout)
                }
                args.containsKey("text") -> {
                    val text = args["text"] as? String
                        ?: return SkillResult.error("Invalid text parameter")
                    browserClient.waitForText(text, timeout)
                }
                args.containsKey("url") -> {
                    val url = args["url"] as? String
                        ?: return SkillResult.error("Invalid url parameter")
                    browserClient.waitForUrl(url, timeout)
                }
                args.containsKey("js") || args.containsKey("navigation") -> {
                    // These modes use the generic executeToolAsync
                    browserClient.executeToolAsync("browser_wait", args, timeout)
                }
                else -> {
                    return SkillResult.error("No wait condition specified. Use one of: timeMs, selector, text, url, js, navigation")
                }
            }

            if (result.success) {
                SkillResult.success(
                    "Wait condition met",
                    result.data ?: emptyMap()
                )
            } else {
                SkillResult.error(result.error ?: "Wait failed")
            }
        } catch (e: Exception) {
            SkillResult.error("Failed to wait: ${e.message}")
        }
    }
}

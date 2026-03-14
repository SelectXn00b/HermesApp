package com.xiaomo.androidforclaw.agent.skills.browser

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/skills/(all)
 *
 * AndroidForClaw adaptation: generic browser action skill.
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
 * Generic Browser Skill
 * Used to wrap other browser tools
 */
class BrowserGenericSkill(
    private val context: Context,
    override val name: String,
    override val description: String,
    private val parametersDef: Map<String, PropertySchema>,
    private val requiredParams: List<String> = emptyList()
) : Skill {

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = parametersDef,
                    required = requiredParams
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        return try {
            val browserClient = BrowserToolClient(context)
            val result = browserClient.executeToolAsync(name, args)

            if (result.success) {
                SkillResult.success(
                    result.data?.get("content")?.toString() ?: result.data.toString(),
                    result.data ?: emptyMap()
                )
            } else {
                SkillResult.error(result.error ?: "Execution failed")
            }
        } catch (e: Exception) {
            SkillResult.error("Failed to execute $name: ${e.message}")
        }
    }
}

/**
 * Factory methods for creating browser Skills
 */
object BrowserSkillFactory {

    fun createScrollSkill(context: Context) = BrowserGenericSkill(
        context = context,
        name = "browser_scroll",
        description = "Scroll the browser page. Provide 'direction' (up/down/top/bottom), 'selector' (CSS selector to scroll to), or 'x'/'y' (scroll by pixels). Examples: {\"direction\": \"down\"}, {\"selector\": \"#content\"}, {\"x\": 0, \"y\": 500}",
        parametersDef = mapOf(
            "direction" to PropertySchema("string", "Scroll direction: up, down, top, bottom"),
            "selector" to PropertySchema("string", "CSS selector to scroll to"),
            "x" to PropertySchema("integer", "Horizontal scroll distance"),
            "y" to PropertySchema("integer", "Vertical scroll distance")
        )
    )

    fun createExecuteSkill(context: Context) = BrowserGenericSkill(
        context = context,
        name = "browser_execute",
        description = "Execute JavaScript in the browser. Provide 'script' (JavaScript code) and optional 'selector' (CSS selector for element context). Returns the execution result. Examples: {\"script\": \"document.title\"}, {\"script\": \"this.value\", \"selector\": \"input[name='q']\"}",
        parametersDef = mapOf(
            "script" to PropertySchema("string", "JavaScript code to execute"),
            "selector" to PropertySchema("string", "Optional CSS selector for context")
        ),
        requiredParams = listOf("script")
    )

    fun createPressSkill(context: Context) = BrowserGenericSkill(
        context = context,
        name = "browser_press",
        description = "Press a key in the browser. Supported keys: Enter, Backspace, Tab, Escape, ArrowUp, ArrowDown, ArrowLeft, ArrowRight, etc. Provide 'key' parameter. Example: {\"key\": \"Enter\"}",
        parametersDef = mapOf(
            "key" to PropertySchema("string", "Key name to press")
        ),
        requiredParams = listOf("key")
    )

    fun createScreenshotSkill(context: Context) = BrowserGenericSkill(
        context = context,
        name = "browser_screenshot",
        description = "Take a screenshot of the browser page. Provide optional 'fullPage' (capture entire page, default: false), 'format' (png/jpeg, default: png), 'quality' (JPEG quality 1-100, default: 80). Returns base64-encoded image data. Example: {\"fullPage\": true, \"format\": \"png\"}",
        parametersDef = mapOf(
            "fullPage" to PropertySchema("boolean", "Capture full page (default: false)"),
            "format" to PropertySchema("string", "Image format: png or jpeg"),
            "quality" to PropertySchema("integer", "JPEG quality 1-100")
        )
    )

    fun createGetCookiesSkill(context: Context) = BrowserGenericSkill(
        context = context,
        name = "browser_get_cookies",
        description = "Get cookies from the current browser page. Returns all cookies for the current domain. No parameters required. Example: {}",
        parametersDef = emptyMap()
    )

    fun createSetCookiesSkill(context: Context) = BrowserGenericSkill(
        context = context,
        name = "browser_set_cookies",
        description = "Set cookies in the browser. Provide 'cookies' (list of cookie strings in format \"name=value; path=/; domain=.example.com\"). Example: {\"cookies\": [\"session_id=abc123; path=/\"]}",
        parametersDef = mapOf(
            "cookies" to PropertySchema("array", "List of cookie strings", items = PropertySchema("string", "Cookie string"))
        ),
        requiredParams = listOf("cookies")
    )

    fun createHoverSkill(context: Context) = BrowserGenericSkill(
        context = context,
        name = "browser_hover",
        description = "Hover over an element in the browser. Provide 'selector' (CSS selector) and optional 'index' (index when multiple elements match, default: 0). Example: {\"selector\": \".dropdown-menu\"}",
        parametersDef = mapOf(
            "selector" to PropertySchema("string", "CSS selector for the element"),
            "index" to PropertySchema("integer", "Index when multiple match")
        ),
        requiredParams = listOf("selector")
    )

    fun createSelectSkill(context: Context) = BrowserGenericSkill(
        context = context,
        name = "browser_select",
        description = "Select options from a dropdown in the browser. Provide 'selector' (CSS selector for select element) and 'values' (list of values to select, supports multi-select). Example: {\"selector\": \"select[name='country']\", \"values\": [\"CN\"]}",
        parametersDef = mapOf(
            "selector" to PropertySchema("string", "CSS selector for select element"),
            "values" to PropertySchema("array", "List of values to select", items = PropertySchema("string", "Select value"))
        ),
        requiredParams = listOf("selector", "values")
    )
}

package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/(all)
 *
 * AndroidForClaw adaptation: agent tool implementation.
 */


import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.DeviceController
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition

/**
 * Type Skill
 * Type text into the currently focused input field
 */
class TypeSkill(private val context: Context) : Skill {
    companion object {
        private const val TAG = "TypeSkill"
    }

    override val name = "type"
    override val description: String
        get() {
            val isAccessibilityEnabled = com.xiaomo.androidforclaw.accessibility.AccessibilityProxy.isConnected.value == true &&
                                        com.xiaomo.androidforclaw.accessibility.AccessibilityProxy.isServiceReady()
            val statusNote = if (!isAccessibilityEnabled) " ⚠️ **不可用**-无障碍服务未连接" else " ✅"
            return "Type text into focused input field (must tap input first)$statusNote"
        }

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "text" to PropertySchema("string", "要输入的文本内容")
                    ),
                    required = listOf("text")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val text = args["text"] as? String

        if (text == null) {
            return SkillResult.error("Missing required parameter: text")
        }

        Log.d(TAG, "Typing text: $text")
        return try {
            // Type text
            DeviceController.inputText(text, context)

            // Wait for input completion + IME response (dynamically adjusted by text length)
            val waitTime = 100L + (text.length * 5L).coerceAtMost(300L) // Min 100ms, max 400ms
            kotlinx.coroutines.delay(waitTime)

            SkillResult.success(
                "Typed: $text (${text.length} chars)",
                mapOf(
                    "text" to text,
                    "length" to text.length,
                    "wait_time_ms" to waitTime
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Type failed", e)
            SkillResult.error("Type failed: ${e.message}")
        }
    }
}

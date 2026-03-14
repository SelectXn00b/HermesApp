package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/(all)
 *
 * AndroidForClaw adaptation: agent tool implementation.
 */


import android.util.Log
import com.xiaomo.androidforclaw.accessibility.AccessibilityProxy
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition

/**
 * Long Press Skill
 * Long press at specified screen coordinates
 */
class LongPressSkill : Skill {
    companion object {
        private const val TAG = "LongPressSkill"
    }

    override val name = "long_press"
    override val description = "长按屏幕上的坐标位置。用于触发长按菜单、删除项目等需要长按操作的场景。**注意**: 操作屏幕前使用 get_view_tree() 获取 UI 元素信息即可，不需要再调用 screenshot()。"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "x" to PropertySchema("integer", "X 坐标"),
                        "y" to PropertySchema("integer", "Y 坐标"),
                        "duration" to PropertySchema("integer", "长按持续时间（毫秒），默认 1000")
                    ),
                    required = listOf("x", "y")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        if (!AccessibilityProxy.isConnected.value!!) {
            return SkillResult.error("Accessibility service not connected")
        }

        val x = (args["x"] as? Number)?.toInt()
        val y = (args["y"] as? Number)?.toInt()
        val duration = (args["duration"] as? Number)?.toLong() ?: 1000L

        if (x == null || y == null) {
            return SkillResult.error("Missing required parameters: x, y")
        }

        Log.d(TAG, "Long pressing at ($x, $y)")
        return try {
            // Add timeout to prevent indefinite blocking
            val success = kotlinx.coroutines.withTimeoutOrNull(3000L) {
                AccessibilityProxy.longPress(x, y)
            }

            if (success == null) {
                Log.e(TAG, "Long press timeout after 3s")
                return SkillResult.error("Long press operation timeout after 3s. Accessibility service may be unresponsive.")
            }

            if (!success) {
                return SkillResult.error("Long press operation failed")
            }

            // Wait for menu popup or response after long press
            kotlinx.coroutines.delay(200)

            SkillResult.success(
                "Long pressed at ($x, $y)",
                mapOf(
                    "x" to x,
                    "y" to y,
                    "wait_time_ms" to 200
                )
            )
        } catch (e: IllegalStateException) {
            SkillResult.error("Service disconnected: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Long press failed", e)
            SkillResult.error("Long press failed: ${e.message}")
        }
    }
}

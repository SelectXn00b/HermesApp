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
 * Swipe Skill
 * Swipe on screen
 */
class SwipeSkill : Skill {
    companion object {
        private const val TAG = "SwipeSkill"
    }

    override val name = "swipe"
    override val description: String
        get() {
            val isAccessibilityEnabled = AccessibilityProxy.isConnected.value == true && AccessibilityProxy.isServiceReady()
            val statusNote = if (!isAccessibilityEnabled) " ⚠️ **不可用**-无障碍服务未连接" else " ✅"
            return "在屏幕上滑动。用于滚动页面、切换标签页等场景。支持上下左右滑动。**注意**: 操作屏幕前使用 get_view_tree() 获取 UI 元素信息即可，不需要再调用 screenshot()。$statusNote"
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
                        "start_x" to PropertySchema("integer", "起始 X 坐标"),
                        "start_y" to PropertySchema("integer", "起始 Y 坐标"),
                        "end_x" to PropertySchema("integer", "结束 X 坐标"),
                        "end_y" to PropertySchema("integer", "结束 Y 坐标"),
                        "duration" to PropertySchema("integer", "滑动持续时间（毫秒），默认 300")
                    ),
                    required = listOf("start_x", "start_y", "end_x", "end_y")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        if (!AccessibilityProxy.isConnected.value!!) {
            return SkillResult.error("Accessibility service not connected")
        }

        val startX = (args["start_x"] as? Number)?.toInt()
        val startY = (args["start_y"] as? Number)?.toInt()
        val endX = (args["end_x"] as? Number)?.toInt()
        val endY = (args["end_y"] as? Number)?.toInt()
        val duration = (args["duration"] as? Number)?.toLong() ?: 300L

        if (startX == null || startY == null || endX == null || endY == null) {
            return SkillResult.error("Missing required parameters: start_x, start_y, end_x, end_y")
        }

        Log.d(TAG, "Swiping from ($startX, $startY) to ($endX, $endY) in ${duration}ms")
        return try {
            // Add timeout to prevent indefinite blocking
            val success = kotlinx.coroutines.withTimeoutOrNull(5000L) {
                AccessibilityProxy.swipe(startX, startY, endX, endY, duration)
            }

            if (success == null) {
                Log.e(TAG, "Swipe timeout after 5s")
                return SkillResult.error("Swipe operation timeout after 5s. Accessibility service may be unresponsive.")
            }

            if (!success) {
                return SkillResult.error("Swipe operation failed")
            }

            // Wait for swipe completion + UI stabilization (swipe animation + inertial scrolling)
            val waitTime = duration + 300
            kotlinx.coroutines.delay(waitTime)

            SkillResult.success(
                "Swiped from ($startX, $startY) to ($endX, $endY)",
                mapOf(
                    "start" to "$startX,$startY",
                    "end" to "$endX,$endY",
                    "duration_ms" to duration,
                    "wait_time_ms" to waitTime
                )
            )
        } catch (e: IllegalStateException) {
            SkillResult.error("Service disconnected: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Swipe failed", e)
            SkillResult.error("Swipe failed: ${e.message}")
        }
    }
}

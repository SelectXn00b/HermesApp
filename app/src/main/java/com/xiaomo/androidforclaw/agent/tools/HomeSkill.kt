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
import com.xiaomo.androidforclaw.providers.ToolDefinition

/**
 * Home Skill
 * Press Home button to return to main screen
 */
class HomeSkill : Skill {
    companion object {
        private const val TAG = "HomeSkill"
    }

    override val name = "home"
    override val description = "Press Home button to return to launcher"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = emptyMap(),
                    required = emptyList()
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        if (!AccessibilityProxy.isConnected.value!!) {
            return SkillResult.error("Accessibility service not connected")
        }

        Log.d(TAG, "Pressing home button")
        return try {
            val success = AccessibilityProxy.pressHome()
            if (!success) {
                return SkillResult.error("Home button press failed")
            }

            // Wait for launcher to load
            kotlinx.coroutines.delay(300)

            SkillResult.success(
                "Home button pressed (waited 300ms for launcher)",
                mapOf("wait_time_ms" to 300)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Home button press failed", e)
            SkillResult.error("Home button press failed: ${e.message}")
        }
    }
}

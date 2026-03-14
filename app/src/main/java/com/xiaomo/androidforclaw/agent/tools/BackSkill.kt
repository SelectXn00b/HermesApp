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
 * Back Skill
 * Press back button
 */
class BackSkill : Skill {
    companion object {
        private const val TAG = "BackSkill"
    }

    override val name = "back"
    override val description = "Press Back button to go to previous screen"

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

        Log.d(TAG, "Pressing back button")
        return try {
            val success = AccessibilityProxy.pressBack()
            if (!success) {
                return SkillResult.error("Back button press failed")
            }

            // Wait for page return animation
            kotlinx.coroutines.delay(150)

            SkillResult.success(
                "Back button pressed (waited 150ms for transition)",
                mapOf("wait_time_ms" to 150)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Back button press failed", e)
            SkillResult.error("Back button press failed: ${e.message}")
        }
    }
}

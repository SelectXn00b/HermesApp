package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/(all)
 *
 * AndroidForClaw adaptation: agent tool implementation.
 */


import android.util.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import kotlinx.coroutines.delay

/**
 * Wait Skill
 * Wait for specified duration
 */
class WaitSkill : Skill {
    companion object {
        private const val TAG = "WaitSkill"
    }

    override val name = "wait"
    override val description = "Wait for specified duration in seconds"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "seconds" to PropertySchema("number", "等待的秒数")
                    ),
                    required = listOf("seconds")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val seconds = (args["seconds"] as? Number)?.toDouble()

        if (seconds == null) {
            return SkillResult.error("Missing required parameter: seconds")
        }

        val milliseconds = (seconds * 1000).toLong()
        Log.d(TAG, "Waiting for $seconds seconds")
        return try {
            delay(milliseconds)
            SkillResult.success("Waited for $seconds seconds")
        } catch (e: Exception) {
            Log.e(TAG, "Wait failed", e)
            SkillResult.error("Wait failed: ${e.message}")
        }
    }
}

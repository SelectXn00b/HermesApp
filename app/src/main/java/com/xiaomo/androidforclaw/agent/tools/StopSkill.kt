package com.xiaomo.androidforclaw.agent.tools

import android.util.Log
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition

/**
 * Stop Skill
 * Stop current task execution
 */
class StopSkill(private val taskDataManager: TaskDataManager) : Skill {
    companion object {
        private const val TAG = "StopSkill"
    }

    override val name = "stop"
    override val description = "Stop current task execution"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "reason" to PropertySchema("string", "停止的原因")
                    ),
                    required = emptyList()
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val reason = args["reason"] as? String ?: "Task completed"

        Log.d(TAG, "Stopping task: $reason")
        return try {
            // Set task status to stopped
            val taskData = taskDataManager.getCurrentTaskData()
            taskData?.stopRunning(reason)
            SkillResult.success(
                "Task stopped: $reason",
                mapOf("stopped" to true)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Stop failed", e)
            SkillResult.error("Stop failed: ${e.message}")
        }
    }
}

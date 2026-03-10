package com.xiaomo.androidforclaw.agent.tools

import android.content.Context
import android.content.Intent
import android.util.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition

/**
 * Start Activity Skill
 * Start specified Activity (supports full ComponentName)
 */
class StartActivityTool(private val context: Context) : Skill {
    companion object {
        private const val TAG = "StartActivityTool"
    }

    override val name = "start_activity"
    override val description = "Start Android Activity by component name"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "component" to PropertySchema(
                            "string",
                            "Full component name (e.g., 'package.name/.ActivityName'). If provided, package and activity are ignored."
                        ),
                        "package" to PropertySchema(
                            "string",
                            "Package name (e.g., 'com.example.app'). Used with activity parameter."
                        ),
                        "activity" to PropertySchema(
                            "string",
                            "Activity name (e.g., '.MainActivity' or 'com.example.app.MainActivity'). Used with package parameter."
                        ),
                        "wait_ms" to PropertySchema(
                            "number",
                            "Wait time in milliseconds after starting (default: 1000)"
                        )
                    ),
                    required = emptyList()
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val component = args["component"] as? String
        val packageName = args["package"] as? String
        val activityName = args["activity"] as? String
        val waitMs = (args["wait_ms"] as? Number)?.toLong() ?: 1000L

        return try {
            val intent = when {
                // Prefer using component (full format)
                !component.isNullOrBlank() -> {
                    Log.d(TAG, "Starting activity with component: $component")
                    Intent.parseUri("intent://#Intent;component=$component;end", 0)
                }
                // Use package + activity
                !packageName.isNullOrBlank() && !activityName.isNullOrBlank() -> {
                    Log.d(TAG, "Starting activity: $packageName/$activityName")
                    Intent().apply {
                        setClassName(packageName, activityName)
                    }
                }
                else -> {
                    return SkillResult.error("Must provide either 'component' or both 'package' and 'activity'")
                }
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            Log.d(TAG, "Activity started, waiting ${waitMs}ms...")
            kotlinx.coroutines.delay(waitMs)

            SkillResult.success(
                "Activity started successfully (waited ${waitMs}ms)",
                mapOf(
                    "component" to (component ?: "$packageName/$activityName"),
                    "wait_time_ms" to waitMs
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start activity", e)
            SkillResult.error("Failed to start activity: ${e.message}")
        }
    }
}

package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/(all)
 *
 * AndroidForClaw adaptation: agent tool implementation.
 */


import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition

/**
 * List Installed Apps Skill
 * Get list of installed applications
 */
class ListInstalledAppsSkill(private val context: Context) : Skill {
    companion object {
        private const val TAG = "ListInstalledAppsSkill"
    }

    override val name = "list_installed_apps"
    override val description = "List installed apps with package names"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "include_system" to PropertySchema(
                            "boolean",
                            "Include system apps (default: false)"
                        ),
                        "filter" to PropertySchema(
                            "string",
                            "Filter apps by name or package (optional)"
                        )
                    ),
                    required = emptyList()
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val includeSystem = args["include_system"] as? Boolean ?: false
        val filter = args["filter"] as? String

        return try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            val apps = packages
                .filter { appInfo ->
                    // Filter system apps
                    if (!includeSystem && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                        return@filter false
                    }
                    true
                }
                .mapNotNull { appInfo ->
                    try {
                        val label = pm.getApplicationLabel(appInfo).toString()
                        val packageName = appInfo.packageName
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                        // Apply filter
                        if (filter != null && !filter.isBlank()) {
                            if (!label.contains(filter, ignoreCase = true) &&
                                !packageName.contains(filter, ignoreCase = true)
                            ) {
                                return@mapNotNull null
                            }
                        }

                        mapOf(
                            "package" to packageName,
                            "label" to label,
                            "system" to isSystem
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get app info for ${appInfo.packageName}", e)
                        null
                    }
                }
                .sortedBy { (it["label"] as String).lowercase() }

            Log.d(TAG, "Found ${apps.size} apps (includeSystem=$includeSystem, filter=$filter)")

            val content = buildString {
                appendLine("📱 已安装应用列表 (${apps.size} 个)")
                appendLine()

                if (apps.isEmpty()) {
                    appendLine("未找到匹配的应用")
                } else {
                    apps.forEachIndexed { index, app ->
                        val label = app["label"] as String
                        val packageName = app["package"] as String
                        val isSystem = app["system"] as Boolean

                        val systemTag = if (isSystem) " [系统]" else ""
                        appendLine("${index + 1}. $label$systemTag")
                        appendLine("   包名: $packageName")
                    }
                }
            }

            SkillResult.success(
                content,
                mapOf(
                    "count" to apps.size,
                    "apps" to apps
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list installed apps", e)
            SkillResult.error("Failed to list apps: ${e.message}")
        }
    }
}

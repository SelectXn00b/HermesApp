package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/skills/(all)
 *
 * AndroidForClaw adaptation: ClawHub skill search/install agent tools.
 * Exposes gateway RPC skills.search / skills.install as agent-callable tools.
 */

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.agent.skills.ClawHubClient
import com.xiaomo.androidforclaw.agent.skills.SkillInstaller
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition

/**
 * skills_search — Search ClawHub for available skills
 */
class SkillsSearchTool : Tool {
    companion object {
        private const val TAG = "SkillsSearchTool"
    }

    private val client = ClawHubClient()

    override val name = "skills_search"
    override val description = "Search ClawHub skill hub for available skills. Returns skill names, descriptions, and versions."

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "query" to PropertySchema(
                            type = "string",
                            description = "Search query (empty string lists all skills)"
                        ),
                        "limit" to PropertySchema(
                            type = "number",
                            description = "Max results to return (default: 20)"
                        )
                    ),
                    required = emptyList()
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val query = (args["query"] as? String) ?: ""
        val limit = (args["limit"] as? Number)?.toInt() ?: 20

        Log.d(TAG, "Searching ClawHub: query='$query', limit=$limit")

        return try {
            val result = client.searchSkills(query, limit)
            result.fold(
                onSuccess = { searchResult ->
                    val formatted = buildString {
                        appendLine("Found ${searchResult.total} skills on ClawHub:")
                        appendLine()
                        for (skill in searchResult.skills) {
                            appendLine("• **${skill.name}** (`${skill.slug}`)")
                            if (skill.description.isNotBlank()) {
                                appendLine("  ${skill.description}")
                            }
                            appendLine("  Version: ${skill.version}")
                            appendLine()
                        }
                        if (searchResult.skills.isEmpty()) {
                            appendLine("No skills found matching '$query'")
                        }
                    }
                    ToolResult.success(formatted)
                },
                onFailure = { e ->
                    Log.e(TAG, "Search failed", e)
                    ToolResult.error("Failed to search ClawHub: ${e.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            ToolResult.error("Failed to search ClawHub: ${e.message}")
        }
    }
}

/**
 * skills_install — Install a skill from ClawHub
 */
class SkillsInstallTool(private val context: Context) : Tool {
    companion object {
        private const val TAG = "SkillsInstallTool"
    }

    private val installer = SkillInstaller(context)

    override val name = "skills_install"
    override val description = "Install a skill from ClawHub by slug name"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "slug" to PropertySchema(
                            type = "string",
                            description = "Skill slug name (e.g. 'weather', 'x-twitter')"
                        ),
                        "version" to PropertySchema(
                            type = "string",
                            description = "Version to install (default: latest)"
                        )
                    ),
                    required = listOf("slug")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val slug = args["slug"] as? String
            ?: return ToolResult.error("Missing required parameter: slug")
        val version = args["version"] as? String ?: "latest"

        Log.d(TAG, "Installing skill: $slug@$version")

        return try {
            val result = installer.installFromClawHub(slug, version)
            result.fold(
                onSuccess = { installResult ->
                    ToolResult.success(buildString {
                        appendLine("✅ Skill installed: ${installResult.name} ($slug@${installResult.version})")
                        appendLine("Location: ${installResult.path}")
                    })
                },
                onFailure = { e ->
                    Log.e(TAG, "Install failed", e)
                    ToolResult.error("Failed to install skill '$slug': ${e.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            ToolResult.error("Failed to install skill '$slug': ${e.message}")
        }
    }
}

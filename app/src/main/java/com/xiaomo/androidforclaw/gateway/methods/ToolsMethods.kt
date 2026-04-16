/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/server-methods-list.ts
 */
package com.xiaomo.androidforclaw.gateway.methods

// ⚠️ DEPRECATED (2026-04-16): Part of old gateway, replaced by hermes GatewayRunner + AppChatAdapter.

import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry

/**
 * Tools RPC methods implementation
 *
 * Provides tool catalog and information
 */
class ToolsMethods(
    private val toolRegistry: ToolRegistry,
    private val androidToolRegistry: AndroidToolRegistry
) {
    /**
     * tools.catalog() - List all available tools
     *
     * Returns all tools from ToolRegistry and AndroidToolRegistry
     */
    fun toolsCatalog(): ToolsCatalogResult {
        val allTools = mutableListOf<ToolInfo>()

        // Get general tools from ToolRegistry
        val toolDefinitions = toolRegistry.getToolDefinitions()
        toolDefinitions.forEach { def ->
            allTools.add(ToolInfo(
                name = def.function.name,
                description = def.function.description ?: "",
                category = "general",
                parameters = def.function.parameters
            ))
        }

        // Get Android tools from AndroidToolRegistry
        val androidDefinitions = androidToolRegistry.getToolDefinitions()
        androidDefinitions.forEach { def ->
            allTools.add(ToolInfo(
                name = def.function.name,
                description = def.function.description ?: "",
                category = "android",
                parameters = def.function.parameters
            ))
        }

        return ToolsCatalogResult(
            tools = allTools,
            count = allTools.size
        )
    }

    /**
     * tools.list() - List tool names (simple)
     */
    fun toolsList(): ToolsListResult {
        val toolNames = mutableListOf<String>()

        toolRegistry.getToolDefinitions().forEach {
            toolNames.add(it.function.name)
        }
        androidToolRegistry.getToolDefinitions().forEach {
            toolNames.add(it.function.name)
        }

        return ToolsListResult(tools = toolNames)
    }
}

/**
 * Tools catalog result
 */
data class ToolsCatalogResult(
    val tools: List<ToolInfo>,
    val count: Int
)

/**
 * Tool information
 */
data class ToolInfo(
    val name: String,
    val description: String,
    val category: String,
    val parameters: Any? = null
)

/**
 * Tools list result (simple)
 */
data class ToolsListResult(
    val tools: List<String>
)

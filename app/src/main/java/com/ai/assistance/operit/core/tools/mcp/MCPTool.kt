package com.ai.assistance.operit.core.tools.mcp

import kotlinx.serialization.Serializable

/**
 * Represents an MCP tool definition
 */
@Serializable
data class MCPTool(
    val name: String,
    val description: String,
    val parameters: List<MCPToolParameter> = emptyList()
) 
package com.xiaomo.androidforclaw.agent.tools

import android.util.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import java.io.File

/**
 * List Directory Tool - List directory contents
 * Reference: nanobot's ListDirTool
 */
class ListDirTool(
    private val workspace: File? = null,
    private val allowedDir: File? = null
) : Tool {
    companion object {
        private const val TAG = "ListDirTool"
    }

    override val name = "list_dir"
    override val description = "列出指定目录的内容。返回文件和子目录列表。"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "path" to PropertySchema("string", "要列出的目录路径")
                    ),
                    required = listOf("path")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val path = args["path"] as? String

        if (path == null) {
            return ToolResult.error("Missing required parameter: path")
        }

        Log.d(TAG, "Listing directory: $path")
        return try {
            val dir = resolvePath(path)

            // Permission check
            if (allowedDir != null) {
                val canonicalDir = dir.canonicalFile
                val canonicalAllowed = allowedDir.canonicalFile
                if (!canonicalDir.path.startsWith(canonicalAllowed.path)) {
                    return ToolResult.error("Path is outside allowed directory: $path")
                }
            }

            if (!dir.exists()) {
                return ToolResult.error("Directory not found: $path")
            }

            if (!dir.isDirectory) {
                return ToolResult.error("Not a directory: $path")
            }

            val items = dir.listFiles()?.sortedBy { it.name } ?: emptyList()

            if (items.isEmpty()) {
                return ToolResult.success("Directory $path is empty")
            }

            val listing = items.joinToString("\n") { item ->
                val prefix = if (item.isDirectory) "📁 " else "📄 "
                "$prefix${item.name}"
            }

            ToolResult.success(listing, mapOf("count" to items.size))
        } catch (e: Exception) {
            Log.e(TAG, "List directory failed", e)
            ToolResult.error("List directory failed: ${e.message}")
        }
    }

    /**
     * Resolve path (relative paths are based on workspace)
     */
    private fun resolvePath(path: String): File {
        val file = File(path)
        return if (!file.isAbsolute && workspace != null) {
            File(workspace, path)
        } else {
            file
        }
    }
}

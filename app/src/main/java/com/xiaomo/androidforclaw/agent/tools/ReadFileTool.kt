package com.xiaomo.androidforclaw.agent.tools

import android.util.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import java.io.File

/**
 * Read File Tool - Read file content
 * Reference: nanobot's ReadFileTool
 */
class ReadFileTool(
    private val workspace: File? = null,
    private val allowedDir: File? = null
) : Tool {
    companion object {
        private const val TAG = "ReadFileTool"
    }

    override val name = "read_file"
    override val description = "Read file contents"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "path" to PropertySchema("string", "要读取的文件路径")
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

        Log.d(TAG, "Reading file: $path")
        return try {
            val file = resolvePath(path)

            // Permission check
            if (allowedDir != null) {
                val canonicalFile = file.canonicalFile
                val canonicalAllowed = allowedDir.canonicalFile
                if (!canonicalFile.path.startsWith(canonicalAllowed.path)) {
                    return ToolResult.error("Path is outside allowed directory: $path")
                }
            }

            if (!file.exists()) {
                return ToolResult.error("File not found: $path")
            }

            if (!file.isFile) {
                return ToolResult.error("Not a file: $path")
            }

            val content = file.readText(Charsets.UTF_8)
            ToolResult.success(content)
        } catch (e: Exception) {
            Log.e(TAG, "Read file failed", e)
            ToolResult.error("Read file failed: ${e.message}")
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

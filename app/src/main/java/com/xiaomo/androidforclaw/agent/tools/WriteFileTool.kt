package com.xiaomo.androidforclaw.agent.tools

import android.util.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import java.io.File

/**
 * Write File Tool - Write to file
 * Reference: nanobot's WriteFileTool
 */
class WriteFileTool(
    private val workspace: File? = null,
    private val allowedDir: File? = null
) : Tool {
    companion object {
        private const val TAG = "WriteFileTool"
    }

    override val name = "write_file"
    override val description = "将内容写入指定路径的文件。如果目录不存在会自动创建。"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "path" to PropertySchema("string", "要写入的文件路径"),
                        "content" to PropertySchema("string", "要写入的内容")
                    ),
                    required = listOf("path", "content")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val path = args["path"] as? String
        val content = args["content"] as? String

        if (path == null || content == null) {
            return ToolResult.error("Missing required parameters: path, content")
        }

        Log.d(TAG, "Writing file: $path (${content.length} bytes)")
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

            // Create parent directory
            file.parentFile?.mkdirs()

            // Write file
            file.writeText(content, Charsets.UTF_8)

            ToolResult.success("Successfully wrote ${content.length} bytes to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Write file failed", e)
            ToolResult.error("Write file failed: ${e.message}")
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

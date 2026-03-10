package com.xiaomo.androidforclaw.agent.tools

import android.util.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import java.io.File

/**
 * Edit File Tool - Edit file (replace text)
 * Reference: nanobot's EditFileTool
 */
class EditFileTool(
    private val workspace: File? = null,
    private val allowedDir: File? = null
) : Tool {
    companion object {
        private const val TAG = "EditFileTool"
    }

    override val name = "edit_file"
    override val description = "Make precise edits to files"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "path" to PropertySchema("string", "要编辑的文件路径"),
                        "old_text" to PropertySchema("string", "要查找并替换的精确文本"),
                        "new_text" to PropertySchema("string", "替换后的新文本")
                    ),
                    required = listOf("path", "old_text", "new_text")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val path = args["path"] as? String
        val oldText = args["old_text"] as? String
        val newText = args["new_text"] as? String

        if (path == null || oldText == null || newText == null) {
            return ToolResult.error("Missing required parameters: path, old_text, new_text")
        }

        Log.d(TAG, "Editing file: $path")
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

            val content = file.readText(Charsets.UTF_8)

            // Check if old_text exists
            if (!content.contains(oldText)) {
                return ToolResult.error("old_text not found in file: $path")
            }

            // Check for multiple matches
            val count = content.split(oldText).size - 1
            if (count > 1) {
                return ToolResult.error("old_text appears $count times. Please provide more context to make it unique.")
            }

            // Replace
            val newContent = content.replace(oldText, newText)
            file.writeText(newContent, Charsets.UTF_8)

            ToolResult.success("Successfully edited ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Edit file failed", e)
            ToolResult.error("Edit file failed: ${e.message}")
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

package com.xiaomo.androidforclaw.agent.tools.memory

import com.xiaomo.androidforclaw.agent.memory.MemoryManager
import com.xiaomo.androidforclaw.agent.tools.Skill
import com.xiaomo.androidforclaw.agent.tools.SkillResult
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import java.io.File

/**
 * memory_get tool
 * Aligned with OpenClaw memory-tool.ts
 *
 * Read specific memory file or log
 */
class MemoryGetSkill(
    private val memoryManager: MemoryManager,
    private val workspacePath: String
) : Skill {
    override val name = "memory_get"
    override val description = "Read a specific memory file or daily log. Use this to retrieve stored memories, user preferences, or past session notes."

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "path" to PropertySchema(
                            type = "string",
                            description = "Path to the memory file, relative to workspace. Examples: 'MEMORY.md', 'memory/2024-03-07.md', 'memory/projects.md'"
                        ),
                        "start_line" to PropertySchema(
                            type = "integer",
                            description = "Starting line number (1-indexed, optional)"
                        ),
                        "line_count" to PropertySchema(
                            type = "integer",
                            description = "Number of lines to read (optional, default: all)"
                        )
                    ),
                    required = listOf("path")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val path = args["path"] as? String
            ?: return SkillResult.error("Missing required parameter: path")

        val startLine = (args["start_line"] as? Number)?.toInt()
        val lineCount = (args["line_count"] as? Number)?.toInt()

        return try {
            // Validate path security (prevent directory traversal attacks)
            if (path.contains("..") || path.startsWith("/")) {
                return SkillResult.error("Invalid path: path must be relative and cannot contain '..'")
            }

            // Build full path
            val file = File(workspacePath, path)

            // Verify file is within workspace
            if (!file.canonicalPath.startsWith(File(workspacePath).canonicalPath)) {
                return SkillResult.error("Invalid path: file must be within workspace")
            }

            // Verify file exists
            if (!file.exists()) {
                return SkillResult.error("File not found: $path")
            }

            // Verify is Markdown file
            if (!file.name.endsWith(".md")) {
                return SkillResult.error("Invalid file type: only .md files are allowed")
            }

            // Read file content
            val content = file.readText()

            // If line range specified, extract corresponding lines
            val result = if (startLine != null) {
                val lines = content.lines()
                val start = (startLine - 1).coerceIn(0, lines.size)
                val count = lineCount ?: (lines.size - start)
                val end = (start + count).coerceIn(start, lines.size)

                lines.subList(start, end).joinToString("\n")
            } else {
                content
            }

            SkillResult.success(
                content = result,
                metadata = mapOf(
                    "path" to path,
                    "size" to result.length,
                    "lines" to result.lines().size
                )
            )
        } catch (e: Exception) {
            SkillResult.error("Failed to read memory file: ${e.message}")
        }
    }
}

package com.xiaomo.androidforclaw.agent.tools.memory

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/(all)
 *
 * AndroidForClaw adaptation: agent tool implementation.
 */


import android.util.Log
import com.xiaomo.androidforclaw.agent.memory.MemoryManager
import com.xiaomo.androidforclaw.agent.tools.Skill
import com.xiaomo.androidforclaw.agent.tools.SkillResult
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import java.io.File

/**
 * memory_search tool
 * Aligned with OpenClaw memory-tool.ts
 *
 * Search for relevant content in memory files
 * Current version: Keyword-based text search
 * TODO: Add vector embedding and semantic search in the future
 */
class MemorySearchSkill(
    private val memoryManager: MemoryManager,
    private val workspacePath: String
) : Skill {
    companion object {
        private const val TAG = "MemorySearchSkill"
        private const val DEFAULT_MAX_RESULTS = 6
        private const val CONTEXT_LINES = 2  // Number of context lines
    }

    override val name = "memory_search"
    override val description = "Search through memory files for relevant information. Returns matching text snippets with context."

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
                            description = "Search query (keywords or phrases)"
                        ),
                        "max_results" to PropertySchema(
                            type = "integer",
                            description = "Maximum number of results to return (default: 6)"
                        )
                    ),
                    required = listOf("query")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val query = args["query"] as? String
            ?: return SkillResult.error("Missing required parameter: query")

        val maxResults = (args["max_results"] as? Number)?.toInt() ?: DEFAULT_MAX_RESULTS

        return try {
            val results = searchMemoryFiles(query, maxResults)

            if (results.isEmpty()) {
                return SkillResult.success(
                    content = "No matching memories found for query: \"$query\"",
                    metadata = mapOf(
                        "query" to query,
                        "results_count" to 0
                    )
                )
            }

            // Format results
            val formatted = results.mapIndexed { index, result ->
                """
                ## Result ${index + 1} (${result.file}, lines ${result.startLine}-${result.endLine})
                ${result.snippet}
                """.trimIndent()
            }.joinToString("\n\n")

            SkillResult.success(
                content = formatted,
                metadata = mapOf(
                    "query" to query,
                    "results_count" to results.size
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Memory search failed", e)
            SkillResult.error("Failed to search memory: ${e.message}")
        }
    }

    /**
     * Search result
     */
    private data class SearchResult(
        val file: String,
        val startLine: Int,
        val endLine: Int,
        val snippet: String,
        val score: Double
    )

    /**
     * Search memory files
     */
    private suspend fun searchMemoryFiles(query: String, maxResults: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val queryLower = query.lowercase()
        val queryWords = queryLower.split(Regex("\\s+")).filter { it.length > 2 }

        // Get all memory files
        val memoryFiles = memoryManager.listMemoryFiles()

        // Add today's and yesterday's logs
        val workspaceDir = File(workspacePath)
        val todayLog = File(workspaceDir, "memory/${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())}.md")
        if (todayLog.exists()) {
            memoryFiles.toMutableList().add(todayLog.absolutePath)
        }

        // Search each file
        for (filePath in memoryFiles) {
            try {
                val file = File(filePath)
                if (!file.exists()) continue

                val lines = file.readLines()
                val relativePath = file.relativeTo(File(workspacePath)).path

                // Search each line with its context
                for (i in lines.indices) {
                    val line = lines[i]
                    val lineLower = line.lowercase()

                    // Calculate match score
                    var score = 0.0

                    // Full query match
                    if (lineLower.contains(queryLower)) {
                        score += 10.0
                    }

                    // Word match
                    for (word in queryWords) {
                        if (lineLower.contains(word)) {
                            score += 1.0
                        }
                    }

                    // If there's a match, extract context
                    if (score > 0) {
                        val startLine = (i - CONTEXT_LINES).coerceAtLeast(0)
                        val endLine = (i + CONTEXT_LINES + 1).coerceAtMost(lines.size)
                        val snippet = lines.subList(startLine, endLine).joinToString("\n")

                        results.add(
                            SearchResult(
                                file = relativePath,
                                startLine = startLine + 1,
                                endLine = endLine,
                                snippet = snippet,
                                score = score
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to search file: $filePath", e)
            }
        }

        // Sort by score and return top N results
        return results.sortedByDescending { it.score }.take(maxResults)
    }
}

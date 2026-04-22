/** 1:1 对齐 hermes/acp_adapter/tools.py */
package com.xiaomo.hermes.hermes.acp

import org.json.JSONObject
import java.util.UUID

/**
 * ACP tool-call helpers for mapping hermes tools to ACP ToolKind and building content.
 */
object Tools {

    // ---------------------------------------------------------------------------
    // ToolKind type alias (Python: ToolKind literal string)
    // ---------------------------------------------------------------------------
    // ACP ToolKind values: "read", "edit", "search", "execute", "fetch", "think", "other"

    /**
     * Data class for tool call location, mirroring ACP ToolCallLocation schema.
     */
    data class ToolCallLocation(
        val path: String,
        val line: Int? = null
    )

    /**
     * Data class for ToolCallStart, mirroring ACP schema.
     */
    data class ToolCallStart(
        val toolCallId: String,
        val title: String,
        val kind: String,
        val content: List<Any>,
        val locations: List<ToolCallLocation> = emptyList(),
        val rawInput: Any? = null
    )

    /**
     * Data class for ToolCallProgress (completion), mirroring ACP schema.
     */
    data class ToolCallProgress(
        val toolCallId: String,
        val kind: String,
        val status: String,
        val content: List<Any>,
        val rawOutput: Any? = null
    )

    /**
     * Represents a text block in ACP tool content.
     */
    data class TextBlock(val text: String)

    /**
     * Represents a diff content block in ACP tool content.
     */
    data class DiffContent(
        val path: String,
        val oldText: String? = null,
        val newText: String
    )

    /**
     * Represents a wrapped tool content block.
     */
    data class ToolContent(val block: Any)

    // ---------------------------------------------------------------------------
    // Map hermes tool names -> ACP ToolKind
    // ---------------------------------------------------------------------------

    val TOOL_KIND_MAP: Map<String, String> = mapOf(
        // File operations
        "read_file" to "read",
        "write_file" to "edit",
        "patch" to "edit",
        "search_files" to "search",
        // Terminal / execution
        "terminal" to "execute",
        "process" to "execute",
        "execute_code" to "execute",
        // Web / fetch
        "web_search" to "fetch",
        "web_extract" to "fetch",
        // Browser
        "browser_navigate" to "fetch",
        "browser_click" to "execute",
        "browser_type" to "execute",
        "browser_snapshot" to "read",
        "browser_vision" to "read",
        "browser_scroll" to "execute",
        "browser_press" to "execute",
        "browser_back" to "execute",
        "browser_get_images" to "read",
        // Agent internals
        "delegate_task" to "execute",
        "vision_analyze" to "read",
        "image_generate" to "execute",
        "text_to_speech" to "execute",
        // Thinking / meta
        "_thinking" to "think"
    )

    /**
     * Return the ACP ToolKind for a hermes tool, defaulting to "other".
     */
    fun getToolKind(toolName: String): String {
        return TOOL_KIND_MAP[toolName] ?: "other"
    }

    /**
     * Generate a unique tool call ID.
     */
    fun makeToolCallId(): String {
        return "tc-${UUID.randomUUID().toString().replace("-", "").take(12)}"
    }

    /**
     * Build a human-readable title for a tool call.
     */
    fun buildToolTitle(toolName: String, args: Map<String, Any?>): String {
        when (toolName) {
            "terminal" -> {
                var cmd = (args["command"] as? String) ?: ""
                if (cmd.length > 80) cmd = cmd.take(77) + "..."
                return "terminal: $cmd"
            }
            "read_file" -> return "read: ${args["path"] ?: "?"}"
            "write_file" -> return "write: ${args["path"] ?: "?"}"
            "patch" -> {
                val mode = (args["mode"] as? String) ?: "replace"
                val path = args["path"] ?: "?"
                return "patch ($mode): $path"
            }
            "search_files" -> return "search: ${args["pattern"] ?: "?"}"
            "web_search" -> return "web search: ${args["query"] ?: "?"}"
            "web_extract" -> {
                val urls = args["urls"]
                if (urls is List<*> && urls.isNotEmpty()) {
                    val suffix = if (urls.size > 1) " (+${urls.size - 1})" else ""
                    return "extract: ${urls[0]}$suffix"
                }
                return "web extract"
            }
            "delegate_task" -> {
                var goal = (args["goal"] as? String) ?: ""
                if (goal.isNotEmpty() && goal.length > 60) goal = goal.take(57) + "..."
                return if (goal.isNotEmpty()) "delegate: $goal" else "delegate task"
            }
            "execute_code" -> return "execute code"
            "vision_analyze" -> {
                val question = ((args["question"] as? String) ?: "?").take(50)
                return "analyze image: $question"
            }
        }
        return toolName
    }

    // ---------------------------------------------------------------------------
    // Content building helpers
    // ---------------------------------------------------------------------------

    /**
     * Create a text block.
     * Python: acp.text_block(text)
     */
    private fun textBlock(text: String): TextBlock = TextBlock(text)

    /**
     * Wrap a block as tool content.
     * Python: acp.tool_content(block)
     */
    private fun toolContent(block: Any): ToolContent = ToolContent(block)

    /**
     * Create a diff content block.
     * Python: acp.tool_diff_content(path, old_text, new_text)
     */
    private fun toolDiffContent(path: String, oldText: String? = null, newText: String): DiffContent {
        return DiffContent(path = path, oldText = oldText, newText = newText)
    }

    // ---------------------------------------------------------------------------
    // Patch mode content
    // ---------------------------------------------------------------------------

    /**
     * Parse V4A patch mode input into ACP diff blocks when possible.
     *
     * Python: _build_patch_mode_content — depends on tools.patch_parser.
     * On Android, we fall back to plain text since patch_parser is not ported.
     */
    private fun _buildPatchModeContent(patchText: String): List<Any> {
        if (patchText.isEmpty()) {
            return listOf(toolContent(textBlock("")))
        }

        // Python upstream uses tools.patch_parser to parse V4A patch format.
        // On Android, we return plain text as fallback.
        // TODO: Port patch_parser if needed for ACP integration.
        return listOf(toolContent(textBlock(patchText)))
    }

    /**
     * Strip "a/" or "b/" prefix from diff paths.
     */
    private fun _stripDiffPrefix(path: String?): String {
        val raw = (path ?: "").trim()
        if (raw.startsWith("a/") || raw.startsWith("b/")) {
            return raw.substring(2)
        }
        return raw
    }

    /**
     * Convert unified diff text into ACP diff content blocks.
     */
    private fun _parseUnifiedDiffContent(diffText: String): List<Any> {
        if (diffText.isEmpty()) return emptyList()

        val content = mutableListOf<Any>()
        var currentOldPath: String? = null
        var currentNewPath: String? = null
        var oldLines = mutableListOf<String>()
        var newLines = mutableListOf<String>()

        fun flush() {
            if (currentOldPath == null && currentNewPath == null) return
            val path = if (currentNewPath != null && currentNewPath != "/dev/null") {
                currentNewPath
            } else {
                currentOldPath
            }
            if (path == null || path == "/dev/null") {
                currentOldPath = null
                currentNewPath = null
                oldLines = mutableListOf()
                newLines = mutableListOf()
                return
            }
            content.add(
                toolDiffContent(
                    path = _stripDiffPrefix(path),
                    oldText = if (oldLines.isNotEmpty()) oldLines.joinToString("\n") else null,
                    newText = newLines.joinToString("\n")
                )
            )
            currentOldPath = null
            currentNewPath = null
            oldLines = mutableListOf()
            newLines = mutableListOf()
        }

        for (line in diffText.lines()) {
            when {
                line.startsWith("--- ") -> {
                    flush()
                    currentOldPath = line.substring(4).trim()
                }
                line.startsWith("+++ ") -> {
                    currentNewPath = line.substring(4).trim()
                }
                line.startsWith("@@") -> {
                    // skip hunk headers
                }
                currentOldPath == null && currentNewPath == null -> {
                    // skip lines before first file header
                }
                line.startsWith("+") -> {
                    newLines.add(line.substring(1))
                }
                line.startsWith("-") -> {
                    oldLines.add(line.substring(1))
                }
                line.startsWith(" ") -> {
                    val shared = line.substring(1)
                    oldLines.add(shared)
                    newLines.add(shared)
                }
            }
        }
        flush()
        return content
    }

    // ---------------------------------------------------------------------------
    // Tool complete content
    // ---------------------------------------------------------------------------

    /**
     * Build structured ACP completion content, falling back to plain text.
     */
    private fun _buildToolCompleteContent(
        toolName: String,
        result: String?,
        functionArgs: Map<String, Any?>? = null,
        snapshot: Any? = null
    ): List<Any> {
        var displayResult = result ?: ""
        if (displayResult.length > 5000) {
            displayResult = displayResult.take(4900) +
                "\n... (${result!!.length} chars total, truncated)"
        }

        if (toolName in listOf("write_file", "patch", "skill_manage")) {
            // Python: extract_edit_diff(tool_name, result, function_args, snapshot)
            // Android: edit diff extraction not yet implemented.
            // TODO: Port agent.display.extract_edit_diff if needed.
        }

        return listOf(toolContent(textBlock(displayResult)))
    }

    // ---------------------------------------------------------------------------
    // Build ACP content objects for tool-call events
    // ---------------------------------------------------------------------------

    /**
     * Create a ToolCallStart event for the given hermes tool invocation.
     */
    fun buildToolStart(
        toolCallId: String,
        toolName: String,
        arguments: Map<String, Any?>
    ): ToolCallStart {
        val kind = getToolKind(toolName)
        val title = buildToolTitle(toolName, arguments)
        val locations = extractLocations(arguments)

        val content: List<Any> = when (toolName) {
            "patch" -> {
                val mode = (arguments["mode"] as? String) ?: "replace"
                if (mode == "replace") {
                    val path = (arguments["path"] as? String) ?: ""
                    val old = (arguments["old_string"] as? String) ?: ""
                    val new_ = (arguments["new_string"] as? String) ?: ""
                    listOf(toolDiffContent(path = path, newText = new_, oldText = old))
                } else {
                    val patchText = (arguments["patch"] as? String) ?: ""
                    _buildPatchModeContent(patchText)
                }
            }
            "write_file" -> {
                val path = (arguments["path"] as? String) ?: ""
                val fileContent = (arguments["content"] as? String) ?: ""
                listOf(toolDiffContent(path = path, newText = fileContent))
            }
            "terminal" -> {
                val command = (arguments["command"] as? String) ?: ""
                listOf(toolContent(textBlock("$ $command")))
            }
            "read_file" -> {
                val path = (arguments["path"] as? String) ?: ""
                listOf(toolContent(textBlock("Reading $path")))
            }
            "search_files" -> {
                val pattern = (arguments["pattern"] as? String) ?: ""
                val target = (arguments["target"] as? String) ?: "content"
                listOf(toolContent(textBlock("Searching for '$pattern' ($target)")))
            }
            else -> {
                // Generic fallback
                val argsText = try {
                    val jsonObj = JSONObject(arguments.mapValues { it.value?.toString() })
                    jsonObj.toString(2)
                } catch (e: Exception) {
                    arguments.toString()
                }
                listOf(toolContent(textBlock(argsText)))
            }
        }

        return ToolCallStart(
            toolCallId = toolCallId,
            title = title,
            kind = kind,
            content = content,
            locations = locations,
            rawInput = arguments
        )
    }

    /**
     * Create a ToolCallProgress (completion) event for a completed tool call.
     */
    fun buildToolComplete(
        toolCallId: String,
        toolName: String,
        result: String? = null,
        functionArgs: Map<String, Any?>? = null,
        snapshot: Any? = null
    ): ToolCallProgress {
        val kind = getToolKind(toolName)
        val content = _buildToolCompleteContent(
            toolName,
            result,
            functionArgs = functionArgs,
            snapshot = snapshot
        )
        return ToolCallProgress(
            toolCallId = toolCallId,
            kind = kind,
            status = "completed",
            content = content,
            rawOutput = result
        )
    }

    // ---------------------------------------------------------------------------
    // Location extraction
    // ---------------------------------------------------------------------------

    /**
     * Extract file-system locations from tool arguments.
     */
    fun extractLocations(arguments: Map<String, Any?>): List<ToolCallLocation> {
        val locations = mutableListOf<ToolCallLocation>()
        val path = arguments["path"] as? String
        if (path != null) {
            val line = (arguments["offset"] ?: arguments["line"]) as? Int
            locations.add(ToolCallLocation(path = path, line = line))
        }
        return locations
    }
}

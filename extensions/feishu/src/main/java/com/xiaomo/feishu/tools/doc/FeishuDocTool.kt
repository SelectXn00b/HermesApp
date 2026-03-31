package com.xiaomo.feishu.tools.doc

/**
 * Aligned with ByteDance official @larksuite/openclaw-lark plugin.
 * MCP doc tools: feishu_fetch_doc, feishu_create_doc, feishu_update_doc
 *
 * Since Android cannot use MCP JSON-RPC, we implement equivalent functionality
 * using direct Feishu Open API calls while matching the official tool names,
 * parameters, and behavior.
 */

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FeishuDocTool"

/**
 * Extract doc_id from URL or return as-is.
 * Supports: https://xxx.feishu.cn/docx/ABC123 → ABC123
 *           https://xxx.larksuite.com/docx/ABC123 → ABC123
 */
fun extractDocId(input: String): String {
    val trimmed = input.trim()
    val regex = Regex("(?:feishu\\.cn|larksuite\\.com)/(?:docx|docs|wiki)/([A-Za-z0-9]+)")
    return regex.find(trimmed)?.groupValues?.get(1) ?: trimmed
}

// ─── feishu_fetch_doc ───────────────────────────────────────────────

// @aligned openclaw-lark v2026.3.30
// NOTE: Official description says "Markdown 格式内容", but on Android we cannot run
// the MCP server that converts docx blocks to Markdown. We use the raw_content API
// instead, which returns plain text. The description below reflects this Android limitation.
/**
 * Fetch document content with optional pagination.
 * Aligned with @larksuite/openclaw-lark feishu_fetch_doc (fetch-doc MCP tool).
 */
class FeishuFetchDocTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_fetch_doc"
    override val description = "获取飞书云文档内容，返回文档标题和纯文本内容（Android 端无 MCP，使用 raw_content API 替代 Markdown 转换）。支持分页获取大文档。"

    override fun isEnabled() = config.enableDocTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val rawDocId = args["doc_id"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: doc_id")
            val docId = extractDocId(rawDocId)
            val offset = (args["offset"] as? Number)?.toInt()
            val limit = (args["limit"] as? Number)?.toInt()

            // Get raw content (Android limitation: no MCP markdown conversion)
            val result = client.get("/open-apis/docx/v1/documents/$docId/raw_content")
            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to fetch document")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val fullContent = data?.get("content")?.asString ?: ""

            // Get document title
            val metaResult = client.get("/open-apis/docx/v1/documents/$docId")
            val title = metaResult.getOrNull()?.getAsJsonObject("data")
                ?.getAsJsonObject("document")?.get("title")?.asString

            // Apply pagination
            val totalLength = fullContent.length
            val start = (offset ?: 0).coerceIn(0, totalLength)
            val end = if (limit != null) (start + limit).coerceIn(start, totalLength) else totalLength
            val content = fullContent.substring(start, end)

            val resultMap = mutableMapOf<String, Any?>(
                "doc_id" to docId,
                "content" to content,
                "total_length" to totalLength
            )
            title?.let { resultMap["title"] = it }
            if (start > 0) resultMap["offset"] = start
            if (end < totalLength) {
                resultMap["has_more"] = true
                resultMap["next_offset"] = end
            }

            ToolResult.success(resultMap)
        } catch (e: Exception) {
            Log.e(TAG, "feishu_fetch_doc failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    // @aligned openclaw-lark v2026.3.30
    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "doc_id" to PropertySchema("string", "文档 ID 或 URL（支持自动解析）"),
                    "offset" to PropertySchema("integer", "字符偏移量（可选，默认0）。用于大文档分页获取。"),
                    "limit" to PropertySchema("integer", "返回的最大字符数（可选）。仅在用户明确要求分页时使用。")
                ),
                required = listOf("doc_id")
            )
        )
    )
}

// ─── feishu_create_doc ──────────────────────────────────────────────

// @aligned openclaw-lark v2026.3.30
/**
 * Create a new Feishu document from markdown content.
 * Supports async task_id polling (aligned with official create-doc MCP tool).
 */
class FeishuCreateDocTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_create_doc"
    override val description = "从 Markdown 创建云文档（支持异步 task_id 查询）"

    override fun isEnabled() = config.enableDocTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val taskId = args["task_id"] as? String
            val markdown = args["markdown"] as? String
            val title = args["title"] as? String
            val folderToken = args["folder_token"] as? String
            val wikiNode = args["wiki_node"] as? String
            val wikiSpace = args["wiki_space"] as? String

            // @aligned openclaw-lark v2026.3.30 - task_id polling
            if (taskId != null) {
                return@withContext pollTaskStatus(client, taskId)
            }

            // @aligned openclaw-lark v2026.3.30 - require both markdown and title when no task_id
            if (markdown == null || title == null) {
                return@withContext ToolResult.error("create-doc：未提供 task_id 时，至少需要提供 markdown 和 title")
            }

            // Validate mutually exclusive location params
            val locationCount = listOfNotNull(folderToken, wikiNode, wikiSpace).size
            if (locationCount > 1) {
                return@withContext ToolResult.error("create-doc：folder_token / wiki_node / wiki_space 三者互斥，请只提供一个")
            }

            // Step 1: Create document
            val createBody = mutableMapOf<String, Any>("title" to title)
            if (folderToken != null) {
                createBody["folder_token"] = folderToken
            }

            val createResult = client.post("/open-apis/docx/v1/documents", createBody)
            if (createResult.isFailure) {
                return@withContext ToolResult.error(
                    createResult.exceptionOrNull()?.message ?: "Failed to create document"
                )
            }

            val docData = createResult.getOrNull()?.getAsJsonObject("data")
                ?.getAsJsonObject("document")
            val docId = docData?.get("document_id")?.asString
                ?: return@withContext ToolResult.error("No document_id in response")

            // Step 2: If wiki, move to wiki space
            if (wikiNode != null || wikiSpace != null) {
                val moveBody = mutableMapOf<String, Any>(
                    "obj_type" to "docx",
                    "obj_token" to docId
                )
                if (wikiNode != null) {
                    val nodeToken = extractDocId(wikiNode)
                    moveBody["parent_wiki_token"] = nodeToken
                }

                val spaceId = if (wikiSpace == "my_library") {
                    // Get user's personal wiki space
                    val spacesResult = client.get("/open-apis/wiki/v2/spaces?page_size=1")
                    spacesResult.getOrNull()?.getAsJsonObject("data")
                        ?.getAsJsonArray("items")?.firstOrNull()?.asJsonObject
                        ?.get("space_id")?.asString
                } else {
                    wikiSpace
                }

                if (spaceId != null) {
                    client.post("/open-apis/wiki/v2/spaces/$spaceId/nodes", moveBody)
                }
            }

            // Step 3: If markdown content, write to document
            if (markdown.isNotEmpty()) {
                writeMarkdownToDoc(client, docId, markdown)
            }

            Log.d(TAG, "Document created: $docId")
            ToolResult.success(mapOf(
                "doc_id" to docId,
                "title" to title,
                "url" to "https://feishu.cn/docx/$docId"
            ))
        } catch (e: Exception) {
            Log.e(TAG, "feishu_create_doc failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    // @aligned openclaw-lark v2026.3.30
    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "markdown" to PropertySchema("string", "Markdown 内容"),
                    "title" to PropertySchema("string", "文档标题"),
                    "folder_token" to PropertySchema("string", "父文件夹 token（可选）"),
                    "wiki_node" to PropertySchema("string", "知识库节点 token 或 URL（可选，传入则在该节点下创建文档）"),
                    "wiki_space" to PropertySchema("string", "知识空间 ID（可选，特殊值 my_library）"),
                    "task_id" to PropertySchema("string", "异步任务 ID。提供此参数将查询任务状态而非创建新文档")
                ),
                required = emptyList()
            )
        )
    )
}

// ─── feishu_update_doc ──────────────────────────────────────────────

// @aligned openclaw-lark v2026.3.30
/**
 * Update a Feishu document with 7 modes and selection mechanisms.
 * Supports async task_id polling (aligned with official update-doc MCP tool).
 *
 * Modes: overwrite, append, replace_range, replace_all, insert_before, insert_after, delete_range
 * Selection: selection_with_ellipsis ("start...end"), selection_by_title ("## Title")
 */
class FeishuUpdateDocTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_update_doc"
    override val description = "更新云文档（overwrite/append/replace_range/replace_all/insert_before/insert_after/delete_range，支持异步 task_id 查询）"

    override fun isEnabled() = config.enableDocTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val taskId = args["task_id"] as? String
            val rawDocId = args["doc_id"] as? String

            // @aligned openclaw-lark v2026.3.30 - task_id polling
            if (taskId != null) {
                return@withContext pollTaskStatus(client, taskId)
            }

            // @aligned openclaw-lark v2026.3.30 - doc_id required when no task_id
            if (rawDocId == null) {
                return@withContext ToolResult.error("update-doc：未提供 task_id 时必须提供 doc_id")
            }
            val docId = extractDocId(rawDocId)
            val mode = args["mode"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: mode")
            val markdown = args["markdown"] as? String
            val selectionEllipsis = args["selection_with_ellipsis"] as? String
            val selectionTitle = args["selection_by_title"] as? String
            val newTitle = args["new_title"] as? String

            // Validate mode
            val validModes = listOf("overwrite", "append", "replace_range", "replace_all",
                "insert_before", "insert_after", "delete_range")
            if (mode !in validModes) {
                return@withContext ToolResult.error("Invalid mode: $mode. Must be one of: ${validModes.joinToString()}")
            }

            // @aligned openclaw-lark v2026.3.30 - selection validation
            val needSelection = mode == "replace_range" || mode == "insert_before" ||
                mode == "insert_after" || mode == "delete_range"
            if (needSelection) {
                val hasEllipsis = selectionEllipsis != null
                val hasTitle = selectionTitle != null
                if ((hasEllipsis && hasTitle) || (!hasEllipsis && !hasTitle)) {
                    return@withContext ToolResult.error(
                        "update-doc：mode 为 replace_range/insert_before/insert_after/delete_range 时，" +
                        "selection_with_ellipsis 与 selection_by_title 必须二选一"
                    )
                }
            }

            // @aligned openclaw-lark v2026.3.30 - markdown required except delete_range
            val needMarkdown = mode != "delete_range"
            if (needMarkdown && markdown == null) {
                return@withContext ToolResult.error("update-doc：mode=$mode 时必须提供 markdown")
            }

            // Update title if requested
            if (newTitle != null) {
                // Feishu doesn't have a direct title update API for docx;
                // title is the first page block's text. We handle it via content manipulation.
            }

            val result = when (mode) {
                "overwrite" -> doOverwrite(docId, markdown!!)
                "append" -> doAppend(docId, markdown!!)
                "replace_range" -> doReplaceRange(docId, markdown!!, selectionEllipsis, selectionTitle)
                "replace_all" -> doReplaceAll(docId, markdown!!, selectionEllipsis, selectionTitle)
                "insert_before" -> doInsertRelative(docId, markdown!!, selectionEllipsis, selectionTitle, before = true)
                "insert_after" -> doInsertRelative(docId, markdown!!, selectionEllipsis, selectionTitle, before = false)
                "delete_range" -> doDeleteRange(docId, selectionEllipsis, selectionTitle)
                else -> return@withContext ToolResult.error("Unknown mode: $mode")
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "feishu_update_doc failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    /**
     * Overwrite: clear document and write new markdown content.
     */
    private suspend fun doOverwrite(docId: String, markdown: String): ToolResult {
        clearDocumentContent(client, docId)
        writeMarkdownToDoc(client, docId, markdown)
        return ToolResult.success(mapOf("doc_id" to docId, "mode" to "overwrite"))
    }

    /**
     * Append: add markdown content to end of document.
     */
    private suspend fun doAppend(docId: String, markdown: String): ToolResult {
        writeMarkdownToDoc(client, docId, markdown)
        return ToolResult.success(mapOf("doc_id" to docId, "mode" to "append"))
    }

    /**
     * Replace range: find selection in content, replace with new markdown.
     */
    private suspend fun doReplaceRange(
        docId: String, markdown: String,
        ellipsis: String?, title: String?
    ): ToolResult {
        val content = readRawContent(docId)
        val range = findSelection(content, ellipsis, title)
            ?: return ToolResult.error("Selection not found in document")

        val newContent = content.substring(0, range.first) + markdown + content.substring(range.second)
        clearDocumentContent(client, docId)
        writeMarkdownToDoc(client, docId, newContent)
        return ToolResult.success(mapOf("doc_id" to docId, "mode" to "replace_range"))
    }

    /**
     * Replace all: find all occurrences and replace.
     * Uses selection_with_ellipsis as the search text (no "..." splitting).
     */
    private suspend fun doReplaceAll(
        docId: String, markdown: String,
        ellipsis: String?, title: String?
    ): ToolResult {
        val content = readRawContent(docId)
        val searchText = ellipsis ?: title
            ?: return ToolResult.error("Selection required for replace_all")

        if (!content.contains(searchText)) {
            return ToolResult.error("Text not found in document: $searchText")
        }

        val newContent = content.replace(searchText, markdown)
        clearDocumentContent(client, docId)
        writeMarkdownToDoc(client, docId, newContent)

        val count = content.split(searchText).size - 1
        return ToolResult.success(mapOf("doc_id" to docId, "mode" to "replace_all", "replacements" to count))
    }

    /**
     * Insert before/after: find selection and insert markdown relative to it.
     */
    private suspend fun doInsertRelative(
        docId: String, markdown: String,
        ellipsis: String?, title: String?,
        before: Boolean
    ): ToolResult {
        val content = readRawContent(docId)
        val range = findSelection(content, ellipsis, title)
            ?: return ToolResult.error("Selection not found in document")

        val insertPos = if (before) range.first else range.second
        val newContent = content.substring(0, insertPos) + markdown + content.substring(insertPos)
        clearDocumentContent(client, docId)
        writeMarkdownToDoc(client, docId, newContent)

        val mode = if (before) "insert_before" else "insert_after"
        return ToolResult.success(mapOf("doc_id" to docId, "mode" to mode))
    }

    /**
     * Delete range: find selection and remove it.
     */
    private suspend fun doDeleteRange(
        docId: String,
        ellipsis: String?, title: String?
    ): ToolResult {
        val content = readRawContent(docId)
        val range = findSelection(content, ellipsis, title)
            ?: return ToolResult.error("Selection not found in document")

        val newContent = content.substring(0, range.first) + content.substring(range.second)
        clearDocumentContent(client, docId)
        writeMarkdownToDoc(client, docId, newContent)
        return ToolResult.success(mapOf("doc_id" to docId, "mode" to "delete_range"))
    }

    /**
     * Read document raw content.
     */
    private suspend fun readRawContent(docId: String): String {
        val result = client.get("/open-apis/docx/v1/documents/$docId/raw_content")
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: Exception("Failed to read document")
        }
        return result.getOrNull()?.getAsJsonObject("data")?.get("content")?.asString ?: ""
    }

    /**
     * Find selection range in content.
     *
     * selection_with_ellipsis: "start text...end text" → find range from start of "start text" to end of "end text"
     * selection_by_title: "## Title" → find from title line to next same-or-higher level heading
     *
     * @return Pair(startIndex, endIndex) or null if not found
     */
    private fun findSelection(content: String, ellipsis: String?, title: String?): Pair<Int, Int>? {
        if (ellipsis != null) {
            return findByEllipsis(content, ellipsis)
        }
        if (title != null) {
            return findByTitle(content, title)
        }
        return null
    }

    /**
     * Find range using "start content...end content" pattern.
     */
    private fun findByEllipsis(content: String, selector: String): Pair<Int, Int>? {
        val parts = selector.split("...")
        if (parts.size < 2) {
            // No ellipsis — treat as literal search
            val idx = content.indexOf(selector)
            if (idx < 0) return null
            return idx to idx + selector.length
        }

        val startText = parts.first().trim()
        val endText = parts.last().trim()

        if (startText.isEmpty() || endText.isEmpty()) {
            // Degenerate: one side empty, treat as literal
            val literal = selector.replace("...", "").trim()
            val idx = content.indexOf(literal)
            if (idx < 0) return null
            return idx to idx + literal.length
        }

        val startIdx = content.indexOf(startText)
        if (startIdx < 0) return null

        val endIdx = content.indexOf(endText, startIdx + startText.length)
        if (endIdx < 0) return null

        return startIdx to endIdx + endText.length
    }

    /**
     * Find range by heading title.
     * Finds the heading line and extends to the next heading of same or higher level (or end of content).
     */
    private fun findByTitle(content: String, titleSelector: String): Pair<Int, Int>? {
        val trimmedTitle = titleSelector.trim()

        // Determine heading level from selector
        val selectorLevel = trimmedTitle.takeWhile { it == '#' }.length.coerceAtLeast(1)
        val titleText = trimmedTitle.removePrefix("#".repeat(selectorLevel)).trim()

        val lines = content.split("\n")
        var startLineIdx = -1
        var startCharIdx = 0
        var charOffset = 0

        // Find the heading line
        for ((i, line) in lines.withIndex()) {
            val trimmedLine = line.trim()
            val lineLevel = trimmedLine.takeWhile { it == '#' }.length
            val lineText = trimmedLine.removePrefix("#".repeat(lineLevel)).trim()

            if (lineLevel > 0 && lineText == titleText) {
                startLineIdx = i
                startCharIdx = charOffset
                break
            }
            charOffset += line.length + 1 // +1 for \n
        }

        if (startLineIdx < 0) return null

        // Find end: next heading of same or higher level, or end of content
        charOffset = startCharIdx
        for (i in startLineIdx until lines.size) {
            charOffset += lines[i].length + 1
        }
        var endCharIdx = charOffset // default: end of content

        charOffset = startCharIdx + lines[startLineIdx].length + 1
        for (i in (startLineIdx + 1) until lines.size) {
            val trimmedLine = lines[i].trim()
            val lineLevel = trimmedLine.takeWhile { it == '#' }.length
            if (lineLevel in 1..selectorLevel) {
                endCharIdx = charOffset
                break
            }
            charOffset += lines[i].length + 1
        }

        return startCharIdx to endCharIdx
    }

    // @aligned openclaw-lark v2026.3.30
    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "doc_id" to PropertySchema("string", "文档 ID 或 URL"),
                    "markdown" to PropertySchema("string", "Markdown 内容"),
                    "mode" to PropertySchema("string", "更新模式（必填）",
                        enum = listOf("overwrite", "append", "replace_range", "replace_all",
                            "insert_before", "insert_after", "delete_range")),
                    "selection_with_ellipsis" to PropertySchema("string",
                        "定位表达式：开头内容...结尾内容（与 selection_by_title 二选一）"),
                    "selection_by_title" to PropertySchema("string",
                        "标题定位：例如 ## 章节标题（与 selection_with_ellipsis 二选一）"),
                    "new_title" to PropertySchema("string", "新的文档标题（可选）"),
                    "task_id" to PropertySchema("string", "异步任务 ID，用于查询任务状态")
                ),
                required = listOf("mode")
            )
        )
    )
}

// ─── Async task polling ─────────────────────────────────────────────

// @aligned openclaw-lark v2026.3.30
/**
 * Poll an async import task status.
 * Used by create_doc and update_doc when task_id is provided.
 * The official MCP plugin delegates to the same endpoint for both tools.
 */
internal suspend fun pollTaskStatus(client: FeishuClient, taskId: String): ToolResult {
    val result = client.get("/open-apis/docx/v1/import_tasks/$taskId")
    if (result.isFailure) {
        return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to poll task status")
    }
    val data = result.getOrNull()?.getAsJsonObject("data")
    val task = data?.getAsJsonObject("task")
    return if (task != null) {
        val status = task.get("status")?.asInt ?: -1
        val resultMap = mutableMapOf<String, Any?>(
            "task_id" to taskId,
            "status" to status
        )
        task.get("url")?.asString?.let { resultMap["url"] = it }
        task.get("doc_id")?.asString?.let { resultMap["doc_id"] = it }
        task.get("type")?.asString?.let { resultMap["type"] = it }
        ToolResult.success(resultMap)
    } else {
        ToolResult.error("No task data returned for task_id=$taskId")
    }
}

// ─── Shared helpers ─────────────────────────────────────────────────

/**
 * Write markdown content to a document using Feishu's batch_update API.
 * Converts markdown to text elements for insertion.
 */
internal suspend fun writeMarkdownToDoc(client: FeishuClient, docId: String, markdown: String) {
    // Try the document.convert API first for proper markdown → blocks conversion
    try {
        val converted = chunkedConvertMarkdown(client, markdown)
        if (converted.blocks.size() > 0) {
            val sorted = sortBlocksByFirstLevel(converted.blocks, converted.firstLevelBlockIds)
            val (cleaned, _) = cleanBlocksForInsert(sorted)
            if (cleaned.size() > 0) {
                chunkedInsertBlocks(client, docId, cleaned)
                return
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Markdown convert API failed, falling back to text insert: ${e.message}")
    }

    // Fallback: insert as plain text elements
    val body = mapOf(
        "requests" to listOf(
            mapOf(
                "insert_text_elements" to mapOf(
                    "location" to mapOf("zone_id" to ""),
                    "elements" to listOf(
                        mapOf("text_run" to mapOf("content" to markdown))
                    )
                )
            )
        )
    )
    client.post("/open-apis/docx/v1/documents/$docId/batch_update", body)
}

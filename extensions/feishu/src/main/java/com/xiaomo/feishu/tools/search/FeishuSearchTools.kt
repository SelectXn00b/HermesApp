package com.xiaomo.feishu.tools.search

/**
 * Feishu Search tool set.
 * Aligned with ByteDance official @larksuite/openclaw-lark plugin.
 * - feishu_search_doc_wiki: unified doc + wiki search
 */

import android.util.Log
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "FeishuSearchTools"

/**
 * Convert ISO 8601 time range fields to seconds-level timestamp strings.
 * @aligned openclaw-lark v2026.3.30
 */
private fun convertTimeRange(timeRange: Map<*, *>): Map<String, String> {
    val result = mutableMapOf<String, String>()
    (timeRange["start"] as? String)?.let {
        result["start"] = try {
            ZonedDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toEpochSecond().toString()
        } catch (e: Exception) { it }
    }
    (timeRange["end"] as? String)?.let {
        result["end"] = try {
            ZonedDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toEpochSecond().toString()
        } catch (e: Exception) { it }
    }
    return result
}

// ─── feishu_search_doc_wiki ────────────────────────────────────────

// @aligned openclaw-lark v2026.3.30
class FeishuSearchDocWikiTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_search_doc_wiki"
    override val description = "【以用户身份】飞书文档与 Wiki 统一搜索工具。同时搜索云空间文档和知识库 Wiki。Actions: search。" +
        "【重要】query 参数是搜索关键词（可选），filter 参数可选。" +
        "【重要】filter 不传时，搜索所有文档和 Wiki；传了则同时对文档和 Wiki 应用相同的过滤条件。" +
        "【重要】支持按文档类型、创建者、创建时间、打开时间等多维度筛选。" +
        "【重要】返回结果包含标题和摘要高亮（<h>标签包裹匹配关键词）。"

    override fun isEnabled() = config.enableSearchTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String ?: "search"

            if (action != "search") {
                return@withContext ToolResult.error("Unknown action: $action. Only 'search' is supported.")
            }

            // query is optional, default to empty string
            val query = args["query"] as? String ?: ""

            if (query.length > 50) {
                return@withContext ToolResult.error("query must be at most 50 characters")
            }

            val body = mutableMapOf<String, Any>("query" to query)

            // Build filter and apply as both doc_filter and wiki_filter
            @Suppress("UNCHECKED_CAST")
            val filter = args["filter"] as? Map<String, Any?>
            if (filter != null) {
                val filterObj = mutableMapOf<String, Any>()
                @Suppress("UNCHECKED_CAST")
                (filter["doc_types"] as? List<String>)?.let { filterObj["doc_types"] = it }
                @Suppress("UNCHECKED_CAST")
                (filter["creator_ids"] as? List<String>)?.let { filterObj["creator_ids"] = it }
                (filter["only_title"] as? Boolean)?.let { filterObj["only_title"] = it }
                (filter["sort_type"] as? String)?.let { filterObj["sort_type"] = it }
                @Suppress("UNCHECKED_CAST")
                (filter["open_time"] as? Map<*, *>)?.let { filterObj["open_time"] = convertTimeRange(it) }
                @Suppress("UNCHECKED_CAST")
                (filter["create_time"] as? Map<*, *>)?.let { filterObj["create_time"] = convertTimeRange(it) }
                // Same filter applied to both doc_filter and wiki_filter
                body["doc_filter"] = HashMap(filterObj)
                body["wiki_filter"] = HashMap(filterObj)
            } else {
                // API requires both filters even when empty
                body["doc_filter"] = emptyMap<String, Any>()
                body["wiki_filter"] = emptyMap<String, Any>()
            }

            (args["page_token"] as? String)?.let { body["page_token"] = it }
            (args["page_size"] as? Number)?.let { body["page_size"] = it.toInt() }

            // Correct endpoint: POST /open-apis/search/v2/doc_wiki/search
            val result = client.post("/open-apis/search/v2/doc_wiki/search", body)
            if (result.isFailure) {
                return@withContext ToolResult.error(
                    result.exceptionOrNull()?.message ?: "Failed to search documents"
                )
            }

            ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
        } catch (e: Exception) {
            Log.e(TAG, "feishu_search_doc_wiki failed", e)
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
                    "action" to PropertySchema("string", "操作类型（目前仅支持 search）", enum = listOf("search")),
                    "query" to PropertySchema("string", "搜索关键词（可选，最多 50 字符）。不传或传空字符串表示空搜"),
                    "filter" to PropertySchema("object", "搜索过滤条件（可选）。不传则搜索所有文档和 Wiki", properties = mapOf(
                        "doc_types" to PropertySchema("array", "文档类型列表",
                            items = PropertySchema("string", "文档类型",
                                enum = listOf("DOC", "SHEET", "BITABLE", "MINDNOTE", "FILE", "WIKI", "DOCX", "FOLDER", "CATALOG", "SLIDES", "SHORTCUT"))),
                        "creator_ids" to PropertySchema("array", "创建者 OpenID 列表（最多 20 个）",
                            items = PropertySchema("string", "创建者 open_id")),
                        "only_title" to PropertySchema("boolean", "仅搜索标题（默认 false）"),
                        "sort_type" to PropertySchema("string", "排序方式",
                            enum = listOf("DEFAULT_TYPE", "OPEN_TIME", "EDIT_TIME", "EDIT_TIME_ASC", "CREATE_TIME")),
                        "open_time" to PropertySchema("object", "打开时间范围 {start, end}（ISO 8601 格式）"),
                        "create_time" to PropertySchema("object", "创建时间范围 {start, end}（ISO 8601 格式）")
                    )),
                    "page_token" to PropertySchema("string", "分页标记"),
                    "page_size" to PropertySchema("integer", "每页数量（默认 15，最大 20）")
                ),
                required = emptyList()
            )
        )
    )
}

// ─── Aggregator ────────────────────────────────────────────────────

class FeishuSearchTools(config: FeishuConfig, client: FeishuClient) {
    private val searchDocWikiTool = FeishuSearchDocWikiTool(config, client)

    fun getAllTools(): List<FeishuToolBase> = listOf(searchDocWikiTool)

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

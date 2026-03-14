package com.xiaomo.feishu.tools.bitable

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel tool definitions.
 */


import android.util.Log
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 飞书多维表格工具集
 * 对齐 OpenClaw src/bitable-tools
 */
class FeishuBitableTools(config: FeishuConfig, client: FeishuClient) {
    private val createTool = BitableCreateTool(config, client)
    private val readTool = BitableReadTool(config, client)
    private val updateTool = BitableUpdateTool(config, client)
    private val deleteTool = BitableDeleteTool(config, client)
    private val queryTool = BitableQueryTool(config, client)
    private val getMetaTool = BitableGetMetaTool(config, client)

    fun getAllTools(): List<FeishuToolBase> {
        return listOf(getMetaTool, createTool, readTool, updateTool, deleteTool, queryTool)
    }

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

/**
 * 创建记录工具
 */
class BitableCreateTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_bitable_create"
    override val description = "在飞书多维表格中创建记录"

    override fun isEnabled() = config.enableBitableTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val appToken = args["app_token"] as? String ?: return@withContext ToolResult.error("Missing app_token")
            val tableId = args["table_id"] as? String ?: return@withContext ToolResult.error("Missing table_id")
            @Suppress("UNCHECKED_CAST")
            val fields = args["fields"] as? Map<String, Any?> ?: return@withContext ToolResult.error("Missing fields")

            val body = mapOf("fields" to fields)

            val result = client.post("/open-apis/bitable/v1/apps/$appToken/tables/$tableId/records", body)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val record = data?.getAsJsonObject("record")
            val recordId = record?.get("record_id")?.asString
                ?: return@withContext ToolResult.error("Missing record_id")

            Log.d("BitableCreateTool", "Record created: $recordId")
            ToolResult.success(mapOf(
                "record_id" to recordId,
                "app_token" to appToken,
                "table_id" to tableId
            ))

        } catch (e: Exception) {
            Log.e("BitableCreateTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "app_token" to PropertySchema("string", "多维表格appToken"),
                    "table_id" to PropertySchema("string", "数据表ID"),
                    "fields" to PropertySchema("object", "字段值对象，如 {\"字段1\": \"值1\", \"字段2\": \"值2\"}", properties = emptyMap())
                ),
                required = listOf("app_token", "table_id", "fields")
            )
        )
    )
}

/**
 * 读取记录工具
 */
class BitableReadTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_bitable_read"
    override val description = "读取飞书多维表格记录"

    override fun isEnabled() = config.enableBitableTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val appToken = args["app_token"] as? String ?: return@withContext ToolResult.error("Missing app_token")
            val tableId = args["table_id"] as? String ?: return@withContext ToolResult.error("Missing table_id")
            val recordId = args["record_id"] as? String ?: return@withContext ToolResult.error("Missing record_id")

            val result = client.get("/open-apis/bitable/v1/apps/$appToken/tables/$tableId/records/$recordId")

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val record = data?.getAsJsonObject("record")
            val fields = record?.getAsJsonObject("fields")

            Log.d("BitableReadTool", "Record read: $recordId")
            ToolResult.success(mapOf(
                "record_id" to recordId,
                "fields" to (fields?.toString() ?: "{}")
            ))

        } catch (e: Exception) {
            Log.e("BitableReadTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "app_token" to PropertySchema("string", "多维表格appToken"),
                    "table_id" to PropertySchema("string", "数据表ID"),
                    "record_id" to PropertySchema("string", "记录ID")
                ),
                required = listOf("app_token", "table_id", "record_id")
            )
        )
    )
}

/**
 * 更新记录工具
 */
class BitableUpdateTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_bitable_update"
    override val description = "更新飞书多维表格记录"

    override fun isEnabled() = config.enableBitableTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val appToken = args["app_token"] as? String ?: return@withContext ToolResult.error("Missing app_token")
            val tableId = args["table_id"] as? String ?: return@withContext ToolResult.error("Missing table_id")
            val recordId = args["record_id"] as? String ?: return@withContext ToolResult.error("Missing record_id")
            @Suppress("UNCHECKED_CAST")
            val fields = args["fields"] as? Map<String, Any?> ?: return@withContext ToolResult.error("Missing fields")

            val body = mapOf("fields" to fields)

            val result = client.put("/open-apis/bitable/v1/apps/$appToken/tables/$tableId/records/$recordId", body)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            Log.d("BitableUpdateTool", "Record updated: $recordId")
            ToolResult.success(mapOf("record_id" to recordId))

        } catch (e: Exception) {
            Log.e("BitableUpdateTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "app_token" to PropertySchema("string", "多维表格appToken"),
                    "table_id" to PropertySchema("string", "数据表ID"),
                    "record_id" to PropertySchema("string", "记录ID"),
                    "fields" to PropertySchema("object", "要更新的字段值对象", properties = emptyMap())
                ),
                required = listOf("app_token", "table_id", "record_id", "fields")
            )
        )
    )
}

/**
 * 删除记录工具
 */
class BitableDeleteTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_bitable_delete"
    override val description = "删除飞书多维表格记录"

    override fun isEnabled() = config.enableBitableTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val appToken = args["app_token"] as? String ?: return@withContext ToolResult.error("Missing app_token")
            val tableId = args["table_id"] as? String ?: return@withContext ToolResult.error("Missing table_id")
            val recordId = args["record_id"] as? String ?: return@withContext ToolResult.error("Missing record_id")

            val result = client.delete("/open-apis/bitable/v1/apps/$appToken/tables/$tableId/records/$recordId")

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            Log.d("BitableDeleteTool", "Record deleted: $recordId")
            ToolResult.success(mapOf("record_id" to recordId))

        } catch (e: Exception) {
            Log.e("BitableDeleteTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "app_token" to PropertySchema("string", "多维表格appToken"),
                    "table_id" to PropertySchema("string", "数据表ID"),
                    "record_id" to PropertySchema("string", "记录ID")
                ),
                required = listOf("app_token", "table_id", "record_id")
            )
        )
    )
}

/**
 * 查询记录工具
 */
class BitableQueryTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_bitable_query"
    override val description = "查询飞书多维表格记录。注意：如果有 wiki URL，先用 feishu_bitable_get_meta 解析出正确的 app_token。"

    override fun isEnabled() = config.enableBitableTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val appToken = args["app_token"] as? String ?: return@withContext ToolResult.error("Missing app_token")
            val tableId = args["table_id"] as? String ?: return@withContext ToolResult.error("Missing table_id")
            val filter = args["filter"] as? String
            val pageSize = (args["page_size"] as? Number)?.toInt() ?: 100

            val params = mutableMapOf("page_size" to pageSize.toString())
            if (filter != null) {
                params["filter"] = filter
            }

            val path = "/open-apis/bitable/v1/apps/$appToken/tables/$tableId/records?" +
                    params.entries.joinToString("&") { "${it.key}=${it.value}" }
            val result = client.get(path)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val items = data?.getAsJsonArray("items") ?: return@withContext ToolResult.success(mapOf("records" to emptyList<Map<String, Any>>()))

            val records = items.map { item ->
                val obj = item.asJsonObject
                mapOf(
                    "record_id" to (obj.get("record_id")?.asString ?: ""),
                    "fields" to (obj.getAsJsonObject("fields")?.toString() ?: "{}")
                )
            }

            Log.d("BitableQueryTool", "Records queried: ${records.size}")
            ToolResult.success(mapOf(
                "app_token" to appToken,
                "table_id" to tableId,
                "records" to records
            ))

        } catch (e: Exception) {
            Log.e("BitableQueryTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "app_token" to PropertySchema("string", "多维表格appToken"),
                    "table_id" to PropertySchema("string", "数据表ID"),
                    "filter" to PropertySchema("string", "筛选条件（可选）"),
                    "page_size" to PropertySchema("number", "每页数量（默认100）")
                ),
                required = listOf("app_token", "table_id")
            )
        )
    )
}

/**
 * 解析多维表格 URL，获取 app_token、table_id 和表列表。
 * 对齐 OpenClaw feishu_bitable_get_meta 工具。
 *
 * 支持两种 URL 格式：
 * - /base/XXX?table=YYY  → 直接提取 app_token
 * - /wiki/XXX?table=YYY  → 先通过 wiki API 获取 obj_token 作为 app_token
 *
 * 重要：wiki URL 中的 token 是 node_token，不能直接用作 app_token！
 * 必须通过 /open-apis/wiki/v2/spaces/get_node 获取真正的 obj_token。
 */
class BitableGetMetaTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_bitable_get_meta"
    override val description = "Parse a Bitable URL and get app_token, table_id, and table list. Use this FIRST when given a /wiki/ or /base/ URL. Supports both /base/XXX?table=YYY and /wiki/XXX?table=YYY formats."

    override fun isEnabled() = config.enableBitableTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val url = args["url"] as? String ?: return@withContext ToolResult.error("Missing url parameter")

            // Parse URL to extract token and table_id
            val (rawToken, tableId, isWiki) = parseUrl(url)
                ?: return@withContext ToolResult.error("Invalid URL format. Expected /wiki/XXX or /base/XXX")

            // Resolve app_token
            val appToken = if (isWiki) {
                // Wiki URL: need to resolve node_token → obj_token via API
                resolveWikiToken(rawToken)
                    ?: return@withContext ToolResult.error("Failed to resolve wiki node_token '$rawToken' to bitable app_token. Check if the wiki page exists and bot has access.")
            } else {
                // Base URL: token is the app_token directly
                rawToken
            }

            // Get table list
            val tables = getTableList(appToken)

            val result = mutableMapOf<String, Any?>(
                "app_token" to appToken,
                "table_id" to tableId
            )
            if (isWiki) {
                result["wiki_token"] = rawToken
                result["note"] = "Resolved from wiki URL. Use app_token (not wiki_token) for bitable API calls."
            }
            if (tables != null) {
                result["tables"] = tables
            }

            Log.d("BitableGetMetaTool", "Resolved: app_token=$appToken, table_id=$tableId, tables=${tables?.size ?: 0}")
            ToolResult.success(result)

        } catch (e: Exception) {
            Log.e("BitableGetMetaTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    /**
     * Parse URL to extract token and table_id.
     * Returns Triple(token, tableId?, isWiki) or null if invalid.
     */
    private fun parseUrl(url: String): Triple<String, String?, Boolean>? {
        // Match /wiki/XXX or /base/XXX
        val wikiMatch = Regex("/wiki/([A-Za-z0-9_-]+)").find(url)
        val baseMatch = Regex("/base/([A-Za-z0-9_-]+)").find(url)

        val (token, isWiki) = when {
            wikiMatch != null -> wikiMatch.groupValues[1] to true
            baseMatch != null -> baseMatch.groupValues[1] to false
            else -> return null
        }

        // Extract table_id from query param
        val tableId = Regex("[?&]table=([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1)

        return Triple(token, tableId, isWiki)
    }

    /**
     * Resolve wiki node_token to bitable obj_token via API.
     * POST /open-apis/wiki/v2/spaces/get_node { token: nodeToken }
     */
    private suspend fun resolveWikiToken(nodeToken: String): String? {
        return try {
            val body = com.google.gson.JsonObject().apply {
                addProperty("token", nodeToken)
            }
            val result = client.post("/open-apis/wiki/v2/spaces/get_node", body)
            if (result.isFailure) {
                Log.e("BitableGetMetaTool", "Wiki get_node failed: ${result.exceptionOrNull()?.message}")
                return null
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val node = data?.getAsJsonObject("node")
            val objToken = node?.get("obj_token")?.asString
            val objType = node?.get("obj_type")?.asString

            if (objType != null && objType != "bitable") {
                Log.w("BitableGetMetaTool", "Wiki node is not bitable: obj_type=$objType")
            }

            Log.d("BitableGetMetaTool", "Wiki resolved: node_token=$nodeToken → obj_token=$objToken (type=$objType)")
            objToken
        } catch (e: Exception) {
            Log.e("BitableGetMetaTool", "Failed to resolve wiki token", e)
            null
        }
    }

    /**
     * Get table list for a bitable app.
     * GET /open-apis/bitable/v1/apps/{app_token}/tables
     */
    private suspend fun getTableList(appToken: String): List<Map<String, String>>? {
        return try {
            val result = client.get("/open-apis/bitable/v1/apps/$appToken/tables")
            if (result.isFailure) {
                Log.w("BitableGetMetaTool", "Failed to list tables: ${result.exceptionOrNull()?.message}")
                return null
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val items = data?.getAsJsonArray("items")
            items?.map { item ->
                val obj = item.asJsonObject
                mapOf(
                    "table_id" to (obj.get("table_id")?.asString ?: ""),
                    "name" to (obj.get("name")?.asString ?: ""),
                    "revision" to (obj.get("revision")?.asString ?: "")
                )
            }
        } catch (e: Exception) {
            Log.e("BitableGetMetaTool", "Failed to list tables", e)
            null
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "url" to PropertySchema("string", "Bitable URL. Supports both formats: /base/XXX?table=YYY or /wiki/XXX?table=YYY")
                ),
                required = listOf("url")
            )
        )
    )
}

package com.xiaomo.feishu.tools.bitable

/**
 * OpenClaw Source Reference:
 * - @larksuite/openclaw-lark bitable tools
 *
 * AndroidForClaw adaptation: Feishu bitable tool definitions.
 * Each tool corresponds to one API resource with multiple actions.
 */

import android.util.Log
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────
// Aggregator
// ─────────────────────────────────────────────────────────────

class FeishuBitableTools(config: FeishuConfig, client: FeishuClient) {
    private val appTool = FeishuBitableAppTool(config, client)
    private val tableTool = FeishuBitableAppTableTool(config, client)
    private val fieldTool = FeishuBitableAppTableFieldTool(config, client)
    private val recordTool = FeishuBitableAppTableRecordTool(config, client)
    private val viewTool = FeishuBitableAppTableViewTool(config, client)

    fun getAllTools(): List<FeishuToolBase> {
        return listOf(appTool, tableTool, fieldTool, recordTool, viewTool)
    }

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

// ─────────────────────────────────────────────────────────────
// Helper: build query string from param pairs
// ─────────────────────────────────────────────────────────────

private fun buildQuery(vararg pairs: Pair<String, Any?>): String {
    val parts = pairs.mapNotNull { (k, v) ->
        if (v != null) "$k=$v" else null
    }
    return if (parts.isNotEmpty()) "?" + parts.joinToString("&") else ""
}

// ─────────────────────────────────────────────────────────────
// 1. FeishuBitableAppTool — 多维表格应用管理
// ─────────────────────────────────────────────────────────────

class FeishuBitableAppTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    override val name = "feishu_bitable_app"
    override val description =
        "【以用户身份】飞书多维表格应用管理工具。当用户要求创建/查询/管理多维表格时使用。" +
        "Actions: create（创建多维表格）, get（获取多维表格元数据）, list（列出多维表格）, " +
        "patch（更新元数据）, copy（复制多维表格）。"

    override fun isEnabled() = config.enableBitableTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            when (action) {
                // @aligned openclaw-lark v2026.3.30
                "create" -> {
                    val name = args["name"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: name")
                    val folderToken = args["folder_token"] as? String
                    val body = mutableMapOf<String, Any>("name" to name)
                    if (folderToken != null) body["folder_token"] = folderToken

                    val result = client.post("/open-apis/bitable/v1/apps", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTool", "App created")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                "get" -> {
                    val appToken = args["app_token"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: app_token")

                    val result = client.get("/open-apis/bitable/v1/apps/$appToken")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTool", "App fetched: $appToken")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // Uses Drive API to list bitable type files
                "list" -> {
                    val folderToken = args["folder_token"] as? String
                    val pageSize = (args["page_size"] as? Number)?.toInt()
                    val pageToken = args["page_token"] as? String

                    val query = buildQuery(
                        "folder_token" to (folderToken ?: ""),
                        "page_size" to pageSize,
                        "page_token" to pageToken
                    )
                    val result = client.get("/open-apis/drive/v1/files$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTool", "Apps listed via Drive API")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                "patch" -> {
                    val appToken = args["app_token"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: app_token")
                    val name = args["name"] as? String
                    val isAdvanced = args["is_advanced"] as? Boolean

                    val body = mutableMapOf<String, Any>()
                    if (name != null) body["name"] = name
                    if (isAdvanced != null) body["is_advanced"] = isAdvanced
                    if (body.isEmpty()) return@withContext ToolResult.error("No update fields provided")

                    val result = client.patch("/open-apis/bitable/v1/apps/$appToken", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTool", "App patched: $appToken")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                "copy" -> {
                    val appToken = args["app_token"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: app_token")
                    val name = args["name"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: name")
                    val folderToken = args["folder_token"] as? String

                    val body = mutableMapOf<String, Any>("name" to name)
                    if (folderToken != null) body["folder_token"] = folderToken

                    val result = client.post("/open-apis/bitable/v1/apps/$appToken/copy", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTool", "App copied: $appToken")
                    ToolResult.success(data)
                }

                else -> ToolResult.error("Unknown action: $action. Supported: create, get, list, patch, copy")
            }
        } catch (e: Exception) {
            Log.e("FeishuBitableAppTool", "Failed", e)
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
                    "action" to PropertySchema(
                        "string", "操作类型",
                        enum = listOf("create", "get", "list", "patch", "copy")
                    ),
                    "app_token" to PropertySchema("string", "多维表格 app_token（get/patch/copy 必填）"),
                    "name" to PropertySchema("string", "多维表格名称（create/copy 必填，patch 可选）"),
                    "folder_token" to PropertySchema("string", "文件夹 token（create/copy/list 可选）"),
                    "is_advanced" to PropertySchema("boolean", "是否开启高级权限（patch 可选）"),
                    "page_size" to PropertySchema("number", "分页大小（list 可选）"),
                    "page_token" to PropertySchema("string", "分页标记（list 可选）")
                ),
                required = listOf("action")
            )
        )
    )
}

// ─────────────────────────────────────────────────────────────
// 2. FeishuBitableAppTableTool — 数据表管理
// ─────────────────────────────────────────────────────────────

class FeishuBitableAppTableTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    override val name = "feishu_bitable_app_table"
    override val description =
        "【以用户身份】飞书多维表格数据表管理工具。当用户要求创建/查询/管理数据表时使用。" +
        "Actions: create（创建数据表）, list（列出所有数据表）, patch（更新数据表）, batch_create（批量创建数据表）。"

    override fun isEnabled() = config.enableBitableTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")
            val appToken = args["app_token"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: app_token")

            val basePath = "/open-apis/bitable/v1/apps/$appToken/tables"

            when (action) {
                // @aligned openclaw-lark v2026.3.30
                // Strip property from checkbox (type=7) and URL (type=15) fields
                "create" -> {
                    @Suppress("UNCHECKED_CAST")
                    val table = args["table"] as? Map<String, Any?>
                        ?: return@withContext ToolResult.error("Missing required parameter: table")

                    val tableData = table.toMutableMap()
                    @Suppress("UNCHECKED_CAST")
                    val fields = tableData["fields"] as? List<Map<String, Any?>>
                    if (fields != null) {
                        tableData["fields"] = fields.map { field ->
                            val type = (field["type"] as? Number)?.toInt()
                            if ((type == 7 || type == 15) && field.containsKey("property")) {
                                field.toMutableMap().apply { remove("property") }
                            } else {
                                field
                            }
                        }
                    }

                    val body = mapOf("table" to tableData)
                    val result = client.post(basePath, body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableTool", "Table created in $appToken")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // Pass page_size and page_token as query params
                "list" -> {
                    val pageSize = (args["page_size"] as? Number)?.toInt()
                    val pageToken = args["page_token"] as? String

                    val query = buildQuery(
                        "page_size" to pageSize,
                        "page_token" to pageToken
                    )
                    val result = client.get("$basePath$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableTool", "Tables listed for $appToken")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // name is OPTIONAL
                "patch" -> {
                    val tableId = args["table_id"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: table_id")
                    val tableName = args["name"] as? String

                    val body = mutableMapOf<String, Any>()
                    if (tableName != null) body["name"] = tableName
                    if (body.isEmpty()) return@withContext ToolResult.error("No update fields provided")

                    val result = client.patch("$basePath/$tableId", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableTool", "Table patched: $tableId")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                "batch_create" -> {
                    @Suppress("UNCHECKED_CAST")
                    val tables = args["tables"] as? List<Map<String, Any?>>
                        ?: return@withContext ToolResult.error("Missing required parameter: tables")
                    val body = mapOf("tables" to tables)

                    val result = client.post("$basePath/batch_create", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableTool", "Tables batch created in $appToken")
                    ToolResult.success(data)
                }

                else -> ToolResult.error("Unknown action: $action. Supported: create, list, patch, batch_create")
            }
        } catch (e: Exception) {
            Log.e("FeishuBitableAppTableTool", "Failed", e)
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
                    "action" to PropertySchema(
                        "string", "操作类型",
                        enum = listOf("create", "list", "patch", "batch_create")
                    ),
                    "app_token" to PropertySchema("string", "多维表格 app_token"),
                    "table_id" to PropertySchema("string", "数据表 ID（patch 必填）"),
                    "name" to PropertySchema("string", "数据表名称（patch 可选）"),
                    "table" to PropertySchema(
                        "object", "数据表定义，含 name、default_view_name、fields（create 必填）",
                        properties = mapOf(
                            "name" to PropertySchema("string", "数据表名称"),
                            "default_view_name" to PropertySchema("string", "默认视图名称"),
                            "fields" to PropertySchema("array", "字段列表", items = PropertySchema("object", "字段定义"))
                        )
                    ),
                    "tables" to PropertySchema(
                        "array", "数据表定义数组（batch_create 必填）",
                        items = PropertySchema("object", "数据表定义")
                    ),
                    "page_size" to PropertySchema("number", "分页大小（list 可选）"),
                    "page_token" to PropertySchema("string", "分页标记（list 可选）")
                ),
                required = listOf("action", "app_token")
            )
        )
    )
}

// ─────────────────────────────────────────────────────────────
// 3. FeishuBitableAppTableFieldTool — 字段（列）管理
// ─────────────────────────────────────────────────────────────

class FeishuBitableAppTableFieldTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    override val name = "feishu_bitable_app_table_field"
    override val description =
        "【以用户身份】飞书多维表格字段（列）管理工具。当用户要求创建/查询/更新/删除字段、调整表结构时使用。" +
        "Actions: create（创建字段）, list（列出所有字段）, update（更新字段，支持只传 field_name 改名）, delete（删除字段）。"

    override fun isEnabled() = config.enableBitableTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")
            val appToken = args["app_token"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: app_token")
            val tableId = args["table_id"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: table_id")

            val basePath = "/open-apis/bitable/v1/apps/$appToken/tables/$tableId/fields"

            when (action) {
                // @aligned openclaw-lark v2026.3.30
                // Strip property from checkbox (type=7) and URL (type=15) fields
                "create" -> {
                    val fieldName = args["field_name"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: field_name")
                    val type = (args["type"] as? Number)?.toInt()
                        ?: return@withContext ToolResult.error("Missing required parameter: type")
                    @Suppress("UNCHECKED_CAST")
                    var property = args["property"] as? Map<String, Any?>

                    // Strip property for checkbox (type=7) and URL (type=15) fields
                    if ((type == 7 || type == 15) && property != null) {
                        Log.w("FeishuBitableAppTableFieldTool",
                            "create: ${if (type == 15) "URL" else "Checkbox"} field (type=$type) " +
                            "detected with property. Removing to avoid API error.")
                        property = null
                    }

                    val body = mutableMapOf<String, Any>(
                        "field_name" to fieldName,
                        "type" to type
                    )
                    if (property != null) body["property"] = property

                    val result = client.post(basePath, body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableFieldTool", "Field created: $fieldName")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // Pass view_id, page_size, page_token as query params
                "list" -> {
                    val viewId = args["view_id"] as? String
                    val pageSize = (args["page_size"] as? Number)?.toInt()
                    val pageToken = args["page_token"] as? String

                    val query = buildQuery(
                        "view_id" to viewId,
                        "page_size" to pageSize,
                        "page_token" to pageToken
                    )
                    val result = client.get("$basePath$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableFieldTool", "Fields listed")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // field_name and type are OPTIONAL; auto-query fallback when missing
                "update" -> {
                    val fieldId = args["field_id"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: field_id")
                    var fieldName = args["field_name"] as? String
                    var type = (args["type"] as? Number)?.toInt()
                    @Suppress("UNCHECKED_CAST")
                    var property = args["property"] as? Map<String, Any?>

                    // Auto-query fallback: if type or field_name is missing, fetch current field info
                    if (type == null || fieldName == null) {
                        Log.d("FeishuBitableAppTableFieldTool",
                            "update: missing type or field_name, auto-querying field info")
                        val listResult = client.get("$basePath?page_size=500")
                        if (listResult.isFailure) {
                            return@withContext ToolResult.error(
                                "Failed to auto-query field info: ${listResult.exceptionOrNull()?.message}")
                        }
                        val listData = listResult.getOrNull()
                        val items = listData?.getAsJsonObject("data")
                            ?.getAsJsonArray("items")
                        var found = false
                        if (items != null) {
                            for (item in items) {
                                val obj = item.asJsonObject
                                if (obj.get("field_id")?.asString == fieldId) {
                                    if (fieldName == null) fieldName = obj.get("field_name")?.asString
                                    if (type == null) type = obj.get("type")?.asInt
                                    if (property == null && obj.has("property") && !obj.get("property").isJsonNull) {
                                        @Suppress("UNCHECKED_CAST")
                                        val gson = com.google.gson.Gson()
                                        property = gson.fromJson(obj.get("property"), Map::class.java) as? Map<String, Any?>
                                    }
                                    found = true
                                    break
                                }
                            }
                        }
                        if (!found) {
                            return@withContext ToolResult.error(
                                "field $fieldId does not exist. Use list action to view all fields.")
                        }
                    }

                    val body = mutableMapOf<String, Any>()
                    if (fieldName != null) body["field_name"] = fieldName
                    if (type != null) body["type"] = type
                    if (property != null) body["property"] = property

                    val result = client.put("$basePath/$fieldId", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableFieldTool", "Field updated: $fieldId")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                "delete" -> {
                    val fieldId = args["field_id"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: field_id")

                    val result = client.delete("$basePath/$fieldId")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableFieldTool", "Field deleted: $fieldId")
                    ToolResult.success(data)
                }

                else -> ToolResult.error("Unknown action: $action. Supported: create, list, update, delete")
            }
        } catch (e: Exception) {
            Log.e("FeishuBitableAppTableFieldTool", "Failed", e)
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
                    "action" to PropertySchema(
                        "string", "操作类型",
                        enum = listOf("create", "list", "update", "delete")
                    ),
                    "app_token" to PropertySchema("string", "多维表格 app_token"),
                    "table_id" to PropertySchema("string", "数据表 ID"),
                    "field_id" to PropertySchema("string", "字段 ID（update/delete 必填）"),
                    "field_name" to PropertySchema("string", "字段名称（create 必填，update 可选）"),
                    "type" to PropertySchema("number", "字段类型编号（create 必填，update 可选）。1=文本, 2=数字, 3=单选, 4=多选, 5=日期, 7=复选框, 11=人员, 13=电话, 15=超链接, 17=附件, 18=关联, 20=公式, 21=双向关联, 22=地理位置, 23=群组, 1001=创建时间, 1002=更新时间, 1003=创建人, 1004=更新人"),
                    "property" to PropertySchema("object", "字段属性配置（可选）。注意：复选框(type=7)和超链接(type=15)字段必须省略此参数", properties = emptyMap()),
                    "view_id" to PropertySchema("string", "视图 ID（list 可选）"),
                    "page_size" to PropertySchema("number", "分页大小（list 可选）"),
                    "page_token" to PropertySchema("string", "分页标记（list 可选）")
                ),
                required = listOf("action", "app_token", "table_id")
            )
        )
    )
}

// ─────────────────────────────────────────────────────────────
// 4. FeishuBitableAppTableRecordTool — 记录（行）管理
// ─────────────────────────────────────────────────────────────

class FeishuBitableAppTableRecordTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    override val name = "feishu_bitable_app_table_record"
    override val description =
        "【以用户身份】飞书多维表格记录（行）管理工具。当用户要求创建/查询/更新/删除记录、搜索数据时使用。\n\n" +
        "Actions:\n" +
        "- create（创建单条记录，使用 fields 参数）\n" +
        "- batch_create（批量创建记录，使用 records 数组参数）\n" +
        "- list（列出/搜索记录）\n" +
        "- update（更新记录）\n" +
        "- delete（删除记录）\n" +
        "- batch_update（批量更新）\n" +
        "- batch_delete（批量删除）\n\n" +
        "注意参数区别：\n" +
        "- create 使用 'fields' 对象（单条）\n" +
        "- batch_create 使用 'records' 数组（批量）\n" +
        "- batch_delete 使用 'record_ids' 字符串数组\n" +
        "- list 的 filter 必须是结构化对象（含 conjunction 和 conditions）"

    override fun isEnabled() = config.enableBitableTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")
            val appToken = args["app_token"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: app_token")
            val tableId = args["table_id"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: table_id")

            val basePath = "/open-apis/bitable/v1/apps/$appToken/tables/$tableId/records"

            when (action) {
                // @aligned openclaw-lark v2026.3.30
                // Add user_id_type=open_id query param
                "create" -> {
                    @Suppress("UNCHECKED_CAST")
                    val fields = args["fields"] as? Map<String, Any?>
                        ?: return@withContext ToolResult.error("Missing required parameter: fields")
                    val body = mapOf("fields" to fields)

                    val result = client.post("$basePath?user_id_type=open_id", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableRecordTool", "Record created")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // filter is structured object; page_size/page_token as query params; rest in POST body
                // isEmpty/isNotEmpty auto-fix: add value=[]
                // Add view_id and user_id_type=open_id
                "list" -> {
                    @Suppress("UNCHECKED_CAST")
                    var filter = args["filter"] as? Map<String, Any?>
                    @Suppress("UNCHECKED_CAST")
                    val sort = args["sort"] as? List<Map<String, Any?>>
                    val pageSize = (args["page_size"] as? Number)?.toInt()
                    val pageToken = args["page_token"] as? String
                    @Suppress("UNCHECKED_CAST")
                    val fieldNames = args["field_names"] as? List<String>
                    val automaticFields = args["automatic_fields"] as? Boolean
                    val viewId = args["view_id"] as? String

                    // isEmpty/isNotEmpty auto-fix: auto-add value=[] for these operators
                    if (filter != null) {
                        @Suppress("UNCHECKED_CAST")
                        val conditions = filter["conditions"] as? List<Map<String, Any?>>
                        if (conditions != null) {
                            val fixedConditions = conditions.map { cond ->
                                val op = cond["operator"] as? String
                                if ((op == "isEmpty" || op == "isNotEmpty") && cond["value"] == null) {
                                    cond.toMutableMap().apply { put("value", emptyList<String>()) }
                                } else {
                                    cond
                                }
                            }
                            filter = filter.toMutableMap().apply { put("conditions", fixedConditions) }
                        }
                    }

                    // Build POST body (filter, sort, field_names, automatic_fields, view_id)
                    val body = mutableMapOf<String, Any>()
                    if (viewId != null) body["view_id"] = viewId
                    if (fieldNames != null) body["field_names"] = fieldNames
                    if (filter != null) body["filter"] = filter
                    if (sort != null) body["sort"] = sort
                    if (automaticFields != null) body["automatic_fields"] = automaticFields

                    // page_size/page_token/user_id_type as query params
                    val query = buildQuery(
                        "user_id_type" to "open_id",
                        "page_size" to pageSize,
                        "page_token" to pageToken
                    )

                    val result = client.post("$basePath/search$query", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableRecordTool", "Records listed")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // Add user_id_type=open_id query param
                "update" -> {
                    val recordId = args["record_id"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: record_id")
                    @Suppress("UNCHECKED_CAST")
                    val fields = args["fields"] as? Map<String, Any?>
                        ?: return@withContext ToolResult.error("Missing required parameter: fields")
                    val body = mapOf("fields" to fields)

                    val result = client.put("$basePath/$recordId?user_id_type=open_id", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableRecordTool", "Record updated: $recordId")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                "delete" -> {
                    val recordId = args["record_id"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: record_id")

                    val result = client.delete("$basePath/$recordId")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableRecordTool", "Record deleted: $recordId")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // Add user_id_type=open_id query param
                "batch_create" -> {
                    @Suppress("UNCHECKED_CAST")
                    val records = args["records"] as? List<Map<String, Any?>>
                        ?: return@withContext ToolResult.error("Missing required parameter: records")
                    val body = mapOf("records" to records)

                    val result = client.post("$basePath/batch_create?user_id_type=open_id", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableRecordTool", "Records batch created")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // Add user_id_type=open_id query param
                "batch_update" -> {
                    @Suppress("UNCHECKED_CAST")
                    val records = args["records"] as? List<Map<String, Any?>>
                        ?: return@withContext ToolResult.error("Missing required parameter: records")
                    val body = mapOf("records" to records)

                    val result = client.post("$basePath/batch_update?user_id_type=open_id", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableRecordTool", "Records batch updated")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // Renamed parameter from records to record_ids
                "batch_delete" -> {
                    @Suppress("UNCHECKED_CAST")
                    val recordIds = args["record_ids"] as? List<String>
                        ?: return@withContext ToolResult.error("Missing required parameter: record_ids (array of record_id strings)")
                    val body = mapOf("records" to recordIds)

                    val result = client.post("$basePath/batch_delete", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableRecordTool", "Records batch deleted")
                    ToolResult.success(data)
                }

                else -> ToolResult.error("Unknown action: $action. Supported: create, list, update, delete, batch_create, batch_update, batch_delete")
            }
        } catch (e: Exception) {
            Log.e("FeishuBitableAppTableRecordTool", "Failed", e)
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
                    "action" to PropertySchema(
                        "string", "操作类型",
                        enum = listOf("create", "list", "update", "delete", "batch_create", "batch_update", "batch_delete")
                    ),
                    "app_token" to PropertySchema("string", "多维表格 app_token"),
                    "table_id" to PropertySchema("string", "数据表 ID"),
                    "record_id" to PropertySchema("string", "记录 ID（update/delete 必填）"),
                    "fields" to PropertySchema("object", "字段值对象，如 {\"字段1\": \"值1\"}（create/update 必填）", properties = emptyMap()),
                    "records" to PropertySchema(
                        "array",
                        "记录数组。batch_create/batch_update 时为 [{fields: {...}}, ...] 对象数组",
                        items = PropertySchema("object", "记录对象")
                    ),
                    "record_ids" to PropertySchema(
                        "array",
                        "记录 ID 数组（batch_delete 必填）",
                        items = PropertySchema("string", "record_id 字符串")
                    ),
                    "view_id" to PropertySchema("string", "视图 ID（list 可选）"),
                    "filter" to PropertySchema(
                        "object",
                        "筛选条件（list 可选），必须是结构化对象。示例：{conjunction: 'and', conditions: [{field_name: '文本', operator: 'is', value: ['测试']}]}",
                        properties = mapOf(
                            "conjunction" to PropertySchema("string", "条件逻辑：and 或 or"),
                            "conditions" to PropertySchema("array", "条件数组", items = PropertySchema("object", "条件对象"))
                        )
                    ),
                    "sort" to PropertySchema(
                        "array", "排序条件数组（list 可选）",
                        items = PropertySchema("object", "排序对象，如 {field_name, desc}")
                    ),
                    "field_names" to PropertySchema(
                        "array", "指定返回的字段名列表（list 可选）",
                        items = PropertySchema("string", "字段名")
                    ),
                    "automatic_fields" to PropertySchema("boolean", "是否返回自动字段（list 可选）"),
                    "page_size" to PropertySchema("number", "分页大小（list 可选）"),
                    "page_token" to PropertySchema("string", "分页标记（list 可选）")
                ),
                required = listOf("action", "app_token", "table_id")
            )
        )
    )
}

// ─────────────────────────────────────────────────────────────
// 5. FeishuBitableAppTableViewTool — 视图管理
// ─────────────────────────────────────────────────────────────

class FeishuBitableAppTableViewTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    override val name = "feishu_bitable_app_table_view"
    override val description =
        "【以用户身份】飞书多维表格视图管理工具。当用户要求创建/查询/更新视图、切换展示方式时使用。" +
        "Actions: create（创建视图）, get（获取视图详情）, list（列出所有视图）, patch（更新视图）。"

    override fun isEnabled() = config.enableBitableTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")
            val appToken = args["app_token"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: app_token")
            val tableId = args["table_id"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: table_id")

            val basePath = "/open-apis/bitable/v1/apps/$appToken/tables/$tableId/views"

            when (action) {
                // @aligned openclaw-lark v2026.3.30
                "create" -> {
                    val viewName = args["view_name"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: view_name")
                    val viewType = args["view_type"] as? String

                    val body = mutableMapOf<String, Any>("view_name" to viewName)
                    if (viewType != null) body["view_type"] = viewType

                    val result = client.post(basePath, body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableViewTool", "View created: $viewName")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                "get" -> {
                    val viewId = args["view_id"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: view_id")

                    val result = client.get("$basePath/$viewId")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableViewTool", "View fetched: $viewId")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // Pass page_size/page_token as query params
                "list" -> {
                    val pageSize = (args["page_size"] as? Number)?.toInt()
                    val pageToken = args["page_token"] as? String

                    val query = buildQuery(
                        "page_size" to pageSize,
                        "page_token" to pageToken
                    )
                    val result = client.get("$basePath$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableViewTool", "Views listed")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // view_name is OPTIONAL
                "patch" -> {
                    val viewId = args["view_id"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: view_id")
                    val viewName = args["view_name"] as? String

                    val body = mutableMapOf<String, Any>()
                    if (viewName != null) body["view_name"] = viewName
                    if (body.isEmpty()) return@withContext ToolResult.error("No update fields provided")

                    val result = client.patch("$basePath/$viewId", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuBitableAppTableViewTool", "View patched: $viewId")
                    ToolResult.success(data)
                }

                else -> ToolResult.error("Unknown action: $action. Supported: create, get, list, patch")
            }
        } catch (e: Exception) {
            Log.e("FeishuBitableAppTableViewTool", "Failed", e)
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
                    "action" to PropertySchema(
                        "string", "操作类型",
                        enum = listOf("create", "get", "list", "patch")
                    ),
                    "app_token" to PropertySchema("string", "多维表格 app_token"),
                    "table_id" to PropertySchema("string", "数据表 ID"),
                    "view_id" to PropertySchema("string", "视图 ID（get/patch 必填）"),
                    "view_name" to PropertySchema("string", "视图名称（create 必填，patch 可选）"),
                    "view_type" to PropertySchema("string", "视图类型（create 可选）。grid=表格视图, kanban=看板视图, gallery=画册视图, gantt=甘特图, form=表单视图"),
                    "page_size" to PropertySchema("number", "分页大小（list 可选）"),
                    "page_token" to PropertySchema("string", "分页标记（list 可选）")
                ),
                required = listOf("action", "app_token", "table_id")
            )
        )
    )
}

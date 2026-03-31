package com.xiaomo.feishu.tools.sheets

/**
 * Feishu Spreadsheet tool set.
 * Aligned with ByteDance official @larksuite/openclaw-lark plugin.
 * - feishu_sheet: unified sheet tool with actions: info, read, write, append, find, create, export
 */

import android.util.Log
import com.google.gson.JsonObject
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAG = "FeishuSheetTool"
private const val MAX_READ_ROWS = 200
private const val MAX_WRITE_ROWS = 5000
private const val MAX_WRITE_COLS = 100
private const val EXPORT_POLL_INTERVAL_MS = 1000L
private const val EXPORT_POLL_MAX_RETRIES = 30

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Known Feishu token type prefixes.
 * New token format: chars at position 5/10/15 (1-indexed) form the prefix.
 * Old token format: first 3 chars are the prefix.
 */
// @aligned openclaw-lark v2026.3.30
private val KNOWN_TOKEN_TYPES = setOf(
    "dox", "doc", "sht", "bas", "app", "sld", "bmn", "fld", "nod", "box",
    "jsn", "img", "isv", "wik", "wia", "wib", "wic", "wid", "wie", "dsb"
)

/**
 * Extract token type prefix from a Feishu token.
 * Checks new format (positions 5/10/15) first, then falls back to old format (first 3 chars).
 */
// @aligned openclaw-lark v2026.3.30
private fun getTokenType(token: String): String? {
    if (token.length >= 15) {
        val prefix = "${token[4]}${token[9]}${token[14]}"
        if (prefix in KNOWN_TOKEN_TYPES) return prefix
    }
    if (token.length >= 3) {
        val prefix = token.substring(0, 3)
        if (prefix in KNOWN_TOKEN_TYPES) return prefix
    }
    return null
}

/**
 * Parse spreadsheet URL. Supports:
 *   https://xxx.feishu.cn/sheets/TOKEN
 *   https://xxx.feishu.cn/sheets/TOKEN?sheet=SHEET_ID
 *   https://xxx.feishu.cn/wiki/TOKEN (wiki spreadsheets)
 */
// @aligned openclaw-lark v2026.3.30
private data class ParsedSheetUrl(val token: String, val sheetId: String?)

// @aligned openclaw-lark v2026.3.30
private fun parseSheetUrl(url: String): ParsedSheetUrl? {
    return try {
        val u = java.net.URI(url)
        val pathMatch = Regex("/(?:sheets|wiki)/([^/?#]+)").find(u.path ?: "") ?: return null
        val token = pathMatch.groupValues[1]
        val query = u.query ?: ""
        val sheetId = Regex("(?:^|&)sheet=([^&]+)").find(query)?.groupValues?.get(1)
        ParsedSheetUrl(token, sheetId)
    } catch (_: Exception) {
        null
    }
}

/**
 * Resolve spreadsheet_token from url or spreadsheet_token param.
 * If a wiki token is detected, resolves to the real spreadsheet_token via wiki API.
 */
// @aligned openclaw-lark v2026.3.30
private suspend fun resolveToken(
    args: Map<String, Any?>,
    client: FeishuClient
): Pair<String, String?> {
    var token: String
    var urlSheetId: String? = null

    val rawToken = args["spreadsheet_token"] as? String
    val rawUrl = args["url"] as? String

    if (rawToken != null) {
        token = rawToken.trim()
    } else if (rawUrl != null) {
        val parsed = parseSheetUrl(rawUrl)
            ?: throw IllegalArgumentException("Failed to parse spreadsheet_token from URL: $rawUrl")
        token = parsed.token
        urlSheetId = parsed.sheetId
    } else {
        throw IllegalArgumentException("url or spreadsheet_token is required")
    }

    // Detect wiki token and resolve to real spreadsheet_token
    val tokenType = getTokenType(token)
    if (tokenType == "wik") {
        Log.i(TAG, "resolveToken: detected wiki token, resolving obj_token...")
        val wikiResult = client.get("/open-apis/wiki/v2/spaces/get_node?token=$token&obj_type=wiki")
        if (wikiResult.isFailure) {
            throw IllegalStateException("Failed to resolve wiki token: ${wikiResult.exceptionOrNull()?.message}")
        }
        val objToken = wikiResult.getOrNull()
            ?.getAsJsonObject("data")
            ?.getAsJsonObject("node")
            ?.get("obj_token")?.asString
            ?: throw IllegalStateException("Failed to resolve spreadsheet token from wiki token: $token")
        Log.i(TAG, "resolveToken: wiki resolved $token -> $objToken")
        token = objToken
    }

    return Pair(token, urlSheetId)
}

/**
 * Resolve the target range for read/write/append operations.
 * Priority: explicit range > sheet_id param / URL sheet > first sheet via API.
 */
// @aligned openclaw-lark v2026.3.30
private suspend fun resolveRange(
    token: String,
    range: String?,
    sheetId: String?,
    client: FeishuClient
): String {
    if (!range.isNullOrBlank()) return range
    if (!sheetId.isNullOrBlank()) return sheetId
    // Query first sheet via API
    val sheetsResult = client.get("/open-apis/sheets/v3/spreadsheets/$token/sheets/query")
    if (sheetsResult.isFailure) {
        throw IllegalStateException("Failed to query sheets: ${sheetsResult.exceptionOrNull()?.message}")
    }
    val sheetsArray = sheetsResult.getOrNull()
        ?.getAsJsonObject("data")
        ?.getAsJsonArray("sheets")
    if (sheetsArray == null || sheetsArray.size() == 0) {
        throw IllegalStateException("Spreadsheet has no worksheets")
    }
    val firstSheetId = sheetsArray[0].asJsonObject.get("sheet_id")?.asString
        ?: throw IllegalStateException("First sheet has no sheet_id")
    return firstSheetId
}

/**
 * Convert column number (1-based) to Excel column letter (A, B, ..., Z, AA, AB, ...).
 */
// @aligned openclaw-lark v2026.3.30
private fun colLetter(n: Int): String {
    var num = n
    val result = StringBuilder()
    while (num > 0) {
        num--
        result.insert(0, ('A' + (num % 26)).toChar())
        num /= 26
    }
    return result.toString()
}

// ─── feishu_sheet ──────────────────────────────────────────────────

class FeishuSheetTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_sheet"
    override val description = "【以用户身份】飞书电子表格工具。支持创建、读写、查找、导出电子表格。" +
        "\n\n电子表格（Sheets）类似 Excel/Google Sheets，与多维表格（Bitable/Airtable）是不同产品。" +
        "\n\n所有 action（除 create 外）均支持传入 url 或 spreadsheet_token，工具会自动解析。支持知识库 wiki URL，自动解析为电子表格 token。" +
        "\n\nActions:" +
        "\n- info：获取表格信息 + 全部工作表列表（一次调用替代 get_info + list_sheets）" +
        "\n- read：读取数据。不填 range 自动读取第一个工作表全部数据" +
        "\n- write：覆盖写入,高危,请谨慎使用该操作。不填 range 自动写入第一个工作表（从 A1 开始）" +
        "\n- append：在已有数据末尾追加行" +
        "\n- find：在工作表中查找单元格" +
        "\n- create：创建电子表格。支持带 headers + data 一步创建含数据的表格" +
        "\n- export：导出为 xlsx 或 csv（csv 必须指定 sheet_id）"

    override fun isEnabled() = config.enableSheetTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            when (action) {
                "info" -> doInfo(args)
                "read" -> doRead(args)
                "write" -> doWrite(args)
                "append" -> doAppend(args)
                "find" -> doFind(args)
                "create" -> doCreate(args)
                "export" -> doExport(args)
                else -> ToolResult.error("Unknown action: $action. Must be one of: info, read, write, append, find, create, export")
            }
        } catch (e: Exception) {
            Log.e(TAG, "feishu_sheet failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun doInfo(args: Map<String, Any?>): ToolResult {
        val (token, _) = resolveToken(args, client)
        Log.i(TAG, "info: token=$token")

        // Parallel requests: spreadsheet info + sheets list
        val spreadsheetResult = client.get("/open-apis/sheets/v3/spreadsheets/$token")
        if (spreadsheetResult.isFailure) {
            return ToolResult.error(spreadsheetResult.exceptionOrNull()?.message ?: "Failed to get spreadsheet info")
        }
        val sheetsResult = client.get("/open-apis/sheets/v3/spreadsheets/$token/sheets/query")
        if (sheetsResult.isFailure) {
            return ToolResult.error(sheetsResult.exceptionOrNull()?.message ?: "Failed to query sheets")
        }

        val spreadsheet = spreadsheetResult.getOrNull()?.getAsJsonObject("data")?.getAsJsonObject("spreadsheet")
        val sheetsArray = sheetsResult.getOrNull()?.getAsJsonObject("data")?.getAsJsonArray("sheets")

        val sheets = mutableListOf<Map<String, Any?>>()
        sheetsArray?.forEach { s ->
            val sheet = s.asJsonObject
            val gridProps = sheet.getAsJsonObject("grid_properties")
            sheets.add(mapOf(
                "sheet_id" to sheet.get("sheet_id")?.asString,
                "title" to sheet.get("title")?.asString,
                "index" to sheet.get("index")?.asInt,
                "row_count" to gridProps?.get("row_count")?.asInt,
                "column_count" to gridProps?.get("column_count")?.asInt,
                "frozen_row_count" to gridProps?.get("frozen_row_count")?.asInt,
                "frozen_column_count" to gridProps?.get("frozen_column_count")?.asInt,
            ))
        }

        return ToolResult.success(mapOf(
            "title" to spreadsheet?.get("title")?.asString,
            "spreadsheet_token" to token,
            "url" to "https://feishu.cn/sheets/$token",
            "sheets" to sheets
        ))
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun doRead(args: Map<String, Any?>): ToolResult {
        val (token, urlSheetId) = resolveToken(args, client)
        val range = resolveRange(token, args["range"] as? String, args["sheet_id"] as? String ?: urlSheetId, client)
        val valueRenderOption = args["value_render_option"] as? String ?: "ToString"
        Log.i(TAG, "read: token=$token, range=$range, valueRenderOption=$valueRenderOption")

        val encodedRange = java.net.URLEncoder.encode(range, "UTF-8")
        val result = client.get(
            "/open-apis/sheets/v2/spreadsheets/$token/values/$encodedRange" +
                "?valueRenderOption=$valueRenderOption&dateTimeRenderOption=FormattedString"
        )
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to read spreadsheet")
        }
        val data = result.getOrNull()?.getAsJsonObject("data")
        return ToolResult.success(data)
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun doWrite(args: Map<String, Any?>): ToolResult {
        val (token, urlSheetId) = resolveToken(args, client)
        val values = args["values"] ?: return ToolResult.error("Missing values")
        val range = resolveRange(token, args["range"] as? String, args["sheet_id"] as? String ?: urlSheetId, client)
        Log.i(TAG, "write: token=$token, range=$range")

        val body = mapOf(
            "valueRange" to mapOf(
                "range" to range,
                "values" to values
            )
        )
        val result = client.put("/open-apis/sheets/v2/spreadsheets/$token/values", body)
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to write spreadsheet")
        }
        val data = result.getOrNull()?.getAsJsonObject("data")
        return ToolResult.success(data)
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun doAppend(args: Map<String, Any?>): ToolResult {
        val (token, urlSheetId) = resolveToken(args, client)
        val values = args["values"] ?: return ToolResult.error("Missing values")
        val range = resolveRange(token, args["range"] as? String, args["sheet_id"] as? String ?: urlSheetId, client)
        Log.i(TAG, "append: token=$token, range=$range")

        val body = mapOf(
            "valueRange" to mapOf(
                "range" to range,
                "values" to values
            )
        )
        val result = client.post("/open-apis/sheets/v2/spreadsheets/$token/values_append", body)
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to append spreadsheet")
        }
        val data = result.getOrNull()?.getAsJsonObject("data")
        return ToolResult.success(data)
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun doFind(args: Map<String, Any?>): ToolResult {
        val (token, _) = resolveToken(args, client)
        val sheetId = args["sheet_id"] as? String
            ?: return ToolResult.error("Missing sheet_id")
        val find = args["find"] as? String
            ?: return ToolResult.error("Missing find parameter")
        Log.i(TAG, "find: token=$token, sheet_id=$sheetId, find=\"$find\"")

        // Build find_condition object (aligned with openclaw-lark)
        val findCondition = mutableMapOf<String, Any>()
        val findRange = args["range"] as? String
        if (findRange != null) {
            findCondition["range"] = "$sheetId!$findRange"
        } else {
            findCondition["range"] = sheetId
        }
        // Invert match_case: oapi issue - true means case-insensitive, false means case-sensitive
        (args["match_case"] as? Boolean)?.let { findCondition["match_case"] = !it }
        (args["match_entire_cell"] as? Boolean)?.let { findCondition["match_entire_cell"] = it }
        (args["search_by_regex"] as? Boolean)?.let { findCondition["search_by_regex"] = it }
        (args["include_formulas"] as? Boolean)?.let { findCondition["include_formulas"] = it }

        val body = mapOf(
            "find_condition" to findCondition,
            "find" to find
        )

        val result = client.post(
            "/open-apis/sheets/v3/spreadsheets/$token/sheets/$sheetId/find",
            body
        )
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to find in spreadsheet")
        }
        val data = result.getOrNull()?.getAsJsonObject("data")
        return ToolResult.success(data)
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun doCreate(args: Map<String, Any?>): ToolResult {
        val title = args["title"] as? String ?: return ToolResult.error("Missing title")
        val folderToken = args["folder_token"] as? String
        @Suppress("UNCHECKED_CAST")
        val headers = args["headers"] as? List<String>
        @Suppress("UNCHECKED_CAST")
        val initialData = args["data"] as? List<List<Any?>>
        Log.i(TAG, "create: title=\"$title\", folder=${folderToken ?: "(root)"}, headers=${headers != null}, data=${initialData?.size ?: 0} rows")

        val body = mutableMapOf<String, Any>("title" to title)
        if (folderToken != null) {
            body["folder_token"] = folderToken
        }

        val result = client.post("/open-apis/sheets/v3/spreadsheets", body)
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to create spreadsheet")
        }
        val data = result.getOrNull()?.getAsJsonObject("data")
        val spreadsheet = data?.getAsJsonObject("spreadsheet")
        val newToken = spreadsheet?.get("spreadsheet_token")?.asString
            ?: return ToolResult.error("Failed to create spreadsheet: no token returned")
        val url = "https://feishu.cn/sheets/$newToken"
        Log.i(TAG, "create: token=$newToken")

        // Step 2: Write initial data (headers + data) if provided
        if (headers != null || initialData != null) {
            val allRows = mutableListOf<List<Any?>>()
            if (headers != null) allRows.add(headers)
            if (initialData != null) allRows.addAll(initialData)

            if (allRows.isNotEmpty()) {
                // Query default sheet's sheet_id
                val sheetsResult = client.get("/open-apis/sheets/v3/spreadsheets/$newToken/sheets/query")
                if (sheetsResult.isSuccess) {
                    val sheetsArray = sheetsResult.getOrNull()?.getAsJsonObject("data")?.getAsJsonArray("sheets")
                    val firstSheetId = sheetsArray?.firstOrNull()?.asJsonObject?.get("sheet_id")?.asString
                    if (firstSheetId != null) {
                        val numRows = allRows.size
                        val numCols = allRows.maxOf { it.size }
                        val writeRange = "$firstSheetId!A1:${colLetter(numCols)}$numRows"
                        Log.i(TAG, "create: writing $numRows rows to $writeRange")
                        val writeBody = mapOf(
                            "valueRange" to mapOf(
                                "range" to writeRange,
                                "values" to allRows
                            )
                        )
                        val writeResult = client.put("/open-apis/sheets/v2/spreadsheets/$newToken/values", writeBody)
                        if (writeResult.isFailure) {
                            Log.i(TAG, "create: initial data write failed: ${writeResult.exceptionOrNull()?.message}")
                            return ToolResult.success(mapOf(
                                "spreadsheet_token" to newToken,
                                "url" to url,
                                "warning" to "Spreadsheet created but failed to write initial data: ${writeResult.exceptionOrNull()?.message}"
                            ))
                        }
                    }
                }
            }
        }

        return ToolResult.success(mapOf(
            "spreadsheet_token" to newToken,
            "title" to title,
            "url" to url
        ))
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun doExport(args: Map<String, Any?>): ToolResult {
        val (token, _) = resolveToken(args, client)
        val fileExtension = args["file_extension"] as? String ?: "xlsx"
        val sheetId = args["sheet_id"] as? String

        if (fileExtension == "csv" && sheetId == null) {
            return ToolResult.error("sheet_id is required for CSV export (CSV can only export one worksheet at a time). Use info action to get the worksheet list.")
        }
        Log.i(TAG, "export: token=$token, format=$fileExtension")

        // Step 1: Create export task via Drive export task API
        val createBody = mutableMapOf<String, Any>(
            "file_extension" to fileExtension,
            "token" to token,
            "type" to "sheet"
        )
        if (sheetId != null) {
            createBody["sub_id"] = sheetId
        }
        val createResult = client.post("/open-apis/drive/v1/export_tasks", createBody)
        if (createResult.isFailure) {
            return ToolResult.error(createResult.exceptionOrNull()?.message ?: "Failed to create export task")
        }
        val taskData = createResult.getOrNull()?.getAsJsonObject("data")
        val ticket = taskData?.get("ticket")?.asString
            ?: return ToolResult.error("Missing ticket in export response")
        Log.i(TAG, "export: ticket=$ticket")

        // Step 2: Poll for export completion via Drive export task API
        var fileToken: String? = null
        var fileName: String? = null
        var fileSize: Long? = null
        for (i in 0 until EXPORT_POLL_MAX_RETRIES) {
            delay(EXPORT_POLL_INTERVAL_MS)
            val pollResult = client.get("/open-apis/drive/v1/export_tasks/$ticket?token=$token")
            if (pollResult.isSuccess) {
                val pollData = pollResult.getOrNull()?.getAsJsonObject("data")?.getAsJsonObject("result")
                val jobStatus = pollData?.get("job_status")?.asInt
                if (jobStatus == 0) { // completed
                    fileToken = pollData?.get("file_token")?.asString
                    fileName = pollData?.get("file_name")?.asString
                    fileSize = pollData?.get("file_size")?.asLong
                    Log.i(TAG, "export: done, file_token=$fileToken, size=$fileSize")
                    break
                }
                if (jobStatus != null && jobStatus >= 3) { // failed
                    val errorMsg = pollData?.get("job_error_msg")?.asString ?: "export failed (status=$jobStatus)"
                    return ToolResult.error(errorMsg)
                }
                Log.i(TAG, "export: polling ${i + 1}/$EXPORT_POLL_MAX_RETRIES, status=$jobStatus")
            }
        }

        if (fileToken == null) {
            return ToolResult.error("Export timeout: task did not complete within 30 seconds")
        }

        // Step 3: Return file info (download via /open-apis/drive/v1/export_tasks/file/:file_token)
        return ToolResult.success(mapOf(
            "file_token" to fileToken,
            "file_name" to fileName,
            "file_size" to fileSize,
            "download_url" to "/open-apis/drive/v1/export_tasks/file/$fileToken",
            "hint" to "File exported. Use download_url to download."
        ))
    }

    // @aligned openclaw-lark v2026.3.30
    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "action" to PropertySchema(
                        "string",
                        "操作类型",
                        enum = listOf("info", "read", "write", "append", "find", "create", "export")
                    ),
                    "spreadsheet_token" to PropertySchema("string", "电子表格 token（与 url 二选一）"),
                    "url" to PropertySchema("string", "电子表格 URL，支持 /sheets/TOKEN 和 /wiki/TOKEN 格式，支持 ?sheet=SHEET_ID 参数（与 spreadsheet_token 二选一）"),
                    "sheet_id" to PropertySchema("string", "工作表 ID（read/write/append 时可选，仅当不提供 range 时生效；find 时必填；export csv 时必填）"),
                    "range" to PropertySchema("string", "数据范围（可选）。格式：<sheetId>!A1:C3。read/write/append 不填则自动使用第一个工作表"),
                    "values" to PropertySchema("array", "要写入的数据（二维数组，write、append 时使用）"),
                    "find" to PropertySchema("string", "查找内容（find 时使用）"),
                    "match_case" to PropertySchema("boolean", "是否区分大小写（find 时使用，默认 true）"),
                    "match_entire_cell" to PropertySchema("boolean", "是否匹配整个单元格（find 时使用，默认 false）"),
                    "search_by_regex" to PropertySchema("boolean", "是否使用正则表达式（find 时使用，默认 false）"),
                    "include_formulas" to PropertySchema("boolean", "是否搜索公式（find 时使用，默认 false）"),
                    "title" to PropertySchema("string", "表格标题（create 时使用）"),
                    "folder_token" to PropertySchema("string", "父文件夹 token（create 时使用）"),
                    "headers" to PropertySchema("array", "表头列名（create 时可选），例如 [\"姓名\", \"部门\"]",
                        items = PropertySchema("string", "列名")),
                    "data" to PropertySchema("array", "初始数据（create 时可选），二维数组，写在表头之后"),
                    "file_extension" to PropertySchema("string", "导出格式（export 时使用，默认 xlsx）", enum = listOf("xlsx", "csv")),
                    "value_render_option" to PropertySchema("string", "值渲染方式（read 时可选）：ToString（默认）、FormattedValue、Formula、UnformattedValue",
                        enum = listOf("ToString", "FormattedValue", "Formula", "UnformattedValue")),
                    "output_path" to PropertySchema("string", "本地保存路径（export 时可选）")
                ),
                required = listOf("action")
            )
        )
    )
}

// ─── Aggregator ────────────────────────────────────────────────────

class FeishuSheetTools(config: FeishuConfig, client: FeishuClient) {
    private val sheetTool = FeishuSheetTool(config, client)

    fun getAllTools(): List<FeishuToolBase> = listOf(sheetTool)

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

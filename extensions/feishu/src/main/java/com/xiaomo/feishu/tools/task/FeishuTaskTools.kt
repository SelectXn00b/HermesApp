package com.xiaomo.feishu.tools.task

/**
 * OpenClaw Source Reference:
 * - @larksuite/openclaw-lark task tools
 *
 * AndroidForClaw adaptation: Feishu task tool definitions.
 * Each tool corresponds to one API resource with multiple actions.
 */

import android.util.Log
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ─────────────────────────────────────────────────────────────
// Aggregator
// ─────────────────────────────────────────────────────────────

class FeishuTaskTools(config: FeishuConfig, client: FeishuClient) {
    private val taskTool = FeishuTaskTaskTool(config, client)
    private val tasklistTool = FeishuTaskTasklistTool(config, client)
    private val subtaskTool = FeishuTaskSubtaskTool(config, client)
    private val commentTool = FeishuTaskCommentTool(config, client)

    fun getAllTools(): List<FeishuToolBase> {
        return listOf(taskTool, tasklistTool, subtaskTool, commentTool)
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
// Helper: parse ISO 8601 timestamp to millisecond string
// @aligned openclaw-lark v2026.3.30
// ─────────────────────────────────────────────────────────────

private fun parseTimeToTimestampMs(input: String): String? {
    // Already a numeric timestamp
    if (input.matches(Regex("^\\d+$"))) {
        return input
    }
    // Try ISO 8601 formats
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd"
    )
    for (fmt in formats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.US)
            if (fmt.endsWith("ss") && !fmt.contains("X") && !fmt.contains("Z")) {
                sdf.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            }
            val date = sdf.parse(input) ?: continue
            return date.time.toString()
        } catch (_: Exception) {
            // try next format
        }
    }
    return null
}

// ─────────────────────────────────────────────────────────────
// Helper: parse due/start object from args
// @aligned openclaw-lark v2026.3.30
// Returns {timestamp: msString, is_all_day: bool} or null
// ─────────────────────────────────────────────────────────────

@Suppress("UNCHECKED_CAST")
private fun parseDueStartObject(raw: Any?): Map<String, Any>? {
    if (raw == null) return null
    // If it's a structured object with timestamp field
    if (raw is Map<*, *>) {
        val tsRaw = raw["timestamp"] as? String ?: return null
        val tsMs = parseTimeToTimestampMs(tsRaw) ?: return null
        val isAllDay = raw["is_all_day"] as? Boolean ?: false
        return mapOf("timestamp" to tsMs, "is_all_day" to isAllDay)
    }
    // If it's a plain string (legacy format), convert to object
    if (raw is String) {
        val tsMs = parseTimeToTimestampMs(raw) ?: return null
        return mapOf("timestamp" to tsMs, "is_all_day" to false)
    }
    return null
}

// ─────────────────────────────────────────────────────────────
// 1. FeishuTaskTaskTool — 任务管理
// ─────────────────────────────────────────────────────────────

class FeishuTaskTaskTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    override val name = "feishu_task_task"
    override val description =
        "【以用户身份】飞书任务管理工具。用于创建、查询、更新任务。" +
        "Actions: create（创建任务）, get（获取任务详情）, list（查询任务列表，仅返回我负责的任务）, patch（更新任务）。" +
        "时间参数使用ISO 8601 / RFC 3339 格式（包含时区），例如 '2024-01-01T00:00:00+08:00'。" +
        "due/start 必须是对象 {timestamp: string, is_all_day?: boolean}。"

    override fun isEnabled() = config.enableTaskTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            val basePath = "/open-apis/task/v2/tasks"

            when (action) {
                // @aligned openclaw-lark v2026.3.30
                // due/start must be objects {timestamp, is_all_day}; parse ISO 8601 to ms
                // Add current_user_id, user_id_type=open_id query param
                "create" -> {
                    val summary = args["summary"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: summary")
                    val description = args["description"] as? String
                    val currentUserId = args["current_user_id"] as? String
                    @Suppress("UNCHECKED_CAST")
                    val members = args["members"] as? List<Map<String, Any?>>
                    val repeatRule = args["repeat_rule"] as? String
                    @Suppress("UNCHECKED_CAST")
                    val tasklists = args["tasklists"] as? List<Map<String, Any?>>

                    val body = mutableMapOf<String, Any>("summary" to summary)
                    if (description != null) body["description"] = description

                    // Parse due as object
                    val dueObj = parseDueStartObject(args["due"])
                    if (args["due"] != null && dueObj == null) {
                        return@withContext ToolResult.error(
                            "due 时间格式错误！必须是对象 {timestamp: 'ISO 8601 string', is_all_day?: boolean}")
                    }
                    if (dueObj != null) body["due"] = dueObj

                    // Parse start as object
                    val startObj = parseDueStartObject(args["start"])
                    if (args["start"] != null && startObj == null) {
                        return@withContext ToolResult.error(
                            "start 时间格式错误！必须是对象 {timestamp: 'ISO 8601 string', is_all_day?: boolean}")
                    }
                    if (startObj != null) body["start"] = startObj

                    if (members != null) body["members"] = members
                    if (repeatRule != null) body["repeat_rule"] = repeatRule
                    if (tasklists != null) body["tasklists"] = tasklists

                    // Auto-add current_user_id as follower if not in members
                    if (currentUserId != null && members != null) {
                        val hasUser = members.any { it["id"] == currentUserId }
                        if (!hasUser) {
                            @Suppress("UNCHECKED_CAST")
                            val membersList = (body["members"] as List<Map<String, Any?>>).toMutableList()
                            membersList.add(mapOf("id" to currentUserId, "role" to "follower"))
                            body["members"] = membersList
                        }
                    } else if (currentUserId != null && members == null) {
                        body["members"] = listOf(mapOf("id" to currentUserId, "role" to "follower"))
                    }

                    val query = buildQuery("user_id_type" to "open_id")
                    val result = client.post("$basePath$query", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuTaskTaskTool", "Task created")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // Add user_id_type=open_id query param
                "get" -> {
                    val taskGuid = args["task_guid"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: task_guid")

                    val query = buildQuery("user_id_type" to "open_id")
                    val result = client.get("$basePath/$taskGuid$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuTaskTaskTool", "Task fetched: $taskGuid")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // Add completed filter, user_id_type=open_id query param
                "list" -> {
                    val pageSize = (args["page_size"] as? Number)?.toInt()
                    val pageToken = args["page_token"] as? String
                    val completed = args["completed"] as? Boolean

                    val query = buildQuery(
                        "page_size" to pageSize,
                        "page_token" to pageToken,
                        "completed" to completed,
                        "user_id_type" to "open_id"
                    )
                    val result = client.get("$basePath$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuTaskTaskTool", "Tasks listed")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // CRITICAL: patch wraps body as {task: updateData, update_fields: [...]}
                // due/start must be objects; parse ISO 8601 to ms
                // Add user_id_type=open_id query param
                "patch" -> {
                    val taskGuid = args["task_guid"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: task_guid")

                    val summary = args["summary"] as? String
                    val description = args["description"] as? String
                    val completedAt = args["completed_at"] as? String
                    @Suppress("UNCHECKED_CAST")
                    val members = args["members"] as? List<Map<String, Any?>>
                    val repeatRule = args["repeat_rule"] as? String

                    val updateData = mutableMapOf<String, Any>()
                    if (summary != null) updateData["summary"] = summary
                    if (description != null) updateData["description"] = description

                    // Parse due as object
                    val dueObj = parseDueStartObject(args["due"])
                    if (args["due"] != null && dueObj == null) {
                        return@withContext ToolResult.error(
                            "due 时间格式错误！必须是对象 {timestamp: 'ISO 8601 string', is_all_day?: boolean}")
                    }
                    if (dueObj != null) updateData["due"] = dueObj

                    // Parse start as object
                    val startObj = parseDueStartObject(args["start"])
                    if (args["start"] != null && startObj == null) {
                        return@withContext ToolResult.error(
                            "start 时间格式错误！必须是对象 {timestamp: 'ISO 8601 string', is_all_day?: boolean}")
                    }
                    if (startObj != null) updateData["start"] = startObj

                    // Handle completed_at conversion
                    if (completedAt != null) {
                        if (completedAt == "0") {
                            updateData["completed_at"] = "0"
                        } else if (completedAt.matches(Regex("^\\d+$"))) {
                            updateData["completed_at"] = completedAt
                        } else {
                            val completedTs = parseTimeToTimestampMs(completedAt)
                            if (completedTs == null) {
                                return@withContext ToolResult.error(
                                    "completed_at 格式错误！支持：1) ISO 8601 格式; 2) '0'（反完成）; 3) 毫秒时间戳字符串。")
                            }
                            updateData["completed_at"] = completedTs
                        }
                    }

                    if (members != null) updateData["members"] = members
                    if (repeatRule != null) updateData["repeat_rule"] = repeatRule

                    if (updateData.isEmpty()) return@withContext ToolResult.error("No update fields provided")

                    // Build update_fields list (required by Task API)
                    val updateFields = updateData.keys.toList()

                    // Wrap as {task: updateData, update_fields: [...]}
                    val body = mapOf(
                        "task" to updateData,
                        "update_fields" to updateFields
                    )

                    val query = buildQuery("user_id_type" to "open_id")
                    val result = client.patch("$basePath/$taskGuid$query", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuTaskTaskTool", "Task patched: $taskGuid")
                    ToolResult.success(data)
                }

                else -> ToolResult.error("Unknown action: $action. Supported: create, get, list, patch")
            }
        } catch (e: Exception) {
            Log.e("FeishuTaskTaskTool", "Failed", e)
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
                    "task_guid" to PropertySchema("string", "任务 GUID（get/patch 必填）"),
                    "summary" to PropertySchema("string", "任务标题（create 必填，patch 可选）"),
                    "description" to PropertySchema("string", "任务描述（可选）"),
                    "current_user_id" to PropertySchema("string", "当前用户 open_id（create 可选，建议传入确保创建者可编辑）"),
                    "due" to PropertySchema(
                        "object", "截止时间对象 {timestamp: 'ISO 8601 string', is_all_day?: boolean}（可选）",
                        properties = mapOf(
                            "timestamp" to PropertySchema("string", "ISO 8601 格式时间字符串"),
                            "is_all_day" to PropertySchema("boolean", "是否全天")
                        )
                    ),
                    "start" to PropertySchema(
                        "object", "开始时间对象 {timestamp: 'ISO 8601 string', is_all_day?: boolean}（可选）",
                        properties = mapOf(
                            "timestamp" to PropertySchema("string", "ISO 8601 格式时间字符串"),
                            "is_all_day" to PropertySchema("boolean", "是否全天")
                        )
                    ),
                    "completed_at" to PropertySchema("string", "完成时间（patch 可选）。ISO 8601 格式、'0'（反完成）或毫秒时间戳字符串"),
                    "completed" to PropertySchema("boolean", "是否筛选已完成任务（list 可选）"),
                    "members" to PropertySchema(
                        "array", "任务成员数组（可选）",
                        items = PropertySchema("object", "成员对象，如 {id, role}")
                    ),
                    "repeat_rule" to PropertySchema("string", "重复规则 RRULE 字符串（可选）"),
                    "tasklists" to PropertySchema(
                        "array", "关联的任务清单数组（create 可选）",
                        items = PropertySchema("object", "清单对象，如 {tasklist_guid}")
                    ),
                    "page_size" to PropertySchema("number", "分页大小（list 可选）"),
                    "page_token" to PropertySchema("string", "分页标记（list 可选）")
                ),
                required = listOf("action")
            )
        )
    )
}

// ─────────────────────────────────────────────────────────────
// 2. FeishuTaskTasklistTool — 任务清单管理
// ─────────────────────────────────────────────────────────────

class FeishuTaskTasklistTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    override val name = "feishu_task_tasklist"
    override val description =
        "【以用户身份】飞书任务清单管理工具。当用户要求创建/查询/管理清单、查看清单内的任务时使用。" +
        "Actions: create（创建清单）, get（获取清单详情）, list（列出所有可读取的清单）, " +
        "tasks（列出清单内的任务）, patch（更新清单）, add_members（添加成员）。"

    override fun isEnabled() = config.enableTaskTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            val basePath = "/open-apis/task/v2/tasklists"

            when (action) {
                // @aligned openclaw-lark v2026.3.30
                // Transform members: add type='user', default role='editor'
                // Add user_id_type=open_id query param
                "create" -> {
                    val name = args["name"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: name")
                    @Suppress("UNCHECKED_CAST")
                    val members = args["members"] as? List<Map<String, Any?>>

                    val body = mutableMapOf<String, Any>("name" to name)
                    if (members != null && members.isNotEmpty()) {
                        body["members"] = members.map { m ->
                            mapOf(
                                "id" to (m["id"] ?: ""),
                                "type" to "user",
                                "role" to (m["role"] ?: "editor")
                            )
                        }
                    }

                    val query = buildQuery("user_id_type" to "open_id")
                    val result = client.post("$basePath$query", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuTaskTasklistTool", "Tasklist created: $name")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // Add user_id_type=open_id query param
                "get" -> {
                    val tasklistGuid = args["tasklist_guid"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: tasklist_guid")

                    val query = buildQuery("user_id_type" to "open_id")
                    val result = client.get("$basePath/$tasklistGuid$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuTaskTasklistTool", "Tasklist fetched: $tasklistGuid")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // Add user_id_type=open_id query param
                "list" -> {
                    val pageSize = (args["page_size"] as? Number)?.toInt()
                    val pageToken = args["page_token"] as? String

                    val query = buildQuery(
                        "page_size" to pageSize,
                        "page_token" to pageToken,
                        "user_id_type" to "open_id"
                    )
                    val result = client.get("$basePath$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuTaskTasklistTool", "Tasklists listed")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // completed should be Boolean; add user_id_type=open_id
                "tasks" -> {
                    val tasklistGuid = args["tasklist_guid"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: tasklist_guid")
                    val pageSize = (args["page_size"] as? Number)?.toInt()
                    val pageToken = args["page_token"] as? String
                    // Accept both Boolean and String for completed
                    val completedRaw = args["completed"]
                    val completed: Boolean? = when (completedRaw) {
                        is Boolean -> completedRaw
                        is String -> completedRaw.toBooleanStrictOrNull()
                        else -> null
                    }

                    val query = buildQuery(
                        "page_size" to pageSize,
                        "page_token" to pageToken,
                        "completed" to completed,
                        "user_id_type" to "open_id"
                    )
                    val result = client.get("$basePath/$tasklistGuid/tasks$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuTaskTasklistTool", "Tasklist tasks listed: $tasklistGuid")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // CRITICAL: patch wraps body as {tasklist: {name: ...}, update_fields: ['name']}
                // name is OPTIONAL; add user_id_type=open_id
                "patch" -> {
                    val tasklistGuid = args["tasklist_guid"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: tasklist_guid")
                    val name = args["name"] as? String

                    val tasklistData = mutableMapOf<String, Any>()
                    val updateFields = mutableListOf<String>()
                    if (name != null) {
                        tasklistData["name"] = name
                        updateFields.add("name")
                    }
                    if (updateFields.isEmpty()) {
                        return@withContext ToolResult.error("No fields to update")
                    }

                    val body = mapOf(
                        "tasklist" to tasklistData,
                        "update_fields" to updateFields
                    )

                    val query = buildQuery("user_id_type" to "open_id")
                    val result = client.patch("$basePath/$tasklistGuid$query", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuTaskTasklistTool", "Tasklist patched: $tasklistGuid")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // CRITICAL: endpoint must be .../add_members NOT .../members
                // Transform members: add type='user', default role='editor'
                // Add user_id_type=open_id
                "add_members" -> {
                    val tasklistGuid = args["tasklist_guid"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: tasklist_guid")
                    @Suppress("UNCHECKED_CAST")
                    val members = args["members"] as? List<Map<String, Any?>>
                        ?: return@withContext ToolResult.error("Missing required parameter: members")

                    val memberData = members.map { m ->
                        mapOf(
                            "id" to (m["id"] ?: ""),
                            "type" to "user",
                            "role" to (m["role"] ?: "editor")
                        )
                    }

                    val body = mapOf("members" to memberData)
                    val query = buildQuery("user_id_type" to "open_id")
                    val result = client.post("$basePath/$tasklistGuid/add_members$query", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuTaskTasklistTool", "Members added to tasklist: $tasklistGuid")
                    ToolResult.success(data)
                }

                else -> ToolResult.error("Unknown action: $action. Supported: create, get, list, tasks, patch, add_members")
            }
        } catch (e: Exception) {
            Log.e("FeishuTaskTasklistTool", "Failed", e)
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
                        enum = listOf("create", "get", "list", "tasks", "patch", "add_members")
                    ),
                    "tasklist_guid" to PropertySchema("string", "清单 GUID（get/tasks/patch/add_members 必填）"),
                    "name" to PropertySchema("string", "清单名称（create 必填，patch 可选）"),
                    "members" to PropertySchema(
                        "array", "成员数组（create 可选，add_members 必填）。自动添加 type='user'",
                        items = PropertySchema("object", "成员对象，如 {id, role}")
                    ),
                    "completed" to PropertySchema("boolean", "筛选任务完成状态（tasks 可选）"),
                    "page_size" to PropertySchema("number", "分页大小（list/tasks 可选）"),
                    "page_token" to PropertySchema("string", "分页标记（list/tasks 可选）")
                ),
                required = listOf("action")
            )
        )
    )
}

// ─────────────────────────────────────────────────────────────
// 3. FeishuTaskSubtaskTool — 子任务管理
// ─────────────────────────────────────────────────────────────

class FeishuTaskSubtaskTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    override val name = "feishu_task_subtask"
    override val description =
        "【以用户身份】飞书任务的子任务管理工具。当用户要求创建子任务、查询任务的子任务列表时使用。" +
        "Actions: create（创建子任务）, list（列出任务的所有子任务）。" +
        "due/start 必须是对象 {timestamp: string, is_all_day?: boolean}。"

    override fun isEnabled() = config.enableTaskTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")
            val taskGuid = args["task_guid"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: task_guid")

            val basePath = "/open-apis/task/v2/tasks/$taskGuid/subtasks"

            when (action) {
                // @aligned openclaw-lark v2026.3.30
                // due/start must be objects; parse ISO 8601 to ms
                // Transform members: add type='user', default role='assignee'
                // Add user_id_type=open_id query param
                "create" -> {
                    val summary = args["summary"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: summary")
                    val description = args["description"] as? String
                    @Suppress("UNCHECKED_CAST")
                    val members = args["members"] as? List<Map<String, Any?>>

                    val body = mutableMapOf<String, Any>("summary" to summary)
                    if (description != null) body["description"] = description

                    // Parse due as object
                    val dueObj = parseDueStartObject(args["due"])
                    if (args["due"] != null && dueObj == null) {
                        return@withContext ToolResult.error(
                            "due 时间格式错误！必须是对象 {timestamp: 'ISO 8601 string', is_all_day?: boolean}")
                    }
                    if (dueObj != null) body["due"] = dueObj

                    // Parse start as object
                    val startObj = parseDueStartObject(args["start"])
                    if (args["start"] != null && startObj == null) {
                        return@withContext ToolResult.error(
                            "start 时间格式错误！必须是对象 {timestamp: 'ISO 8601 string', is_all_day?: boolean}")
                    }
                    if (startObj != null) body["start"] = startObj

                    // Transform members: add type='user', default role='assignee'
                    if (members != null && members.isNotEmpty()) {
                        body["members"] = members.map { m ->
                            mapOf(
                                "id" to (m["id"] ?: ""),
                                "type" to "user",
                                "role" to (m["role"] ?: "assignee")
                            )
                        }
                    }

                    val query = buildQuery("user_id_type" to "open_id")
                    val result = client.post("$basePath$query", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuTaskSubtaskTool", "Subtask created for $taskGuid")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // Add user_id_type=open_id query param
                "list" -> {
                    val pageSize = (args["page_size"] as? Number)?.toInt()
                    val pageToken = args["page_token"] as? String

                    val query = buildQuery(
                        "page_size" to pageSize,
                        "page_token" to pageToken,
                        "user_id_type" to "open_id"
                    )
                    val result = client.get("$basePath$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuTaskSubtaskTool", "Subtasks listed for $taskGuid")
                    ToolResult.success(data)
                }

                else -> ToolResult.error("Unknown action: $action. Supported: create, list")
            }
        } catch (e: Exception) {
            Log.e("FeishuTaskSubtaskTool", "Failed", e)
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
                        enum = listOf("create", "list")
                    ),
                    "task_guid" to PropertySchema("string", "父任务 GUID"),
                    "summary" to PropertySchema("string", "子任务标题（create 必填）"),
                    "description" to PropertySchema("string", "子任务描述（create 可选）"),
                    "due" to PropertySchema(
                        "object", "截止时间对象 {timestamp: 'ISO 8601 string', is_all_day?: boolean}（create 可选）",
                        properties = mapOf(
                            "timestamp" to PropertySchema("string", "ISO 8601 格式时间字符串"),
                            "is_all_day" to PropertySchema("boolean", "是否全天")
                        )
                    ),
                    "start" to PropertySchema(
                        "object", "开始时间对象 {timestamp: 'ISO 8601 string', is_all_day?: boolean}（create 可选）",
                        properties = mapOf(
                            "timestamp" to PropertySchema("string", "ISO 8601 格式时间字符串"),
                            "is_all_day" to PropertySchema("boolean", "是否全天")
                        )
                    ),
                    "members" to PropertySchema(
                        "array", "子任务成员数组（create 可选）。自动添加 type='user'，默认 role='assignee'",
                        items = PropertySchema("object", "成员对象，如 {id, role}")
                    ),
                    "page_size" to PropertySchema("number", "分页大小（list 可选）"),
                    "page_token" to PropertySchema("string", "分页标记（list 可选）")
                ),
                required = listOf("action", "task_guid")
            )
        )
    )
}

// ─────────────────────────────────────────────────────────────
// 4. FeishuTaskCommentTool — 任务评论管理
// ─────────────────────────────────────────────────────────────

class FeishuTaskCommentTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    override val name = "feishu_task_comment"
    override val description =
        "【以用户身份】飞书任务评论管理工具。当用户要求添加/查询任务评论、回复评论时使用。" +
        "Actions: create（添加评论）, list（列出任务的所有评论）, get（获取单个评论详情）。"

    override fun isEnabled() = config.enableTaskTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            when (action) {
                // @aligned openclaw-lark v2026.3.30
                // CRITICAL: endpoint is POST /open-apis/task/v2/comments (no task_guid in path)
                // Add resource_type='task' and resource_id=task_guid to body
                // Add user_id_type=open_id query param
                "create" -> {
                    val taskGuid = args["task_guid"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: task_guid")
                    val content = args["content"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: content")
                    val replyToCommentId = args["reply_to_comment_id"] as? String

                    val body = mutableMapOf<String, Any>(
                        "content" to content,
                        "resource_type" to "task",
                        "resource_id" to taskGuid
                    )
                    if (replyToCommentId != null) body["reply_to_comment_id"] = replyToCommentId

                    val query = buildQuery("user_id_type" to "open_id")
                    val result = client.post("/open-apis/task/v2/comments$query", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuTaskCommentTool", "Comment created on task $taskGuid")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // CRITICAL: use query params resource_type=task&resource_id=... on /open-apis/task/v2/comments
                // Add user_id_type=open_id query param
                "list" -> {
                    val resourceId = args["resource_id"] as? String
                        ?: (args["task_guid"] as? String)
                        ?: return@withContext ToolResult.error("Missing required parameter: resource_id or task_guid")
                    val pageSize = (args["page_size"] as? Number)?.toInt()
                    val pageToken = args["page_token"] as? String
                    val direction = args["direction"] as? String

                    val query = buildQuery(
                        "resource_type" to "task",
                        "resource_id" to resourceId,
                        "page_size" to pageSize,
                        "page_token" to pageToken,
                        "direction" to direction,
                        "user_id_type" to "open_id"
                    )
                    val result = client.get("/open-apis/task/v2/comments$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuTaskCommentTool", "Comments listed for $resourceId")
                    ToolResult.success(data)
                }

                // @aligned openclaw-lark v2026.3.30
                // Add user_id_type=open_id query param
                "get" -> {
                    val commentId = args["comment_id"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: comment_id")

                    val query = buildQuery("user_id_type" to "open_id")
                    val result = client.get("/open-apis/task/v2/comments/$commentId$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.d("FeishuTaskCommentTool", "Comment fetched: $commentId")
                    ToolResult.success(data)
                }

                else -> ToolResult.error("Unknown action: $action. Supported: create, list, get")
            }
        } catch (e: Exception) {
            Log.e("FeishuTaskCommentTool", "Failed", e)
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
                        enum = listOf("create", "list", "get")
                    ),
                    "task_guid" to PropertySchema("string", "任务 GUID（create 必填）"),
                    "resource_id" to PropertySchema("string", "资源 ID，即任务 GUID（list 必填，与 task_guid 二选一）"),
                    "comment_id" to PropertySchema("string", "评论 ID（get 必填）"),
                    "content" to PropertySchema("string", "评论内容（create 必填）"),
                    "reply_to_comment_id" to PropertySchema("string", "回复的评论 ID（create 可选）"),
                    "direction" to PropertySchema("string", "排序方向 asc/desc（list 可选）"),
                    "page_size" to PropertySchema("number", "分页大小（list 可选）"),
                    "page_token" to PropertySchema("string", "分页标记（list 可选）")
                ),
                required = listOf("action")
            )
        )
    )
}

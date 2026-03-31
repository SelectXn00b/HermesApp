package com.xiaomo.feishu.tools.calendar

/**
 * Feishu Calendar tool set.
 * Aligned with ByteDance official @larksuite/openclaw-lark plugin.
 * - feishu_calendar_calendar: calendar management (list, get, primary)
 * - feishu_calendar_event: event management (create, list, get, patch, delete, search, reply, instances, instance_view)
 * - feishu_calendar_event_attendee: attendee management (create, list)
 * - feishu_calendar_freebusy: free/busy query
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

private const val TAG = "FeishuCalendarTools"

// ---------------------------------------------------------------------------
// Time parsing helpers
// ---------------------------------------------------------------------------

/**
 * Parse ISO 8601 / RFC 3339 time string to Unix timestamp (seconds as string).
 * Supports formats like '2024-01-01T00:00:00+08:00', '2026-02-25 14:00:00'.
 * Returns null if parsing fails.
 */
// @aligned openclaw-lark v2026.3.30
private fun parseTimeToTimestamp(timeStr: String): String? {
    return try {
        // Try ISO 8601 with timezone offset
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                if (!format.contains("X") && !format.contains("Z") && format.contains("HH")) {
                    // For formats without explicit timezone, use system default
                    sdf.timeZone = TimeZone.getDefault()
                }
                val date = sdf.parse(timeStr) ?: continue
                return (date.time / 1000).toString()
            } catch (_: Exception) {
                continue
            }
        }
        // Check if already a timestamp
        val num = timeStr.toLongOrNull()
        if (num != null) {
            // If it looks like milliseconds, convert to seconds
            if (num > 1_000_000_000_000L) return (num / 1000).toString()
            return num.toString()
        }
        null
    } catch (_: Exception) {
        null
    }
}

/**
 * Parse ISO 8601 time string to RFC 3339 format (for freebusy API).
 * Returns RFC 3339 string or null if parsing fails.
 */
// @aligned openclaw-lark v2026.3.30
private fun parseTimeToRFC3339(timeStr: String): String? {
    return try {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                if (!format.contains("X") && !format.contains("Z") && format.contains("HH")) {
                    sdf.timeZone = TimeZone.getDefault()
                }
                val date = sdf.parse(timeStr) ?: continue
                // Output RFC 3339 format
                val outFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                outFmt.timeZone = TimeZone.getDefault()
                return outFmt.format(date)
            } catch (_: Exception) {
                continue
            }
        }
        // If already RFC 3339, return as-is
        if (timeStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}"))) {
            return timeStr
        }
        null
    } catch (_: Exception) {
        null
    }
}

// ─── feishu_calendar_calendar ──────────────────────────────────────

class FeishuCalendarCalendarTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_calendar_calendar"
    override val description = "【以用户身份】飞书日历管理工具。用于查询日历列表、获取日历信息、查询主日历。" +
        "Actions: list（查询日历列表）, get（查询指定日历信息）, primary（查询主日历信息）。"

    override fun isEnabled() = config.enableCalendarTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            when (action) {
                // @aligned openclaw-lark v2026.3.30
                "list" -> {
                    val pageSize = (args["page_size"] as? Number)?.toInt() ?: 50
                    val pageToken = args["page_token"] as? String
                    var path = "/open-apis/calendar/v4/calendars?page_size=$pageSize"
                    if (pageToken != null) path += "&page_token=$pageToken"

                    val result = client.get(path)
                    if (result.isFailure) {
                        return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to list calendars")
                    }
                    ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
                }
                // @aligned openclaw-lark v2026.3.30
                "get" -> {
                    val calendarId = args["calendar_id"] as? String
                        ?: return@withContext ToolResult.error("Missing calendar_id")
                    val result = client.get("/open-apis/calendar/v4/calendars/$calendarId")
                    if (result.isFailure) {
                        return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to get calendar")
                    }
                    ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
                }
                // @aligned openclaw-lark v2026.3.30
                "primary" -> {
                    // Official API uses POST, not GET
                    val result = client.post("/open-apis/calendar/v4/calendars/primary", emptyMap<String, Any>())
                    if (result.isFailure) {
                        return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to get primary calendar")
                    }
                    ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
                }
                else -> ToolResult.error("Unknown action: $action. Must be one of: list, get, primary")
            }
        } catch (e: Exception) {
            Log.e(TAG, "feishu_calendar_calendar failed", e)
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
                    "action" to PropertySchema("string", "操作类型", enum = listOf("list", "get", "primary")),
                    "calendar_id" to PropertySchema("string", "日历 ID（get 时必填）"),
                    "page_size" to PropertySchema("integer", "每页数量（list 时使用，默认 50）"),
                    "page_token" to PropertySchema("string", "分页标记（list 时使用）")
                ),
                required = listOf("action")
            )
        )
    )
}

// ─── Helper: resolve primary calendar_id ──────────────────────────

/**
 * Resolve calendar_id: use provided value or auto-resolve to primary calendar.
 */
// @aligned openclaw-lark v2026.3.30
private suspend fun resolveCalendarId(calendarId: String?, client: FeishuClient): String {
    if (!calendarId.isNullOrBlank()) return calendarId
    // Auto-resolve to primary calendar
    val result = client.post("/open-apis/calendar/v4/calendars/primary", emptyMap<String, Any>())
    if (result.isFailure) {
        throw IllegalStateException("Could not determine primary calendar: ${result.exceptionOrNull()?.message}")
    }
    val data = result.getOrNull()?.getAsJsonObject("data")
    val calendars = data?.getAsJsonArray("calendars")
    if (calendars != null && calendars.size() > 0) {
        val cid = calendars[0].asJsonObject
            ?.getAsJsonObject("calendar")
            ?.get("calendar_id")?.asString
        if (cid != null) {
            Log.i(TAG, "resolveCalendarId: primary() returned calendar_id=$cid")
            return cid
        }
    }
    throw IllegalStateException("Could not determine primary calendar")
}

// ─── feishu_calendar_event ─────────────────────────────────────────

class FeishuCalendarEventTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_calendar_event"
    override val description = "【以用户身份】飞书日程管理工具。当用户要求查看日程、创建会议、约会议、修改日程、" +
        "删除日程、搜索日程、回复日程邀请时使用。Actions: create（创建日历事件）, list（查询时间范围内的日程，自动展开重复日程）, " +
        "get（获取日程详情）, patch（更新日程）, delete（删除日程）, search（搜索日程）, reply（回复日程邀请）, " +
        "instances（获取重复日程的实例列表）, instance_view（查看展开后的日程列表）。" +
        "【重要】create 时必须传 user_open_id 参数，值为消息上下文中的 SenderId（格式 ou_xxx），否则日程只在应用日历上，用户完全看不到。" +
        "list 操作使用 instance_view 接口，会自动展开重复日程为多个实例，时间区间不能超过40天。" +
        "时间参数使用ISO 8601 / RFC 3339 格式（包含时区），例如 '2024-01-01T00:00:00+08:00'。" +
        "calendar_id 可选，不填时自动使用主日历。"

    override fun isEnabled() = config.enableCalendarTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")
            val calendarIdArg = args["calendar_id"] as? String

            when (action) {
                "create" -> doCreate(calendarIdArg, args)
                "list" -> doList(calendarIdArg, args)
                "get" -> doGet(calendarIdArg, args)
                "patch" -> doPatch(calendarIdArg, args)
                "delete" -> doDelete(calendarIdArg, args)
                "search" -> doSearch(calendarIdArg, args)
                "reply" -> doReply(calendarIdArg, args)
                "instances" -> doInstances(calendarIdArg, args)
                "instance_view" -> doInstanceView(calendarIdArg, args)
                else -> ToolResult.error(
                    "Unknown action: $action. Must be one of: create, list, get, patch, delete, search, reply, instances, instance_view"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "feishu_calendar_event failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun doCreate(calendarIdArg: String?, args: Map<String, Any?>): ToolResult {
        val summary = args["summary"] as? String
        val startTimeStr = args["start_time"] as? String
            ?: return ToolResult.error("start_time is required")
        val endTimeStr = args["end_time"] as? String
            ?: return ToolResult.error("end_time is required")
        val userOpenId = args["user_open_id"] as? String

        val startTs = parseTimeToTimestamp(startTimeStr)
            ?: return ToolResult.error("Invalid start_time format. Must use ISO 8601 / RFC 3339 with timezone, e.g. '2024-01-01T00:00:00+08:00'")
        val endTs = parseTimeToTimestamp(endTimeStr)
            ?: return ToolResult.error("Invalid end_time format. Must use ISO 8601 / RFC 3339 with timezone, e.g. '2024-01-01T00:00:00+08:00'")

        val cid = resolveCalendarId(calendarIdArg, client)
        Log.i(TAG, "create: summary=$summary, calendar_id=$cid, start=$startTs, end=$endTs, user_open_id=${userOpenId ?: "MISSING"}")

        // Build event body with timestamps
        val eventData = mutableMapOf<String, Any?>(
            "start_time" to mapOf("timestamp" to startTs),
            "end_time" to mapOf("timestamp" to endTs),
            "need_notification" to true,
            "attendee_ability" to (args["attendee_ability"] as? String ?: "can_modify_event")
        )
        if (summary != null) eventData["summary"] = summary
        (args["description"] as? String)?.let { eventData["description"] = it }
        (args["visibility"] as? String)?.let { eventData["visibility"] = it }
        (args["free_busy_status"] as? String)?.let { eventData["free_busy_status"] = it }
        (args["recurrence"] as? String)?.let { eventData["recurrence"] = it }

        // VChat
        @Suppress("UNCHECKED_CAST")
        (args["vchat"] as? Map<String, Any?>)?.let { eventData["vchat"] = it }

        // Location (accept object as from schema)
        @Suppress("UNCHECKED_CAST")
        (args["location"] as? Map<String, Any?>)?.let { eventData["location"] = it }

        // Reminders
        @Suppress("UNCHECKED_CAST")
        (args["reminders"] as? List<Map<String, Any?>>)?.let { eventData["reminders"] = it }

        val result = client.post("/open-apis/calendar/v4/calendars/$cid/events", eventData)
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to create event")
        }
        val eventResult = result.getOrNull()?.getAsJsonObject("data")?.getAsJsonObject("event")
        val eventId = eventResult?.get("event_id")?.asString

        // Build attendee list: merge explicit attendees + user_open_id
        @Suppress("UNCHECKED_CAST")
        val explicitAttendees = (args["attendees"] as? List<Map<String, Any?>>)?.toMutableList() ?: mutableListOf()
        if (userOpenId != null) {
            val alreadyIncluded = explicitAttendees.any { it["type"] == "user" && it["id"] == userOpenId }
            if (!alreadyIncluded) {
                explicitAttendees.add(mapOf("type" to "user", "id" to userOpenId))
            }
        }

        var attendeeError: String? = null
        if (explicitAttendees.isNotEmpty() && eventId != null) {
            val operateId = userOpenId ?: explicitAttendees.firstOrNull { it["type"] == "user" }?.get("id") as? String
            val attendeeData = explicitAttendees.map { a ->
                val entry = mutableMapOf<String, Any?>("type" to a["type"])
                when (a["type"]) {
                    "user" -> entry["user_id"] = a["id"]
                    "chat" -> entry["chat_id"] = a["id"]
                    "resource" -> entry["room_id"] = a["id"]
                    "third_party" -> entry["third_party_email"] = a["id"]
                }
                if (operateId != null) entry["operate_id"] = operateId
                entry
            }
            val attendeeBody = mapOf(
                "attendees" to attendeeData,
                "need_notification" to true
            )
            try {
                val attendeeResult = client.post(
                    "/open-apis/calendar/v4/calendars/$cid/events/$eventId/attendees?user_id_type=open_id",
                    attendeeBody
                )
                if (attendeeResult.isFailure) {
                    attendeeError = attendeeResult.exceptionOrNull()?.message ?: "Failed to add attendees"
                }
            } catch (e: Exception) {
                attendeeError = e.message
            }
        }

        val response = mutableMapOf<String, Any?>(
            "event" to mapOf(
                "event_id" to eventId,
                "summary" to summary,
                "app_link" to eventResult?.get("app_link")?.asString,
                "start_time" to startTimeStr,
                "end_time" to endTimeStr
            ),
            "attendees" to explicitAttendees.map { mapOf("type" to it["type"], "id" to it["id"]) }
        )
        if (attendeeError != null) {
            response["warning"] = "日程已创建，但添加参会人失败：$attendeeError"
        } else if (explicitAttendees.isEmpty()) {
            response["error"] = "日程已创建在应用日历上，但未添加任何参会人，用户看不到此日程。请重新调用时传入 user_open_id 参数。"
        } else {
            response["note"] = "已成功添加 ${explicitAttendees.size} 位参会人，日程应出现在参会人的飞书日历中。"
        }

        return ToolResult.success(response)
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun doList(calendarIdArg: String?, args: Map<String, Any?>): ToolResult {
        val startTimeStr = args["start_time"] as? String
            ?: return ToolResult.error("start_time is required")
        val endTimeStr = args["end_time"] as? String
            ?: return ToolResult.error("end_time is required")

        val startTs = parseTimeToTimestamp(startTimeStr)
            ?: return ToolResult.error("Invalid start_time format. Must use ISO 8601 / RFC 3339 with timezone.")
        val endTs = parseTimeToTimestamp(endTimeStr)
            ?: return ToolResult.error("Invalid end_time format. Must use ISO 8601 / RFC 3339 with timezone.")

        val cid = resolveCalendarId(calendarIdArg, client)
        Log.i(TAG, "list: calendar_id=$cid, start=$startTs, end=$endTs (using instance_view)")

        // Use instance_view endpoint (NOT .../events)
        val result = client.get(
            "/open-apis/calendar/v4/calendars/$cid/events/instance_view?start_time=$startTs&end_time=$endTs&user_id_type=open_id"
        )
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to list events")
        }
        return ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun doGet(calendarIdArg: String?, args: Map<String, Any?>): ToolResult {
        val eventId = args["event_id"] as? String
            ?: return ToolResult.error("Missing event_id")
        val cid = resolveCalendarId(calendarIdArg, client)
        val result = client.get("/open-apis/calendar/v4/calendars/$cid/events/$eventId")
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to get event")
        }
        return ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun doPatch(calendarIdArg: String?, args: Map<String, Any?>): ToolResult {
        val eventId = args["event_id"] as? String
            ?: return ToolResult.error("Missing event_id")
        val cid = resolveCalendarId(calendarIdArg, client)

        val updateData = mutableMapOf<String, Any?>()
        (args["summary"] as? String)?.let { updateData["summary"] = it }
        (args["description"] as? String)?.let { updateData["description"] = it }

        // Handle time conversion
        (args["start_time"] as? String)?.let { timeStr ->
            val ts = parseTimeToTimestamp(timeStr)
                ?: return ToolResult.error("start_time 格式错误！必须使用ISO 8601 / RFC 3339 格式（包含时区）")
            updateData["start_time"] = mapOf("timestamp" to ts)
        }
        (args["end_time"] as? String)?.let { timeStr ->
            val ts = parseTimeToTimestamp(timeStr)
                ?: return ToolResult.error("end_time 格式错误！必须使用ISO 8601 / RFC 3339 格式（包含时区）")
            updateData["end_time"] = mapOf("timestamp" to ts)
        }

        // Location: accept plain string, wrap as {name: string} internally
        (args["location"] as? String)?.let { updateData["location"] = mapOf("name" to it) }

        Log.i(TAG, "patch: calendar_id=$cid, event_id=$eventId, fields=${updateData.keys.joinToString(",")}")
        val result = client.patch("/open-apis/calendar/v4/calendars/$cid/events/$eventId", updateData)
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to patch event")
        }
        return ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun doDelete(calendarIdArg: String?, args: Map<String, Any?>): ToolResult {
        val eventId = args["event_id"] as? String
            ?: return ToolResult.error("Missing event_id")
        val cid = resolveCalendarId(calendarIdArg, client)
        val needNotification = args["need_notification"] as? Boolean ?: true
        Log.i(TAG, "delete: calendar_id=$cid, event_id=$eventId, notify=$needNotification")

        val result = client.delete("/open-apis/calendar/v4/calendars/$cid/events/$eventId?need_notification=$needNotification")
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to delete event")
        }
        return ToolResult.success(mapOf("success" to true, "event_id" to eventId))
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun doSearch(calendarIdArg: String?, args: Map<String, Any?>): ToolResult {
        val query = args["query"] as? String
            ?: return ToolResult.error("Missing query")
        val cid = resolveCalendarId(calendarIdArg, client)

        val params = mutableListOf<String>()
        (args["page_size"] as? Number)?.let { params.add("page_size=${it.toInt()}") }
        (args["page_token"] as? String)?.let { params.add("page_token=$it") }
        val queryStr = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""

        val body = mapOf("query" to query)
        Log.i(TAG, "search: calendar_id=$cid, query=$query")

        // Correct endpoint: POST /calendars/:cid/events/search
        val result = client.post("/open-apis/calendar/v4/calendars/$cid/events/search$queryStr", body)
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to search events")
        }
        return ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun doReply(calendarIdArg: String?, args: Map<String, Any?>): ToolResult {
        val eventId = args["event_id"] as? String
            ?: return ToolResult.error("Missing event_id")
        val rsvpStatus = args["rsvp_status"] as? String
            ?: return ToolResult.error("Missing rsvp_status")
        val cid = resolveCalendarId(calendarIdArg, client)
        val body = mapOf("rsvp_status" to rsvpStatus)
        val result = client.post("/open-apis/calendar/v4/calendars/$cid/events/$eventId/reply", body)
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to reply event")
        }
        return ToolResult.success(mapOf("success" to true, "event_id" to eventId, "rsvp_status" to rsvpStatus))
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun doInstances(calendarIdArg: String?, args: Map<String, Any?>): ToolResult {
        val eventId = args["event_id"] as? String
            ?: return ToolResult.error("Missing event_id")
        val startTimeStr = args["start_time"] as? String
            ?: return ToolResult.error("start_time is required")
        val endTimeStr = args["end_time"] as? String
            ?: return ToolResult.error("end_time is required")

        val startTs = parseTimeToTimestamp(startTimeStr)
            ?: return ToolResult.error("Invalid start_time format.")
        val endTs = parseTimeToTimestamp(endTimeStr)
            ?: return ToolResult.error("Invalid end_time format.")

        val cid = resolveCalendarId(calendarIdArg, client)
        val params = mutableListOf("start_time=$startTs", "end_time=$endTs")
        (args["page_size"] as? Number)?.let { params.add("page_size=${it.toInt()}") }
        (args["page_token"] as? String)?.let { params.add("page_token=$it") }
        val queryStr = params.joinToString("&")

        val result = client.get("/open-apis/calendar/v4/calendars/$cid/events/$eventId/instances?$queryStr")
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to get instances")
        }
        return ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun doInstanceView(calendarIdArg: String?, args: Map<String, Any?>): ToolResult {
        val startTimeStr = args["start_time"] as? String
            ?: return ToolResult.error("start_time is required")
        val endTimeStr = args["end_time"] as? String
            ?: return ToolResult.error("end_time is required")

        val startTs = parseTimeToTimestamp(startTimeStr)
            ?: return ToolResult.error("Invalid start_time format.")
        val endTs = parseTimeToTimestamp(endTimeStr)
            ?: return ToolResult.error("Invalid end_time format.")

        val cid = resolveCalendarId(calendarIdArg, client)
        val params = mutableListOf("start_time=$startTs", "end_time=$endTs", "user_id_type=open_id")
        (args["page_size"] as? Number)?.let { params.add("page_size=${it.toInt()}") }
        (args["page_token"] as? String)?.let { params.add("page_token=$it") }
        val queryStr = params.joinToString("&")

        // instance_view does NOT require event_id - it's a calendar-level endpoint
        val result = client.get("/open-apis/calendar/v4/calendars/$cid/events/instance_view?$queryStr")
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to get instance view")
        }
        return ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
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
                        enum = listOf("create", "list", "get", "patch", "delete", "search", "reply", "instances", "instance_view")
                    ),
                    "calendar_id" to PropertySchema("string", "日历 ID（可选，不填时自动使用主日历）"),
                    "event_id" to PropertySchema("string", "日程 ID（get、patch、delete、reply、instances 时必填）"),
                    "summary" to PropertySchema("string", "日程标题（create、patch 时使用）"),
                    "description" to PropertySchema("string", "日程描述（create、patch 时使用）"),
                    "start_time" to PropertySchema("string", "开始时间，ISO 8601 / RFC 3339 格式（包含时区），例如 '2024-01-01T00:00:00+08:00'"),
                    "end_time" to PropertySchema("string", "结束时间，格式同 start_time"),
                    "user_open_id" to PropertySchema("string", "当前请求用户的 open_id（create 时强烈建议提供，格式 ou_xxx）"),
                    "attendees" to PropertySchema("array", "参会人列表（create 时使用）。type='user' 时 id 填 open_id，type='third_party' 时 id 填邮箱。",
                        items = PropertySchema("object", "参会人 {type, id}")),
                    "vchat" to PropertySchema("object", "视频会议配置（create 时使用）"),
                    "visibility" to PropertySchema("string", "可见性（create 时使用）", enum = listOf("default", "public", "private")),
                    "attendee_ability" to PropertySchema("string", "参与人权限（create 时使用，默认 can_modify_event）",
                        enum = listOf("none", "can_see_others", "can_invite_others", "can_modify_event")),
                    "free_busy_status" to PropertySchema("string", "忙闲状态（create 时使用）", enum = listOf("busy", "free")),
                    "location" to PropertySchema("string", "地点（patch 时为纯字符串，内部包装为 {name: string}）"),
                    "reminders" to PropertySchema("array", "提醒列表（create 时使用）"),
                    "recurrence" to PropertySchema("string", "重复规则 RRULE（create 时使用）"),
                    "need_notification" to PropertySchema("boolean", "是否通知参会人（delete 时使用，默认 true）"),
                    "query" to PropertySchema("string", "搜索关键词（search 时必填）"),
                    "rsvp_status" to PropertySchema("string", "回复状态（reply 时必填）", enum = listOf("accept", "decline", "tentative")),
                    "page_size" to PropertySchema("integer", "每页数量"),
                    "page_token" to PropertySchema("string", "分页标记")
                ),
                required = listOf("action")
            )
        )
    )
}

// ─── feishu_calendar_event_attendee ────────────────────────────────

class FeishuCalendarEventAttendeeTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_calendar_event_attendee"
    override val description = "飞书日程参会人管理工具。当用户要求邀请/添加参会人、查看参会人列表时使用。" +
        "Actions: create（添加参会人）, list（查询参会人列表）。"

    override fun isEnabled() = config.enableCalendarTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")
            val calendarId = args["calendar_id"] as? String
                ?: return@withContext ToolResult.error("Missing calendar_id")
            val eventId = args["event_id"] as? String
                ?: return@withContext ToolResult.error("Missing event_id")

            val basePath = "/open-apis/calendar/v4/calendars/$calendarId/events/$eventId/attendees"

            when (action) {
                // @aligned openclaw-lark v2026.3.30
                "create" -> {
                    val attendees = args["attendees"]
                        ?: return@withContext ToolResult.error("Missing attendees")
                    val needNotification = args["need_notification"] as? Boolean ?: true
                    val attendeeAbility = args["attendee_ability"] as? String

                    val body = mutableMapOf<String, Any?>(
                        "attendees" to attendees,
                        "need_notification" to needNotification
                    )
                    if (attendeeAbility != null) {
                        body["attendee_ability"] = attendeeAbility
                    }

                    // Add user_id_type=open_id query param
                    val result = client.post("$basePath?user_id_type=open_id", body)
                    if (result.isFailure) {
                        return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to add attendees")
                    }
                    ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
                }
                // @aligned openclaw-lark v2026.3.30
                "list" -> {
                    val params = mutableListOf<String>()
                    val userIdType = args["user_id_type"] as? String ?: "open_id"
                    params.add("user_id_type=$userIdType")
                    (args["page_size"] as? Number)?.let { params.add("page_size=${it.toInt()}") }
                    (args["page_token"] as? String)?.let { params.add("page_token=$it") }
                    val query = params.joinToString("&")

                    val result = client.get("$basePath?$query")
                    if (result.isFailure) {
                        return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to list attendees")
                    }
                    ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
                }
                else -> ToolResult.error("Unknown action: $action. Must be one of: create, list")
            }
        } catch (e: Exception) {
            Log.e(TAG, "feishu_calendar_event_attendee failed", e)
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
                    "action" to PropertySchema("string", "操作类型", enum = listOf("create", "list")),
                    "calendar_id" to PropertySchema("string", "日历 ID"),
                    "event_id" to PropertySchema("string", "日程 ID"),
                    "attendees" to PropertySchema("array", "参会人列表（create 时必填）。type=user 时 attendee_id 为 open_id，type=third_party 时为邮箱。",
                        items = PropertySchema("object", "参会人 {type, attendee_id}")),
                    "need_notification" to PropertySchema("boolean", "是否通知参会人（create 时使用，默认 true）"),
                    "attendee_ability" to PropertySchema("string", "参与人权限（create 时使用）",
                        enum = listOf("none", "can_see_others", "can_invite_others", "can_modify_event")),
                    "user_id_type" to PropertySchema("string", "用户 ID 类型（list 时使用，默认 open_id）",
                        enum = listOf("open_id", "union_id", "user_id")),
                    "page_size" to PropertySchema("integer", "每页数量（list 时使用）"),
                    "page_token" to PropertySchema("string", "分页标记（list 时使用）")
                ),
                required = listOf("action", "calendar_id", "event_id")
            )
        )
    )
}

// ─── feishu_calendar_freebusy ──────────────────────────────────────

class FeishuCalendarFreebusyTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_calendar_freebusy"
    override val description = "【以用户身份】飞书日历忙闲查询工具。当用户要求查询某时间段内某人是否空闲、" +
        "查看忙闲状态时使用。支持批量查询 1-10 个用户的主日历忙闲信息，用于安排会议时间。"

    override fun isEnabled() = config.enableCalendarTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String ?: "list"
            if (action != "list") {
                return@withContext ToolResult.error("Unknown action: $action. Only 'list' is supported.")
            }

            val timeMinStr = args["time_min"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: time_min")
            val timeMaxStr = args["time_max"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: time_max")
            @Suppress("UNCHECKED_CAST")
            val userIds = args["user_ids"] as? List<String>
                ?: return@withContext ToolResult.error("Missing required parameter: user_ids (flat array of open_id strings)")

            if (userIds.isEmpty()) {
                return@withContext ToolResult.error("user_ids is required (1-10 user IDs)")
            }
            if (userIds.size > 10) {
                return@withContext ToolResult.error("user_ids supports at most 10 users (current: ${userIds.size})")
            }

            // Convert time strings to RFC 3339 format (required by freebusy API)
            val timeMin = parseTimeToRFC3339(timeMinStr)
                ?: return@withContext ToolResult.error("Invalid time_min format. Must use ISO 8601 / RFC 3339 with timezone.")
            val timeMax = parseTimeToRFC3339(timeMaxStr)
                ?: return@withContext ToolResult.error("Invalid time_max format. Must use ISO 8601 / RFC 3339 with timezone.")

            Log.i(TAG, "freebusy: time_min=$timeMinStr -> $timeMin, time_max=$timeMaxStr -> $timeMax, users=${userIds.size}")

            // Correct endpoint: POST /open-apis/calendar/v4/freebusy/batch
            val body = mapOf(
                "time_min" to timeMin,
                "time_max" to timeMax,
                "user_ids" to userIds,
                "include_external_calendar" to true,
                "only_busy" to true
            )

            val result = client.post("/open-apis/calendar/v4/freebusy/batch", body)
            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to query freebusy")
            }
            ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
        } catch (e: Exception) {
            Log.e(TAG, "feishu_calendar_freebusy failed", e)
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
                    "action" to PropertySchema("string", "操作类型", enum = listOf("list")),
                    "time_min" to PropertySchema("string", "查询起始时间（ISO 8601 / RFC 3339 格式，例如 '2024-01-01T00:00:00+08:00'）"),
                    "time_max" to PropertySchema("string", "查询结束时间（ISO 8601 / RFC 3339 格式）"),
                    "user_ids" to PropertySchema("array", "用户 open_id 列表（1-10 个），扁平字符串数组",
                        items = PropertySchema("string", "用户 open_id"))
                ),
                required = listOf("action", "time_min", "time_max", "user_ids")
            )
        )
    )
}

// ─── Aggregator ────────────────────────────────────────────────────

class FeishuCalendarTools(config: FeishuConfig, client: FeishuClient) {
    private val calendarTool = FeishuCalendarCalendarTool(config, client)
    private val eventTool = FeishuCalendarEventTool(config, client)
    private val attendeeTool = FeishuCalendarEventAttendeeTool(config, client)
    private val freebusyTool = FeishuCalendarFreebusyTool(config, client)

    fun getAllTools(): List<FeishuToolBase> {
        return listOf(calendarTool, eventTool, attendeeTool, freebusyTool)
    }

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

package com.xiaomo.feishu.tools.im

/**
 * Feishu IM (Instant Messaging) tool set.
 * Aligned with ByteDance official @larksuite/openclaw-lark plugin.
 * - feishu_im_user_message: send/reply messages as user
 * - feishu_im_user_get_messages: get chat history
 * - feishu_im_user_get_thread_messages: get thread messages
 * - feishu_im_user_search_messages: cross-chat message search
 * - feishu_im_user_fetch_resource: download message resources (user token)
 * - feishu_im_bot_image: download message resources (bot token)
 */

import android.util.Log
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter

private const val TAG = "FeishuImTools"

// ─── Relative time helper ─────────────────────────────────────────

/**
 * Parse a relative_time string into a pair of seconds-level timestamps (start, end).
 * Supports: today, yesterday, day_before_yesterday, this_week, last_week,
 *           this_month, last_month, last_{N}_{unit} (unit: minutes/hours/days)
 * @aligned openclaw-lark v2026.3.30
 */
private fun parseRelativeTimeToSeconds(relativeTime: String): Pair<String, String> {
    val now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
    val todayStart = now.toLocalDate().atStartOfDay(now.zone)

    val (start, end) = when (relativeTime) {
        "today" -> todayStart to now
        "yesterday" -> todayStart.minusDays(1) to todayStart
        "day_before_yesterday" -> todayStart.minusDays(2) to todayStart.minusDays(1)
        "this_week" -> {
            val weekStart = todayStart.with(java.time.DayOfWeek.MONDAY)
            weekStart to now
        }
        "last_week" -> {
            val thisWeekStart = todayStart.with(java.time.DayOfWeek.MONDAY)
            thisWeekStart.minusWeeks(1) to thisWeekStart
        }
        "this_month" -> {
            val monthStart = todayStart.withDayOfMonth(1)
            monthStart to now
        }
        "last_month" -> {
            val thisMonthStart = todayStart.withDayOfMonth(1)
            val lastMonthStart = thisMonthStart.minusMonths(1)
            lastMonthStart to thisMonthStart
        }
        else -> {
            // last_{N}_{unit} pattern
            val regex = Regex("""last_(\d+)_(minutes?|hours?|days?)""")
            val match = regex.matchEntire(relativeTime)
            if (match != null) {
                val n = match.groupValues[1].toLong()
                val unit = match.groupValues[2].removeSuffix("s")
                val startTime = when (unit) {
                    "minute" -> now.minusMinutes(n)
                    "hour" -> now.minusHours(n)
                    "day" -> now.minusDays(n)
                    else -> now.minusDays(n)
                }
                startTime to now
            } else {
                throw IllegalArgumentException("Invalid relative_time: $relativeTime")
            }
        }
    }
    return start.toEpochSecond().toString() to end.toEpochSecond().toString()
}

/**
 * Convert ISO 8601 datetime string to seconds timestamp string.
 * @aligned openclaw-lark v2026.3.30
 */
private fun dateTimeToSecondsString(isoString: String): String {
    return try {
        val zdt = ZonedDateTime.parse(isoString, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        zdt.toEpochSecond().toString()
    } catch (e: Exception) {
        // If already a numeric timestamp, return as-is
        isoString
    }
}

/**
 * Map sort_rule enum to Feishu API sort_type parameter.
 * @aligned openclaw-lark v2026.3.30
 */
private fun sortRuleToSortType(sortRule: String?): String {
    return if (sortRule == "create_time_asc") "ByCreateTimeAsc" else "ByCreateTimeDesc"
}

// ─── feishu_im_user_message ────────────────────────────────────────

// @aligned openclaw-lark v2026.3.30
class FeishuImUserMessageTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_im_user_message"
    override val description = "飞书用户身份 IM 消息工具。有且仅当用户明确要求以自己身份发消息、回复消息时使用，当没有明确要求时优先使用message系统工具。" +
        "\n\nActions:" +
        "\n- send（发送消息）：发送消息到私聊或群聊。私聊用 receive_id_type=open_id，群聊用 receive_id_type=chat_id" +
        "\n- reply（回复消息）：回复指定 message_id 的消息，支持话题回复（reply_in_thread=true）" +
        "\n\n【重要】content 必须是合法 JSON 字符串，格式取决于 msg_type。" +
        "最常用：text 类型 content 为 '{\"text\":\"消息内容\"}'。" +
        "\n\n【安全约束】此工具以用户身份发送消息，发出后对方看到的发送者是用户本人。" +
        "调用前必须先向用户确认：1) 发送对象（哪个人或哪个群）2) 消息内容。" +
        "禁止在用户未明确同意的情况下自行发送消息。"

    override fun isEnabled() = config.enableImTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            when (action) {
                "send" -> {
                    val receiveIdType = args["receive_id_type"] as? String
                        ?: return@withContext ToolResult.error("Missing receive_id_type")
                    val receiveId = args["receive_id"] as? String
                        ?: return@withContext ToolResult.error("Missing receive_id")
                    val msgType = args["msg_type"] as? String
                        ?: return@withContext ToolResult.error("Missing msg_type")
                    val content = args["content"] as? String
                        ?: return@withContext ToolResult.error("Missing content")

                    val body = mutableMapOf<String, Any>(
                        "receive_id" to receiveId,
                        "msg_type" to msgType,
                        "content" to content
                    )
                    (args["uuid"] as? String)?.let { body["uuid"] = it }

                    val result = client.post(
                        "/open-apis/im/v1/messages?receive_id_type=$receiveIdType",
                        body
                    )
                    if (result.isFailure) {
                        return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to send message")
                    }
                    ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
                }
                "reply" -> {
                    val messageId = args["message_id"] as? String
                        ?: return@withContext ToolResult.error("Missing message_id")
                    val msgType = args["msg_type"] as? String
                        ?: return@withContext ToolResult.error("Missing msg_type")
                    val content = args["content"] as? String
                        ?: return@withContext ToolResult.error("Missing content")

                    val body = mutableMapOf<String, Any>(
                        "msg_type" to msgType,
                        "content" to content
                    )
                    (args["reply_in_thread"] as? Boolean)?.let { body["reply_in_thread"] = it }
                    (args["uuid"] as? String)?.let { body["uuid"] = it }

                    val result = client.post("/open-apis/im/v1/messages/$messageId/reply", body)
                    if (result.isFailure) {
                        return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to reply message")
                    }
                    ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
                }
                else -> ToolResult.error("Unknown action: $action. Must be one of: send, reply")
            }
        } catch (e: Exception) {
            Log.e(TAG, "feishu_im_user_message failed", e)
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
                    "action" to PropertySchema("string", "操作类型", enum = listOf("send", "reply")),
                    "receive_id_type" to PropertySchema("string", "接收者 ID 类型：open_id（私聊，ou_xxx）、chat_id（群聊，oc_xxx）",
                        enum = listOf("open_id", "chat_id")),
                    "receive_id" to PropertySchema("string", "接收者 ID，与 receive_id_type 对应。open_id 填 'ou_xxx'，chat_id 填 'oc_xxx'"),
                    "message_id" to PropertySchema("string", "被回复消息的 ID（om_xxx 格式）（reply 时必填）"),
                    "msg_type" to PropertySchema("string", "消息类型",
                        enum = listOf("text", "post", "image", "file", "audio", "media", "interactive", "share_chat", "share_user")),
                    "content" to PropertySchema("string", "消息内容 JSON 字符串"),
                    "reply_in_thread" to PropertySchema("boolean", "是否以话题形式回复（reply 时使用）"),
                    "uuid" to PropertySchema("string", "幂等 UUID（可选）")
                ),
                required = listOf("action")
            )
        )
    )
}

// ─── feishu_im_user_get_messages ───────────────────────────────────

// @aligned openclaw-lark v2026.3.30
class FeishuImUserGetMessagesTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_im_user_get_messages"
    override val description = "【以用户身份】获取群聊或单聊的历史消息。" +
        "\n\n用法：" +
        "\n- 通过 chat_id 获取群聊/单聊消息" +
        "\n- 通过 open_id 获取与指定用户的单聊消息（自动解析 chat_id）" +
        "\n- 支持时间范围过滤：relative_time（如 today、last_3_days）或 start_time/end_time（ISO 8601 格式）" +
        "\n- 支持分页：page_size + page_token" +
        "\n\n【参数约束】" +
        "\n- open_id 和 chat_id 必须二选一，不能同时提供" +
        "\n- relative_time 和 start_time/end_time 不能同时使用" +
        "\n- page_size 范围 1-50，默认 50" +
        "\n\n返回消息列表，每条消息包含 message_id、msg_type、content（AI 可读文本）、sender、create_time 等字段。"

    override fun isEnabled() = config.enableImTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val chatId = args["chat_id"] as? String
            val openId = args["open_id"] as? String
            val relativeTime = args["relative_time"] as? String
            val startTime = args["start_time"] as? String
            val endTime = args["end_time"] as? String

            if (chatId == null && openId == null) {
                return@withContext ToolResult.error("Either chat_id or open_id is required")
            }
            if (chatId != null && openId != null) {
                return@withContext ToolResult.error("Cannot provide both open_id and chat_id, please provide only one")
            }
            if (relativeTime != null && (startTime != null || endTime != null)) {
                return@withContext ToolResult.error("Cannot use both relative_time and start_time/end_time")
            }

            // Resolve chat_id: if open_id is provided, resolve via P2P batch query API
            val resolvedChatId = if (openId != null) {
                Log.d(TAG, "Resolving P2P chat for open_id=$openId")
                val batchBody = mapOf("chatter_ids" to listOf(openId))
                val batchResult = client.post(
                    "/open-apis/im/v1/chat_p2p/batch_query?user_id_type=open_id",
                    batchBody
                )
                if (batchResult.isFailure) {
                    return@withContext ToolResult.error(
                        "Failed to resolve P2P chat for open_id=$openId: ${batchResult.exceptionOrNull()?.message}"
                    )
                }
                val batchData = batchResult.getOrNull()?.getAsJsonObject("data")
                val p2pChats = batchData?.getAsJsonArray("p2p_chats")
                if (p2pChats == null || p2pChats.size() == 0) {
                    return@withContext ToolResult.error(
                        "No 1-on-1 chat found with open_id=$openId. You may not have chat history with this user."
                    )
                }
                p2pChats[0].asJsonObject.get("chat_id")?.asString
                    ?: return@withContext ToolResult.error("P2P chat resolved but chat_id is null")
            } else {
                chatId!!
            }

            // Resolve time range
            val timeRange: Pair<String?, String?> = if (relativeTime != null) {
                val (s, e) = parseRelativeTimeToSeconds(relativeTime)
                s to e
            } else {
                val s = startTime?.let { dateTimeToSecondsString(it) }
                val e = endTime?.let { dateTimeToSecondsString(it) }
                s to e
            }

            val sortRule = args["sort_rule"] as? String

            val params = mutableListOf(
                "container_id_type=chat",
                "container_id=$resolvedChatId",
                "card_msg_content_type=raw_card_content"
            )
            params.add("sort_type=${sortRuleToSortType(sortRule)}")
            val pageSize = (args["page_size"] as? Number)?.toInt() ?: 50
            params.add("page_size=$pageSize")
            (args["page_token"] as? String)?.let { params.add("page_token=$it") }
            timeRange.first?.let { params.add("start_time=$it") }
            timeRange.second?.let { params.add("end_time=$it") }

            val query = params.joinToString("&")
            val result = client.get("/open-apis/im/v1/messages?$query")
            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to get messages")
            }
            ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
        } catch (e: Exception) {
            Log.e(TAG, "feishu_im_user_get_messages failed", e)
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
                    "chat_id" to PropertySchema("string", "会话 ID（oc_xxx），支持单聊和群聊。与 open_id 互斥"),
                    "open_id" to PropertySchema("string", "用户 open_id（ou_xxx），获取与该用户的单聊消息。与 chat_id 互斥"),
                    "sort_rule" to PropertySchema("string", "排序方式，默认 create_time_desc（最新消息在前）",
                        enum = listOf("create_time_asc", "create_time_desc")),
                    "page_size" to PropertySchema("integer", "每页消息数（1-50），默认 50"),
                    "page_token" to PropertySchema("string", "分页标记，用于获取下一页"),
                    "relative_time" to PropertySchema("string",
                        "相对时间范围：today / yesterday / day_before_yesterday / this_week / last_week / this_month / last_month / last_{N}_{unit}（unit: minutes/hours/days）。与 start_time/end_time 互斥"),
                    "start_time" to PropertySchema("string", "起始时间（ISO 8601 格式，如 2026-02-27T00:00:00+08:00）。与 relative_time 互斥"),
                    "end_time" to PropertySchema("string", "结束时间（ISO 8601 格式，如 2026-02-27T23:59:59+08:00）。与 relative_time 互斥")
                ),
                required = emptyList()
            )
        )
    )
}

// ─── feishu_im_user_get_thread_messages ────────────────────────────

// @aligned openclaw-lark v2026.3.30
class FeishuImUserGetThreadMessagesTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_im_user_get_thread_messages"
    override val description = "【以用户身份】获取话题（thread）内的消息列表。" +
        "\n\n用法：" +
        "\n- 通过 thread_id（omt_xxx）获取话题内的所有消息" +
        "\n- 支持分页：page_size + page_token" +
        "\n\n【注意】话题消息不支持时间范围过滤（飞书 API 限制）" +
        "\n\n返回消息列表，格式同 feishu_im_user_get_messages。"

    override fun isEnabled() = config.enableImTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val threadId = args["thread_id"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: thread_id")

            val sortRule = args["sort_rule"] as? String

            val params = mutableListOf(
                "container_id_type=thread",
                "container_id=$threadId",
                "card_msg_content_type=raw_card_content"
            )
            params.add("sort_type=${sortRuleToSortType(sortRule)}")
            val pageSize = (args["page_size"] as? Number)?.toInt() ?: 50
            params.add("page_size=$pageSize")
            (args["page_token"] as? String)?.let { params.add("page_token=$it") }

            val query = params.joinToString("&")
            // Use /open-apis/im/v1/messages with container_id_type=thread (NOT /threads/:id/messages)
            val result = client.get("/open-apis/im/v1/messages?$query")
            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to get thread messages")
            }
            ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
        } catch (e: Exception) {
            Log.e(TAG, "feishu_im_user_get_thread_messages failed", e)
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
                    "thread_id" to PropertySchema("string", "话题 ID（omt_xxx 格式）"),
                    "sort_rule" to PropertySchema("string", "排序方式，默认 create_time_desc（最新消息在前）",
                        enum = listOf("create_time_asc", "create_time_desc")),
                    "page_size" to PropertySchema("integer", "每页消息数（1-50），默认 50"),
                    "page_token" to PropertySchema("string", "分页标记，用于获取下一页")
                ),
                required = listOf("thread_id")
            )
        )
    )
}

// ─── feishu_im_user_search_messages ────────────────────────────────

// @aligned openclaw-lark v2026.3.30
class FeishuImUserSearchMessagesTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_im_user_search_messages"
    override val description = "【以用户身份】跨会话搜索飞书消息。" +
        "\n\n用法：" +
        "\n- 按关键词搜索消息内容" +
        "\n- 按发送者、被@用户、消息类型过滤" +
        "\n- 按时间范围过滤：relative_time 或 start_time/end_time" +
        "\n- 限定在某个会话内搜索（chat_id）" +
        "\n- 支持分页：page_size + page_token" +
        "\n\n【参数约束】" +
        "\n- 所有参数均可选，但至少应提供一个过滤条件" +
        "\n- relative_time 和 start_time/end_time 不能同时使用" +
        "\n- page_size 范围 1-50，默认 50" +
        "\n\n返回消息列表，每条消息包含 message_id、msg_type、content、sender、create_time 等字段。"

    override fun isEnabled() = config.enableImTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val relativeTime = args["relative_time"] as? String
            val startTime = args["start_time"] as? String
            val endTime = args["end_time"] as? String

            if (relativeTime != null && (startTime != null || endTime != null)) {
                return@withContext ToolResult.error("Cannot use both relative_time and start_time/end_time")
            }

            // Resolve time range
            val timeRange: Pair<String?, String?> = if (relativeTime != null) {
                val (s, e) = parseRelativeTimeToSeconds(relativeTime)
                s to e
            } else {
                val s = startTime?.let { dateTimeToSecondsString(it) }
                val e = endTime?.let { dateTimeToSecondsString(it) }
                s to e
            }

            // Build search request body with correct field names
            val query = args["query"] as? String ?: ""
            val body = mutableMapOf<String, Any>(
                "query" to query,
                "start_time" to (timeRange.first ?: "978307200"),
                "end_time" to (timeRange.second ?: (System.currentTimeMillis() / 1000).toString())
            )
            @Suppress("UNCHECKED_CAST")
            (args["sender_ids"] as? List<String>)?.let { body["from_ids"] = it }
            (args["chat_id"] as? String)?.let { body["chat_ids"] = listOf(it) }
            @Suppress("UNCHECKED_CAST")
            (args["mention_ids"] as? List<String>)?.let { body["at_chatter_ids"] = it }
            (args["message_type"] as? String)?.let { body["message_type"] = it }
            (args["sender_type"] as? String)?.let {
                if (it != "all") body["from_type"] = it
            }
            (args["chat_type"] as? String)?.let {
                body["chat_type"] = when (it) {
                    "group" -> "group_chat"
                    "p2p" -> "p2p_chat"
                    else -> it
                }
            }

            val pageSize = (args["page_size"] as? Number)?.toInt() ?: 50
            val pageToken = args["page_token"] as? String

            // Correct endpoint: POST /open-apis/search/v2/message
            val queryParams = mutableListOf(
                "user_id_type=open_id",
                "page_size=$pageSize"
            )
            pageToken?.let { queryParams.add("page_token=$it") }
            val queryString = queryParams.joinToString("&")

            val result = client.post("/open-apis/search/v2/message?$queryString", body)
            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to search messages")
            }
            ToolResult.success(result.getOrNull()?.getAsJsonObject("data"))
        } catch (e: Exception) {
            Log.e(TAG, "feishu_im_user_search_messages failed", e)
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
                    "query" to PropertySchema("string", "搜索关键词，匹配消息内容。可为空字符串表示不按内容过滤"),
                    "sender_ids" to PropertySchema("array", "发送者 open_id 列表", items = PropertySchema("string", "发送者的 open_id（ou_xxx）")),
                    "chat_id" to PropertySchema("string", "限定搜索范围的会话 ID（oc_xxx）"),
                    "mention_ids" to PropertySchema("array", "被@用户的 open_id 列表", items = PropertySchema("string", "被@用户的 open_id（ou_xxx）")),
                    "message_type" to PropertySchema("string", "消息类型过滤：file / image / media",
                        enum = listOf("file", "image", "media")),
                    "sender_type" to PropertySchema("string", "发送者类型：user / bot / all。默认 user",
                        enum = listOf("user", "bot", "all")),
                    "chat_type" to PropertySchema("string", "会话类型：group（群聊）/ p2p（单聊）",
                        enum = listOf("group", "p2p")),
                    "relative_time" to PropertySchema("string",
                        "相对时间范围：today / yesterday / day_before_yesterday / this_week / last_week / this_month / last_month / last_{N}_{unit}（unit: minutes/hours/days）。与 start_time/end_time 互斥"),
                    "start_time" to PropertySchema("string", "起始时间（ISO 8601 格式）。与 relative_time 互斥"),
                    "end_time" to PropertySchema("string", "结束时间（ISO 8601 格式）。与 relative_time 互斥"),
                    "page_size" to PropertySchema("integer", "每页消息数（1-50），默认 50"),
                    "page_token" to PropertySchema("string", "分页标记，用于获取下一页")
                ),
                required = emptyList()
            )
        )
    )
}

// ─── feishu_im_user_fetch_resource ─────────────────────────────────

// @aligned openclaw-lark v2026.3.30
class FeishuImUserFetchResourceTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_im_user_fetch_resource"
    override val description = "【以用户身份】下载飞书 IM 消息中的文件或图片资源到本地文件。需要用户 OAuth 授权。"

    override fun isEnabled() = config.enableImTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val messageId = args["message_id"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: message_id")
            val fileKey = args["file_key"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: file_key")
            val type = args["type"] as? String ?: "file"

            val path = "/open-apis/im/v1/messages/$messageId/resources/$fileKey?type=$type"
            val result = client.downloadRaw(path)
            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to download resource")
            }

            val bytes = result.getOrNull()!!
            val ext = if (type == "image") "png" else "bin"
            val tmpFile = File.createTempFile("feishu_resource_", ".$ext")
            tmpFile.writeBytes(bytes)

            Log.d(TAG, "Resource downloaded: ${tmpFile.absolutePath} (${bytes.size} bytes)")
            ToolResult.success(mapOf(
                "file_path" to tmpFile.absolutePath,
                "size" to bytes.size,
                "type" to type
            ))
        } catch (e: Exception) {
            Log.e(TAG, "feishu_im_user_fetch_resource failed", e)
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
                    "message_id" to PropertySchema("string", "消息 ID（om_xxx 格式）"),
                    "file_key" to PropertySchema("string", "资源 Key（图片 img_xxx 或文件 file_xxx）"),
                    "type" to PropertySchema("string", "资源类型", enum = listOf("image", "file"))
                ),
                required = listOf("message_id", "file_key")
            )
        )
    )
}

// ─── feishu_im_bot_image ───────────────────────────────────────────

// @aligned openclaw-lark v2026.3.30
class FeishuImBotImageTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_im_bot_image"
    override val description = "【以机器人身份】下载飞书 IM 消息中的图片或文件资源到本地。"

    override fun isEnabled() = config.enableImTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val messageId = args["message_id"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: message_id")
            val fileKey = args["file_key"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: file_key")
            val type = args["type"] as? String ?: "image"

            val path = "/open-apis/im/v1/messages/$messageId/resources/$fileKey?type=$type"
            val result = client.downloadRaw(path)
            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to download resource")
            }

            val bytes = result.getOrNull()!!
            val ext = if (type == "image") "png" else "bin"
            val tmpFile = File.createTempFile("feishu_bot_resource_", ".$ext")
            tmpFile.writeBytes(bytes)

            Log.d(TAG, "Bot resource downloaded: ${tmpFile.absolutePath} (${bytes.size} bytes)")
            ToolResult.success(mapOf(
                "file_path" to tmpFile.absolutePath,
                "size" to bytes.size,
                "type" to type
            ))
        } catch (e: Exception) {
            Log.e(TAG, "feishu_im_bot_image failed", e)
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
                    "message_id" to PropertySchema("string", "消息 ID（om_xxx 格式）"),
                    "file_key" to PropertySchema("string", "资源 Key（图片 img_xxx 或文件 file_xxx）"),
                    "type" to PropertySchema("string", "资源类型", enum = listOf("image", "file"))
                ),
                required = listOf("message_id", "file_key")
            )
        )
    )
}

// ─── Aggregator ────────────────────────────────────────────────────

class FeishuImTools(config: FeishuConfig, client: FeishuClient) {
    private val userMessageTool = FeishuImUserMessageTool(config, client)
    private val getMessagesTool = FeishuImUserGetMessagesTool(config, client)
    private val getThreadMessagesTool = FeishuImUserGetThreadMessagesTool(config, client)
    private val searchMessagesTool = FeishuImUserSearchMessagesTool(config, client)
    private val fetchResourceTool = FeishuImUserFetchResourceTool(config, client)
    private val botImageTool = FeishuImBotImageTool(config, client)

    fun getAllTools(): List<FeishuToolBase> {
        return listOf(userMessageTool, getMessagesTool, getThreadMessagesTool, searchMessagesTool, fetchResourceTool, botImageTool)
    }

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

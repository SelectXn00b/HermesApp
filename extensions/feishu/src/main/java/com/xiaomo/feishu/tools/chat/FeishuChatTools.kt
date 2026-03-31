package com.xiaomo.feishu.tools.chat

import android.util.Log
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 飞书群聊工具集
 * 对齐 @larksuite/openclaw-lark chat-tools
 */
class FeishuChatTools(config: FeishuConfig, client: FeishuClient) {
    private val chatTool = FeishuChatTool(config, client)
    private val membersTool = FeishuChatMembersTool(config, client)

    fun getAllTools(): List<FeishuToolBase> = listOf(chatTool, membersTool)

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

// ---------------------------------------------------------------------------
// FeishuChatTool
// ---------------------------------------------------------------------------

class FeishuChatTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    companion object {
        private const val TAG = "FeishuChatTool"
    }

    override val name = "feishu_chat"

    // @aligned openclaw-lark v2026.3.30
    override val description = "以用户身份调用飞书群聊管理工具。Actions: search（搜索群列表，支持关键词匹配群名称、群成员）, " +
            "get（获取指定群的详细信息，包括群名称、描述、头像、群主、权限配置等）。"

    override fun isEnabled() = config.enableChatTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            when (action) {
                "search" -> executeSearch(args)
                "get" -> executeGet(args)
                else -> ToolResult.error("Unknown action: $action. Supported: search, get")
            }
        } catch (e: Exception) {
            Log.e(TAG, "execute failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun executeSearch(args: Map<String, Any?>): ToolResult {
        val queryStr = args["query"] as? String
            ?: return ToolResult.error("Missing required parameter: query")

        val params = mutableListOf("query=$queryStr")
        (args["page_size"] as? Number)?.let { params.add("page_size=${it.toInt()}") }
        (args["page_token"] as? String)?.let { params.add("page_token=$it") }
        (args["user_id_type"] as? String)?.let { params.add("user_id_type=$it") }

        val query = "?${params.joinToString("&")}"
        val result = client.get("/open-apis/im/v1/chats/search$query")

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to search chats")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        Log.d(TAG, "Chats searched")
        return ToolResult.success(data)
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun executeGet(args: Map<String, Any?>): ToolResult {
        val chatId = args["chat_id"] as? String
            ?: return ToolResult.error("Missing required parameter: chat_id")

        val params = mutableListOf<String>()
        (args["user_id_type"] as? String)?.let { params.add("user_id_type=$it") }

        val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val result = client.get("/open-apis/im/v1/chats/$chatId$query")

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to get chat info")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        Log.d(TAG, "Chat info retrieved: $chatId")
        return ToolResult.success(data)
    }

    // @aligned openclaw-lark v2026.3.30
    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "action" to PropertySchema(
                        type = "string",
                        description = "操作类型",
                        enum = listOf("search", "get")
                    ),
                    "chat_id" to PropertySchema(
                        type = "string",
                        description = "群聊 ID（get 操作必填）"
                    ),
                    "query" to PropertySchema(
                        type = "string",
                        description = "搜索关键词（search 操作必填）。支持匹配群名称、群成员名称。支持多语种、拼音、前缀等模糊搜索。"
                    ),
                    "user_id_type" to PropertySchema(
                        type = "string",
                        description = "用户 ID 类型（可选，默认 open_id）",
                        enum = listOf("open_id", "union_id", "user_id")
                    ),
                    "page_size" to PropertySchema(
                        type = "number",
                        description = "分页大小（search 操作可选）"
                    ),
                    "page_token" to PropertySchema(
                        type = "string",
                        description = "分页标记（search 操作可选）"
                    )
                ),
                required = listOf("action")
            )
        )
    )
}

// ---------------------------------------------------------------------------
// FeishuChatMembersTool
// ---------------------------------------------------------------------------

class FeishuChatMembersTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    companion object {
        private const val TAG = "FeishuChatMembersTool"
    }

    override val name = "feishu_chat_members"

    // @aligned openclaw-lark v2026.3.30
    override val description = "以用户的身份获取指定群组的成员列表。返回成员信息，包含成员 ID、姓名等。" +
            "注意：不会返回群组内的机器人成员。"

    override fun isEnabled() = config.enableChatTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val chatId = args["chat_id"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: chat_id")

            val params = mutableListOf<String>()
            (args["member_id_type"] as? String)?.let { params.add("member_id_type=$it") }
            (args["page_size"] as? Number)?.let { params.add("page_size=${it.toInt()}") }
            (args["page_token"] as? String)?.let { params.add("page_token=$it") }

            val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
            val result = client.get("/open-apis/im/v1/chats/$chatId/members$query")

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to get chat members")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            Log.d(TAG, "Chat members retrieved: $chatId")
            ToolResult.success(data)

        } catch (e: Exception) {
            Log.e(TAG, "execute failed", e)
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
                    "chat_id" to PropertySchema(
                        type = "string",
                        description = "群聊 ID（格式如 oc_xxx）"
                    ),
                    "member_id_type" to PropertySchema(
                        type = "string",
                        description = "成员 ID 类型（可选，默认 open_id）",
                        enum = listOf("open_id", "union_id", "user_id")
                    ),
                    "page_size" to PropertySchema(
                        type = "number",
                        description = "分页大小（可选，默认 20）"
                    ),
                    "page_token" to PropertySchema(
                        type = "string",
                        description = "分页标记（可选）"
                    )
                ),
                required = listOf("chat_id")
            )
        )
    )
}

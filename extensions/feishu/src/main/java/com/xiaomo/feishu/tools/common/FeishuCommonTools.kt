package com.xiaomo.feishu.tools.common

/**
 * Feishu Common tool set.
 * Aligned with ByteDance official @larksuite/openclaw-lark plugin.
 * - feishu_get_user: get user info (self or by user_id)
 * - feishu_search_user: search users by keyword
 */

import android.util.Log
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FeishuCommonTools"

// ─── feishu_get_user ───────────────────────────────────────────────

// @aligned openclaw-lark v2026.3.30
class FeishuGetUserTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_get_user"
    override val description = "获取用户信息。不传 user_id 时获取当前用户自己的信息；传 user_id 时获取指定用户的信息。" +
        "返回用户姓名、头像、邮箱、手机号、部门等信息。"

    override fun isEnabled() = config.enableCommonTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val userId = args["user_id"] as? String
            val userIdType = args["user_id_type"] as? String ?: "open_id"

            if (userId == null) {
                // Get current user info
                val result = client.get("/open-apis/authen/v1/user_info")
                if (result.isFailure) {
                    return@withContext ToolResult.error(
                        result.exceptionOrNull()?.message ?: "Failed to get current user info"
                    )
                }
                val data = result.getOrNull()?.getAsJsonObject("data")
                ToolResult.success(data)
            } else {
                // Get specified user info
                val result = client.get("/open-apis/contact/v3/users/$userId?user_id_type=$userIdType")
                if (result.isFailure) {
                    return@withContext ToolResult.error(
                        result.exceptionOrNull()?.message ?: "Failed to get user info"
                    )
                }
                val data = result.getOrNull()?.getAsJsonObject("data")
                ToolResult.success(data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "feishu_get_user failed", e)
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
                    "user_id" to PropertySchema("string", "用户 ID（不传则获取当前用户自己的信息）"),
                    "user_id_type" to PropertySchema("string", "用户 ID 类型（默认 open_id）",
                        enum = listOf("open_id", "union_id", "user_id"))
                ),
                required = emptyList()
            )
        )
    )
}

// ─── feishu_search_user ────────────────────────────────────────────

// @aligned openclaw-lark v2026.3.30
class FeishuSearchUserTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_search_user"
    override val description = "搜索员工信息（通过关键词搜索姓名、手机号、邮箱）。返回匹配的员工列表，" +
        "包含姓名、部门、open_id 等信息。"

    override fun isEnabled() = config.enableCommonTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val query = args["query"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: query")

            val params = mutableListOf("query=$query")
            (args["page_size"] as? Number)?.let { params.add("page_size=${it.toInt()}") }
            (args["page_token"] as? String)?.let { params.add("page_token=$it") }

            val queryString = params.joinToString("&")
            val result = client.get("/open-apis/search/v1/user?$queryString")
            if (result.isFailure) {
                return@withContext ToolResult.error(
                    result.exceptionOrNull()?.message ?: "Failed to search users"
                )
            }
            val data = result.getOrNull()?.getAsJsonObject("data")
            ToolResult.success(data)
        } catch (e: Exception) {
            Log.e(TAG, "feishu_search_user failed", e)
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
                    "query" to PropertySchema("string", "搜索关键词（姓名、手机号或邮箱）"),
                    "page_size" to PropertySchema("integer", "每页数量（默认 20）"),
                    "page_token" to PropertySchema("string", "分页标记")
                ),
                required = listOf("query")
            )
        )
    )
}

// ─── Aggregator ────────────────────────────────────────────────────

class FeishuCommonTools(config: FeishuConfig, client: FeishuClient) {
    private val getUserTool = FeishuGetUserTool(config, client)
    private val searchUserTool = FeishuSearchUserTool(config, client)

    fun getAllTools(): List<FeishuToolBase> = listOf(getUserTool, searchUserTool)

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

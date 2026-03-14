package com.xiaomo.feishu.tools.chat

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
 * 飞书群聊工具集
 * 对齐 OpenClaw src/chat-tools
 */
class FeishuChatTools(config: FeishuConfig, client: FeishuClient) {
    private val createTool = ChatCreateTool(config, client)
    private val infoTool = ChatInfoTool(config, client)
    private val addMemberTool = ChatAddMemberTool(config, client)
    private val removeMemberTool = ChatRemoveMemberTool(config, client)

    fun getAllTools(): List<FeishuToolBase> {
        return listOf(createTool, infoTool, addMemberTool, removeMemberTool)
    }

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

/**
 * 创建群聊工具
 */
class ChatCreateTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_chat_create"
    override val description = "创建飞书群聊"

    override fun isEnabled() = config.enableChatTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val name = args["name"] as? String ?: return@withContext ToolResult.error("Missing name")
            val description = args["description"] as? String
            @Suppress("UNCHECKED_CAST")
            val userIds = args["user_ids"] as? List<String>

            val body = mutableMapOf<String, Any>("name" to name)
            if (description != null) body["description"] = description
            if (userIds != null && userIds.isNotEmpty()) {
                body["user_ids"] = userIds
            }

            val result = client.post("/open-apis/im/v1/chats", body)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val chatId = data?.get("chat_id")?.asString
                ?: return@withContext ToolResult.error("Missing chat_id")

            Log.d("ChatCreateTool", "Chat created: $chatId")
            ToolResult.success(mapOf(
                "chat_id" to chatId,
                "name" to name
            ))

        } catch (e: Exception) {
            Log.e("ChatCreateTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "name" to PropertySchema("string", "群聊名称"),
                    "description" to PropertySchema("string", "群聊描述（可选）"),
                    "user_ids" to PropertySchema("array", "初始成员ID列表（可选）", items = PropertySchema("string", "用户ID"))
                ),
                required = listOf("name")
            )
        )
    )
}

/**
 * 获取群聊信息工具
 */
class ChatInfoTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_chat_info"
    override val description = "获取飞书群聊信息"

    override fun isEnabled() = config.enableChatTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val chatId = args["chat_id"] as? String ?: return@withContext ToolResult.error("Missing chat_id")

            val result = client.get("/open-apis/im/v1/chats/$chatId")

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val name = data?.get("name")?.asString ?: ""
            val description = data?.get("description")?.asString ?: ""
            val ownerUserId = data?.get("owner_user_id")?.asString ?: ""

            Log.d("ChatInfoTool", "Chat info: $chatId")
            ToolResult.success(mapOf(
                "chat_id" to chatId,
                "name" to name,
                "description" to description,
                "owner_user_id" to ownerUserId
            ))

        } catch (e: Exception) {
            Log.e("ChatInfoTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "chat_id" to PropertySchema("string", "群聊ID")
                ),
                required = listOf("chat_id")
            )
        )
    )
}

/**
 * 添加群成员工具
 */
class ChatAddMemberTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_chat_add_member"
    override val description = "添加飞书群聊成员"

    override fun isEnabled() = config.enableChatTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val chatId = args["chat_id"] as? String ?: return@withContext ToolResult.error("Missing chat_id")
            @Suppress("UNCHECKED_CAST")
            val userIds = args["user_ids"] as? List<String> ?: return@withContext ToolResult.error("Missing user_ids")

            val body = mapOf("id_list" to userIds)

            val result = client.post("/open-apis/im/v1/chats/$chatId/members", body)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            Log.d("ChatAddMemberTool", "Members added to chat: $chatId")
            ToolResult.success(mapOf(
                "chat_id" to chatId,
                "added_count" to userIds.size
            ))

        } catch (e: Exception) {
            Log.e("ChatAddMemberTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "chat_id" to PropertySchema("string", "群聊ID"),
                    "user_ids" to PropertySchema("array", "要添加的用户ID列表", items = PropertySchema("string", "用户ID"))
                ),
                required = listOf("chat_id", "user_ids")
            )
        )
    )
}

/**
 * 移除群成员工具
 */
class ChatRemoveMemberTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_chat_remove_member"
    override val description = "移除飞书群聊成员"

    override fun isEnabled() = config.enableChatTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val chatId = args["chat_id"] as? String ?: return@withContext ToolResult.error("Missing chat_id")
            @Suppress("UNCHECKED_CAST")
            val userIds = args["user_ids"] as? List<String> ?: return@withContext ToolResult.error("Missing user_ids")

            // DELETE 请求通常不带 body，使用 query params 或单独的 API
            // 这里简化处理，实际可能需要调整
            val result = client.delete("/open-apis/im/v1/chats/$chatId/members")

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            Log.d("ChatRemoveMemberTool", "Members removed from chat: $chatId")
            ToolResult.success(mapOf(
                "chat_id" to chatId,
                "removed_count" to userIds.size
            ))

        } catch (e: Exception) {
            Log.e("ChatRemoveMemberTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "chat_id" to PropertySchema("string", "群聊ID"),
                    "user_ids" to PropertySchema("array", "要移除的用户ID列表", items = PropertySchema("string", "用户ID"))
                ),
                required = listOf("chat_id", "user_ids")
            )
        )
    )
}

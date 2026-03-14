package com.xiaomo.feishu.tools.perm

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
 * 飞书权限工具集
 * 对齐 OpenClaw src/perm-tools
 */
class FeishuPermTools(config: FeishuConfig, client: FeishuClient) {
    private val checkTool = PermCheckTool(config, client)
    private val grantTool = PermGrantTool(config, client)
    private val revokeTool = PermRevokeTool(config, client)

    fun getAllTools(): List<FeishuToolBase> {
        return listOf(checkTool, grantTool, revokeTool)
    }

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

/**
 * 检查权限工具
 */
class PermCheckTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_perm_check"
    override val description = "检查飞书文档权限"

    override fun isEnabled() = config.enablePermTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val token = args["token"] as? String ?: return@withContext ToolResult.error("Missing token")
            val type = args["type"] as? String ?: "doc" // doc, sheet, bitable, etc.

            val result = client.get("/open-apis/drive/v1/permissions/$token/public?type=$type")

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val permissionPublic = data?.get("permission_public")?.asString ?: "private"
            val externalAccess = data?.get("external_access")?.asBoolean ?: false

            Log.d("PermCheckTool", "Permission checked: $token")
            ToolResult.success(mapOf(
                "token" to token,
                "type" to type,
                "permission_public" to permissionPublic,
                "external_access" to externalAccess
            ))

        } catch (e: Exception) {
            Log.e("PermCheckTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "token" to PropertySchema("string", "文档token"),
                    "type" to PropertySchema("string", "文档类型（doc/sheet/bitable等，默认doc）")
                ),
                required = listOf("token")
            )
        )
    )
}

/**
 * 授予权限工具
 */
class PermGrantTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_perm_grant"
    override val description = "授予飞书文档权限"

    override fun isEnabled() = config.enablePermTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val token = args["token"] as? String ?: return@withContext ToolResult.error("Missing token")
            val type = args["type"] as? String ?: "doc"
            val memberType = args["member_type"] as? String ?: "user" // user, chat, etc.
            val memberId = args["member_id"] as? String ?: return@withContext ToolResult.error("Missing member_id")
            val perm = args["perm"] as? String ?: "view" // view, edit, full_access

            val body = mapOf(
                "type" to type,
                "member_type" to memberType,
                "member_id" to memberId,
                "perm" to perm
            )

            val result = client.post("/open-apis/drive/v1/permissions/$token/members", body)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            Log.d("PermGrantTool", "Permission granted: $token to $memberId")
            ToolResult.success(mapOf(
                "token" to token,
                "member_id" to memberId,
                "perm" to perm
            ))

        } catch (e: Exception) {
            Log.e("PermGrantTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "token" to PropertySchema("string", "文档token"),
                    "type" to PropertySchema("string", "文档类型（doc/sheet/bitable等，默认doc）"),
                    "member_type" to PropertySchema("string", "成员类型（user/chat等，默认user）"),
                    "member_id" to PropertySchema("string", "成员ID"),
                    "perm" to PropertySchema("string", "权限级别（view/edit/full_access，默认view）")
                ),
                required = listOf("token", "member_id")
            )
        )
    )
}

/**
 * 撤销权限工具
 */
class PermRevokeTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_perm_revoke"
    override val description = "撤销飞书文档权限"

    override fun isEnabled() = config.enablePermTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val token = args["token"] as? String ?: return@withContext ToolResult.error("Missing token")
            val type = args["type"] as? String ?: "doc"
            val memberType = args["member_type"] as? String ?: "user"
            val memberId = args["member_id"] as? String ?: return@withContext ToolResult.error("Missing member_id")

            val path = "/open-apis/drive/v1/permissions/$token/members/$memberId?type=$type&member_type=$memberType"
            val result = client.delete(path)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            Log.d("PermRevokeTool", "Permission revoked: $token from $memberId")
            ToolResult.success(mapOf(
                "token" to token,
                "member_id" to memberId
            ))

        } catch (e: Exception) {
            Log.e("PermRevokeTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "token" to PropertySchema("string", "文档token"),
                    "type" to PropertySchema("string", "文档类型（doc/sheet/bitable等，默认doc）"),
                    "member_type" to PropertySchema("string", "成员类型（user/chat等，默认user）"),
                    "member_id" to PropertySchema("string", "成员ID")
                ),
                required = listOf("token", "member_id")
            )
        )
    )
}

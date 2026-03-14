package com.xiaomo.feishu.tools.wiki

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
 * 飞书知识库工具集
 * 对齐 OpenClaw src/wiki-tools
 */
class FeishuWikiTools(config: FeishuConfig, client: FeishuClient) {
    private val createTool = WikiCreateTool(config, client)
    private val readTool = WikiReadTool(config, client)
    private val updateTool = WikiUpdateTool(config, client)
    private val listTool = WikiListTool(config, client)

    fun getAllTools(): List<FeishuToolBase> {
        return listOf(createTool, readTool, updateTool, listTool)
    }

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

/**
 * 创建知识库工具
 */
class WikiCreateTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_wiki_create"
    override val description = "创建飞书知识库"

    override fun isEnabled() = config.enableWikiTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val name = args["name"] as? String ?: return@withContext ToolResult.error("Missing name")
            val description = args["description"] as? String ?: ""

            val body = mapOf(
                "name" to name,
                "description" to description
            )

            val result = client.post("/open-apis/wiki/v2/spaces", body)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val spaceId = data?.getAsJsonObject("space")?.get("space_id")?.asString
                ?: return@withContext ToolResult.error("Missing space_id")

            Log.d("WikiCreateTool", "Wiki created: $spaceId")
            ToolResult.success(mapOf("space_id" to spaceId, "name" to name))

        } catch (e: Exception) {
            Log.e("WikiCreateTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "name" to PropertySchema("string", "知识库名称"),
                    "description" to PropertySchema("string", "知识库描述（可选）")
                ),
                required = listOf("name")
            )
        )
    )
}

/**
 * 读取知识库工具
 */
class WikiReadTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_wiki_read"
    override val description = "读取飞书知识库信息"

    override fun isEnabled() = config.enableWikiTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val spaceId = args["space_id"] as? String ?: return@withContext ToolResult.error("Missing space_id")

            val result = client.get("/open-apis/wiki/v2/spaces/$spaceId")

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val space = data?.getAsJsonObject("space")

            val name = space?.get("name")?.asString ?: ""
            val description = space?.get("description")?.asString ?: ""

            Log.d("WikiReadTool", "Wiki read: $spaceId")
            ToolResult.success(mapOf(
                "space_id" to spaceId,
                "name" to name,
                "description" to description
            ))

        } catch (e: Exception) {
            Log.e("WikiReadTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "space_id" to PropertySchema("string", "知识库ID")
                ),
                required = listOf("space_id")
            )
        )
    )
}

/**
 * 更新知识库工具
 */
class WikiUpdateTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_wiki_update"
    override val description = "更新飞书知识库信息"

    override fun isEnabled() = config.enableWikiTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val spaceId = args["space_id"] as? String ?: return@withContext ToolResult.error("Missing space_id")
            val name = args["name"] as? String
            val description = args["description"] as? String

            val body = mutableMapOf<String, Any>()
            if (name != null) body["name"] = name
            if (description != null) body["description"] = description

            if (body.isEmpty()) {
                return@withContext ToolResult.error("No update fields provided")
            }

            val result = client.patch("/open-apis/wiki/v2/spaces/$spaceId", body)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            Log.d("WikiUpdateTool", "Wiki updated: $spaceId")
            ToolResult.success(mapOf("space_id" to spaceId))

        } catch (e: Exception) {
            Log.e("WikiUpdateTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "space_id" to PropertySchema("string", "知识库ID"),
                    "name" to PropertySchema("string", "新名称（可选）"),
                    "description" to PropertySchema("string", "新描述（可选）")
                ),
                required = listOf("space_id")
            )
        )
    )
}

/**
 * 列出知识库节点工具
 */
class WikiListTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_wiki_list"
    override val description = "列出飞书知识库节点"

    override fun isEnabled() = config.enableWikiTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val spaceId = args["space_id"] as? String ?: return@withContext ToolResult.error("Missing space_id")
            val parentNodeToken = args["parent_node_token"] as? String

            val params = mutableListOf("space_id=$spaceId")
            if (parentNodeToken != null) {
                params.add("parent_node_token=$parentNodeToken")
            }

            val path = "/open-apis/wiki/v2/spaces/$spaceId/nodes?" + params.joinToString("&")
            val result = client.get(path)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val items = data?.getAsJsonArray("items") ?: return@withContext ToolResult.success(mapOf("nodes" to emptyList<Map<String, Any>>()))

            val nodes = items.map { item ->
                val obj = item.asJsonObject
                mapOf(
                    "node_token" to (obj.get("node_token")?.asString ?: ""),
                    "title" to (obj.get("title")?.asString ?: ""),
                    "obj_type" to (obj.get("obj_type")?.asString ?: ""),
                    "has_child" to (obj.get("has_child")?.asBoolean ?: false)
                )
            }

            Log.d("WikiListTool", "Wiki nodes listed: ${nodes.size}")
            ToolResult.success(mapOf("space_id" to spaceId, "nodes" to nodes))

        } catch (e: Exception) {
            Log.e("WikiListTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "space_id" to PropertySchema("string", "知识库ID"),
                    "parent_node_token" to PropertySchema("string", "父节点token（可选，不传则列出根节点）")
                ),
                required = listOf("space_id")
            )
        )
    )
}

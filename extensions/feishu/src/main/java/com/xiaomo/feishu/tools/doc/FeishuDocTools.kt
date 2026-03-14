package com.xiaomo.feishu.tools.doc

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel tool definitions.
 */


import android.util.Log
import com.google.gson.JsonObject
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 飞书文档工具集
 * 对齐 OpenClaw src/doc-tools
 */
class FeishuDocTools(config: FeishuConfig, client: FeishuClient) {
    private val createTool = DocCreateTool(config, client)
    private val readTool = DocReadTool(config, client)
    private val updateTool = DocUpdateTool(config, client)
    private val deleteTool = DocDeleteTool(config, client)

    /**
     * 获取所有工具
     */
    fun getAllTools(): List<FeishuToolBase> {
        return listOf(createTool, readTool, updateTool, deleteTool)
    }

    /**
     * 获取工具定义（用于 Agent）
     */
    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

/**
 * 创建文档工具
 */
class DocCreateTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_doc_create"
    override val description = "创建飞书文档"

    override fun isEnabled() = config.enableDocTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val title = args["title"] as? String ?: return@withContext ToolResult.error("Missing title")
            val content = args["content"] as? String ?: ""
            val folderId = args["folder_id"] as? String

            val body = mutableMapOf<String, Any>(
                "title" to title,
                "type" to "doc"
            )
            if (folderId != null) {
                body["folder_token"] = folderId
            }

            val result = client.post("/open-apis/docx/v1/documents", body)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val docId = data?.get("document")?.asJsonObject?.get("document_id")?.asString
                ?: return@withContext ToolResult.error("Missing document_id")

            // 如果有内容，写入文档
            if (content.isNotEmpty()) {
                updateDocContent(docId, content)
            }

            Log.d("DocCreateTool", "Doc created: $docId")
            ToolResult.success(mapOf("document_id" to docId, "title" to title))

        } catch (e: Exception) {
            Log.e("DocCreateTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "title" to PropertySchema("string", "文档标题"),
                    "content" to PropertySchema("string", "文档内容（可选）"),
                    "folder_id" to PropertySchema("string", "文件夹ID（可选）")
                ),
                required = listOf("title")
            )
        )
    )

    private suspend fun updateDocContent(docId: String, content: String) {
        val body = mapOf(
            "requests" to listOf(
                mapOf(
                    "insert_text_elements" to mapOf(
                        "location" to mapOf("zone_id" to ""),
                        "elements" to listOf(
                            mapOf("text_run" to mapOf("content" to content))
                        )
                    )
                )
            )
        )
        client.post("/open-apis/docx/v1/documents/$docId/batch_update", body)
    }
}

/**
 * 读取文档工具
 */
class DocReadTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_doc_read"
    override val description = "读取飞书文档内容"

    override fun isEnabled() = config.enableDocTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val docId = args["document_id"] as? String ?: return@withContext ToolResult.error("Missing document_id")

            val result = client.get("/open-apis/docx/v1/documents/$docId/raw_content")

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val content = data?.get("content")?.asString ?: ""

            Log.d("DocReadTool", "Doc read: $docId")
            ToolResult.success(mapOf("document_id" to docId, "content" to content))

        } catch (e: Exception) {
            Log.e("DocReadTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "document_id" to PropertySchema("string", "文档ID")
                ),
                required = listOf("document_id")
            )
        )
    )
}

/**
 * 更新文档工具
 */
class DocUpdateTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_doc_update"
    override val description = "更新飞书文档内容"

    override fun isEnabled() = config.enableDocTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val docId = args["document_id"] as? String ?: return@withContext ToolResult.error("Missing document_id")
            val content = args["content"] as? String ?: return@withContext ToolResult.error("Missing content")

            val body = mapOf(
                "requests" to listOf(
                    mapOf(
                        "insert_text_elements" to mapOf(
                            "location" to mapOf("zone_id" to ""),
                            "elements" to listOf(
                                mapOf("text_run" to mapOf("content" to content))
                            )
                        )
                    )
                )
            )

            val result = client.post("/open-apis/docx/v1/documents/$docId/batch_update", body)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            Log.d("DocUpdateTool", "Doc updated: $docId")
            ToolResult.success(mapOf("document_id" to docId))

        } catch (e: Exception) {
            Log.e("DocUpdateTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "document_id" to PropertySchema("string", "文档ID"),
                    "content" to PropertySchema("string", "要添加的内容")
                ),
                required = listOf("document_id", "content")
            )
        )
    )
}

/**
 * 删除文档工具
 */
class DocDeleteTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_doc_delete"
    override val description = "删除飞书文档"

    override fun isEnabled() = config.enableDocTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val docId = args["document_id"] as? String ?: return@withContext ToolResult.error("Missing document_id")

            val result = client.delete("/open-apis/docx/v1/documents/$docId")

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            Log.d("DocDeleteTool", "Doc deleted: $docId")
            ToolResult.success(mapOf("document_id" to docId))

        } catch (e: Exception) {
            Log.e("DocDeleteTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "document_id" to PropertySchema("string", "文档ID")
                ),
                required = listOf("document_id")
            )
        )
    )
}

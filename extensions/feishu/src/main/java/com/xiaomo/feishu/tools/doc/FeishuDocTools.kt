package com.xiaomo.feishu.tools.doc

/**
 * Feishu document tool set.
 * Aligned with ByteDance official @larksuite/openclaw-lark plugin:
 * - feishu_fetch_doc  (MCP: fetch-doc)   — read document with pagination
 * - feishu_create_doc (MCP: create-doc)   — create document from markdown
 * - feishu_update_doc (MCP: update-doc)   — update with 7 modes + selection
 * - feishu_doc_media  (OAPI)              — insert/download media
 * - feishu_doc_comments (OAPI)            — list/create/patch comments
 */

import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*

class FeishuDocTools(config: FeishuConfig, client: FeishuClient) {
    private val fetchDocTool = FeishuFetchDocTool(config, client)
    private val createDocTool = FeishuCreateDocTool(config, client)
    private val updateDocTool = FeishuUpdateDocTool(config, client)
    private val docMediaTool = FeishuDocMediaTool(config, client)
    private val docCommentsTool = FeishuDocCommentsTool(config, client)

    fun getAllTools(): List<FeishuToolBase> {
        return listOf(fetchDocTool, createDocTool, updateDocTool, docMediaTool, docCommentsTool)
    }

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

            // 如果有内容，写入文档（对齐 OpenClaw writeDoc 逻辑）
            var contentWriteError: String? = null
            if (content.isNotEmpty()) {
                val writeResult = DocUpdateHelper(client).updateDocContent(docId, content)
                if (writeResult.isFailure) {
                    contentWriteError = writeResult.exceptionOrNull()?.message
                    Log.w("DocCreateTool", "Content write failed for $docId: $contentWriteError")
                }
            }

            Log.d("DocCreateTool", "Doc created: $docId" +
                if (contentWriteError != null) " (content write failed: $contentWriteError)" else "")

            val metadata = mutableMapOf<String, Any?>(
                "document_id" to docId,
                "title" to title
            )
            if (contentWriteError != null) {
                metadata["content_write_error"] = contentWriteError
            }
            ToolResult.success(metadata)

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
 * 文档内容写入工具类 - 对齐 OpenClaw docx.ts 的 insertBlocksWithDescendant。
 * DocCreateTool 和 DocUpdateTool 共用。
 */
class DocUpdateHelper(private val client: FeishuClient) {
    companion object {
        private const val TAG = "DocUpdateHelper"
    }

    /**
     * 将文本内容写入文档 body。
     * 1. 获取文档 block 列表，找到 body 块 (block_type=1, parent_id=docId)
     * 2. 通过 documentBlockChildren.create API 在 body 块下插入文本子块
     */
    suspend fun updateDocContent(docId: String, content: String): Result<Unit> {
        // Step 1: 获取文档的 block 列表，找到 body 块
        val blocksResult = client.get("/open-apis/docx/v1/documents/$docId/blocks?page_size=500")
        if (blocksResult.isFailure) {
            val err = blocksResult.exceptionOrNull()?.message ?: "Failed to list blocks"
            Log.w(TAG, "List blocks failed: $err, trying batch_update fallback")
            return insertViaBatchUpdate(docId, content)
        }

        val blocks = blocksResult.getOrNull()
            ?.getAsJsonObject("data")
            ?.getAsJsonArray("items")

        // 找 body 块：parent_id == docId 且 block_type == 1
        var bodyBlockId: String? = null
        blocks?.forEach { block ->
            val b = block.asJsonObject
            val parentId = b.get("parent_id")?.asString
            val blockType = b.get("block_type")?.asInt
            val blockId = b.get("block_id")?.asString
            if (parentId == docId && blockType == 1) {
                bodyBlockId = blockId
            }
        }

        if (bodyBlockId == null) {
            Log.w(TAG, "Body block not found, using docId as parent")
            bodyBlockId = docId
        }

        // Step 2: 使用 documentBlockChildren.create API 插入文本块
        val createBody = mapOf(
            "children" to listOf(
                mapOf(
                    "block_type" to 2,
                    "text" to mapOf(
                        "elements" to listOf(
                            mapOf(
                                "text_run" to mapOf(
                                    "content" to content,
                                    "text_element_style" to emptyMap<String, Any>()
                                )
                            )
                        ),
                        "style" to emptyMap<String, Any>()
                    )
                )
            ),
            "index" to 0
        )

        val createResult = client.post(
            "/open-apis/docx/v1/documents/$docId/blocks/$bodyBlockId/children",
            createBody
        )

        if (createResult.isFailure) {
            val err = createResult.exceptionOrNull()?.message ?: "block children create failed"
            Log.w(TAG, "block children create failed: $err, trying batch_update fallback")
            return insertViaBatchUpdate(docId, content)
        }

        Log.d(TAG, "Content written to doc $docId via block children API")
        return Result.success(Unit)
    }

    /**
     * Fallback: 使用 batch_update insert 请求插入文本。
     * 对齐 OpenClaw docx.ts 的 batchUpdateInsertChildren 逻辑。
     */
    private suspend fun insertViaBatchUpdate(docId: String, content: String): Result<Unit> {
        val body = mapOf(
            "requests" to listOf(
                mapOf(
                    "insert" to mapOf(
                        "children" to listOf(
                            mapOf(
                                "block_type" to 2,
                                "text" to mapOf(
                                    "elements" to listOf(
                                        mapOf(
                                            "text_run" to mapOf(
                                                "content" to content,
                                                "text_element_style" to emptyMap<String, Any>()
                                            )
                                        )
                                    ),
                                    "style" to emptyMap<String, Any>()
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = client.post("/open-apis/docx/v1/documents/$docId/batch_update", body)
        if (result.isFailure) {
            val err = result.exceptionOrNull()?.message ?: "batch_update failed"
            Log.e(TAG, "batch_update fallback also failed: $err")
            return Result.failure(Exception(err))
        }

        Log.d(TAG, "Content written to doc $docId via batch_update fallback")
        return Result.success(Unit)
    }
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

            // 复用 DocCreateTool 的 updateDocContent 逻辑
            val helper = DocUpdateHelper(client)
            val result = helper.updateDocContent(docId, content)

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

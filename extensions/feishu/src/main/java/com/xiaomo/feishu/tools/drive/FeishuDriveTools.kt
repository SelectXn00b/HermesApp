package com.xiaomo.feishu.tools.drive

import android.util.Base64
import android.util.Log
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 飞书云空间工具集
 * 对齐 @larksuite/openclaw-lark drive-tools
 */
class FeishuDriveTools(config: FeishuConfig, client: FeishuClient) {
    private val fileTool = FeishuDriveFileTool(config, client)

    fun getAllTools(): List<FeishuToolBase> = listOf(fileTool)

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

// ---------------------------------------------------------------------------
// FeishuDriveFileTool
// ---------------------------------------------------------------------------

class FeishuDriveFileTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    companion object {
        private const val TAG = "FeishuDriveFileTool"
    }

    override val name = "feishu_drive_file"

    // @aligned openclaw-lark v2026.3.30
    override val description = "【以用户身份】飞书云空间文件管理工具。当用户要求查看云空间(云盘)中的文件列表、获取文件信息、" +
            "复制/移动/删除文件、上传/下载文件时使用。消息中的文件读写**禁止**使用该工具!" +
            "\n\nActions:" +
            "\n- list（列出文件）：列出文件夹下的文件。不提供 folder_token 时获取根目录清单" +
            "\n- get_meta（批量获取元数据）：批量查询文档元信息，使用 request_docs 数组参数，格式：[{doc_token: '...', doc_type: 'sheet'}]" +
            "\n- copy（复制文件）：复制文件到指定位置" +
            "\n- move（移动文件）：移动文件到指定文件夹" +
            "\n- delete（删除文件）：删除文件" +
            "\n- upload（上传文件）：上传本地文件到云空间。提供 file_path（本地文件路径）或 file_content_base64（Base64 编码）" +
            "\n- download（下载文件）：下载文件到本地。提供 output_path（本地保存路径）则保存到本地，否则返回 Base64 编码" +
            "\n\n【重要】copy/move/delete 操作需要 file_token 和 type 参数。get_meta 使用 request_docs 数组参数。" +
            "\n【重要】upload 优先使用 file_path（自动读取文件、提取文件名和大小），也支持 file_content_base64（需手动提供 file_name 和 size）。" +
            "\n【重要】download 提供 output_path 时保存到本地（可以是文件路径或文件夹路径+file_name），不提供则返回 Base64。"

    override fun isEnabled() = config.enableDriveTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            when (action) {
                "list" -> executeList(args)
                "get_meta" -> executeGetMeta(args)
                "copy" -> executeCopy(args)
                "move" -> executeMove(args)
                "delete" -> executeDelete(args)
                "upload" -> executeUpload(args)
                "download" -> executeDownload(args)
                else -> ToolResult.error("Unknown action: $action. Supported: list, get_meta, copy, move, delete, upload, download")
            }
        } catch (e: Exception) {
            Log.e(TAG, "execute failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun executeList(args: Map<String, Any?>): ToolResult {
        val params = mutableListOf<String>()
        (args["folder_token"] as? String)?.let { params.add("folder_token=$it") }
        (args["page_size"] as? Number)?.let { params.add("page_size=${it.toInt()}") }
        (args["page_token"] as? String)?.let { params.add("page_token=$it") }
        (args["order_by"] as? String)?.let { params.add("order_by=$it") }
        (args["direction"] as? String)?.let { params.add("direction=$it") }

        val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val result = client.get("/open-apis/drive/v1/files$query")

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to list drive files")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        Log.d(TAG, "Drive files listed")
        return ToolResult.success(data)
    }

    // @aligned openclaw-lark v2026.3.30
    @Suppress("UNCHECKED_CAST")
    private suspend fun executeGetMeta(args: Map<String, Any?>): ToolResult {
        val requestDocs = args["request_docs"] as? List<Map<String, Any?>>
            ?: return ToolResult.error("Missing required parameter: request_docs (array of {doc_token, doc_type})")

        val body = mapOf("request_docs" to requestDocs)
        val result = client.post("/open-apis/drive/v1/metas/batch_query", body)

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to get file meta")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        Log.d(TAG, "Drive file meta retrieved")
        return ToolResult.success(data)
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun executeCopy(args: Map<String, Any?>): ToolResult {
        val fileToken = args["file_token"] as? String
            ?: return ToolResult.error("Missing required parameter: file_token")
        val name = args["name"] as? String
            ?: return ToolResult.error("Missing required parameter: name")
        val type = args["type"] as? String
            ?: return ToolResult.error("Missing required parameter: type")

        val body = mutableMapOf<String, Any>(
            "name" to name,
            "type" to type
        )
        (args["folder_token"] as? String)?.let { body["folder_token"] = it }

        val result = client.post("/open-apis/drive/v1/files/$fileToken/copy", body)

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to copy file")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        Log.d(TAG, "Drive file copied: $fileToken")
        return ToolResult.success(data)
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun executeMove(args: Map<String, Any?>): ToolResult {
        val fileToken = args["file_token"] as? String
            ?: return ToolResult.error("Missing required parameter: file_token")
        val type = args["type"] as? String
            ?: return ToolResult.error("Missing required parameter: type")
        val folderToken = args["folder_token"] as? String
            ?: return ToolResult.error("Missing required parameter: folder_token")

        val body = mapOf(
            "type" to type,
            "folder_token" to folderToken
        )

        val result = client.post("/open-apis/drive/v1/files/$fileToken/move", body)

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to move file")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        Log.d(TAG, "Drive file moved: $fileToken")
        return ToolResult.success(data)
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun executeDelete(args: Map<String, Any?>): ToolResult {
        val fileToken = args["file_token"] as? String
            ?: return ToolResult.error("Missing required parameter: file_token")
        val type = args["type"] as? String
            ?: return ToolResult.error("Missing required parameter: type")

        val result = client.delete("/open-apis/drive/v1/files/$fileToken?type=$type")

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to delete file")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        Log.d(TAG, "Drive file deleted: $fileToken")
        return ToolResult.success(data)
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun executeUpload(args: Map<String, Any?>): ToolResult {
        val filePath = args["file_path"] as? String
        val fileContentBase64 = args["file_content_base64"] as? String
        val parentNode = args["parent_node"] as? String ?: ""

        val data: ByteArray
        val resolvedFileName: String

        if (filePath != null) {
            // Priority: use file_path
            val file = File(filePath)
            if (!file.exists()) {
                return ToolResult.error("File not found: $filePath")
            }
            data = file.readBytes()
            resolvedFileName = (args["file_name"] as? String) ?: file.name
        } else if (fileContentBase64 != null) {
            // Fallback: use base64 content
            val fileName = args["file_name"] as? String
                ?: return ToolResult.error("file_name is required when using file_content_base64")
            data = Base64.decode(fileContentBase64, Base64.DEFAULT)
            resolvedFileName = fileName
        } else {
            return ToolResult.error("Either file_path or file_content_base64 is required")
        }

        val result = client.uploadMedia(
            fileName = resolvedFileName,
            parentType = "explorer",
            parentNode = parentNode,
            data = data
        )

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to upload file")
        }

        Log.d(TAG, "Drive file uploaded: $resolvedFileName")
        return ToolResult.success(result.getOrNull())
    }

    // @aligned openclaw-lark v2026.3.30
    private suspend fun executeDownload(args: Map<String, Any?>): ToolResult {
        val fileToken = args["file_token"] as? String
            ?: return ToolResult.error("Missing required parameter: file_token")
        val outputPath = args["output_path"] as? String

        val result = client.downloadRaw("/open-apis/drive/v1/files/$fileToken/download")

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to download file")
        }

        val bytes = result.getOrNull()
            ?: return ToolResult.error("Empty download response")

        if (outputPath != null) {
            // Save to local file
            val file = File(outputPath)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)

            Log.d(TAG, "Drive file downloaded: $fileToken -> $outputPath (${bytes.size} bytes)")
            return ToolResult.success(mapOf(
                "saved_path" to outputPath,
                "size" to bytes.size
            ))
        } else {
            // Return base64 encoded content
            val base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Log.d(TAG, "Drive file downloaded as base64: $fileToken (${bytes.size} bytes)")
            return ToolResult.success(mapOf(
                "file_content_base64" to base64Content,
                "size" to bytes.size
            ))
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
                        type = "string",
                        description = "操作类型",
                        enum = listOf("list", "get_meta", "copy", "move", "delete", "upload", "download")
                    ),
                    "file_token" to PropertySchema(
                        type = "string",
                        description = "文件 token（copy/move/delete/download 操作必填）"
                    ),
                    "folder_token" to PropertySchema(
                        type = "string",
                        description = "文件夹 token（list 操作可选，copy 操作可选指定目标文件夹，move 操作必填）"
                    ),
                    "name" to PropertySchema(
                        type = "string",
                        description = "目标文件名（copy 操作必填）"
                    ),
                    "type" to PropertySchema(
                        type = "string",
                        description = "文档类型（copy/move/delete 操作必填）",
                        enum = listOf("doc", "sheet", "file", "bitable", "docx", "folder", "mindnote", "slides")
                    ),
                    "request_docs" to PropertySchema(
                        type = "array",
                        description = "文档元信息查询列表（get_meta 操作必填），每项包含 doc_token 和 doc_type",
                        items = PropertySchema(
                            type = "object",
                            description = "文档查询项",
                            properties = mapOf(
                                "doc_token" to PropertySchema("string", "文档 token"),
                                "doc_type" to PropertySchema("string", "文档类型")
                            )
                        )
                    ),
                    "file_path" to PropertySchema(
                        type = "string",
                        description = "本地文件路径（upload 操作，与 file_content_base64 二选一）"
                    ),
                    "file_content_base64" to PropertySchema(
                        type = "string",
                        description = "文件内容的 Base64 编码（upload 操作，与 file_path 二选一）"
                    ),
                    "file_name" to PropertySchema(
                        type = "string",
                        description = "文件名（upload 操作可选，使用 file_content_base64 时必填）"
                    ),
                    "parent_node" to PropertySchema(
                        type = "string",
                        description = "父节点 token（upload 操作可选，默认根目录）"
                    ),
                    "output_path" to PropertySchema(
                        type = "string",
                        description = "本地保存的完整文件路径（download 操作可选）。不提供则返回 Base64 编码"
                    ),
                    "page_size" to PropertySchema(
                        type = "number",
                        description = "分页大小（list 操作可选）"
                    ),
                    "page_token" to PropertySchema(
                        type = "string",
                        description = "分页标记（list 操作可选）"
                    ),
                    "order_by" to PropertySchema(
                        type = "string",
                        description = "排序字段（list 操作可选）",
                        enum = listOf("EditedTime", "CreatedTime")
                    ),
                    "direction" to PropertySchema(
                        type = "string",
                        description = "排序方向（list 操作可选）",
                        enum = listOf("ASC", "DESC")
                    )
                ),
                required = listOf("action")
            )
        )
    )
}

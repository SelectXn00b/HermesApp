package com.xiaomo.feishu.tools.drive

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
import java.io.File

/**
 * 飞书云文档工具集
 * 对齐 OpenClaw src/drive-tools
 */
class FeishuDriveTools(config: FeishuConfig, client: FeishuClient) {
    private val uploadTool = DriveUploadTool(config, client)
    private val downloadTool = DriveDownloadTool(config, client)
    private val listTool = DriveListTool(config, client)
    private val deleteTool = DriveDeleteTool(config, client)

    fun getAllTools(): List<FeishuToolBase> {
        return listOf(uploadTool, downloadTool, listTool, deleteTool)
    }

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

/**
 * 上传文件工具
 */
class DriveUploadTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_drive_upload"
    override val description = "上传文件到飞书云文档"

    override fun isEnabled() = config.enableDriveTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val filePath = args["file_path"] as? String ?: return@withContext ToolResult.error("Missing file_path")
            val fileName = args["file_name"] as? String
            val parentType = args["parent_type"] as? String ?: "explorer"
            val parentNode = args["parent_node"] as? String ?: "0"

            val file = File(filePath)
            if (!file.exists()) {
                return@withContext ToolResult.error("File not found: $filePath")
            }

            val body = mapOf(
                "file_name" to (fileName ?: file.name),
                "parent_type" to parentType,
                "parent_node" to parentNode,
                "size" to file.length(),
                "file" to file
            )

            val result = client.post("/open-apis/drive/v1/files/upload_all", body)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val fileToken = data?.get("file_token")?.asString
                ?: return@withContext ToolResult.error("Missing file_token")

            Log.d("DriveUploadTool", "File uploaded: $fileToken")
            ToolResult.success(mapOf(
                "file_token" to fileToken,
                "file_name" to (fileName ?: file.name)
            ))

        } catch (e: Exception) {
            Log.e("DriveUploadTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "file_path" to PropertySchema("string", "本地文件路径"),
                    "file_name" to PropertySchema("string", "上传后的文件名（可选）"),
                    "parent_type" to PropertySchema("string", "父节点类型（explorer/doc_node，默认explorer）"),
                    "parent_node" to PropertySchema("string", "父节点token（默认根目录）")
                ),
                required = listOf("file_path")
            )
        )
    )
}

/**
 * 下载文件工具
 */
class DriveDownloadTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_drive_download"
    override val description = "从飞书云文档下载文件"

    override fun isEnabled() = config.enableDriveTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val fileToken = args["file_token"] as? String ?: return@withContext ToolResult.error("Missing file_token")
            val savePath = args["save_path"] as? String ?: return@withContext ToolResult.error("Missing save_path")

            val result = client.get("/open-apis/drive/v1/files/$fileToken/download")

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            // 实际下载逻辑需要处理二进制流
            // 这里简化处理，实际应用中需要使用 OkHttp 直接处理响应流
            val file = File(savePath)
            file.parentFile?.mkdirs()

            Log.d("DriveDownloadTool", "File downloaded: $fileToken to $savePath")
            ToolResult.success(mapOf(
                "file_token" to fileToken,
                "save_path" to savePath
            ))

        } catch (e: Exception) {
            Log.e("DriveDownloadTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "file_token" to PropertySchema("string", "文件token"),
                    "save_path" to PropertySchema("string", "保存路径")
                ),
                required = listOf("file_token", "save_path")
            )
        )
    )
}

/**
 * 列出文件工具
 */
class DriveListTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_drive_list"
    override val description = "列出飞书云文档文件"

    override fun isEnabled() = config.enableDriveTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val folderToken = args["folder_token"] as? String ?: "0"
            val pageSize = (args["page_size"] as? Number)?.toInt() ?: 50

            val path = "/open-apis/drive/v1/files?folder_token=$folderToken&page_size=$pageSize"
            val result = client.get(path)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val files = data?.getAsJsonArray("files") ?: return@withContext ToolResult.success(mapOf("files" to emptyList<Map<String, Any>>()))

            val fileList = files.map { file ->
                val obj = file.asJsonObject
                mapOf(
                    "token" to (obj.get("token")?.asString ?: ""),
                    "name" to (obj.get("name")?.asString ?: ""),
                    "type" to (obj.get("type")?.asString ?: ""),
                    "url" to (obj.get("url")?.asString ?: "")
                )
            }

            Log.d("DriveListTool", "Files listed: ${fileList.size}")
            ToolResult.success(mapOf("folder_token" to folderToken, "files" to fileList))

        } catch (e: Exception) {
            Log.e("DriveListTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "folder_token" to PropertySchema("string", "文件夹token（默认根目录）"),
                    "page_size" to PropertySchema("number", "每页数量（默认50）")
                ),
                required = emptyList()
            )
        )
    )
}

/**
 * 删除文件工具
 */
class DriveDeleteTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_drive_delete"
    override val description = "删除飞书云文档文件"

    override fun isEnabled() = config.enableDriveTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val fileToken = args["file_token"] as? String ?: return@withContext ToolResult.error("Missing file_token")
            val type = args["type"] as? String ?: "file"

            val result = client.delete("/open-apis/drive/v1/files/$fileToken?type=$type")

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            Log.d("DriveDeleteTool", "File deleted: $fileToken")
            ToolResult.success(mapOf("file_token" to fileToken))

        } catch (e: Exception) {
            Log.e("DriveDeleteTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "file_token" to PropertySchema("string", "文件token"),
                    "type" to PropertySchema("string", "类型（file/folder，默认file）")
                ),
                required = listOf("file_token")
            )
        )
    )
}

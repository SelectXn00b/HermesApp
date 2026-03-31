package com.xiaomo.feishu.tools.doc

// @aligned openclaw-lark v2026.3.30
/**
 * Aligned with ByteDance official @larksuite/openclaw-lark feishu_doc_media OAPI tool.
 * Actions: insert (image/file into doc), download (media/whiteboard).
 */

import android.util.Log
import com.google.gson.JsonObject
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "FeishuDocMediaTool"

// @aligned openclaw-lark v2026.3.30
/** MIME type to extension mapping (matches official MIME_TO_EXT) */
private val MIME_TO_EXT = mapOf(
    "image/png" to ".png",
    "image/jpeg" to ".jpg",
    "image/jpg" to ".jpg",
    "image/gif" to ".gif",
    "image/webp" to ".webp",
    "image/svg+xml" to ".svg",
    "image/bmp" to ".bmp",
    "image/tiff" to ".tiff",
    "video/mp4" to ".mp4",
    "video/mpeg" to ".mpeg",
    "video/quicktime" to ".mov",
    "video/x-msvideo" to ".avi",
    "video/webm" to ".webm",
    "application/pdf" to ".pdf",
    "application/msword" to ".doc",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to ".docx",
    "application/vnd.ms-excel" to ".xls",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to ".xlsx",
    "application/vnd.ms-powerpoint" to ".ppt",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation" to ".pptx",
    "application/zip" to ".zip",
    "application/x-rar-compressed" to ".rar",
    "text/plain" to ".txt",
    "application/json" to ".json",
)

// @aligned openclaw-lark v2026.3.30
/** Alignment enum mapping */
private val ALIGN_MAP = mapOf(
    "left" to 1,
    "center" to 2,
    "right" to 3,
)

class FeishuDocMediaTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_doc_media"
    override val description = "【以用户身份】文档媒体管理工具。" +
        "支持两种操作：" +
        "(1) insert - 在飞书文档末尾插入本地图片或文件（需要文档 ID + 本地文件路径）；" +
        "(2) download - 下载文档素材或画板缩略图到本地（需要资源 token + 输出路径）。" +
        "\n\n【重要】insert 仅支持本地文件路径。URL 图片请使用 create-doc/update-doc 的 <image url=\"...\"/> 语法。"

    override fun isEnabled() = config.enableDocTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            when (action) {
                "insert" -> doInsert(args)
                "download" -> doDownload(args)
                else -> ToolResult.error("Invalid action: $action. Must be 'insert' or 'download'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "feishu_doc_media failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    // @aligned openclaw-lark v2026.3.30
    /**
     * Insert an image or file into a document.
     * 3-step flow: create empty block -> upload media -> patch block with file token.
     * Passes align, width, height, caption for image blocks (matching official).
     */
    private suspend fun doInsert(args: Map<String, Any?>): ToolResult {
        val rawDocId = args["doc_id"] as? String
            ?: return ToolResult.error("Missing doc_id for insert action")
        val docId = extractDocId(rawDocId)
        val filePath = args["file_path"] as? String
            ?: return ToolResult.error("Missing file_path for insert action")
        val type = args["type"] as? String ?: "image"
        val align = args["align"] as? String ?: "center"
        val caption = args["caption"] as? String

        val file = File(filePath)
        if (!file.exists()) return ToolResult.error("File not found: $filePath")

        val maxSize = 20L * 1024 * 1024
        if (file.length() > maxSize) {
            return ToolResult.error("file ${(file.length().toDouble() / 1024 / 1024).let { "%.1f".format(it) }}MB exceeds 20MB limit")
        }

        val fileBytes = file.readBytes()
        val fileName = file.name

        return if (type == "image") {
            // Image block (block_type 27)
            val result = uploadImageBlockAligned(client, docId, fileBytes, fileName, align, caption)
            ToolResult.success(mapOf(
                "success" to true,
                "type" to "image",
                "document_id" to docId,
                "block_id" to (result.get("block_id")?.asString ?: ""),
                "file_token" to (result.get("file_token")?.asString ?: ""),
                "file_name" to fileName
            ))
        } else {
            // File upload: create file block (block_type 23) -> upload -> patch
            val result = uploadFileBlock(client, docId, fileBytes, fileName)
            ToolResult.success(mapOf(
                "success" to true,
                "type" to "file",
                "document_id" to docId,
                "block_id" to (result.get("block_id")?.asString ?: ""),
                "file_token" to (result.get("file_token")?.asString ?: ""),
                "file_name" to fileName
            ))
        }
    }

    // @aligned openclaw-lark v2026.3.30
    /**
     * Upload image block with align, width, height, and caption support.
     * Matches official handleInsert flow: create block -> upload -> batchUpdate with replace_image.
     */
    private suspend fun uploadImageBlockAligned(
        client: FeishuClient,
        docToken: String,
        imageBytes: ByteArray,
        fileName: String,
        align: String,
        caption: String?
    ): JsonObject {
        // Step 1: Create empty image block (block_type 27)
        val createBody = mapOf(
            "children" to listOf(mapOf("block_type" to 27, "image" to emptyMap<String, Any>())),
            "index" to -1
        )

        val createResult = client.post(
            "/open-apis/docx/v1/documents/$docToken/blocks/$docToken/children",
            createBody
        )
        if (createResult.isFailure) {
            throw createResult.exceptionOrNull() ?: Exception("Failed to create image block")
        }

        val children = createResult.getOrNull()?.getAsJsonObject("data")?.getAsJsonArray("children")
        var imageBlockId: String? = null
        if (children != null) {
            for (i in 0 until children.size()) {
                val child = children[i].asJsonObject
                if (child.get("block_type")?.asInt == 27) {
                    imageBlockId = child.get("block_id")?.asString
                    break
                }
            }
        }

        if (imageBlockId == null) {
            throw Exception("Failed to create image block: no block_id returned")
        }

        // Step 2: Upload image
        val fileToken = uploadImageToDocx(client, imageBlockId, imageBytes, fileName, docToken)

        // Step 3: Patch block with file token, align, dimensions, caption
        val alignNum = ALIGN_MAP[align] ?: 2
        val replaceImage = JsonObject().apply {
            addProperty("token", fileToken)
            addProperty("align", alignNum)
        }

        // Try to detect image dimensions via BitmapFactory
        try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                replaceImage.addProperty("width", options.outWidth)
                replaceImage.addProperty("height", options.outHeight)
                Log.d(TAG, "insert: detected image size ${options.outWidth}x${options.outHeight}")
            }
        } catch (e: Exception) {
            Log.d(TAG, "insert: could not detect image dimensions, skipping")
        }

        // Add caption if provided
        if (caption != null) {
            replaceImage.add("caption", JsonObject().apply {
                addProperty("content", caption)
            })
        }

        val patchBody = JsonObject().apply {
            add("replace_image", replaceImage)
        }

        val patchResult = client.patch(
            "/open-apis/docx/v1/documents/$docToken/blocks/$imageBlockId",
            patchBody
        )
        if (patchResult.isFailure) {
            throw patchResult.exceptionOrNull() ?: Exception("Failed to patch image block")
        }

        return JsonObject().apply {
            addProperty("success", true)
            addProperty("block_id", imageBlockId)
            addProperty("file_token", fileToken)
            addProperty("file_name", fileName)
            addProperty("size", imageBytes.size)
        }
    }

    // @aligned openclaw-lark v2026.3.30
    /**
     * Download document media or whiteboard thumbnail.
     * Whiteboard uses /board/v1/whiteboards/{id}/download_as_image (not /drive/v1/).
     * Auto-appends file extension from Content-Type header.
     * Returns field names matching official: resource_type, resource_token, size_bytes, content_type, saved_path.
     */
    private suspend fun doDownload(args: Map<String, Any?>): ToolResult {
        val resourceToken = args["resource_token"] as? String
            ?: return ToolResult.error("Missing resource_token for download action")
        val resourceType = args["resource_type"] as? String
            ?: return ToolResult.error("Missing resource_type for download action")
        val outputPath = args["output_path"] as? String
            ?: return ToolResult.error("Missing output_path for download action")

        // @aligned openclaw-lark v2026.3.30 - correct API paths
        val apiPath = when (resourceType) {
            "media" -> "/open-apis/drive/v1/medias/$resourceToken/download"
            "whiteboard" -> "/open-apis/board/v1/whiteboards/$resourceToken/download_as_image"
            else -> return ToolResult.error("Invalid resource_type: $resourceType")
        }

        val result = client.downloadRawWithHeaders(apiPath)
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Download failed")
        }

        val (bytes, contentType) = result.getOrNull() ?: return ToolResult.error("Empty response")

        // @aligned openclaw-lark v2026.3.30 - auto-append extension from Content-Type
        var finalPath = outputPath
        val currentExt = File(outputPath).extension
        if (currentExt.isEmpty() && contentType.isNotEmpty()) {
            val mimeType = contentType.split(";").first().trim()
            val defaultExt = if (resourceType == "whiteboard") ".png" else null
            val suggestedExt = MIME_TO_EXT[mimeType] ?: defaultExt
            if (suggestedExt != null) {
                finalPath = outputPath + suggestedExt
                Log.d(TAG, "download: auto-detected extension $suggestedExt")
            }
        }

        val outputFile = File(finalPath)
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(bytes)

        // @aligned openclaw-lark v2026.3.30 - return field names matching official
        return ToolResult.success(mapOf(
            "resource_type" to resourceType,
            "resource_token" to resourceToken,
            "size_bytes" to bytes.size,
            "content_type" to contentType,
            "saved_path" to finalPath
        ))
    }

    /**
     * Upload a file block to a document (block_type 23).
     */
    // @aligned openclaw-lark v2026.3.30
    private suspend fun uploadFileBlock(
        client: FeishuClient,
        docToken: String,
        fileBytes: ByteArray,
        fileName: String
    ): JsonObject {
        // Step 1: Create empty file block
        val createBody = mapOf(
            "children" to listOf(mapOf("block_type" to 23, "file" to mapOf("token" to ""))),
            "index" to -1
        )

        val createResult = client.post(
            "/open-apis/docx/v1/documents/$docToken/blocks/$docToken/children",
            createBody
        )
        if (createResult.isFailure) {
            throw createResult.exceptionOrNull() ?: Exception("Failed to create file block")
        }

        // File Block returns View Block (block_type: 33) as container,
        // real File Block ID is in children[0].children[0]
        val children = createResult.getOrNull()?.getAsJsonObject("data")?.getAsJsonArray("children")
        var fileBlockId: String? = null
        if (children != null && children.size() > 0) {
            val firstChild = children[0].asJsonObject
            val innerChildren = firstChild.getAsJsonArray("children")
            if (innerChildren != null && innerChildren.size() > 0) {
                fileBlockId = innerChildren[0].asString
            }
            // Fallback: direct block_id if no nested children
            if (fileBlockId == null) {
                fileBlockId = firstChild.get("block_id")?.asString
            }
        }
        if (fileBlockId == null) throw Exception("Failed to create file block: no block_id")

        // Step 2: Upload file
        val uploadResult = client.uploadMedia(
            fileName = fileName,
            parentType = "docx_file",
            parentNode = fileBlockId,
            data = fileBytes
        )
        if (uploadResult.isFailure) {
            throw uploadResult.exceptionOrNull() ?: Exception("File upload failed")
        }

        val fileToken = uploadResult.getOrNull()?.get("file_token")?.asString
            ?: throw Exception("No file_token returned")

        // Step 3: Patch block with file token
        val patchBody = JsonObject().apply {
            add("replace_file", JsonObject().apply {
                addProperty("token", fileToken)
            })
        }
        client.patch("/open-apis/docx/v1/documents/$docToken/blocks/$fileBlockId", patchBody)

        return JsonObject().apply {
            addProperty("success", true)
            addProperty("block_id", fileBlockId)
            addProperty("file_token", fileToken)
            addProperty("file_name", fileName)
            addProperty("size", fileBytes.size)
        }
    }

    // @aligned openclaw-lark v2026.3.30
    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "action" to PropertySchema("string", "Action: insert or download",
                        enum = listOf("insert", "download")),
                    "doc_id" to PropertySchema("string", "文档 ID 或文档 URL（insert 时必填）。支持从 URL 自动提取 document_id"),
                    "file_path" to PropertySchema("string", "本地文件的绝对路径（insert 时必填）。图片支持 jpg/png/gif/webp 等，文件支持任意格式，最大 20MB"),
                    "type" to PropertySchema("string", "媒体类型：\"image\"（图片，默认）或 \"file\"（文件附件）",
                        enum = listOf("image", "file")),
                    "align" to PropertySchema("string", "对齐方式（仅图片生效）：\"center\"（默认居中）、\"left\"（居左）、\"right\"（居右）",
                        enum = listOf("left", "center", "right")),
                    "caption" to PropertySchema("string", "图片描述/标题（可选，仅图片生效）"),
                    "resource_token" to PropertySchema("string", "资源的唯一标识（file_token 用于文档素材，whiteboard_id 用于画板）"),
                    "resource_type" to PropertySchema("string", "资源类型：media（文档素材：图片、视频、文件等）或 whiteboard（画板缩略图）",
                        enum = listOf("media", "whiteboard")),
                    "output_path" to PropertySchema("string", "保存文件的完整本地路径。可以包含扩展名（如 /tmp/image.png），也可以不带扩展名，系统会根据 Content-Type 自动添加")
                ),
                required = listOf("action")
            )
        )
    )
}

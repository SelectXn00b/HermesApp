package com.xiaomo.feishu.tools.doc

// @aligned openclaw-lark v2026.3.30
/**
 * Aligned with ByteDance official @larksuite/openclaw-lark feishu_doc_comments OAPI tool.
 * Actions: list (get comments with replies), create (whole-doc comment), patch (resolve/restore).
 */

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FeishuDocComments"

class FeishuDocCommentsTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_doc_comments"
    override val description = "【以用户身份】管理云文档评论。支持: " +
        "(1) list - 获取评论列表(含完整回复); " +
        "(2) create - 添加全文评论(支持文本、@用户、超链接); " +
        "(3) patch - 解决/恢复评论。" +
        "支持 wiki token。"

    override fun isEnabled() = config.enableDocTools

    // @aligned openclaw-lark v2026.3.30
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            when (action) {
                "list" -> doList(args)
                "create" -> doCreate(args)
                "patch" -> doPatch(args)
                else -> ToolResult.error("Invalid action: $action. Must be 'list', 'create', or 'patch'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "feishu_doc_comments failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    // @aligned openclaw-lark v2026.3.30
    /**
     * List comments with complete replies.
     * Fetches complete reply lists via fileCommentReply.list pagination loop
     * (matching official assembleCommentsWithReplies).
     * Default page_size: 50 (matching official).
     * Returns structured data: { items, has_more, page_token }.
     */
    private suspend fun doList(args: Map<String, Any?>): ToolResult {
        val fileToken = args["file_token"] as? String
            ?: return ToolResult.error("Missing file_token")
        val fileType = args["file_type"] as? String
            ?: return ToolResult.error("Missing file_type")
        val isWhole = args["is_whole"] as? Boolean
        val isSolved = args["is_solved"] as? Boolean
        // @aligned openclaw-lark v2026.3.30 - default page_size 50
        val pageSize = (args["page_size"] as? Number)?.toInt() ?: 50
        val pageToken = args["page_token"] as? String
        val userIdType = args["user_id_type"] as? String ?: "open_id"

        // Resolve wiki token if needed
        val (resolvedToken, resolvedType) = resolveWikiToken(fileToken, fileType)

        var path = "/open-apis/drive/v1/files/$resolvedToken/comments" +
                "?file_type=$resolvedType&user_id_type=$userIdType&page_size=$pageSize"
        if (isWhole != null) path += "&is_whole=$isWhole"
        if (isSolved != null) path += "&is_solved=$isSolved"
        if (pageToken != null) path += "&page_token=$pageToken"

        val result = client.get(path)
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to list comments")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        val items = data?.getAsJsonArray("items") ?: JsonArray()
        val hasMore = data?.get("has_more")?.asBoolean ?: false
        val nextPageToken = data?.get("page_token")?.asString

        Log.d(TAG, "doc_comments.list: found ${items.size()} comments")

        // @aligned openclaw-lark v2026.3.30 - assemble complete replies
        val assembledItems = assembleCommentsWithReplies(
            client, resolvedToken, resolvedType, items, userIdType
        )

        // @aligned openclaw-lark v2026.3.30 - return structured data matching official format
        val resultMap = mutableMapOf<String, Any?>(
            "items" to assembledItems.toString(),
            "has_more" to hasMore
        )
        if (nextPageToken != null) resultMap["page_token"] = nextPageToken

        return ToolResult.success(resultMap)
    }

    // @aligned openclaw-lark v2026.3.30
    /**
     * Assemble comments with complete reply lists.
     * For each comment that has replies, fetches the full reply list
     * via fileCommentReply.list in a pagination loop.
     * Matches official assembleCommentsWithReplies function.
     */
    private suspend fun assembleCommentsWithReplies(
        client: FeishuClient,
        fileToken: String,
        fileType: String,
        comments: JsonArray,
        userIdType: String
    ): JsonArray {
        val result = JsonArray()
        for (i in 0 until comments.size()) {
            val comment = comments[i].asJsonObject.deepCopy()
            val commentId = comment.get("comment_id")?.asString

            // Check if comment has replies that may be incomplete
            val replyList = comment.getAsJsonObject("reply_list")
            val hasReplies = replyList?.getAsJsonArray("replies")?.size()?.let { it > 0 } ?: false

            if (commentId != null && hasReplies) {
                try {
                    val allReplies = JsonArray()
                    var replyPageToken: String? = null
                    var replyHasMore = true

                    while (replyHasMore) {
                        var replyPath = "/open-apis/drive/v1/files/$fileToken/comments/$commentId/replies" +
                            "?file_type=$fileType&page_size=50&user_id_type=$userIdType"
                        if (replyPageToken != null) replyPath += "&page_token=$replyPageToken"

                        val replyResult = client.get(replyPath)
                        if (replyResult.isFailure) break

                        val replyJson = replyResult.getOrNull()
                        val replyCode = replyJson?.get("code")?.asInt ?: -1
                        val replyData = replyJson?.getAsJsonObject("data")

                        if (replyCode == 0 && replyData != null) {
                            val replyItems = replyData.getAsJsonArray("items")
                            if (replyItems != null) {
                                for (j in 0 until replyItems.size()) {
                                    allReplies.add(replyItems[j])
                                }
                            }
                            replyHasMore = replyData.get("has_more")?.asBoolean ?: false
                            replyPageToken = replyData.get("page_token")?.asString
                        } else {
                            break
                        }
                    }

                    // Replace with complete replies
                    comment.add("reply_list", JsonObject().apply {
                        add("replies", allReplies)
                    })
                    Log.d(TAG, "Assembled ${allReplies.size()} replies for comment $commentId")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch replies for comment $commentId: ${e.message}")
                    // Keep original reply data
                }
            }
            result.add(comment)
        }
        return result
    }

    // @aligned openclaw-lark v2026.3.30
    /**
     * Create a whole-document comment.
     * Element type mapping: mention -> person, link -> docs_link (matching official).
     * Does NOT send is_whole: true (omitted, matching official which doesn't set it).
     */
    private suspend fun doCreate(args: Map<String, Any?>): ToolResult {
        val fileToken = args["file_token"] as? String
            ?: return ToolResult.error("Missing file_token")
        val fileType = args["file_type"] as? String
            ?: return ToolResult.error("Missing file_type")
        val userIdType = args["user_id_type"] as? String ?: "open_id"

        @Suppress("UNCHECKED_CAST")
        val elements = args["elements"] as? List<Map<String, Any?>>
            ?: return ToolResult.error("elements 参数必填且不能为空")

        if (elements.isEmpty()) {
            return ToolResult.error("elements 参数必填且不能为空")
        }

        val (resolvedToken, resolvedType) = resolveWikiToken(fileToken, fileType)

        // @aligned openclaw-lark v2026.3.30 - correct element type mapping
        val richTextElements = JsonArray()
        for (element in elements) {
            val type = element["type"] as? String ?: continue
            val jsonElement = JsonObject()

            when (type) {
                "text" -> {
                    jsonElement.addProperty("type", "text_run")
                    jsonElement.add("text_run", JsonObject().apply {
                        addProperty("text", element["text"] as? String ?: "")
                    })
                }
                // @aligned openclaw-lark v2026.3.30 - mention -> person (NOT mention_user)
                "mention" -> {
                    jsonElement.addProperty("type", "person")
                    jsonElement.add("person", JsonObject().apply {
                        addProperty("user_id", element["open_id"] as? String ?: "")
                    })
                }
                // @aligned openclaw-lark v2026.3.30 - link -> docs_link (NOT link)
                "link" -> {
                    jsonElement.addProperty("type", "docs_link")
                    jsonElement.add("docs_link", JsonObject().apply {
                        addProperty("url", element["url"] as? String ?: "")
                    })
                }
            }
            richTextElements.add(jsonElement)
        }

        // @aligned openclaw-lark v2026.3.30 - no is_whole in body
        val body = JsonObject().apply {
            add("reply_list", JsonObject().apply {
                val replies = JsonArray()
                replies.add(JsonObject().apply {
                    add("content", JsonObject().apply {
                        add("elements", richTextElements)
                    })
                })
                add("replies", replies)
            })
        }

        val path = "/open-apis/drive/v1/files/$resolvedToken/comments" +
                "?file_type=$resolvedType&user_id_type=$userIdType"

        val result = client.post(path, body)
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to create comment")
        }

        val responseData = result.getOrNull()?.getAsJsonObject("data")
        val commentId = responseData?.get("comment_id")?.asString

        Log.d(TAG, "doc_comments.create: created comment $commentId")
        return ToolResult.success(responseData?.toString() ?: mapOf("comment_id" to commentId))
    }

    // @aligned openclaw-lark v2026.3.30
    /**
     * Patch: resolve or restore a comment.
     */
    private suspend fun doPatch(args: Map<String, Any?>): ToolResult {
        val fileToken = args["file_token"] as? String
            ?: return ToolResult.error("Missing file_token")
        val fileType = args["file_type"] as? String
            ?: return ToolResult.error("Missing file_type")
        val commentId = args["comment_id"] as? String
            ?: return ToolResult.error("comment_id 参数必填")
        val isSolved = args["is_solved_value"] as? Boolean
            ?: return ToolResult.error("is_solved_value 参数必填")

        val (resolvedToken, resolvedType) = resolveWikiToken(fileToken, fileType)

        val body = JsonObject().apply {
            addProperty("is_solved", isSolved)
        }

        val path = "/open-apis/drive/v1/files/$resolvedToken/comments/$commentId" +
                "?file_type=$resolvedType"

        val result = client.patch(path, body)
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to patch comment")
        }

        Log.d(TAG, "doc_comments.patch: success")
        return ToolResult.success(mapOf("success" to true))
    }

    // @aligned openclaw-lark v2026.3.30
    /**
     * Resolve wiki token to actual obj_token and obj_type.
     */
    private suspend fun resolveWikiToken(fileToken: String, fileType: String): Pair<String, String> {
        if (fileType != "wiki") return fileToken to fileType

        val result = client.get("/open-apis/wiki/v2/spaces/get_node?token=$fileToken")
        if (result.isFailure) return fileToken to fileType

        val node = result.getOrNull()?.getAsJsonObject("data")?.getAsJsonObject("node")
        val objToken = node?.get("obj_token")?.asString ?: fileToken
        val objType = node?.get("obj_type")?.asString ?: "docx"

        return objToken to objType
    }

    // @aligned openclaw-lark v2026.3.30
    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "action" to PropertySchema("string", "Action: list, create, or patch",
                        enum = listOf("list", "create", "patch")),
                    "file_token" to PropertySchema("string", "云文档token或wiki节点token(可从文档URL获取)。如果是wiki token，会自动转换为实际文档的obj_token"),
                    "file_type" to PropertySchema("string", "文档类型。wiki类型会自动解析为实际文档类型(docx/sheet/bitable等)",
                        enum = listOf("doc", "docx", "sheet", "file", "slides", "wiki")),
                    "is_whole" to PropertySchema("boolean", "是否只获取全文评论(action=list时可选)"),
                    "is_solved" to PropertySchema("boolean", "是否只获取已解决的评论(action=list时可选)"),
                    "page_size" to PropertySchema("integer", "分页大小"),
                    "page_token" to PropertySchema("string", "分页标记"),
                    "elements" to PropertySchema("array", "评论内容元素数组(action=create时必填)。支持text(纯文本)、mention(@用户)、link(超链接)三种类型",
                        items = PropertySchema("object", "Comment element")),
                    "comment_id" to PropertySchema("string", "评论ID(action=patch时必填)"),
                    "is_solved_value" to PropertySchema("boolean", "解决状态:true=解决,false=恢复(action=patch时必填)"),
                    "user_id_type" to PropertySchema("string", "User ID type: open_id, union_id, user_id",
                        enum = listOf("open_id", "union_id", "user_id"))
                ),
                required = listOf("action", "file_token", "file_type")
            )
        )
    )
}

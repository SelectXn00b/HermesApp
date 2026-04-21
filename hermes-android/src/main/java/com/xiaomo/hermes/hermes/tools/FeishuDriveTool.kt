/** 1:1 对齐 hermes/tools/feishu_drive_tool.py */
package com.xiaomo.hermes.hermes.tools

import java.util.logging.Logger

/**
 * Feishu Drive Tools -- document comment operations via Feishu/Lark API.
 *
 * Provides tools for listing, replying to, and adding document comments.
 * Uses the same lazy-import + BaseRequest pattern as feishu_comment.py.
 * The lark client is injected per-thread by the comment event handler.
 */

private val logger = Logger.getLogger("FeishuDriveTool")

// Thread-local storage for the lark client injected by feishu_comment handler.
private val _local = ThreadLocal<Any?>()

fun setClient(client: Any?) {
    _local.set(client)
}

fun getClient(): Any? {
    return _local.get()
}

private fun _checkFeishu(): Boolean {
    return try {
        Class.forName("com.lark.oapi.Client")
        true
    } catch (e: ClassNotFoundException) {
        false
    }
}

/**
 * Build and execute a BaseRequest, return Triple(code, msg, dataDict).
 */
private fun _doRequest(
    client: Any,
    method: String,
    uri: String,
    paths: Map<String, String>? = null,
    queries: List<Pair<String, String>>? = null,
    body: Any? = null
): Triple<Int?, String, Map<String, Any?>> {
    // Placeholder implementation — actual Lark SDK calls
    // would be integrated here in the Android environment.
    // This 1:1 matches the Python structure.
    try {
        // The actual implementation would use Lark SDK's BaseRequest
        // builder pattern to construct and execute the request.
        // For now, return error indicating SDK not available.
        return Triple(-1, "Lark SDK not available in Android context", emptyMap())
    } catch (e: Exception) {
        return Triple(-1, e.message ?: "Unknown error", emptyMap())
    }
}

// ---------------------------------------------------------------------------
// feishu_drive_list_comments
// ---------------------------------------------------------------------------

private const val _LIST_COMMENTS_URI = "/open-apis/drive/v1/files/:file_token/comments"

val FEISHU_DRIVE_LIST_COMMENTS_SCHEMA = mapOf(
    "name" to "feishu_drive_list_comments",
    "description" to "List comments on a Feishu document. Use is_whole=true to list whole-document comments only.",
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "file_token" to mapOf("type" to "string", "description" to "The document file token."),
            "file_type" to mapOf("type" to "string", "description" to "File type (default: docx).", "default" to "docx"),
            "is_whole" to mapOf("type" to "boolean", "description" to "If true, only return whole-document comments.", "default" to false),
            "page_size" to mapOf("type" to "integer", "description" to "Number of comments per page (max 100).", "default" to 100),
            "page_token" to mapOf("type" to "string", "description" to "Pagination token for next page.")
        ),
        "required" to listOf("file_token")
    )
)

private fun _handleListComments(args: Map<String, Any>): String {
    val client = getClient() ?: return Registry.toolError("Feishu client not available")

    val fileToken = (args["file_token"] as? String)?.trim() ?: ""
    if (fileToken.isEmpty()) return Registry.toolError("file_token is required")

    val fileType = (args["file_type"] as? String) ?: "docx"
    val isWhole = args["is_whole"] as? Boolean ?: false
    val pageSize = (args["page_size"] as? Number)?.toInt() ?: 100
    val pageToken = (args["page_token"] as? String) ?: ""

    val queries = mutableListOf(
        "file_type" to fileType,
        "user_id_type" to "open_id",
        "page_size" to pageSize.toString()
    )
    if (isWhole) queries.add("is_whole" to "true")
    if (pageToken.isNotEmpty()) queries.add("page_token" to pageToken)

    val (code, msg, data) = _doRequest(
        client, "GET", _LIST_COMMENTS_URI,
        paths = mapOf("file_token" to fileToken),
        queries = queries
    )
    if (code != 0) return Registry.toolError("List comments failed: code=$code msg=$msg")

    return Registry.toolResult(data)
}

// ---------------------------------------------------------------------------
// feishu_drive_list_comment_replies
// ---------------------------------------------------------------------------

private const val _LIST_REPLIES_URI = "/open-apis/drive/v1/files/:file_token/comments/:comment_id/replies"

val FEISHU_DRIVE_LIST_REPLIES_SCHEMA = mapOf(
    "name" to "feishu_drive_list_comment_replies",
    "description" to "List all replies in a comment thread on a Feishu document.",
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "file_token" to mapOf("type" to "string", "description" to "The document file token."),
            "comment_id" to mapOf("type" to "string", "description" to "The comment ID to list replies for."),
            "file_type" to mapOf("type" to "string", "description" to "File type (default: docx).", "default" to "docx"),
            "page_size" to mapOf("type" to "integer", "description" to "Number of replies per page (max 100).", "default" to 100),
            "page_token" to mapOf("type" to "string", "description" to "Pagination token for next page.")
        ),
        "required" to listOf("file_token", "comment_id")
    )
)

private fun _handleListReplies(args: Map<String, Any>): String {
    val client = getClient() ?: return Registry.toolError("Feishu client not available")

    val fileToken = (args["file_token"] as? String)?.trim() ?: ""
    val commentId = (args["comment_id"] as? String)?.trim() ?: ""
    if (fileToken.isEmpty() || commentId.isEmpty()) return Registry.toolError("file_token and comment_id are required")

    val fileType = (args["file_type"] as? String) ?: "docx"
    val pageSize = (args["page_size"] as? Number)?.toInt() ?: 100
    val pageToken = (args["page_token"] as? String) ?: ""

    val queries = mutableListOf(
        "file_type" to fileType,
        "user_id_type" to "open_id",
        "page_size" to pageSize.toString()
    )
    if (pageToken.isNotEmpty()) queries.add("page_token" to pageToken)

    val (code, msg, data) = _doRequest(
        client, "GET", _LIST_REPLIES_URI,
        paths = mapOf("file_token" to fileToken, "comment_id" to commentId),
        queries = queries
    )
    if (code != 0) return Registry.toolError("List replies failed: code=$code msg=$msg")

    return Registry.toolResult(data)
}

// ---------------------------------------------------------------------------
// feishu_drive_reply_comment
// ---------------------------------------------------------------------------

private const val _REPLY_COMMENT_URI = "/open-apis/drive/v1/files/:file_token/comments/:comment_id/replies"

val FEISHU_DRIVE_REPLY_SCHEMA = mapOf(
    "name" to "feishu_drive_reply_comment",
    "description" to "Reply to a local comment thread on a Feishu document. Use this for local (quoted-text) comments. For whole-document comments, use feishu_drive_add_comment instead.",
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "file_token" to mapOf("type" to "string", "description" to "The document file token."),
            "comment_id" to mapOf("type" to "string", "description" to "The comment ID to reply to."),
            "content" to mapOf("type" to "string", "description" to "The reply text content (plain text only, no markdown)."),
            "file_type" to mapOf("type" to "string", "description" to "File type (default: docx).", "default" to "docx")
        ),
        "required" to listOf("file_token", "comment_id", "content")
    )
)

private fun _handleReplyComment(args: Map<String, Any>): String {
    val client = getClient() ?: return Registry.toolError("Feishu client not available")

    val fileToken = (args["file_token"] as? String)?.trim() ?: ""
    val commentId = (args["comment_id"] as? String)?.trim() ?: ""
    val content = (args["content"] as? String)?.trim() ?: ""
    if (fileToken.isEmpty() || commentId.isEmpty() || content.isEmpty()) {
        return Registry.toolError("file_token, comment_id, and content are required")
    }

    val fileType = (args["file_type"] as? String) ?: "docx"

    val body = mapOf(
        "content" to mapOf(
            "elements" to listOf(
                mapOf(
                    "type" to "text_run",
                    "text_run" to mapOf("text" to content)
                )
            )
        )
    )

    val (code, msg, data) = _doRequest(
        client, "POST", _REPLY_COMMENT_URI,
        paths = mapOf("file_token" to fileToken, "comment_id" to commentId),
        queries = listOf("file_type" to fileType),
        body = body
    )
    if (code != 0) return Registry.toolError("Reply comment failed: code=$code msg=$msg")

    return Registry.toolResult(data)
}

// ---------------------------------------------------------------------------
// feishu_drive_add_comment
// ---------------------------------------------------------------------------

private const val _ADD_COMMENT_URI = "/open-apis/drive/v1/files/:file_token/new_comments"

val FEISHU_DRIVE_ADD_COMMENT_SCHEMA = mapOf(
    "name" to "feishu_drive_add_comment",
    "description" to "Add a new whole-document comment on a Feishu document. Use this for whole-document comments or as a fallback when reply_comment fails with code 1069302.",
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "file_token" to mapOf("type" to "string", "description" to "The document file token."),
            "content" to mapOf("type" to "string", "description" to "The comment text content (plain text only, no markdown)."),
            "file_type" to mapOf("type" to "string", "description" to "File type (default: docx).", "default" to "docx")
        ),
        "required" to listOf("file_token", "content")
    )
)

private fun _handleAddComment(args: Map<String, Any>): String {
    val client = getClient() ?: return Registry.toolError("Feishu client not available")

    val fileToken = (args["file_token"] as? String)?.trim() ?: ""
    val content = (args["content"] as? String)?.trim() ?: ""
    if (fileToken.isEmpty() || content.isEmpty()) {
        return Registry.toolError("file_token and content are required")
    }

    val fileType = (args["file_type"] as? String) ?: "docx"

    val body = mapOf(
        "file_type" to fileType,
        "reply_elements" to listOf(
            mapOf("type" to "text", "text" to content)
        )
    )

    val (code, msg, data) = _doRequest(
        client, "POST", _ADD_COMMENT_URI,
        paths = mapOf("file_token" to fileToken),
        body = body
    )
    if (code != 0) return Registry.toolError("Add comment failed: code=$code msg=$msg")

    return Registry.toolResult(data)
}

// ---------------------------------------------------------------------------
// Registration
// ---------------------------------------------------------------------------

fun registerFeishuDriveTools(registry: ToolRegistry) {
    registry.register(ToolEntry(
        name = "feishu_drive_list_comments",
        toolset = "feishu_drive",
        schema = FEISHU_DRIVE_LIST_COMMENTS_SCHEMA,
        handler = ::_handleListComments,
        checkFn = ::_checkFeishu,
        isAsync = false,
        description = "List document comments",
        emoji = "\uD83D\uDCAC"
    ))

    registry.register(ToolEntry(
        name = "feishu_drive_list_comment_replies",
        toolset = "feishu_drive",
        schema = FEISHU_DRIVE_LIST_REPLIES_SCHEMA,
        handler = ::_handleListReplies,
        checkFn = ::_checkFeishu,
        isAsync = false,
        description = "List comment replies",
        emoji = "\uD83D\uDCAC"
    ))

    registry.register(ToolEntry(
        name = "feishu_drive_reply_comment",
        toolset = "feishu_drive",
        schema = FEISHU_DRIVE_REPLY_SCHEMA,
        handler = ::_handleReplyComment,
        checkFn = ::_checkFeishu,
        isAsync = false,
        description = "Reply to a document comment",
        emoji = "\u2709\uFE0F"
    ))

    registry.register(ToolEntry(
        name = "feishu_drive_add_comment",
        toolset = "feishu_drive",
        schema = FEISHU_DRIVE_ADD_COMMENT_SCHEMA,
        handler = ::_handleAddComment,
        checkFn = ::_checkFeishu,
        isAsync = false,
        description = "Add a whole-document comment",
        emoji = "\u2709\uFE0F"
    ))
}

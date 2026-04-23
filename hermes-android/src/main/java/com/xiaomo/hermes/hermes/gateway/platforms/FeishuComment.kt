package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * Ported from gateway/platforms/feishu_comment.py.
 *
 * Android 侧的飞书评论分发：本模块只在有 Feishu OpenAPI 网络通道时可用。
 * 所有需要 HTTP/OAuth/飞书 SDK 的函数在 Android 上保留签名返回安全回退，
 * 供上层路由在没有评论通道时跳过。纯逻辑（URL 正则、时间线选择、
 * 文本切分、prompt 构造）完整实现，方便后续接真实 HTTP 客户端。
 */

import java.util.concurrent.ConcurrentHashMap

private val feishuCommentLogger =
    com.xiaomo.hermes.hermes.getLogger("feishu_comment")

// ── Constants (1:1 with Python) ─────────────────────────────────────────

const val _REACTION_URI: String = "/open-apis/drive/v2/files/:file_token/comments/reaction"
const val _BATCH_QUERY_META_URI: String = "/open-apis/drive/v1/metas/batch_query"
const val _BATCH_QUERY_COMMENT_URI: String =
    "/open-apis/drive/v1/files/:file_token/comments/batch_query"
const val _LIST_COMMENTS_URI: String = "/open-apis/drive/v1/files/:file_token/comments"
const val _LIST_REPLIES_URI: String =
    "/open-apis/drive/v1/files/:file_token/comments/:comment_id/replies"
const val _REPLY_COMMENT_URI: String =
    "/open-apis/drive/v1/files/:file_token/comments/:comment_id/replies"
const val _ADD_COMMENT_URI: String = "/open-apis/drive/v1/files/:file_token/new_comments"

const val _COMMENT_RETRY_LIMIT: Int = 6
const val _COMMENT_RETRY_DELAY_S: Double = 1.0
const val _REPLY_CHUNK_SIZE: Int = 4000

val _FEISHU_DOC_URL_RE: Regex = Regex(
    "(?:feishu\\.cn|larkoffice\\.com|larksuite\\.com|lark\\.suite\\.com)" +
        "/(?<docType>wiki|doc|docx|sheet|sheets|slides|mindnote|bitable|base|file)" +
        "/(?<token>[A-Za-z0-9_-]{10,40})"
)

const val _WIKI_GET_NODE_URI: String = "/open-apis/wiki/v2/spaces/get_node"

const val _PROMPT_TEXT_LIMIT: Int = 220
const val _LOCAL_TIMELINE_LIMIT: Int = 20
const val _WHOLE_TIMELINE_LIMIT: Int = 12

const val _COMMON_INSTRUCTIONS: String =
    "This is a Feishu document comment thread, not an IM chat.\n" +
        "Do NOT call feishu_drive_add_comment or feishu_drive_reply_comment yourself.\n" +
        "Your reply will be posted automatically. Just output the reply text.\n" +
        "Use the thread timeline above as the main context.\n" +
        "If the quoted content is not enough, use feishu_doc_read to read nearby context.\n" +
        "The quoted content is your primary anchor — insert/summarize/explain requests are about it.\n" +
        "Do not guess document content you haven't read.\n" +
        "Reply in the same language as the user's comment unless they request otherwise.\n" +
        "Use plain text only. Do not use Markdown, headings, bullet lists, tables, or code blocks.\n" +
        "Do not show your reasoning process. Do not start with \"I will\", \"Let me\", or \"I'll first\".\n" +
        "Output only the final user-facing reply.\n" +
        "If no reply is needed, output exactly NO_REPLY."

const val _SESSION_MAX_MESSAGES: Int = 50
const val _SESSION_TTL_S: Int = 3600
const val _NO_REPLY_SENTINEL: String = "NO_REPLY"

val _ALLOWED_NOTICE_TYPES: Set<String> = setOf("add_comment", "add_reply")

// Session cache: file_type:file_token → (lastActive, messages)
private val _sessionStore = ConcurrentHashMap<String, Pair<Long, MutableList<Map<String, Any?>>>>()

// ── Request helpers (Android stub — real HTTP lives in the tool layer) ──

fun _buildRequest(
    method: String,
    uri: String,
    paths: Map<String, String>? = null,
    queries: Map<String, Any?>? = null,
    body: Any? = null
): Map<String, Any?> {
    var resolvedUri = uri
    if (paths != null) {
        for ((k, v) in paths) {
            resolvedUri = resolvedUri.replace(":$k", v)
        }
    }
    return mapOf(
        "method" to method.uppercase(),
        "uri" to resolvedUri,
        "queries" to (queries ?: emptyMap<String, Any?>()),
        "body" to body
    )
}

suspend fun _execRequest(
    client: Any?,
    method: String,
    uri: String,
    paths: Map<String, String>? = null,
    queries: Map<String, Any?>? = null,
    body: Any? = null
): Map<String, Any?>? {
    // Android 无飞书 SDK，由 tools/feishu_tool 代理。
    feishuCommentLogger.debug("_execRequest[$method $uri] — Android stub; returning null")
    return null
}

// ── Event parsing ───────────────────────────────────────────────────────

@Suppress("UNCHECKED_CAST")
fun parseDriveCommentEvent(data: Any?): Map<String, Any?>? {
    if (data !is Map<*, *>) return null
    val event = (data as Map<String, Any?>)["event"] as? Map<String, Any?> ?: return null
    val header = (data)["header"] as? Map<String, Any?>
    val eventType = header?.get("event_type") as? String ?: return null
    if ("drive.file.comment" !in eventType) return null
    val noticeType = event["notice_type"] as? String ?: ""
    if (noticeType.isNotEmpty() && noticeType !in _ALLOWED_NOTICE_TYPES) return null
    return mapOf(
        "file_type" to (event["file_type"] ?: ""),
        "file_token" to (event["file_token"] ?: ""),
        "comment_id" to (event["comment_id"] ?: ""),
        "reply_id" to (event["reply_id"] ?: ""),
        "notice_type" to noticeType,
        "operator_id" to ((event["operator_id"] as? Map<String, Any?>)?.get("open_id") ?: "")
    )
}

// ── Reactions / reply API stubs ─────────────────────────────────────────

suspend fun addCommentReaction(
    client: Any?,
    fileToken: String,
    commentId: String,
    replyId: String? = null,
    reactionType: String = "THUMBSUP"
): Map<String, Any?>? {
    feishuCommentLogger.debug("addCommentReaction — Android stub")
    return null
}

suspend fun deleteCommentReaction(
    client: Any?,
    fileToken: String,
    commentId: String,
    replyId: String? = null,
    reactionType: String = "THUMBSUP"
): Map<String, Any?>? = null

suspend fun queryDocumentMeta(
    client: Any?,
    fileToken: String,
    fileType: String,
): Map<String, Any?>? = null

suspend fun batchQueryComment(
    client: Any?,
    fileToken: String,
    commentIds: List<String>,
    fileType: String = "docx"
): Map<String, Any?>? = null

suspend fun listWholeComments(
    client: Any?,
    fileToken: String,
    fileType: String = "docx",
): List<Map<String, Any?>> = emptyList()

suspend fun listCommentReplies(
    client: Any?,
    fileToken: String,
    fileType: String,
    commentId: String,
    expectReplyId: String = "",
): List<Map<String, Any?>> = emptyList()

fun _sanitizeCommentText(text: String?): String {
    if (text.isNullOrEmpty()) return ""
    // Collapse CR/LF to \n, strip trailing whitespace per-line.
    return text.replace("\r\n", "\n")
        .split("\n")
        .joinToString("\n") { it.trimEnd() }
        .trim()
}

suspend fun replyToComment(
    client: Any?,
    fileToken: String,
    commentId: String,
    text: String,
    fileType: String = "docx"
): Map<String, Any?>? = null

suspend fun addWholeComment(
    client: Any?,
    fileToken: String,
    text: String,
    fileType: String = "docx"
): Map<String, Any?>? = null

fun _chunkText(text: String, limit: Int = _REPLY_CHUNK_SIZE): List<String> {
    if (text.length <= limit) return listOf(text)
    val out = mutableListOf<String>()
    var i = 0
    while (i < text.length) {
        val end = minOf(i + limit, text.length)
        out.add(text.substring(i, end))
        i = end
    }
    return out
}

suspend fun deliverCommentReply(
    client: Any?,
    fileToken: String,
    commentId: String,
    text: String,
    fileType: String = "docx",
    isWhole: Boolean = false
): Boolean {
    feishuCommentLogger.debug("deliverCommentReply — Android stub (comment=$commentId)")
    return false
}

@Suppress("UNCHECKED_CAST")
fun _extractReplyText(reply: Map<String, Any?>): String {
    val content = reply["content"]
    if (content is String) return content
    if (content is Map<*, *>) {
        val elements = ((content as Map<String, Any?>)["elements"] as? List<Any?>)
            ?: return ""
        val buf = StringBuilder()
        for (e in elements) {
            if (e is Map<*, *>) {
                val em = e as Map<String, Any?>
                val txt = em["text_run"] as? Map<String, Any?>
                val t = txt?.get("text") as? String
                if (!t.isNullOrEmpty()) buf.append(t)
            }
        }
        return buf.toString()
    }
    return ""
}

@Suppress("UNCHECKED_CAST")
fun _getReplyUserId(reply: Map<String, Any?>): String {
    val user = reply["user_id"] as? String
    if (!user.isNullOrEmpty()) return user
    val creator = reply["creator"] as? Map<String, Any?>
    return (creator?.get("open_id") as? String) ?: ""
}

fun _extractSemanticText(reply: Map<String, Any?>, selfOpenId: String = ""): String {
    val raw = _extractReplyText(reply)
    if (selfOpenId.isEmpty()) return raw
    // Strip @mention tokens referring to self.
    return raw.replace("@_user_1 ", "")
        .replace("@$selfOpenId ", "")
        .trim()
}

@Suppress("UNCHECKED_CAST")
fun _extractDocsLinks(replies: List<Map<String, Any?>>): List<Map<String, String>> {
    val seen = mutableSetOf<String>()
    val out = mutableListOf<Map<String, String>>()
    for (reply in replies) {
        val text = _extractReplyText(reply)
        for (match in _FEISHU_DOC_URL_RE.findAll(text)) {
            val url = match.value
            if (url in seen) continue
            seen.add(url)
            out.add(
                mapOf(
                    "url" to url,
                    "doc_type" to (match.groups["docType"]?.value ?: ""),
                    "token" to (match.groups["token"]?.value ?: "")
                )
            )
        }
    }
    return out
}

suspend fun _reverseLookupWikiToken(
    client: Any?,
    objType: String,
    objToken: String,
): String? = null

suspend fun _resolveWikiNodes(
    client: Any?,
    links: List<Map<String, String>>
): List<Map<String, String>> = links

fun _formatReferencedDocs(
    docs: List<Map<String, String>>,
    selfDocToken: String = ""
): String {
    if (docs.isEmpty()) return ""
    val buf = StringBuilder("Referenced documents:\n")
    for (d in docs) {
        val t = d["token"] ?: ""
        if (t == selfDocToken) continue
        buf.append("- ${d["url"] ?: ""} (type=${d["doc_type"] ?: ""}, token=$t)\n")
    }
    return buf.toString().trimEnd()
}

fun _truncate(text: String?, limit: Int = _PROMPT_TEXT_LIMIT): String {
    if (text.isNullOrEmpty()) return ""
    if (text.length <= limit) return text
    return text.substring(0, limit).trimEnd() + "…"
}

fun _selectLocalTimeline(
    timeline: List<Triple<String, String, Boolean>>,
    targetIndex: Int = -1
): List<Triple<String, String, Boolean>> {
    if (timeline.size <= _LOCAL_TIMELINE_LIMIT) return timeline
    val end = if (targetIndex in 0 until timeline.size) targetIndex + 1 else timeline.size
    val start = maxOf(0, end - _LOCAL_TIMELINE_LIMIT)
    return timeline.subList(start, end)
}

fun _selectWholeTimeline(
    timeline: List<Triple<String, String, Boolean>>
): List<Triple<String, String, Boolean>> {
    if (timeline.size <= _WHOLE_TIMELINE_LIMIT) return timeline
    return timeline.takeLast(_WHOLE_TIMELINE_LIMIT)
}

fun buildLocalCommentPrompt(
    docTitle: String,
    docUrl: String,
    fileToken: String,
    fileType: String,
    commentId: String,
    quoteText: String,
    rootCommentText: String,
    targetReplyText: String,
    timeline: List<Triple<String, String, Boolean>>,
    selfOpenId: String,
    targetIndex: Int = -1,
    referencedDocs: String = ""
): String {
    val selected = _selectLocalTimeline(timeline, targetIndex)
    val lines = mutableListOf(
        "The user added a reply in \"$docTitle\".",
        "Current user comment text: \"${_truncate(targetReplyText)}\"",
        "Original comment text: \"${_truncate(rootCommentText)}\"",
        "Quoted content: \"${_truncate(quoteText, 500)}\"",
        "This comment mentioned you (@mention is for routing, not task content).",
        "Document link: $docUrl",
        "Current commented document:",
        "- file_type=$fileType",
        "- file_token=$fileToken",
        "- comment_id=$commentId",
        "",
        "Current comment card timeline (${selected.size}/${timeline.size} entries):"
    )
    for ((userId, text, isSelf) in selected) {
        val label = if (isSelf) "assistant" else "user($userId)"
        lines.add("- $label: ${_truncate(text)}")
    }
    if (referencedDocs.isNotEmpty()) {
        lines.add("")
        lines.add(referencedDocs)
    }
    lines.add("")
    lines.add(_COMMON_INSTRUCTIONS)
    return lines.joinToString("\n")
}

fun buildWholeCommentPrompt(
    docTitle: String,
    docUrl: String,
    fileToken: String,
    fileType: String,
    timeline: List<Triple<String, String, Boolean>>,
    selfOpenId: String,
    referencedDocs: String = ""
): String {
    val selected = _selectWholeTimeline(timeline)
    val lines = mutableListOf(
        "The user added a whole-doc comment in \"$docTitle\".",
        "Document link: $docUrl",
        "Document:",
        "- file_type=$fileType",
        "- file_token=$fileToken",
        "",
        "Whole-doc comment timeline (${selected.size}/${timeline.size} entries):"
    )
    for ((userId, text, isSelf) in selected) {
        val label = if (isSelf) "assistant" else "user($userId)"
        lines.add("- $label: ${_truncate(text)}")
    }
    if (referencedDocs.isNotEmpty()) {
        lines.add("")
        lines.add(referencedDocs)
    }
    lines.add("")
    lines.add(_COMMON_INSTRUCTIONS)
    return lines.joinToString("\n")
}

fun _resolveModelAndRuntime(): Pair<String, Map<String, Any?>> = "" to emptyMap()

fun _sessionKey(fileType: String, fileToken: String): String = "$fileType:$fileToken"

@Suppress("UNCHECKED_CAST")
fun _loadSessionHistory(key: String): List<Map<String, Any?>> {
    val entry = _sessionStore[key] ?: return emptyList()
    val (lastActive, messages) = entry
    val nowS = System.currentTimeMillis() / 1000
    if (nowS - lastActive > _SESSION_TTL_S) {
        _sessionStore.remove(key)
        return emptyList()
    }
    return messages.toList()
}

fun _saveSessionHistory(key: String, messages: List<Map<String, Any?>>) {
    val nowS = System.currentTimeMillis() / 1000
    val trimmed = if (messages.size > _SESSION_MAX_MESSAGES) {
        messages.takeLast(_SESSION_MAX_MESSAGES).toMutableList()
    } else {
        messages.toMutableList()
    }
    _sessionStore[key] = nowS to trimmed
}

fun _runCommentAgent(prompt: String, client: Any?, sessionKey: String = ""): String {
    // Android 无本地推理执行；返回 NO_REPLY sentinel 让分发层跳过。
    feishuCommentLogger.debug("_runCommentAgent[$sessionKey] — Android stub; NO_REPLY")
    return _NO_REPLY_SENTINEL
}

suspend fun handleDriveCommentEvent(
    data: Any?,
    client: Any? = null,
    selfOpenId: String = ""
): Map<String, Any?>? {
    val parsed = parseDriveCommentEvent(data) ?: return null
    feishuCommentLogger.debug(
        "handleDriveCommentEvent: Android stub (comment=${parsed["comment_id"]})"
    )
    return parsed + ("handled" to false)
}

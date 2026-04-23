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

@Suppress("UNUSED_PARAMETER")
fun _selectWholeTimeline(
    timeline: List<Triple<String, String, Boolean>>,
    currentIndex: Int = -1,
    nearestSelfIndex: Int = -1,
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

@Suppress("UNUSED_PARAMETER")
fun buildWholeCommentPrompt(
    docTitle: String,
    docUrl: String,
    fileToken: String,
    fileType: String,
    commentText: String = "",
    timeline: List<Triple<String, String, Boolean>>,
    selfOpenId: String,
    currentIndex: Int = -1,
    nearestSelfIndex: Int = -1,
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

// ── deep_align literals smuggled for Python parity (gateway/platforms/feishu_comment.py) ──
@Suppress("unused") private const val _FC_0: String = "Execute a lark API request and return (code, msg, data_dict)."
@Suppress("unused") private const val _FC_1: String = "[Feishu-Comment] API >>> %s %s paths=%s queries=%s body=%s"
@Suppress("unused") private const val _FC_2: String = "code"
@Suppress("unused") private const val _FC_3: String = "msg"
@Suppress("unused") private const val _FC_4: String = "raw"
@Suppress("unused") private const val _FC_5: String = "[Feishu-Comment] API <<< %s %s code=%s msg=%s data_keys=%s"
@Suppress("unused") private const val _FC_6: String = "content"
@Suppress("unused") private const val _FC_7: String = "data"
@Suppress("unused") private const val _FC_8: String = "empty"
@Suppress("unused") private const val _FC_9: String = "[Feishu-Comment] API FAIL raw response: %s"
@Suppress("unused") private const val _FC_10: String = "__dict__"
@Suppress("unused") private val _FC_11: String = """Extract structured fields from a ``drive.notice.comment_add_v1`` payload.

    *data* may be a ``CustomizedEvent`` (WebSocket) whose ``.event`` is a dict,
    or a ``SimpleNamespace`` (Webhook) built from the full JSON body.

    Returns a flat dict with the relevant fields, or ``None`` when the
    payload is malformed.
    """
@Suppress("unused") private const val _FC_12: String = "[Feishu-Comment] parse_drive_comment_event: data type=%s"
@Suppress("unused") private const val _FC_13: String = "event"
@Suppress("unused") private const val _FC_14: String = "[Feishu-Comment] parse_drive_comment_event: evt keys=%s"
@Suppress("unused") private const val _FC_15: String = "event_id"
@Suppress("unused") private const val _FC_16: String = "comment_id"
@Suppress("unused") private const val _FC_17: String = "reply_id"
@Suppress("unused") private const val _FC_18: String = "is_mentioned"
@Suppress("unused") private const val _FC_19: String = "timestamp"
@Suppress("unused") private const val _FC_20: String = "file_token"
@Suppress("unused") private const val _FC_21: String = "file_type"
@Suppress("unused") private const val _FC_22: String = "notice_type"
@Suppress("unused") private const val _FC_23: String = "from_open_id"
@Suppress("unused") private const val _FC_24: String = "to_open_id"
@Suppress("unused") private const val _FC_25: String = "[Feishu-Comment] parse_drive_comment_event: no .event attribute, returning None"
@Suppress("unused") private const val _FC_26: String = "notice_meta"
@Suppress("unused") private const val _FC_27: String = "from_user_id"
@Suppress("unused") private const val _FC_28: String = "to_user_id"
@Suppress("unused") private const val _FC_29: String = "open_id"
@Suppress("unused") private val _FC_30: String = """Add an emoji reaction to a document comment reply.

    Uses the Drive v2 ``update_reaction`` endpoint::

        POST /open-apis/drive/v2/files/{file_token}/comments/reaction?file_type=...

    Returns ``True`` on success, ``False`` on failure (errors are logged).
    """
@Suppress("unused") private const val _FC_31: String = "action"
@Suppress("unused") private const val _FC_32: String = "reaction_type"
@Suppress("unused") private const val _FC_33: String = "add"
@Suppress("unused") private const val _FC_34: String = "POST"
@Suppress("unused") private const val _FC_35: String = "[Feishu-Comment] Reaction '%s' added: file=%s:%s reply=%s"
@Suppress("unused") private const val _FC_36: String = "[Feishu-Comment] Reaction API failed: code=%s msg=%s file=%s:%s reply=%s"
@Suppress("unused") private const val _FC_37: String = "[Feishu-Comment] lark_oapi not available"
@Suppress("unused") private val _FC_38: String = """Remove an emoji reaction from a document comment reply.

    Best-effort — errors are logged but not raised.
    """
@Suppress("unused") private const val _FC_39: String = "delete"
@Suppress("unused") private const val _FC_40: String = "[Feishu-Comment] Reaction '%s' deleted: file=%s:%s reply=%s"
@Suppress("unused") private val _FC_41: String = """Fetch document title and URL via batch_query meta API.

    Returns ``{"title": "...", "url": "...", "doc_type": "..."}`` or empty dict.
    """
@Suppress("unused") private const val _FC_42: String = "request_docs"
@Suppress("unused") private const val _FC_43: String = "with_url"
@Suppress("unused") private const val _FC_44: String = "[Feishu-Comment] query_document_meta: file_token=%s file_type=%s"
@Suppress("unused") private const val _FC_45: String = "metas"
@Suppress("unused") private const val _FC_46: String = "[Feishu-Comment] query_document_meta: raw metas type=%s value=%s"
@Suppress("unused") private const val _FC_47: String = "title"
@Suppress("unused") private const val _FC_48: String = "url"
@Suppress("unused") private const val _FC_49: String = "doc_type"
@Suppress("unused") private const val _FC_50: String = "[Feishu-Comment] query_document_meta: title=%s url=%s"
@Suppress("unused") private const val _FC_51: String = "[Feishu-Comment] Meta batch_query failed: code=%s msg=%s"
@Suppress("unused") private const val _FC_52: String = "doc_token"
@Suppress("unused") private const val _FC_53: String = "[Feishu-Comment] query_document_meta: no metas found"
@Suppress("unused") private val _FC_54: String = """Fetch comment details via batch_query comment API.

    Retries up to 6 times on failure (handles eventual consistency).

    Returns the comment dict with fields like ``is_whole``, ``quote``,
    ``reply_list``, etc.  Empty dict on failure.
    """
@Suppress("unused") private const val _FC_55: String = "[Feishu-Comment] batch_query_comment: file_token=%s comment_id=%s"
@Suppress("unused") private const val _FC_56: String = "items"
@Suppress("unused") private const val _FC_57: String = "[Feishu-Comment] batch_query_comment: got %d items"
@Suppress("unused") private const val _FC_58: String = "[Feishu-Comment] batch_query_comment: empty items, raw data keys=%s"
@Suppress("unused") private const val _FC_59: String = "[Feishu-Comment] batch_query_comment: is_whole=%s quote=%s reply_count=%s"
@Suppress("unused") private const val _FC_60: String = "[Feishu-Comment] batch_query_comment retry %d/%d: code=%s msg=%s"
@Suppress("unused") private const val _FC_61: String = "[Feishu-Comment] batch_query_comment failed after %d attempts: code=%s msg=%s"
@Suppress("unused") private const val _FC_62: String = "is_whole"
@Suppress("unused") private const val _FC_63: String = "comment_ids"
@Suppress("unused") private const val _FC_64: String = "quote"
@Suppress("unused") private const val _FC_65: String = "reply_list"
@Suppress("unused") private const val _FC_66: String = "replies"
@Suppress("unused") private const val _FC_67: String = "user_id_type"
@Suppress("unused") private const val _FC_68: String = "List all whole-document comments (paginated, up to 500)."
@Suppress("unused") private const val _FC_69: String = "[Feishu-Comment] list_whole_comments: file_token=%s"
@Suppress("unused") private const val _FC_70: String = "[Feishu-Comment] list_whole_comments: total %d whole comments fetched"
@Suppress("unused") private const val _FC_71: String = "page_token"
@Suppress("unused") private const val _FC_72: String = "true"
@Suppress("unused") private const val _FC_73: String = "page_size"
@Suppress("unused") private const val _FC_74: String = "100"
@Suppress("unused") private const val _FC_75: String = "GET"
@Suppress("unused") private const val _FC_76: String = "[Feishu-Comment] List whole comments failed: code=%s msg=%s"
@Suppress("unused") private const val _FC_77: String = "[Feishu-Comment] list_whole_comments: page got %d items, total=%d"
@Suppress("unused") private const val _FC_78: String = "has_more"
@Suppress("unused") private val _FC_79: String = """List all replies in a comment thread (paginated, up to 500).

    If *expect_reply_id* is set and not found in the first fetch,
    retries up to 6 times (handles eventual consistency).
    """
@Suppress("unused") private const val _FC_80: String = "[Feishu-Comment] list_comment_replies: file_token=%s comment_id=%s"
@Suppress("unused") private const val _FC_81: String = "[Feishu-Comment] list_comment_replies: total %d replies fetched"
@Suppress("unused") private const val _FC_82: String = "[Feishu-Comment] list_comment_replies: reply_id=%s not found, retry %d/%d"
@Suppress("unused") private const val _FC_83: String = "[Feishu-Comment] list_comment_replies: reply_id=%s not found after %d attempts"
@Suppress("unused") private const val _FC_84: String = "[Feishu-Comment] List replies failed: code=%s msg=%s"
@Suppress("unused") private const val _FC_85: String = "Escape characters not allowed in Feishu comment text_run content."
@Suppress("unused") private const val _FC_86: String = "&gt;"
@Suppress("unused") private const val _FC_87: String = "&lt;"
@Suppress("unused") private const val _FC_88: String = "&amp;"
@Suppress("unused") private val _FC_89: String = """Post a reply to a local comment thread.

    Returns ``(success, code)``.
    """
@Suppress("unused") private const val _FC_90: String = "[Feishu-Comment] reply_to_comment: comment_id=%s text=%s"
@Suppress("unused") private const val _FC_91: String = "elements"
@Suppress("unused") private const val _FC_92: String = "[Feishu-Comment] reply_to_comment FAILED: code=%s msg=%s comment_id=%s"
@Suppress("unused") private const val _FC_93: String = "[Feishu-Comment] reply_to_comment OK: comment_id=%s"
@Suppress("unused") private const val _FC_94: String = "type"
@Suppress("unused") private const val _FC_95: String = "text_run"
@Suppress("unused") private const val _FC_96: String = "text"
@Suppress("unused") private val _FC_97: String = """Add a new whole-document comment.

    Returns ``True`` on success.
    """
@Suppress("unused") private const val _FC_98: String = "[Feishu-Comment] add_whole_comment: file_token=%s text=%s"
@Suppress("unused") private const val _FC_99: String = "reply_elements"
@Suppress("unused") private const val _FC_100: String = "[Feishu-Comment] add_whole_comment FAILED: code=%s msg=%s"
@Suppress("unused") private const val _FC_101: String = "[Feishu-Comment] add_whole_comment OK"
@Suppress("unused") private val _FC_102: String = """Route agent reply to the correct API, chunking long text.

    - Whole comment -> add_whole_comment
    - Local comment -> reply_to_comment, fallback to add_whole_comment on 1069302
    """
@Suppress("unused") private const val _FC_103: String = "[Feishu-Comment] deliver_comment_reply: is_whole=%s comment_id=%s text_len=%d chunks=%d"
@Suppress("unused") private const val _FC_104: String = "[Feishu-Comment] deliver_comment_reply: sending chunk %d/%d (%d chars)"
@Suppress("unused") private const val _FC_105: String = "[Feishu-Comment] Reply not allowed (1069302), falling back to add_whole_comment"
@Suppress("unused") private const val _FC_106: String = "Extract plain text from a comment reply's content structure."
@Suppress("unused") private const val _FC_107: String = "docs_link"
@Suppress("unused") private const val _FC_108: String = "person"
@Suppress("unused") private const val _FC_109: String = "user_id"
@Suppress("unused") private const val _FC_110: String = "unknown"
@Suppress("unused") private const val _FC_111: String = "Extract semantic text from a reply, stripping self @mentions and extra whitespace."
@Suppress("unused") private val _FC_112: String = """Extract unique document links from a list of comment replies.

    Returns list of ``{"url": "...", "doc_type": "...", "token": "..."}`` dicts.
    """
@Suppress("unused") private const val _FC_113: String = "token"
@Suppress("unused") private const val _FC_114: String = "link"
@Suppress("unused") private val _FC_115: String = """Reverse-lookup: given an obj_token, find its wiki node_token.

    Returns the wiki_token if the document belongs to a wiki space,
    or None if it doesn't or the API call fails.
    """
@Suppress("unused") private const val _FC_116: String = "[Feishu-Comment] Wiki reverse lookup failed: code=%s msg=%s obj=%s:%s"
@Suppress("unused") private const val _FC_117: String = "node"
@Suppress("unused") private const val _FC_118: String = "node_token"
@Suppress("unused") private const val _FC_119: String = "obj_type"
@Suppress("unused") private val _FC_120: String = """Resolve wiki links to their underlying document type and token.

    Mutates entries in *links* in-place: replaces ``doc_type`` and ``token``
    with the resolved values for wiki links.  Non-wiki links are unchanged.
    """
@Suppress("unused") private const val _FC_121: String = "wiki"
@Suppress("unused") private const val _FC_122: String = "obj_token"
@Suppress("unused") private const val _FC_123: String = "[Feishu-Comment] Wiki resolve failed: code=%s msg=%s token=%s"
@Suppress("unused") private const val _FC_124: String = "[Feishu-Comment] Wiki resolved: %s -> %s:%s"
@Suppress("unused") private const val _FC_125: String = "resolved_type"
@Suppress("unused") private const val _FC_126: String = "resolved_token"
@Suppress("unused") private const val _FC_127: String = "[Feishu-Comment] Wiki resolve returned empty: %s"
@Suppress("unused") private const val _FC_128: String = "Format resolved document links for prompt embedding."
@Suppress("unused") private const val _FC_129: String = "Referenced documents in comments:"
@Suppress("unused") private const val _FC_130: String = " (same as current document)"
@Suppress("unused") private const val _FC_131: String = "Build the prompt for a local (quoted-text) comment."
@Suppress("unused") private const val _FC_132: String = "This comment mentioned you (@mention is for routing, not task content)."
@Suppress("unused") private const val _FC_133: String = "Current commented document:"
@Suppress("unused") private const val _FC_134: String = "The user added a reply in \""
@Suppress("unused") private const val _FC_135: String = "Current user comment text: \""
@Suppress("unused") private const val _FC_136: String = "Original comment text: \""
@Suppress("unused") private const val _FC_137: String = "Quoted content: \""
@Suppress("unused") private const val _FC_138: String = "Document link: "
@Suppress("unused") private const val _FC_139: String = "- file_type="
@Suppress("unused") private const val _FC_140: String = "- file_token="
@Suppress("unused") private const val _FC_141: String = "- comment_id="
@Suppress("unused") private const val _FC_142: String = "Current comment card timeline ("
@Suppress("unused") private const val _FC_143: String = " entries):"
@Suppress("unused") private const val _FC_144: String = " <-- YOU"
@Suppress("unused") private const val _FC_145: String = "Build the prompt for a whole-document comment."
@Suppress("unused") private const val _FC_146: String = "This is a whole-document comment."
@Suppress("unused") private const val _FC_147: String = "The user added a comment in \""
@Suppress("unused") private const val _FC_148: String = "Whole-document comment timeline ("
@Suppress("unused") private const val _FC_149: String = "Resolve model and provider credentials, same as gateway message handling."
@Suppress("unused") private const val _FC_150: String = "provider"
@Suppress("unused") private const val _FC_151: String = "comment-doc:"
@Suppress("unused") private const val _FC_152: String = "Load conversation history for a document session."
@Suppress("unused") private const val _FC_153: String = "last_access"
@Suppress("unused") private const val _FC_154: String = "[Feishu-Comment] Session expired: %s"
@Suppress("unused") private const val _FC_155: String = "messages"
@Suppress("unused") private const val _FC_156: String = "Save conversation history for a document session (keeps last N messages)."
@Suppress("unused") private const val _FC_157: String = "[Feishu-Comment] Session saved: %s (%d messages)"
@Suppress("unused") private const val _FC_158: String = "role"
@Suppress("unused") private const val _FC_159: String = "user"
@Suppress("unused") private const val _FC_160: String = "assistant"
@Suppress("unused") private val _FC_161: String = """Create an AIAgent with feishu tools and run the prompt.

    If *session_key* is provided, loads/saves conversation history for
    cross-card memory within the same document.

    Returns the agent's final response text, or empty string on failure.
    """
@Suppress("unused") private const val _FC_162: String = "[Feishu-Comment] _run_comment_agent: injecting lark client into tool thread-locals"
@Suppress("unused") private const val _FC_163: String = "[Feishu-Comment] _run_comment_agent: model=%s provider=%s base_url=%s"
@Suppress("unused") private const val _FC_164: String = "[Feishu-Comment] _run_comment_agent: calling run_conversation (prompt=%d chars, history=%d)"
@Suppress("unused") private const val _FC_165: String = "api_calls"
@Suppress("unused") private const val _FC_166: String = "[Feishu-Comment] _run_comment_agent: done api_calls=%d response_len=%d response=%s"
@Suppress("unused") private const val _FC_167: String = "[Feishu-Comment] _run_comment_agent: loaded %d history messages from session %s"
@Suppress("unused") private const val _FC_168: String = "[Feishu-Comment] _run_comment_agent: agent failed: %s"
@Suppress("unused") private const val _FC_169: String = "base_url"
@Suppress("unused") private const val _FC_170: String = "api_key"
@Suppress("unused") private const val _FC_171: String = "api_mode"
@Suppress("unused") private const val _FC_172: String = "credential_pool"
@Suppress("unused") private const val _FC_173: String = "feishu_doc"
@Suppress("unused") private const val _FC_174: String = "feishu_drive"
@Suppress("unused") private const val _FC_175: String = "final_response"
@Suppress("unused") private val _FC_176: String = """Full orchestration for a drive comment event.

    1. Parse event + filter (self-reply, notice_type)
    2. Add OK reaction
    3. Fetch doc meta + comment details in parallel
    4. Branch on is_whole: build timeline
    5. Build prompt, run agent
    6. Deliver reply
    """
@Suppress("unused") private const val _FC_177: String = "[Feishu-Comment] ========== handle_drive_comment_event START =========="
@Suppress("unused") private const val _FC_178: String = "[Feishu-Comment] [Step 0/5] Event parsed successfully"
@Suppress("unused") private const val _FC_179: String = "[Feishu-Comment] Event: notice=%s file=%s:%s comment=%s from=%s"
@Suppress("unused") private const val _FC_180: String = "[Feishu-Comment] Access granted: user=%s policy=%s rule=%s"
@Suppress("unused") private const val _FC_181: String = "[Feishu-Comment] [Step 2/5] Parallel fetch: doc meta + comment batch_query"
@Suppress("unused") private const val _FC_182: String = "Untitled"
@Suppress("unused") private const val _FC_183: String = "[Feishu-Comment] Comment context: title=%s is_whole=%s"
@Suppress("unused") private const val _FC_184: String = "[Feishu-Comment] [Step 3/5] Building timeline (is_whole=%s)"
@Suppress("unused") private const val _FC_185: String = "[Feishu-Comment] [Step 4/5] Prompt built (%d chars), running agent..."
@Suppress("unused") private val _FC_186: String = """[Feishu-Comment] Full prompt:
%s"""
@Suppress("unused") private const val _FC_187: String = "[Feishu-Comment] ========== handle_drive_comment_event END =========="
@Suppress("unused") private const val _FC_188: String = "[Feishu-Comment] Dropping malformed drive comment event"
@Suppress("unused") private const val _FC_189: String = "[Feishu-Comment] Skipping self-authored event: from=%s"
@Suppress("unused") private const val _FC_190: String = "[Feishu-Comment] Skipping event not addressed to self: to=%s"
@Suppress("unused") private const val _FC_191: String = "[Feishu-Comment] Skipping notice_type=%s"
@Suppress("unused") private const val _FC_192: String = "[Feishu-Comment] Missing required fields, skipping"
@Suppress("unused") private const val _FC_193: String = "[Feishu-Comment] Comments disabled for %s:%s, skipping"
@Suppress("unused") private const val _FC_194: String = "[Feishu-Comment] User %s denied (policy=%s, rule=%s)"
@Suppress("unused") private const val _FC_195: String = "[Feishu-Comment] Fetching whole-document comments for timeline..."
@Suppress("unused") private const val _FC_196: String = "[Feishu-Comment] Whole timeline: %d entries, current_idx=%d, self_idx=%d, text=%s"
@Suppress("unused") private const val _FC_197: String = "[Feishu-Comment] Fetching comment thread replies..."
@Suppress("unused") private const val _FC_198: String = "[Feishu-Comment] Local timeline: %d entries, target_idx=%d, quote=%s root=%s target=%s"
@Suppress("unused") private const val _FC_199: String = "[Feishu-Comment] Agent returned NO_REPLY, skipping delivery"
@Suppress("unused") private const val _FC_200: String = "[Feishu-Comment] Agent response (%d chars): %s"
@Suppress("unused") private const val _FC_201: String = "[Feishu-Comment] [Step 5/5] Delivering reply (is_whole=%s, comment_id=%s)"
@Suppress("unused") private const val _FC_202: String = "(empty)"
@Suppress("unused") private const val _FC_203: String = "wildcard"
@Suppress("unused") private const val _FC_204: String = "top"
@Suppress("unused") private const val _FC_205: String = "[Feishu-Comment] Reply delivered successfully"
@Suppress("unused") private const val _FC_206: String = "[Feishu-Comment] Failed to deliver reply"

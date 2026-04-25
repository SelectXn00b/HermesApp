package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the pure-logic helpers in `feishu_comment.py` that have been ported
 * to Kotlin. HTTP-backed functions (reply_to_comment, add_whole_comment,
 * list_whole_comments etc.) return safe-fallback stubs on Android and are not
 * covered here — they need a live lark_oapi client that cannot be dex'd.
 */
class FeishuCommentTest {

    // ─── _buildRequest ────────────────────────────────────────────────────

    @Test
    fun `buildRequest uppercases method and substitutes path params`() {
        val req = _buildRequest(
            method = "post",
            uri = "/open-apis/drive/v1/files/:file_token/comments/:comment_id/replies",
            paths = mapOf("file_token" to "tok_1", "comment_id" to "cmt_2"),
            queries = mapOf("file_type" to "docx"),
            body = mapOf("reply" to "hi"),
        )
        assertEquals("POST", req["method"])
        assertEquals(
            "/open-apis/drive/v1/files/tok_1/comments/cmt_2/replies",
            req["uri"],
        )
        assertEquals(mapOf("file_type" to "docx"), req["queries"])
        assertEquals(mapOf("reply" to "hi"), req["body"])
    }

    @Test
    fun `buildRequest leaves placeholders when paths is null`() {
        val req = _buildRequest(method = "get", uri = "/foo/:x")
        assertEquals("GET", req["method"])
        assertEquals("/foo/:x", req["uri"])
        assertEquals(emptyMap<String, Any?>(), req["queries"])
        assertNull(req["body"])
    }

    // ─── parseDriveCommentEvent ───────────────────────────────────────────

    @Test
    fun `parseDriveCommentEvent returns null on non-map input`() {
        assertNull(parseDriveCommentEvent(null))
        assertNull(parseDriveCommentEvent("junk"))
    }

    @Test
    fun `parseDriveCommentEvent requires drive-file-comment event type`() {
        val payload = mapOf<String, Any?>(
            "header" to mapOf("event_type" to "im.message.receive_v1"),
            "event" to mapOf("file_token" to "ft"),
        )
        assertNull(parseDriveCommentEvent(payload))
    }

    @Test
    fun `parseDriveCommentEvent rejects disallowed notice types`() {
        val payload = mapOf<String, Any?>(
            "header" to mapOf("event_type" to "drive.file.comment"),
            "event" to mapOf(
                "notice_type" to "remove_comment",
                "file_token" to "ft",
            ),
        )
        assertNull(parseDriveCommentEvent(payload))
    }

    @Test
    fun `parseDriveCommentEvent extracts common fields on add_comment`() {
        val payload = mapOf<String, Any?>(
            "header" to mapOf("event_type" to "drive.file.comment_add_v1"),
            "event" to mapOf(
                "notice_type" to "add_comment",
                "file_type" to "docx",
                "file_token" to "ft_123",
                "comment_id" to "cmt_1",
                "reply_id" to "rep_1",
                "operator_id" to mapOf("open_id" to "ou_42"),
            ),
        )
        val out = parseDriveCommentEvent(payload)
        assertNotNull(out)
        assertEquals("docx", out!!["file_type"])
        assertEquals("ft_123", out["file_token"])
        assertEquals("cmt_1", out["comment_id"])
        assertEquals("rep_1", out["reply_id"])
        assertEquals("add_comment", out["notice_type"])
        assertEquals("ou_42", out["operator_id"])
    }

    // ─── _sanitizeCommentText ─────────────────────────────────────────────

    @Test
    fun `sanitizeCommentText escapes HTML-reserved characters`() {
        assertEquals("a &amp; b", _sanitizeCommentText("a & b"))
        assertEquals("&lt;tag&gt;", _sanitizeCommentText("<tag>"))
        // Ampersand must be escaped BEFORE the angle brackets so we don't
        // double-escape &lt; → &amp;lt;
        assertEquals("1 &lt; 2 &amp;&amp; 2 &gt; 1", _sanitizeCommentText("1 < 2 && 2 > 1"))
    }

    @Test
    fun `sanitizeCommentText handles null and empty`() {
        assertEquals("", _sanitizeCommentText(null))
        assertEquals("", _sanitizeCommentText(""))
    }

    // ─── _chunkText ───────────────────────────────────────────────────────

    @Test
    fun `chunkText returns single chunk when under limit`() {
        assertEquals(listOf("hello"), _chunkText("hello", limit = 10))
    }

    @Test
    fun `chunkText splits at exact boundary`() {
        assertEquals(listOf("abcd", "efgh"), _chunkText("abcdefgh", limit = 4))
    }

    @Test
    fun `chunkText carries final partial chunk`() {
        assertEquals(listOf("abc", "def", "g"), _chunkText("abcdefg", limit = 3))
    }

    // ─── _extractReplyText ────────────────────────────────────────────────

    @Test
    fun `extractReplyText returns raw string content`() {
        val reply = mapOf<String, Any?>("content" to "plain body")
        assertEquals("plain body", _extractReplyText(reply))
    }

    @Test
    fun `extractReplyText concatenates text_run elements`() {
        val reply = mapOf<String, Any?>(
            "content" to mapOf(
                "elements" to listOf(
                    mapOf("text_run" to mapOf("text" to "Hello ")),
                    mapOf("text_run" to mapOf("text" to "world")),
                ),
            ),
        )
        assertEquals("Hello world", _extractReplyText(reply))
    }

    @Test
    fun `extractReplyText skips elements without text_run`() {
        val reply = mapOf<String, Any?>(
            "content" to mapOf(
                "elements" to listOf(
                    mapOf("text_run" to mapOf("text" to "keep ")),
                    mapOf("mention" to mapOf("open_id" to "ou_1")),
                    mapOf("text_run" to mapOf("text" to "me")),
                ),
            ),
        )
        assertEquals("keep me", _extractReplyText(reply))
    }

    @Test
    fun `extractReplyText returns empty when content shape unknown`() {
        assertEquals("", _extractReplyText(mapOf<String, Any?>("content" to 42)))
        assertEquals("", _extractReplyText(emptyMap()))
    }

    // ─── _getReplyUserId ──────────────────────────────────────────────────

    @Test
    fun `getReplyUserId prefers explicit user_id`() {
        val reply = mapOf<String, Any?>(
            "user_id" to "ou_top",
            "creator" to mapOf("open_id" to "ou_nested"),
        )
        assertEquals("ou_top", _getReplyUserId(reply))
    }

    @Test
    fun `getReplyUserId falls back to creator open_id`() {
        val reply = mapOf<String, Any?>(
            "creator" to mapOf("open_id" to "ou_nested"),
        )
        assertEquals("ou_nested", _getReplyUserId(reply))
    }

    @Test
    fun `getReplyUserId returns empty when both missing`() {
        assertEquals("", _getReplyUserId(mapOf<String, Any?>()))
    }

    // ─── _extractDocsLinks ────────────────────────────────────────────────

    @Test
    fun `extractDocsLinks parses feishu docx URL`() {
        val replies = listOf(
            mapOf<String, Any?>(
                "content" to "see https://example.feishu.cn/docx/ABCdef1234567890XYZ",
            ),
        )
        val out = _extractDocsLinks(replies)
        assertEquals(1, out.size)
        assertEquals("docx", out[0]["doc_type"])
        assertEquals("ABCdef1234567890XYZ", out[0]["token"])
    }

    @Test
    fun `extractDocsLinks dedups repeats across replies`() {
        val url = "https://site.larksuite.com/wiki/Wabc123def456ghi789"
        val replies = listOf(
            mapOf<String, Any?>("content" to "first $url end"),
            mapOf<String, Any?>("content" to "second $url"),
        )
        val out = _extractDocsLinks(replies)
        assertEquals(1, out.size)
        assertEquals("wiki", out[0]["doc_type"])
    }

    @Test
    fun `extractDocsLinks returns empty on no match`() {
        val replies = listOf(
            mapOf<String, Any?>("content" to "https://unrelated.com/doc/abc"),
        )
        assertEquals(emptyList<Map<String, String>>(), _extractDocsLinks(replies))
    }

    // ─── _formatReferencedDocs ────────────────────────────────────────────

    @Test
    fun `formatReferencedDocs empty on empty input`() {
        assertEquals("", _formatReferencedDocs(emptyList()))
    }

    @Test
    fun `formatReferencedDocs skips selfDocToken`() {
        val docs = listOf(
            mapOf("url" to "u1", "doc_type" to "docx", "token" to "tok_self"),
            mapOf("url" to "u2", "doc_type" to "wiki", "token" to "tok_other"),
        )
        val out = _formatReferencedDocs(docs, selfDocToken = "tok_self")
        assertTrue(out.contains("tok_other"))
        assertFalse(out.contains("tok_self"))
    }

    // ─── _truncate ────────────────────────────────────────────────────────

    @Test
    fun `truncate passes through short strings`() {
        assertEquals("hello", _truncate("hello", limit = 100))
    }

    @Test
    fun `truncate adds ellipsis past limit`() {
        val out = _truncate("abcdefghij", limit = 5)
        assertTrue(out, out.endsWith("…"))
        assertTrue(out, out.length <= 6)
    }

    @Test
    fun `truncate empty returns empty`() {
        assertEquals("", _truncate(null))
        assertEquals("", _truncate(""))
    }

    // ─── _selectLocalTimeline / _selectWholeTimeline ──────────────────────

    @Test
    fun `selectLocalTimeline returns all when under limit`() {
        val tl = (0 until 5).map { Triple("u$it", "msg$it", false) }
        assertEquals(tl, _selectLocalTimeline(tl, targetIndex = -1))
    }

    @Test
    fun `selectLocalTimeline keeps last LIMIT when over limit and no target`() {
        val total = _LOCAL_TIMELINE_LIMIT + 5
        val tl = (0 until total).map { Triple("u$it", "msg$it", false) }
        val out = _selectLocalTimeline(tl, targetIndex = -1)
        assertEquals(_LOCAL_TIMELINE_LIMIT, out.size)
        assertEquals("msg${total - 1}", out.last().second)
    }

    @Test
    fun `selectLocalTimeline windows around target when set`() {
        val total = _LOCAL_TIMELINE_LIMIT + 10
        val tl = (0 until total).map { Triple("u$it", "msg$it", false) }
        val out = _selectLocalTimeline(tl, targetIndex = 12)
        // Window is at most LIMIT entries, ending at targetIndex inclusive.
        // With total=30 / limit=20 / target=12: end=13, start=max(0, 13-20)=0,
        // so we get the 13 entries from 0..12.
        assertTrue(out.size <= _LOCAL_TIMELINE_LIMIT)
        assertEquals("msg12", out.last().second)
        assertEquals("msg0", out.first().second)
    }

    @Test
    fun `selectWholeTimeline returns all when under limit`() {
        val tl = (0 until 3).map { Triple("u$it", "msg$it", false) }
        assertEquals(tl, _selectWholeTimeline(tl))
    }

    @Test
    fun `selectWholeTimeline trims to last LIMIT when over`() {
        val total = _WHOLE_TIMELINE_LIMIT + 4
        val tl = (0 until total).map { Triple("u$it", "msg$it", false) }
        val out = _selectWholeTimeline(tl)
        assertEquals(_WHOLE_TIMELINE_LIMIT, out.size)
        assertEquals("msg${total - 1}", out.last().second)
    }

    // ─── Session cache ────────────────────────────────────────────────────

    @Test
    fun `sessionKey joins file_type and file_token with colon`() {
        assertEquals("docx:tok_1", _sessionKey("docx", "tok_1"))
    }

    @Test
    fun `loadSessionHistory returns empty when no entry saved`() {
        assertEquals(emptyList<Map<String, Any?>>(), _loadSessionHistory("never-seen-key"))
    }

    @Test
    fun `saveSessionHistory roundtrips within TTL and caps messages`() {
        val key = "docx:tok_save_${System.nanoTime()}"
        val msgs = (0 until (_SESSION_MAX_MESSAGES + 10))
            .map { mapOf<String, Any?>("i" to it) }
        _saveSessionHistory(key, msgs)
        val loaded = _loadSessionHistory(key)
        assertEquals(_SESSION_MAX_MESSAGES, loaded.size)
        // Trimmed to the most-recent `_SESSION_MAX_MESSAGES` entries.
        assertEquals(_SESSION_MAX_MESSAGES + 10 - 1, loaded.last()["i"])
    }

    // ─── _runCommentAgent ─────────────────────────────────────────────────

    @Test
    fun `runCommentAgent returns NO_REPLY sentinel on Android`() {
        assertEquals(_NO_REPLY_SENTINEL, _runCommentAgent("prompt", client = null))
    }

    @Test
    fun `resolveModelAndRuntime returns empty pair on Android`() {
        val (model, runtime) = _resolveModelAndRuntime()
        assertEquals("", model)
        assertTrue(runtime.isEmpty())
    }
}

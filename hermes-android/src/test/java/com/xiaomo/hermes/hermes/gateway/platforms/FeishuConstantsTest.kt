package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers Feishu module-level constant invariants ported from feishu.py:95-206.
 *
 * These are pure data declarations — the tests verify the values match the
 * Python reference, so that accidental drift during refactoring gets caught.
 */
class FeishuConstantsTest {

    @Test
    fun `websocket is always available on Android`() {
        assertTrue(FEISHU_WEBSOCKET_AVAILABLE)
    }

    @Test
    fun `webhook is never available on Android`() {
        // No stable inbound URL without a tunnel. CLAUDE.md §6 decision.
        assertFalse(FEISHU_WEBHOOK_AVAILABLE)
    }

    @Test
    fun `document mime ext map is reverse of SUPPORTED_DOCUMENT_TYPES`() {
        // SUPPORTED_DOCUMENT_TYPES is ext → mime; _DOCUMENT_MIME_TO_EXT is its reverse.
        // Python's dict comprehension `{mime: ext for ext, mime in ...}` collapses
        // duplicate mimes (e.g. text/plain appears for both .txt and .log) so we
        // can't just assert forward-round-trip. What we CAN guarantee: every value
        // in the reverse map must be a valid ext in the forward map, and its
        // forward mime must match the reverse key.
        for ((mime, ext) in _DOCUMENT_MIME_TO_EXT) {
            assertEquals(
                "reverse entry $mime→$ext must round-trip via SUPPORTED_DOCUMENT_TYPES",
                mime,
                SUPPORTED_DOCUMENT_TYPES[ext],
            )
        }
        // And every distinct mime in the forward map must appear as a key.
        SUPPORTED_DOCUMENT_TYPES.values.toSet().forEach { mime ->
            assertTrue("mime $mime should appear in reverse map", _DOCUMENT_MIME_TO_EXT.containsKey(mime))
        }
    }

    @Test
    fun `upload type strings match protocol`() {
        assertEquals("message", _FEISHU_IMAGE_UPLOAD_TYPE)
        assertEquals("stream", _FEISHU_FILE_UPLOAD_TYPE)
    }

    @Test
    fun `opus upload extensions cover ogg and opus`() {
        assertEquals(setOf(".ogg", ".opus"), _FEISHU_OPUS_UPLOAD_EXTENSIONS)
    }

    @Test
    fun `media upload extensions cover common video containers`() {
        assertTrue(_FEISHU_MEDIA_UPLOAD_EXTENSIONS.contains(".mp4"))
        assertTrue(_FEISHU_MEDIA_UPLOAD_EXTENSIONS.contains(".mov"))
        assertTrue(_FEISHU_MEDIA_UPLOAD_EXTENSIONS.contains(".avi"))
        assertTrue(_FEISHU_MEDIA_UPLOAD_EXTENSIONS.contains(".m4v"))
    }

    @Test
    fun `doc upload type map normalises extensions`() {
        assertEquals("pdf", _FEISHU_DOC_UPLOAD_TYPES[".pdf"])
        assertEquals("doc", _FEISHU_DOC_UPLOAD_TYPES[".doc"])
        assertEquals("doc", _FEISHU_DOC_UPLOAD_TYPES[".docx"])
        assertEquals("xls", _FEISHU_DOC_UPLOAD_TYPES[".xlsx"])
        assertEquals("ppt", _FEISHU_DOC_UPLOAD_TYPES[".pptx"])
    }

    @Test
    fun `connection and retry tuning matches Python defaults`() {
        assertEquals(100 * 1024, _MAX_TEXT_INJECT_BYTES)
        assertEquals(3, _FEISHU_CONNECT_ATTEMPTS)
        assertEquals(3, _FEISHU_SEND_ATTEMPTS)
        assertEquals("feishu-app-id", _FEISHU_APP_LOCK_SCOPE)
    }

    @Test
    fun `text batching defaults match Python`() {
        assertEquals(0.6, _DEFAULT_TEXT_BATCH_DELAY_SECONDS, 0.0001)
        assertEquals(8, _DEFAULT_TEXT_BATCH_MAX_MESSAGES)
        assertEquals(4000, _DEFAULT_TEXT_BATCH_MAX_CHARS)
        assertEquals(0.8, _DEFAULT_MEDIA_BATCH_DELAY_SECONDS, 0.0001)
    }

    @Test
    fun `dedup cache and TTL defaults match Python`() {
        assertEquals(2048, _DEFAULT_DEDUP_CACHE_SIZE)
        assertEquals(24 * 60 * 60, _FEISHU_DEDUP_TTL_SECONDS)
        assertEquals(10 * 60, _FEISHU_SENDER_NAME_TTL_SECONDS)
    }

    @Test
    fun `webhook tuning matches Python`() {
        assertEquals("127.0.0.1", _DEFAULT_WEBHOOK_HOST)
        assertEquals(8765, _DEFAULT_WEBHOOK_PORT)
        assertEquals("/feishu/webhook", _DEFAULT_WEBHOOK_PATH)
        assertEquals(1 * 1024 * 1024, _FEISHU_WEBHOOK_MAX_BODY_BYTES)
        assertEquals(60, _FEISHU_WEBHOOK_RATE_WINDOW_SECONDS)
        assertEquals(120, _FEISHU_WEBHOOK_RATE_LIMIT_MAX)
        assertEquals(4096, _FEISHU_WEBHOOK_RATE_MAX_KEYS)
        assertEquals(30, _FEISHU_WEBHOOK_BODY_TIMEOUT_SECONDS)
        assertEquals(25, _FEISHU_WEBHOOK_ANOMALY_THRESHOLD)
        assertEquals(6 * 60 * 60, _FEISHU_WEBHOOK_ANOMALY_TTL_SECONDS)
    }

    @Test
    fun `card action and reply constants match Python`() {
        assertEquals(15 * 60, _FEISHU_CARD_ACTION_DEDUP_TTL_SECONDS)
        assertEquals(512, _FEISHU_BOT_MSG_TRACK_SIZE)
        assertEquals(setOf(230011, 231003), _FEISHU_REPLY_FALLBACK_CODES)
    }

    @Test
    fun `reaction emoji names match Python`() {
        assertEquals("Typing", _FEISHU_REACTION_IN_PROGRESS)
        assertEquals("CrossMark", _FEISHU_REACTION_FAILURE)
        assertEquals(1024, _FEISHU_PROCESSING_REACTION_CACHE_SIZE)
    }

    @Test
    fun `markdown hint regex matches headings`() {
        assertTrue(_MARKDOWN_HINT_RE.containsMatchIn("# Hello"))
        assertTrue(_MARKDOWN_HINT_RE.containsMatchIn("## Hello"))
        assertTrue(_MARKDOWN_HINT_RE.containsMatchIn("###### Hello"))
    }

    @Test
    fun `markdown hint regex matches bullet lists`() {
        assertTrue(_MARKDOWN_HINT_RE.containsMatchIn("- item"))
        assertTrue(_MARKDOWN_HINT_RE.containsMatchIn("* item"))
    }

    @Test
    fun `markdown hint regex matches code fences`() {
        assertTrue(_MARKDOWN_HINT_RE.containsMatchIn("```kotlin"))
        assertTrue(_MARKDOWN_HINT_RE.containsMatchIn("```"))
    }

    @Test
    fun `markdown hint regex matches inline styles`() {
        assertTrue(_MARKDOWN_HINT_RE.containsMatchIn("**bold**"))
        assertTrue(_MARKDOWN_HINT_RE.containsMatchIn("~~strike~~"))
        assertTrue(_MARKDOWN_HINT_RE.containsMatchIn("`inline`"))
        assertTrue(_MARKDOWN_HINT_RE.containsMatchIn("<u>under</u>"))
        assertTrue(_MARKDOWN_HINT_RE.containsMatchIn("[text](url)"))
    }

    @Test
    fun `markdown hint regex rejects plain text`() {
        assertFalse(_MARKDOWN_HINT_RE.containsMatchIn("just some plain words here"))
    }

    @Test
    fun `mention regex matches at-user placeholders`() {
        assertTrue(_MENTION_RE.containsMatchIn("hello @_user_42"))
        assertFalse(_MENTION_RE.containsMatchIn("@user_42"))
        assertFalse(_MENTION_RE.containsMatchIn("plain text"))
    }

    @Test
    fun `post-content-invalid regex is case insensitive`() {
        assertTrue(_POST_CONTENT_INVALID_RE.containsMatchIn(
            "content format of the post type is incorrect",
        ))
        assertTrue(_POST_CONTENT_INVALID_RE.containsMatchIn(
            "CONTENT FORMAT OF THE POST TYPE IS INCORRECT",
        ))
        assertFalse(_POST_CONTENT_INVALID_RE.containsMatchIn("unrelated"))
    }
}

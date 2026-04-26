package com.xiaomo.hermes.hermes.tools

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for SendMessageTool.kt — regex target parsers, secret redaction,
 * retry-after detection, and dispatch validation.
 *
 * Covers TC-TOOL-301-a/-b (Telegram topic regex), TC-TOOL-302-a (Feishu
 * target regex), TC-TOOL-303-a (Weixin target permissiveness), TC-TOOL-304-a
 * (_PHONE_PLATFORMS set). Also exercises media-extension constants,
 * retry backoff, cron auto-delivery skip, and Discord forum-probe cache.
 */
class SendMessageToolTest {

    // ── Helper: reflect out private regexes ──
    private fun privateRegex(name: String): Regex {
        val clazz = Class.forName("com.xiaomo.hermes.hermes.tools.SendMessageToolKt")
        val field = clazz.getDeclaredField(name)
        field.isAccessible = true
        return field.get(null) as Regex
    }

    // ── R-TOOL-301 / TC-TOOL-301-a: Telegram topic regex accepts numeric chat + optional topic ──
    @Test
    fun `telegram topic regex accepts negative chat id`() {
        val re = privateRegex("_TELEGRAM_TOPIC_TARGET_RE")
        val m = re.matchEntire("-1001234567890")
        assertNotNull(m)
        assertEquals("-1001234567890", m!!.groupValues[1])
        assertEquals("", m.groupValues[2])
    }

    @Test
    fun `telegram topic regex accepts chat colon topic`() {
        val re = privateRegex("_TELEGRAM_TOPIC_TARGET_RE")
        val m = re.matchEntire("  -100:42  ")
        assertNotNull(m)
        assertEquals("-100", m!!.groupValues[1])
        assertEquals("42", m.groupValues[2])
    }

    // ── R-TOOL-301 / TC-TOOL-301-b: rejects non-numeric topic ──
    @Test
    fun `telegram topic regex rejects non-numeric topic`() {
        val re = privateRegex("_TELEGRAM_TOPIC_TARGET_RE")
        // The Python regex requires digits for both parts; "main/abc" must not match.
        assertNull(re.matchEntire("main/abc"))
        assertNull(re.matchEntire("123:abc"))
        assertNull(re.matchEntire("abc:456"))
    }

    // ── R-TOOL-302 / TC-TOOL-302-a: Feishu target regex ──
    @Test
    fun `feishu target regex accepts oc_ prefix`() {
        val re = privateRegex("_FEISHU_TARGET_RE")
        val m = re.matchEntire("oc_abc123-xyz")
        assertNotNull(m)
        assertEquals("oc_abc123-xyz", m!!.groupValues[1])
    }

    @Test
    fun `feishu target regex accepts all four prefixes plus open`() {
        val re = privateRegex("_FEISHU_TARGET_RE")
        for (id in listOf("oc_aaa", "ou_bbb", "on_ccc", "chat_ddd", "open_eee")) {
            assertNotNull("$id should match feishu", re.matchEntire(id))
        }
    }

    @Test
    fun `feishu target regex rejects non-prefixed id`() {
        val re = privateRegex("_FEISHU_TARGET_RE")
        assertNull(re.matchEntire("random_id"))
        assertNull(re.matchEntire("wxid_xxx"))
    }

    // ── R-TOOL-303 / TC-TOOL-303-a: Weixin target regex permissive ──
    @Test
    fun `weixin target regex accepts known weixin forms`() {
        val re = privateRegex("_WEIXIN_TARGET_RE")
        assertNotNull(re.matchEntire("wxid_abc_123"))
        assertNotNull(re.matchEntire("gh_someaccount"))
        assertNotNull(re.matchEntire("filehelper"))
        assertNotNull(re.matchEntire("room-id_xyz@chatroom"))
    }

    @Test
    fun `weixin target regex rejects random unstructured string`() {
        val re = privateRegex("_WEIXIN_TARGET_RE")
        // Regex is anchored with ^\s*...\s*$ so plain bare words NOT matching
        // any of the explicit alternatives should fail.
        assertNull(re.matchEntire("no prefix id"))  // space not allowed in wxid/chatroom alt
    }

    // ── R-TOOL-304 / TC-TOOL-304-a: _PHONE_PLATFORMS set ──
    @Test
    fun `_PHONE_PLATFORMS equals signal sms whatsapp`() {
        assertEquals(setOf("signal", "sms", "whatsapp"), _PHONE_PLATFORMS)
    }

    // ── Media extension constants ──
    @Test
    fun `media extension sets are lowercase dot-prefixed`() {
        assertTrue(".jpg" in _IMAGE_EXTS)
        assertTrue(".png" in _IMAGE_EXTS)
        assertTrue(".mp4" in _VIDEO_EXTS)
        assertTrue(".mp3" in _AUDIO_EXTS)
        assertTrue(".opus" in _VOICE_EXTS)
        // Voice overlaps with audio for ogg/opus
        assertTrue(".ogg" in _AUDIO_EXTS && ".ogg" in _VOICE_EXTS)
    }

    // ── _sanitizeErrorText redacts URL secrets ──
    @Test
    fun `_sanitizeErrorText redacts access_token in url`() {
        val s = _sanitizeErrorText("GET https://api.example.com/x?access_token=supersecret&user=me")
        assertTrue("must redact access_token", s.contains("access_token=***"))
        assertFalse(s.contains("supersecret"))
    }

    @Test
    fun `_sanitizeErrorText redacts generic api_key assign`() {
        val s = _sanitizeErrorText("failure: api_key=abc-123-def in logs")
        assertTrue(s.contains("api_key=***"))
        assertFalse(s.contains("abc-123-def"))
    }

    @Test
    fun `_sanitizeErrorText leaves harmless text alone`() {
        val s = _sanitizeErrorText("plain error, no secrets here")
        assertEquals("plain error, no secrets here", s)
    }

    // ── _error wraps with redaction ──
    @Test
    fun `_error includes redacted message`() {
        // _sanitizeErrorText only targets specific named secrets. Use an
        // access_token= pair (matched by _GENERIC_SECRET_ASSIGN_RE).
        val result = _error("access_token=ohno")
        assertEquals("access_token=***", result["error"])
    }

    @Test
    fun `_error preserves non-secret payload verbatim`() {
        val result = _error("plain message")
        assertEquals("plain message", result["error"])
    }

    // ── _telegramRetryDelay: backoff rules ──
    @Test
    fun `_telegramRetryDelay returns null for generic timeout`() {
        val exc = RuntimeException("read timed out")
        assertNull(_telegramRetryDelay(exc, attempt = 1))
    }

    @Test
    fun `_telegramRetryDelay exponential for 429`() {
        val exc = RuntimeException("HTTP 429 too many requests")
        val delay = _telegramRetryDelay(exc, attempt = 2)
        // 2^2 = 4.0
        assertEquals(4.0, delay!!, 0.0001)
    }

    @Test
    fun `_telegramRetryDelay exponential for 502 bad gateway`() {
        val exc = RuntimeException("502 Bad Gateway")
        val delay = _telegramRetryDelay(exc, attempt = 0)
        assertEquals(1.0, delay!!, 0.0001)
    }

    @Test
    fun `_telegramRetryDelay null for unknown error`() {
        val exc = RuntimeException("some unrelated failure")
        assertNull(_telegramRetryDelay(exc, attempt = 1))
    }

    // ── _describeMediaForMirror ──
    @Test
    fun `_describeMediaForMirror returns empty when no files`() {
        assertEquals("", _describeMediaForMirror(null))
        assertEquals("", _describeMediaForMirror(emptyList()))
    }

    @Test
    fun `_describeMediaForMirror single image`() {
        val out = _describeMediaForMirror(listOf("/tmp/photo.jpg" to false))
        assertEquals("[Sent image attachment]", out)
    }

    @Test
    fun `_describeMediaForMirror voice message`() {
        val out = _describeMediaForMirror(listOf("/tmp/note.opus" to true))
        assertEquals("[Sent voice message]", out)
    }

    @Test
    fun `_describeMediaForMirror multiple attachments`() {
        val out = _describeMediaForMirror(listOf(
            "/tmp/a.png" to false,
            "/tmp/b.mp4" to false,
            "/tmp/c.mp3" to false))
        assertEquals("[Sent 3 media attachments]", out)
    }

    @Test
    fun `_describeMediaForMirror falls back to document for unknown ext`() {
        val out = _describeMediaForMirror(listOf("/tmp/file.bin" to false))
        assertEquals("[Sent document attachment]", out)
    }

    // ── SEND_MESSAGE_SCHEMA ──
    @Test
    fun `SEND_MESSAGE_SCHEMA has expected shape`() {
        assertEquals("send_message", SEND_MESSAGE_SCHEMA["name"])
        @Suppress("UNCHECKED_CAST")
        val params = SEND_MESSAGE_SCHEMA["parameters"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val props = params["properties"] as Map<String, Any?>
        assertTrue("action" in props.keys)
        assertTrue("target" in props.keys)
        assertTrue("message" in props.keys)
        @Suppress("UNCHECKED_CAST")
        val actionProp = props["action"] as Map<String, Any?>
        assertEquals(listOf("send", "list"), actionProp["enum"])
    }

    // ── sendMessageTool dispatch to list/send ──
    @Test
    fun `sendMessageTool action list returns targets shape`() {
        val result = sendMessageTool(mapOf("action" to "list"))
        val json = JSONObject(result)
        assertTrue(json.has("targets"))
    }

    @Test
    fun `sendMessageTool default send returns not-available error on Android`() {
        val result = sendMessageTool(mapOf())
        val json = JSONObject(result)
        assertTrue(json.has("error"))
    }

    // ── _maybeSkipCronDuplicateSend (env-free path) ──
    @Test
    fun `_maybeSkipCronDuplicateSend returns null when cron env unset`() {
        // No HERMES_CRON_AUTO_DELIVER_* vars set on the test JVM → returns null.
        val result = _maybeSkipCronDuplicateSend("telegram", "12345", null)
        assertNull(result)
    }

    // ── Discord forum probe cache ──
    @Test
    fun `_rememberChannelIsForum roundtrips via probe cache`() {
        val id = "ch-${System.nanoTime()}"
        assertNull(_probeIsForumCached(id))
        _rememberChannelIsForum(id, true)
        assertEquals(true, _probeIsForumCached(id))
        _rememberChannelIsForum(id, false)
        assertEquals(false, _probeIsForumCached(id))
    }

    // ── _deriveForumThreadName caps at 50 ──
    @Test
    fun `_deriveForumThreadName caps at 50 chars`() {
        val long = "a".repeat(120)
        assertEquals(50, _deriveForumThreadName(long).length)
    }

    @Test
    fun `_deriveForumThreadName keeps short trimmed`() {
        assertEquals("hello", _deriveForumThreadName("  hello  "))
    }

    // ── _parseTargetRef stub returns raw target ──
    @Test
    fun `_parseTargetRef stub echos target with null thread`() {
        val (chat, thread, extra) = _parseTargetRef("telegram", "some-target")
        assertEquals("some-target", chat)
        assertNull(thread)
        assertNull(extra)
    }

    // ── _checkSendMessage default (no platform, not running) ──
    @Test
    fun `_checkSendMessage returns false when no platform and gateway off`() {
        // On pure JVM without Android context, isGatewayRunning either throws
        // or returns false. Either way the helper should resolve to false.
        val result = _checkSendMessage()
        // Tolerant: if user happens to set HERMES_SESSION_PLATFORM != local,
        // the helper may return true. Treat that as a non-default environment
        // and don't assert.
        val platform = System.getProperty("HERMES_SESSION_PLATFORM", "") ?: ""
        if (platform.isEmpty() || platform == "local") {
            assertFalse(result)
        }
    }
}

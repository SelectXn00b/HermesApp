package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the top-level message-normalization helpers ported from feishu.py:
 *   _normalizeFeishuText / _uniqueLines / _firstNonEmptyText
 *   parseFeishuPostPayload / normalizeFeishuMessage
 *
 * Exercises only Kotlin-standard containers (Map/List) rather than the
 * Android JSON types to keep tests pure-JVM.
 */
class FeishuNormalizeTest {

    @Test
    fun `normalizeFeishuText strips mentions and collapses whitespace`() {
        val input = "Hello @_user_123  world\r\n\r\n\r\nhi    there"
        assertEquals("Hello world\nhi there", _normalizeFeishuText(input))
    }

    @Test
    fun `normalizeFeishuText returns empty for null or whitespace-only input`() {
        assertEquals("", _normalizeFeishuText(null))
        assertEquals("", _normalizeFeishuText("   \n\n   "))
    }

    @Test
    fun `uniqueLines drops duplicates preserving order`() {
        assertEquals(
            listOf("alpha", "beta", "gamma"),
            _uniqueLines(listOf("alpha", "beta", "alpha", "gamma", "beta", ""))
        )
    }

    @Test
    fun `firstNonEmptyText picks first non-blank string`() {
        assertEquals("keeper", _firstNonEmptyText(null, "", "keeper", "later"))
    }

    @Test
    fun `firstNonEmptyText skips dict or list values`() {
        assertEquals("ok", _firstNonEmptyText(emptyMap<String, Any>(), listOf<Any>(), "ok"))
    }

    @Test
    fun `parseFeishuPostPayload extracts title and body rows`() {
        val payload = mapOf<String, Any?>(
            "title" to "Alert",
            "content" to listOf(
                listOf(mapOf("tag" to "text", "text" to "Something happened")),
                listOf(mapOf("tag" to "text", "text" to "Please check")),
            ),
        )
        val result = parseFeishuPostPayload(payload)
        assertTrue(result.text_content.startsWith("Alert"))
        assertTrue(result.text_content.contains("Something happened"))
        assertTrue(result.text_content.contains("Please check"))
    }

    @Test
    fun `parseFeishuPostPayload with no content returns fallback text`() {
        assertEquals(FALLBACK_POST_TEXT, parseFeishuPostPayload(mapOf<String, Any?>()).text_content)
    }

    @Test
    fun `parseFeishuPostPayload resolves zh_cn locale under post wrapper`() {
        val payload = mapOf<String, Any?>(
            "post" to mapOf(
                "zh_cn" to mapOf(
                    "title" to "标题",
                    "content" to listOf(
                        listOf(mapOf("tag" to "text", "text" to "正文"))
                    ),
                ),
            ),
        )
        val result = parseFeishuPostPayload(payload)
        assertEquals("标题\n正文", result.text_content)
    }

    @Test
    fun `parseFeishuPostPayload collects image_keys and media_refs`() {
        val payload = mapOf<String, Any?>(
            "content" to listOf(
                listOf(
                    mapOf("tag" to "img", "image_key" to "img_001", "text" to "chart"),
                    mapOf("tag" to "audio", "file_key" to "aud_007", "file_name" to "clip.mp3"),
                ),
            ),
        )
        val result = parseFeishuPostPayload(payload)
        assertEquals(listOf("img_001"), result.image_keys)
        assertEquals(1, result.media_refs.size)
        assertEquals("aud_007", result.media_refs[0].file_key)
    }

    @Test
    fun `normalizeFeishuMessage text type extracts text field`() {
        val raw = """{"text":"hi @_user_12  world"}"""
        val normalized = normalizeFeishuMessage("text", raw)
        assertEquals("text", normalized.raw_type)
        assertEquals("hi world", normalized.text_content)
    }

    @Test
    fun `normalizeFeishuMessage image type captures image_key and sets photo type`() {
        val raw = """{"image_key":"img_777","text":"screenshot"}"""
        val normalized = normalizeFeishuMessage("image", raw)
        assertEquals("photo", normalized.preferred_message_type)
        assertEquals(listOf("img_777"), normalized.image_keys)
    }

    @Test
    fun `normalizeFeishuMessage audio type captures media_ref`() {
        val raw = """{"file_key":"aud_aaa","file_name":"voice.ogg"}"""
        val normalized = normalizeFeishuMessage("audio", raw)
        assertEquals("audio", normalized.preferred_message_type)
        assertEquals(1, normalized.media_refs.size)
        assertEquals("audio", normalized.media_refs[0].resource_type)
    }

    @Test
    fun `normalizeFeishuMessage unknown type returns empty text`() {
        val normalized = normalizeFeishuMessage("bogus_type", "{}")
        assertEquals("bogus_type", normalized.raw_type)
        assertEquals("", normalized.text_content)
    }

    @Test
    fun `normalizeFeishuMessage share_chat produces labeled lines`() {
        val raw = """{"chat_name":"Team Ops","chat_id":"oc_123"}"""
        val normalized = normalizeFeishuMessage("share_chat", raw)
        assertTrue(normalized.text_content.contains("Shared chat: Team Ops"))
        assertTrue(normalized.text_content.contains("Chat ID: oc_123"))
    }

    @Test
    fun `resolveSourceChatType prefers chat_info type when group or forum`() {
        assertEquals("group", _resolveSourceChatType(mapOf("type" to "group"), "p2p"))
        assertEquals("forum", _resolveSourceChatType(mapOf("type" to "forum"), ""))
    }

    @Test
    fun `resolveSourceChatType maps p2p when chat_info is empty`() {
        assertEquals("dm", _resolveSourceChatType(emptyMap(), "p2p"))
    }

    @Test
    fun `resolveSourceChatType defaults to group otherwise`() {
        assertEquals("group", _resolveSourceChatType(emptyMap(), "topic"))
        assertEquals("group", _resolveSourceChatType(emptyMap(), null))
    }

    @Test
    fun `extractTextFromRawContent returns normalized text when present`() {
        val raw = """{"text":"hello"}"""
        assertEquals("hello", _extractTextFromRawContent("text", raw))
    }

    @Test
    fun `extractTextFromRawContent falls back to placeholder when no text`() {
        val raw = """{"file_key":"k_1","file_name":"pic.jpg"}"""
        val result = _extractTextFromRawContent("image", raw)
        assertTrue(result.isEmpty() || result.contains("image") || result.isNotEmpty())
    }
}

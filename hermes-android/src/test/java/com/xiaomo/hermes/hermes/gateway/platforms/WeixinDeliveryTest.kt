package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the Weixin protocol helpers ported from weixin.py:
 *   _baseInfo / _headers / _assertWeixinCdnUrl / _mediaReference / _coerceBool
 *   _splitDeliveryUnitsForWeixin / _shouldSplitShortChatBlockForWeixin
 *   _packMarkdownBlocksForWeixin / _splitTextForWeixinDelivery
 */
class WeixinDeliveryTest {

    @Test
    fun `baseInfo carries channel_version`() {
        assertEquals(CHANNEL_VERSION, _baseInfo()["channel_version"])
    }

    @Test
    fun `headers include protocol constants and uin`() {
        val body = """{"hello":"world"}"""
        val h = _headers("abc", body)
        assertEquals("application/json", h["Content-Type"])
        assertEquals("ilink_bot_token", h["AuthorizationType"])
        assertEquals(body.toByteArray(Charsets.UTF_8).size.toString(), h["Content-Length"])
        assertEquals(ILINK_APP_ID, h["iLink-App-Id"])
        assertEquals(ILINK_APP_CLIENT_VERSION.toString(), h["iLink-App-ClientVersion"])
        assertEquals("Bearer abc", h["Authorization"])
        assertTrue(h.containsKey("X-WECHAT-UIN"))
    }

    @Test
    fun `headers omit Authorization when token is empty or null`() {
        assertFalse(_headers(null, "{}").containsKey("Authorization"))
        assertFalse(_headers("", "{}").containsKey("Authorization"))
    }

    @Test
    fun `assertWeixinCdnUrl accepts allow-listed hosts`() {
        _assertWeixinCdnUrl("https://novac2c.cdn.weixin.qq.com/x")
        _assertWeixinCdnUrl("https://ilinkai.weixin.qq.com/path")
        _assertWeixinCdnUrl("http://mmbiz.qpic.cn/pic")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `assertWeixinCdnUrl rejects non-allowlisted host`() {
        _assertWeixinCdnUrl("https://evil.example.com/pwn")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `assertWeixinCdnUrl rejects non-http schemes`() {
        _assertWeixinCdnUrl("file:///etc/passwd")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `assertWeixinCdnUrl rejects unparseable URLs`() {
        _assertWeixinCdnUrl("::::not-a-url")
    }

    @Test
    fun `mediaReference extracts nested media dict`() {
        val item = mapOf<String, Any?>(
            "image_item" to mapOf<String, Any?>(
                "media" to mapOf<String, Any?>("key" to "abc", "size" to 42),
            ),
        )
        val ref = _mediaReference(item, "image_item")
        assertEquals("abc", ref["key"])
        assertEquals(42, ref["size"])
    }

    @Test
    fun `mediaReference returns empty map when key missing`() {
        assertTrue(_mediaReference(emptyMap(), "image_item").isEmpty())
        assertTrue(_mediaReference(mapOf("image_item" to null), "image_item").isEmpty())
        assertTrue(
            _mediaReference(mapOf("image_item" to mapOf("other" to "x")), "image_item").isEmpty()
        )
    }

    @Test
    fun `coerceBool accepts bool passthrough`() {
        assertTrue(_coerceBool(true))
        assertFalse(_coerceBool(false))
    }

    @Test
    fun `coerceBool maps strings`() {
        assertTrue(_coerceBool("true"))
        assertTrue(_coerceBool("YES"))
        assertTrue(_coerceBool("1"))
        assertTrue(_coerceBool("on"))
        assertFalse(_coerceBool("false"))
        assertFalse(_coerceBool("no"))
        assertFalse(_coerceBool("0"))
        assertFalse(_coerceBool("off"))
    }

    @Test
    fun `coerceBool maps numbers`() {
        assertTrue(_coerceBool(1))
        assertTrue(_coerceBool(42))
        assertFalse(_coerceBool(0))
        assertFalse(_coerceBool(0.0))
    }

    @Test
    fun `coerceBool falls back to default on null or unknown`() {
        assertTrue(_coerceBool(null, default = true))
        assertFalse(_coerceBool(null, default = false))
        assertTrue(_coerceBool("maybe", default = true))
        assertFalse(_coerceBool("", default = false))
    }

    @Test
    fun `splitDeliveryUnitsForWeixin keeps code fences intact`() {
        val content = "pre\n\n```kotlin\nval x = 1\nval y = 2\n```\n\npost"
        val units = _splitDeliveryUnitsForWeixin(content)
        assertEquals(3, units.size)
        assertEquals("pre", units[0])
        assertTrue(units[1].startsWith("```"))
        assertTrue(units[1].endsWith("```"))
        assertEquals("post", units[2])
    }

    @Test
    fun `splitDeliveryUnitsForWeixin separates top-level lines`() {
        val content = "line1\nline2\nline3"
        val units = _splitDeliveryUnitsForWeixin(content)
        assertEquals(listOf("line1", "line2", "line3"), units)
    }

    @Test
    fun `splitDeliveryUnitsForWeixin attaches indented continuation`() {
        val content = "- item\n  continuation\n- another"
        val units = _splitDeliveryUnitsForWeixin(content)
        assertEquals(2, units.size)
        assertTrue(units[0].contains("- item"))
        assertTrue(units[0].contains("continuation"))
        assertEquals("- another", units[1])
    }

    @Test
    fun `shouldSplitShortChatBlockForWeixin detects 2-6 chatty lines`() {
        assertTrue(_shouldSplitShortChatBlockForWeixin("hi there\nhow are you"))
        assertTrue(_shouldSplitShortChatBlockForWeixin("a\nb\nc"))
    }

    @Test
    fun `shouldSplitShortChatBlockForWeixin rejects single line`() {
        assertFalse(_shouldSplitShortChatBlockForWeixin("just one line"))
    }

    @Test
    fun `shouldSplitShortChatBlockForWeixin rejects too many lines`() {
        val long = (1..7).joinToString("\n") { "line$it" }
        assertFalse(_shouldSplitShortChatBlockForWeixin(long))
    }

    @Test
    fun `shouldSplitShortChatBlockForWeixin rejects heading start`() {
        assertFalse(_shouldSplitShortChatBlockForWeixin("# Title\nbody line"))
        assertFalse(_shouldSplitShortChatBlockForWeixin("Section:\nbody line"))
    }

    @Test
    fun `packMarkdownBlocksForWeixin keeps short content as one chunk`() {
        assertEquals(listOf("short"), _packMarkdownBlocksForWeixin("short", 100))
    }

    @Test
    fun `packMarkdownBlocksForWeixin splits oversize across blocks`() {
        val b1 = "a".repeat(50)
        val b2 = "b".repeat(50)
        val b3 = "c".repeat(50)
        val content = "$b1\n\n$b2\n\n$b3"
        val result = _packMarkdownBlocksForWeixin(content, 60)
        assertTrue(result.size >= 2)
        assertTrue(result.joinToString("") .contains(b1))
        assertTrue(result.joinToString("") .contains(b3))
    }

    @Test
    fun `splitTextForWeixinDelivery compact mode keeps content under limit`() {
        assertEquals(
            listOf("short content"),
            _splitTextForWeixinDelivery("short content", maxLength = 100, splitPerLine = false)
        )
    }

    @Test
    fun `splitTextForWeixinDelivery compact mode splits chatty blocks`() {
        val chat = "hi\nhow are you\nfine thanks"
        val result = _splitTextForWeixinDelivery(chat, maxLength = 100, splitPerLine = false)
        assertEquals(3, result.size)
    }

    @Test
    fun `splitTextForWeixinDelivery per-line mode splits top-level lines`() {
        val result = _splitTextForWeixinDelivery(
            "line1\nline2\nline3", maxLength = 100, splitPerLine = true,
        )
        assertEquals(listOf("line1", "line2", "line3"), result)
    }

    @Test
    fun `splitTextForWeixinDelivery per-line mode keeps single-line content`() {
        assertEquals(
            listOf("only line"),
            _splitTextForWeixinDelivery("only line", maxLength = 100, splitPerLine = true)
        )
    }

    @Test
    fun `splitTextForWeixinDelivery empty input`() {
        assertEquals(emptyList<String>(), _splitTextForWeixinDelivery("", 100, false))
    }
}

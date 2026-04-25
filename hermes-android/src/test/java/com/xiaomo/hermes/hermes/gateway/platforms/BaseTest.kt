package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the pure-logic subset of Base.kt — top-level utility functions
 * ported from gateway/platforms/base.py that have no Android Context
 * dependency:
 *
 *   utf16Len / prefixWithinUtf16Limit — LLM byte-count math
 *   safeUrlForLog — credential masking
 *   looksLikeImage — magic-byte sniff
 *   isAnimationUrl / isRetryableError / isTimeoutError — classifier predicates
 *   mergeCaption / truncateMessage / formatMessage — text helpers
 *   extractImages / extractMedia / extractLocalFiles — response extractors
 *   formatDuration — seconds → h/m/s string
 *   _customUnitToCp — binary-search codepoint finder
 *   _detectMacosSystemProxy — null on Android
 *   proxyKwargsForBot / proxyKwargsForAiohttp — kwargs builder
 *
 * File-backed cache helpers (cacheImageFromBytes, getImageCacheDir etc.)
 * need Context.cacheDir and are covered separately under Robolectric.
 */
class BaseTest {

    // ─── utf16Len / prefixWithinUtf16Limit ────────────────────────────────

    @Test
    fun `utf16Len counts BMP as 1 and supplementary as 2`() {
        assertEquals(5, utf16Len("hello"))
        // "𠮷" is U+20BB7, a supplementary char encoded as a surrogate pair.
        // As two Char values in Kotlin, each char is in the BMP range, so
        // the fold returns 2 even though the codepoint is supplementary.
        assertEquals(2, utf16Len("𠮷"))
        assertEquals(4, utf16Len("ab𠮷"))
    }

    @Test
    fun `utf16Len empty is zero`() {
        assertEquals(0, utf16Len(""))
    }

    @Test
    fun `prefixWithinUtf16Limit trims to budget`() {
        assertEquals("hel", prefixWithinUtf16Limit("hello", 3))
        assertEquals("hello", prefixWithinUtf16Limit("hello", 10))
        assertEquals("", prefixWithinUtf16Limit("hello", 0))
    }

    // ─── safeUrlForLog ────────────────────────────────────────────────────

    @Test
    fun `safeUrlForLog masks userinfo credentials`() {
        val out = safeUrlForLog("https://user:pass@api.example.com/path")
        assertEquals("https://***@api.example.com/path", out)
    }

    @Test
    fun `safeUrlForLog truncates long URLs with ellipsis`() {
        val long = "https://example.com/" + "x".repeat(200)
        val out = safeUrlForLog(long, maxLength = 30)
        assertEquals(30, out.length)
        assertTrue(out, out.endsWith("..."))
    }

    @Test
    fun `safeUrlForLog passes short URLs through`() {
        assertEquals("https://example.com", safeUrlForLog("https://example.com"))
    }

    // ─── looksLikeImage ───────────────────────────────────────────────────

    @Test
    fun `looksLikeImage detects JPEG magic bytes`() {
        assertTrue(looksLikeImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)))
    }

    @Test
    fun `looksLikeImage detects PNG magic bytes`() {
        assertTrue(looksLikeImage(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x00, 0x00)))
    }

    @Test
    fun `looksLikeImage detects GIF magic bytes`() {
        assertTrue(looksLikeImage(byteArrayOf(0x47.toByte(), 0x49.toByte(), 0x00, 0x00)))
    }

    @Test
    fun `looksLikeImage returns false on small or non-image data`() {
        assertFalse(looksLikeImage(byteArrayOf()))
        assertFalse(looksLikeImage(byteArrayOf(0x01, 0x02)))
        assertFalse(looksLikeImage(byteArrayOf(0x00, 0x00, 0x00, 0x00)))
    }

    // ─── isAnimationUrl ───────────────────────────────────────────────────

    @Test
    fun `isAnimationUrl detects gif extension`() {
        assertTrue(isAnimationUrl("https://example.com/x.gif"))
        assertTrue(isAnimationUrl("https://example.com/x.GIF"))
    }

    @Test
    fun `isAnimationUrl detects gif hosts`() {
        assertTrue(isAnimationUrl("https://tenor.com/view/xyz"))
        assertTrue(isAnimationUrl("https://media.giphy.com/media/abc"))
        assertTrue(isAnimationUrl("https://cdn.x.com/animated/foo"))
    }

    @Test
    fun `isAnimationUrl returns false on non-gif urls`() {
        assertFalse(isAnimationUrl("https://example.com/photo.jpg"))
        assertFalse(isAnimationUrl("https://example.com/video.mp4"))
    }

    // ─── isRetryableError / isTimeoutError ────────────────────────────────

    @Test
    fun `isRetryableError matches transient signals`() {
        assertTrue(isRetryableError("Connection reset"))
        assertTrue(isRetryableError("Read timeout after 30s"))
        assertTrue(isRetryableError("Rate limit exceeded"))
        assertTrue(isRetryableError("HTTP 429 Too Many Requests"))
        assertTrue(isRetryableError("503 Service Unavailable"))
        assertTrue(isRetryableError("500 Internal Server Error"))
    }

    @Test
    fun `isRetryableError false on non-retryable errors`() {
        assertFalse(isRetryableError("401 Unauthorized"))
        assertFalse(isRetryableError("404 Not Found"))
        assertFalse(isRetryableError("Malformed request"))
    }

    @Test
    fun `isTimeoutError matches timeout signals`() {
        assertTrue(isTimeoutError("Read timed out"))
        assertTrue(isTimeoutError("Connect timed out"))
        assertTrue(isTimeoutError("request timeout"))
        assertFalse(isTimeoutError("403 Forbidden"))
    }

    // ─── mergeCaption ─────────────────────────────────────────────────────

    @Test
    fun `mergeCaption returns new when existing is blank`() {
        assertEquals("new", mergeCaption("", "new"))
        assertEquals("new", mergeCaption("   ", "new"))
    }

    @Test
    fun `mergeCaption returns existing when new is blank`() {
        assertEquals("old", mergeCaption("old", ""))
        assertEquals("old", mergeCaption("old", "   "))
    }

    @Test
    fun `mergeCaption skips when new is already substring of existing`() {
        assertEquals("Hello world", mergeCaption("Hello world", "hello"))
    }

    @Test
    fun `mergeCaption joins with double newline`() {
        assertEquals("first\n\nsecond", mergeCaption("first", "second"))
    }

    // ─── truncateMessage ──────────────────────────────────────────────────

    @Test
    fun `truncateMessage returns single chunk under limit`() {
        assertEquals(listOf("hi"), truncateMessage("hi", maxLength = 100))
    }

    @Test
    fun `truncateMessage splits long text into multiple chunks`() {
        val content = "x".repeat(250)
        val chunks = truncateMessage(content, maxLength = 100)
        assertTrue("expected at least 3 chunks, got ${chunks.size}", chunks.size >= 3)
        // Joining chunks yields the original content
        assertEquals(content, chunks.joinToString(""))
    }

    @Test
    fun `truncateMessage prefers newline boundary when past halfway`() {
        // Content has a newline at position 60 and maxLength is 100; since
        // 60 > 100/2 = 50 the split should happen at position 61 (after \n).
        val content = "a".repeat(60) + "\n" + "b".repeat(100)
        val chunks = truncateMessage(content, maxLength = 100)
        assertTrue(chunks.first().endsWith("\n"))
        assertEquals(61, chunks.first().length)
    }

    // ─── formatMessage ────────────────────────────────────────────────────

    @Test
    fun `formatMessage passes through unchanged`() {
        assertEquals("hello", formatMessage("hello"))
        assertEquals("", formatMessage(""))
    }

    // ─── extractImages ────────────────────────────────────────────────────

    @Test
    fun `extractImages finds markdown image url`() {
        val out = extractImages("look ![cat](https://x.com/cat.jpg) here")
        assertEquals(listOf("https://x.com/cat.jpg"), out)
    }

    @Test
    fun `extractImages finds html img src`() {
        val out = extractImages("""<img src="https://x.com/a.png" alt="a">""")
        assertEquals(listOf("https://x.com/a.png"), out)
    }

    @Test
    fun `extractImages finds bare image extension urls`() {
        val out = extractImages("link: https://x.com/photo.jpeg trailing")
        assertEquals(listOf("https://x.com/photo.jpeg"), out)
    }

    @Test
    fun `extractImages dedupes repeats`() {
        val url = "https://x.com/a.png"
        val out = extractImages("![a]($url) and $url also $url")
        assertEquals(listOf(url), out)
    }

    // ─── extractMedia ─────────────────────────────────────────────────────

    @Test
    fun `extractMedia pulls MEDIA tokens and leaves text`() {
        val (text, paths, voice) = extractMedia("say hi MEDIA:/tmp/a.mp3 and bye")
        assertFalse(voice)
        assertEquals(listOf("/tmp/a.mp3"), paths)
        assertEquals("say hi  and bye", text)
    }

    @Test
    fun `extractMedia flags audio_as_voice`() {
        val (text, _, voice) = extractMedia("hello [[audio_as_voice]]")
        assertTrue(voice)
        assertEquals("hello", text)
    }

    @Test
    fun `extractMedia returns empty when no tags`() {
        val (text, paths, voice) = extractMedia("plain text")
        assertEquals("plain text", text)
        assertTrue(paths.isEmpty())
        assertFalse(voice)
    }

    // ─── extractLocalFiles ────────────────────────────────────────────────

    @Test
    fun `extractLocalFiles finds absolute paths with extension`() {
        val out = extractLocalFiles("open /tmp/log.txt now")
        assertEquals(listOf("/tmp/log.txt"), out)
    }

    @Test
    fun `extractLocalFiles finds tilde paths`() {
        val out = extractLocalFiles("see ~/.config/x.json for details")
        assertEquals(listOf("~/.config/x.json"), out)
    }

    @Test
    fun `extractLocalFiles empty when no paths`() {
        assertTrue(extractLocalFiles("just plain prose").isEmpty())
    }

    // ─── formatDuration ───────────────────────────────────────────────────

    @Test
    fun `formatDuration seconds under a minute`() {
        assertEquals("0s", formatDuration(0))
        assertEquals("45s", formatDuration(45))
    }

    @Test
    fun `formatDuration minutes and seconds`() {
        assertEquals("1m 0s", formatDuration(60))
        assertEquals("2m 30s", formatDuration(150))
    }

    @Test
    fun `formatDuration hours and minutes`() {
        assertEquals("1h 0m", formatDuration(3600))
        assertEquals("2h 30m", formatDuration(9000))
    }

    @Test
    fun `formatDuration days and hours`() {
        assertEquals("1d 0h", formatDuration(86400))
        assertEquals("3d 12h", formatDuration(3 * 86400 + 12 * 3600))
    }

    // ─── _customUnitToCp ──────────────────────────────────────────────────

    @Test
    fun `customUnitToCp returns full length when budget fits`() {
        assertEquals(5, _customUnitToCp("hello", budget = 10) { it.length })
    }

    @Test
    fun `customUnitToCp binary-searches to exact budget`() {
        assertEquals(3, _customUnitToCp("abcdef", budget = 3) { it.length })
    }

    @Test
    fun `customUnitToCp returns 0 when nothing fits`() {
        // budget=0 and non-empty string → returns 0 (prefix "" fits budget=0)
        assertEquals(0, _customUnitToCp("abc", budget = 0) { it.length })
    }

    // ─── proxy helpers ────────────────────────────────────────────────────

    @Test
    fun `detectMacosSystemProxy returns null on Android`() {
        assertNull(_detectMacosSystemProxy())
    }

    @Test
    fun `proxyKwargsForBot empty on null or blank`() {
        assertTrue(proxyKwargsForBot(null).isEmpty())
        assertTrue(proxyKwargsForBot("").isEmpty())
    }

    @Test
    fun `proxyKwargsForBot uses socks_proxy for socks url`() {
        val out = proxyKwargsForBot("socks5://127.0.0.1:1080")
        assertEquals("socks5://127.0.0.1:1080", out["socks_proxy"])
        assertFalse(out.containsKey("proxy"))
    }

    @Test
    fun `proxyKwargsForBot uses proxy for http url`() {
        val out = proxyKwargsForBot("http://proxy.local:3128")
        assertEquals("http://proxy.local:3128", out["proxy"])
        assertFalse(out.containsKey("socks_proxy"))
    }

    @Test
    fun `proxyKwargsForAiohttp empty on null or blank`() {
        val (session, request) = proxyKwargsForAiohttp(null)
        assertTrue(session.isEmpty())
        assertTrue(request.isEmpty())
    }

    @Test
    fun `proxyKwargsForAiohttp socks goes to session kwargs`() {
        val (session, request) = proxyKwargsForAiohttp("socks5://x:1080")
        assertEquals("socks5://x:1080", session["socks_proxy"])
        assertTrue(request.isEmpty())
    }

    @Test
    fun `proxyKwargsForAiohttp http goes to request kwargs`() {
        val (session, request) = proxyKwargsForAiohttp("http://x:3128")
        assertTrue(session.isEmpty())
        assertEquals("http://x:3128", request["proxy"])
    }

    // ─── pending interrupts ───────────────────────────────────────────────

    @Test
    fun `hasPendingInterrupt false for unseen session`() {
        assertFalse(hasPendingInterrupt("never-seen-${System.nanoTime()}"))
    }

    @Test
    fun `getPendingMessage returns null for unseen session`() {
        assertNull(getPendingMessage("never-seen-${System.nanoTime()}"))
    }
}

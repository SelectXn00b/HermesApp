package com.xiaomo.hermes.hermes.gateway.platforms.qqbot

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the pure-logic subset of qqbot/Utils.kt:
 *   buildUserAgent — deterministic UA shape (Android SDK_INT is 0 under JVM stub)
 *   getApiHeaders — fixed keys + UA included
 *   coerceList — null / String CSV / Collection / fallback toString
 *
 * The real HTTPS-backed helpers (Onboard.createBindTask, pollBindResult) need
 * live sockets and are deferred to integration tests.
 */
class UtilsTest {

    // ─── buildUserAgent ───────────────────────────────────────────────────

    @Test
    fun `buildUserAgent follows QQBotAdapter slash version slash Android shape`() {
        val ua = buildUserAgent()
        // Shape: "QQBotAdapter/1.0 (Android/<sdk>; android; Hermes/<ver>)"
        assertTrue(ua, ua.startsWith("QQBotAdapter/1.0 (Android/"))
        assertTrue(ua, ua.contains("; android; Hermes/"))
        assertTrue(ua, ua.endsWith(")"))
    }

    // ─── getApiHeaders ────────────────────────────────────────────────────

    @Test
    fun `getApiHeaders contains expected keys`() {
        val headers = getApiHeaders()
        assertEquals("application/json", headers["Content-Type"])
        assertEquals("application/json", headers["Accept"])
        assertNotNull(headers["User-Agent"])
        assertTrue(headers["User-Agent"]!!.startsWith("QQBotAdapter/"))
    }

    // ─── coerceList ───────────────────────────────────────────────────────

    @Test
    fun `coerceList null returns empty`() {
        assertEquals(emptyList<String>(), coerceList(null))
    }

    @Test
    fun `coerceList string splits csv and trims`() {
        assertEquals(listOf("a", "b", "c"), coerceList("a, b ,c"))
    }

    @Test
    fun `coerceList string drops empty segments`() {
        assertEquals(listOf("x", "y"), coerceList("x, ,,y,"))
    }

    @Test
    fun `coerceList string with only commas returns empty`() {
        assertEquals(emptyList<String>(), coerceList(",,,"))
        assertEquals(emptyList<String>(), coerceList(""))
    }

    @Test
    fun `coerceList string with single entry returns singleton`() {
        assertEquals(listOf("only"), coerceList("only"))
    }

    @Test
    fun `coerceList collection maps and trims`() {
        assertEquals(listOf("a", "b"), coerceList(listOf("a", " b ")))
    }

    @Test
    fun `coerceList collection drops nulls and blanks`() {
        val src = listOf("keep", null, "   ", "also")
        assertEquals(listOf("keep", "also"), coerceList(src))
    }

    @Test
    fun `coerceList collection stringifies non-strings`() {
        assertEquals(listOf("1", "2", "3"), coerceList(listOf(1, 2, 3)))
    }

    @Test
    fun `coerceList other value toStrings and returns singleton`() {
        assertEquals(listOf("42"), coerceList(42))
        assertEquals(listOf("true"), coerceList(true))
    }

    @Test
    fun `coerceList blank other value returns empty`() {
        // An object whose toString() is only whitespace → singleton empty is
        // normalized away, yielding emptyList.
        val blank = object {
            override fun toString(): String = "   "
        }
        assertEquals(emptyList<String>(), coerceList(blank))
    }
}

package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the four remaining pure-logic Feishu module helpers ported from
 * gateway/platforms/feishu.py:
 *   _stripMarkdownToPlainText (feishu.py 410)
 *   _coerceInt                 (feishu.py 428)
 *   _coerceRequiredInt         (feishu.py 437)
 *   checkFeishuRequirements    (feishu.py 1095)
 */
class FeishuCoercionTest {

    // ─── _stripMarkdownToPlainText ────────────────────────────────────────

    @Test
    fun `stripMarkdown normalises CRLF to LF`() {
        assertEquals("line1\nline2", _stripMarkdownToPlainText("line1\r\nline2"))
    }

    @Test
    fun `stripMarkdown expands links to text plus parenthesised url`() {
        val out = _stripMarkdownToPlainText("see [docs](https://example.com/doc)")
        assertTrue(out, out.contains("docs (https://example.com/doc)"))
    }

    @Test
    fun `stripMarkdown removes blockquote marker at line start`() {
        assertEquals("quoted line", _stripMarkdownToPlainText("> quoted line"))
    }

    @Test
    fun `stripMarkdown collapses horizontal rule variants to triple-dash`() {
        val out = _stripMarkdownToPlainText("a\n-----\nb")
        assertTrue(out, out.contains("---"))
    }

    @Test
    fun `stripMarkdown removes strikethrough`() {
        val out = _stripMarkdownToPlainText("~~gone~~ stays")
        assertTrue(out, out.contains("gone"))
        assertFalse(out, out.contains("~~"))
    }

    @Test
    fun `stripMarkdown removes underline tags`() {
        val out = _stripMarkdownToPlainText("<u>emphasised</u> text")
        assertTrue(out, out.contains("emphasised"))
        assertFalse(out, out.contains("<u>"))
        assertFalse(out, out.contains("</u>"))
    }

    @Test
    fun `stripMarkdown delegates bold and inline code to stripMarkdown helper`() {
        val out = _stripMarkdownToPlainText("**bold** and `code`")
        assertTrue(out, out.contains("bold"))
        assertTrue(out, out.contains("code"))
    }

    // ─── _coerceInt ────────────────────────────────────────────────────────

    @Test
    fun `coerceInt passes through Int`() {
        assertEquals(42, _coerceInt(42))
        assertEquals(0, _coerceInt(0))
    }

    @Test
    fun `coerceInt parses Long and Number`() {
        assertEquals(5, _coerceInt(5L))
        assertEquals(3, _coerceInt(3.7)) // truncate
    }

    @Test
    fun `coerceInt parses string digits`() {
        assertEquals(7, _coerceInt("7"))
        assertEquals(10, _coerceInt(" 10 "))
    }

    @Test
    fun `coerceInt returns default on non-numeric string`() {
        assertEquals(99, _coerceInt("abc", default = 99))
        assertNull(_coerceInt("abc"))
    }

    @Test
    fun `coerceInt returns default on null`() {
        assertNull(_coerceInt(null))
        assertEquals(42, _coerceInt(null, default = 42))
    }

    @Test
    fun `coerceInt enforces minValue by returning default`() {
        assertEquals(5, _coerceInt(-1, default = 5, minValue = 0))
        assertEquals(10, _coerceInt(3, default = 10, minValue = 5))
    }

    @Test
    fun `coerceInt accepts value equal to minValue`() {
        assertEquals(5, _coerceInt(5, default = 0, minValue = 5))
    }

    @Test
    fun `coerceInt rejects NaN and Infinity`() {
        assertEquals(0, _coerceInt(Double.NaN, default = 0))
        assertEquals(0, _coerceInt(Double.POSITIVE_INFINITY, default = 0))
    }

    // ─── _coerceRequiredInt ────────────────────────────────────────────────

    @Test
    fun `coerceRequiredInt never returns null`() {
        assertEquals(42, _coerceRequiredInt("junk", default = 42))
        assertEquals(42, _coerceRequiredInt(null, default = 42))
        assertEquals(7, _coerceRequiredInt(7, default = 42))
    }

    @Test
    fun `coerceRequiredInt enforces minValue`() {
        assertEquals(10, _coerceRequiredInt(-5, default = 10, minValue = 0))
    }

    // ─── checkFeishuRequirements ───────────────────────────────────────────

    @Test
    fun `checkFeishuRequirements mirrors FEISHU_AVAILABLE`() {
        assertEquals(FEISHU_AVAILABLE, checkFeishuRequirements())
        // On Android this is false (SDK can't be dex'd).
        assertFalse(checkFeishuRequirements())
    }
}

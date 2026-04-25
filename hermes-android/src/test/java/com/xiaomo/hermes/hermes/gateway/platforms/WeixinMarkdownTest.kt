package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the Weixin Markdown helpers ported from weixin.py 629-828:
 *   _mimeFromFilename / _splitTableRow / _rewriteHeadersForWeixin
 *   _rewriteTableBlockForWeixin / _normalizeMarkdownBlocks / _splitMarkdownBlocks
 *   _looksLikeChattyLineForWeixin / _looksLikeHeadingLineForWeixin
 */
class WeixinMarkdownTest {

    @Test
    fun `mimeFromFilename maps common extensions`() {
        assertEquals("application/pdf", _mimeFromFilename("report.pdf"))
        assertEquals("text/markdown", _mimeFromFilename("readme.md"))
        assertEquals("image/jpeg", _mimeFromFilename("photo.JPG"))
        assertEquals("image/png", _mimeFromFilename("pic.png"))
        assertEquals("video/mp4", _mimeFromFilename("clip.mp4"))
        assertEquals("audio/mpeg", _mimeFromFilename("song.mp3"))
    }

    @Test
    fun `mimeFromFilename falls back to octet-stream`() {
        assertEquals("application/octet-stream", _mimeFromFilename("weird.xyz"))
        assertEquals("application/octet-stream", _mimeFromFilename(null))
        assertEquals("application/octet-stream", _mimeFromFilename(""))
        assertEquals("application/octet-stream", _mimeFromFilename("noextension"))
    }

    @Test
    fun `splitTableRow strips leading and trailing pipes`() {
        assertEquals(listOf("a", "b", "c"), _splitTableRow("| a | b | c |"))
        assertEquals(listOf("a", "b", "c"), _splitTableRow("a | b | c"))
        assertEquals(listOf("cell"), _splitTableRow("| cell |"))
    }

    @Test
    fun `splitTableRow trims cells`() {
        assertEquals(listOf("foo", "bar"), _splitTableRow("|   foo   |   bar   |"))
    }

    @Test
    fun `rewriteHeadersForWeixin converts H1 to bracket form`() {
        assertEquals("【Title】", _rewriteHeadersForWeixin("# Title"))
    }

    @Test
    fun `rewriteHeadersForWeixin converts H2-H6 to bold`() {
        assertEquals("**Sub**", _rewriteHeadersForWeixin("## Sub"))
        assertEquals("**Third**", _rewriteHeadersForWeixin("### Third"))
        assertEquals("**Sixth**", _rewriteHeadersForWeixin("###### Sixth"))
    }

    @Test
    fun `rewriteHeadersForWeixin leaves non-headers untouched`() {
        assertEquals("regular line", _rewriteHeadersForWeixin("regular line"))
        assertEquals("- bullet", _rewriteHeadersForWeixin("- bullet"))
    }

    @Test
    fun `rewriteTableBlockForWeixin flattens a 2-column table to bullets`() {
        val lines = listOf(
            "| Name | Value |",
            "|------|-------|",
            "| Alice | 42 |",
            "| Bob   | 7  |",
        )
        val result = _rewriteTableBlockForWeixin(lines)
        assertTrue(result.contains("- Name: Alice"))
        assertTrue(result.contains("  Value: 42"))
        assertTrue(result.contains("- Name: Bob"))
        assertTrue(result.contains("  Value: 7"))
    }

    @Test
    fun `rewriteTableBlockForWeixin handles single-column tables`() {
        val lines = listOf(
            "| Item |",
            "|------|",
            "| Alpha |",
            "| Beta |",
        )
        val result = _rewriteTableBlockForWeixin(lines)
        assertTrue(result.contains("- Item: Alpha"))
        assertTrue(result.contains("- Item: Beta"))
    }

    @Test
    fun `rewriteTableBlockForWeixin handles 3+ columns as summary`() {
        val lines = listOf(
            "| A | B | C |",
            "|---|---|---|",
            "| 1 | 2 | 3 |",
        )
        val result = _rewriteTableBlockForWeixin(lines)
        assertTrue(result.contains("A: 1"))
        assertTrue(result.contains("B: 2"))
        assertTrue(result.contains("C: 3"))
        assertTrue(result.contains("|"))
    }

    @Test
    fun `rewriteTableBlockForWeixin returns input unchanged when too few rows`() {
        val lines = listOf("| Only Header |")
        assertEquals("| Only Header |", _rewriteTableBlockForWeixin(lines))
    }

    @Test
    fun `normalizeMarkdownBlocks collapses blank runs`() {
        val input = "line1\n\n\n\nline2\n\n\nline3"
        val result = _normalizeMarkdownBlocks(input)
        assertEquals("line1\n\nline2\n\nline3", result)
    }

    @Test
    fun `normalizeMarkdownBlocks preserves code fences`() {
        val input = "para1\n\n```kotlin\nval x = 1\n\n\nval y = 2\n```\n\npara2"
        val result = _normalizeMarkdownBlocks(input)
        // Inside the fence, the blank lines should be preserved as-is
        assertTrue(result.contains("val x = 1\n\n\nval y = 2"))
    }

    @Test
    fun `splitMarkdownBlocks separates by blank lines`() {
        val input = "block1 line1\nblock1 line2\n\nblock2 line1\n\nblock3"
        val blocks = _splitMarkdownBlocks(input)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0].startsWith("block1"))
        assertTrue(blocks[1].startsWith("block2"))
        assertEquals("block3", blocks[2])
    }

    @Test
    fun `splitMarkdownBlocks keeps fenced code intact`() {
        val input = "intro\n\n```python\nprint('hi')\n\nprint('bye')\n```\n\noutro"
        val blocks = _splitMarkdownBlocks(input)
        assertEquals(3, blocks.size)
        assertEquals("intro", blocks[0])
        assertTrue(blocks[1].startsWith("```"))
        assertTrue(blocks[1].endsWith("```"))
        assertTrue(blocks[1].contains("print('hi')"))
        assertTrue(blocks[1].contains("print('bye')"))
        assertEquals("outro", blocks[2])
    }

    @Test
    fun `splitMarkdownBlocks returns empty list for empty input`() {
        assertEquals(emptyList<String>(), _splitMarkdownBlocks(""))
    }

    @Test
    fun `looksLikeChattyLineForWeixin accepts short prose`() {
        assertTrue(_looksLikeChattyLineForWeixin("Hello there"))
        assertTrue(_looksLikeChattyLineForWeixin("Quick update"))
    }

    @Test
    fun `looksLikeChattyLineForWeixin rejects long lines`() {
        val long = "a".repeat(49)
        assertFalse(_looksLikeChattyLineForWeixin(long))
    }

    @Test
    fun `looksLikeChattyLineForWeixin rejects markdown-ish leads`() {
        assertFalse(_looksLikeChattyLineForWeixin("- bullet"))
        assertFalse(_looksLikeChattyLineForWeixin("* star"))
        assertFalse(_looksLikeChattyLineForWeixin("> quote"))
        assertFalse(_looksLikeChattyLineForWeixin("# header"))
        assertFalse(_looksLikeChattyLineForWeixin("| table |"))
        assertFalse(_looksLikeChattyLineForWeixin("【section】"))
    }

    @Test
    fun `looksLikeChattyLineForWeixin rejects indented lines`() {
        assertFalse(_looksLikeChattyLineForWeixin("  indented"))
        assertFalse(_looksLikeChattyLineForWeixin("\ttabbed"))
    }

    @Test
    fun `looksLikeChattyLineForWeixin rejects numbered lists and bold-only`() {
        assertFalse(_looksLikeChattyLineForWeixin("1. item"))
        assertFalse(_looksLikeChattyLineForWeixin("**bold only**"))
    }

    @Test
    fun `looksLikeChattyLineForWeixin rejects empty`() {
        assertFalse(_looksLikeChattyLineForWeixin(""))
        assertFalse(_looksLikeChattyLineForWeixin("   "))
    }

    @Test
    fun `looksLikeHeadingLineForWeixin detects hash headers`() {
        assertTrue(_looksLikeHeadingLineForWeixin("# Title"))
        assertTrue(_looksLikeHeadingLineForWeixin("### Third"))
    }

    @Test
    fun `looksLikeHeadingLineForWeixin detects short lines ending in colon`() {
        assertTrue(_looksLikeHeadingLineForWeixin("Summary:"))
        assertTrue(_looksLikeHeadingLineForWeixin("概述："))
    }

    @Test
    fun `looksLikeHeadingLineForWeixin rejects long lines with colon`() {
        assertFalse(_looksLikeHeadingLineForWeixin("this is a long sentence ending in colon:"))
    }

    @Test
    fun `looksLikeHeadingLineForWeixin rejects plain prose`() {
        assertFalse(_looksLikeHeadingLineForWeixin("regular prose"))
        assertFalse(_looksLikeHeadingLineForWeixin(""))
    }
}

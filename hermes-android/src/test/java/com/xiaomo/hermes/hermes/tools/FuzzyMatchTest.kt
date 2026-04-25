package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatchTest {

    @Test
    fun `exact strategy replaces single occurrence`() {
        val r = fuzzyFindAndReplace("hello world", "world", "kotlin")
        assertEquals("hello kotlin", r["content"])
        assertEquals(1, r["match_count"])
        assertEquals("exact", r["strategy"])
        assertNull(r["error"])
    }

    @Test
    fun `empty old_string returns error`() {
        val r = fuzzyFindAndReplace("abc", "", "x")
        assertEquals(0, r["match_count"])
        assertEquals("old_string cannot be empty", r["error"])
    }

    @Test
    fun `identical old and new returns error`() {
        val r = fuzzyFindAndReplace("abc", "abc", "abc")
        assertEquals(0, r["match_count"])
        assertEquals("old_string and new_string are identical", r["error"])
    }

    @Test
    fun `multiple exact matches without replaceAll returns error`() {
        val r = fuzzyFindAndReplace("foo foo foo", "foo", "bar")
        assertEquals(0, r["match_count"])
        assertTrue((r["error"] as String).contains("3 matches"))
    }

    @Test
    fun `multiple exact matches with replaceAll succeeds`() {
        val r = fuzzyFindAndReplace("foo foo foo", "foo", "bar", replaceAll = true)
        assertEquals("bar bar bar", r["content"])
        assertEquals(3, r["match_count"])
    }

    @Test
    fun `line_trimmed strategy matches ignoring trailing spaces`() {
        val content = "first line   \nsecond line\t\nthird line"
        val r = fuzzyFindAndReplace(content, "first line\nsecond line", "X")
        assertEquals("line_trimmed", r["strategy"])
        assertEquals(1, r["match_count"])
    }

    @Test
    fun `indentation_flexible strategy matches with different leading ws`() {
        val content = "    def foo():\n        return 1\n"
        val r = fuzzyFindAndReplace(content, "def foo():\n    return 1", "REPLACED")
        assertNotNull(r["strategy"])
        assertEquals(1, r["match_count"])
    }

    @Test
    fun `unicode_normalize maps smart quotes to ascii`() {
        val input = "hello \u201cworld\u201d and \u2018quote\u2019"
        val out = _unicodeNormalize(input)
        assertEquals("hello \"world\" and 'quote'", out)
    }

    @Test
    fun `unicode_normalize expands ellipsis and nbsp`() {
        val out = _unicodeNormalize("a\u2026b\u00a0c")
        assertEquals("a...b c", out)
    }

    @Test
    fun `escape_normalize converts backslash-n to newline`() {
        val content = "line1\nline2"
        val r = fuzzyFindAndReplace(content, "line1\\nline2", "X")
        assertEquals("escape_normalized", r["strategy"])
        assertEquals("X", r["content"])
    }

    @Test
    fun `no match returns error`() {
        val r = fuzzyFindAndReplace("abc def", "xyz", "q")
        assertEquals(0, r["match_count"])
        assertEquals("Could not find a match for old_string in the file", r["error"])
    }

    @Test
    fun `_strategyExact returns empty on no match`() {
        assertTrue(_strategyExact("hello", "xyz").isEmpty())
    }

    @Test
    fun `_strategyExact finds all occurrences`() {
        val hits = _strategyExact("ababab", "ab")
        assertEquals(3, hits.size)
    }

    @Test
    fun `findClosestLines returns did-you-mean hint`() {
        val content = "line one\nline two\nline three\ndef foo():\n    pass\n"
        val hint = findClosestLines("def fo0():", content)
        assertTrue(hint.contains("def foo():"))
    }

    @Test
    fun `findClosestLines empty inputs return empty`() {
        assertEquals("", findClosestLines("", "content"))
        assertEquals("", findClosestLines("pattern", ""))
    }

    @Test
    fun `formatNoMatchHint returns empty when matchCount gt 0`() {
        assertEquals("", formatNoMatchHint("err", 1, "old", "content"))
    }

    @Test
    fun `formatNoMatchHint returns empty when error does not match prefix`() {
        assertEquals("", formatNoMatchHint("Some other error", 0, "old", "content"))
    }

    @Test
    fun `_detectEscapeDrift returns null when no backslash-quote in newString`() {
        assertNull(_detectEscapeDrift("content", listOf(0 to 3), "old", "new"))
    }

    @Test
    fun `_detectEscapeDrift flags backslash-apostrophe artifact`() {
        // content has plain apostrophe; old/new both have backslash-apostrophe
        val content = "it's ok"
        // Matched region is "it's ok" (no backslash), old_string has \' so it did not match via exact
        // but a later strategy would match. Call _detectEscapeDrift directly.
        val msg = _detectEscapeDrift(content, listOf(0 to content.length), "it\\'s ok", "it\\'s broken")
        assertNotNull(msg)
        assertTrue(msg!!.contains("Escape-drift"))
    }

    @Test
    fun `_applyReplacements replaces from end to start`() {
        val out = _applyReplacements("ababab", listOf(0 to 2, 2 to 4, 4 to 6), "X")
        assertEquals("XXX", out)
    }

    @Test
    fun `UNICODE_MAP contains expected keys`() {
        assertEquals("\"", UNICODE_MAP["\u201c"])
        assertEquals("'", UNICODE_MAP["\u2018"])
        assertEquals("...", UNICODE_MAP["\u2026"])
    }
}

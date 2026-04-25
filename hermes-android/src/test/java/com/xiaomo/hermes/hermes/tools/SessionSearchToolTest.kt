package com.xiaomo.hermes.hermes.tools

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSearchToolTest {

    @Test
    fun `MAX_SESSION_CHARS is 100k`() {
        assertEquals(100_000, MAX_SESSION_CHARS)
    }

    @Test
    fun `MAX_SUMMARY_TOKENS is 10k`() {
        assertEquals(10000, MAX_SUMMARY_TOKENS)
    }

    @Test
    fun `_truncateAroundMatches returns input unchanged when under limit`() {
        val text = "short text"
        val out = _truncateAroundMatches(text, "anything", maxChars = 100)
        assertEquals(text, out)
    }

    @Test
    fun `_truncateAroundMatches finds phrase match and centers window`() {
        val prefix = "a".repeat(500)
        val needle = "MAGIC_PHRASE"
        val suffix = "b".repeat(500)
        val full = prefix + needle + suffix
        val out = _truncateAroundMatches(full, needle, maxChars = 300)
        assertTrue(out.contains(needle))
        // Output contains earlier/later truncation markers depending on window.
        assertTrue(out.contains("truncated") || out.length <= 400)
    }

    @Test
    fun `_truncateAroundMatches falls back to hard-truncate when no match found`() {
        val full = "x".repeat(2000)
        val out = _truncateAroundMatches(full, "unfindable", maxChars = 500)
        // No match → hard-truncated prefix + suffix marker.
        assertTrue(out.startsWith("x"))
        assertTrue("later conversation truncated" in out)
    }

    @Test
    fun `_truncateAroundMatches handles case-insensitive phrase match`() {
        val full = "prefix " + "x".repeat(2000) + " NEEDLE " + "y".repeat(2000) + " suffix"
        val out = _truncateAroundMatches(full, "needle", maxChars = 1000)
        assertTrue(out.lowercase().contains("needle"))
    }

    @Test
    fun `_truncateAroundMatches uses individual-term fallback when no phrase match`() {
        val full = "alpha foo " + "x".repeat(3000) + " bar beta"
        // "foo bar" as a phrase won't match; but individual terms "foo" and "bar" will.
        val out = _truncateAroundMatches(full, "foo bar", maxChars = 500)
        assertTrue(out.length <= 600)  // max + markers
    }

    @Test
    fun `sessionSearch returns error when db is null`() {
        val result = sessionSearch(query = "anything", db = null)
        val obj = JSONObject(result)
        assertTrue(obj.has("error"))
        assertEquals(false, obj.optBoolean("success", true))
    }

    @Test
    fun `SESSION_SEARCH_SCHEMA has expected shape`() {
        assertEquals("session_search", SESSION_SEARCH_SCHEMA["name"])
        assertNotNull(SESSION_SEARCH_SCHEMA["description"])
        @Suppress("UNCHECKED_CAST")
        val params = SESSION_SEARCH_SCHEMA["parameters"] as Map<String, Any>
        assertEquals("object", params["type"])
        @Suppress("UNCHECKED_CAST")
        val props = params["properties"] as Map<String, Any>
        assertTrue("query" in props)
        assertTrue("role_filter" in props)
        assertTrue("limit" in props)
    }

    @Test
    fun `checkSessionSearchRequirements does not throw`() {
        // Result depends on filesystem state — just verify it doesn't explode.
        checkSessionSearchRequirements()
    }

    @Test
    fun `_truncateAroundMatches handles proximity co-occurrence`() {
        // Two terms close together but no phrase match.
        val full = "a".repeat(500) + " foo zzz bar " + "a".repeat(500)
        val out = _truncateAroundMatches(full, "foo bar", maxChars = 400)
        assertTrue(out.contains("foo") && out.contains("bar"))
    }

    @Test
    fun `_truncateAroundMatches returns empty-input unchanged`() {
        assertEquals("", _truncateAroundMatches("", "anything", maxChars = 100))
    }

    @Test
    fun `_truncateAroundMatches adds suffix marker when truncation drops the tail`() {
        val full = "NEEDLE" + "x".repeat(5000)
        val out = _truncateAroundMatches(full, "NEEDLE", maxChars = 500)
        assertTrue("later conversation truncated" in out)
        // Match is at position 0 so no earlier-truncation prefix.
        assertFalse(out.startsWith("...[earlier"))
    }
}

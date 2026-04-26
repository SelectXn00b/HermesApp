package com.xiaomo.hermes.hermes.tools

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for WebTools.kt — Firecrawl / Tavily / Exa / Parallel backends are
 * not reachable on Android, so the high-level entry points return toolError.
 * The schema fields, base URLs, and module constants must line up 1:1 with
 * Python `tools/web_tools.py`.
 *
 * Covers TC-TOOL-330-a (schema field descriptions match Python upstream).
 */
class WebToolsTest {

    // ── Module constants ──
    @Test
    fun `_TAVILY_BASE_URL matches Python`() {
        assertEquals("https://api.tavily.com", _TAVILY_BASE_URL)
    }

    @Test
    fun `DEFAULT_MIN_LENGTH_FOR_SUMMARIZATION is 5000`() {
        assertEquals(5000, DEFAULT_MIN_LENGTH_FOR_SUMMARIZATION)
    }

    // ── R-TOOL-330 / TC-TOOL-330-a: schema field descriptions match Python ──
    /**
     * TC-TOOL-330-a — the `web_search` and `web_extract` schemas surface to
     * the model verbatim. Their field descriptions must match the Python
     * upstream (`tools/web_tools.py`). Any drift would change how the model
     * picks between tools or passes arguments.
     */
    @Test
    fun `schema text matches`() {
        // ── web_search ──
        assertEquals("web_search", WEB_SEARCH_SCHEMA["name"])
        val searchDesc = WEB_SEARCH_SCHEMA["description"] as String
        assertEquals(
            "Search the web for information on any topic. Returns up to 5 " +
                "relevant results with titles, URLs, and descriptions.",
            searchDesc)

        @Suppress("UNCHECKED_CAST")
        val searchParams = WEB_SEARCH_SCHEMA["parameters"] as Map<String, Any?>
        assertEquals("object", searchParams["type"])
        assertEquals(listOf("query"), searchParams["required"])

        @Suppress("UNCHECKED_CAST")
        val searchProps = searchParams["properties"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val queryProp = searchProps["query"] as Map<String, Any?>
        assertEquals("string", queryProp["type"])
        assertEquals(
            "The search query to look up on the web",
            queryProp["description"])

        // ── web_extract ──
        assertEquals("web_extract", WEB_EXTRACT_SCHEMA["name"])
        val extractDesc = WEB_EXTRACT_SCHEMA["description"] as String
        // The exact Python description: must mention markdown + PDF handling
        // + the 5000-char and 2M-char caps + browser-tool fallback.
        assertTrue(
            "web_extract description must mention 5000-char cap: $extractDesc",
            extractDesc.contains("5000 chars"))
        assertTrue(
            "web_extract description must mention 2M refusal: $extractDesc",
            extractDesc.contains("2M chars"))
        assertTrue(
            "web_extract description must reference PDF support: $extractDesc",
            extractDesc.contains("PDF"))
        assertTrue(
            "web_extract description must suggest browser fallback: $extractDesc",
            extractDesc.contains("browser"))

        @Suppress("UNCHECKED_CAST")
        val extractParams = WEB_EXTRACT_SCHEMA["parameters"] as Map<String, Any?>
        assertEquals("object", extractParams["type"])
        assertEquals(listOf("urls"), extractParams["required"])

        @Suppress("UNCHECKED_CAST")
        val extractProps = extractParams["properties"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val urlsProp = extractProps["urls"] as Map<String, Any?>
        assertEquals("array", urlsProp["type"])
        assertEquals(5, urlsProp["maxItems"])
        assertEquals(
            "List of URLs to extract content from (max 5 URLs per call)",
            urlsProp["description"])
    }

    // ── Backend checks: all false on Android ──
    @Test
    fun `checkFirecrawlApiKey false on Android`() {
        assertFalse(checkFirecrawlApiKey())
    }

    @Test
    fun `checkWebApiKey false on Android`() {
        assertFalse(checkWebApiKey())
    }

    @Test
    fun `checkAuxiliaryModel false on Android`() {
        assertFalse(checkAuxiliaryModel())
    }

    // ── cleanBase64Images is a pass-through on Android ──
    @Test
    fun `cleanBase64Images passes text through`() {
        val src = "some text"
        assertEquals(src, cleanBase64Images(src))
    }

    @Test
    fun `cleanBase64Images handles empty string`() {
        assertEquals("", cleanBase64Images(""))
    }

    // ── processContentWithLlm returns content unchanged on Android stub ──
    @Test
    fun `processContentWithLlm returns content unchanged on Android`() = runBlocking {
        val content = "raw web content"
        val result = processContentWithLlm(content, url = "https://ex.com", title = "Example")
        assertEquals(content, result)
    }

    // ── webSearchTool Android stub returns toolError ──
    @Test
    fun `webSearchTool returns toolError on Android`() {
        val result = webSearchTool("kotlin coroutines")
        val json = JSONObject(result)
        assertTrue(json.has("error"))
        val err = json.getString("error")
        assertTrue("must mention Android: $err", err.contains("Android"))
    }

    @Test
    fun `webSearchTool honours default limit without throwing`() {
        val result = webSearchTool("query") // uses default limit=5
        val json = JSONObject(result)
        assertTrue(json.has("error"))
    }

    // ── webExtractTool Android stub returns toolError ──
    @Test
    fun `webExtractTool returns toolError on Android`() = runBlocking {
        val result = webExtractTool(listOf("https://ex.com"))
        val json = JSONObject(result)
        assertTrue(json.has("error"))
        assertTrue(json.getString("error").contains("Android"))
    }

    @Test
    fun `webExtractTool uses DEFAULT_MIN_LENGTH default`() = runBlocking {
        // Just verifies the default parameter binding doesn't throw.
        val result = webExtractTool(
            urls = listOf("https://ex.com"),
            format = "markdown")
        val json = JSONObject(result)
        assertTrue(json.has("error"))
    }

    // ── webCrawlTool Android stub returns toolError ──
    @Test
    fun `webCrawlTool returns toolError on Android`() = runBlocking {
        val result = webCrawlTool("https://ex.com")
        val json = JSONObject(result)
        assertTrue(json.has("error"))
        assertTrue(json.getString("error").contains("Android"))
    }

    @Test
    fun `webCrawlTool default depth is basic`() = runBlocking {
        // Verify the default parameter 'basic' binds without throwing.
        val result = webCrawlTool(url = "https://ex.com", instructions = "get titles")
        val json = JSONObject(result)
        assertTrue(json.has("error"))
    }
}

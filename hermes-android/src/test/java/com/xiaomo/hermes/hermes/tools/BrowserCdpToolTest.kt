package com.xiaomo.hermes.hermes.tools

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for BrowserCdpTool.kt — the CDP (Chrome DevTools Protocol) passthrough
 * tool. Most of the file is a WebSocket client wrapper that cannot run in a
 * plain JVM test; we focus on pure validation branches in [browserCdp] and the
 * [BROWSER_CDP_SCHEMA] shape.
 *
 * Covers TC-TOOL-281-a (requires env / no CDP endpoint → error). The
 * private `_cdpCall` WebSocket transport is a structural stub and explicitly
 * not tested here.
 */
class BrowserCdpToolTest {

    // ── CDP_DOCS_URL constant ──
    @Test
    fun `CDP_DOCS_URL points to chromedevtools doc root`() {
        assertEquals("https://chromedevtools.github.io/devtools-protocol/", CDP_DOCS_URL)
    }

    // ── browserCdp validation: empty method ──
    @Test
    fun `browserCdp empty method returns error with cdp_docs hint`() {
        val json = JSONObject(browserCdp(method = ""))
        assertTrue(json.has("error"))
        assertTrue(json.getString("error").contains("'method' is required"))
        assertEquals(CDP_DOCS_URL, json.getString("cdp_docs"))
    }

    // ── R-TOOL-281 / TC-TOOL-281-a: no endpoint → configured error ──
    @Test
    fun `browserCdp without BROWSER_CDP_URL returns no-endpoint error`() {
        // _resolveCdpEndpoint reads env BROWSER_CDP_URL. On a clean JVM it's
        // unset → "". Test asserts the configured error surfaces.
        if (!System.getenv("BROWSER_CDP_URL").isNullOrBlank()) return
        val json = JSONObject(browserCdp(method = "Target.getTargets"))
        assertTrue(json.has("error"))
        val err = json.getString("error")
        assertTrue("should complain about no endpoint: $err",
            err.contains("No CDP endpoint is available"))
        assertEquals(CDP_DOCS_URL, json.getString("cdp_docs"))
    }

    @Test
    fun `browserCdp no-endpoint error mentions browser connect guidance`() {
        if (!System.getenv("BROWSER_CDP_URL").isNullOrBlank()) return
        val json = JSONObject(browserCdp(method = "Network.getAllCookies"))
        val err = json.getString("error")
        assertTrue(err.contains("/browser connect") || err.contains("browser.cdp_url"))
    }

    // ── browserCdp default parameters — nullable params accepted ──
    @Test
    fun `browserCdp accepts null params and defaults to empty map`() {
        if (!System.getenv("BROWSER_CDP_URL").isNullOrBlank()) return
        // With no endpoint we still bail before calling _cdpCall — the error
        // path must not throw NPE when params is null.
        val result = browserCdp(method = "Target.getTargets", params = null)
        val json = JSONObject(result)
        assertTrue(json.has("error"))
    }

    // ── BROWSER_CDP_SCHEMA shape ──
    @Test
    fun `BROWSER_CDP_SCHEMA has correct name and required`() {
        assertEquals("browser_cdp", BROWSER_CDP_SCHEMA["name"])
        @Suppress("UNCHECKED_CAST")
        val params = BROWSER_CDP_SCHEMA["parameters"] as Map<String, Any?>
        assertEquals("object", params["type"])
        assertEquals(listOf("method"), params["required"])
    }

    @Test
    fun `BROWSER_CDP_SCHEMA properties include method params target_id timeout`() {
        @Suppress("UNCHECKED_CAST")
        val params = BROWSER_CDP_SCHEMA["parameters"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val props = params["properties"] as Map<String, Any?>
        assertTrue("method" in props.keys)
        assertTrue("params" in props.keys)
        assertTrue("target_id" in props.keys)
        assertTrue("timeout" in props.keys)
    }

    @Test
    fun `BROWSER_CDP_SCHEMA timeout default is 30`() {
        @Suppress("UNCHECKED_CAST")
        val params = BROWSER_CDP_SCHEMA["parameters"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val props = params["properties"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val timeout = props["timeout"] as Map<String, Any?>
        assertEquals("number", timeout["type"])
        assertEquals(30, timeout["default"])
    }

    @Test
    fun `BROWSER_CDP_SCHEMA description references CDP docs url`() {
        val desc = BROWSER_CDP_SCHEMA["description"] as String
        assertTrue(desc.contains(CDP_DOCS_URL))
        assertTrue(desc.contains("Target.getTargets"))
    }

    @Test
    fun `BROWSER_CDP_SCHEMA params property has additionalProperties true`() {
        @Suppress("UNCHECKED_CAST")
        val params = BROWSER_CDP_SCHEMA["parameters"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val props = params["properties"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val pparams = props["params"] as Map<String, Any?>
        assertEquals(true, pparams["additionalProperties"])
        assertEquals("object", pparams["type"])
    }

    // ── Private helpers via reflection ──
    @Test
    fun `_resolveCdpEndpoint returns empty string when env unset`() {
        if (!System.getenv("BROWSER_CDP_URL").isNullOrBlank()) return
        val clazz = Class.forName("com.xiaomo.hermes.hermes.tools.BrowserCdpToolKt")
        val method = clazz.getDeclaredMethod("_resolveCdpEndpoint")
        method.isAccessible = true
        val result = method.invoke(null) as String
        assertEquals("", result)
    }

    @Test
    fun `_browserCdpCheck false when no endpoint`() {
        if (!System.getenv("BROWSER_CDP_URL").isNullOrBlank()) return
        val clazz = Class.forName("com.xiaomo.hermes.hermes.tools.BrowserCdpToolKt")
        val method = clazz.getDeclaredMethod("_browserCdpCheck")
        method.isAccessible = true
        val result = method.invoke(null) as Boolean
        assertFalse(result)
    }

    // ── browserCdp timeout edge (no endpoint path never reaches safeTimeout,
    //    but we document the method signature works with defaults) ──
    @Test
    fun `browserCdp signature accepts default timeout`() {
        // Just verify that calling with defaults does not throw.
        val result = browserCdp(method = "")  // will error-out early
        assertNotNull(result)
    }
}

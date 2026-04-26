package com.xiaomo.hermes.hermes.tools

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for BrowserTool.kt — PATH merging, schema lookup, session creation,
 * screenshot-path extraction, and tool entry points.
 *
 * Covers TC-TOOL-280-a (merges browser path) plus structural tests for
 * BROWSER_TOOL_SCHEMAS, session helpers, and the Android-disabled entry
 * points. Most Python-side logic is a stub on Android — tests here lock in
 * the stub contract and the surviving pure helpers (PATH, truncation,
 * screenshot-path regex).
 */
class BrowserToolTest {

    // ── _SANE_PATH_DIRS / _SANE_PATH ──
    @Test
    fun `_SANE_PATH_DIRS has seven canonical entries`() {
        assertEquals(7, _SANE_PATH_DIRS.size)
        assertTrue("/data/data/com.termux/files/usr/bin" in _SANE_PATH_DIRS)
        assertTrue("/opt/homebrew/bin" in _SANE_PATH_DIRS)
        assertTrue("/usr/bin" in _SANE_PATH_DIRS)
    }

    @Test
    fun `_SANE_PATH joins with colon`() {
        assertEquals(_SANE_PATH_DIRS.joinToString(":"), _SANE_PATH)
    }

    // ── R-TOOL-280 / TC-TOOL-280-a: _mergeBrowserPath adds SANE_PATH entries ──
    @Test
    fun `_mergeBrowserPath empty returns SANE path joined`() {
        val merged = _mergeBrowserPath("")
        // Empty input returns only the candidate dirs joined
        assertEquals(_SANE_PATH_DIRS.joinToString(":"), merged)
    }

    @Test
    fun `_mergeBrowserPath prefix preserves existing entries and appends SANE`() {
        val merged = _mergeBrowserPath("/opt:/usr/bin")
        assertTrue("existing first", merged.startsWith("/opt:/usr/bin:"))
        // All sane dirs appear somewhere in the merged string
        for (dir in _SANE_PATH_DIRS) {
            assertTrue("$dir should appear", merged.contains(dir))
        }
    }

    @Test
    fun `_browserCandidatePathDirs equals SANE when no homebrew discovery`() {
        // _discoverHomebrewNodeDirs returns empty on Android → candidates == SANE
        assertEquals(_SANE_PATH_DIRS, _browserCandidatePathDirs())
    }

    // ── Constants ──
    @Test
    fun `DEFAULT_COMMAND_TIMEOUT is 30 seconds`() {
        assertEquals(30, DEFAULT_COMMAND_TIMEOUT)
        assertEquals(30, _getCommandTimeout())
    }

    @Test
    fun `SNAPSHOT_SUMMARIZE_THRESHOLD is 8000`() {
        assertEquals(8000, SNAPSHOT_SUMMARIZE_THRESHOLD)
    }

    @Test
    fun `BROWSER_SESSION_INACTIVITY_TIMEOUT defaults to 300s`() {
        // Unless the test JVM explicitly sets BROWSER_INACTIVITY_TIMEOUT env,
        // the initializer falls through to 300.
        val envOverride = System.getenv("BROWSER_INACTIVITY_TIMEOUT")
        if (envOverride.isNullOrBlank()) {
            assertEquals(300, BROWSER_SESSION_INACTIVITY_TIMEOUT)
        }
    }

    // ── Mode / config helpers ──
    @Test
    fun `_isLocalMode and _isLocalBackend default true`() {
        assertTrue(_isLocalMode())
        assertTrue(_isLocalBackend())
    }

    @Test
    fun `_allowPrivateUrls default false`() {
        assertFalse(_allowPrivateUrls())
    }

    @Test
    fun `_getVisionModel and _getExtractionModel default null`() {
        assertNull(_getVisionModel())
        assertNull(_getExtractionModel())
    }

    @Test
    fun `_getCdpOverride empty on default`() {
        assertEquals("", _getCdpOverride())
    }

    @Test
    fun `_resolveCdpOverride echoes input`() {
        assertEquals("ws://localhost:9222", _resolveCdpOverride("ws://localhost:9222"))
        assertEquals("", _resolveCdpOverride(""))
    }

    @Test
    fun `_getCloudProvider default null`() {
        assertNull(_getCloudProvider())
    }

    @Test
    fun `_browserInstallHint mentions agent-browser`() {
        assertTrue(_browserInstallHint().contains("agent-browser"))
    }

    @Test
    fun `_requiresRealTermuxBrowserInstall always false on Android`() {
        assertFalse(_requiresRealTermuxBrowserInstall("anything"))
        assertEquals("", _termuxBrowserInstallError())
    }

    @Test
    fun `_socketSafeTmpdir returns slash tmp`() {
        assertEquals("/tmp", _socketSafeTmpdir())
    }

    // ── BROWSER_TOOL_SCHEMAS shape ──
    @Test
    fun `BROWSER_TOOL_SCHEMAS has 10 entries with browser_ prefix`() {
        assertEquals(10, BROWSER_TOOL_SCHEMAS.size)
        for (schema in BROWSER_TOOL_SCHEMAS) {
            val name = schema["name"] as String
            assertTrue("$name should start with browser_", name.startsWith("browser_"))
            assertTrue("$name must have parameters", schema.containsKey("parameters"))
        }
    }

    @Test
    fun `_BROWSER_SCHEMA_MAP indexes by schema name`() {
        for (schema in BROWSER_TOOL_SCHEMAS) {
            val name = schema["name"] as String
            val looked = _BROWSER_SCHEMA_MAP[name]
            assertNotNull("must look up $name", looked)
            assertEquals(schema, looked)
        }
    }

    @Test
    fun `browser_click schema requires ref`() {
        val click = BROWSER_TOOL_SCHEMAS.first { it["name"] == "browser_click" }
        @Suppress("UNCHECKED_CAST")
        val params = click["parameters"] as Map<String, Any?>
        assertEquals(listOf("ref"), params["required"])
    }

    @Test
    fun `browser_type schema requires ref and text`() {
        val type = BROWSER_TOOL_SCHEMAS.first { it["name"] == "browser_type" }
        @Suppress("UNCHECKED_CAST")
        val params = type["parameters"] as Map<String, Any?>
        assertEquals(listOf("ref", "text"), params["required"])
    }

    @Test
    fun `browser_scroll schema enumerates up and down`() {
        val scroll = BROWSER_TOOL_SCHEMAS.first { it["name"] == "browser_scroll" }
        @Suppress("UNCHECKED_CAST")
        val params = scroll["parameters"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val props = params["properties"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val direction = props["direction"] as Map<String, Any?>
        assertEquals(listOf("up", "down"), direction["enum"])
    }

    // ── _createLocalSession / _createCdpSession ──
    @Test
    fun `_createLocalSession returns h_ prefixed name with features local`() {
        val info = _createLocalSession("task-local-1")
        val name = info["session_name"] as String
        assertTrue(name.startsWith("h_"))
        assertNull(info["bb_session_id"])
        assertNull(info["cdp_url"])
        @Suppress("UNCHECKED_CAST")
        val features = info["features"] as Map<String, Any?>
        assertEquals(true, features["local"])
    }

    @Test
    fun `_createCdpSession returns cdp_ prefixed name with url`() {
        val info = _createCdpSession("task-cdp-1", "ws://host:9222")
        val name = info["session_name"] as String
        assertTrue(name.startsWith("cdp_"))
        assertEquals("ws://host:9222", info["cdp_url"])
        @Suppress("UNCHECKED_CAST")
        val features = info["features"] as Map<String, Any?>
        assertEquals(true, features["cdp_override"])
    }

    @Test
    fun `_getSessionInfo falls through to local session`() {
        val info = _getSessionInfo("task-fallback")
        val name = info["session_name"] as String
        // Default path creates a local session.
        assertTrue(name.startsWith("h_"))
    }

    @Test
    fun `_findAgentBrowser returns agent-browser stub`() {
        assertEquals("agent-browser", _findAgentBrowser())
    }

    // ── _extractScreenshotPathFromText ──
    @Test
    fun `_extractScreenshotPathFromText finds explicit path`() {
        val text = "Screenshot saved to: /tmp/shot.png"
        assertEquals("/tmp/shot.png", _extractScreenshotPathFromText(text))
    }

    @Test
    fun `_extractScreenshotPathFromText case insensitive`() {
        val text = "screenshot: /var/tmp/a.png"
        assertEquals("/var/tmp/a.png", _extractScreenshotPathFromText(text))
    }

    @Test
    fun `_extractScreenshotPathFromText returns null when absent`() {
        assertNull(_extractScreenshotPathFromText("no path here"))
    }

    // ── _truncateSnapshot ──
    @Test
    fun `_truncateSnapshot no-op below cap`() {
        val body = "short text"
        assertEquals(body, _truncateSnapshot(body, maxChars = 100))
    }

    @Test
    fun `_truncateSnapshot cuts with marker over cap`() {
        val body = "x".repeat(500)
        val out = _truncateSnapshot(body, maxChars = 100)
        assertTrue(out.length > 100)  // marker adds bytes
        assertTrue(out.startsWith("x".repeat(100)))
        assertTrue(out.endsWith("[truncated]"))
    }

    @Test
    fun `_extractRelevantContent caps at 8000 chars`() {
        val body = "y".repeat(10_000)
        val out = _extractRelevantContent(body, userTask = "anything")
        assertEquals(8000, out.length)
    }

    // ── _runBrowserCommand stub ──
    @Test
    fun `_runBrowserCommand returns error JSON on Android`() {
        val out = _runBrowserCommand(listOf("open", "https://ex.com"))
        val json = JSONObject(out)
        assertTrue(json.has("error"))
        assertTrue(json.getString("error").contains("not available"))
    }

    // ── Public tool entry points — all Android stubs return error JSON ──
    @Test
    fun `browserNavigate returns not-available error on Android`() {
        val json = JSONObject(browserNavigate("https://ex.com"))
        assertTrue(json.getString("error").contains("browser_navigate"))
    }

    @Test
    fun `browserSnapshot returns error on Android`() {
        val json = JSONObject(browserSnapshot())
        assertTrue(json.getString("error").contains("browser_snapshot"))
    }

    @Test
    fun `browserClick returns error on Android`() {
        val json = JSONObject(browserClick("@e1"))
        assertTrue(json.getString("error").contains("browser_click"))
    }

    @Test
    fun `browserType returns error on Android`() {
        val json = JSONObject(browserType("@e1", "hi"))
        assertTrue(json.getString("error").contains("browser_type"))
    }

    @Test
    fun `browserScroll returns error on Android`() {
        val json = JSONObject(browserScroll("down"))
        assertTrue(json.getString("error").contains("browser_scroll"))
    }

    @Test
    fun `browserBack returns error on Android`() {
        val json = JSONObject(browserBack())
        assertTrue(json.getString("error").contains("browser_back"))
    }

    @Test
    fun `browserPress returns error on Android`() {
        val json = JSONObject(browserPress("Enter"))
        assertTrue(json.getString("error").contains("browser_press"))
    }

    @Test
    fun `browserConsole returns error on Android`() {
        val json = JSONObject(browserConsole())
        assertTrue(json.getString("error").contains("browser_console"))
    }

    @Test
    fun `browserGetImages returns error on Android`() {
        val json = JSONObject(browserGetImages())
        assertTrue(json.getString("error").contains("browser_get_images"))
    }

    @Test
    fun `browserVision returns error on Android`() {
        val json = JSONObject(browserVision("what do you see?"))
        assertTrue(json.getString("error").contains("browser_vision"))
    }

    // ── checkBrowserRequirements ──
    @Test
    fun `checkBrowserRequirements always false on Android`() {
        assertFalse(checkBrowserRequirements())
    }

    // ── Cleanup functions are void / no-op — verify they don't throw ──
    @Test
    fun `cleanupBrowser no-op does not throw`() {
        cleanupBrowser("task-x")
        cleanupBrowser()
        cleanupAllBrowsers()
    }
}

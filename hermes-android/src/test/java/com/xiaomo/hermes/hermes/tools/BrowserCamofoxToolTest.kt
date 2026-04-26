package com.xiaomo.hermes.hermes.tools

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for BrowserCamofox.kt — Camofox is a remote Firefox anti-detection
 * backend. On Android the service is never reachable, so every public
 * `camofox*` entry point must return a `toolError`-shaped JSON blob with
 * "Camofox is not available on Android".
 *
 * Covers TC-TOOL-282-a (Camofox 任意接口 → toolError).
 */
class BrowserCamofoxToolTest {

    private fun assertToolError(json: String) {
        val obj = JSONObject(json)
        assertTrue("response must have an error key: $json", obj.has("error"))
        val err = obj.getString("error")
        assertTrue(
            "error must mention Camofox + Android: $err",
            err.contains("Camofox") && err.contains("Android"),
        )
    }

    // ── Constants ──
    @Test
    fun `_DEFAULT_TIMEOUT is 30`() {
        assertEquals(30, _DEFAULT_TIMEOUT)
    }

    @Test
    fun `_SNAPSHOT_MAX_CHARS is 80000`() {
        assertEquals(80_000, _SNAPSHOT_MAX_CHARS)
    }

    // ── Env / mode helpers ──
    @Test
    fun `getCamofoxUrl empty when env unset`() {
        if (!System.getenv("CAMOFOX_URL").isNullOrBlank()) return
        assertEquals("", getCamofoxUrl())
    }

    @Test
    fun `isCamofoxMode false when env unset`() {
        if (!System.getenv("CAMOFOX_URL").isNullOrBlank()) return
        assertFalse(isCamofoxMode())
    }

    @Test
    fun `checkCamofoxAvailable matches isCamofoxMode`() {
        assertEquals(isCamofoxMode(), checkCamofoxAvailable())
    }

    @Test
    fun `getVncUrl returns null on Android stub`() {
        assertEquals(null, getVncUrl())
    }

    @Test
    fun `_managedPersistenceEnabled is false on Android`() {
        assertFalse(_managedPersistenceEnabled())
    }

    @Test
    fun `_getSession echoes provided id`() {
        assertEquals("sid-1", _getSession("sid-1"))
        assertEquals(null, _getSession(null))
    }

    @Test
    fun `_ensureTab returns null on Android`() {
        assertEquals(null, _ensureTab("sid", "https://ex.com"))
    }

    @Test
    fun `_dropSession no-op does not throw`() {
        _dropSession("sid-x")
    }

    // ── Low-level HTTP helpers (all return null/stub) ──
    @Test
    fun `_post returns null on Android`() {
        assertEquals(null, _post("/path", mapOf("k" to "v")))
    }

    @Test
    fun `_get returns null on Android`() {
        assertEquals(null, _get("/path"))
    }

    @Test
    fun `_getRaw returns null on Android`() {
        assertEquals(null, _getRaw("/path"))
    }

    @Test
    fun `_delete returns null on Android`() {
        assertEquals(null, _delete("/path"))
    }

    // ── TC-TOOL-282-a: every public camofox_* entry point returns toolError ──
    @Test
    fun `android toolError`() = runBlocking {
        // Full fan-out — every high-level entry point in the module must
        // surface a toolError mentioning Camofox + Android. If any adds real
        // implementation later, this test surfaces the change.
        assertToolError(camofoxNavigate("https://ex.com"))
        assertToolError(camofoxSnapshot())
        assertToolError(camofoxClick("#btn"))
        assertToolError(camofoxType("#input", "hi"))
        assertToolError(camofoxScroll())
        assertToolError(camofoxBack())
        assertToolError(camofoxPress("Enter"))
        assertToolError(camofoxClose())
        assertToolError(camofoxGetImages())
        assertToolError(camofoxVision("what do you see?"))
        assertToolError(camofoxConsole())
        assertToolError(camofoxSoftCleanup())
    }

    // ── Individual entry points with explicit assertions so one failure
    //    doesn't mask another when the combined test above regresses ──
    @Test
    fun `camofoxNavigate returns toolError`() = runBlocking {
        val json = JSONObject(camofoxNavigate("https://ex.com"))
        assertNotNull(json.getString("error"))
        assertTrue(json.getString("error").contains("Camofox"))
    }

    @Test
    fun `camofoxSnapshot returns toolError`() = runBlocking {
        val json = JSONObject(camofoxSnapshot(full = true))
        assertTrue(json.getString("error").contains("Android"))
    }

    @Test
    fun `camofoxClick returns toolError`() = runBlocking {
        val json = JSONObject(camofoxClick("#primary"))
        assertTrue(json.getString("error").contains("Android"))
    }

    @Test
    fun `camofoxType returns toolError`() = runBlocking {
        val json = JSONObject(camofoxType("#input", "some text"))
        assertTrue(json.getString("error").contains("Android"))
    }

    @Test
    fun `camofoxScroll defaults applied and returns toolError`() = runBlocking {
        val json = JSONObject(camofoxScroll())
        assertTrue(json.getString("error").contains("Camofox"))
    }

    @Test
    fun `camofoxVision with annotate flag returns toolError`() = runBlocking {
        val json = JSONObject(camofoxVision("question", annotate = true))
        assertTrue(json.getString("error").contains("Android"))
    }
}

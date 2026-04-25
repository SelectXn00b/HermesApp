package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolBackendHelpersTest {

    @Test
    fun `normalizeBrowserCloudProvider defaults to local when null`() {
        assertEquals("local", normalizeBrowserCloudProvider(null))
    }

    @Test
    fun `normalizeBrowserCloudProvider defaults to local when empty`() {
        assertEquals("local", normalizeBrowserCloudProvider(""))
        assertEquals("local", normalizeBrowserCloudProvider("   "))
    }

    @Test
    fun `normalizeBrowserCloudProvider trims and lowercases`() {
        assertEquals("browserbase", normalizeBrowserCloudProvider("  BrowserBase  "))
        assertEquals("local", normalizeBrowserCloudProvider("LOCAL"))
    }

    @Test
    fun `normalizeBrowserCloudProvider accepts non-string input via toString`() {
        assertEquals("42", normalizeBrowserCloudProvider(42))
    }

    @Test
    fun `coerceModalMode defaults to auto when null`() {
        assertEquals("auto", coerceModalMode(null))
    }

    @Test
    fun `coerceModalMode accepts valid modes case-insensitively`() {
        assertEquals("auto", coerceModalMode("AUTO"))
        assertEquals("direct", coerceModalMode("Direct"))
        assertEquals("managed", coerceModalMode("managed"))
    }

    @Test
    fun `coerceModalMode falls back to auto for invalid input`() {
        assertEquals("auto", coerceModalMode("bogus"))
        assertEquals("auto", coerceModalMode(""))
        assertEquals("auto", coerceModalMode("   "))
    }

    @Test
    fun `normalizeModalMode delegates to coerceModalMode`() {
        assertEquals(coerceModalMode("direct"), normalizeModalMode("direct"))
        assertEquals(coerceModalMode("bogus"), normalizeModalMode("bogus"))
    }

    @Test
    fun `managedNousToolsEnabled is false without env var`() {
        // No HERMES_ENABLE_NOUS_MANAGED_TOOLS in test env — should be false.
        assertFalse(managedNousToolsEnabled())
    }

    @Test
    fun `resolveModalBackendState managed mode without credentials returns managed when ready`() {
        val state = resolveModalBackendState(
            modalMode = "managed",
            hasDirect = false,
            managedReady = true,
        )
        // managedNousToolsEnabled is false in test env, so selected_backend falls to null.
        assertEquals("managed", state["requested_mode"])
        assertEquals(null, state["selected_backend"])
        assertEquals(true, state["managed_mode_blocked"])  // requested managed but feature off
    }

    @Test
    fun `resolveModalBackendState direct mode picks direct when credentials present`() {
        val state = resolveModalBackendState(
            modalMode = "direct",
            hasDirect = true,
            managedReady = false,
        )
        assertEquals("direct", state["selected_backend"])
    }

    @Test
    fun `resolveModalBackendState direct mode falls through when no credentials`() {
        val state = resolveModalBackendState(
            modalMode = "direct",
            hasDirect = false,
            managedReady = true,
        )
        assertEquals(null, state["selected_backend"])
    }

    @Test
    fun `resolveModalBackendState auto mode prefers direct when managed unavailable`() {
        val state = resolveModalBackendState(
            modalMode = "auto",
            hasDirect = true,
            managedReady = false,
        )
        assertEquals("direct", state["selected_backend"])
    }

    @Test
    fun `resolveModalBackendState auto mode returns null when nothing available`() {
        val state = resolveModalBackendState(
            modalMode = "auto",
            hasDirect = false,
            managedReady = false,
        )
        assertEquals(null, state["selected_backend"])
    }

    @Test
    fun `resolveModalBackendState returns all expected keys`() {
        val state = resolveModalBackendState(modalMode = "auto", hasDirect = false, managedReady = false)
        assertTrue("requested_mode" in state)
        assertTrue("mode" in state)
        assertTrue("has_direct" in state)
        assertTrue("managed_ready" in state)
        assertTrue("managed_mode_blocked" in state)
        assertTrue("selected_backend" in state)
    }

    @Test
    fun `resolveModalBackendState invalid mode normalizes to auto`() {
        val state = resolveModalBackendState(
            modalMode = "bogus",
            hasDirect = true,
            managedReady = false,
        )
        assertEquals("auto", state["requested_mode"])
        assertEquals("direct", state["selected_backend"])
    }

    @Test
    fun `prefersGateway returns false on Android without yaml loader`() {
        assertFalse(prefersGateway("skills"))
        assertFalse(prefersGateway("anything"))
    }

    @Test
    fun `resolveOpenaiAudioApiKey returns a trimmed string from env`() {
        // Can't mutate process env from JVM tests; assert only that the function
        // returns a trimmed string (no leading/trailing whitespace).
        val result = resolveOpenaiAudioApiKey()
        assertEquals(result, result.trim())
    }

    @Test
    fun `falKeyIsConfigured returns false without env`() {
        assertFalse(falKeyIsConfigured())
    }
}

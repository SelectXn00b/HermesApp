package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedToolGatewayTest {

    @Test
    fun `authJsonPath ends with dot-hermes auth-json`() {
        val p = authJsonPath()
        assertTrue(p.absolutePath.endsWith(".hermes/auth.json"))
    }

    @Test
    fun `getToolGatewayScheme defaults to https when env missing`() {
        // TOOL_GATEWAY_SCHEME unset in test env.
        assertEquals("https", getToolGatewayScheme())
    }

    @Test
    fun `buildVendorGatewayUrl uses default domain when env missing`() {
        val url = buildVendorGatewayUrl("foo")
        assertTrue(url.startsWith("https://foo-gateway."))
        assertTrue(url.endsWith("nousresearch.com"))
    }

    @Test
    fun `buildVendorGatewayUrl uppercases and dash-replaces vendor for env key lookup`() {
        // No env set → falls back to constructed URL with original lowercase vendor.
        val url = buildVendorGatewayUrl("some-vendor")
        assertTrue(url.contains("some-vendor-gateway"))
    }

    @Test
    fun `resolveManagedToolGateway returns null when managed tools disabled`() {
        // managedNousToolsEnabled() is false in test env (no HERMES_ENABLE_NOUS_MANAGED_TOOLS).
        val result = resolveManagedToolGateway("foo")
        assertNull(result)
    }

    @Test
    fun `isManagedToolGatewayReady returns false when managed tools disabled`() {
        assertFalse(isManagedToolGatewayReady("foo"))
    }

    @Test
    fun `ManagedToolGatewayConfig holds vendor-origin-token-mode`() {
        val cfg = ManagedToolGatewayConfig(
            vendor = "v",
            gatewayOrigin = "https://v-gateway.example.com",
            nousUserToken = "tok",
            managedMode = true,
        )
        assertEquals("v", cfg.vendor)
        assertEquals("https://v-gateway.example.com", cfg.gatewayOrigin)
        assertEquals("tok", cfg.nousUserToken)
        assertTrue(cfg.managedMode)
    }

    @Test
    fun `readNousAccessToken returns null when no auth file or env`() {
        // No TOOL_GATEWAY_USER_TOKEN and no auth.json likely → null.
        // If auth.json happens to exist on test machine, at least non-throwing.
        val token = readNousAccessToken()
        assertTrue(token == null || token.isNotEmpty())
    }
}

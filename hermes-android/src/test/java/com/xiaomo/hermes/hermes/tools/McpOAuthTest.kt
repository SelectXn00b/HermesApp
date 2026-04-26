package com.xiaomo.hermes.hermes.tools

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for McpOAuth.kt — credential persistence + OAuth URL helpers.
 * The browser-interactive flow is stubbed on Android; tests focus on the
 * pure helpers (port finder, base-URL parser, filename safety) and the
 * token-storage round trip using unique names under user.home so they
 * don't collide with real profile data.
 * Covers TC-MCP-020 (token cache) / TC-MCP-022 (auth URL helpers).
 */
class McpOAuthTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // Use a UUID-like suffix so the tests never stomp on real ~/.hermes data
    // even if the user happens to have a server called "test".
    private val uniqueServer = "mcp-oauth-test-${System.nanoTime()}"

    @After
    fun cleanup() {
        // Remove anything we persisted for uniqueServer; _getTokenDir points
        // at ~/.hermes/mcp-tokens/ which is real user state.
        try {
            removeOauthTokens(uniqueServer)
        } catch (_: Throwable) { /* best-effort */ }
    }

    /** TC-MCP-020-a: token dir is below user.home / .hermes / mcp-tokens. */
    @Test
    fun `token dir lives under hermes profile`() {
        val dir = _getTokenDir()
        val home = System.getProperty("user.home") ?: "/tmp"
        assertTrue(
            "expected dir to start with user.home, got $dir",
            dir.absolutePath.startsWith(home),
        )
        assertEquals("mcp-tokens", dir.name)
        assertEquals(".hermes", dir.parentFile.name)
    }

    /** TC-MCP-022-a: _safeFilename strips unsafe chars and caps length. */
    @Test
    fun `safeFilename strips unsafe chars`() {
        assertEquals("foo_bar", _safeFilename("foo/bar"))
        assertEquals("abc-123", _safeFilename("abc-123"))
        // Empty/only-bad input -> "default" fallback (after trim).
        assertEquals("default", _safeFilename("///"))
        assertEquals("default", _safeFilename(""))
        // 128-char cap.
        val long = "a".repeat(200)
        assertEquals(128, _safeFilename(long).length)
    }

    /** TC-MCP-020-a: Android always reports non-interactive / browserless env. */
    @Test
    fun `interactive and browser checks return false on android`() {
        assertFalse(_isInteractive())
        assertFalse(_canOpenBrowser())
        // Module-level flag is const false.
        assertFalse(_OAUTH_AVAILABLE)
    }

    /** TC-MCP-022-a: _findFreePort binds an OS-assigned port. */
    @Test
    fun `findFreePort returns a usable high port or 0`() {
        val p = _findFreePort()
        // On sandboxed CI environments, bind may fail and return 0; the
        // function contract is "best-effort, fall back to 0". Either way
        // we must never produce a negative number.
        assertTrue("port should be non-negative, got $p", p >= 0)
        if (p != 0) {
            assertTrue("port should fit in ephemeral range, got $p", p in 1..65535)
        }
    }

    /** TC-MCP-022-a: build client metadata honours config overrides. */
    @Test
    fun `buildClientMetadata applies overrides`() {
        val meta = _buildClientMetadata(
            mapOf(
                "client_name" to "my-bot",
                "redirect_uri" to "https://example.com/cb",
            ),
        )
        assertEquals("my-bot", meta["client_name"])
        @Suppress("UNCHECKED_CAST")
        val redirect = meta["redirect_uris"] as List<Any?>
        assertEquals(listOf("https://example.com/cb"), redirect)
        @Suppress("UNCHECKED_CAST")
        val grants = meta["grant_types"] as List<Any?>
        assertTrue(grants.contains("authorization_code"))
        assertTrue(grants.contains("refresh_token"))
        assertEquals("none", meta["token_endpoint_auth_method"])
    }

    /** TC-MCP-022-a: defaults are populated when cfg is null. */
    @Test
    fun `buildClientMetadata default values`() {
        val meta = _buildClientMetadata(null)
        assertEquals("Hermes", meta["client_name"])
        @Suppress("UNCHECKED_CAST")
        val redirect = meta["redirect_uris"] as List<Any?>
        assertEquals(listOf("http://localhost/callback"), redirect)
    }

    /** TC-MCP-022-a: parseBaseUrl strips path components. */
    @Test
    fun `parseBaseUrl normalises mcp server url`() {
        assertEquals("https://example.com", _parseBaseUrl("https://example.com/api/v1"))
        assertEquals("http://127.0.0.1:8080", _parseBaseUrl("http://127.0.0.1:8080/mcp"))
        // Blank -> empty string.
        assertEquals("", _parseBaseUrl(""))
    }

    /** TC-MCP-022-a: malformed URL falls back to the original string. */
    @Test
    fun `parseBaseUrl falls back on malformed`() {
        // A URI without a scheme/host still needs a safe result.
        val bad = "not a url"
        // Per source: if URI parses but host==null, return serverUrl unchanged.
        // "not a url" fails URI parse entirely -> catch branch returns input.
        assertEquals(bad, _parseBaseUrl(bad))
    }

    /** TC-MCP-022-a: _configureCallbackPort honours explicit port. */
    @Test
    fun `configureCallbackPort picks configured value when present`() {
        assertEquals(12345, _configureCallbackPort(mapOf("callback_port" to 12345)))
        // Null cfg -> falls through to _findFreePort (≥ 0).
        val free = _configureCallbackPort(null)
        assertTrue(free >= 0)
    }

    /** TC-MCP-020-a: callback-waiter stub returns null immediately. */
    @Test
    fun `waitForCallback returns null stub`() = runBlocking {
        assertNull(_waitForCallback())
    }

    /** TC-MCP-022-a: buildOauthAuth is null on Android (no SDK). */
    @Test
    fun `buildOauthAuth is null on android`() {
        assertNull(buildOauthAuth("srv", null))
        assertNull(buildOauthAuth("srv", mapOf("client_name" to "x"), interactive = false))
    }

    /** TC-MCP-020-a: token storage round-trip persists and reloads. */
    @Test
    fun `token storage persists and reloads`() = runBlocking {
        val storage = HermesTokenStorage(uniqueServer)
        val tokens = mapOf(
            "access_token" to "tok-xyz",
            "refresh_token" to "ref-abc",
            "expires_in" to 3600,
        )

        assertFalse(storage.hasCachedTokens())
        storage.setTokens(tokens)
        assertTrue(storage.hasCachedTokens())

        @Suppress("UNCHECKED_CAST")
        val reloaded = storage.getTokens() as Map<String, Any?>
        assertEquals("tok-xyz", reloaded["access_token"])
        assertEquals("ref-abc", reloaded["refresh_token"])
        // Gson parses numbers as Double.
        assertEquals(3600.0, reloaded["expires_in"] as Double, 0.0)

        storage.remove()
        assertFalse(storage.hasCachedTokens())
        assertNull(storage.getTokens())
    }

    /** TC-MCP-020-a: client-info round-trip uses a separate file. */
    @Test
    fun `client info stored in separate file`() = runBlocking {
        val storage = HermesTokenStorage(uniqueServer)
        val info = mapOf("client_id" to "abc123")
        storage.setClientInfo(info)

        // Two distinct files: <name>.json and <name>.client.json
        val tokensFile = storage._tokensPath()
        val clientFile = storage._clientInfoPath()
        assertFalse("tokens file should not have been created", tokensFile.exists())
        assertTrue("client info file must exist", clientFile.exists())

        @Suppress("UNCHECKED_CAST")
        val reloaded = storage.getClientInfo() as Map<String, Any?>
        assertEquals("abc123", reloaded["client_id"])

        storage.remove()
    }

    /** TC-MCP-020-a: corrupt tokens are treated as absent and don't throw. */
    @Test
    fun `corrupt tokens file is ignored`() = runBlocking {
        val storage = HermesTokenStorage(uniqueServer)
        // Pre-create the file with garbage.
        val path = storage._tokensPath()
        path.parentFile?.mkdirs()
        path.writeText("{ not valid json ")

        assertTrue(storage.hasCachedTokens())  // file exists
        // Reading should not raise and should yield null.
        assertNull(storage.getTokens())
    }

    /** TC-MCP-020-a: _readJson / _writeJson round trip on disk. */
    @Test
    fun `readJson writeJson round trip`() {
        val file = tmp.newFile("payload.json")
        val data = mapOf("a" to 1, "b" to "hello")
        _writeJson(file, data)
        val loaded = _readJson(file)
        assertNotNull(loaded)
        assertEquals("hello", loaded!!["b"])
        assertEquals(1.0, loaded["a"] as Double, 0.0)  // Gson number -> Double
    }

    /** TC-MCP-020-a: _readJson returns null for missing path. */
    @Test
    fun `readJson returns null when missing`() {
        assertNull(_readJson(null))
        assertNull(_readJson(File(tmp.root, "no-such.json")))
    }

    /** TC-MCP-020-a: _makeCallbackHandler is null on Android, but dict is usable. */
    @Test
    fun `makeCallbackHandler returns null handler and mutable dict`() {
        val (handler, result) = _makeCallbackHandler()
        assertNull(handler)
        // Empty but mutable — caller writes auth_code / state into it.
        result["foo"] = "bar"
        assertEquals("bar", result["foo"])
    }

    /** TC-MCP-022-a: OAuthNonInteractiveError carries a sensible default. */
    @Test
    fun `OAuthNonInteractiveError has default message`() {
        val e = OAuthNonInteractiveError()
        assertNotNull(e.message)
        assertTrue(e.message!!.contains("OAuth"))
        assertTrue(e.message!!.contains("browser"))
    }
}

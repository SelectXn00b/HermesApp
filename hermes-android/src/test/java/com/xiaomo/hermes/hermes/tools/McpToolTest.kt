package com.xiaomo.hermes.hermes.tools

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for McpTool.kt — Android stub for MCP (Model Context Protocol) client.
 * Python-side hermes-agent relies on the optional `mcp` SDK + asyncio loop,
 * neither of which is available on Android. The port is a structural
 * skeleton; tests verify the Android contracts (stubs return empty /
 * toolError, handler factories produce error JSON, sanitizers work).
 * Covers TC-MCP-001/002/003/004/005.
 */
class McpToolTest {

    private val gson = Gson()

    /** TC-MCP-005-a (Android stdio stub): global availability flags are false. */
    @Test
    fun `mcp availability flags are all false on android`() {
        // Use reflection since these are file-private.
        val clazz = Class.forName("com.xiaomo.hermes.hermes.tools.McpToolKt")
        for (name in listOf(
            "_MCP_AVAILABLE",
            "_MCP_HTTP_AVAILABLE",
            "_MCP_SAMPLING_TYPES",
            "_MCP_NOTIFICATION_TYPES",
            "_MCP_MESSAGE_HANDLER_SUPPORTED",
        )) {
            val f = clazz.getDeclaredField(name)
            f.isAccessible = true
            assertFalse("$name must be false on Android", f.getBoolean(null))
        }
    }

    /** TC-MCP-005-a: _checkMessageHandlerSupport returns false on Android. */
    @Test
    fun `checkMessageHandlerSupport returns false`() {
        assertFalse(_checkMessageHandlerSupport())
    }

    /** TC-MCP-005-a: _buildSafeEnv returns empty map on Android. */
    @Test
    fun `buildSafeEnv returns empty map`() {
        assertTrue(_buildSafeEnv(null).isEmpty())
        assertTrue(_buildSafeEnv(mapOf("FOO" to "bar", "PATH" to "/x")).isEmpty())
    }

    /**
     * TC-MCP-001-a / TC-MCP-002-a: registerMcpServers is inert when SDK
     * unavailable — returns empty list without exceptions, even for
     * servers that would otherwise fail to connect.
     */
    @Test
    fun `registerMcpServers returns empty when SDK unavailable`() {
        val servers = mapOf(
            "foo" to mapOf("command" to "foo-cmd"),
            "bar" to mapOf("url" to "http://127.0.0.1:9999/mcp"),
        )
        val registered = registerMcpServers(servers)
        assertTrue("no servers should register on Android", registered.isEmpty())
    }

    /** TC-MCP-001-a: discoverMcpTools is inert on Android. */
    @Test
    fun `discoverMcpTools returns empty list`() {
        assertTrue(discoverMcpTools().isEmpty())
    }

    /** TC-MCP-001-a: getMcpStatus returns empty list. */
    @Test
    fun `getMcpStatus returns empty`() {
        assertTrue(getMcpStatus().isEmpty())
        assertTrue(probeMcpServerTools().isEmpty())
    }

    /** TC-MCP-005-a: shutdown helpers are idempotent no-ops. */
    @Test
    fun `shutdown helpers do not throw`() {
        // All of these are no-ops on Android; the only contract is "no throw".
        shutdownMcpServers()
        _killOrphanedMcpChildren()
        _stopMcpLoop()
        _ensureMcpLoop()
    }

    /** TC-MCP-003-a (Android path): tool handler factory returns JSON error. */
    @Test
    fun `makeToolHandler returns error json`() {
        val handler = _makeToolHandler("my-server", "my-tool", 5.0)
        val out = handler(mapOf("x" to 1))
        @Suppress("UNCHECKED_CAST")
        val parsed = gson.fromJson(out, Map::class.java) as Map<String, Any?>
        assertEquals("mcp tool not ported", parsed["error"])
    }

    /** TC-MCP-003-a (Android path): resource/prompt handler factories return errors. */
    @Test
    fun `resource and prompt handler factories return error json`() {
        val calls = listOf(
            _makeListResourcesHandler("s", 1.0),
            _makeReadResourceHandler("s", 1.0),
            _makeListPromptsHandler("s", 1.0),
            _makeGetPromptHandler("s", 1.0),
        )
        for (h in calls) {
            val out = h(emptyMap())
            @Suppress("UNCHECKED_CAST")
            val parsed = gson.fromJson(out, Map::class.java) as Map<String, Any?>
            assertTrue(
                "expected an error message, got: $parsed",
                parsed["error"]?.toString()?.isNotEmpty() == true,
            )
        }
    }

    /** TC-MCP-004-a: sanitizeMcpNameComponent strips invalid chars. */
    @Test
    fun `sanitizeMcpNameComponent replaces invalid chars`() {
        // Hermes's historical rule: only [A-Za-z0-9_-] survive; anything
        // else collapses to '_'. Hyphens are preserved by Kotlin regex.
        assertEquals("foo_bar", sanitizeMcpNameComponent("foo bar"))
        assertEquals("foo_bar_baz", sanitizeMcpNameComponent("foo/bar.baz"))
        assertEquals("a-b", sanitizeMcpNameComponent("a-b"))
        assertEquals("a_b_", sanitizeMcpNameComponent("a\u00a0b!"))
        // ASCII alphanumerics + '_' + '-' pass through unchanged.
        assertEquals("ok_name-01", sanitizeMcpNameComponent("ok_name-01"))
    }

    /** TC-MCP-004-a: _normalizeMcpInputSchema returns the input or an empty map. */
    @Test
    fun `normalizeMcpInputSchema returns input when present`() {
        val schema = mapOf("type" to "object", "properties" to emptyMap<String, Any?>())
        assertEquals(schema, _normalizeMcpInputSchema(schema))
        // Null input -> empty map, never null.
        assertTrue(_normalizeMcpInputSchema(null).isEmpty())
    }

    /** TC-MCP-005-a: _parseBoolish interprets common truthy strings. */
    @Test
    fun `parseBoolish handles truthy strings`() {
        assertTrue(_parseBoolish("true"))
        assertTrue(_parseBoolish("YES"))
        assertTrue(_parseBoolish("on"))
        assertTrue(_parseBoolish("1"))
        assertTrue(_parseBoolish(true))
        assertTrue(_parseBoolish(1))
    }

    /** TC-MCP-005-a: _parseBoolish falsy + default propagation. */
    @Test
    fun `parseBoolish handles falsy and null`() {
        assertFalse(_parseBoolish("false"))
        assertFalse(_parseBoolish("no"))
        assertFalse(_parseBoolish(0))
        // null -> default
        assertTrue(_parseBoolish(null, default = true))
        assertFalse(_parseBoolish(null, default = false))
    }

    /** TC-MCP-001-a: MCPServerTask defaults reflect disconnected state. */
    @Test
    fun `MCPServerTask default state is disconnected`() {
        val t = MCPServerTask("my-server")
        assertEquals("my-server", t.name)
        assertNull(t.session)
        assertFalse(t.connected)
        assertNull(t.lastError)
        assertTrue(t.tools.isEmpty())
        assertFalse(t._isHttp())
        assertNull(t._makeMessageHandler())
    }

    /** TC-MCP-005-a: _connectServer always yields a fresh disconnected task. */
    @Test
    fun `connectServer produces disconnected MCPServerTask`() = runBlocking {
        val t = _connectServer("x", mapOf("command" to "noop"))
        assertEquals("x", t.name)
        assertFalse(t.connected)
    }

    /** TC-MCP-003-a: SamplingHandler._error wraps message + code. */
    @Test
    fun `SamplingHandler error envelope`() {
        val handler = SamplingHandler("srv", emptyMap())
        val env = handler._error("nope", code = -42)
        @Suppress("UNCHECKED_CAST")
        val inner = env["error"] as Map<String, Any?>
        assertEquals("nope", inner["message"])
        assertEquals(-42, inner["code"])
    }

    /** TC-MCP-005-a: auth-error helpers are inert on Android. */
    @Test
    fun `auth error helpers are inert stubs`() {
        assertTrue(_getAuthErrorTypes().isEmpty())
        assertFalse(_isAuthError(RuntimeException("oops")))
        _bumpServerError("foo")  // no-op; just must not throw.
        _resetServerError("foo")
    }

    /** TC-MCP-005-a: _interruptedCallResult returns structured JSON. */
    @Test
    fun `interruptedCallResult returns structured error json`() {
        val s = _interruptedCallResult()
        @Suppress("UNCHECKED_CAST")
        val parsed = gson.fromJson(s, Map::class.java) as Map<String, Any?>
        assertEquals("MCP call interrupted", parsed["error"])
    }
}

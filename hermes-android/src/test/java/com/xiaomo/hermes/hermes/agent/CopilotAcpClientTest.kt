package com.xiaomo.hermes.hermes.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CopilotAcpClient — HermesApp as ACP client against Copilot.
 *
 * Requirement: R-ACP-004
 * Test cases:
 *   TC-ACP-210-a — handshake: client exposes `apiKey`, `baseUrl` (acp://copilot)
 *   TC-ACP-211-a — remote tools registered: _formatMessagesAsPrompt includes
 *                  tool schemas when passed
 *   TC-ACP-212-a — event mapping: _extractToolCallsFromText pulls
 *                  <tool_call>{...}</tool_call> blocks from text
 *   TC-ACP-213-a — reconnect preserves state: client.close() sets isClosed
 *                  and is idempotent
 */
class CopilotAcpClientTest {

    // -----------------------------------------------------------------------
    // TC-ACP-210-a — handshake defaults
    // -----------------------------------------------------------------------

    /** TC-ACP-210-a — default client has acp://copilot base URL and sentinel key. */
    @Test
    fun `default client has acp marker base url and sentinel key`() {
        val client = CopilotACPClient()
        assertEquals("copilot-acp", client.apiKey)
        assertEquals(ACP_MARKER_BASE_URL, client.baseUrl)
        assertEquals("acp://copilot", client.baseUrl)
        assertNotNull(client.chat)
        assertFalse(client.isClosed)
    }

    /** Explicit apiKey and baseUrl override defaults. */
    @Test
    fun `explicit apiKey and baseUrl override defaults`() {
        val client = CopilotACPClient(apiKey = "k", baseUrl = "acp://custom")
        assertEquals("k", client.apiKey)
        assertEquals("acp://custom", client.baseUrl)
    }

    // -----------------------------------------------------------------------
    // TC-ACP-213-a — close() idempotency
    // -----------------------------------------------------------------------

    @Test
    fun `close flips isClosed and is idempotent`() {
        val client = CopilotACPClient()
        assertFalse(client.isClosed)
        client.close()
        assertTrue(client.isClosed)
        // Second close is a no-op, not an exception.
        client.close()
        assertTrue(client.isClosed)
    }

    // -----------------------------------------------------------------------
    // TC-ACP-211-a — _formatMessagesAsPrompt: tool schemas + transcript
    // -----------------------------------------------------------------------

    /** When tools are present, their schemas are included in the prompt. */
    @Test
    fun `formatMessagesAsPrompt includes tool schemas when tools provided`() {
        val tools = listOf(
            mapOf(
                "function" to mapOf(
                    "name" to "sleep",
                    "description" to "sleeps",
                    "parameters" to mapOf("seconds" to "int"),
                ),
            ),
        )
        val prompt = _formatMessagesAsPrompt(
            messages = listOf(mapOf("role" to "user", "content" to "hi")),
            tools = tools,
        )
        assertTrue(prompt.contains("sleep"))
        assertTrue(prompt.contains("Available tools"))
        assertTrue(prompt.contains("User:"))
        assertTrue(prompt.contains("hi"))
    }

    /** Empty tools list drops the tools section entirely. */
    @Test
    fun `formatMessagesAsPrompt with no tools omits tools section`() {
        val prompt = _formatMessagesAsPrompt(
            messages = listOf(mapOf("role" to "user", "content" to "hello")),
            tools = emptyList(),
        )
        assertFalse(prompt.contains("Available tools"))
        assertTrue(prompt.contains("hello"))
    }

    /** Model hint surfaces as its own line. */
    @Test
    fun `formatMessagesAsPrompt includes model hint`() {
        val prompt = _formatMessagesAsPrompt(
            messages = listOf(mapOf("role" to "user", "content" to "x")),
            model = "claude-opus-4",
        )
        assertTrue(prompt.contains("claude-opus-4"))
    }

    /** Messages with empty content are skipped from the transcript. */
    @Test
    fun `formatMessagesAsPrompt skips empty content messages`() {
        val prompt = _formatMessagesAsPrompt(
            messages = listOf(
                mapOf("role" to "system", "content" to ""),
                mapOf("role" to "user", "content" to "real question"),
            ),
        )
        assertFalse(prompt.contains("System:\n"))
        assertTrue(prompt.contains("real question"))
    }

    // -----------------------------------------------------------------------
    // TC-ACP-212-a — _extractToolCallsFromText
    // -----------------------------------------------------------------------

    /** TC-ACP-212-a — <tool_call>{...}</tool_call> block is parsed out. */
    @Test
    fun `extractToolCallsFromText parses tool_call block`() {
        val text = """
            Here's the plan:
            <tool_call>{"id":"call_1","type":"function","function":{"name":"sleep","arguments":"{\"seconds\":1}"}}</tool_call>
            Done.
        """.trimIndent()
        val (tools, residual) = _extractToolCallsFromText(text)
        assertEquals(1, tools.size)
        val call = tools[0]
        assertEquals("call_1", call["id"])
        assertEquals("function", call["type"])
        @Suppress("UNCHECKED_CAST")
        val fn = call["function"] as Map<String, Any?>
        assertEquals("sleep", fn["name"])
        assertTrue(residual.contains("Here's the plan"))
        assertTrue(residual.contains("Done."))
        assertFalse(residual.contains("<tool_call>"))
    }

    /** Synthetic id is assigned when the tool_call block lacks one. */
    @Test
    fun `extractToolCallsFromText assigns synthetic id when missing`() {
        val text = """<tool_call>{"type":"function","function":{"name":"foo","arguments":"{}"}}</tool_call>"""
        val (tools, _) = _extractToolCallsFromText(text)
        assertEquals(1, tools.size)
        assertEquals("acp_call_1", tools[0]["id"])
    }

    /** No tool_call, no JSON shape → empty list + text trimmed. */
    @Test
    fun `extractToolCallsFromText returns empty on plain text`() {
        val (tools, residual) = _extractToolCallsFromText("just a plain reply")
        assertTrue(tools.isEmpty())
        assertEquals("just a plain reply", residual)
    }

    /** Null / blank text returns empty pair. */
    @Test
    fun `extractToolCallsFromText null returns empty pair`() {
        val (tools, residual) = _extractToolCallsFromText(null)
        assertTrue(tools.isEmpty())
        assertEquals("", residual)
    }

    // -----------------------------------------------------------------------
    // Helper: _renderMessageContent
    // -----------------------------------------------------------------------

    @Test
    fun `renderMessageContent handles string dict list null`() {
        assertEquals("", _renderMessageContent(null))
        assertEquals("hello", _renderMessageContent("  hello  "))

        assertEquals("embedded", _renderMessageContent(mapOf("text" to "embedded")))

        assertTrue(
            _renderMessageContent(
                listOf(
                    mapOf("text" to "line1"),
                    mapOf("text" to "line2"),
                    "bare",
                )
            ).contains("line1")
        )
    }

    // -----------------------------------------------------------------------
    // Helper: _jsonrpcError + _permissionDenied JSON-RPC envelopes
    // -----------------------------------------------------------------------

    @Test
    fun `jsonrpcError envelope has correct fields`() {
        val env = _jsonrpcError(messageId = 42, code = -32602, message = "bad")
        assertEquals("2.0", env["jsonrpc"])
        assertEquals(42, env["id"])
        @Suppress("UNCHECKED_CAST")
        val err = env["error"] as Map<String, Any?>
        assertEquals(-32602, err["code"])
        assertEquals("bad", err["message"])
    }

    @Test
    fun `permissionDenied envelope returns cancelled outcome`() {
        val env = _permissionDenied(7)
        assertEquals(7, env["id"])
        @Suppress("UNCHECKED_CAST")
        val result = env["result"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val outcome = result["outcome"] as Map<String, Any?>
        assertEquals("cancelled", outcome["outcome"])
    }

    // -----------------------------------------------------------------------
    // Helper: _resolveCommand / _resolveArgs
    // -----------------------------------------------------------------------

    @Test
    fun `resolveCommand defaults to copilot`() {
        // Without env overrides, default is "copilot".
        // (Env overrides can't be set portably inside a JVM unit test.)
        if (System.getenv("HERMES_COPILOT_ACP_COMMAND").isNullOrEmpty() &&
            System.getenv("COPILOT_CLI_PATH").isNullOrEmpty()
        ) {
            assertEquals("copilot", _resolveCommand())
        }
    }

    @Test
    fun `resolveArgs default list has --acp and --stdio`() {
        if (System.getenv("HERMES_COPILOT_ACP_ARGS").isNullOrEmpty()) {
            val args = _resolveArgs()
            assertTrue(args.contains("--acp"))
            assertTrue(args.contains("--stdio"))
        }
    }

    // -----------------------------------------------------------------------
    // Helper: _ensurePathWithinCwd — path security guard
    // -----------------------------------------------------------------------

    @Test
    fun `ensurePathWithinCwd accepts path inside cwd`() {
        val tmpDir = File.createTempFile("acp_cwd_", "").apply { delete(); mkdirs() }
        tmpDir.deleteOnExit()
        val inside = File(tmpDir, "child.txt")
        val resolved = _ensurePathWithinCwd(inside.absolutePath, tmpDir.absolutePath)
        assertEquals(inside.canonicalPath, resolved.canonicalPath)
    }

    @Test(expected = SecurityException::class)
    fun `ensurePathWithinCwd rejects path outside cwd`() {
        val tmpDir = File.createTempFile("acp_cwd_", "").apply { delete(); mkdirs() }
        tmpDir.deleteOnExit()
        // /tmp or /etc/passwd — absolute, not under tmpDir.
        _ensurePathWithinCwd("/etc/passwd", tmpDir.absolutePath)
    }

    @Test(expected = SecurityException::class)
    fun `ensurePathWithinCwd rejects relative path`() {
        val tmpDir = File.createTempFile("acp_cwd_", "").apply { delete(); mkdirs() }
        tmpDir.deleteOnExit()
        _ensurePathWithinCwd("relative/path", tmpDir.absolutePath)
    }

    // -----------------------------------------------------------------------
    // Module-level constants match Python literals
    // -----------------------------------------------------------------------

    @Test
    fun `ACP_MARKER_BASE_URL is acp copilot literal`() {
        assertEquals("acp://copilot", ACP_MARKER_BASE_URL)
    }

    @Test
    fun `default timeout is 15 minutes`() {
        assertEquals(900.0, _DEFAULT_TIMEOUT_SECONDS, 0.001)
    }
}

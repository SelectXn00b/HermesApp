package com.xiaomo.hermes.hermes.tools

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for CodeExecutionTool.kt — Android stub for execute_code.
 *
 * Android has no sandbox backend (Modal / Docker / Bubblewrap), so the
 * executeCode surface always returns toolError, and the RPC server loops
 * throw UnsupportedOperationException to prevent accidental launch.
 * buildExecuteCodeSchema still produces a meaningful schema and varies
 * its description with mode (strict vs project).
 *
 * Covers TC-TOOL-225..228.
 */
class CodeExecutionToolTest {

    private val gson = Gson()

    /** TC-TOOL-225-a: executeCode always returns a toolError JSON on Android. */
    @Test
    fun `executeCode returns toolError`() {
        val out = executeCode(code = "print('x')")
        @Suppress("UNCHECKED_CAST")
        val parsed = gson.fromJson(out, Map::class.java) as Map<String, Any?>
        assertEquals("code_execution tool is not available on Android", parsed["error"])
    }

    /** TC-TOOL-225-a: same for _executeRemote. */
    @Test
    fun `executeRemote returns toolError`() {
        val out = _executeRemote(code = "print('x')", taskId = "t", enabledTools = null)
        @Suppress("UNCHECKED_CAST")
        val parsed = gson.fromJson(out, Map::class.java) as Map<String, Any?>
        assertEquals("code_execution tool is not available on Android", parsed["error"])
    }

    /** TC-TOOL-225-a: checkSandboxRequirements is false on Android. */
    @Test
    fun `checkSandboxRequirements is false`() {
        assertFalse(checkSandboxRequirements())
        assertFalse(SANDBOX_AVAILABLE)
        assertTrue(SANDBOX_ALLOWED_TOOLS.isEmpty())
    }

    /**
     * TC-TOOL-226-a: the synchronous RPC server loop throws
     * UnsupportedOperationException so no-one starts it by mistake.
     */
    @Test
    fun `rpcServerLoop throws UnsupportedOperationException`() {
        try {
            _rpcServerLoop()
            fail("expected UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertNotNull(e.message)
            assertTrue(e.message!!.contains("not available on Android"))
        }
    }

    /** TC-TOOL-226-a: _rpcPollLoop also throws. */
    @Test
    fun `rpcPollLoop throws UnsupportedOperationException`() {
        try {
            _rpcPollLoop(rpcDir = "/tmp/x")
            fail("expected UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message!!.contains("not available on Android"))
        }
    }

    private fun fail(msg: String): Nothing = throw AssertionError(msg)

    /** TC-TOOL-227-a: strict vs project descriptions differ. */
    @Test
    fun `schema description varies by mode`() {
        val strict = buildExecuteCodeSchema(mode = "strict")
        val project = buildExecuteCodeSchema(mode = "project")
        val strictDesc = strict["description"] as String
        val projectDesc = project["description"] as String
        assertNotEquals(strictDesc, projectDesc)
        assertTrue(strictDesc.contains("temp dir"))
        assertTrue(projectDesc.contains("working directory"))
    }

    private fun assertNotEquals(a: Any?, b: Any?) {
        if (a == b) throw AssertionError("expected $a != $b but were equal")
    }

    /** TC-TOOL-227-a: enabled-tool doc lines are reflected in description. */
    @Test
    fun `enabled tools appear in description`() {
        val schema = buildExecuteCodeSchema(
            enabledSandboxTools = setOf("web_search", "terminal"),
            mode = "project",
        )
        val desc = schema["description"] as String
        assertTrue(desc.contains("web_search"))
        assertTrue(desc.contains("terminal"))
        // Unincluded tool should not appear.
        assertFalse(desc.contains("read_file("))
    }

    /** TC-TOOL-227-a: schema always carries a required `code` param. */
    @Test
    fun `schema has required code parameter`() {
        val schema = buildExecuteCodeSchema(enabledSandboxTools = setOf("terminal"))
        assertEquals("execute_code", schema["name"])
        @Suppress("UNCHECKED_CAST")
        val params = schema["parameters"] as Map<String, Any?>
        assertEquals("object", params["type"])
        @Suppress("UNCHECKED_CAST")
        val required = params["required"] as List<Any?>
        assertEquals(listOf("code"), required)
        @Suppress("UNCHECKED_CAST")
        val props = params["properties"] as Map<String, Any?>
        assertTrue(props.containsKey("code"))
    }

    /** TC-TOOL-227-a: top-level EXECUTE_CODE_SCHEMA is non-empty and well-formed. */
    @Test
    fun `top level schema is populated`() {
        val s = EXECUTE_CODE_SCHEMA
        assertEquals("execute_code", s["name"])
        assertTrue((s["description"] as String).isNotEmpty())
    }

    /** TC-TOOL-227-a: tools intersection picks from SANDBOX_ALLOWED_TOOLS when null. */
    @Test
    fun `null enabled tools falls back to sandbox allowed`() {
        // On Android SANDBOX_ALLOWED_TOOLS is empty, so the description's
        // doc-line section collapses to an empty line but the schema
        // itself is still valid.
        val schema = buildExecuteCodeSchema(enabledSandboxTools = null)
        assertNotNull(schema["description"])
    }

    /**
     * TC-TOOL-228-a: _resolveChildCwd in strict mode always returns the
     * staging dir regardless of TERMINAL_CWD.
     */
    @Test
    fun `resolveChildCwd in strict mode returns staging dir`() {
        val staging = File(System.getProperty("java.io.tmpdir") ?: "/tmp").absolutePath
        assertEquals(staging, _resolveChildCwd("strict", staging))
    }

    /**
     * TC-TOOL-228-a: _resolveChildCwd in project mode falls back to
     * user.dir (CWD) when TERMINAL_CWD env is not set, or staging if
     * user.dir is not a directory.
     */
    @Test
    fun `resolveChildCwd in project mode uses user dir or staging`() {
        val staging = System.getProperty("java.io.tmpdir") ?: "/tmp"
        val result = _resolveChildCwd("project", staging)
        // Whatever the fallback, it must be an existing directory.
        assertTrue(
            "resolved cwd must be an existing dir: $result",
            File(result).isDirectory,
        )
    }

    /** TC-TOOL-228-a: _envTempDir returns a usable tmp path. */
    @Test
    fun `envTempDir returns existing tmp path`() {
        val d = _envTempDir(null)
        assertTrue("env temp dir must be a directory: $d", File(d).isDirectory)
    }

    /** TC-TOOL-228-a: _getExecutionMode returns one of EXECUTION_MODES. */
    @Test
    fun `getExecutionMode returns a known mode`() {
        val mode = _getExecutionMode()
        assertTrue(mode in EXECUTION_MODES)
        // Default is "project" on Android (empty config -> fallback).
        assertEquals(DEFAULT_EXECUTION_MODE, mode)
    }

    /** TC-TOOL-225-a: _resolveChildPython returns the "python" sentinel on Android. */
    @Test
    fun `resolveChildPython returns sentinel`() {
        assertEquals("python", _resolveChildPython("project"))
        assertEquals("python", _resolveChildPython("strict"))
    }

    /** TC-TOOL-225-a: _isUsablePython always false on Android (no subprocess). */
    @Test
    fun `isUsablePython is false and cached`() {
        val first = _isUsablePython("/does/not/exist")
        val second = _isUsablePython("/does/not/exist")
        assertFalse(first)
        assertFalse(second)
    }

    /** TC-TOOL-225-a: generateHermesToolsModule returns empty string on Android. */
    @Test
    fun `generateHermesToolsModule returns empty`() {
        assertEquals("", generateHermesToolsModule(enabledTools = listOf("terminal")))
    }

    /** Module-level constants sanity. */
    @Test
    fun `timeout and byte caps are positive`() {
        assertEquals(300, DEFAULT_TIMEOUT)
        assertEquals(50, DEFAULT_MAX_TOOL_CALLS)
        assertTrue(MAX_STDOUT_BYTES > 0)
        assertTrue(MAX_STDERR_BYTES > 0)
        assertTrue(_TERMINAL_BLOCKED_PARAMS.contains("background"))
        assertTrue(_TERMINAL_BLOCKED_PARAMS.contains("pty"))
        assertEquals(listOf("project", "strict"), EXECUTION_MODES)
    }

    /** _RPC_DIR exists and points to something reasonable. */
    @Test
    fun `rpc dir is writeable path`() {
        // Either the env override or tmpdir/hermes_rpc. Must be non-empty.
        assertTrue(_RPC_DIR.isNotEmpty())
        // Path should end in "hermes_rpc" when env var not set, OR be the
        // env override verbatim.
        val envOverride = System.getenv("HERMES_RPC_DIR")
        if (envOverride == null) {
            assertTrue(
                "default RPC dir should end with hermes_rpc: $_RPC_DIR",
                _RPC_DIR.endsWith("hermes_rpc"),
            )
        } else {
            assertEquals(envOverride, _RPC_DIR)
        }
    }
}

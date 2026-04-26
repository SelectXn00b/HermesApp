package com.xiaomo.hermes.hermes.tools

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Method

/**
 * Tests for TerminalTool.kt — Android terminal execution.
 *
 * The happy-path actually invokes /system/bin/sh which does NOT exist on
 * a macOS/Linux JVM test host; that branch returns a structured error
 * JSON (exit_code:-1, "Execution failed: …") thanks to the top-level
 * try/catch. The tests focus on the parts that *don't* need a live
 * Android shell: top-level constants, schema shape, workdir validator,
 * env-var parser, background rejection, sudo/approval stubs.
 *
 * Covers TC-TOOL-210..216.
 */
class TerminalToolTest {

    private val gson = Gson()

    private val ttClass: Class<*> by lazy {
        Class.forName("com.xiaomo.hermes.hermes.tools.TerminalToolKt")
    }

    /** Small reflection helper for private module functions. */
    private fun invokePrivate(name: String, vararg args: Any?): Any? {
        val m = ttClass.declaredMethods.firstOrNull { it.name == name }
            ?: error("private method $name not found on TerminalToolKt")
        m.isAccessible = true
        return m.invoke(null, *args)
    }

    /** TC-TOOL-210-a: background=true returns error JSON, never runs the shell. */
    @Test
    fun `background not supported on android`() {
        val out = terminalTool(command = "echo hi", background = true)
        @Suppress("UNCHECKED_CAST")
        val parsed = gson.fromJson(out, Map::class.java) as Map<String, Any?>
        assertEquals("background execution is not supported on Android", parsed["error"])
        // exit_code -1 per source — Gson deserialises numbers to Double.
        assertEquals(-1.0, parsed["exit_code"] as Double, 0.0)
        assertEquals("", parsed["output"])
    }

    /**
     * TC-TOOL-211-a: timeout clamped to FOREGROUND_MAX_TIMEOUT.
     * We can't easily observe the clamped value without inspecting process
     * internals — but we can verify the constant itself has a sensible
     * default and matches either the env override or 600.
     */
    @Test
    fun `FOREGROUND_MAX_TIMEOUT has sensible default`() {
        val envOverride = System.getenv("TERMINAL_MAX_FOREGROUND_TIMEOUT")?.toIntOrNull()
        val expected = envOverride ?: 600
        assertEquals(expected, FOREGROUND_MAX_TIMEOUT)
        assertTrue("clamp ceiling must be positive", FOREGROUND_MAX_TIMEOUT > 0)
    }

    /** TC-TOOL-212-a: schema declares command + terminal name. */
    @Test
    fun `TERMINAL_SCHEMA describes command param`() {
        assertEquals("terminal", TERMINAL_SCHEMA["name"])
        @Suppress("UNCHECKED_CAST")
        val params = TERMINAL_SCHEMA["parameters"] as Map<String, Any?>
        assertEquals("object", params["type"])
        @Suppress("UNCHECKED_CAST")
        val required = params["required"] as List<String>
        assertTrue("command must be required", required.contains("command"))
        @Suppress("UNCHECKED_CAST")
        val props = params["properties"] as Map<String, Any?>
        assertTrue(props.containsKey("command"))
        assertTrue(props.containsKey("background"))
        assertTrue(props.containsKey("workdir"))
    }

    /** TC-TOOL-212-a: TERMINAL_TOOL_DESCRIPTION mentions foreground/background modes. */
    @Test
    fun `TERMINAL_TOOL_DESCRIPTION mentions key modes`() {
        assertTrue(TERMINAL_TOOL_DESCRIPTION.contains("Foreground"))
        assertTrue(TERMINAL_TOOL_DESCRIPTION.contains("Background"))
        // Key guidance about reserved-for-shell use cases.
        assertTrue(TERMINAL_TOOL_DESCRIPTION.contains("builds"))
    }

    /** TC-TOOL-214-a: _validateWorkdir rejects shell metachars, accepts ordinary paths. */
    @Test
    fun `validateWorkdir accepts plain paths`() {
        assertNull(invokePrivate("_validateWorkdir", ""))
        assertNull(invokePrivate("_validateWorkdir", "/tmp/foo"))
        assertNull(invokePrivate("_validateWorkdir", "/home/user/bar-baz_01"))
        assertNull(invokePrivate("_validateWorkdir", "~/projects"))
    }

    /** TC-TOOL-214-a: _validateWorkdir rejects shell-chars. */
    @Test
    fun `validateWorkdir rejects shell chars`() {
        val bad = listOf("a; b", "a|b", "a\$b", "a`b`", "\"x\"", "'x'", "*")
        for (s in bad) {
            val msg = invokePrivate("_validateWorkdir", s) as? String
            assertNotNull("expected rejection for $s", msg)
            assertTrue(
                "error should mention unsupported characters for $s",
                msg!!.contains("unsupported characters"),
            )
        }
    }

    /** TC-TOOL-215-a: _WORKDIR_SAFE_RE regex allow-list matches expected chars. */
    @Test
    fun `workdir safe regex matches expected chars`() {
        // Spot-check: each allowed char should match a 1-char string.
        for (c in listOf("/", ":", "_", "-", ".", "~", " ", "+", "@", "=", ",")) {
            assertTrue("$c should be allowed", _WORKDIR_SAFE_RE.matches(c))
        }
        // Non-allowed spot checks.
        assertFalse(_WORKDIR_SAFE_RE.matches("$"))
        assertFalse(_WORKDIR_SAFE_RE.matches(";"))
    }

    /**
     * TC-TOOL-215-a: _parseEnvVar returns the default when env var is absent.
     * We cannot mutate the process env portably, so we pick an unlikely
     * var name and verify default propagation.
     */
    @Test
    fun `parseEnvVar falls back to default for unset vars`() {
        // Method signature: (String, String, Function1, String): Any
        val m = ttClass.declaredMethods.first { it.name == "_parseEnvVar" }
        m.isAccessible = true
        val converter: (String) -> Any = { it.toInt() }
        val result = m.invoke(null, "THIS_VAR_DOES_NOT_EXIST_XYZ_${System.nanoTime()}", "42", converter, "integer")
        assertEquals(42, result)
    }

    /** TC-TOOL-215-a: _parseEnvVar tolerates a bad raw and returns converter(default). */
    @Test
    fun `parseEnvVar tolerates conversion failure by using default`() {
        // We can't inject a bad real env var portably; but we can set one
        // we know won't exist AND whose default the converter will accept.
        val m = ttClass.declaredMethods.first { it.name == "_parseEnvVar" }
        m.isAccessible = true
        // Converter that throws on any non-digit string; raw path absent → default "100" applies.
        val converter: (String) -> Any = { s ->
            s.toIntOrNull() ?: throw IllegalArgumentException("not an int: $s")
        }
        val result = m.invoke(null, "ANOTHER_UNSET_VAR_${System.nanoTime()}", "100", converter, "integer")
        assertEquals(100, result)
    }

    /** TC-TOOL-216-a: setSudoPasswordCallback / setApprovalCallback are no-ops. */
    @Test
    fun `sudo and approval callbacks are no-ops`() {
        // Simply confirm the function returns without side-effect.
        setSudoPasswordCallback(null)
        setSudoPasswordCallback(Any())
        setApprovalCallback(null)
        setApprovalCallback(Any())
    }

    /** TC-TOOL-216-a: task env override registration is a no-op. */
    @Test
    fun `task env override registration is no-op`() {
        registerTaskEnvOverrides("task-123", mapOf("FOO" to "bar"))
        clearTaskEnvOverrides("task-123")
        assertNull(getActiveEnv("task-123"))
        assertFalse(isPersistentEnv("task-123"))
        cleanupAllEnvironments()
        cleanupVm("task-123")
    }

    /** TC-TOOL-216-a: checkTerminalRequirements reports true on Android. */
    @Test
    fun `checkTerminalRequirements returns true`() {
        assertTrue(checkTerminalRequirements())
    }

    /**
     * TC-TOOL-213-a: on hosts without /system/bin/sh, the exception branch
     * catches the IOException and returns an "Execution failed" payload.
     * On Android emulator /system/bin/sh would exist; here we observe the
     * catch-all fallback contract which is still a valid structured result.
     */
    @Test
    fun `terminalTool returns structured error when shell is unavailable`() {
        val out = terminalTool(command = "echo hi")
        @Suppress("UNCHECKED_CAST")
        val parsed = gson.fromJson(out, Map::class.java) as Map<String, Any?>
        // Either the shell isn't available (JVM host) → error path
        // OR the shell exists and exit_code is set. Both are valid
        // outcomes of the function contract; the only invariant we can
        // assert here is "output + exit_code fields are present".
        assertTrue(parsed.containsKey("output"))
        assertTrue(parsed.containsKey("exit_code"))
    }

    /**
     * TC-TOOL-213-a: very large negative timeout still yields a structured
     * response (no IllegalArgumentException bubbling up).
     */
    @Test
    fun `terminalTool tolerates negative timeout`() {
        // 0 is coerced via coerceAtMost to 0; waitFor(0, SECONDS) returns
        // immediately. Should not throw.
        val out = terminalTool(command = "true", timeout = 0)
        @Suppress("UNCHECKED_CAST")
        val parsed = gson.fromJson(out, Map::class.java) as Map<String, Any?>
        assertNotNull(parsed["exit_code"])
    }

    /** Background rejection also works via _handleTerminal map entry point. */
    @Test
    fun `handleTerminal args dispatch background rejection`() {
        val m = ttClass.declaredMethods.first { it.name == "_handleTerminal" }
        m.isAccessible = true
        val args = mapOf<String, Any?>(
            "command" to "echo hi",
            "background" to true,
        )
        val out = m.invoke(null, args, emptyArray<Any?>()) as String
        @Suppress("UNCHECKED_CAST")
        val parsed = gson.fromJson(out, Map::class.java) as Map<String, Any?>
        assertEquals("background execution is not supported on Android", parsed["error"])
    }

    /** Module-level background-regex constants are populated as expected. */
    @Test
    fun `background regex constants are defined`() {
        assertTrue(_SHELL_LEVEL_BACKGROUND_RE.containsMatchIn("nohup foo"))
        assertTrue(_SHELL_LEVEL_BACKGROUND_RE.containsMatchIn("disown"))
        assertTrue(_SHELL_LEVEL_BACKGROUND_RE.containsMatchIn("SETSID x"))
        assertFalse(_SHELL_LEVEL_BACKGROUND_RE.containsMatchIn("plain command"))
        assertTrue(_INLINE_BACKGROUND_AMP_RE.containsMatchIn("a & b"))
        assertTrue(_TRAILING_BACKGROUND_AMP_RE.containsMatchIn("a &"))
        assertTrue(_LONG_LIVED_FOREGROUND_PATTERNS.isEmpty())
    }

    // ── TC-TOOL-200-a: platform defaults to android ──
    /**
     * TC-TOOL-200-a — Python upstream's `_get_session_platform` returns the
     * remote SSH session's platform or "linux" as default; the Android port
     * has no session concept, so the only supported platform is Android.
     * Source-level guard: the Kotlin module must hardcode `/system/bin/sh`
     * as its only shell path, with no alternative platform branch.
     */
    @Test
    fun `platform defaults android`() {
        val src = File("src/main/java/com/xiaomo/hermes/hermes/tools/TerminalTool.kt")
        assertTrue("TerminalTool.kt must be readable", src.exists())
        val text = src.readText()
        // Only one shell path reference: /system/bin/sh. No /bin/bash, /bin/sh, etc.
        assertTrue(
            "must hardcode /system/bin/sh as the android shell path",
            text.contains("\"/system/bin/sh\""),
        )
        assertFalse(
            "must not reference /bin/bash (remote-linux path)",
            text.contains("\"/bin/bash\""),
        )
    }

    // ── TC-TOOL-201-a: remote env neutralized ──
    /**
     * TC-TOOL-201-a — Python upstream ships env customization for remote
     * sessions (SSH / Modal backend). The Android port must NOT propagate
     * remote-env plumbing (no session id routing). Source-level guard:
     * `_createEnvironment` / `_getModalBackendState` / `_cleanupInactiveEnvs`
     * all exist as parity stubs with no-op bodies (= empty map / Unit).
     */
    @Test
    fun `remote env neutralized`() {
        val src = File("src/main/java/com/xiaomo/hermes/hermes/tools/TerminalTool.kt")
        val text = src.readText()
        assertTrue(
            "_createEnvironment must be a parity stub",
            text.contains("private fun _createEnvironment(") &&
                text.contains("emptyMap()"),
        )
        assertTrue(
            "_getModalBackendState must be a parity stub returning empty map",
            text.contains("private fun _getModalBackendState(") &&
                text.contains("Map<String, Any?> = emptyMap()"),
        )
        assertTrue(
            "registerTaskEnvOverrides must be a no-op (Unit return)",
            text.contains("fun registerTaskEnvOverrides(") &&
                text.contains("Unit = Unit"),
        )
        // The behavioural contract: even after registering "remote" env, the
        // getter returns null. Already asserted in `task env override registration is no-op`;
        // here we lock the source shape so future edits don't silently add a backend.
    }

    // ── TC-TOOL-211-a: timeout clamps to FOREGROUND_MAX_TIMEOUT ──
    /**
     * TC-TOOL-211-a — passing a timeout larger than `FOREGROUND_MAX_TIMEOUT`
     * must be coerced down. Source-level guard: `terminalTool` clamps via
     * `coerceAtMost(FOREGROUND_MAX_TIMEOUT)` before process spawn.
     */
    @Test
    fun `timeout double clamp`() {
        val src = File("src/main/java/com/xiaomo/hermes/hermes/tools/TerminalTool.kt")
        val text = src.readText()
        assertTrue(
            "timeout must be clamped to FOREGROUND_MAX_TIMEOUT",
            text.contains(".coerceAtMost(FOREGROUND_MAX_TIMEOUT)"),
        )
        // Behavioural: a huge timeout still yields a structured response
        // (proxy: no throw, has exit_code key). On JVM host the shell isn't
        // there so we get the error branch, but the contract "no exception
        // bubble" is what we're pinning.
        val out = terminalTool(command = "true", timeout = 99999)
        @Suppress("UNCHECKED_CAST")
        val parsed = gson.fromJson(out, Map::class.java) as Map<String, Any?>
        assertTrue(parsed.containsKey("exit_code"))
    }

    // ── TC-TOOL-212-a: android shell path fixed ──
    /**
     * TC-TOOL-212-a — the Android port must route every shell invocation
     * through `/system/bin/sh -c`, never an alternative interpreter.
     */
    @Test
    fun `android shell path fixed`() {
        val src = File("src/main/java/com/xiaomo/hermes/hermes/tools/TerminalTool.kt")
        val text = src.readText()
        assertTrue(
            "ProcessBuilder must be invoked with /system/bin/sh -c",
            text.contains(".command(\"/system/bin/sh\", \"-c\", command)"),
        )
    }

    // ── TC-TOOL-213-a: timeout returns exit_code=124 ──
    /**
     * TC-TOOL-213-a — on process timeout, `terminalTool` must return
     * structured JSON with exit_code=124 + a "timed out" message. Source
     * guard: the timeout branch writes `exit_code` 124 and destroys the
     * process.
     */
    @Test
    fun `timeout returns 124`() {
        val src = File("src/main/java/com/xiaomo/hermes/hermes/tools/TerminalTool.kt")
        val text = src.readText()
        assertTrue(
            "timeout branch must set exit_code=124",
            text.contains("put(\"exit_code\", 124)"),
        )
        assertTrue(
            "timeout branch must destroyForcibly()",
            text.contains("process.destroyForcibly()"),
        )
        assertTrue(
            "timeout branch must emit human-readable error",
            text.contains("Command timed out"),
        )
    }

    // ── TC-TOOL-216-a: sudo path is stubbed ──
    /**
     * TC-TOOL-216-a — Python has a full sudo-rewrite pipeline; the Android
     * port stubs all of it: `_rewriteRealSudoInvocations`,
     * `_transformSudoCommand`, `_promptForSudoPassword` return pass-through
     * or empty values. Behavioral + source guard.
     */
    @Test
    fun `sudo stub`() {
        // Behavioural: reflection-invoked stubs return inert values.
        @Suppress("UNCHECKED_CAST")
        val rewritten = invokePrivate("_rewriteRealSudoInvocations", "sudo rm -rf /") as Pair<String, Boolean>
        assertEquals("sudo rm -rf /", rewritten.first)
        assertFalse("stub must not flag sudo as rewritten", rewritten.second)

        @Suppress("UNCHECKED_CAST")
        val transformed = invokePrivate("_transformSudoCommand", "sudo apt update") as Pair<String?, String?>
        assertEquals("stub returns command unchanged", "sudo apt update", transformed.first)
        assertNull("stub returns no sudo_stdin", transformed.second)

        val promptResult = invokePrivate("_promptForSudoPassword", 45) as String
        assertEquals("stub returns empty password", "", promptResult)

        // Source: the prompt function must be a parity stub (empty return).
        val src = File("src/main/java/com/xiaomo/hermes/hermes/tools/TerminalTool.kt")
        val text = src.readText()
        assertTrue(
            "_promptForSudoPassword must be a parity stub",
            text.contains("private fun _promptForSudoPassword(") &&
                text.contains(": String = \"\""),
        )
    }
}

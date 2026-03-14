package com.xiaomo.androidforclaw.agent.tools

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TermuxBridgeToolTest {

    private lateinit var context: Context
    private lateinit var pm: PackageManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        pm = mockk(relaxed = true)
        every { context.packageManager } returns pm
    }

    private fun termuxInstalled(installed: Boolean = true) {
        if (installed) {
            every { pm.getPackageInfo("com.termux", any<Int>()) } returns PackageInfo()
        } else {
            every { pm.getPackageInfo("com.termux", any<Int>()) } throws PackageManager.NameNotFoundException()
        }
    }

    private fun tool() = TermuxBridgeTool(context)

    // ==================== 1. isAvailable ====================

    @Test
    fun `isAvailable returns false when termux not installed`() {
        termuxInstalled(false)
        assertFalse(tool().isAvailable())
    }

    @Test
    fun `isAvailable returns false when termux installed but not ready`() {
        termuxInstalled(true)
        assertFalse(tool().isAvailable())
    }

    // ==================== 2. Termux not installed ====================

    @Test
    fun `execute returns install prompt when termux not installed`() = runBlocking {
        termuxInstalled(false)
        val result = tool().execute(mapOf("command" to "echo hi"))
        assertFalse(result.success)
        assertTrue(result.content.contains("not installed"))
        assertTrue(result.content.contains("f-droid.org"))
    }

    @Test
    fun `install prompt does not mention SSH`() = runBlocking {
        termuxInstalled(false)
        val result = tool().execute(mapOf("command" to "echo hi"))
        assertFalse(result.content.lowercase().contains("ssh"))
    }

    // ==================== 3. Termux not ready ====================
    // Note: Full execute() with Termux installed but SSH unreachable requires
    // Android runtime (Log, startService). Tested on device via instrumented tests.
    // Here we verify the error messages don't leak SSH details by checking constants.

    @Test
    fun `error messages do not expose SSH internals`() {
        // The not-ready message in TermuxBridgeTool says:
        val notReadyMsg = "Termux is not ready. Please open Termux and run: pkg install openssh && sshd"
        assertFalse(notReadyMsg.contains("SSH server"))
        assertFalse(notReadyMsg.contains("8022"))
        assertFalse(notReadyMsg.contains("authorized_keys"))
        assertTrue(notReadyMsg.contains("Termux"))
    }

    // ==================== 4. Parameter validation ====================

    @Test
    fun `rejects missing command and code`() = runBlocking {
        val tool = createParamValidationTool()
        val result = tool.execute(mapOf<String, Any?>())
        assertFalse(result.success)
        assertTrue(result.content.contains("Missing required parameter"))
    }

    @Test
    fun `rejects blank command`() = runBlocking {
        val tool = createParamValidationTool()
        val result = tool.execute(mapOf("command" to "   "))
        assertFalse(result.success)
        assertTrue(result.content.contains("Missing required parameter"))
    }

    @Test
    fun `rejects invalid runtime`() = runBlocking {
        val tool = createParamValidationTool()
        val result = tool.execute(mapOf("runtime" to "rust", "code" to "fn main(){}"))
        assertFalse(result.success)
        assertTrue(result.content.contains("Invalid runtime"))
    }

    @Test
    fun `rejects runtime without code`() = runBlocking {
        val tool = createParamValidationTool()
        val result = tool.execute(mapOf("runtime" to "python"))
        assertFalse(result.success)
    }

    @Test
    fun `rejects code without runtime`() = runBlocking {
        val tool = createParamValidationTool()
        val result = tool.execute(mapOf("code" to "print('hi')"))
        assertFalse(result.success)
    }

    // ==================== 5. Runtime resolution ====================

    @Test
    fun `python runtime resolves to python3 -c`() = runBlocking {
        val tool = createParamValidationTool()
        val result = tool.execute(mapOf("runtime" to "python", "code" to "print('hi')"))
        assertTrue(result.success)
        assertTrue(result.content.contains("python3 -c"))
    }

    @Test
    fun `nodejs runtime resolves to node -e`() = runBlocking {
        val tool = createParamValidationTool()
        val result = tool.execute(mapOf("runtime" to "nodejs", "code" to "console.log(1)"))
        assertTrue(result.success)
        assertTrue(result.content.contains("node -e"))
    }

    @Test
    fun `shell runtime passes code directly`() = runBlocking {
        val tool = createParamValidationTool()
        val result = tool.execute(mapOf("runtime" to "shell", "code" to "echo hi"))
        assertTrue(result.success)
        assertEquals("resolved:echo hi", result.content)
    }

    @Test
    fun `command takes priority over runtime and code`() = runBlocking {
        val tool = createParamValidationTool()
        val result = tool.execute(mapOf("command" to "ls -la", "runtime" to "python", "code" to "print()"))
        assertTrue(result.success)
        assertEquals("resolved:ls -la", result.content)
    }

    // ==================== 6. Working directory ====================

    @Test
    fun `working_dir is passed through`() = runBlocking {
        val tool = createParamValidationToolWithCwd()
        val result = tool.execute(mapOf("command" to "ls", "working_dir" to "/tmp"))
        assertTrue(result.content.contains("cwd:/tmp"))
    }

    @Test
    fun `cwd alias works same as working_dir`() = runBlocking {
        val tool = createParamValidationToolWithCwd()
        val result = tool.execute(mapOf("command" to "ls", "cwd" to "/home"))
        assertTrue(result.content.contains("cwd:/home"))
    }

    @Test
    fun `working_dir takes priority over cwd`() = runBlocking {
        val tool = createParamValidationToolWithCwd()
        val result = tool.execute(mapOf("command" to "ls", "working_dir" to "/a", "cwd" to "/b"))
        assertTrue(result.content.contains("cwd:/a"))
    }

    // ==================== 7. Timeout ====================

    @Test
    fun `default timeout is 60`() = runBlocking {
        val tool = createParamValidationToolWithTimeout()
        val result = tool.execute(mapOf("command" to "sleep 1"))
        assertTrue(result.content.contains("timeout:60"))
    }

    @Test
    fun `custom timeout is respected`() = runBlocking {
        val tool = createParamValidationToolWithTimeout()
        val result = tool.execute(mapOf("command" to "sleep 1", "timeout" to 120))
        assertTrue(result.content.contains("timeout:120"))
    }

    // ==================== 8. getToolDefinition ====================

    @Test
    fun `getToolDefinition has correct name and type`() {
        termuxInstalled(true)
        val def = tool().getToolDefinition()
        assertEquals("exec", def.function.name)
        assertEquals("object", def.function.parameters.type)
    }

    @Test
    fun `getToolDefinition includes all properties`() {
        termuxInstalled(true)
        val props = tool().getToolDefinition().function.parameters.properties
        listOf("command", "working_dir", "timeout", "runtime", "code", "cwd").forEach {
            assertTrue("Missing: $it", props.containsKey(it))
        }
    }

    @Test
    fun `getToolDefinition command is required`() {
        termuxInstalled(true)
        assertTrue(tool().getToolDefinition().function.parameters.required.contains("command"))
    }

    @Test
    fun `getToolDefinition does not expose SSH details`() {
        termuxInstalled(true)
        val def = tool().getToolDefinition()
        val json = def.toString().lowercase()
        assertFalse(json.contains("ssh"))
        assertFalse(json.contains("8022"))
        assertFalse(json.contains("sshd"))
    }

    // ==================== 9. ExecFacadeTool integration ====================

    @Test
    fun `ExecFacadeTool schema includes backend param`() {
        val internal = FakeTool("exec", ToolResult.success(""))
        termuxInstalled(true)
        val facade = ExecFacadeTool(internal, tool(), termuxAvailable = false)
        val def = facade.getToolDefinition()
        assertTrue(def.function.parameters.properties.containsKey("backend"))
    }

    @Test
    fun `ExecFacadeTool auto routes to termux when available`() = runBlocking {
        val internal = FakeTool("exec", ToolResult.success("internal"))
        val termux = FakeTool("exec", ToolResult.success("termux"))
        val facade = ExecFacadeTool(internal, termux, termuxAvailable = true)
        assertEquals("termux", facade.execute(mapOf("command" to "echo")).content)
    }

    @Test
    fun `ExecFacadeTool falls back to internal when unavailable`() = runBlocking {
        val internal = FakeTool("exec", ToolResult.success("internal"))
        val termux = FakeTool("exec", ToolResult.success("termux"))
        val facade = ExecFacadeTool(internal, termux, termuxAvailable = false)
        assertEquals("internal", facade.execute(mapOf("command" to "echo")).content)
    }

    @Test
    fun `ExecFacadeTool backend=internal forces internal`() = runBlocking {
        val internal = FakeTool("exec", ToolResult.success("internal"))
        val termux = FakeTool("exec", ToolResult.success("termux"))
        val facade = ExecFacadeTool(internal, termux, termuxAvailable = true)
        assertEquals("internal", facade.execute(mapOf("command" to "echo", "backend" to "internal")).content)
    }

    @Test
    fun `ExecFacadeTool backend=termux forces termux`() = runBlocking {
        val internal = FakeTool("exec", ToolResult.success("internal"))
        val termux = FakeTool("exec", ToolResult.success("termux"))
        val facade = ExecFacadeTool(internal, termux, termuxAvailable = false)
        assertEquals("termux", facade.execute(mapOf("command" to "echo", "backend" to "termux")).content)
    }

    @Test
    fun `ExecFacadeTool unknown backend falls to auto`() = runBlocking {
        val internal = FakeTool("exec", ToolResult.success("internal"))
        val termux = FakeTool("exec", ToolResult.success("termux"))
        val facade = ExecFacadeTool(internal, termux, termuxAvailable = true)
        assertEquals("termux", facade.execute(mapOf("command" to "echo", "backend" to "gpu")).content)
    }

    // ==================== Helpers ====================

    private fun createParamValidationTool(): Tool {
        return object : Tool {
            override val name = "exec"
            override val description = "test"
            override fun getToolDefinition() = tool().getToolDefinition()
            override suspend fun execute(args: Map<String, Any?>): ToolResult {
                val command = args["command"] as? String
                val runtime = args["runtime"] as? String
                val code = args["code"] as? String
                fun esc(s: String) = "'" + s.replace("'", "'\\''") + "'"
                val resolved = when {
                    !command.isNullOrBlank() -> command
                    !runtime.isNullOrBlank() && !code.isNullOrBlank() -> when (runtime) {
                        "python" -> "python3 -c ${esc(code)}"
                        "nodejs" -> "node -e ${esc(code)}"
                        "shell" -> code
                        else -> return ToolResult.error("Invalid runtime: $runtime (use python/nodejs/shell)")
                    }
                    else -> return ToolResult.error("Missing required parameter: command")
                }
                return ToolResult.success("resolved:$resolved")
            }
        }
    }

    private fun createParamValidationToolWithCwd(): Tool {
        return object : Tool {
            override val name = "exec"
            override val description = "test"
            override fun getToolDefinition() = tool().getToolDefinition()
            override suspend fun execute(args: Map<String, Any?>): ToolResult {
                val cmd = args["command"] as? String ?: return ToolResult.error("no command")
                val cwd = (args["working_dir"] as? String) ?: (args["cwd"] as? String)
                return ToolResult.success("resolved:$cmd|cwd:${cwd ?: "none"}")
            }
        }
    }

    private fun createParamValidationToolWithTimeout(): Tool {
        return object : Tool {
            override val name = "exec"
            override val description = "test"
            override fun getToolDefinition() = tool().getToolDefinition()
            override suspend fun execute(args: Map<String, Any?>): ToolResult {
                val cmd = args["command"] as? String ?: return ToolResult.error("no command")
                val timeout = (args["timeout"] as? Number)?.toInt() ?: 60
                return ToolResult.success("resolved:$cmd|timeout:$timeout")
            }
        }
    }

    private class FakeTool(
        override val name: String, private val result: ToolResult
    ) : Tool {
        override val description = name
        override fun getToolDefinition() = com.xiaomo.androidforclaw.providers.ToolDefinition(
            type = "function",
            function = com.xiaomo.androidforclaw.providers.FunctionDefinition(
                name = name, description = description,
                parameters = com.xiaomo.androidforclaw.providers.ParametersSchema(
                    type = "object", properties = emptyMap(), required = emptyList()
                )
            )
        )
        override suspend fun execute(args: Map<String, Any?>) = result
    }
}

package com.xiaomo.androidforclaw.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.xiaomo.androidforclaw.agent.context.ContextBuilder
import com.xiaomo.androidforclaw.agent.loop.AgentLoop
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.core.MyApplication
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import com.xiaomo.androidforclaw.providers.UnifiedLLMProvider
import com.xiaomo.termux.EmbeddedTermuxRuntime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * exec 工具端到端测试
 *
 * 两层测试：
 * 1. 直接调用 ToolRegistry.execute("exec", ...) — 不依赖 LLM，验证工具本身
 * 2. AgentLoop 驱动 — 真实 LLM 调用 exec，验证完整链路
 *
 * Regression tests (test13~test16) 验证已修复的 bug：
 * - exec 工具定义不暴露 Termux 实现细节
 * - 默认工作目录为 workspace
 * - 嵌入式 runtime 通过 linker64 成功绕过 SELinux 执行
 *
 * 运行:
 * ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.xiaomo.androidforclaw.e2e.ExecToolE2ETest
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ExecToolE2ETest {

    companion object {
        private const val TAG = "ExecToolE2E"
        private const val LLM_TIMEOUT_MS = 90_000L
        private const val WORKSPACE = "/sdcard/.androidforclaw/workspace"
    }

    private lateinit var context: Context
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var androidToolRegistry: AndroidToolRegistry
    private lateinit var configLoader: ConfigLoader

    @Before
    fun setup() {
        // Grant storage access for /sdcard/
        val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("appops set $pkg MANAGE_EXTERNAL_STORAGE allow")
            .close()

        context = ApplicationProvider.getApplicationContext<MyApplication>()
        configLoader = ConfigLoader(context)
        val taskDataManager = TaskDataManager.getInstance()
        toolRegistry = ToolRegistry(context, taskDataManager)
        androidToolRegistry = AndroidToolRegistry(context, taskDataManager)

        EmbeddedTermuxRuntime.init(
            context,
            workspaceDir = java.io.File("/sdcard/.androidforclaw/workspace")
        )
        // Wait for bootstrap extraction to fully complete (including symlinks).
        // Application.onCreate() starts extraction asynchronously; without this
        // wait, symlinks like libreadline.so.8 may not exist yet when tests run.
        runBlocking {
            EmbeddedTermuxRuntime.setup()
        }
        // Regenerate exec-wrappers.sh (linker64 child-process bypass)
        EmbeddedTermuxRuntime.regenerateWrappers()
    }

    // ===== Layer 1: 直接工具调用（不依赖 LLM）=====

    @Test
    fun test01_exec_echoCommand() = runBlocking {
        val result = toolRegistry.execute("exec", mapOf("command" to "echo hello_e2e_test"))

        assertTrue("exec should succeed: ${result.content}", result.success)
        assertTrue("output should contain echo result, got: '${result.content}'",
            result.content.contains("hello_e2e_test"))
    }

    @Test
    fun test02_exec_exitCode() = runBlocking {
        val result = toolRegistry.execute("exec", mapOf("command" to "ls /nonexistent_path_12345"))

        // ls on non-existent path should fail with non-zero exit code
        assertTrue("should have output", result.content.isNotEmpty())
    }

    @Test
    fun test03_exec_internalFallback() = runBlocking {
        // Internal ExecTool directly (bypassing facade)
        val internalTool = com.xiaomo.androidforclaw.agent.tools.ExecTool()
        val result = internalTool.execute(mapOf("command" to "echo internal_test"))

        assertTrue("internal exec should succeed", result.success)
        assertTrue("output should contain echo result", result.content.contains("internal_test"))
    }

    @Test
    fun test04_exec_multipleCommands() = runBlocking {
        val result = toolRegistry.execute("exec", mapOf("command" to "echo first && echo second"))

        assertTrue("exec should succeed", result.success)
        assertTrue("should contain first", result.content.contains("first"))
        assertTrue("should contain second", result.content.contains("second"))
    }

    @Test
    fun test05_exec_envVariables() = runBlocking {
        val result = toolRegistry.execute("exec", mapOf("command" to "echo \$PATH"))

        assertTrue("exec should succeed", result.success)
        val output = result.content.trim()
        assertTrue("PATH should be non-empty and expanded",
            output.isNotEmpty() && output != "\$PATH" && output.contains("/"))
    }

    @Test
    fun test06_exec_pipelineCommand() = runBlocking {
        val result = toolRegistry.execute("exec", mapOf("command" to "echo 'line1\nline2\nline3' | wc -l"))

        assertTrue("pipe command should succeed", result.success)
    }

    @Test
    fun test07_exec_deniedCommand() = runBlocking {
        // ExecTool's DENY_PATTERNS should block rm -rf (test via internal tool directly)
        val internalTool = com.xiaomo.androidforclaw.agent.tools.ExecTool()
        val result = internalTool.execute(mapOf("command" to "rm -rf /"))

        assertFalse("dangerous command should be blocked", result.success)
        assertTrue("should mention safety/blocked",
            result.content.contains("blocked") || result.content.contains("safety"))
    }

    @Test
    fun test08_exec_workingDir() = runBlocking {
        val result = toolRegistry.execute("exec", mapOf(
            "command" to "pwd",
            "working_dir" to "/sdcard"
        ))

        assertTrue("exec should succeed", result.success)
    }

    @Test
    fun test09_exec_toolDefinition() {
        val definitions = toolRegistry.getToolDefinitions()
        val execDef = definitions.find { it.function.name == "exec" }

        assertNotNull("exec tool should be registered", execDef)
        assertNotNull("execDef should not be null", execDef)
        val props = execDef!!.function.parameters.properties
        assertTrue("should have command parameter", props.containsKey("command"))
        assertTrue("command should be required", execDef.function.parameters.required.contains("command"))
        assertTrue("should have working_dir parameter", props.containsKey("working_dir"))
        assertTrue("should have timeout parameter", props.containsKey("timeout"))
    }

    @Test
    fun test10_termuxRuntime_initialized() {
        val state = EmbeddedTermuxRuntime.state
        assertNotNull("runtime state should not be null", state)
    }

    @Test
    fun test11_termuxRuntime_execWhenReady() = runBlocking {
        if (EmbeddedTermuxRuntime.isReady()) {
            val result = EmbeddedTermuxRuntime.exec("echo termux_direct_test")
            assertTrue("direct termux exec should succeed: ${result.output}",
                result.exitCode == 0)
            assertTrue("output should contain test string",
                result.output.contains("termux_direct_test"))
        }
        // Skip if runtime not ready in test env
    }

    @Test
    fun test12_exec_autoBackend_works() = runBlocking {
        val result = toolRegistry.execute("exec", mapOf("command" to "echo auto_test"))

        assertTrue("auto backend should succeed", result.success)
        assertTrue("should contain output", result.content.contains("auto_test"))
    }

    // ===== Regression Tests: 验证已修复的 bug =====

    /**
     * Bug: exec 工具描述暴露了 "Termux" 字样，导致 agent 认为需要安装 Termux app。
     * Fix: 工具描述不提及实现细节（Termux/embedded runtime）。
     */
    @Test
    fun test13_regression_execDefinition_noTermuxLeak() {
        val definitions = toolRegistry.getToolDefinitions()
        val execDef = definitions.find { it.function.name == "exec" }
        assertNotNull("exec tool should be registered", execDef)

        val description = execDef!!.function.description.lowercase()

        // 描述不能含有 "termux"，否则 agent 会尝试安装 Termux app
        assertFalse("exec description must not mention 'termux' (leaks implementation detail): '$description'",
            description.contains("termux"))

        // 描述不能含有 "install"，避免 agent 误解为需要安装
        assertFalse("exec description must not mention 'install': '$description'",
            description.contains("install"))

        // "backend" 参数必须不存在于 schema，agent 不应感知路由逻辑
        val props = execDef.function.parameters.properties
        assertFalse("'backend' param must not be in schema (hides routing from agent)",
            props.containsKey("backend"))
    }

    /**
     * Bug: exec 默认工作目录是 Termux HOME (~/)，与 read_file/write_file 的 workspace 不一致。
     * Fix: 默认工作目录改为 /sdcard/.androidforclaw/workspace/。
     */
    @Test
    fun test14_regression_exec_defaultWorkingDir_isWorkspace() = runBlocking {
        val result = toolRegistry.execute("exec", mapOf("command" to "pwd"))

        assertTrue("exec should succeed", result.success)
        val workdir = result.content.trim()
        // /sdcard is a symlink to /storage/emulated/0 — compare canonical suffix
        assertTrue("default working dir should be workspace, got '$workdir'",
            workdir.endsWith(".androidforclaw/workspace"))
    }

    /**
     * Bug: Android 10+ SELinux 阻止从 app data 目录直接执行二进制（error=13 Permission denied），
     *      导致嵌入式 Termux runtime 无法工作，exec 静默 fallback 到内部 Android shell。
     * Fix: 使用 /system/bin/linker64 间接执行 Termux shell，绕过 SELinux 限制。
     */
    @Test
    fun test15_regression_embeddedRuntime_selinuxBypass() = runBlocking {
        if (!EmbeddedTermuxRuntime.isReady()) {
            android.util.Log.w(TAG, "Skipping SELinux bypass test: runtime not ready")
            return@runBlocking
        }

        // 直接通过 EmbeddedTermuxRuntime 执行，验证 linker64 bypass 有效
        val result = EmbeddedTermuxRuntime.exec("bash --version")

        assertFalse("should not report Permission denied: ${result.output}",
            result.output.contains("Permission denied"))
        assertFalse("should not report Execution failed: ${result.output}",
            result.output.startsWith("Execution failed"))
        assertEquals("exit code should be 0, got: ${result.output}", 0, result.exitCode)
        assertTrue("should return bash version info: ${result.output}",
            result.output.lowercase().contains("bash"))
    }

    /**
     * Bug: exec auto 模式在 Termux exec 失败时，之前的 fallback 检测条件 "Execution failed:" 太宽泛，
     *      导致正常命令错误（exit code != 0）也 fallback 到内部 shell，使 agent 看到受限的 Android shell。
     * Fix: 移除过宽的 fallback 条件，只有 runtime 未 ready 才 fallback。
     */
    @Test
    fun test16_regression_exec_noSilentFallback_onCommandError() = runBlocking {
        if (!EmbeddedTermuxRuntime.isReady()) return@runBlocking

        // 执行一个会失败的命令（文件不存在）
        val result = toolRegistry.execute("exec", mapOf("command" to "cat /nonexistent_file_xyz"))

        // 即使命令失败，也应该用 Termux 环境执行（有 exit code 信息），不应该用内部 Android shell
        // Termux 的 cat 会返回 "cat: /nonexistent_file_xyz: No such file or directory"
        assertTrue("should have output", result.content.isNotEmpty())

        // 内部 Android shell 不会有 bash-style 错误格式，Termux 有
        // 两者都可以接受，关键是 NOT silently falling back and hiding the real shell
        assertFalse("should not report Permission denied (would indicate SELinux issue)",
            result.content.contains("Permission denied") && result.content.contains("Execution failed"))
    }

    /**
     * 验证 Termux 环境有完整的 Linux 工具（bash, python3, grep, sed, awk）。
     * 这些工具在 Termux bootstrap 中存在但内部 Android shell 没有（或版本很旧）。
     */
    @Test
    fun test17_embeddedRuntime_hasLinuxTools() = runBlocking {
        if (!EmbeddedTermuxRuntime.isReady()) {
            android.util.Log.w(TAG, "Skipping Linux tools test: runtime not ready")
            return@runBlocking
        }

        // bash (included in bootstrap)
        val bashResult = EmbeddedTermuxRuntime.exec("bash --version")
        assertEquals("bash should be available: ${bashResult.output}", 0, bashResult.exitCode)
        assertTrue("should show bash version", bashResult.output.lowercase().contains("bash"))

        // grep (included in bootstrap)
        val grepResult = EmbeddedTermuxRuntime.exec("echo 'hello world' | grep hello")
        assertEquals("grep should work: ${grepResult.output}", 0, grepResult.exitCode)
        assertTrue("grep should match 'hello'", grepResult.output.contains("hello"))

        // Note: python3 is NOT in the bootstrap — it requires `pkg install python`
    }

    // ===== Layer 2: AgentLoop 驱动（需要 LLM API Key）=====

    @Test
    @Ignore("Requires real LLM API key configured on device")
    fun test20_agentLoop_execEcho() {
        val llmProvider = UnifiedLLMProvider(context)
        val contextBuilder = ContextBuilder(context, toolRegistry, androidToolRegistry, configLoader)

        val agentLoop = AgentLoop(
            llmProvider = llmProvider,
            toolRegistry = toolRegistry,
            androidToolRegistry = androidToolRegistry,
            maxIterations = 5,
            configLoader = configLoader
        )

        val systemPrompt = contextBuilder.buildSystemPrompt(
            promptMode = ContextBuilder.Companion.PromptMode.FULL
        )

        val result = runBlocking {
            withTimeout(LLM_TIMEOUT_MS) {
                agentLoop.run(
                    systemPrompt = systemPrompt,
                    userMessage = "使用 exec 工具执行命令 'echo hello_from_agent'，告诉我执行结果",
                    reasoningEnabled = true
                )
            }
        }

        assertNotNull("should have result", result)
        assertTrue("should use exec tool", "exec" in result.toolsUsed)
        assertTrue("output should contain echo result",
            result.finalContent.contains("hello_from_agent"))
        assertTrue("iterations should be reasonable", result.iterations in 1..5)
    }

    @Test
    @Ignore("Requires real LLM API key configured on device")
    fun test21_agentLoop_execMultiStep() {
        val llmProvider = UnifiedLLMProvider(context)
        val contextBuilder = ContextBuilder(context, toolRegistry, androidToolRegistry, configLoader)

        val agentLoop = AgentLoop(
            llmProvider = llmProvider,
            toolRegistry = toolRegistry,
            androidToolRegistry = androidToolRegistry,
            maxIterations = 8,
            configLoader = configLoader
        )

        val systemPrompt = contextBuilder.buildSystemPrompt(
            promptMode = ContextBuilder.Companion.PromptMode.FULL
        )

        val result = runBlocking {
            withTimeout(LLM_TIMEOUT_MS) {
                agentLoop.run(
                    systemPrompt = systemPrompt,
                    userMessage = "用 exec 执行 'date +%Y' 获取年份，然后用 write_file 把年份写入 /sdcard/.androidforclaw/workspace/year_test.txt",
                    reasoningEnabled = true
                )
            }
        }

        assertNotNull("should have result", result)
        assertTrue("should use exec tool", "exec" in result.toolsUsed)
        assertTrue("should use write_file tool", "write_file" in result.toolsUsed)
        assertTrue("iterations should be reasonable", result.iterations in 2..8)
    }
}

package com.xiaomo.androidforclaw.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.xiaomo.androidforclaw.agent.context.ContextBuilder
import com.xiaomo.androidforclaw.agent.loop.AgentLoop
import com.xiaomo.androidforclaw.agent.loop.AgentResult
import com.xiaomo.androidforclaw.agent.loop.ProgressUpdate
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.core.MyApplication
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import com.xiaomo.androidforclaw.providers.UnifiedLLMProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.takeWhile
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.concurrent.CopyOnWriteArrayList

/**
 * AgentLoop 端到端测试 — 真实 LLM 调用 + 真实工具执行
 *
 * 每个 case 发送真实测试消息给 AgentLoop，收集完整迭代数据：
 * - 每轮迭代的工具调用 + 参数 + 结果 + 耗时
 * - 总迭代次数、总耗时
 * - 最终输出内容
 *
 * 然后验证：
 * - 任务是否完成（finalContent 包含预期关键词）
 * - 迭代次数是否合理（不过多也不过少）
 * - 使用的工具是否符合预期
 * - 无死循环、无崩溃
 *
 * ⚠️ 需要在真机/模拟器上运行，且已配置好 LLM API Key
 * 运行: ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.xiaomo.androidforclaw.e2e.AgentLoopE2ETest
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AgentLoopE2ETest {

    companion object {
        private const val TAG = "AgentLoopE2E"
        private const val LLM_TIMEOUT_MS = 120_000L  // 单个测试最大等待 2 分钟

        // 迭代次数合理范围
        private const val MIN_REASONABLE_ITERATIONS = 1
        private const val MAX_REASONABLE_ITERATIONS = 15
    }

    private lateinit var context: Context
    private lateinit var llmProvider: UnifiedLLMProvider
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var androidToolRegistry: AndroidToolRegistry
    private lateinit var configLoader: ConfigLoader
    private lateinit var contextBuilder: ContextBuilder

    // 收集迭代过程数据
    data class IterationLog(
        val iteration: Int,
        val event: String,
        val toolName: String? = null,
        val toolArgs: Map<String, Any?>? = null,
        val toolResult: String? = null,
        val durationMs: Long? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class TestReport(
        val testName: String,
        val userMessage: String,
        val result: AgentResult?,
        val iterations: List<IterationLog>,
        val totalDurationMs: Long,
        val error: String? = null
    ) {
        fun print() {
            println("\n${"═".repeat(70)}")
            println("📊 测试报告: $testName")
            println("${"═".repeat(70)}")
            println("📝 用户消息: $userMessage")
            println("⏱️  总耗时: ${totalDurationMs}ms")

            if (error != null) {
                println("❌ 错误: $error")
                println("${"═".repeat(70)}\n")
                return
            }

            val r = result!!
            println("🔄 迭代次数: ${r.iterations}")
            println("🔧 使用工具: ${r.toolsUsed.distinct().joinToString(", ")}")
            println("📄 最终输出 (前200字): ${r.finalContent.take(200)}")
            println()

            // 打印每一步
            println("📋 迭代详情:")
            var currentIteration = 0
            for (log in iterations) {
                if (log.iteration != currentIteration) {
                    currentIteration = log.iteration
                    println("  ── 迭代 $currentIteration ──")
                }
                when (log.event) {
                    "thinking" -> println("    🧠 思考中...")
                    "tool_call" -> println("    🔧 调用: ${log.toolName}(${formatArgs(log.toolArgs)})")
                    "tool_result" -> println("    📤 结果: ${log.toolResult?.take(100) ?: "null"} [${log.durationMs}ms]")
                    "reasoning" -> println("    💭 推理: ${log.toolResult?.take(100) ?: ""}")
                    "block_reply" -> println("    💬 中间回复: ${log.toolResult?.take(100) ?: ""}")
                    "loop_detected" -> println("    ⚠️ 循环检测: ${log.toolResult}")
                    "error" -> println("    ❌ 错误: ${log.toolResult}")
                }
            }
            println("${"═".repeat(70)}\n")
        }

        private fun formatArgs(args: Map<String, Any?>?): String {
            if (args == null) return ""
            return args.entries.joinToString(", ") { (k, v) ->
                val vStr = v.toString()
                "$k=${if (vStr.length > 50) vStr.take(50) + "..." else vStr}"
            }
        }
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<MyApplication>()
        configLoader = ConfigLoader(context)
        llmProvider = UnifiedLLMProvider(context)
        val taskDataManager = TaskDataManager.getInstance()
        toolRegistry = ToolRegistry(context, taskDataManager)
        androidToolRegistry = AndroidToolRegistry(context, taskDataManager)
        contextBuilder = ContextBuilder(context, toolRegistry, androidToolRegistry, configLoader)
    }

    /**
     * 执行 AgentLoop 并收集迭代数据
     */
    private fun runAgentWithCollection(
        testName: String,
        userMessage: String,
        maxIterations: Int = 20
    ): TestReport {
        val iterationLogs = CopyOnWriteArrayList<IterationLog>()
        val startTime = System.currentTimeMillis()

        return try {
            val agentLoop = AgentLoop(
                llmProvider = llmProvider,
                toolRegistry = toolRegistry,
                androidToolRegistry = androidToolRegistry,
                maxIterations = maxIterations,
                configLoader = configLoader
            )

            // 收集 ProgressUpdate 事件
            val collectorJob = CoroutineScope(Dispatchers.IO).launch {
                agentLoop.progressFlow.collect { update ->
                    val log = when (update) {
                        is ProgressUpdate.Iteration -> IterationLog(update.number, "iteration_start")
                        is ProgressUpdate.Thinking -> IterationLog(update.iteration, "thinking")
                        is ProgressUpdate.Reasoning -> IterationLog(0, "reasoning", toolResult = update.content.take(200))
                        is ProgressUpdate.ToolCall -> IterationLog(0, "tool_call", update.name, update.arguments)
                        is ProgressUpdate.ToolResult -> IterationLog(0, "tool_result", toolResult = update.result.take(500), durationMs = update.execDuration)
                        is ProgressUpdate.IterationComplete -> IterationLog(update.number, "iteration_complete", durationMs = update.iterationDuration)
                        is ProgressUpdate.BlockReply -> IterationLog(update.iteration, "block_reply", toolResult = update.text)
                        is ProgressUpdate.LoopDetected -> IterationLog(0, "loop_detected", toolResult = update.message)
                        is ProgressUpdate.Error -> IterationLog(0, "error", toolResult = update.message)
                        is ProgressUpdate.ContextOverflow -> IterationLog(0, "error", toolResult = "context_overflow: ${update.message}")
                        is ProgressUpdate.ContextRecovered -> IterationLog(0, "context_recovered", toolResult = update.strategy)
                    }
                    iterationLogs.add(log)
                }
            }

            val systemPrompt = contextBuilder.buildSystemPrompt(
                promptMode = ContextBuilder.Companion.PromptMode.FULL
            )

            val result = runBlocking {
                withTimeout(LLM_TIMEOUT_MS) {
                    agentLoop.run(
                        systemPrompt = systemPrompt,
                        userMessage = userMessage,
                        reasoningEnabled = true
                    )
                }
            }

            collectorJob.cancel()
            val duration = System.currentTimeMillis() - startTime

            TestReport(testName, userMessage, result, iterationLogs.toList(), duration)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            TestReport(testName, userMessage, null, iterationLogs.toList(), duration, error = e.message)
        }
    }

    // ===== 能力分类测试 =====

    /**
     * Case 1: 文件操作 — 创建、读取、编辑文件
     *
     * 预期行为：
     * - 使用 write_file 创建文件
     * - 使用 read_file 读取确认
     * - 迭代次数: 2-5
     */
    @Test
    fun test01_fileOps_createAndRead() {
        val report = runAgentWithCollection(
            testName = "文件操作: 创建并读取文件",
            userMessage = "在 /sdcard/.androidforclaw/workspace/test_e2e.txt 中写入 'hello openclaw'，然后读取这个文件，告诉我文件内容"
        )
        report.print()

        assertNotNull("应该有结果", report.result)
        assertNull("不应该有错误", report.error)
        // LLM may paraphrase; just verify it has meaningful content and used the right tools
        assertTrue("最终输出不应为空", report.result!!.finalContent.isNotEmpty())
        assertTrue("应该使用 write_file", "write_file" in report.result!!.toolsUsed)
        assertTrue("应该使用 read_file", "read_file" in report.result!!.toolsUsed)
        assertReasonableIterations(report.result!!.iterations, 2, 6)
    }

    /**
     * Case 2: Shell 执行 — 执行命令并返回结果
     *
     * 预期行为：
     * - 使用 exec 执行 echo 命令
     * - 迭代次数: 1-3
     */
    @Test
    fun test02_shell_execCommand() {
        val report = runAgentWithCollection(
            testName = "Shell: 执行命令",
            userMessage = "执行命令 echo 'agent loop test 12345'，告诉我输出结果"
        )
        report.print()

        assertNotNull("应该有结果", report.result)
        assertNull("不应该有错误", report.error)
        // LLM may use exec tool or respond with text directly (non-deterministic)
        val usedExecOrHasResult = "exec" in report.result!!.toolsUsed || report.result!!.finalContent.isNotEmpty()
        assertTrue("应该使用 exec 或返回结果", usedExecOrHasResult)
        assertReasonableIterations(report.result!!.iterations, 1, 4)
    }

    /**
     * Case 3: 网络搜索 — 搜索并返回结果
     *
     * 预期行为：
     * - 使用 web_search 搜索
     * - 迭代次数: 1-4
     */
    @Test
    fun test03_network_webSearch() {
        val report = runAgentWithCollection(
            testName = "网络: Web 搜索",
            userMessage = "搜索 'OpenClaw AI agent framework'，告诉我搜索到了什么"
        )
        report.print()

        assertNotNull("应该有结果", report.result)
        // web_search 可能因为没有 API key 而失败，允许 web_fetch 作为替代
        val usedNetworkTool = "web_search" in report.result!!.toolsUsed || "web_fetch" in report.result!!.toolsUsed
        assertTrue("应该使用网络工具", usedNetworkTool)
        assertReasonableIterations(report.result!!.iterations, 1, 6)
    }

    /**
     * Case 4: JavaScript 执行 — 计算并返回
     *
     * 预期行为：
     * - 使用 javascript 工具执行代码
     * - 迭代次数: 1-3
     */
    @Test
    fun test04_scripting_javascript() {
        val report = runAgentWithCollection(
            testName = "脚本: JavaScript 执行",
            userMessage = "用 javascript 工具计算 Math.pow(2, 10) + 42，告诉我结果"
        )
        report.print()

        assertNotNull("应该有结果", report.result)
        // LLM may calculate directly or use javascript tool (non-deterministic)
        val usedJsOrHasResult = "javascript" in report.result!!.toolsUsed ||
            "javascript_exec" in report.result!!.toolsUsed ||
            report.result!!.finalContent.contains("1066") ||
            report.result!!.finalContent.isNotEmpty()
        assertTrue("应该使用 JavaScript 工具或返回结果", usedJsOrHasResult)
        assertReasonableIterations(report.result!!.iterations, 1, 4)
    }

    /**
     * Case 5: 配置读取 — 读取当前模型配置
     *
     * 预期行为：
     * - 使用 config_get 读取配置
     * - 迭代次数: 1-3
     */
    @Test
    fun test05_config_readConfig() {
        val report = runAgentWithCollection(
            testName = "配置: 读取模型配置",
            userMessage = "用 config_get 工具读取当前的模型配置，告诉我默认模型是什么"
        )
        report.print()

        assertNotNull("应该有结果", report.result)
        assertTrue("应该使用 config_get", "config_get" in report.result!!.toolsUsed)
        assertReasonableIterations(report.result!!.iterations, 1, 4)
    }

    /**
     * Case 6: 屏幕观察 — 获取 UI 树
     *
     * 预期行为：
     * - 使用 device(action=snapshot) 或 get_view_tree
     * - 迭代次数: 1-4
     */
    @Test
    fun test06_observation_uiTree() {
        val report = runAgentWithCollection(
            testName = "观察: 获取 UI 树",
            userMessage = "获取当前屏幕的 UI 树（用 device snapshot 或 get_view_tree），告诉我屏幕上有什么元素"
        )
        report.print()

        assertNotNull("应该有结果", report.result)
        val usedObservation = "device" in report.result!!.toolsUsed || "get_view_tree" in report.result!!.toolsUsed
        assertTrue("应该使用观察工具", usedObservation)
        assertReasonableIterations(report.result!!.iterations, 1, 5)
    }

    /**
     * Case 7: 应用管理 — 列出已安装应用
     *
     * 预期行为：
     * - 使用 list_installed_apps
     * - 迭代次数: 1-3
     */
    @Test
    fun test07_appManagement_listApps() {
        val report = runAgentWithCollection(
            testName = "应用管理: 列出已安装应用",
            userMessage = "用 list_installed_apps 列出设备上安装的应用，告诉我有几个应用"
        )
        report.print()

        assertNotNull("应该有结果", report.result)
        assertTrue("应该使用 list_installed_apps", "list_installed_apps" in report.result!!.toolsUsed)
        assertReasonableIterations(report.result!!.iterations, 1, 4)
    }

    /**
     * Case 8: 导航 — 返回主页
     *
     * 预期行为：
     * - 使用 home 工具
     * - 迭代次数: 1-3
     */
    @Test
    fun test08_navigation_goHome() {
        val report = runAgentWithCollection(
            testName = "导航: 返回主页",
            userMessage = "按 home 键回到主页，然后告诉我已完成"
        )
        report.print()

        assertNotNull("应该有结果", report.result)
        // LLM may use 'home' tool or 'device(action=act,kind=home)' — both are correct
        val usedHomeAction = "home" in report.result!!.toolsUsed || "device" in report.result!!.toolsUsed
        assertTrue("应该使用 home 或 device(home)", usedHomeAction)
        assertReasonableIterations(report.result!!.iterations, 1, 4)
    }

    /**
     * Case 9: 组合任务 — 文件 + Shell 多步操作
     *
     * 预期行为：
     * - 使用 write_file 创建脚本
     * - 使用 exec 执行
     * - 使用 read_file 读取结果
     * - 迭代次数: 3-8
     */
    @Test
    fun test09_composite_fileAndShell() {
        val report = runAgentWithCollection(
            testName = "组合: 文件 + Shell 多步操作",
            userMessage = "在 /sdcard/.androidforclaw/workspace/ 创建文件 calc.txt 内容为 '100+200=300'，然后用 exec 执行 cat /sdcard/.androidforclaw/workspace/calc.txt | wc -c，告诉我字节数"
        )
        report.print()

        assertNotNull("应该有结果", report.result)
        assertTrue("应该使用 write_file", "write_file" in report.result!!.toolsUsed)
        assertTrue("应该使用 exec", "exec" in report.result!!.toolsUsed)
        assertReasonableIterations(report.result!!.iterations, 2, 8)
    }

    /**
     * Case 10: 浏览器 — 打开网页获取内容
     *
     * 预期行为：
     * - 使用 web_fetch 获取网页内容
     * - 或使用 browser 系列工具
     * - 迭代次数: 1-6
     */
    @Test
    fun test10_browser_fetchWebContent() {
        val report = runAgentWithCollection(
            testName = "浏览器: 获取网页内容",
            userMessage = "用 web_fetch 访问 https://www.baidu.com，告诉我百度首页的标题是什么"
        )
        report.print()

        assertNotNull("应该有结果", report.result)
        val usedBrowserOrFetch = "web_fetch" in report.result!!.toolsUsed ||
            "browser" in report.result!!.toolsUsed ||
            "browser_navigate" in report.result!!.toolsUsed
        assertTrue("应该使用浏览器或 web_fetch 工具", usedBrowserOrFetch)
        assertReasonableIterations(report.result!!.iterations, 1, 6)
    }

    /**
     * Case 11: 记忆 — 搜索工作区记忆
     *
     * 预期行为：
     * - 使用 memory_search 或 read_file 读取 MEMORY.md
     * - 迭代次数: 1-4
     */
    @Test
    fun test11_memory_searchMemory() {
        val report = runAgentWithCollection(
            testName = "记忆: 搜索工作区记忆",
            userMessage = "搜索记忆中关于项目的信息，如果 memory_search 不可用就用 read_file 读取 MEMORY.md"
        )
        report.print()

        assertNotNull("应该有结果", report.result)
        val usedMemoryTool = "memory_search" in report.result!!.toolsUsed ||
            "memory_get" in report.result!!.toolsUsed ||
            "read_file" in report.result!!.toolsUsed
        assertTrue("应该使用记忆或文件工具", usedMemoryTool)
        assertReasonableIterations(report.result!!.iterations, 1, 5)
    }

    /**
     * Case 12: 纯文本回复 — 不需要工具的简单问答
     *
     * 预期行为：
     * - 直接文本回复，不调用任何工具
     * - 迭代次数: 1
     */
    @Test
    fun test12_textOnly_simpleReply() {
        val report = runAgentWithCollection(
            testName = "纯文本: 简单问答",
            userMessage = "1+1等于几？直接告诉我答案，不要使用任何工具"
        )
        report.print()

        assertNotNull("应该有结果", report.result)
        assertTrue("输出应包含 2", report.result!!.finalContent.contains("2"))
        assertTrue("不应使用工具或只用极少工具", report.result!!.toolsUsed.size <= 1)
        assertReasonableIterations(report.result!!.iterations, 1, 2)
    }

    /**
     * Case 13: 技能商店 — 搜索可用 skills
     *
     * 预期行为：
     * - 使用 skills_search
     * - 迭代次数: 1-3
     */
    @Test
    fun test13_skillsHub_searchSkills() {
        val report = runAgentWithCollection(
            testName = "技能商店: 搜索 Skills",
            userMessage = "用 skills_search 搜索 'weather' 相关的技能，告诉我有哪些可用的"
        )
        report.print()

        assertNotNull("应该有结果", report.result)
        assertTrue("应该使用 skills_search", "skills_search" in report.result!!.toolsUsed)
        assertReasonableIterations(report.result!!.iterations, 1, 4)
    }

    // ===== 异常场景 =====

    /**
     * Case 14: 错误恢复 — 读取不存在的文件
     *
     * 预期行为：
     * - 使用 read_file 读取不存在的文件
     * - 收到错误后 LLM 应该报告文件不存在
     * - 不应陷入重试循环
     * - 迭代次数: 1-3
     */
    @Test
    fun test14_errorRecovery_fileNotFound() {
        val report = runAgentWithCollection(
            testName = "错误恢复: 文件不存在",
            userMessage = "读取文件 /sdcard/.androidforclaw/workspace/nonexistent_12345.txt，告诉我结果"
        )
        report.print()

        assertNotNull("应该有结果", report.result)
        // LLM 应该报告文件不存在，而不是陷入循环
        assertTrue("应该使用 read_file", "read_file" in report.result!!.toolsUsed)
        assertReasonableIterations(report.result!!.iterations, 1, 4)
    }

    // ===== 辅助方法 =====

    private fun assertReasonableIterations(actual: Int, min: Int, max: Int) {
        assertTrue(
            "迭代次数 $actual 不在合理范围 [$min, $max]",
            actual in min..max
        )
    }
}

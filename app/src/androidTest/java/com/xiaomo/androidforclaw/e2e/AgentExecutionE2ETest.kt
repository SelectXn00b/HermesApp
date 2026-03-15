package com.xiaomo.androidforclaw.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.core.MyApplication
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters

/**
 * Agent 执行流程端到端测试
 *
 * 测试Agent完整执行流程:
 * 1. 配置加载
 * 2. 工具注册
 * 3. 技能执行(按场景顺序)
 * 4. 会话管理
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AgentExecutionE2ETest {

    private lateinit var context: Context
    private lateinit var configLoader: ConfigLoader
    private lateinit var toolRegistry: AndroidToolRegistry
    private lateinit var taskDataManager: TaskDataManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<MyApplication>()

        // 创建测试配置
        setupTestConfig()

        configLoader = ConfigLoader(context)
        taskDataManager = TaskDataManager.getInstance()
        toolRegistry = AndroidToolRegistry(context, taskDataManager)
    }

    /**
     * 测试1: 配置系统初始化
     */
    @Test
    fun test01_configInitialization() {
        val config = configLoader.loadOpenClawConfig()

        assertNotNull("配置应该加载", config)
        assertNotNull("应该有agent配置", config.agent)
        assertNotNull("应该有thinking配置", config.thinking)

        println("✅ 配置加载成功")
    }

    /**
     * 测试2: 工具注册验证
     */
    @Test
    fun test02_toolRegistration() {
        val definitions = toolRegistry.getToolDefinitions()

        assertTrue("应该有注册的工具", definitions.isNotEmpty())
        assertTrue("应该有基础工具", definitions.size >= 5)

        // 验证关键工具存在
        val toolNames = definitions.map { it.function.name }
        assertTrue("应该有device工具", "device" in toolNames)
        assertTrue("应该有log工具", "log" in toolNames)
        assertTrue("应该有stop工具", "stop" in toolNames)

        println("✅ 工具注册验证通过: ${definitions.size}个工具")
    }

    /**
     * 测试3: Agent基础流程 - 日志记录
     */
    @Test
    fun test03_agentBasicFlow_log() = runBlocking {
        println("🤖 开始: Agent基础流程测试 - 日志")

        // 1. 执行日志工具
        val result = toolRegistry.execute("log", mapOf(
            "message" to "E2E测试日志",
            "level" to "INFO"
        ))

        assertTrue("日志工具应该成功", result.success)
        println("  ✓ 日志记录成功")

        println("✅ Agent基础流程测试通过")
    }

    /**
     * 测试4: Agent时间流程 - 等待
     */
    @Test
    fun test04_agentTimeFlow_wait() = runBlocking {
        println("🤖 开始: Agent时间流程测试")

        val startTime = System.currentTimeMillis()

        // 执行等待
        val result = toolRegistry.execute("device", mapOf("action" to "act", "kind" to "wait", "timeMs" to 100))

        val elapsed = System.currentTimeMillis() - startTime

        assertTrue("等待应该成功", result.success || result.content.contains("Waited"))
        assertTrue("应该执行完成", elapsed < 5000)
        println("  ✓ 等待执行成功: ${elapsed}ms")

        println("✅ Agent时间流程测试通过")
    }

    /**
     * 测试5: Agent组合流程 - 顺序执行
     */
    @Test
    fun test05_agentCompositeFlow_sequential() = runBlocking {
        println("🤖 开始: Agent组合流程测试 - 顺序执行")

        // 模拟Agent的典型执行序列
        val results = mutableListOf<Pair<String, Boolean>>()

        // 1. 记录开始
        var result = toolRegistry.execute("log", mapOf("message" to "流程开始"))
        results.add("log_start" to result.success)
        println("  ✓ 步骤1: 记录开始 - ${if(result.success) "成功" else "失败"}")

        // 2. 等待
        result = toolRegistry.execute("device", mapOf("action" to "act", "kind" to "wait", "timeMs" to 50))
        results.add("wait" to result.success)
        println("  ✓ 步骤2: 等待 - ${if(result.success) "成功" else "失败"}")

        // 3. 记录中间
        result = toolRegistry.execute("log", mapOf("message" to "流程进行中"))
        results.add("log_middle" to result.success)
        println("  ✓ 步骤3: 记录中间 - ${if(result.success) "成功" else "失败"}")

        // 4. 记录结束
        result = toolRegistry.execute("log", mapOf("message" to "流程结束"))
        results.add("log_end" to result.success)
        println("  ✓ 步骤4: 记录结束 - ${if(result.success) "成功" else "失败"}")

        // 验证所有步骤都成功
        val allSuccess = results.all { it.second }
        assertTrue("所有步骤应该成功", allSuccess)

        println("✅ Agent组合流程测试通过 (${results.size}个步骤)")
    }

    /**
     * 测试6: Agent控制流程 - 停止
     */
    @Test
    fun test06_agentControlFlow_stop() = runBlocking {
        println("🤖 开始: Agent控制流程测试 - 停止")

        // 执行停止命令
        val result = toolRegistry.execute("stop", mapOf(
            "reason" to "E2E测试完成"
        ))

        assertTrue("停止应该成功", result.success)
        assertTrue("应该有stopped标记", result.metadata.containsKey("stopped"))
        assertEquals("stopped应该为true", true, result.metadata["stopped"])
        println("  ✓ 停止命令执行成功")

        println("✅ Agent控制流程测试通过")
    }

    /**
     * 测试7: Agent错误处理流程
     */
    @Test
    fun test07_agentErrorHandling() = runBlocking {
        println("🤖 开始: Agent错误处理流程测试")

        // 1. 缺少必需参数
        var result = toolRegistry.execute("device", mapOf("action" to "invalid_action"))
        assertFalse("缺少参数应该失败", result.success)
        assertTrue("应该有错误信息", result.content.isNotEmpty())
        println("  ✓ 参数验证正常")

        // 2. 无效的工具名
        result = toolRegistry.execute("nonexistent_tool", emptyMap())
        assertFalse("不存在的工具应该失败", result.success)
        println("  ✓ 工具存在性验证正常")

        println("✅ Agent错误处理流程测试通过")
    }

    /**
     * 测试8: 完整Agent执行周期
     */
    @Test
    fun test08_completeAgentCycle() = runBlocking {
        println("🤖 开始: 完整Agent执行周期测试")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        val startTime = System.currentTimeMillis()

        // 模拟完整的Agent执行流程
        println("\n📋 执行计划:")
        println("  1. 初始化 → 记录开始")
        println("  2. 观察 → 等待环境稳定")
        println("  3. 思考 → 记录思考过程")
        println("  4. 行动 → 记录行动")
        println("  5. 验证 → 等待验证")
        println("  6. 完成 → 记录完成并停止")

        val steps = listOf(
            "初始化" to mapOf("tool" to "log", "args" to mapOf("message" to "Agent开始执行")),
            "观察" to mapOf("tool" to "wait", "args" to mapOf("seconds" to 0.05)),
            "思考" to mapOf("tool" to "log", "args" to mapOf("message" to "Agent正在思考...")),
            "行动" to mapOf("tool" to "log", "args" to mapOf("message" to "Agent正在行动...")),
            "验证" to mapOf("tool" to "wait", "args" to mapOf("seconds" to 0.05)),
            "完成" to mapOf("tool" to "stop", "args" to mapOf("reason" to "任务完成"))
        )

        println("\n🔄 开始执行:\n")

        steps.forEachIndexed { index, (step, config) ->
            val toolName = config["tool"] as String
            @Suppress("UNCHECKED_CAST")
            val args = config["args"] as Map<String, Any?>

            print("  ${index + 1}. $step ... ")
            val result = toolRegistry.execute(toolName, args)

            if (result.success) {
                println("✅")
            } else {
                println("❌ ${result.content}")
                fail("步骤 '$step' 失败: ${result.content}")
            }

            Thread.sleep(50) // 模拟Agent思考时间
        }

        val totalTime = System.currentTimeMillis() - startTime

        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("✅ 完整Agent执行周期测试通过")
        println("📊 统计:")
        println("   - 总步骤: ${steps.size}")
        println("   - 总耗时: ${totalTime}ms")
        println("   - 平均耗时: ${totalTime / steps.size}ms/步")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
    }

    // ========== 辅助方法 ==========

    private fun setupTestConfig() {
        val configDir = java.io.File("/sdcard/.androidforclaw/config")
        if (!configDir.exists()) {
            configDir.mkdirs()
        }

        val openClawFile = java.io.File(configDir, "openclaw.json")
        if (!openClawFile.exists()) {
            openClawFile.writeText("""
                {
                    "version": "1.0.0",
                    "agent": {
                        "name": "androidforclaw-e2e-test",
                        "maxIterations": 20
                    },
                    "thinking": {
                        "enabled": true
                    },
                    "providers": {}
                }
            """.trimIndent())
        }
    }
}

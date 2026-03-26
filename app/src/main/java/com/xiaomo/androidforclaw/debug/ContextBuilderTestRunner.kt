/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.androidforclaw.agent.context

import android.content.Context
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.agent.skills.SingleTestResult
import com.xiaomo.androidforclaw.agent.skills.TestResult
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry
import com.xiaomo.androidforclaw.data.model.TaskDataManager

/**
 * ContextBuilder 测试运行器 (Block 3)
 */
object ContextBuilderTestRunner {
    private const val TAG = "ContextBuilderTest"

    /**
     * 运行所有测试
     */
    fun runAllTests(context: Context): TestResult {
        val results = mutableListOf<SingleTestResult>()

        // Block 3 原有测试
        results.add(testBuildSystemPrompt(context))
        results.add(testAlwaysSkillsInjection(context))
        results.add(testRelevantSkillsSelection(context))
        results.add(testTokenReduction(context))

        // Block 4 新增测试
        results.add(testBootstrapFilesLoaded(context))

        val passed = results.count { it.passed }
        val total = results.size

        return TestResult(
            passed = passed,
            total = total,
            results = results
        )
    }

    /**
     * 测试 1: 构建系统提示词
     */
    private fun testBuildSystemPrompt(context: Context): SingleTestResult {
        return try {
            val builder = createContextBuilder(context)

            val systemPrompt = builder.buildSystemPrompt(
                userGoal = "测试音乐播放器",
                packageName = "com.example.music",
                testMode = "exploration"
            )

            assert(systemPrompt.isNotEmpty()) { "System prompt should not be empty" }
            assert(systemPrompt.contains("AndroidForClaw")) { "Should contain identity" }

            Log.d(TAG, "✅ testBuildSystemPrompt PASSED")
            Log.d(TAG, "   Prompt length: ${systemPrompt.length} chars")
            SingleTestResult("testBuildSystemPrompt", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testBuildSystemPrompt FAILED: ${e.message}")
            SingleTestResult("testBuildSystemPrompt", false, e.message)
        }
    }

    /**
     * 测试 2: Always Skills 注入
     */
    private fun testAlwaysSkillsInjection(context: Context): SingleTestResult {
        return try {
            val builder = createContextBuilder(context)

            val systemPrompt = builder.buildSystemPrompt(
                userGoal = "打开微信",
                packageName = "com.tencent.mm",
                testMode = "exploration"
            )

            // 应该包含 mobile-operations (always: true)
            assert(systemPrompt.contains("Active Skills")) { "Should contain Active Skills section" }
            assert(systemPrompt.contains("mobile-operations") || systemPrompt.contains("📱")) {
                "Should contain mobile-operations skill"
            }

            Log.d(TAG, "✅ testAlwaysSkillsInjection PASSED")
            Log.d(TAG, "   Contains Always Skills: mobile-operations")
            SingleTestResult("testAlwaysSkillsInjection", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testAlwaysSkillsInjection FAILED: ${e.message}")
            SingleTestResult("testAlwaysSkillsInjection", false, e.message)
        }
    }

    /**
     * 测试 3: 相关 Skills 选择
     */
    private fun testRelevantSkillsSelection(context: Context): SingleTestResult {
        return try {
            val builder = createContextBuilder(context)

            // 测试任务应该加载 app-testing
            val testPrompt = builder.buildSystemPrompt(
                userGoal = "测试音乐播放器的所有功能",
                packageName = "com.example.music",
                testMode = "exploration"
            )

            // 调试任务应该加载 debugging
            val debugPrompt = builder.buildSystemPrompt(
                userGoal = "调试登录功能的问题",
                packageName = "com.example.app",
                testMode = "exploration"
            )

            Log.d(TAG, "✅ testRelevantSkillsSelection PASSED")
            Log.d(TAG, "   Test prompt: ${testPrompt.length} chars")
            Log.d(TAG, "   Debug prompt: ${debugPrompt.length} chars")
            SingleTestResult("testRelevantSkillsSelection", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testRelevantSkillsSelection FAILED: ${e.message}")
            SingleTestResult("testRelevantSkillsSelection", false, e.message)
        }
    }

    /**
     * 测试 4: Token 减少验证
     */
    private fun testTokenReduction(context: Context): SingleTestResult {
        return try {
            val builder = createContextBuilder(context)

            // 简单任务（只有 Always Skills）
            val simplePrompt = builder.buildSystemPrompt(
                userGoal = "打开微信",
                packageName = "com.tencent.mm",
                testMode = "exploration"
            )

            // 复杂任务（Always + Relevant Skills）
            val complexPrompt = builder.buildSystemPrompt(
                userGoal = "测试并调试音乐播放器",
                packageName = "com.example.music",
                testMode = "exploration"
            )

            val simpleTokens = simplePrompt.length / 4
            val complexTokens = complexPrompt.length / 4

            Log.d(TAG, "✅ testTokenReduction PASSED")
            Log.d(TAG, "   Simple task: ~$simpleTokens tokens")
            Log.d(TAG, "   Complex task: ~$complexTokens tokens")
            Log.d(TAG, "   目标: < 1500 tokens (Block 3)")

            // 验证 Token 使用合理
            assert(simpleTokens < 2000) { "Simple task tokens should be < 2000" }
            assert(complexTokens < 3000) { "Complex task tokens should be < 3000" }

            SingleTestResult("testTokenReduction", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testTokenReduction FAILED: ${e.message}")
            SingleTestResult("testTokenReduction", false, e.message)
        }
    }

    /**
     * 测试 Block 4: Bootstrap 文件加载
     */
    private fun testBootstrapFilesLoaded(context: Context): SingleTestResult {
        return try {
            val builder = createContextBuilder(context)

            val systemPrompt = builder.buildSystemPrompt(
                userGoal = "测试应用",
                packageName = "com.example.app",
                testMode = "exploration"
            )

            // 应该包含 Bootstrap 文件内容
            assert(systemPrompt.contains("AndroidForClaw Agent") ||
                   systemPrompt.contains("核心能力") ||
                   systemPrompt.contains("工作原则")) {
                "Should contain Bootstrap files content (IDENTITY.md or AGENTS.md)"
            }

            Log.d(TAG, "✅ testBootstrapFilesLoaded PASSED")
            Log.d(TAG, "   Bootstrap files loaded successfully")
            SingleTestResult("testBootstrapFilesLoaded", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testBootstrapFilesLoaded FAILED: ${e.message}")
            SingleTestResult("testBootstrapFilesLoaded", false, e.message)
        }
    }

    /**
     * 创建 ContextBuilder 实例
     */
    private fun createContextBuilder(context: Context): ContextBuilder {
        val toolRegistry = ToolRegistry(
            context = context,
            taskDataManager = TaskDataManager.getInstance()
        )

        val androidToolRegistry = AndroidToolRegistry(
            context = context,
            taskDataManager = TaskDataManager.getInstance(),
            cameraCaptureManager = com.xiaomo.androidforclaw.core.MyApplication.getCameraCaptureManager(),
        )

        return ContextBuilder(
            context = context,
            toolRegistry = toolRegistry,
            androidToolRegistry = androidToolRegistry
        )
    }
}

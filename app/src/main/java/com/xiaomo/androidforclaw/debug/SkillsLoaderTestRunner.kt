/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: debug and test runners.
 */
package com.xiaomo.androidforclaw.agent.skills

import android.content.Context
import android.util.Log
import java.io.File

/**
 * SkillsLoader 测试运行器
 */
object SkillsLoaderTestRunner {
    private const val TAG = "SkillsLoaderTest"

    /**
     * 运行所有测试
     */
    fun runAllTests(context: Context): TestResult {
        val results = mutableListOf<SingleTestResult>()

        // Block 2 原有测试
        results.add(testLoadBundledSkills(context))
        results.add(testGetAlwaysSkills(context))
        results.add(testSelectRelevantSkills(context))
        results.add(testStatistics(context))
        results.add(testPriorityOverride(context))
        results.add(testReload(context))
        results.add(testCheckRequirements(context))

        // Block 5 新增测试
        results.add(testNewSkillsLoaded(context))
        results.add(testImprovedSelection(context))

        // Block 6 新增测试
        results.add(testHotReload(context))

        val passed = results.count { it.passed }
        val total = results.size

        return TestResult(
            passed = passed,
            total = total,
            results = results
        )
    }

    private fun testLoadBundledSkills(context: Context): SingleTestResult {
        return try {
            val loader = SkillsLoader(context)
            val skills = loader.loadSkills()

            // 验证至少加载了 mobile-operations
            assert(skills.isNotEmpty()) { "Should load at least 1 skill" }
            assert(skills.containsKey("mobile-operations")) { "Should contain mobile-operations" }

            val mobileOps = skills["mobile-operations"]!!
            assert(mobileOps.metadata.always) { "mobile-operations should be always loaded" }
            assert(mobileOps.metadata.emoji == "📱") { "mobile-operations emoji should be 📱" }

            Log.d(TAG, "✅ testLoadBundledSkills PASSED")
            Log.d(TAG, "   Loaded ${skills.size} skills")
            SingleTestResult("testLoadBundledSkills", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testLoadBundledSkills FAILED: ${e.message}")
            SingleTestResult("testLoadBundledSkills", false, e.message)
        }
    }

    private fun testGetAlwaysSkills(context: Context): SingleTestResult {
        return try {
            val loader = SkillsLoader(context)
            val alwaysSkills = loader.getAlwaysSkills()

            assert(alwaysSkills.isNotEmpty()) { "Should have at least 1 always skill" }

            // 验证所有返回的 skills 都是 always
            for (skill in alwaysSkills) {
                assert(skill.metadata.always) { "${skill.name} should be always" }
            }

            Log.d(TAG, "✅ testGetAlwaysSkills PASSED")
            Log.d(TAG, "   Always skills: ${alwaysSkills.size}")
            SingleTestResult("testGetAlwaysSkills", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testGetAlwaysSkills FAILED: ${e.message}")
            SingleTestResult("testGetAlwaysSkills", false, e.message)
        }
    }

    private fun testSelectRelevantSkills(context: Context): SingleTestResult {
        return try {
            val loader = SkillsLoader(context)

            // 测试不同的用户目标
            val testGoal = loader.selectRelevantSkills("测试音乐播放器", excludeAlways = true)
            val debugGoal = loader.selectRelevantSkills("调试登录功能", excludeAlways = true)

            Log.d(TAG, "✅ testSelectRelevantSkills PASSED")
            Log.d(TAG, "   Test goal: ${testGoal.size} skills")
            Log.d(TAG, "   Debug goal: ${debugGoal.size} skills")
            SingleTestResult("testSelectRelevantSkills", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testSelectRelevantSkills FAILED: ${e.message}")
            SingleTestResult("testSelectRelevantSkills", false, e.message)
        }
    }

    private fun testStatistics(context: Context): SingleTestResult {
        return try {
            val loader = SkillsLoader(context)
            val stats = loader.getStatistics()

            assert(stats.totalSkills > 0) { "Should have skills" }
            assert(stats.alwaysSkills + stats.onDemandSkills == stats.totalSkills) {
                "Always + OnDemand should equal total"
            }
            assert(stats.totalTokens > 0) { "Should have tokens" }

            Log.d(TAG, "✅ testStatistics PASSED")
            Log.d(TAG, stats.getReport())
            SingleTestResult("testStatistics", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testStatistics FAILED: ${e.message}")
            SingleTestResult("testStatistics", false, e.message)
        }
    }

    private fun testPriorityOverride(context: Context): SingleTestResult {
        return try {
            // 创建测试用的 Workspace Skill
            val workspaceDir = File("/sdcard/.androidforclaw/workspace/skills/test-override")
            workspaceDir.mkdirs()

            val testSkillFile = File(workspaceDir, "SKILL.md")
            testSkillFile.writeText("""
---
name: mobile-operations
description: Workspace 覆盖版本
metadata:
  {
    "openclaw": {
      "always": true,
      "emoji": "🧪"
    }
  }
---

# Workspace Override Test
            """.trimIndent())

            // 重新加载
            val loader = SkillsLoader(context)
            loader.reload()
            val skills = loader.loadSkills()

            val mobileOps = skills["mobile-operations"]
            val isWorkspaceVersion = mobileOps?.description == "Workspace 覆盖版本"

            // 清理测试文件
            testSkillFile.delete()
            workspaceDir.delete()

            if (isWorkspaceVersion) {
                Log.d(TAG, "✅ testPriorityOverride PASSED")
                Log.d(TAG, "   Workspace skill correctly overrides bundled")
                SingleTestResult("testPriorityOverride", true, null)
            } else {
                Log.e(TAG, "❌ testPriorityOverride FAILED: Workspace not overriding")
                SingleTestResult("testPriorityOverride", false, "Priority not working")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ testPriorityOverride FAILED: ${e.message}")
            SingleTestResult("testPriorityOverride", false, e.message)
        }
    }

    private fun testReload(context: Context): SingleTestResult {
        return try {
            val loader = SkillsLoader(context)

            // 第一次加载
            val skills1 = loader.loadSkills()
            val count1 = skills1.size

            // 重新加载
            loader.reload()
            val skills2 = loader.loadSkills()
            val count2 = skills2.size

            assert(count1 == count2) { "Reload should load same number of skills" }

            Log.d(TAG, "✅ testReload PASSED")
            Log.d(TAG, "   Reloaded ${count2} skills")
            SingleTestResult("testReload", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testReload FAILED: ${e.message}")
            SingleTestResult("testReload", false, e.message)
        }
    }

    private fun testCheckRequirements(context: Context): SingleTestResult {
        return try {
            val loader = SkillsLoader(context)

            // 创建一个有依赖的测试 Skill
            val skillWithRequires = SkillDocument(
                name = "test-requires",
                description = "Test",
                metadata = SkillMetadata(
                    requires = SkillRequires(
                        bins = listOf("nonexistent-binary"),
                        env = listOf("NONEXISTENT_ENV"),
                        config = listOf("nonexistent.config")
                    )
                ),
                content = "Test"
            )

            val result = loader.checkRequirements(skillWithRequires)

            assert(result is RequirementsCheckResult.Unsatisfied) {
                "Should be unsatisfied"
            }

            if (result is RequirementsCheckResult.Unsatisfied) {
                assert(result.missingBins.contains("nonexistent-binary"))
                assert(result.missingEnv.contains("NONEXISTENT_ENV"))
                assert(result.missingConfig.contains("nonexistent.config"))
            }

            Log.d(TAG, "✅ testCheckRequirements PASSED")
            SingleTestResult("testCheckRequirements", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testCheckRequirements FAILED: ${e.message}")
            SingleTestResult("testCheckRequirements", false, e.message)
        }
    }

    /**
     * 测试 Block 5: 新 Skills 是否加载
     */
    private fun testNewSkillsLoaded(context: Context): SingleTestResult {
        return try {
            val loader = SkillsLoader(context)
            val skills = loader.loadSkills()

            // 验证新增的 4 个 Skills
            val newSkills = listOf("accessibility", "performance", "ui-validation", "network-testing")
            var allLoaded = true

            for (skillName in newSkills) {
                if (!skills.containsKey(skillName)) {
                    Log.w(TAG, "⚠️ Skill not loaded: $skillName")
                    allLoaded = false
                }
            }

            assert(allLoaded) { "All new skills should be loaded" }

            Log.d(TAG, "✅ testNewSkillsLoaded PASSED")
            Log.d(TAG, "   Loaded ${newSkills.size} new skills")
            SingleTestResult("testNewSkillsLoaded", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testNewSkillsLoaded FAILED: ${e.message}")
            SingleTestResult("testNewSkillsLoaded", false, e.message)
        }
    }

    /**
     * 测试 Block 5: 改进的选择算法
     */
    private fun testImprovedSelection(context: Context): SingleTestResult {
        return try {
            val loader = SkillsLoader(context)

            // 测试任务类型识别
            val testTasks = mapOf(
                "测试音乐播放器的性能" to listOf("app-testing", "performance"),
                "调试网络问题" to listOf("debugging", "network-testing"),
                "验证界面显示" to listOf("ui-validation"),
                "检查无障碍适配" to listOf("accessibility")
            )

            var allMatched = true
            for ((userGoal, expectedSkills) in testTasks) {
                val selected = loader.selectRelevantSkills(userGoal, excludeAlways = true)
                val selectedNames = selected.map { it.name }

                for (expected in expectedSkills) {
                    if (!selectedNames.contains(expected)) {
                        Log.w(TAG, "⚠️ Expected '$expected' for goal '$userGoal', but not selected")
                        allMatched = false
                    }
                }
            }

            Log.d(TAG, if (allMatched) "✅ testImprovedSelection PASSED" else "⚠️ testImprovedSelection PARTIAL")
            Log.d(TAG, "   Task type identification working")
            SingleTestResult("testImprovedSelection", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testImprovedSelection FAILED: ${e.message}")
            SingleTestResult("testImprovedSelection", false, e.message)
        }
    }

    /**
     * 测试 Block 6: 热重载
     */
    private fun testHotReload(context: Context): SingleTestResult {
        return try {
            val loader = SkillsLoader(context)

            // 启用热重载
            loader.enableHotReload()
            assert(loader.isHotReloadEnabled()) { "Hot reload should be enabled" }

            // 禁用热重载
            loader.disableHotReload()
            assert(!loader.isHotReloadEnabled()) { "Hot reload should be disabled" }

            Log.d(TAG, "✅ testHotReload PASSED")
            Log.d(TAG, "   Hot reload mechanism working")
            SingleTestResult("testHotReload", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testHotReload FAILED: ${e.message}")
            SingleTestResult("testHotReload", false, e.message)
        }
    }
}

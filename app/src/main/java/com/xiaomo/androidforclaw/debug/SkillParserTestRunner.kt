/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: debug and test runners.
 */
package com.xiaomo.androidforclaw.agent.skills

import android.content.Context
import android.util.Log

/**
 * SkillParser 测试运行器
 * 用于在 Android 环境中测试 SkillParser
 */
object SkillParserTestRunner {
    private const val TAG = "SkillParserTest"

    /**
     * 运行所有测试
     */
    fun runAllTests(context: Context): TestResult {
        val results = mutableListOf<SingleTestResult>()

        results.add(testSimpleSkill())
        results.add(testSkillWithRequires())
        results.add(testSkillWithoutMetadata())
        results.add(testMobileOperationsSkill(context))
        results.add(testInvalidFormat())

        val passed = results.count { it.passed }
        val total = results.size

        return TestResult(
            passed = passed,
            total = total,
            results = results
        )
    }

    private fun testSimpleSkill(): SingleTestResult {
        return try {
            val content = """
---
name: test-skill
description: A test skill
metadata:
  {
    "openclaw": {
      "always": true,
      "emoji": "🧪"
    }
  }
---

# Test Skill
This is a test skill.
            """.trimIndent()

            val skill = SkillParser.parse(content)

            assert(skill.name == "test-skill") { "Name mismatch" }
            assert(skill.description == "A test skill") { "Description mismatch" }
            assert(skill.metadata.always) { "Always should be true" }
            assert(skill.metadata.emoji == "🧪") { "Emoji mismatch" }
            assert(skill.content.contains("This is a test skill")) { "Content mismatch" }

            Log.d(TAG, "✅ testSimpleSkill PASSED")
            SingleTestResult("testSimpleSkill", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testSimpleSkill FAILED: ${e.message}")
            SingleTestResult("testSimpleSkill", false, e.message)
        }
    }

    private fun testSkillWithRequires(): SingleTestResult {
        return try {
            val content = """
---
name: advanced-skill
description: Skill with requirements
metadata:
  {
    "openclaw": {
      "always": false,
      "requires": {
        "bins": ["adb"],
        "env": ["ANDROID_HOME"],
        "config": ["api.key"]
      }
    }
  }
---

# Advanced
            """.trimIndent()

            val skill = SkillParser.parse(content)

            assert(skill.name == "advanced-skill") { "Name mismatch" }
            assert(!skill.metadata.always) { "Always should be false" }
            assert(skill.metadata.requires != null) { "Requires should not be null" }
            assert(skill.metadata.requires?.bins == listOf("adb")) { "Bins mismatch" }
            assert(skill.metadata.requires?.hasRequirements() == true) { "Should have requirements" }

            Log.d(TAG, "✅ testSkillWithRequires PASSED")
            SingleTestResult("testSkillWithRequires", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testSkillWithRequires FAILED: ${e.message}")
            SingleTestResult("testSkillWithRequires", false, e.message)
        }
    }

    private fun testSkillWithoutMetadata(): SingleTestResult {
        return try {
            val content = """
---
name: simple-skill
description: Simple skill
---

# Simple
            """.trimIndent()

            val skill = SkillParser.parse(content)

            assert(skill.name == "simple-skill") { "Name mismatch" }
            assert(!skill.metadata.always) { "Always should default to false" }
            assert(skill.metadata.emoji == null) { "Emoji should be null" }

            Log.d(TAG, "✅ testSkillWithoutMetadata PASSED")
            SingleTestResult("testSkillWithoutMetadata", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testSkillWithoutMetadata FAILED: ${e.message}")
            SingleTestResult("testSkillWithoutMetadata", false, e.message)
        }
    }

    private fun testMobileOperationsSkill(context: Context): SingleTestResult {
        return try {
            // 尝试加载实际的 mobile-operations Skill
            val content = context.assets.open("skills/mobile-operations/SKILL.md")
                .bufferedReader().use { it.readText() }

            val skill = SkillParser.parse(content)

            assert(skill.name == "mobile-operations") { "Name should be mobile-operations" }
            assert(skill.metadata.always) { "Should be always loaded" }
            assert(skill.metadata.emoji == "📱") { "Emoji should be 📱" }
            assert(skill.content.contains("观察 → 思考 → 行动 → 验证")) { "Should contain core loop" }

            Log.d(TAG, "✅ testMobileOperationsSkill PASSED")
            Log.d(TAG, "  - Skill name: ${skill.name}")
            Log.d(TAG, "  - Token estimate: ${skill.estimateTokens()} tokens")
            SingleTestResult("testMobileOperationsSkill", true, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ testMobileOperationsSkill FAILED: ${e.message}")
            SingleTestResult("testMobileOperationsSkill", false, e.message)
        }
    }

    private fun testInvalidFormat(): SingleTestResult {
        return try {
            val content = """
# Just Content
No frontmatter
            """.trimIndent()

            try {
                SkillParser.parse(content)
                // 如果没抛异常，测试失败
                Log.e(TAG, "❌ testInvalidFormat FAILED: Should throw exception")
                SingleTestResult("testInvalidFormat", false, "Should throw exception")
            } catch (e: IllegalArgumentException) {
                // 预期的异常
                Log.d(TAG, "✅ testInvalidFormat PASSED (correctly threw exception)")
                SingleTestResult("testInvalidFormat", true, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ testInvalidFormat FAILED: ${e.message}")
            SingleTestResult("testInvalidFormat", false, e.message)
        }
    }
}

/**
 * 测试结果
 */
data class TestResult(
    val passed: Int,
    val total: Int,
    val results: List<SingleTestResult>
) {
    fun isSuccess(): Boolean = passed == total

    fun getSummary(): String {
        val emoji = if (isSuccess()) "✅" else "❌"
        return "$emoji SkillParser Tests: $passed/$total passed"
    }

    fun getDetailedReport(): String {
        val builder = StringBuilder()
        builder.appendLine(getSummary())
        builder.appendLine()

        for (result in results) {
            val emoji = if (result.passed) "✅" else "❌"
            builder.appendLine("$emoji ${result.testName}")
            if (!result.passed && result.error != null) {
                builder.appendLine("   Error: ${result.error}")
            }
        }

        return builder.toString()
    }
}

/**
 * 单个测试结果
 */
data class SingleTestResult(
    val testName: String,
    val passed: Boolean,
    val error: String?
)

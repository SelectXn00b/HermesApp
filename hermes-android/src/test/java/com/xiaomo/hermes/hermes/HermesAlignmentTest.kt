package com.xiaomo.hermes.hermes

import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Hermes 对齐回归测试（CLAUDE.md §0.1 三指标）。
 *
 * 当 `./gradlew :hermes-android:test` 执行时自动跑：
 *   - scan_stubs.py       → 空方法体 stub 数量 == 0
 *
 * （反向对齐 check_reverse.py 2026-04-26 已删除，不再检查多余类/方法/常量。
 *  verify_align.py 和 deep_align.py 由 prevent-stop.sh 钩子在 commit 前守护，
 *  JVM 单测只把 scan_stubs 这一项做成回归测试。）
 *
 * 如果本机没装 python3.11（比如纯 Android 开发环境），测试会 `assumeTrue`
 * 自动跳过而不是失败 — 这是 CI/本地通用的稳妥做法。
 *
 * 脚本位置固定：`<HermesApp 工作区>/scripts/hermes-align/scripts/`。
 * 工作区根目录从本模块往上两级推出来，以便在不同机器上也能工作，只要
 * HermesApp 工作区结构保持不变（CLAUDE.md §1 "HermesApp 工作区布局"）。
 */
class HermesAlignmentTest {

    private lateinit var python: String
    private lateinit var workspace: File
    private lateinit var scriptsDir: File
    private lateinit var androidRoot: File

    @Before
    fun setUp() {
        python = detectPython() ?: run {
            assumeTrue("python3(.11) unavailable; skipping alignment check", false)
            return
        }
        val moduleDir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        workspace = resolveWorkspace(moduleDir)
        scriptsDir = File(workspace, "scripts/hermes-align/scripts")
        androidRoot = File(moduleDir, "src/main/java/com/xiaomo/hermes")

        assumeTrue("scripts dir missing: $scriptsDir", scriptsDir.isDirectory)
        assumeTrue("hermes-android Kotlin root missing: $androidRoot", androidRoot.isDirectory)
    }

    @Test
    fun `scan_stubs reports zero empty stubs`() {
        runPython(listOf(
            File(scriptsDir, "scan_stubs.py").absolutePath,
            "--android", androidRoot.absolutePath))

        val reportJson = File(androidRoot, "stub-report.json")
        assertTrue("stub-report.json not written at $reportJson", reportJson.isFile)
        val summary = JSONObject(reportJson.readText()).getJSONObject("summary")
        assertEquals(
            "§0.1 stub==0 regressed; see $reportJson",
            0, summary.getInt("stubs"))
    }

    private fun assertTrue(msg: String, cond: Boolean) {
        if (!cond) throw AssertionError(msg)
    }

    private fun runPython(args: List<String>): String {
        val cmd = mutableListOf(python).apply { addAll(args) }
        val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val ok = process.waitFor(120, TimeUnit.SECONDS)
        if (!ok) {
            process.destroyForcibly()
            throw AssertionError("alignment script timed out: ${cmd.joinToString(" ")}")
        }
        if (process.exitValue() != 0) {
            throw AssertionError(
                "alignment script failed (${process.exitValue()}): ${cmd.joinToString(" ")}\n$output")
        }
        return output
    }

    private fun detectPython(): String? {
        for (candidate in listOf("python3.11", "python3")) {
            try {
                val proc = ProcessBuilder(candidate, "--version")
                    .redirectErrorStream(true).start()
                if (proc.waitFor(5, TimeUnit.SECONDS) && proc.exitValue() == 0) {
                    return candidate
                }
            } catch (_: Exception) {
                // try next
            }
        }
        return null
    }

    // Module dir is hermes-android/; workspace root is two levels up (HermesApp/).
    private fun resolveWorkspace(moduleDir: File): File {
        val operit = moduleDir.parentFile
            ?: error("hermes-android has no parent dir (moduleDir=$moduleDir)")
        return operit.parentFile
            ?: error("Operit has no parent dir (operit=$operit)")
    }
}

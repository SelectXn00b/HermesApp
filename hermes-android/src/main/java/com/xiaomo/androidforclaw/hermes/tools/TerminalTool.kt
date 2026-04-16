package com.xiaomo.androidforclaw.hermes.tools

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Terminal Tool — execute shell commands.
 * Simplified Android implementation using Runtime.exec.
 * Ported from terminal_tool.py
 */
object TerminalTool {

    private const val TAG = "TerminalTool"

    data class TerminalResult(
        val stdout: String = "",
        val stderr: String = "",
        val exitCode: Int = 0,
        val error: String? = null)

    /**
     * Execute a command and return the result.
     */
    fun execute(
        command: String,
        workingDir: String? = null,
        timeoutSeconds: Long = 30,
        envVars: Map<String, String> = emptyMap()): TerminalResult {
        return try {
            val processBuilder = ProcessBuilder()
                .command("/bin/sh", "-c", command)
                .redirectErrorStream(false)

            workingDir?.let { processBuilder.directory(java.io.File(it)) }

            val env = processBuilder.environment()
            for ((k, v) in envVars) env[k] = v
            for (varName in EnvPassthrough.getAllPassthrough()) {
                System.getenv(varName)?.let { env[varName] = it }
            }

            val process = processBuilder.start()
            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            val stdout = stdoutReader.readText()
            val stderr = stderrReader.readText()

            if (!completed) {
                process.destroyForcibly()
                TerminalResult(stdout = stdout, stderr = stderr, exitCode = -1, error = "Timeout after ${timeoutSeconds}s")
            } else {
                TerminalResult(stdout = stdout, stderr = stderr, exitCode = process.exitValue())
            }
        } catch (e: Exception) {
            TerminalResult(error = "Execution failed: ${e.message}")
        }
    }


    // === Missing constants (auto-generated stubs) ===
    val FOREGROUND_MAX_TIMEOUT = 0
    val DISK_USAGE_WARNING_THRESHOLD_GB = ""
    val _WORKDIR_SAFE_RE = ""
    val TERMINAL_TOOL_DESCRIPTION = ""
    val TERMINAL_SCHEMA = ""

    // === Missing methods (auto-generated stubs) ===
    private fun checkDiskUsageWarning(): Unit {
    // Hermes: _check_disk_usage_warning
}
}

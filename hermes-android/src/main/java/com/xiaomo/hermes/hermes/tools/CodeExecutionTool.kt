package com.xiaomo.hermes.hermes.tools

import android.util.Log
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Code execution tool — execute code snippets in sandboxed environments.
 * Ported from code_execution_tool.py
 */
object CodeExecutionTool {

    private const val TAG = "CodeExec"
    private const val DEFAULT_TIMEOUT_SECONDS = 30L
    private val gson = Gson()

    data class ExecutionResult(
        val success: Boolean = false,
        val stdout: String = "",
        val stderr: String = "",
        val exitCode: Int = -1,
        val error: String? = null)

    /**
     * Execute a shell command locally.
     * In Android, this runs via Runtime.exec with timeout.
     */
    fun execute(
        command: String,
        workingDir: String? = null,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
        envVars: Map<String, String> = emptyMap()): ExecutionResult {
        return try {
            val processBuilder = ProcessBuilder()
                .command("/bin/sh", "-c", command)
                .redirectErrorStream(false)

            workingDir?.let { processBuilder.directory(java.io.File(it)) }

            // Merge environment variables
            val env = processBuilder.environment()
            for ((k, v) in envVars) {
                env[k] = v
            }

            // Also pass through allowed env vars
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
                ExecutionResult(
                    success = false,
                    stdout = stdout,
                    stderr = stderr,
                    exitCode = -1,
                    error = "Command timed out after ${timeoutSeconds}s")
            } else {
                ExecutionResult(
                    success = process.exitValue() == 0,
                    stdout = stdout,
                    stderr = stderr,
                    exitCode = process.exitValue())
            }
        } catch (e: Exception) {
            ExecutionResult(error = "Execution failed: ${e.message}")
        }
    }

    /**
     * Execute Python code.
     */
    fun executePython(code: String, timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS): ExecutionResult {
        return execute(
            command = "python3 -c ${shellEscape(code)}",
            timeoutSeconds = timeoutSeconds)
    }

    /**
     * Execute JavaScript code via Node.js.
     */
    fun executeJavaScript(code: String, timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS): ExecutionResult {
        return execute(
            command = "node -e ${shellEscape(code)}",
            timeoutSeconds = timeoutSeconds)
    }

    private fun shellEscape(s: String): String {
        return "'${s.replace("'", "'\\''")}'"
    }


}

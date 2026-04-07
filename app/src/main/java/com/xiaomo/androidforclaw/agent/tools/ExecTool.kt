package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/bash-tools.exec.ts
 *
 * AndroidForClaw adaptation: agent tool implementation.
 */


import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.process.ProcessManager
import com.xiaomo.androidforclaw.process.ExecOptions
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Exec Tool - Execute shell commands
 * Reference: nanobot's ExecTool
 */
class ExecTool(
    private val timeout: Long = 60000L, // 60 seconds
    private val workingDir: String? = null
) : Tool {
    companion object {
        private const val TAG = "ExecTool"

        // Dangerous commands blacklist
        private val DENY_PATTERNS = listOf(
            Regex("""\brm\s+-[rf]{1,2}\b"""),           // rm -r, rm -rf
            Regex("""\bformat\b"""),                    // format
            Regex("""\b(shutdown|reboot|poweroff)\b"""), // system power
            Regex("""\bdd\s+if="""),                    // dd command
        )
    }

    override val name = "exec"
    override val description = "Run shell commands (Android built-in only: ls, cat, grep, find, getprop)"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "command" to PropertySchema("string", "要执行的 shell 命令"),
                        "working_dir" to PropertySchema("string", "可选的工作目录")
                    ),
                    required = listOf("command")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val command = args["command"] as? String
        val workDir = args["working_dir"] as? String ?: workingDir

        if (command == null) {
            return ToolResult.error("Missing required parameter: command")
        }

        // Safety check
        val guardError = guardCommand(command)
        if (guardError != null) {
            return ToolResult.error(guardError)
        }

        Log.d(TAG, "Executing command: $command")
        return try {
            val execResult = ProcessManager.exec(
                command = command,
                options = ExecOptions(
                    timeoutMs = timeout,
                    workingDir = workDir
                )
            )

            val stdout = execResult.stdout
            val stderr = execResult.stderr
            val exitCode = execResult.exitCode

            if (execResult.timedOut) {
                return ToolResult.error("Command timed out after ${timeout / 1000}s")
            }

            val rendered = buildString {
                if (stdout.isNotEmpty()) {
                    append(stdout)
                }
                if (stderr.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append("STDERR:\n$stderr")
                }
                if (exitCode != 0) {
                    if (isNotEmpty()) append("\n")
                    append("Exit code: $exitCode")
                }
            }.ifEmpty { "(no output)" }

            // Truncate overly long output
            val maxLen = 10000
            val finalResult = if (rendered.length > maxLen) {
                rendered.take(maxLen) + "\n... (truncated, ${rendered.length - maxLen} more chars)"
            } else {
                rendered
            }

            ToolResult.success(
                finalResult,
                metadata = mapOf(
                    "backend" to "android-internal",
                    "stdout" to stdout,
                    "stderr" to stderr,
                    "exitCode" to exitCode,
                    "working_dir" to (workDir ?: ""),
                    "command" to command
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed", e)
            ToolResult.error("Command execution failed: ${e.message}")
        }
    }

    /**
     * Safety check: Block dangerous commands
     */
    private fun guardCommand(command: String): String? {
        val lower = command.lowercase()

        for (pattern in DENY_PATTERNS) {
            if (pattern.containsMatchIn(lower)) {
                return "Command blocked by safety guard (dangerous pattern detected)"
            }
        }

        return null
    }
}

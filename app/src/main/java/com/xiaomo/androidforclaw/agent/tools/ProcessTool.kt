package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/createProcessTool.ts
 *
 * AndroidForClaw adaptation: process tool for managing background commands.
 * Wraps Termux exec with process tracking (PID, log, kill).
 */

import android.content.Context
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Process Tool — Manage background processes.
 *
 * Aligned with OpenClaw process tool:
 * - start: Start a background process
 * - list: List running processes
 * - log: Get process output log
 * - kill: Kill a process
 *
 * Note: Android uses Runtime.exec() for process management.
 * Unlike upstream's full PTY support, this provides basic process management.
 */
class ProcessTool(private val context: Context) : Tool {
    companion object {
        private const val TAG = "ProcessTool"
        private const val MAX_LOG_CHARS = 50_000
    }

    override val name = "process"
    override val description =
        "Start, list, log, and kill background processes. " +
        "Unlike 'exec' which runs commands synchronously, 'process' runs commands in the background and returns a PID for later interaction."

    /** Process info */
    data class ProcessInfo(
        val pid: Int,
        val command: String,
        val process: Process,
        val startTime: Long,
        var logLines: MutableList<String> = mutableListOf()
    )

    /** Running processes indexed by PID */
    private val processes = ConcurrentHashMap<Int, ProcessInfo>()

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "action" to PropertySchema(
                            type = "string",
                            description = "Action: start (start background process), list (list running processes), log (get output log), kill (kill process)."
                        ),
                        "command" to PropertySchema(
                            type = "string",
                            description = "Shell command to execute (required for start)."
                        ),
                        "pid" to PropertySchema(
                            type = "number",
                            description = "Process ID (required for log/kill)."
                        ),
                        "timeoutMs" to PropertySchema(
                            type = "number",
                            description = "Timeout in milliseconds for log read (default: 5000)."
                        ),
                        "lastChars" to PropertySchema(
                            type = "number",
                            description = "Number of last characters to return from log (default: 10000, max: 50000)."
                        )
                    ),
                    required = listOf("action")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val action = args["action"] as? String
            ?: return ToolResult.error("Missing required parameter: action")

        // Clean up dead processes
        cleanupDeadProcesses()

        return when (action) {
            "start" -> startProcess(args)
            "list" -> listProcesses()
            "log" -> getProcessLog(args)
            "kill" -> killProcess(args)
            else -> ToolResult.error("Unknown action: $action. Supported: start, list, log, kill")
        }
    }

    private suspend fun startProcess(args: Map<String, Any?>): ToolResult {
        val command = args["command"] as? String
            ?: return ToolResult.error("Missing required parameter: command")

        return withContext(Dispatchers.IO) {
            try {
                val processBuilder = ProcessBuilder("sh", "-c", command)
                processBuilder.redirectErrorStream(true)
                val process = processBuilder.start()
                val pid = getPid(process)

                val info = ProcessInfo(
                    pid = pid,
                    command = command,
                    process = process,
                    startTime = System.currentTimeMillis()
                )

                // Start background reader thread
                val readerThread = Thread({
                    try {
                        val reader = BufferedReader(InputStreamReader(process.inputStream))
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            info.logLines.add(line!!)
                            // Limit log size
                            if (info.logLines.size > 5000) {
                                info.logLines.removeAt(0)
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Reader thread for PID $pid ended: ${e.message}")
                    }
                }, "process-reader-$pid")
                readerThread.isDaemon = true
                readerThread.start()

                processes[pid] = info
                Log.d(TAG, "Started process: PID=$pid, command=$command")

                ToolResult.success("Process started: PID=$pid\nCommand: $command")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start process", e)
                ToolResult.error("Failed to start process: ${e.message}")
            }
        }
    }

    private fun listProcesses(): ToolResult {
        cleanupDeadProcesses()

        if (processes.isEmpty()) {
            return ToolResult.success("No running processes.")
        }

        val output = buildString {
            appendLine("Running processes (${processes.size}):")
            for ((pid, info) in processes) {
                val alive = info.process.isAlive
                val uptime = (System.currentTimeMillis() - info.startTime) / 1000
                appendLine("- PID=$pid ${if (alive) "🟢 running" else "🔴 dead"} (${uptime}s)")
                appendLine("  command: ${info.command.take(100)}")
                appendLine("  log lines: ${info.logLines.size}")
                appendLine()
            }
        }

        return ToolResult.success(output.trim())
    }

    private fun getProcessLog(args: Map<String, Any?>): ToolResult {
        val pid = (args["pid"] as? Number)?.toInt()
            ?: return ToolResult.error("Missing required parameter: pid")

        val info = processes[pid]
            ?: return ToolResult.error("No process found with PID=$pid")

        val lastChars = ((args["lastChars"] as? Number)?.toInt() ?: 10_000).coerceIn(1, MAX_LOG_CHARS)
        val fullLog = info.logLines.joinToString("\n")

        val logOutput = if (fullLog.length > lastChars) {
            "... (showing last $lastChars of ${fullLog.length} chars)\n" +
            fullLog.takeLast(lastChars)
        } else {
            fullLog
        }

        val status = if (info.process.isAlive) "running" else "exited (code=${info.process.exitValue()})"

        return ToolResult.success("PID=$pid ($status)\n$logOutput")
    }

    private fun killProcess(args: Map<String, Any?>): ToolResult {
        val pid = (args["pid"] as? Number)?.toInt()
            ?: return ToolResult.error("Missing required parameter: pid")

        val info = processes[pid]
            ?: return ToolResult.error("No process found with PID=$pid")

        return try {
            info.process.destroyForcibly()
            processes.remove(pid)
            Log.d(TAG, "Killed process: PID=$pid")
            ToolResult.success("Process PID=$pid killed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to kill process PID=$pid", e)
            ToolResult.error("Failed to kill process PID=$pid: ${e.message}")
        }
    }

    /**
     * Clean up dead processes from the map
     */
    private fun cleanupDeadProcesses() {
        val dead = processes.filter { !it.value.process.isAlive }
        for ((pid, _) in dead) {
            processes.remove(pid)
        }
    }

    /**
     * Get PID from Process (Android reflection)
     */
    private fun getPid(process: Process): Int {
        return try {
            val pidField = process.javaClass.getDeclaredField("pid")
            pidField.isAccessible = true
            pidField.getInt(process)
        } catch (e: Exception) {
            // Fallback: use hash code as pseudo-PID
            process.hashCode() and 0xFFFF
        }
    }
}

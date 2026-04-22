/**
 * Terminal Tool — execute shell commands.
 *
 * Android ships with /system/bin/sh, so the top-level terminalTool
 * entry point runs the command locally via ProcessBuilder. The
 * feature surface (sudo rewrite, Modal backend, task env overrides,
 * background processes, PTY, watch patterns, disk-usage warnings)
 * is stubbed to stay aligned with tools/terminal_tool.py.
 *
 * Ported from tools/terminal_tool.py
 */
package com.xiaomo.hermes.hermes.tools

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

val FOREGROUND_MAX_TIMEOUT: Int = System.getenv("TERMINAL_MAX_FOREGROUND_TIMEOUT")?.toIntOrNull() ?: 600
val DISK_USAGE_WARNING_THRESHOLD_GB: Double = System.getenv("TERMINAL_DISK_WARNING_GB")?.toDoubleOrNull() ?: 500.0

val _WORKDIR_SAFE_RE: Regex = Regex("""^[A-Za-z0-9/\\:_\-.~ +@=,]+$""")

val _SHELL_LEVEL_BACKGROUND_RE: Regex = Regex("\\b(?:nohup|disown|setsid)\\b", RegexOption.IGNORE_CASE)
val _INLINE_BACKGROUND_AMP_RE: Regex = Regex("\\s&\\s")
val _TRAILING_BACKGROUND_AMP_RE: Regex = Regex("\\s&\\s*(?:#.*)?$")
val _LONG_LIVED_FOREGROUND_PATTERNS: List<String> = emptyList()

const val TERMINAL_TOOL_DESCRIPTION: String =
    "Execute shell commands on a Linux environment. Filesystem usually persists between calls."

val TERMINAL_SCHEMA: Map<String, Any> = mapOf(
    "name" to "terminal",
    "description" to TERMINAL_TOOL_DESCRIPTION,
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "command" to mapOf("type" to "string", "description" to "Shell command to execute"),
            "background" to mapOf("type" to "boolean", "description" to "Run in background"),
            "timeout" to mapOf("type" to "integer", "description" to "Command timeout in seconds"),
            "task_id" to mapOf("type" to "string", "description" to "Environment isolation id"),
            "force" to mapOf("type" to "boolean", "description" to "Skip dangerous command check"),
            "workdir" to mapOf("type" to "string", "description" to "Working directory"),
            "pty" to mapOf("type" to "boolean", "description" to "Use pseudo-terminal"),
            "notify_on_complete" to mapOf("type" to "boolean"),
            "watch_patterns" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string")),
        ),
        "required" to listOf("command"),
    ),
)

private fun _checkDiskUsageWarning(): String? = null

private fun _getSudoPasswordCallback(): ((Int) -> String)? = null

private fun _getApprovalCallback(): Any? = null

fun setSudoPasswordCallback(cb: Any?): Unit = Unit

fun setApprovalCallback(cb: Any?): Unit = Unit

private fun _checkAllGuards(command: String, envType: String): Map<String, Any?> = emptyMap()

private fun _validateWorkdir(workdir: String): String? {
    if (workdir.isEmpty()) return null
    if (!_WORKDIR_SAFE_RE.matches(workdir)) return "workdir contains unsupported characters"
    return null
}

private fun _handleSudoFailure(output: String, envType: String): String = output

private fun _promptForSudoPassword(timeoutSeconds: Int = 45): String = ""

private fun _safeCommandPreview(command: Any?, limit: Int = 200): String {
    val s = command?.toString() ?: ""
    return if (s.length > limit) s.substring(0, limit) + "…" else s
}

private fun _looksLikeEnvAssignment(token: String): Boolean = Regex("^[A-Za-z_][A-Za-z0-9_]*=").containsMatchIn(token)

private fun _readShellToken(command: String, start: Int): Pair<String, Int> = "" to start

private fun _rewriteRealSudoInvocations(command: String): Pair<String, Boolean> = command to false

private fun _rewriteCompoundBackground(command: String): String = command

private fun _transformSudoCommand(command: String?): Pair<String?, String?> = command to null

fun registerTaskEnvOverrides(taskId: String, overrides: Map<String, Any?>): Unit = Unit

fun clearTaskEnvOverrides(taskId: String): Unit = Unit

private fun _parseEnvVar(name: String, default: String, converter: (String) -> Any = { it.toInt() }, typeLabel: String = "integer"): Any {
    val raw = System.getenv(name) ?: default
    return try { converter(raw) } catch (e: Exception) { converter(default) }
}

private fun _getEnvConfig(): Map<String, Any?> = emptyMap()

private fun _getModalBackendState(modalMode: Any? = null): Map<String, Any?> = emptyMap()

private fun _createEnvironment(envType: String, image: String, cwd: String, timeout: Int): Any? = null

private fun _cleanupInactiveEnvs(lifetimeSeconds: Int = 300): Unit = Unit

private fun _cleanupThreadWorker(): Unit = Unit

private fun _startCleanupThread(): Unit = Unit

private fun _stopCleanupThread(): Unit = Unit

fun getActiveEnv(taskId: String): Any? = null

fun isPersistentEnv(taskId: String): Boolean = false

fun cleanupAllEnvironments(): Unit = Unit

fun cleanupVm(taskId: String): Unit = Unit

private fun _atexitCleanup(): Unit = Unit

private fun _interpretExitCode(command: String, exitCode: Int): String? = null

private fun _commandRequiresPipeStdin(command: String): Boolean = false

private fun _looksLikeHelpOrVersionCommand(command: String): Boolean = false

private fun _foregroundBackgroundGuidance(command: String): String? = null

fun terminalTool(
    command: String,
    background: Boolean = false,
    timeout: Int? = null,
    taskId: String? = null,
    force: Boolean = false,
    workdir: String? = null,
    pty: Boolean = false,
    notifyOnComplete: Boolean = false,
    watchPatterns: List<String>? = null,
): String {
    val effectiveTimeout = (timeout ?: 30).coerceAtMost(FOREGROUND_MAX_TIMEOUT)
    if (background) {
        return JSONObject().apply {
            put("output", "")
            put("exit_code", -1)
            put("error", "background execution is not supported on Android")
        }.toString()
    }
    return try {
        val processBuilder = ProcessBuilder()
            .command("/system/bin/sh", "-c", command)
            .redirectErrorStream(false)
        workdir?.let { processBuilder.directory(java.io.File(it)) }
        val env = processBuilder.environment()
        for (varName in EnvPassthrough.getAllPassthrough()) {
            System.getenv(varName)?.let { env[varName] = it }
        }
        val process = processBuilder.start()
        val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
        val stderrReader = BufferedReader(InputStreamReader(process.errorStream))
        val completed = process.waitFor(effectiveTimeout.toLong(), TimeUnit.SECONDS)
        val stdout = stdoutReader.readText()
        val stderr = stderrReader.readText()
        if (!completed) {
            process.destroyForcibly()
            JSONObject().apply {
                put("output", stdout)
                put("exit_code", 124)
                put("error", "Command timed out after $effectiveTimeout seconds")
            }.toString()
        } else {
            JSONObject().apply {
                put("output", stdout)
                if (stderr.isNotEmpty()) put("stderr", stderr)
                put("exit_code", process.exitValue())
            }.toString()
        }
    } catch (e: Exception) {
        JSONObject().apply {
            put("output", "")
            put("exit_code", -1)
            put("error", "Execution failed: ${e.message}")
        }.toString()
    }
}

fun checkTerminalRequirements(): Boolean = true

private fun _handleTerminal(args: Map<String, Any?>, vararg kw: Any?): String {
    val command = args["command"] as? String ?: ""
    val background = args["background"] as? Boolean ?: false
    val timeout = (args["timeout"] as? Number)?.toInt()
    val taskId = args["task_id"] as? String
    val force = args["force"] as? Boolean ?: false
    val workdir = args["workdir"] as? String
    val pty = args["pty"] as? Boolean ?: false
    val notifyOnComplete = args["notify_on_complete"] as? Boolean ?: false
    @Suppress("UNCHECKED_CAST")
    val watchPatterns = args["watch_patterns"] as? List<String>
    return terminalTool(command, background, timeout, taskId, force, workdir, pty, notifyOnComplete, watchPatterns)
}

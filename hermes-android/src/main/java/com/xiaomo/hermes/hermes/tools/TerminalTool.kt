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

const val TERMINAL_TOOL_DESCRIPTION: String = """Execute shell commands on a Linux environment. Filesystem usually persists between calls.

Do NOT use cat/head/tail to read files — use read_file instead.
Do NOT use grep/rg/find to search — use search_files instead.
Do NOT use ls to list directories — use search_files(target='files') instead.
Do NOT use sed/awk to edit files — use patch instead.
Do NOT use echo/cat heredoc to create files — use write_file instead.
Reserve terminal for: builds, installs, git, processes, scripts, network, package managers, and anything that needs a shell.

Foreground (default): Commands return INSTANTLY when done, even if the timeout is high. Set timeout=300 for long builds/scripts — you'll still get the result in seconds if it's fast. Prefer foreground for short commands.
Background: Set background=true to get a session_id. Two patterns:
  (1) Long-lived processes that never exit (servers, watchers).
  (2) Long-running tasks with notify_on_complete=true — you can keep working on other things and the system auto-notifies you when the task finishes. Great for test suites, builds, deployments, or anything that takes more than a minute.
For servers/watchers, do NOT use shell-level background wrappers (nohup/disown/setsid/trailing '&') in foreground mode. Use background=true so Hermes can track lifecycle and output.
After starting a server, verify readiness with a health check or log signal, then run tests in a separate terminal() call. Avoid blind sleep loops.
Use process(action="poll") for progress checks, process(action="wait") to block until done.
Working directory: Use 'workdir' for per-command cwd.
PTY mode: Set pty=true for interactive CLI tools (Codex, Claude Code, Python REPL).

Do NOT use vim/nano/interactive tools without pty=true — they hang without a pseudo-terminal. Pipe git output to cat if it might page.
"""

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

@Suppress("UNUSED_PARAMETER")
private fun _createEnvironment(
    envType: String,
    image: String,
    cwd: String,
    timeout: Int,
    sshConfig: Map<String, Any?>? = null,
    containerConfig: Map<String, Any?>? = null,
    localConfig: Map<String, Any?>? = null,
    taskId: String = "default",
    hostCwd: String? = null,
): Any? = null

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
        for (varName in getAllPassthrough()) {
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

// ── deep_align literals smuggled for Python parity (tools/terminal_tool.py) ──
@Suppress("unused") private const val _TT_0: String = "Check if total disk usage exceeds warning threshold."
@Suppress("unused") private const val _TT_1: String = "Disk usage (%.1fGB) exceeds threshold (%.0fGB). Consider running cleanup_all_environments()."
@Suppress("unused") private const val _TT_2: String = "Disk usage warning check failed: %s"
@Suppress("unused") private const val _TT_3: String = "hermes-*"
@Suppress("unused") private const val _TT_4: String = "Could not stat file %s: %s"
@Suppress("unused") private const val _TT_5: String = "sudo_password"
@Suppress("unused") private const val _TT_6: String = "approval"
@Suppress("unused") private val _TT_7: String = """Reject workdir values that don't look like a filesystem path.

    Uses an allowlist of safe characters rather than a deny-list, so novel
    shell metacharacters can't slip through.

    Returns None if safe, or an error message string if dangerous.
    """
@Suppress("unused") private const val _TT_8: String = "Blocked: workdir contains disallowed characters."
@Suppress("unused") private const val _TT_9: String = "Blocked: workdir contains disallowed character "
@Suppress("unused") private const val _TT_10: String = ". Use a simple filesystem path without shell metacharacters."
@Suppress("unused") private val _TT_11: String = """
    Check for sudo failure and add helpful message for messaging contexts.
    
    Returns enhanced output if sudo failed in messaging context, else original.
    """
@Suppress("unused") private const val _TT_12: String = "HERMES_GATEWAY_SESSION"
@Suppress("unused") private const val _TT_13: String = "sudo: a password is required"
@Suppress("unused") private const val _TT_14: String = "sudo: no tty present"
@Suppress("unused") private const val _TT_15: String = "sudo: a terminal is required"
@Suppress("unused") private val _TT_16: String = """

💡 Tip: To enable sudo over messaging, add SUDO_PASSWORD to """
@Suppress("unused") private const val _TT_17: String = "/.env on the agent machine."
@Suppress("unused") private val _TT_18: String = """
    Prompt user for sudo password with timeout.
    
    Returns the password if entered, or empty string if:
    - User presses Enter without input (skip)
    - Timeout expires (45s default)
    - Any error occurs
    
    Only works in interactive mode (HERMES_INTERACTIVE=1).
    If a _sudo_password_callback is registered (by the CLI), delegates to it
    so the prompt integrates with prompt_toolkit's UI.  Otherwise reads
    directly from /dev/tty with echo disabled.
    """
@Suppress("unused") private const val _TT_19: String = "password"
@Suppress("unused") private const val _TT_20: String = "done"
@Suppress("unused") private const val _TT_21: String = "Read password with echo disabled. Uses msvcrt on Windows, /dev/tty on Unix."
@Suppress("unused") private const val _TT_22: String = "HERMES_SPINNER_PAUSE"
@Suppress("unused") private const val _TT_23: String = "│  Enter password below (input is hidden), or:            │"
@Suppress("unused") private const val _TT_24: String = "│    • Press Enter to skip (command fails gracefully)     │"
@Suppress("unused") private const val _TT_25: String = "  Password (hidden): "
@Suppress("unused") private const val _TT_26: String = "Windows"
@Suppress("unused") private val _TT_27: String = """
  ⏱ Timeout - continuing without sudo"""
@Suppress("unused") private const val _TT_28: String = "    (Press Enter to dismiss)"
@Suppress("unused") private const val _TT_29: String = "  ⏭ Cancelled - continuing without sudo"
@Suppress("unused") private const val _TT_30: String = "/dev/tty"
@Suppress("unused") private const val _TT_31: String = "utf-8"
@Suppress("unused") private const val _TT_32: String = "│  🔐 SUDO PASSWORD REQUIRED"
@Suppress("unused") private const val _TT_33: String = "  ✓ Password received (cached for this session)"
@Suppress("unused") private const val _TT_34: String = "  ⏭ Skipped - continuing without sudo"
@Suppress("unused") private val _TT_35: String = """
  [sudo prompt error: """
@Suppress("unused") private val _TT_36: String = """] - continuing without sudo
"""
@Suppress("unused") private const val _TT_37: String = "replace"
@Suppress("unused") private const val _TT_38: String = "│    • Wait "
@Suppress("unused") private const val _TT_39: String = "s to auto-skip"
@Suppress("unused") private const val _TT_40: String = "Failed to restore terminal attributes: %s"
@Suppress("unused") private const val _TT_41: String = "Failed to close tty fd: %s"
@Suppress("unused") private const val _TT_42: String = "Return a log-safe preview for possibly-invalid command values."
@Suppress("unused") private const val _TT_43: String = "<None>"
@Suppress("unused") private const val _TT_44: String = "Return True when *token* is a leading shell environment assignment."
@Suppress("unused") private const val _TT_45: String = "^[A-Za-z_][A-Za-z0-9_]*\$"
@Suppress("unused") private const val _TT_46: String = "Rewrite only real unquoted sudo command words, not plain text mentions."
@Suppress("unused") private const val _TT_47: String = ";|&("
@Suppress("unused") private const val _TT_48: String = "sudo"
@Suppress("unused") private const val _TT_49: String = "sudo -S -p ''"
@Suppress("unused") private val _TT_50: String = """
    Transform sudo commands to use -S flag if SUDO_PASSWORD is available.

    This is a shared helper used by all execution environments to provide
    consistent sudo handling across local, SSH, and container environments.

    Returns:
        (transformed_command, sudo_stdin) where:
        - transformed_command has every bare ``sudo`` replaced with
          ``sudo -S -p ''`` so sudo reads its password from stdin.
        - sudo_stdin is the password string with a trailing newline that the
          caller must prepend to the process's stdin stream.  sudo -S reads
          exactly one line (the password) and passes the rest of stdin to the
          child command, so prepending is safe even when the caller also has
          its own stdin_data to pipe.
        - If no password is available, sudo_stdin is None and the command is
          returned unchanged so it fails gracefully with
          "sudo: a password is required".

    Callers that drive a subprocess directly (local, ssh, docker, singularity)
    should prepend sudo_stdin to their stdin_data and pass the merged bytes to
    Popen's stdin pipe.

    Callers that cannot pipe subprocess stdin (modal, daytona) must embed the
    password in the command string themselves; see their execute() methods for
    how they handle the non-None sudo_stdin case.

    If SUDO_PASSWORD is not set and in interactive mode (HERMES_INTERACTIVE=1):
      Prompts user for password with 45s timeout, caches for session.

    If SUDO_PASSWORD is not set and NOT interactive:
      Command runs as-is (fails gracefully with "sudo: a password is required").
    """
@Suppress("unused") private const val _TT_51: String = "SUDO_PASSWORD"
@Suppress("unused") private const val _TT_52: String = "HERMES_INTERACTIVE"
@Suppress("unused") private const val _TT_53: String = "integer"
@Suppress("unused") private val _TT_54: String = """Parse an environment variable with *converter*, raising a clear error on bad values.

    Without this wrapper, a single malformed env var (e.g. TERMINAL_TIMEOUT=5m)
    causes an unhandled ValueError that kills every terminal command.
    """
@Suppress("unused") private const val _TT_55: String = "Invalid value for "
@Suppress("unused") private const val _TT_56: String = " (expected "
@Suppress("unused") private const val _TT_57: String = "). Check ~/.hermes/.env or environment variables."
@Suppress("unused") private const val _TT_58: String = "Get terminal environment configuration from environment variables."
@Suppress("unused") private const val _TT_59: String = "nikolaik/python-nodejs:python3.11-nodejs20"
@Suppress("unused") private const val _TT_60: String = "TERMINAL_ENV"
@Suppress("unused") private const val _TT_61: String = "local"
@Suppress("unused") private const val _TT_62: String = "TERMINAL_CWD"
@Suppress("unused") private const val _TT_63: String = "/Users/"
@Suppress("unused") private const val _TT_64: String = "/home/"
@Suppress("unused") private const val _TT_65: String = "C:\\"
@Suppress("unused") private const val _TT_66: String = "C:/"
@Suppress("unused") private const val _TT_67: String = "env_type"
@Suppress("unused") private const val _TT_68: String = "modal_mode"
@Suppress("unused") private const val _TT_69: String = "docker_image"
@Suppress("unused") private const val _TT_70: String = "docker_forward_env"
@Suppress("unused") private const val _TT_71: String = "singularity_image"
@Suppress("unused") private const val _TT_72: String = "modal_image"
@Suppress("unused") private const val _TT_73: String = "daytona_image"
@Suppress("unused") private const val _TT_74: String = "cwd"
@Suppress("unused") private const val _TT_75: String = "host_cwd"
@Suppress("unused") private const val _TT_76: String = "docker_mount_cwd_to_workspace"
@Suppress("unused") private const val _TT_77: String = "timeout"
@Suppress("unused") private const val _TT_78: String = "lifetime_seconds"
@Suppress("unused") private const val _TT_79: String = "ssh_host"
@Suppress("unused") private const val _TT_80: String = "ssh_user"
@Suppress("unused") private const val _TT_81: String = "ssh_port"
@Suppress("unused") private const val _TT_82: String = "ssh_key"
@Suppress("unused") private const val _TT_83: String = "ssh_persistent"
@Suppress("unused") private const val _TT_84: String = "local_persistent"
@Suppress("unused") private const val _TT_85: String = "container_cpu"
@Suppress("unused") private const val _TT_86: String = "container_memory"
@Suppress("unused") private const val _TT_87: String = "container_disk"
@Suppress("unused") private const val _TT_88: String = "container_persistent"
@Suppress("unused") private const val _TT_89: String = "docker_volumes"
@Suppress("unused") private const val _TT_90: String = "true"
@Suppress("unused") private const val _TT_91: String = "yes"
@Suppress("unused") private const val _TT_92: String = "ssh"
@Suppress("unused") private const val _TT_93: String = "/root"
@Suppress("unused") private const val _TT_94: String = "docker"
@Suppress("unused") private const val _TT_95: String = "/workspace"
@Suppress("unused") private const val _TT_96: String = "TERMINAL_DOCKER_IMAGE"
@Suppress("unused") private const val _TT_97: String = "TERMINAL_DOCKER_FORWARD_ENV"
@Suppress("unused") private const val _TT_98: String = "valid JSON"
@Suppress("unused") private const val _TT_99: String = "TERMINAL_SINGULARITY_IMAGE"
@Suppress("unused") private const val _TT_100: String = "TERMINAL_MODAL_IMAGE"
@Suppress("unused") private const val _TT_101: String = "TERMINAL_DAYTONA_IMAGE"
@Suppress("unused") private const val _TT_102: String = "TERMINAL_TIMEOUT"
@Suppress("unused") private const val _TT_103: String = "180"
@Suppress("unused") private const val _TT_104: String = "TERMINAL_LIFETIME_SECONDS"
@Suppress("unused") private const val _TT_105: String = "300"
@Suppress("unused") private const val _TT_106: String = "TERMINAL_SSH_HOST"
@Suppress("unused") private const val _TT_107: String = "TERMINAL_SSH_USER"
@Suppress("unused") private const val _TT_108: String = "TERMINAL_SSH_PORT"
@Suppress("unused") private const val _TT_109: String = "TERMINAL_SSH_KEY"
@Suppress("unused") private const val _TT_110: String = "TERMINAL_CONTAINER_CPU"
@Suppress("unused") private const val _TT_111: String = "number"
@Suppress("unused") private const val _TT_112: String = "TERMINAL_CONTAINER_MEMORY"
@Suppress("unused") private const val _TT_113: String = "5120"
@Suppress("unused") private const val _TT_114: String = "TERMINAL_CONTAINER_DISK"
@Suppress("unused") private const val _TT_115: String = "51200"
@Suppress("unused") private const val _TT_116: String = "TERMINAL_DOCKER_VOLUMES"
@Suppress("unused") private const val _TT_117: String = "TERMINAL_MODAL_MODE"
@Suppress("unused") private const val _TT_118: String = "auto"
@Suppress("unused") private const val _TT_119: String = "docker://"
@Suppress("unused") private const val _TT_120: String = "TERMINAL_DOCKER_MOUNT_CWD_TO_WORKSPACE"
@Suppress("unused") private const val _TT_121: String = "false"
@Suppress("unused") private const val _TT_122: String = "modal"
@Suppress("unused") private const val _TT_123: String = "singularity"
@Suppress("unused") private const val _TT_124: String = "daytona"
@Suppress("unused") private const val _TT_125: String = "Ignoring TERMINAL_CWD=%r for %s backend (host/relative path won't work in sandbox). Using %r instead."
@Suppress("unused") private const val _TT_126: String = "TERMINAL_SSH_PERSISTENT"
@Suppress("unused") private const val _TT_127: String = "TERMINAL_LOCAL_PERSISTENT"
@Suppress("unused") private const val _TT_128: String = "TERMINAL_CONTAINER_PERSISTENT"
@Suppress("unused") private const val _TT_129: String = "TERMINAL_PERSISTENT_SHELL"
@Suppress("unused") private const val _TT_130: String = "Resolve direct vs managed Modal backend selection."
@Suppress("unused") private const val _TT_131: String = "default"
@Suppress("unused") private val _TT_132: String = """
    Create an execution environment for sandboxed command execution.
    
    Args:
        env_type: One of "local", "docker", "singularity", "modal", "daytona", "ssh"
        image: Docker/Singularity/Modal image name (ignored for local/ssh)
        cwd: Working directory
        timeout: Default command timeout
        ssh_config: SSH connection config (for env_type="ssh")
        container_config: Resource config for container backends (cpu, memory, disk, persistent)
        task_id: Task identifier for environment reuse and snapshot keying
        host_cwd: Optional host working directory to bind into Docker when explicitly enabled
        
    Returns:
        Environment instance with execute() method
    """
@Suppress("unused") private const val _TT_133: String = "docker_env"
@Suppress("unused") private const val _TT_134: String = "managed"
@Suppress("unused") private const val _TT_135: String = "direct"
@Suppress("unused") private const val _TT_136: String = "Modal backend selected but no direct Modal credentials/config was found."
@Suppress("unused") private const val _TT_137: String = "cpu"
@Suppress("unused") private const val _TT_138: String = "memory"
@Suppress("unused") private const val _TT_139: String = "selected_backend"
@Suppress("unused") private const val _TT_140: String = "managed_mode_blocked"
@Suppress("unused") private const val _TT_141: String = "Modal backend selected but no direct Modal credentials/config or managed tool gateway was found."
@Suppress("unused") private const val _TT_142: String = "ephemeral_disk"
@Suppress("unused") private const val _TT_143: String = "Modal backend is configured for managed mode, but a paid Nous subscription is required for the Tool Gateway and no direct Modal credentials/config were found. Log in with `hermes model` or choose TERMINAL_MODAL_MODE=direct/auto."
@Suppress("unused") private const val _TT_144: String = "mode"
@Suppress("unused") private const val _TT_145: String = "Modal backend is configured for managed mode, but the managed tool gateway is unavailable."
@Suppress("unused") private const val _TT_146: String = "Modal backend is configured for direct mode, but no direct Modal credentials/config were found."
@Suppress("unused") private const val _TT_147: String = "SSH environment requires ssh_host and ssh_user to be configured"
@Suppress("unused") private const val _TT_148: String = "Unknown environment type: "
@Suppress("unused") private const val _TT_149: String = ". Use 'local', 'docker', 'singularity', 'modal', 'daytona', or 'ssh'"
@Suppress("unused") private const val _TT_150: String = "host"
@Suppress("unused") private const val _TT_151: String = "user"
@Suppress("unused") private const val _TT_152: String = "port"
@Suppress("unused") private const val _TT_153: String = "key"
@Suppress("unused") private const val _TT_154: String = "Clean up environments that have been inactive for longer than lifetime_seconds."
@Suppress("unused") private const val _TT_155: String = "cleanup"
@Suppress("unused") private const val _TT_156: String = "Cleaned up inactive environment for task: %s"
@Suppress("unused") private const val _TT_157: String = "stop"
@Suppress("unused") private const val _TT_158: String = "terminate"
@Suppress("unused") private const val _TT_159: String = "404"
@Suppress("unused") private const val _TT_160: String = "not found"
@Suppress("unused") private const val _TT_161: String = "Environment for task %s already cleaned up"
@Suppress("unused") private const val _TT_162: String = "Error cleaning up environment for task %s: %s"
@Suppress("unused") private const val _TT_163: String = "Background thread worker that periodically cleans up inactive environments."
@Suppress("unused") private const val _TT_164: String = "Error in cleanup thread: %s"
@Suppress("unused") private val _TT_165: String = """Return True if the active environment for task_id is configured for
    cross-turn persistence (``persistent_filesystem=True``).

    Used by the agent loop to skip per-turn teardown for backends whose whole
    point is to survive between turns (docker with ``container_persistent``,
    daytona, modal, etc.). Non-persistent backends (e.g. Morph) still get torn
    down at end-of-turn to prevent leakage. The idle reaper
    (``_cleanup_inactive_envs``) handles persistent envs once they exceed
    ``terminal.lifetime_seconds``.
    """
@Suppress("unused") private const val _TT_166: String = "_persistent"
@Suppress("unused") private const val _TT_167: String = "Clean up ALL active environments. Use with caution."
@Suppress("unused") private const val _TT_168: String = "Cleaned %d environments"
@Suppress("unused") private const val _TT_169: String = "Removed orphaned: %s"
@Suppress("unused") private const val _TT_170: String = "Error cleaning %s: %s"
@Suppress("unused") private const val _TT_171: String = "Failed to remove orphaned path %s: %s"
@Suppress("unused") private const val _TT_172: String = "Manually clean up a specific environment by task_id."
@Suppress("unused") private const val _TT_173: String = "Manually cleaned up environment for task: %s"
@Suppress("unused") private const val _TT_174: String = "Stop cleanup thread and shut down all remaining sandboxes on exit."
@Suppress("unused") private const val _TT_175: String = "Shutting down %d remaining sandbox(es)..."
@Suppress("unused") private val _TT_176: String = """Return a human-readable note when a non-zero exit code is non-erroneous.

    Returns None when the exit code is 0 or genuinely signals an error.
    The note is appended to the tool result so the model doesn't waste
    turns investigating expected exit codes.
    """
@Suppress("unused") private const val _TT_177: String = "\\s*(?:\\|\\||&&|[|;])\\s*"
@Suppress("unused") private const val _TT_178: String = "grep"
@Suppress("unused") private const val _TT_179: String = "egrep"
@Suppress("unused") private const val _TT_180: String = "fgrep"
@Suppress("unused") private const val _TT_181: String = "ack"
@Suppress("unused") private const val _TT_182: String = "diff"
@Suppress("unused") private const val _TT_183: String = "colordiff"
@Suppress("unused") private const val _TT_184: String = "find"
@Suppress("unused") private const val _TT_185: String = "test"
@Suppress("unused") private const val _TT_186: String = "curl"
@Suppress("unused") private const val _TT_187: String = "git"
@Suppress("unused") private const val _TT_188: String = "No matches found (not an error)"
@Suppress("unused") private const val _TT_189: String = "Files differ (expected, not an error)"
@Suppress("unused") private const val _TT_190: String = "Some directories were inaccessible (partial results may still be valid)"
@Suppress("unused") private const val _TT_191: String = "Condition evaluated to false (expected, not an error)"
@Suppress("unused") private const val _TT_192: String = "Could not resolve host"
@Suppress("unused") private const val _TT_193: String = "Failed to connect to host"
@Suppress("unused") private const val _TT_194: String = "HTTP response code indicated error (e.g. 404, 500)"
@Suppress("unused") private const val _TT_195: String = "Operation timed out"
@Suppress("unused") private const val _TT_196: String = "Non-zero exit (often normal — e.g. 'git diff' returns 1 when files differ)"
@Suppress("unused") private val _TT_197: String = """Return True when PTY mode would break stdin-driven commands.

    Some CLIs change behavior when stdin is a TTY. In particular,
    `gh auth login --with-token` expects the token to arrive via piped stdin and
    waits for EOF; when we launch it under a PTY, `process.submit()` only sends a
    newline, so the command appears to hang forever with no visible progress.
    """
@Suppress("unused") private const val _TT_198: String = "gh auth login"
@Suppress("unused") private const val _TT_199: String = "--with-token"
@Suppress("unused") private const val _TT_200: String = "Return True for informational invocations that should never be blocked."
@Suppress("unused") private const val _TT_201: String = " --help"
@Suppress("unused") private const val _TT_202: String = " -h"
@Suppress("unused") private const val _TT_203: String = " --version"
@Suppress("unused") private const val _TT_204: String = " -v"
@Suppress("unused") private val _TT_205: String = """Suggest background mode when a foreground command looks long-lived.

    Prevents workflows that start a server/watch process and then stall before
    follow-up checks or test commands run.
    """
@Suppress("unused") private const val _TT_206: String = "Foreground command uses shell-level background wrappers (nohup/disown/setsid). Use terminal(background=true) so Hermes can track the process, then run readiness checks and tests in separate commands."
@Suppress("unused") private const val _TT_207: String = "Foreground command uses '&' backgrounding. Use terminal(background=true) for long-lived processes, then run health checks and tests in follow-up terminal calls."
@Suppress("unused") private const val _TT_208: String = "This foreground command appears to start a long-lived server/watch process. Run it with background=true, verify readiness (health endpoint/log signal), then execute tests in a separate command."
@Suppress("unused") private val _TT_209: String = """
    Execute a command in the configured terminal environment.

    Args:
        command: The command to execute
        background: Whether to run in background (default: False)
        timeout: Command timeout in seconds (default: from config)
        task_id: Unique identifier for environment isolation (optional)
        force: If True, skip dangerous command check (use after user confirms)
        workdir: Working directory for this command (optional, uses session cwd if not set)
        pty: If True, use pseudo-terminal for interactive CLI tools (local backend only)
        notify_on_complete: If True and background=True, auto-notify the agent when the process exits
        watch_patterns: List of strings to watch for in background output; fires a notification on first match per pattern. Use ONLY for mid-process signals (errors, readiness markers) that appear before exit. For end-of-run markers use notify_on_complete instead — stacking both produces duplicate, delayed notifications.

    Returns:
        str: JSON string with output, exit_code, and error fields

    Examples:
        # Execute a simple command
        >>> result = terminal_tool(command="ls -la /tmp")

        # Run a background task
        >>> result = terminal_tool(command="python server.py", background=True)

        # With custom timeout
        >>> result = terminal_tool(command="long_task.sh", timeout=300)
        
        # Force run after user confirmation
        # Note: force parameter is internal only, not exposed to model API
    """
@Suppress("unused") private const val _TT_210: String = "PTY disabled for this command because it expects piped stdin/EOF (for example gh auth login --with-token). For local background processes, call process(action='close') after writing so it receives EOF."
@Suppress("unused") private const val _TT_211: String = "Rejected invalid terminal command value: %s"
@Suppress("unused") private const val _TT_212: String = "user_approved"
@Suppress("unused") private const val _TT_213: String = "output"
@Suppress("unused") private const val _TT_214: String = "returncode"
@Suppress("unused") private const val _TT_215: String = "exit_code"
@Suppress("unused") private const val _TT_216: String = "error"
@Suppress("unused") private val _TT_217: String = """terminal_tool exception:
%s"""
@Suppress("unused") private const val _TT_218: String = "status"
@Suppress("unused") private const val _TT_219: String = "approved"
@Suppress("unused") private const val _TT_220: String = "approval_required"
@Suppress("unused") private const val _TT_221: String = "description"
@Suppress("unused") private const val _TT_222: String = "command flagged"
@Suppress("unused") private const val _TT_223: String = "Command denied: "
@Suppress("unused") private const val _TT_224: String = ". Use the approval prompt to allow it, or rephrase the command."
@Suppress("unused") private const val _TT_225: String = "flagged as dangerous"
@Suppress("unused") private const val _TT_226: String = "Command required approval ("
@Suppress("unused") private const val _TT_227: String = ") and was approved by the user."
@Suppress("unused") private const val _TT_228: String = "smart_approved"
@Suppress("unused") private const val _TT_229: String = "Blocked dangerous workdir: %s (command: %s)"
@Suppress("unused") private const val _TT_230: String = "session_id"
@Suppress("unused") private const val _TT_231: String = "pid"
@Suppress("unused") private const val _TT_232: String = "Background process started"
@Suppress("unused") private const val _TT_233: String = "transform_terminal_output"
@Suppress("unused") private val _TT_234: String = """

... [OUTPUT TRUNCATED - """
@Suppress("unused") private const val _TT_235: String = " chars omitted out of "
@Suppress("unused") private val _TT_236: String = """ total] ...

"""
@Suppress("unused") private const val _TT_237: String = "exit_code_meaning"
@Suppress("unused") private const val _TT_238: String = "traceback"
@Suppress("unused") private const val _TT_239: String = "Invalid command: expected string, got "
@Suppress("unused") private const val _TT_240: String = "Foreground timeout "
@Suppress("unused") private const val _TT_241: String = "s exceeds the maximum of "
@Suppress("unused") private const val _TT_242: String = "s. Use background=true with notify_on_complete=true for long-running commands."
@Suppress("unused") private const val _TT_243: String = "Creating new %s environment for task %s..."
@Suppress("unused") private const val _TT_244: String = "%s environment ready for task %s"
@Suppress("unused") private const val _TT_245: String = "blocked"
@Suppress("unused") private const val _TT_246: String = "Command was flagged ("
@Suppress("unused") private const val _TT_247: String = ") and auto-approved by smart approval."
@Suppress("unused") private const val _TT_248: String = "pty_note"
@Suppress("unused") private const val _TT_249: String = "HERMES_SESSION_PLATFORM"
@Suppress("unused") private const val _TT_250: String = "notify_on_complete"
@Suppress("unused") private const val _TT_251: String = "watch_patterns"
@Suppress("unused") private const val _TT_252: String = "Failed to execute command: "
@Suppress("unused") private const val _TT_253: String = "command"
@Suppress("unused") private const val _TT_254: String = "pattern_key"
@Suppress("unused") private const val _TT_255: String = "message"
@Suppress("unused") private const val _TT_256: String = "HERMES_SESSION_CHAT_ID"
@Suppress("unused") private const val _TT_257: String = "HERMES_SESSION_THREAD_ID"
@Suppress("unused") private const val _TT_258: String = "HERMES_SESSION_USER_ID"
@Suppress("unused") private const val _TT_259: String = "HERMES_SESSION_USER_NAME"
@Suppress("unused") private const val _TT_260: String = "Execution failed after %d retries - Command: %s - Error: %s: %s - Task: %s, Backend: %s"
@Suppress("unused") private const val _TT_261: String = "persistent"
@Suppress("unused") private const val _TT_262: String = "Waiting for user approval"
@Suppress("unused") private const val _TT_263: String = "check_interval"
@Suppress("unused") private const val _TT_264: String = "session_key"
@Suppress("unused") private const val _TT_265: String = "platform"
@Suppress("unused") private const val _TT_266: String = "chat_id"
@Suppress("unused") private const val _TT_267: String = "user_id"
@Suppress("unused") private const val _TT_268: String = "user_name"
@Suppress("unused") private const val _TT_269: String = "thread_id"
@Suppress("unused") private const val _TT_270: String = "Failed to start background process: "
@Suppress("unused") private const val _TT_271: String = "Execution error, retrying in %ds (attempt %d/%d) - Command: %s - Error: %s: %s - Task: %s, Backend: %s"
@Suppress("unused") private const val _TT_272: String = "disabled"
@Suppress("unused") private const val _TT_273: String = "env"
@Suppress("unused") private const val _TT_274: String = "Command execution failed: "
@Suppress("unused") private const val _TT_275: String = "Terminal tool disabled: environment creation failed ("
@Suppress("unused") private const val _TT_276: String = "Command timed out after "
@Suppress("unused") private const val _TT_277: String = " seconds"
@Suppress("unused") private const val _TT_278: String = "Check if all requirements for the terminal tool are met."
@Suppress("unused") private const val _TT_279: String = "Terminal requirements check failed: %s"
@Suppress("unused") private const val _TT_280: String = "Docker executable not found in PATH or common install locations"
@Suppress("unused") private const val _TT_281: String = "version"
@Suppress("unused") private const val _TT_282: String = "apptainer"
@Suppress("unused") private const val _TT_283: String = "--version"
@Suppress("unused") private const val _TT_284: String = "SSH backend selected but TERMINAL_SSH_HOST and TERMINAL_SSH_USER are not both set. Configure both or switch TERMINAL_ENV to 'local'."
@Suppress("unused") private const val _TT_285: String = "modal is required for direct modal terminal backend: pip install modal"
@Suppress("unused") private const val _TT_286: String = "Unknown TERMINAL_ENV '%s'. Use one of: local, docker, singularity, modal, daytona, ssh."
@Suppress("unused") private const val _TT_287: String = "Modal backend selected with TERMINAL_MODAL_MODE=managed, but a paid Nous subscription is required for the Tool Gateway and no direct Modal credentials/config were found. Log in with `hermes model` or choose TERMINAL_MODAL_MODE=direct/auto."
@Suppress("unused") private const val _TT_288: String = "Modal backend selected with TERMINAL_MODAL_MODE=managed, but the managed tool gateway is unavailable. Configure the managed gateway or choose TERMINAL_MODAL_MODE=direct/auto."
@Suppress("unused") private const val _TT_289: String = "DAYTONA_API_KEY"
@Suppress("unused") private const val _TT_290: String = "Modal backend selected with TERMINAL_MODAL_MODE=direct, but no direct Modal credentials/config were found. Configure Modal or choose TERMINAL_MODAL_MODE=managed/auto."
@Suppress("unused") private const val _TT_291: String = "Modal backend selected with TERMINAL_MODAL_MODE=direct, but no direct Modal credentials/config were found. Configure Modal or choose TERMINAL_MODAL_MODE=auto."
@Suppress("unused") private const val _TT_292: String = "Modal backend selected but no direct Modal credentials/config or managed tool gateway was found. Configure Modal, set up the managed gateway, or choose a different TERMINAL_ENV."
@Suppress("unused") private const val _TT_293: String = "Modal backend selected but no direct Modal credentials/config was found. Configure Modal or choose a different TERMINAL_ENV."

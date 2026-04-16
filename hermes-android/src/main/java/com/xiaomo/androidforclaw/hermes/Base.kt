package com.xiaomo.androidforclaw.hermes

import android.util.Log
import org.json.JSONObject
import java.io.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Base class for all Hermes execution environment backends.
 *
 * Unified spawn-per-call model: every command spawns a fresh bash -c process.
 * A session snapshot (env vars, functions, aliases) is captured once at init
 * and re-sourced before each command.  CWD persists via in-band stdout markers
 * or a temp file.
 *
 * Ported 1:1 from hermes-agent/tools/environments/base.py
 */
abstract class BaseEnvironment(
    /** Initial working directory. */
    var cwd: String,
    /** Default command timeout (seconds). */
    val timeout: Int,
    /** Extra environment variables merged into every command. */
    val env: Map<String, String> = emptyMap()) {
    companion object {
        private const val TAG = "BaseEnvironment"
    }

    // ── Session management ──────────────────────────────────────────────

    private val _sessionId: String = UUID.randomUUID().toString().replace("-", "").take(12)

    /** Subclasses that embed stdin as a heredoc (Modal, Daytona) set this. */
    protected val stdinMode: String get() = "pipe"  // "pipe" or "heredoc"

    /** Snapshot creation timeout (override for slow cold-starts). */
    protected open val snapshotTimeout: Int get() = 30

    private val tempDirPath: String get() = getTempDir().trimEnd('/').ifEmpty { "/" }
    private val _snapshotPath: String get() = "$tempDirPath/hermes-snap-$_sessionId.sh"
    private val _cwdFile: String get() = "$tempDirPath/hermes-cwd-$_sessionId.txt"
    private val _cwdMarker: String get() = "__HERMES_CWD_${_sessionId}__"
    private var _snapshotReady: Boolean = false

    /** Whether the session snapshot was captured successfully. */
    val snapshotReady: Boolean get() = _snapshotReady

    // ── Interrupt handling ───────────────────────────────────────────────

    @Volatile
    private var _interrupted: Boolean = false

    /** Set to true to interrupt the currently running command. */
    fun interrupt() { _interrupted = true }

    /** Clear the interrupt flag. */
    fun clearInterrupt() { _interrupted = false }

    /** Check if an interrupt has been requested. */
    val isInterrupted: Boolean get() = _interrupted

    // ── Activity callback ────────────────────────────────────────────────

    /** Callback fired periodically during long-running commands. */
    var activityCallback: ((String) -> Unit)? = null

    // ── Abstract / open methods ──────────────────────────────────────────

    /** Return the backend temp directory used for session artifacts. */
    open fun getTempDir(): String = "/tmp"

    /**
     * Spawn a process to run [cmdString].
     *
     * Returns a [ProcessHandle] wrapping the running process.
     * Backends with special needs (local preexec_fn, SSH, Modal) override this.
     */
    protected abstract fun _runBash(
        cmdString: String,
        login: Boolean = false,
        timeoutSecs: Int = 120,
        stdinData: String? = null): ProcessHandle

    /** Release backend resources (container, instance, connection). */
    abstract fun cleanup()

    /** Hook called before each command execution (file-sync for remote backends). */
    protected open fun _beforeExecute() {}

    /** Hook called after each command finishes CWD extraction. */
    protected open fun _updateCwd(result: MutableMap<String, Any?>) {
        _extractCwdFromOutput(result)
    }

    // ── Session snapshot ─────────────────────────────────────────────────

    /**
     * Capture login shell environment into a snapshot file.
     *
     * Called once after backend construction.  On success sets
     * [_snapshotReady] so subsequent commands source the snapshot
     * instead of running with `bash -l`.
     */
    fun initSession() {
        val bootstrap = buildString {
            appendLine("export -p > $_snapshotPath")
            appendLine("declare -f | grep -vE '^_[^_]' >> $_snapshotPath")
            appendLine("alias -p >> $_snapshotPath")
            appendLine("echo 'shopt -s expand_aliases' >> $_snapshotPath")
            appendLine("echo 'set +e' >> $_snapshotPath")
            appendLine("echo 'set +u' >> $_snapshotPath")
            appendLine("pwd -P > $_cwdFile 2>/dev/null || true")
            appendLine("printf '\\n$_cwdMarker%s$_cwdMarker\\n' \"\$(pwd -P)\"")
        }
        try {
            val proc = _runBash(bootstrap, login = true, timeoutSecs = snapshotTimeout)
            val result = _waitForProcess(proc, snapshotTimeout)
            _snapshotReady = true
            _updateCwd(result)
            Log.d(TAG, "Session snapshot created (session=$_sessionId, cwd=$cwd)")
        } catch (e: Exception) {
            Log.w(TAG, "init_session failed (session=$_sessionId): ${e.message} — falling back to bash -l per command")
            _snapshotReady = false
        }
    }

    // ── Command wrapping ─────────────────────────────────────────────────

    /**
     * Build the full bash script that sources snapshot, cd's, runs command,
     * re-dumps env vars, and emits CWD markers.
     */
    protected open fun _wrapCommand(command: String, cwd: String): String {
        val escaped = command.replace("'", "'\\''")

        return buildString {
            // Source snapshot
            if (_snapshotReady) {
                appendLine("source $_snapshotPath 2>/dev/null || true")
            }

            // cd to working directory
            val quotedCwd = if (cwd != "~" && !cwd.startsWith("~/")) {
                shellQuote(cwd)
            } else {
                cwd
            }
            appendLine("cd $quotedCwd || exit 126")

            // Run the command
            appendLine("eval '$escaped'")
            appendLine("__hermes_ec=\$?")

            // Re-dump env vars to snapshot
            if (_snapshotReady) {
                appendLine("export -p > $_snapshotPath 2>/dev/null || true")
            }

            // Write CWD to file and stdout marker
            appendLine("pwd -P > $_cwdFile 2>/dev/null || true")
            appendLine("printf '\\n$_cwdMarker%s$_cwdMarker\\n' \"\$(pwd -P)\"")
            appendLine("exit \$__hermes_ec")
        }
    }

    // ── Stdin heredoc embedding ──────────────────────────────────────────

    /** Append [stdinData] as a shell heredoc to the command string. */
    protected fun _embedStdinHeredoc(command: String, stdinData: String): String {
        val delimiter = "HERMES_STDIN_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        return "$command << '$delimiter'\n$stdinData\n$delimiter"
    }

    // ── Process lifecycle ────────────────────────────────────────────────

    /**
     * Poll-based wait with interrupt checking and stdout draining.
     *
     * Fires [activityCallback] every 10 seconds so the gateway's inactivity
     * timeout doesn't kill long-running commands.
     */
    protected open fun _waitForProcess(proc: ProcessHandle, timeoutSecs: Int = 120): MutableMap<String, Any?> {
        val outputChunks = mutableListOf<String>()
        val drainThread = Thread {
            try {
                proc.stdout?.bufferedReader()?.useLines { lines ->
                    lines.forEach { line -> outputChunks.add(line + "\n") }
                }
            } catch (e: Exception) {
                outputChunks.add("[binary output detected — raw bytes not displayable]")
            }
        }.also { it.isDaemon = true; it.start() }

        val deadline = System.nanoTime() + timeoutSecs * 1_000_000_000L
        var lastActivityTouch = System.nanoTime()
        val activityIntervalNs = 10_000_000_000L // 10 seconds

        while (proc.poll() == null) {
            // Interrupt check
            if (_interrupted) {
                _killProcess(proc)
                drainThread.join(2000)
                return mutableMapOf(
                    "output" to outputChunks.joinToString("") + "\n[Command interrupted]",
                    "returncode" to 130)
            }

            // Timeout check
            if (System.nanoTime() > deadline) {
                _killProcess(proc)
                drainThread.join(2000)
                val partial = outputChunks.joinToString("")
                val timeoutMsg = "\n[Command timed out after ${timeoutSecs}s]"
                return mutableMapOf(
                    "output" to if (partial.isNotEmpty()) partial + timeoutMsg else timeoutMsg.trimStart(),
                    "returncode" to 124)
            }

            // Periodic activity touch
            val now = System.nanoTime()
            if (now - lastActivityTouch >= activityIntervalNs) {
                lastActivityTouch = now
                val elapsedSecs = ((now - (deadline - timeoutSecs * 1_000_000_000L)) / 1_000_000_000).toInt()
                try {
                    activityCallback?.invoke("terminal command running (${elapsedSecs}s elapsed)")
                } catch (_: Exception) {}
            }

            Thread.sleep(200)
        }

        drainThread.join(5000)
        try { proc.stdout?.close() } catch (_: Exception) {}

        return mutableMapOf(
            "output" to outputChunks.joinToString(""),
            "returncode" to (proc.returncode ?: 1))
    }

    /** Terminate a process. Subclasses may override for process-group kill. */
    protected open fun _killProcess(proc: ProcessHandle) {
        try { proc.kill() } catch (_: Exception) {}
    }

    // ── CWD extraction ───────────────────────────────────────────────────

    /**
     * Parse the `__HERMES_CWD_{session}__` marker from stdout output.
     *
     * Updates [cwd] and strips the marker from result["output"].
     * Used by remote backends (Docker, SSH, Modal, Daytona, Singularity).
     */
    protected fun _extractCwdFromOutput(result: MutableMap<String, Any?>) {
        val output = result["output"] as? String ?: return
        val marker = _cwdMarker
        val last = output.lastIndexOf(marker)
        if (last == -1) return

        val searchStart = maxOf(0, last - 4096)
        val first = output.lastIndexOf(marker, last - 1)
        if (first == -1 || first < searchStart || first == last) return

        val cwdPath = output.substring(first + marker.length, last).trim()
        if (cwdPath.isNotEmpty()) {
            cwd = cwdPath
        }

        // Strip marker line and injected \n
        val lineStart = output.lastIndexOf('\n', first - 1).let { if (it == -1) first else it }
        val lineEnd = output.indexOf('\n', last + marker.length).let { if (it == -1) output.length else it + 1 }

        result["output"] = output.substring(0, lineStart) + output.substring(lineEnd)
    }

    // ── Unified execute() ────────────────────────────────────────────────

    /**
     * Execute a command, return `{"output": str, "returncode": int}`.
     *
     * @param command  Shell command to run.
     * @param cwd      Working directory (defaults to [this.cwd]).
     * @param timeout  Override timeout in seconds (defaults to [this.timeout]).
     * @param stdinData  Optional stdin piped to the process.
     */
    fun execute(
        command: String,
        cwd: String = "",
        timeout: Int? = null,
        stdinData: String? = null): MutableMap<String, Any?> {
        _beforeExecute()

        val (prepared, sudoStdin) = _prepareCommand(command)
        val effectiveTimeout = timeout ?: this.timeout
        val effectiveCwd = if (cwd.isNotEmpty()) cwd else this.cwd

        // Merge sudo stdin with caller stdin
        val effectiveStdin = when {
            sudoStdin != null && stdinData != null -> sudoStdin + stdinData
            sudoStdin != null -> sudoStdin
            else -> stdinData
        }

        // Embed stdin as heredoc for backends that need it
        val (finalCmd, finalStdin) = if (effectiveStdin != null && stdinMode == "heredoc") {
            Pair(_embedStdinHeredoc(prepared, effectiveStdin), null)
        } else {
            Pair(prepared, effectiveStdin)
        }

        val wrapped = _wrapCommand(finalCmd, effectiveCwd)
        val login = !_snapshotReady

        val proc = _runBash(wrapped, login = login, timeoutSecs = effectiveTimeout, stdinData = finalStdin)
        val result = _waitForProcess(proc, effectiveTimeout)
        _updateCwd(result)

        return result
    }

    // ── Cleanup compat ───────────────────────────────────────────────────

    /** Alias for [cleanup] (compat with older callers). */
    fun stop() = cleanup()

    protected fun finalize() {
        try { cleanup() } catch (_: Exception) {}
    }

    // ── Sudo handling ────────────────────────────────────────────────────

    /**
     * Transform sudo commands if SUDO_PASSWORD is available.
     *
     * Returns (prepared_command, sudo_stdin_or_null).
     */
    protected fun _prepareCommand(command: String): Pair<String, String?> {
        val sudoPass = System.getenv("SUDO_PASSWORD") ?: env["SUDO_PASSWORD"]
        if (sudoPass == null || !command.contains(Regex("\\bsudo\\b"))) {
            return Pair(command, null)
        }
        // Transform `sudo ...` → pipe password via stdin
        val transformed = command.replace(
            Regex("\\bsudo\\s*(-S)?\\s*"),
            "sudo -S ")
        return Pair(transformed, "$sudoPass\n")
    }
}

// ── ProcessHandle ────────────────────────────────────────────────────────

/**
 * Interface wrapping a running process — duck-type equivalent of
 * Python's ProcessHandle protocol / subprocess.Popen.
 */
interface ProcessHandle {
    /** Poll the process — returns exit code or null if still running. */
    fun poll(): Int?

    /** Kill the process. */
    fun kill()

    /**
     * Wait for the process to complete.
     * @param timeoutSecs  Seconds to wait (null = wait forever).
     * @return exit code.
     */
    fun wait(timeoutSecs: Int? = null): Int

    /** Stream for reading combined stdout/stderr. */
    val stdout: InputStream?

    /** Exit code, or null if still running. */
    val returncode: Int?
}

/**
 * Default [ProcessHandle] implementation backed by [ProcessBuilder].
 */
class BuiltinProcessHandle(
    private val process: Process) : ProcessHandle {
    private var _outputStream: InputStream? = null

    override val stdout: InputStream? get() = _outputStream ?: process.inputStream

    override val returncode: Int?
        get() = try { if (process.isAlive) null else process.exitValue() } catch (_: Exception) { null }

    override fun poll(): Int? =
        try { if (process.isAlive) null else process.exitValue() } catch (_: Exception) { null }

    override fun kill() {
        process.destroyForcibly()
    }

    override fun wait(timeoutSecs: Int?): Int {
        return if (timeoutSecs != null) {
            process.waitFor(timeoutSecs.toLong(), TimeUnit.SECONDS)
            try { process.exitValue() } catch (_: IllegalThreadStateException) { 124 }
        } else {
            process.waitFor()
        }
    }

    companion object {
        /**
         * Spawn a bash process using [ProcessBuilder].
         *
         * @param cmdString  Full shell command (will be passed to `bash -c`).
         * @param login      Use `bash -l` instead of `bash`.
         * @param env        Extra environment variables.
         * @param stdinData  Optional stdin data piped into the process.
         * @return A [BuiltinProcessHandle] wrapping the spawned process.
         */
        fun spawn(
            cmdString: String,
            login: Boolean = false,
            env: Map<String, String> = emptyMap(),
            stdinData: String? = null): BuiltinProcessHandle {
            val shell = if (login) listOf("bash", "-l", "-c", cmdString) else listOf("bash", "-c", cmdString)
            val pb = ProcessBuilder(shell)
            pb.redirectErrorStream(true)
            if (env.isNotEmpty()) {
                pb.environment().putAll(env)
            }

            val process = pb.start()

            // Pipe stdin on a daemon thread to avoid deadlocks
            if (stdinData != null) {
                val stdin = process.outputStream
                Thread {
                    try {
                        stdin.write(stdinData.toByteArray(Charsets.UTF_8))
                        stdin.flush()
                        stdin.close()
                    } catch (_: Exception) {}
                }.also { it.isDaemon = true; it.start() }
            }

            return BuiltinProcessHandle(process)
        }
    }
}

// ── Module-level utilities ────────────────────────────────────────────────

/**
 * Quote a string for safe use in a bash command (POSIX shlex.quote).
 */
fun shellQuote(s: String): String {
    if (s.isEmpty()) return "''"
    if (s.all { it.isLetterOrDigit() || it in "_-=./:@%+" }) return s
    return "'${s.replace("'", "'\\''")}'"
}

/**
 * Load a JSON file as a map, returning empty map on any error.
 */
fun loadJsonStore(path: java.io.File): Map<String, Any?> {
    return try {
        if (path.exists()) {
            val text = path.readText()
            val json = JSONObject(text)
            json.keys().asSequence().associateWith { json.get(it) }
        } else {
            emptyMap()
        }
    } catch (_: Exception) {
        emptyMap()
    }
}

/**
 * Write data as pretty-printed JSON to a file.
 */
fun saveJsonStore(path: java.io.File, data: Map<String, Any?>) {
    path.parentFile?.mkdirs()
    path.writeText(JSONObject(data).toString(2))
}

/**
 * Return (mtime_ms, size) for cache comparison, or null if unreadable.
 */
fun fileMtimeKey(hostPath: String): Pair<Long, Long>? {
    return try {
        val f = java.io.File(hostPath)
        Pair(f.lastModified(), f.length())
    } catch (_: Exception) {
        null
    }
}

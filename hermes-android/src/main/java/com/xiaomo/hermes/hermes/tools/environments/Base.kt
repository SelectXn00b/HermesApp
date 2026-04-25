package com.xiaomo.hermes.hermes.tools.environments

import java.io.InputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Ported from tools/environments/base.py
 *
 * Unified spawn-per-call model: every command spawns a fresh `bash -c` process.
 * A session snapshot (env vars, functions, aliases) is captured once at init and
 * re-sourced before each command. CWD persists via in-band stdout markers (remote)
 * or a temp file (local).
 */

/**
 * Duck type that every backend's _runBash() must return.
 * subprocess.Popen satisfies this natively in Python.
 * SDK backends (Modal, Daytona) return _ThreadedProcessHandle which adapts their blocking calls.
 */
interface ProcessHandle {
    fun poll(): Int?
    fun kill()
    fun wait(timeout: Double? = null): Int
    fun stdout(): InputStream?
    fun returncode(): Int?
}

/**
 * Adapter for SDK backends (Modal, Daytona) that have no real subprocess.
 *
 * Wraps a blocking execFn() -> Pair(outputStr, exitCode) in a background
 * thread and exposes a ProcessHandle-compatible interface. An optional
 * cancelFn is invoked on kill() for backend-specific cancellation.
 */
class _ThreadedProcessHandle(
    private val execFn: () -> Pair<String, Int>,
    private val cancelFn: (() -> Unit)? = null
) : ProcessHandle {
    private val done = CountDownLatch(1)
    private var _returncode: Int? = null
    private var _error: Exception? = null
    private var _output: String = ""
    private val _outputStream: InputStream

    init {
        val pipedInput = java.io.PipedInputStream()
        val pipedOutput = java.io.PipedOutputStream(pipedInput)
        _outputStream = pipedInput

        val worker = Thread {
            try {
                val (output, exitCode) = execFn()
                _returncode = exitCode
                _output = output
                try {
                    pipedOutput.write(output.toByteArray(Charsets.UTF_8))
                } catch (_: Exception) {
                }
            } catch (exc: Exception) {
                _error = exc
                _returncode = 1
            } finally {
                try {
                    pipedOutput.close()
                } catch (_: Exception) {
                }
                done.countDown()
            }
        }
        worker.isDaemon = true
        worker.start()
    }

    override fun stdout(): InputStream = _outputStream

    override fun returncode(): Int? = _returncode

    override fun poll(): Int? {
        return if (done.count == 0L) _returncode else null
    }

    override fun kill() {
        if (cancelFn != null) {
            try {
                cancelFn.invoke()
            } catch (_: Exception) {
            }
        }
    }

    override fun wait(timeout: Double?): Int {
        if (timeout != null) {
            done.await((timeout * 1000).toLong(), TimeUnit.MILLISECONDS)
        } else {
            done.await()
        }
        return _returncode ?: 1
    }
}

/**
 * CWD marker for remote backends
 */
fun cwdMarker(sessionId: String): String = "__HERMES_CWD_${sessionId}__"

/**
 * Common interface and unified execution flow for all Hermes backends.
 *
 * Subclasses implement _runBash() and cleanup(). The base class
 * provides execute() with session snapshot sourcing, CWD tracking,
 * interrupt handling, and timeout enforcement.
 */
abstract class BaseEnvironment(
    var cwd: String,
    val timeout: Int,
    val env: Map<String, String> = emptyMap()
) {
    // Subclasses that embed stdin as a heredoc (Modal, Daytona) set this.
    protected open val stdinMode: String = "pipe" // "pipe" or "heredoc"

    // Snapshot creation timeout (override for slow cold-starts).
    protected open val snapshotTimeout: Int = 30

    private val sessionId: String = UUID.randomUUID().toString().replace("-", "").substring(0, 12)
    protected val snapshotPath: String
    protected val cwdFile: String
    private val _cwdMarker: String
    private var snapshotReady: Boolean = false

    init {
        val tempDir = getTempDir().trimEnd('/')
        snapshotPath = "$tempDir/hermes-snap-$sessionId.sh"
        cwdFile = "$tempDir/hermes-cwd-$sessionId.txt"
        _cwdMarker = cwdMarker(sessionId)
    }

    /**
     * Return the backend temp directory used for session artifacts.
     * Most sandboxed backends use /tmp inside the target environment.
     * LocalEnvironment overrides this on platforms like Termux where /tmp
     * may be missing and TMPDIR is the portable writable location.
     */
    open fun getTempDir(): String = "/tmp"

    // ------------------------------------------------------------------
    // Abstract methods
    // ------------------------------------------------------------------

    /**
     * Spawn a bash process to run cmdString.
     * Returns a ProcessHandle.
     * Must be overridden by every backend.
     */
    protected abstract fun _runBash(
        cmdString: String,
        login: Boolean = false,
        timeout: Int = 120,
        stdinData: String? = null
    ): ProcessHandle

    /**
     * Release backend resources (container, instance, connection).
     */
    abstract fun cleanup()

    // ------------------------------------------------------------------
    // Session snapshot (initSession)
    // ------------------------------------------------------------------

    /**
     * Capture login shell environment into a snapshot file.
     * Called once after backend construction. On success, sets
     * snapshotReady = true so subsequent commands source the snapshot.
     */
    fun initSession() {
        val bootstrap = buildString {
            appendLine("export -p > $snapshotPath")
            appendLine("declare -f | grep -vE '^_[^_]' >> $snapshotPath")
            appendLine("alias -p >> $snapshotPath")
            appendLine("echo 'shopt -s expand_aliases' >> $snapshotPath")
            appendLine("echo 'set +e' >> $snapshotPath")
            appendLine("echo 'set +u' >> $snapshotPath")
            appendLine("pwd -P > $cwdFile 2>/dev/null || true")
            appendLine("printf '\\n${_cwdMarker}%s${_cwdMarker}\\n' \"\$(pwd -P)\"")
        }
        try {
            val proc = _runBash(bootstrap, login = true, timeout = snapshotTimeout)
            val result = _waitForProcess(proc, timeout = snapshotTimeout)
            snapshotReady = true
            _updateCwd(result)
        } catch (_: Exception) {
            snapshotReady = false
        }
    }

    // ------------------------------------------------------------------
    // Command wrapping
    // ------------------------------------------------------------------

    /**
     * Build the full bash script that sources snapshot, cd's, runs command,
     * re-dumps env vars, and emits CWD markers.
     */
    protected fun _wrapCommand(command: String, cwd: String): String {
        val escaped = command.replace("'", "'\\''")
        val parts = mutableListOf<String>()

        // Source snapshot (env vars from previous commands)
        if (snapshotReady) {
            parts.add("source $snapshotPath 2>/dev/null || true")
        }

        // cd to working directory - let bash expand ~ natively
        val quotedCwd = if (cwd == "~" || cwd.startsWith("~/")) cwd else "'$cwd'"
        parts.add("cd $quotedCwd || exit 126")

        // Run the actual command
        parts.add("eval '$escaped'")
        parts.add("__hermes_ec=\$?")

        // Re-dump env vars to snapshot
        if (snapshotReady) {
            parts.add("export -p > $snapshotPath 2>/dev/null || true")
        }

        // Write CWD to file and stdout marker
        parts.add("pwd -P > $cwdFile 2>/dev/null || true")
        parts.add("printf '\\n${_cwdMarker}%s${_cwdMarker}\\n' \"\$(pwd -P)\"")
        parts.add("exit \$__hermes_ec")

        return parts.joinToString("\n")
    }

    // ------------------------------------------------------------------
    // Stdin heredoc embedding (for SDK backends)
    // ------------------------------------------------------------------

    /**
     * Append stdinData as a shell heredoc to the command string.
     */
    protected fun _embedStdinHeredoc(command: String, stdinData: String): String {
        val delimiter = "HERMES_STDIN_${UUID.randomUUID().toString().replace("-", "").substring(0, 12)}"
        return "$command << '$delimiter'\n$stdinData\n$delimiter"
    }

    // ------------------------------------------------------------------
    // Process lifecycle
    // ------------------------------------------------------------------

    /**
     * Poll-based wait with interrupt checking and stdout draining.
     * Fires the activity callback every 10s while process is running.
     */
    protected fun _waitForProcess(proc: ProcessHandle, timeout: Int = 120): MutableMap<String, Any?> {
        val outputChunks = mutableListOf<String>()

        val drainThread = Thread {
            try {
                proc.stdout()?.bufferedReader()?.forEachLine { line ->
                    outputChunks.add(line + "\n")
                }
            } catch (_: Exception) {
            }
        }
        drainThread.isDaemon = true
        drainThread.start()

        val deadline = System.currentTimeMillis() + timeout * 1000L

        try {
            while (proc.poll() == null) {
                if (System.currentTimeMillis() > deadline) {
                    _killProcess(proc)
                    drainThread.join(2000)
                    val partial = outputChunks.joinToString("")
                    val timeoutMsg = "\n[Command timed out after ${timeout}s]"
                    return mutableMapOf(
                        "output" to (if (partial.isNotEmpty()) partial + timeoutMsg else timeoutMsg.trimStart()),
                        "returncode" to 124
                    )
                }
                Thread.sleep(200)
            }
        } catch (_: InterruptedException) {
            try {
                _killProcess(proc)
                drainThread.join(2000)
            } catch (_: Exception) {
            }
            throw InterruptedException()
        }

        drainThread.join(5000)

        return mutableMapOf(
            "output" to outputChunks.joinToString(""),
            "returncode" to proc.returncode()
        )
    }

    /**
     * Terminate a process. Subclasses may override for process-group kill.
     */
    protected open fun _killProcess(proc: ProcessHandle) {
        try {
            proc.kill()
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------
    // CWD extraction
    // ------------------------------------------------------------------

    /**
     * Extract CWD from command output. Override for local file-based read.
     */
    protected open fun _updateCwd(result: MutableMap<String, Any?>) {
        _extractCwdFromOutput(result)
    }

    /**
     * Parse the __HERMES_CWD_{session}__ marker from stdout output.
     * Updates cwd and strips the marker from result["output"].
     */
    protected fun _extractCwdFromOutput(result: MutableMap<String, Any?>) {
        val output = result["output"] as? String ?: return
        val marker = _cwdMarker
        val last = output.lastIndexOf(marker)
        if (last == -1) return

        val searchStart = maxOf(0, last - 4096)
        val first = output.lastIndexOf(marker, last - 1)
        if (first == -1 || first == last || first < searchStart) return

        val cwdPath = output.substring(first + marker.length, last).trim()
        if (cwdPath.isNotEmpty()) {
            this.cwd = cwdPath
        }

        // Strip the marker line AND the \n injected before it
        val lineStart = output.lastIndexOf("\n", first - 1).let { if (it == -1) first else it }
        val lineEnd = output.indexOf("\n", last + marker.length).let { if (it == -1) output.length else it + 1 }

        result["output"] = output.substring(0, lineStart) + output.substring(lineEnd)
    }

    // ------------------------------------------------------------------
    // Hooks
    // ------------------------------------------------------------------

    /**
     * Hook called before each command execution.
     * Remote backends override this to trigger their FileSyncManager.
     */
    protected open fun _beforeExecute() {
        // No-op by default
    }

    // ------------------------------------------------------------------
    // Unified execute()
    // ------------------------------------------------------------------

    /**
     * Execute a command, return map with "output" and "returncode".
     */
    fun execute(
        command: String,
        cwd: String = "",
        timeout: Int? = null,
        stdinData: String? = null
    ): MutableMap<String, Any?> {
        _beforeExecute()

        val (execCommand, sudoStdin) = _prepareCommand(command)
        val effectiveTimeout = timeout ?: this.timeout
        val effectiveCwd = cwd.ifEmpty { this.cwd }

        // Merge sudo stdin with caller stdin
        val effectiveStdin = when {
            sudoStdin != null && stdinData != null -> sudoStdin + stdinData
            sudoStdin != null -> sudoStdin
            else -> stdinData
        }

        var finalCommand = execCommand
        var finalStdin = effectiveStdin

        // Embed stdin as heredoc for backends that need it
        if (finalStdin != null && stdinMode == "heredoc") {
            finalCommand = _embedStdinHeredoc(finalCommand, finalStdin)
            finalStdin = null
        }

        val wrapped = _wrapCommand(finalCommand, effectiveCwd)

        // Use login shell if snapshot failed
        val login = !snapshotReady

        val proc = _runBash(wrapped, login = login, timeout = effectiveTimeout, stdinData = finalStdin)
        val result = _waitForProcess(proc, timeout = effectiveTimeout)
        _updateCwd(result)

        return result
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /**
     * Alias for cleanup (compat with older callers).
     */
    fun stop() {
        cleanup()
    }

    /**
     * Transform sudo commands if SUDO_PASSWORD is available.
     */
    protected open fun _prepareCommand(command: String): Pair<String, String?> {
        // Stub: sudo transformation not applicable on Android
        return Pair(command, null)
    }
}

// ── Module-level helpers ported from tools/environments/base.py ───────────

/** Opt-in debug trace: every is_interrupted() state change from _waitForProcess. */
val _DEBUG_INTERRUPT: Boolean = (System.getenv("HERMES_DEBUG_INTERRUPT")?.isNotEmpty() == true)

/** Thread-local activity callback — the agent sets this before a tool call. */
private val _activityCallbackLocal: ThreadLocal<((String) -> Unit)?> = ThreadLocal()

/** Register a callback that _waitForProcess fires periodically. */
fun setActivityCallback(cb: ((String) -> Unit)?) {
    _activityCallbackLocal.set(cb)
}

fun _getActivityCallback(): ((String) -> Unit)? = _activityCallbackLocal.get()

/**
 * Fire the activity callback at most once every `state['interval']` seconds.
 *
 * *state* must contain `last_touch` (monotonic timestamp) and `start`
 * (monotonic timestamp of the operation start). An optional `interval`
 * key overrides the default 10 s cadence.
 */
fun touchActivityIfDue(state: MutableMap<String, Any?>, label: String) {
    val now = System.nanoTime() / 1_000_000_000.0
    val interval = (state["interval"] as? Number)?.toDouble() ?: 10.0
    val lastTouch = (state["last_touch"] as? Number)?.toDouble() ?: 0.0
    if (now - lastTouch < interval) return
    state["last_touch"] = now
    try {
        val cb = _getActivityCallback() ?: return
        val start = (state["start"] as? Number)?.toDouble() ?: now
        val elapsed = (now - start).toInt()
        cb("$label (${elapsed}s elapsed)")
    } catch (_: Exception) {
    }
}

/**
 * Return the host-side root for all sandbox storage (Docker workspaces,
 * Singularity overlays/SIF cache, etc.).
 *
 * Configurable via TERMINAL_SANDBOX_DIR. Defaults to {HERMES_HOME}/sandboxes/.
 */
fun getSandboxDir(): java.io.File {
    val custom = System.getenv("TERMINAL_SANDBOX_DIR")
    val p = if (!custom.isNullOrEmpty()) {
        java.io.File(custom)
    } else {
        java.io.File(System.getProperty("user.home") ?: "/data/local/tmp", ".hermes/sandboxes")
    }
    if (!p.exists()) p.mkdirs()
    return p
}

/** Write *data* to proc.stdin on a daemon thread to avoid pipe-buffer deadlocks. */
fun _pipeStdin(proc: Process, data: String) {
    val t = Thread {
        try {
            proc.outputStream.write(data.toByteArray(Charsets.UTF_8))
            proc.outputStream.close()
        } catch (_: Exception) {
        }
    }
    t.isDaemon = true
    t.start()
}

/**
 * Spawn a subprocess with standard stdout/stderr/stdin setup.
 *
 * If *stdinData* is provided, writes it asynchronously via [_pipeStdin].
 * Backends with special Popen needs can bypass this and call [_pipeStdin] directly.
 */
fun _popenBash(cmd: List<String>, stdinData: String? = null, envOverrides: Map<String, String>? = null): Process {
    val pb = ProcessBuilder(cmd)
    pb.redirectErrorStream(true)
    if (envOverrides != null) {
        pb.environment().putAll(envOverrides)
    }
    val proc = pb.start()
    if (stdinData != null) _pipeStdin(proc, stdinData)
    return proc
}

/** Load a JSON file as a dict, returning empty map on any error. */
@Suppress("UNCHECKED_CAST")
fun _loadJsonStore(path: java.io.File): MutableMap<String, Any?> {
    if (!path.exists()) return mutableMapOf()
    return try {
        val text = path.readText(Charsets.UTF_8)
        val parsed = com.xiaomo.hermes.hermes.gson.fromJson(text, Map::class.java) as? Map<String, Any?>
        (parsed ?: emptyMap()).toMutableMap()
    } catch (_: Exception) {
        mutableMapOf()
    }
}

/** Write *data* as pretty-printed JSON to *path*. */
fun _saveJsonStore(path: java.io.File, data: Map<String, Any?>) {
    path.parentFile?.let { if (!it.exists()) it.mkdirs() }
    try {
        val json = com.xiaomo.hermes.hermes.gson.toJson(data)
        path.writeText(json, Charsets.UTF_8)
    } catch (_: Exception) {
    }
}

/** Return (mtime, size) for cache comparison, or null if unreadable. */
fun _fileMtimeKey(hostPath: String): Pair<Long, Long>? {
    return try {
        val f = java.io.File(hostPath)
        if (!f.exists()) null else (f.lastModified() to f.length())
    } catch (_: Exception) {
        null
    }
}

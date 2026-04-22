package com.xiaomo.hermes.hermes.tools

import android.util.Log
import java.io.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.concurrent.thread

/**
 * In-process managed process registry for tracking long-running child processes.
 * Ported from process_registry.py
 */
object ProcessRegistry {

    private const val _TAG = "ProcessRegistry"

    // Limits
    private const val MAX_OUTPUT_CHARS = 200_000  // 200KB rolling output buffer
    private const val FINISHED_TTL_SECONDS = 1800L  // 30 minutes
    private const val MAX_PROCESSES = 64

    // Watch pattern rate limiting
    private const val WATCH_MAX_PER_WINDOW = 8
    private const val WATCH_WINDOW_SECONDS = 10L
    private const val WATCH_OVERLOAD_KILL_SECONDS = 45L

    private val _SHELL_NOISE_SUBSTRINGS = listOf(
        "bash: cannot set terminal process group",
        "bash: no job control in this shell",
        "no job control in this shell",
        "cannot set terminal process group",
        "tcsetattr: Inappropriate ioctl for device")

    data class ProcessSession(
        val id: String,
        val command: String,
        val taskId: String = "",
        val sessionKey: String = "",
        val cwd: String? = null,
        val startedAt: Double = 0.0,
        val pidScope: String = "host",
        // Mutable fields
        var pid: Int? = null,
        var process: Process? = null,
        var exited: Boolean = false,
        var exitCode: Int? = null,
        var outputBuffer: String = "",
        var maxOutputChars: Int = MAX_OUTPUT_CHARS,
        var detached: Boolean = false,
        // Watcher metadata
        var watcherPlatform: String = "",
        var watcherChatId: String = "",
        var watcherUserId: String = "",
        var watcherUserName: String = "",
        var watcherThreadId: String = "",
        var watcherInterval: Int = 0,
        var notifyOnComplete: Boolean = false,
        // Watch patterns
        var watchPatterns: List<String> = emptyList(),
        // Internal watch state (not serialized)
        var watchHits: Int = 0,
        var watchSuppressed: Int = 0,
        var watchOverloadSince: Double = 0.0,
        var watchDisabled: Boolean = false,
        var watchWindowHits: Int = 0,
        var watchWindowStart: Double = 0.0,
        // Thread handle for reader
        var readerThread: Thread? = null,
        // Lock for thread-safe mutations
        val lock: ReentrantLock = ReentrantLock())

    private val _running = ConcurrentHashMap<String, ProcessSession>()
    private val _finished = ConcurrentHashMap<String, ProcessSession>()
    private val _lock = ReentrantLock()

    // Completion notification queue
    private val completionQueue: MutableList<Map<String, Any>> = mutableListOf()
    private val _completionConsumed: MutableSet<String> = mutableSetOf()

    // Checkpoint file path
    var checkpointPath: File? = null

    // Watcher side-channel
    val pendingWatchers: MutableList<Map<String, Any>> = mutableListOf()

    fun killAll() {
        _lock.withLock {
            for ((id, session) in _running) {
                if (!session.exited) {
                    try {
                        killProcess(id)
                    } catch (_: Exception) {}
                }
            }
        }
        _running.clear()
    }

    // --- Helper methods ---

    fun cleanShellNoise(text: String): String {
        val lines = text.split("\n").toMutableList()
        while (lines.isNotEmpty() && _SHELL_NOISE_SUBSTRINGS.any { it in lines[0] }) {
            lines.removeAt(0)
        }
        return lines.joinToString("\n")
    }

    fun checkWatchPatterns(session: ProcessSession, newText: String) {
        if (session.watchPatterns.isEmpty() || session.watchDisabled) return

        val matchedLines = mutableListOf<String>()
        var matchedPattern: String? = null
        for (line in newText.lines()) {
            for (pat in session.watchPatterns) {
                if (pat in line) {
                    matchedLines.add(line.trimEnd())
                    if (matchedPattern == null) matchedPattern = pat
                    break
                }
            }
        }

        if (matchedLines.isEmpty()) return

        val now = (System.currentTimeMillis() / 1000.0)
        session.lock.withLock {
            if (now - session.watchWindowStart >= WATCH_WINDOW_SECONDS) {
                session.watchWindowHits = 0
                session.watchWindowStart = now
            }

            if (session.watchWindowHits >= WATCH_MAX_PER_WINDOW) {
                session.watchSuppressed += matchedLines.size
                if (session.watchOverloadSince == 0.0) {
                    session.watchOverloadSince = now
                } else if (now - session.watchOverloadSince > WATCH_OVERLOAD_KILL_SECONDS) {
                    session.watchDisabled = true
                    synchronized(completionQueue) {
                        completionQueue.add(mapOf(
                            "session_id" to session.id,
                            "command" to session.command,
                            "type" to "watch_disabled",
                            "suppressed" to session.watchSuppressed,
                            "message" to "Watch patterns disabled for process ${session.id} — too many matches (${session.watchSuppressed} suppressed). Use process(action='poll') to check output manually."
                        ))
                    }
                }
                return
            }

            session.watchWindowHits++
            session.watchHits++
            session.watchOverloadSince = 0.0
        }

        val output = matchedLines.take(20).joinToString("\n").let {
            if (it.length > 2000) it.take(2000) + "\n...(truncated)" else it
        }

        synchronized(completionQueue) {
            completionQueue.add(mapOf(
                "session_id" to session.id,
                "command" to session.command,
                "type" to "watch_match",
                "pattern" to (matchedPattern ?: ""),
                "output" to output,
                "suppressed" to 0))
        }
    }

    fun isHostPidAlive(pid: Int?): Boolean {
        if (pid == null) return false
        return try {
            // Use ProcessBuilder to check if PID exists via `kill -0`
            val pb = ProcessBuilder("kill", "-0", pid.toString())
            pb.redirectErrorStream(true)
            val proc = pb.start()
            proc.waitFor(2, TimeUnit.SECONDS)
            proc.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun refreshDetachedSession(session: ProcessSession?): ProcessSession? {
        if (session == null || session.exited || !session.detached || session.pidScope != "host") return session
        if (isHostPidAlive(session.pid)) return session

        session.lock.withLock {
            if (session.exited) return session
            session.exited = true
            session.exitCode = null
        }
        moveToFinished(session)
        return session
    }

    fun terminateHostPid(pid: Int) {
        try {
            // On Android, use kill command
            val pb = ProcessBuilder("kill", "-TERM", pid.toString())
            pb.redirectErrorStream(true)
            val proc = pb.start()
            proc.waitFor(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(_TAG, "Failed to terminate host PID $pid: ${e.message}")
            try {
                val pb = ProcessBuilder("kill", "-9", pid.toString())
                pb.redirectErrorStream(true)
                pb.start().waitFor(2, TimeUnit.SECONDS)
            } catch (_: Exception) {}
        }
    }

    fun envTempDir(): String {
        return "/data/local/tmp"
    }

    // --- Spawn methods ---

    fun spawnLocal(
        command: String,
        cwd: String? = null,
        taskId: String = "",
        sessionKey: String = "",
        envVars: Map<String, String>? = null,
        usePty: Boolean = false): ProcessSession {
        val sessionId = "proc_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val workDir = cwd ?: System.getProperty("user.dir", "/data/local/tmp")

        val session = ProcessSession(
            id = sessionId,
            command = command,
            taskId = taskId,
            sessionKey = sessionKey,
            cwd = workDir,
            startedAt = (System.currentTimeMillis() / 1000.0))

        if (usePty) {
            Log.w(_TAG, "PTY mode not supported on Android, falling back to pipe mode")
        }

        try {
            // Use bash -lc for login shell behavior
            val pb = ProcessBuilder("bash", "-lc", command)
            pb.directory(File(workDir))
            pb.redirectErrorStream(true) // Merge stderr into stdout

            // Set environment variables
            envVars?.forEach { (k, v) -> pb.environment()[k] = v }
            pb.environment()["PYTHONUNBUFFERED"] = "1"

            val proc = pb.start()
            session.process = proc
            session.pid = try {
                val field = proc.javaClass.getDeclaredField("pid")
                field.isAccessible = true
                field.getInt(proc)
            } catch (_: Exception) { 0 }

            // Start output reader thread
            val reader = thread(
                start = true,
                isDaemon = true,
                name = "proc-reader-$sessionId") {
                readerLoop(session)
            }
            session.readerThread = reader

            _lock.withLock {
                pruneIfNeeded()
                _running[sessionId] = session
            }
            writeCheckpoint()
        } catch (e: Exception) {
            session.exited = true
            session.exitCode = -1
            session.outputBuffer = "Failed to start: ${e.message}"
            Log.e(_TAG, "Failed to spawn local process: ${e.message}")
        }

        return session
    }

    fun spawnViaEnv(
        command: String,
        cwd: String? = null,
        taskId: String = "",
        sessionKey: String = "",
        timeout: Int = 10): ProcessSession {
        val sessionId = "proc_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        val session = ProcessSession(
            id = sessionId,
            command = command,
            taskId = taskId,
            sessionKey = sessionKey,
            cwd = cwd,
            startedAt = (System.currentTimeMillis() / 1000.0),
            pidScope = "sandbox")

        val tempDir = envTempDir()
        val logPath = "$tempDir/hermes_bg_${sessionId}.log"
        val pidPath = "$tempDir/hermes_bg_${sessionId}.pid"
        val exitPath = "$tempDir/hermes_bg_${sessionId}.exit"

        val shQ: (String) -> String = { s -> "'${s.replace("'", "'\\''")}'" }
        val quotedCommand = shQ(command)
        val quotedTempDir = shQ(tempDir)
        val quotedLogPath = shQ(logPath)
        val quotedPidPath = shQ(pidPath)
        val quotedExitPath = shQ(exitPath)

        val bgCommand = "mkdir -p $quotedTempDir && " +
            "( nohup bash -lc $quotedCommand > $quotedLogPath 2>&1; " +
            "rc=\$?; printf '%s\\n' \"\$rc\" > $quotedExitPath ) & " +
            "echo \$! > $quotedPidPath && cat $quotedPidPath"

        try {
            val pb = ProcessBuilder("bash", "-lc", bgCommand)
            if (cwd != null) pb.directory(File(cwd))
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val finished = proc.waitFor(timeout.toLong(), TimeUnit.SECONDS)
            if (finished) {
                val output = proc.inputStream.bufferedReader().readText().trim()
                val pidStr = output.lines().lastOrNull()?.trim()
                if (pidStr != null && pidStr.toIntOrNull() != null) {
                    session.pid = pidStr.toInt()
                }
            }
        } catch (e: Exception) {
            session.exited = true
            session.exitCode = -1
            session.outputBuffer = "Failed to start: ${e.message}"
        }

        if (!session.exited) {
            val reader = thread(
                start = true,
                isDaemon = true,
                name = "proc-poller-$sessionId") {
                envPollerLoop(session, logPath, pidPath, exitPath)
            }
            session.readerThread = reader
        }

        _lock.withLock {
            pruneIfNeeded()
            _running[sessionId] = session
        }
        writeCheckpoint()

        return session
    }

    // --- Reader / Poller threads ---

    fun readerLoop(session: ProcessSession) {
        val proc = session.process ?: return
        var firstChunk = true
        try {
            val reader = proc.inputStream.bufferedReader()
            val buffer = CharArray(4096)
            while (true) {
                val bytesRead = reader.read(buffer)
                if (bytesRead <= 0) break
                var chunk = String(buffer, 0, bytesRead)
                if (firstChunk) {
                    chunk = cleanShellNoise(chunk)
                    firstChunk = false
                }
                session.lock.withLock {
                    session.outputBuffer += chunk
                    if (session.outputBuffer.length > session.maxOutputChars) {
                        session.outputBuffer = session.outputBuffer.takeLast(session.maxOutputChars)
                    }
                }
                checkWatchPatterns(session, chunk)
            }
        } catch (e: Exception) {
            Log.d(_TAG, "Process stdout reader ended: ${e.message}")
        } finally {
            try {
                proc.waitFor(5, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Log.d(_TAG, "Process wait timed out or failed: ${e.message}")
            }
            session.exited = true
            session.exitCode = try { proc.exitValue() } catch (_: Exception) { -1 }
            moveToFinished(session)
        }
    }

    fun envPollerLoop(session: ProcessSession, logPath: String, pidPath: String, exitPath: String) {
        var prevOutputLen = 0
        while (!session.exited) {
            Thread.sleep(2000) // Poll every 2 seconds
            try {
                // Read new output from log file
                val logFile = File(logPath)
                if (logFile.exists()) {
                    val newOutput = try { logFile.readText() } catch (_: Exception) { "" }
                    if (newOutput.isNotEmpty()) {
                        val delta = if (newOutput.length > prevOutputLen) newOutput.substring(prevOutputLen) else ""
                        prevOutputLen = newOutput.length
                        session.lock.withLock {
                            session.outputBuffer = newOutput
                            if (session.outputBuffer.length > session.maxOutputChars) {
                                session.outputBuffer = session.outputBuffer.takeLast(session.maxOutputChars)
                            }
                        }
                        if (delta.isNotEmpty()) checkWatchPatterns(session, delta)
                    }
                }

                // Check if process is still running
                val exitFile = File(exitPath)
                if (exitFile.exists()) {
                    val exitStr = exitFile.readText().trim()
                    session.exitCode = exitStr.toIntOrNull() ?: -1
                    session.exited = true
                    moveToFinished(session)
                    return
                }

                // Check PID liveness
                val pidFile = File(pidPath)
                if (pidFile.exists()) {
                    val pidStr = pidFile.readText().trim()
                    val pid = pidStr.toIntOrNull()
                    if (pid != null && !isHostPidAlive(pid)) {
                        if (!exitFile.exists()) {
                            session.exitCode = -1
                        }
                        session.exited = true
                        moveToFinished(session)
                        return
                    }
                }
            } catch (e: Exception) {
                session.exited = true
                session.exitCode = -1
                moveToFinished(session)
                return
            }
        }
    }

    fun ptyReaderLoop(session: ProcessSession) {
        // PTY not supported on Android — Log.w and mark as exited
        Log.w(_TAG, "PTY reader loop called but PTY is not supported on Android")
        session.exited = true
        session.exitCode = -1
        moveToFinished(session)
    }

    fun moveToFinished(session: ProcessSession) {
        _lock.withLock {
            val wasRunning = _running.remove(session.id) != null
            _finished[session.id] = session
        }
        writeCheckpoint()

        if (session.exited && session.notifyOnComplete) {
            val outputTail = if (session.outputBuffer.isNotEmpty()) session.outputBuffer.takeLast(2000) else ""
            synchronized(completionQueue) {
                completionQueue.add(mapOf(
                    "type" to "completion",
                    "session_id" to session.id,
                    "command" to session.command,
                    "exit_code" to (session.exitCode ?: -1),
                    "output" to outputTail))
            }
        }
    }

    // --- Query methods ---

    fun isCompletionConsumed(sessionId: String): Boolean {
        return sessionId in _completionConsumed
    }

    fun get(sessionId: String): ProcessSession? {
        val session = _running[sessionId] ?: _finished[sessionId]
        return refreshDetachedSession(session)
    }

    fun poll(sessionId: String): Map<String, Any> {
        val session = get(sessionId)
            ?: return mapOf("status" to "not_found", "error" to "No process with ID $sessionId")

        val outputPreview = session.lock.withLock {
            if (session.outputBuffer.isNotEmpty()) session.outputBuffer.takeLast(1000) else ""
        }

        val result = mutableMapOf<String, Any>(
            "session_id" to session.id,
            "command" to session.command,
            "status" to if (session.exited) "exited" else "running",
            "pid" to (session.pid ?: 0),
            "uptime_seconds" to ((System.currentTimeMillis() / 1000.0) - session.startedAt).toInt(),
            "output_preview" to outputPreview)
        if (session.exited) {
            result["exit_code"] = session.exitCode ?: -1
            _completionConsumed.add(sessionId)
        }
        if (session.detached) {
            result["detached"] = true
            result["note"] = "Process recovered after restart -- output history unavailable"
        }
        return result
    }

    fun readLog(sessionId: String, offset: Int = 0, limit: Int = 200): Map<String, Any> {
        val session = get(sessionId)
            ?: return mapOf("status" to "not_found", "error" to "No process with ID $sessionId")

        val fullOutput = session.lock.withLock { session.outputBuffer }
        val lines = fullOutput.lines()
        val totalLines = lines.size

        val selected = if (offset == 0 && limit > 0) {
            lines.takeLast(limit)
        } else {
            lines.drop(offset).take(limit)
        }

        val result = mutableMapOf<String, Any>(
            "session_id" to session.id,
            "status" to if (session.exited) "exited" else "running",
            "output" to selected.joinToString("\n"),
            "total_lines" to totalLines,
            "showing" to "${selected.size} lines")
        if (session.exited) _completionConsumed.add(sessionId)
        return result
    }

    fun wait(sessionId: String, timeout: Int? = null): Map<String, Any> {
        val defaultTimeout = try {
            System.getenv("TERMINAL_TIMEOUT")?.toInt() ?: 180
        } catch (_: Exception) { 180 }

        val maxTimeout = defaultTimeout
        var timeoutNote: String? = null
        val effectiveTimeout = if (timeout != null && timeout > maxTimeout) {
            timeoutNote = "Requested wait of ${timeout}s was clamped to configured limit of ${maxTimeout}s"
            maxTimeout
        } else {
            timeout ?: maxTimeout
        }

        var session = get(sessionId)
            ?: return mapOf("status" to "not_found", "error" to "No process with ID $sessionId")

        val deadline = System.nanoTime() + effectiveTimeout * 1_000_000_000L

        while (System.nanoTime() < deadline) {
            session = refreshDetachedSession(session) ?: break
            if (session.exited) {
                _completionConsumed.add(sessionId)
                val result = mutableMapOf<String, Any>(
                    "status" to "exited",
                    "exit_code" to (session.exitCode ?: -1),
                    "output" to session.outputBuffer.takeLast(2000))
                if (timeoutNote != null) result["timeout_note"] = timeoutNote
                return result
            }
            Thread.sleep(1000)
        }

        val result = mutableMapOf<String, Any>(
            "status" to "timeout",
            "output" to (session.outputBuffer.takeLast(1000)))
        result["timeout_note"] = timeoutNote ?: "Waited ${effectiveTimeout}s, process still running"
        return result
    }

    fun killProcess(sessionId: String): Map<String, Any> {
        val session = get(sessionId)
            ?: return mapOf("status" to "not_found", "error" to "No process with ID $sessionId")

        if (session.exited) {
            return mapOf("status" to "already_exited", "exit_code" to (session.exitCode ?: -1))
        }

        try {
            val proc = session.process
            if (proc != null) {
                proc.destroy()
                if (proc.isAlive) {
                    proc.waitFor(5, TimeUnit.SECONDS)
                    if (proc.isAlive) proc.destroyForcibly()
                }
            } else if (session.pid != null && session.pidScope == "host") {
                terminateHostPid(session.pid!!)
            } else if (session.detached && session.pidScope == "host" && session.pid != null) {
                if (!isHostPidAlive(session.pid)) {
                    session.lock.withLock {
                        session.exited = true
                        session.exitCode = null
                    }
                    moveToFinished(session)
                    return mapOf("status" to "already_exited", "exit_code" to (session.exitCode ?: -1))
                }
                terminateHostPid(session.pid!!)
            } else {
                return mapOf(
                    "status" to "error",
                    "error" to "Recovered process cannot be killed after restart because its original runtime handle is no longer available"
                )
            }
            session.exited = true
            session.exitCode = -15 // SIGTERM
            moveToFinished(session)
            writeCheckpoint()
            return mapOf("status" to "killed", "session_id" to session.id)
        } catch (e: Exception) {
            return mapOf("status" to "error", "error" to (e.message ?: "Unknown error"))
        }
    }

    fun writeStdin(sessionId: String, data: String): Map<String, Any> {
        val session = get(sessionId)
            ?: return mapOf("status" to "not_found", "error" to "No process with ID $sessionId")
        if (session.exited) return mapOf("status" to "already_exited", "error" to "Process has already finished")

        val proc = session.process
        if (proc == null || proc.outputWriter == null) {
            return mapOf("status" to "error", "error" to "Process stdin not available")
        }
        return try {
            val writer = proc.outputWriter as java.io.OutputStreamWriter
            writer.write(data)
            writer.flush()
            mapOf("status" to "ok", "bytes_written" to data.length)
        } catch (e: Exception) {
            mapOf("status" to "error", "error" to (e.message ?: "Unknown error"))
        }
    }

    fun submitStdin(sessionId: String, data: String = ""): Map<String, Any> {
        return writeStdin(sessionId, "$data\n")
    }

    fun closeStdin(sessionId: String): Map<String, Any> {
        val session = get(sessionId)
            ?: return mapOf("status" to "not_found", "error" to "No process with ID $sessionId")
        if (session.exited) return mapOf("status" to "already_exited", "error" to "Process has already finished")

        val proc = session.process
        if (proc == null) {
            return mapOf("status" to "error", "error" to "Process stdin not available")
        }
        return try {
            proc.outputWriter?.close()
            mapOf("status" to "ok", "message" to "stdin closed")
        } catch (e: Exception) {
            mapOf("status" to "error", "error" to (e.message ?: "Unknown error"))
        }
    }

    fun listSessions(taskId: String? = null): List<Map<String, Any>> {
        val allSessions = mutableListOf<ProcessSession>()
        _lock.withLock {
            allSessions.addAll(_running.values)
            allSessions.addAll(_finished.values)
        }

        val refreshed = allSessions.map { refreshDetachedSession(it) }.filterNotNull()
        val filtered = if (taskId != null) refreshed.filter { it.taskId == taskId } else refreshed

        return filtered.map { s ->
            val entry = mutableMapOf<String, Any>(
                "session_id" to s.id,
                "command" to s.command.take(200),
                "cwd" to (s.cwd ?: ""),
                "pid" to (s.pid ?: 0),
                "started_at" to java.time.Instant.ofEpochSecond(s.startedAt.toLong()).toString(),
                "uptime_seconds" to ((System.currentTimeMillis() / 1000.0) - s.startedAt).toInt(),
                "status" to if (s.exited) "exited" else "running",
                "output_preview" to s.outputBuffer.takeLast(200))
            if (s.exited) entry["exit_code"] = s.exitCode ?: -1
            if (s.detached) entry["detached"] = true
            entry
        }
    }

    // --- Session/Task queries ---

    fun hasActiveProcesses(taskId: String): Boolean {
        val sessions = _lock.withLock { _running.values.toList() }
        sessions.forEach { refreshDetachedSession(it) }
        return _lock.withLock {
            _running.values.any { it.taskId == taskId && !it.exited }
        }
    }

    fun hasActiveForSession(sessionKey: String): Boolean {
        val sessions = _lock.withLock { _running.values.toList() }
        sessions.forEach { refreshDetachedSession(it) }
        return _lock.withLock {
            _running.values.any { it.sessionKey == sessionKey && !it.exited }
        }
    }

    fun killAll(taskId: String? = null): Int {
        val targets = _lock.withLock {
            _running.values.filter { s ->
                (taskId == null || s.taskId == taskId) && !s.exited
            }
        }
        var killed = 0
        for (session in targets) {
            val result = killProcess(session.id)
            if (result["status"] in listOf("killed", "already_exited")) killed++
        }
        return killed
    }

    // --- Cleanup / Pruning ---

    fun pruneIfNeeded() {
        // Prune expired finished sessions
        val now = (System.currentTimeMillis() / 1000.0)
        val expired = _finished.filter { (_, s) ->
            (now - s.startedAt) > FINISHED_TTL_SECONDS
        }.keys
        expired.forEach { _finished.remove(it) }

        // If still over limit, remove oldest finished
        val total = _running.size + _finished.size
        if (total >= MAX_PROCESSES && _finished.isNotEmpty()) {
            val oldestId = _finished.minByOrNull { it.value.startedAt }?.key
            if (oldestId != null) _finished.remove(oldestId)
        }
    }

    // --- Checkpoint ---

    fun writeCheckpoint() {
        val file = checkpointPath ?: return
        try {
            _lock.withLock {
                val entries = _running.values.filter { !it.exited }.map { s ->
                    mapOf(
                        "session_id" to s.id,
                        "command" to s.command,
                        "pid" to (s.pid ?: 0),
                        "pid_scope" to s.pidScope,
                        "cwd" to (s.cwd ?: ""),
                        "started_at" to s.startedAt,
                        "task_id" to s.taskId,
                        "session_key" to s.sessionKey,
                        "watcher_platform" to s.watcherPlatform,
                        "watcher_chat_id" to s.watcherChatId,
                        "watcher_user_id" to s.watcherUserId,
                        "watcher_user_name" to s.watcherUserName,
                        "watcher_thread_id" to s.watcherThreadId,
                        "watcher_interval" to s.watcherInterval,
                        "notify_on_complete" to s.notifyOnComplete,
                        "watch_patterns" to s.watchPatterns)
                }
                val json = org.json.JSONArray(entries)
                file.parentFile?.mkdirs()
                file.writeText(json.toString())
            }
        } catch (e: Exception) {
            Log.d(_TAG, "Failed to write checkpoint file: ${e.message}")
        }
    }

    fun recoverFromCheckpoint(): Int {
        val file = checkpointPath ?: return 0
        if (!file.exists()) return 0

        val entries = try {
            val json = org.json.JSONArray(file.readText())
            (0 until json.length()).map { json.getJSONObject(it) }
        } catch (_: Exception) { return 0 }

        var recovered = 0
        for (entry in entries) {
            val pid = entry.optInt("pid", 0)
            if (pid <= 0) continue

            val pidScope = entry.optString("pid_scope", "host")
            if (pidScope != "host") {
                Log.i(_TAG, "Skipping recovery for non-host process: ${entry.optString("command", "unknown").take(60)}")
                continue
            }

            val alive = isHostPidAlive(pid)
            if (alive) {
                val session = ProcessSession(
                    id = entry.getString("session_id"),
                    command = entry.optString("command", "unknown"),
                    taskId = entry.optString("task_id", ""),
                    sessionKey = entry.optString("session_key", ""),
                    pid = pid,
                    pidScope = pidScope,
                    cwd = entry.optString("cwd", null),
                    startedAt = entry.optDouble("started_at", (System.currentTimeMillis() / 1000.0)),
                    detached = true,
                    watcherPlatform = entry.optString("watcher_platform", ""),
                    watcherChatId = entry.optString("watcher_chat_id", ""),
                    watcherUserId = entry.optString("watcher_user_id", ""),
                    watcherUserName = entry.optString("watcher_user_name", ""),
                    watcherThreadId = entry.optString("watcher_thread_id", ""),
                    watcherInterval = entry.optInt("watcher_interval", 0),
                    notifyOnComplete = entry.optBoolean("notify_on_complete", false),
                    watchPatterns = try {
                        val arr = entry.getJSONArray("watch_patterns")
                        (0 until arr.length()).map { arr.getString(it) }
                    } catch (_: Exception) { emptyList() })
                _running[session.id] = session
                recovered++
                Log.i(_TAG, "Recovered detached process: ${session.command.take(60)} (pid=$pid)")

                if (session.watcherInterval > 0) {
                    pendingWatchers.add(mapOf(
                        "session_id" to session.id,
                        "check_interval" to session.watcherInterval,
                        "session_key" to session.sessionKey,
                        "platform" to session.watcherPlatform,
                        "chat_id" to session.watcherChatId,
                        "user_id" to session.watcherUserId,
                        "user_name" to session.watcherUserName,
                        "thread_id" to session.watcherThreadId,
                        "notify_on_complete" to session.notifyOnComplete))
                }
            }
        }

        writeCheckpoint()
        return recovered
    }

    // --- Utility ---

    // Helper to get Process.outputWriter (OutputStreamWriter for stdin)
    private val Process.outputWriter: OutputStreamWriter?
        get() = try { this.outputStream?.let { OutputStreamWriter(it) } } catch (_: Exception) { null }
}

/** Per-module constants for process_registry — wrapped to avoid
 *  colliding with `_IS_WINDOWS` in CodeExecutionTool.kt (same package). */
private object _ProcessRegistryConstants {
    /** Windows-platform check (Python `sys.platform`). On Android: always false. */
    const val _IS_WINDOWS: Boolean = false

    /** Path to the process-registry checkpoint JSON under HERMES_HOME. */
    val CHECKPOINT_PATH: java.io.File by lazy {
        val env = (System.getenv("HERMES_HOME") ?: "").trim()
        val home = if (env.isNotEmpty()) java.io.File(env)
        else java.io.File(System.getProperty("user.home") ?: "/", ".hermes")
        java.io.File(home, "process_registry.json")
    }

    /** OpenAI-shaped schema for the `process_*` tool family (stub). */
    val PROCESS_SCHEMA: Map<String, Any?> = emptyMap()
}

/** Python `format_uptime_short` — stub. */
fun formatUptimeShort(seconds: Double): String {
    val s = seconds.toInt()
    if (s < 60) return "${s}s"
    if (s < 3600) return "${s / 60}m"
    if (s < 86400) return "${s / 3600}h"
    return "${s / 86400}d"
}

/** Python `_handle_process` — stub. */
private fun _handleProcess(pid: Int, action: String): Boolean = false

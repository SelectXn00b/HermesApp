package com.xiaomo.hermes.hermes

import android.util.Log
import java.util.UUID

/**
 * ModalUtils - Ported from ../hermes-agent/tools/environments/modal_utils.py
 *
 * Shared Hermes-side execution flow for Modal transports.
 * Handles command preparation, cwd/timeout normalization, stdin/sudo
 * shell wrapping, common result shape, and interrupt/cancel polling.
 */

/**
 * Normalized command data passed to a transport-specific exec runner.
 */
data class PreparedModalExec(
    val command: String,
    val cwd: String,
    val timeout: Int,
    val stdinData: String? = null
)

/**
 * Transport response after starting an exec.
 */
data class ModalExecStart(
    val handle: Any? = null,
    val immediateResult: Map<String, Any?>? = null
)

/**
 * Append stdin as a shell heredoc for transports without stdin piping.
 */
fun wrapModalStdinHeredoc(command: String, stdinData: String): String {
    var marker = "HERMES_EOF_${UUID.randomUUID().toString().replace("-", "").substring(0, 8)}"
    while (stdinData.contains(marker)) {
        marker = "HERMES_EOF_${UUID.randomUUID().toString().replace("-", "").substring(0, 8)}"
    }
    return "$command << '$marker'\n$stdinData\n$marker"
}

/**
 * Feed sudo via a shell pipe for transports without direct stdin piping.
 */
fun wrapModalSudoPipe(command: String, sudoStdin: String): String {
    val escaped = sudoStdin.trimEnd().replace("'", "'\\''")
    return "printf '%s\\n' '$escaped' | $command"
}

/**
 * Base execution flow for Modal transports (managed and direct).
 *
 * This overrides BaseEnvironment.execute() because the tool-gateway handles
 * command preparation, CWD tracking, and env-snapshot management on the
 * server side. The base class's _wrap_command / _wait_for_process / snapshot
 * machinery does not apply here -- the gateway owns that responsibility.
 */
abstract class BaseModalExecutionEnvironment(
    protected val modalCwd: String = "/root",
    protected val modalTimeout: Int = 60
) {
    companion object {
        private const val TAG = "BaseModalExec"
    }

    protected open val stdinMode: String = "payload"
    protected open val pollIntervalSeconds: Double = 0.25
    protected open val clientTimeoutGraceSeconds: Double? = null
    protected open val interruptOutput: String = "[Command interrupted]"
    protected open val unexpectedErrorPrefix: String = "Modal execution error"

    /**
     * Execute a command in the Modal sandbox with polling for completion.
     */
    fun execute(command: String, cwd: String = ""): Map<String, Any?> {
        _beforeExecute()
        val prepared = _prepareModalExec(command, cwd)

        val start = try {
            _startModalExec(prepared)
        } catch (e: Exception) {
            return _errorResult("$unexpectedErrorPrefix: ${e.message}")
        }

        if (start.immediateResult != null) {
            return start.immediateResult
        }

        if (start.handle == null) {
            return _errorResult("$unexpectedErrorPrefix: transport did not return an exec handle")
        }

        // Poll for completion
        val deadline = if (clientTimeoutGraceSeconds != null) {
            System.currentTimeMillis() + ((prepared.timeout + clientTimeoutGraceSeconds!!) * 1000).toLong()
        } else null

        while (true) {
            val result = try {
                _pollModalExec(start.handle)
            } catch (e: Exception) {
                return _errorResult("$unexpectedErrorPrefix: ${e.message}")
            }

            if (result != null) return result

            if (deadline != null && System.currentTimeMillis() >= deadline) {
                try { _cancelModalExec(start.handle) } catch (_: Exception) {}
                return _timeoutResultForModal(prepared.timeout)
            }

            Thread.sleep((pollIntervalSeconds * 1000).toLong())
        }
    }

    /**
     * Hook for backends that need pre-exec sync or validation.
     */
    open fun _beforeExecute() {
        // No-op by default
    }

    /**
     * Prepare command data for Modal exec.
     */
    fun _prepareModalExec(command: String, cwd: String = "", timeout: Int? = null, stdinData: String? = null): PreparedModalExec {
        val effectiveCwd = cwd.ifEmpty { modalCwd }
        val effectiveTimeout = timeout ?: modalTimeout

        var execCommand = command
        var execStdin = if (stdinMode == "payload") stdinData else null
        if (stdinData != null && stdinMode == "heredoc") {
            execCommand = wrapModalStdinHeredoc(execCommand, stdinData)
        }

        return PreparedModalExec(
            command = execCommand,
            cwd = effectiveCwd,
            timeout = effectiveTimeout,
            stdinData = execStdin
        )
    }

    /**
     * Build a standard result map.
     */
    protected fun _result(output: String, returncode: Int): Map<String, Any?> {
        return mapOf("output" to output, "returncode" to returncode)
    }

    /**
     * Build an error result (returncode=1).
     */
    fun _errorResult(output: String): Map<String, Any?> {
        return _result(output, 1)
    }

    /**
     * Build a timeout result.
     */
    open fun _timeoutResultForModal(timeout: Int): Map<String, Any?> {
        return _result("Command timed out after ${timeout}s", 124)
    }

    /**
     * Begin a transport-specific exec. Must be implemented by subclass.
     */
    abstract fun _startModalExec(prepared: PreparedModalExec): ModalExecStart

    /**
     * Return a final result dict when complete, else null.
     */
    abstract fun _pollModalExec(handle: Any?): Map<String, Any?>?

    /**
     * Cancel or terminate the active transport exec.
     */
    abstract fun _cancelModalExec(handle: Any?)
}

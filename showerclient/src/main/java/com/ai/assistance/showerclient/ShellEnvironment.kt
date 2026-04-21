package com.ai.assistance.showerclient

import android.util.Log

/**
 * Identity for executing shell commands.
 */
enum class ShellIdentity {
    DEFAULT,
    SHELL,
    ROOT,
}

/**
 * Result of executing a shell command.
 */
data class ShellCommandResult(
    val success: Boolean,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
)

/**
 * Minimal abstraction for running shell commands with an identity.
 *
 * Host apps must provide an implementation and assign it to [ShowerEnvironment.shellRunner]
 * during application startup.
 */
fun interface ShellRunner {
    suspend fun run(command: String, identity: ShellIdentity): ShellCommandResult
}

/**
 * Optional host-provided sink for Shower client logs.
 *
 * The Shower client remains host-agnostic: by default it logs to Android's system log.
 * Hosts can inject this sink to mirror logs to their own logging pipeline.
 */
fun interface ShowerLogSink {
    fun log(priority: Int, tag: String, message: String, throwable: Throwable?)
}

/**
 * Global environment configuration for the Shower client library.
 */
object ShowerEnvironment {

    @Volatile
    var shellRunner: ShellRunner? = null

    /**
     * Optional sink used to mirror Shower client logs into host logging systems.
     */
    @Volatile
    var logSink: ShowerLogSink? = null

    /**
     * Keep writing logs to Android system logcat.
     *
     * Default is true to preserve existing behavior when no host integration is configured.
     */
    @Volatile
    var emitToSystemLog: Boolean = true
}

/**
 * Logging facade for Shower client.
 *
 * This avoids hardcoding host logger implementations inside the showerclient module.
 */
object ShowerLog {

    private fun emit(priority: Int, tag: String, message: String, throwable: Throwable? = null) {
        if (ShowerEnvironment.emitToSystemLog) {
            when (priority) {
                Log.VERBOSE -> if (throwable != null) Log.v(tag, message, throwable) else Log.v(tag, message)
                Log.DEBUG -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
                Log.INFO -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
                Log.WARN -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
                Log.ERROR -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
                Log.ASSERT -> if (throwable != null) Log.wtf(tag, message, throwable) else Log.wtf(tag, message)
                else -> Log.println(priority, tag, message)
            }
        }

        try {
            ShowerEnvironment.logSink?.log(priority, tag, message, throwable)
        } catch (_: Throwable) {
            // Never allow host logger failures to affect showerclient execution.
        }
    }

    fun v(tag: String, message: String) = emit(Log.VERBOSE, tag, message)
    fun v(tag: String, message: String, throwable: Throwable) = emit(Log.VERBOSE, tag, message, throwable)
    fun d(tag: String, message: String) = emit(Log.DEBUG, tag, message)
    fun d(tag: String, message: String, throwable: Throwable) = emit(Log.DEBUG, tag, message, throwable)
    fun i(tag: String, message: String) = emit(Log.INFO, tag, message)
    fun i(tag: String, message: String, throwable: Throwable) = emit(Log.INFO, tag, message, throwable)
    fun w(tag: String, message: String) = emit(Log.WARN, tag, message)
    fun w(tag: String, message: String, throwable: Throwable) = emit(Log.WARN, tag, message, throwable)
    fun e(tag: String, message: String) = emit(Log.ERROR, tag, message)
    fun e(tag: String, message: String, throwable: Throwable) = emit(Log.ERROR, tag, message, throwable)
}

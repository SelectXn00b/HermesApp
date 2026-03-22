package com.xiaomo.termux

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Executes commands in the embedded Termux environment via ProcessBuilder.
 *
 * On Android 10+ the app's private data directory is not directly executable
 * (SELinux policy prevents `untrusted_app` from executing files in `/data/data/`).
 * We work around this by invoking the system dynamic linker (`/system/bin/linker64`)
 * to load and execute the Termux shell binary. This is the same approach used by
 * Termux itself.
 */
class ProcessExecutor(private val env: TermuxEnvironment) {

    companion object {
        private const val TAG = "ProcessExecutor"
        private const val MAX_OUTPUT_LENGTH = 50_000
        private const val LINKER64 = "/system/bin/linker64"
        private const val LINKER32 = "/system/bin/linker"
    }

    /**
     * Execute a shell command in the Termux environment.
     *
     * @param command    The command string to execute (passed to sh -c)
     * @param workingDir Optional working directory
     * @param timeout    Timeout in milliseconds (default 120s)
     * @param extraEnv   Additional environment variables to set
     */
    suspend fun exec(
        command: String,
        workingDir: File? = null,
        timeout: Long = 120_000,
        extraEnv: Map<String, String>? = null
    ): ExecResult = withContext(Dispatchers.IO) {
        try {
            val shell = env.shell
            if (!shell.exists()) {
                return@withContext ExecResult(
                    exitCode = -1,
                    output = "Termux runtime not installed. Shell not found at: ${shell.absolutePath}"
                )
            }

            // Wrap user command with exec-wrappers.sh so child processes (bash, python3, etc.)
            // are launched via linker64, bypassing SELinux W^X on Android 10+.
            val wrappedCommand = wrapWithLinkerFunctions(command)
            val cmdList = buildExecCommand(shell.absolutePath, wrappedCommand)

            val pb = ProcessBuilder(cmdList)
            pb.redirectErrorStream(true)

            // Set environment
            val processEnv = pb.environment()
            processEnv.putAll(env.envMap())
            extraEnv?.let { processEnv.putAll(it) }

            // Set working directory — default to workspace for consistency with other tools
            val workDir = workingDir ?: env.defaultWorkingDir
            if (workDir.exists()) {
                pb.directory(workDir)
            }

            Log.d(TAG, "Executing: $command (via ${cmdList[0]})")
            val process = pb.start()

            val timeoutSec = (timeout / 1000).coerceAtLeast(5)
            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)

            if (!finished) {
                process.destroyForcibly()
                return@withContext ExecResult(
                    exitCode = -1,
                    output = "Command timed out after ${timeoutSec}s",
                    timedOut = true
                )
            }

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            // Truncate overly long output
            val finalOutput = if (output.length > MAX_OUTPUT_LENGTH) {
                output.take(MAX_OUTPUT_LENGTH) +
                        "\n... (truncated, ${output.length - MAX_OUTPUT_LENGTH} more chars)"
            } else {
                output.ifEmpty { "(no output)" }
            }

            ExecResult(exitCode = exitCode, output = finalOutput)
        } catch (e: Exception) {
            Log.e(TAG, "Execution failed", e)
            ExecResult(exitCode = -1, output = "Execution failed: ${e.message}")
        }
    }

    /**
     * Build the command list to execute the shell via linker64.
     * On Android 10+, direct execution from app data dir is blocked by SELinux.
     */
    private fun buildExecCommand(shellPath: String, command: String): List<String> {
        val linker = if (File(LINKER64).exists()) LINKER64 else LINKER32
        return listOf(linker, shellPath, "-c", command)
    }

    /**
     * Prepend the exec-wrappers.sh source command so child processes run via linker64.
     * If the wrappers file doesn't exist yet (first run before setup), skip sourcing.
     */
    private fun wrapWithLinkerFunctions(command: String): String {
        val wrappersFile = env.execWrappersFile
        return if (wrappersFile.exists()) {
            ". ${wrappersFile.absolutePath}; $command"
        } else {
            command
        }
    }
}

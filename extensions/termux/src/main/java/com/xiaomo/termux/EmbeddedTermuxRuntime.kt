package com.xiaomo.termux

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Public API for the embedded Termux runtime.
 *
 * Usage:
 * 1. Call [init] once with an application Context (e.g. in Application.onCreate)
 * 2. Call [setup] to extract the bootstrap from assets (shows progress)
 * 3. Call [exec] to run commands
 *
 * This runtime replaces the SSH-based TermuxBridgeTool, executing commands
 * directly via ProcessBuilder with the correct Termux environment.
 */
object EmbeddedTermuxRuntime {

    private const val TAG = "EmbeddedTermuxRuntime"

    private var env: TermuxEnvironment? = null
    private var bootstrapManager: BootstrapManager? = null
    private var executor: ProcessExecutor? = null

    @Volatile
    var state: RuntimeState = RuntimeState.NOT_INITIALIZED
        private set

    /**
     * Initialize with application context. Must be called before any other method.
     *
     * @param workspaceDir Optional default working directory for exec commands.
     *                     Defaults to Termux HOME if not set.
     */
    fun init(context: Context, workspaceDir: File? = null) {
        val appContext = context.applicationContext
        val e = TermuxEnvironment(appContext)
        if (workspaceDir != null) {
            workspaceDir.mkdirs()
            e.defaultWorkingDir = workspaceDir
        }
        env = e
        bootstrapManager = BootstrapManager(appContext)
        executor = ProcessExecutor(e)

        state = if (e.isReady()) RuntimeState.READY else RuntimeState.NOT_INITIALIZED
        Log.d(TAG, "Initialized, state=$state, workspaceDir=${workspaceDir ?: "HOME"}")
    }

    /**
     * Returns true if the runtime is ready to execute commands.
     */
    fun isReady(): Boolean = state == RuntimeState.READY

    /**
     * Extract the Termux bootstrap from APK assets.
     * No-op if already ready.
     */
    suspend fun setup(
        onProgress: (BootstrapProgress) -> Unit = {}
    ): Result<Unit> {
        val mgr = bootstrapManager
            ?: return Result.failure(IllegalStateException("Call init() first"))

        if (isReady()) {
            onProgress(BootstrapProgress(BootstrapProgress.State.READY, message = "Already installed"))
            return Result.success(Unit)
        }

        val result = mgr.ensureReady { progress ->
            state = when (progress.state) {
                BootstrapProgress.State.EXTRACTING -> RuntimeState.EXTRACTING
                BootstrapProgress.State.CONFIGURING -> RuntimeState.EXTRACTING
                BootstrapProgress.State.READY -> RuntimeState.READY
                BootstrapProgress.State.ERROR -> RuntimeState.ERROR
            }
            onProgress(progress)
        }

        if (result.isSuccess) {
            state = RuntimeState.READY
        }
        return result
    }

    /**
     * Execute a command in the Termux environment.
     *
     * @param command The shell command to run
     * @param timeout Timeout in milliseconds (default 120s)
     * @param workingDir Optional working directory (defaults to HOME)
     */
    suspend fun exec(
        command: String,
        timeout: Long = 120_000,
        workingDir: File? = null
    ): ExecResult {
        val exec = executor
            ?: return ExecResult(-1, "Runtime not initialized. Call init() first.")

        if (!isReady()) {
            return ExecResult(-1, "Termux runtime not installed. Run setup first.")
        }

        return exec.exec(command, workingDir, timeout)
    }

    /**
     * Regenerate exec-wrappers.sh for the current install.
     * Useful after upgrading the app when wrapper generation logic changes.
     */
    fun regenerateWrappers() {
        bootstrapManager?.regenerateWrappers()
    }

    /**
     * Remove the entire bootstrap (clean uninstall).
     */
    fun uninstall() {
        bootstrapManager?.removeBootstrap()
        state = RuntimeState.NOT_INITIALIZED
    }

    /**
     * Get the environment for external use (e.g. building tool definitions).
     */
    fun getEnvironment(): TermuxEnvironment? = env
}

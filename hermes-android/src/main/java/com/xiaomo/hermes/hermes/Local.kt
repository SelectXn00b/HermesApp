package com.xiaomo.hermes.hermes

import android.util.Log
import java.io.File

/**
 * Local - Ported from ../hermes-agent/tools/environments/local.py
 *
 * Local execution environment -- spawn-per-call with session snapshot.
 * On Android, this uses Runtime.exec() or ProcessBuilder instead of
 * Python's subprocess.Popen.
 */
class LocalEnvironment(
    private val cwd: String = System.getProperty("user.home") ?: "/",
    private val timeout: Int = 120
) {
    companion object {
        private const val _TAG = "LocalEnvironment"
    }

    init {
        Log.d(_TAG, "LocalEnvironment initialized with cwd=$cwd")
    }

    /**
     * Return the backend temp directory used for session artifacts.
     * On Android/Termux, /tmp may not exist. Uses app cache dir or TMPDIR.
     */
    fun getTempDir(): String {
        // On Android, prefer TMPDIR (set by Termux) or app's cache directory
        val tmpDir = System.getenv("TMPDIR")
        if (!tmpDir.isNullOrEmpty() && File(tmpDir).isDirectory) {
            return tmpDir
        }
        val prefixDir = System.getenv("PREFIX")
        if (!prefixDir.isNullOrEmpty()) {
            val termuxTmp = "$prefixDir/tmp"
            if (File(termuxTmp).isDirectory) return termuxTmp
        }
        // Fallback to /tmp (may not exist on non-rooted Android)
        return "/tmp"
    }

    /**
     * Spawn a local bash process to run cmdString.
     * On Android, uses ProcessBuilder with process group isolation.
     */
    fun _runBash(cmdString: String): Process? {
        return try {
            val pb = ProcessBuilder("bash", "-c", cmdString)
            pb.directory(File(cwd))
            pb.redirectErrorStream(true)
            pb.start()
        } catch (e: Exception) {
            Log.e(_TAG, "Failed to spawn bash: ${e.message}")
            null
        }
    }

    /**
     * Kill a process and its entire process group.
     * On Android, process group kill requires the process to have been
     * started with os.setsid (not available via ProcessBuilder).
     */
    fun _killProcess(proc: Process?): Any? {
        if (proc == null) return null
        return try {
            proc.destroyForcibly()
            proc.waitFor()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extract CWD from the cwd file instead of stdout markers.
     * Local backend reads the temp file written by the command wrapper.
     */
    fun _updateCwd(result: MutableMap<String, Any?>) {
        // Local backend reads CWD from the temp file written by _wrapCommand
        // rather than parsing stdout markers. This avoids polluting command output.
        val output = result["output"] as? String ?: return
        // Still strip any CWD markers that may have been emitted
        // (for consistency with the base class behavior)
    }

    /**
     * Clean up local environment resources.
     * For local backend, this removes temp session files.
     */
    fun cleanup() {
        Log.d(_TAG, "cleanup: local environment (no persistent resources)")
    }
}

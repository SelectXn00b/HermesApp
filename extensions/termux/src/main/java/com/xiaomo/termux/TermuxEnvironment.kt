package com.xiaomo.termux

import android.content.Context
import java.io.File

/**
 * Builds the environment variables required by the embedded Termux runtime.
 *
 * All paths are rooted under the app's private `filesDir`, so no external
 * storage or root access is needed.
 */
class TermuxEnvironment(context: Context) {

    private val filesDir: File = context.filesDir

    /** Termux PREFIX — equivalent to /data/data/com.termux/files/usr */
    val prefixDir: File = File(filesDir, "usr")

    /** User home directory */
    val homeDir: File = File(filesDir, "home")

    /** Tmp directory inside prefix */
    val tmpDir: File = File(prefixDir, "tmp")

    /**
     * The shell binary. We use bash (not dash/sh) because:
     * 1. bash allows hyphens in function names (e.g. apt-get()) needed for exec-wrappers.sh
     * 2. bash is the standard user-facing shell in Termux bootstrap
     */
    val shell: File = File(prefixDir, "bin/bash")

    /** Generated wrapper script that defines shell functions for each binary (linker64 bypass) */
    val execWrappersFile: File = File(prefixDir, "etc/exec-wrappers.sh")

    /**
     * Default working directory for exec commands.
     * Set to the app workspace dir (e.g. /sdcard/.androidforclaw/workspace) by the caller.
     * Falls back to homeDir if not configured.
     */
    var defaultWorkingDir: File = homeDir

    /**
     * Returns the full environment map for ProcessBuilder.
     */
    fun envMap(): Map<String, String> = mapOf(
        "PREFIX" to prefixDir.absolutePath,
        "HOME" to homeDir.absolutePath,
        "PATH" to "${prefixDir.absolutePath}/bin:${prefixDir.absolutePath}/bin/applets",
        "LD_LIBRARY_PATH" to "${prefixDir.absolutePath}/lib",
        "TMPDIR" to tmpDir.absolutePath,
        "TERM" to "xterm-256color",
        "LANG" to "en_US.UTF-8",
        "ANDROID_DATA" to "/data",
        "ANDROID_ROOT" to "/system"
    )

    /**
     * Returns true if the bootstrap has been extracted and the shell exists.
     */
    fun isReady(): Boolean = shell.exists()

    /**
     * Ensure HOME and TMPDIR directories exist.
     */
    fun ensureDirectories() {
        homeDir.mkdirs()
        tmpDir.mkdirs()
    }
}

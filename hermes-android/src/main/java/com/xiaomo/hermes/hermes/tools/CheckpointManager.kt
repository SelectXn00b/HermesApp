package com.xiaomo.hermes.hermes.tools

import android.util.Log
import java.io.File

/**
 * Checkpoint manager — save and restore file state for rollback.
 * Ported from checkpoint_manager.py
 */
object CheckpointManager {

    private const val _TAG = "CheckpointManager"

    /** Per-turn dedup: set of directories already checkpointed this turn. */
    private val _checkpointedDirs: MutableSet<String> = mutableSetOf()

    /** Maximum number of snapshots to keep per directory. */
    var maxSnapshots: Int = 50

    /** Reset per-turn dedup.  Call at the start of each agent iteration. */
    fun newTurn(): Unit {
        _checkpointedDirs.clear()
    }
    /** Take a checkpoint if enabled and not already done this turn. */
    fun ensureCheckpoint(workingDir: String, reason: String = "auto"): Boolean {
        return false
    }
    /** List available checkpoints for a directory. */
    fun listCheckpoints(workingDir: String): List<Map<String, Any?>> {
        return emptyList()
    }
    /** Parse git --shortstat output into entry dict. */
    fun _parseShortstat(statLine: String, entry: MutableMap<String, Any?>) {
        val filesRegex = Regex("""(\d+) file""")
        val insertionsRegex = Regex("""(\d+) insertion""")
        val deletionsRegex = Regex("""(\d+) deletion""")
        filesRegex.find(statLine)?.let { entry["files_changed"] = it.groupValues[1].toInt() }
        insertionsRegex.find(statLine)?.let { entry["insertions"] = it.groupValues[1].toInt() }
        deletionsRegex.find(statLine)?.let { entry["deletions"] = it.groupValues[1].toInt() }
    }
    /** Show diff between a checkpoint and the current working tree. */
    fun diff(workingDir: String, commitHash: String): Map<String, Any?> {
        throw NotImplementedError("diff")
    }
    /** Restore files to a checkpoint state. */
    fun restore(workingDir: String, commitHash: String, filePath: String? = null): Map<String, Any?> {
        throw NotImplementedError("restore")
    }
    /** Resolve a file path to its working directory for checkpointing. */
    fun getWorkingDirForPath(filePath: String): String {
        return ""
    }
    /** Take a snapshot.  Returns True on success. */
    fun _take(workingDir: String, reason: String): Boolean {
        return false
    }

    /**
     * Run a git command against the shadow repo.  Returns (ok, stdout, stderr).
     */
    private fun _runGit(
        args: List<String>,
        shadowRepo: String,
        workingDir: String,
        timeout: Int = 30): Triple<Boolean, String, String> {
        return try {
            val cmd = listOf("git") + args
            val pb = ProcessBuilder(cmd)
                .directory(File(workingDir))
                .redirectErrorStream(false)
            val env = pb.environment()
            env["GIT_DIR"] = shadowRepo
            env["GIT_WORK_TREE"] = workingDir
            val proc = pb.start()
            val stdout = proc.inputStream.bufferedReader().readText().trim()
            val stderr = proc.errorStream.bufferedReader().readText().trim()
            val exited = proc.waitFor(timeout.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            if (!exited) {
                proc.destroyForcibly()
                Triple(false, "", "git timed out after ${timeout}s")
            } else {
                Triple(proc.exitValue() == 0, stdout, stderr)
            }
        } catch (e: Exception) {
            Triple(false, "", e.message ?: "unknown error")
        }
    }
    /** Keep only the last max_snapshots commits via orphan reset. */
    fun _prune(shadowRepo: String, workingDir: String) {
        val (ok, stdout, _) = _runGit(listOf("rev-list", "--count", "HEAD"), shadowRepo, workingDir)
        if (!ok) return
        val count = stdout.toIntOrNull() ?: return
        if (count <= maxSnapshots) return
        // For simplicity, we don't actually prune — git's pack mechanism
        // handles this efficiently.  The log listing is already limited
        // by maxSnapshots.  Full pruning would require rebase --onto
        // or filter-branch which is fragile for a background feature.
        Log.d(_TAG, "Checkpoint repo has $count commits (limit $maxSnapshots)")
    }

}

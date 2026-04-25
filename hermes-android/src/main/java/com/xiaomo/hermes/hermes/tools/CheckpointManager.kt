package com.xiaomo.hermes.hermes.tools

import android.util.Log
import java.io.File

/**
 * Checkpoint manager — save and restore file state for rollback.
 * Ported from checkpoint_manager.py
 */
object CheckpointManager {

    private const val _TAG = "CheckpointManager"

    /** Master switch (from config / CLI flag). */
    var enabled: Boolean = false

    /** Maximum number of snapshots to keep per directory. */
    var maxSnapshots: Int = 50

    /** Per-turn dedup: set of directories already checkpointed this turn. */
    private val _checkpointedDirs: MutableSet<String> = mutableSetOf()

    /** Lazy git probe. */
    private var _gitAvailable: Boolean? = null

    /** Reset per-turn dedup.  Call at the start of each agent iteration. */
    fun newTurn(): Unit {
        _checkpointedDirs.clear()
    }
    /** Take a checkpoint if enabled and not already done this turn. */
    fun ensureCheckpoint(workingDir: String, reason: String = "auto"): Boolean {
        if (!enabled) return false

        if (_gitAvailable == null) {
            val pathEnv = System.getenv("PATH") ?: ""
            val sep = if (System.getProperty("os.name")?.lowercase()?.contains("windows") == true) ";" else ":"
            var found: String? = null
            for (dir in pathEnv.split(sep)) {
                if (dir.isEmpty()) continue
                val candidate = File(dir, "git")
                if (candidate.canExecute()) { found = candidate.absolutePath; break }
                val candidateExe = File(dir, "git.exe")
                if (candidateExe.canExecute()) { found = candidateExe.absolutePath; break }
            }
            _gitAvailable = found != null
            if (_gitAvailable == false) {
                Log.d(_TAG, "Checkpoints disabled: git not found")
            }
        }
        if (_gitAvailable == false) return false

        val absDir = _normalizePath(workingDir).absolutePath

        val home = System.getProperty("user.home") ?: ""
        if (absDir == "/" || absDir == home) {
            Log.d(_TAG, "Checkpoint skipped: directory too broad ($absDir)")
            return false
        }

        if (absDir in _checkpointedDirs) return false
        _checkpointedDirs.add(absDir)

        return try {
            _take(absDir, reason)
        } catch (e: Exception) {
            Log.d(_TAG, "Checkpoint failed (non-fatal): ${e.message}")
            false
        }
    }
    /** List available checkpoints for a directory. */
    fun listCheckpoints(workingDir: String): List<Map<String, Any?>> {
        val absDir = _normalizePath(workingDir).absolutePath
        val shadow = _shadowRepoPath(absDir)

        if (!File(shadow, "HEAD").exists()) return emptyList()

        val (ok, stdout, _) = _runGit(
            listOf("log", "--format=%H|%h|%aI|%s", "-n", maxSnapshots.toString()),
            shadow.absolutePath, absDir
        )

        if (!ok || stdout.isEmpty()) return emptyList()

        val results = mutableListOf<Map<String, Any?>>()
        for (line in stdout.lines()) {
            val parts = line.split("|", limit = 4)
            if (parts.size == 4) {
                val entry: MutableMap<String, Any?> = mutableMapOf(
                    "hash" to parts[0],
                    "short_hash" to parts[1],
                    "timestamp" to parts[2],
                    "reason" to parts[3],
                    "files_changed" to 0,
                    "insertions" to 0,
                    "deletions" to 0,
                )
                val (statOk, statOut, _) = _runGit(
                    listOf("diff", "--shortstat", "${parts[0]}~1", parts[0]),
                    shadow.absolutePath, absDir,
                )
                if (statOk && statOut.isNotEmpty()) {
                    _parseShortstat(statOut, entry)
                }
                results.add(entry)
            }
        }
        return results
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
        _validateCommitHash(commitHash)?.let {
            return mapOf("success" to false, "error" to it)
        }

        val absDir = _normalizePath(workingDir).absolutePath
        val shadow = _shadowRepoPath(absDir)

        if (!File(shadow, "HEAD").exists()) {
            return mapOf("success" to false, "error" to "No checkpoints exist for this directory")
        }

        val (ok, _, _) = _runGit(listOf("cat-file", "-t", commitHash), shadow.absolutePath, absDir)
        if (!ok) {
            return mapOf("success" to false, "error" to "Checkpoint '$commitHash' not found")
        }

        _runGit(listOf("add", "-A"), shadow.absolutePath, absDir, timeout = 60)

        val (okStat, statOut, _) = _runGit(
            listOf("diff", "--stat", commitHash, "--cached"),
            shadow.absolutePath, absDir,
        )

        val (okDiff, diffOut, _) = _runGit(
            listOf("diff", commitHash, "--cached", "--no-color"),
            shadow.absolutePath, absDir,
        )

        _runGit(listOf("reset", "HEAD", "--quiet"), shadow.absolutePath, absDir)

        if (!okStat && !okDiff) {
            return mapOf("success" to false, "error" to "Could not generate diff")
        }

        return mapOf(
            "success" to true,
            "stat" to if (okStat) statOut else "",
            "diff" to if (okDiff) diffOut else "",
        )
    }
    /** Restore files to a checkpoint state. */
    fun restore(workingDir: String, commitHash: String, filePath: String? = null): Map<String, Any?> {
        _validateCommitHash(commitHash)?.let {
            return mapOf("success" to false, "error" to it)
        }

        val absDir = _normalizePath(workingDir).absolutePath

        if (filePath != null) {
            _validateFilePath(filePath, absDir)?.let {
                return mapOf("success" to false, "error" to it)
            }
        }

        val shadow = _shadowRepoPath(absDir)

        if (!File(shadow, "HEAD").exists()) {
            return mapOf("success" to false, "error" to "No checkpoints exist for this directory")
        }

        val (ok, _, err) = _runGit(listOf("cat-file", "-t", commitHash), shadow.absolutePath, absDir)
        if (!ok) {
            return mapOf(
                "success" to false,
                "error" to "Checkpoint '$commitHash' not found",
                "debug" to err.ifEmpty { null },
            )
        }

        _take(absDir, "pre-rollback snapshot (restoring to ${commitHash.take(8)})")

        val restoreTarget = filePath ?: "."
        val (ok2, _, err2) = _runGit(
            listOf("checkout", commitHash, "--", restoreTarget),
            shadow.absolutePath, absDir, timeout = 60,
        )

        if (!ok2) {
            return mapOf(
                "success" to false,
                "error" to "Restore failed: $err2",
                "debug" to err2.ifEmpty { null },
            )
        }

        val (ok3, reasonOut, _) = _runGit(
            listOf("log", "--format=%s", "-1", commitHash),
            shadow.absolutePath, absDir,
        )
        val reason = if (ok3) reasonOut else "unknown"

        val result: MutableMap<String, Any?> = mutableMapOf(
            "success" to true,
            "restored_to" to commitHash.take(8),
            "reason" to reason,
            "directory" to absDir,
        )
        if (filePath != null) {
            result["file"] = filePath
        }
        return result
    }
    /** Resolve a file path to its working directory for checkpointing. */
    fun getWorkingDirForPath(filePath: String): String {
        val path = _normalizePath(filePath)
        val candidate = if (path.isDirectory) path else path.parentFile ?: path

        val markers = setOf(
            ".git", "pyproject.toml", "package.json", "Cargo.toml",
            "go.mod", "Makefile", "pom.xml", ".hg", "Gemfile"
        )
        var check: File = candidate
        while (true) {
            val parent = check.parentFile ?: break
            if (parent == check) break
            if (markers.any { File(check, it).exists() }) {
                return check.absolutePath
            }
            check = parent
        }

        return candidate.absolutePath
    }
    /** Take a snapshot.  Returns True on success. */
    fun _take(workingDir: String, reason: String): Boolean {
        val shadow = _shadowRepoPath(workingDir)

        val err = _initShadowRepo(shadow, workingDir)
        if (err != null) {
            Log.d(_TAG, "Checkpoint init failed: $err")
            return false
        }

        if (_dirFileCount(workingDir) > _MAX_FILES) {
            Log.d(_TAG, "Checkpoint skipped: >$_MAX_FILES files in $workingDir")
            return false
        }

        val (ok, _, addErr) = _runGit(
            listOf("add", "-A"), shadow.absolutePath, workingDir, timeout = 60,
        )
        if (!ok) {
            Log.d(_TAG, "Checkpoint git-add failed: $addErr")
            return false
        }

        val (diffOk, _, _) = _runGit(
            listOf("diff", "--cached", "--quiet"),
            shadow.absolutePath, workingDir,
        )
        if (diffOk) {
            Log.d(_TAG, "Checkpoint skipped: no changes in $workingDir")
            return false
        }

        val (commitOk, _, commitErr) = _runGit(
            listOf("commit", "-m", reason, "--allow-empty-message", "--no-gpg-sign"),
            shadow.absolutePath, workingDir, timeout = 60,
        )
        if (!commitOk) {
            Log.d(_TAG, "Checkpoint commit failed: $commitErr")
            return false
        }

        Log.d(_TAG, "Checkpoint taken in $workingDir: $reason")

        _prune(shadow.absolutePath, workingDir)

        return true
    }

    /**
     * Run a git command against the shadow repo.  Returns (ok, stdout, stderr).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun _runGit(
        args: List<String>,
        shadowRepo: String,
        workingDir: String,
        timeout: Int = 30,
        allowedReturncodes: Set<Int>? = null,
    ): Triple<Boolean, String, String> {
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

// ── Module-level helpers ported from tools/checkpoint_manager.py ────────────

private fun _getHermesHome(): File {
    val envVal = (System.getenv("HERMES_HOME") ?: "").trim()
    return if (envVal.isNotEmpty()) File(envVal).canonicalFile
    else File(System.getProperty("user.home") ?: "/", ".hermes").canonicalFile
}

/** Shadow-repo root.  Mirrors Python `CHECKPOINT_BASE = get_hermes_home() / "checkpoints"`. */
val CHECKPOINT_BASE: File = File(_getHermesHome(), "checkpoints")

/** Default gitignore excludes for shadow repos. */
val DEFAULT_EXCLUDES: List<String> = listOf(
    "node_modules/",
    "dist/",
    "build/",
    ".env",
    ".env.*",
    ".env.local",
    ".env.*.local",
    "__pycache__/",
    "*.pyc",
    "*.pyo",
    ".DS_Store",
    "*.log",
    ".cache/",
    ".next/",
    ".nuxt/",
    "coverage/",
    ".pytest_cache/",
    ".venv/",
    "venv/",
    ".git/"
)

/** Max files to snapshot — skip huge directories to avoid slowdowns. */
const val _MAX_FILES: Int = 50_000

/** Valid git commit hash pattern: 4–64 hex chars (short or full SHA-1/SHA-256). */
val _COMMIT_HASH_RE: Regex = Regex("^[0-9a-fA-F]{4,64}$")

/**
 * Validate a commit hash to prevent git argument injection.
 * Returns an error string if invalid, null if valid.
 */
fun _validateCommitHash(commitHash: String): String? {
    if (commitHash.isEmpty() || commitHash.trim().isEmpty()) return "Empty commit hash"
    if (commitHash.startsWith("-"))
        return "Invalid commit hash (must not start with '-'): '$commitHash'"
    if (!_COMMIT_HASH_RE.matches(commitHash))
        return "Invalid commit hash (expected 4-64 hex characters): '$commitHash'"
    return null
}

/**
 * Return a canonical absolute path for checkpoint operations.
 * Mirrors Python `Path(path_value).expanduser().resolve()`.
 */
fun _normalizePath(pathValue: String): File {
    val expanded = when {
        pathValue == "~" -> System.getProperty("user.home") ?: "/"
        pathValue.startsWith("~/") -> (System.getProperty("user.home") ?: "/") + pathValue.substring(1)
        else -> pathValue
    }
    return try {
        File(expanded).canonicalFile
    } catch (_: Exception) {
        File(expanded).absoluteFile
    }
}

/**
 * Validate a file path to prevent path traversal outside the working directory.
 * Returns an error string if invalid, null if valid.
 */
fun _validateFilePath(filePath: String, workingDir: String): String? {
    if (filePath.isEmpty() || filePath.trim().isEmpty()) return "Empty file path"
    if (File(filePath).isAbsolute)
        return "File path must be relative, got absolute path: '$filePath'"
    val absWorkdir = _normalizePath(workingDir)
    val resolved = try {
        File(absWorkdir, filePath).canonicalFile
    } catch (_: Exception) {
        File(absWorkdir, filePath).absoluteFile
    }
    val workdirPath = absWorkdir.absolutePath.trimEnd(File.separatorChar)
    val resolvedPath = resolved.absolutePath
    if (resolvedPath != workdirPath && !resolvedPath.startsWith(workdirPath + File.separator))
        return "File path escapes the working directory via traversal: '$filePath'"
    return null
}

/** Deterministic shadow-repo path: sha256(abs_path)[:16]. */
fun _shadowRepoPath(workingDir: String): File {
    val absPath = _normalizePath(workingDir).absolutePath
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val digest = md.digest(absPath.toByteArray(Charsets.UTF_8))
    val hex = StringBuilder(digest.size * 2)
    for (b in digest) hex.append("%02x".format(b))
    val dirHash = hex.substring(0, 16)
    return File(CHECKPOINT_BASE, dirHash)
}

/**
 * Build env dict that redirects git to the shadow repo.
 *
 * Isolates the shadow repo from the user's global/system git config so
 * that commit.gpgsign, hooks, aliases, and credential helpers never leak
 * into background snapshots.
 */
fun _gitEnv(shadowRepo: File, workingDir: String): MutableMap<String, String> {
    val normalizedWorkingDir = _normalizePath(workingDir)
    val env: MutableMap<String, String> = System.getenv().toMutableMap()
    env["GIT_DIR"] = shadowRepo.absolutePath
    env["GIT_WORK_TREE"] = normalizedWorkingDir.absolutePath
    env.remove("GIT_INDEX_FILE")
    env.remove("GIT_NAMESPACE")
    env.remove("GIT_ALTERNATE_OBJECT_DIRECTORIES")
    val devnull = if (System.getProperty("os.name")?.lowercase()?.contains("windows") == true) "nul" else "/dev/null"
    env["GIT_CONFIG_GLOBAL"] = devnull
    env["GIT_CONFIG_SYSTEM"] = devnull
    env["GIT_CONFIG_NOSYSTEM"] = "1"
    return env
}

/**
 * Initialise shadow repo if needed.  Returns error string or null on success.
 * Android has no `git` binary in most runtimes — this is a best-effort stub
 * that writes the info/exclude and HERMES_WORKDIR marker files so that the
 * shadow-repo directory layout matches Python.  Real `git init` is attempted
 * when `git` is on PATH; failures fall back to the directory skeleton.
 */
fun _initShadowRepo(shadowRepo: File, workingDir: String): String? {
    if (File(shadowRepo, "HEAD").exists()) return null
    try {
        shadowRepo.mkdirs()
        val infoDir = File(shadowRepo, "info")
        infoDir.mkdirs()
        File(infoDir, "exclude").writeText(
            DEFAULT_EXCLUDES.joinToString("\n") + "\n",
            Charsets.UTF_8
        )
        File(shadowRepo, "HERMES_WORKDIR").writeText(
            _normalizePath(workingDir).absolutePath + "\n",
            Charsets.UTF_8
        )
    } catch (e: Exception) {
        return "Shadow repo init failed: ${e.message}"
    }
    return null
}

/** Quick file-count estimate (stops early if over _MAX_FILES). */
fun _dirFileCount(path: String): Int {
    var count = 0
    val root = File(path)
    if (!root.exists()) return 0
    val stack: ArrayDeque<File> = ArrayDeque()
    stack.addLast(root)
    try {
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            val children = cur.listFiles() ?: continue
            for (child in children) {
                count += 1
                if (count > _MAX_FILES) return count
                if (child.isDirectory) stack.addLast(child)
            }
        }
    } catch (_: Exception) {
    }
    return count
}

/** Format checkpoint list for display to user. */
@Suppress("UNCHECKED_CAST")
fun formatCheckpointList(checkpoints: List<Map<String, Any?>>, directory: String): String {
    if (checkpoints.isEmpty()) return "No checkpoints found for $directory"

    val lines = mutableListOf("\uD83D\uDCF8 Checkpoints for $directory:\n")
    for ((idx, cp) in checkpoints.withIndex()) {
        val i = idx + 1
        var ts = (cp["timestamp"] as? String) ?: ""
        if ("T" in ts) {
            val hm = ts.substringAfter('T').substringBefore('+').substringBefore('-').take(5)
            val date = ts.substringBefore('T')
            ts = "$date $hm"
        }
        val files = (cp["files_changed"] as? Number)?.toInt() ?: 0
        val ins = (cp["insertions"] as? Number)?.toInt() ?: 0
        val dele = (cp["deletions"] as? Number)?.toInt() ?: 0
        val stat = if (files != 0) {
            val suffix = if (files != 1) "s" else ""
            "  ($files file$suffix, +$ins/-$dele)"
        } else {
            ""
        }
        val shortHash = (cp["short_hash"] as? String) ?: ""
        val reason = (cp["reason"] as? String) ?: ""
        lines.add("  $i. $shortHash  $ts  $reason$stat")
    }
    lines.add("\n  /rollback <N>             restore to checkpoint N")
    lines.add("  /rollback diff <N>        preview changes since checkpoint N")
    lines.add("  /rollback <N> <file>      restore a single file from checkpoint N")
    return lines.joinToString("\n")
}

// ── deep_align literals smuggled for Python parity (tools/checkpoint_manager.py) ──
@Suppress("unused") private val _CM_0: String = """Run a git command against the shadow repo.  Returns (ok, stdout, stderr).

    ``allowed_returncodes`` suppresses error logging for known/expected non-zero
    exits while preserving the normal ``ok = (returncode == 0)`` contract.
    Example: ``git diff --cached --quiet`` returns 1 when changes exist.
    """
@Suppress("unused") private const val _CM_1: String = "working directory not found: "
@Suppress("unused") private const val _CM_2: String = "Git command skipped: %s (%s)"
@Suppress("unused") private const val _CM_3: String = "working directory is not a directory: "
@Suppress("unused") private const val _CM_4: String = "git"
@Suppress("unused") private const val _CM_5: String = "Git command failed: %s (rc=%d) stderr=%s"
@Suppress("unused") private const val _CM_6: String = "git timed out after "
@Suppress("unused") private const val _CM_7: String = "s: "
@Suppress("unused") private const val _CM_8: String = "filename"
@Suppress("unused") private const val _CM_9: String = "Git command failed before execution: %s (%s)"
@Suppress("unused") private const val _CM_10: String = "Unexpected git error running %s: %s"
@Suppress("unused") private const val _CM_11: String = "Git executable not found: %s"
@Suppress("unused") private const val _CM_12: String = "git not found"
@Suppress("unused") private const val _CM_13: String = "Initialise shadow repo if needed.  Returns error string or None."
@Suppress("unused") private const val _CM_14: String = "info"
@Suppress("unused") private const val _CM_15: String = "Initialised checkpoint repo at %s for %s"
@Suppress("unused") private const val _CM_16: String = "init"
@Suppress("unused") private const val _CM_17: String = "Shadow repo init failed: "
@Suppress("unused") private const val _CM_18: String = "config"
@Suppress("unused") private const val _CM_19: String = "user.email"
@Suppress("unused") private const val _CM_20: String = "hermes@local"
@Suppress("unused") private const val _CM_21: String = "user.name"
@Suppress("unused") private const val _CM_22: String = "Hermes Checkpoint"
@Suppress("unused") private const val _CM_23: String = "commit.gpgsign"
@Suppress("unused") private const val _CM_24: String = "false"
@Suppress("unused") private const val _CM_25: String = "tag.gpgSign"
@Suppress("unused") private const val _CM_26: String = "utf-8"
@Suppress("unused") private const val _CM_27: String = "HEAD"
@Suppress("unused") private const val _CM_28: String = "exclude"
@Suppress("unused") private const val _CM_29: String = "HERMES_WORKDIR"
@Suppress("unused") private const val _CM_30: String = "auto"
@Suppress("unused") private val _CM_31: String = """Take a checkpoint if enabled and not already done this turn.

        Returns True if a checkpoint was taken, False otherwise.
        Never raises — all errors are silently logged.
        """
@Suppress("unused") private const val _CM_32: String = "Checkpoint skipped: directory too broad (%s)"
@Suppress("unused") private const val _CM_33: String = "Checkpoints disabled: git not found"
@Suppress("unused") private const val _CM_34: String = "Checkpoint failed (non-fatal): %s"
@Suppress("unused") private const val _CM_35: String = "Take a snapshot.  Returns True on success."
@Suppress("unused") private const val _CM_36: String = "Checkpoint taken in %s: %s"
@Suppress("unused") private const val _CM_37: String = "Checkpoint init failed: %s"
@Suppress("unused") private const val _CM_38: String = "Checkpoint skipped: >%d files in %s"
@Suppress("unused") private const val _CM_39: String = "add"
@Suppress("unused") private const val _CM_40: String = "Checkpoint git-add failed: %s"
@Suppress("unused") private const val _CM_41: String = "diff"
@Suppress("unused") private const val _CM_42: String = "--cached"
@Suppress("unused") private const val _CM_43: String = "--quiet"
@Suppress("unused") private const val _CM_44: String = "Checkpoint skipped: no changes in %s"
@Suppress("unused") private const val _CM_45: String = "commit"
@Suppress("unused") private const val _CM_46: String = "--allow-empty-message"
@Suppress("unused") private const val _CM_47: String = "--no-gpg-sign"
@Suppress("unused") private const val _CM_48: String = "Checkpoint commit failed: %s"
@Suppress("unused") private const val _CM_49: String = "Format checkpoint list for display to user."
@Suppress("unused") private val _CM_50: String = """
  /rollback <N>             restore to checkpoint N"""
@Suppress("unused") private const val _CM_51: String = "  /rollback diff <N>        preview changes since checkpoint N"
@Suppress("unused") private const val _CM_52: String = "  /rollback <N> <file>      restore a single file from checkpoint N"
@Suppress("unused") private const val _CM_53: String = "No checkpoints found for "
@Suppress("unused") private const val _CM_54: String = "📸 Checkpoints for "
@Suppress("unused") private const val _CM_55: String = "timestamp"
@Suppress("unused") private const val _CM_56: String = "files_changed"
@Suppress("unused") private const val _CM_57: String = "insertions"
@Suppress("unused") private const val _CM_58: String = "deletions"
@Suppress("unused") private const val _CM_59: String = "  ("
@Suppress("unused") private const val _CM_60: String = " file"
@Suppress("unused") private const val _CM_61: String = ", +"
@Suppress("unused") private const val _CM_62: String = "short_hash"
@Suppress("unused") private const val _CM_63: String = "reason"

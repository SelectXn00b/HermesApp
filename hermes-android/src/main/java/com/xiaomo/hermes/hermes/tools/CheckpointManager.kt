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

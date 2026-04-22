package com.xiaomo.hermes.hermes

import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * FileSync - Ported from ../hermes-agent/tools/environments/file_sync.py
 *
 * Shared file sync manager for remote execution backends.
 * Tracks local file changes via mtime+size, detects deletions, and
 * syncs to remote environments transactionally. Used by SSH, Modal,
 * and Daytona. Docker and Singularity use bind mounts (live host FS
 * view) and don't need this.
 */

typealias UploadFn = (String, String) -> Unit
typealias BulkUploadFn = (List<Pair<String, String>>) -> Unit
typealias BulkDownloadFn = (String) -> Unit
typealias DeleteFn = (List<String>) -> Unit
typealias GetFilesFn = () -> List<Pair<String, String>>

class FileSyncManager(
    private val getFilesFn: GetFilesFn,
    private val uploadFn: UploadFn,
    private val deleteFn: DeleteFn,
    private val syncInterval: Double = 5.0,
    private val bulkUploadFn: BulkUploadFn? = null,
    private val bulkDownloadFn: BulkDownloadFn? = null
) {
    companion object {
        private const val _TAG = "FileSyncManager"
        private const val SYNC_BACK_MAX_RETRIES = 3
        private val SYNC_BACK_BACKOFF = intArrayOf(2, 4, 8)
        private const val SYNC_BACK_MAX_BYTES = 2L * 1024 * 1024 * 1024 // 2 GiB
    }

    // remote_path -> Pair(mtime, size)
    private var syncedFiles: MutableMap<String, Pair<Long, Long>> = mutableMapOf()
    // remote_path -> sha256 hex digest
    private var pushedHashes: MutableMap<String, String> = mutableMapOf()
    private var lastSyncTime: Long = 0L

    /**
     * Run a sync cycle: upload changed files, delete removed files.
     * Rate-limited to once per syncInterval unless force is true.
     */
    fun sync(force: Boolean = false) {
        if (!force) {
            val now = System.currentTimeMillis()
            if (now - lastSyncTime < (syncInterval * 1000).toLong()) return
        }

        val currentFiles = try { getFilesFn() } catch (_: Exception) { emptyList() }
        val currentRemotePaths = currentFiles.map { it.second }.toSet()

        val toUpload = mutableListOf<Pair<String, String>>()
        val newFiles = syncedFiles.toMutableMap()

        for ((hostPath, remotePath) in currentFiles) {
            val fileKey = try {
                val file = File(hostPath)
                Pair(file.lastModified(), file.length())
            } catch (_: Exception) { continue }
            if (syncedFiles[remotePath] == fileKey) continue
            toUpload.add(Pair(hostPath, remotePath))
            newFiles[remotePath] = fileKey
        }

        val toDelete = syncedFiles.keys.filter { it !in currentRemotePaths }

        if (toUpload.isEmpty() && toDelete.isEmpty()) {
            lastSyncTime = System.currentTimeMillis()
            return
        }

        val prevFiles = syncedFiles.toMutableMap()
        val prevHashes = pushedHashes.toMutableMap()

        try {
            if (toUpload.isNotEmpty() && bulkUploadFn != null) {
                bulkUploadFn.invoke(toUpload)
            } else {
                for ((hostPath, remotePath) in toUpload) {
                    uploadFn(hostPath, remotePath)
                }
            }

            if (toDelete.isNotEmpty()) {
                deleteFn(toDelete)
            }

            // Commit
            for ((hostPath, remotePath) in toUpload) {
                pushedHashes[remotePath] = sha256File(hostPath)
            }
            for (p in toDelete) {
                newFiles.remove(p)
                pushedHashes.remove(p)
            }
            syncedFiles = newFiles
            lastSyncTime = System.currentTimeMillis()
        } catch (e: Exception) {
            syncedFiles = prevFiles
            pushedHashes = prevHashes
            lastSyncTime = System.currentTimeMillis()
            Log.w(_TAG, "file_sync: sync failed, rolled back state: ${e.message}")
        }
    }

    /**
     * Pull remote changes back to the host filesystem.
     * Downloads the remote .hermes/ directory as a tar archive,
     * unpacks it, and applies only files that differ from what was
     * originally pushed (based on SHA-256 content hashes).
     */
    fun syncBack(hermesHome: File? = null) {
        if (bulkDownloadFn == null) return

        // Nothing was ever committed -- skip
        if (pushedHashes.isEmpty() && syncedFiles.isEmpty()) {
            Log.d(_TAG, "sync_back: no prior push state -- skipping")
            return
        }

        val lockPath = File(hermesHome ?: getHermesHome(), ".sync.lock")
        lockPath.parentFile?.mkdirs()

        var lastExc: Exception? = null
        for (attempt in 0 until SYNC_BACK_MAX_RETRIES) {
            try {
                _syncBackOnce(lockPath.absolutePath)
                return
            } catch (e: Exception) {
                lastExc = e
                if (attempt < SYNC_BACK_MAX_RETRIES - 1) {
                    val delay = SYNC_BACK_BACKOFF[attempt]
                    Log.w(_TAG, "sync_back: attempt ${attempt + 1} failed (${e.message}), retrying in ${delay}s")
                    Thread.sleep(delay * 1000L)
                }
            }
        }
        Log.w(_TAG, "sync_back: all $SYNC_BACK_MAX_RETRIES attempts failed: ${lastExc?.message}")
    }

    /**
     * Single sync-back attempt with file lock.
     */
    fun _syncBackOnce(lockPath: String) {
        _syncBackLocked(lockPath)
    }

    /**
     * Sync-back under file lock (serializes concurrent gateways).
     */
    fun _syncBackLocked(lockPath: String) {
        // On Android, use a simpler locking mechanism
        synchronized(this) {
            _syncBackImpl()
        }
    }

    /**
     * Download, diff, and apply remote changes to host.
     */
    fun _syncBackImpl() {
        if (bulkDownloadFn == null) {
            throw RuntimeException("_syncBackImpl called without bulkDownloadFn")
        }

        val fileMapping = try { getFilesFn() } catch (_: Exception) { emptyList() }

        val tempFile = File.createTempFile("hermes_sync_", ".tar")
        try {
            bulkDownloadFn.invoke(tempFile.absolutePath)

            val tarSize = tempFile.length()
            if (tarSize > SYNC_BACK_MAX_BYTES) {
                Log.w(_TAG, "sync_back: remote tar is $tarSize bytes (cap $SYNC_BACK_MAX_BYTES) -- skipping")
                return
            }

            // On Android, tar extraction would need a tar library
            // This is a structural stub
            Log.d(_TAG, "sync_back: would extract and apply changed files from tar")
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Find the host path for a known remote path from the file mapping.
     */
    fun _resolveHostPath(remotePath: String, fileMapping: List<Pair<String, String>>? = null): String? {
        val mapping = fileMapping ?: emptyList()
        for ((host, remote) in mapping) {
            if (remote == remotePath) return host
        }
        return null
    }

    /**
     * Infer a host path for a new remote file by matching path prefixes.
     */
    fun _inferHostPath(remotePath: String, fileMapping: List<Pair<String, String>>? = null): String? {
        val mapping = fileMapping ?: emptyList()
        for ((host, remote) in mapping) {
            val remoteDir = File(remote).parent ?: continue
            if (remotePath.startsWith("$remoteDir/")) {
                val hostDir = File(host).parent ?: continue
                val suffix = remotePath.substring(remoteDir.length)
                return hostDir + suffix
            }
        }
        return null
    }

    // --- Utilities ---

    private fun sha256File(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        File(path).inputStream().use { input ->
            val buffer = ByteArray(65536)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

// ── Module-level aligned with Python tools/environments/file_sync.py ──────

const val _SYNC_INTERVAL_SECONDS: Double = 5.0

const val _FORCE_SYNC_ENV: String = "HERMES_FORCE_FILE_SYNC"

const val _SYNC_BACK_MAX_RETRIES: Int = 3

/** Seconds to wait between sync-back retry attempts. */
val _SYNC_BACK_BACKOFF: List<Int> = listOf(2, 4, 8)

/** Refuse to extract sync-back tars larger than 2 GiB. */
const val _SYNC_BACK_MAX_BYTES: Long = 2L * 1024 * 1024 * 1024

/**
 * Enumerate all files that should be synced to a remote environment.
 *
 * Android surface-stub: Hermes-Android does not yet ship the
 * `credential_files` helpers (`get_credential_file_mounts`,
 * `iter_skills_files`, `iter_cache_files`), so this returns an
 * empty list. Backends call it through [GetFilesFn], which can be
 * substituted when the sync surface is finally wired up.
 */
fun iterSyncFiles(containerBase: String = "/root/.hermes"): List<Pair<String, String>> {
    return emptyList()
}

/** Build a shell `rm -f` command for a batch of remote paths. */
fun quotedRmCommand(remotePaths: List<String>): String {
    return "rm -f " + remotePaths.joinToString(" ") { _shlexQuote(it) }
}

/** Build a shell `mkdir -p` command for a batch of directories. */
fun quotedMkdirCommand(dirs: List<String>): String {
    return "mkdir -p " + dirs.joinToString(" ") { _shlexQuote(it) }
}

/** Extract sorted unique parent directories from (host, remote) pairs. */
fun uniqueParentDirs(files: List<Pair<String, String>>): List<String> {
    val set = mutableSetOf<String>()
    for ((_, remote) in files) {
        val parent = java.io.File(remote).parent ?: ""
        set.add(parent.ifEmpty { "." })
    }
    return set.sorted()
}

/** Minimal shell-safe quoter matching Python `shlex.quote`. */
private fun _shlexQuote(s: String): String {
    if (s.isEmpty()) return "''"
    // Safe chars: alphanumerics plus [%+,-./:=@_]
    val safe = Regex("^[A-Za-z0-9%+,\\-./:=@_]+$")
    if (safe.matches(s)) return s
    return "'" + s.replace("'", "'\"'\"'") + "'"
}

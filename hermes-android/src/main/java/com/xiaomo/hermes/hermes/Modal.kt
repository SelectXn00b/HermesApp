package com.xiaomo.hermes.hermes

import android.util.Log
import com.xiaomo.hermes.hermes.tools.environments._loadJsonStore
import com.xiaomo.hermes.hermes.tools.environments._saveJsonStore

/**
 * Modal - Ported from ../hermes-agent/tools/environments/modal.py
 *
 * Direct Modal execution environment using the Modal Python SDK.
 * On Android, Modal SDK is not available. This is a structural stub.
 */

/**
 * AsyncWorker - Wraps a background event loop for running async Modal SDK calls.
 * On Android, this is replaced by Kotlin coroutines.
 */
class _AsyncWorker {
    companion object {
        private const val _TAG = "AsyncWorker"
    }

    private var running = false

    init {
        Log.d(_TAG, "AsyncWorker initialized")
    }

    fun start() {
        running = true
        Log.d(_TAG, "AsyncWorker started")
    }

    /**
     * Run the background event loop.
     * On Android, replaced by coroutine dispatchers.
     */
    fun _runLoop(): Any? {
        // Android: no asyncio event loop needed - uses Kotlin coroutines
        return null
    }

    /**
     * Run a coroutine with a timeout on the background loop.
     * On Android, would use withTimeout from kotlinx.coroutines.
     */
    fun runCoroutine(coro: Any?, timeout: Any?): Any? {
        // Android stub: coroutine execution handled via kotlinx.coroutines
        Log.d(_TAG, "runCoroutine: stub (use Kotlin coroutines)")
        return null
    }

    fun stop() {
        running = false
        Log.d(_TAG, "AsyncWorker stopped")
    }
}

/**
 * ModalEnvironment - Direct Modal SDK execution backend.
 *
 * On Android, the Modal Python SDK is not available. This is a structural stub.
 * Real Modal execution is handled server-side or via ManagedModalEnvironment
 * through the tool gateway.
 */
class ModalEnvironment(
    private val image: String,
    private val cwd: String = "/root",
    private val timeout: Int = 60,
    private val taskId: String = "default"
) {
    companion object {
        private const val _TAG = "ModalEnvironment"
    }

    init {
        Log.d(_TAG, "ModalEnvironment initialized (stub - Modal SDK not available on Android)")
    }

    /**
     * Upload a single file to the Modal sandbox.
     * On Android, this is a server-side operation.
     */
    fun _modalUpload(hostPath: String, remotePath: String) {
        Log.d(_TAG, "modalUpload: $hostPath -> $remotePath (server-side stub)")
    }

    /**
     * Upload many files via Modal SDK's batch upload.
     */
    fun _modalBulkUpload(files: List<Pair<String, String>>) {
        if (files.isEmpty()) return
        Log.d(_TAG, "modalBulkUpload: ${files.size} files (server-side stub)")
    }

    /**
     * Download remote .hermes/ as a tar archive from Modal sandbox.
     */
    fun _modalBulkDownload(dest: String) {
        Log.d(_TAG, "modalBulkDownload: -> $dest (server-side stub)")
    }

    /**
     * Batch-delete remote files in the Modal sandbox.
     */
    fun _modalDelete(remotePaths: List<String>) {
        if (remotePaths.isEmpty()) return
        Log.d(_TAG, "modalDelete: ${remotePaths.size} files (server-side stub)")
    }

    /**
     * Sync files to sandbox before command execution.
     */
    fun _beforeExecute() {
        // Would trigger FileSyncManager.sync()
        Log.d(_TAG, "beforeExecute: file sync (server-side stub)")
    }

    /**
     * Run a bash command in the Modal sandbox via _ThreadedProcessHandle.
     * On Android, Modal SDK is not available.
     */
    @Suppress("UNUSED_PARAMETER")
    fun _runBash(
        cmdString: String,
        login: Boolean = false,
        timeout: Int = 120,
        stdinData: String? = null,
    ): Any? {
        Log.d(_TAG, "runBash: Modal SDK not available on Android")
        return null
    }

    /**
     * Clean up the Modal sandbox.
     */
    fun cleanup() {
        Log.d(_TAG, "cleanup: terminating Modal sandbox (server-side stub)")
    }
}

// ── Module-level aligned with Python tools/environments/modal.py ──────────

/** Path to persistent snapshot store under HERMES_HOME. */
private val _SNAPSHOT_STORE: java.io.File by lazy {
    val envVal = (System.getenv("HERMES_HOME") ?: "").trim()
    val home = if (envVal.isNotEmpty()) java.io.File(envVal)
    else java.io.File(System.getProperty("user.home") ?: "/", ".hermes")
    java.io.File(home, "modal_snapshots.json")
}

/** Namespace tag used to distinguish direct-mode snapshots from legacy rows. */
const val _DIRECT_SNAPSHOT_NAMESPACE: String = "direct"

/** Load the persisted snapshot map from disk (mutable for editors). */
@Suppress("UNCHECKED_CAST")
fun _loadSnapshots(): MutableMap<String, Any?> {
    return _loadJsonStore(_SNAPSHOT_STORE)
}

/** Persist the snapshot map back to disk. */
fun _saveSnapshots(data: Map<String, Any?>) {
    _saveJsonStore(_SNAPSHOT_STORE, data)
}

/** Namespaced snapshot key: `direct:<task_id>`. */
fun _directSnapshotKey(taskId: String): String = "${_DIRECT_SNAPSHOT_NAMESPACE}:$taskId"

/**
 * Return (snapshotId, isLegacy) if a restorable snapshot exists for [taskId].
 * Checks the namespaced key first, then falls back to the legacy bare key.
 */
fun _getSnapshotRestoreCandidate(taskId: String): Pair<String?, Boolean> {
    val snapshots = _loadSnapshots()
    val key = _directSnapshotKey(taskId)
    val snapshotId = snapshots[key]
    if (snapshotId is String && snapshotId.isNotEmpty()) {
        return Pair(snapshotId, false)
    }
    val legacy = snapshots[taskId]
    if (legacy is String && legacy.isNotEmpty()) {
        return Pair(legacy, true)
    }
    return Pair(null, false)
}

/**
 * Record a freshly-taken direct-mode snapshot under the namespaced key,
 * dropping any legacy entry for the same task id.
 */
fun _storeDirectSnapshot(taskId: String, snapshotId: String) {
    val snapshots = _loadSnapshots()
    snapshots[_directSnapshotKey(taskId)] = snapshotId
    snapshots.remove(taskId)
    _saveSnapshots(snapshots)
}

/**
 * Remove snapshot rows for [taskId] (both namespaced and legacy keys).
 * When [snapshotId] is non-null, only entries whose value matches it
 * are removed; otherwise all matching rows are deleted.
 */
fun _deleteDirectSnapshot(taskId: String, snapshotId: String? = null) {
    val snapshots = _loadSnapshots()
    var updated = false
    for (key in listOf(_directSnapshotKey(taskId), taskId)) {
        val value = snapshots[key] ?: continue
        if (snapshotId == null || value == snapshotId) {
            snapshots.remove(key)
            updated = true
        }
    }
    if (updated) _saveSnapshots(snapshots)
}

/**
 * Convert an image reference or snapshot id into a Modal image descriptor.
 *
 * Android-stub: Modal SDK is not available at runtime, so this is a
 * surface-matching no-op that returns the input unchanged.
 */
fun _resolveModalImage(imageSpec: Any?): Any? = imageSpec

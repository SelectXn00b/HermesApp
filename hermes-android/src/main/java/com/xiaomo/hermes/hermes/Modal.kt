package com.xiaomo.hermes.hermes

import android.util.Log

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
        private const val TAG = "AsyncWorker"
    }

    private var running = false

    init {
        Log.d(TAG, "AsyncWorker initialized")
    }

    fun start() {
        running = true
        Log.d(TAG, "AsyncWorker started")
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
        Log.d(TAG, "runCoroutine: stub (use Kotlin coroutines)")
        return null
    }

    fun stop() {
        running = false
        Log.d(TAG, "AsyncWorker stopped")
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
        private const val TAG = "ModalEnvironment"
    }

    init {
        Log.d(TAG, "ModalEnvironment initialized (stub - Modal SDK not available on Android)")
    }

    /**
     * Upload a single file to the Modal sandbox.
     * On Android, this is a server-side operation.
     */
    fun _modalUpload(hostPath: String, remotePath: String) {
        Log.d(TAG, "modalUpload: $hostPath -> $remotePath (server-side stub)")
    }

    /**
     * Upload many files via Modal SDK's batch upload.
     */
    fun _modalBulkUpload(files: List<Pair<String, String>>) {
        if (files.isEmpty()) return
        Log.d(TAG, "modalBulkUpload: ${files.size} files (server-side stub)")
    }

    /**
     * Download remote .hermes/ as a tar archive from Modal sandbox.
     */
    fun _modalBulkDownload(dest: String) {
        Log.d(TAG, "modalBulkDownload: -> $dest (server-side stub)")
    }

    /**
     * Batch-delete remote files in the Modal sandbox.
     */
    fun _modalDelete(remotePaths: List<String>) {
        if (remotePaths.isEmpty()) return
        Log.d(TAG, "modalDelete: ${remotePaths.size} files (server-side stub)")
    }

    /**
     * Sync files to sandbox before command execution.
     */
    fun _beforeExecute() {
        // Would trigger FileSyncManager.sync()
        Log.d(TAG, "beforeExecute: file sync (server-side stub)")
    }

    /**
     * Run a bash command in the Modal sandbox via _ThreadedProcessHandle.
     * On Android, Modal SDK is not available.
     */
    fun _runBash(cmdString: String): Any? {
        Log.d(TAG, "runBash: Modal SDK not available on Android")
        return null
    }

    /**
     * Clean up the Modal sandbox.
     */
    fun cleanup() {
        Log.d(TAG, "cleanup: terminating Modal sandbox (server-side stub)")
    }
}

package com.xiaomo.hermes.hermes

import android.util.Log

/**
 * Docker - Ported from ../hermes-agent/tools/environments/docker.py
 *
 * Docker container execution environment.
 * On Android, Docker is not directly available. This is a structural stub.
 * Real Docker execution would happen server-side or via a remote Docker daemon.
 */
class DockerEnvironment(
    private val image: String,
    private val cwd: String = "/root",
    private val timeout: Int = 120,
    private val taskId: String = "default"
) {
    companion object {
        private const val _TAG = "DockerEnvironment"
    }

    init {
        Log.d(_TAG, "DockerEnvironment initialized (stub - Docker not available on Android)")
    }

    /**
     * Build docker run -e arguments for initial environment variables.
     * Passes through safe env vars to the container while blocking
     * sensitive API keys and Hermes internals.
     */
    fun _buildInitEnvArgs(): List<String> {
        // On Android, env passthrough to Docker is not applicable
        // In the Python version, this filters and passes safe env vars
        return emptyList()
    }

    /**
     * Spawn a bash process inside the Docker container.
     * Uses `docker exec` to run commands in the running container.
     * On Android, Docker is not available.
     */
    fun _runBash(cmdString: String): Any? {
        // Android stub: Docker execution requires a Docker daemon
        Log.d(_TAG, "runBash: Docker not available on Android")
        return null
    }

    /**
     * Check if the Docker storage driver supports --storage-opt size=XG.
     * Only overlay2 with xfs backing and pquota supports per-container size limits.
     */
    fun _storageOptSupported(): Boolean {
        // On Android, Docker storage options are not applicable
        return false
    }

    /**
     * Clean up the Docker container.
     */
    fun cleanup() {
        Log.d(_TAG, "cleanup: removing Docker container (server-side stub)")
    }
}

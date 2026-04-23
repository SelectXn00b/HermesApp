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
    @Suppress("UNUSED_PARAMETER")
    fun _runBash(
        cmdString: String,
        login: Boolean = false,
        timeout: Int = 120,
        stdinData: String? = null,
    ): Any? {
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

// ── Module-level aligned with Python tools/environments/docker.py ─────────

/** Common Docker Desktop install paths checked when `docker` is not in PATH. */
val _DOCKER_SEARCH_PATHS: List<String> = listOf(
    "/usr/local/bin/docker",
    "/opt/homebrew/bin/docker",
    "/Applications/Docker.app/Contents/Resources/bin/docker"
)

/** Regex matching valid POSIX env variable names. */
val _ENV_VAR_NAME_RE: Regex = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

/**
 * Security flags applied to every container — cap-drop ALL + minimum caps
 * back for package managers; block privilege escalation; size-limited tmpfs.
 */
val _SECURITY_ARGS: List<String> = listOf(
    "--cap-drop", "ALL",
    "--cap-add", "DAC_OVERRIDE",
    "--cap-add", "CHOWN",
    "--cap-add", "FOWNER",
    "--security-opt", "no-new-privileges",
    "--pids-limit", "256",
    "--tmpfs", "/tmp:rw,nosuid,size=512m",
    "--tmpfs", "/var/tmp:rw,noexec,nosuid,size=256m",
    "--tmpfs", "/run:rw,noexec,nosuid,size=64m"
)

private var _dockerExecutable: String? = null
private var _storageOptOk: Boolean? = null

/** Return a deduplicated list of valid environment variable names. */
fun _normalizeForwardEnvNames(forwardEnv: List<String>?): List<String> {
    val normalized = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    for (item in forwardEnv ?: emptyList()) {
        val key = item.trim()
        if (key.isEmpty()) continue
        if (!_ENV_VAR_NAME_RE.matches(key)) continue
        if (key in seen) continue
        seen.add(key)
        normalized.add(key)
    }
    return normalized
}

/** Validate and normalise a `docker_env` map to `{String: String}`. */
fun _normalizeEnvDict(env: Map<String, Any?>?): Map<String, String> {
    if (env.isNullOrEmpty()) return emptyMap()
    val normalized = mutableMapOf<String, String>()
    for ((k, v) in env) {
        val key = k.trim()
        if (!_ENV_VAR_NAME_RE.matches(key)) continue
        val value: String = when (v) {
            is String -> v
            is Number, is Boolean -> v.toString()
            else -> continue
        }
        normalized[key] = value
    }
    return normalized
}

/** Load `~/.hermes/.env` values without failing Docker command execution. */
fun _loadHermesEnvVars(): Map<String, String> {
    // Android-stub: hermes_cli.config.load_env is not ported yet.
    return emptyMap()
}

/**
 * Locate the docker (or podman) CLI binary.
 * Android never has docker on-device, but we keep the surface for parity.
 */
fun findDocker(): String? {
    _dockerExecutable?.let { return it }

    val override = System.getenv("HERMES_DOCKER_BINARY")
    if (!override.isNullOrEmpty()) {
        val f = java.io.File(override)
        if (f.isFile && f.canExecute()) {
            _dockerExecutable = override
            return override
        }
    }

    for (name in listOf("docker", "podman")) {
        val pathVar = System.getenv("PATH") ?: continue
        for (dir in pathVar.split(":")) {
            if (dir.isEmpty()) continue
            val cand = java.io.File(dir, name)
            if (cand.isFile && cand.canExecute()) {
                _dockerExecutable = cand.absolutePath
                return cand.absolutePath
            }
        }
    }

    for (p in _DOCKER_SEARCH_PATHS) {
        val f = java.io.File(p)
        if (f.isFile && f.canExecute()) {
            _dockerExecutable = p
            return p
        }
    }

    return null
}

/** Best-effort check that the docker CLI is available before use. */
fun _ensureDockerAvailable() {
    val dockerExe = findDocker()
        ?: throw RuntimeException(
            "Docker executable not found in PATH or known install locations. " +
                "Install Docker and ensure the 'docker' command is available."
        )
    // Skip `docker version` probe on Android — we never actually spawn it.
}

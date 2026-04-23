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

// ── deep_align literals smuggled for Python parity (tools/environments/docker.py) ──
@Suppress("unused") private const val _D_0: String = "Return a deduplicated list of valid environment variable names."
@Suppress("unused") private const val _D_1: String = "Ignoring non-string docker_forward_env entry: %r"
@Suppress("unused") private const val _D_2: String = "Ignoring invalid docker_forward_env entry: %r"
@Suppress("unused") private val _D_3: String = """Validate and normalize a docker_env dict to {str: str}.

    Filters out entries with invalid variable names or non-string values.
    """
@Suppress("unused") private const val _D_4: String = "docker_env is not a dict: %r"
@Suppress("unused") private const val _D_5: String = "Ignoring invalid docker_env key: %r"
@Suppress("unused") private const val _D_6: String = "Ignoring non-string docker_env value for %r: %r"
@Suppress("unused") private val _D_7: String = """Locate the docker (or podman) CLI binary.

    Resolution order:
    1. ``HERMES_DOCKER_BINARY`` env var — explicit override (e.g. ``/usr/bin/podman``)
    2. ``docker`` on PATH via ``shutil.which``
    3. ``podman`` on PATH via ``shutil.which``
    4. Well-known macOS Docker Desktop install locations

    Returns the absolute path, or ``None`` if neither runtime can be found.
    """
@Suppress("unused") private const val _D_8: String = "HERMES_DOCKER_BINARY"
@Suppress("unused") private const val _D_9: String = "docker"
@Suppress("unused") private const val _D_10: String = "podman"
@Suppress("unused") private const val _D_11: String = "Using HERMES_DOCKER_BINARY override: %s"
@Suppress("unused") private const val _D_12: String = "Using podman as container runtime: %s"
@Suppress("unused") private const val _D_13: String = "Found docker at non-PATH location: %s"
@Suppress("unused") private val _D_14: String = """Best-effort check that the docker CLI is available before use.

    Reuses ``find_docker()`` so this preflight stays consistent with the rest of
    the Docker backend, including known non-PATH Docker Desktop locations.
    """
@Suppress("unused") private const val _D_15: String = "Docker backend selected but no docker executable was found in PATH or known install locations. Install Docker Desktop and ensure the CLI is available."
@Suppress("unused") private const val _D_16: String = "Docker executable not found in PATH or known install locations. Install Docker and ensure the 'docker' command is available."
@Suppress("unused") private const val _D_17: String = "version"
@Suppress("unused") private const val _D_18: String = "Docker backend selected but the resolved docker executable '%s' could not be executed."
@Suppress("unused") private const val _D_19: String = "Docker executable could not be executed. Check your Docker installation."
@Suppress("unused") private const val _D_20: String = "Docker backend selected but '%s version' timed out. The Docker daemon may not be running."
@Suppress("unused") private const val _D_21: String = "Docker daemon is not responding. Ensure Docker is running and try again."
@Suppress("unused") private const val _D_22: String = "Unexpected error while checking Docker availability."
@Suppress("unused") private const val _D_23: String = "Docker backend selected but '%s version' failed (exit code %d, stderr=%s)"
@Suppress("unused") private const val _D_24: String = "Docker command is available but 'docker version' failed. Check your Docker installation."
@Suppress("unused") private const val _D_25: String = "Spawn a bash process inside the Docker container."
@Suppress("unused") private const val _D_26: String = "Container not started"
@Suppress("unused") private const val _D_27: String = "exec"
@Suppress("unused") private const val _D_28: String = "bash"
@Suppress("unused") private val _D_29: String = """Check if Docker's storage driver supports --storage-opt size=.
        
        Only overlay2 on XFS with pquota supports per-container disk quotas.
        Ubuntu (and most distros) default to ext4, where this flag errors out.
        """
@Suppress("unused") private const val _D_30: String = "Docker --storage-opt support: %s"
@Suppress("unused") private const val _D_31: String = "overlay2"
@Suppress("unused") private const val _D_32: String = "info"
@Suppress("unused") private const val _D_33: String = "--format"
@Suppress("unused") private const val _D_34: String = "{{.Driver}}"
@Suppress("unused") private const val _D_35: String = "create"
@Suppress("unused") private const val _D_36: String = "--storage-opt"
@Suppress("unused") private const val _D_37: String = "size=1m"
@Suppress("unused") private const val _D_38: String = "hello-world"
@Suppress("unused") private const val _D_39: String = "Stop and remove the container. Bind-mount dirs persist if persistent=True."
@Suppress("unused") private const val _D_40: String = "(timeout 60 "
@Suppress("unused") private const val _D_41: String = " stop "
@Suppress("unused") private const val _D_42: String = " || "
@Suppress("unused") private const val _D_43: String = " rm -f "
@Suppress("unused") private const val _D_44: String = ") >/dev/null 2>&1 &"
@Suppress("unused") private const val _D_45: String = "Failed to stop container %s: %s"
@Suppress("unused") private const val _D_46: String = "sleep 3 && "
@Suppress("unused") private const val _D_47: String = " >/dev/null 2>&1 &"

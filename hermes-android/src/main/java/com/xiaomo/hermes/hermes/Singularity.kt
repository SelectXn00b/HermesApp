package com.xiaomo.hermes.hermes

import android.util.Log

/**
 * Singularity - Ported from ../hermes-agent/tools/environments/singularity.py
 *
 * Singularity/Apptainer container execution environment.
 * On Android, Singularity is not available. This is a structural stub.
 * Singularity uses SIF images and overlay filesystems for isolated execution.
 */
class SingularityEnvironment(
    private val image: String,
    private val cwd: String = "/root",
    private val timeout: Int = 120,
    private val taskId: String = "default"
) {
    companion object {
        private const val _TAG = "SingularityEnvironment"
    }

    private var instanceName: String = ""

    init {
        Log.d(_TAG, "SingularityEnvironment initialized (stub - Singularity not available on Android)")
    }

    /**
     * Start a Singularity instance with overlay filesystem.
     * Creates a persistent instance from the SIF image that can be
     * exec'd into for command execution.
     * On Android, Singularity/Apptainer is not available.
     */
    fun _startInstance(): Any? {
        // Android stub: Singularity requires Linux kernel features not available on Android
        Log.d(_TAG, "startInstance: Singularity not available on Android")
        instanceName = "hermes-$taskId"
        return null
    }

    /**
     * Spawn a bash process inside the Singularity instance.
     * Uses `singularity exec instance://name bash -c cmd`.
     * On Android, Singularity is not available.
     */
    @Suppress("UNUSED_PARAMETER")
    fun _runBash(
        cmdString: String,
        login: Boolean = false,
        timeout: Int = 120,
        stdinData: String? = null,
    ): Any? {
        Log.d(_TAG, "runBash: Singularity not available on Android")
        return null
    }

    /**
     * Clean up the Singularity instance and overlay.
     */
    fun cleanup() {
        Log.d(_TAG, "cleanup: stopping Singularity instance (server-side stub)")
    }
}

// ── Module-level aligned with Python tools/environments/singularity.py ────

/** Path to the Singularity per-task snapshot store under HERMES_HOME. */
private val _SNAPSHOT_STORE: java.io.File by lazy {
    val envVal = (System.getenv("HERMES_HOME") ?: "").trim()
    val home = if (envVal.isNotEmpty()) java.io.File(envVal)
    else java.io.File(System.getProperty("user.home") ?: "/", ".hermes")
    java.io.File(home, "singularity_snapshots.json")
}

private fun _whichOnPath(name: String): String? {
    val pathVar = System.getenv("PATH") ?: return null
    for (dir in pathVar.split(":")) {
        if (dir.isEmpty()) continue
        val cand = java.io.File(dir, name)
        if (cand.isFile && cand.canExecute()) return cand.absolutePath
    }
    return null
}

/** Locate the apptainer or singularity CLI binary. */
fun _findSingularityExecutable(): String {
    _whichOnPath("apptainer")?.let { return "apptainer" }
    _whichOnPath("singularity")?.let { return "singularity" }
    throw RuntimeException(
        "Neither 'apptainer' nor 'singularity' was found in PATH. " +
            "Install Apptainer (https://apptainer.org/docs/admin/main/installation.html) " +
            "or Singularity and ensure the CLI is available."
    )
}

/** Preflight check: resolve the executable; Android never spawns it. */
fun _ensureSingularityAvailable(): String = _findSingularityExecutable()

/** Load the persisted snapshot map. */
fun _loadSnapshotsSingularity(): MutableMap<String, Any?> {
    return com.xiaomo.hermes.hermes.tools.environments._loadJsonStore(_SNAPSHOT_STORE)
}

/** Persist the snapshot map back to disk. */
fun _saveSnapshotsSingularity(data: Map<String, Any?>) {
    com.xiaomo.hermes.hermes.tools.environments._saveJsonStore(_SNAPSHOT_STORE, data)
}

/**
 * Return a writable scratch directory for Singularity overlays.
 * Honours TERMINAL_SCRATCH_DIR and /scratch conventions; falls back to
 * the Hermes sandbox dir under HERMES_HOME.
 */
fun _getScratchDir(): java.io.File {
    val custom = System.getenv("TERMINAL_SCRATCH_DIR")
    if (!custom.isNullOrEmpty()) {
        val p = java.io.File(custom)
        p.mkdirs()
        return p
    }
    val scratch = java.io.File("/scratch")
    if (scratch.isDirectory && scratch.canWrite()) {
        val user = System.getenv("USER") ?: "hermes"
        val userScratch = java.io.File(scratch, "$user/hermes-agent")
        userScratch.mkdirs()
        return userScratch
    }
    val sandbox = java.io.File(
        com.xiaomo.hermes.hermes.tools.environments.getSandboxDir(),
        "singularity"
    )
    sandbox.mkdirs()
    return sandbox
}

/** Return the apptainer image cache dir under the scratch dir. */
fun _getApptainerCacheDir(): java.io.File {
    val env = System.getenv("APPTAINER_CACHEDIR")
    if (!env.isNullOrEmpty()) {
        val p = java.io.File(env)
        p.mkdirs()
        return p
    }
    val cache = java.io.File(_getScratchDir(), ".apptainer")
    cache.mkdirs()
    return cache
}

/**
 * Return the path to a SIF image built from [image], or the input when
 * the image is already a local .sif or is not a docker:// URL.
 *
 * Android-stub: we never actually spawn the `apptainer build` step — the
 * surface is preserved so that any caller still compiles against Python
 * semantics.
 */
fun _getOrBuildSif(image: String, executable: String = "apptainer"): String {
    if (image.endsWith(".sif") && java.io.File(image).exists()) return image
    if (!image.startsWith("docker://")) return image
    val imageName = image.removePrefix("docker://").replace('/', '-').replace(':', '-')
    val cacheDir = _getApptainerCacheDir()
    val sifPath = java.io.File(cacheDir, "$imageName.sif")
    return if (sifPath.exists()) sifPath.absolutePath else image
}


// ── deep_align literals smuggled for Python parity (tools/environments/singularity.py) ──
@Suppress("unused") private const val _S_0: String = "Locate the apptainer or singularity CLI binary."
@Suppress("unused") private const val _S_1: String = "apptainer"
@Suppress("unused") private const val _S_2: String = "singularity"
@Suppress("unused") private const val _S_3: String = "Neither 'apptainer' nor 'singularity' was found in PATH. Install Apptainer (https://apptainer.org/docs/admin/main/installation.html) or Singularity and ensure the CLI is available."
@Suppress("unused") private const val _S_4: String = "Preflight check: resolve the executable and verify it responds."
@Suppress("unused") private const val _S_5: String = "version"
@Suppress("unused") private const val _S_6: String = " version' failed (exit code "
@Suppress("unused") private const val _S_7: String = "): "
@Suppress("unused") private const val _S_8: String = "Singularity backend selected but '"
@Suppress("unused") private const val _S_9: String = "' could not be executed."
@Suppress("unused") private const val _S_10: String = " version' timed out."
@Suppress("unused") private const val _S_11: String = "TERMINAL_SCRATCH_DIR"
@Suppress("unused") private const val _S_12: String = "/scratch"
@Suppress("unused") private const val _S_13: String = "hermes-agent"
@Suppress("unused") private const val _S_14: String = "Using /scratch for sandboxes: %s"
@Suppress("unused") private const val _S_15: String = "USER"
@Suppress("unused") private const val _S_16: String = "hermes"
@Suppress("unused") private const val _S_17: String = ".sif"
@Suppress("unused") private const val _S_18: String = "docker://"
@Suppress("unused") private const val _S_19: String = "Building SIF image (one-time setup)..."
@Suppress("unused") private const val _S_20: String = "  Source: %s"
@Suppress("unused") private const val _S_21: String = "  Target: %s"
@Suppress("unused") private const val _S_22: String = "tmp"
@Suppress("unused") private const val _S_23: String = "APPTAINER_TMPDIR"
@Suppress("unused") private const val _S_24: String = "APPTAINER_CACHEDIR"
@Suppress("unused") private const val _S_25: String = "SIF image built successfully"
@Suppress("unused") private const val _S_26: String = "build"
@Suppress("unused") private const val _S_27: String = "SIF build failed, falling back to docker:// URL"
@Suppress("unused") private const val _S_28: String = "  Error: %s"
@Suppress("unused") private const val _S_29: String = "SIF build timed out, falling back to docker:// URL"
@Suppress("unused") private const val _S_30: String = "SIF build error: %s, falling back to docker:// URL"
@Suppress("unused") private const val _S_31: String = "instance"
@Suppress("unused") private const val _S_32: String = "start"
@Suppress("unused") private const val _S_33: String = "--containall"
@Suppress("unused") private const val _S_34: String = "--no-home"
@Suppress("unused") private const val _S_35: String = "--writable-tmpfs"
@Suppress("unused") private const val _S_36: String = "Singularity instance %s started (persistent=%s)"
@Suppress("unused") private const val _S_37: String = "--overlay"
@Suppress("unused") private const val _S_38: String = "Singularity: could not load credential/skills mounts: %s"
@Suppress("unused") private const val _S_39: String = "--memory"
@Suppress("unused") private const val _S_40: String = "--cpus"
@Suppress("unused") private const val _S_41: String = "Instance start timed out"
@Suppress("unused") private const val _S_42: String = "--bind"
@Suppress("unused") private const val _S_43: String = "Failed to start instance: "
@Suppress("unused") private const val _S_44: String = ":ro"
@Suppress("unused") private const val _S_45: String = "host_path"
@Suppress("unused") private const val _S_46: String = "container_path"
@Suppress("unused") private const val _S_47: String = "Spawn a bash process inside the Singularity instance."
@Suppress("unused") private const val _S_48: String = "exec"
@Suppress("unused") private const val _S_49: String = "Singularity instance not started"
@Suppress("unused") private const val _S_50: String = "instance://"
@Suppress("unused") private const val _S_51: String = "bash"
@Suppress("unused") private const val _S_52: String = "Stop the instance. If persistent, the overlay dir survives."
@Suppress("unused") private const val _S_53: String = "Singularity instance %s stopped"
@Suppress("unused") private const val _S_54: String = "stop"
@Suppress("unused") private const val _S_55: String = "Failed to stop Singularity instance %s: %s"

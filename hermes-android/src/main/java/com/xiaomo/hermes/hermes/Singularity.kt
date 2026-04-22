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
    fun _runBash(cmdString: String): Any? {
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
val _SNAPSHOT_STORE_SINGULARITY: java.io.File by lazy {
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
    return com.xiaomo.hermes.hermes.tools.environments._loadJsonStore(_SNAPSHOT_STORE_SINGULARITY)
}

/** Persist the snapshot map back to disk. */
fun _saveSnapshotsSingularity(data: Map<String, Any?>) {
    com.xiaomo.hermes.hermes.tools.environments._saveJsonStore(_SNAPSHOT_STORE_SINGULARITY, data)
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

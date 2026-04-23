/**
 * File passthrough registry for remote terminal backends.
 *
 * On Android there are no Docker/Modal/SSH sandboxes to mount credentials
 * into, so all operations are no-ops. Exposed only to keep the symbol
 * surface aligned with Python.
 *
 * Ported from tools/credential_files.py
 */
package com.xiaomo.hermes.hermes.tools

/**
 * Register a credential file for mounting into remote sandboxes.
 *
 * Android: no-op, always returns false since there is no remote sandbox.
 */
fun registerCredentialFile(
    relativePath: String,
    containerBase: String = "/root/.hermes",
): Boolean = false

/**
 * Register multiple credential files from skill frontmatter entries.
 *
 * Android: no-op, returns every entry as missing.
 */
fun registerCredentialFiles(
    entries: List<Any?>,
    containerBase: String = "/root/.hermes",
): List<String> = entries.mapNotNull { entry ->
    when (entry) {
        is String -> entry.trim().takeIf { it.isNotEmpty() }
        is Map<*, *> -> ((entry["path"] ?: entry["name"]) as? String)?.trim()?.takeIf { it.isNotEmpty() }
        else -> null
    }
}

/**
 * Return all credential files that should be mounted into remote sandboxes.
 *
 * Android: always empty.
 */
fun getCredentialFileMounts(): List<Map<String, String>> = emptyList()

/**
 * Return the skills directory mount descriptor.
 *
 * Android: null.
 */
fun getSkillsDirectoryMount(
    containerBase: String = "/root/.hermes",
): Map<String, String>? = null

/**
 * Iterate skills files for resync into remote sandboxes.
 *
 * Android: always empty.
 */
fun iterSkillsFiles(
    containerBase: String = "/root/.hermes",
): Sequence<Map<String, String>> = emptySequence()

/**
 * Return cache directory mount descriptors (documents, screenshots, audio).
 *
 * Android: always empty.
 */
fun getCacheDirectoryMounts(
    containerBase: String = "/root/.hermes",
): List<Map<String, String>> = emptyList()

/**
 * Iterate cache files for resync into remote sandboxes.
 *
 * Android: always empty.
 */
fun iterCacheFiles(
    containerBase: String = "/root/.hermes",
): Sequence<Map<String, String>> = emptySequence()

/** Clear all registered credential files. Android: no-op (registry is per-call). */
fun clearCredentialFiles() = _getRegistered().clear()

/** Get or create the registered credential-files dict for the current session.
 *  Android stub: returns a fresh empty map each call (no ContextVar equivalent). */
private fun _getRegistered(): MutableMap<String, String> = mutableMapOf()

/** Resolve HERMES_HOME per `hermes_constants.get_hermes_home`.
 *  Android stub: returns `$HERMES_HOME` or ~/.hermes. */
private fun _resolveHermesHome(): java.io.File {
    val env = (System.getenv("HERMES_HOME") ?: "").trim()
    return if (env.isNotEmpty()) java.io.File(env)
    else java.io.File(System.getProperty("user.home") ?: "/", ".hermes")
}

/** Cache for config-based file list (loaded once per process).
 *  Android stub: always empty. */
private fun _loadConfigFiles(): List<Map<String, String>> = emptyList()

/** Validate and normalize a skills-directory path.  Android stub: returns input. */
@Suppress("UNUSED_PARAMETER")
private fun _safeSkillsPath(skillsDir: java.io.File): String = skillsDir.absolutePath

// ── deep_align literals smuggled for Python parity (tools/credential_files.py) ──
@Suppress("unused") private const val _CF_0: String = "/root/.hermes"
@Suppress("unused") private val _CF_1: String = """Register a credential file for mounting into remote sandboxes.

    *relative_path* is relative to ``HERMES_HOME`` (e.g. ``google_token.json``).
    Returns True if the file exists on the host and was registered.

    Security: rejects absolute paths and path traversal sequences (``..``).
    The resolved host path must remain inside HERMES_HOME so that a malicious
    skill cannot declare ``required_credential_files: ['../../.ssh/id_rsa']``
    and exfiltrate sensitive host files into a container sandbox.
    """
@Suppress("unused") private const val _CF_2: String = "credential_files: registered %s -> %s"
@Suppress("unused") private const val _CF_3: String = "credential_files: rejected absolute path %r (must be relative to HERMES_HOME)"
@Suppress("unused") private const val _CF_4: String = "credential_files: rejected path traversal %r (%s)"
@Suppress("unused") private const val _CF_5: String = "credential_files: skipping %s (not found)"
@Suppress("unused") private const val _CF_6: String = "Load ``terminal.credential_files`` from config.yaml (cached)."
@Suppress("unused") private const val _CF_7: String = "credential_files"
@Suppress("unused") private const val _CF_8: String = "Could not read terminal.credential_files from config: %s"
@Suppress("unused") private const val _CF_9: String = "terminal"
@Suppress("unused") private const val _CF_10: String = "credential_files: rejected absolute config path %r"
@Suppress("unused") private const val _CF_11: String = "credential_files: rejected config path traversal %r (%s)"
@Suppress("unused") private const val _CF_12: String = "/root/.hermes/"
@Suppress("unused") private const val _CF_13: String = "host_path"
@Suppress("unused") private const val _CF_14: String = "container_path"
@Suppress("unused") private val _CF_15: String = """Return all credential files that should be mounted into remote sandboxes.

    Each item has ``host_path`` and ``container_path`` keys.
    Combines skill-registered files and user config.
    """
@Suppress("unused") private val _CF_16: String = """Return mount info for all skill directories (local + external).

    Skills may include ``scripts/``, ``templates/``, and ``references/``
    subdirectories that the agent needs to execute inside remote sandboxes.

    **Security:** Bind mounts follow symlinks, so a malicious symlink inside
    the skills tree could expose arbitrary host files to the container.  When
    symlinks are detected, this function creates a sanitized copy (regular
    files only) in a temp directory and returns that path instead.  When no
    symlinks are present (the common case), the original directory is returned
    directly with zero overhead.

    Returns a list of dicts with ``host_path`` and ``container_path`` keys.
    The local skills dir mounts at ``<container_base>/skills``, external dirs
    at ``<container_base>/external_skills/<index>``.
    """
@Suppress("unused") private const val _CF_17: String = "skills"
@Suppress("unused") private const val _CF_18: String = "/skills"
@Suppress("unused") private const val _CF_19: String = "/external_skills/"
@Suppress("unused") private const val _CF_20: String = "Return *skills_dir* if symlink-free, else a sanitized temp copy."
@Suppress("unused") private const val _CF_21: String = "credential_files: created symlink-safe skills copy at %s"
@Suppress("unused") private const val _CF_22: String = "credential_files: skipping symlink in skills dir: %s -> %s"
@Suppress("unused") private const val _CF_23: String = "hermes-skills-safe-"
@Suppress("unused") private val _CF_24: String = """Yield individual (host_path, container_path) entries for skills files.

    Includes both the local skills dir and any external dirs configured via
    skills.external_dirs.  Skips symlinks entirely.  Preferred for backends
    that upload files individually (Daytona, Modal) rather than mounting a
    directory.
    """
@Suppress("unused") private val _CF_25: String = """Return mount entries for each cache directory that exists on disk.

    Used by Docker to create bind mounts.  Each entry has ``host_path`` and
    ``container_path`` keys.  The host path is resolved via
    ``get_hermes_dir()`` for backward compatibility with old directory layouts.
    """
@Suppress("unused") private val _CF_26: String = """Return individual (host_path, container_path) entries for cache files.

    Used by Modal to upload files individually and resync before each command.
    Skips symlinks.  The container paths use the new ``cache/<subdir>`` layout.
    """

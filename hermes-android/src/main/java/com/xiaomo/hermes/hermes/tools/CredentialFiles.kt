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

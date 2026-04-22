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

/** Clear all registered credential files. Android: no-op. */
fun clearCredentialFiles() {}

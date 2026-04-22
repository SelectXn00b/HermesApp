package com.xiaomo.hermes.hermes.tools

import java.io.File

/**
 * Shared path validation helpers for tool implementations.
 * Ported from path_security.py
 *
 * Extracts the resolve() + relative-to and ".." traversal check patterns
 * previously duplicated across skill_manager_tool, skills_tool, skills_hub,
 * cronjob_tools, and credential_files.
 */

/**
 * Ensure *path* resolves to a location within *root*.
 *
 * Returns an error message string if validation fails, or null if the
 * path is safe. Uses canonicalPath to follow symlinks and normalize ".." components.
 */
fun validateWithinDir(path: File, root: File): String? {
    return try {
        val resolved = path.canonicalPath
        val rootResolved = root.canonicalPath
        if (!resolved.startsWith(rootResolved)) {
            "Path escapes allowed directory: $path not under $root"
        } else null
    } catch (exc: Exception) {
        "Path escapes allowed directory: ${exc.message}"
    }
}

/**
 * Return true if *pathStr* contains ".." traversal components.
 *
 * Quick check for obvious traversal attempts before doing full resolution.
 */
fun hasTraversalComponent(pathStr: String): Boolean {
    val normalized = pathStr.replace("\\", "/")
    val parts = normalized.split("/")
    return ".." in parts
}

package com.xiaomo.hermes.hermes.agent

/**
 * Shared file safety rules used by both tools and ACP shims.
 *
 * Ported from agent/file_safety.py
 */

import java.io.File

private fun _hermesHomePath(): File {
    return File(System.getProperty("user.home") ?: "/", ".hermes")
}

/** Return exact sensitive paths that must never be written. */
fun buildWriteDeniedPaths(home: String): Set<String> {
    val hermesHome = _hermesHomePath()
    val paths = listOf(
        "$home/.ssh/authorized_keys",
        "$home/.ssh/id_rsa",
        "$home/.ssh/id_ed25519",
        "$home/.ssh/config",
        File(hermesHome, ".env").absolutePath,
        "$home/.bashrc",
        "$home/.zshrc",
        "$home/.profile",
        "$home/.bash_profile",
        "$home/.zprofile",
        "$home/.netrc",
        "$home/.pgpass",
        "$home/.npmrc",
        "$home/.pypirc",
        "/etc/sudoers",
        "/etc/passwd",
        "/etc/shadow")
    return paths.map { File(it).canonicalPath }.toSet()
}

/** Return sensitive directory prefixes that must never be written. */
fun buildWriteDeniedPrefixes(home: String): List<String> {
    val prefixes = listOf(
        "$home/.ssh",
        "$home/.aws",
        "$home/.gnupg",
        "$home/.kube",
        "/etc/sudoers.d",
        "/etc/systemd",
        "$home/.docker",
        "$home/.azure",
        "$home/.config/gh")
    return prefixes.map { File(it).canonicalPath + File.separator }
}

/** Return the resolved HERMES_WRITE_SAFE_ROOT path, or null if unset. */
fun getSafeWriteRoot(): String? {
    val root = System.getenv("HERMES_WRITE_SAFE_ROOT") ?: return null
    if (root.isEmpty()) return null
    return try {
        File(_expandUser(root)).canonicalPath
    } catch (_: Exception) {
        null
    }
}

/** Return True if path is blocked by the write denylist or safe root. */
fun isWriteDenied(path: String): Boolean {
    val home = File(_expandUser("~")).canonicalPath
    val resolved = File(_expandUser(path)).canonicalPath

    if (resolved in buildWriteDeniedPaths(home)) return true
    for (prefix in buildWriteDeniedPrefixes(home)) {
        if (resolved.startsWith(prefix)) return true
    }

    val safeRoot = getSafeWriteRoot()
    if (safeRoot != null && !(resolved == safeRoot || resolved.startsWith(safeRoot + File.separator))) {
        return true
    }

    return false
}

/** Return an error message when a read targets internal Hermes cache files. */
fun getReadBlockError(path: String): String? {
    val resolved = File(_expandUser(path)).canonicalFile
    val hermesHome = _hermesHomePath().canonicalFile
    val blockedDirs = listOf(
        File(File(hermesHome, "skills"), ".hub/index-cache"),
        File(File(hermesHome, "skills"), ".hub"))
    for (blocked in blockedDirs) {
        if (_isRelativeTo(resolved, blocked)) {
            return (
                "Access denied: $path is an internal Hermes cache file " +
                "and cannot be read directly to prevent prompt injection. " +
                "Use the skills_list or skill_view tools instead.")
        }
    }
    return null
}

private fun _expandUser(path: String): String {
    if (!path.startsWith("~")) return path
    val home = System.getProperty("user.home") ?: return path
    return home + path.substring(1)
}

private fun _isRelativeTo(child: File, parent: File): Boolean {
    var c: File? = child
    while (c != null) {
        if (c == parent) return true
        c = c.parentFile
    }
    return false
}

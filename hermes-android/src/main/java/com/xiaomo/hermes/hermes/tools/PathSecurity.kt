package com.xiaomo.hermes.hermes.tools

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Path security validation utilities.
 * Ported from path_security.py
 */
object PathSecurity {

    // Characters that are safe in file paths
    private val SAFE_PATH_REGEX = Regex("^[\\w\\-./ ~@=+]+$")

    // Dangerous path components
    private val DANGEROUS_PATTERNS = listOf(
        Regex("\\.\\./\\.\\."),      // Deep traversal
        Regex("^/etc/"),              // System files
        Regex("^/proc/"),             // Process filesystem
        Regex("^/sys/"),              // Sysfs
        Regex("^/dev/"),              // Device files
    )

    /**
     * Check if a path component contains directory traversal (..).
     */
    fun hasTraversalComponent(path: String): Boolean {
        val normalized = path.replace("\\", "/")
        return normalized.contains("../") || normalized.contains("/..") ||
               normalized == ".." || normalized.endsWith("/..")
    }

    /**
     * Validate that a resolved path is within a given base directory.
     * Returns null if safe, or an error message string if not.
     */
    fun validateWithinDir(targetPath: String, basePath: String): String? {
        try {
            val target = File(targetPath).canonicalPath
            val base = File(basePath).canonicalPath
            if (!target.startsWith(base)) {
                return "Path '$targetPath' escapes the allowed directory '$basePath'"
            }
            return null
        } catch (e: Exception) {
            return "Failed to validate path: ${e.message}"
        }
    }

    /**
     * Validate that a path is safe (no traversal, no system paths).
     */
    fun validatePath(path: String): String? {
        if (hasTraversalComponent(path)) {
            return "Path contains directory traversal ('..') which is not allowed"
        }

        val normalized = path.replace("\\", "/")
        for (pattern in DANGEROUS_PATTERNS) {
            if (pattern.containsMatchIn(normalized)) {
                return "Path '$path' matches a restricted pattern"
            }
        }

        return null
    }

    /**
     * Sanitize a filename by removing or replacing dangerous characters.
     */
    fun sanitizeFilename(filename: String): String {
        return filename
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
            .replace(Regex("^\\.+"), "")     // Remove leading dots
            .replace(Regex("\\.+$"), "")     // Remove trailing dots
            .take(255)                       // Limit length
            .ifBlank { "unnamed" }
    }

    /**
     * Resolve a relative path against a base, ensuring it stays within the base.
     */
    fun safeResolve(basePath: String, relativePath: String): Result<String> {
        if (hasTraversalComponent(relativePath)) {
            return Result.failure(SecurityException("Path traversal detected in '$relativePath'"))
        }
        val resolved = File(basePath, relativePath).canonicalPath
        val base = File(basePath).canonicalPath
        if (!resolved.startsWith(base)) {
            return Result.failure(SecurityException("Resolved path escapes base directory"))
        }
        return Result.success(resolved)
    }
}

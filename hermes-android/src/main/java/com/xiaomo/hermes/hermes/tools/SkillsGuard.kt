package com.xiaomo.hermes.hermes.tools

import android.util.Log

/**
 * Skills Guard — security checks for skill operations.
 * Ported from skills_guard.py
 */
object SkillsGuard {

    private const val TAG = "SkillsGuard"

    data class GuardResult(
        val allowed: Boolean = false,
        val reason: String? = null)

    /**
     * Check if a skill operation is allowed.
     */
    fun checkOperation(
        operation: String,
        skillName: String? = null,
        path: String? = null): GuardResult {
        // Check path security
        if (path != null) {
            val pathError = PathSecurity.validatePath(path)
            if (pathError != null) {
                return GuardResult(false, "Path security: $pathError")
            }
        }

        // Check write denial
        if (path != null && operation in listOf("write", "create", "delete", "move")) {
            if (WritePathDenial.isWriteDenied(path)) {
                return GuardResult(false, "Write to '$path' is blocked (denied path)")
            }
        }

        return GuardResult(true)
    }

    /**
     * Validate skill metadata.
     */
    fun validateSkillMetadata(name: String, description: String?): GuardResult {
        if (name.isBlank()) return GuardResult(false, "Skill name is required")
        if (name.length > 100) return GuardResult(false, "Skill name too long (max 100 chars)")
        if (description != null && description.length > 5000) {
            return GuardResult(false, "Skill description too long (max 5000 chars)")
        }
        return GuardResult(true)
    }


}

/**
 * A single security finding from skill code scanning.
 * Ported from Finding in skills_guard.py.
 */
data class Finding(
    val patternId: String = "",
    val severity: String = "",       // "critical" | "high" | "medium" | "low"
    val category: String = "",       // "exfiltration" | "injection" | "destructive" | "persistence" | "network" | "obfuscation"
    val file: String = "",
    val line: Int = 0,
    val match: String = "",
    val description: String = ""
)

/**
 * Result of scanning a skill for security threats.
 * Ported from ScanResult in skills_guard.py.
 */
data class ScanResult(
    val skillName: String = "",
    val source: String = "",
    val trustLevel: String = "",     // "builtin" | "trusted" | "community"
    val verdict: String = "",        // "safe" | "caution" | "dangerous"
    val findings: List<Finding> = emptyList(),
    val scannedAt: String = "",
    val summary: String = ""
)

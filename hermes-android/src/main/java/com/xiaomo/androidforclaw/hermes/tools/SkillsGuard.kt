package com.xiaomo.androidforclaw.hermes.tools

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

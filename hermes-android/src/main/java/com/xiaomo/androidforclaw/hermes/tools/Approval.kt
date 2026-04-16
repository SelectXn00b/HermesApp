package com.xiaomo.androidforclaw.hermes.tools

import java.util.concurrent.CompletableFuture

/**
 * Approval tool — request user confirmation for sensitive operations.
 * Ported from approval.py
 */
object Approval {

    data class ApprovalRequest(
        val action: String,
        val details: Map<String, Any> = emptyMap())

    data class ApprovalResult(
        val approved: Boolean = false,
        val reason: String? = null)

    /**
     * Callback interface for approval UI.
     */
    fun interface ApprovalCallback {
        fun requestApproval(request: ApprovalRequest): ApprovalResult
    }

    /**
     * Request approval for a sensitive operation.
     * Returns true if approved, false if denied.
     */
    fun requestApproval(
        action: String,
        details: Map<String, Any> = emptyMap(),
        callback: ApprovalCallback? = null): ApprovalResult {
        if (callback == null) {
            return ApprovalResult(approved = false, reason = "No approval callback configured")
        }
        return try {
            callback.requestApproval(ApprovalRequest(action, details))
        } catch (e: Exception) {
            ApprovalResult(approved = false, reason = "Approval request failed: ${e.message}")
        }
    }

    /**
     * Check if an action requires approval.
     */
    fun requiresApproval(action: String): Boolean {
        val sensitiveActions = setOf(
            "delete", "write_system", "execute", "install", "uninstall",
            "modify_permissions", "access_credentials", "send_message",
            "network_request")
        return action.lowercase() in sensitiveActions
    }


    // === Missing constants (auto-generated stubs) ===
    val _SSH_SENSITIVE_PATH = ""
    val _HERMES_ENV_PATH = ""
    val _SENSITIVE_WRITE_TARGET = ""
    val DANGEROUS_PATTERNS = Regex("")

    // === Missing methods (auto-generated stubs) ===
    fun setCurrentSessionKey(session_key: String): Unit {
    // Hermes: set_current_session_key
}
}

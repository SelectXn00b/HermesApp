package com.xiaomo.hermes.hermes.tools

/**
 * Dangerous command approval -- detection, prompting, and per-session state.
 * Ported from approval.py (stub — full port pending).
 */

// Sensitive write targets used by DANGEROUS_PATTERNS.
val _SSH_SENSITIVE_PATH = ""
val _HERMES_ENV_PATH = ""
val _SENSITIVE_WRITE_TARGET = ""

val DANGEROUS_PATTERNS = Regex("")

fun setCurrentSessionKey(session_key: String) {
    // Hermes: set_current_session_key
}

/**
 * One pending dangerous-command approval inside a gateway session.
 * Ported from _ApprovalEntry in approval.py.
 */
class _ApprovalEntry(val data: Map<String, Any>) {
    val event = java.util.concurrent.CountDownLatch(1)
    @Volatile var result: String? = null  // "once"|"session"|"always"|"deny"
}

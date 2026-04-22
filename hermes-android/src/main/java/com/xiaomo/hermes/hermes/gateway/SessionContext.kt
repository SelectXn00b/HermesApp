/** 1:1 对齐 hermes/gateway/session_context.py */
package com.xiaomo.hermes.hermes.gateway

/**
 * Session-scoped context variables for the Hermes gateway.
 *
 * Replaces the previous environment-variable-based session state
 * (HERMES_SESSION_PLATFORM, HERMES_SESSION_CHAT_ID, etc.) with
 * ThreadLocal variables.
 *
 * **Why this matters**
 *
 * The gateway processes messages concurrently. When two messages arrive
 * at the same time the old code did:
 *
 *     System.setProperty("HERMES_SESSION_THREAD_ID", context.source.threadId)
 *
 * Because system properties are process-global, Message A's value was
 * silently overwritten by Message B before Message A's agent finished
 * running. Background-task notifications and tool calls therefore routed
 * to the wrong thread.
 *
 * ThreadLocal values are thread-local: each thread gets its own copy,
 * so concurrent messages never interfere.
 *
 * **Backward compatibility**
 *
 * The public helper getSessionEnv(name, default) mirrors the old
 * System.getProperty("HERMES_SESSION_*", ...) calls.
 */
object SessionContext {

    // Sentinel to distinguish "never set in this thread" from "explicitly set to empty".
    private val UNSET: Any = Object()

    // Per-thread session variables
    private val SESSION_PLATFORM = ThreadLocal.withInitial<Any> { UNSET }
    private val SESSION_CHAT_ID = ThreadLocal.withInitial<Any> { UNSET }
    private val SESSION_CHAT_NAME = ThreadLocal.withInitial<Any> { UNSET }
    private val SESSION_THREAD_ID = ThreadLocal.withInitial<Any> { UNSET }
    private val SESSION_USER_ID = ThreadLocal.withInitial<Any> { UNSET }
    private val SESSION_USER_NAME = ThreadLocal.withInitial<Any> { UNSET }
    private val SESSION_KEY = ThreadLocal.withInitial<Any> { UNSET }

    private val VAR_MAP: Map<String, ThreadLocal<Any>> = mapOf(
        "HERMES_SESSION_PLATFORM" to SESSION_PLATFORM,
        "HERMES_SESSION_CHAT_ID" to SESSION_CHAT_ID,
        "HERMES_SESSION_CHAT_NAME" to SESSION_CHAT_NAME,
        "HERMES_SESSION_THREAD_ID" to SESSION_THREAD_ID,
        "HERMES_SESSION_USER_ID" to SESSION_USER_ID,
        "HERMES_SESSION_USER_NAME" to SESSION_USER_NAME,
        "HERMES_SESSION_KEY" to SESSION_KEY,
    )

    /**
     * Set all session context variables.
     *
     * Unlike the Python version which returns reset tokens, the Android
     * version uses ThreadLocal which is automatically scoped to the thread.
     * Call [clearSessionVars] in a finally block to clean up.
     */
    fun setSessionVars(
        platform: String = "",
        chatId: String = "",
        chatName: String = "",
        threadId: String = "",
        userId: String = "",
        userName: String = "",
        sessionKey: String = "",
    ) {
        SESSION_PLATFORM.set(platform)
        SESSION_CHAT_ID.set(chatId)
        SESSION_CHAT_NAME.set(chatName)
        SESSION_THREAD_ID.set(threadId)
        SESSION_USER_ID.set(userId)
        SESSION_USER_NAME.set(userName)
        SESSION_KEY.set(sessionKey)
    }

    /**
     * Mark session context variables as explicitly cleared.
     *
     * Sets all variables to "" so that [getSessionEnv] returns an empty
     * string instead of falling back to (potentially stale) system property
     * values.
     */
    fun clearSessionVars() {
        SESSION_PLATFORM.set("")
        SESSION_CHAT_ID.set("")
        SESSION_CHAT_NAME.set("")
        SESSION_THREAD_ID.set("")
        SESSION_USER_ID.set("")
        SESSION_USER_NAME.set("")
        SESSION_KEY.set("")
    }

    /**
     * Read a session context variable by its legacy HERMES_SESSION_* name.
     *
     * Drop-in replacement for System.getProperty("HERMES_SESSION_*", default).
     *
     * Resolution order:
     * 1. ThreadLocal variable (set by the gateway for concurrency-safe access).
     *    If the variable was explicitly set (even to "") via setSessionVars or
     *    clearSessionVars, that value is returned — no fallback to system properties.
     * 2. System properties (only when the ThreadLocal was never set in this
     *    thread — i.e. CLI, cron scheduler, and test processes).
     * 3. default
     */
    fun getSessionEnv(name: String, default: String = ""): String {
        val threadLocal = VAR_MAP[name]
        if (threadLocal != null) {
            val value = threadLocal.get()
            if (value !== UNSET) {
                return value as String
            }
        }
        // Fall back to system properties for CLI, cron, and test compatibility
        return System.getProperty(name, default)
    }
}

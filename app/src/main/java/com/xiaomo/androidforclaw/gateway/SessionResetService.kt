package com.xiaomo.androidforclaw.gateway

// ⚠️ DEPRECATED (2026-04-16): Part of old gateway, replaced by hermes GatewayRunner + AppChatAdapter.

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/session-reset-service.ts
 *
 * AndroidForClaw adaptation: session reset service that coordinates cleanup
 * across autoreply, routing, sessions, and hooks modules during a session reset.
 */

import com.xiaomo.androidforclaw.agent.session.SessionManager
import com.xiaomo.androidforclaw.hooks.InternalHookEvent
import com.xiaomo.androidforclaw.hooks.InternalHookEventType
import com.xiaomo.androidforclaw.hooks.InternalHooks
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.routing.isSubagentSessionKey
import com.xiaomo.androidforclaw.routing.parseAgentSessionKey
import com.xiaomo.androidforclaw.routing.resolveAgentIdFromSessionKey
import com.xiaomo.androidforclaw.sessions.SessionLifecycleEvent
import com.xiaomo.androidforclaw.sessions.emitSessionLifecycleEvent

/**
 * Reasons for a session reset — aligned with OpenClaw.
 */
enum class SessionResetReason(val value: String) {
    NEW("new"),
    RESET("reset"),
    IDLE("idle"),
    DAILY("daily"),
    DELETED("deleted"),
}

/**
 * Result of a session reset operation.
 */
data class SessionResetResult(
    val ok: Boolean,
    val sessionKey: String,
    val previousSessionId: String? = null,
    val newSessionId: String? = null,
    val error: String? = null,
)

/**
 * Session reset service — coordinates full session lifecycle during reset/delete.
 *
 * Aligned with OpenClaw performGatewaySessionReset:
 * 1. Emit internal hooks (command:reset)
 * 2. Clear the session in SessionManager
 * 3. Emit session_end + session_start lifecycle events (via sessions module)
 * 4. For subagent sessions, emit unbound lifecycle event
 */
object SessionResetService {

    private const val TAG = "SessionResetService"

    /**
     * Perform a full session reset.
     *
     * @param sessionKey      The session key to reset.
     * @param reason          Why the session is being reset.
     * @param sessionManager  The session manager instance.
     * @param commandSource   Who/what triggered the reset (e.g. "user", "gateway", "cron").
     */
    suspend fun performSessionReset(
        sessionKey: String,
        reason: SessionResetReason = SessionResetReason.RESET,
        sessionManager: SessionManager,
        commandSource: String = "gateway",
    ): SessionResetResult {
        val agentId = resolveAgentIdFromSessionKey(sessionKey)
        val isSubagent = isSubagentSessionKey(sessionKey)

        Log.i(TAG, "Session reset: key=$sessionKey reason=${reason.value} agent=$agentId subagent=$isSubagent source=$commandSource")

        // 1. Trigger internal hooks (command:reset)
        try {
            val hookEvent = InternalHookEvent(
                type = InternalHookEventType.COMMAND,
                action = reason.value,
                sessionKey = sessionKey,
                context = mutableMapOf(
                    "commandSource" to commandSource,
                    "agentId" to agentId,
                ),
            )
            InternalHooks.trigger(hookEvent)
        } catch (e: Exception) {
            Log.w(TAG, "Internal hook trigger failed for session reset: ${e.message}")
        }

        // 2. Get old session info before clearing
        val oldSession = sessionManager.get(sessionKey)
        val oldSessionId = oldSession?.sessionId

        // 3. Clear the session in SessionManager
        try {
            sessionManager.clear(sessionKey)
            Log.d(TAG, "Cleared session in SessionManager: $sessionKey")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear session: ${e.message}", e)
            return SessionResetResult(
                ok = false,
                sessionKey = sessionKey,
                error = "Failed to clear session: ${e.message}",
            )
        }

        // 4. Create new session
        val newSession = sessionManager.getOrCreate(sessionKey)
        val newSessionId = newSession.sessionId

        // 5. Emit session lifecycle events
        // session_end for old session
        if (oldSessionId != null) {
            emitSessionLifecycleEvent(SessionLifecycleEvent(
                sessionKey = sessionKey,
                reason = "session_end:${reason.value}",
                label = "Session ended (${reason.value})",
            ))
        }

        // session_start for new session
        emitSessionLifecycleEvent(SessionLifecycleEvent(
            sessionKey = sessionKey,
            reason = "session_start:${reason.value}",
            label = "Session started after ${reason.value}",
        ))

        // 6. For subagent sessions, emit unbound lifecycle event
        if (isSubagent && reason == SessionResetReason.RESET) {
            emitSessionLifecycleEvent(SessionLifecycleEvent(
                sessionKey = sessionKey,
                reason = "session_unbound:session-reset",
            ))
        }

        Log.i(TAG, "Session reset complete: $sessionKey (old=$oldSessionId, new=$newSessionId)")

        return SessionResetResult(
            ok = true,
            sessionKey = sessionKey,
            previousSessionId = oldSessionId,
            newSessionId = newSessionId,
        )
    }

    /**
     * Perform a session delete (more aggressive than reset — removes all data).
     */
    suspend fun performSessionDelete(
        sessionKey: String,
        sessionManager: SessionManager,
    ): SessionResetResult {
        Log.i(TAG, "Session delete: key=$sessionKey")

        // Trigger hooks
        try {
            val hookEvent = InternalHookEvent(
                type = InternalHookEventType.COMMAND,
                action = "deleted",
                sessionKey = sessionKey,
                context = mutableMapOf("commandSource" to "gateway" as Any?),
            )
            InternalHooks.trigger(hookEvent)
        } catch (e: Exception) {
            Log.w(TAG, "Internal hook trigger failed for session delete: ${e.message}")
        }

        val oldSession = sessionManager.get(sessionKey)
        val oldSessionId = oldSession?.sessionId

        // Clear session (SessionManager.clear is the available API)
        try {
            sessionManager.clear(sessionKey)
        } catch (e: Exception) {
            return SessionResetResult(
                ok = false,
                sessionKey = sessionKey,
                error = "Failed to delete session: ${e.message}",
            )
        }

        // Emit lifecycle events
        if (oldSessionId != null) {
            emitSessionLifecycleEvent(SessionLifecycleEvent(
                sessionKey = sessionKey,
                reason = "session_end:deleted",
            ))
        }

        if (isSubagentSessionKey(sessionKey)) {
            emitSessionLifecycleEvent(SessionLifecycleEvent(
                sessionKey = sessionKey,
                reason = "session_unbound:session-delete",
            ))
        }

        return SessionResetResult(
            ok = true,
            sessionKey = sessionKey,
            previousSessionId = oldSessionId,
        )
    }
}

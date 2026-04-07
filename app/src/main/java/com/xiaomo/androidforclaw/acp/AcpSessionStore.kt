package com.xiaomo.androidforclaw.acp

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * OpenClaw module: acp
 * Source: OpenClaw/src/acp/session.ts
 *
 * In-memory ACP session store with idle eviction and LRU cleanup.
 * Aligned with TS createInMemorySessionStore().
 */

data class AcpSessionEntry(
    val sessionId: String,
    var sessionKey: String,
    var cwd: String,
    val createdAt: Long,
    var lastTouchedAt: Long,
    var activeRunId: String? = null,
    var cancellable: Boolean = false,
)

interface AcpSessionStore {
    fun createSession(sessionKey: String, cwd: String, sessionId: String? = null): AcpSessionEntry
    fun hasSession(sessionId: String): Boolean
    fun getSession(sessionId: String): AcpSessionEntry?
    fun getSessionByRunId(runId: String): AcpSessionEntry?
    fun setActiveRun(sessionId: String, runId: String)
    fun clearActiveRun(sessionId: String)
    fun cancelActiveRun(sessionId: String): Boolean
    fun clearAll()
}

class InMemoryAcpSessionStore(
    private val maxSessions: Int = DEFAULT_MAX_SESSIONS,
    private val idleTtlMs: Long = DEFAULT_IDLE_TTL_MS,
    private val now: () -> Long = { System.currentTimeMillis() },
) : AcpSessionStore {

    companion object {
        const val DEFAULT_MAX_SESSIONS = 5_000
        const val DEFAULT_IDLE_TTL_MS = 24L * 60 * 60 * 1_000
    }

    private val sessions = ConcurrentHashMap<String, AcpSessionEntry>()
    private val runIdToSessionId = ConcurrentHashMap<String, String>()

    private fun touchSession(session: AcpSessionEntry) {
        session.lastTouchedAt = now()
    }

    private fun removeSession(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        session.activeRunId?.let { runIdToSessionId.remove(it) }
        sessions.remove(sessionId)
        return true
    }

    private fun reapIdleSessions() {
        val idleBefore = now() - idleTtlMs
        val toRemove = sessions.entries
            .filter { (_, s) ->
                s.activeRunId == null && !s.cancellable && s.lastTouchedAt <= idleBefore
            }
            .map { it.key }
        toRemove.forEach { removeSession(it) }
    }

    private fun evictOldestIdleSession(): Boolean {
        val oldest = sessions.entries
            .filter { (_, s) -> s.activeRunId == null && !s.cancellable }
            .minByOrNull { it.value.lastTouchedAt }
        return oldest?.let { removeSession(it.key) } ?: false
    }

    override fun createSession(sessionKey: String, cwd: String, sessionId: String?): AcpSessionEntry {
        val id = sessionId ?: UUID.randomUUID().toString()
        val nowMs = now()

        // If session with this ID already exists, update it
        val existing = sessions[id]
        if (existing != null) {
            existing.sessionKey = sessionKey
            existing.cwd = cwd
            touchSession(existing)
            return existing
        }

        reapIdleSessions()
        if (sessions.size >= maxSessions && !evictOldestIdleSession()) {
            throw IllegalStateException(
                "ACP session limit reached (max $maxSessions). Close idle ACP clients and retry."
            )
        }

        val session = AcpSessionEntry(
            sessionId = id,
            sessionKey = sessionKey,
            cwd = cwd,
            createdAt = nowMs,
            lastTouchedAt = nowMs,
        )
        sessions[id] = session
        return session
    }

    override fun hasSession(sessionId: String): Boolean = sessions.containsKey(sessionId)

    override fun getSession(sessionId: String): AcpSessionEntry? {
        val session = sessions[sessionId] ?: return null
        touchSession(session)
        return session
    }

    override fun getSessionByRunId(runId: String): AcpSessionEntry? {
        val sessionId = runIdToSessionId[runId] ?: return null
        return getSession(sessionId)
    }

    override fun setActiveRun(sessionId: String, runId: String) {
        val session = sessions[sessionId] ?: return
        session.activeRunId = runId
        runIdToSessionId[runId] = sessionId
        touchSession(session)
    }

    override fun clearActiveRun(sessionId: String) {
        val session = sessions[sessionId] ?: return
        session.activeRunId?.let { runIdToSessionId.remove(it) }
        session.activeRunId = null
        session.cancellable = false
        touchSession(session)
    }

    override fun cancelActiveRun(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        if (!session.cancellable && session.activeRunId == null) return false
        session.activeRunId?.let { runIdToSessionId.remove(it) }
        session.activeRunId = null
        session.cancellable = false
        touchSession(session)
        return true
    }

    override fun clearAll() {
        sessions.clear()
        runIdToSessionId.clear()
    }
}

/** Default shared session store (singleton). */
val defaultAcpSessionStore: AcpSessionStore = InMemoryAcpSessionStore()

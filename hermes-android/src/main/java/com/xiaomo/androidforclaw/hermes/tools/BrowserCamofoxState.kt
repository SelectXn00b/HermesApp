package com.xiaomo.androidforclaw.hermes.tools

/**
 * Camofox browser state management.
 * Ported from browser_camofox_state.py
 */
object BrowserCamofoxState {

    data class CamofoxSession(
        val sessionId: String,
        val currentUrl: String? = null,
        val title: String? = null,
        val isActive: Boolean = true)

    private val _sessions = mutableMapOf<String, CamofoxSession>()

    fun createSession(sessionId: String): CamofoxSession {
        val session = CamofoxSession(sessionId = sessionId)
        _sessions[sessionId] = session
        return session
    }

    fun getSession(sessionId: String): CamofoxSession? = _sessions[sessionId]

    fun updateSession(sessionId: String, url: String? = null, title: String? = null) {
        val session = _sessions[sessionId] ?: return
        _sessions[sessionId] = session.copy(
            currentUrl = url ?: session.currentUrl,
            title = title ?: session.title)
    }

    fun removeSession(sessionId: String) {
        _sessions.remove(sessionId)
    }

    fun getAllSessions(): Map<String, CamofoxSession> = _sessions.toMap()

    fun clearAll() = _sessions.clear()


}

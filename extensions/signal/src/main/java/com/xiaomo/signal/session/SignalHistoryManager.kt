/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/signal/(all)
 *
 * AndroidForClaw adaptation: Signal channel runtime.
 */
package com.xiaomo.signal.session

import android.util.Log

/**
 * Signal message history management
 */
class SignalHistoryManager(private val historyLimit: Int = 50) {
    companion object {
        private const val TAG = "SignalHistoryManager"
    }

    private val histories = mutableMapOf<String, MutableList<HistoryEntry>>()

    fun addMessage(sessionKey: String, role: String, content: String) {
        val history = histories.getOrPut(sessionKey) { mutableListOf() }
        history.add(HistoryEntry(role, content, System.currentTimeMillis()))
        if (history.size > historyLimit) {
            history.removeAt(0)
        }
    }

    fun getHistory(sessionKey: String): List<HistoryEntry> {
        return histories[sessionKey] ?: emptyList()
    }

    data class HistoryEntry(val role: String, val content: String, val timestamp: Long)
}

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/slack/(all)
 *
 * AndroidForClaw adaptation: Slack channel runtime.
 */
package com.xiaomo.slack.session

import android.util.Log

/**
 * Slack message history management
 */
class SlackHistoryManager(private val historyLimit: Int = 50) {
    companion object {
        private const val TAG = "SlackHistoryManager"
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

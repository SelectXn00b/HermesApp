/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/whatsapp/(all)
 *
 * AndroidForClaw adaptation: WhatsApp channel runtime.
 */
package com.xiaomo.whatsapp.session

import android.util.Log

/**
 * WhatsApp message history management
 */
class WhatsAppHistoryManager(private val historyLimit: Int = 50) {
    companion object {
        private const val TAG = "WhatsAppHistoryManager"
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

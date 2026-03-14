/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: data models.
 */
package com.xiaomo.androidforclaw.data.model

/**
 * Task data class - Simplified version
 *
 * Only keeps running state and package name info.
 * Conversation history managed by Session (agent/session/SessionManager.kt)
 */
class TaskData(
    val taskId: String,
    var packageName: String
) {
    // ===== Running state =====
    private var _isRunning: Boolean = false
    private var _conversationId: String = ""

    // ===== State management =====
    fun setIsRunning(isRunning: Boolean) {
        _isRunning = isRunning
    }

    fun stopRunning(reason: String) {
        _isRunning = false
    }

    fun getIsRunning(): Boolean = _isRunning

    fun updateConversationId(conversationId: String) {
        _conversationId = conversationId
    }

    fun getConversationId(): String = _conversationId
}

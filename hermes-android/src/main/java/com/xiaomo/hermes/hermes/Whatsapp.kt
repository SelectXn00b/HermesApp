package com.xiaomo.hermes.hermes

import android.util.Log
import org.json.JSONObject

/**
 * WhatsAppAdapter - 对齐 hermes-agent/gateway/platforms/whatsapp.py
 */
class WhatsAppAdapter(
    private val config: Any? = null
) {
    companion object {
        private const val _TAG = "WhatsAppAdapter"
    }

    private fun whatsappRequireMention(): Boolean {
        return false
    }

    private fun whatsappFreeResponseChats(): List<String> {
        return emptyList()
    }

    private fun compileMentionPatterns(): List<Regex> {
        return emptyList()
    }

    private fun normalizeWhatsappId(value: String): String {
        return value
    }

    private fun botIdsFromMessage(data: JSONObject): List<String> {
        return emptyList()
    }

    private fun messageIsReplyToBot(data: JSONObject): Boolean {
        return false
    }

    private fun messageMentionsBot(data: JSONObject): Boolean {
        return false
    }

    private fun messageMatchesMentionPatterns(data: JSONObject): Boolean {
        return false
    }

    private fun cleanBotMentionText(text: String, data: JSONObject): String {
        return text
    }

    private fun shouldProcessMessage(data: JSONObject): Boolean {
        return true
    }

    private fun closeBridgeLog() {
        // cleanup
    }

    fun formatMessage(content: String): String {
        return content
    }

    private fun saveFence(m: JSONObject) {
        // save fence state
    }

    private fun saveCode(m: JSONObject) {
        // save code block
    }
}

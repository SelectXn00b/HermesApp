package com.xiaomo.androidforclaw.hermes.tools

/**
 * Send message tool — delegates to platform messaging.
 * Ported from send_message_tool.py
 */
object SendMessageTool {

    data class SendMessageResult(
        val success: Boolean = false,
        val messageId: String? = null,
        val error: String? = null)

    /**
     * Callback interface for sending messages.
     */
    fun interface MessageSender {
        fun send(channel: String, message: String, threadId: String?): SendMessageResult
    }

    /**
     * Send a message via the platform.
     */
    fun sendMessage(
        channel: String,
        message: String,
        threadId: String? = null,
        sender: MessageSender? = null): SendMessageResult {
        if (sender == null) {
            return SendMessageResult(error = "No message sender configured")
        }
        if (message.isBlank()) {
            return SendMessageResult(error = "Message content is empty")
        }
        if (channel.isBlank()) {
            return SendMessageResult(error = "Channel is empty")
        }
        return try {
            sender.send(channel, message, threadId)
        } catch (e: Exception) {
            SendMessageResult(error = "Failed to send message: ${e.message}")
        }
    }


}

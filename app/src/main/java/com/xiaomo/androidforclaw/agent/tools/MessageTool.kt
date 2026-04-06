package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/message-tool.ts
 *
 * AndroidForClaw adaptation: message tool for sending messages via channel (Feishu/Telegram/etc.).
 * Wraps FeishuSender capabilities into an agent-callable tool.
 */

import android.content.Context
import com.xiaomo.androidforclaw.core.MyApplication
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import com.xiaomo.feishu.messaging.SendResult
import java.io.File

/**
 * Message Tool — Send messages via channel (Feishu/Telegram/etc.)
 *
 * Aligned with OpenClaw message tool schema:
 * - action: send, reply, sendAttachment, delete
 * - channel: feishu, telegram, discord, etc.
 * - target: receive_id (open_id / chat_id)
 * - message: text content
 * - media: image/file path
 * - replyTo: message_id for quote reply
 */
class MessageTool(private val context: Context) : Tool {
    companion object {
        private const val TAG = "MessageTool"
    }

    override val name = "message"
    override val description =
        "Send, reply, delete messages via channel plugins (Feishu/Telegram/etc.). " +
        "Supports text, images, files, interactive cards, quote replies, and thread replies. " +
        "Use 'action' to select the operation."

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "action" to PropertySchema(
                            type = "string",
                            description = "Action to perform: send (send message), reply (quote reply), sendAttachment (send file/image), delete (delete message), updateCard (update card message)."
                        ),
                        "channel" to PropertySchema(
                            type = "string",
                            description = "Channel name: feishu, telegram, discord, whatsapp, slack, signal, imessage, line. Default: feishu."
                        ),
                        "target" to PropertySchema(
                            type = "string",
                            description = "Target: open_id (ou_xxx), chat_id (oc_xxx), or user identifier. For 'reply' action, this is the reply-to message_id (om_xxx)."
                        ),
                        "message" to PropertySchema(
                            type = "string",
                            description = "Text message content. Supports Markdown formatting (auto-rendered as card on Feishu)."
                        ),
                        "media" to PropertySchema(
                            type = "string",
                            description = "Media URL or local file path for attachments."
                        ),
                        "filename" to PropertySchema(
                            type = "string",
                            description = "Filename for attachment."
                        ),
                        "filePath" to PropertySchema(
                            type = "string",
                            description = "Alias for media (local file path)."
                        ),
                        "mimeType" to PropertySchema(
                            type = "string",
                            description = "MIME type for attachments."
                        ),
                        "replyTo" to PropertySchema(
                            type = "string",
                            description = "Message ID to reply to (quote reply). Alternative to 'target' for reply action."
                        ),
                        "threadId" to PropertySchema(
                            type = "string",
                            description = "Root message ID for thread reply (the message that started the thread). Pass the root message's ID (om_xxx) and the reply will appear in that thread."
                        ),
                        "asVoice" to PropertySchema(
                            type = "boolean",
                            description = "Send as voice message."
                        ),
                        "silent" to PropertySchema(
                            type = "boolean",
                            description = "Send silently (no notification)."
                        ),
                        "receiveIdType" to PropertySchema(
                            type = "string",
                            description = "Receive ID type: open_id, chat_id, union_id. Auto-detected from target format if not specified."
                        ),
                        "messageId" to PropertySchema(
                            type = "string",
                            description = "Message ID for updateCard/delete actions."
                        ),
                        "card" to PropertySchema(
                            type = "string",
                            description = "Card JSON content for updateCard action."
                        ),
                        "dryRun" to PropertySchema(
                            type = "boolean",
                            description = "If true, validate but don't actually send."
                        )
                    ),
                    required = listOf("action")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val action = args["action"] as? String
            ?: return ToolResult.error("Missing required parameter: action")

        val channel = (args["channel"] as? String) ?: "feishu"
        val target = args["target"] as? String
        val message = args["message"] as? String
        val media = args["media"] as? String ?: args["filePath"] as? String
        val replyTo = args["replyTo"] as? String
        val messageId = args["messageId"] as? String
        val dryRun = args["dryRun"] as? Boolean == true

        if (dryRun) {
            return ToolResult.success("[dryRun] Would execute: action=$action, channel=$channel, target=$target, message=${message?.take(50)}...")
        }

        return when (action) {
            "send" -> executeSend(channel, target, message, media, args)
            "reply" -> executeReply(channel, target, replyTo, message, args)
            "sendAttachment" -> executeSendAttachment(channel, target, media, args)
            "delete" -> executeDelete(channel, messageId)
            "updateCard" -> executeUpdateCard(channel, messageId, args["card"] as? String)
            else -> ToolResult.error("Unknown action: $action. Supported: send, reply, sendAttachment, delete, updateCard")
        }
    }

    /**
     * Send message to target
     */
    private suspend fun executeSend(
        channel: String,
        target: String?,
        message: String?,
        media: String?,
        args: Map<String, Any?>
    ): ToolResult {
        if (target.isNullOrBlank()) {
            return ToolResult.error("Missing required parameter: target (open_id or chat_id)")
        }

        val receiveIdType = args["receiveIdType"] as? String ?: inferReceiveIdType(target)

        // Send media (image/file)
        if (!media.isNullOrBlank()) {
            return when (channel) {
                "feishu" -> sendFeishuMedia(target, media, receiveIdType, message)
                else -> ToolResult.error("Channel '$channel' not supported on Android. Supported: feishu")
            }
        }

        // Send text
        if (message.isNullOrBlank()) {
            return ToolResult.error("Missing required parameter: message or media")
        }

        return when (channel) {
            "feishu" -> sendFeishuMessage(target, message, receiveIdType, args)
            else -> ToolResult.error("Channel '$channel' not supported on Android. Supported: feishu")
        }
    }

    /**
     * Send quote reply
     */
    private suspend fun executeReply(
        channel: String,
        target: String?,
        replyTo: String?,
        message: String?,
        args: Map<String, Any?>
    ): ToolResult {
        val replyToId = replyTo ?: target
        if (replyToId.isNullOrBlank()) {
            return ToolResult.error("Missing required parameter: replyTo or target (message_id)")
        }
        if (message.isNullOrBlank()) {
            return ToolResult.error("Missing required parameter: message")
        }

        return when (channel) {
            "feishu" -> replyFeishuMessage(replyToId, message, args)
            else -> ToolResult.error("Channel '$channel' not supported on Android. Supported: feishu")
        }
    }

    /**
     * Send attachment (image/file)
     */
    private suspend fun executeSendAttachment(
        channel: String,
        target: String?,
        media: String?,
        args: Map<String, Any?>
    ): ToolResult {
        if (target.isNullOrBlank()) {
            return ToolResult.error("Missing required parameter: target (open_id or chat_id)")
        }
        if (media.isNullOrBlank()) {
            return ToolResult.error("Missing required parameter: media (file path or URL)")
        }

        val receiveIdType = args["receiveIdType"] as? String ?: inferReceiveIdType(target)
        val caption = args["message"] as? String

        return when (channel) {
            "feishu" -> sendFeishuMedia(target, media, receiveIdType, caption)
            else -> ToolResult.error("Channel '$channel' not supported on Android. Supported: feishu")
        }
    }

    /**
     * Delete message
     */
    private suspend fun executeDelete(channel: String, messageId: String?): ToolResult {
        if (messageId.isNullOrBlank()) {
            return ToolResult.error("Missing required parameter: messageId")
        }

        return when (channel) {
            "feishu" -> deleteFeishuMessage(messageId)
            else -> ToolResult.error("Channel '$channel' not supported on Android. Supported: feishu")
        }
    }

    /**
     * Update card message
     */
    private suspend fun executeUpdateCard(channel: String, messageId: String?, card: String?): ToolResult {
        if (messageId.isNullOrBlank()) {
            return ToolResult.error("Missing required parameter: messageId")
        }
        if (card.isNullOrBlank()) {
            return ToolResult.error("Missing required parameter: card (card JSON content)")
        }

        return when (channel) {
            "feishu" -> updateFeishuCard(messageId, card)
            else -> ToolResult.error("Channel '$channel' not supported on Android. Supported: feishu")
        }
    }

    // ===== Feishu implementations =====

    private suspend fun sendFeishuMessage(
        target: String,
        message: String,
        receiveIdType: String,
        args: Map<String, Any?>
    ): ToolResult {
        val feishuChannel = MyApplication.getFeishuChannel()
            ?: return ToolResult.error("Feishu channel not initialized. Check feishu configuration in openclaw.json")

        val sender = feishuChannel.sender
        val threadId = args["threadId"] as? String
        val silent = args["silent"] as? Boolean == true

        try {
            // Thread reply
            if (!threadId.isNullOrBlank()) {
                val result = sender.replyInThread(threadId, message)
                return if (result.isSuccess) {
                    val sendResult = result.getOrNull()!!
                    ToolResult.success("Message sent as thread reply: ${sendResult.messageId}")
                } else {
                    ToolResult.error("Thread reply failed: ${result.exceptionOrNull()?.message}")
                }
            }

            // Normal send
            val result = sender.sendTextMessage(
                receiveId = target,
                text = message,
                receiveIdType = receiveIdType
            )

            return if (result.isSuccess) {
                val sendResult = result.getOrNull()!!
                ToolResult.success("Message sent: ${sendResult.messageId}")
            } else {
                ToolResult.error("Send failed: ${result.exceptionOrNull()?.message}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Feishu message", e)
            return ToolResult.error("Send failed: ${e.message}")
        }
    }

    private suspend fun replyFeishuMessage(
        replyToId: String,
        message: String,
        args: Map<String, Any?>
    ): ToolResult {
        val feishuChannel = MyApplication.getFeishuChannel()
            ?: return ToolResult.error("Feishu channel not initialized")

        val sender = feishuChannel.sender

        try {
            val result = sender.sendTextReply(replyToId, message)
            return if (result.isSuccess) {
                val sendResult = result.getOrNull()!!
                ToolResult.success("Reply sent: ${sendResult.messageId} (reply to $replyToId)")
            } else {
                ToolResult.error("Reply failed: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reply Feishu message", e)
            return ToolResult.error("Reply failed: ${e.message}")
        }
    }

    private suspend fun sendFeishuMedia(
        target: String,
        media: String,
        receiveIdType: String,
        caption: String?
    ): ToolResult {
        val feishuChannel = MyApplication.getFeishuChannel()
            ?: return ToolResult.error("Feishu channel not initialized")

        val sender = feishuChannel.sender

        try {
            val file = File(media)
            if (!file.exists()) {
                return ToolResult.error("File not found: $media")
            }

            val isImage = media.lowercase().let {
                it.endsWith(".jpg") || it.endsWith(".jpeg") ||
                it.endsWith(".png") || it.endsWith(".gif") || it.endsWith(".webp")
            }

            if (isImage) {
                val result = sender.uploadAndSendImage(target, file.absolutePath, receiveIdType)
                return if (result.isSuccess) {
                    val sendResult = result.getOrNull()!!
                    var output = "Image sent: ${sendResult.messageId}"
                    if (!caption.isNullOrBlank()) {
                        // Send caption as separate message
                        val captionResult = sender.sendTextMessage(target, caption, receiveIdType)
                        if (captionResult.isSuccess) {
                            output += " (with caption)"
                        }
                    }
                    ToolResult.success(output)
                } else {
                    ToolResult.error("Image send failed: ${result.exceptionOrNull()?.message}")
                }
            } else {
                return ToolResult.error("Non-image file sending not yet supported. Use image files (.jpg, .png, .gif, .webp)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Feishu media", e)
            return ToolResult.error("Media send failed: ${e.message}")
        }
    }

    private suspend fun deleteFeishuMessage(messageId: String): ToolResult {
        val feishuChannel = MyApplication.getFeishuChannel()
            ?: return ToolResult.error("Feishu channel not initialized")

        val sender = feishuChannel.sender

        try {
            val result = sender.deleteMessage(messageId)
            return if (result.isSuccess) {
                ToolResult.success("Message deleted: $messageId")
            } else {
                ToolResult.error("Delete failed: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete Feishu message", e)
            return ToolResult.error("Delete failed: ${e.message}")
        }
    }

    private suspend fun updateFeishuCard(messageId: String, card: String): ToolResult {
        val feishuChannel = MyApplication.getFeishuChannel()
            ?: return ToolResult.error("Feishu channel not initialized")

        val sender = feishuChannel.sender

        try {
            val result = sender.updateCard(messageId, card)
            return if (result.isSuccess) {
                ToolResult.success("Card updated: $messageId")
            } else {
                ToolResult.error("Card update failed: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Feishu card", e)
            return ToolResult.error("Card update failed: ${e.message}")
        }
    }

    // ===== Helpers =====

    /**
     * Infer receive_id_type from target format
     * - ou_xxx → open_id
     * - oc_xxx → chat_id
     * - else → open_id (default)
     */
    private fun inferReceiveIdType(target: String): String {
        return when {
            target.startsWith("oc_") -> "chat_id"
            target.startsWith("ou_") -> "open_id"
            target.startsWith("on_") -> "union_id"
            else -> "open_id" // Default for email or other formats
        }
    }
}

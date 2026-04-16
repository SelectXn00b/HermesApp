package com.xiaomo.androidforclaw.hermes.gateway.platforms

/**
 * WhatsApp platform adapter — stub implementation.
 *
 * Full implementation requires WhatsApp Business API or wa-cli.
 * This stub provides the interface for future integration.
 *
 * Ported from gateway/platforms/whatsapp.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.hermes.gateway.*
import org.json.JSONObject

class WhatsAppAdapter(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.WHATSAPP) {
    companion object { private const val TAG = "WhatsAppAdapter" }

    override suspend fun connect(): Boolean {
        Log.w(TAG, "WhatsApp adapter is a stub — not implemented on Android")
        return false
    }

    override suspend fun disconnect() {}


    override suspend fun send(chatId: String, content: String, replyTo: String?, metadata: JSONObject?): SendResult =
        SendResult(success = false, error = "WhatsApp not supported on Android")

    override suspend fun sendImage(chatId: String, imageUrl: String, caption: String?, replyTo: String?): SendResult =
        SendResult(success = false, error = "WhatsApp not supported on Android")

    override suspend fun sendDocument(chatId: String, fileUrl: String, fileName: String?, caption: String?, replyTo: String?): SendResult =
        SendResult(success = false, error = "WhatsApp not supported on Android")
}

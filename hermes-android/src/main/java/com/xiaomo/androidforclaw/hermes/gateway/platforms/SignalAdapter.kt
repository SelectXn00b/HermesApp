package com.xiaomo.androidforclaw.hermes.gateway.platforms

/**
 * Signal platform adapter — stub implementation.
 *
 * Full implementation requires signal-cli (not available on Android).
 * This stub provides the interface for future integration.
 *
 * Ported from gateway/platforms/signal.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.hermes.gateway.*
import org.json.JSONObject

class SignalAdapter(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.SIGNAL) {
    companion object { private const val TAG = "SignalAdapter" }

    override suspend fun connect(): Boolean {
        Log.w(TAG, "Signal adapter is a stub — not implemented on Android")
        return false
    }

    override suspend fun disconnect() {}


    override suspend fun send(chatId: String, content: String, replyTo: String?, metadata: JSONObject?): SendResult =
        SendResult(success = false, error = "Signal not supported on Android")

    override suspend fun sendImage(chatId: String, imageUrl: String, caption: String?, replyTo: String?): SendResult =
        SendResult(success = false, error = "Signal not supported on Android")

    override suspend fun sendDocument(chatId: String, fileUrl: String, fileName: String?, caption: String?, replyTo: String?): SendResult =
        SendResult(success = false, error = "Signal not supported on Android")
}

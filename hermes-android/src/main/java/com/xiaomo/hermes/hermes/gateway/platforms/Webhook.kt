package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * Webhook platform adapter — stub implementation.
 *
 * Full implementation requires HTTP server (aiohttp) which is not
 * available on Android. This stub provides the interface for future integration.
 *
 * Ported from gateway/platforms/webhook.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.*
import org.json.JSONObject

class WebhookAdapter(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.WEBHOOK) {
    companion object { private const val _TAG = "Webhook" }

    override suspend fun connect(): Boolean {
        Log.w(_TAG, "Webhook adapter requires HTTP server — not supported on Android")
        return false
    }

    override suspend fun disconnect() {}


    override suspend fun send(chatId: String, content: String, replyTo: String?, metadata: JSONObject?): SendResult =
        SendResult(success = false, error = "Webhook not supported on Android")
}

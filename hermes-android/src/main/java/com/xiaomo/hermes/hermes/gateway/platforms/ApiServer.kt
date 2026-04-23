package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * API Server platform adapter — stub implementation.
 *
 * Full implementation requires HTTP server (aiohttp) which is not
 * available on Android. This stub provides the interface for future integration.
 *
 * Ported from gateway/platforms/api_server.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.*
import org.json.JSONObject

class APIServerAdapter(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.API_SERVER) {
    companion object { private const val _TAG = "ApiServer" }

    override suspend fun connect(): Boolean {
        Log.w(_TAG, "API Server adapter requires HTTP server — not supported on Android")
        return false
    }

    override suspend fun disconnect() {
        markDisconnected()
    }


    override suspend fun send(chatId: String, content: String, replyTo: String?, metadata: JSONObject?): SendResult =
        SendResult(success = false, error = "API Server not supported on Android")
}

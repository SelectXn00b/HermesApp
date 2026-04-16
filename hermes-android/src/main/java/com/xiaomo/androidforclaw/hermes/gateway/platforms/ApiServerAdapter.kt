package com.xiaomo.androidforclaw.hermes.gateway.platforms

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
import com.xiaomo.androidforclaw.hermes.gateway.*
import org.json.JSONObject

class ApiServerAdapter(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.API_SERVER) {
    companion object { private const val TAG = "ApiServerAdapter" }

    override suspend fun connect(): Boolean {
        Log.w(TAG, "API Server adapter requires HTTP server — not supported on Android")
        return false
    }

    override suspend fun disconnect() {}


    override suspend fun send(chatId: String, content: String, replyTo: String?, metadata: JSONObject?): SendResult =
        SendResult(success = false, error = "API Server not supported on Android")
}

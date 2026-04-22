package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * Mattermost platform adapter — stub implementation.
 *
 * Full implementation requires the Mattermost driver (not available on Android).
 * This stub provides the interface for future integration.
 *
 * Ported from gateway/platforms/mattermost.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.*
import org.json.JSONObject

class MattermostAdapter(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.MATTERMOST) {
    companion object { private const val _TAG = "Mattermost" }

    override suspend fun connect(): Boolean {
        Log.w(_TAG, "Mattermost adapter is a stub — not implemented on Android")
        return false
    }

    override suspend fun disconnect() {}


    override suspend fun send(chatId: String, content: String, replyTo: String?, metadata: JSONObject?): SendResult =
        SendResult(success = false, error = "Mattermost not supported on Android")
}

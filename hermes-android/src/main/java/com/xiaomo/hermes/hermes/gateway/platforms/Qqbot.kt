package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * QQ Bot platform adapter — stub implementation.
 *
 * Full implementation requires the QQ Bot SDK (not available on Android).
 * This stub provides the interface for future integration.
 *
 * Ported from gateway/platforms/qqbot.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.*
import org.json.JSONObject

class Qqbot(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.QQBOT) {
    companion object { private const val TAG = "Qqbot" }

    override suspend fun connect(): Boolean {
        Log.w(TAG, "QQ Bot adapter is a stub — not implemented on Android")
        return false
    }

    override suspend fun disconnect() {}


    override suspend fun send(chatId: String, content: String, replyTo: String?, metadata: JSONObject?): SendResult =
        SendResult(success = false, error = "QQ Bot not supported on Android")
}

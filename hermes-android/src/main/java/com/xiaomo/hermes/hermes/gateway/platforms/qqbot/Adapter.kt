package com.xiaomo.hermes.hermes.gateway.platforms.qqbot

/**
 * QQ Bot platform adapter — stub implementation.
 *
 * Full implementation requires the QQ Bot SDK (not available on Android).
 * This stub provides the interface for future integration.
 *
 * Ported from gateway/platforms/qqbot/adapter.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.*
import com.xiaomo.hermes.hermes.gateway.platforms.BasePlatformAdapter
import com.xiaomo.hermes.hermes.gateway.platforms.SendResult
import org.json.JSONObject

class QQAdapter(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.QQBOT) {
    companion object { private const val _TAG = "QQAdapter" }

    override suspend fun connect(): Boolean {
        Log.w(_TAG, "QQ Bot adapter is a stub — not implemented on Android")
        return false
    }

    override suspend fun disconnect() {}


    override suspend fun send(chatId: String, content: String, replyTo: String?, metadata: JSONObject?): SendResult =
        SendResult(success = false, error = "QQ Bot not supported on Android")
}

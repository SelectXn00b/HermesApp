package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * WeCom callback mode adapter — stub implementation.
 *
 * Full implementation requires HTTP server (aiohttp) which is not
 * available on Android. Use WeComAdapter (webhook mode) instead.
 *
 * Ported from gateway/platforms/wecom_callback.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.*
import org.json.JSONObject

class WecomCallbackAdapter(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.WECOM_CALLBACK) {
    companion object { private const val _TAG = "WeComCallback" }

    override suspend fun connect(): Boolean {
        Log.w(_TAG, "WeCom callback adapter requires HTTP server — use WeCom webhook mode on Android")
        return false
    }

    override suspend fun disconnect() {
        markDisconnected()
    }


    override suspend fun send(chatId: String, content: String, replyTo: String?, metadata: JSONObject?): SendResult =
        SendResult(success = false, error = "WeCom callback not supported on Android — use webhook mode")
}

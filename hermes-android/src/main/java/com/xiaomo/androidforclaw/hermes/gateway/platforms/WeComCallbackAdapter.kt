package com.xiaomo.androidforclaw.hermes.gateway.platforms

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
import com.xiaomo.androidforclaw.hermes.gateway.*
import org.json.JSONObject

class WeComCallbackAdapter(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.WECOM_CALLBACK) {
    companion object { private const val TAG = "WeComCallbackAdapter" }

    override suspend fun connect(): Boolean {
        Log.w(TAG, "WeCom callback adapter requires HTTP server — use WeCom webhook mode on Android")
        return false
    }

    override suspend fun disconnect() {}


    override suspend fun send(chatId: String, content: String, replyTo: String?, metadata: JSONObject?): SendResult =
        SendResult(success = false, error = "WeCom callback not supported on Android — use webhook mode")
}

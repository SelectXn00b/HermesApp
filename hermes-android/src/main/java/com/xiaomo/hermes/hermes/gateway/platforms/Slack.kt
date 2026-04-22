package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * Slack platform adapter — stub implementation.
 *
 * Full implementation requires Slack Bolt SDK (not available on Android).
 * This stub provides the interface for future integration.
 *
 * Ported from gateway/platforms/slack.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.*
import org.json.JSONObject

class Slack(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.SLACK) {
    companion object { private const val TAG = "Slack" }

    override suspend fun connect(): Boolean {
        Log.w(TAG, "Slack adapter is a stub — not implemented on Android")
        return false
    }

    override suspend fun disconnect() {}


    override suspend fun send(chatId: String, content: String, replyTo: String?, metadata: JSONObject?): SendResult =
        SendResult(success = false, error = "Slack not supported on Android")

    override suspend fun sendImage(chatId: String, imageUrl: String, caption: String?, replyTo: String?): SendResult =
        SendResult(success = false, error = "Slack not supported on Android")

    override suspend fun sendDocument(chatId: String, fileUrl: String, fileName: String?, caption: String?, replyTo: String?): SendResult =
        SendResult(success = false, error = "Slack not supported on Android")
}

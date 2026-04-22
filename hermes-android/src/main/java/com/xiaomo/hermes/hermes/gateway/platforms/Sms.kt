package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * SMS platform adapter — stub implementation (iOS-specific).
 *
 * SMS is not available on Android in the same way as iOS.
 * This stub provides the interface for future integration.
 *
 * Ported from gateway/platforms/sms.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.*
import org.json.JSONObject

class Sms(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.SMS) {
    companion object { private const val _TAG = "Sms" }

    override suspend fun connect(): Boolean {
        Log.w(_TAG, "SMS adapter is iOS-specific — not supported on Android")
        return false
    }

    override suspend fun disconnect() {}


    override suspend fun send(chatId: String, content: String, replyTo: String?, metadata: JSONObject?): SendResult =
        SendResult(success = false, error = "SMS not supported on Android")
}

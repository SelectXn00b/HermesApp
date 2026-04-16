package com.xiaomo.androidforclaw.hermes.gateway.platforms

/**
 * BlueBubbles platform adapter — stub implementation (iOS/macOS-specific).
 *
 * BlueBubbles is an iMessage bridge that only works on iOS/macOS.
 * This stub provides the interface for future integration.
 *
 * Ported from gateway/platforms/bluebubbles.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.hermes.gateway.*
import org.json.JSONObject

class BluebubblesAdapter(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.BLUEBUBBLES) {
    companion object { private const val TAG = "BluebubblesAdapter" }

    override suspend fun connect(): Boolean {
        Log.w(TAG, "BlueBubbles is iOS/macOS-specific — not supported on Android")
        return false
    }

    override suspend fun disconnect() {}


    override suspend fun send(chatId: String, content: String, replyTo: String?, metadata: JSONObject?): SendResult =
        SendResult(success = false, error = "BlueBubbles not supported on Android")
}

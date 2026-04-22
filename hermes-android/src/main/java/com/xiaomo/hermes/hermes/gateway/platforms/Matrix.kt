package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * Matrix platform adapter — stub implementation.
 *
 * Full implementation requires matrix-nio (not available on Android).
 * This stub provides the interface for future integration.
 *
 * Ported from gateway/platforms/matrix.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.*
import org.json.JSONObject

class Matrix(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.MATRIX) {
    companion object { private const val _TAG = "Matrix" }

    override suspend fun connect(): Boolean {
        Log.w(_TAG, "Matrix adapter is a stub — not implemented on Android")
        return false
    }

    override suspend fun disconnect() {}


    override suspend fun send(chatId: String, content: String, replyTo: String?, metadata: JSONObject?): SendResult =
        SendResult(success = false, error = "Matrix not supported on Android")
}

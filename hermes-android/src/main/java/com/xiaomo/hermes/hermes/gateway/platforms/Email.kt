package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * Email platform adapter — stub implementation.
 *
 * Full implementation requires IMAP/SMTP (not available on Android without
 * additional libraries). This stub provides the interface for future integration.
 *
 * Ported from gateway/platforms/email.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.*
import org.json.JSONObject

class EmailAdapter(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.EMAIL) {
    companion object { private const val _TAG = "Email" }

    override suspend fun connect(): Boolean {
        Log.w(_TAG, "Email adapter is a stub — requires IMAP/SMTP libraries on Android")
        return false
    }

    override suspend fun disconnect() {}


    override suspend fun send(chatId: String, content: String, replyTo: String?, metadata: JSONObject?): SendResult =
        SendResult(success = false, error = "Email not supported on Android without IMAP/SMTP libraries")
}

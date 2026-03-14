/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/signal/(all)
 *
 * AndroidForClaw adaptation: Signal channel runtime.
 */
package com.xiaomo.signal

import android.util.Log
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Signal HTTP API Client
 */
class SignalClient(private val config: SignalConfig) {
    companion object {
        private const val TAG = "SignalClient"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun sendMessage(chatId: String, text: String): Boolean {
        Log.d(TAG, "Sending message to $chatId: ${text.take(50)}")
        // TODO: Implement Signal API send
        return false
    }
}

package com.xiaomo.androidforclaw.core

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/(all)
 *
 * AndroidForClaw adaptation: bridge inbound external messages into app agent flow.
 */


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tencent.mmkv.MMKV

/**
 * Agent Message Broadcast Receiver
 * Receives Agent execution requests from Gateway or ADB
 */
class AgentMessageReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AgentMessageReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Use System.out to ensure logs are visible
        System.out.println("========== AgentMessageReceiver.onReceive called ==========")
        Log.e(TAG, "========== onReceive called ==========")
        Log.e(TAG, "Action: ${intent.action}")
        Log.e(TAG, "Extras: ${intent.extras}")

        if (intent.action != "com.xiaomo.androidforclaw.ACTION_EXECUTE_AGENT") {
            Log.e(TAG, "⚠️ [Receiver] Unknown action: ${intent.action}")
            return
        }

        val message = intent.getStringExtra("message")
        val explicitSessionId = intent.getStringExtra("sessionId")
        val resolvedSessionId = explicitSessionId ?: MMKV.defaultMMKV()?.decodeString("last_session_id")

        Log.e(TAG, "📨 [Receiver] Received Agent execution request:")
        Log.e(TAG, "  💬 Message: $message")
        Log.e(TAG, "  🆔 Session ID: $resolvedSessionId (explicit=$explicitSessionId)")
        System.out.println("📨 Message: $message, SessionID: $resolvedSessionId")

        if (message.isNullOrEmpty()) {
            Log.e(TAG, "⚠️ [Receiver] Message is empty, ignoring")
            return
        }

        // Ensure MainEntryNew is initialized
        try {
            Log.e(TAG, "🔧 [Receiver] Ensuring MainEntryNew is initialized...")
            MainEntryNew.initialize(context.applicationContext as android.app.Application)
        } catch (e: Exception) {
            // Already initialized, ignore
            Log.e(TAG, "✓ [Receiver] MainEntryNew already initialized")
        }

        // Execute Agent
        Log.e(TAG, "🚀 [Receiver] Starting Agent execution...")
        MainEntryNew.runWithSession(
            userInput = message,
            sessionId = resolvedSessionId,
            application = context.applicationContext as android.app.Application
        )
        Log.e(TAG, "✅ [Receiver] Agent execution started")
    }
}

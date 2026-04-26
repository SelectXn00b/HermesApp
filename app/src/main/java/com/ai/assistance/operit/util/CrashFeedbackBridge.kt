package com.ai.assistance.operit.util

import android.content.Context

object CrashFeedbackBridge {
    private const val PREFS_NAME = "crash_feedback_bridge"
    private const val KEY_PENDING_CRASH = "pending_crash_trace"

    fun savePendingCrashFeedback(context: Context, stackTrace: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_CRASH, stackTrace)
            .commit()
    }

    fun consumePendingCrashFeedback(context: Context): String? {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val trace = prefs.getString(KEY_PENDING_CRASH, null)
        if (trace != null) {
            prefs.edit().remove(KEY_PENDING_CRASH).commit()
        }
        return trace
    }
}

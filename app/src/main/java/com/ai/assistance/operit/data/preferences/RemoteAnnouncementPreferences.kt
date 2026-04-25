package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences

class RemoteAnnouncementPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAcknowledgedVersion(): Int {
        return prefs.getInt(KEY_ACKNOWLEDGED_VERSION, 0)
    }

    fun shouldShow(version: Int): Boolean {
        return version > getAcknowledgedVersion()
    }

    fun setAcknowledgedVersion(version: Int) {
        prefs.edit().putInt(KEY_ACKNOWLEDGED_VERSION, version).apply()
    }

    companion object {
        private const val PREFS_NAME = "remote_announcement_preferences"
        private const val KEY_ACKNOWLEDGED_VERSION = "acknowledged_version"
    }
}

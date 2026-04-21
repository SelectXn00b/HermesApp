package com.ai.assistance.operit.data.preferences

import android.content.Context
import java.security.MessageDigest

class SkillVisibilityPreferences private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "com.ai.assistance.operit.data.preferences.SkillVisibilityPreferences"

        @Volatile
        private var INSTANCE: SkillVisibilityPreferences? = null

        fun getInstance(context: Context): SkillVisibilityPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SkillVisibilityPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun keyForSkillName(skillName: String): String {
            val normalized = skillName.trim()
            val hashBytes = MessageDigest.getInstance("SHA-256")
                .digest(normalized.toByteArray(Charsets.UTF_8))
            val hexChars = "0123456789abcdef"
            val hex = buildString(hashBytes.size * 2) {
                for (b in hashBytes) {
                    val v = b.toInt() and 0xFF
                    append(hexChars[v ushr 4])
                    append(hexChars[v and 0x0F])
                }
            }
            return "skill_visible_${hex.take(16)}"
        }

        private fun legacyKeyForSkillName(skillName: String): String {
            val safe = skillName.trim().replace("[^a-zA-Z0-9_]".toRegex(), "_")
            return "skill_visible_$safe"
        }
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    fun isSkillVisibleToAi(skillName: String): Boolean {
        if (skillName.isBlank()) return true
        val newKey = keyForSkillName(skillName)
        if (prefs.contains(newKey)) {
            return prefs.getBoolean(newKey, true)
        }

        val legacyKey = legacyKeyForSkillName(skillName)
        if (prefs.contains(legacyKey)) {
            val legacyValue = prefs.getBoolean(legacyKey, true)
            prefs.edit().remove(legacyKey).putBoolean(newKey, legacyValue).apply()
            return legacyValue
        }

        return true
    }

    fun setSkillVisibleToAi(skillName: String, visible: Boolean) {
        if (skillName.isBlank()) return
        prefs.edit().putBoolean(keyForSkillName(skillName), visible).apply()
    }
}

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: utility helpers.
 */
package com.xiaomo.androidforclaw.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * 语言管理工具类
 *
 * 支持动态切换应用语言（中文/英文）
 *
 * 使用示例：
 * ```kotlin
 * // 切换到中文
 * LocaleHelper.setLocale(context, LocaleHelper.LANGUAGE_CHINESE)
 *
 * // 切换到英文
 * LocaleHelper.setLocale(context, LocaleHelper.LANGUAGE_ENGLISH)
 *
 * // 获取当前语言
 * val currentLang = LocaleHelper.getLanguage(context)
 * ```
 */
object LocaleHelper {
    private const val PREF_LANGUAGE = "pref_language"
    private const val PREF_NAME = "locale_settings"

    const val LANGUAGE_CHINESE = "zh"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_SYSTEM = "system"

    /**
     * 设置应用语言
     * @param context Context
     * @param language 语言代码（zh/en/system）
     * @return 更新后的 Context
     */
    fun setLocale(context: Context, language: String): Context {
        saveLanguage(context, language)
        return updateResources(context, language)
    }

    /**
     * 获取保存的语言设置
     */
    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_LANGUAGE, LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM
    }

    /**
     * 保存语言设置
     */
    private fun saveLanguage(context: Context, language: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_LANGUAGE, language).apply()
    }

    /**
     * 更新资源配置
     */
    private fun updateResources(context: Context, language: String): Context {
        val locale = when (language) {
            LANGUAGE_CHINESE -> Locale.CHINESE
            LANGUAGE_ENGLISH -> Locale.ENGLISH
            LANGUAGE_SYSTEM -> Locale.getDefault()
            else -> Locale.getDefault()
        }

        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }

    /**
     * 应用已保存的语言设置
     */
    fun applyLanguage(context: Context): Context {
        val language = getLanguage(context)
        return updateResources(context, language)
    }

    /**
     * 获取当前语言的显示名称
     */
    fun getLanguageDisplayName(context: Context): String {
        return when (getLanguage(context)) {
            LANGUAGE_CHINESE -> "中文"
            LANGUAGE_ENGLISH -> "English"
            LANGUAGE_SYSTEM -> "System / 系统"
            else -> "System / 系统"
        }
    }

    /**
     * 获取所有可用语言
     */
    fun getAvailableLanguages(): List<LanguageOption> {
        return listOf(
            LanguageOption(LANGUAGE_SYSTEM, "System / 系统"),
            LanguageOption(LANGUAGE_ENGLISH, "English"),
            LanguageOption(LANGUAGE_CHINESE, "中文")
        )
    }

    data class LanguageOption(
        val code: String,
        val displayName: String
    )
}

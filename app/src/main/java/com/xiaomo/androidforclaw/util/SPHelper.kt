/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: utility helpers.
 */
package com.xiaomo.androidforclaw.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class SPHelper private constructor(context: Context) {

    private val sharedPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)
    private val editor: SharedPreferences.Editor = sharedPreferences.edit()

    companion object {
        private var instance: SPHelper? = null

        fun getInstance(context: Context): SPHelper {
            if (instance == null) {
                instance = SPHelper(context.applicationContext)
            }
            return instance as SPHelper
        }
    }

    // 保存数据
    fun saveData(key: String, value: String) {
        editor.putString(key, value)
        editor.apply()
    }

    // 保存数据
    fun saveData(key: String, value: Int) {
        editor.putInt(key, value)
        editor.apply()
    }

    // 保存数据
    fun saveData(key: String, value: Boolean) {
        editor.putBoolean(key, value)
        editor.apply()
    }

    // 保存数据
    fun saveData(key: String, value: Long) {
        editor.putLong(key, value)
        editor.apply()
    }

    // 读取数据
    fun getData(key: String, defaultValue: String): String? {
        return sharedPreferences.getString(key, defaultValue)
    }

    // 读取数据
    fun getData(key: String, defaultValue: Int): Int {
        return sharedPreferences.getInt(key, defaultValue)
    }

    // 读取数据
    fun getData(key: String, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    // 读取数据
    fun getData(key: String, defaultValue: Long): Long {
        return sharedPreferences.getLong(key, defaultValue)
    }
}

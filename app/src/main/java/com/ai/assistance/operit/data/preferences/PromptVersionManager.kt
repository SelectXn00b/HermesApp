package com.ai.assistance.operit.data.preferences

import androidx.datastore.preferences.core.*

/**
 * 通用版本管理器
 * 用于管理基于 Preference 的默认配置/版本升级
 */
class PromptVersionManager<T : PromptVersionManager.VersionSpec> {

    interface VersionSpec {
        /**
         * Preference Key 的前缀/标识
         */
        val key: String

        /**
         * 版本号映射：版本号 -> 内容
         */
        val defaultsByVersion: Map<Int, String>
    }

    private var specs: Map<String, T> = emptyMap()

    fun setVersions(newSpecs: Map<String, T>) {
        specs = newSpecs
    }

    /**
     * 检查是否需要更新（任意一项需要更新即返回 true）
     */
    fun isNeededUpdate(preferences: Preferences): Boolean {
        return specs.any { (_, spec) ->
            shouldUpdate(preferences, spec)
        }
    }

    /**
     * 自动更新所有需要更新的项
     * @param preferences 可变偏好设置
     * @param updater 更新回调，用于执行具体的业务更新逻辑（如设置名称、描述等）
     */
    fun autoUpdate(
        preferences: MutablePreferences,
        updater: (MutablePreferences, String, T) -> Unit
    ) {
        specs.forEach { (id, spec) ->
            if (shouldUpdate(preferences, spec)) {
                // 执行调用者定义的更新逻辑
                updater(preferences, id, spec)
                
                // 执行通用的版本和内容更新
                applyLatest(preferences, spec)
            }
        }
    }

    private fun shouldUpdate(preferences: Preferences, spec: VersionSpec): Boolean {
        val valueKey = stringPreferencesKey("${spec.key}_prompt_content")
        val defaultVersionKey = intPreferencesKey("${spec.key}_default_version")

        val currentContent = preferences[valueKey] ?: ""
        val storedVersion = preferences[defaultVersionKey]
        
        val latestVersion = spec.defaultsByVersion.keys.maxOrNull() ?: return false
        val latestContent = spec.defaultsByVersion[latestVersion] ?: return false
        
        val knownDefaults = spec.defaultsByVersion.values
        // 如果内容是空白，或者内容匹配已知的旧版本默认值，则允许更新
        val isUsingKnownDefault = currentContent.isBlank() || knownDefaults.contains(currentContent)
        
        // 需更新条件：当前正在使用默认值 且 (内容不同 或 版本落后)
        return isUsingKnownDefault && (currentContent != latestContent || storedVersion != latestVersion)
    }

    private fun applyLatest(preferences: MutablePreferences, spec: VersionSpec) {
        val valueKey = stringPreferencesKey("${spec.key}_prompt_content")
        val defaultVersionKey = intPreferencesKey("${spec.key}_default_version")
        
        val latestVersion = spec.defaultsByVersion.keys.maxOrNull() ?: return
        val latestContent = spec.defaultsByVersion[latestVersion] ?: return
        
        preferences[valueKey] = latestContent
        preferences[defaultVersionKey] = latestVersion
    }
    
    companion object {
        fun defaults(vararg values: String): Map<Int, String> {
            if (values.isEmpty()) throw IllegalArgumentException("defaults values is empty")
            return values.mapIndexed { index, value -> (index + 1) to value }.toMap()
        }
    }
}

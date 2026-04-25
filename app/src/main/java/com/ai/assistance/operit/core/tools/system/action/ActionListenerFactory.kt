package com.ai.assistance.operit.core.tools.system.action

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences

/** UI操作监听器工厂类 根据权限级别提供相应的监听器实例 */
class ActionListenerFactory {
    companion object {
        private const val TAG = "ActionListenerFactory"

        // 缓存已创建的监听器实例
        private val listeners = mutableMapOf<AndroidPermissionLevel, ActionListener>()

        /**
         * 获取指定权限级别的UI操作监听器
         * @param context Android上下文
         * @param permissionLevel 所需权限级别
         * @return 对应的UI操作监听器
         */
        fun getListener(context: Context, permissionLevel: AndroidPermissionLevel): ActionListener {
            // 检查缓存中是否已有该级别的监听器
            listeners[permissionLevel]?.let {
                return it
            }

            // 创建新的监听器实例
            val listener = when (permissionLevel) {
                AndroidPermissionLevel.ROOT -> RootActionListener(context)
                AndroidPermissionLevel.ADMIN -> AdminActionListener(context)
                AndroidPermissionLevel.DEBUGGER -> DebuggerActionListener(context)
                AndroidPermissionLevel.ACCESSIBILITY -> AccessibilityActionListener(context)
                AndroidPermissionLevel.STANDARD -> StandardActionListener(context)
            }

            // 初始化监听器
            listener.initialize()

            // 缓存监听器
            listeners[permissionLevel] = listener

            AppLogger.d(TAG, "Created action listener for permission level: $permissionLevel")
            return listener
        }

        /**
         * 获取当前设备支持的最高权限级别的监听器 按权限从高到低尝试，返回第一个可用的监听器
         * @param context Android上下文
         * @return 可用的最高权限UI操作监听器，以及权限状态
         */
        suspend fun getHighestAvailableListener(
            context: Context
        ): Pair<ActionListener, ActionListener.PermissionStatus> {

            // 按权限从高到低尝试
            val levels = listOf(
                AndroidPermissionLevel.ROOT,
                AndroidPermissionLevel.ADMIN,
                AndroidPermissionLevel.DEBUGGER,
                AndroidPermissionLevel.ACCESSIBILITY,
                AndroidPermissionLevel.STANDARD
            )

            for (level in levels) {
                val listener = getListener(context, level)
                val permStatus = listener.hasPermission()

                if (listener.isAvailable() && permStatus.granted) {
                    AppLogger.d(TAG, "Found highest available action listener: ${listener.getPermissionLevel()}")
                    return Pair(listener, permStatus)
                }
            }

            // 如果没有找到可用的监听器，返回标准监听器（至少能监听基本操作）
            AppLogger.d(TAG, "No available action listener found, falling back to STANDARD")
            val standardListener = getListener(context, AndroidPermissionLevel.STANDARD)
            return Pair(standardListener, standardListener.hasPermission())
        }

        /**
         * 获取用户首选的UI操作监听器，忽略可用性检查
         * @param context Android上下文
         * @return 用户首选的UI操作监听器
         */
        fun getUserPreferredListener(context: Context): ActionListener {
            try {
                val preferredLevel = androidPermissionPreferences.getPreferredPermissionLevel()
                // 如果preferredLevel为null，使用标准权限级别
                val actualLevel = preferredLevel ?: AndroidPermissionLevel.STANDARD
                return getListener(context, actualLevel)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error getting preferred permission level, falling back to STANDARD", e)
                return getListener(context, AndroidPermissionLevel.STANDARD)
            }
        }

        /**
         * 获取可用的最高权限UI操作监听器，用于向后兼容
         * @param context Android上下文
         * @return 可用的最高权限UI操作监听器
         */
        suspend fun getHighestAvailableListenerLegacy(context: Context): ActionListener {
            val (listener, _) = getHighestAvailableListener(context)
            return listener
        }

        /**
         * 清除特定级别的监听器缓存
         * @param permissionLevel 要清除的权限级别，null表示清除所有
         */
        fun clearCache(permissionLevel: AndroidPermissionLevel? = null) {
            if (permissionLevel != null) {
                listeners.remove(permissionLevel)
                AppLogger.d(TAG, "Cleared action listener cache for level: $permissionLevel")
            } else {
                listeners.clear()
                AppLogger.d(TAG, "Cleared all action listener caches")
            }
        }

        /**
         * 获取所有可用的监听器及其权限状态 这对于调试和显示给用户选择可用的监听方式很有用
         * @param context Android上下文
         * @return 权限级别到监听器和权限状态的映射
         */
        suspend fun getAvailableListeners(
            context: Context
        ): Map<AndroidPermissionLevel, Pair<ActionListener, ActionListener.PermissionStatus>> {
            val result = mutableMapOf<AndroidPermissionLevel, Pair<ActionListener, ActionListener.PermissionStatus>>()

            for (level in AndroidPermissionLevel.values()) {
                val listener = getListener(context, level)
                val status = listener.hasPermission()

                result[level] = Pair(listener, status)
            }

            return result
        }

        /**
         * 停止所有活跃的监听器
         * @return 停止操作是否成功
         */
        suspend fun stopAllListeners(): Boolean {
            var allStopped = true
            listeners.values.forEach { listener ->
                if (listener.isListening()) {
                    val stopped = listener.stopListening()
                    if (!stopped) {
                        allStopped = false
                        AppLogger.w(TAG, "Failed to stop listener: ${listener.getPermissionLevel()}")
                    }
                }
            }
            AppLogger.d(TAG, "All listeners stop result: $allStopped")
            return allStopped
        }
    }
} 
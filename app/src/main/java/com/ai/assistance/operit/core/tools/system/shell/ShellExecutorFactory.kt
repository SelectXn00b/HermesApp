package com.ai.assistance.operit.core.tools.system.shell

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences

/** Shell执行器工厂类 根据权限级别提供相应的执行器实例 */
class ShellExecutorFactory {
    companion object {
        private const val TAG = "ShellExecutorFactory"

        // 缓存已创建的执行器实例
        private val executors = mutableMapOf<AndroidPermissionLevel, ShellExecutor>()

        /**
         * 获取指定权限级别的Shell执行器
         * @param context Android上下文
         * @param permissionLevel 所需权限级别
         * @return 对应的Shell执行器
         */
        fun getExecutor(context: Context, permissionLevel: AndroidPermissionLevel): ShellExecutor {
            // AppLogger.d(TAG, "Requested shell executor for permission level: $permissionLevel")

            // 检查缓存中是否已有该级别的执行器
            executors[permissionLevel]?.let {
                // AppLogger.d(TAG, "Returning cached executor for level: $permissionLevel")
                return it
            }

            // 创建新的执行器实例
            val executor =
                    when (permissionLevel) {
                        AndroidPermissionLevel.ROOT -> RootShellExecutor(context)
                        AndroidPermissionLevel.ADMIN -> AdminShellExecutor(context)
                        AndroidPermissionLevel.DEBUGGER -> DebuggerShellExecutor(context)
                        AndroidPermissionLevel.ACCESSIBILITY -> AccessibilityShellExecutor(context)
                        AndroidPermissionLevel.STANDARD -> StandardShellExecutor(context)
                    }

            // 初始化执行器
            executor.initialize()

            // 缓存执行器
            executors[permissionLevel] = executor

            return executor
        }

        /**
         * 获取当前设备支持的最高权限级别的执行器 按权限从高到低尝试，返回第一个可用的执行器
         * @param context Android上下文
         * @return 可用的最高权限Shell执行器，以及权限状态
         */
        fun getHighestAvailableExecutor(
                context: Context
        ): Pair<ShellExecutor, ShellExecutor.PermissionStatus> {

            // 按权限从高到低尝试
            val levels =
                    listOf(
                            AndroidPermissionLevel.ROOT,
                            AndroidPermissionLevel.ADMIN,
                            AndroidPermissionLevel.DEBUGGER,
                            AndroidPermissionLevel.ACCESSIBILITY,
                            AndroidPermissionLevel.STANDARD
                    )

            for (level in levels) {
                val executor = getExecutor(context, level)
                val permStatus = executor.hasPermission()

                if (executor.isAvailable() && permStatus.granted) {
                    AppLogger.d(TAG, "Found highest available executor: ${executor.getPermissionLevel()}")
                    return Pair(executor, permStatus)
                }
            }

            // 如果没有找到可用的执行器，返回标准执行器（至少能执行基本命令）
            AppLogger.d(TAG, "No available executor found, falling back to STANDARD")
            val standardExecutor = getExecutor(context, AndroidPermissionLevel.STANDARD)
            return Pair(standardExecutor, standardExecutor.hasPermission())
        }

        /**
         * 获取用户首选的Shell执行器，忽略可用性检查
         * @param context Android上下文
         * @return 用户首选的Shell执行器
         */
        fun getUserPreferredExecutor(context: Context): ShellExecutor {
            try {
                val preferredLevel = androidPermissionPreferences.getPreferredPermissionLevel()
                // 如果preferredLevel为null，使用标准权限级别
                val actualLevel = preferredLevel ?: AndroidPermissionLevel.STANDARD
                return getExecutor(context, actualLevel)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error getting preferred permission level, falling back to STANDARD", e)
                return getExecutor(context, AndroidPermissionLevel.STANDARD)
            }
        }

        /**
         * 获取可用的最高权限Shell执行器，用于向后兼容
         * @param context Android上下文
         * @return 可用的最高权限Shell执行器
         */
        fun getHighestAvailableExecutorLegacy(context: Context): ShellExecutor {
            val (executor, _) = getHighestAvailableExecutor(context)
            return executor
        }

        /**
         * 清除特定级别的执行器缓存
         * @param permissionLevel 要清除的权限级别，null表示清除所有
         */
        fun clearCache(permissionLevel: AndroidPermissionLevel? = null) {
            if (permissionLevel != null) {
                executors.remove(permissionLevel)
                AppLogger.d(TAG, "Cleared executor cache for level: $permissionLevel")
            } else {
                executors.clear()
                AppLogger.d(TAG, "Cleared all executor caches")
            }
        }

        /**
         * 获取所有可用的执行器及其权限状态 这对于调试和显示给用户选择可用的执行方式很有用
         * @param context Android上下文
         * @return 权限级别到执行器和权限状态的映射
         */
        fun getAvailableExecutors(
                context: Context
        ): Map<AndroidPermissionLevel, Pair<ShellExecutor, ShellExecutor.PermissionStatus>> {
            val result =
                    mutableMapOf<
                            AndroidPermissionLevel,
                            Pair<ShellExecutor, ShellExecutor.PermissionStatus>>()

            for (level in AndroidPermissionLevel.values()) {
                val executor = getExecutor(context, level)
                val status = executor.hasPermission()

                result[level] = Pair(executor, status)
            }

            return result
        }
    }
}

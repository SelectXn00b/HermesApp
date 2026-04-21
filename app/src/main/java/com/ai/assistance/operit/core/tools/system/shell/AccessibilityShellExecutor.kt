package com.ai.assistance.operit.core.tools.system.shell

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShellIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

/**
 * 基于无障碍服务的Shell命令执行器 实现ACCESSIBILITY权限级别的命令执行
 *
 * 注意：无障碍服务不是真正的shell执行方式，但可用于模拟某些操作
 */
class AccessibilityShellExecutor(private val context: Context) : ShellExecutor {
    companion object {
        private const val TAG = "AccessibilityShellExecutor"
        private var accessibilityService: AccessibilityService? = null

        /**
         * 设置全局无障碍服务引用
         * @param service 无障碍服务实例
         */
        fun setAccessibilityService(service: AccessibilityService?) {
            accessibilityService = service
        }
    }

    override fun getPermissionLevel(): AndroidPermissionLevel = AndroidPermissionLevel.ACCESSIBILITY

    override fun isAvailable(): Boolean {
        return isAccessibilityServiceEnabled() && accessibilityService != null
    }

    override fun hasPermission(): ShellExecutor.PermissionStatus {
        val serviceEnabled = isAccessibilityServiceEnabled()
        val serviceAvailable = accessibilityService != null

        return when {
            !serviceEnabled ->
                    ShellExecutor.PermissionStatus.denied("Accessibility service is not enabled")
            !serviceAvailable ->
                    ShellExecutor.PermissionStatus.denied(
                            "Accessibility service reference is not set"
                    )
            else -> ShellExecutor.PermissionStatus.granted()
        }
    }

    override fun initialize() {
        // 无障碍服务初始化由系统控制，此处无需额外操作
    }

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        if (isAvailable()) {
            onResult(true)
            return
        }

        // 引导用户打开无障碍服务设置
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)

            // 由于无法知道用户是否启用了服务，返回false，让调用者自行处理后续检查
            onResult(false)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error opening accessibility settings", e)
            onResult(false)
        }
    }

    override suspend fun startProcess(command: String): ShellProcess {
        return AccessibilityShellProcess(command, this)
    }

    /** 检查无障碍服务是否已启用 */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceString = context.packageName + "/.accessibility.YourAccessibilityService"
        val enabledServices =
                Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                        ?: return false

        return enabledServices.contains(serviceString)
    }

    override suspend fun executeCommand(
        command: String,
        identity: ShellIdentity
    ): ShellExecutor.CommandResult =
            withContext(Dispatchers.IO) {
                val permStatus = hasPermission()
                if (!permStatus.granted) {
                    return@withContext ShellExecutor.CommandResult(false, "", permStatus.reason, -1)
                }

                AppLogger.d(TAG, "Executing command via accessibility: $command")

                // 无障碍服务不能直接执行shell命令，此处应该转换为UI操作
                // 这里仅作为一个框架，实际实现将根据应用程序需求而定

                // 目前只返回错误信息
                return@withContext ShellExecutor.CommandResult(
                        false,
                        "",
                        "Accessibility service cannot directly execute shell commands. Command was: $command",
                        -1
                )

                // 实际实现应该解析命令并转换为相应的UI自动化操作
                // 例如:
                // if (command.startsWith("tap")) {
                //     // 解析坐标
                //     // 执行点击
                //     return@withContext ShellExecutor.CommandResult(true, "Tap executed", "", 0)
                // }
            }
}

/**
 * 无障碍服务的 ShellProcess 实现
 */
private class AccessibilityShellProcess(
    private val command: String, 
    private val executor: AccessibilityShellExecutor
) : ShellProcess {
    private var completed = false
    private var exitCode = -1
    
    override val stdout: Flow<String> = callbackFlow {
        // 无障碍服务不能执行真正的shell命令，返回错误信息
        trySend("Accessibility service cannot execute shell commands directly")
        completed = true
        close()
        awaitClose { }
    }

    override val stderr: Flow<String> = callbackFlow {
        trySend("Command: $command")
        trySend("Accessibility service requires UI automation conversion")
        close()
        awaitClose { }
    }

    override val isAlive: Boolean
        get() = !completed

    override fun destroy() {
        completed = true
    }

    override suspend fun waitFor(): Int = withContext(Dispatchers.IO) {
        while (!completed) {
            kotlinx.coroutines.delay(10)
        }
        exitCode
            }
}

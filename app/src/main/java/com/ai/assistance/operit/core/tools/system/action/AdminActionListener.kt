package com.ai.assistance.operit.core.tools.system.action

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** 基于设备管理员的UI操作监听器 实现ADMIN权限级别的操作监听 */
class AdminActionListener(private val context: Context) : ActionListener {
    companion object {
        private const val TAG = "AdminActionListener"
        private var adminComponentName: ComponentName? = null

        /**
         * 设置设备管理员组件名称
         * @param componentName 设备管理员组件名称
         */
        fun setAdminComponentName(componentName: ComponentName) {
            adminComponentName = componentName
        }
    }

    private val devicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val isListening = AtomicBoolean(false)
    private var actionCallback: ((ActionListener.ActionEvent) -> Unit)? = null

    override fun getPermissionLevel(): AndroidPermissionLevel = AndroidPermissionLevel.ADMIN

    override suspend fun isAvailable(): Boolean {
        return adminComponentName != null && isDeviceAdminActive()
    }

    override suspend fun hasPermission(): ActionListener.PermissionStatus {
        if (adminComponentName == null) {
            return ActionListener.PermissionStatus.denied(context.getString(R.string.admin_device_admin_component_name_not_set))
        }

        return if (isDeviceAdminActive()) {
            ActionListener.PermissionStatus.granted()
        } else {
            ActionListener.PermissionStatus.denied(context.getString(R.string.admin_device_admin_permission_not_activated))
        }
    }

    override fun initialize() {
        AppLogger.d(TAG, "设备管理员UI操作监听器初始化完成")
    }

    override suspend fun requestPermission(onResult: (Boolean) -> Unit) {
        if (isAvailable()) {
            onResult(true)
            return
        }

        if (adminComponentName == null) {
            AppLogger.e(TAG, "管理员组件名称未设置")
            onResult(false)
            return
        }

        // 引导用户激活设备管理员
        try {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.admin_need_device_admin_permission))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)

            onResult(false)
        } catch (e: Exception) {
            AppLogger.e(TAG, "打开设备管理员设置失败", e)
            onResult(false)
        }
    }

    override fun isListening(): Boolean = isListening.get()

    override suspend fun startListening(onAction: (ActionListener.ActionEvent) -> Unit): ActionListener.ListeningResult =
        withContext(Dispatchers.IO) {
            try {
                val permStatus = hasPermission()
                if (!permStatus.granted) {
                    return@withContext ActionListener.ListeningResult.failure(permStatus.reason)
                }

                if (isListening.get()) {
                    return@withContext ActionListener.ListeningResult.failure(context.getString(R.string.admin_already_listening))
                }

                actionCallback = onAction
                isListening.set(true)

                AppLogger.d(TAG, "开始设备管理员级别的UI操作监听")

                // 启动管理员级别的事件监控
                startAdminEventMonitoring()

                return@withContext ActionListener.ListeningResult.success(context.getString(R.string.admin_ui_listener_started))
            } catch (e: Exception) {
                AppLogger.e(TAG, "启动设备管理员UI操作监听失败", e)
                isListening.set(false)
                return@withContext ActionListener.ListeningResult.failure(context.getString(R.string.admin_start_failed, e.message ?: "Unknown error"))
            }
        }

    override suspend fun stopListening(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isListening.get()) {
                return@withContext true
            }

            isListening.set(false)
            actionCallback = null

            stopAdminEventMonitoring()

            AppLogger.d(TAG, "设备管理员UI操作监听已停止")
            return@withContext true
        } catch (e: Exception) {
            AppLogger.e(TAG, "停止设备管理员UI操作监听失败", e)
            return@withContext false
        }
    }

    /** 检查设备管理员是否已激活 */
    private fun isDeviceAdminActive(): Boolean {
        return try {
            adminComponentName?.let { devicePolicyManager.isAdminActive(it) } ?: false
        } catch (e: Exception) {
            AppLogger.e(TAG, "检查设备管理员状态出错", e)
            false
        }
    }

    /**
     * 开始管理员级别的事件监控
     * 设备管理员可以监听系统状态变化、锁屏事件等
     */
    private fun startAdminEventMonitoring() {
        AppLogger.d(TAG, "开始管理员级别事件监控 - 可监听系统状态和安全事件")
        
        // 设备管理员可以监听：
        // - 锁屏/解锁事件
        // - 应用安装/卸载事件
        // - 系统安全策略变化
        // - 密码尝试失败事件
    }

    /**
     * 停止管理员级别的事件监控
     */
    private fun stopAdminEventMonitoring() {
        AppLogger.d(TAG, "停止管理员级别事件监控")
    }

    /**
     * 处理系统锁屏事件
     */
    fun handleScreenLockEvent() {
        if (isListening.get() && actionCallback != null) {
            val event = ActionListener.ActionEvent(
                timestamp = System.currentTimeMillis(),
                actionType = ActionListener.ActionType.SYSTEM_EVENT,
                additionalData = mapOf(
                    "event" to "screen_lock",
                    "source" to "device_admin"
                )
            )
            actionCallback?.invoke(event)
        }
    }

    /**
     * 处理应用状态变化事件
     * @param packageName 应用包名
     * @param event 事件类型
     */
    fun handleAppStateChange(packageName: String, event: String) {
        if (isListening.get() && actionCallback != null) {
            val actionEvent = ActionListener.ActionEvent(
                timestamp = System.currentTimeMillis(),
                actionType = ActionListener.ActionType.APP_SWITCH,
                elementInfo = ActionListener.ElementInfo(packageName = packageName),
                additionalData = mapOf(
                    "event" to event,
                    "source" to "device_admin"
                )
            )
            actionCallback?.invoke(actionEvent)
        }
    }
} 
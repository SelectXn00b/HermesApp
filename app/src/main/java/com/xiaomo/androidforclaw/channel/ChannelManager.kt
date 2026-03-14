/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/(all)
 *
 * AndroidForClaw adaptation: channel management.
 */
package com.xiaomo.androidforclaw.channel

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.xiaomo.androidforclaw.service.PhoneAccessibilityService
import com.xiaomo.androidforclaw.util.MediaProjectionHelper
import java.util.UUID

/**
 * Channel Manager - 管理 Android App Channel 的生命周期和状态
 *
 * 职责:
 * - 账号管理（创建、更新、删除）
 * - 状态追踪（连接、运行、错误）
 * - 权限检查（无障碍、悬浮窗、录屏）
 * - 系统提示词集成（Channel Hints）
 */
class ChannelManager(private val context: Context) {

    companion object {
        private const val TAG = "ChannelManager"
        private const val DEFAULT_ACCOUNT_ID = "android-device-default"
    }

    // 当前账号（单设备模式）
    private var currentAccount: ChannelAccount? = null

    // 渠道配置
    private var channelConfig = ChannelConfig()

    init {
        // 初始化默认账号
        initializeDefaultAccount()
    }

    /**
     * 初始化默认账号（单设备模式）
     */
    private fun initializeDefaultAccount() {
        val deviceId = getDeviceId()
        val account = ChannelAccount(
            accountId = DEFAULT_ACCOUNT_ID,
            name = "${android.os.Build.MODEL} (${android.os.Build.DEVICE})",
            enabled = true,
            configured = true,  // Android App 无需额外配置
            deviceId = deviceId,
            deviceModel = android.os.Build.MODEL,
            androidVersion = android.os.Build.VERSION.RELEASE,
            apiLevel = android.os.Build.VERSION.SDK_INT,
            architecture = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        )

        currentAccount = account
        channelConfig = ChannelConfig(
            enabled = true,
            defaultAccount = DEFAULT_ACCOUNT_ID,
            accounts = mapOf(DEFAULT_ACCOUNT_ID to account)
        )

        Log.d(TAG, "✅ Initialized Android App Channel")
        Log.d(TAG, "  - Account ID: ${account.accountId}")
        Log.d(TAG, "  - Device: ${account.name}")
        Log.d(TAG, "  - Android: ${account.androidVersion} (API ${account.apiLevel})")
    }

    /**
     * 获取设备 ID（唯一标识）
     */
    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "android-${UUID.randomUUID()}"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get device ID, using random UUID", e)
            "android-${UUID.randomUUID()}"
        }
    }

    /**
     * 更新账号状态（权限、连接等）
     */
    fun updateAccountStatus(): ChannelAccount {
        val current = currentAccount ?: return currentAccount!!

        val accessibilityEnabled = PhoneAccessibilityService.isAccessibilityServiceEnabled()
        val overlayPermission = Settings.canDrawOverlays(context)
        val mediaProjection = MediaProjectionHelper.isMediaProjectionGranted()

        val connected = accessibilityEnabled && overlayPermission && mediaProjection
        val running = accessibilityEnabled  // 至少需要无障碍服务

        val updatedAccount = current.copy(
            accessibilityEnabled = accessibilityEnabled,
            overlayPermission = overlayPermission,
            mediaProjection = mediaProjection,
            connected = connected,
            running = running,
            linked = connected,
            lastProbeAt = System.currentTimeMillis()
        )

        currentAccount = updatedAccount
        channelConfig = channelConfig.copy(
            accounts = mapOf(DEFAULT_ACCOUNT_ID to updatedAccount)
        )

        Log.d(TAG, "📊 Account status updated:")
        Log.d(TAG, "  - Accessibility: $accessibilityEnabled")
        Log.d(TAG, "  - Overlay: $overlayPermission")
        Log.d(TAG, "  - Media Projection: $mediaProjection")
        Log.d(TAG, "  - Connected: $connected")
        Log.d(TAG, "  - Running: $running")

        return updatedAccount
    }

    /**
     * 记录入站消息（用户发送）
     */
    fun recordInbound() {
        currentAccount = currentAccount?.copy(
            lastInboundAt = System.currentTimeMillis()
        )
    }

    /**
     * 记录出站消息（Agent 响应）
     */
    fun recordOutbound() {
        currentAccount = currentAccount?.copy(
            lastOutboundAt = System.currentTimeMillis()
        )
    }

    /**
     * 记录错误
     */
    fun recordError(error: String) {
        currentAccount = currentAccount?.copy(
            lastError = error
        )
        Log.e(TAG, "Channel error: $error")
    }

    /**
     * 记录启动
     */
    fun recordStart() {
        currentAccount = currentAccount?.copy(
            running = true,
            lastStartAt = System.currentTimeMillis()
        )
    }

    /**
     * 记录停止
     */
    fun recordStop() {
        currentAccount = currentAccount?.copy(
            running = false,
            lastStopAt = System.currentTimeMillis()
        )
    }

    /**
     * 获取当前渠道状态
     */
    fun getChannelStatus(): ChannelStatus {
        updateAccountStatus()  // 刷新状态

        return ChannelStatus(
            timestamp = System.currentTimeMillis(),
            channelId = CHANNEL_ID,
            meta = CHANNEL_META,
            capabilities = ANDROID_CHANNEL_CAPABILITIES,
            accounts = listOf(currentAccount!!),
            defaultAccountId = DEFAULT_ACCOUNT_ID
        )
    }

    /**
     * 获取当前账号
     */
    fun getCurrentAccount(): ChannelAccount {
        return currentAccount ?: throw IllegalStateException("No current account")
    }

    /**
     * 检查渠道是否可用（所有权限已授予）
     */
    fun isChannelReady(): Boolean {
        val account = currentAccount ?: return false
        return account.accessibilityEnabled &&
               account.overlayPermission &&
               account.mediaProjection
    }

    /**
     * 获取缺失的权限列表
     */
    fun getMissingPermissions(): List<String> {
        val account = currentAccount ?: return emptyList()
        val missing = mutableListOf<String>()

        if (!account.accessibilityEnabled) missing.add("Accessibility Service")
        if (!account.overlayPermission) missing.add("Display Over Apps")
        if (!account.mediaProjection) missing.add("Screen Capture")

        return missing
    }

    /**
     * 获取 Agent Prompt Hints（系统提示词集成）
     */
    fun getAgentPromptHints(): List<String> {
        val account = currentAccount
        return AndroidChannelPromptHints.getMessageToolHints(account)
    }

    /**
     * 获取 Runtime Channel 信息（Runtime Section 集成）
     */
    fun getRuntimeChannelInfo(): String {
        val account = currentAccount
        return AndroidChannelPromptHints.getRuntimeChannelInfo(account)
    }

    /**
     * 获取渠道能力描述（用于日志）
     */
    fun getCapabilitiesDescription(): String {
        return buildString {
            appendLine("Channel Capabilities:")
            appendLine("  - Chat Types: ${ANDROID_CHANNEL_CAPABILITIES.chatTypes.joinToString()}")
            appendLine("  - Media: ${ANDROID_CHANNEL_CAPABILITIES.media}")
            appendLine("  - Native Commands: ${ANDROID_CHANNEL_CAPABILITIES.nativeCommands}")
            appendLine("  - Block Streaming: ${ANDROID_CHANNEL_CAPABILITIES.blockStreaming}")
        }
    }
}

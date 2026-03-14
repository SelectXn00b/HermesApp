package com.xiaomo.androidforclaw.accessibility

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: observer permission and projection flow.
 */


import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import com.xiaomo.androidforclaw.accessibility.databinding.ActivityObserverPermissionsBinding
import kotlinx.coroutines.*
import java.io.File

/**
 * 权限请求 Activity (重构版)
 *
 * 主要改进:
 * 1. 异步权限检查 (不阻塞主线程)
 * 2. 降低检查频率 (1秒 -> 2秒)
 * 3. 事件驱动 UI 更新
 * 4. 添加详细状态说明
 * 5. 优化用户体验
 */
class PermissionActivity : Activity() {
    companion object {
        private const val TAG = "PermissionActivity"
        private const val REQUEST_CODE_MEDIA_PROJECTION = 10086
        private const val REQUEST_CODE_ACCESSIBILITY = 1001
        private const val REQUEST_CODE_MANAGE_STORAGE = 1002
        private const val STATUS_CHECK_INTERVAL = 2000L  // 2秒检查一次 (降低频率)
    }

    private lateinit var binding: ActivityObserverPermissionsBinding
    private val mainHandler = Handler(Looper.getMainLooper())

    // Coroutine scope for this activity
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 状态缓存 (避免频繁检查)
    private var cachedAccessibilityEnabled = false
    private var cachedMediaProjectionAuthorized = false
    private var cachedStorageGranted = false
    private var lastCheckTime = 0L

    // Status check job
    private var statusCheckJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        // 初始化 MediaProjectionHelper
        val workspace = File("/sdcard/.androidforclaw/workspace")
        val screenshotDir = File(workspace, "screenshots")
        MediaProjectionHelper.initialize(this, screenshotDir)

        binding = ActivityObserverPermissionsBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        setupViews()

        // 初始检查
        checkPermissionsAsync()
    }

    override fun onResume() {
        super.onResume()
        // 启动定期检查
        startStatusCheck()
    }

    override fun onPause() {
        super.onPause()
        // 停止定期检查
        stopStatusCheck()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel all coroutines
        activityScope.cancel()
        Log.d(TAG, "onDestroy called")
    }

    private fun setupViews() {
        binding.apply {
            // 无障碍服务按钮
            btnAccessibility.setOnClickListener {
                Log.d(TAG, "btnAccessibility clicked")
                requestAccessibilityPermission()
            }

            // 存储权限按钮
            btnStorage.setOnClickListener {
                Log.d(TAG, "btnStorage clicked")
                requestStoragePermission()
            }

            // 录屏权限按钮
            btnScreenCapture.setOnClickListener {
                Log.d(TAG, "btnScreenCapture clicked")
                requestMediaProjectionPermission()
            }

            // 一键授权按钮
            btnGrantAll.setOnClickListener {
                Log.d(TAG, "btnGrantAll clicked")
                grantAllPermissions()
            }

            // 重置按钮 (隐藏,用于调试)
            tvAllStatus.setOnLongClickListener {
                showResetDialog()
                true
            }
        }
    }

    /**
     * 异步检查权限状态
     */
    private fun checkPermissionsAsync() {
        activityScope.launch {
            try {
                // 在后台线程检查
                val (accessibilityEnabled, mediaProjectionAuthorized, storageGranted) = withContext(Dispatchers.IO) {
                    val accessibility = isAccessibilityServiceEnabled()
                    val mediaProjection = MediaProjectionHelper.isAuthorized()
                    val storage = isStoragePermissionGranted()
                    Triple(accessibility, mediaProjection, storage)
                }

                // 更新缓存
                cachedAccessibilityEnabled = accessibilityEnabled
                cachedMediaProjectionAuthorized = mediaProjectionAuthorized
                cachedStorageGranted = storageGranted
                lastCheckTime = System.currentTimeMillis()

                // 在主线程更新 UI
                withContext(Dispatchers.Main) {
                    updateAccessibilityUI(accessibilityEnabled)
                    updateMediaProjectionUI(mediaProjectionAuthorized)
                    updateStorageUI(storageGranted)
                    updateAllPermissionsUI(accessibilityEnabled, mediaProjectionAuthorized, storageGranted)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error checking permissions", e)
            }
        }
    }

    /**
     * 检查存储权限是否已授予
     */
    private fun isStoragePermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE
            Environment.isExternalStorageManager()
        } else {
            // Android 10 及以下检查 WRITE_EXTERNAL_STORAGE
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 启动定期状态检查
     */
    private fun startStatusCheck() {
        stopStatusCheck()

        statusCheckJob = activityScope.launch {
            while (isActive) {
                checkPermissionsAsync()
                delay(STATUS_CHECK_INTERVAL)
            }
        }

        Log.d(TAG, "Started permission status check (interval: ${STATUS_CHECK_INTERVAL}ms)")
    }

    /**
     * 停止定期状态检查
     */
    private fun stopStatusCheck() {
        statusCheckJob?.cancel()
        statusCheckJob = null
        Log.d(TAG, "Stopped permission status check")
    }

    /**
     * 更新无障碍服务 UI
     */
    private fun updateAccessibilityUI(isEnabled: Boolean) {
        binding.apply {
            if (isEnabled) {
                tvAccessibilityStatus.text = "✅ 已启用"
                tvAccessibilityStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                btnAccessibility.isEnabled = false
                btnAccessibility.text = "已启用"
                btnAccessibility.alpha = 0.5f

                tvAccessibilityDesc.text = """
                    ✅ 无障碍服务已启用

                    功能:
                    • 点击、滑动、长按
                    • 输入文本
                    • 获取界面信息
                    • 导航 (Home/Back)
                """.trimIndent()
            } else {
                tvAccessibilityStatus.text = "❌ 未启用"
                tvAccessibilityStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                btnAccessibility.isEnabled = true
                btnAccessibility.text = "去设置"
                btnAccessibility.alpha = 1.0f

                tvAccessibilityDesc.text = """
                    ⚠️ 需要启用无障碍服务

                    步骤:
                    1. 点击"去设置"按钮
                    2. 找到 "S4Claw" 或 "无障碍服务"
                    3. 开启服务开关
                    4. 授予权限
                """.trimIndent()
            }
        }
    }

    /**
     * 更新录屏权限 UI
     */
    private fun updateMediaProjectionUI(isAuthorized: Boolean) {
        val statusDetails = MediaProjectionHelper.getDetailedStatus()

        binding.apply {
            if (isAuthorized) {
                tvScreenCaptureStatus.text = "✅ 已授权"
                tvScreenCaptureStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                btnScreenCapture.isEnabled = false
                btnScreenCapture.text = "已授权"
                btnScreenCapture.alpha = 0.5f

                tvScreenCaptureDesc.text = """
                    ✅ 录屏权限已授权

                    状态: $statusDetails

                    功能:
                    • 截取屏幕画面
                    • 分析 UI 元素
                    • 辅助 Agent 观察
                """.trimIndent()
            } else {
                tvScreenCaptureStatus.text = "❌ 未授权"
                tvScreenCaptureStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                btnScreenCapture.isEnabled = true
                btnScreenCapture.text = "授予权限"
                btnScreenCapture.alpha = 1.0f

                tvScreenCaptureDesc.text = """
                    ⚠️ 需要授予录屏权限

                    状态: $statusDetails

                    说明:
                    • 点击"授予权限"按钮
                    • 在弹窗中点击"立即开始"
                    • 前台服务将自动启动

                    注意: 录屏权限需要前台服务维持
                """.trimIndent()
            }
        }
    }

    /**
     * 更新存储权限 UI
     */
    private fun updateStorageUI(isGranted: Boolean) {
        binding.apply {
            if (isGranted) {
                tvStorageStatus.text = "✅ 已授权"
                tvStorageStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                btnStorage.isEnabled = false
                btnStorage.text = "已授权"
                btnStorage.alpha = 0.5f

                tvStorageDesc.text = """
                    ✅ 存储权限已授权

                    功能:
                    • 保存截图文件
                    • 访问工作空间
                    • 读写配置文件
                """.trimIndent()
            } else {
                tvStorageStatus.text = "❌ 未授权"
                tvStorageStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                btnStorage.isEnabled = true
                btnStorage.text = "授予权限"
                btnStorage.alpha = 1.0f

                tvStorageDesc.text = """
                    ⚠️ 需要授予存储权限

                    说明:
                    • Android 11+ 需要"所有文件访问权限"
                    • 点击"授予权限"按钮
                    • 在设置中开启权限

                    注意: 存储权限用于保存截图
                """.trimIndent()
            }
        }
    }

    /**
     * 更新总体状态 UI
     */
    private fun updateAllPermissionsUI(accessibilityEnabled: Boolean, mediaProjectionAuthorized: Boolean, storageGranted: Boolean) {
        val allGranted = accessibilityEnabled && mediaProjectionAuthorized && storageGranted
        val grantedCount = listOf(accessibilityEnabled, mediaProjectionAuthorized, storageGranted).count { it }

        binding.apply {
            if (allGranted) {
                tvAllStatus.text = "✅ 所有权限已授予 (3/3)"
                tvAllStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                btnGrantAll.isEnabled = false
                btnGrantAll.text = "全部已授权"
                btnGrantAll.alpha = 0.5f
            } else {
                tvAllStatus.text = "⚠️ 已授予 $grantedCount/3 个权限"
                tvAllStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
                btnGrantAll.isEnabled = true
                btnGrantAll.text = "一键授权 (${grantedCount}/3)"
                btnGrantAll.alpha = 1.0f
            }
        }
    }

    /**
     * 请求无障碍服务权限
     */
    private fun requestAccessibilityPermission() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivityForResult(intent, REQUEST_CODE_ACCESSIBILITY)
            Toast.makeText(this, "请找到并启用 S4Claw 无障碍服务", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings", e)
            Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 请求录屏权限
     */
    private fun requestMediaProjectionPermission() {
        try {
            val needsPermission = !MediaProjectionHelper.requestPermission(this)

            if (!needsPermission) {
                Toast.makeText(this, "录屏权限已授予", Toast.LENGTH_SHORT).show()
                checkPermissionsAsync()
            } else {
                Toast.makeText(this, "请在弹窗中点击\"立即开始\"", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request media projection", e)
            Toast.makeText(this, "请求录屏权限失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 请求存储权限
     */
    private fun requestStoragePermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 需要跳转到 MANAGE_EXTERNAL_STORAGE 设置
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
                Toast.makeText(this, "请开启\"允许管理所有文件\"权限", Toast.LENGTH_LONG).show()
            } else {
                // Android 10 及以下直接请求 WRITE_EXTERNAL_STORAGE
                requestPermissions(
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_CODE_MANAGE_STORAGE
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request storage permission", e)
            Toast.makeText(this, "请求存储权限失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 一键授权所有权限
     */
    private fun grantAllPermissions() {
        if (!cachedAccessibilityEnabled) {
            requestAccessibilityPermission()
        } else if (!cachedStorageGranted) {
            requestStoragePermission()
        } else if (!cachedMediaProjectionAuthorized) {
            requestMediaProjectionPermission()
        }
    }

    /**
     * 显示重置对话框 (长按触发)
     */
    private fun showResetDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("重置权限")
            .setMessage("确定要重置所有权限吗?\n\n这将:\n• 停止前台服务\n• 清除录屏权限\n• 需要重新授权")
            .setPositiveButton("重置") { _, _ ->
                MediaProjectionHelper.releaseCompletely(this)
                Toast.makeText(this, "权限已重置", Toast.LENGTH_SHORT).show()
                mainHandler.postDelayed({ checkPermissionsAsync() }, 500)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 检查无障碍服务是否启用
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val serviceShort = "${packageName}/.service.PhoneAccessibilityService"
            val serviceFull = "${packageName}/com.xiaomo.androidforclaw.accessibility.service.PhoneAccessibilityService"
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            enabledServices?.let {
                it.contains(serviceShort) || it.contains(serviceFull)
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service", e)
            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")

        when (requestCode) {
            REQUEST_CODE_MEDIA_PROJECTION -> {
                val granted = MediaProjectionHelper.handlePermissionResult(this, requestCode, resultCode, data)

                if (granted) {
                    Toast.makeText(this, "✅ 录屏权限已授予", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "❌ 录屏权限被拒绝", Toast.LENGTH_SHORT).show()
                }

                mainHandler.postDelayed({ checkPermissionsAsync() }, 500)
            }

            REQUEST_CODE_ACCESSIBILITY -> {
                Toast.makeText(this, "正在检查无障碍服务状态...", Toast.LENGTH_SHORT).show()
                mainHandler.postDelayed({ checkPermissionsAsync() }, 1000)
            }

            REQUEST_CODE_MANAGE_STORAGE -> {
                val granted = isStoragePermissionGranted()
                if (granted) {
                    Toast.makeText(this, "✅ 存储权限已授予", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "❌ 存储权限被拒绝", Toast.LENGTH_SHORT).show()
                }
                mainHandler.postDelayed({ checkPermissionsAsync() }, 500)
            }
        }
    }
}

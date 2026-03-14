/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: Android UI layer.
 */
package com.xiaomo.androidforclaw.ui.activity

import android.content.Intent
import android.content.ComponentName
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xiaomo.androidforclaw.core.MyApplication
import com.xiaomo.androidforclaw.accessibility.AccessibilityProxy
import com.xiaomo.androidforclaw.util.MMKVKeys
import com.draco.ladb.R
import com.draco.ladb.databinding.ActivityMainBinding
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.launch

/**
 * AndroidForClaw Main Activity
 *
 * Maps OpenClaw CLI commands to visual interface:
 * - openclaw status → Status cards
 * - openclaw config → Config page
 * - openclaw skills → Skills management
 * - openclaw gateway → Gateway control
 * - openclaw sessions → Session list
 */
class MainActivity : AppCompatActivity() {

    private fun launchObserverPermissionActivity() {
        try {
            startActivity(Intent().apply {
                component = ComponentName(
                    "com.xiaomo.androidforclaw",
                    "com.xiaomo.androidforclaw.accessibility.PermissionActivity"
                )
            })
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Observer PermissionActivity unavailable, fallback to local PermissionsActivity", e)
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
    }

    private lateinit var binding: ActivityMainBinding
    private val mmkv by lazy { MMKV.defaultMMKV() }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_ACCESSIBILITY = 1001
        private const val REQUEST_OVERLAY = 1002
        private const val REQUEST_SCREEN_CAPTURE = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        updateStatusCards()
    }

    override fun onResume() {
        super.onResume()
        updateStatusCards()
    }

    private fun setupViews() {
        // Status card click events
        binding.apply {
            // Gateway card
            cardGateway.setOnClickListener {
                if (isGatewayRunning()) {
                    showGatewayInfo()
                } else {
                    Toast.makeText(this@MainActivity, "Gateway 未运行", Toast.LENGTH_SHORT).show()
                }
            }

            // Permissions card
            cardPermissions.setOnClickListener {
                launchObserverPermissionActivity()
            }

            // Skills card
            cardSkills.setOnClickListener {
                // TODO: Open Skills management page
                Toast.makeText(this@MainActivity, "Skills 管理 (开发中)", Toast.LENGTH_SHORT).show()
            }

            // Sessions card
            cardSessions.setOnClickListener {
                // TODO: Open Sessions list
                Toast.makeText(this@MainActivity, "Session 列表 (开发中)", Toast.LENGTH_SHORT).show()
            }

            // Bottom navigation buttons
            btnConfig.setOnClickListener {
                startActivity(Intent(this@MainActivity, ConfigActivity::class.java))
            }

            btnTest.setOnClickListener {
                // AgentTestActivity removed
                Toast.makeText(this@MainActivity, "Agent测试功能已废弃", Toast.LENGTH_SHORT).show()
            }

            btnLogs.setOnClickListener {
                // TODO: View logs
                Toast.makeText(this@MainActivity, "日志查看 (开发中)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Update status cards
     * Maps to OpenClaw CLI: openclaw status
     */
    private fun updateStatusCards() {
        lifecycleScope.launch {
            updateGatewayCard()
            updatePermissionsCard()
            updateSkillsCard()
            updateSessionsCard()
        }
    }

    /**
     * Update Gateway status card
     */
    private fun updateGatewayCard() {
        val isRunning = isGatewayRunning()
        binding.apply {
            tvGatewayStatus.text = if (isRunning) "运行中" else "未运行"
            tvGatewayStatus.setTextColor(
                if (isRunning) getColor(R.color.status_ok)
                else getColor(R.color.status_error)
            )

            if (isRunning) {
                tvGatewayDetails.text = "WebSocket: ws://0.0.0.0:8765\n" +
                        "Sessions: ${getSessionCount()}"
            } else {
                tvGatewayDetails.text = "Gateway 服务未启动"
            }
        }
    }

    /**
     * Update permissions status card
     */
    private fun updatePermissionsCard() {
        val accessibility = AccessibilityProxy.isConnected.value == true && AccessibilityProxy.isServiceReady()
        val overlay = Settings.canDrawOverlays(this)
        val screenCapture = AccessibilityProxy.isMediaProjectionGranted()

        val allGranted = accessibility && overlay && screenCapture

        binding.apply {
            tvPermissionsStatus.text = if (allGranted) "已授权" else "需要授权"
            tvPermissionsStatus.setTextColor(
                if (allGranted) getColor(R.color.status_ok)
                else getColor(R.color.status_warning)
            )

            tvPermissionsDetails.text = buildString {
                append("无障碍: ${if (accessibility) "✓" else "✗"}\n")
                append("悬浮窗: ${if (overlay) "✓" else "✗"}\n")
                append("录屏: ${if (screenCapture) "✓" else "✗"} (${AccessibilityProxy.getMediaProjectionStatus()})")
            }
        }
    }

    /**
     * Update Skills status card
     */
    private fun updateSkillsCard() {
        // TODO: Get actual data from SkillsLoader
        val totalSkills = 8  // Temporary data
        val alwaysSkills = 3

        binding.apply {
            tvSkillsStatus.text = "$totalSkills 个 Skills"
            tvSkillsStatus.setTextColor(getColor(R.color.status_ok))

            tvSkillsDetails.text = buildString {
                append("Always: $alwaysSkills\n")
                append("On-Demand: ${totalSkills - alwaysSkills}\n")
                append("Bundled: 8")
            }
        }
    }

    /**
     * Update Sessions status card
     */
    private fun updateSessionsCard() {
        val sessionCount = getSessionCount()

        binding.apply {
            tvSessionsStatus.text = if (sessionCount > 0) {
                "$sessionCount 个活跃会话"
            } else {
                "无活跃会话"
            }
            tvSessionsStatus.setTextColor(
                if (sessionCount > 0) getColor(R.color.status_ok)
                else getColor(R.color.text_secondary)
            )

            tvSessionsDetails.text = if (sessionCount > 0) {
                "点击查看详情"
            } else {
                "暂无活跃的 Agent 会话"
            }
        }
    }

    /**
     * Show Gateway detailed information
     * Maps to OpenClaw CLI: openclaw gateway status
     */
    private fun showGatewayInfo() {
        val info = buildString {
            append("Gateway 状态\n\n")
            append("WebSocket 端口: 8765\n")
            append("连接地址: ws://0.0.0.0:8765\n")
            append("活跃 Sessions: ${getSessionCount()}\n\n")
            append("RPC 方法:\n")
            append("  • agent - 执行 Agent 任务\n")
            append("  • agent.wait - 等待任务完成\n")
            append("  • health - 健康检查\n")
            append("  • session.list - 列出会话\n")
            append("  • session.reset - 重置会话\n")
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Gateway 信息")
            .setMessage(info)
            .setPositiveButton("关闭", null)
            .setNeutralButton("测试连接") { _, _ ->
                // TODO: 测试 WebSocket 连接
                Toast.makeText(this, "测试功能开发中", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /**
     * Show permissions dialog
     */
    private fun showPermissionsDialog() {
        val accessibility = AccessibilityProxy.isConnected.value == true && AccessibilityProxy.isServiceReady()
        val overlay = Settings.canDrawOverlays(this)
        val screenCapture = AccessibilityProxy.isMediaProjectionGranted()

        val message = buildString {
            append("权限状态:\n\n")
            append("${if (accessibility) "✓" else "✗"} 无障碍服务\n")
            if (!accessibility) {
                append("  用于: 点击、滑动、输入\n\n")
            }
            append("${if (overlay) "✓" else "✗"} 悬浮窗权限\n")
            if (!overlay) {
                append("  用于: 显示 Agent 状态\n\n")
            }
            append("${if (screenCapture) "✓" else "✗"} 录屏权限\n")
            if (!screenCapture) {
                append("  用于: 截图观察界面\n")
                append("  状态: ${AccessibilityProxy.getMediaProjectionStatus()}\n")
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("权限管理")
            .setMessage(message)
            .setPositiveButton("前往设置") { _, _ ->
                requestPermissions()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * Request permissions
     */
    private fun requestPermissions() {
        val accessibility = AccessibilityProxy.isConnected.value == true && AccessibilityProxy.isServiceReady()
        val overlay = Settings.canDrawOverlays(this)
        val screenCapture = AccessibilityProxy.isMediaProjectionGranted()

        when {
            !accessibility -> {
                // Open accessibility settings
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivityForResult(intent, REQUEST_ACCESSIBILITY)
            }
            !overlay -> {
                // Request overlay permission
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, REQUEST_OVERLAY)
            }
            !screenCapture -> {
                // Screen recording permission managed by accessibility service APK
                Toast.makeText(
                    this,
                    "录屏权限由无障碍服务 APK 管理\n请在系统设置中授予",
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                Toast.makeText(this, "所有权限已授予", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Check if Gateway is running
     */
    private fun isGatewayRunning(): Boolean {
        // TODO: Actually check GatewayService status
        // Temporary: check via Application
        return true  // Gateway started in Application.onCreate
    }

    /**
     * Get active Session count
     */
    private fun getSessionCount(): Int {
        // TODO: Get actual data from GatewayService
        return 0  // Temporary: return 0
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_ACCESSIBILITY, REQUEST_OVERLAY -> {
                // Returned from permission settings, refresh status
                updateStatusCards()
            }
        }
    }
}

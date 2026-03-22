/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.androidforclaw.ui.activity

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.xiaomo.androidforclaw.logging.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import com.xiaomo.androidforclaw.ui.compose.ForClawConnectTab
import com.xiaomo.androidforclaw.ui.compose.ForClawSettingsTab
import com.xiaomo.androidforclaw.util.ChatBroadcastReceiver
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.RootScreen
import ai.openclaw.app.ui.OpenClawTheme
import com.xiaomo.androidforclaw.accessibility.MediaProjectionHelper
import com.xiaomo.androidforclaw.ui.float.SessionFloatWindow
import com.tencent.mmkv.MMKV
import com.xiaomo.androidforclaw.util.MMKVKeys
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Check if S4Claw (observer extension) accessibility service is enabled
 *
 * Note: This method only checks system settings without blocking the thread
 */
suspend fun isS4ClawAccessibilityEnabled(context: Context): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            // Check system settings
            val accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1

            if (!accessibilityEnabled) {
                Log.d("MainActivityCompose", "System accessibility not enabled")
                return@withContext false
            }

            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return@withContext false

            // S4Claw accessibility service package name
            val s4clawServiceName = "com.xiaomo.androidforclaw.accessibility/com.xiaomo.androidforclaw.accessibility.service.PhoneAccessibilityService"

            val isEnabled = enabledServices.contains(s4clawServiceName)
            Log.d("MainActivityCompose", "S4Claw accessibility service system status: $isEnabled")

            // If system shows enabled, verify service is actually available
            if (isEnabled) {
                try {
                    val ready = com.xiaomo.androidforclaw.accessibility.AccessibilityProxy.isServiceReadyAsync()
                    Log.d("MainActivityCompose", "S4Claw accessibility service availability: $ready")
                    return@withContext ready
                } catch (e: Exception) {
                    Log.w("MainActivityCompose", "Service check failed, using system settings result", e)
                    return@withContext isEnabled
                }
            }

            isEnabled
        } catch (e: Exception) {
            Log.e("MainActivityCompose", "Failed to check S4Claw accessibility service", e)
            false
        }
    }
}

/**
 * MainActivity - Compose version
 *
 * Contains three tabs:
 * 1. Chat - AI assistant chat interface
 * 2. Status - System status cards
 * 3. Settings - Configuration and test entries
 */
class MainActivityCompose : ComponentActivity() {

    private val openClawViewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    /**
     * Workaround for Compose 1.4.x hover event crash on some MIUI devices.
     * See: https://issuetracker.google.com/issues/286991266
     */
    override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent?): Boolean {
        return try {
            super.dispatchGenericMotionEvent(ev)
        } catch (e: IllegalStateException) {
            if (e.message?.contains("HOVER_EXIT") == true) {
                Log.w("MainActivityCompose", "Suppressed Compose hover crash: ${e.message}")
                true
            } else throw e
        }
    }

    private fun launchObserverPermissionActivity() {
        try {
            startActivity(Intent().apply {
                component = android.content.ComponentName(
                    "com.xiaomo.androidforclaw",
                    "com.xiaomo.androidforclaw.accessibility.PermissionActivity"
                )
            })
        } catch (e: Exception) {
            Log.w(TAG, "Observer PermissionActivity unavailable, fallback to local PermissionsActivity", e)
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
    }

    companion object {
        private const val TAG = "MainActivityCompose"
        private const val REQUEST_MANAGE_EXTERNAL_STORAGE = 1001
    }

    private var chatBroadcastReceiver: ChatBroadcastReceiver? = null
    private var permissionChangedReceiver: android.content.BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check and request file management permission
        checkAndRequestStoragePermission()

        // Check if model setup is needed (first run, no API key configured)
        if (ModelSetupActivity.isNeeded(this)) {
            Log.i(TAG, "🔧 首次启动，打开模型配置引导...")
            startActivity(Intent(this, ModelSetupActivity::class.java))
        }

        // Auto-prune old sessions (>30 days) on startup
        lifecycleScope.launch {
            try {
                val sessionManager = com.xiaomo.androidforclaw.core.MainEntryNew.getSessionManager()
                sessionManager?.pruneOldSessions()
            } catch (_: Exception) {}
        }

        // AndroidForClaw uses ModelSetupActivity (LLM API key) instead of OpenClaw's gateway
        // onboarding. Mark onboarding as completed so RootScreen always shows PostOnboardingTabs.
        // Also pre-configure the loopback connection so the OpenClaw client connects to our
        // local GatewayWebSocketServer on ws://127.0.0.1:8765 (no TLS, no auth).
        getSharedPreferences("openclaw.node", MODE_PRIVATE).edit()
            .putBoolean("onboarding.completed", true)
            .putBoolean("gateway.manual.enabled", true)
            .putString("gateway.manual.host", "127.0.0.1")
            .putInt("gateway.manual.port", 8765)
            .putBoolean("gateway.manual.tls", false)
            .apply()

        setContent {
            // Trigger loopback connection with retry — Gateway may still be starting.
            LaunchedEffect(Unit) {
                repeat(5) { attempt ->
                    openClawViewModel.connectManual()
                    // Wait and check if connected; if so, stop retrying
                    kotlinx.coroutines.delay(if (attempt == 0) 500L else 2000L)
                    if (openClawViewModel.isConnected.value) return@LaunchedEffect
                }
            }

            OpenClawTheme {
                RootScreen(
                    viewModel = openClawViewModel,
                    connectTabSlot = { ForClawConnectTab() },
                    settingsTabSlot = { ForClawSettingsTab() },
                )
            }
        }

        // Register ADB test interface
        registerChatBroadcastReceiver()

        // Register permission change receiver (from PermissionActivity in observer module)
        permissionChangedReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
                com.xiaomo.androidforclaw.accessibility.AccessibilityProxy.refreshPermissions(ctx)
            }
        }
        val permFilter = android.content.IntentFilter("com.xiaomo.androidforclaw.PERMISSION_CHANGED")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(permissionChangedReceiver, permFilter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(permissionChangedReceiver, permFilter)
        }
    }

    override fun onStart() {
        super.onStart()
        openClawViewModel.setForeground(true)
    }

    override fun onResume() {
        super.onResume()
        // Refresh permission LiveData on every resume
        com.xiaomo.androidforclaw.accessibility.AccessibilityProxy.refreshPermissions(this)
        // Notify float window manager when main activity is visible
        SessionFloatWindow.setMainActivityVisible(true, this)
        // Silent update check on every resume (cold + warm start)
        silentUpdateCheck()
    }

    override fun onStop() {
        openClawViewModel.setForeground(false)
        super.onStop()
    }

    /**
     * Check GitHub Releases for updates in background.
     * Only shows dialog if a new version is available.
     */
    fun silentUpdateCheck() {
        lifecycleScope.launch {
            try {
                val updater = com.xiaomo.androidforclaw.updater.AppUpdater(this@MainActivityCompose)
                val info = updater.checkForUpdate()
                if (info.hasUpdate && info.downloadUrl != null) {
                    // Show update dialog on main thread
                    val sizeStr = if (info.fileSize > 0) "%.1f MB".format(info.fileSize / 1024.0 / 1024.0) else ""
                    val message = buildString {
                        append("发现新版本 v${info.latestVersion}\n")
                        append("当前版本 v${info.currentVersion}\n")
                        if (sizeStr.isNotEmpty()) append("大小: $sizeStr\n")
                        if (!info.releaseNotes.isNullOrEmpty()) {
                            append("\n${info.releaseNotes.take(200)}")
                        }
                    }

                    androidx.appcompat.app.AlertDialog.Builder(this@MainActivityCompose)
                        .setTitle("发现新版本")
                        .setMessage(message)
                        .setPositiveButton("立即更新") { _, _ ->
                            lifecycleScope.launch {
                                val success = updater.downloadAndInstall(info.downloadUrl, info.latestVersion)
                                if (!success) {
                                    try {
                                        startActivity(android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(info.releaseUrl)
                                        ))
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                        .setNegativeButton("稍后再说", null)
                        .show()
                }
            } catch (_: Exception) {
                // Silent — don't interrupt user
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Notify float window manager when main activity is not visible
        SessionFloatWindow.setMainActivityVisible(false, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterChatBroadcastReceiver()
        permissionChangedReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
    }

    /**
     * Register Chat Broadcast Receiver
     *
     * Note: Uses RECEIVER_EXPORTED to support ADB testing
     */
    private fun registerChatBroadcastReceiver() {
        chatBroadcastReceiver = ChatBroadcastReceiver { message ->
            Log.d(TAG, "📨 [BroadcastReceiver] Received message: $message")
            // Message routing handled by MyApplication.handleChatBroadcast
        }

        val filter = ChatBroadcastReceiver.createIntentFilter()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.i(TAG, "✅ Register ChatBroadcastReceiver (EXPORTED, SDK >= 33)")
            registerReceiver(chatBroadcastReceiver, filter, RECEIVER_EXPORTED)
        } else {
            Log.i(TAG, "✅ Register ChatBroadcastReceiver (SDK < 33)")
            registerReceiver(chatBroadcastReceiver, filter)
        }
    }

    /**
     * Unregister Chat Broadcast Receiver
     */
    private fun unregisterChatBroadcastReceiver() {
        chatBroadcastReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }


    /**
     * Check and request file management permission.
     * Shows an in-app dialog if permission is not granted.
     */
    private fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.i(TAG, "File management permission not granted, showing dialog...")
                showStoragePermissionDialog()
            } else {
                Log.i(TAG, "✅ File management permission granted")
            }
        } else {
            Log.i(TAG, "Android 10 and below, using traditional storage permissions")
        }
    }

    private fun showStoragePermissionDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("需要文件管理权限")
            .setMessage("ForClaw 需要「所有文件访问」权限来保存配置和聊天记录。\n\n请在接下来的设置页面中开启此权限。")
            .setCancelable(false)
            .setPositiveButton("去授权") { _, _ ->
                openStoragePermissionSettings()
            }
            .setNegativeButton("退出") { _, _ ->
                finish()
            }
            .show()
    }

    private fun openStoragePermissionSettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open file management permission settings page", e)
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE)
            } catch (e2: Exception) {
                Log.e(TAG, "Cannot open file management permission settings", e2)
                Toast.makeText(this, "无法打开权限设置，请手动授权", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_MANAGE_EXTERNAL_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Log.i(TAG, "✅ File management permission granted")
                    // Restart to apply — recreate so config/sessions init properly
                    recreate()
                } else {
                    Log.w(TAG, "⚠️ File management permission still not granted")
                    showStoragePermissionDialog()
                }
            }
        }
    }
}

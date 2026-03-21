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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaomo.androidforclaw.ui.compose.ChatScreen
import com.xiaomo.androidforclaw.ui.viewmodel.ChatViewModel
import com.xiaomo.androidforclaw.util.ChatBroadcastReceiver
import com.xiaomo.androidforclaw.util.MediaProjectionHelper
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
    private var chatViewModel: ChatViewModel? = null

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

        setContent {
            // Save ViewModel reference for BroadcastReceiver use
            val viewModel: ChatViewModel = viewModel()
            chatViewModel = viewModel

            MaterialTheme {
                MainScreen(
                    chatViewModel = viewModel,
                    onNavigateToPermissions = {
                        startActivity(Intent(this, PermissionsActivity::class.java))
                    },
                    onNavigateToSkills = {
                        startActivity(Intent(this, SkillsActivity::class.java))
                    },
                    onNavigateToConfig = {
                        Log.d("MainActivityCompose", "Clicked model configuration")
                        try {
                            startActivity(Intent(this, ModelConfigActivity::class.java))
                            Log.d("MainActivityCompose", "Successfully started ModelConfigActivity")
                        } catch (e: Exception) {
                            Log.e("MainActivityCompose", "Failed to start ConfigActivity", e)
                        }
                    },
                    onNavigateToTermux = {
                        try {
                            startActivity(Intent(this, TermuxSetupActivity::class.java))
                        } catch (e: Exception) {
                            Log.e("MainActivityCompose", "Failed to start TermuxSetupActivity", e)
                        }
                    },
                    onNavigateToTest = {
                        // AgentTestActivity has been removed
                        Toast.makeText(this, "Agent测试功能已废弃", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Register ADB test interface
        registerChatBroadcastReceiver()
    }

    override fun onResume() {
        super.onResume()
        // Notify float window manager when main activity is visible
        SessionFloatWindow.setMainActivityVisible(true, this)
        // Silent update check on every resume (cold + warm start)
        silentUpdateCheck()
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
    }

    /**
     * Register Chat Broadcast Receiver
     *
     * Note: Uses RECEIVER_EXPORTED to support ADB testing
     */
    private fun registerChatBroadcastReceiver() {
        chatBroadcastReceiver = ChatBroadcastReceiver { message ->
            Log.d(TAG, "📨 [BroadcastReceiver] Received message: $message")
            chatViewModel?.sendMessage(message)
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
     * Check and request file management permission
     */
    private fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ requires MANAGE_EXTERNAL_STORAGE permission
            if (!Environment.isExternalStorageManager()) {
                // Debug version skips permission request page to avoid jumping to Settings causing Activity to go background affecting tests
                if (com.draco.ladb.BuildConfig.SKIP_PERMISSION_REQUEST) {
                    Log.w(TAG, "⚠️ DEBUG mode: File management permission not granted, but skipping request page")
                    Log.w(TAG, "   Config file read/write may fail, please grant permission manually")
                    return
                }

                Log.i(TAG, "File management permission not granted, requesting permission...")
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE)
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot open file management permission settings page", e)
                    // Fallback to general settings page
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Cannot open file management permission settings", e2)
                    }
                }
            } else {
                Log.i(TAG, "✅ File management permission granted")
            }
        } else {
            // Android 10 and below use traditional permissions
            Log.i(TAG, "Android 10 and below, using traditional storage permissions")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_MANAGE_EXTERNAL_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Log.i(TAG, "✅ File management permission granted")
                } else {
                    Log.w(TAG, "⚠️ File management permission not granted, config file reading may fail")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    chatViewModel: ChatViewModel,
    onNavigateToPermissions: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToConfig: () -> Unit,
    onNavigateToTermux: () -> Unit,
    onNavigateToTest: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.values().forEachIndexed { index, tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> ChatTab(chatViewModel)
                1 -> StatusTab(
                    onNavigateToPermissions = onNavigateToPermissions,
                    onNavigateToSkills = onNavigateToSkills
                )
                2 -> SettingsTab(onNavigateToConfig, onNavigateToTermux, onNavigateToTest)
            }
        }
    }
}

enum class MainTab(val title: String, val icon: ImageVector) {
    CHAT("对话", Icons.Default.Chat),
    STATUS("状态", Icons.Default.Dashboard),
    SETTINGS("设置", Icons.Default.Settings)
}

@Composable
fun ChatTab(chatViewModel: ChatViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val messages by chatViewModel.messages.collectAsState()
    val isLoading by chatViewModel.isLoading.collectAsState()
    val sessions by chatViewModel.sessions.collectAsState()
    val currentSession by chatViewModel.currentSession.collectAsState()

    ChatScreen(
        messages = messages,
        onSendMessage = { message ->
            chatViewModel.sendMessage(message)
        },
        isLoading = isLoading,
        sessions = sessions,
        currentSession = currentSession,
        onSessionChange = { sessionId ->
            chatViewModel.switchSession(sessionId)
        },
        onNewSession = {
            chatViewModel.createNewSession()
        },
        onDeleteSession = { sessionId ->
            chatViewModel.deleteSession(sessionId)
        },
        onCheckUpdate = {
            // Trigger manual update check with toast feedback
            val activity = context as? MainActivityCompose
            activity?.let {
                android.widget.Toast.makeText(it, "正在检查更新...", android.widget.Toast.LENGTH_SHORT).show()
                it.lifecycleScope.launch {
                    try {
                        val updater = com.xiaomo.androidforclaw.updater.AppUpdater(it)
                        val info = updater.checkForUpdate()
                        if (info.hasUpdate) {
                            it.silentUpdateCheck()
                        } else {
                            android.widget.Toast.makeText(it, "已是最新版本 v${info.currentVersion}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(it, "检查更新失败", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusTab(
    onNavigateToPermissions: () -> Unit,
    onNavigateToSkills: () -> Unit = {}
) {
    val context = LocalContext.current

    // Dynamically get Gateway status
    val gatewayRunning = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Check if Gateway port is listening
        try {
            val result = withContext(Dispatchers.IO) {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress("127.0.0.1", 8765), 100)
                    true
                }
            }
            gatewayRunning.value = result
        } catch (e: Exception) {
            gatewayRunning.value = false
        }
    }

    // Dynamically get Skills count
    val skillsCount = remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        try {
            val loader = com.xiaomo.androidforclaw.agent.skills.SkillsLoader(context)
            val stats = loader.getStatistics()
            skillsCount.value = stats.totalSkills
        } catch (e: Exception) {
            Log.e("StatusTab", "Failed to get Skills count", e)
            skillsCount.value = 0
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "AndroidForClaw",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "AI 移动自动化平台",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 权限卡片
        PermissionsCard(onClick = onNavigateToPermissions)

        // Gateway 卡片
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Gateway",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (gatewayRunning.value) "运行中 (ws://0.0.0.0:8765)" else "未运行",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (gatewayRunning.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }

        // Skills 卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateToSkills
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Skills",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (skillsCount.value > 0) "${skillsCount.value} 个 Skills" else "加载中...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsCard(onClick: () -> Unit) {
    val context = LocalContext.current

    var accessibility by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(false) }
    var screenCapture by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }

    suspend fun refreshPermissionState() {
        withContext(Dispatchers.IO) {
            try {
                overlay = Settings.canDrawOverlays(context)

                // Step 1: Check system settings — is our accessibility service enabled?
                val systemEnabled = try {
                    val accessibilityOn = Settings.Secure.getInt(
                        context.contentResolver,
                        Settings.Secure.ACCESSIBILITY_ENABLED, 0
                    ) == 1
                    if (!accessibilityOn) false
                    else {
                        val services = Settings.Secure.getString(
                            context.contentResolver,
                            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                        ) ?: ""
                        services.contains("com.xiaomo.androidforclaw")
                    }
                } catch (e: Exception) { false }

                // Step 2: Check serviceInstance directly (covers shortcut-toggle case)
                val proxy = com.xiaomo.androidforclaw.accessibility.AccessibilityProxy
                val serviceReady = proxy.isServiceReadyAsync()
                val serviceAvailable = com.xiaomo.androidforclaw.accessibility.service.AccessibilityBinderService.serviceInstance != null

                // Show as enabled if system settings or serviceInstance is available
                accessibility = systemEnabled || serviceAvailable || serviceReady
                screenCapture = if (serviceAvailable) proxy.isMediaProjectionGranted() else false

                Log.d("PermissionsCard", "Permission status: accessibility=$accessibility (system=$systemEnabled, serviceAvailable=$serviceAvailable, serviceReady=$serviceReady), overlay=$overlay, screenCapture=$screenCapture")
            } catch (e: Exception) {
                Log.e("PermissionsCard", "Error checking permissions", e)
            }
        }
    }

    // Refresh on initial composition
    LaunchedEffect(Unit) {
        refreshPermissionState()
    }

    // Refresh every time the activity resumes (e.g. returning from system settings)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    Log.d("PermissionsCard", "ON_RESUME: refreshing permission state")
                    refreshPermissionState()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(context) {
        val accessibilityObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    refreshPermissionState()
                }
            }
        }

        val overlayObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    refreshPermissionState()
                }
            }
        }

        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            accessibilityObserver
        )
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),
            false,
            accessibilityObserver
        )
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("enabled_accessibility_services"),
            false,
            accessibilityObserver
        )
        context.contentResolver.registerContentObserver(
            Settings.canDrawOverlays(context).let { Settings.System.CONTENT_URI },
            true,
            overlayObserver
        )

        onDispose {
            context.contentResolver.unregisterContentObserver(accessibilityObserver)
            context.contentResolver.unregisterContentObserver(overlayObserver)
        }
    }

    val allGranted = accessibility && overlay && screenCapture
    val grantedCount = listOf(accessibility, overlay, screenCapture).count { it }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "权限",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    onNavigateToConfig: () -> Unit,
    onNavigateToTermux: () -> Unit,
    onNavigateToTest: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 配置按钮
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                android.util.Log.d("SettingsTab", "卡片被点击了")
                onNavigateToConfig()
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "配置"
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "模型配置",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "配置 API Key 和模型参数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Channels 按钮
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val intent = Intent(context, ChannelListActivity::class.java)
                context.startActivity(intent)
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Channels"
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Channels",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "配置多渠道接入（飞书等）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateToTermux
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Termux 配置"
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Termux 配置",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "查看 Termux 状态并尝试自动配置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 查看 openclaw.json
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val openclawJsonPath = "/sdcard/.androidforclaw/openclaw.json"
                val file = java.io.File(openclawJsonPath)
                if (file.exists()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW)
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file
                        )
                        intent.setDataAndType(uri, "text/plain")
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(intent, "选择文本编辑器"))
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "无法打开文件: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.widget.Toast.makeText(context, "文件不存在: $openclawJsonPath", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "openclaw.json"
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "openclaw.json",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "查看 OpenClaw 配置文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "/sdcard/.androidforclaw/openclaw.json",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // 测试按钮
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateToTest
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "测试"
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Agent Test",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Execute Agent Task",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 检查更新
        CheckUpdateCard()

        // 重启应用
        RestartAppCard()

        // 悬浮窗开关
        FloatWindowSwitch()

        // 关于
        AboutCard()
    }
}

@Composable
fun FloatWindowSwitch() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mmkv = remember { MMKV.defaultMMKV() }
    var isEnabled by remember {
        mutableStateOf(mmkv.decodeBool(MMKVKeys.FLOAT_WINDOW_ENABLED.key, false))
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "会话悬浮窗",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "在后台显示会话信息",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = { enabled ->
                    isEnabled = enabled
                    SessionFloatWindow.setEnabled(context, enabled)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckUpdateCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? MainActivityCompose
    val updater = remember { com.xiaomo.androidforclaw.updater.AppUpdater(context) }
    val currentVersion = remember { updater.getCurrentVersion() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            android.widget.Toast.makeText(context, "正在检查更新...", android.widget.Toast.LENGTH_SHORT).show()
            activity?.lifecycleScope?.launch {
                try {
                    val info = updater.checkForUpdate()
                    if (info.hasUpdate) {
                        activity.silentUpdateCheck()
                    } else {
                        android.widget.Toast.makeText(context, "已是最新版本 v${info.currentVersion}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "检查更新失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "检查更新"
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "检查更新",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "当前版本 v$currentVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestartAppCard() {
    val context = androidx.compose.ui.platform.LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("重启应用")
                .setMessage("将关闭并重新启动应用，重新加载所有配置和服务。\n\n确定要重启吗？")
                .setPositiveButton("重启") { _, _ ->
                    triggerRestart(context)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = "重启应用"
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "重启应用",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "重新加载配置和所有服务",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Reliable app restart using shell `am start` command.
 * This works because `am start` runs in the system_server process,
 * which survives our process death.
 */
/**
 * Reliable restart: startActivity → wait for system to create new process → kill old.
 * Key: startActivity is async. System creates the new Activity in a NEW process
 * because we use FLAG_ACTIVITY_CLEAR_TASK. After 1s delay the new process is alive,
 * then we kill the old one.
 */
/**
 * Hard restart via WorkManager: schedule relaunch → kill process.
 * WorkManager runs in a separate process/thread managed by the system,
 * so it survives our process death and reliably relaunches the app.
 */
/**
 * Restart: recreate all activities + reinitialize services.
 * Android 12+ restricts background app launches, so we do a "soft restart":
 * CLEAR_TASK destroys all activities, new MainActivityCompose.onCreate re-runs init.
 */
private fun triggerRestart(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    (context as? android.app.Activity)?.finishAffinity()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutCard() {
    val context = androidx.compose.ui.platform.LocalContext.current

    // 获取版本信息
    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
    }
    val versionName = packageInfo?.versionName ?: "Unknown"
    val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        packageInfo?.longVersionCode?.toString() ?: "Unknown"
    } else {
        @Suppress("DEPRECATION")
        packageInfo?.versionCode?.toString() ?: "Unknown"
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "关于",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Divider()

            // 邮箱
            InfoRow(
                label = "邮箱",
                value = "xiaomochn@gmail.com",
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:xiaomochn@gmail.com")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // 复制到剪贴板
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Email", "xiaomochn@gmail.com")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "邮箱已复制", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // 微信
            InfoRow(
                label = "微信",
                value = "xiaomocn",
                onClick = {
                    // 复制到剪贴板
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("WeChat ID", "xiaomocn")
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "微信号已复制", Toast.LENGTH_SHORT).show()
                }
            )

            Divider()

            // 飞书群
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val feishuUrl = "https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(feishuUrl))
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "飞书群",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "飞书体验群",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            maxLines = 2
                        )
                    }
                }
            }

            // GitHub
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val githubUrl = "https://github.com/SelectXn00b/AndroidForClaw"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "GitHub"
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "GitHub 仓库",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "查看源码、提交 Issue、参与贡献",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Divider()

            // 版本和版权信息
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "版本：$versionName ($versionCode)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Text(
                    text = "© 2024-2025 AndroidForClaw",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                Text(
                    text = "Inspired by OpenClaw",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}


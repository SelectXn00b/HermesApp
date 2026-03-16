/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: Termux one-click setup wizard.
 */
package com.xiaomo.androidforclaw.ui.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomo.androidforclaw.agent.tools.TermuxBridgeTool
import com.xiaomo.androidforclaw.agent.tools.TermuxStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TermuxSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TermuxSetupScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermuxSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bridge = remember { TermuxBridgeTool(context) }

    // Status checks
    var termuxInstalled by remember { mutableStateOf(false) }
    var termuxApiInstalled by remember { mutableStateOf(false) }
    var sshReachable by remember { mutableStateOf(false) }
    var sshConfigured by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("等待检测...") }
    var checking by remember { mutableStateOf(true) }
    var autoSettingUp by remember { mutableStateOf(false) }

    // Generate setup command
    // Minimal command — only things that MUST run inside Termux
    // SSH key, config file, whoami are handled automatically by the app
    val setupCommand = remember {
        "pkg install -y openssh python && mkdir -p ~/.ssh && cat /sdcard/.androidforclaw/.ssh/id_ed25519.pub >> ~/.ssh/authorized_keys && chmod 700 ~/.ssh && chmod 600 ~/.ssh/authorized_keys && sshd && echo '✅ Done'"
    }

    // Check status on launch and periodically
    fun checkStatus() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val status: TermuxStatus = bridge.getStatus()
                termuxInstalled = status.termuxInstalled
                termuxApiInstalled = status.termuxApiInstalled
                sshReachable = status.sshReachable
                sshConfigured = status.sshConfigPresent
                statusMessage = status.message
                checking = false
            }
        }
    }

    LaunchedEffect(Unit) {
        // Ensure BouncyCastle for SSH
        try {
            val bc = org.bouncycastle.jce.provider.BouncyCastleProvider()
            java.security.Security.removeProvider(bc.name)
            java.security.Security.insertProviderAt(bc, 1)
        } catch (_: Exception) {}
        checkStatus()
        // Auto-refresh every 3 seconds
        while (true) {
            delay(3000)
            checkStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Termux 配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                "🐧 Termux 配置向导",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "Termux 让 AI 能在手机上运行 Python、Node.js 和 Shell 命令。\n按以下步骤完成配置：",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider()

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("当前状态", style = MaterialTheme.typography.titleSmall)
                    Text("- Termux: ${if (termuxInstalled) "已安装" else "未安装"}")
                    Text("- Termux:API: ${if (termuxApiInstalled) "已安装" else "未安装"}")
                    Text("- SSH 8022: ${if (sshReachable) "可连接" else "不可连接"}")
                    Text("- SSH 配置: ${if (sshConfigured) "已生成" else "未生成"}")
                    Text("- 当前卡点: $statusMessage", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                autoSettingUp = true
                                val status = withContext(Dispatchers.IO) { bridge.triggerAutoSetup() }
                                termuxInstalled = status.termuxInstalled
                                termuxApiInstalled = status.termuxApiInstalled
                                sshReachable = status.sshReachable
                                sshConfigured = status.sshConfigPresent
                                statusMessage = status.message
                                autoSettingUp = false
                            }
                        },
                        enabled = termuxInstalled && !autoSettingUp && !(sshReachable && sshConfigured)
                    ) {
                        if (autoSettingUp) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("自动配置中...")
                        } else {
                            Text("尝试自动配置")
                        }
                    }
                }
            }

            // Step 1: Install Termux
            StepCard(
                step = 1,
                title = "安装 Termux",
                description = "从 F-Droid 安装 Termux（不要用 Play Store 版本）",
                done = termuxInstalled,
                action = if (!termuxInstalled) {
                    {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/"))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "请手动搜索安装 Termux", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else null,
                actionLabel = "去下载"
            )

            // Step 2: Open Termux and paste command
            StepCard(
                step = 2,
                title = "打开 Termux，粘贴一行命令",
                description = "复制命令 → 打开 Termux → 长按粘贴 → 回车\n（理想情况会逐步自动完成；如果失败，请看上面的具体卡点）",
                done = sshReachable && sshConfigured
            )

            // Command box
            if (termuxInstalled && !(sshReachable && sshConfigured)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = setupCommand,
                            color = Color(0xFF00FF00),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("termux-setup", setupCommand))
                            Toast.makeText(context, "✅ 已复制命令", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("复制命令")
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = context.packageManager.getLaunchIntentForPackage("com.termux")
                                if (intent != null) context.startActivity(intent)
                                else Toast.makeText(context, "Termux 未安装", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("打开 Termux")
                    }
                }
            }

            // Step 3: Verify
            StepCard(
                step = 3,
                title = "验证连接",
                description = if (sshReachable && sshConfigured) "SSH 连接正常，配置完成！"
                    else if (sshReachable) "SSH 可达，但配置文件未创建"
                    else "等待 Termux 配置完成...",
                done = sshReachable && sshConfigured
            )

            // All done
            if (sshReachable && sshConfigured) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            "完成",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "🎉 Termux 配置完成！",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "AI 现在可以通过 exec 命令执行 Python、Node.js 和 Shell 脚本了。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onBack) {
                            Text("完成")
                        }
                    }
                }
            }

            // Loading indicator
            if (checking) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
fun StepCard(
    step: Int,
    title: String,
    description: String,
    done: Boolean,
    action: (() -> Unit)? = null,
    actionLabel: String = ""
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Step number / check
            if (done) {
                Icon(
                    Icons.Default.CheckCircle,
                    "完成",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$step", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (action != null && !done) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = action) { Text(actionLabel) }
                }
            }
        }
    }
}

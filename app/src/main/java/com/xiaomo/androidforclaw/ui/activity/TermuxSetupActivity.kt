/**
 * Termux Runtime Setup — Embedded bootstrap, no SSH needed.
 */
package com.xiaomo.androidforclaw.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xiaomo.termux.BootstrapProgress
import com.xiaomo.termux.EmbeddedTermuxRuntime
import com.xiaomo.termux.RuntimeState
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    var runtimeState by remember { mutableStateOf(EmbeddedTermuxRuntime.state) }
    var progressMessage by remember { mutableStateOf("") }
    var isSettingUp by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Termux 运行时") },
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
                "🐧 Termux 运行时",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "内嵌的 Linux 运行环境，让 AI 能执行 Python、Node.js、Shell 命令。\n无需安装 Termux App，一键安装即可使用。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider()

            // Status card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("运行时状态", style = MaterialTheme.typography.titleSmall)

                    when (runtimeState) {
                        RuntimeState.READY -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    "就绪",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("已安装，可正常使用", color = Color(0xFF4CAF50))
                            }
                        }
                        RuntimeState.EXTRACTING -> {
                            Text("正在解压运行时...")
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                progressMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RuntimeState.ERROR -> {
                            Text("安装失败", color = MaterialTheme.colorScheme.error)
                            errorMessage?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        RuntimeState.NOT_INITIALIZED -> {
                            Text("未安装")
                        }
                    }
                }
            }

            // Action buttons
            if (runtimeState != RuntimeState.READY) {
                Button(
                    onClick = {
                        scope.launch {
                            isSettingUp = true
                            errorMessage = null
                            val result = EmbeddedTermuxRuntime.setup { progress ->
                                runtimeState = EmbeddedTermuxRuntime.state
                                progressMessage = progress.message
                                if (progress.state == BootstrapProgress.State.ERROR) {
                                    errorMessage = progress.message
                                }
                            }
                            runtimeState = EmbeddedTermuxRuntime.state
                            isSettingUp = false
                            if (result.isFailure) {
                                errorMessage = result.exceptionOrNull()?.message
                            }
                        }
                    },
                    enabled = !isSettingUp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSettingUp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("安装中...")
                    } else {
                        Text(if (runtimeState == RuntimeState.ERROR) "重新安装运行时" else "安装运行时")
                    }
                }
            }

            // Ready state — show done message + uninstall option
            if (runtimeState == RuntimeState.READY) {
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
                            "🎉 运行时已就绪！",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "AI 现在可以通过 exec 命令执行 Python、Node.js 和 Shell 脚本。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onBack) {
                            Text("完成")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Uninstall option
                OutlinedButton(
                    onClick = {
                        EmbeddedTermuxRuntime.uninstall()
                        runtimeState = EmbeddedTermuxRuntime.state
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("卸载运行时")
                }
            }

            // Info footer
            Spacer(Modifier.height(8.dp))
            Text(
                "运行时基于 Termux bootstrap，包含 bash、coreutils 等基础 Linux 工具。\n" +
                        "安装后可通过 exec 命令使用 pkg 安装 Python、Node.js 等。\n" +
                        "运行时存储在应用私有目录，卸载应用时会自动清除。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

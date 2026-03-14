/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: Android UI layer.
 */
package com.xiaomo.androidforclaw.ui.activity

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xiaomo.androidforclaw.config.ConfigLoader
import kotlinx.coroutines.launch

/**
 * 飞书 Channel 配置页面
 * 对齐 clawdbot-feishu 配置项
 */
class FeishuChannelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 禁止截屏
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            MaterialTheme {
                FeishuChannelScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeishuChannelScreen(onBack: () -> Unit, context: android.content.Context = androidx.compose.ui.platform.LocalContext.current) {
    val scope = rememberCoroutineScope()
    val configLoader = remember { ConfigLoader(context) }

    // 加载配置
    val openClawConfig = remember { configLoader.loadOpenClawConfig() }
    val savedConfig = remember { openClawConfig.channels.feishu }

    // 状态变量（对齐 clawdbot-feishu 配置）
    var enabled by remember { mutableStateOf(savedConfig.enabled) }
    var appId by remember { mutableStateOf(savedConfig.appId) }
    var appSecret by remember { mutableStateOf(savedConfig.appSecret) }
    var dmPolicy by remember { mutableStateOf(savedConfig.dmPolicy) }
    var groupPolicy by remember { mutableStateOf(savedConfig.groupPolicy) }
    var requireMention by remember { mutableStateOf(savedConfig.requireMention) }
    var groupAllowFrom by remember { mutableStateOf(savedConfig.groupAllowFrom.joinToString("\n")) }

    var showSaveSuccess by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feishu Channel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                // 读取当前完整配置
                                val currentConfig = configLoader.loadOpenClawConfig()

                                // 更新 feishu 配置
                                val updatedFeishuConfig = currentConfig.channels.feishu.copy(
                                    enabled = enabled,
                                    appId = appId,
                                    appSecret = appSecret,
                                    connectionMode = currentConfig.channels.feishu.connectionMode,
                                    dmPolicy = dmPolicy,
                                    groupPolicy = groupPolicy,
                                    requireMention = requireMention,
                                    groupAllowFrom = groupAllowFrom.split("\n").filter { it.isNotBlank() },
                                    historyLimit = currentConfig.channels.feishu.historyLimit,
                                    dmHistoryLimit = currentConfig.channels.feishu.dmHistoryLimit
                                )

                                // 更新完整配置
                                val updatedChannelsConfig = currentConfig.channels.copy(
                                    feishu = updatedFeishuConfig
                                )
                                val updatedConfig = currentConfig.copy(
                                    channels = updatedChannelsConfig
                                )

                                // 保存到 openclaw.json
                                configLoader.saveOpenClawConfig(updatedConfig)

                                showSaveSuccess = true
                            }
                        }
                    ) {
                        Text("保存")
                    }
                }
            )
        },
        snackbarHost = {
            if (showSaveSuccess) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    showSaveSuccess = false
                }
                Snackbar(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("配置已保存")
                }
            }
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
            // 启用开关
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "启用 Feishu Channel",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "开启后将接收飞书消息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }
            }

            // 基础配置
            Text(
                text = "基础配置",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = appId,
                onValueChange = { appId = it },
                label = { Text("App ID") },
                placeholder = { Text("cli_xxxxxx") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = appSecret,
                onValueChange = { appSecret = it },
                label = { Text("App Secret") },
                placeholder = { Text("输入 App Secret") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // DM 策略
            Text(
                text = "私聊策略 (DM Policy)",
                style = MaterialTheme.typography.titleMedium
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("open", "pairing", "allowlist").forEach { policy ->
                    FilterChip(
                        selected = dmPolicy == policy,
                        onClick = { dmPolicy = policy },
                        label = {
                            Column {
                                Text(policy.replaceFirstChar { it.uppercase() })
                                Text(
                                    text = when (policy) {
                                        "open" -> "接受所有私聊"
                                        "pairing" -> "需要配对后才能使用"
                                        "allowlist" -> "仅白名单用户"
                                        else -> "其他策略"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 群聊策略
            Text(
                text = "群聊策略 (Group Policy)",
                style = MaterialTheme.typography.titleMedium
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("open", "allowlist", "disabled").forEach { policy ->
                    FilterChip(
                        selected = groupPolicy == policy,
                        onClick = { groupPolicy = policy },
                        label = {
                            Column {
                                Text(policy.replaceFirstChar { it.uppercase() })
                                Text(
                                    text = when (policy) {
                                        "open" -> "接受所有群聊"
                                        "allowlist" -> "仅白名单群聊"
                                        "disabled" -> "禁用群聊"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 群聊白名单
            if (groupPolicy == "allowlist") {
                OutlinedTextField(
                    value = groupAllowFrom,
                    onValueChange = { groupAllowFrom = it },
                    label = { Text("群聊白名单") },
                    placeholder = { Text("每行一个群聊ID\noc_xxxxxx") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5
                )
            }

            // 群聊 @ 提及
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "群聊需要 @ 提及",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "开启后仅响应 @ 机器人的消息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = requireMention,
                        onCheckedChange = { requireMention = it }
                    )
                }
            }

            // 配置文件路径提示
            Text(
                text = "配置保存在:\n/sdcard/.androidforclaw/openclaw.json (channels.feishu)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

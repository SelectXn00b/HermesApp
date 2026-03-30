/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiaomo.androidforclaw.R
import com.xiaomo.androidforclaw.config.ConfigLoader
import kotlinx.coroutines.launch

/**
 * Discord Channel 配置页面
 */
class DiscordChannelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DiscordChannelScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordChannelScreen(
    onBack: () -> Unit,
    context: android.content.Context = androidx.compose.ui.platform.LocalContext.current
) {
    val scope = rememberCoroutineScope()
    val configLoader = remember { ConfigLoader(context) }

    // 加载配置
    val openClawConfig = remember { configLoader.loadOpenClawConfig() }
    val savedConfig = remember { openClawConfig.channels.discord }

    // 状态变量（对齐 Discord 配置）
    var enabled by remember { mutableStateOf(savedConfig?.enabled ?: false) }
    var token by remember { mutableStateOf(savedConfig?.token ?: "") }
    var name by remember { mutableStateOf(savedConfig?.name ?: "AndroidForClaw Bot") }
    var dmPolicy by remember { mutableStateOf(savedConfig?.dm?.policy ?: "pairing") }
    var groupPolicy by remember { mutableStateOf(savedConfig?.groupPolicy ?: "allowlist") }
    var replyToMode by remember { mutableStateOf(savedConfig?.replyToMode ?: "off") }
    var allowFrom by remember { mutableStateOf(savedConfig?.dm?.allowFrom?.joinToString("\n") ?: "") }

    var showSaveSuccess by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.discord_channel_title)) },
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

                                // 构建 Discord 配置
                                val updatedDiscordConfig = com.xiaomo.androidforclaw.config.DiscordChannelConfig(
                                    enabled = enabled,
                                    token = token.takeIf { it.isNotBlank() },
                                    name = name.takeIf { it.isNotBlank() },
                                    dm = com.xiaomo.androidforclaw.config.DmPolicyConfig(
                                        policy = dmPolicy,
                                        allowFrom = allowFrom.split("\n").filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
                                    ),
                                    groupPolicy = groupPolicy,
                                    replyToMode = replyToMode
                                )

                                // 更新完整配置
                                val updatedChannelsConfig = currentConfig.channels.copy(
                                    discord = updatedDiscordConfig
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
                    Text("配置已保存，需要重启应用生效")
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
                            text = "启用 Discord Channel",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "开启后将接收 Discord 消息",
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
                value = token,
                onValueChange = { token = it },
                label = { Text("Bot Token") },
                placeholder = { Text("MTxxxxxxxx.Gxxxx.xxxxxxxxxxxxxxx") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Bot Name (可选)") },
                placeholder = { Text("AndroidForClaw Bot") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // DM 策略
            Text(
                text = "DM (私聊) 策略",
                style = MaterialTheme.typography.titleLarge
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = dmPolicy == "open",
                    onClick = { dmPolicy = "open" },
                    label = { Text("Open - 接受所有 DM") },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = dmPolicy == "pairing",
                    onClick = { dmPolicy = "pairing" },
                    label = { Text("Pairing - 需要管理员审批 (推荐)") },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = dmPolicy == "allowlist",
                    onClick = { dmPolicy = "allowlist" },
                    label = { Text("Allowlist - 仅允许白名单用户") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (dmPolicy == "allowlist") {
                OutlinedTextField(
                    value = allowFrom,
                    onValueChange = { allowFrom = it },
                    label = { Text("白名单用户 ID (每行一个)") },
                    placeholder = { Text("123456789012345678\n987654321098765432") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5
                )
            }

            // Guild (服务器) 策略
            Text(
                text = "Guild (服务器) 策略",
                style = MaterialTheme.typography.titleLarge
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = groupPolicy == "open",
                    onClick = { groupPolicy = "open" },
                    label = { Text("Open - 接受所有频道 (需 @提及)") },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = groupPolicy == "allowlist",
                    onClick = { groupPolicy = "allowlist" },
                    label = { Text("Allowlist - 仅允许配置的频道 (推荐)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 回复模式
            Text(
                text = "回复模式",
                style = MaterialTheme.typography.titleLarge
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = replyToMode == "off",
                    onClick = { replyToMode = "off" },
                    label = { Text("Off - 不使用回复 (推荐)") },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = replyToMode == "always",
                    onClick = { replyToMode = "always" },
                    label = { Text("Always - 总是使用回复") },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = replyToMode == "threads",
                    onClick = { replyToMode = "threads" },
                    label = { Text("Threads - 在线程中使用回复") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 配置提示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⚠️ 配置说明",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = """
                            1. 需要先在 Discord Developer Portal 创建 Bot
                            2. 启用 MESSAGE CONTENT INTENT (特权 Intent)
                            3. 获取 Bot Token 并填入上方
                            4. 将 Bot 邀请到你的服务器
                            5. 详细配置参见：extensions/discord/SETUP_GUIDE.md
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/config/types.telegram.ts  (TelegramAccountConfig, TelegramConfig)
 */
package com.xiaomo.androidforclaw.ui.activity

import android.os.Bundle
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiaomo.androidforclaw.R
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.config.TelegramChannelConfig
import com.xiaomo.androidforclaw.ui.compose.ChannelModelPicker
import kotlinx.coroutines.launch

class TelegramChannelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TelegramChannelScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramChannelScreen(
    onBack: () -> Unit,
    context: android.content.Context = androidx.compose.ui.platform.LocalContext.current
) {
    val scope = rememberCoroutineScope()
    val configLoader = remember { ConfigLoader(context) }

    val openClawConfig = remember { configLoader.loadOpenClawConfig() }
    val savedConfig = remember { openClawConfig.channels.telegram }

    var enabled by remember { mutableStateOf(savedConfig?.enabled ?: false) }
    var botToken by remember { mutableStateOf(savedConfig?.botToken ?: "") }
    var dmPolicy by remember { mutableStateOf(savedConfig?.dmPolicy ?: "open") }
    var groupPolicy by remember { mutableStateOf(savedConfig?.groupPolicy ?: "open") }
    var requireMention by remember { mutableStateOf(savedConfig?.requireMention ?: true) }
    var historyLimitText by remember { mutableStateOf(savedConfig?.historyLimit?.toString() ?: "") }
    var streaming by remember { mutableStateOf(savedConfig?.streaming ?: "partial") }
    var webhookUrl by remember { mutableStateOf(savedConfig?.webhookUrl ?: "") }
    var model by remember { mutableStateOf(savedConfig?.model) }
    var showSaveSuccess by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.telegram_channel_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val currentConfig = configLoader.loadOpenClawConfig()
                                val updated = (currentConfig.channels.telegram ?: TelegramChannelConfig()).copy(
                                    enabled = enabled,
                                    botToken = botToken,
                                    dmPolicy = dmPolicy,
                                    groupPolicy = groupPolicy,
                                    requireMention = requireMention,
                                    historyLimit = historyLimitText.toIntOrNull(),
                                    streaming = streaming,
                                    webhookUrl = webhookUrl.takeIf { it.isNotBlank() },
                                    model = model?.takeIf { it.isNotBlank() }
                                )
                                configLoader.saveOpenClawConfig(
                                    currentConfig.copy(channels = currentConfig.channels.copy(telegram = updated))
                                )
                                showSaveSuccess = true
                            }
                        }
                    ) { Text("保存") }
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
            // ── 启用 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("启用 Telegram", style = MaterialTheme.typography.titleMedium)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Divider()

            // ── Bot Token ──
            OutlinedTextField(
                value = botToken,
                onValueChange = { botToken = it },
                label = { Text("Bot Token") },
                placeholder = { Text("从 @BotFather 获取，格式：123456:ABC-...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Divider()

            // ── DM Policy ──
            Text(stringResource(R.string.dm_policy), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("open", "pairing", "allowlist").forEach { policy ->
                    FilterChip(
                        selected = dmPolicy == policy,
                        onClick = { dmPolicy = policy },
                        label = { Text(policy) }
                    )
                }
            }

            // ── Group Policy ──
            Text(stringResource(R.string.group_policy), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("open", "allowlist", "disabled").forEach { policy ->
                    FilterChip(
                        selected = groupPolicy == policy,
                        onClick = { groupPolicy = policy },
                        label = { Text(policy) }
                    )
                }
            }

            // ── Require Mention ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("群聊需要 @提及")
                Switch(checked = requireMention, onCheckedChange = { requireMention = it })
            }

            Divider()

            // ── History Limit ──
            OutlinedTextField(
                value = historyLimitText,
                onValueChange = { historyLimitText = it.filter { c -> c.isDigit() } },
                label = { Text("历史消息条数限制（可选）") },
                placeholder = { Text("留空 = 不限制，如 50") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // ── Streaming ──
            Text("流式回复模式", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("off", "partial", "block", "progress").forEach { value ->
                    FilterChip(
                        selected = streaming == value,
                        onClick = { streaming = value },
                        label = { Text(value) }
                    )
                }
            }

            // ── Webhook URL（可选） ──
            OutlinedTextField(
                value = webhookUrl,
                onValueChange = { webhookUrl = it },
                label = { Text("Webhook URL（可选）") },
                placeholder = { Text("留空 = 使用长轮询（推荐）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Divider()

            // ── Model Picker ──
            ChannelModelPicker(
                config = openClawConfig,
                selected = model,
                onSelected = { model = it },
                modifier = Modifier.fillMaxWidth()
            )

            if (showSaveSuccess) {
                Spacer(Modifier.height(4.dp))
                Text("✅ 配置已保存", color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "配置保存后需要重启应用生效。\n获取 Bot Token：向 @BotFather 发送 /newbot。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

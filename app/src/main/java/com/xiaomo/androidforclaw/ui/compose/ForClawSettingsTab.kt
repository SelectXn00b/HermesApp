/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.androidforclaw.ui.compose

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomo.androidforclaw.R
import com.xiaomo.androidforclaw.accessibility.AccessibilityProxy
import com.xiaomo.androidforclaw.agent.skills.SkillsLoader
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.workspace.StoragePaths
import com.xiaomo.androidforclaw.ui.activity.*
import com.xiaomo.androidforclaw.ui.activity.LegalActivity
import com.xiaomo.androidforclaw.ui.float.SessionFloatWindow
import com.xiaomo.androidforclaw.updater.AppUpdater
import com.xiaomo.androidforclaw.util.MMKVKeys
import com.tencent.mmkv.MMKV
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ForClawSettingsTab() {
    val context = LocalContext.current

    // ── 状态数据 ──────────────────────────────────────────────
    val loadingText = stringResource(R.string.connect_loading)
    var providerName by remember { mutableStateOf(loadingText) }
    var modelId by remember { mutableStateOf("") }
    var apiKeyOk by remember { mutableStateOf(false) }
    var gatewayRunning by remember { mutableStateOf(false) }
    var skillsCount by remember { mutableStateOf(0) }
    var feishuEnabled by remember { mutableStateOf(false) }
    var discordEnabled by remember { mutableStateOf(false) }
    var slackEnabled by remember { mutableStateOf(false) }
    var telegramEnabled by remember { mutableStateOf(false) }
    var whatsappEnabled by remember { mutableStateOf(false) }
    var signalEnabled by remember { mutableStateOf(false) }
    var weixinEnabled by remember { mutableStateOf(false) }

    val accessibilityOk by AccessibilityProxy.isConnected.observeAsState(false)
    val overlayOk by AccessibilityProxy.overlayGranted.observeAsState(false)
    val screenCaptureOk by AccessibilityProxy.screenCaptureGranted.observeAsState(false)

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val loader = ConfigLoader(context)
                val config = loader.loadOpenClawConfig()
                val providers = config.resolveProviders()
                val resolvedModel = config.resolveDefaultModel()
                val resolvedProvider = resolvedModel.substringBefore("/", "")
                val entry = if (resolvedProvider.isNotEmpty()) {
                    providers[resolvedProvider]?.let { resolvedProvider to it }
                } else {
                    providers.entries.firstOrNull()?.let { it.key to it.value }
                }
                if (entry != null) {
                    providerName = entry.first
                    modelId = resolvedModel
                    val key = entry.second.apiKey
                    apiKeyOk = !key.isNullOrBlank() && !key.startsWith("\${") && key != "未配置"
                } else {
                    providerName = context.getString(R.string.connect_api_not_configured)
                    apiKeyOk = false
                }
                feishuEnabled = config.channels.feishu.enabled && config.channels.feishu.appId.isNotBlank()
                discordEnabled = config.channels.discord?.let { it.enabled && !it.token.isNullOrBlank() } ?: false
                slackEnabled = config.channels.slack?.let { it.enabled && it.botToken.isNotBlank() } ?: false
                telegramEnabled = config.channels.telegram?.let { it.enabled && it.botToken.isNotBlank() } ?: false
                whatsappEnabled = config.channels.whatsapp?.let { it.enabled && it.phoneNumber.isNotBlank() } ?: false
                signalEnabled = config.channels.signal?.let { it.enabled && it.phoneNumber.isNotBlank() } ?: false
                weixinEnabled = config.channels.weixin?.let { it.enabled } ?: false
            } catch (_: Exception) {
                providerName = context.getString(R.string.connect_read_failed)
            }
            gatewayRunning = com.xiaomo.androidforclaw.core.MyApplication.isGatewayRunning()
            try { skillsCount = SkillsLoader(context).getStatistics().totalSkills } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── 状态总览 ──────────────────────────────────────────
        val notConfigured = stringResource(R.string.connect_api_not_configured)
        val configured = stringResource(R.string.connect_api_configured)

        // LLM API
        StatusCard(
            title = stringResource(R.string.connect_llm_api),
            icon = Icons.Default.SmartToy,
            rows = listOf(
                StatusRow(stringResource(R.string.connect_provider), providerName.ifBlank { notConfigured }),
                StatusRow(stringResource(R.string.connect_default_model), modelId.ifBlank { "—" }),
                StatusRow(stringResource(R.string.connect_api_key), if (apiKeyOk) configured else notConfigured, if (apiKeyOk) StatusLevel.Ok else StatusLevel.Error),
            ),
            onClick = { context.startActivity(Intent(context, ModelConfigActivity::class.java)) },
            clickLabel = stringResource(R.string.connect_modify_config),
        )

        // Gateway
        StatusCard(
            title = stringResource(R.string.connect_local_gateway),
            icon = Icons.Default.Router,
            rows = listOf(
                StatusRow(stringResource(R.string.connect_port_label), "ws://127.0.0.1:8765"),
                StatusRow(stringResource(R.string.connect_status_label), if (gatewayRunning) stringResource(R.string.connect_running) else stringResource(R.string.connect_not_running),
                    if (gatewayRunning) StatusLevel.Ok else StatusLevel.Neutral),
            ),
        )

        // Web Clipboard
        val localIp = remember {
            try {
                java.net.NetworkInterface.getNetworkInterfaces()?.toList()
                    ?.flatMap { it.inetAddresses.toList() }
                    ?.firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                    ?.hostAddress ?: "未连接 WiFi"
            } catch (_: Exception) { "获取失败" }
        }
        val clipboardUrl = if (localIp.contains(".")) "http://$localIp:19789/clipboard" else localIp
        StatusCard(
            title = "Web Clipboard",
            icon = Icons.Default.ContentPaste,
            rows = listOf(
                StatusRow("地址", clipboardUrl),
                StatusRow("用途", "电脑输入 → 手机剪切板"),
            ),
            onClick = {
                if (clipboardUrl.startsWith("http")) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(clipboardUrl)))
                }
            },
            clickLabel = "打开",
        )

        // Channels
        val enabled = stringResource(R.string.connect_enabled)
        val channelEntries = buildList {
            if (feishuEnabled)   add(StatusRow(stringResource(R.string.connect_feishu), enabled, StatusLevel.Ok))
            if (discordEnabled)  add(StatusRow("Discord",  enabled, StatusLevel.Ok))
            if (telegramEnabled) add(StatusRow("Telegram", enabled, StatusLevel.Ok))
            if (slackEnabled)    add(StatusRow("Slack",    enabled, StatusLevel.Ok))
            if (whatsappEnabled) add(StatusRow("WhatsApp", enabled, StatusLevel.Ok))
            if (signalEnabled)   add(StatusRow("Signal",   enabled, StatusLevel.Ok))
            if (weixinEnabled)   add(StatusRow(stringResource(R.string.connect_weixin), enabled, StatusLevel.Ok))
        }
        StatusCard(
            title = stringResource(R.string.connect_channels),
            icon = Icons.Default.Hub,
            rows = channelEntries.ifEmpty {
                listOf(StatusRow(stringResource(R.string.connect_channels), notConfigured, StatusLevel.Neutral))
            },
            onClick = {
                context.startActivity(Intent().apply {
                    setClassName(context, "com.xiaomo.androidforclaw.ui.activity.ChannelListActivity")
                })
            },
            clickLabel = stringResource(R.string.connect_manage),
        )

        // MCP Server
        val mcpRunning = remember { mutableStateOf(com.xiaomo.androidforclaw.mcp.ObserverMcpServer.isRunning()) }
        StatusCard(
            title = stringResource(R.string.connect_mcp_server),
            icon = Icons.Default.Dns,
            rows = listOf(
                StatusRow(stringResource(R.string.connect_status_label), if (mcpRunning.value) stringResource(R.string.connect_running) else stringResource(R.string.connect_mcp_stopped),
                    if (mcpRunning.value) StatusLevel.Ok else StatusLevel.Neutral),
                StatusRow(stringResource(R.string.connect_port_label), "${com.xiaomo.androidforclaw.mcp.ObserverMcpServer.DEFAULT_PORT}"),
            ),
            onClick = {
                context.startActivity(Intent(context, com.xiaomo.androidforclaw.ui.activity.McpConfigActivity::class.java))
            },
            clickLabel = stringResource(R.string.connect_mcp_config),
        )

        // 权限
        val allPermissionsOk = accessibilityOk && screenCaptureOk
        val granted = stringResource(R.string.connect_granted)
        val notGranted = stringResource(R.string.connect_not_granted)
        StatusCard(
            title = stringResource(R.string.connect_permissions),
            icon = Icons.Default.Security,
            rows = listOf(
                StatusRow(stringResource(R.string.connect_accessibility), if (accessibilityOk) granted else notGranted,
                    if (accessibilityOk) StatusLevel.Ok else StatusLevel.Error),
                StatusRow(stringResource(R.string.connect_overlay), if (overlayOk) granted else notGranted,
                    if (overlayOk) StatusLevel.Ok else StatusLevel.Neutral),
                StatusRow(stringResource(R.string.connect_screen_capture), if (screenCaptureOk) granted else notGranted,
                    if (screenCaptureOk) StatusLevel.Ok else StatusLevel.Error),
            ),
            onClick = {
                try {
                    context.startActivity(Intent().apply {
                        component = ComponentName(
                            "com.xiaomo.androidforclaw",
                            "com.xiaomo.androidforclaw.accessibility.PermissionActivity"
                        )
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    })
                } catch (_: Exception) {
                    context.startActivity(Intent(context, com.xiaomo.androidforclaw.ui.activity.PermissionsActivity::class.java))
                }
            },
            clickLabel = if (allPermissionsOk) stringResource(R.string.connect_view) else stringResource(R.string.connect_go_grant),
        )

        // ── 配置 ─────────────────────────────────────────────────
        SettingsSection(stringResource(R.string.settings_section_config)) {
            SettingsNavItem(
                icon = Icons.Default.Terminal,
                title = stringResource(R.string.settings_termux),
                subtitle = stringResource(R.string.settings_termux_desc),
                onClick = { context.startActivity(Intent(context, TermuxSetupActivity::class.java)) }
            )
        }

        // ── 文件 ─────────────────────────────────────────────────
        SettingsSection(stringResource(R.string.settings_section_files)) {
            SettingsNavItem(
                icon = Icons.Default.Description,
                title = "openclaw.json",
                subtitle = StoragePaths.openclawConfig.absolutePath,
                onClick = {
                    val file = StoragePaths.openclawConfig
                    if (file.exists()) {
                        try {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.provider", file
                            )
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "text/plain")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                    context.getString(R.string.settings_select_editor)
                                )
                            )
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, context.getString(R.string.settings_cannot_open, e.message ?: ""), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        android.widget.Toast.makeText(context, context.getString(R.string.settings_file_not_found), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // ── 界面 ─────────────────────────────────────────────────
        SettingsSection(stringResource(R.string.settings_section_ui)) {
            FloatWindowToggleItem()
        }

        // ── 应用 ─────────────────────────────────────────────────
        SettingsSection(stringResource(R.string.settings_section_app)) {
            CheckUpdateItem()
            RestartAppItem()
        }

        // ── 法律 ─────────────────────────────────────────────────
        SettingsSection(stringResource(R.string.settings_section_legal)) {
            SettingsNavItem(
                icon = Icons.Default.Policy,
                title = stringResource(R.string.settings_privacy_policy),
                subtitle = stringResource(R.string.settings_privacy_desc),
                onClick = { LegalActivity.start(context, LegalActivity.TYPE_PRIVACY) }
            )
            SettingsNavItem(
                icon = Icons.Default.Gavel,
                title = stringResource(R.string.settings_terms),
                subtitle = stringResource(R.string.settings_terms_desc),
                onClick = { LegalActivity.start(context, LegalActivity.TYPE_TERMS) }
            )
        }

        // ── 关于 ─────────────────────────────────────────────────
        AboutSection()
    }
}

// ─── Section wrapper ─────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            content()
        }
    }
}

// ─── Nav item ────────────────────────────────────────────────────────────────

@Composable
private fun SettingsNavItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ─── Status card ─────────────────────────────────────────────────────────────

private enum class StatusLevel { Ok, Error, Neutral }

private data class StatusRow(
    val label: String,
    val value: String,
    val level: StatusLevel = StatusLevel.Neutral,
)

@Composable
private fun StatusCard(
    title: String,
    icon: ImageVector,
    rows: List<StatusRow>,
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Text(title, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                if (onClick != null && clickLabel != null) {
                    Text(
                        text = clickLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onClick),
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.label, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = row.value,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                        ),
                        color = when (row.level) {
                            StatusLevel.Ok -> MaterialTheme.colorScheme.primary
                            StatusLevel.Error -> MaterialTheme.colorScheme.error
                            StatusLevel.Neutral -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

// ─── Specific items ───────────────────────────────────────────────────────────

@Composable
private fun FloatWindowToggleItem() {
    val context = LocalContext.current
    val mmkv = remember { MMKV.defaultMMKV() }
    var enabled by remember { mutableStateOf(mmkv.decodeBool(MMKVKeys.FLOAT_WINDOW_ENABLED.key, false)) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Default.PictureInPicture,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_float_window), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_float_window_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { v ->
                    enabled = v
                    SessionFloatWindow.setEnabled(context, v)
                }
            )
        }
    }
}

@Composable
private fun CheckUpdateItem() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val updater = remember { AppUpdater(context) }
    val currentVersion = remember { updater.getCurrentVersion() }

    SettingsNavItem(
        icon = Icons.Default.SystemUpdate,
        title = stringResource(R.string.settings_check_update),
        subtitle = stringResource(R.string.settings_current_version, currentVersion),
        onClick = {
            android.widget.Toast.makeText(context, context.getString(R.string.settings_checking_update), android.widget.Toast.LENGTH_SHORT).show()
            lifecycleOwner.lifecycleScope.launch {
                try {
                    val info = updater.checkForUpdate()
                    if (info.hasUpdate && info.downloadUrl != null) {
                        val success = updater.downloadAndInstall(info.downloadUrl, info.latestVersion)
                        if (!success) {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl))
                                )
                            } catch (_: Exception) {}
                        }
                    } else {
                        android.widget.Toast.makeText(context, context.getString(R.string.settings_up_to_date, info.currentVersion), android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, context.getString(R.string.settings_check_failed, e.message ?: ""), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    )
}

@Composable
private fun RestartAppItem() {
    val context = LocalContext.current

    SettingsNavItem(
        icon = Icons.Default.RestartAlt,
        title = stringResource(R.string.settings_restart_app),
        subtitle = stringResource(R.string.settings_restart_desc),
        onClick = {
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.settings_restart_title))
                .setMessage(context.getString(R.string.settings_restart_message))
                .setPositiveButton(context.getString(R.string.settings_restart_confirm)) { _, _ ->
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    intent?.let { context.startActivity(it) }
                    (context as? android.app.Activity)?.finishAffinity()
                }
                .setNegativeButton(context.getString(R.string.action_cancel), null)
                .show()
        }
    )
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val packageInfo = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (_: Exception) { null }
    }
    val versionName = packageInfo?.versionName ?: stringResource(R.string.unknown)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = stringResource(R.string.settings_section_about),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            AboutRow(stringResource(R.string.settings_version), "v$versionName")
            AboutRow(stringResource(R.string.settings_email), "xiaomochn@gmail.com", onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:xiaomochn@gmail.com") })
                } catch (_: Exception) {
                    val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("Email", "xiaomochn@gmail.com"))
                    android.widget.Toast.makeText(context, context.getString(R.string.settings_copied), android.widget.Toast.LENGTH_SHORT).show()
                }
            })
            AboutRow(stringResource(R.string.settings_wechat), "xiaomocn", onClick = {
                val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cb.setPrimaryClip(android.content.ClipData.newPlainText("WeChat", "xiaomocn"))
                android.widget.Toast.makeText(context, context.getString(R.string.settings_copied), android.widget.Toast.LENGTH_SHORT).show()
            })
            AboutRow(stringResource(R.string.settings_feishu_group), stringResource(R.string.settings_feishu_join), onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74"
                    )))
                } catch (_: Exception) {
                    android.widget.Toast.makeText(context, context.getString(R.string.settings_cannot_open_link), android.widget.Toast.LENGTH_SHORT).show()
                }
            })
            AboutRow(stringResource(R.string.settings_github), stringResource(R.string.settings_github_desc), onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://github.com/SelectXn00b/AndroidForClaw"
                    )))
                } catch (_: Exception) {
                    android.widget.Toast.makeText(context, context.getString(R.string.settings_cannot_open_link), android.widget.Toast.LENGTH_SHORT).show()
                }
            })
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    stringResource(R.string.settings_copyright),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.settings_inspired),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

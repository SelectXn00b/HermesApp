/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.androidforclaw.ui.compose

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomo.androidforclaw.accessibility.AccessibilityProxy
import com.xiaomo.androidforclaw.agent.skills.SkillsLoader
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.ui.activity.ModelConfigActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AndroidForClaw 状态 tab，替换 OpenClaw 的 Gateway Connection 页面。
 * 显示：LLM API 配置、Gateway、Channels、Skills、权限。
 */
@Composable
fun ForClawConnectTab() {
    val context = LocalContext.current

    // ── LLM 配置 ──────────────────────────────────────────────
    var providerName by remember { mutableStateOf("加载中...") }
    var modelId by remember { mutableStateOf("") }
    var apiKeyOk by remember { mutableStateOf(false) }

    // ── Gateway (port 8765) ────────────────────────────────────
    var gatewayRunning by remember { mutableStateOf(false) }

    // ── Skills ─────────────────────────────────────────────────
    var skillsCount by remember { mutableStateOf(0) }

    // ── Channels ───────────────────────────────────────────────
    var feishuEnabled by remember { mutableStateOf(false) }
    var discordEnabled by remember { mutableStateOf(false) }

    // ── 权限（LiveData 实时同步）────────────────────────────────
    val accessibilityOk by AccessibilityProxy.isConnected.observeAsState(false)
    val overlayOk by AccessibilityProxy.overlayGranted.observeAsState(false)
    val screenCaptureOk by AccessibilityProxy.screenCaptureGranted.observeAsState(false)

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // LLM config
            try {
                val loader = ConfigLoader(context)
                val config = loader.loadOpenClawConfig()
                val providers = config.resolveProviders()
                val entry = providers.entries.firstOrNull()
                if (entry != null) {
                    providerName = entry.key
                    modelId = entry.value.models.firstOrNull()?.id ?: config.resolveDefaultModel()
                    val key = entry.value.apiKey
                    apiKeyOk = !key.isNullOrBlank() && !key.startsWith("\${") && key != "未配置"
                } else {
                    providerName = "未配置"
                    apiKeyOk = false
                }

                // Channels
                feishuEnabled = config.channels.feishu.enabled &&
                        config.channels.feishu.appId.isNotBlank()
                discordEnabled = config.channels.discord?.let {
                    it.enabled && !it.token.isNullOrBlank()
                } ?: false
            } catch (_: Exception) {
                providerName = "读取失败"
            }

            // Gateway (local in-process channel)
            gatewayRunning = com.xiaomo.androidforclaw.core.MyApplication.isGatewayRunning()

            // Skills
            try {
                skillsCount = SkillsLoader(context).getStatistics().totalSkills
            } catch (_: Exception) {}

            // 权限状态由 AccessibilityProxy LiveData 驱动，无需在此手动检查
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── LLM API 配置 ──────────────────────────────────────
        StatusCard(
            title = "LLM API",
            icon = Icons.Default.SmartToy,
            rows = listOf(
                StatusRow("服务商", providerName.ifBlank { "未配置" }),
                StatusRow("默认模型", modelId.ifBlank { "—" }),
                StatusRow("API Key", if (apiKeyOk) "已配置" else "未配置", if (apiKeyOk) StatusLevel.Ok else StatusLevel.Error),
            ),
            onClick = {
                context.startActivity(Intent(context, ModelConfigActivity::class.java))
            },
            clickLabel = "修改配置",
        )

        // ── Gateway ───────────────────────────────────────────
        StatusCard(
            title = "本地 Gateway",
            icon = Icons.Default.Router,
            rows = listOf(
                StatusRow("端口", "ws://127.0.0.1:8765"),
                StatusRow("状态", if (gatewayRunning) "运行中" else "未运行",
                    if (gatewayRunning) StatusLevel.Ok else StatusLevel.Neutral),
            ),
        )

        // ── Channels ──────────────────────────────────────────
        StatusCard(
            title = "Channels",
            icon = Icons.Default.Hub,
            rows = buildList {
                add(StatusRow("飞书", if (feishuEnabled) "已启用" else "未配置",
                    if (feishuEnabled) StatusLevel.Ok else StatusLevel.Neutral))
                add(StatusRow("Discord", if (discordEnabled) "已启用" else "未配置",
                    if (discordEnabled) StatusLevel.Ok else StatusLevel.Neutral))
            },
            onClick = {
                context.startActivity(
                    Intent().apply {
                        setClassName(
                            context,
                            "com.xiaomo.androidforclaw.ui.activity.ChannelListActivity"
                        )
                    }
                )
            },
            clickLabel = "管理",
        )

        // ── Skills ────────────────────────────────────────────
        StatusCard(
            title = "Skills",
            icon = Icons.Default.Build,
            rows = listOf(
                StatusRow("已加载", if (skillsCount > 0) "$skillsCount 个" else "加载中..."),
            ),
        )

        // ── MCP Server（给外部 Agent 用，非 AndroidForClaw 自身）───
        val mcpRunning = remember { mutableStateOf(com.xiaomo.androidforclaw.mcp.ObserverMcpServer.isRunning()) }
        StatusCard(
            title = "MCP Server",
            icon = Icons.Default.Dns,
            rows = listOf(
                StatusRow("状态", if (mcpRunning.value) "运行中" else "已停止",
                    if (mcpRunning.value) StatusLevel.Ok else StatusLevel.Neutral),
                StatusRow("端口", "${com.xiaomo.androidforclaw.mcp.ObserverMcpServer.DEFAULT_PORT}"),
            ),
            onClick = {
                context.startActivity(Intent(context,
                    com.xiaomo.androidforclaw.ui.activity.McpConfigActivity::class.java))
            },
            clickLabel = "配置",
        )

        // ── 权限 ─────────────────────────────────────────────
        val allPermissionsOk = accessibilityOk && overlayOk && screenCaptureOk
        StatusCard(
            title = "权限",
            icon = Icons.Default.Security,
            rows = listOf(
                StatusRow("无障碍", if (accessibilityOk) "已授权" else "未授权",
                    if (accessibilityOk) StatusLevel.Ok else StatusLevel.Error),
                StatusRow("悬浮窗", if (overlayOk) "已授权" else "未授权",
                    if (overlayOk) StatusLevel.Ok else StatusLevel.Error),
                StatusRow("录屏", if (screenCaptureOk) "已授权" else "未授权",
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
                    context.startActivity(Intent(context,
                        com.xiaomo.androidforclaw.ui.activity.PermissionsActivity::class.java))
                }
            },
            clickLabel = if (allPermissionsOk) "查看" else "去授权",
        )
    }
}

// ─── helpers ──────────────────────────────────────────────────────────────────

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
            // Header row
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

            // Data rows
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

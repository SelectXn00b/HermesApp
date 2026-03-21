package com.xiaomo.androidforclaw.ui.compose

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.tencent.mmkv.MMKV
import com.xiaomo.androidforclaw.ui.activity.*
import com.xiaomo.androidforclaw.ui.float.SessionFloatWindow
import com.xiaomo.androidforclaw.updater.AppUpdater
import com.xiaomo.androidforclaw.util.MMKVKeys
import kotlinx.coroutines.launch

@Composable
fun ForClawSettingsTab() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── 配置 ─────────────────────────────────────────────────
        SettingsSection("配置") {
            SettingsNavItem(
                icon = Icons.Default.SmartToy,
                title = "模型配置",
                subtitle = "API Key 和模型参数",
                onClick = { context.startActivity(Intent(context, ModelConfigActivity::class.java)) }
            )
            SettingsNavItem(
                icon = Icons.Default.Hub,
                title = "Channels",
                subtitle = "飞书、Discord 等多渠道接入",
                onClick = { context.startActivity(Intent(context, ChannelListActivity::class.java)) }
            )
            SettingsNavItem(
                icon = Icons.Default.Extension,
                title = "Skills",
                subtitle = "管理 Agent Skills",
                onClick = { context.startActivity(Intent(context, SkillsActivity::class.java)) }
            )
            SettingsNavItem(
                icon = Icons.Default.Terminal,
                title = "Termux 配置",
                subtitle = "查看状态并自动配置 Termux 环境",
                onClick = { context.startActivity(Intent(context, TermuxSetupActivity::class.java)) }
            )
        }

        // ── 文件 ─────────────────────────────────────────────────
        SettingsSection("文件") {
            SettingsNavItem(
                icon = Icons.Default.Description,
                title = "openclaw.json",
                subtitle = "/sdcard/.androidforclaw/openclaw.json",
                onClick = {
                    val file = java.io.File("/sdcard/.androidforclaw/openclaw.json")
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
                                    "选择文本编辑器"
                                )
                            )
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "无法打开: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        android.widget.Toast.makeText(context, "文件不存在", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // ── 界面 ─────────────────────────────────────────────────
        SettingsSection("界面") {
            FloatWindowToggleItem()
        }

        // ── 应用 ─────────────────────────────────────────────────
        SettingsSection("应用") {
            CheckUpdateItem()
            RestartAppItem()
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
                Text("会话悬浮窗", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "在后台显示会话信息",
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
        title = "检查更新",
        subtitle = "当前版本 v$currentVersion",
        onClick = {
            android.widget.Toast.makeText(context, "正在检查更新...", android.widget.Toast.LENGTH_SHORT).show()
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
                        android.widget.Toast.makeText(context, "已是最新版本 v${info.currentVersion}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "检查失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
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
        title = "重启应用",
        subtitle = "重新加载配置和所有服务",
        onClick = {
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("重启应用")
                .setMessage("将关闭并重新启动应用，重新加载所有配置和服务。\n\n确定要重启吗？")
                .setPositiveButton("重启") { _, _ ->
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    intent?.let { context.startActivity(it) }
                    (context as? android.app.Activity)?.finishAffinity()
                }
                .setNegativeButton("取消", null)
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
    val versionName = packageInfo?.versionName ?: "Unknown"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = "关于",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            AboutRow("版本", "v$versionName")
            AboutRow("邮箱", "xiaomochn@gmail.com", onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:xiaomochn@gmail.com") })
                } catch (_: Exception) {
                    val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("Email", "xiaomochn@gmail.com"))
                    android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                }
            })
            AboutRow("微信", "xiaomocn", onClick = {
                val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cb.setPrimaryClip(android.content.ClipData.newPlainText("WeChat", "xiaomocn"))
                android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
            })
            AboutRow("飞书体验群", "点击加入", onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74"
                    )))
                } catch (_: Exception) {
                    android.widget.Toast.makeText(context, "无法打开链接", android.widget.Toast.LENGTH_SHORT).show()
                }
            })
            AboutRow("GitHub 仓库", "查看源码 / 提交 Issue", onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://github.com/SelectXn00b/AndroidForClaw"
                    )))
                } catch (_: Exception) {
                    android.widget.Toast.makeText(context, "无法打开链接", android.widget.Toast.LENGTH_SHORT).show()
                }
            })
            // 版权信息
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "© 2024-2025 AndroidForClaw",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Inspired by OpenClaw",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val mod = if (onClick != null)
        Modifier.fillMaxWidth()
    else
        Modifier.fillMaxWidth()

    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = mod,
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

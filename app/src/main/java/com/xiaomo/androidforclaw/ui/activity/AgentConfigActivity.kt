/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/config/types.openclaw.ts (agents.list, models.providers, channels.*, bindings)
 *
 * AndroidForClaw: 独立 Agent 配置页面
 *
 * 架构：
 * - 每个 Agent 的 LLM API → models.providers["agent-{id}"]（独立 provider 条目）
 * - 每个 Agent 的 Channel → channels.{type}.accounts["agent-{id}"] + bindings 路由
 * - 删除 Agent → 自动清理对应的 provider、account、binding
 *
 * UI 完全按 Agent 组织，不暴露全局 provider/account 列表。
 */
package com.xiaomo.androidforclaw.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.xiaomo.androidforclaw.config.*

class AgentConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AgentConfigScreen(onBack = { finish() })
            }
        }
    }
}

// ─── 内部数据模型 ────────────────────────────────────────────────────────────

/** UI 用的 Agent 聚合视图 */
data class AgentView(
    val id: String,
    val name: String,
    val agentDir: String?,
    // LLM
    val providerBaseUrl: String,
    val providerApiKey: String,
    val providerApi: String,
    val modelId: String,
    // Feishu channel
    val feishuEnabled: Boolean,
    val feishuAppId: String,
    val feishuAppSecret: String,
    // Telegram channel
    val telegramEnabled: Boolean,
    val telegramToken: String,
    // Discord channel
    val discordEnabled: Boolean,
    val discordToken: String,
)

private fun providerKey(agentId: String) = "agent-$agentId"

/** 从完整 config 构建 AgentView 列表 */
private fun buildAgentViews(config: OpenClawConfig): List<AgentView> {
    val agents = config.agents?.list ?: return emptyList()
    val providers = config.resolveProviders()
    val bindings = config.bindings
    val feishuAccts = config.channels.feishu.accounts
    val telegramAccts = config.channels.telegram?.accounts
    val discordAccts = config.channels.discord?.accounts

    return agents.map { agent ->
        val pkey = providerKey(agent.id)
        val prov = providers[pkey]
        val modelId = agent.model?.primary
            ?.removePrefix("$pkey/")
            ?.takeIf { it != agent.model?.primary }
            ?: prov?.models?.firstOrNull()?.id
            ?: ""
        val feishuAcct = feishuAccts?.get(pkey)
        val tgAcct = telegramAccts?.get(pkey)
        val dcAcct = discordAccts?.get(pkey)

        AgentView(
            id = agent.id,
            name = agent.name,
            agentDir = agent.agentDir,
            providerBaseUrl = prov?.baseUrl ?: "",
            providerApiKey = prov?.apiKey ?: "",
            providerApi = prov?.api ?: "openai-completions",
            modelId = modelId,
            feishuEnabled = feishuAcct != null && (feishuAcct.enabled),
            feishuAppId = feishuAcct?.appId ?: "",
            feishuAppSecret = feishuAcct?.appSecret ?: "",
            telegramEnabled = tgAcct != null && (tgAcct.enabled == true),
            telegramToken = tgAcct?.botToken ?: "",
            discordEnabled = dcAcct != null && (dcAcct.enabled == true),
            discordToken = dcAcct?.token ?: "",
        )
    }
}

// ─── 主页面 ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val loader = remember { ConfigLoader.getInstance() }

    var agentViews by remember { mutableStateOf<List<AgentView>>(emptyList()) }
    var editingIndex by remember { mutableStateOf<Int?>(null) } // null=new, >=0=edit
    var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }

    fun reload() {
        val config = loader.loadOpenClawConfig()
        agentViews = buildAgentViews(config)
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agent 配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { editingIndex = -1 }) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 说明 ──────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 0.dp
            ) {
                Text(
                    text = "每个 Agent 拥有独立的 LLM API 和 Channel Bot。" +
                        "底层自动管理 provider、channel account 和路由绑定。",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // ── Agent 列表 ────────────────────────────────────
            if (agentViews.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.SmartToy, null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "暂无独立 Agent",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "点击右上角「添加」创建",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            } else {
                agentViews.forEachIndexed { index, av ->
                    AgentSummaryCard(
                        av = av,
                        onEdit = { editingIndex = index },
                        onDelete = { showDeleteConfirm = index }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── 编辑/新建 ──────────────────────────────────────────────
    if (editingIndex != null) {
        val isNew = editingIndex == -1
        val existing = if (!isNew) agentViews[editingIndex!!] else null
        AgentEditDialog(
            existing = existing,
            onDismiss = { editingIndex = null },
            onSave = { view ->
                saveAgent(loader, existing?.id, view, isNew)
                reload()
                editingIndex = null
                Toast.makeText(context, if (isNew) "已添加" else "已更新", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // ── 删除确认 ──────────────────────────────────────────────
    if (showDeleteConfirm != null) {
        val av = agentViews[showDeleteConfirm!!]
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除 Agent") },
            text = {
                Text("确定删除「${av.name}」(${av.id})？\n\n将同时清理：\n• 独立 LLM Provider\n• 已绑定的 Channel 账号\n• 路由绑定")
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteAgent(loader, av.id)
                    reload()
                    showDeleteConfirm = null
                    Toast.makeText(context, "已删除并清理", Toast.LENGTH_SHORT).show()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            }
        )
    }
}

// ─── Agent 摘要卡片 ──────────────────────────────────────────────────────────

@Composable
private fun AgentSummaryCard(
    av: AgentView,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Filled.SmartToy, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        av.name.ifBlank { av.id },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "ID: ${av.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Edit, "编辑", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, "删除", modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))

            // LLM
            SectionLabel("LLM API")
            if (av.providerBaseUrl.isNotBlank() || av.modelId.isNotBlank()) {
                DetailRow("模型", av.modelId.ifBlank { "—" })
                DetailRow("API", av.providerBaseUrl.ifBlank { "全局默认" })
            } else {
                Text("使用全局默认模型", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))

            // Channels
            SectionLabel("Channels")
            val channels = buildList {
                if (av.feishuEnabled) add("飞书")
                if (av.telegramEnabled) add("Telegram")
                if (av.discordEnabled) add("Discord")
            }
            if (channels.isNotEmpty()) {
                DetailRow("已绑定", channels.joinToString("、"))
            } else {
                Text("未绑定 Channel（仅通过默认 Channel 响应）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

// ─── 编辑对话框 ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentEditDialog(
    existing: AgentView?,
    onDismiss: () -> Unit,
    onSave: (AgentView) -> Unit
) {
    val isNew = existing == null

    // 基本信息
    var id by remember { mutableStateOf(existing?.id ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var agentDir by remember { mutableStateOf(existing?.agentDir ?: "") }

    // LLM API
    var providerBaseUrl by remember { mutableStateOf(existing?.providerBaseUrl ?: "") }
    var providerApiKey by remember { mutableStateOf(existing?.providerApiKey ?: "") }
    var providerApi by remember { mutableStateOf(existing?.providerApi ?: "openai-completions") }
    var modelId by remember { mutableStateOf(existing?.modelId ?: "") }
    var showApiKey by remember { mutableStateOf(false) }

    // Feishu
    var feishuEnabled by remember { mutableStateOf(existing?.feishuEnabled ?: false) }
    var feishuAppId by remember { mutableStateOf(existing?.feishuAppId ?: "") }
    var feishuAppSecret by remember { mutableStateOf(existing?.feishuAppSecret ?: "") }

    // Telegram
    var telegramEnabled by remember { mutableStateOf(existing?.telegramEnabled ?: false) }
    var telegramToken by remember { mutableStateOf(existing?.telegramToken ?: "") }

    // Discord
    var discordEnabled by remember { mutableStateOf(existing?.discordEnabled ?: false) }
    var discordToken by remember { mutableStateOf(existing?.discordToken ?: "") }

    // 折叠区
    var llmExpanded by remember { mutableStateOf(true) }
    var feishuExpanded by remember { mutableStateOf(false) }
    var telegramExpanded by remember { mutableStateOf(false) }
    var discordExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "添加 Agent" else "编辑 Agent") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── 基本信息 ──────────────────────────────────
                Text("基本信息", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = id, onValueChange = { id = it },
                    label = { Text("Agent ID *") }, placeholder = { Text("e.g. coder") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    enabled = isNew
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("名称 *") }, placeholder = { Text("e.g. 代码助手") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = agentDir, onValueChange = { agentDir = it },
                    label = { Text("Workspace 目录") }, placeholder = { Text("可选，留空自动创建") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                // ── LLM API ───────────────────────────────────
                CollapsibleSection(
                    title = "LLM API 配置",
                    expanded = llmExpanded,
                    onToggle = { llmExpanded = !llmExpanded }
                ) {
                    OutlinedTextField(
                        value = providerBaseUrl, onValueChange = { providerBaseUrl = it },
                        label = { Text("API 地址") },
                        placeholder = { Text("e.g. https://api.openai.com/v1") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = providerApiKey, onValueChange = { providerApiKey = it },
                        label = { Text("API Key") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    "切换显示"
                                )
                            }
                        }
                    )
                    // API 类型
                    var apiDropdown by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = apiDropdown,
                        onExpandedChange = { apiDropdown = !apiDropdown }
                    ) {
                        OutlinedTextField(
                            value = providerApi, onValueChange = {},
                            label = { Text("API 类型") },
                            readOnly = true, singleLine = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = apiDropdown,
                            onDismissRequest = { apiDropdown = false }
                        ) {
                            listOf(
                                "openai-completions" to "OpenAI Completions",
                                "openai-responses" to "OpenAI Responses",
                                "anthropic-messages" to "Anthropic Messages",
                                "google-generative-ai" to "Google Gemini",
                                "ollama" to "Ollama"
                            ).forEach { (api, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { providerApi = api; apiDropdown = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = modelId, onValueChange = { modelId = it },
                        label = { Text("模型 ID") },
                        placeholder = { Text("e.g. gpt-4o / claude-sonnet-4-20250514") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // ── Feishu ────────────────────────────────────
                CollapsibleSection(
                    title = "飞书 Bot",
                    expanded = feishuExpanded,
                    onToggle = { feishuExpanded = !feishuExpanded },
                    enabled = feishuEnabled,
                    onEnabledChange = { feishuEnabled = it }
                ) {
                    OutlinedTextField(
                        value = feishuAppId, onValueChange = { feishuAppId = it },
                        label = { Text("App ID") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = feishuAppSecret, onValueChange = { feishuAppSecret = it },
                        label = { Text("App Secret") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation()
                    )
                }

                HorizontalDivider()

                // ── Telegram ──────────────────────────────────
                CollapsibleSection(
                    title = "Telegram Bot",
                    expanded = telegramExpanded,
                    onToggle = { telegramExpanded = !telegramExpanded },
                    enabled = telegramEnabled,
                    onEnabledChange = { telegramEnabled = it }
                ) {
                    OutlinedTextField(
                        value = telegramToken, onValueChange = { telegramToken = it },
                        label = { Text("Bot Token") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation()
                    )
                }

                HorizontalDivider()

                // ── Discord ───────────────────────────────────
                CollapsibleSection(
                    title = "Discord Bot",
                    expanded = discordExpanded,
                    onToggle = { discordExpanded = !discordExpanded },
                    enabled = discordEnabled,
                    onEnabledChange = { discordEnabled = it }
                ) {
                    OutlinedTextField(
                        value = discordToken, onValueChange = { discordToken = it },
                        label = { Text("Bot Token") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        AgentView(
                            id = id.trim(), name = name.trim(),
                            agentDir = agentDir.trim().ifBlank { null }?.let { it },
                            providerBaseUrl = providerBaseUrl.trim(),
                            providerApiKey = providerApiKey.trim(),
                            providerApi = providerApi,
                            modelId = modelId.trim(),
                            feishuEnabled = feishuEnabled,
                            feishuAppId = feishuAppId.trim(),
                            feishuAppSecret = feishuAppSecret.trim(),
                            telegramEnabled = telegramEnabled,
                            telegramToken = telegramToken.trim(),
                            discordEnabled = discordEnabled,
                            discordToken = discordToken.trim(),
                        )
                    )
                },
                enabled = id.isNotBlank() && name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean? = null,
    onEnabledChange: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                null, modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            if (enabled != null && onEnabledChange != null) {
                Switch(
                    checked = enabled,
                    onCheckedChange = { onEnabledChange(it) },
                    modifier = Modifier.height(24.dp)
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

// ─── 保存/删除 逻辑 ──────────────────────────────────────────────────────────

private fun saveAgent(
    loader: ConfigLoader,
    oldId: String?,
    view: AgentView,
    isNew: Boolean
) {
    val config = loader.loadOpenClawConfig()
    val pkey = providerKey(view.id)

    // ── 1. 更新 models.providers ──────────────────────────
    val existingProviders = config.resolveProviders().toMutableMap()

    // 如果是改名，先删旧的 provider
    if (oldId != null && oldId != view.id) {
        existingProviders.remove(providerKey(oldId))
    }

    // 只有填写了 API 地址或 Key 时才创建独立 provider
    if (view.providerBaseUrl.isNotBlank() || view.providerApiKey.isNotBlank()) {
        existingProviders[pkey] = ProviderConfig(
            baseUrl = view.providerBaseUrl.ifBlank { "https://api.openai.com/v1" },
            apiKey = view.providerApiKey.ifBlank { null },
            api = view.providerApi,
            models = listOf(
                ModelDefinition(
                    id = view.modelId.ifBlank { "gpt-4o" },
                    name = view.modelId.ifBlank { "gpt-4o" }
                )
            )
        )
    } else {
        // 没配 API → 删除独立 provider
        existingProviders.remove(pkey)
    }

    // ── 2. 更新 agents.list ───────────────────────────────
    val agentList = config.agents?.list?.toMutableList() ?: mutableListOf()
    if (!isNew && oldId != null && oldId != view.id) {
        agentList.removeAll { it.id == oldId }
    }
    val agentEntry = AgentEntry(
        id = view.id,
        name = view.name,
        agentDir = view.agentDir?.ifBlank { null },
        model = if (existingProviders.containsKey(pkey)) {
            ModelSelectionConfig(primary = "$pkey/${view.modelId.ifBlank { "gpt-4o" }}")
        } else null
    )
    if (isNew) {
        agentList.add(agentEntry)
    } else {
        val idx = agentList.indexOfFirst { it.id == (oldId ?: view.id) }
        if (idx >= 0) agentList[idx] = agentEntry else agentList.add(agentEntry)
    }

    val agentsConfig = AgentsConfig(
        list = agentList,
        defaults = config.agents?.defaults ?: AgentDefaultsConfig()
    )

    // ── 3. 更新 channels accounts ─────────────────────────
    val channels = config.channels

    // Feishu
    val feishuAccounts = (channels.feishu.accounts?.toMutableMap() ?: mutableMapOf()).apply {
        if (oldId != null && oldId != view.id) remove(providerKey(oldId))
        if (view.feishuEnabled && view.feishuAppId.isNotBlank()) {
            put(pkey, FeishuAccountConfig(
                enabled = true,
                name = view.name,
                appId = view.feishuAppId,
                appSecret = view.feishuAppSecret.ifBlank { null }
            ))
        } else {
            remove(pkey)
        }
    }

    // Telegram
    val telegramConfig = channels.telegram?.let { tg ->
        val tgAccounts = (tg.accounts?.toMutableMap() ?: mutableMapOf()).apply {
            if (oldId != null && oldId != view.id) remove(providerKey(oldId))
            if (view.telegramEnabled && view.telegramToken.isNotBlank()) {
                put(pkey, TelegramAccountConfig(
                    enabled = true,
                    name = view.name,
                    botToken = view.telegramToken
                ))
            } else {
                remove(pkey)
            }
        }
        tg.copy(accounts = tgAccounts)
    }

    // Discord
    val discordConfig = channels.discord?.let { dc ->
        val dcAccounts = (dc.accounts?.toMutableMap() ?: mutableMapOf()).apply {
            if (oldId != null && oldId != view.id) remove(providerKey(oldId))
            if (view.discordEnabled && view.discordToken.isNotBlank()) {
                put(pkey, DiscordAccountPolicyConfig(
                    enabled = true,
                    name = view.name,
                    token = view.discordToken
                ))
            } else {
                remove(pkey)
            }
        }
        dc.copy(accounts = dcAccounts)
    }

    val updatedChannels = channels.copy(
        feishu = channels.feishu.copy(accounts = feishuAccounts),
        telegram = telegramConfig,
        discord = discordConfig
    )

    // ── 4. 更新 bindings ──────────────────────────────────
    val bindings = config.bindings.toMutableList()
    // 清理旧 binding
    if (oldId != null && oldId != view.id) {
        bindings.removeAll { it.agentId == providerKey(oldId) }
    }
    bindings.removeAll { it.agentId == pkey }

    // 添加新 binding
    if (view.feishuEnabled && view.feishuAppId.isNotBlank()) {
        bindings.add(BindingEntry(
            agentId = view.id,
            match = BindingMatch(channel = "feishu", accountId = pkey)
        ))
    }
    if (view.telegramEnabled && view.telegramToken.isNotBlank()) {
        bindings.add(BindingEntry(
            agentId = view.id,
            match = BindingMatch(channel = "telegram", accountId = pkey)
        ))
    }
    if (view.discordEnabled && view.discordToken.isNotBlank()) {
        bindings.add(BindingEntry(
            agentId = view.id,
            match = BindingMatch(channel = "discord", accountId = pkey)
        ))
    }

    // ── 5. 持久化 ─────────────────────────────────────────
    val modelsConfig = (config.models ?: ModelsConfig()).copy(providers = existingProviders)
    val newConfig = config.copy(
        models = modelsConfig,
        agents = agentsConfig,
        channels = updatedChannels,
        bindings = bindings
    )
    loader.saveOpenClawConfig(newConfig)
}

private fun deleteAgent(loader: ConfigLoader, agentId: String) {
    val config = loader.loadOpenClawConfig()
    val pkey = providerKey(agentId)

    // 1. 删除 provider
    val providers = config.resolveProviders().toMutableMap().apply { remove(pkey) }

    // 2. 删除 agent entry
    val agentList = config.agents?.list?.filter { it.id != agentId }

    // 3. 删除 channel accounts
    val channels = config.channels
    val feishuAccounts = channels.feishu.accounts?.toMutableMap()?.apply { remove(pkey) }
    val telegramConfig = channels.telegram?.let {
        it.copy(accounts = it.accounts?.toMutableMap()?.apply { remove(pkey) })
    }
    val discordConfig = channels.discord?.let {
        it.copy(accounts = it.accounts?.toMutableMap()?.apply { remove(pkey) })
    }

    // 4. 删除 bindings
    val bindings = config.bindings.filter { it.agentId != agentId }

    val newConfig = config.copy(
        models = (config.models ?: ModelsConfig()).copy(providers = providers),
        agents = AgentsConfig(
            list = agentList?.ifEmpty { null },
            defaults = config.agents?.defaults ?: AgentDefaultsConfig()
        ),
        channels = channels.copy(
            feishu = channels.feishu.copy(accounts = feishuAccounts),
            telegram = telegramConfig,
            discord = discordConfig
        ),
        bindings = bindings
    )
    loader.saveOpenClawConfig(newConfig)
}

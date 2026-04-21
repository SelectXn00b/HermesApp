package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel.MCPMarketViewModel
import com.ai.assistance.operit.data.api.GitHubIssue
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Terminal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPPublishScreen(
    onNavigateBack: () -> Unit,
    editingIssue: GitHubIssue? = null, // 如果不为null，则为编辑模式
    viewModel: MCPMarketViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    val isEditMode = editingIssue != null
    
    // 表单状态 - 根据模式初始化
    val initialDraft = if (isEditMode && editingIssue != null) {
        viewModel.parsePluginInfoFromIssue(editingIssue)
    } else {
        viewModel.publishDraft
    }
    
    var title by remember { mutableStateOf(initialDraft.title) }
    var description by remember { mutableStateOf(initialDraft.description) }
    var repositoryUrl by remember { mutableStateOf(initialDraft.repositoryUrl) }
    var installConfig by remember { mutableStateOf(initialDraft.installConfig) }
    // 版本固定为 v1（系统管理）
    val version = "v1"
    
    // UI 状态
    var isPublishing by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // 实时保存草稿（仅新建模式）
    if (!isEditMode) {
        LaunchedEffect(title, description, repositoryUrl, installConfig) {
            viewModel.saveDraft(
                title = title,
                description = description,
                repositoryUrl = repositoryUrl,
                tags = "", // Removed
                installConfig = installConfig,
                category = "" // Removed
            )
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // 信息提示卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp, top = 2.dp)
                )
                Column {
                    Text(
                        text = if (isEditMode) stringResource(R.string.edit_description) else stringResource(R.string.publish_description),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEditMode) {
                            stringResource(R.string.edit_plugin_info_description)
                        } else {
                            stringResource(R.string.publish_plugin_info_description)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        // 插件名称
        OutlinedTextField(
            value = title,
            onValueChange = { newValue ->
                // 只允许英文字母、数字和下划线
                val filtered = newValue.filter { it.isLetterOrDigit() || it == '_' }
                title = filtered
            },
            label = { Text(stringResource(R.string.plugin_name_required)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true,
            isError = title.isBlank(),
            supportingText = { 
                Text(
                    stringResource(R.string.only_alphanumeric_underscore_plugin_id),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        )
        
        // 插件描述
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(stringResource(R.string.plugin_description_required)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            minLines = 3,
            maxLines = 5,
            isError = description.isBlank()
        )
        
        // 仓库地址
        OutlinedTextField(
            value = repositoryUrl,
            onValueChange = { repositoryUrl = it },
            label = { Text(stringResource(R.string.github_repo_address_required)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true,
            placeholder = { Text("https://github.com/username/repo") },
            isError = repositoryUrl.isBlank()
        )
        
        // 安装配置
        OutlinedTextField(
            value = installConfig,
            onValueChange = { installConfig = it },
            label = { Text(stringResource(R.string.install_config)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            placeholder = { Text(stringResource(R.string.install_config_example)) },
            minLines = 3,
            maxLines = 8,
            leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = stringResource(R.string.install_config)) },
            supportingText = { Text(stringResource(R.string.install_config_optional_description)) }
        )
        
        // 错误信息
        errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        // 发布/更新按钮
        Button(
            onClick = {
                if (title.isBlank() || description.isBlank() || repositoryUrl.isBlank()) {
                    errorMessage = context.getString(R.string.please_fill_all_required_fields)
                    return@Button
                }
                showConfirmationDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = !isPublishing && title.isNotBlank() && description.isNotBlank() && 
                     repositoryUrl.isNotBlank()
        ) {
            if (isPublishing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEditMode) stringResource(R.string.updating_progress) else stringResource(R.string.publishing_progress))
            } else {
                Text(if (isEditMode) stringResource(R.string.update_plugin) else stringResource(R.string.publish_to_market))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 取消按钮
        OutlinedButton(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
    
    // 确认发布对话框
    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text(if (isEditMode) stringResource(R.string.confirm_update) else stringResource(R.string.confirm_publish)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.please_check_submitted_info))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.name_colon, title), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.description_colon, description), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.repository_colon, repositoryUrl), style = MaterialTheme.typography.bodyMedium)
                    if (installConfig.isNotBlank()) {
                        Text(stringResource(R.string.install_config_colon, installConfig), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.confirm_mcp_git_import_deployment),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmationDialog = false
                        scope.launch {
                            isPublishing = true
                            errorMessage = null
                            
                            try {
                                val success = if (isEditMode && editingIssue != null) {
                                    // 编辑模式：更新现有插件
                                    viewModel.updatePublishedPlugin(
                                        issueNumber = editingIssue.number,
                                        title = title,
                                        description = description,
                                        repositoryUrl = repositoryUrl,
                                        category = "", // Removed
                                        tags = "", // Removed
                                        installConfig = installConfig,
                                        version = version
                                    )
                                    true
                                } else {
                                    // 新建模式：发布新插件
                                    viewModel.publishMCP(
                                        title = title,
                                        description = description,
                                        repositoryUrl = repositoryUrl,
                                        category = "", // Removed
                                        tags = "", // Removed
                                        installConfig = installConfig,
                                        version = version
                                    )
                                }
                                
                                if (success) {
                                    if (!isEditMode) {
                                        // 只在新建模式下清空草稿
                                        viewModel.clearDraft()
                                    }
                                    showSuccessDialog = true
                                } else {
                                    errorMessage = if (isEditMode) context.getString(R.string.update_failed_check_network) else context.getString(R.string.publish_failed_check_network_repo)
                                }
                            } catch (e: Exception) {
                                errorMessage = if (isEditMode) context.getString(R.string.update_failed_with_error, e.message ?: "") else context.getString(R.string.publish_failed_with_error, e.message ?: "")
                            } finally {
                                isPublishing = false
                            }
                        }
                    }
                ) {
                    Text(if (isEditMode) stringResource(R.string.confirm_update) else stringResource(R.string.confirm_publish))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmationDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 成功对话框
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(if (isEditMode) stringResource(R.string.update_success) else stringResource(R.string.publish_success)) },
            text = { 
                Text(
                    if (isEditMode) {
                        stringResource(R.string.mcp_plugin_update_success_message)
                    } else {
                        stringResource(R.string.mcp_plugin_publish_success_message)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
} 
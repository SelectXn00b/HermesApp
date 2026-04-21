package com.ai.assistance.operit.ui.features.chat.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.ui.features.chat.components.config.TokenInfoDialog
import kotlinx.coroutines.CoroutineScope

/** 简洁风格的AI助手配置界面 */
@Composable
fun ConfigurationScreen(
        apiEndpoint: String,
        apiKey: String,
        modelName: String,
        onApiEndpointChange: (String) -> Unit,
        onApiKeyChange: (String) -> Unit,
        onModelNameChange: (String) -> Unit,
        onApiProviderTypeChange: (ApiProviderType) -> Unit,
        onSaveConfig: () -> Unit,
        onError: (String) -> Unit,
        coroutineScope: CoroutineScope,
        onUseDefault: () -> Unit = {},
        isUsingDefault: Boolean = false,
        onNavigateToChat: () -> Unit = {},
        onNavigateToTokenConfig: () -> Unit = {},
        onNavigateToSettings: () -> Unit = {},
        onNavigateToModelConfig: () -> Unit = {}
) {
        // 获取Context
        val context = LocalContext.current

        // 状态管理
        var apiKeyInput by remember { mutableStateOf(if (isUsingDefault) "" else apiKey) }
        var showTokenInfoDialog by remember { mutableStateOf(false) }

        // 使用默认密钥的状态检测
        val isUsingDefaultApiKey by
                remember(apiKeyInput) {
                        derivedStateOf {
                                apiKeyInput == ApiPreferences.DEFAULT_API_KEY ||
                                        (apiKeyInput.isBlank() && isUsingDefault)
                        }
                }

        // 检测用户是否输入了自己的token
        val hasEnteredToken =
                apiKeyInput.isNotBlank() && apiKeyInput != ApiPreferences.DEFAULT_API_KEY

        // 导航处理
        LaunchedEffect(isUsingDefault) {
                if (!isUsingDefault) {
                        onNavigateToChat()
                }
        }

        // 密钥信息对话框
        if (showTokenInfoDialog) {
                TokenInfoDialog(
                        onDismiss = { showTokenInfoDialog = false },
                        onConfirm = {
                                showTokenInfoDialog = false
                                onNavigateToTokenConfig()
                        }
                )
        }

        // 主界面 - 简洁设计
        Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
        ) {
                Column(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 4.dp)
                                        .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                ) {
                        // 标题和说明
                        Text(
                                text = stringResource(id = R.string.config_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 20.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                                text = stringResource(id = R.string.config_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // API密钥输入框 - 简洁设计
                        OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                label = {
                                        Text(
                                                stringResource(id = R.string.config_api_key_label),
                                                fontSize = 12.sp
                                        )
                                },
                                placeholder = {
                                        Text(
                                                stringResource(
                                                        id = R.string.config_api_key_placeholder
                                                ),
                                                fontSize = 12.sp
                                        )
                                },
                                leadingIcon = {
                                        Icon(
                                                imageVector = Icons.Default.Key,
                                                contentDescription =
                                                        stringResource(
                                                                id = R.string.config_api_key_label
                                                        ),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                        )
                                },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp),
                                colors =
                                        OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor =
                                                        MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor =
                                                        MaterialTheme.colorScheme.outline.copy(
                                                                alpha = 0.7f
                                                        )
                                        ),
                                textStyle =
                                        MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 主按钮 - 根据输入状态动态变化
                        Button(
                                onClick = {
                                        if (hasEnteredToken) {
                                                // 如果用户已输入token，直接保存配置
                                                onApiKeyChange(apiKeyInput)
                                                onApiEndpointChange(
                                                        ApiPreferences.DEFAULT_API_ENDPOINT
                                                )
                                                onModelNameChange(ApiPreferences.DEFAULT_MODEL_NAME)
                                                onApiProviderTypeChange(ApiProviderType.DEEPSEEK)
                                                onSaveConfig()
                                        } else {
                                                // 否则显示获取token的对话框
                                                showTokenInfoDialog = true
                                        }
                                },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor =
                                                        if (hasEnteredToken)
                                                                MaterialTheme.colorScheme
                                                                        .primaryContainer
                                                        else MaterialTheme.colorScheme.primary
                                        )
                        ) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                ) {
                                        if (hasEnteredToken) {
                                                Icon(
                                                        imageVector = Icons.Default.Save,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                                if (hasEnteredToken)
                                                        stringResource(
                                                                id = R.string.config_save_button
                                                        )
                                                else stringResource(id = R.string.config_get_token),
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp,
                                                color =
                                                        if (hasEnteredToken)
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer
                                                        else MaterialTheme.colorScheme.onPrimary
                                        )
                                }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 底部选项
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                        ) {
                                // 右侧 - 自定义
                                TextButton(
                                        onClick = { onNavigateToModelConfig() }
                                ) {
                                        Text(
                                                stringResource(id = R.string.config_custom),
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp
                                        )
                                }
                        }
                }
        }
}

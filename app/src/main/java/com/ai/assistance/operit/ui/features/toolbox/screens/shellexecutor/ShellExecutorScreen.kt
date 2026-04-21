package com.ai.assistance.operit.ui.features.toolbox.screens.shellexecutor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/** 命令执行记录数据类 */
data class CommandRecord(
    val command: String,
    val result: AndroidShellExecutor.CommandResult,
    val timestamp: Long = System.currentTimeMillis()
)

/** 预设命令分类 */
enum class CommandCategory(val stringResId: Int) {
    SYSTEM(R.string.shell_executor_category_system),
    FILE(R.string.shell_executor_category_file),
    NETWORK(R.string.shell_executor_category_network),
    HARDWARE(R.string.shell_executor_category_hardware),
    PACKAGE(R.string.shell_executor_category_package)
}

@Composable
fun CommandCategory.getDisplayName(): String {
    return stringResource(stringResId)
}

/** 预设命令数据类 */
data class PresetCommand(
    val name: String,
    val command: String,
    val description: String,
    val category: CommandCategory,
    val icon: ImageVector
)

/**
 * Shell命令执行器屏幕
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShellExecutorScreen(navController: NavController? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    // 创建命令管理器
    val commandManager = remember { ShellCommandManager(context) }
    
    // 状态管理
    var commandInput by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    var commandHistory by remember { mutableStateOf(listOf<CommandRecord>()) }
    var showPresets by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    var suggestionsList by remember { mutableStateOf(listOf<String>()) }
    
    // 从管理器获取预设命令
    val presetCommands = remember { commandManager.getPresetCommands() }
    
    // 加载历史记录
    LaunchedEffect(Unit) {
        commandHistory = commandManager.getCommandHistory()
    }
    
    // 命令建议更新
    LaunchedEffect(commandInput) {
        if (commandInput.isNotEmpty()) {
            suggestionsList = commandManager.getSuggestedCommands(commandInput)
            showSuggestions = suggestionsList.isNotEmpty()
        } else {
            showSuggestions = false
        }
    }

    // 执行命令函数
    fun executeCommand(command: String) {
        val trimmedCommand = command.trim()
        if (trimmedCommand.isBlank()) return
        
        isExecuting = true
        focusManager.clearFocus()
        
        coroutineScope.launch {
            try {
                val record = commandManager.executeCommand(trimmedCommand)
                
                // 添加到历史记录
                commandHistory = listOf(record) + commandHistory
                commandInput = "" // 清空输入
            } catch (e: Exception) {
                errorMessage = context.getString(R.string.shell_executor_execute_failed, e.message ?: "Unknown error")
                showError = true
            } finally {
                // 确保执行状态重置
                isExecuting = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部区域
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.tool_shell_executor),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.tool_shell_executor_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 命令输入区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = commandInput,
                            onValueChange = { commandInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.shell_executor_input_hint)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Terminal,
                                    contentDescription = stringResource(R.string.shell_executor_command),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (commandInput.isNotEmpty()) {
                                    IconButton(onClick = { commandInput = "" }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.shell_executor_clear)
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = { executeCommand(commandInput) }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        
                        // 命令建议下拉菜单
                        if (showSuggestions && commandInput.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shadowElevation = 4.dp,
                                modifier = Modifier.fillMaxWidth()
                                    .align(Alignment.BottomStart)
                                    .offset(y = 56.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    suggestionsList.forEach { suggestion ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    commandInput = suggestion
                                                    showSuggestions = false
                                                }
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = suggestion,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        
                                        if (suggestion != suggestionsList.last()) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 执行按钮
                    FilledTonalButton(
                        onClick = { executeCommand(commandInput) },
                        enabled = commandInput.trim().isNotEmpty(),
                        shape = CircleShape,
                        modifier = Modifier.height(56.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.shell_executor_execute)
                            )
                        }
                    }
                }

                // 工具栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 清除历史按钮
                    if (commandHistory.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                commandManager.clearCommandHistory()
                                commandHistory = emptyList()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.shell_executor_clear_history),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.shell_executor_clear_history))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // 预设命令切换按钮
                    TextButton(
                        onClick = { showPresets = !showPresets },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(if (showPresets) stringResource(R.string.shell_executor_hide_presets) else stringResource(R.string.shell_executor_show_presets))
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (showPresets) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 状态指示器
                if (isExecuting) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.shell_executor_executing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 预设命令区域
        AnimatedVisibility(
            visible = showPresets,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                val presetsByCategory = remember(presetCommands) {
                    presetCommands.groupBy { it.category }
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.shell_executor_common_commands),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 按分类显示预设命令
                    presetsByCategory.forEach { (category, commands) ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = category.getDisplayName(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                maxItemsInEachRow = 2
                            ) {
                                commands.forEach { presetCommand ->
                                    PresetCommandChip(
                                        presetCommand = presetCommand,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            commandInput = presetCommand.command
                                            showPresets = false
                                        }
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        // 结果区域
        Box(modifier = Modifier.weight(1f)) {
            if (commandHistory.isEmpty()) {
                // 空状态
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(72.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(R.string.shell_executor_input_command_hint),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.shell_executor_view_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = { showPresets = !showPresets }) {
                        Text(stringResource(R.string.shell_executor_view_presets))
                    }
                }
            } else {
                // 命令历史记录
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(items = commandHistory) { record ->
                        CommandResultCard(
                            record = record,
                            onReExecute = { executeCommand(record.command) }
                        )
                    }

                    // 底部空间
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    // 错误提示
    if (showError && errorMessage != null) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.shell_executor_error_title))
                }
            },
            text = { Text(errorMessage!!, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = { showError = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text(stringResource(R.string.shell_executor_confirm)) }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

/** 预设命令芯片 */
@Composable
fun PresetCommandChip(presetCommand: PresetCommand, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = presetCommand.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = presetCommand.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 命令结果卡片 */
@Composable
fun CommandResultCard(record: CommandRecord, onReExecute: () -> Unit = {}) {
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()) }
    val formattedDate = remember(record) { dateFormatter.format(Date(record.timestamp)) }
    
    var expanded by remember { mutableStateOf(false) }
    val backgroundColor = MaterialTheme.colorScheme.surface
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 命令和时间信息
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.command,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 状态指示
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (record.result.success) Color(0xFF4CAF50)
                            else Color(0xFFFF5252)
                        )
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 展开/收起按钮
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) stringResource(R.string.shell_executor_collapse) else stringResource(R.string.shell_executor_expand)
                    )
                }
            }

            // 命令输出结果
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // 标准输出
                    if (record.result.stdout.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.shell_executor_stdout),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = record.result.stdout,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                ),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // 标准错误
                    if (record.result.stderr.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.shell_executor_stderr),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = record.result.stderr,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                ),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // 退出代码
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.shell_executor_exit_code, record.result.exitCode.toString()),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (record.result.exitCode == 0)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // 重新执行按钮
                        TextButton(
                            onClick = onReExecute,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.shell_executor_re_execute),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.shell_executor_re_execute),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
} 
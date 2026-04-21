package com.ai.assistance.operit.ui.features.toolbox.screens.processlimit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 进程限制操作记录
 */
data class ProcessLimitRecord(
    val action: ProcessLimitAction,
    val result: AndroidShellExecutor.CommandResult,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 进程限制操作类型
 */
enum class ProcessLimitAction {
    REMOVE,
    RESTORE
}

/**
 * 解除进程限制工具屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessLimitRemoverScreen(navController: NavController? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // 字符串资源
    val statusChecking = stringResource(R.string.process_limit_status_checking)
    val statusRemoved = stringResource(R.string.process_limit_status_removed)
    val statusLimited = stringResource(R.string.process_limit_status_limited)
    val statusDefault = stringResource(R.string.process_limit_status_default)
    val statusUnknown = stringResource(R.string.process_limit_status_unknown)
    
    // 状态管理
    var isExecuting by remember { mutableStateOf(false) }
    var currentStatus by remember { mutableStateOf<String?>(null) }
    var operationHistory by remember { mutableStateOf(listOf<ProcessLimitRecord>()) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<ProcessLimitRecord?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // 加载当前状态
    LaunchedEffect(Unit) {
        try {
            val result = AndroidShellExecutor.executeShellCommand(
                "device_config get activity_manager max_phantom_processes"
            )
            currentStatus = if (result.success && result.stdout.trim().isNotEmpty()) {
                val maxProcesses = result.stdout.trim()
                when {
                    maxProcesses == "2147483647" -> statusRemoved.format(maxProcesses)
                    maxProcesses.toIntOrNull() != null && maxProcesses.toInt() > 100 -> statusRemoved.format(maxProcesses)
                    maxProcesses == "null" || maxProcesses.isEmpty() -> statusDefault
                    else -> statusLimited.format(maxProcesses)
                }
            } else {
                statusUnknown
            }
        } catch (e: Exception) {
            currentStatus = "$statusUnknown: ${e.message}"
        }
    }
    
    // 执行操作
    fun executeAction(action: ProcessLimitAction) {
        isExecuting = true
        errorMessage = null
        
        coroutineScope.launch {
            try {
                // 执行两个命令：设置最大幻象进程数 + 禁用同步测试
                val commands = when (action) {
                    ProcessLimitAction.REMOVE -> listOf(
                        "device_config put activity_manager max_phantom_processes 2147483647",
                        "device_config set_sync_disabled_for_tests persistent"
                    )
                    ProcessLimitAction.RESTORE -> listOf(
                        "device_config delete activity_manager max_phantom_processes",
                        "device_config set_sync_disabled_for_tests none"
                    )
                }
                
                // 执行第一个命令
                val result1 = AndroidShellExecutor.executeShellCommand(commands[0])
                // 执行第二个命令
                val result2 = AndroidShellExecutor.executeShellCommand(commands[1])
                
                // 合并结果
                val combinedResult = AndroidShellExecutor.CommandResult(
                    success = result1.success && result2.success,
                    stdout = "${result1.stdout}\n${result2.stdout}".trim(),
                    stderr = "${result1.stderr}\n${result2.stderr}".trim(),
                    exitCode = if (result1.success && result2.success) 0 else maxOf(result1.exitCode, result2.exitCode)
                )
                
                val record = ProcessLimitRecord(action, combinedResult)
                
                // 添加到历史记录
                operationHistory = listOf(record) + operationHistory
                lastResult = record
                
                // 更新当前状态
                if (combinedResult.success) {
                    currentStatus = when (action) {
                        ProcessLimitAction.REMOVE -> statusRemoved.format("2147483647")
                        ProcessLimitAction.RESTORE -> statusDefault
                    }
                    showResultDialog = true
                } else {
                    errorMessage = "${context.getString(R.string.process_limit_error_title)}: ${combinedResult.stderr.ifEmpty { statusUnknown }}"
                }
            } catch (e: Exception) {
                errorMessage = "${context.getString(R.string.process_limit_error_title)}: ${e.message}"
            } finally {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.process_limit_title),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = stringResource(R.string.process_limit_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    
                    // 帮助按钮
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.process_limit_info_title),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 当前状态卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            Text(
                                text = stringResource(R.string.process_limit_current_status),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            
                            Text(
                                text = currentStatus ?: statusChecking,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        // 刷新按钮
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        val result = AndroidShellExecutor.executeShellCommand(
                                            "device_config get activity_manager max_phantom_processes"
                                        )
                                        currentStatus = if (result.success && result.stdout.trim().isNotEmpty()) {
                                            val maxProcesses = result.stdout.trim()
                                            when {
                                                maxProcesses == "2147483647" -> statusRemoved.format(maxProcesses)
                                                maxProcesses.toIntOrNull() != null && maxProcesses.toInt() > 100 -> statusRemoved.format(maxProcesses)
                                                maxProcesses == "null" || maxProcesses.isEmpty() -> statusDefault
                                                else -> statusLimited.format(maxProcesses)
                                            }
                                        } else {
                                            statusUnknown
                                        }
                                    } catch (e: Exception) {
                                        currentStatus = statusUnknown
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.process_limit_current_status)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 操作按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 解除限制按钮
                    Button(
                        onClick = { executeAction(ProcessLimitAction.REMOVE) },
                        enabled = !isExecuting,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.process_limit_action_remove),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    
                    // 恢复限制按钮
                    OutlinedButton(
                        onClick = { executeAction(ProcessLimitAction.RESTORE) },
                        enabled = !isExecuting,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.process_limit_action_restore),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
                
                // 错误提示
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
        
        // 操作历史区域
        Box(modifier = Modifier.weight(1f)) {
            if (operationHistory.isEmpty()) {
                // 空状态
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(72.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(R.string.process_limit_no_history),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = stringResource(R.string.process_limit_execution_history),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                // 操作历史列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.process_limit_execution_history),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            
                            if (operationHistory.isNotEmpty()) {
                                TextButton(
                                    onClick = { operationHistory = emptyList() },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = context.getString(R.string.process_limit_execution_history),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(context.getString(R.string.process_limit_execution_history))
                                }
                            }
                        }
                    }
                    
                    items(items = operationHistory) { record ->
                        OperationRecordCard(record = record)
                    }
                    
                    // 底部空间
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
    
    // 帮助信息对话框
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.process_limit_info_title))
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.process_limit_what_is_title),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.process_limit_what_is_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = stringResource(R.string.process_limit_benefits_title),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.process_limit_benefits_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = stringResource(R.string.process_limit_warnings_title),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.process_limit_warnings_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text(context.getString(android.R.string.ok)) }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
    
    // 操作结果对话框
    if (showResultDialog && lastResult != null) {
        val actionName = when (lastResult!!.action) {
            ProcessLimitAction.REMOVE -> stringResource(R.string.process_limit_action_remove)
            ProcessLimitAction.RESTORE -> stringResource(R.string.process_limit_action_restore)
        }
        
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (lastResult!!.result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (lastResult!!.result.success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (lastResult!!.result.success) stringResource(R.string.process_limit_success_title) else stringResource(R.string.process_limit_error_title))
                }
            },
            text = {
                Column {
                    Text(
                        text = "$actionName ${if (lastResult!!.result.success) context.getString(android.R.string.ok) else stringResource(R.string.process_limit_error_title)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    if (!lastResult!!.result.success && lastResult!!.result.stderr.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${stringResource(R.string.process_limit_error_output)}: ${lastResult!!.result.stderr}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showResultDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text(context.getString(android.R.string.ok)) }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

/**
 * 操作记录卡片
 */
@Composable
fun OperationRecordCard(record: ProcessLimitRecord) {
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val formattedDate = remember(record) { dateFormatter.format(Date(record.timestamp)) }
    
    val actionName = when (record.action) {
        ProcessLimitAction.REMOVE -> stringResource(R.string.process_limit_action_remove)
        ProcessLimitAction.RESTORE -> stringResource(R.string.process_limit_action_restore)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 操作图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (record.action == ProcessLimitAction.REMOVE)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (record.action == ProcessLimitAction.REMOVE) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (record.action == ProcessLimitAction.REMOVE)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 操作信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = actionName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    )
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
        }
    }
}


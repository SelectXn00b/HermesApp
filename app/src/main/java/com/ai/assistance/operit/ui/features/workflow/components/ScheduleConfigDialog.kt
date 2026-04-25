package com.ai.assistance.operit.ui.features.workflow.components

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * Schedule Configuration Dialog
 * 
 * Provides a user-friendly interface for configuring workflow schedules
 */
@SuppressLint("RememberReturnType")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleConfigDialog(
    initialScheduleType: String = "interval",
    initialConfig: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit,
    onConfirm: (scheduleType: String, config: Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    var scheduleType by remember { mutableStateOf(initialScheduleType) }
    var scheduleTypeExpanded by remember { mutableStateOf(false) }
    
    // Interval configuration
    var intervalValue by remember { 
        mutableStateOf(initialConfig["interval_ms"]?.toLongOrNull()?.let { it / 60000 }?.toString() ?: "15") 
    }
    var intervalUnit by remember { mutableStateOf("minutes") }
    var intervalUnitExpanded by remember { mutableStateOf(false) }
    
    // Specific time configuration - 使用 Calendar 来管理日期和时间
    val calendar = remember { Calendar.getInstance() }
    
    // 尝试从初始配置解析日期时间
    remember(initialConfig) {
        initialConfig["specific_time"]?.let { dateTimeStr ->
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                sdf.parse(dateTimeStr)?.let { date ->
                    calendar.time = date
                }
            } catch (e: Exception) {
                // 解析失败，使用默认时间
            }
        }
    }
    
    var selectedYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }
    var selectedHour by remember { mutableIntStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(calendar.get(Calendar.MINUTE)) }
    
    // Cron configuration
    var cronExpression by remember { mutableStateOf(initialConfig["cron_expression"] ?: "0 0 * * *") }
    var cronPresetExpanded by remember { mutableStateOf(false) }
    
    // Common settings
    var repeat by remember { mutableStateOf(initialConfig["repeat"]?.toBoolean() ?: true) }
    var enabled by remember { mutableStateOf(initialConfig["enabled"]?.toBoolean() ?: true) }
    
    val scheduleTypes = mapOf(
        "interval" to stringResource(R.string.workflow_schedule_type_interval),
        "specific_time" to stringResource(R.string.workflow_schedule_type_specific_time),
        "cron" to stringResource(R.string.workflow_schedule_type_cron)
    )
    
    val intervalUnits = mapOf(
        "minutes" to stringResource(R.string.workflow_interval_unit_minutes),
        "hours" to stringResource(R.string.workflow_interval_unit_hours),
        "days" to stringResource(R.string.workflow_interval_unit_days)
    )
    
    val cronPresets = mapOf(
        "0 0 * * *" to stringResource(R.string.workflow_cron_preset_daily_midnight),
        "0 9 * * *" to stringResource(R.string.workflow_cron_preset_daily_9am),
        "0 12 * * *" to stringResource(R.string.workflow_cron_preset_daily_noon),
        "0 18 * * *" to stringResource(R.string.workflow_cron_preset_daily_6pm),
        "0 */2 * * *" to stringResource(R.string.workflow_cron_preset_every_2_hours),
        "0 */6 * * *" to stringResource(R.string.workflow_cron_preset_every_6_hours),
        "*/15 * * * *" to stringResource(R.string.workflow_cron_preset_every_15_minutes),
        "*/30 * * * *" to stringResource(R.string.workflow_cron_preset_every_30_minutes),
        "0 0 * * 1" to stringResource(R.string.workflow_cron_preset_every_monday_midnight),
        "0 9 * * 1-5" to stringResource(R.string.workflow_cron_preset_workday_9am)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workflow_schedule_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Schedule type selector
                ExposedDropdownMenuBox(
                    expanded = scheduleTypeExpanded,
                    onExpandedChange = { scheduleTypeExpanded = !scheduleTypeExpanded }
                ) {
                    OutlinedTextField(
                        value = scheduleTypes[scheduleType] ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.workflow_schedule_type_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = scheduleTypeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = scheduleTypeExpanded,
                        onDismissRequest = { scheduleTypeExpanded = false }
                    ) {
                        scheduleTypes.forEach { (key, value) ->
                            DropdownMenuItem(
                                text = { Text(value) },
                                onClick = {
                                    scheduleType = key
                                    scheduleTypeExpanded = false
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Configuration based on schedule type
                when (scheduleType) {
                    "interval" -> {
                        Text(
                            text = stringResource(R.string.workflow_interval_config_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = intervalValue,
                                onValueChange = { intervalValue = it },
                                label = { Text(stringResource(R.string.workflow_interval_value_label)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            
                            ExposedDropdownMenuBox(
                                expanded = intervalUnitExpanded,
                                onExpandedChange = { intervalUnitExpanded = !intervalUnitExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = intervalUnits[intervalUnit] ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.workflow_interval_unit_label)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalUnitExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = intervalUnitExpanded,
                                    onDismissRequest = { intervalUnitExpanded = false }
                                ) {
                                    intervalUnits.forEach { (key, value) ->
                                        DropdownMenuItem(
                                            text = { Text(value) },
                                            onClick = {
                                                intervalUnit = key
                                                intervalUnitExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        Text(
                            text = stringResource(R.string.workflow_schedule_min_interval_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    "specific_time" -> {
                        Text(
                            text = stringResource(R.string.workflow_specific_time_config_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // 日期选择器
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            selectedYear = year
                                            selectedMonth = month
                                            selectedDay = dayOfMonth
                                        },
                                        selectedYear,
                                        selectedMonth,
                                        selectedDay
                                    ).show()
                                }
                        ) {
                            OutlinedTextField(
                                value = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.workflow_date_label)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DateRange,
                                        contentDescription = stringResource(R.string.workflow_select_date)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 时间选择器
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    TimePickerDialog(
                                        context,
                                        { _, hourOfDay, minute ->
                                            selectedHour = hourOfDay
                                            selectedMinute = minute
                                        },
                                        selectedHour,
                                        selectedMinute,
                                        true // 24小时制
                                    ).show()
                                }
                        ) {
                            OutlinedTextField(
                                value = String.format("%02d:%02d", selectedHour, selectedMinute),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.workflow_time_label)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Schedule,
                                        contentDescription = stringResource(R.string.workflow_select_time)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 显示完整的日期时间
                        val fullDateTime = String.format(
                            "%04d-%02d-%02d %02d:%02d:00",
                            selectedYear,
                            selectedMonth + 1,
                            selectedDay,
                            selectedHour,
                            selectedMinute
                        )
                        Text(
                            text = stringResource(R.string.workflow_schedule_execute_time_format, fullDateTime),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    "cron" -> {
                        Text(
                            text = stringResource(R.string.workflow_cron_config_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        ExposedDropdownMenuBox(
                            expanded = cronPresetExpanded,
                            onExpandedChange = { cronPresetExpanded = !cronPresetExpanded }
                        ) {
                            OutlinedTextField(
                                value = stringResource(R.string.workflow_select_preset_template),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.workflow_preset_template_label)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cronPresetExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = cronPresetExpanded,
                                onDismissRequest = { cronPresetExpanded = false }
                            ) {
                                cronPresets.forEach { (expression, description) ->
                                    DropdownMenuItem(
                                        text = { 
                                            Column {
                                                Text(description)
                                                Text(
                                                    text = expression,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            cronExpression = expression
                                            cronPresetExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = cronExpression,
                            onValueChange = { cronExpression = it },
                            label = { Text(stringResource(R.string.workflow_cron_expression_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.workflow_cron_expression_placeholder)) }
                        )
                        
                        Text(
                            text = stringResource(R.string.workflow_cron_format_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                // Common settings
                Text(
                    text = stringResource(R.string.workflow_common_settings_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.workflow_repeat_label))
                    Switch(
                        checked = repeat,
                        onCheckedChange = { repeat = it }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.workflow_schedule_enabled_label))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val config = mutableMapOf<String, String>()
                    config["schedule_type"] = scheduleType
                    config["enabled"] = enabled.toString()
                    config["repeat"] = repeat.toString()
                    
                    when (scheduleType) {
                        "interval" -> {
                            val value = intervalValue.toLongOrNull() ?: 15
                            val multiplier = when (intervalUnit) {
                                "hours" -> 60
                                "days" -> 60 * 24
                                else -> 1 // minutes
                            }
                            config["interval_ms"] = (value * multiplier * 60 * 1000).toString()
                        }
                        "specific_time" -> {
                            // 构造日期时间字符串
                            val dateTimeStr = String.format(
                                "%04d-%02d-%02d %02d:%02d:00",
                                selectedYear,
                                selectedMonth + 1,
                                selectedDay,
                                selectedHour,
                                selectedMinute
                            )
                            config["specific_time"] = dateTimeStr
                        }
                        "cron" -> {
                            if (cronExpression.isNotBlank()) {
                                config["cron_expression"] = cronExpression
                            }
                        }
                    }
                    
                    onConfirm(scheduleType, config)
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_action))
            }
        }
    )
}


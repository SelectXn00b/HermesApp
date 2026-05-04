package com.ai.assistance.operit.ui.features.toolbox.screens.skillrecorder

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.skillrecorder.BuilderStep
import com.ai.assistance.operit.data.model.skillrecorder.RecordingState

/**
 * Skill 构建器入口页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillRecorderScreen(
    onNavigateToReview: () -> Unit = {},
    viewModel: SkillRecorderViewModel = viewModel()
) {
    val recordingState by viewModel.recordingState.collectAsState()
    val stepFrameCount by viewModel.stepFrameCount.collectAsState()
    val showInstallProviderPrompt by viewModel.showInstallProviderPrompt.collectAsState()
    val showAccessibilityPrompt by viewModel.showAccessibilityPrompt.collectAsState()
    val modelConfigs by viewModel.modelConfigs.collectAsState()
    val selectedModelConfigId by viewModel.selectedModelConfigId.collectAsState()
    val draftText by viewModel.draftText.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()
    val lastSavedSkillName by viewModel.lastSavedSkillName.collectAsState()
    val builderSteps by viewModel.builderSteps.collectAsState()
    val showThinkEditor by viewModel.showThinkEditor.collectAsState()
    val thinkStepText by viewModel.thinkStepText.collectAsState()
    val context = LocalContext.current
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var showDiscardAllDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 保存成功后显示 Snackbar 提示
    val savedMessage = lastSavedSkillName?.let { name ->
        stringResource(R.string.skill_recorder_saved, name)
    }
    LaunchedEffect(isSaved, lastSavedSkillName) {
        if (isSaved && savedMessage != null) {
            snackbarHostState.showSnackbar(savedMessage)
            viewModel.clearSavedState()
        }
    }

    // 服务提供者未安装提示
    if (showInstallProviderPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissInstallProviderPrompt() },
            title = { Text(stringResource(R.string.accessibility_wizard_step1)) },
            text = { Text(stringResource(R.string.accessibility_wizard_install_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.installProvider() }) {
                    Text(stringResource(R.string.accessibility_wizard_install_provider))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissInstallProviderPrompt() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 无障碍服务未开启提示
    if (showAccessibilityPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAccessibilityPrompt() },
            title = { Text(stringResource(R.string.a11y_service_not_enabled)) },
            text = { Text(stringResource(R.string.skill_recorder_accessibility_required)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissAccessibilityPrompt()
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) { }
                }) {
                    Text(stringResource(R.string.accessibility_wizard_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAccessibilityPrompt() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 丢弃确认对话框
    if (showDiscardAllDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardAllDialog = false },
            title = { Text(stringResource(R.string.skill_recorder_discard_all)) },
            text = { Text(stringResource(R.string.skill_recorder_confirm_discard_all)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.discardAll()
                    showDiscardAllDialog = false
                }) {
                    Text(stringResource(R.string.skill_recorder_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardAllDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // 当进入 REVIEW 状态时导航到审阅页面
    LaunchedEffect(recordingState) {
        if (recordingState == RecordingState.REVIEW) {
            onNavigateToReview()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (recordingState) {
                RecordingState.IDLE -> {
                    // ──── IDLE：草稿 + 模型选择 + 开始构建 ────
                    IdleContent(
                        draftText = draftText,
                        onDraftChange = { viewModel.updateDraftText(it) },
                        modelConfigs = modelConfigs,
                        selectedModelConfigId = selectedModelConfigId,
                        modelDropdownExpanded = modelDropdownExpanded,
                        onModelDropdownToggle = { modelDropdownExpanded = it },
                        onModelSelect = { viewModel.selectModelConfig(it) },
                        onStartBuilding = { viewModel.startBuilding() }
                    )
                }

                RecordingState.BUILDING -> {
                    // ──── BUILDING：步骤列表 + 操作按钮 ────
                    BuildingContent(
                        steps = builderSteps,
                        showThinkEditor = showThinkEditor,
                        thinkStepText = thinkStepText,
                        onThinkTextChange = { viewModel.updateThinkStepText(it) },
                        onAddRecordStep = { viewModel.startStepRecording() },
                        onAddThinkStep = { viewModel.showThinkStepEditor() },
                        onCommitThinkStep = { viewModel.commitThinkStep() },
                        onCancelThinkStep = { viewModel.cancelThinkEditor() },
                        onEditThinkStep = { viewModel.editThinkStep(it) },
                        onRemoveStep = { viewModel.removeStep(it) },
                        onMoveStep = { from, to -> viewModel.moveStep(from, to) },
                        onGenerate = { viewModel.generateSkill() },
                        onDiscardAll = { showDiscardAllDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                RecordingState.STEP_RECORDING, RecordingState.STEP_PAUSED -> {
                    // ──── STEP_RECORDING：录制 overlay ────
                    StepRecordingContent(
                        recordingState = recordingState,
                        stepFrameCount = stepFrameCount,
                        onPause = { viewModel.pauseStepRecording() },
                        onResume = { viewModel.resumeStepRecording() },
                        onStop = { viewModel.stopStepRecording() },
                        onDiscard = { viewModel.discardStepRecording() }
                    )
                }

                RecordingState.SUMMARIZING -> {
                    // ──── SUMMARIZING：进度 + 跳过 ────
                    SummarizingContent(
                        onSkip = { viewModel.skipSummarization() }
                    )
                }

                RecordingState.REVIEW -> {
                    // 将自动导航到 ReviewScreen
                }
            }
        }
    }
}

@Composable
private fun IdleContent(
    draftText: String,
    onDraftChange: (String) -> Unit,
    modelConfigs: List<com.ai.assistance.operit.data.model.ModelConfigSummary>,
    selectedModelConfigId: String?,
    modelDropdownExpanded: Boolean,
    onModelDropdownToggle: (Boolean) -> Unit,
    onModelSelect: (String) -> Unit,
    onStartBuilding: () -> Unit
) {
    // 状态卡片
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.skill_recorder_ready),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // 草稿输入区
    OutlinedTextField(
        value = draftText,
        onValueChange = onDraftChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp, max = 160.dp),
        label = { Text(stringResource(R.string.skill_recorder_draft_label)) },
        placeholder = { Text(stringResource(R.string.skill_recorder_draft_placeholder)) },
        supportingText = { Text(stringResource(R.string.skill_recorder_draft_hint)) },
        maxLines = 6,
        textStyle = MaterialTheme.typography.bodyMedium
    )

    Spacer(modifier = Modifier.height(12.dp))

    // 模型选择
    if (modelConfigs.size > 1) {
        val selectedConfig = modelConfigs.find { it.id == selectedModelConfigId }
        Box {
            OutlinedButton(
                onClick = { onModelDropdownToggle(true) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.skill_recorder_model_label,
                        selectedConfig?.name ?: "—"),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            DropdownMenu(
                expanded = modelDropdownExpanded,
                onDismissRequest = { onModelDropdownToggle(false) }
            ) {
                modelConfigs.forEach { config ->
                    DropdownMenuItem(
                        text = { Text(config.name) },
                        onClick = {
                            onModelSelect(config.id)
                            onModelDropdownToggle(false)
                        },
                        trailingIcon = {
                            if (config.id == selectedModelConfigId) {
                                Text("✓")
                            }
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    Button(
        onClick = onStartBuilding,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            stringResource(R.string.skill_recorder_start),
            style = MaterialTheme.typography.titleMedium
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.skill_recorder_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun BuildingContent(
    steps: List<BuilderStep>,
    showThinkEditor: Boolean,
    thinkStepText: String,
    onThinkTextChange: (String) -> Unit,
    onAddRecordStep: () -> Unit,
    onAddThinkStep: () -> Unit,
    onCommitThinkStep: () -> Unit,
    onCancelThinkStep: () -> Unit,
    onEditThinkStep: (BuilderStep.Think) -> Unit,
    onRemoveStep: (String) -> Unit,
    onMoveStep: (Int, Int) -> Unit,
    onGenerate: () -> Unit,
    onDiscardAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 状态卡片
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.skill_recorder_building),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (steps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.skill_recorder_steps_overview, steps.size),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 步骤列表
    if (steps.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.skill_recorder_no_steps),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.skill_recorder_empty_steps_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(steps, key = { _, step -> step.id }) { index, step ->
                StepCard(
                    step = step,
                    index = index,
                    totalCount = steps.size,
                    onMoveUp = { if (index > 0) onMoveStep(index, index - 1) },
                    onMoveDown = { if (index < steps.size - 1) onMoveStep(index, index + 1) },
                    onRemove = { onRemoveStep(step.id) },
                    onEdit = { if (step is BuilderStep.Think) onEditThinkStep(step) }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 思考步骤编辑器
    if (showThinkEditor) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = thinkStepText,
                    onValueChange = onThinkTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 160.dp),
                    placeholder = { Text(stringResource(R.string.skill_recorder_think_placeholder)) },
                    maxLines = 8,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancelThinkStep) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onCommitThinkStep,
                        enabled = thinkStepText.isNotBlank()
                    ) {
                        Text(stringResource(R.string.skill_recorder_think_add))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 添加步骤按钮
    if (!showThinkEditor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onAddRecordStep,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.skill_recorder_add_record_step))
            }

            OutlinedButton(
                onClick = onAddThinkStep,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.skill_recorder_add_think_step))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    // 生成 + 丢弃按钮
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onDiscardAll,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.skill_recorder_discard_all))
        }

        Button(
            onClick = onGenerate,
            modifier = Modifier.weight(1f),
            enabled = steps.isNotEmpty()
        ) {
            Text(stringResource(R.string.skill_recorder_generate))
        }
    }
}

@Composable
private fun StepCard(
    step: BuilderStep,
    index: Int,
    totalCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (step) {
                is BuilderStep.Record -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                is BuilderStep.Think -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 步骤信息
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.skill_recorder_step_n, index + 1),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = when (step) {
                                    is BuilderStep.Record -> stringResource(R.string.skill_recorder_step_record_label)
                                    is BuilderStep.Think -> stringResource(R.string.skill_recorder_step_think_label)
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                when (step) {
                    is BuilderStep.Record -> {
                        Text(
                            text = stringResource(R.string.skill_recorder_step_frames, step.frames.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is BuilderStep.Think -> {
                        Text(
                            text = step.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 操作按钮
            Column {
                Row {
                    if (index > 0) {
                        IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (index < totalCount - 1) {
                        IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (step is BuilderStep.Think) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRecordingContent(
    recordingState: RecordingState,
    stepFrameCount: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit
) {
    // 录制状态卡片
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (recordingState == RecordingState.STEP_RECORDING)
                MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (recordingState == RecordingState.STEP_RECORDING)
                    stringResource(R.string.skill_recorder_recording)
                else stringResource(R.string.skill_recorder_paused),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.skill_recorder_frame_count, stepFrameCount),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = stringResource(R.string.skill_recorder_step_recording_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(24.dp))

    // 操作按钮
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 暂停/继续
        OutlinedButton(
            onClick = {
                if (recordingState == RecordingState.STEP_RECORDING) onPause()
                else onResume()
            },
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Icon(
                if (recordingState == RecordingState.STEP_RECORDING) Icons.Default.Pause
                else Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                if (recordingState == RecordingState.STEP_RECORDING)
                    stringResource(R.string.skill_recorder_pause)
                else stringResource(R.string.skill_recorder_resume)
            )
        }

        // 停止
        Button(
            onClick = onStop,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.skill_recorder_stop))
        }

        // 丢弃
        IconButton(onClick = onDiscard) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.skill_recorder_discard),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SummarizingContent(
    onSkip: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.skill_recorder_summarizing),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    OutlinedButton(
        onClick = onSkip,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.skill_recorder_skip_summary))
    }
}

package com.ai.assistance.operit.ui.features.toolbox.screens.skillrecorder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.skillrecorder.BuilderStep
import com.ai.assistance.operit.data.model.skillrecorder.RecordingState

/**
 * 录制审阅页面：查看步骤概览、编辑并保存生成的 SKILL.md
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillRecorderReviewScreen(
    onGoBack: () -> Unit = {},
    viewModel: SkillRecorderViewModel = viewModel()
) {
    val session by viewModel.currentSession.collectAsState()
    val editedSkillMd by viewModel.editedSkillMd.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val modelConfigs by viewModel.modelConfigs.collectAsState()
    val selectedModelConfigId by viewModel.selectedModelConfigId.collectAsState()
    var skillName by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    // 初始化编辑内容
    LaunchedEffect(session?.generatedSkillMd) {
        viewModel.initEditedSkillMd()
    }

    // 生成默认 skill 名
    LaunchedEffect(session) {
        if (skillName.isBlank()) {
            skillName = "recorded-${session?.id?.take(8) ?: "skill"}"
        }
    }

    // 保存成功后自动跳回主页
    LaunchedEffect(isSaved) {
        if (isSaved) {
            viewModel.finishAfterSave()
            onGoBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 草稿显示（只读）
        val sessionDraft = session?.draftText
        if (!sessionDraft.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.skill_recorder_draft_section_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Text(
                    text = sessionDraft,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 步骤概览
        val steps = session?.steps ?: emptyList()
        if (steps.isNotEmpty()) {
            Text(
                text = stringResource(R.string.skill_recorder_steps_overview, steps.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                steps.forEachIndexed { index, step ->
                    val label = when (step) {
                        is BuilderStep.Record ->
                            "${index + 1}: ${stringResource(R.string.skill_recorder_step_record_label)} (${step.frames.size})"
                        is BuilderStep.Think ->
                            "${index + 1}: ${stringResource(R.string.skill_recorder_step_think_label)}"
                    }
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(label, maxLines = 1, fontSize = 11.sp)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // SKILL.md 编辑区
        Text(
            text = "SKILL.md",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (recordingState == RecordingState.SUMMARIZING) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.skill_recorder_summarizing))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = { viewModel.skipSummarization() }) {
                        Text(stringResource(R.string.skill_recorder_skip_summary))
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = editedSkillMd,
                onValueChange = { viewModel.updateEditedSkillMd(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 模型选择（用于重新生成）
        if (modelConfigs.size > 1) {
            val selectedConfig = modelConfigs.find { it.id == selectedModelConfigId }
            Box {
                OutlinedButton(
                    onClick = { modelDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.skill_recorder_model_label,
                            selectedConfig?.name ?: "—"),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                DropdownMenu(
                    expanded = modelDropdownExpanded,
                    onDismissRequest = { modelDropdownExpanded = false }
                ) {
                    modelConfigs.forEach { config ->
                        DropdownMenuItem(
                            text = { Text(config.name) },
                            onClick = {
                                viewModel.selectModelConfig(config.id)
                                modelDropdownExpanded = false
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
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 底部按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.regenerateSummary() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.skill_recorder_regenerate))
            }

            Button(
                onClick = { showSaveDialog = true },
                modifier = Modifier.weight(1f),
                enabled = editedSkillMd.isNotBlank()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.skill_recorder_save))
            }
        }

        // 已保存提示 + 新建录制按钮
        if (isSaved) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.skill_recorder_saved, session?.savedSkillName ?: ""),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 新建录制按钮
        OutlinedButton(
            onClick = {
                viewModel.newRecording()
                onGoBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.skill_recorder_new_recording))
        }
    }

    // 保存对话框
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.skill_recorder_save_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = skillName,
                    onValueChange = { skillName = it.replace(Regex("[^a-zA-Z0-9_-]"), "") },
                    label = { Text(stringResource(R.string.skill_recorder_skill_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveAsSkill(skillName)
                        showSaveDialog = false
                    },
                    enabled = skillName.isNotBlank()
                ) {
                    Text(stringResource(R.string.skill_recorder_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

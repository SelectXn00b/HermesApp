package com.ai.assistance.operit.ui.features.toolbox.screens.skillrecorder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.skillrecorder.RecordingState

/**
 * 录制审阅页面：查看帧时间线、编辑并保存生成的 SKILL.md
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
    var skillName by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var selectedFrameIndex by remember { mutableIntStateOf(-1) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 帧时间线
        val frames = session?.frames ?: emptyList()
        if (frames.isNotEmpty()) {
            Text(
                text = stringResource(R.string.skill_recorder_timeline, frames.size),
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
                frames.forEachIndexed { index, frame ->
                    FilterChip(
                        selected = index == selectedFrameIndex,
                        onClick = {
                            selectedFrameIndex = if (selectedFrameIndex == index) -1 else index
                        },
                        label = {
                            Text(
                                "${index + 1}: ${frame.eventType}",
                                maxLines = 1,
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }

            // 选中帧的详情
            if (selectedFrameIndex in frames.indices) {
                val frame = frames[selectedFrameIndex]
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Step ${frame.index + 1}: ${frame.eventType}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        frame.activityName?.let {
                            Text("Activity: $it", style = MaterialTheme.typography.bodySmall)
                        }
                        frame.eventDetails.text?.let {
                            Text("Text: \"$it\"", style = MaterialTheme.typography.bodySmall)
                        }
                        frame.eventDetails.contentDescription?.let {
                            Text("Desc: \"$it\"", style = MaterialTheme.typography.bodySmall)
                        }
                    }
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

        // 底部按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.stopRecording() },
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

        // 已保存提示
        if (isSaved) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.skill_recorder_saved),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
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

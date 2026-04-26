package com.ai.assistance.operit.ui.features.toolbox.screens.skillrecorder

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.skillrecorder.RecordingState

/**
 * Skill Recorder 入口页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillRecorderScreen(
    onNavigateToReview: () -> Unit = {},
    viewModel: SkillRecorderViewModel = viewModel()
) {
    val recordingState by viewModel.recordingState.collectAsState()
    val frameCount by viewModel.frameCount.collectAsState()
    val showAccessibilityPrompt by viewModel.showAccessibilityPrompt.collectAsState()
    val context = LocalContext.current

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

    // 当进入 REVIEW 状态时导航到审阅页面
    LaunchedEffect(recordingState) {
        if (recordingState == RecordingState.REVIEW) {
            onNavigateToReview()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 状态卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (recordingState) {
                    RecordingState.RECORDING -> MaterialTheme.colorScheme.errorContainer
                    RecordingState.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
                    RecordingState.SUMMARIZING -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (recordingState) {
                        RecordingState.IDLE -> stringResource(R.string.skill_recorder_ready)
                        RecordingState.RECORDING -> stringResource(R.string.skill_recorder_recording)
                        RecordingState.PAUSED -> stringResource(R.string.skill_recorder_paused)
                        RecordingState.SUMMARIZING -> stringResource(R.string.skill_recorder_summarizing)
                        RecordingState.REVIEW -> stringResource(R.string.skill_recorder_review)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                if (recordingState == RecordingState.RECORDING ||
                    recordingState == RecordingState.PAUSED
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.skill_recorder_frame_count, frameCount),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                if (recordingState == RecordingState.SUMMARIZING) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 操作按钮
        when (recordingState) {
            RecordingState.IDLE -> {
                Button(
                    onClick = { viewModel.startRecording() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.FiberManualRecord, contentDescription = null)
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

            RecordingState.RECORDING, RecordingState.PAUSED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 暂停/继续
                    OutlinedButton(
                        onClick = {
                            if (recordingState == RecordingState.RECORDING) {
                                viewModel.pauseRecording()
                            } else {
                                viewModel.resumeRecording()
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(
                            if (recordingState == RecordingState.RECORDING) Icons.Default.Pause
                            else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (recordingState == RecordingState.RECORDING)
                                stringResource(R.string.skill_recorder_pause)
                            else stringResource(R.string.skill_recorder_resume)
                        )
                    }

                    // 停止
                    Button(
                        onClick = { viewModel.stopRecording() },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.skill_recorder_stop))
                    }

                    // 丢弃
                    IconButton(onClick = { viewModel.discardRecording() }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.skill_recorder_discard),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            RecordingState.SUMMARIZING -> {
                // AI 总结中，无操作按钮
            }

            RecordingState.REVIEW -> {
                // 将自动导航到 ReviewScreen
            }
        }
    }
}

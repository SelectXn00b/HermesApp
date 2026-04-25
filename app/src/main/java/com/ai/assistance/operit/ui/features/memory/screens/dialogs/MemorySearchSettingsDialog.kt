package com.ai.assistance.operit.ui.features.memory.screens.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.CloudEmbeddingConfig
import com.ai.assistance.operit.data.model.DimensionCount
import com.ai.assistance.operit.data.model.EmbeddingDimensionUsage
import com.ai.assistance.operit.data.model.EmbeddingRebuildProgress
import com.ai.assistance.operit.data.model.MemoryScoreMode
import com.ai.assistance.operit.data.model.MemorySearchConfig
import kotlin.math.roundToInt

@Composable
fun MemorySearchSettingsDialog(
    currentConfig: MemorySearchConfig,
    cloudConfig: CloudEmbeddingConfig,
    dimensionUsage: EmbeddingDimensionUsage,
    rebuildProgress: EmbeddingRebuildProgress,
    error: String?,
    isRebuilding: Boolean,
    onDismiss: () -> Unit,
    onSave: (MemorySearchConfig, CloudEmbeddingConfig) -> Unit,
    onRebuild: () -> Unit,
    onSimulateSearch: () -> Unit
) {
    var keywordWeight by remember(currentConfig) { mutableFloatStateOf(currentConfig.keywordWeight) }
    var tagWeight by remember(currentConfig) { mutableFloatStateOf(currentConfig.tagWeight) }
    var vectorWeight by remember(currentConfig) { mutableFloatStateOf(currentConfig.vectorWeight) }
    var edgeWeight by remember(currentConfig) { mutableFloatStateOf(currentConfig.edgeWeight) }
    var scoreMode by remember(currentConfig) { mutableStateOf(currentConfig.scoreMode) }

    var cloudEnabled by remember(cloudConfig) { mutableStateOf(cloudConfig.enabled) }
    var endpoint by remember(cloudConfig) { mutableStateOf(cloudConfig.endpoint) }
    var apiKey by remember(cloudConfig) { mutableStateOf(cloudConfig.apiKey) }
    var model by remember(cloudConfig) { mutableStateOf(cloudConfig.model) }
    var showApiKey by remember { mutableStateOf(false) }

    val editedCloudConfig = CloudEmbeddingConfig(
        enabled = cloudEnabled,
        endpoint = endpoint,
        apiKey = apiKey,
        model = model
    ).normalized()

    val rebuildEnabled = !isRebuilding

    AlertDialog(
        modifier = Modifier.fillMaxHeight(0.86f),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.memory_search_settings_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsSection(title = stringResource(R.string.memory_search_weight_section)) {
                    SliderSettingItem(
                        title = stringResource(R.string.memory_search_keyword_weight),
                        value = keywordWeight,
                        valueText = String.format("%.2f", keywordWeight),
                        valueRange = 0.0f..20.0f,
                        onValueChange = { keywordWeight = it }
                    )
                    SliderSettingItem(
                        title = stringResource(R.string.memory_search_tag_weight),
                        value = tagWeight,
                        valueText = String.format("%.2f", tagWeight),
                        valueRange = 0.0f..20.0f,
                        onValueChange = { tagWeight = it }
                    )
                    SliderSettingItem(
                        title = stringResource(R.string.memory_search_vector_weight),
                        value = vectorWeight,
                        valueText = String.format("%.2f", vectorWeight),
                        valueRange = 0.0f..2.0f,
                        onValueChange = { vectorWeight = it }
                    )
                    SliderSettingItem(
                        title = stringResource(R.string.memory_search_edge_weight),
                        value = edgeWeight,
                        valueText = String.format("%.2f", edgeWeight),
                        valueRange = 0.0f..2.0f,
                        onValueChange = { edgeWeight = it }
                    )
                }

                SettingsSection(title = stringResource(R.string.memory_embedding_cloud_title)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.memory_embedding_cloud_enable),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (editedCloudConfig.isReady()) editedCloudConfig.model else stringResource(R.string.not_configured),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (editedCloudConfig.isReady()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = cloudEnabled,
                            onCheckedChange = { cloudEnabled = it }
                        )
                    }

                    if (cloudEnabled) {
                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = { endpoint = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.memory_embedding_cloud_endpoint)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.memory_embedding_cloud_api_key)) },
                            singleLine = true,
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.memory_embedding_cloud_model)) },
                            singleLine = true
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.not_configured),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SettingsSection(title = stringResource(R.string.memory_embedding_dimension_usage)) {
                    DimensionUsageBlock(
                        title = stringResource(
                            R.string.memory_embedding_dimension_memory_summary,
                            dimensionUsage.memoryTotal,
                            dimensionUsage.memoryMissing
                        ),
                        dimensions = dimensionUsage.memoryDimensions
                    )

                    DimensionUsageBlock(
                        title = stringResource(
                            R.string.memory_embedding_dimension_chunk_summary,
                            dimensionUsage.chunkTotal,
                            dimensionUsage.chunkMissing
                        ),
                        dimensions = dimensionUsage.chunkDimensions
                    )

                    OutlinedButton(
                        onClick = onRebuild,
                        enabled = rebuildEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.memory_embedding_rebuild_action))
                    }

                    if (!error.isNullOrBlank()) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (isRebuilding || rebuildProgress.total > 0) {
                        val progressValue = rebuildProgress.fraction.coerceIn(0f, 1f)
                        val progressPercent = (progressValue * 100f).roundToInt()
                        LinearProgressIndicator(
                            progress = { progressValue },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "$progressPercent%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(
                                R.string.memory_embedding_rebuild_progress,
                                rebuildProgress.processed,
                                rebuildProgress.total,
                                rebuildProgress.failed,
                                stageText(rebuildProgress.currentStage)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SettingsSection(title = stringResource(R.string.memory_search_debug_tools)) {
                    OutlinedButton(
                        onClick = onSimulateSearch,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.memory_search_simulation_open))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        MemorySearchConfig(
                            scoreMode = scoreMode,
                            keywordWeight = keywordWeight,
                            tagWeight = tagWeight,
                            vectorWeight = vectorWeight,
                            edgeWeight = edgeWeight
                        ).normalized(),
                        editedCloudConfig
                    )
                }
            ) {
                Text(stringResource(R.string.memory_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scoreMode = MemoryScoreMode.BALANCED
                        keywordWeight = 10.0f
                        tagWeight = 0.0f
                        vectorWeight = 0.0f
                        edgeWeight = 0.4f
                    }
                ) {
                    Text(stringResource(R.string.memory_search_reset_default))
                }
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.memory_cancel))
                }
            }
        }
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun DimensionUsageBlock(
    title: String,
    dimensions: List<DimensionCount>
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (dimensions.isEmpty()) {
            Text(
                text = stringResource(R.string.memory_embedding_dimension_none),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            val compact = dimensions.joinToString(separator = "  ·  ") { item ->
                context.getString(
                    R.string.memory_embedding_dimension_item,
                    item.dimension,
                    item.count
                )
            }
            Text(
                text = compact,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun SliderSettingItem(
    title: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

@Composable
private fun stageText(stage: String): String {
    return when (stage) {
        "preparing" -> stringResource(R.string.memory_embedding_stage_preparing)
        "memory_embedding" -> stringResource(R.string.memory_embedding_stage_memory_embedding)
        "chunk_embedding" -> stringResource(R.string.memory_embedding_stage_chunk_embedding)
        "memory_index" -> stringResource(R.string.memory_embedding_stage_memory_index)
        "chunk_index" -> stringResource(R.string.memory_embedding_stage_chunk_index)
        "done" -> stringResource(R.string.memory_embedding_stage_done)
        else -> stage
    }
}

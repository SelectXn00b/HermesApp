package com.ai.assistance.operit.ui.features.assistant.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.common.control.AvatarSettingKeys
import com.ai.assistance.operit.core.avatar.common.model.AvatarType
import com.ai.assistance.operit.core.avatar.common.state.AvatarCustomMoodDefinition
import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion
import com.ai.assistance.operit.core.avatar.common.state.AvatarMoodTypeDefinition
import com.ai.assistance.operit.core.avatar.common.state.AvatarMoodTypes
import com.ai.assistance.operit.ui.features.assistant.viewmodel.AssistantConfigViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun AvatarConfigSection(
    viewModel: AssistantConfigViewModel,
    uiState: AssistantConfigViewModel.UiState,
    avatarController: AvatarController?,
    onImportClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.avatar_config),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(10.dp)
                )
                .padding(12.dp)
        ) {
            val currentAvatarConfig = uiState.currentAvatarConfig
            val currentAvatarModel = uiState.currentAvatarModel
            val currentSettings = uiState.config

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.avatar_voice_call_enable_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.avatar_voice_call_enable_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = uiState.isVoiceCallAvatarEnabled,
                    onCheckedChange = viewModel::updateVoiceCallAvatarEnabled
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            var availableAnimations by remember(currentAvatarModel?.id) {
                mutableStateOf(emptyList<String>())
            }

            LaunchedEffect(avatarController, currentAvatarModel?.id) {
                if (avatarController == null) {
                    availableAnimations = emptyList()
                    return@LaunchedEffect
                }

                while (isActive) {
                    val latestAnimations =
                        avatarController.availableAnimations
                            .map { animationName -> animationName.trim() }
                            .filter { animationName -> animationName.isNotBlank() }
                            .distinct()

                    if (latestAnimations != availableAnimations) {
                        availableAnimations = latestAnimations
                    }

                    delay(300)
                }
            }

            if (currentAvatarModel != null) {
                MoodTriggerMappingSection(
                    sectionStateKey = currentAvatarModel.id,
                    avatarController = avatarController,
                    availableAnimations = availableAnimations,
                    moodAnimationMapping = uiState.moodAnimationMapping,
                    customMoodDefinitions = uiState.customMoodDefinitions,
                    onMoodAnimationMappingChange = { triggerKey, animationName ->
                        viewModel.updateMoodAnimationMapping(triggerKey, animationName)
                    },
                    onUpsertCustomMoodDefinition = { originalKey, key, promptHint ->
                        viewModel.upsertCustomMoodDefinition(originalKey, key, promptHint)
                    },
                    onDeleteCustomMoodDefinition = viewModel::deleteCustomMoodDefinition
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    ModelSelector(
                        models = uiState.avatarConfigs,
                        currentModelId = uiState.currentAvatarConfig?.id,
                        onModelSelected = { viewModel.switchAvatar(it) },
                        onModelRename = { modelId, newName ->
                            viewModel.renameAvatar(modelId, newName)
                        },
                        onModelDelete = { viewModel.deleteAvatar(it) }
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = onImportClick) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.import_model),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = stringResource(R.string.avatar_config_supported_formats),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = stringResource(R.string.avatar_config_import_methods),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            if (currentAvatarConfig != null && currentSettings != null) {
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.scale, String.format("%.2f", currentSettings.scale)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = currentSettings.scale,
                    onValueChange = { viewModel.updateScale(it) },
                    valueRange = 0.1f..2.0f
                )

                Text(
                    text = stringResource(R.string.x_translation, String.format("%.1f", currentSettings.translateX)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = currentSettings.translateX,
                    onValueChange = { viewModel.updateTranslateX(it) },
                    valueRange = -500f..500f
                )

                Text(
                    text = stringResource(R.string.y_translation, String.format("%.1f", currentSettings.translateY)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = currentSettings.translateY,
                    onValueChange = { viewModel.updateTranslateY(it) },
                    valueRange = -500f..500f
                )

                if (currentAvatarConfig.type == AvatarType.MMD) {
                    val cameraPitch =
                        currentSettings.customSettings[AvatarSettingKeys.MMD_INITIAL_ROTATION_X] ?: 0f
                    val initialRotationY =
                        currentSettings.customSettings[AvatarSettingKeys.MMD_INITIAL_ROTATION_Y] ?: 0f
                    val cameraDistanceScale =
                        currentSettings.customSettings[AvatarSettingKeys.MMD_CAMERA_DISTANCE_SCALE] ?: 1f
                    val cameraTargetHeight =
                        currentSettings.customSettings[AvatarSettingKeys.MMD_CAMERA_TARGET_HEIGHT] ?: 0f

                    Text(
                        text =
                            stringResource(
                                R.string.avatar_mmd_camera_pitch,
                                String.format("%.1f deg", cameraPitch)
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = cameraPitch,
                        onValueChange = {
                            viewModel.updateCustomSetting(
                                AvatarSettingKeys.MMD_INITIAL_ROTATION_X,
                                it
                            )
                        },
                        valueRange = -90f..90f
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.avatar_mmd_camera_yaw,
                                String.format("%.1f deg", initialRotationY)
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = initialRotationY,
                        onValueChange = {
                            viewModel.updateCustomSetting(
                                AvatarSettingKeys.MMD_INITIAL_ROTATION_Y,
                                it
                            )
                        },
                        valueRange = -180f..180f
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.avatar_mmd_camera_distance,
                                String.format("%.2fx", cameraDistanceScale)
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = cameraDistanceScale,
                        onValueChange = {
                            viewModel.updateCustomSetting(
                                AvatarSettingKeys.MMD_CAMERA_DISTANCE_SCALE,
                                it
                            )
                        },
                        valueRange = 0.02f..12.0f
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.avatar_mmd_orbit_pivot_height,
                                String.format("%.2f", cameraTargetHeight)
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = cameraTargetHeight.coerceIn(-2.0f, 2.0f),
                        onValueChange = {
                            viewModel.updateCustomSetting(
                                AvatarSettingKeys.MMD_CAMERA_TARGET_HEIGHT,
                                it
                            )
                        },
                        valueRange = -2.0f..2.0f
                    )
                }

                if (currentAvatarConfig.type == AvatarType.GLTF) {
                    val cameraPitch =
                        currentSettings.customSettings[AvatarSettingKeys.GLTF_CAMERA_PITCH] ?: 8f
                    val cameraYaw =
                        currentSettings.customSettings[AvatarSettingKeys.GLTF_CAMERA_YAW] ?: 0f
                    val cameraDistanceScale =
                        currentSettings.customSettings[AvatarSettingKeys.GLTF_CAMERA_DISTANCE_SCALE] ?: 0.5f
                    val cameraTargetHeight =
                        currentSettings.customSettings[AvatarSettingKeys.GLTF_CAMERA_TARGET_HEIGHT] ?: 0f

                    Text(
                        text =
                            stringResource(
                                R.string.avatar_gltf_camera_pitch,
                                String.format("%.1f deg", cameraPitch)
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = cameraPitch,
                        onValueChange = {
                            viewModel.updateCustomSetting(
                                AvatarSettingKeys.GLTF_CAMERA_PITCH,
                                it
                            )
                        },
                        valueRange = -89f..89f
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.avatar_gltf_camera_yaw,
                                String.format("%.1f deg", cameraYaw)
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = cameraYaw,
                        onValueChange = {
                            viewModel.updateCustomSetting(
                                AvatarSettingKeys.GLTF_CAMERA_YAW,
                                it
                            )
                        },
                        valueRange = -180f..180f
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.avatar_gltf_camera_distance,
                                String.format("%.3fx", cameraDistanceScale)
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = cameraDistanceScale.coerceIn(0.0f, 10.0f),
                        onValueChange = {
                            viewModel.updateCustomSetting(
                                AvatarSettingKeys.GLTF_CAMERA_DISTANCE_SCALE,
                                it
                            )
                        },
                        valueRange = 0.0f..10.0f
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.avatar_gltf_orbit_pivot_height,
                                String.format("%.2f", cameraTargetHeight)
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = cameraTargetHeight.coerceIn(-2.0f, 2.0f),
                        onValueChange = {
                            viewModel.updateCustomSetting(
                                AvatarSettingKeys.GLTF_CAMERA_TARGET_HEIGHT,
                                it
                            )
                        },
                        valueRange = -2.0f..2.0f
                    )
                }

                if (currentAvatarConfig.type == AvatarType.FBX) {
                    val cameraPitch =
                        currentSettings.customSettings[AvatarSettingKeys.FBX_CAMERA_PITCH] ?: 8f
                    val cameraYaw =
                        currentSettings.customSettings[AvatarSettingKeys.FBX_CAMERA_YAW] ?: 0f
                    val cameraDistanceScale =
                        currentSettings.customSettings[AvatarSettingKeys.FBX_CAMERA_DISTANCE_SCALE] ?: 1f
                    val cameraTargetHeight =
                        currentSettings.customSettings[AvatarSettingKeys.FBX_CAMERA_TARGET_HEIGHT] ?: 0f

                    Text(
                        text =
                            stringResource(
                                R.string.avatar_fbx_camera_pitch,
                                String.format("%.1f deg", cameraPitch)
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = cameraPitch,
                        onValueChange = {
                            viewModel.updateCustomSetting(
                                AvatarSettingKeys.FBX_CAMERA_PITCH,
                                it
                            )
                        },
                        valueRange = -89f..89f
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.avatar_fbx_camera_yaw,
                                String.format("%.1f deg", cameraYaw)
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = cameraYaw,
                        onValueChange = {
                            viewModel.updateCustomSetting(
                                AvatarSettingKeys.FBX_CAMERA_YAW,
                                it
                            )
                        },
                        valueRange = -180f..180f
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.avatar_fbx_camera_distance,
                                String.format("%.3fx", cameraDistanceScale)
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = cameraDistanceScale.coerceIn(0.02f, 12.0f),
                        onValueChange = {
                            viewModel.updateCustomSetting(
                                AvatarSettingKeys.FBX_CAMERA_DISTANCE_SCALE,
                                it
                            )
                        },
                        valueRange = 0.02f..12.0f
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.avatar_fbx_orbit_pivot_height,
                                String.format("%.2f", cameraTargetHeight)
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = cameraTargetHeight.coerceIn(-2.0f, 2.0f),
                        onValueChange = {
                            viewModel.updateCustomSetting(
                                AvatarSettingKeys.FBX_CAMERA_TARGET_HEIGHT,
                                it
                            )
                        },
                        valueRange = -2.0f..2.0f
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HowToImportSection()
            Spacer(modifier = Modifier.height(8.dp))
            AllAvatarImportGuideSection()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoodTriggerMappingSection(
    sectionStateKey: String,
    avatarController: AvatarController?,
    availableAnimations: List<String>,
    moodAnimationMapping: Map<String, String>,
    customMoodDefinitions: List<AvatarCustomMoodDefinition>,
    onMoodAnimationMappingChange: (String, String?) -> Unit,
    onUpsertCustomMoodDefinition: (String?, String, String) -> Unit,
    onDeleteCustomMoodDefinition: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingCustomMood by remember { mutableStateOf<AvatarCustomMoodDefinition?>(null) }
    var expanded by remember(sectionStateKey) { mutableStateOf(false) }

    if (showCreateDialog) {
        MoodTypeEditorDialog(
            initialDefinition = null,
            onDismiss = { showCreateDialog = false },
            onConfirm = { key, promptHint ->
                onUpsertCustomMoodDefinition(null, key, promptHint)
                showCreateDialog = false
            }
        )
    }

    editingCustomMood?.let { definition ->
        MoodTypeEditorDialog(
            initialDefinition = definition,
            onDismiss = { editingCustomMood = null },
            onConfirm = { key, promptHint ->
                onUpsertCustomMoodDefinition(definition.key, key, promptHint)
                editingCustomMood = null
            }
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.avatar_mood_trigger_mapping_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        TextButton(onClick = { expanded = !expanded }) {
            Text(
                text = if (expanded) {
                    stringResource(R.string.collapse)
                } else {
                    stringResource(R.string.expand)
                }
            )
        }
    }
    Text(
        text = stringResource(R.string.avatar_mood_trigger_mapping_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )

    if (!expanded) {
        return
    }

    if (availableAnimations.isEmpty()) {
        Text(
            text = stringResource(R.string.avatar_no_model_actions),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }

    Spacer(modifier = Modifier.height(6.dp))

    AvatarMoodTypes.builtInDefinitions.forEach { definition ->
        MoodMappingCard(
            title = definition.toLocalizedDisplayName(),
            triggerKey = definition.key,
            promptHint = definition.toLocalizedPromptHint(),
            mappedAnimation = moodAnimationMapping[definition.key],
            availableAnimations = availableAnimations,
            builtInDefinition = definition,
            onMoodAnimationMappingChange = onMoodAnimationMappingChange,
            onPreview = {
                previewMoodTrigger(
                    coroutineScope = coroutineScope,
                    avatarController = avatarController,
                    triggerKey = definition.key,
                    mappedAnimation = moodAnimationMapping[definition.key],
                    fallbackEmotion = definition.fallbackEmotion
                )
            }
        )
        Spacer(modifier = Modifier.height(6.dp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.avatar_custom_types_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        TextButton(onClick = { showCreateDialog = true }) {
            Text(text = stringResource(R.string.avatar_custom_type_add))
        }
    }

    if (customMoodDefinitions.isEmpty()) {
        Text(
            text = stringResource(R.string.avatar_custom_type_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        customMoodDefinitions.forEachIndexed { index, definition ->
            MoodMappingCard(
                title = definition.key,
                triggerKey = definition.key,
                promptHint = definition.promptHint,
                mappedAnimation = moodAnimationMapping[definition.key],
                availableAnimations = availableAnimations,
                onMoodAnimationMappingChange = onMoodAnimationMappingChange,
                onPreview = {
                    previewMoodTrigger(
                        coroutineScope = coroutineScope,
                        avatarController = avatarController,
                        triggerKey = definition.key,
                        mappedAnimation = moodAnimationMapping[definition.key],
                        fallbackEmotion = null
                    )
                },
                onEdit = { editingCustomMood = definition },
                onDelete = { onDeleteCustomMoodDefinition(definition.key) }
            )
            if (index < customMoodDefinitions.lastIndex) {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoodMappingCard(
    title: String,
    triggerKey: String,
    promptHint: String,
    mappedAnimation: String?,
    availableAnimations: List<String>,
    builtInDefinition: AvatarMoodTypeDefinition? = null,
    onMoodAnimationMappingChange: (String, String?) -> Unit,
    onPreview: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val canPreview = onPreview != null && !mappedAnimation.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                RoundedCornerShape(10.dp)
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.avatar_trigger_key_label, triggerKey),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = promptHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                builtInDefinition?.fallbackEmotion?.let { fallbackEmotion ->
                    Text(
                        text =
                            stringResource(
                                R.string.avatar_fallback_emotion_label,
                                fallbackEmotion.toDisplayName()
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            if (onEdit != null || onDelete != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    onEdit?.let { editAction ->
                        IconButton(onClick = editAction) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.avatar_edit_custom_mood_type)
                            )
                        }
                    }
                    onDelete?.let { deleteAction ->
                        IconButton(onClick = deleteAction) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.avatar_delete_custom_mood_type)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AnimationSelectionField(
            stateKey = triggerKey,
            availableAnimations = availableAnimations,
            selectedAnimation = mappedAnimation,
            label = stringResource(R.string.avatar_mapped_action),
            onAnimationSelected = { animationName ->
                onMoodAnimationMappingChange(triggerKey, animationName)
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { onPreview?.invoke() },
                enabled = canPreview
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.avatar_preview_action),
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(text = stringResource(R.string.avatar_preview_action))
            }
            TextButton(
                onClick = {
                    onMoodAnimationMappingChange(triggerKey, null)
                },
                enabled = !mappedAnimation.isNullOrBlank()
            ) {
                Text(text = stringResource(R.string.clear))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimationSelectionField(
    stateKey: String,
    availableAnimations: List<String>,
    selectedAnimation: String?,
    label: String,
    onAnimationSelected: (String) -> Unit
) {
    var expanded by remember(stateKey) { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (availableAnimations.isNotEmpty()) {
                expanded = !expanded
            }
        }
    ) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            value = selectedAnimation ?: stringResource(R.string.avatar_unmapped),
            onValueChange = {},
            readOnly = true,
            enabled = availableAnimations.isNotEmpty(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            label = { Text(text = label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded
                )
            }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableAnimations.forEach { animationName ->
                DropdownMenuItem(
                    text = { Text(text = animationName) },
                    onClick = {
                        onAnimationSelected(animationName)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MoodTypeEditorDialog(
    initialDefinition: AvatarCustomMoodDefinition?,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var keyInput by remember(initialDefinition?.key) {
        mutableStateOf(initialDefinition?.key.orEmpty())
    }
    var promptHintInput by remember(initialDefinition?.promptHint) {
        mutableStateOf(initialDefinition?.promptHint.orEmpty())
    }
    val normalizedKeyPreview = remember(keyInput) {
        AvatarMoodTypes.normalizeKey(keyInput)
    }
    val canConfirm = keyInput.trim().isNotEmpty() && promptHintInput.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text =
                    if (initialDefinition == null) {
                        stringResource(R.string.avatar_custom_type_add_title)
                    } else {
                        stringResource(R.string.avatar_custom_type_edit_title)
                    }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.avatar_custom_type_key)) }
                )
                Text(
                    text =
                        stringResource(
                            R.string.avatar_custom_type_key_hint,
                            normalizedKeyPreview.ifBlank {
                                stringResource(R.string.avatar_empty_value_placeholder)
                            }
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = promptHintInput,
                    onValueChange = { promptHintInput = it },
                    label = { Text(stringResource(R.string.avatar_custom_type_prompt_hint_label)) }
                )
                Text(
                    text = stringResource(R.string.avatar_custom_type_prompt_hint_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(keyInput, promptHintInput)
                },
                enabled = canConfirm
            ) {
                Text(
                    text =
                        if (initialDefinition == null) {
                            stringResource(R.string.add)
                        } else {
                            stringResource(R.string.save)
                        }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun AllAvatarImportGuideSection(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                RoundedCornerShape(10.dp)
            )
            .padding(10.dp)
    ) {
        Text(
            text =
                if (expanded) {
                    stringResource(R.string.import_structure_guide_tap_to_collapse)
                } else {
                    stringResource(R.string.import_structure_guide_tap_to_expand)
                },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { expanded = !expanded }
        )

        if (expanded) {
            Text(
                text = stringResource(R.string.import_structure_guide_content),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
    models: List<com.ai.assistance.operit.data.repository.AvatarConfig>,
    currentModelId: String?,
    onModelSelected: (String) -> Unit,
    onModelRename: (String, String) -> Unit,
    onModelDelete: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentModel = models.find { it.id == currentModelId }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }

    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.confirm_delete_model_title)) },
            text = { Text(stringResource(R.string.confirm_delete_model_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onModelDelete(showDeleteDialog!!)
                        showDeleteDialog = null
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showRenameDialog != null) {
        val canRename = renameInput.trim().isNotEmpty()
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text(stringResource(R.string.rename_avatar_model_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.rename_avatar_model_message))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.avatar_model_name_label)) }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRenameDialog?.let { modelId ->
                            onModelRename(modelId, renameInput.trim())
                        }
                        showRenameDialog = null
                    },
                    enabled = canRename
                ) {
                    Text(stringResource(R.string.rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = currentModel?.name ?: stringResource(R.string.select_model),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            label = { Text(stringResource(R.string.current_model)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (model in models) {
                DropdownMenuItem(
                    text = { Text(model.name) },
                    onClick = {
                        onModelSelected(model.id)
                        expanded = false
                    },
                    trailingIcon = {
                        Row {
                            IconButton(
                                onClick = {
                                    showRenameDialog = model.id
                                    renameInput = model.name
                                    expanded = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.rename)
                                )
                            }
                            IconButton(
                                onClick = {
                                    showDeleteDialog = model.id
                                    expanded = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete)
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AvatarEmotion.toDisplayName(): String {
    return when (this) {
        AvatarEmotion.IDLE -> stringResource(R.string.avatar_emotion_idle)
        AvatarEmotion.LISTENING -> stringResource(R.string.avatar_emotion_listening)
        AvatarEmotion.THINKING -> stringResource(R.string.avatar_emotion_thinking)
        AvatarEmotion.HAPPY -> stringResource(R.string.avatar_emotion_happy)
        AvatarEmotion.SAD -> stringResource(R.string.avatar_emotion_sad)
        AvatarEmotion.CONFUSED -> stringResource(R.string.avatar_emotion_confused)
        AvatarEmotion.SURPRISED -> stringResource(R.string.avatar_emotion_surprised)
    }
}

@Composable
private fun AvatarMoodTypeDefinition.toLocalizedDisplayName(): String {
    return when (key) {
        "angry" -> stringResource(R.string.avatar_mood_angry_name)
        "happy" -> stringResource(R.string.avatar_mood_happy_name)
        "shy" -> stringResource(R.string.avatar_mood_shy_name)
        "aojiao" -> stringResource(R.string.avatar_mood_aojiao_name)
        "cry" -> stringResource(R.string.avatar_mood_cry_name)
        else -> displayName
    }
}

@Composable
private fun AvatarMoodTypeDefinition.toLocalizedPromptHint(): String {
    return when (key) {
        "angry" -> stringResource(R.string.avatar_mood_angry_hint)
        "happy" -> stringResource(R.string.avatar_mood_happy_hint)
        "shy" -> stringResource(R.string.avatar_mood_shy_hint)
        "aojiao" -> stringResource(R.string.avatar_mood_aojiao_hint)
        "cry" -> stringResource(R.string.avatar_mood_cry_hint)
        else -> promptHint
    }
}

private fun previewMoodTrigger(
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    avatarController: AvatarController?,
    triggerKey: String,
    mappedAnimation: String?,
    fallbackEmotion: AvatarEmotion?
) {
    if (avatarController == null || mappedAnimation.isNullOrBlank()) {
        return
    }

    coroutineScope.launch {
        val handled = avatarController.playTrigger(triggerKey, loop = 1)
        if (!handled) {
            avatarController.playAnimation(mappedAnimation, loop = 1)
        }

        val durationMillis =
            avatarController.estimateTriggerDurationMillis(triggerKey)
                ?: fallbackEmotion?.let { avatarController.estimateEmotionDurationMillis(it) }

        durationMillis?.let {
            delay(it)
            avatarController.setEmotion(AvatarEmotion.IDLE)
        }
    }
}

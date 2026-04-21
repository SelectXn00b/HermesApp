package com.ai.assistance.operit.ui.features.chat.components.style.input.classic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Portrait
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import android.widget.Toast
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.FunctionConfigMapping
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.model.getModelList
import com.ai.assistance.operit.data.model.getValidModelIndex
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.InputMenuToggleHookParams
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.InputMenuTogglePluginRegistry
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.CharacterCardModelBindingSwitchConfirmDialog
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.ToolPromptManagerDialog
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import java.text.DecimalFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.ai.assistance.operit.R

@Composable
fun ClassicChatSettingsBar(
    modifier: Modifier = Modifier,
    featureStates: Map<String, Boolean>,
    onToggleFeature: (String) -> Unit,
    permissionLevel: PermissionLevel,
    onTogglePermission: () -> Unit,
    enableThinkingMode: Boolean,
    onToggleThinkingMode: () -> Unit,
    enableThinkingGuidance: Boolean,
    onToggleThinkingGuidance: () -> Unit,
    thinkingQualityLevel: Int,
    onThinkingQualityLevelChange: (Int) -> Unit,
    maxWindowSizeInK: Float,
    baseContextLengthInK: Float,
    maxContextLengthInK: Float,
    onContextLengthChange: (Float) -> Unit,
    enableMemoryQuery: Boolean,
    onToggleMemoryQuery: () -> Unit,
    enableMaxContextMode: Boolean,
    onToggleEnableMaxContextMode: () -> Unit,
    summaryTokenThreshold: Float,
    onSummaryTokenThresholdChange: (Float) -> Unit,
    onNavigateToUserPreferences: () -> Unit,
    onNavigateToModelConfig: () -> Unit,
    onNavigateToModelPrompts: () -> Unit,
    onNavigateToPackageManager: () -> Unit,
    isAutoReadEnabled: Boolean,
    onToggleAutoRead: () -> Unit,
    enableTools: Boolean,
    onToggleTools: () -> Unit,
    toolPromptVisibility: Map<String, Boolean>,
    onSaveToolPromptVisibilityMap: (Map<String, Boolean>) -> Unit,
    disableStreamOutput: Boolean,
    onToggleDisableStreamOutput: () -> Unit,
    disableUserPreferenceDescription: Boolean,
    onToggleDisableUserPreferenceDescription: () -> Unit,
    disableStatusTags: Boolean,
    onToggleDisableStatusTags: () -> Unit,
    onManualMemoryUpdate: () -> Unit,
    onManualSummarizeConversation: () -> Unit,
    characterCardBoundChatModelConfigId: String? = null,
    characterCardBoundChatModelIndex: Int = 0
) {
    var showMenu by remember { mutableStateOf(false) }
    val iconScale by
            animateFloatAsState(targetValue = if (showMenu) 1.2f else 1f, label = "iconScale")

    // 用于显示详情说明的状态，现在使用一个Pair来保存标题和内容
    var infoPopupContent by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var showPromptDropdown by remember { mutableStateOf(false) }
    var showMemoryDropdown by remember { mutableStateOf(false) }
    var showThinkingDropdown by remember { mutableStateOf(false) }
    var showDisableSettingsDropdown by remember { mutableStateOf(false) }
    var showToolPromptManagerDialog by remember { mutableStateOf(false) }
    var showCharacterCardBindingSwitchConfirm by remember { mutableStateOf(false) }
    var pendingCharacterCardModelSelection by remember { mutableStateOf<Pair<String, Int>?>(null) }

    // 将模型选择逻辑封装到组件内部
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    val functionalConfigManager = remember { FunctionalConfigManager(context) }
    val modelConfigManager = remember { ModelConfigManager(context) }
    val configMappingWithIndex by
            functionalConfigManager.functionConfigMappingWithIndexFlow.collectAsState(initial = emptyMap())
    var configSummaries by remember { mutableStateOf<List<ModelConfigSummary>>(emptyList()) }
    LaunchedEffect(Unit) { configSummaries = modelConfigManager.getAllConfigSummaries() }
    val currentConfigMapping =
            configMappingWithIndex[FunctionType.CHAT] ?: FunctionConfigMapping(FunctionalConfigManager.DEFAULT_CONFIG_ID, 0)
    val isModelSelectionLockedByCharacterCard = !characterCardBoundChatModelConfigId.isNullOrBlank()
    val effectiveCurrentConfigMapping =
            if (isModelSelectionLockedByCharacterCard) {
                FunctionConfigMapping(
                        characterCardBoundChatModelConfigId
                                ?: FunctionalConfigManager.DEFAULT_CONFIG_ID,
                        characterCardBoundChatModelIndex.coerceAtLeast(0)
                )
            } else {
                currentConfigMapping
            }
    
    // 获取上下文长度设置，用于显示在 MaxMode 描述中
    // 新增：用户偏好（记忆）选择逻辑
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val activeProfileId by
            userPreferencesManager.activeProfileIdFlow.collectAsState(initial = "default")
    var preferenceProfiles by remember { mutableStateOf<List<PreferenceProfile>>(emptyList()) }
    LaunchedEffect(Unit) {
        val profileIds = userPreferencesManager.profileListFlow.first()
        preferenceProfiles =
                profileIds.map { id -> userPreferencesManager.getUserPreferencesFlow(id).first() }
    }
    
    // 获取聊天设置按钮右边距设置
    val chatSettingsBarRightMargin by
            userPreferencesManager.chatSettingsButtonEndPadding.collectAsState(initial = 2f)
    val inputMenuToggles = InputMenuTogglePluginRegistry.changeVersion.collectAsState().value.let {
        InputMenuTogglePluginRegistry.createToggles(
            params = InputMenuToggleHookParams(
                context = context,
                featureStates = featureStates,
                onToggleFeature = onToggleFeature
            )
        )
    }

    val onSelectModel: (String, Int) -> Unit = { selectedId, modelIndex ->
        if (isModelSelectionLockedByCharacterCard) {
            val currentModelIndex =
                configSummaries.find { it.id == effectiveCurrentConfigMapping.configId }?.let { config ->
                    getValidModelIndex(config.modelName, effectiveCurrentConfigMapping.modelIndex)
                } ?: effectiveCurrentConfigMapping.modelIndex.coerceAtLeast(0)
            val isSameSelection =
                selectedId == effectiveCurrentConfigMapping.configId && modelIndex == currentModelIndex
            if (isSameSelection) {
                showModelDropdown = false
            } else {
                pendingCharacterCardModelSelection = selectedId to modelIndex
                showModelDropdown = false
                showCharacterCardBindingSwitchConfirm = true
            }
        } else {
            scope.launch {
                functionalConfigManager.setConfigForFunction(FunctionType.CHAT, selectedId, modelIndex)
                EnhancedAIService.refreshServiceForFunction(context, FunctionType.CHAT)
            }
        }
    }

    val onSelectMemory: (String) -> Unit = { selectedId ->
        scope.launch {
            userPreferencesManager.setActiveProfile(selectedId)
            // 用户偏好和记忆库绑定，可能影响AI行为，所以刷新服务
            EnhancedAIService.refreshServiceForFunction(context, FunctionType.CHAT)
        }
    }

    CharacterCardModelBindingSwitchConfirmDialog(
        visible = showCharacterCardBindingSwitchConfirm,
        onConfirm = {
            val selection = pendingCharacterCardModelSelection
            if (selection == null) {
                showCharacterCardBindingSwitchConfirm = false
                return@CharacterCardModelBindingSwitchConfirmDialog
            }
            scope.launch {
                val activePrompt = activePromptManager.getActivePrompt()
                val activeCard = when (activePrompt) {
                    is ActivePrompt.CharacterCard -> characterCardManager.getCharacterCard(activePrompt.id)
                    is ActivePrompt.CharacterGroup -> null
                }
                if (activeCard != null) {
                    characterCardManager.updateCharacterCard(
                        activeCard.copy(
                            chatModelBindingMode = CharacterCardChatModelBindingMode.FIXED_CONFIG,
                            chatModelConfigId = selection.first,
                            chatModelIndex = selection.second.coerceAtLeast(0),
                        )
                    )
                }
                showModelDropdown = false
                showCharacterCardBindingSwitchConfirm = false
                pendingCharacterCardModelSelection = null
            }
        },
        onDismiss = {
            showCharacterCardBindingSwitchConfirm = false
            pendingCharacterCardModelSelection = null
        }
    )

    // The passed modifier will align this Box within its parent.
    Box(modifier = modifier.padding(end = chatSettingsBarRightMargin.dp)) {
        IconButton(
            onClick = { showMenu = !showMenu },
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 2.dp)
                .size(28.dp)
                .align(Alignment.BottomEnd)
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = stringResource(R.string.settings_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp).scale(iconScale)
            )
        }

        if (showMenu) {
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = {
                    showMenu = false
                    showModelDropdown = false // 关闭主菜单时也关闭模型菜单
                    showPromptDropdown = false
                    showMemoryDropdown = false
                    showThinkingDropdown = false
                },
                    properties =
                            PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(modifier = Modifier.padding(top = 0.dp, bottom = 76.dp)) {
                    Card(
                        modifier = Modifier.width(280.dp),
                        shape = RoundedCornerShape(8.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.surface.copy(
                                                            alpha = 0.95f
                                                    )
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                                modifier =
                                        Modifier.padding(vertical = 4.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // ========== 基础配置类 ==========
                            // 模型选择器
                            ModelSelectorItem(
                                configSummaries = configSummaries,
                                currentConfigMapping = effectiveCurrentConfigMapping,
                                onSelectModel = onSelectModel,
                                expanded = showModelDropdown,
                                    onExpandedChange = { showModelDropdown = it },
                                    onManageClick = {
                                        onNavigateToModelConfig()
                                        showMenu = false
                                    },
                                    onInfoClick = {
                                        infoPopupContent =
                                                context.getString(R.string.model_config) to context.getString(R.string.model_config_desc)
                                        showMenu = false
                                    }
                            )

                            // 记忆选择器
                            MemorySelectorItem(
                                preferenceProfiles = preferenceProfiles,
                                currentProfileId = activeProfileId,
                                onSelectMemory = onSelectMemory,
                                expanded = showMemoryDropdown,
                                onExpandedChange = { showMemoryDropdown = it },
                                onInfoClick = {
                                        infoPopupContent =
                                                context.getString(R.string.memory) to context.getString(R.string.memory_desc)
                                        showMenu = false
                                    },
                                    onManageClick = {
                                        onNavigateToUserPreferences()
                                    showMenu = false
                                }
                            )

                            ThinkingSettingsItem(
                                enableThinkingMode = enableThinkingMode,
                                onToggleThinkingMode = onToggleThinkingMode,
                                enableThinkingGuidance = enableThinkingGuidance,
                                onToggleThinkingGuidance = onToggleThinkingGuidance,
                                thinkingQualityLevel = thinkingQualityLevel,
                                onThinkingQualityLevelChange = onThinkingQualityLevelChange,
                                expanded = showThinkingDropdown,
                                onExpandedChange = { showThinkingDropdown = it },
                                onInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.thinking_settings) to context.getString(R.string.thinking_settings_desc)
                                    showMenu = false
                                },
                                onThinkingModeInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.thinking_mode) to context.getString(R.string.thinking_mode_desc)
                                    showMenu = false
                                },
                                onThinkingQualityInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.thinking_quality) to context.getString(R.string.thinking_quality_desc)
                                    showMenu = false
                                },
                                onThinkingGuidanceInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.thinking_guidance) to context.getString(R.string.thinking_guidance_desc)
                                    showMenu = false
                                }
                            )

                            DisableSettingsGroupItem(
                                disableStreamOutput = disableStreamOutput,
                                onToggleDisableStreamOutput = onToggleDisableStreamOutput,
                                enableTools = enableTools,
                                onToggleTools = onToggleTools,
                                disableUserPreferenceDescription = disableUserPreferenceDescription,
                                onToggleDisableUserPreferenceDescription =
                                        onToggleDisableUserPreferenceDescription,
                                disableStatusTags = disableStatusTags,
                                onToggleDisableStatusTags = onToggleDisableStatusTags,
                                expanded = showDisableSettingsDropdown,
                                onExpandedChange = { showDisableSettingsDropdown = it },
                                onInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.disable_settings_group) to context.getString(R.string.disable_settings_group_desc)
                                    showMenu = false
                                },
                                onDisableStreamOutputInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.disable_stream_output) to context.getString(R.string.disable_stream_output_desc)
                                    showMenu = false
                                },
                                onDisableToolsInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.disable_tools) to context.getString(R.string.disable_tools_desc)
                                    showMenu = false
                                },
                                onDisableUserPreferenceDescriptionInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.disable_user_preference_description) to context.getString(R.string.disable_user_preference_description_desc)
                                    showMenu = false
                                },
                                onDisableStatusTagsInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.disable_status_tags) to context.getString(R.string.disable_status_tags_desc)
                                    showMenu = false
                                },
                                onManageToolsClick = {
                                    showToolPromptManagerDialog = true
                                    showMenu = false
                                }
                            )

                            // Max模式
                            SettingItem(
                                title = stringResource(R.string.max_mode_title),
                                icon = if (enableMaxContextMode) Icons.Rounded.Whatshot else Icons.Outlined.Whatshot,
                                iconTint = if (enableMaxContextMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                isChecked = enableMaxContextMode,
                                onToggle = onToggleEnableMaxContextMode,
                                onInfoClick = {
                                    val normalLengthText = if (baseContextLengthInK % 1f == 0f) {
                                        baseContextLengthInK.toInt().toString()
                                    } else {
                                        String.format("%.1f", baseContextLengthInK)
                                    }
                                    val maxLengthText = if (maxContextLengthInK % 1f == 0f) {
                                        maxContextLengthInK.toInt().toString()
                                    } else {
                                        String.format("%.1f", maxContextLengthInK)
                                    }
                                    infoPopupContent = context.getString(R.string.max_mode_title) to context.getString(
                                        R.string.max_mode_info,
                                        normalLengthText,
                                        maxLengthText
                                    )
                                    showMenu = false
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                thickness = 0.5.dp,
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.2f
                                            )
                            )

                            // ========== 记忆相关 ==========
                            // 记忆附着
                            SettingItem(
                                title = stringResource(R.string.memory_attachment),
                                    icon =
                                            if (enableMemoryQuery) Icons.Rounded.Link
                                            else Icons.Outlined.LinkOff,
                                    iconTint =
                                            if (enableMemoryQuery)
                                                    MaterialTheme.colorScheme.primary
                                            else
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.7f
                                                    ),
                                isChecked = enableMemoryQuery,
                                onToggle = onToggleMemoryQuery,
                                onInfoClick = {
                                        infoPopupContent =
                                                context.getString(R.string.memory_attachment) to context.getString(R.string.memory_attachment_desc)
                                    showMenu = false
                                }
                            )

                            // 手动更新记忆
                            ActionSettingItem(
                                title = stringResource(R.string.manual_memory_update),
                                icon = Icons.Outlined.Save,
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                onClick = {
                                    onManualMemoryUpdate()
                                    showMenu = false
                                },
                                onInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.manual_memory_update) to context.getString(R.string.manual_memory_update_desc)
                                    showMenu = false
                                }
                            )

                            // 手动总结对话
                            ActionSettingItem(
                                title = stringResource(R.string.manual_conversation_summary),
                                icon = Icons.Outlined.History,
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                onClick = {
                                    onManualSummarizeConversation()
                                    showMenu = false
                                },
                                onInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.manual_conversation_summary) to context.getString(R.string.manual_conversation_summary_desc)
                                    showMenu = false
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                thickness = 0.5.dp,
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.2f
                                            )
                            )

                            // ========== 输出相关 ==========
                            // 自动朗读
                            SettingItem(
                                title = stringResource(R.string.auto_read_message),
                                    icon =
                                            if (isAutoReadEnabled) Icons.AutoMirrored.Rounded.VolumeUp
                                            else Icons.AutoMirrored.Outlined.VolumeOff,
                                    iconTint =
                                            if (isAutoReadEnabled)
                                                    MaterialTheme.colorScheme.primary
                                            else
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.7f
                                                    ),
                                isChecked = isAutoReadEnabled,
                                onToggle = onToggleAutoRead,
                                onInfoClick = {
                                        infoPopupContent =
                                                context.getString(R.string.auto_read_message) to context.getString(R.string.auto_read_desc)
                                    showMenu = false
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                thickness = 0.5.dp,
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.2f
                                            )
                            )

                            // ========== AI功能类 ==========
                            inputMenuToggles.forEach { toggle ->
                                val toggleTitle =
                                    if (toggle.titleRes != 0) stringResource(toggle.titleRes)
                                    else toggle.title.orEmpty()
                                SettingItem(
                                    title = toggleTitle,
                                    icon = Icons.Outlined.Hub,
                                    iconTint =
                                        if (!toggle.isEnabled)
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        else if (toggle.isChecked) MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.7f
                                            ),
                                    isChecked = toggle.isChecked,
                                    isEnabled = toggle.isEnabled,
                                    onToggle = toggle.onToggle,
                                    onInfoClick = {
                                        val infoTitle =
                                            if (toggle.titleRes != 0) context.getString(toggle.titleRes)
                                            else toggle.title.orEmpty()
                                        val infoDescription =
                                            if (toggle.descriptionRes != 0) context.getString(toggle.descriptionRes)
                                            else toggle.description.orEmpty()
                                        infoPopupContent =
                                            infoTitle to infoDescription
                                        showMenu = false
                                    }
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                thickness = 0.5.dp,
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.2f
                                            )
                            )

                            // ========== 权限与工具 ==========

                            // 自动批准
                            SettingItem(
                                title = stringResource(R.string.auto_approve),
                                    icon =
                                            if (permissionLevel == PermissionLevel.ALLOW)
                                                    Icons.Rounded.Security
                                            else Icons.Outlined.Security,
                                    iconTint =
                                            if (permissionLevel == PermissionLevel.ALLOW)
                                                    MaterialTheme.colorScheme.primary
                                            else
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.7f
                                                    ),
                                isChecked = permissionLevel == PermissionLevel.ALLOW,
                                onToggle = onTogglePermission,
                                onInfoClick = {
                                        infoPopupContent =
                                                context.getString(R.string.auto_approve) to context.getString(R.string.auto_approve_desc)
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        ToolPromptManagerDialog(
            visible = showToolPromptManagerDialog,
            toolPromptVisibility = toolPromptVisibility,
            onSaveToolPromptVisibilityMap = onSaveToolPromptVisibilityMap,
            onDismissRequest = { showToolPromptManagerDialog = false },
            onManagePackagesClick = {
                showToolPromptManagerDialog = false
                onNavigateToPackageManager()
            },
        )

        // 详情说明弹窗
        if (infoPopupContent != null) {
            Popup(
                alignment = Alignment.TopStart, // 将弹窗对齐到父布局的左上角
                onDismissRequest = { infoPopupContent = null },
                    properties =
                            PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(
                        modifier =
                                Modifier.padding(
                                        top = 0.dp,
                                        bottom = 76.dp,
                                        end = 40.dp
                                ) // 调整边距，使其显示在左侧
                ) {
                    Card(
                        modifier = Modifier.width(220.dp),
                        shape = RoundedCornerShape(8.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.surface.copy(
                                                            alpha = 0.95f
                                                    )
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = infoPopupContent!!.first,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = infoPopupContent!!.second,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    isChecked: Boolean,
    isEnabled: Boolean = true,
    onToggle: () -> Unit,
    onInfoClick: () -> Unit
) {
    val context = LocalContext.current
    val stateDescription = if (isChecked) {
        context.getString(R.string.enabled)
    } else {
        context.getString(R.string.disabled)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .padding(horizontal = 12.dp)
            .semantics {
                contentDescription = title
            }
            .toggleable(
                value = isChecked,
                enabled = isEnabled,
                onValueChange = { onToggle() },
                role = Role.Switch
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .size(16.dp)
                .clearAndSetSemantics {}
        )
        // 详情按钮（左侧）
        IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
        // 文本
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color =
                if (isEnabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .clearAndSetSemantics {}
        )
        // 开关
        Switch(
            checked = isChecked,
            onCheckedChange = null, // 由Row的toggleable处理
            enabled = isEnabled,
            modifier = Modifier
                .scale(0.65f)
                .clearAndSetSemantics {},
                colors =
                        SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun SettingSliderItem(
    label: String,
    icon: ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    onInfoClick: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    decimalFormatPattern: String,
    unitText: String? = null
) {
    var sliderValue by remember { mutableStateOf(value) }
    val df = remember(decimalFormatPattern) { DecimalFormat(decimalFormatPattern) }
    var textValue by remember { mutableStateOf(df.format(value)) }
    val focusManager = LocalFocusManager.current

    // When the external value changes, sync the internal state
    LaunchedEffect(value) {
        sliderValue = value
        textValue = df.format(value)
    }

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            // Info button
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            BasicTextField(
                value = textValue,
                onValueChange = { newText ->
                    textValue = newText
                    newText.toFloatOrNull()?.let {
                        sliderValue = it.coerceIn(valueRange)
                    }
                },
                modifier = Modifier
                    .width(50.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val finalValue = textValue.toFloatOrNull()?.coerceIn(valueRange) ?: sliderValue
                        onValueChange(finalValue)
                        textValue = df.format(finalValue)
                        focusManager.clearFocus()
                    }
                ),
                singleLine = true
            )

            // Here is the fix for alignment
            Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.CenterStart) {
                if (unitText != null) {
                    Text(
                        text = unitText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }

        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                textValue = df.format(it)
            },
            onValueChangeFinished = { onValueChange(sliderValue) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth().height(24.dp)
        )
    }
}

@Composable
private fun DisableSettingsGroupItem(
    disableStreamOutput: Boolean,
    onToggleDisableStreamOutput: () -> Unit,
    enableTools: Boolean,
    onToggleTools: () -> Unit,
    disableUserPreferenceDescription: Boolean,
    onToggleDisableUserPreferenceDescription: () -> Unit,
    disableStatusTags: Boolean,
    onToggleDisableStatusTags: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    onDisableStreamOutputInfoClick: () -> Unit,
    onDisableToolsInfoClick: () -> Unit,
    onDisableUserPreferenceDescriptionInfoClick: () -> Unit,
    onDisableStatusTagsInfoClick: () -> Unit,
    onManageToolsClick: () -> Unit
) {
    val disabledStates =
            listOf(
                            disableStreamOutput,
                            !enableTools,
                            disableUserPreferenceDescription,
                            disableStatusTags
                    )
    val disabledCount = disabledStates.count { it }
    val summaryText = "$disabledCount/${disabledStates.size}"
    val expandStateDesc = if (expanded) stringResource(R.string.expanded) else stringResource(R.string.collapsed)
    val accessibilityDesc = "${stringResource(R.string.disable_settings_group)}: $summaryText, $expandStateDesc"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .heightIn(min = 36.dp)
                                .semantics { contentDescription = accessibilityDesc }
                                .clickable { onExpandedChange(!expanded) }
                                .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp).clearAndSetSemantics {}
            )
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.disable_settings_group) + ":",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clearAndSetSemantics {}
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = summaryText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }
            Icon(
                    imageVector =
                            if (expanded) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        if (expanded) {
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                    .padding(horizontal = 12.dp)
            ) {
                SettingItem(
                    title = stringResource(R.string.disable_stream_output),
                        icon = if (disableStreamOutput) Icons.Outlined.Block else Icons.Outlined.Speed,
                        iconTint =
                                if (disableStreamOutput) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    isChecked = disableStreamOutput,
                    onToggle = onToggleDisableStreamOutput,
                    onInfoClick = onDisableStreamOutputInfoClick
                )

                SettingItem(
                    title = stringResource(R.string.disable_tools),
                        icon = if (!enableTools) Icons.Outlined.Block else Icons.Outlined.Build,
                        iconTint =
                                if (!enableTools) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    isChecked = !enableTools,
                    onToggle = onToggleTools,
                    onInfoClick = onDisableToolsInfoClick
                )

                SettingItem(
                    title = stringResource(R.string.disable_user_preference_description),
                        icon =
                                if (disableUserPreferenceDescription) Icons.Outlined.Block
                                else Icons.Outlined.Portrait,
                        iconTint =
                                if (disableUserPreferenceDescription) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    isChecked = disableUserPreferenceDescription,
                    onToggle = onToggleDisableUserPreferenceDescription,
                    onInfoClick = onDisableUserPreferenceDescriptionInfoClick
                )

                SettingItem(
                    title = stringResource(R.string.disable_status_tags),
                        icon = Icons.Outlined.Block,
                        iconTint =
                                if (disableStatusTags) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    isChecked = disableStatusTags,
                    onToggle = onToggleDisableStatusTags,
                    onInfoClick = onDisableStatusTagsInfoClick
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clickable(onClick = onManageToolsClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.manage_tools),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ThinkingSettingsItem(
    enableThinkingMode: Boolean,
    onToggleThinkingMode: () -> Unit,
    enableThinkingGuidance: Boolean,
    onToggleThinkingGuidance: () -> Unit,
    thinkingQualityLevel: Int,
    onThinkingQualityLevelChange: (Int) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    onThinkingModeInfoClick: () -> Unit,
    onThinkingQualityInfoClick: () -> Unit,
    onThinkingGuidanceInfoClick: () -> Unit
) {
    val context = LocalContext.current

    @Composable
    fun ThinkingSubSettingItem(
        title: String,
        icon: ImageVector,
        iconTint: Color,
        isChecked: Boolean,
        onToggle: () -> Unit,
        onInfoClick: () -> Unit
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else Color.Transparent
                    )
                    .toggleable(
                        value = isChecked,
                        onValueChange = { onToggle() },
                        role = Role.Switch
                    )
                    .heightIn(min = 36.dp)
                    .padding(horizontal = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp).clearAndSetSemantics {}
                )
                IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.details),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).clearAndSetSemantics {}
                )
                Switch(
                    checked = isChecked,
                    onCheckedChange = null,
                    modifier =
                        Modifier.align(Alignment.CenterVertically)
                            .scale(0.65f)
                            .clearAndSetSemantics {},
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }

    val expandStateDesc =
            if (expanded) stringResource(R.string.expanded) else stringResource(R.string.collapsed)

    val thinkingTypeText =
            when {
                enableThinkingGuidance -> stringResource(R.string.thinking_type_guidance)
                enableThinkingMode -> stringResource(R.string.thinking_type_mode)
                else -> stringResource(R.string.thinking_type_off)
            }

    val stateText = buildString {
        append(stringResource(R.string.thinking_mode))
        append(": ")
        append(if (enableThinkingMode) context.getString(R.string.enabled) else context.getString(R.string.disabled))
        append(", ")
        append(stringResource(R.string.thinking_guidance))
        append(": ")
        append(
            if (enableThinkingGuidance) context.getString(R.string.enabled)
            else context.getString(R.string.disabled)
        )
    }
    val accessibilityDesc =
            "${stringResource(R.string.thinking_settings)}: $thinkingTypeText, $stateText, $expandStateDesc"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .heightIn(min = 36.dp)
                            .semantics { contentDescription = accessibilityDesc }
                            .clickable { onExpandedChange(!expanded) }
                            .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp).clearAndSetSemantics {}
            )
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.thinking_settings) + ":",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clearAndSetSemantics {}
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = thinkingTypeText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false).clearAndSetSemantics {}
                )
            }
            Icon(
                imageVector =
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        if (expanded) {
            Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp)
            ) {
                ThinkingSubSettingItem(
                    title = stringResource(R.string.thinking_mode),
                    icon =
                        if (enableThinkingMode) Icons.Rounded.Psychology
                        else Icons.Outlined.Psychology,
                    iconTint =
                        if (enableThinkingMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    isChecked = enableThinkingMode,
                    onToggle = onToggleThinkingMode,
                    onInfoClick = onThinkingModeInfoClick
                )

                if (enableThinkingMode) {
                    Box(modifier = Modifier.padding(start = 28.dp)) {
                        SettingSliderItem(
                            label = stringResource(R.string.thinking_quality),
                            icon = Icons.Outlined.Speed,
                            value = thinkingQualityLevel.toFloat(),
                            onValueChange = { newValue ->
                                val intValue = newValue.toInt().coerceIn(1, 4)
                                onThinkingQualityLevelChange(intValue)
                            },
                            onInfoClick = onThinkingQualityInfoClick,
                            valueRange = 1f..4f,
                            steps = 2,
                            decimalFormatPattern = "0"
                        )
                    }
                }

                ThinkingSubSettingItem(
                    title = stringResource(R.string.thinking_guidance),
                    icon =
                        if (enableThinkingGuidance) Icons.Rounded.TipsAndUpdates
                        else Icons.Outlined.TipsAndUpdates,
                    iconTint =
                        if (enableThinkingGuidance) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    isChecked = enableThinkingGuidance,
                    onToggle = onToggleThinkingGuidance,
                    onInfoClick = onThinkingGuidanceInfoClick
                )
            }
        }
    }
}

@Composable
private fun MemorySelectorItem(
    preferenceProfiles: List<PreferenceProfile>,
    currentProfileId: String,
    onSelectMemory: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    onManageClick: () -> Unit
) {
    val currentProfile = preferenceProfiles.find { it.id == currentProfileId }

    val currentProfileName = currentProfile?.name ?: stringResource(R.string.not_selected)
    val expandStateDesc = if (expanded) stringResource(R.string.expanded) else stringResource(R.string.collapsed)
    val accessibilityDesc = "${stringResource(R.string.memory)}: $currentProfileName, $expandStateDesc"
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                .heightIn(min = 36.dp)
                .semantics {
                    contentDescription = accessibilityDesc
                }
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Portrait,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(16.dp)
                    .clearAndSetSemantics {}
            )
            // 详情按钮（左侧）
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Row(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.memory) + ":",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clearAndSetSemantics {}
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentProfile?.name ?: stringResource(R.string.not_selected),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false).clearAndSetSemantics {}
                )
            }
            Icon(
                    imageVector =
                            if (expanded) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        if (expanded) {
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                preferenceProfiles.forEach { profile ->
                    val isSelected = profile.id == currentProfileId
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                    if (isSelected)
                                                            MaterialTheme.colorScheme.primary.copy(
                                                                    alpha = 0.1f
                                                            )
                                                    else Color.Transparent
                                            )
                            .clickable {
                                onSelectMemory(profile.id)
                                onExpandedChange(false)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = profile.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color =
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (preferenceProfiles.last() != profile) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clickable(onClick = onManageClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.manage_config), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ModelSelectorItem(
    configSummaries: List<ModelConfigSummary>,
    currentConfigMapping: FunctionConfigMapping,
    onSelectModel: (String, Int) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onManageClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val context = LocalContext.current
    val showAutoGlmError: () -> Unit = {
        Toast.makeText(
            context,
            context.getString(R.string.chat_autoglm_warning),
            Toast.LENGTH_LONG
        ).show()
    }

    val currentConfig = configSummaries.find { it.id == currentConfigMapping.configId }
    var expandedConfigId by remember { mutableStateOf<String?>(null) } // 用于记录当前展开的配置的模型列表

    val currentModelName = currentConfig?.let { config ->
        val validIndex = getValidModelIndex(config.modelName, currentConfigMapping.modelIndex)
        getModelByIndex(config.modelName, validIndex)
    } ?: stringResource(R.string.not_selected)
    val effectiveExpanded = expanded
    val expandStateDesc =
            if (effectiveExpanded) stringResource(R.string.expanded) else stringResource(R.string.collapsed)
    val accessibilityDesc = "${stringResource(R.string.model)}: $currentModelName, $expandStateDesc"
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                .heightIn(min = 36.dp)
                .semantics {
                    contentDescription = accessibilityDesc
                }
                .clickable {
                    onExpandedChange(!expanded)
                }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.DataObject,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(16.dp)
                    .clearAndSetSemantics {}
            )
            // 详情按钮（左侧）
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.model) + ":",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clearAndSetSemantics {}
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 只显示选中的模型名称
                currentConfig?.let { config ->
                    val validIndex = getValidModelIndex(config.modelName, currentConfigMapping.modelIndex)
                    val selectedModel = getModelByIndex(config.modelName, validIndex)
                    Text(
                        text = selectedModel.ifEmpty { stringResource(R.string.not_selected) },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clearAndSetSemantics {}
                    )
                } ?: Text(
                    text = stringResource(R.string.not_selected),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false).clearAndSetSemantics {}
                )
            }
            Icon(
                imageVector = if (effectiveExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        if (effectiveExpanded) {
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                configSummaries.forEach { config ->
                    val isSelected = config.id == currentConfigMapping.configId
                    val modelList = getModelList(config.modelName)
                    val hasMultipleModels = modelList.size > 1
                    val isExpanded = expandedConfigId == config.id
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                        if (isSelected)
                                                                MaterialTheme.colorScheme.primary.copy(
                                                                        alpha = 0.1f
                                                                )
                                                        else Color.Transparent
                                                )
                                .clickable {
                                    if (hasMultipleModels) {
                                        // 如果有多个模型，切换展开状态
                                        expandedConfigId = if (isExpanded) null else config.id
                                    } else {
                                        // 如果只有一个模型，直接选择
                                        val singleModelName = modelList.firstOrNull().orEmpty()
                                        if (singleModelName.contains("autoglm", ignoreCase = true)) {
                                            showAutoGlmError()
                                        } else {
                                            onSelectModel(config.id, 0)
                                            onExpandedChange(false)
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = config.name,
                                        fontWeight =
                                                if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color =
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                if (hasMultipleModels) {
                                    Text(
                                        text = stringResource(R.string.chat_model_count, modelList.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = config.modelName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        
                        // 如果有多个模型且已展开，显示模型列表
                        if (hasMultipleModels && isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
                            ) {
                                // 计算有效索引一次，避免重复计算
                                val validIndex = getValidModelIndex(config.modelName, currentConfigMapping.modelIndex)
                                modelList.forEachIndexed { index, modelName ->
                                    val isModelSelected = isSelected && validIndex == index
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (isModelSelected)
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                if (modelName.contains("autoglm", ignoreCase = true)) {
                                                    showAutoGlmError()
                                                } else {
                                                    onSelectModel(config.id, index)
                                                    onExpandedChange(false)
                                                    expandedConfigId = null
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = modelName,
                                            fontSize = 12.sp,
                                            fontWeight = if (isModelSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isModelSelected) 
                                                MaterialTheme.colorScheme.primary 
                                            else 
                                                MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (index < modelList.size - 1) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (configSummaries.last() != config) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clickable(onClick = onManageClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.manage_config), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ActionSettingItem(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Row(
        modifier =
                Modifier.fillMaxWidth()
                        .heightIn(min = 36.dp)
                        .padding(vertical = 2.dp)
                        .padding(horizontal = 3.dp)
                        .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .semantics {
                            contentDescription = title
                        }
                        .clickable(
                            onClick = onClick,
                            role = Role.Button
                        )
                        .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Icon(
            imageVector = icon, 
            contentDescription = null, 
            tint = iconTint, 
            modifier = Modifier
                .size(16.dp)
                .clearAndSetSemantics {}
        )
        // 详情按钮（左侧）
        IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
        // 文本
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .clearAndSetSemantics {}
        )
    }
}

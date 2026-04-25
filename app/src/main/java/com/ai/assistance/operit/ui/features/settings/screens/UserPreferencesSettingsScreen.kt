package com.ai.assistance.operit.ui.features.settings.screens

import android.app.DatePickerDialog as AndroidDatePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.ai.assistance.operit.data.preferences.preferencesManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun UserPreferencesSettingsScreen(
        onNavigateBack: () -> Unit,
        onNavigateToGuide: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 获取所有配置文件
    val profileList by preferencesManager.profileListFlow.collectAsState(initial = emptyList())
    val activeProfileId by
            preferencesManager.activeProfileIdFlow.collectAsState(initial = "default")

    // 下拉菜单状态
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // 获取所有配置文件的名称映射(id -> name)
    val profileNameMap = remember { mutableStateMapOf<String, String>() }

    // 获取字符串资源
    val defaultProfileName = stringResource(R.string.default_profile)

    // 确保默认配置文件存在并在列表中显示
    LaunchedEffect(Unit) {
        // 检查配置列表是否为空，或者不包含默认配置
        if (profileList.isEmpty() || !profileList.contains("default")) {
            // 创建默认配置
            val defaultProfileId = preferencesManager.createProfile(defaultProfileName, isDefault = true)
            preferencesManager.setActiveProfile(defaultProfileId)
        }
    }

    // 加载所有配置文件名称
    LaunchedEffect(profileList) {
        profileList.forEach { profileId ->
            val profile = preferencesManager.getUserPreferencesFlow(profileId).first()
            profileNameMap[profileId] = profile.name
        }
    }

    // 分类锁定状态
    val categoryLockStatus by
            preferencesManager.categoryLockStatusFlow.collectAsState(initial = emptyMap())

    // 对话框状态
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    // 新增：删除确认弹窗状态
    var showDeleteProfileDialog by remember { mutableStateOf(false) }
    // 新增：重命名弹窗状态
    var showRenameProfileDialog by remember { mutableStateOf(false) }
    var editingProfileName by remember { mutableStateOf("") }

    // 选中的配置文件
    var selectedProfileId by remember { mutableStateOf(activeProfileId) }
    var selectedProfile by remember { mutableStateOf<PreferenceProfile?>(null) }

    // 编辑状态
    var editMode by remember { mutableStateOf(false) }
    var editBirthDate by remember { mutableStateOf(0L) }
    var editGender by remember { mutableStateOf("") }
    var editPersonality by remember { mutableStateOf("") }
    var editIdentity by remember { mutableStateOf("") }
    var editOccupation by remember { mutableStateOf("") }
    var editAiStyle by remember { mutableStateOf("") }

    // 日期选择器状态
    val dateFormatter = SimpleDateFormat(stringResource(R.string.date_format_chinese), Locale.getDefault())

    // 动画状态
    val listState = rememberLazyListState()

    // 加载选中的配置文件
    LaunchedEffect(selectedProfileId) {
        preferencesManager.getUserPreferencesFlow(selectedProfileId).collect { profile ->
            selectedProfile = profile
            // 初始化编辑字段
            editBirthDate = profile.birthDate
            editGender = profile.gender
            editPersonality = profile.personality
            editIdentity = profile.identity
            editOccupation = profile.occupation
            editAiStyle = profile.aiStyle
        }
    }

    // 保存用户偏好配置函数
    fun saveUserPreferences() {
        scope.launch {
            selectedProfile?.let { profile ->
                preferencesManager.updateProfileCategory(
                    profileId = profile.id,
                    birthDate = editBirthDate,
                    gender = editGender.takeIf { it.isNotBlank() },
                    personality = editPersonality.takeIf { it.isNotBlank() },
                    identity = editIdentity.takeIf { it.isNotBlank() },
                    occupation = editOccupation.takeIf { it.isNotBlank() },
                    aiStyle = editAiStyle.takeIf { it.isNotBlank() }
                )
                editMode = false
            }
        }
    }

    // 日期选择器函数
    val showDatePickerDialog = {
        val calendar =
                Calendar.getInstance().apply {
                    if (editBirthDate > 0) {
                        timeInMillis = editBirthDate
                    } else {
                        set(Calendar.YEAR, 1990)
                        set(Calendar.MONTH, Calendar.JANUARY)
                        set(Calendar.DAY_OF_MONTH, 1)
                    }
                }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        AndroidDatePickerDialog(
                        context,
                        { _, selectedYear, selectedMonth, selectedDay ->
                            val selectedCalendar =
                                    Calendar.getInstance().apply {
                                        set(Calendar.YEAR, selectedYear)
                                        set(Calendar.MONTH, selectedMonth)
                                        set(Calendar.DAY_OF_MONTH, selectedDay)
                                    }
                            editBirthDate = selectedCalendar.timeInMillis
                        },
                        year,
                        month,
                        day
                )
                .show()
    }

    CustomScaffold(
            floatingActionButton = {
                FloatingActionButton(
                        onClick = { 
                            if (editMode) {
                                saveUserPreferences()
                            } else {
                                editMode = true
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                            if (editMode) Icons.Default.Save else Icons.Default.Edit,
                            contentDescription = if (editMode) stringResource(R.string.save_action) else stringResource(R.string.edit_profile)
                    )
                }
            }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
                // 配置文件选择区域
                Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                ),
                        border =
                                BorderStroke(
                                        0.7.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        // 水平分隔线 - 减小垂直间距
                        HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // 配置选择器区 - 标签和新建按钮放在一行
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 配置文件选择标签
                            Text(
                                    stringResource(R.string.select_preference_profile),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                            )

                            // 新建按钮 - 更小的尺寸
                            OutlinedButton(
                                    onClick = { showAddProfileDialog = true },
                                    shape = RoundedCornerShape(16.dp),
                                    border =
                                            BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary),
                                    contentPadding =
                                            PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp),
                                    colors =
                                            ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.primary
                                            )
                            ) {
                                Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                        stringResource(R.string.new_action),
                                        fontSize = 12.sp,
                                        style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        val selectedProfileName = profileNameMap[selectedProfileId] ?: stringResource(R.string.default_profile)
                        val isActive = selectedProfileId == activeProfileId

                        Surface(
                                modifier =
                                        Modifier.fillMaxWidth().clickable {
                                            isDropdownExpanded = true
                                        },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                tonalElevation = 0.5.dp,
                        ) {
                            Row(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 活跃状态指示
                                    if (isActive) {
                                        Box(
                                                modifier =
                                                        Modifier.size(8.dp)
                                                                .background(
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                        CircleShape
                                                                )
                                        )
                                    }

                                    Text(
                                            text = selectedProfileName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight =
                                                    if (isActive) FontWeight.Medium
                                                    else FontWeight.Normal,
                                            color =
                                                    if (isActive) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                AnimatedContent(
                                        targetState = isDropdownExpanded,
                                        transitionSpec = {
                                            fadeIn() + scaleIn() with fadeOut() + scaleOut()
                                        }
                                ) { expanded ->
                                    Icon(
                                            if (expanded) Icons.Default.KeyboardArrowUp
                                            else Icons.Default.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.select_config),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 操作按钮
                        Row(
                                modifier = Modifier.padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // 激活按钮
                            if (!isActive) {
                                TextButton(
                                        onClick = {
                                            scope.launch {
                                                preferencesManager.setActiveProfile(
                                                        selectedProfileId
                                                )
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.set_active), fontSize = 14.sp)
                                }
                            }

                            // 重命名按钮 - 只有非默认配置才显示
                            if (selectedProfileId != "default") {
                                TextButton(
                                        onClick = {
                                            editingProfileName = selectedProfileName
                                            showRenameProfileDialog = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                            Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.rename_action), fontSize = 14.sp)
                                }
                            }

                            // 删除按钮
                            if (selectedProfileId != "default") {
                                TextButton(
                                        onClick = {
                                            showDeleteProfileDialog = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        colors =
                                                ButtonDefaults.textButtonColors(
                                                        contentColor =
                                                                MaterialTheme.colorScheme.error
                                                ),
                                        modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.delete_action), fontSize = 14.sp)
                                }
                            }
                        }

                        // 下拉菜单
                        DropdownMenu(
                                expanded = isDropdownExpanded,
                                onDismissRequest = { isDropdownExpanded = false },
                                modifier = Modifier.width(280.dp),
                                properties = PopupProperties(focusable = true)
                        ) {
                            profileList.forEach { profileId ->
                                val profileName = profileNameMap[profileId] ?: stringResource(R.string.unnamed_profile)
                                val isCurrentActive = profileId == activeProfileId
                                val isSelected = profileId == selectedProfileId

                                DropdownMenuItem(
                                        text = {
                                            Text(
                                                    text = profileName,
                                                    fontWeight =
                                                            if (isSelected) FontWeight.SemiBold
                                                            else FontWeight.Normal,
                                                    color =
                                                            when {
                                                                isSelected ->
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                isCurrentActive ->
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.8f
                                                                        )
                                                                else ->
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface
                                                            }
                                            )
                                        },
                                        leadingIcon =
                                                if (isCurrentActive) {
                                                    {
                                                        Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                } else null,
                                        trailingIcon =
                                                if (isSelected) {
                                                    {
                                                        Box(
                                                                modifier =
                                                                        Modifier.size(8.dp)
                                                                                .background(
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary,
                                                                                        CircleShape
                                                                                )
                                                        )
                                                    }
                                                } else null,
                                        onClick = {
                                            selectedProfileId = profileId
                                            isDropdownExpanded = false
                                            editMode = false
                                        },
                                        colors =
                                                MenuDefaults.itemColors(
                                                        textColor =
                                                                if (isSelected)
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface
                                                ),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                if (profileId != profileList.last()) {
                                    HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 配置文件详情
                AnimatedVisibility(
                        visible = selectedProfile != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                ) {
                    selectedProfile?.let { profile ->
                        Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                // 标题和引导按钮
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                                Icons.Default.Settings,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                                text = "${profile.name}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // 添加引导配置按钮
                                    if (!editMode) {
                                        OutlinedButton(
                                                onClick = {
                                                    onNavigateToGuide(profile.name, profile.id)
                                                },
                                                shape = RoundedCornerShape(16.dp),
                                                border =
                                                        BorderStroke(
                                                                1.dp,
                                                                MaterialTheme.colorScheme.primary
                                                        ),
                                                contentPadding =
                                                        PaddingValues(
                                                                horizontal = 10.dp,
                                                                vertical = 6.dp
                                                        ),
                                                modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(
                                                    Icons.Default.Assistant,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.config_wizard), fontSize = 14.sp)
                                        }
                                    }
                                }

                                // 偏好分类项
                                LazyColumn(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // 出生日期
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = stringResource(R.string.birth_date),
                                                value =
                                                        if (profile.birthDate > 0)
                                                                dateFormatter.format(
                                                                        Date(profile.birthDate)
                                                                )
                                                        else stringResource(R.string.not_set),
                                                editMode = editMode,
                                                isLocked = categoryLockStatus["birthDate"] ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "birthDate",
                                                                locked
                                                        )
                                                    }
                                                },
                                                icon = Icons.Default.Cake,
                                                onDatePickerClick = {
                                                    if (editMode &&
                                                                    !(categoryLockStatus[
                                                                            "birthDate"]
                                                                            ?: false)
                                                    ) {
                                                        showDatePickerDialog()
                                                    }
                                                },
                                                dateValue = editBirthDate
                                        )
                                    }

                                    // 性别
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = stringResource(R.string.gender),
                                                value = profile.gender.ifEmpty { stringResource(R.string.not_set) },
                                                editValue = editGender,
                                                onValueChange = { editGender = it },
                                                isLocked = categoryLockStatus["gender"] ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "gender",
                                                                locked
                                                        )
                                                    }
                                                },
                                                editMode = editMode,
                                                icon = Icons.Default.Face
                                        )
                                    }

                                    // 性格特点
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = stringResource(R.string.personality_traits),
                                                value = profile.personality.ifEmpty { stringResource(R.string.not_set) },
                                                editValue = editPersonality,
                                                onValueChange = { editPersonality = it },
                                                isLocked = categoryLockStatus["personality"]
                                                                ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "personality",
                                                                locked
                                                        )
                                                    }
                                                },
                                                editMode = editMode,
                                                icon = Icons.Default.Psychology
                                        )
                                    }

                                    // 身份认同
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = stringResource(R.string.identity),
                                                value = profile.identity.ifEmpty { stringResource(R.string.not_set) },
                                                editValue = editIdentity,
                                                onValueChange = { editIdentity = it },
                                                isLocked = categoryLockStatus["identity"] ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "identity",
                                                                locked
                                                        )
                                                    }
                                                },
                                                editMode = editMode,
                                                icon = Icons.Default.Badge
                                        )
                                    }

                                    // 职业
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = stringResource(R.string.occupation),
                                                value = profile.occupation.ifEmpty { stringResource(R.string.not_set) },
                                                editValue = editOccupation,
                                                onValueChange = { editOccupation = it },
                                                isLocked = categoryLockStatus["occupation"]
                                                                ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "occupation",
                                                                locked
                                                        )
                                                    }
                                                },
                                                editMode = editMode,
                                                icon = Icons.Default.Work
                                        )
                                    }

                                    // AI风格偏好
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = stringResource(R.string.ai_style),
                                                value = profile.aiStyle.ifEmpty { stringResource(R.string.not_set) },
                                                editValue = editAiStyle,
                                                onValueChange = { editAiStyle = it },
                                                isLocked = categoryLockStatus["aiStyle"] ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "aiStyle",
                                                                locked
                                                        )
                                                    }
                                                },
                                                editMode = editMode,
                                                icon = Icons.Default.SmartToy
                                        )
                                    }

                                    // 保存按钮（编辑模式时显示）
                                    if (editMode) {
                                        item {
                                            Button(
                                                    onClick = {
                                                        saveUserPreferences()
                                                    },
                                                    modifier =
                                                            Modifier.fillMaxWidth()
                                                                    .padding(top = 8.dp),
                                                    contentPadding = PaddingValues(vertical = 8.dp),
                                                    shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                        stringResource(R.string.save_changes),
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 新建配置文件对话框
        if (showAddProfileDialog) {
            AlertDialog(
                    onDismissRequest = {
                        showAddProfileDialog = false
                        newProfileName = ""
                    },
                    title = {
                        Text(
                                stringResource(R.string.new_preference_profile),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                    stringResource(R.string.create_new_profile_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                    value = newProfileName,
                                    onValueChange = { newProfileName = it },
                                    label = { Text(stringResource(R.string.profile_name), fontSize = 12.sp) },
                                    placeholder = { Text(stringResource(R.string.profile_name_placeholder), fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors =
                                            OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor =
                                                            MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor =
                                                            MaterialTheme.colorScheme.outlineVariant
                                            ),
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                                onClick = {
                                    if (newProfileName.isNotBlank()) {
                                        scope.launch {
                                            val newProfileId =
                                                    preferencesManager.createProfile(newProfileName)
                                            selectedProfileId = newProfileId
                                            showAddProfileDialog = false

                                            // 导航到引导页，传递配置ID和名称
                                            onNavigateToGuide(newProfileName, newProfileId)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                        ) { Text(stringResource(R.string.create_and_configure), fontSize = 13.sp) }
                    },
                    dismissButton = {
                        TextButton(
                                onClick = {
                                    showAddProfileDialog = false
                                    newProfileName = ""
                                }
                        ) { Text(stringResource(R.string.cancel_action), fontSize = 13.sp) }
                    },
                    shape = RoundedCornerShape(12.dp)
            )
        }
        // 新增：重命名配置文件弹窗
        if (showRenameProfileDialog) {
            AlertDialog(
                onDismissRequest = {
                    showRenameProfileDialog = false
                    editingProfileName = ""
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp).size(24.dp)
                        )
                        Text(
                            stringResource(R.string.rename_profile),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.enter_new_profile_name),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editingProfileName,
                            onValueChange = { editingProfileName = it },
                            label = { Text(stringResource(R.string.profile_name), fontSize = 12.sp) },
                            placeholder = { Text(stringResource(R.string.profile_name_placeholder), fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editingProfileName.isNotBlank()) {
                                scope.launch {
                                    selectedProfile?.let { profile ->
                                        val updatedProfile = profile.copy(name = editingProfileName)
                                        preferencesManager.updateProfile(updatedProfile)
                                        profileNameMap[selectedProfileId] = editingProfileName
                                    }
                                    showRenameProfileDialog = false
                                    editingProfileName = ""
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        enabled = editingProfileName.isNotBlank()
                    ) { 
                        Text(stringResource(R.string.confirm_rename), fontSize = 13.sp) 
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showRenameProfileDialog = false
                            editingProfileName = ""
                        }
                    ) { 
                        Text(stringResource(R.string.cancel_action), fontSize = 13.sp) 
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )
        }
        // 新增：删除配置文件确认弹窗
        if (showDeleteProfileDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteProfileDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp).size(24.dp)
                        )
                        Text(stringResource(R.string.confirm_delete_profile))
                    }
                },
                text = {
                    Text(stringResource(R.string.delete_profile_warning))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteProfileDialog = false
                            scope.launch {
                                preferencesManager.deleteProfile(selectedProfileId)
                                selectedProfileId = activeProfileId
                            }
                        }
                    ) { Text(stringResource(R.string.confirm_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteProfileDialog = false }) { Text(stringResource(R.string.cancel_action)) }
                },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
fun ProfileItem(
        profileName: String,
        isActive: Boolean,
        isSelected: Boolean,
        onSelect: () -> Unit,
        onActivate: () -> Unit,
        onDelete: (() -> Unit)? = null
) {
    Surface(
            modifier = Modifier.fillMaxWidth().height(50.dp).clickable(onClick = onSelect),
            shape = RoundedCornerShape(8.dp),
            color =
                    when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        isActive -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.surface
                    },
            border =
                    BorderStroke(
                            width = if (isSelected) 1.dp else 0.dp,
                            color =
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                    )
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                    selected = isSelected,
                    onClick = onSelect,
                    colors =
                            RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.outline
                            ),
                    modifier = Modifier.size(36.dp)
            )

            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                        text = profileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 16.sp,
                        color =
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )

                if (isActive) {
                    Text(
                            text = stringResource(R.string.currently_active),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isActive) {
                    OutlinedButton(
                            onClick = onActivate,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.height(28.dp)
                    ) { Text(stringResource(R.string.activate_action), style = MaterialTheme.typography.labelMedium, fontSize = 13.sp) }
                }

                if (onDelete != null) {
                    IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp).clip(CircleShape)
                    ) {
                        Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_action),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModernPreferenceCategoryItem(
        title: String,
        value: String,
        editValue: String = "",
        onValueChange: (String) -> Unit = {},
        isLocked: Boolean,
        onLockChange: (Boolean) -> Unit,
        editMode: Boolean,
        isNumeric: Boolean = false,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        placeholder: String = stringResource(R.string.input_field_placeholder, title),
        dateValue: Long = 0L,
        onDatePickerClick: () -> Unit = {}
) {
    val animatedElevation by
            animateDpAsState(
                    targetValue = if (editMode && !isLocked) 2.dp else 0.dp,
                    label = "elevation"
            )

    Surface(
            modifier =
                    Modifier.fillMaxWidth()
                            .shadow(
                                    elevation = animatedElevation,
                                    shape = RoundedCornerShape(8.dp)
                            ),
            shape = RoundedCornerShape(8.dp),
            color =
                    if (isLocked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.surface,
            border =
                    BorderStroke(
                            width = if (editMode && !isLocked) 1.dp else 0.dp,
                            color =
                                    if (editMode && !isLocked) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                    )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            fontSize = 16.sp
                    )
                }

                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                            if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (isLocked) stringResource(R.string.locked) else stringResource(R.string.unlocked),
                            tint =
                                    if (isLocked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                    )

                    Switch(
                            checked = isLocked,
                            onCheckedChange = onLockChange,
                            modifier = Modifier.scale(0.8f),
                            colors =
                                    SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                                            uncheckedThumbColor =
                                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                            uncheckedTrackColor =
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                            uncheckedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            AnimatedContent(
                    targetState = editMode,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.95f)).togetherWith(fadeOut())
                    },
                    label = "edit mode transition"
            ) { isEditMode ->
                if (isEditMode) {
                    if (title == stringResource(R.string.birth_date)) {
                        // 出生日期使用点击卡片打开日期选择器
                        Card(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(50.dp)
                                                .clickable(
                                                        enabled = !isLocked,
                                                        onClick = onDatePickerClick
                                                ),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface,
                                                disabledContainerColor =
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                                .copy(alpha = 0.8f)
                                        )
                        ) {
                            Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                        text =
                                                if (dateValue > 0)
                                                        SimpleDateFormat(
                                                                        stringResource(R.string.date_format_chinese),
                                                                        Locale.getDefault()
                                                                )
                                                                .format(Date(dateValue))
                                                else stringResource(R.string.select_birth_date),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color =
                                                if (isLocked)
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.6f
                                                        )
                                                else MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                        Icons.Default.CalendarMonth,
                                        contentDescription = stringResource(R.string.select_date),
                                        tint =
                                                if (isLocked)
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.6f
                                                        )
                                                else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                                value = editValue,
                                onValueChange = {
                                    if (isNumeric) {
                                        if (it.all { char -> char.isDigit() } || it.isEmpty()) {
                                            onValueChange(it)
                                        }
                                    } else {
                                        onValueChange(it)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                enabled = !isLocked,
                                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                                shape = RoundedCornerShape(6.dp),
                                colors =
                                        OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor =
                                                        MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor =
                                                        MaterialTheme.colorScheme.outlineVariant,
                                                disabledBorderColor =
                                                        MaterialTheme.colorScheme.outlineVariant
                                                                .copy(alpha = 0.5f),
                                                disabledTextColor =
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.6f
                                                        )
                                        ),
                                placeholder = {
                                    Text(
                                            placeholder,
                                            color =
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.6f
                                                    ),
                                            fontSize = 16.sp
                                    )
                                }
                        )
                    }
                } else {
                    val displayText =
                            if (value == stringResource(R.string.not_set)) {
                                stringResource(R.string.not_set_field, title)
                            } else {
                                value
                            }

                    Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                            color =
                                    if (value == stringResource(R.string.not_set))
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

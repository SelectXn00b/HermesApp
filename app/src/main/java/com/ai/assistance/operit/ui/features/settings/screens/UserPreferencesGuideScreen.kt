package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import android.content.Context

/** 根据用户选择的关键信息生成偏好描述 */
private fun generatePreferencesDescription(
        gender: String,
        occupation: String,
        birthDate: Long,
        context: Context
): String {
    val genderDesc = when (gender) {
        context.getString(R.string.user_male) -> context.getString(R.string.user_male_desc)
        context.getString(R.string.user_female) -> context.getString(R.string.user_female_desc)
        else -> context.getString(R.string.user_generic_desc)
            }

    // 根据出生日期计算年龄
    val age = if (birthDate > 0) {
                val today = Calendar.getInstance()
                val birthCal = Calendar.getInstance().apply { timeInMillis = birthDate }
                var age = today.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
                // 如果今年的生日还没过，年龄减一
                if (today.get(Calendar.MONTH) < birthCal.get(Calendar.MONTH) ||
                                (today.get(Calendar.MONTH) == birthCal.get(Calendar.MONTH) &&
                                        today.get(Calendar.DAY_OF_MONTH) <
                                                birthCal.get(Calendar.DAY_OF_MONTH))
                ) {
                    age--
                }
                age
            } else {
                0
            }

    val ageDesc = when {
        age in 1..12 -> context.getString(R.string.age_child)
        age in 13..17 -> context.getString(R.string.age_teenager)
        age in 18..25 -> context.getString(R.string.age_young_adult)
        age in 26..40 -> context.getString(R.string.age_adult)
        age in 41..60 -> context.getString(R.string.age_middle_aged)
        age > 60 -> context.getString(R.string.age_elderly)
                else -> ""
            }

    val occupationDesc = when (occupation) {
        context.getString(R.string.occupation_student) -> context.getString(R.string.occupation_student_desc)
        context.getString(R.string.occupation_employee) -> context.getString(R.string.occupation_employee_desc)
        context.getString(R.string.occupation_freelancer) -> context.getString(R.string.occupation_freelancer_desc)
        else -> context.getString(R.string.occupation_worker_desc)
            }

    val interestTopics = when (occupation) {
        context.getString(R.string.occupation_student) -> context.getString(R.string.interests_student)
        context.getString(R.string.occupation_employee) -> context.getString(R.string.interests_employee)
        context.getString(R.string.occupation_freelancer) -> context.getString(R.string.interests_freelancer)
        else -> context.getString(R.string.interests_general)
            }

    val preferenceStyle = when (gender) {
        context.getString(R.string.user_male) -> context.getString(R.string.communication_style_male)
        context.getString(R.string.user_female) -> context.getString(R.string.communication_style_female)
        else -> context.getString(R.string.communication_style_general)
            }

    // 组装完整描述
    val description = if (ageDesc.isNotEmpty()) {
        context.getString(R.string.preferences_intro_template, genderDesc, ageDesc, occupationDesc, interestTopics, preferenceStyle)
    } else {
        context.getString(R.string.preferences_intro_template_no_age, genderDesc, occupationDesc, interestTopics, preferenceStyle)
    }

    // 确保不超过100字
    return if (description.length > 100) description.substring(0, 97) + "..." else description
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserPreferencesGuideScreen(
        profileName: String = "",
        profileId: String = "",
        onComplete: () -> Unit,
        navigateToPermissions: () -> Unit = onComplete,
        onBackPressed: () -> Unit = onComplete
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesManager = remember { UserPreferencesManager.getInstance(context) }

    var selectedGender by remember { mutableStateOf("") }
    var selectedOccupation by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf(0L) }
    var selectedPersonality by remember { mutableStateOf(setOf<String>()) }
    var selectedIdentity by remember { mutableStateOf(setOf<String>()) }
    var selectedAiStyleTags by remember { mutableStateOf(setOf<String>()) }

    // 自定义标签相关状态
    var customPersonalityTags by remember { mutableStateOf(setOf<String>()) }
    var customIdentityTags by remember { mutableStateOf(setOf<String>()) }
    var customAiStyleTags by remember { mutableStateOf(setOf<String>()) }

    // 新增标签对话框相关状态
    var showCustomTagDialog by remember { mutableStateOf(false) }
    var newTagText by remember { mutableStateOf("") }
    var currentTagCategory by remember { mutableStateOf("") } // personality, identity, or aiStyle

    // 日期选择器状态
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = SimpleDateFormat(stringResource(R.string.date_format_pattern), Locale.getDefault())

    // 初始化日期选择器状态
    val initialSelectedDateMillis =
            if (birthDate > 0) birthDate
            else {
                // 默认设置为1990年1月1日
                Calendar.getInstance()
                        .apply {
                            set(Calendar.YEAR, 1990)
                            set(Calendar.MONTH, Calendar.JANUARY)
                            set(Calendar.DAY_OF_MONTH, 1)
                        }
                        .timeInMillis
            }
    val datePickerState =
            rememberDatePickerState(
                    initialSelectedDateMillis = initialSelectedDateMillis,
                    initialDisplayMode = DisplayMode.Picker
            )

    // 各种选项数据
    val genderOptions = listOf(
        stringResource(R.string.user_male), 
        stringResource(R.string.user_female), 
        stringResource(R.string.user_other)
    )
    val occupationOptions = listOf(
        stringResource(R.string.occupation_student), 
        stringResource(R.string.occupation_teacher), 
        stringResource(R.string.occupation_doctor), 
        stringResource(R.string.occupation_engineer), 
        stringResource(R.string.occupation_designer), 
        stringResource(R.string.occupation_programmer), 
        stringResource(R.string.occupation_business_owner), 
        stringResource(R.string.occupation_sales), 
        stringResource(R.string.occupation_customer_service), 
        stringResource(R.string.occupation_freelancer), 
        stringResource(R.string.occupation_retired), 
        stringResource(R.string.occupation_other)
    )
    val personalityOptions = listOf(
        stringResource(R.string.personality_extroverted),
        stringResource(R.string.personality_introverted),
        stringResource(R.string.personality_sensitive),
        stringResource(R.string.personality_rational),
        stringResource(R.string.personality_emotional),
        stringResource(R.string.personality_cautious),
        stringResource(R.string.personality_adventurous),
        stringResource(R.string.personality_patient),
        stringResource(R.string.personality_impatient),
        stringResource(R.string.personality_optimistic),
        stringResource(R.string.personality_pessimistic),
        stringResource(R.string.personality_curious),
        stringResource(R.string.personality_conservative),
        stringResource(R.string.personality_innovative),
        stringResource(R.string.personality_meticulous),
        stringResource(R.string.personality_rough)
            )
    val identityOptions = listOf(
        stringResource(R.string.identity_student),
        stringResource(R.string.identity_teacher),
        stringResource(R.string.identity_parent),
        stringResource(R.string.identity_music_lover),
        stringResource(R.string.identity_art_lover),
        stringResource(R.string.identity_gamer),
        stringResource(R.string.identity_athlete),
        stringResource(R.string.identity_tech_enthusiast),
        stringResource(R.string.identity_traveler),
        stringResource(R.string.identity_foodie),
        stringResource(R.string.identity_entrepreneur),
        stringResource(R.string.identity_professional)
            )
    val aiStyleOptions = listOf(
        stringResource(R.string.ai_style_professional), 
        stringResource(R.string.ai_style_humorous), 
        stringResource(R.string.ai_style_direct), 
        stringResource(R.string.ai_style_patient), 
        stringResource(R.string.ai_style_creative), 
        stringResource(R.string.ai_style_technical), 
        stringResource(R.string.ai_style_educational), 
        stringResource(R.string.ai_style_supportive)
    )

    // 从配置文件加载现有数据
    LaunchedEffect(profileId) {
        try {
            // 检查是否存在配置文件列表
            val profiles = preferencesManager.profileListFlow.first()

            // 如果配置列表为空，创建默认配置
            if (profiles.isEmpty()) {
                // 创建默认配置并设置为活动配置
                val defaultProfileId = preferencesManager.createProfile(context.getString(R.string.default_profile), isDefault = true)
                preferencesManager.setActiveProfile(defaultProfileId)
                // 给一点时间让数据存储更新
                delay(100)
            }

            // 如果提供了profileId，加载特定配置；否则加载活动配置
            val profile =
                    if (profileId.isNotEmpty()) {
                        preferencesManager.getUserPreferencesFlow(profileId).first()
                    } else {
                        preferencesManager.getUserPreferencesFlow().first()
                    }

            // 填充表单字段
            selectedGender = profile.gender
            selectedOccupation = profile.occupation
            birthDate = profile.birthDate

            // 解析个性特点
            val personalityTags =
                    profile.personality.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val (standard, custom) = personalityTags.partition { personalityOptions.contains(it) }
            selectedPersonality = standard.toSet()
            customPersonalityTags = custom.toSet()

            // 解析身份认同
            val identityTags =
                    profile.identity.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val (standardId, customId) = identityTags.partition { identityOptions.contains(it) }
            selectedIdentity = standardId.toSet()
            customIdentityTags = customId.toSet()

            // 解析AI风格标签
            val styleTags = profile.aiStyle.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val (standardStyle, customStyle) = styleTags.partition { aiStyleOptions.contains(it) }
            selectedAiStyleTags = standardStyle.toSet()
            customAiStyleTags = customStyle.toSet()

            // 如果没有已选的标签，默认选择一些并保存到配置中
            var needsUpdate = false

            if (selectedAiStyleTags.isEmpty() && customAiStyleTags.isEmpty()) {
                selectedAiStyleTags = setOf(
                    context.getString(R.string.ai_style_professional), 
                    context.getString(R.string.ai_style_direct)
                )
                needsUpdate = true
            }

            if (selectedPersonality.isEmpty() && customPersonalityTags.isEmpty()) {
                selectedPersonality = setOf(
                    context.getString(R.string.personality_rational), 
                    context.getString(R.string.personality_patient)
                )
                needsUpdate = true
            }

            // 如果需要更新默认值，保存到配置
            if (needsUpdate) {
                if (profileId.isNotEmpty()) {
                    // 更新指定的配置文件
                    preferencesManager.updateProfileCategory(
                            profileId = profileId,
                            aiStyle = selectedAiStyleTags.joinToString(", "),
                            personality = selectedPersonality.joinToString(", ")
                    )
                } else {
                    // 更新当前活动的配置文件
                    preferencesManager.updateProfileCategory(
                            aiStyle = selectedAiStyleTags.joinToString(", "),
                            personality = selectedPersonality.joinToString(", ")
                    )
                }
            }
        } catch (e: Exception) {
            // 如果获取配置失败，创建默认配置
            try {
                // 创建默认配置
                val defaultProfileId = preferencesManager.createProfile(context.getString(R.string.default_profile), isDefault = true)

                // 设置默认值
                selectedAiStyleTags = setOf(
                    context.getString(R.string.ai_style_professional), 
                    context.getString(R.string.ai_style_direct)
                )
                selectedPersonality = setOf(
                    context.getString(R.string.personality_rational), 
                    context.getString(R.string.personality_patient)
                )

                // 保存默认值到配置
                if (profileId.isNotEmpty()) {
                    // 如果有指定的profileId，更新指定配置
                    preferencesManager.updateProfileCategory(
                            profileId = profileId,
                            aiStyle = selectedAiStyleTags.joinToString(", "),
                            personality = selectedPersonality.joinToString(", ")
                    )
                } else {
                    // 否则更新默认配置
                    preferencesManager.updateProfileCategory(
                            profileId = defaultProfileId,
                            aiStyle = selectedAiStyleTags.joinToString(", "),
                            personality = selectedPersonality.joinToString(", ")
                    )
                }
            } catch (ex: Exception) {
                // 如果还是失败，至少确保UI有默认值显示
                selectedAiStyleTags = setOf(
                    context.getString(R.string.ai_style_professional), 
                    context.getString(R.string.ai_style_direct)
                )
                selectedPersonality = setOf(
                    context.getString(R.string.personality_rational), 
                    context.getString(R.string.personality_patient)
                )
            }
        }
    }

    // 自定义标签对话框
    if (showCustomTagDialog) {
        AlertDialog(
                onDismissRequest = {
                    showCustomTagDialog = false
                    newTagText = ""
                },
                title = {
                    Text(
                            when (currentTagCategory) {
                                "personality" -> stringResource(R.string.add_custom_personality)
                                "identity" -> stringResource(R.string.add_custom_identity)
                                "aiStyle" -> stringResource(R.string.add_custom_ai_style)
                                else -> stringResource(R.string.add_custom_tag)
                            }
                    )
                },
                text = {
                    OutlinedTextField(
                            value = newTagText,
                            onValueChange = { if (it.length <= 10) newTagText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.enter_tag_name)) },
                            singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                if (newTagText.isNotEmpty()) {
                                    when (currentTagCategory) {
                                        "personality" -> {
                                            customPersonalityTags =
                                                    customPersonalityTags + newTagText
                                            selectedPersonality = selectedPersonality + newTagText
                                        }
                                        "identity" -> {
                                            customIdentityTags = customIdentityTags + newTagText
                                            selectedIdentity = selectedIdentity + newTagText
                                        }
                                        "aiStyle" -> {
                                            customAiStyleTags = customAiStyleTags + newTagText
                                            selectedAiStyleTags = selectedAiStyleTags + newTagText
                                        }
                                    }
                                }
                                newTagText = ""
                                showCustomTagDialog = false
                            },
                            enabled = newTagText.isNotEmpty()
                    ) { Text(stringResource(R.string.add_action)) }
                },
                dismissButton = {
                    TextButton(
                            onClick = {
                                newTagText = ""
                                showCustomTagDialog = false
                            }
                    ) { Text(stringResource(R.string.cancel_action)) }
                }
        )
    }

    // 日期选择器对话框
    if (showDatePicker) {
        DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                            onClick = {
                                datePickerState.selectedDateMillis?.let { birthDate = it }
                                showDatePicker = false
                            }
                    ) { Text(stringResource(R.string.confirm_action)) }
                },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel_action)) } }
        ) {
            DatePicker(
                    state = datePickerState,
                    title = {
                        Text(
                                stringResource(R.string.select_birth_date_title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                        )
                    }
            )
        }
    }

    CustomScaffold() { paddingValues ->
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(paddingValues)
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                        text =
                                if (profileName.isNotEmpty()) stringResource(R.string.profile_config_title, profileName)
                                else stringResource(id = R.string.preferences_guide_title),
                        style = MaterialTheme.typography.titleMedium
                )
                
                // 显示当前编辑的配置ID（调试用）
                if (profileId.isNotEmpty()) {
                    Text(
                            text = stringResource(R.string.editing_profile_id, profileId),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // 添加说明卡片，提示所有选项都是可选的
            Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.info_icon),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.all_options_optional),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // 性别选择（标签选择）
            Text(stringResource(R.string.gender_optional), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 4,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genderOptions.forEach { option ->
                    FilterChip(
                            selected = selectedGender == option,
                            onClick = { selectedGender = option },
                            label = { Text(option) },
                            modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // 职业选择（标签选择）
            Text(
                    stringResource(R.string.occupation_optional),
                    style = MaterialTheme.typography.titleSmall
            )
            FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 4,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                occupationOptions.forEach { option ->
                    FilterChip(
                            selected = selectedOccupation == option,
                            onClick = { selectedOccupation = option },
                            label = { Text(option) },
                            modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // 出生日期选择
            Text(
                    stringResource(R.string.birth_date_optional),
                    style = MaterialTheme.typography.titleSmall
            )
            OutlinedCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    onClick = { showDatePicker = true }
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                            text =
                                    if (birthDate > 0) dateFormatter.format(Date(birthDate))
                                    else stringResource(R.string.select_birth_date),
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                    if (birthDate > 0) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = stringResource(R.string.select_date),
                            tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 性格特点选择（多选标签）
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.personality_optional), style = MaterialTheme.typography.titleSmall)
                TextButton(
                        onClick = {
                            currentTagCategory = "personality"
                            showCustomTagDialog = true
                        }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_icon))
                    Text(stringResource(R.string.add_custom))
                }
            }
            FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 4,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 预定义标签
                personalityOptions.forEach { option ->
                    val isSelected = selectedPersonality.contains(option)
                    FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    selectedPersonality = selectedPersonality - option
                                } else {
                                    selectedPersonality = selectedPersonality + option
                                }
                            },
                            label = { Text(option) },
                            modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // 自定义标签
                customPersonalityTags.forEach { tag ->
                    FilterChip(
                            selected = selectedPersonality.contains(tag),
                            onClick = {
                                if (selectedPersonality.contains(tag)) {
                                    selectedPersonality = selectedPersonality - tag
                                } else {
                                    selectedPersonality = selectedPersonality + tag
                                }
                            },
                            label = { Text(tag) },
                            modifier = Modifier.padding(vertical = 4.dp),
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.delete_icon),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            customPersonalityTags = customPersonalityTags - tag
                                            selectedPersonality = selectedPersonality - tag
                                        }
                                )
                            }
                    )
                }
            }

            // 身份认同选择（多选标签）
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.identity_optional), style = MaterialTheme.typography.titleSmall)
                TextButton(
                        onClick = {
                            currentTagCategory = "identity"
                            showCustomTagDialog = true
                        }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_icon))
                    Text(stringResource(R.string.add_custom))
                }
            }
            FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 4,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 预定义标签
                identityOptions.forEach { option ->
                    val isSelected = selectedIdentity.contains(option)
                    FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    selectedIdentity = selectedIdentity - option
                                } else {
                                    selectedIdentity = selectedIdentity + option
                                }
                            },
                            label = { Text(option) },
                            modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // 自定义标签
                customIdentityTags.forEach { tag ->
                    FilterChip(
                            selected = selectedIdentity.contains(tag),
                            onClick = {
                                if (selectedIdentity.contains(tag)) {
                                    selectedIdentity = selectedIdentity - tag
                                } else {
                                    selectedIdentity = selectedIdentity + tag
                                }
                            },
                            label = { Text(tag) },
                            modifier = Modifier.padding(vertical = 4.dp),
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.delete_icon),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            customIdentityTags = customIdentityTags - tag
                                            selectedIdentity = selectedIdentity - tag
                                        }
                                )
                            }
                    )
                }
            }

            // AI风格标签选择
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.ai_style_optional), style = MaterialTheme.typography.titleSmall)
                TextButton(
                        onClick = {
                            currentTagCategory = "aiStyle"
                            showCustomTagDialog = true
                        }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_icon))
                    Text(stringResource(R.string.add_custom))
                }
            }
            FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 4,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 预定义标签
                aiStyleOptions.forEach { option ->
                    val isSelected = selectedAiStyleTags.contains(option)
                    FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    selectedAiStyleTags = selectedAiStyleTags - option
                                } else {
                                    selectedAiStyleTags = selectedAiStyleTags + option
                                }
                            },
                            label = { Text(option) },
                            modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // 自定义标签
                customAiStyleTags.forEach { tag ->
                    FilterChip(
                            selected = selectedAiStyleTags.contains(tag),
                            onClick = {
                                if (selectedAiStyleTags.contains(tag)) {
                                    selectedAiStyleTags = selectedAiStyleTags - tag
                                } else {
                                    selectedAiStyleTags = selectedAiStyleTags + tag
                                }
                            },
                            label = { Text(tag) },
                            modifier = Modifier.padding(vertical = 4.dp),
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.delete_icon),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            customAiStyleTags = customAiStyleTags - tag
                                            selectedAiStyleTags = selectedAiStyleTags - tag
                                        }
                                )
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 完成按钮
            Button(
                    onClick = {
                        scope.launch {
                            // 将选中的标签合并为字符串（包括自定义标签）
                            val personalityTags = selectedPersonality.joinToString(", ")
                            val identityTags = selectedIdentity.joinToString(", ")
                            val aiStyleTags = selectedAiStyleTags.joinToString(", ")

                            // 更新配置信息
                            if (profileId.isNotEmpty()) {
                                // 如果提供了profileId，更新指定的配置文件
                                preferencesManager.updateProfileCategory(
                                        profileId = profileId,
                                        birthDate = birthDate,
                                        gender = selectedGender,
                                        occupation = selectedOccupation,
                                        personality = personalityTags,
                                        identity = identityTags,
                                        aiStyle = aiStyleTags
                                )
                            } else {
                                // 否则更新当前活动的配置文件
                                preferencesManager.updateProfileCategory(
                                        birthDate = birthDate,
                                        gender = selectedGender,
                                        occupation = selectedOccupation,
                                        personality = personalityTags,
                                        identity = identityTags,
                                        aiStyle = aiStyleTags
                                )
                            }

                            // 根据流程选择不同的导航目标
                            if (profileName.isNotEmpty()) {
                                // 如果是从配置页来的，返回到配置页
                                onComplete()
                            } else {
                                // 如果是首次启动应用，进入权限页
                                navigateToPermissions()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(id = R.string.complete)) }
        }
    }
}

package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.theme.ProvideAiMarkdownTextLayoutSettings
import java.text.DecimalFormat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutAdjustmentSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val userPreferences = remember { UserPreferencesManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    val chatSettingsButtonEndPadding by userPreferences.chatSettingsButtonEndPadding.collectAsState(initial = 2f)
    val chatAreaHorizontalPadding by userPreferences.chatAreaHorizontalPadding.collectAsState(initial = 16f)
    val aiMarkdownLineHeightMultiplier by userPreferences.aiMarkdownLineHeightMultiplier.collectAsState(initial = 1f)
    val aiMarkdownLetterSpacing by userPreferences.aiMarkdownLetterSpacing.collectAsState(initial = 0f)
    val aiMarkdownParagraphSpacing by userPreferences.aiMarkdownParagraphSpacing.collectAsState(initial = 12f)

    val sectionContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    val itemBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    CustomScaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.layout_adjustment_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionTitle(
                text = stringResource(R.string.layout_adjustment_settings_title),
                icon = Icons.Default.Tune
            )

            SettingsSectionCard(containerColor = sectionContainerColor) {
                CompactEditableFloatSettingItem(
                    title = stringResource(R.string.chat_settings_button_end_padding),
                    description = stringResource(R.string.chat_settings_button_end_padding_desc),
                    currentValue = chatSettingsButtonEndPadding,
                    onSave = { newValue ->
                        scope.launch {
                            userPreferences.saveChatSettingsButtonEndPadding(newValue)
                        }
                    },
                    defaultValue = 2f,
                    unitLabel = "dp",
                    backgroundColor = itemBackgroundColor
                )

                CompactEditableFloatSettingItem(
                    title = stringResource(R.string.chat_area_horizontal_padding),
                    description = stringResource(R.string.chat_area_horizontal_padding_desc),
                    currentValue = chatAreaHorizontalPadding,
                    onSave = { newValue ->
                        scope.launch {
                            userPreferences.saveChatAreaHorizontalPadding(newValue)
                        }
                    },
                    defaultValue = 16f,
                    unitLabel = "dp",
                    backgroundColor = itemBackgroundColor
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                CompactEditableFloatSettingItem(
                    title = stringResource(R.string.global_text_line_height_multiplier),
                    description = stringResource(R.string.global_text_line_height_multiplier_desc),
                    currentValue = aiMarkdownLineHeightMultiplier,
                    onSave = { newValue ->
                        scope.launch {
                            userPreferences.saveAiMarkdownLineHeightMultiplier(newValue)
                        }
                    },
                    defaultValue = 1f,
                    unitLabel = "x",
                    valueRange = 0.8f..2.0f,
                    backgroundColor = itemBackgroundColor
                )

                CompactEditableFloatSettingItem(
                    title = stringResource(R.string.global_text_letter_spacing),
                    description = stringResource(R.string.global_text_letter_spacing_desc),
                    currentValue = aiMarkdownLetterSpacing,
                    onSave = { newValue ->
                        scope.launch {
                            userPreferences.saveAiMarkdownLetterSpacing(newValue)
                        }
                    },
                    defaultValue = 0f,
                    unitLabel = "sp",
                    valueRange = -1f..8f,
                    backgroundColor = itemBackgroundColor
                )

                CompactEditableFloatSettingItem(
                    title = stringResource(R.string.global_text_paragraph_spacing),
                    description = stringResource(R.string.global_text_paragraph_spacing_desc),
                    currentValue = aiMarkdownParagraphSpacing,
                    onSave = { newValue ->
                        scope.launch {
                            userPreferences.saveAiMarkdownParagraphSpacing(newValue)
                        }
                    },
                    defaultValue = 12f,
                    unitLabel = "dp",
                    valueRange = 0f..48f,
                    backgroundColor = itemBackgroundColor
                )

            }

            SectionTitle(
                text = stringResource(R.string.layout_adjustment_preview_title),
                icon = Icons.Default.Visibility
            )

            LayoutAdjustmentPreviewCard(
                markdownText = stringResource(R.string.layout_adjustment_preview_markdown),
                containerColor = sectionContainerColor
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsSectionCard(
    containerColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun LayoutAdjustmentPreviewCard(
    markdownText: String,
    containerColor: Color
) {
    val previewText = remember(markdownText) {
        markdownText.replace("\\n", "\n")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.layout_adjustment_preview_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ProvideAiMarkdownTextLayoutSettings {
                MarkdownTextComposable(
                    text = previewText,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CompactEditableFloatSettingItem(
    title: String,
    description: String,
    currentValue: Float,
    onSave: (Float) -> Unit,
    defaultValue: Float,
    unitLabel: String,
    backgroundColor: Color,
    valueRange: ClosedFloatingPointRange<Float> = 0f..50f
) {
    val focusManager = LocalFocusManager.current
    val decimalFormat = remember { DecimalFormat("0.##") }

    var textValue by remember { mutableStateOf(decimalFormat.format(currentValue)) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(currentValue) {
        textValue = decimalFormat.format(currentValue)
        isError = false
    }

    val validateAndSave = {
        val floatValue = textValue.toFloatOrNull()
        if (floatValue != null && floatValue in valueRange) {
            textValue = decimalFormat.format(floatValue)
            onSave(floatValue)
            isError = false
            focusManager.clearFocus()
        } else {
            isError = true
        }
    }

    val resetToDefault = {
        textValue = decimalFormat.format(defaultValue)
        isError = false
        onSave(defaultValue)
        focusManager.clearFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = textValue,
                    onValueChange = { newValue ->
                        textValue = newValue
                        val floatValue = newValue.toFloatOrNull()
                        isError = newValue.isNotBlank() && (floatValue == null || floatValue !in valueRange)
                    },
                    modifier = Modifier
                        .width(64.dp)
                        .background(
                            color = if (isError) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { validateAndSave() }
                    ),
                    singleLine = true
                )

                Text(
                    text = unitLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        if (isError) {
            Text(
                text = stringResource(
                    R.string.invalid_value_range,
                    valueRange.start,
                    valueRange.endInclusive
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = resetToDefault) {
                Text(stringResource(R.string.reset_to_default))
            }

            TextButton(onClick = validateAndSave) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

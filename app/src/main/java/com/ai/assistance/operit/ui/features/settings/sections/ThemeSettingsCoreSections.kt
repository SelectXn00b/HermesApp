package com.ai.assistance.operit.ui.features.settings.sections

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.settings.components.ChatStyleOption
import com.ai.assistance.operit.ui.features.settings.components.ColorSelectionItem
import com.ai.assistance.operit.ui.features.settings.components.ThemeModeOption
import com.ai.assistance.operit.ui.features.chat.components.ChatStyle
import com.ai.assistance.operit.ui.features.chat.components.style.bubble.BubbleImageBackgroundSurface
import com.ai.assistance.operit.ui.features.chat.components.style.bubble.BubbleImageStyleConfig
import com.ai.assistance.operit.ui.theme.applyFontFamilyToTypography
import com.ai.assistance.operit.ui.theme.resolveConfiguredFontFamily
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal typealias SaveThemeSettingsAction = (suspend () -> Unit) -> Unit

@Composable
internal fun ThemeSettingsCharacterBindingInfoCard(
    aiAvatarUri: String?,
    activeCharacterName: String?,
    isGroupTarget: Boolean,
    cardColors: CardColors,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = cardColors,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (aiAvatarUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(aiAvatarUri)),
                        contentDescription = stringResource(R.string.character_avatar),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription =
                            stringResource(R.string.character_card_default_avatar),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        if (isGroupTarget) {
                            stringResource(R.string.current_character_group, activeCharacterName ?: "")
                        } else {
                            stringResource(R.string.current_character, activeCharacterName ?: "")
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text =
                        if (isGroupTarget) {
                            stringResource(R.string.theme_auto_bind_character_group)
                        } else {
                            stringResource(R.string.theme_auto_bind_character_card)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                Icons.Default.Link,
                contentDescription = stringResource(R.string.bind),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun ThemeSettingsThemeModeSection(
    cardColors: CardColors,
    useSystemThemeInput: Boolean,
    onUseSystemThemeInputChange: (Boolean) -> Unit,
    themeModeInput: String,
    onThemeModeInputChange: (String) -> Unit,
    saveThemeSettingsWithCharacterCard: SaveThemeSettingsAction,
    preferencesManager: UserPreferencesManager,
) {
    ThemeSettingsSectionTitle(
        title = stringResource(id = R.string.theme_title_mode),
        icon = Icons.Default.Brightness4,
    )

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = R.string.theme_system_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.theme_follow_system),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(id = R.string.theme_follow_system_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(
                    checked = useSystemThemeInput,
                    onCheckedChange = {
                        onUseSystemThemeInputChange(it)
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(useSystemTheme = it)
                        }
                    },
                )
            }

            if (!useSystemThemeInput) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(id = R.string.theme_select),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeModeOption(
                        title = stringResource(id = R.string.theme_light),
                        selected = themeModeInput == UserPreferencesManager.THEME_MODE_LIGHT,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onThemeModeInputChange(UserPreferencesManager.THEME_MODE_LIGHT)
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(
                                    themeMode = UserPreferencesManager.THEME_MODE_LIGHT,
                                )
                            }
                        },
                    )

                    ThemeModeOption(
                        title = stringResource(id = R.string.theme_dark),
                        selected = themeModeInput == UserPreferencesManager.THEME_MODE_DARK,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onThemeModeInputChange(UserPreferencesManager.THEME_MODE_DARK)
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(
                                    themeMode = UserPreferencesManager.THEME_MODE_DARK,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ThemeSettingsChatStyleSection(
    cardColors: CardColors,
    chatStyleInput: String,
    onChatStyleInputChange: (String) -> Unit,
    inputStyleInput: String,
    onInputStyleInputChange: (String) -> Unit,
    bubbleShowAvatarInput: Boolean,
    onBubbleShowAvatarInputChange: (Boolean) -> Unit,
    bubbleWideLayoutEnabledInput: Boolean,
    onBubbleWideLayoutEnabledInputChange: (Boolean) -> Unit,
    cursorUserBubbleFollowThemeInput: Boolean,
    onCursorUserBubbleFollowThemeInputChange: (Boolean) -> Unit,
    cursorUserBubbleLiquidGlassInput: Boolean,
    onCursorUserBubbleLiquidGlassInputChange: (Boolean) -> Unit,
    cursorUserBubbleWaterGlassInput: Boolean,
    onCursorUserBubbleWaterGlassInputChange: (Boolean) -> Unit,
    bubbleUserBubbleLiquidGlassInput: Boolean,
    onBubbleUserBubbleLiquidGlassInputChange: (Boolean) -> Unit,
    bubbleUserBubbleWaterGlassInput: Boolean,
    onBubbleUserBubbleWaterGlassInputChange: (Boolean) -> Unit,
    bubbleAiBubbleLiquidGlassInput: Boolean,
    onBubbleAiBubbleLiquidGlassInputChange: (Boolean) -> Unit,
    bubbleAiBubbleWaterGlassInput: Boolean,
    onBubbleAiBubbleWaterGlassInputChange: (Boolean) -> Unit,
    cursorUserBubbleColorInput: Int,
    bubbleUserBubbleColorInput: Int,
    bubbleAiBubbleColorInput: Int,
    bubbleUserTextColorInput: Int,
    bubbleAiTextColorInput: Int,
    bubbleUserUseCustomFontInput: Boolean,
    onBubbleUserUseCustomFontInputChange: (Boolean) -> Unit,
    bubbleUserFontTypeInput: String,
    onBubbleUserFontTypeInputChange: (String) -> Unit,
    bubbleUserSystemFontNameInput: String,
    onBubbleUserSystemFontNameInputChange: (String) -> Unit,
    bubbleUserCustomFontPathInput: String?,
    onBubbleUserCustomFontPathInputChange: (String?) -> Unit,
    onPickBubbleUserFont: () -> Unit,
    bubbleAiUseCustomFontInput: Boolean,
    onBubbleAiUseCustomFontInputChange: (Boolean) -> Unit,
    bubbleAiFontTypeInput: String,
    onBubbleAiFontTypeInputChange: (String) -> Unit,
    bubbleAiSystemFontNameInput: String,
    onBubbleAiSystemFontNameInputChange: (String) -> Unit,
    bubbleAiCustomFontPathInput: String?,
    onBubbleAiCustomFontPathInputChange: (String?) -> Unit,
    onPickBubbleAiFont: () -> Unit,
    previewUserAvatarUri: String?,
    previewAiAvatarUri: String?,
    onShowColorPicker: (String) -> Unit,
    bubbleUserUseImageInput: Boolean,
    onBubbleUserUseImageInputChange: (Boolean) -> Unit,
    bubbleAiUseImageInput: Boolean,
    onBubbleAiUseImageInputChange: (Boolean) -> Unit,
    bubbleUserImageUriInput: String?,
    bubbleAiImageUriInput: String?,
    onPickBubbleUserImage: () -> Unit,
    onPickBubbleAiImage: () -> Unit,
    onClearBubbleUserImage: () -> Unit,
    onClearBubbleAiImage: () -> Unit,
    bubbleUserImageCropLeftInput: Float,
    onBubbleUserImageCropLeftInputChange: (Float) -> Unit,
    bubbleUserImageCropTopInput: Float,
    onBubbleUserImageCropTopInputChange: (Float) -> Unit,
    bubbleUserImageCropRightInput: Float,
    onBubbleUserImageCropRightInputChange: (Float) -> Unit,
    bubbleUserImageCropBottomInput: Float,
    onBubbleUserImageCropBottomInputChange: (Float) -> Unit,
    bubbleUserImageRepeatStartInput: Float,
    onBubbleUserImageRepeatStartInputChange: (Float) -> Unit,
    bubbleUserImageRepeatEndInput: Float,
    onBubbleUserImageRepeatEndInputChange: (Float) -> Unit,
    bubbleUserImageRepeatYStartInput: Float,
    onBubbleUserImageRepeatYStartInputChange: (Float) -> Unit,
    bubbleUserImageRepeatYEndInput: Float,
    onBubbleUserImageRepeatYEndInputChange: (Float) -> Unit,
    bubbleUserImageScaleInput: Float,
    onBubbleUserImageScaleInputChange: (Float) -> Unit,
    bubbleAiImageCropLeftInput: Float,
    onBubbleAiImageCropLeftInputChange: (Float) -> Unit,
    bubbleAiImageCropTopInput: Float,
    onBubbleAiImageCropTopInputChange: (Float) -> Unit,
    bubbleAiImageCropRightInput: Float,
    onBubbleAiImageCropRightInputChange: (Float) -> Unit,
    bubbleAiImageCropBottomInput: Float,
    onBubbleAiImageCropBottomInputChange: (Float) -> Unit,
    bubbleAiImageRepeatStartInput: Float,
    onBubbleAiImageRepeatStartInputChange: (Float) -> Unit,
    bubbleAiImageRepeatEndInput: Float,
    onBubbleAiImageRepeatEndInputChange: (Float) -> Unit,
    bubbleAiImageRepeatYStartInput: Float,
    onBubbleAiImageRepeatYStartInputChange: (Float) -> Unit,
    bubbleAiImageRepeatYEndInput: Float,
    onBubbleAiImageRepeatYEndInputChange: (Float) -> Unit,
    bubbleAiImageScaleInput: Float,
    onBubbleAiImageScaleInputChange: (Float) -> Unit,
    bubbleImageRenderModeInput: String,
    onBubbleImageRenderModeInputChange: (String) -> Unit,
    bubbleUserRoundedCornersEnabledInput: Boolean,
    onBubbleUserRoundedCornersEnabledInputChange: (Boolean) -> Unit,
    bubbleAiRoundedCornersEnabledInput: Boolean,
    onBubbleAiRoundedCornersEnabledInputChange: (Boolean) -> Unit,
    bubbleUserContentPaddingLeftInput: Float,
    onBubbleUserContentPaddingLeftInputChange: (Float) -> Unit,
    bubbleUserContentPaddingRightInput: Float,
    onBubbleUserContentPaddingRightInputChange: (Float) -> Unit,
    bubbleAiContentPaddingLeftInput: Float,
    onBubbleAiContentPaddingLeftInputChange: (Float) -> Unit,
    bubbleAiContentPaddingRightInput: Float,
    onBubbleAiContentPaddingRightInputChange: (Float) -> Unit,
    saveThemeSettingsWithCharacterCard: SaveThemeSettingsAction,
    preferencesManager: UserPreferencesManager,
) {
    ThemeSettingsSectionTitle(
        title = stringResource(id = R.string.chat_style_title),
        icon = Icons.Default.ColorLens,
    )

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = R.string.chat_style_desc),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChatStyleOption(
                    title = stringResource(id = R.string.chat_style_cursor),
                    selected = chatStyleInput == UserPreferencesManager.CHAT_STYLE_CURSOR,
                    modifier = Modifier.weight(1f),
                ) {
                    onChatStyleInputChange(UserPreferencesManager.CHAT_STYLE_CURSOR)
                    saveThemeSettingsWithCharacterCard {
                        preferencesManager.saveThemeSettings(
                            chatStyle = UserPreferencesManager.CHAT_STYLE_CURSOR,
                        )
                    }
                }

                ChatStyleOption(
                    title = stringResource(id = R.string.chat_style_bubble),
                    selected = chatStyleInput == UserPreferencesManager.CHAT_STYLE_BUBBLE,
                    modifier = Modifier.weight(1f),
                ) {
                    onChatStyleInputChange(UserPreferencesManager.CHAT_STYLE_BUBBLE)
                    saveThemeSettingsWithCharacterCard {
                        preferencesManager.saveThemeSettings(
                            chatStyle = UserPreferencesManager.CHAT_STYLE_BUBBLE,
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(id = R.string.input_style_title),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = stringResource(id = R.string.input_style_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChatStyleOption(
                    title = stringResource(id = R.string.input_style_classic),
                    selected =
                        inputStyleInput == UserPreferencesManager.INPUT_STYLE_CLASSIC,
                    modifier = Modifier.weight(1f),
                ) {
                    onInputStyleInputChange(UserPreferencesManager.INPUT_STYLE_CLASSIC)
                    saveThemeSettingsWithCharacterCard {
                        preferencesManager.saveThemeSettings(
                            inputStyle = UserPreferencesManager.INPUT_STYLE_CLASSIC,
                        )
                    }
                }

                ChatStyleOption(
                    title = stringResource(id = R.string.input_style_agent),
                    selected = inputStyleInput == UserPreferencesManager.INPUT_STYLE_AGENT,
                    modifier = Modifier.weight(1f),
                ) {
                    onInputStyleInputChange(UserPreferencesManager.INPUT_STYLE_AGENT)
                    saveThemeSettingsWithCharacterCard {
                        preferencesManager.saveThemeSettings(
                            inputStyle = UserPreferencesManager.INPUT_STYLE_AGENT,
                        )
                    }
                }
            }

            if (chatStyleInput == UserPreferencesManager.CHAT_STYLE_CURSOR) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.chat_style_cursor_user_follow_theme),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text =
                                stringResource(id = R.string.chat_style_cursor_user_follow_theme_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = cursorUserBubbleFollowThemeInput,
                        onCheckedChange = {
                            onCursorUserBubbleFollowThemeInputChange(it)
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(
                                    cursorUserBubbleFollowTheme = it,
                                )
                            }
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                stringResource(
                                    id = R.string.chat_style_cursor_user_bubble_liquid_glass
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text =
                                stringResource(
                                    id = R.string.chat_style_cursor_user_bubble_liquid_glass_desc
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = cursorUserBubbleLiquidGlassInput,
                        onCheckedChange = {
                            onCursorUserBubbleLiquidGlassInputChange(it)
                            if (it) {
                                onCursorUserBubbleWaterGlassInputChange(false)
                            }
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(
                                    cursorUserBubbleLiquidGlass = it,
                                    cursorUserBubbleWaterGlass = if (it) false else null,
                                )
                            }
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                stringResource(
                                    id = R.string.chat_style_cursor_user_bubble_water_glass
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text =
                                stringResource(
                                    id = R.string.chat_style_cursor_user_bubble_water_glass_desc
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = cursorUserBubbleWaterGlassInput,
                        onCheckedChange = {
                            onCursorUserBubbleWaterGlassInputChange(it)
                            if (it) {
                                onCursorUserBubbleLiquidGlassInputChange(false)
                            }
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(
                                    cursorUserBubbleWaterGlass = it,
                                    cursorUserBubbleLiquidGlass = if (it) false else null,
                                )
                            }
                        },
                    )
                }

                if (!cursorUserBubbleFollowThemeInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ColorSelectionItem(
                            title = stringResource(id = R.string.chat_style_cursor_user_bubble_color),
                            color = Color(cursorUserBubbleColorInput),
                            modifier = Modifier.weight(1f),
                            onClick = { onShowColorPicker("cursorUserBubble") },
                        )
                    }
                }
            }

            val previewUserImageStyle =
                remember(
                    bubbleUserBubbleLiquidGlassInput,
                    bubbleUserBubbleWaterGlassInput,
                    bubbleUserUseImageInput,
                    bubbleUserImageUriInput,
                    bubbleUserImageCropLeftInput,
                    bubbleUserImageCropTopInput,
                    bubbleUserImageCropRightInput,
                    bubbleUserImageCropBottomInput,
                    bubbleUserImageRepeatStartInput,
                    bubbleUserImageRepeatEndInput,
                    bubbleUserImageRepeatYStartInput,
                    bubbleUserImageRepeatYEndInput,
                    bubbleUserImageScaleInput,
                    bubbleImageRenderModeInput,
                ) {
                    val imageUri = bubbleUserImageUriInput
                    if (
                        !bubbleUserBubbleLiquidGlassInput &&
                            !bubbleUserBubbleWaterGlassInput &&
                            bubbleUserUseImageInput &&
                            !imageUri.isNullOrBlank()
                    ) {
                        BubbleImageStyleConfig(
                            imageUri = imageUri,
                            cropLeftRatio = bubbleUserImageCropLeftInput,
                            cropTopRatio = bubbleUserImageCropTopInput,
                            cropRightRatio = bubbleUserImageCropRightInput,
                            cropBottomRatio = bubbleUserImageCropBottomInput,
                            repeatXStartRatio = bubbleUserImageRepeatStartInput,
                            repeatXEndRatio = bubbleUserImageRepeatEndInput,
                            repeatYStartRatio = bubbleUserImageRepeatYStartInput,
                            repeatYEndRatio = bubbleUserImageRepeatYEndInput,
                            imageScale = bubbleUserImageScaleInput,
                            renderMode = bubbleImageRenderModeInput,
                        )
                    } else {
                        null
                    }
                }
            val previewAiImageStyle =
                remember(
                    bubbleAiBubbleLiquidGlassInput,
                    bubbleAiBubbleWaterGlassInput,
                    bubbleAiUseImageInput,
                    bubbleAiImageUriInput,
                    bubbleAiImageCropLeftInput,
                    bubbleAiImageCropTopInput,
                    bubbleAiImageCropRightInput,
                    bubbleAiImageCropBottomInput,
                    bubbleAiImageRepeatStartInput,
                    bubbleAiImageRepeatEndInput,
                    bubbleAiImageRepeatYStartInput,
                    bubbleAiImageRepeatYEndInput,
                    bubbleAiImageScaleInput,
                    bubbleImageRenderModeInput,
                ) {
                    val imageUri = bubbleAiImageUriInput
                    if (
                        !bubbleAiBubbleLiquidGlassInput &&
                            !bubbleAiBubbleWaterGlassInput &&
                            bubbleAiUseImageInput &&
                            !imageUri.isNullOrBlank()
                    ) {
                        BubbleImageStyleConfig(
                            imageUri = imageUri,
                            cropLeftRatio = bubbleAiImageCropLeftInput,
                            cropTopRatio = bubbleAiImageCropTopInput,
                            cropRightRatio = bubbleAiImageCropRightInput,
                            cropBottomRatio = bubbleAiImageCropBottomInput,
                            repeatXStartRatio = bubbleAiImageRepeatStartInput,
                            repeatXEndRatio = bubbleAiImageRepeatEndInput,
                            repeatYStartRatio = bubbleAiImageRepeatYStartInput,
                            repeatYEndRatio = bubbleAiImageRepeatYEndInput,
                            imageScale = bubbleAiImageScaleInput,
                            renderMode = bubbleImageRenderModeInput,
                        )
                    } else {
                        null
                    }
                }
            val context = LocalContext.current
            val previewUserFontFamily =
                remember(
                    context,
                    bubbleUserUseCustomFontInput,
                    bubbleUserFontTypeInput,
                    bubbleUserSystemFontNameInput,
                    bubbleUserCustomFontPathInput,
                ) {
                    resolveConfiguredFontFamily(
                        context = context,
                        useCustomFont = bubbleUserUseCustomFontInput,
                        fontType = bubbleUserFontTypeInput,
                        systemFontName = bubbleUserSystemFontNameInput,
                        customFontPath = bubbleUserCustomFontPathInput,
                    )
                }
            val previewAiFontFamily =
                remember(
                    context,
                    bubbleAiUseCustomFontInput,
                    bubbleAiFontTypeInput,
                    bubbleAiSystemFontNameInput,
                    bubbleAiCustomFontPathInput,
                ) {
                    resolveConfiguredFontFamily(
                        context = context,
                        useCustomFont = bubbleAiUseCustomFontInput,
                        fontType = bubbleAiFontTypeInput,
                        systemFontName = bubbleAiSystemFontNameInput,
                        customFontPath = bubbleAiCustomFontPathInput,
                    )
                }
            if (chatStyleInput == UserPreferencesManager.CHAT_STYLE_BUBBLE) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.chat_style_bubble_show_avatar),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text =
                                stringResource(id = R.string.chat_style_bubble_show_avatar_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = bubbleShowAvatarInput,
                        onCheckedChange = {
                            onBubbleShowAvatarInputChange(it)
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(bubbleShowAvatar = it)
                            }
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.chat_style_bubble_wide_layout),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(id = R.string.chat_style_bubble_wide_layout_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = bubbleWideLayoutEnabledInput,
                        onCheckedChange = {
                            onBubbleWideLayoutEnabledInputChange(it)
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(
                                    bubbleWideLayoutEnabled = it,
                                )
                            }
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                stringResource(
                                    id = R.string.chat_style_bubble_user_bubble_liquid_glass,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text =
                                stringResource(
                                    id = R.string.chat_style_bubble_user_bubble_liquid_glass_desc,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = bubbleUserBubbleLiquidGlassInput,
                        onCheckedChange = {
                            onBubbleUserBubbleLiquidGlassInputChange(it)
                            if (it) {
                                onBubbleUserBubbleWaterGlassInputChange(false)
                                onBubbleUserUseImageInputChange(false)
                            }
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(
                                    bubbleUserBubbleLiquidGlass = it,
                                    bubbleUserBubbleWaterGlass = if (it) false else null,
                                    bubbleUserUseImage = if (it) false else null,
                                )
                            }
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                stringResource(
                                    id = R.string.chat_style_bubble_user_bubble_water_glass,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text =
                                stringResource(
                                    id = R.string.chat_style_bubble_user_bubble_water_glass_desc,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = bubbleUserBubbleWaterGlassInput,
                        onCheckedChange = {
                            onBubbleUserBubbleWaterGlassInputChange(it)
                            if (it) {
                                onBubbleUserBubbleLiquidGlassInputChange(false)
                                onBubbleUserUseImageInputChange(false)
                            }
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(
                                    bubbleUserBubbleWaterGlass = it,
                                    bubbleUserBubbleLiquidGlass = if (it) false else null,
                                    bubbleUserUseImage = if (it) false else null,
                                )
                            }
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                stringResource(
                                    id = R.string.chat_style_bubble_ai_bubble_liquid_glass,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text =
                                stringResource(
                                    id = R.string.chat_style_bubble_ai_bubble_liquid_glass_desc,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = bubbleAiBubbleLiquidGlassInput,
                        onCheckedChange = {
                            onBubbleAiBubbleLiquidGlassInputChange(it)
                            if (it) {
                                onBubbleAiBubbleWaterGlassInputChange(false)
                                onBubbleAiUseImageInputChange(false)
                            }
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(
                                    bubbleAiBubbleLiquidGlass = it,
                                    bubbleAiBubbleWaterGlass = if (it) false else null,
                                    bubbleAiUseImage = if (it) false else null,
                                )
                            }
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                stringResource(
                                    id = R.string.chat_style_bubble_ai_bubble_water_glass,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text =
                                stringResource(
                                    id = R.string.chat_style_bubble_ai_bubble_water_glass_desc,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = bubbleAiBubbleWaterGlassInput,
                        onCheckedChange = {
                            onBubbleAiBubbleWaterGlassInputChange(it)
                            if (it) {
                                onBubbleAiBubbleLiquidGlassInputChange(false)
                                onBubbleAiUseImageInputChange(false)
                            }
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(
                                    bubbleAiBubbleWaterGlass = it,
                                    bubbleAiBubbleLiquidGlass = if (it) false else null,
                                    bubbleAiUseImage = if (it) false else null,
                                )
                            }
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(id = R.string.chat_style_bubble_image_render_mode_title),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = stringResource(id = R.string.chat_style_bubble_image_render_mode_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChatStyleOption(
                        title =
                            stringResource(
                                id = R.string.chat_style_bubble_image_render_mode_nine_patch,
                            ),
                        selected =
                            bubbleImageRenderModeInput ==
                                UserPreferencesManager.BUBBLE_IMAGE_RENDER_MODE_NINE_PATCH,
                        modifier = Modifier.weight(1f),
                    ) {
                        val mode = UserPreferencesManager.BUBBLE_IMAGE_RENDER_MODE_NINE_PATCH
                        onBubbleImageRenderModeInputChange(mode)
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(
                                bubbleImageRenderMode = mode,
                            )
                        }
                    }
                    ChatStyleOption(
                        title =
                            stringResource(
                                id = R.string.chat_style_bubble_image_render_mode_tiled,
                            ),
                        selected =
                            bubbleImageRenderModeInput ==
                                UserPreferencesManager.BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE,
                        modifier = Modifier.weight(1f),
                    ) {
                        val mode =
                            UserPreferencesManager.BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE
                        onBubbleImageRenderModeInputChange(mode)
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(
                                bubbleImageRenderMode = mode,
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(id = R.string.chat_style_bubble_rounded_corners),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text =
                        stringResource(
                            id = R.string.chat_style_bubble_rounded_corners_desc,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(id = R.string.chat_style_bubble_rounded_corners_user),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Switch(
                            checked = bubbleUserRoundedCornersEnabledInput,
                            onCheckedChange = {
                                onBubbleUserRoundedCornersEnabledInputChange(it)
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(
                                        bubbleUserRoundedCornersEnabled = it,
                                    )
                                }
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(id = R.string.chat_style_bubble_rounded_corners_ai),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Switch(
                            checked = bubbleAiRoundedCornersEnabledInput,
                            onCheckedChange = {
                                onBubbleAiRoundedCornersEnabledInputChange(it)
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(
                                        bubbleAiRoundedCornersEnabled = it,
                                    )
                                }
                            },
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(id = R.string.chat_style_bubble_color_title),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ColorSelectionItem(
                        title = stringResource(id = R.string.chat_style_bubble_user_color),
                        color = Color(bubbleUserBubbleColorInput),
                        modifier = Modifier.weight(1f),
                        onClick = { onShowColorPicker("bubbleUserBubble") },
                    )
                    ColorSelectionItem(
                        title = stringResource(id = R.string.chat_style_bubble_ai_color),
                        color = Color(bubbleAiBubbleColorInput),
                        modifier = Modifier.weight(1f),
                        onClick = { onShowColorPicker("bubbleAiBubble") },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(id = R.string.chat_style_bubble_text_style_title),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ColorSelectionItem(
                        title = stringResource(id = R.string.chat_style_bubble_user_text_color),
                        color = Color(bubbleUserTextColorInput),
                        modifier = Modifier.weight(1f),
                        onClick = { onShowColorPicker("bubbleUserText") },
                    )
                    ColorSelectionItem(
                        title = stringResource(id = R.string.chat_style_bubble_ai_text_color),
                        color = Color(bubbleAiTextColorInput),
                        modifier = Modifier.weight(1f),
                        onClick = { onShowColorPicker("bubbleAiText") },
                    )
                }

                BubbleFontStyleEditor(
                    title = stringResource(id = R.string.chat_style_bubble_user_font_title),
                    useCustomFont = bubbleUserUseCustomFontInput,
                    onUseCustomFontChange = {
                        onBubbleUserUseCustomFontInputChange(it)
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleUserUseCustomFont = it)
                        }
                    },
                    fontType = bubbleUserFontTypeInput,
                    onFontTypeChange = {
                        onBubbleUserFontTypeInputChange(it)
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleUserFontType = it)
                        }
                    },
                    systemFontName = bubbleUserSystemFontNameInput,
                    onSystemFontNameChange = {
                        onBubbleUserSystemFontNameInputChange(it)
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(
                                bubbleUserSystemFontName = it,
                            )
                        }
                    },
                    customFontPath = bubbleUserCustomFontPathInput,
                    onPickFont = onPickBubbleUserFont,
                    onClearFont = {
                        onBubbleUserCustomFontPathInputChange(null)
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleUserCustomFontPath = "")
                        }
                    },
                )

                BubbleFontStyleEditor(
                    title = stringResource(id = R.string.chat_style_bubble_ai_font_title),
                    useCustomFont = bubbleAiUseCustomFontInput,
                    onUseCustomFontChange = {
                        onBubbleAiUseCustomFontInputChange(it)
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleAiUseCustomFont = it)
                        }
                    },
                    fontType = bubbleAiFontTypeInput,
                    onFontTypeChange = {
                        onBubbleAiFontTypeInputChange(it)
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleAiFontType = it)
                        }
                    },
                    systemFontName = bubbleAiSystemFontNameInput,
                    onSystemFontNameChange = {
                        onBubbleAiSystemFontNameInputChange(it)
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(
                                bubbleAiSystemFontName = it,
                            )
                        }
                    },
                    customFontPath = bubbleAiCustomFontPathInput,
                    onPickFont = onPickBubbleAiFont,
                    onClearFont = {
                        onBubbleAiCustomFontPathInputChange(null)
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleAiCustomFontPath = "")
                        }
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                BubbleImageStyleEditor(
                    title = stringResource(id = R.string.chat_style_bubble_user_image_title),
                    enabled =
                        !bubbleUserBubbleLiquidGlassInput &&
                            !bubbleUserBubbleWaterGlassInput &&
                            bubbleUserUseImageInput,
                    switchEnabled =
                        !bubbleUserBubbleLiquidGlassInput && !bubbleUserBubbleWaterGlassInput,
                    description = stringResource(id = R.string.chat_style_bubble_image_desc),
                    onEnabledChange = {
                        onBubbleUserUseImageInputChange(it)
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleUserUseImage = it)
                        }
                    },
                    imageUri = bubbleUserImageUriInput,
                    onPickImage = onPickBubbleUserImage,
                    onClearImage = onClearBubbleUserImage,
                    cropLeft = bubbleUserImageCropLeftInput,
                    onCropLeftChange = {
                        val value = it.coerceIn(0f, 0.45f)
                        onBubbleUserImageCropLeftInputChange(value)
                    },
                    onCropLeftChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleUserImageCropLeft = value)
                        }
                    },
                    cropTop = bubbleUserImageCropTopInput,
                    onCropTopChange = {
                        val value = it.coerceIn(0f, 0.45f)
                        onBubbleUserImageCropTopInputChange(value)
                    },
                    onCropTopChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleUserImageCropTop = value)
                        }
                    },
                    cropRight = bubbleUserImageCropRightInput,
                    onCropRightChange = {
                        val value = it.coerceIn(0f, 0.45f)
                        onBubbleUserImageCropRightInputChange(value)
                    },
                    onCropRightChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleUserImageCropRight = value)
                        }
                    },
                    cropBottom = bubbleUserImageCropBottomInput,
                    onCropBottomChange = {
                        val value = it.coerceIn(0f, 0.45f)
                        onBubbleUserImageCropBottomInputChange(value)
                    },
                    onCropBottomChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleUserImageCropBottom = value)
                        }
                    },
                    repeatXStart = bubbleUserImageRepeatStartInput,
                    onRepeatXStartChange = {
                        val maxValue = (bubbleUserImageRepeatEndInput - 0.01f).coerceAtLeast(0.05f)
                        val value = it.coerceIn(0.05f, maxValue)
                        onBubbleUserImageRepeatStartInputChange(value)
                    },
                    onRepeatXStartChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleUserImageRepeatStart = value)
                        }
                    },
                    repeatXEnd = bubbleUserImageRepeatEndInput,
                    onRepeatXEndChange = {
                        val minValue = (bubbleUserImageRepeatStartInput + 0.01f).coerceAtMost(0.95f)
                        val value = it.coerceIn(minValue, 0.95f)
                        onBubbleUserImageRepeatEndInputChange(value)
                    },
                    onRepeatXEndChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleUserImageRepeatEnd = value)
                        }
                    },
                    repeatYStart = bubbleUserImageRepeatYStartInput,
                    onRepeatYStartChange = {
                        val maxValue = (bubbleUserImageRepeatYEndInput - 0.01f).coerceAtLeast(0.05f)
                        val value = it.coerceIn(0.05f, maxValue)
                        onBubbleUserImageRepeatYStartInputChange(value)
                    },
                    onRepeatYStartChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleUserImageRepeatYStart = value)
                        }
                    },
                    repeatYEnd = bubbleUserImageRepeatYEndInput,
                    onRepeatYEndChange = {
                        val minValue = (bubbleUserImageRepeatYStartInput + 0.01f).coerceAtMost(0.95f)
                        val value = it.coerceIn(minValue, 0.95f)
                        onBubbleUserImageRepeatYEndInputChange(value)
                    },
                    onRepeatYEndChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleUserImageRepeatYEnd = value)
                        }
                    },
                    imageScale = bubbleUserImageScaleInput,
                    onImageScaleChange = {
                        val value = it.coerceIn(0.2f, 3f)
                        onBubbleUserImageScaleInputChange(value)
                    },
                    onImageScaleChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleUserImageScale = value)
                        }
                    },
                    contentPaddingLeft = bubbleUserContentPaddingLeftInput,
                    onContentPaddingLeftChange = {
                        onBubbleUserContentPaddingLeftInputChange(it)
                    },
                    onContentPaddingLeftChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(
                                bubbleUserContentPaddingLeft = value,
                            )
                        }
                    },
                    contentPaddingRight = bubbleUserContentPaddingRightInput,
                    onContentPaddingRightChange = {
                        onBubbleUserContentPaddingRightInputChange(it)
                    },
                    onContentPaddingRightChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(
                                bubbleUserContentPaddingRight = value,
                            )
                        }
                    },
                )

                if (previewUserImageStyle != null) {
                    NineSliceSourcePreviewCard(
                        title = stringResource(id = R.string.chat_style_preview_user_source_title),
                        imageStyle = previewUserImageStyle,
                    )
                }

                BubbleImageStyleEditor(
                    title = stringResource(id = R.string.chat_style_bubble_ai_image_title),
                    enabled =
                        !bubbleAiBubbleLiquidGlassInput &&
                            !bubbleAiBubbleWaterGlassInput &&
                            bubbleAiUseImageInput,
                    switchEnabled =
                        !bubbleAiBubbleLiquidGlassInput && !bubbleAiBubbleWaterGlassInput,
                    description =
                        if (bubbleAiBubbleLiquidGlassInput || bubbleAiBubbleWaterGlassInput) {
                            stringResource(
                                id = R.string.chat_style_bubble_ai_image_disabled_by_glass,
                            )
                        } else {
                            stringResource(id = R.string.chat_style_bubble_image_desc)
                        },
                    onEnabledChange = {
                        onBubbleAiUseImageInputChange(it)
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleAiUseImage = it)
                        }
                    },
                    imageUri = bubbleAiImageUriInput,
                    onPickImage = onPickBubbleAiImage,
                    onClearImage = onClearBubbleAiImage,
                    cropLeft = bubbleAiImageCropLeftInput,
                    onCropLeftChange = {
                        val value = it.coerceIn(0f, 0.45f)
                        onBubbleAiImageCropLeftInputChange(value)
                    },
                    onCropLeftChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleAiImageCropLeft = value)
                        }
                    },
                    cropTop = bubbleAiImageCropTopInput,
                    onCropTopChange = {
                        val value = it.coerceIn(0f, 0.45f)
                        onBubbleAiImageCropTopInputChange(value)
                    },
                    onCropTopChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleAiImageCropTop = value)
                        }
                    },
                    cropRight = bubbleAiImageCropRightInput,
                    onCropRightChange = {
                        val value = it.coerceIn(0f, 0.45f)
                        onBubbleAiImageCropRightInputChange(value)
                    },
                    onCropRightChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleAiImageCropRight = value)
                        }
                    },
                    cropBottom = bubbleAiImageCropBottomInput,
                    onCropBottomChange = {
                        val value = it.coerceIn(0f, 0.45f)
                        onBubbleAiImageCropBottomInputChange(value)
                    },
                    onCropBottomChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleAiImageCropBottom = value)
                        }
                    },
                    repeatXStart = bubbleAiImageRepeatStartInput,
                    onRepeatXStartChange = {
                        val maxValue = (bubbleAiImageRepeatEndInput - 0.01f).coerceAtLeast(0.05f)
                        val value = it.coerceIn(0.05f, maxValue)
                        onBubbleAiImageRepeatStartInputChange(value)
                    },
                    onRepeatXStartChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleAiImageRepeatStart = value)
                        }
                    },
                    repeatXEnd = bubbleAiImageRepeatEndInput,
                    onRepeatXEndChange = {
                        val minValue = (bubbleAiImageRepeatStartInput + 0.01f).coerceAtMost(0.95f)
                        val value = it.coerceIn(minValue, 0.95f)
                        onBubbleAiImageRepeatEndInputChange(value)
                    },
                    onRepeatXEndChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleAiImageRepeatEnd = value)
                        }
                    },
                    repeatYStart = bubbleAiImageRepeatYStartInput,
                    onRepeatYStartChange = {
                        val maxValue = (bubbleAiImageRepeatYEndInput - 0.01f).coerceAtLeast(0.05f)
                        val value = it.coerceIn(0.05f, maxValue)
                        onBubbleAiImageRepeatYStartInputChange(value)
                    },
                    onRepeatYStartChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleAiImageRepeatYStart = value)
                        }
                    },
                    repeatYEnd = bubbleAiImageRepeatYEndInput,
                    onRepeatYEndChange = {
                        val minValue = (bubbleAiImageRepeatYStartInput + 0.01f).coerceAtMost(0.95f)
                        val value = it.coerceIn(minValue, 0.95f)
                        onBubbleAiImageRepeatYEndInputChange(value)
                    },
                    onRepeatYEndChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleAiImageRepeatYEnd = value)
                        }
                    },
                    imageScale = bubbleAiImageScaleInput,
                    onImageScaleChange = {
                        val value = it.coerceIn(0.2f, 3f)
                        onBubbleAiImageScaleInputChange(value)
                    },
                    onImageScaleChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(bubbleAiImageScale = value)
                        }
                    },
                    contentPaddingLeft = bubbleAiContentPaddingLeftInput,
                    onContentPaddingLeftChange = {
                        onBubbleAiContentPaddingLeftInputChange(it)
                    },
                    onContentPaddingLeftChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(
                                bubbleAiContentPaddingLeft = value,
                            )
                        }
                    },
                    contentPaddingRight = bubbleAiContentPaddingRightInput,
                    onContentPaddingRightChange = {
                        onBubbleAiContentPaddingRightInputChange(it)
                    },
                    onContentPaddingRightChangeFinished = { value ->
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(
                                bubbleAiContentPaddingRight = value,
                            )
                        }
                    },
                )

                if (previewAiImageStyle != null) {
                    NineSliceSourcePreviewCard(
                        title = stringResource(id = R.string.chat_style_preview_ai_source_title),
                        imageStyle = previewAiImageStyle,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val previewChatStyle =
                if (chatStyleInput == UserPreferencesManager.CHAT_STYLE_BUBBLE) {
                    ChatStyle.BUBBLE
                } else {
                    ChatStyle.CURSOR
                }

            ChatStylePreviewCard(
                chatStyle = previewChatStyle,
                userColor =
                    if (previewChatStyle == ChatStyle.CURSOR && cursorUserBubbleFollowThemeInput) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else if (previewChatStyle == ChatStyle.CURSOR) {
                        Color(cursorUserBubbleColorInput)
                    } else {
                        Color(bubbleUserBubbleColorInput)
                    },
                aiColor =
                    if (previewChatStyle == ChatStyle.BUBBLE) {
                        Color(bubbleAiBubbleColorInput)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                userTextColor = Color(bubbleUserTextColorInput),
                aiTextColor = Color(bubbleAiTextColorInput),
                userFontFamily = previewUserFontFamily,
                aiFontFamily = previewAiFontFamily,
                userImageStyle = if (previewChatStyle == ChatStyle.BUBBLE) previewUserImageStyle else null,
                aiImageStyle = if (previewChatStyle == ChatStyle.BUBBLE) previewAiImageStyle else null,
                bubbleShowAvatar = bubbleShowAvatarInput,
                bubbleWideLayoutEnabled = bubbleWideLayoutEnabledInput,
                bubbleUserRoundedCornersEnabled = bubbleUserRoundedCornersEnabledInput,
                bubbleAiRoundedCornersEnabled = bubbleAiRoundedCornersEnabledInput,
                bubbleUserContentPaddingLeft = bubbleUserContentPaddingLeftInput,
                bubbleUserContentPaddingRight = bubbleUserContentPaddingRightInput,
                bubbleAiContentPaddingLeft = bubbleAiContentPaddingLeftInput,
                bubbleAiContentPaddingRight = bubbleAiContentPaddingRightInput,
                userAvatarUri = previewUserAvatarUri,
                aiAvatarUri = previewAiAvatarUri,
            )
        }
    }
}

@Composable
private fun BubbleFontStyleEditor(
    title: String,
    useCustomFont: Boolean,
    onUseCustomFontChange: (Boolean) -> Unit,
    fontType: String,
    onFontTypeChange: (String) -> Unit,
    systemFontName: String,
    onSystemFontNameChange: (String) -> Unit,
    customFontPath: String?,
    onPickFont: () -> Unit,
    onClearFont: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(id = R.string.use_system_or_custom_font),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = useCustomFont, onCheckedChange = onUseCustomFontChange)
            }

            if (useCustomFont) {
                Text(
                    text = stringResource(id = R.string.font_type_label),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = fontType == UserPreferencesManager.FONT_TYPE_SYSTEM,
                        onClick = {
                            onFontTypeChange(UserPreferencesManager.FONT_TYPE_SYSTEM)
                        },
                        label = { Text(stringResource(id = R.string.system_font)) },
                    )
                    FilterChip(
                        selected = fontType == UserPreferencesManager.FONT_TYPE_FILE,
                        onClick = {
                            onFontTypeChange(UserPreferencesManager.FONT_TYPE_FILE)
                        },
                        label = { Text(stringResource(id = R.string.custom_font_file)) },
                    )
                }

                when (fontType) {
                    UserPreferencesManager.FONT_TYPE_SYSTEM -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf(
                                UserPreferencesManager.SYSTEM_FONT_DEFAULT to
                                    stringResource(id = R.string.theme_font_default),
                                UserPreferencesManager.SYSTEM_FONT_SERIF to
                                    stringResource(id = R.string.theme_font_serif),
                                UserPreferencesManager.SYSTEM_FONT_SANS_SERIF to
                                    stringResource(id = R.string.theme_font_sans_serif),
                                UserPreferencesManager.SYSTEM_FONT_MONOSPACE to
                                    stringResource(id = R.string.theme_font_monospace),
                                UserPreferencesManager.SYSTEM_FONT_CURSIVE to
                                    stringResource(id = R.string.theme_font_cursive),
                            ).forEach { (fontName, label) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = systemFontName == fontName,
                                        onClick = { onSystemFontNameChange(fontName) },
                                    )
                                    Text(text = label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    UserPreferencesManager.FONT_TYPE_FILE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = onPickFont,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(id = R.string.select_font_file))
                            }
                            OutlinedButton(
                                onClick = onClearFont,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(id = R.string.clear_font))
                            }
                        }

                        if (!customFontPath.isNullOrBlank()) {
                            Text(
                                text =
                                    stringResource(
                                        id = R.string.current_font_file_path,
                                        customFontPath.substringAfterLast("/"),
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BubbleImageStyleEditor(
    title: String,
    enabled: Boolean,
    switchEnabled: Boolean = true,
    description: String,
    onEnabledChange: (Boolean) -> Unit,
    imageUri: String?,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    cropLeft: Float,
    onCropLeftChange: (Float) -> Unit,
    onCropLeftChangeFinished: (Float) -> Unit,
    cropTop: Float,
    onCropTopChange: (Float) -> Unit,
    onCropTopChangeFinished: (Float) -> Unit,
    cropRight: Float,
    onCropRightChange: (Float) -> Unit,
    onCropRightChangeFinished: (Float) -> Unit,
    cropBottom: Float,
    onCropBottomChange: (Float) -> Unit,
    onCropBottomChangeFinished: (Float) -> Unit,
    repeatXStart: Float,
    onRepeatXStartChange: (Float) -> Unit,
    onRepeatXStartChangeFinished: (Float) -> Unit,
    repeatXEnd: Float,
    onRepeatXEndChange: (Float) -> Unit,
    onRepeatXEndChangeFinished: (Float) -> Unit,
    repeatYStart: Float,
    onRepeatYStartChange: (Float) -> Unit,
    onRepeatYStartChangeFinished: (Float) -> Unit,
    repeatYEnd: Float,
    onRepeatYEndChange: (Float) -> Unit,
    onRepeatYEndChangeFinished: (Float) -> Unit,
    imageScale: Float,
    onImageScaleChange: (Float) -> Unit,
    onImageScaleChangeFinished: (Float) -> Unit,
    contentPaddingLeft: Float,
    onContentPaddingLeftChange: (Float) -> Unit,
    onContentPaddingLeftChangeFinished: (Float) -> Unit,
    contentPaddingRight: Float,
    onContentPaddingRightChange: (Float) -> Unit,
    onContentPaddingRightChangeFinished: (Float) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    enabled = switchEnabled,
                    onCheckedChange = onEnabledChange,
                )
            }

            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onPickImage,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(id = R.string.chat_style_bubble_pick_image))
                    }
                    OutlinedButton(
                        onClick = onClearImage,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(id = R.string.chat_style_bubble_clear_image))
                    }
                }

                Text(
                    text =
                        if (imageUri.isNullOrBlank()) {
                            stringResource(id = R.string.chat_style_bubble_no_image_selected)
                        } else {
                            stringResource(id = R.string.chat_style_bubble_image_selected)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BubbleStyleSliderRow(
                        label = stringResource(id = R.string.chat_style_bubble_crop_left),
                        value = cropLeft,
                        range = 0f..0.45f,
                        onValueChange = onCropLeftChange,
                        onValueChangeFinished = onCropLeftChangeFinished,
                        modifier = Modifier.weight(1f),
                    )
                    BubbleStyleSliderRow(
                        label = stringResource(id = R.string.chat_style_bubble_crop_top),
                        value = cropTop,
                        range = 0f..0.45f,
                        onValueChange = onCropTopChange,
                        onValueChangeFinished = onCropTopChangeFinished,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BubbleStyleSliderRow(
                        label = stringResource(id = R.string.chat_style_bubble_crop_right),
                        value = cropRight,
                        range = 0f..0.45f,
                        onValueChange = onCropRightChange,
                        onValueChangeFinished = onCropRightChangeFinished,
                        modifier = Modifier.weight(1f),
                    )
                    BubbleStyleSliderRow(
                        label = stringResource(id = R.string.chat_style_bubble_crop_bottom),
                        value = cropBottom,
                        range = 0f..0.45f,
                        onValueChange = onCropBottomChange,
                        onValueChangeFinished = onCropBottomChangeFinished,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BubbleStyleSliderRow(
                        label = stringResource(id = R.string.chat_style_bubble_repeat_x_start),
                        value = repeatXStart,
                        range = 0.05f..0.9f,
                        onValueChange = onRepeatXStartChange,
                        onValueChangeFinished = onRepeatXStartChangeFinished,
                        modifier = Modifier.weight(1f),
                    )
                    BubbleStyleSliderRow(
                        label = stringResource(id = R.string.chat_style_bubble_repeat_x_end),
                        value = repeatXEnd,
                        range = ((repeatXStart + 0.01f).coerceAtMost(0.95f))..0.95f,
                        onValueChange = onRepeatXEndChange,
                        onValueChangeFinished = onRepeatXEndChangeFinished,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BubbleStyleSliderRow(
                        label = stringResource(id = R.string.chat_style_bubble_repeat_y_start),
                        value = repeatYStart,
                        range = 0.05f..0.9f,
                        onValueChange = onRepeatYStartChange,
                        onValueChangeFinished = onRepeatYStartChangeFinished,
                        modifier = Modifier.weight(1f),
                    )
                    BubbleStyleSliderRow(
                        label = stringResource(id = R.string.chat_style_bubble_repeat_y_end),
                        value = repeatYEnd,
                        range = ((repeatYStart + 0.01f).coerceAtMost(0.95f))..0.95f,
                        onValueChange = onRepeatYEndChange,
                        onValueChangeFinished = onRepeatYEndChangeFinished,
                        modifier = Modifier.weight(1f),
                    )
                }
                BubbleStyleSliderRow(
                    label = stringResource(id = R.string.chat_style_bubble_image_scale),
                    value = imageScale,
                    range = 0.2f..3f,
                    onValueChange = onImageScaleChange,
                    onValueChangeFinished = onImageScaleChangeFinished,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BubbleStyleDpSliderRow(
                        label = stringResource(id = R.string.chat_style_bubble_padding_left),
                        value = contentPaddingLeft,
                        range = 0f..32f,
                        onValueChange = onContentPaddingLeftChange,
                        onValueChangeFinished = onContentPaddingLeftChangeFinished,
                        modifier = Modifier.weight(1f),
                    )
                    BubbleStyleDpSliderRow(
                        label = stringResource(id = R.string.chat_style_bubble_padding_right),
                        value = contentPaddingRight,
                        range = 0f..32f,
                        onValueChange = onContentPaddingRightChange,
                        onValueChangeFinished = onContentPaddingRightChangeFinished,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleStyleSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastCommittedValue by remember { mutableStateOf(value) }
    val latestValue by rememberUpdatedState(value)
    val latestRange by rememberUpdatedState(range)
    val latestValueFinishCallback by rememberUpdatedState(onValueChangeFinished)
    val valueChangeFinished = remember {
        {
            val finalValue = latestValue.coerceIn(latestRange.start, latestRange.endInclusive)
            if (abs(finalValue - lastCommittedValue) > 0.0005f) {
                latestValueFinishCallback(finalValue)
                lastCommittedValue = finalValue
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = valueChangeFinished,
            valueRange = range,
        )
    }
}

@Composable
private fun BubbleStyleDpSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastCommittedValue by remember { mutableStateOf(value) }
    val latestValue by rememberUpdatedState(value)
    val latestRange by rememberUpdatedState(range)
    val latestValueFinishCallback by rememberUpdatedState(onValueChangeFinished)
    val valueChangeFinished = remember {
        {
            val finalValue = latestValue.coerceIn(latestRange.start, latestRange.endInclusive)
            if (abs(finalValue - lastCommittedValue) > 0.1f) {
                latestValueFinishCallback(finalValue)
                lastCommittedValue = finalValue
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${value.roundToInt()}dp",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = valueChangeFinished,
            valueRange = range,
        )
    }
}

@Composable
private fun NineSliceSourcePreviewCard(
    title: String,
    imageStyle: BubbleImageStyleConfig,
) {
    val bitmap = rememberSourcePreviewBitmap(imageStyle.imageUri)

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            bitmap?.let { previewBitmap ->
                Canvas(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(84.dp),
                ) {
                    val srcWidth = previewBitmap.width.coerceAtLeast(1)
                    val srcHeight = previewBitmap.height.coerceAtLeast(1)
                    val fitScale =
                        min(
                            size.width / srcWidth.toFloat(),
                            size.height / srcHeight.toFloat(),
                        )

                    val drawWidth = max(1, (srcWidth * fitScale).roundToInt())
                    val drawHeight = max(1, (srcHeight * fitScale).roundToInt())
                    val drawLeft = ((size.width - drawWidth.toFloat()) / 2f).roundToInt()
                    val drawTop = ((size.height - drawHeight.toFloat()) / 2f).roundToInt()

                    drawImage(
                        image = previewBitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(srcWidth, srcHeight),
                        dstOffset = IntOffset(drawLeft, drawTop),
                        dstSize = IntSize(drawWidth, drawHeight),
                    )

                    val cropLeft = imageStyle.cropLeftRatio.coerceIn(0f, 0.45f)
                    val cropTop = imageStyle.cropTopRatio.coerceIn(0f, 0.45f)
                    val cropRight = imageStyle.cropRightRatio.coerceIn(0f, 0.45f)
                    val cropBottom = imageStyle.cropBottomRatio.coerceIn(0f, 0.45f)
                    val repeatXStart = imageStyle.repeatXStartRatio.coerceIn(0.05f, 0.9f)
                    val repeatXEnd = imageStyle.repeatXEndRatio.coerceIn(repeatXStart + 0.01f, 0.95f)
                    val repeatYStart = imageStyle.repeatYStartRatio.coerceIn(0.05f, 0.9f)
                    val repeatYEnd = imageStyle.repeatYEndRatio.coerceIn(repeatYStart + 0.01f, 0.95f)

                    val srcLeft = (srcWidth * cropLeft).roundToInt().coerceIn(0, srcWidth - 1)
                    val srcTop = (srcHeight * cropTop).roundToInt().coerceIn(0, srcHeight - 1)
                    val srcRight = (srcWidth * (1f - cropRight)).roundToInt().coerceIn(srcLeft + 1, srcWidth)
                    val srcBottom = (srcHeight * (1f - cropBottom)).roundToInt().coerceIn(srcTop + 1, srcHeight)
                    val croppedWidth = (srcRight - srcLeft).coerceAtLeast(1)
                    val croppedHeight = (srcBottom - srcTop).coerceAtLeast(1)

                    val repeatStartXPx =
                        if (croppedWidth < 3) {
                            0
                        } else {
                            (croppedWidth * repeatXStart).roundToInt().coerceIn(1, croppedWidth - 2)
                        }
                    val repeatEndXPx =
                        if (croppedWidth < 3) {
                            croppedWidth
                        } else {
                            (croppedWidth * repeatXEnd).roundToInt().coerceIn(repeatStartXPx + 1, croppedWidth - 1)
                        }
                    val repeatStartYPx =
                        if (croppedHeight < 3) {
                            0
                        } else {
                            (croppedHeight * repeatYStart).roundToInt().coerceIn(1, croppedHeight - 2)
                        }
                    val repeatEndYPx =
                        if (croppedHeight < 3) {
                            croppedHeight
                        } else {
                            (croppedHeight * repeatYEnd).roundToInt().coerceIn(repeatStartYPx + 1, croppedHeight - 1)
                        }

                    val cropLeftX = drawLeft + srcLeft * fitScale
                    val cropRightX = drawLeft + srcRight * fitScale
                    val cropTopY = drawTop + srcTop * fitScale
                    val cropBottomY = drawTop + srcBottom * fitScale
                    val leftX = drawLeft + (srcLeft + repeatStartXPx) * fitScale
                    val rightX = drawLeft + (srcLeft + repeatEndXPx) * fitScale
                    val topY = drawTop + (srcTop + repeatStartYPx) * fitScale
                    val bottomY = drawTop + (srcTop + repeatEndYPx) * fitScale
                    val lineColor = Color.Red.copy(alpha = 0.9f)
                    val borderColor = Color.Red.copy(alpha = 0.4f)
                    val strokeWidth = 1.dp.toPx().coerceAtLeast(1f)

                    // Crop border.
                    drawLine(
                        color = borderColor,
                        start = androidx.compose.ui.geometry.Offset(cropLeftX, cropTopY),
                        end = androidx.compose.ui.geometry.Offset(cropRightX, cropTopY),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        color = borderColor,
                        start = androidx.compose.ui.geometry.Offset(cropLeftX, cropBottomY),
                        end = androidx.compose.ui.geometry.Offset(cropRightX, cropBottomY),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        color = borderColor,
                        start = androidx.compose.ui.geometry.Offset(cropLeftX, cropTopY),
                        end = androidx.compose.ui.geometry.Offset(cropLeftX, cropBottomY),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        color = borderColor,
                        start = androidx.compose.ui.geometry.Offset(cropRightX, cropTopY),
                        end = androidx.compose.ui.geometry.Offset(cropRightX, cropBottomY),
                        strokeWidth = strokeWidth,
                    )

                    // 9-slice grid lines inside cropped area.
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(leftX, cropTopY),
                        end = androidx.compose.ui.geometry.Offset(leftX, cropBottomY),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(rightX, cropTopY),
                        end = androidx.compose.ui.geometry.Offset(rightX, cropBottomY),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(cropLeftX, topY),
                        end = androidx.compose.ui.geometry.Offset(cropRightX, topY),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(cropLeftX, bottomY),
                        end = androidx.compose.ui.geometry.Offset(cropRightX, bottomY),
                        strokeWidth = strokeWidth,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberSourcePreviewBitmap(uriString: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(uriString) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(context, uriString) {
        if (uriString.isBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                        BitmapFactory.decodeStream(input)?.asImageBitmap()
                    }
                }.getOrNull()
            }
    }

    return bitmap
}

@Composable
private fun ChatStylePreviewCard(
    chatStyle: ChatStyle,
    userColor: Color,
    aiColor: Color,
    userTextColor: Color,
    aiTextColor: Color,
    userFontFamily: FontFamily?,
    aiFontFamily: FontFamily?,
    userImageStyle: BubbleImageStyleConfig?,
    aiImageStyle: BubbleImageStyleConfig?,
    bubbleShowAvatar: Boolean,
    bubbleWideLayoutEnabled: Boolean,
    bubbleUserRoundedCornersEnabled: Boolean,
    bubbleAiRoundedCornersEnabled: Boolean,
    bubbleUserContentPaddingLeft: Float,
    bubbleUserContentPaddingRight: Float,
    bubbleAiContentPaddingLeft: Float,
    bubbleAiContentPaddingRight: Float,
    userAvatarUri: String?,
    aiAvatarUri: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
    ) {
        val baseTypography = MaterialTheme.typography
        val userTypography =
            remember(userFontFamily, baseTypography) {
                applyFontFamilyToTypography(baseTypography, userFontFamily)
            }
        val aiTypography =
            remember(aiFontFamily, baseTypography) {
                applyFontFamilyToTypography(baseTypography, aiFontFamily)
            }
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(id = R.string.chat_style_preview_title),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (chatStyle == ChatStyle.CURSOR) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = userColor,
                    tonalElevation = 1.dp,
                ) {
                    Text(
                        text = stringResource(id = R.string.chat_style_preview_user_message),
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.mcp_command_response),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                    Text(
                        text = stringResource(id = R.string.chat_style_preview_ai_message),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                val previewNameColor = MaterialTheme.colorScheme.onSurface
                val bubblePreviewMaxWidth = if (bubbleWideLayoutEnabled) 280.dp else 240.dp
                val userBubbleShape =
                    if (bubbleUserRoundedCornersEnabled) {
                        RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp)
                    } else {
                        RoundedCornerShape(0.dp)
                    }
                val aiBubbleShape =
                    if (bubbleAiRoundedCornersEnabled) {
                        RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
                    } else {
                        RoundedCornerShape(0.dp)
                    }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (bubbleWideLayoutEnabled) {
                        MaterialTheme(typography = userTypography) {
                            if (bubbleShowAvatar) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.chat_style_preview_user_name),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = previewNameColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    PreviewChatAvatar(
                                        avatarUri = userAvatarUri,
                                        contentDescription = stringResource(id = R.string.user_avatar_label),
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                if (userImageStyle != null) {
                                    BubbleImageBackgroundSurface(
                                        imageStyle = userImageStyle,
                                        shape = userBubbleShape,
                                        modifier = Modifier.widthIn(max = bubblePreviewMaxWidth).defaultMinSize(minHeight = 44.dp),
                                        contentPadding =
                                            PaddingValues(
                                                start = bubbleUserContentPaddingLeft.dp,
                                                top = 12.dp,
                                                end = bubbleUserContentPaddingRight.dp,
                                                bottom = 12.dp,
                                            ),
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.chat_style_preview_user_message),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = userTextColor,
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = userBubbleShape,
                                        color = userColor,
                                        modifier = Modifier.widthIn(max = bubblePreviewMaxWidth).defaultMinSize(minHeight = 44.dp),
                                        tonalElevation = 1.dp,
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.chat_style_preview_user_message),
                                            modifier =
                                                Modifier.padding(
                                                    start = bubbleUserContentPaddingLeft.dp,
                                                    top = 12.dp,
                                                    end = bubbleUserContentPaddingRight.dp,
                                                    bottom = 12.dp,
                                                ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = userTextColor,
                                        )
                                    }
                                }
                            }
                        }

                        MaterialTheme(typography = aiTypography) {
                            if (bubbleShowAvatar) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PreviewChatAvatar(
                                        avatarUri = aiAvatarUri,
                                        contentDescription = stringResource(id = R.string.ai_avatar_label),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = stringResource(id = R.string.chat_style_preview_ai_name),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = previewNameColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = stringResource(id = R.string.chat_style_preview_ai_meta),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                            ) {
                                if (aiImageStyle != null) {
                                    BubbleImageBackgroundSurface(
                                        imageStyle = aiImageStyle,
                                        shape = aiBubbleShape,
                                        modifier = Modifier.widthIn(max = bubblePreviewMaxWidth).defaultMinSize(minHeight = 44.dp),
                                        contentPadding =
                                            PaddingValues(
                                                start = bubbleAiContentPaddingLeft.dp,
                                                top = 12.dp,
                                                end = bubbleAiContentPaddingRight.dp,
                                                bottom = 12.dp,
                                            ),
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.chat_style_preview_ai_message),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = aiTextColor,
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = aiBubbleShape,
                                        color = aiColor,
                                        modifier = Modifier.widthIn(max = bubblePreviewMaxWidth).defaultMinSize(minHeight = 44.dp),
                                        tonalElevation = 1.dp,
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.chat_style_preview_ai_message),
                                            modifier =
                                                Modifier.padding(
                                                    start = bubbleAiContentPaddingLeft.dp,
                                                    top = 12.dp,
                                                    end = bubbleAiContentPaddingRight.dp,
                                                    bottom = 12.dp,
                                                ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = aiTextColor,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        MaterialTheme(typography = userTypography) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                if (userImageStyle != null) {
                                    BubbleImageBackgroundSurface(
                                        imageStyle = userImageStyle,
                                        shape = userBubbleShape,
                                        modifier = Modifier.widthIn(max = bubblePreviewMaxWidth).defaultMinSize(minHeight = 44.dp),
                                        contentPadding =
                                            PaddingValues(
                                                start = bubbleUserContentPaddingLeft.dp,
                                                top = 12.dp,
                                                end = bubbleUserContentPaddingRight.dp,
                                                bottom = 12.dp,
                                            ),
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.chat_style_preview_user_message),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = userTextColor,
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = userBubbleShape,
                                        color = userColor,
                                        modifier = Modifier.widthIn(max = bubblePreviewMaxWidth).defaultMinSize(minHeight = 44.dp),
                                        tonalElevation = 1.dp,
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.chat_style_preview_user_message),
                                            modifier =
                                                Modifier.padding(
                                                    start = bubbleUserContentPaddingLeft.dp,
                                                    top = 12.dp,
                                                    end = bubbleUserContentPaddingRight.dp,
                                                    bottom = 12.dp,
                                                ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = userTextColor,
                                        )
                                    }
                                }
                                if (bubbleShowAvatar) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    PreviewChatAvatar(
                                        avatarUri = userAvatarUri,
                                        contentDescription = stringResource(id = R.string.user_avatar_label),
                                    )
                                }
                            }
                        }

                        MaterialTheme(typography = aiTypography) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                if (bubbleShowAvatar) {
                                    PreviewChatAvatar(
                                        avatarUri = aiAvatarUri,
                                        contentDescription = stringResource(id = R.string.ai_avatar_label),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                if (aiImageStyle != null) {
                                    BubbleImageBackgroundSurface(
                                        imageStyle = aiImageStyle,
                                        shape = aiBubbleShape,
                                        modifier = Modifier.widthIn(max = bubblePreviewMaxWidth).defaultMinSize(minHeight = 44.dp),
                                        contentPadding =
                                            PaddingValues(
                                                start = bubbleAiContentPaddingLeft.dp,
                                                top = 12.dp,
                                                end = bubbleAiContentPaddingRight.dp,
                                                bottom = 12.dp,
                                            ),
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.chat_style_preview_ai_message),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = aiTextColor,
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = aiBubbleShape,
                                        color = aiColor,
                                        modifier = Modifier.widthIn(max = bubblePreviewMaxWidth).defaultMinSize(minHeight = 44.dp),
                                        tonalElevation = 1.dp,
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.chat_style_preview_ai_message),
                                            modifier =
                                                Modifier.padding(
                                                    start = bubbleAiContentPaddingLeft.dp,
                                                    top = 12.dp,
                                                    end = bubbleAiContentPaddingRight.dp,
                                                    bottom = 12.dp,
                                                ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = aiTextColor,
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

@Composable
private fun PreviewChatAvatar(
    avatarUri: String?,
    contentDescription: String,
) {
    Box(
        modifier =
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUri.isNullOrBlank()) {
            Image(
                painter = rememberAsyncImagePainter(Uri.parse(avatarUri)),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun ThemeSettingsDisplayOptionsSection(
    cardColors: CardColors,
    showThinkingProcessInput: Boolean,
    onShowThinkingProcessInputChange: (Boolean) -> Unit,
    showStatusTagsInput: Boolean,
    onShowStatusTagsInputChange: (Boolean) -> Unit,
    showInputProcessingStatusInput: Boolean,
    onShowInputProcessingStatusInputChange: (Boolean) -> Unit,
    showChatFloatingDotsAnimationInput: Boolean,
    onShowChatFloatingDotsAnimationInputChange: (Boolean) -> Unit,
    saveThemeSettingsWithCharacterCard: SaveThemeSettingsAction,
    preferencesManager: UserPreferencesManager,
) {
    ThemeSettingsSectionTitle(
        title = stringResource(id = R.string.display_options_title),
        icon = Icons.Default.ColorLens,
    )

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.show_thinking_process),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(id = R.string.show_thinking_process_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = showThinkingProcessInput,
                    onCheckedChange = {
                        onShowThinkingProcessInputChange(it)
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(showThinkingProcess = it)
                        }
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.show_status_tags),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(id = R.string.show_status_tags_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = showStatusTagsInput,
                    onCheckedChange = {
                        onShowStatusTagsInputChange(it)
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(showStatusTags = it)
                        }
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.show_input_processing_status),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text =
                            stringResource(id = R.string.show_input_processing_status_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = showInputProcessingStatusInput,
                    onCheckedChange = {
                        onShowInputProcessingStatusInputChange(it)
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(
                                showInputProcessingStatus = it,
                            )
                        }
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.show_chat_floating_dots_animation),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(id = R.string.show_chat_floating_dots_animation_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = showChatFloatingDotsAnimationInput,
                    onCheckedChange = {
                        onShowChatFloatingDotsAnimationInputChange(it)
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(
                                showChatFloatingDotsAnimation = it,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun ThemeSettingsSectionTitle(
    title: String,
    icon: ImageVector,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
}

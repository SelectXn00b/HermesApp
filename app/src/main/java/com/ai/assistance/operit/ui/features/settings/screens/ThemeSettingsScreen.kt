package com.ai.assistance.operit.ui.features.settings.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.ui.features.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsAvatarSection
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsBackgroundSection
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsCharacterBindingInfoCard
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsChatStyleSection
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsColorCustomizationSection
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsDisplayOptionsSection
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsFontSection
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsThemeModeSection
import com.ai.assistance.operit.ui.main.components.rememberNavigationDrawerAppearance
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.FileUtils
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

private data class NinePatchBubbleAutoParams(
    val cropLeftRatio: Float,
    val cropTopRatio: Float,
    val cropRightRatio: Float,
    val cropBottomRatio: Float,
    val repeatXStartRatio: Float,
    val repeatXEndRatio: Float,
    val repeatYStartRatio: Float,
    val repeatYEndRatio: Float,
)

private fun isNinePatchMarker(colorInt: Int): Boolean {
    val alpha = (colorInt ushr 24) and 0xFF
    if (alpha < 0x80) return false
    val red = (colorInt ushr 16) and 0xFF
    val green = (colorInt ushr 8) and 0xFF
    val blue = colorInt and 0xFF
    return red < 32 && green < 32 && blue < 32
}

private fun buildStretchRange(marked: List<Int>, innerSize: Int): Pair<Float, Float>? {
    if (marked.isEmpty() || innerSize <= 0) return null
    val start = marked.first().toFloat() / innerSize.toFloat()
    val endExclusive = (marked.last() + 1).toFloat() / innerSize.toFloat()
    return start.coerceIn(0f, 1f) to endExclusive.coerceIn(0f, 1f)
}

private suspend fun parseNinePatchBubbleParams(context: android.content.Context, uri: Uri): NinePatchBubbleAutoParams? =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull()?.let { bitmap ->
            val width = bitmap.width
            val height = bitmap.height
            if (width < 3 || height < 3) return@let null

            val innerWidth = width - 2
            val innerHeight = height - 2
            if (innerWidth <= 0 || innerHeight <= 0) return@let null

            val topMarkers = mutableListOf<Int>()
            val leftMarkers = mutableListOf<Int>()
            for (x in 0 until innerWidth) {
                if (isNinePatchMarker(bitmap.getPixel(x + 1, 0))) {
                    topMarkers.add(x)
                }
            }
            for (y in 0 until innerHeight) {
                if (isNinePatchMarker(bitmap.getPixel(0, y + 1))) {
                    leftMarkers.add(y)
                }
            }

            val xRange = buildStretchRange(topMarkers, innerWidth) ?: (0.35f to 0.65f)
            val yRange = buildStretchRange(leftMarkers, innerHeight) ?: (0.35f to 0.65f)

            NinePatchBubbleAutoParams(
                cropLeftRatio = (1f / width.toFloat()).coerceIn(0f, 0.45f),
                cropTopRatio = (1f / height.toFloat()).coerceIn(0f, 0.45f),
                cropRightRatio = (1f / width.toFloat()).coerceIn(0f, 0.45f),
                cropBottomRatio = (1f / height.toFloat()).coerceIn(0f, 0.45f),
                repeatXStartRatio = xRange.first,
                repeatXEndRatio = xRange.second,
                repeatYStartRatio = yRange.first,
                repeatYEndRatio = yRange.second,
            )
        }
    }

private fun resolveDisplayName(context: android.content.Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            }
    }.getOrNull()
}

private fun isNinePatchPngUri(context: android.content.Context, uri: Uri): Boolean {
    val displayName = resolveDisplayName(context, uri)?.lowercase()
    if (displayName != null && displayName.endsWith(".9.png")) {
        return true
    }
    val pathName = uri.lastPathSegment?.lowercase()
    return pathName?.endsWith(".9.png") == true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen() {
    val context = LocalContext.current
    val preferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val displayPreferencesManager = remember { DisplayPreferencesManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    // 添加角色卡管理器
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val characterGroupCardManager = remember { CharacterGroupCardManager.getInstance(context) }
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }

    val activePrompt by activePromptManager.activePromptFlow.collectAsState(
        initial = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
    )
    val activeCharacterCard by remember(activePrompt) {
        when (val prompt = activePrompt) {
            is ActivePrompt.CharacterCard -> characterCardManager.getCharacterCardFlow(prompt.id)
            is ActivePrompt.CharacterGroup -> flowOf(null)
        }
    }.collectAsState(initial = null)
    val activeCharacterGroup by remember(activePrompt) {
        when (val prompt = activePrompt) {
            is ActivePrompt.CharacterGroup -> characterGroupCardManager.getCharacterGroupCardFlow(prompt.id)
            is ActivePrompt.CharacterCard -> flowOf(null)
        }
    }.collectAsState(initial = null)

    // Collect theme settings
    val themeMode =
            preferencesManager.themeMode.collectAsState(
                            initial = UserPreferencesManager.THEME_MODE_LIGHT
                    )
                    .value
    val useSystemTheme = preferencesManager.useSystemTheme.collectAsState(initial = true).value
    val customPrimaryColor =
            preferencesManager.customPrimaryColor.collectAsState(initial = null).value
    val customSecondaryColor =
            preferencesManager.customSecondaryColor.collectAsState(initial = null).value
    val useCustomColors = preferencesManager.useCustomColors.collectAsState(initial = false).value

    // Collect background image settings
    val useBackgroundImage =
            preferencesManager.useBackgroundImage.collectAsState(initial = false).value
    val backgroundImageUri =
            preferencesManager.backgroundImageUri.collectAsState(initial = null).value
    val backgroundImageOpacity =
            preferencesManager.backgroundImageOpacity.collectAsState(initial = 0.3f).value

    // Collect background media type and video settings
    val backgroundMediaType =
            preferencesManager.backgroundMediaType.collectAsState(
                            initial = UserPreferencesManager.MEDIA_TYPE_IMAGE
                    )
                    .value
    val videoBackgroundMuted =
            preferencesManager.videoBackgroundMuted.collectAsState(initial = true).value
    val videoBackgroundLoop =
            preferencesManager.videoBackgroundLoop.collectAsState(initial = true).value

    // Collect toolbar transparency setting
    val toolbarTransparent =
            preferencesManager.toolbarTransparent.collectAsState(initial = false).value

    // Collect navigation drawer appearance settings
    val navigationDrawerWaterGlass =
            preferencesManager.navigationDrawerWaterGlass.collectAsState(initial = false).value
    val navigationDrawerButtonLiquidGlass =
            preferencesManager
                    .navigationDrawerButtonLiquidGlass
                    .collectAsState(initial = false)
                    .value
    val useCustomNavigationDrawerBackgroundColor =
            preferencesManager
                    .useCustomNavigationDrawerBackgroundColor
                    .collectAsState(initial = false)
                    .value
    val customNavigationDrawerBackgroundColor =
            preferencesManager.customNavigationDrawerBackgroundColor.collectAsState(initial = null).value
    val useCustomNavigationDrawerAccentColor =
            preferencesManager
                    .useCustomNavigationDrawerAccentColor
                    .collectAsState(initial = false)
                    .value
    val customNavigationDrawerAccentColor =
            preferencesManager.customNavigationDrawerAccentColor.collectAsState(initial = null).value
    val navigationDrawerAppearance = rememberNavigationDrawerAppearance()

    // Collect AppBar custom color settings
    val useCustomAppBarColor =
            preferencesManager.useCustomAppBarColor.collectAsState(initial = false).value
    val customAppBarColor =
            preferencesManager.customAppBarColor.collectAsState(initial = null).value

    // Collect status bar color settings
    val useCustomStatusBarColor =
            preferencesManager.useCustomStatusBarColor.collectAsState(initial = false).value
    val customStatusBarColor =
            preferencesManager.customStatusBarColor.collectAsState(initial = null).value
    val statusBarTransparent =
            preferencesManager.statusBarTransparent.collectAsState(initial = false).value
    val statusBarHidden =
            preferencesManager.statusBarHidden.collectAsState(initial = false).value
    val chatHeaderTransparent =
            preferencesManager.chatHeaderTransparent.collectAsState(initial = false).value
    val chatInputTransparent =
            preferencesManager.chatInputTransparent.collectAsState(initial = false).value
    val chatInputFloating =
            preferencesManager.chatInputFloating.collectAsState(initial = false).value
    val chatInputLiquidGlassRaw =
            preferencesManager.chatInputLiquidGlass.collectAsState(initial = false).value
    val chatInputWaterGlass =
            preferencesManager.chatInputWaterGlass.collectAsState(initial = false).value
    val chatInputLiquidGlass = chatInputLiquidGlassRaw && !chatInputWaterGlass
    val chatHeaderOverlayMode =
            preferencesManager.chatHeaderOverlayMode.collectAsState(initial = false).value

    // Collect AppBar content color settings
    val forceAppBarContentColor =
            preferencesManager.forceAppBarContentColor.collectAsState(initial = false).value
    val appBarContentColorMode =
            preferencesManager.appBarContentColorMode.collectAsState(
                            initial = UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_LIGHT
                    )
                    .value

    // Collect ChatHeader icon color settings
    val chatHeaderHistoryIconColor =
            preferencesManager.chatHeaderHistoryIconColor.collectAsState(initial = null).value
    val chatHeaderPipIconColor =
            preferencesManager.chatHeaderPipIconColor.collectAsState(initial = null).value

    // Collect background blur settings
    val useBackgroundBlur =
            preferencesManager.useBackgroundBlur.collectAsState(initial = false).value
    val backgroundBlurRadius =
            preferencesManager.backgroundBlurRadius.collectAsState(initial = 10f).value

    // Collect chat style setting
    val chatStyle = preferencesManager.chatStyle.collectAsState(initial = UserPreferencesManager.CHAT_STYLE_CURSOR).value

    // Collect input style setting
    val inputStyle =
        preferencesManager.inputStyle.collectAsState(
            initial = UserPreferencesManager.INPUT_STYLE_AGENT,
        ).value

    val bubbleShowAvatar = preferencesManager.bubbleShowAvatar.collectAsState(initial = true).value
    val bubbleWideLayoutEnabled =
        preferencesManager.bubbleWideLayoutEnabled.collectAsState(initial = false).value
    val cursorUserBubbleFollowTheme =
        preferencesManager.cursorUserBubbleFollowTheme.collectAsState(initial = true).value
    val cursorUserBubbleLiquidGlassRaw =
        preferencesManager.cursorUserBubbleLiquidGlass.collectAsState(initial = false).value
    val cursorUserBubbleWaterGlass =
        preferencesManager.cursorUserBubbleWaterGlass.collectAsState(initial = false).value
    val cursorUserBubbleLiquidGlass =
        cursorUserBubbleLiquidGlassRaw && !cursorUserBubbleWaterGlass
    val bubbleUserBubbleLiquidGlassRaw =
        preferencesManager.bubbleUserBubbleLiquidGlass.collectAsState(initial = false).value
    val bubbleUserBubbleWaterGlass =
        preferencesManager.bubbleUserBubbleWaterGlass.collectAsState(initial = false).value
    val bubbleUserBubbleLiquidGlass =
        bubbleUserBubbleLiquidGlassRaw && !bubbleUserBubbleWaterGlass
    val bubbleAiBubbleLiquidGlassRaw =
        preferencesManager.bubbleAiBubbleLiquidGlass.collectAsState(initial = false).value
    val bubbleAiBubbleWaterGlass =
        preferencesManager.bubbleAiBubbleWaterGlass.collectAsState(initial = false).value
    val bubbleAiBubbleLiquidGlass =
        bubbleAiBubbleLiquidGlassRaw && !bubbleAiBubbleWaterGlass
    val cursorUserBubbleColor =
        preferencesManager.cursorUserBubbleColor.collectAsState(initial = null).value
    val bubbleUserBubbleColor =
        preferencesManager.bubbleUserBubbleColor.collectAsState(initial = null).value
    val bubbleAiBubbleColor =
        preferencesManager.bubbleAiBubbleColor.collectAsState(initial = null).value
    val bubbleUserTextColor =
        preferencesManager.bubbleUserTextColor.collectAsState(initial = null).value
    val bubbleAiTextColor =
        preferencesManager.bubbleAiTextColor.collectAsState(initial = null).value
    val bubbleUserUseCustomFont =
        preferencesManager.bubbleUserUseCustomFont.collectAsState(initial = false).value
    val bubbleUserFontType =
        preferencesManager.bubbleUserFontType.collectAsState(
            initial = UserPreferencesManager.FONT_TYPE_SYSTEM,
        ).value
    val bubbleUserSystemFontName =
        preferencesManager.bubbleUserSystemFontName.collectAsState(
            initial = UserPreferencesManager.SYSTEM_FONT_DEFAULT,
        ).value
    val bubbleUserCustomFontPath =
        preferencesManager.bubbleUserCustomFontPath.collectAsState(initial = null).value
    val bubbleAiUseCustomFont =
        preferencesManager.bubbleAiUseCustomFont.collectAsState(initial = false).value
    val bubbleAiFontType =
        preferencesManager.bubbleAiFontType.collectAsState(
            initial = UserPreferencesManager.FONT_TYPE_SYSTEM,
        ).value
    val bubbleAiSystemFontName =
        preferencesManager.bubbleAiSystemFontName.collectAsState(
            initial = UserPreferencesManager.SYSTEM_FONT_DEFAULT,
        ).value
    val bubbleAiCustomFontPath =
        preferencesManager.bubbleAiCustomFontPath.collectAsState(initial = null).value
    val bubbleUserUseImage =
        preferencesManager.bubbleUserUseImage.collectAsState(initial = false).value
    val bubbleAiUseImage =
        preferencesManager.bubbleAiUseImage.collectAsState(initial = false).value
    val bubbleUserImageUri =
        preferencesManager.bubbleUserImageUri.collectAsState(initial = null).value
    val bubbleAiImageUri =
        preferencesManager.bubbleAiImageUri.collectAsState(initial = null).value
    val bubbleUserImageCropLeft =
        preferencesManager.bubbleUserImageCropLeft.collectAsState(initial = 0f).value
    val bubbleUserImageCropTop =
        preferencesManager.bubbleUserImageCropTop.collectAsState(initial = 0f).value
    val bubbleUserImageCropRight =
        preferencesManager.bubbleUserImageCropRight.collectAsState(initial = 0f).value
    val bubbleUserImageCropBottom =
        preferencesManager.bubbleUserImageCropBottom.collectAsState(initial = 0f).value
    val bubbleUserImageRepeatStart =
        preferencesManager.bubbleUserImageRepeatStart.collectAsState(initial = 0.35f).value
    val bubbleUserImageRepeatEnd =
        preferencesManager.bubbleUserImageRepeatEnd.collectAsState(initial = 0.65f).value
    val bubbleUserImageRepeatYStart =
        preferencesManager.bubbleUserImageRepeatYStart.collectAsState(initial = 0.35f).value
    val bubbleUserImageRepeatYEnd =
        preferencesManager.bubbleUserImageRepeatYEnd.collectAsState(initial = 0.65f).value
    val bubbleUserImageScale =
        preferencesManager.bubbleUserImageScale.collectAsState(initial = 1f).value
    val bubbleAiImageCropLeft =
        preferencesManager.bubbleAiImageCropLeft.collectAsState(initial = 0f).value
    val bubbleAiImageCropTop =
        preferencesManager.bubbleAiImageCropTop.collectAsState(initial = 0f).value
    val bubbleAiImageCropRight =
        preferencesManager.bubbleAiImageCropRight.collectAsState(initial = 0f).value
    val bubbleAiImageCropBottom =
        preferencesManager.bubbleAiImageCropBottom.collectAsState(initial = 0f).value
    val bubbleAiImageRepeatStart =
        preferencesManager.bubbleAiImageRepeatStart.collectAsState(initial = 0.35f).value
    val bubbleAiImageRepeatEnd =
        preferencesManager.bubbleAiImageRepeatEnd.collectAsState(initial = 0.65f).value
    val bubbleAiImageRepeatYStart =
        preferencesManager.bubbleAiImageRepeatYStart.collectAsState(initial = 0.35f).value
    val bubbleAiImageRepeatYEnd =
        preferencesManager.bubbleAiImageRepeatYEnd.collectAsState(initial = 0.65f).value
    val bubbleAiImageScale =
        preferencesManager.bubbleAiImageScale.collectAsState(initial = 1f).value
    val bubbleImageRenderMode =
        preferencesManager.bubbleImageRenderMode.collectAsState(
            initial = UserPreferencesManager.BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE,
        ).value
    val bubbleUserRoundedCornersEnabled =
        preferencesManager.bubbleUserRoundedCornersEnabled.collectAsState(initial = true).value
    val bubbleAiRoundedCornersEnabled =
        preferencesManager.bubbleAiRoundedCornersEnabled.collectAsState(initial = true).value
    val bubbleUserContentPaddingLeft =
        preferencesManager.bubbleUserContentPaddingLeft.collectAsState(initial = 12f).value
    val bubbleUserContentPaddingRight =
        preferencesManager.bubbleUserContentPaddingRight.collectAsState(initial = 12f).value
    val bubbleAiContentPaddingLeft =
        preferencesManager.bubbleAiContentPaddingLeft.collectAsState(initial = 12f).value
    val bubbleAiContentPaddingRight =
        preferencesManager.bubbleAiContentPaddingRight.collectAsState(initial = 12f).value

    // Collect new display settings
    val showThinkingProcess = preferencesManager.showThinkingProcess.collectAsState(initial = true).value
    val showStatusTags = preferencesManager.showStatusTags.collectAsState(initial = true).value
    val showInputProcessingStatus = preferencesManager.showInputProcessingStatus.collectAsState(initial = true).value
    val showChatFloatingDotsAnimation =
        preferencesManager.showChatFloatingDotsAnimation.collectAsState(initial = true).value

    // Collect avatar settings
    val userAvatarUri = preferencesManager.customUserAvatarUri.collectAsState(initial = null).value
    val aiAvatarUri = preferencesManager.customAiAvatarUri.collectAsState(initial = null).value
    val activeCardAvatarUri by remember(activeCharacterCard?.id) {
        activeCharacterCard?.id?.let { preferencesManager.getAiAvatarForCharacterCardFlow(it) }
            ?: flowOf(null)
    }.collectAsState(initial = null)
    val activeGroupAvatarUri by remember(activeCharacterGroup?.id) {
        activeCharacterGroup?.id?.let { preferencesManager.getAiAvatarForCharacterGroupFlow(it) }
            ?: flowOf(null)
    }.collectAsState(initial = null)
    val avatarShape = preferencesManager.avatarShape.collectAsState(initial = UserPreferencesManager.AVATAR_SHAPE_CIRCLE).value
    val avatarCornerRadius = preferencesManager.avatarCornerRadius.collectAsState(initial = 8f).value

    // Collect on color mode
    val onColorMode = preferencesManager.onColorMode.collectAsState(initial = UserPreferencesManager.ON_COLOR_MODE_AUTO).value

    // Collect recent colors
    val recentColors = preferencesManager.recentColorsFlow.collectAsState(initial = emptyList()).value



    var showSaveSuccessMessage by remember { mutableStateOf(false) }

    val activeThemeTargetName = activeCharacterGroup?.name ?: activeCharacterCard?.name
    val activeThemeTargetAvatarUri = activeGroupAvatarUri ?: activeCardAvatarUri
    val isGroupThemeTarget = activePrompt is ActivePrompt.CharacterGroup

    // 自动保存主题到当前角色目标（角色卡或群聊）的函数
    val saveThemeToActiveTarget: () -> Unit = {
        scope.launch {
            when (activePrompt) {
                is ActivePrompt.CharacterGroup -> {
                    activeCharacterGroup?.id?.let { preferencesManager.saveCurrentThemeToCharacterGroup(it) }
                }
                is ActivePrompt.CharacterCard -> {
                    activeCharacterCard?.id?.let { preferencesManager.saveCurrentThemeToCharacterCard(it) }
                }
            }
        }
    }

    // 包装的保存函数，会同时保存设置和当前角色目标主题
    fun saveThemeSettingsWithCharacterCard(saveAction: suspend () -> Unit) {
        scope.launch {
            saveAction()
            saveThemeToActiveTarget()
        }
    }

    // Default color definitions
    val defaultPrimaryColor = Color.Magenta.toArgb()
    val defaultSecondaryColor = Color.Blue.toArgb()
    val defaultNavigationDrawerBackgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    val defaultNavigationDrawerAccentColor = MaterialTheme.colorScheme.primary.toArgb()
    val defaultCursorUserBubbleColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val defaultBubbleUserBubbleColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val defaultBubbleAiBubbleColor = MaterialTheme.colorScheme.surface.toArgb()
    val defaultBubbleUserTextColor =
        getTextColorForBackground(Color(defaultBubbleUserBubbleColor)).toArgb()
    val defaultBubbleAiTextColor =
        getTextColorForBackground(Color(defaultBubbleAiBubbleColor)).toArgb()
    // Mutable state
    var themeModeInput by remember { mutableStateOf(themeMode) }
    var useSystemThemeInput by remember { mutableStateOf(useSystemTheme) }
    var primaryColorInput by remember { mutableStateOf(customPrimaryColor ?: defaultPrimaryColor) }
    var secondaryColorInput by remember {
        mutableStateOf(customSecondaryColor ?: defaultSecondaryColor)
    }
    var useCustomColorsInput by remember { mutableStateOf(useCustomColors) }

    // Background image state
    var useBackgroundImageInput by remember { mutableStateOf(useBackgroundImage) }
    var backgroundImageUriInput by remember { mutableStateOf(backgroundImageUri) }
    var backgroundImageOpacityInput by remember { mutableStateOf(backgroundImageOpacity) }

    // Background media type and video settings state
    var backgroundMediaTypeInput by remember { mutableStateOf(backgroundMediaType) }
    var videoBackgroundMutedInput by remember { mutableStateOf(videoBackgroundMuted) }
    var videoBackgroundLoopInput by remember { mutableStateOf(videoBackgroundLoop) }

    // Toolbar transparency state
    var toolbarTransparentInput by remember { mutableStateOf(toolbarTransparent) }

    // Navigation drawer appearance state
    var navigationDrawerWaterGlassInput by
        remember { mutableStateOf(navigationDrawerWaterGlass) }
    var navigationDrawerButtonLiquidGlassInput by
        remember { mutableStateOf(navigationDrawerButtonLiquidGlass) }
    var useCustomNavigationDrawerBackgroundColorInput by
        remember { mutableStateOf(useCustomNavigationDrawerBackgroundColor) }
    var navigationDrawerBackgroundColorInput by
        remember {
            mutableStateOf(customNavigationDrawerBackgroundColor ?: defaultNavigationDrawerBackgroundColor)
        }
    var useCustomNavigationDrawerAccentColorInput by
        remember { mutableStateOf(useCustomNavigationDrawerAccentColor) }
    var navigationDrawerAccentColorInput by
        remember {
            mutableStateOf(
                customNavigationDrawerAccentColor
                    ?: navigationDrawerAppearance.titleColor.toArgb(),
            )
        }

    // AppBar custom color state
    var useCustomAppBarColorInput by remember { mutableStateOf(useCustomAppBarColor) }
    var customAppBarColorInput by remember { mutableStateOf(customAppBarColor ?: defaultPrimaryColor) }

    // Status bar color state
    var useCustomStatusBarColorInput by remember { mutableStateOf(useCustomStatusBarColor) }
    var customStatusBarColorInput by remember { mutableStateOf(customStatusBarColor ?: defaultPrimaryColor) }
    var statusBarTransparentInput by remember { mutableStateOf(statusBarTransparent) }
    var statusBarHiddenInput by remember { mutableStateOf(statusBarHidden) }
    var chatHeaderTransparentInput by remember { mutableStateOf(chatHeaderTransparent) }
    var chatInputTransparentInput by remember { mutableStateOf(chatInputTransparent) }
    var chatInputFloatingInput by remember { mutableStateOf(chatInputFloating) }
    var chatInputLiquidGlassInput by remember { mutableStateOf(chatInputLiquidGlass) }
    var chatInputWaterGlassInput by remember { mutableStateOf(chatInputWaterGlass) }
    var chatHeaderOverlayModeInput by remember { mutableStateOf(chatHeaderOverlayMode) }

    // AppBar content color state
    var forceAppBarContentColorInput by remember { mutableStateOf(forceAppBarContentColor) }
    var appBarContentColorModeInput by remember { mutableStateOf(appBarContentColorMode) }

    // ChatHeader icon color state
    var chatHeaderHistoryIconColorInput by remember {
        mutableStateOf(chatHeaderHistoryIconColor ?: Color.Gray.toArgb())
    }
    var chatHeaderPipIconColorInput by remember {
        mutableStateOf(chatHeaderPipIconColor ?: Color.Gray.toArgb())
    }

    // Background blur state
    var useBackgroundBlurInput by remember { mutableStateOf(useBackgroundBlur) }
    var backgroundBlurRadiusInput by remember { mutableStateOf(backgroundBlurRadius) }

    // Chat style state
    var chatStyleInput by remember { mutableStateOf(chatStyle) }
    var inputStyleInput by remember { mutableStateOf(inputStyle) }

    var bubbleShowAvatarInput by remember { mutableStateOf(bubbleShowAvatar) }
    var bubbleWideLayoutEnabledInput by remember { mutableStateOf(bubbleWideLayoutEnabled) }
    var cursorUserBubbleFollowThemeInput by remember { mutableStateOf(cursorUserBubbleFollowTheme) }
    var cursorUserBubbleLiquidGlassInput by
        remember { mutableStateOf(cursorUserBubbleLiquidGlass) }
    var cursorUserBubbleWaterGlassInput by
        remember { mutableStateOf(cursorUserBubbleWaterGlass) }
    var bubbleUserBubbleLiquidGlassInput by
        remember { mutableStateOf(bubbleUserBubbleLiquidGlass) }
    var bubbleUserBubbleWaterGlassInput by
        remember { mutableStateOf(bubbleUserBubbleWaterGlass) }
    var bubbleAiBubbleLiquidGlassInput by
        remember { mutableStateOf(bubbleAiBubbleLiquidGlass) }
    var bubbleAiBubbleWaterGlassInput by
        remember { mutableStateOf(bubbleAiBubbleWaterGlass) }
    var cursorUserBubbleColorInput by
        remember { mutableStateOf(cursorUserBubbleColor ?: defaultCursorUserBubbleColor) }
    var bubbleUserBubbleColorInput by
        remember { mutableStateOf(bubbleUserBubbleColor ?: defaultBubbleUserBubbleColor) }
    var bubbleAiBubbleColorInput by
        remember { mutableStateOf(bubbleAiBubbleColor ?: defaultBubbleAiBubbleColor) }
    var bubbleUserTextColorInput by
        remember { mutableStateOf(bubbleUserTextColor ?: defaultBubbleUserTextColor) }
    var bubbleAiTextColorInput by
        remember { mutableStateOf(bubbleAiTextColor ?: defaultBubbleAiTextColor) }
    var bubbleUserTextColorCustomizedInput by
        remember { mutableStateOf(bubbleUserTextColor != null) }
    var bubbleAiTextColorCustomizedInput by
        remember { mutableStateOf(bubbleAiTextColor != null) }
    var bubbleUserUseCustomFontInput by remember { mutableStateOf(bubbleUserUseCustomFont) }
    var bubbleUserFontTypeInput by remember { mutableStateOf(bubbleUserFontType) }
    var bubbleUserSystemFontNameInput by remember { mutableStateOf(bubbleUserSystemFontName) }
    var bubbleUserCustomFontPathInput by remember { mutableStateOf(bubbleUserCustomFontPath) }
    var bubbleAiUseCustomFontInput by remember { mutableStateOf(bubbleAiUseCustomFont) }
    var bubbleAiFontTypeInput by remember { mutableStateOf(bubbleAiFontType) }
    var bubbleAiSystemFontNameInput by remember { mutableStateOf(bubbleAiSystemFontName) }
    var bubbleAiCustomFontPathInput by remember { mutableStateOf(bubbleAiCustomFontPath) }
    var bubbleUserUseImageInput by remember { mutableStateOf(bubbleUserUseImage) }
    var bubbleAiUseImageInput by remember { mutableStateOf(bubbleAiUseImage) }
    var bubbleUserImageUriInput by remember { mutableStateOf(bubbleUserImageUri) }
    var bubbleAiImageUriInput by remember { mutableStateOf(bubbleAiImageUri) }
    var bubbleUserImageCropLeftInput by remember { mutableStateOf(bubbleUserImageCropLeft) }
    var bubbleUserImageCropTopInput by remember { mutableStateOf(bubbleUserImageCropTop) }
    var bubbleUserImageCropRightInput by remember { mutableStateOf(bubbleUserImageCropRight) }
    var bubbleUserImageCropBottomInput by remember { mutableStateOf(bubbleUserImageCropBottom) }
    var bubbleUserImageRepeatStartInput by remember { mutableStateOf(bubbleUserImageRepeatStart) }
    var bubbleUserImageRepeatEndInput by remember { mutableStateOf(bubbleUserImageRepeatEnd) }
    var bubbleUserImageRepeatYStartInput by remember { mutableStateOf(bubbleUserImageRepeatYStart) }
    var bubbleUserImageRepeatYEndInput by remember { mutableStateOf(bubbleUserImageRepeatYEnd) }
    var bubbleUserImageScaleInput by remember { mutableStateOf(bubbleUserImageScale) }
    var bubbleAiImageCropLeftInput by remember { mutableStateOf(bubbleAiImageCropLeft) }
    var bubbleAiImageCropTopInput by remember { mutableStateOf(bubbleAiImageCropTop) }
    var bubbleAiImageCropRightInput by remember { mutableStateOf(bubbleAiImageCropRight) }
    var bubbleAiImageCropBottomInput by remember { mutableStateOf(bubbleAiImageCropBottom) }
    var bubbleAiImageRepeatStartInput by remember { mutableStateOf(bubbleAiImageRepeatStart) }
    var bubbleAiImageRepeatEndInput by remember { mutableStateOf(bubbleAiImageRepeatEnd) }
    var bubbleAiImageRepeatYStartInput by remember { mutableStateOf(bubbleAiImageRepeatYStart) }
    var bubbleAiImageRepeatYEndInput by remember { mutableStateOf(bubbleAiImageRepeatYEnd) }
    var bubbleAiImageScaleInput by remember { mutableStateOf(bubbleAiImageScale) }
    var bubbleImageRenderModeInput by remember { mutableStateOf(bubbleImageRenderMode) }
    var bubbleUserRoundedCornersEnabledInput by remember {
        mutableStateOf(bubbleUserRoundedCornersEnabled)
    }
    var bubbleAiRoundedCornersEnabledInput by remember {
        mutableStateOf(bubbleAiRoundedCornersEnabled)
    }
    var bubbleUserContentPaddingLeftInput by
        remember { mutableStateOf(bubbleUserContentPaddingLeft) }
    var bubbleUserContentPaddingRightInput by
        remember { mutableStateOf(bubbleUserContentPaddingRight) }
    var bubbleAiContentPaddingLeftInput by
        remember { mutableStateOf(bubbleAiContentPaddingLeft) }
    var bubbleAiContentPaddingRightInput by
        remember { mutableStateOf(bubbleAiContentPaddingRight) }

    // New display settings state
    var showThinkingProcessInput by remember { mutableStateOf(showThinkingProcess) }
    var showStatusTagsInput by remember { mutableStateOf(showStatusTags) }
    var showInputProcessingStatusInput by remember { mutableStateOf(showInputProcessingStatus) }
    var showChatFloatingDotsAnimationInput by remember {
        mutableStateOf(showChatFloatingDotsAnimation)
    }

    // Avatar state
    var userAvatarUriInput by remember { mutableStateOf(userAvatarUri) }
    var aiAvatarUriInput by remember { mutableStateOf(aiAvatarUri) }
    var avatarShapeInput by remember { mutableStateOf(avatarShape) }
    var avatarCornerRadiusInput by remember { mutableStateOf(avatarCornerRadius) }

    // 添加全局用户头像状态（已迁移到DisplayPreferencesManager）
    val globalUserAvatarUri = displayPreferencesManager.globalUserAvatarUri.collectAsState(initial = null).value
    var globalUserAvatarUriInput by remember { mutableStateOf(globalUserAvatarUri) }

    // 添加全局用户名称状态（已迁移到DisplayPreferencesManager）
    val globalUserName = displayPreferencesManager.globalUserName.collectAsState(initial = null).value
    var globalUserNameInput by remember { mutableStateOf(globalUserName) }

    // On color mode state
    var onColorModeInput by remember { mutableStateOf(onColorMode) }

    // 字体设置状态
    val useCustomFont = preferencesManager.useCustomFont.collectAsState(initial = false).value
    val fontType = preferencesManager.fontType.collectAsState(initial = UserPreferencesManager.FONT_TYPE_SYSTEM).value
    val systemFontName = preferencesManager.systemFontName.collectAsState(initial = UserPreferencesManager.SYSTEM_FONT_DEFAULT).value
    val customFontPath = preferencesManager.customFontPath.collectAsState(initial = null).value
    val fontScale = preferencesManager.fontScale.collectAsState(initial = 1.0f).value

    var useCustomFontInput by remember { mutableStateOf(useCustomFont) }
    var fontTypeInput by remember { mutableStateOf(fontType) }
    var systemFontNameInput by remember { mutableStateOf(systemFontName) }
    var customFontPathInput by remember { mutableStateOf(customFontPath) }
    var fontScaleInput by remember { mutableStateOf(fontScale) }

    var showColorPicker by remember { mutableStateOf(false) }
    var currentColorPickerMode by remember { mutableStateOf("primary") }

    // Video player state
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
                // Add stricter memory limits
                .setLoadControl(
                        DefaultLoadControl.Builder()
                                .setBufferDurationsMs(
                                        5000, // Minimum buffer time reduced to 5 seconds
                                        10000, // Maximum buffer time reduced to 10 seconds
                                        500, // Minimum buffer for playback
                                        1000 // Minimum buffer for playback after rebuffering
                                )
                                .setTargetBufferBytes(5 * 1024 * 1024) // Limit buffer to 5MB
                                .setPrioritizeTimeOverSizeThresholds(true)
                                .build()
                )
                .build()
                .apply {
                    // Set loop playback
                    repeatMode = Player.REPEAT_MODE_ALL
                    // Set mute
                    volume = if (videoBackgroundMutedInput) 0f else 1f
                    playWhenReady = true

                    // If there's a background video URI, load it
                    if (!backgroundImageUriInput.isNullOrEmpty() &&
                                    backgroundMediaTypeInput ==
                                            UserPreferencesManager.MEDIA_TYPE_VIDEO
                    ) {
                        try {
                            val mediaItem = MediaItem.Builder()
                                .setUri(Uri.parse(backgroundImageUriInput))
                                .build()
                            setMediaItem(mediaItem)
                            prepare()
                        } catch (e: Exception) {
                            AppLogger.e("ThemeSettings", "Video loading error", e)
                        }
                    }
                }
    }

    // Free ExoPlayer resources when component is destroyed
    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            } catch (e: Exception) {
                AppLogger.e("ThemeSettings", "ExoPlayer release error", e)
            }
        }
    }

    // Handle video URI changes
    LaunchedEffect(backgroundImageUriInput, backgroundMediaTypeInput) {
        if (!backgroundImageUriInput.isNullOrEmpty() &&
                        backgroundMediaTypeInput == UserPreferencesManager.MEDIA_TYPE_VIDEO
        ) {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.setMediaItem(MediaItem.Builder()
                    .setUri(Uri.parse(backgroundImageUriInput))
                    .build())
                exoPlayer.prepare()
                exoPlayer.play()
            } catch (e: Exception) {
                AppLogger.e("ThemeSettings", "更新视频来源错误", e)
            }
        }
    }

    // Handle video settings changes - add error handling
    LaunchedEffect(videoBackgroundMutedInput, videoBackgroundLoopInput) {
        try {
            exoPlayer.volume = if (videoBackgroundMutedInput) 0f else 1f
            exoPlayer.repeatMode =
                    if (videoBackgroundLoopInput) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        } catch (e: Exception) {
            AppLogger.e("ThemeSettings", "更新视频设置错误", e)
        }
    }

    // Image crop launcher
    val cropImageLauncher =
            rememberLauncherForActivityResult(CropImageContract()) { result ->
                if (result.isSuccessful) {
                    val croppedUri = result.uriContent
                    if (croppedUri != null) {
                        scope.launch {
                            val internalUri =
                                    FileUtils.copyFileToInternalStorage(context, croppedUri, "background")
                            if (internalUri != null) {
                                AppLogger.d("ThemeSettings", "Background image saved to: $internalUri")
                                backgroundImageUriInput = internalUri.toString()
                                backgroundMediaTypeInput = UserPreferencesManager.MEDIA_TYPE_IMAGE
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(
                                        backgroundImageUri = internalUri.toString(),
                                        backgroundMediaType =
                                                UserPreferencesManager.MEDIA_TYPE_IMAGE
                                    )
                                }
                                showSaveSuccessMessage = true
                                Toast.makeText(
                                                context,
                                                context.getString(R.string.theme_image_saved),
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                            } else {
                                Toast.makeText(
                                                context,
                                                context.getString(R.string.theme_copy_failed),
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                            }
                        }
                    }
                } else if (result.error != null) {
                    Toast.makeText(
                                    context,
                                    context.getString(
                                            R.string.theme_image_crop_failed,
                                            result.error!!.message
                                    ),
                                    Toast.LENGTH_LONG
                            )
                            .show()
                }
            }

    // Launch image crop function
    fun launchImageCrop(uri: Uri) {
        // Use safe way to get system colors
        var primaryColor: Int
        var onPrimaryColor: Int
        var surfaceColor: Int
        var statusBarColor: Int

        // Check system dark theme
        val isNightMode =
                context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES

        try {
            // Try to use theme colors - this is a fallback option
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
            primaryColor = typedValue.data

            // Try to get system status bar color (API 23+)
            try {
                context.theme.resolveAttribute(android.R.attr.colorPrimaryDark, typedValue, true)
                statusBarColor = typedValue.data
            } catch (e: Exception) {
                // If unable to get, use theme color
                statusBarColor = primaryColor
            }

            context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            surfaceColor = typedValue.data

            onPrimaryColor =
                    if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        } catch (e: Exception) {
            // Use fallback colors
            primaryColor = if (isNightMode) 0xFF9C27B0.toInt() else 0xFF6200EE.toInt() // Purple
            statusBarColor =
                    if (isNightMode) 0xFF7B1FA2.toInt() else 0xFF3700B3.toInt() // Dark purple
            surfaceColor =
                    if (isNightMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            onPrimaryColor =
                    if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        }

        val cropOptions =
                CropImageContractOptions(
                        uri,
                        CropImageOptions().apply {
                            guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
                            outputCompressFormat = android.graphics.Bitmap.CompressFormat.JPEG
                            outputCompressQuality = 90
                            fixAspectRatio = false
                            cropMenuCropButtonTitle = context.getString(R.string.theme_crop_done)
                            activityTitle = context.getString(R.string.theme_crop_image)

                            // Set theme colors
                            toolbarColor = primaryColor
                            toolbarBackButtonColor = onPrimaryColor
                            toolbarTitleColor = onPrimaryColor
                            activityBackgroundColor = surfaceColor
                            backgroundColor = surfaceColor

                            // Status bar color
                            statusBarColor = statusBarColor

                            // Use light/dark theme
                            activityMenuIconColor = onPrimaryColor

                            // Improve user experience
                            showCropOverlay = true
                            showProgressBar = true
                            multiTouchEnabled = true
                            autoZoomEnabled = true
                        }
                )
        cropImageLauncher.launch(cropOptions)
    }

    // Image/video picker launcher
    val mediaPickerLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
                    uri: Uri? ->
                if (uri != null) {
                    // Check if it's a video file
                    val isVideo = FileUtils.isVideoFile(context, uri)

                    if (isVideo) {
                        // Video file check size
                        val isVideoSizeAcceptable =
                                FileUtils.checkVideoSize(context, uri, 30) // Limit to 30MB

                        if (!isVideoSizeAcceptable) {
                            // Video too large, show warning
                            Toast.makeText(
                                            context,
                                            context.getString(R.string.theme_video_too_large),
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                            return@rememberLauncherForActivityResult
                        }

                        // Video file size acceptable, directly save
                        scope.launch {
                            val internalUri = FileUtils.copyFileToInternalStorage(context, uri, "background_video")

                            if (internalUri != null) {
                                AppLogger.d("ThemeSettings", "Background video saved to: $internalUri")
                                backgroundImageUriInput = internalUri.toString()
                                backgroundMediaTypeInput = UserPreferencesManager.MEDIA_TYPE_VIDEO
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(
                                        backgroundImageUri = internalUri.toString(),
                                        backgroundMediaType =
                                                UserPreferencesManager.MEDIA_TYPE_VIDEO
                                    )
                                }
                                showSaveSuccessMessage = true
                                Toast.makeText(
                                                context,
                                                context.getString(R.string.theme_video_saved),
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                            } else {
                                Toast.makeText(
                                                context,
                                                context.getString(R.string.theme_copy_failed),
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                            }
                        }
                    } else {
                        // Image file first launch crop
                        launchImageCrop(uri)
                    }
                }
            }

    var bubbleImagePickerTarget by remember { mutableStateOf("user") }
    val bubbleImageCropLauncher =
        rememberLauncherForActivityResult(CropImageContract()) { result ->
            if (result.isSuccessful) {
                val croppedUri = result.uriContent
                if (croppedUri != null) {
                    scope.launch {
                        val uniqueName =
                            if (bubbleImagePickerTarget == "ai") {
                                "bubble_ai"
                            } else {
                                "bubble_user"
                            }
                        val internalUri =
                            FileUtils.copyFileToInternalStorage(context, croppedUri, uniqueName)
                        if (internalUri != null) {
                            if (bubbleImagePickerTarget == "ai") {
                                bubbleAiImageUriInput = internalUri.toString()
                                bubbleAiUseImageInput =
                                    !bubbleAiBubbleLiquidGlassInput &&
                                        !bubbleAiBubbleWaterGlassInput
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(
                                        bubbleAiImageUri = internalUri.toString(),
                                        bubbleAiUseImage =
                                            !bubbleAiBubbleLiquidGlassInput &&
                                                !bubbleAiBubbleWaterGlassInput,
                                    )
                                }
                            } else {
                                bubbleUserImageUriInput = internalUri.toString()
                                bubbleUserUseImageInput =
                                    !bubbleUserBubbleLiquidGlassInput &&
                                        !bubbleUserBubbleWaterGlassInput
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(
                                        bubbleUserImageUri = internalUri.toString(),
                                        bubbleUserUseImage =
                                            !bubbleUserBubbleLiquidGlassInput &&
                                                !bubbleUserBubbleWaterGlassInput,
                                    )
                                }
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.chat_style_bubble_image_saved),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.theme_copy_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } else if (result.error != null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.theme_image_crop_failed, result.error!!.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    fun launchBubbleImageCrop(uri: Uri) {
        val cropOptions =
            CropImageContractOptions(
                uri,
                CropImageOptions().apply {
                    guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
                    // Bubble images need alpha channel, so use PNG to preserve transparency.
                    outputCompressFormat = android.graphics.Bitmap.CompressFormat.PNG
                    outputCompressQuality = 90
                    fixAspectRatio = false
                    cropMenuCropButtonTitle = context.getString(R.string.theme_crop_done)
                    activityTitle = context.getString(R.string.theme_crop_image)
                    showCropOverlay = true
                    showProgressBar = true
                    multiTouchEnabled = true
                    autoZoomEnabled = true
                },
            )
        bubbleImageCropLauncher.launch(cropOptions)
    }

    val bubbleImagePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                if (!isNinePatchPngUri(context, uri)) {
                    launchBubbleImageCrop(uri)
                    return@rememberLauncherForActivityResult
                }

                scope.launch {
                    val uniqueName =
                        if (bubbleImagePickerTarget == "ai") {
                            "bubble_ai"
                        } else {
                            "bubble_user"
                        }
                    val internalUri =
                        FileUtils.copyFileToInternalStorage(context, uri, uniqueName)
                    if (internalUri == null) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.theme_copy_failed),
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }

                    val autoParams =
                        parseNinePatchBubbleParams(context, uri)
                            ?: parseNinePatchBubbleParams(context, internalUri)
                    if (autoParams == null) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.theme_copy_failed),
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    val internalUriString = internalUri.toString()
                    bubbleImageRenderModeInput =
                        UserPreferencesManager.BUBBLE_IMAGE_RENDER_MODE_NINE_PATCH

                    if (bubbleImagePickerTarget == "ai") {
                        bubbleAiImageUriInput = internalUriString
                        bubbleAiUseImageInput =
                            !bubbleAiBubbleLiquidGlassInput &&
                                !bubbleAiBubbleWaterGlassInput
                        bubbleAiImageCropLeftInput = autoParams.cropLeftRatio
                        bubbleAiImageCropTopInput = autoParams.cropTopRatio
                        bubbleAiImageCropRightInput = autoParams.cropRightRatio
                        bubbleAiImageCropBottomInput = autoParams.cropBottomRatio
                        bubbleAiImageRepeatStartInput = autoParams.repeatXStartRatio
                        bubbleAiImageRepeatEndInput = autoParams.repeatXEndRatio
                        bubbleAiImageRepeatYStartInput = autoParams.repeatYStartRatio
                        bubbleAiImageRepeatYEndInput = autoParams.repeatYEndRatio
                        bubbleAiImageScaleInput = 1f
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(
                                bubbleAiImageUri = internalUriString,
                                bubbleAiUseImage =
                                    !bubbleAiBubbleLiquidGlassInput &&
                                        !bubbleAiBubbleWaterGlassInput,
                                bubbleAiImageCropLeft = autoParams.cropLeftRatio,
                                bubbleAiImageCropTop = autoParams.cropTopRatio,
                                bubbleAiImageCropRight = autoParams.cropRightRatio,
                                bubbleAiImageCropBottom = autoParams.cropBottomRatio,
                                bubbleAiImageRepeatStart = autoParams.repeatXStartRatio,
                                bubbleAiImageRepeatEnd = autoParams.repeatXEndRatio,
                                bubbleAiImageRepeatYStart = autoParams.repeatYStartRatio,
                                bubbleAiImageRepeatYEnd = autoParams.repeatYEndRatio,
                                bubbleAiImageScale = 1f,
                                bubbleImageRenderMode =
                                    UserPreferencesManager.BUBBLE_IMAGE_RENDER_MODE_NINE_PATCH,
                            )
                        }
                    } else {
                        bubbleUserImageUriInput = internalUriString
                        bubbleUserUseImageInput =
                            !bubbleUserBubbleLiquidGlassInput &&
                                !bubbleUserBubbleWaterGlassInput
                        bubbleUserImageCropLeftInput = autoParams.cropLeftRatio
                        bubbleUserImageCropTopInput = autoParams.cropTopRatio
                        bubbleUserImageCropRightInput = autoParams.cropRightRatio
                        bubbleUserImageCropBottomInput = autoParams.cropBottomRatio
                        bubbleUserImageRepeatStartInput = autoParams.repeatXStartRatio
                        bubbleUserImageRepeatEndInput = autoParams.repeatXEndRatio
                        bubbleUserImageRepeatYStartInput = autoParams.repeatYStartRatio
                        bubbleUserImageRepeatYEndInput = autoParams.repeatYEndRatio
                        bubbleUserImageScaleInput = 1f
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(
                                bubbleUserImageUri = internalUriString,
                                bubbleUserUseImage =
                                    !bubbleUserBubbleLiquidGlassInput &&
                                        !bubbleUserBubbleWaterGlassInput,
                                bubbleUserImageCropLeft = autoParams.cropLeftRatio,
                                bubbleUserImageCropTop = autoParams.cropTopRatio,
                                bubbleUserImageCropRight = autoParams.cropRightRatio,
                                bubbleUserImageCropBottom = autoParams.cropBottomRatio,
                                bubbleUserImageRepeatStart = autoParams.repeatXStartRatio,
                                bubbleUserImageRepeatEnd = autoParams.repeatXEndRatio,
                                bubbleUserImageRepeatYStart = autoParams.repeatYStartRatio,
                                bubbleUserImageRepeatYEnd = autoParams.repeatYEndRatio,
                                bubbleUserImageScale = 1f,
                                bubbleImageRenderMode =
                                    UserPreferencesManager.BUBBLE_IMAGE_RENDER_MODE_NINE_PATCH,
                            )
                        }
                    }

                    Toast.makeText(
                        context,
                        context.getString(R.string.chat_style_bubble_image_saved),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    // Migrate existing background image if needed (on first load)
    LaunchedEffect(Unit) {
        // Check if we have a background image URI that starts with content://
        backgroundImageUri?.let { uriString ->
            if (uriString.startsWith("content://")) {
                try {
                    // Try to copy to internal storage
                    val uri = Uri.parse(uriString)
                    scope.launch {
                        val internalUri = FileUtils.copyFileToInternalStorage(context, uri, "migrated_background")
                        if (internalUri != null) {
                            AppLogger.d("ThemeSettings", "Migrated background image to: $internalUri")
                            // Update the URI in preferences
                            preferencesManager.saveThemeSettings(
                                    backgroundImageUri = internalUri.toString()
                            )
                            // Update the local state
                            backgroundImageUriInput = internalUri.toString()
                            Toast.makeText(context, context.getString(R.string.background_image_migrated), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("ThemeSettings", "Error migrating background image", e)
                    // If migration fails, disable background image to prevent
                    // crashes
                    scope.launch {
                        preferencesManager.saveThemeSettings(useBackgroundImage = false)
                        useBackgroundImageInput = false
                        Toast.makeText(context, context.getString(R.string.background_image_access_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // When settings change, update local state
    LaunchedEffect(
            themeMode,
            useSystemTheme,
            customPrimaryColor,
            customSecondaryColor,
            useCustomColors,
            useBackgroundImage,
            backgroundImageUri,
            backgroundImageOpacity,
            backgroundMediaType,
            videoBackgroundMuted,
            videoBackgroundLoop,
            toolbarTransparent,
            navigationDrawerWaterGlass,
            navigationDrawerButtonLiquidGlass,
            useCustomNavigationDrawerBackgroundColor,
            customNavigationDrawerBackgroundColor,
            useCustomNavigationDrawerAccentColor,
            customNavigationDrawerAccentColor,
            navigationDrawerAppearance.titleColor,
            useCustomStatusBarColor,
            customStatusBarColor,
            statusBarTransparent,
            statusBarHidden,
            chatHeaderTransparent,
            chatInputTransparent,
            chatInputFloating,
            chatInputLiquidGlass,
            chatInputWaterGlass,
            chatHeaderOverlayMode,
            forceAppBarContentColor,
            appBarContentColorMode,
            chatHeaderHistoryIconColor,
            chatHeaderPipIconColor,
            useBackgroundBlur,
            backgroundBlurRadius,
            chatStyle,
            inputStyle,
            bubbleShowAvatar,
            bubbleWideLayoutEnabled,
            cursorUserBubbleFollowTheme,
            cursorUserBubbleLiquidGlass,
            cursorUserBubbleWaterGlass,
            bubbleUserBubbleLiquidGlass,
            bubbleUserBubbleWaterGlass,
            bubbleAiBubbleLiquidGlass,
            bubbleAiBubbleWaterGlass,
            cursorUserBubbleColor,
            bubbleUserBubbleColor,
            bubbleAiBubbleColor,
            bubbleUserTextColor,
            bubbleAiTextColor,
            bubbleUserUseCustomFont,
            bubbleUserFontType,
            bubbleUserSystemFontName,
            bubbleUserCustomFontPath,
            bubbleAiUseCustomFont,
            bubbleAiFontType,
            bubbleAiSystemFontName,
            bubbleAiCustomFontPath,
            bubbleUserUseImage,
            bubbleAiUseImage,
            bubbleUserImageUri,
            bubbleAiImageUri,
            bubbleUserImageCropLeft,
            bubbleUserImageCropTop,
            bubbleUserImageCropRight,
            bubbleUserImageCropBottom,
            bubbleUserImageRepeatStart,
            bubbleUserImageRepeatEnd,
            bubbleUserImageRepeatYStart,
            bubbleUserImageRepeatYEnd,
            bubbleUserImageScale,
            bubbleAiImageCropLeft,
            bubbleAiImageCropTop,
            bubbleAiImageCropRight,
            bubbleAiImageCropBottom,
            bubbleAiImageRepeatStart,
            bubbleAiImageRepeatEnd,
            bubbleAiImageRepeatYStart,
            bubbleAiImageRepeatYEnd,
            bubbleAiImageScale,
            bubbleImageRenderMode,
            bubbleUserRoundedCornersEnabled,
            bubbleAiRoundedCornersEnabled,
            bubbleUserContentPaddingLeft,
            bubbleUserContentPaddingRight,
            bubbleAiContentPaddingLeft,
            bubbleAiContentPaddingRight,
            showThinkingProcess,
            showStatusTags,
            showInputProcessingStatus,
            userAvatarUri,
            aiAvatarUri,
            avatarShape,
            avatarCornerRadius,
            onColorMode,
            globalUserAvatarUri,
            globalUserName,
            useCustomFont,
            fontType,
            systemFontName,
            customFontPath,
            fontScale
    ) {
        themeModeInput = themeMode
        useSystemThemeInput = useSystemTheme
        if (customPrimaryColor != null) primaryColorInput = customPrimaryColor
        if (customSecondaryColor != null) secondaryColorInput = customSecondaryColor
        useCustomColorsInput = useCustomColors
        useBackgroundImageInput = useBackgroundImage
        backgroundImageUriInput = backgroundImageUri
        backgroundImageOpacityInput = backgroundImageOpacity
        backgroundMediaTypeInput = backgroundMediaType
        videoBackgroundMutedInput = videoBackgroundMuted
        videoBackgroundLoopInput = videoBackgroundLoop
        toolbarTransparentInput = toolbarTransparent
        navigationDrawerWaterGlassInput = navigationDrawerWaterGlass
        navigationDrawerButtonLiquidGlassInput = navigationDrawerButtonLiquidGlass
        useCustomNavigationDrawerBackgroundColorInput = useCustomNavigationDrawerBackgroundColor
        navigationDrawerBackgroundColorInput =
            customNavigationDrawerBackgroundColor ?: defaultNavigationDrawerBackgroundColor
        useCustomNavigationDrawerAccentColorInput = useCustomNavigationDrawerAccentColor
        navigationDrawerAccentColorInput =
            customNavigationDrawerAccentColor ?: navigationDrawerAppearance.titleColor.toArgb()
        useCustomStatusBarColorInput = useCustomStatusBarColor
        if (customStatusBarColor != null) customStatusBarColorInput = customStatusBarColor
        statusBarTransparentInput = statusBarTransparent
        statusBarHiddenInput = statusBarHidden
        chatHeaderTransparentInput = chatHeaderTransparent
        chatInputTransparentInput = chatInputTransparent
        chatInputFloatingInput = chatInputFloating
        chatInputLiquidGlassInput = chatInputLiquidGlass
        chatInputWaterGlassInput = chatInputWaterGlass
        chatHeaderOverlayModeInput = chatHeaderOverlayMode
        forceAppBarContentColorInput = forceAppBarContentColor
        appBarContentColorModeInput = appBarContentColorMode
        if (chatHeaderHistoryIconColor != null) {
            chatHeaderHistoryIconColorInput = chatHeaderHistoryIconColor
        }
        if (chatHeaderPipIconColor != null) {
            chatHeaderPipIconColorInput = chatHeaderPipIconColor
        }
        useBackgroundBlurInput = useBackgroundBlur
        backgroundBlurRadiusInput = backgroundBlurRadius
        chatStyleInput = chatStyle
        inputStyleInput = inputStyle
        bubbleShowAvatarInput = bubbleShowAvatar
        bubbleWideLayoutEnabledInput = bubbleWideLayoutEnabled
        cursorUserBubbleFollowThemeInput = cursorUserBubbleFollowTheme
        cursorUserBubbleLiquidGlassInput = cursorUserBubbleLiquidGlass
        cursorUserBubbleWaterGlassInput = cursorUserBubbleWaterGlass
        bubbleUserBubbleLiquidGlassInput = bubbleUserBubbleLiquidGlass
        bubbleUserBubbleWaterGlassInput = bubbleUserBubbleWaterGlass
        bubbleAiBubbleLiquidGlassInput = bubbleAiBubbleLiquidGlass
        bubbleAiBubbleWaterGlassInput = bubbleAiBubbleWaterGlass
        cursorUserBubbleColorInput = cursorUserBubbleColor ?: defaultCursorUserBubbleColor
        bubbleUserBubbleColorInput = bubbleUserBubbleColor ?: defaultBubbleUserBubbleColor
        bubbleAiBubbleColorInput = bubbleAiBubbleColor ?: defaultBubbleAiBubbleColor
        bubbleUserTextColorCustomizedInput = bubbleUserTextColor != null
        bubbleAiTextColorCustomizedInput = bubbleAiTextColor != null
        bubbleUserTextColorInput =
            bubbleUserTextColor
                ?: getTextColorForBackground(Color(bubbleUserBubbleColorInput)).toArgb()
        bubbleAiTextColorInput =
            bubbleAiTextColor
                ?: getTextColorForBackground(Color(bubbleAiBubbleColorInput)).toArgb()
        bubbleUserUseCustomFontInput = bubbleUserUseCustomFont
        bubbleUserFontTypeInput = bubbleUserFontType
        bubbleUserSystemFontNameInput = bubbleUserSystemFontName
        bubbleUserCustomFontPathInput = bubbleUserCustomFontPath
        bubbleAiUseCustomFontInput = bubbleAiUseCustomFont
        bubbleAiFontTypeInput = bubbleAiFontType
        bubbleAiSystemFontNameInput = bubbleAiSystemFontName
        bubbleAiCustomFontPathInput = bubbleAiCustomFontPath
        bubbleUserUseImageInput = bubbleUserUseImage
        bubbleAiUseImageInput = bubbleAiUseImage
        bubbleUserImageUriInput = bubbleUserImageUri
        bubbleAiImageUriInput = bubbleAiImageUri
        bubbleUserImageCropLeftInput = bubbleUserImageCropLeft
        bubbleUserImageCropTopInput = bubbleUserImageCropTop
        bubbleUserImageCropRightInput = bubbleUserImageCropRight
        bubbleUserImageCropBottomInput = bubbleUserImageCropBottom
        bubbleUserImageRepeatStartInput = bubbleUserImageRepeatStart
        bubbleUserImageRepeatEndInput = bubbleUserImageRepeatEnd
        bubbleUserImageRepeatYStartInput = bubbleUserImageRepeatYStart
        bubbleUserImageRepeatYEndInput = bubbleUserImageRepeatYEnd
        bubbleUserImageScaleInput = bubbleUserImageScale
        bubbleAiImageCropLeftInput = bubbleAiImageCropLeft
        bubbleAiImageCropTopInput = bubbleAiImageCropTop
        bubbleAiImageCropRightInput = bubbleAiImageCropRight
        bubbleAiImageCropBottomInput = bubbleAiImageCropBottom
        bubbleAiImageRepeatStartInput = bubbleAiImageRepeatStart
        bubbleAiImageRepeatEndInput = bubbleAiImageRepeatEnd
        bubbleAiImageRepeatYStartInput = bubbleAiImageRepeatYStart
        bubbleAiImageRepeatYEndInput = bubbleAiImageRepeatYEnd
        bubbleAiImageScaleInput = bubbleAiImageScale
        bubbleImageRenderModeInput = bubbleImageRenderMode
        bubbleUserRoundedCornersEnabledInput = bubbleUserRoundedCornersEnabled
        bubbleAiRoundedCornersEnabledInput = bubbleAiRoundedCornersEnabled
        bubbleUserContentPaddingLeftInput = bubbleUserContentPaddingLeft
        bubbleUserContentPaddingRightInput = bubbleUserContentPaddingRight
        bubbleAiContentPaddingLeftInput = bubbleAiContentPaddingLeft
        bubbleAiContentPaddingRightInput = bubbleAiContentPaddingRight
        showThinkingProcessInput = showThinkingProcess
        showStatusTagsInput = showStatusTags
        showInputProcessingStatusInput = showInputProcessingStatus
        userAvatarUriInput = userAvatarUri
        aiAvatarUriInput = aiAvatarUri
        avatarShapeInput = avatarShape
        avatarCornerRadiusInput = avatarCornerRadius
        onColorModeInput = onColorMode
        globalUserAvatarUriInput = globalUserAvatarUri
        globalUserNameInput = globalUserName
        useCustomFontInput = useCustomFont
        fontTypeInput = fontType
        systemFontNameInput = systemFontName
        customFontPathInput = customFontPath
        fontScaleInput = fontScale
    }

    val effectiveBubbleUserTextColorInput =
        if (bubbleUserTextColorCustomizedInput) {
            bubbleUserTextColorInput
        } else {
            getTextColorForBackground(Color(bubbleUserBubbleColorInput)).toArgb()
        }
    val effectiveBubbleAiTextColorInput =
        if (bubbleAiTextColorCustomizedInput) {
            bubbleAiTextColorInput
        } else {
            getTextColorForBackground(Color(bubbleAiBubbleColorInput)).toArgb()
        }

    var fontPickerMode by remember { mutableStateOf("global") }

    // 字体文件选择器 launcher
    val fontPickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    // 获取文件扩展名进行验证
                    val extension = FileUtils.getFileExtension(context, uri)?.lowercase()
                    
                    // 检查是否是支持的字体文件格式
                    if (extension != null && (extension == "ttf" || extension == "otf" || extension == "ttc")) {
                        // 复制字体文件到内部存储
                        val targetName =
                            when (fontPickerMode) {
                                "bubbleUser" -> "bubble_user_font"
                                "bubbleAi" -> "bubble_ai_font"
                                else -> "custom_font"
                            }
                        val internalUri = FileUtils.copyFileToInternalStorage(context, uri, targetName)
                        if (internalUri != null) {
                            AppLogger.d("ThemeSettings", "Font file saved to: $internalUri")
                            when (fontPickerMode) {
                                "bubbleUser" -> {
                                    bubbleUserCustomFontPathInput = internalUri.toString()
                                    bubbleUserFontTypeInput = UserPreferencesManager.FONT_TYPE_FILE
                                    saveThemeSettingsWithCharacterCard {
                                        preferencesManager.saveThemeSettings(
                                            bubbleUserCustomFontPath = internalUri.toString(),
                                            bubbleUserFontType = UserPreferencesManager.FONT_TYPE_FILE,
                                        )
                                    }
                                }
                                "bubbleAi" -> {
                                    bubbleAiCustomFontPathInput = internalUri.toString()
                                    bubbleAiFontTypeInput = UserPreferencesManager.FONT_TYPE_FILE
                                    saveThemeSettingsWithCharacterCard {
                                        preferencesManager.saveThemeSettings(
                                            bubbleAiCustomFontPath = internalUri.toString(),
                                            bubbleAiFontType = UserPreferencesManager.FONT_TYPE_FILE,
                                        )
                                    }
                                }
                                else -> {
                                    customFontPathInput = internalUri.toString()
                                    fontTypeInput = UserPreferencesManager.FONT_TYPE_FILE
                                    saveThemeSettingsWithCharacterCard {
                                        preferencesManager.saveThemeSettings(
                                            customFontPath = internalUri.toString(),
                                            fontType = UserPreferencesManager.FONT_TYPE_FILE,
                                        )
                                    }
                                }
                            }
                            Toast.makeText(context, context.getString(R.string.font_file_saved, extension), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.font_file_save_failed), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        // 不是支持的字体格式
                        Toast.makeText(
                            context, 
                            context.getString(R.string.unsupported_font_format),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

    // Avatar picker and cropper launcher
    var avatarPickerMode by remember { mutableStateOf("user") }

    val cropAvatarLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val croppedUri = result.uriContent
            if (croppedUri != null) {
                scope.launch {
                    val uniqueName = when (avatarPickerMode) {
                        "user" -> "user_avatar"
                        "ai" -> "ai_avatar"
                        "global_user" -> "global_user_avatar"
                        else -> "user_avatar"
                    }
                    val internalUri = FileUtils.copyFileToInternalStorage(context, croppedUri, uniqueName)
                    if (internalUri != null) {
                        when (avatarPickerMode) {
                            "user" -> {
                                AppLogger.d("ThemeSettings", "User avatar saved to: $internalUri")
                                userAvatarUriInput = internalUri.toString()
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(customUserAvatarUri = internalUri.toString())
                                }
                            }
                            "ai" -> {
                                AppLogger.d("ThemeSettings", "AI avatar saved to: $internalUri")
                                aiAvatarUriInput = internalUri.toString()
                                preferencesManager.saveThemeSettings(customAiAvatarUri = internalUri.toString())
                            }
                            "global_user" -> {
                                AppLogger.d("ThemeSettings", "Global user avatar saved to: $internalUri")
                                globalUserAvatarUriInput = internalUri.toString()
                                displayPreferencesManager.saveDisplaySettings(globalUserAvatarUri = internalUri.toString())
                            }
                        }
                        Toast.makeText(context, context.getString(R.string.avatar_updated), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.theme_copy_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else if (result.error != null) {
            Toast.makeText(context, context.getString(R.string.avatar_crop_failed, result.error!!.message), Toast.LENGTH_LONG).show()
        }
    }

    fun launchAvatarCrop(uri: Uri) {
        val cropOptions = CropImageContractOptions(
            uri,
            CropImageOptions().apply {
                guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
                outputCompressFormat = android.graphics.Bitmap.CompressFormat.PNG
                outputCompressQuality = 90
                fixAspectRatio = true
                aspectRatioX = 1
                aspectRatioY = 1
                cropMenuCropButtonTitle = context.getString(R.string.theme_crop_done)
                activityTitle = context.getString(R.string.crop_avatar)
                // Basic theming, can be expanded later
                toolbarColor = Color.Gray.toArgb()
                toolbarTitleColor = Color.White.toArgb()
            }
        )
        cropAvatarLauncher.launch(cropOptions)
    }

    val avatarImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            launchAvatarCrop(uri)
        }
    }


    // Get background image state to check if we need opaque cards
    val hasBackgroundImage =
            preferencesManager.useBackgroundImage.collectAsState(initial = false).value

    // Color surface modifier based on whether background image is used
    val cardModifier =
            if (hasBackgroundImage) {
                // Make cards fully opaque when background image is used
                CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f)
                )
            } else {
                CardDefaults.cardColors()
            }

    // Add a scroll state that we can control
    val scrollState = rememberScrollState()

    suspend fun resetThemeSettingsAndUi() {
        preferencesManager.resetThemeSettings()
        if (isGroupThemeTarget) {
            activeCharacterGroup?.id?.let { preferencesManager.deleteCharacterGroupTheme(it) }
        } else {
            activeCharacterCard?.id?.let { preferencesManager.deleteCharacterCardTheme(it) }
        }

        themeModeInput = UserPreferencesManager.THEME_MODE_LIGHT
        useSystemThemeInput = true
        primaryColorInput = defaultPrimaryColor
        secondaryColorInput = defaultSecondaryColor
        useCustomColorsInput = false
        useBackgroundImageInput = false
        backgroundImageUriInput = null
        backgroundImageOpacityInput = 0.3f
        backgroundMediaTypeInput = UserPreferencesManager.MEDIA_TYPE_IMAGE
        videoBackgroundMutedInput = true
        videoBackgroundLoopInput = true
        toolbarTransparentInput = false
        navigationDrawerWaterGlassInput = false
        navigationDrawerButtonLiquidGlassInput = false
        useCustomNavigationDrawerBackgroundColorInput = false
        navigationDrawerBackgroundColorInput = defaultNavigationDrawerBackgroundColor
        useCustomNavigationDrawerAccentColorInput = false
        navigationDrawerAccentColorInput = defaultNavigationDrawerAccentColor
        useCustomAppBarColorInput = false
        customAppBarColorInput = defaultPrimaryColor
        useCustomStatusBarColorInput = false
        customStatusBarColorInput = defaultPrimaryColor
        statusBarTransparentInput = false
        statusBarHiddenInput = false
        chatHeaderTransparentInput = false
        chatInputTransparentInput = false
        chatInputFloatingInput = false
        chatInputLiquidGlassInput = false
        chatInputWaterGlassInput = false
        chatHeaderOverlayModeInput = false
        forceAppBarContentColorInput = false
        appBarContentColorModeInput = UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_LIGHT
        chatHeaderHistoryIconColorInput = Color.Gray.toArgb()
        chatHeaderPipIconColorInput = Color.Gray.toArgb()
        useBackgroundBlurInput = false
        backgroundBlurRadiusInput = 10f
        chatStyleInput = UserPreferencesManager.CHAT_STYLE_CURSOR
        inputStyleInput = UserPreferencesManager.INPUT_STYLE_AGENT
        bubbleShowAvatarInput = true
        bubbleWideLayoutEnabledInput = false
        cursorUserBubbleFollowThemeInput = true
        cursorUserBubbleLiquidGlassInput = false
        cursorUserBubbleWaterGlassInput = false
        bubbleUserBubbleLiquidGlassInput = false
        bubbleUserBubbleWaterGlassInput = false
        bubbleAiBubbleLiquidGlassInput = false
        bubbleAiBubbleWaterGlassInput = false
        cursorUserBubbleColorInput = defaultCursorUserBubbleColor
        bubbleUserBubbleColorInput = defaultBubbleUserBubbleColor
        bubbleAiBubbleColorInput = defaultBubbleAiBubbleColor
        bubbleUserTextColorInput = defaultBubbleUserTextColor
        bubbleAiTextColorInput = defaultBubbleAiTextColor
        bubbleUserTextColorCustomizedInput = false
        bubbleAiTextColorCustomizedInput = false
        bubbleUserUseCustomFontInput = false
        bubbleUserFontTypeInput = UserPreferencesManager.FONT_TYPE_SYSTEM
        bubbleUserSystemFontNameInput = UserPreferencesManager.SYSTEM_FONT_DEFAULT
        bubbleUserCustomFontPathInput = null
        bubbleAiUseCustomFontInput = false
        bubbleAiFontTypeInput = UserPreferencesManager.FONT_TYPE_SYSTEM
        bubbleAiSystemFontNameInput = UserPreferencesManager.SYSTEM_FONT_DEFAULT
        bubbleAiCustomFontPathInput = null
        bubbleUserUseImageInput = false
        bubbleAiUseImageInput = false
        bubbleUserImageUriInput = null
        bubbleAiImageUriInput = null
        bubbleUserImageCropLeftInput = 0f
        bubbleUserImageCropTopInput = 0f
        bubbleUserImageCropRightInput = 0f
        bubbleUserImageCropBottomInput = 0f
        bubbleUserImageRepeatStartInput = 0.35f
        bubbleUserImageRepeatEndInput = 0.65f
        bubbleUserImageRepeatYStartInput = 0.35f
        bubbleUserImageRepeatYEndInput = 0.65f
        bubbleUserImageScaleInput = 1f
        bubbleAiImageCropLeftInput = 0f
        bubbleAiImageCropTopInput = 0f
        bubbleAiImageCropRightInput = 0f
        bubbleAiImageCropBottomInput = 0f
        bubbleAiImageRepeatStartInput = 0.35f
        bubbleAiImageRepeatEndInput = 0.65f
        bubbleAiImageRepeatYStartInput = 0.35f
        bubbleAiImageRepeatYEndInput = 0.65f
        bubbleAiImageScaleInput = 1f
        bubbleImageRenderModeInput =
            UserPreferencesManager.BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE
        bubbleUserRoundedCornersEnabledInput = true
        bubbleAiRoundedCornersEnabledInput = true
        bubbleUserContentPaddingLeftInput = 12f
        bubbleUserContentPaddingRightInput = 12f
        bubbleAiContentPaddingLeftInput = 12f
        bubbleAiContentPaddingRightInput = 12f
        showThinkingProcessInput = true
        showStatusTagsInput = true
        showInputProcessingStatusInput = true
        showChatFloatingDotsAnimationInput = true
        userAvatarUriInput = null
        aiAvatarUriInput = null
        avatarShapeInput = UserPreferencesManager.AVATAR_SHAPE_CIRCLE
        avatarCornerRadiusInput = 8f
        onColorModeInput = UserPreferencesManager.ON_COLOR_MODE_AUTO
        showSaveSuccessMessage = true
        globalUserAvatarUriInput = null
        globalUserNameInput = null
        useCustomFontInput = false
        fontTypeInput = UserPreferencesManager.FONT_TYPE_SYSTEM
        systemFontNameInput = UserPreferencesManager.SYSTEM_FONT_DEFAULT
        customFontPathInput = null
        fontScaleInput = 1.0f
    }

    fun handleThemeColorSelected(
        primaryColor: Int?,
        secondaryColor: Int?,
        statusBarColor: Int?,
        appBarColor: Int?,
        navigationDrawerBackgroundColor: Int?,
        navigationDrawerAccentColor: Int?,
        historyIconColor: Int?,
        pipIconColor: Int?,
        cursorUserBubbleColor: Int?,
        bubbleUserBubbleColor: Int?,
        bubbleAiBubbleColor: Int?,
        bubbleUserTextColor: Int?,
        bubbleAiTextColor: Int?,
    ) {
        primaryColor?.let { primaryColorInput = it }
        secondaryColor?.let { secondaryColorInput = it }
        statusBarColor?.let { customStatusBarColorInput = it }
        appBarColor?.let { customAppBarColorInput = it }
        navigationDrawerBackgroundColor?.let { navigationDrawerBackgroundColorInput = it }
        navigationDrawerAccentColor?.let { navigationDrawerAccentColorInput = it }
        historyIconColor?.let { chatHeaderHistoryIconColorInput = it }
        pipIconColor?.let { chatHeaderPipIconColorInput = it }
        cursorUserBubbleColor?.let { cursorUserBubbleColorInput = it }
        bubbleUserBubbleColor?.let { bubbleUserBubbleColorInput = it }
        bubbleAiBubbleColor?.let { bubbleAiBubbleColorInput = it }
        bubbleUserTextColor?.let {
            bubbleUserTextColorInput = it
            bubbleUserTextColorCustomizedInput = true
        }
        bubbleAiTextColor?.let {
            bubbleAiTextColorInput = it
            bubbleAiTextColorCustomizedInput = true
        }

        val newColor =
            primaryColor
                ?: secondaryColor
                ?: statusBarColor
                ?: appBarColor
                ?: navigationDrawerBackgroundColor
                ?: navigationDrawerAccentColor
                ?: historyIconColor
                ?: pipIconColor
                ?: cursorUserBubbleColor
                ?: bubbleUserBubbleColor
                ?: bubbleAiBubbleColor
                ?: bubbleUserTextColor
                ?: bubbleAiTextColor
        newColor?.let { scope.launch { preferencesManager.addRecentColor(it) } }

        saveThemeSettingsWithCharacterCard {
            when (currentColorPickerMode) {
                "primary" ->
                    primaryColor?.let {
                        preferencesManager.saveThemeSettings(customPrimaryColor = it)
                    }
                "secondary" ->
                    secondaryColor?.let {
                        preferencesManager.saveThemeSettings(customSecondaryColor = it)
                    }
                "statusBar" ->
                    statusBarColor?.let {
                        preferencesManager.saveThemeSettings(customStatusBarColor = it)
                    }
                "appBar" ->
                    appBarColor?.let {
                        preferencesManager.saveThemeSettings(customAppBarColor = it)
                    }
                "navigationDrawerBackground" ->
                    navigationDrawerBackgroundColor?.let {
                        preferencesManager.saveThemeSettings(
                            customNavigationDrawerBackgroundColor = it,
                        )
                    }
                "navigationDrawerAccent" ->
                    navigationDrawerAccentColor?.let {
                        preferencesManager.saveThemeSettings(
                            customNavigationDrawerAccentColor = it,
                        )
                    }
                "historyIcon" ->
                    historyIconColor?.let {
                        preferencesManager.saveThemeSettings(chatHeaderHistoryIconColor = it)
                    }
                "pipIcon" ->
                    pipIconColor?.let {
                        preferencesManager.saveThemeSettings(chatHeaderPipIconColor = it)
                    }
                "cursorUserBubble" ->
                    cursorUserBubbleColor?.let {
                        preferencesManager.saveThemeSettings(cursorUserBubbleColor = it)
                    }
                "bubbleUserBubble" ->
                    bubbleUserBubbleColor?.let {
                        preferencesManager.saveThemeSettings(bubbleUserBubbleColor = it)
                    }
                "bubbleAiBubble" ->
                    bubbleAiBubbleColor?.let {
                        preferencesManager.saveThemeSettings(bubbleAiBubbleColor = it)
                    }
                "bubbleUserText" ->
                    bubbleUserTextColor?.let {
                        preferencesManager.saveThemeSettings(bubbleUserTextColor = it)
                    }
                "bubbleAiText" ->
                    bubbleAiTextColor?.let {
                        preferencesManager.saveThemeSettings(bubbleAiTextColor = it)
                    }
            }
        }
    }

    @Composable
    fun ChatStyleSectionContent() {
        ThemeSettingsChatStyleSection(
            cardColors = cardModifier,
            chatStyleInput = chatStyleInput,
            onChatStyleInputChange = { chatStyleInput = it },
            inputStyleInput = inputStyleInput,
            onInputStyleInputChange = { inputStyleInput = it },
            bubbleShowAvatarInput = bubbleShowAvatarInput,
            onBubbleShowAvatarInputChange = { bubbleShowAvatarInput = it },
            bubbleWideLayoutEnabledInput = bubbleWideLayoutEnabledInput,
            onBubbleWideLayoutEnabledInputChange = { bubbleWideLayoutEnabledInput = it },
            cursorUserBubbleFollowThemeInput = cursorUserBubbleFollowThemeInput,
            onCursorUserBubbleFollowThemeInputChange = { cursorUserBubbleFollowThemeInput = it },
            cursorUserBubbleLiquidGlassInput = cursorUserBubbleLiquidGlassInput,
            onCursorUserBubbleLiquidGlassInputChange = {
                cursorUserBubbleLiquidGlassInput = it
            },
            cursorUserBubbleWaterGlassInput = cursorUserBubbleWaterGlassInput,
            onCursorUserBubbleWaterGlassInputChange = {
                cursorUserBubbleWaterGlassInput = it
            },
            bubbleUserBubbleLiquidGlassInput = bubbleUserBubbleLiquidGlassInput,
            onBubbleUserBubbleLiquidGlassInputChange = {
                bubbleUserBubbleLiquidGlassInput = it
            },
            bubbleUserBubbleWaterGlassInput = bubbleUserBubbleWaterGlassInput,
            onBubbleUserBubbleWaterGlassInputChange = {
                bubbleUserBubbleWaterGlassInput = it
            },
            bubbleAiBubbleLiquidGlassInput = bubbleAiBubbleLiquidGlassInput,
            onBubbleAiBubbleLiquidGlassInputChange = {
                bubbleAiBubbleLiquidGlassInput = it
            },
            bubbleAiBubbleWaterGlassInput = bubbleAiBubbleWaterGlassInput,
            onBubbleAiBubbleWaterGlassInputChange = {
                bubbleAiBubbleWaterGlassInput = it
            },
            cursorUserBubbleColorInput = cursorUserBubbleColorInput,
            bubbleUserBubbleColorInput = bubbleUserBubbleColorInput,
            bubbleAiBubbleColorInput = bubbleAiBubbleColorInput,
            bubbleUserTextColorInput = effectiveBubbleUserTextColorInput,
            bubbleAiTextColorInput = effectiveBubbleAiTextColorInput,
            bubbleUserUseCustomFontInput = bubbleUserUseCustomFontInput,
            onBubbleUserUseCustomFontInputChange = { bubbleUserUseCustomFontInput = it },
            bubbleUserFontTypeInput = bubbleUserFontTypeInput,
            onBubbleUserFontTypeInputChange = { bubbleUserFontTypeInput = it },
            bubbleUserSystemFontNameInput = bubbleUserSystemFontNameInput,
            onBubbleUserSystemFontNameInputChange = { bubbleUserSystemFontNameInput = it },
            bubbleUserCustomFontPathInput = bubbleUserCustomFontPathInput,
            onBubbleUserCustomFontPathInputChange = { bubbleUserCustomFontPathInput = it },
            onPickBubbleUserFont = {
                fontPickerMode = "bubbleUser"
                fontPickerLauncher.launch("*/*")
            },
            bubbleAiUseCustomFontInput = bubbleAiUseCustomFontInput,
            onBubbleAiUseCustomFontInputChange = { bubbleAiUseCustomFontInput = it },
            bubbleAiFontTypeInput = bubbleAiFontTypeInput,
            onBubbleAiFontTypeInputChange = { bubbleAiFontTypeInput = it },
            bubbleAiSystemFontNameInput = bubbleAiSystemFontNameInput,
            onBubbleAiSystemFontNameInputChange = { bubbleAiSystemFontNameInput = it },
            bubbleAiCustomFontPathInput = bubbleAiCustomFontPathInput,
            onBubbleAiCustomFontPathInputChange = { bubbleAiCustomFontPathInput = it },
            onPickBubbleAiFont = {
                fontPickerMode = "bubbleAi"
                fontPickerLauncher.launch("*/*")
            },
            previewUserAvatarUri = userAvatarUriInput ?: globalUserAvatarUriInput,
            previewAiAvatarUri = aiAvatarUriInput,
            onShowColorPicker = {
                currentColorPickerMode = it
                showColorPicker = true
            },
            bubbleUserUseImageInput = bubbleUserUseImageInput,
            onBubbleUserUseImageInputChange = { bubbleUserUseImageInput = it },
            bubbleAiUseImageInput = bubbleAiUseImageInput,
            onBubbleAiUseImageInputChange = { bubbleAiUseImageInput = it },
            bubbleUserImageUriInput = bubbleUserImageUriInput,
            bubbleAiImageUriInput = bubbleAiImageUriInput,
            onPickBubbleUserImage = {
                bubbleImagePickerTarget = "user"
                bubbleImagePickerLauncher.launch("image/*")
            },
            onPickBubbleAiImage = {
                bubbleImagePickerTarget = "ai"
                bubbleImagePickerLauncher.launch("image/*")
            },
            onClearBubbleUserImage = {
                bubbleUserImageUriInput = null
                bubbleUserUseImageInput = false
                saveThemeSettingsWithCharacterCard {
                    preferencesManager.saveThemeSettings(
                        bubbleUserImageUri = "",
                        bubbleUserUseImage = false,
                    )
                }
            },
            onClearBubbleAiImage = {
                bubbleAiImageUriInput = null
                bubbleAiUseImageInput = false
                saveThemeSettingsWithCharacterCard {
                    preferencesManager.saveThemeSettings(
                        bubbleAiImageUri = "",
                        bubbleAiUseImage = false,
                    )
                }
            },
            bubbleUserImageCropLeftInput = bubbleUserImageCropLeftInput,
            onBubbleUserImageCropLeftInputChange = { bubbleUserImageCropLeftInput = it },
            bubbleUserImageCropTopInput = bubbleUserImageCropTopInput,
            onBubbleUserImageCropTopInputChange = { bubbleUserImageCropTopInput = it },
            bubbleUserImageCropRightInput = bubbleUserImageCropRightInput,
            onBubbleUserImageCropRightInputChange = { bubbleUserImageCropRightInput = it },
            bubbleUserImageCropBottomInput = bubbleUserImageCropBottomInput,
            onBubbleUserImageCropBottomInputChange = { bubbleUserImageCropBottomInput = it },
            bubbleUserImageRepeatStartInput = bubbleUserImageRepeatStartInput,
            onBubbleUserImageRepeatStartInputChange = { bubbleUserImageRepeatStartInput = it },
            bubbleUserImageRepeatEndInput = bubbleUserImageRepeatEndInput,
            onBubbleUserImageRepeatEndInputChange = { bubbleUserImageRepeatEndInput = it },
            bubbleUserImageRepeatYStartInput = bubbleUserImageRepeatYStartInput,
            onBubbleUserImageRepeatYStartInputChange = { bubbleUserImageRepeatYStartInput = it },
            bubbleUserImageRepeatYEndInput = bubbleUserImageRepeatYEndInput,
            onBubbleUserImageRepeatYEndInputChange = { bubbleUserImageRepeatYEndInput = it },
            bubbleUserImageScaleInput = bubbleUserImageScaleInput,
            onBubbleUserImageScaleInputChange = { bubbleUserImageScaleInput = it },
            bubbleAiImageCropLeftInput = bubbleAiImageCropLeftInput,
            onBubbleAiImageCropLeftInputChange = { bubbleAiImageCropLeftInput = it },
            bubbleAiImageCropTopInput = bubbleAiImageCropTopInput,
            onBubbleAiImageCropTopInputChange = { bubbleAiImageCropTopInput = it },
            bubbleAiImageCropRightInput = bubbleAiImageCropRightInput,
            onBubbleAiImageCropRightInputChange = { bubbleAiImageCropRightInput = it },
            bubbleAiImageCropBottomInput = bubbleAiImageCropBottomInput,
            onBubbleAiImageCropBottomInputChange = { bubbleAiImageCropBottomInput = it },
            bubbleAiImageRepeatStartInput = bubbleAiImageRepeatStartInput,
            onBubbleAiImageRepeatStartInputChange = { bubbleAiImageRepeatStartInput = it },
            bubbleAiImageRepeatEndInput = bubbleAiImageRepeatEndInput,
            onBubbleAiImageRepeatEndInputChange = { bubbleAiImageRepeatEndInput = it },
            bubbleAiImageRepeatYStartInput = bubbleAiImageRepeatYStartInput,
            onBubbleAiImageRepeatYStartInputChange = { bubbleAiImageRepeatYStartInput = it },
            bubbleAiImageRepeatYEndInput = bubbleAiImageRepeatYEndInput,
            onBubbleAiImageRepeatYEndInputChange = { bubbleAiImageRepeatYEndInput = it },
            bubbleAiImageScaleInput = bubbleAiImageScaleInput,
            onBubbleAiImageScaleInputChange = { bubbleAiImageScaleInput = it },
            bubbleImageRenderModeInput = bubbleImageRenderModeInput,
            onBubbleImageRenderModeInputChange = { bubbleImageRenderModeInput = it },
            bubbleUserRoundedCornersEnabledInput = bubbleUserRoundedCornersEnabledInput,
            onBubbleUserRoundedCornersEnabledInputChange = {
                bubbleUserRoundedCornersEnabledInput = it
            },
            bubbleAiRoundedCornersEnabledInput = bubbleAiRoundedCornersEnabledInput,
            onBubbleAiRoundedCornersEnabledInputChange = {
                bubbleAiRoundedCornersEnabledInput = it
            },
            bubbleUserContentPaddingLeftInput = bubbleUserContentPaddingLeftInput,
            onBubbleUserContentPaddingLeftInputChange = {
                bubbleUserContentPaddingLeftInput = it
            },
            bubbleUserContentPaddingRightInput = bubbleUserContentPaddingRightInput,
            onBubbleUserContentPaddingRightInputChange = {
                bubbleUserContentPaddingRightInput = it
            },
            bubbleAiContentPaddingLeftInput = bubbleAiContentPaddingLeftInput,
            onBubbleAiContentPaddingLeftInputChange = {
                bubbleAiContentPaddingLeftInput = it
            },
            bubbleAiContentPaddingRightInput = bubbleAiContentPaddingRightInput,
            onBubbleAiContentPaddingRightInputChange = {
                bubbleAiContentPaddingRightInput = it
            },
            saveThemeSettingsWithCharacterCard = ::saveThemeSettingsWithCharacterCard,
            preferencesManager = preferencesManager,
        )
    }

    @Composable
    fun ThemeSettingsFooterContent() {
        OutlinedButton(
            onClick = {
                scope.launch {
                    resetThemeSettingsAndUi()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        ) {
            Text(stringResource(id = R.string.theme_reset))
        }

        if (showSaveSuccessMessage) {
            LaunchedEffect(key1 = showSaveSuccessMessage) {
                kotlinx.coroutines.delay(2000)
                showSaveSuccessMessage = false
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(id = R.string.theme_saved),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (showColorPicker) {
            ColorPickerDialog(
                showColorPicker = showColorPicker,
                currentColorPickerMode = currentColorPickerMode,
                primaryColorInput = primaryColorInput,
                secondaryColorInput = secondaryColorInput,
                statusBarColorInput = customStatusBarColorInput,
                appBarColorInput = customAppBarColorInput,
                navigationDrawerBackgroundColorInput = navigationDrawerBackgroundColorInput,
                navigationDrawerAccentColorInput = navigationDrawerAccentColorInput,
                historyIconColorInput = chatHeaderHistoryIconColorInput,
                pipIconColorInput = chatHeaderPipIconColorInput,
                cursorUserBubbleColorInput = cursorUserBubbleColorInput,
                bubbleUserBubbleColorInput = bubbleUserBubbleColorInput,
                bubbleAiBubbleColorInput = bubbleAiBubbleColorInput,
                bubbleUserTextColorInput = effectiveBubbleUserTextColorInput,
                bubbleAiTextColorInput = effectiveBubbleAiTextColorInput,
                recentColors = recentColors,
                onColorSelected = ::handleThemeColorSelected,
                onDismiss = { showColorPicker = false },
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        ThemeSettingsCharacterBindingInfoCard(
            aiAvatarUri = activeThemeTargetAvatarUri ?: aiAvatarUri,
            activeCharacterName = activeThemeTargetName,
            isGroupTarget = isGroupThemeTarget,
            cardColors = cardModifier,
        )

        ThemeSettingsThemeModeSection(
            cardColors = cardModifier,
            useSystemThemeInput = useSystemThemeInput,
            onUseSystemThemeInputChange = { useSystemThemeInput = it },
            themeModeInput = themeModeInput,
            onThemeModeInputChange = { themeModeInput = it },
            saveThemeSettingsWithCharacterCard = ::saveThemeSettingsWithCharacterCard,
            preferencesManager = preferencesManager,
        )

        ThemeSettingsColorCustomizationSection(
            cardColors = cardModifier,
            preferencesManager = preferencesManager,
            scope = scope,
            saveThemeSettingsWithCharacterCard = ::saveThemeSettingsWithCharacterCard,
            statusBarHiddenInput = statusBarHiddenInput,
            onStatusBarHiddenInputChange = { statusBarHiddenInput = it },
            statusBarTransparentInput = statusBarTransparentInput,
            onStatusBarTransparentInputChange = { statusBarTransparentInput = it },
            useCustomStatusBarColorInput = useCustomStatusBarColorInput,
            onUseCustomStatusBarColorInputChange = { useCustomStatusBarColorInput = it },
            customStatusBarColorInput = customStatusBarColorInput,
            toolbarTransparentInput = toolbarTransparentInput,
            onToolbarTransparentInputChange = { toolbarTransparentInput = it },
            useCustomAppBarColorInput = useCustomAppBarColorInput,
            onUseCustomAppBarColorInputChange = { useCustomAppBarColorInput = it },
            customAppBarColorInput = customAppBarColorInput,
            navigationDrawerWaterGlassInput = navigationDrawerWaterGlassInput,
            onNavigationDrawerWaterGlassInputChange = {
                navigationDrawerWaterGlassInput = it
            },
            navigationDrawerButtonLiquidGlassInput = navigationDrawerButtonLiquidGlassInput,
            onNavigationDrawerButtonLiquidGlassInputChange = {
                navigationDrawerButtonLiquidGlassInput = it
            },
            useCustomNavigationDrawerBackgroundColorInput = useCustomNavigationDrawerBackgroundColorInput,
            onUseCustomNavigationDrawerBackgroundColorInputChange = {
                useCustomNavigationDrawerBackgroundColorInput = it
            },
            navigationDrawerBackgroundColorInput = navigationDrawerBackgroundColorInput,
            useCustomNavigationDrawerAccentColorInput = useCustomNavigationDrawerAccentColorInput,
            onUseCustomNavigationDrawerAccentColorInputChange = {
                useCustomNavigationDrawerAccentColorInput = it
            },
            navigationDrawerAccentColorInput = navigationDrawerAccentColorInput,
            chatHeaderTransparentInput = chatHeaderTransparentInput,
            onChatHeaderTransparentInputChange = { chatHeaderTransparentInput = it },
            chatHeaderOverlayModeInput = chatHeaderOverlayModeInput,
            onChatHeaderOverlayModeInputChange = { chatHeaderOverlayModeInput = it },
            chatInputTransparentInput = chatInputTransparentInput,
            onChatInputTransparentInputChange = { chatInputTransparentInput = it },
            chatInputFloatingInput = chatInputFloatingInput,
            onChatInputFloatingInputChange = { chatInputFloatingInput = it },
            chatInputLiquidGlassInput = chatInputLiquidGlassInput,
            onChatInputLiquidGlassInputChange = { chatInputLiquidGlassInput = it },
            chatInputWaterGlassInput = chatInputWaterGlassInput,
            onChatInputWaterGlassInputChange = { chatInputWaterGlassInput = it },
            forceAppBarContentColorInput = forceAppBarContentColorInput,
            onForceAppBarContentColorInputChange = { forceAppBarContentColorInput = it },
            appBarContentColorModeInput = appBarContentColorModeInput,
            onAppBarContentColorModeInputChange = { appBarContentColorModeInput = it },
            chatHeaderHistoryIconColorInput = chatHeaderHistoryIconColorInput,
            chatHeaderPipIconColorInput = chatHeaderPipIconColorInput,
            useCustomColorsInput = useCustomColorsInput,
            onUseCustomColorsInputChange = { useCustomColorsInput = it },
            primaryColorInput = primaryColorInput,
            secondaryColorInput = secondaryColorInput,
            onColorModeInput = onColorModeInput,
            onOnColorModeInputChange = { onColorModeInput = it },
            onShowColorPicker = {
                currentColorPickerMode = it
                showColorPicker = true
            },
            onShowSaveSuccessMessage = { showSaveSuccessMessage = true },
        )

        ChatStyleSectionContent()

        ThemeSettingsAvatarSection(
            cardColors = cardModifier,
            scope = scope,
            preferencesManager = preferencesManager,
            displayPreferencesManager = displayPreferencesManager,
            saveThemeSettingsWithCharacterCard = ::saveThemeSettingsWithCharacterCard,
            userAvatarUriInput = userAvatarUriInput,
            onUserAvatarUriInputChange = { userAvatarUriInput = it },
            globalUserAvatarUriInput = globalUserAvatarUriInput,
            onGlobalUserAvatarUriInputChange = { globalUserAvatarUriInput = it },
            globalUserNameInput = globalUserNameInput,
            onGlobalUserNameInputChange = { globalUserNameInput = it },
            avatarShapeInput = avatarShapeInput,
            onAvatarShapeInputChange = { avatarShapeInput = it },
            avatarCornerRadiusInput = avatarCornerRadiusInput,
            onAvatarCornerRadiusInputChange = { avatarCornerRadiusInput = it },
            avatarImagePicker = avatarImagePicker,
            onAvatarPickerModeChange = { avatarPickerMode = it },
        )

        ThemeSettingsDisplayOptionsSection(
            cardColors = cardModifier,
            showThinkingProcessInput = showThinkingProcessInput,
            onShowThinkingProcessInputChange = { showThinkingProcessInput = it },
            showStatusTagsInput = showStatusTagsInput,
            onShowStatusTagsInputChange = { showStatusTagsInput = it },
            showInputProcessingStatusInput = showInputProcessingStatusInput,
            onShowInputProcessingStatusInputChange = { showInputProcessingStatusInput = it },
            showChatFloatingDotsAnimationInput = showChatFloatingDotsAnimationInput,
            onShowChatFloatingDotsAnimationInputChange = {
                showChatFloatingDotsAnimationInput = it
            },
            saveThemeSettingsWithCharacterCard = ::saveThemeSettingsWithCharacterCard,
            preferencesManager = preferencesManager,
        )

        ThemeSettingsFontSection(
            cardColors = cardModifier,
            context = context,
            preferencesManager = preferencesManager,
            saveThemeSettingsWithCharacterCard = ::saveThemeSettingsWithCharacterCard,
            useCustomFontInput = useCustomFontInput,
            onUseCustomFontInputChange = { useCustomFontInput = it },
            fontTypeInput = fontTypeInput,
            onFontTypeInputChange = { fontTypeInput = it },
            systemFontNameInput = systemFontNameInput,
            onSystemFontNameInputChange = { systemFontNameInput = it },
            customFontPathInput = customFontPathInput,
            onCustomFontPathInputChange = { customFontPathInput = it },
            fontScaleInput = fontScaleInput,
            onFontScaleInputChange = { fontScaleInput = it },
            onPickFont = {
                fontPickerMode = "global"
                fontPickerLauncher.launch("*/*")
            },
        )


        ThemeSettingsBackgroundSection(
            cardColors = cardModifier,
            context = context,
            preferencesManager = preferencesManager,
            saveThemeSettingsWithCharacterCard = ::saveThemeSettingsWithCharacterCard,
            exoPlayer = exoPlayer,
            launchImageCrop = ::launchImageCrop,
            mediaPickerLauncher = mediaPickerLauncher,
            scrollState = scrollState,
            useBackgroundImageInput = useBackgroundImageInput,
            onUseBackgroundImageInputChange = { useBackgroundImageInput = it },
            backgroundMediaTypeInput = backgroundMediaTypeInput,
            onBackgroundMediaTypeInputChange = { backgroundMediaTypeInput = it },
            backgroundImageUriInput = backgroundImageUriInput,
            backgroundImageOpacityInput = backgroundImageOpacityInput,
            onBackgroundImageOpacityInputChange = { backgroundImageOpacityInput = it },
            videoBackgroundMutedInput = videoBackgroundMutedInput,
            onVideoBackgroundMutedInputChange = { videoBackgroundMutedInput = it },
            videoBackgroundLoopInput = videoBackgroundLoopInput,
            onVideoBackgroundLoopInputChange = { videoBackgroundLoopInput = it },
            useBackgroundBlurInput = useBackgroundBlurInput,
            onUseBackgroundBlurInputChange = { useBackgroundBlurInput = it },
            backgroundBlurRadiusInput = backgroundBlurRadiusInput,
            onBackgroundBlurRadiusInputChange = { backgroundBlurRadiusInput = it },
        )

        ThemeSettingsFooterContent()


    }
}

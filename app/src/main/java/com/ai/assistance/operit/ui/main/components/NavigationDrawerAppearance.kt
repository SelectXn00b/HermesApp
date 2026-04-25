package com.ai.assistance.operit.ui.main.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import com.ai.assistance.operit.ui.theme.isLiquidGlassSupported
import com.ai.assistance.operit.ui.theme.isWaterGlassSupported

data class NavigationDrawerAppearance(
    val containerColor: Color,
    val titleColor: Color,
    val statusAvailableColor: Color,
    val itemColor: Color,
    val buttonContainerColor: Color,
    val selectedContainerColor: Color,
    val selectedContentColor: Color,
    val dividerColor: Color,
    val waterGlassEnabled: Boolean,
    val buttonLiquidGlassEnabled: Boolean,
)

@Composable
fun rememberNavigationDrawerAppearance(): NavigationDrawerAppearance {
    val context = LocalContext.current
    val preferencesManager = remember(context) { UserPreferencesManager.getInstance(context) }
    val navigationDrawerWaterGlass by
        preferencesManager.navigationDrawerWaterGlass.collectAsState(initial = false)
    val navigationDrawerButtonLiquidGlass by
        preferencesManager.navigationDrawerButtonLiquidGlass.collectAsState(initial = false)
    val useCustomNavigationDrawerBackgroundColor by
        preferencesManager.useCustomNavigationDrawerBackgroundColor.collectAsState(initial = false)
    val customNavigationDrawerBackgroundColor by
        preferencesManager.customNavigationDrawerBackgroundColor.collectAsState(initial = null)
    val useCustomNavigationDrawerAccentColor by
        preferencesManager.useCustomNavigationDrawerAccentColor.collectAsState(initial = false)
    val customNavigationDrawerAccentColor by
        preferencesManager.customNavigationDrawerAccentColor.collectAsState(initial = null)

    val waterGlassEnabled = navigationDrawerWaterGlass && isWaterGlassSupported()
    val buttonLiquidGlassEnabled =
        navigationDrawerButtonLiquidGlass && isLiquidGlassSupported()
    val defaultTitleColor = MaterialTheme.colorScheme.primary
    val defaultStatusColor = MaterialTheme.colorScheme.primary
    val defaultDividerColor = defaultTitleColor.copy(alpha = 0.42f)
    val defaultAppearance =
        NavigationDrawerAppearance(
            containerColor = MaterialTheme.colorScheme.surface,
            titleColor =
                if (useCustomNavigationDrawerAccentColor) {
                    customNavigationDrawerAccentColor?.let(::Color) ?: defaultTitleColor
                } else {
                    defaultTitleColor
                },
            statusAvailableColor =
                if (useCustomNavigationDrawerAccentColor) {
                    customNavigationDrawerAccentColor?.let(::Color) ?: defaultStatusColor
                } else {
                    defaultStatusColor
                },
            itemColor = MaterialTheme.colorScheme.onSurfaceVariant,
            buttonContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedContentColor = MaterialTheme.colorScheme.primary,
            dividerColor =
                if (useCustomNavigationDrawerAccentColor) {
                    customNavigationDrawerAccentColor?.let { Color(it).copy(alpha = 0.42f) }
                        ?: defaultDividerColor
                } else {
                    defaultDividerColor
                },
            waterGlassEnabled = waterGlassEnabled,
            buttonLiquidGlassEnabled = buttonLiquidGlassEnabled,
        )

    val customColorValue = customNavigationDrawerBackgroundColor
    if (!useCustomNavigationDrawerBackgroundColor || customColorValue == null) {
        return defaultAppearance
    }

    val containerColor = Color(customColorValue)
    val onContainerColor = getTextColorForBackground(containerColor)
    val accentColor = lerp(onContainerColor, MaterialTheme.colorScheme.primary, 0.28f)
    val buttonContainerColor = lerp(containerColor, onContainerColor, 0.08f)
    val selectedContainerColor =
        accentColor
            .copy(alpha = if (containerColor.luminance() > 0.5f) 0.14f else 0.24f)
            .compositeOver(containerColor)

    return NavigationDrawerAppearance(
        containerColor = containerColor,
        titleColor =
            if (useCustomNavigationDrawerAccentColor) {
                customNavigationDrawerAccentColor?.let(::Color) ?: accentColor
            } else {
                accentColor
            },
        statusAvailableColor =
            if (useCustomNavigationDrawerAccentColor) {
                customNavigationDrawerAccentColor?.let(::Color) ?: accentColor
            } else {
                accentColor
            },
        itemColor = onContainerColor.copy(alpha = 0.76f),
        buttonContainerColor = buttonContainerColor,
        selectedContainerColor = selectedContainerColor,
        selectedContentColor = getTextColorForBackground(selectedContainerColor),
        dividerColor =
            if (useCustomNavigationDrawerAccentColor) {
                customNavigationDrawerAccentColor?.let { Color(it).copy(alpha = 0.42f) }
                    ?: accentColor.copy(alpha = 0.42f)
            } else {
                accentColor.copy(alpha = 0.42f)
            },
        waterGlassEnabled = waterGlassEnabled,
        buttonLiquidGlassEnabled = buttonLiquidGlassEnabled,
    )
}

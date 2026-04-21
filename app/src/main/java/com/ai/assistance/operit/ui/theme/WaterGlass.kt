package com.ai.assistance.operit.ui.theme

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquid

val LocalWaterGlassState = compositionLocalOf<LiquidState?> { null }

private const val WaterGlassMinApi = Build.VERSION_CODES.TIRAMISU

fun isWaterGlassSupported(): Boolean = Build.VERSION.SDK_INT >= WaterGlassMinApi

@Composable
fun Modifier.waterGlass(
    enabled: Boolean,
    shape: Shape = RoundedCornerShape(0.dp),
    containerColor: Color,
    shadowElevation: Dp = 14.dp,
    borderWidth: Dp = 1.dp,
    overlayAlphaBoost: Float = 0f,
): Modifier {
    if (!enabled || !isWaterGlassSupported()) {
        return this
    }

    val liquidState = LocalWaterGlassState.current ?: return this
    val isLightGlass = containerColor.luminance() >= 0.5f
    val tintAlpha = if (isLightGlass) 0.09f else 0.16f
    val surfaceTint = containerColor.copy(alpha = (tintAlpha + overlayAlphaBoost).coerceIn(0f, 0.56f))
    val borderColor =
        if (isLightGlass) {
            Color.White.copy(alpha = 0.18f)
        } else {
            Color.White.copy(alpha = 0.10f)
        }
    val shadowColor =
        if (isLightGlass) {
            Color.Black.copy(alpha = 0.10f)
        } else {
            Color.Black.copy(alpha = 0.18f)
        }

    return this
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = shadowColor,
            spotColor = shadowColor,
        )
        .border(width = borderWidth, color = borderColor, shape = shape)
        .liquid(liquidState) {
            this.shape = shape
            this.frost = if (isLightGlass) 6.dp else 8.dp
            this.curve = if (isLightGlass) 0.40f else 0.30f
            this.refraction = if (isLightGlass) 0.12f else 0.09f
            this.dispersion = if (isLightGlass) 0.18f else 0.13f
            this.saturation = if (isLightGlass) 0.40f else 0.32f
            this.contrast = if (isLightGlass) 1.22f else 1.40f
            this.tint = surfaceTint
        }
}

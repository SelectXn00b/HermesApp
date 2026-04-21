package com.ai.assistance.operit.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Determines the appropriate text color (black or white) based on background color
 */
fun getTextColorForBackground(backgroundColor: Color): Color {
    val luminance =
            0.299 * backgroundColor.red +
                    0.587 * backgroundColor.green +
                    0.114 * backgroundColor.blue
    return if (luminance > 0.5) Color.Black else Color.White
}

/**
 * Determines if a color has high contrast with both black and white
 * Colors in the middle range (not too light, not too dark) tend to have low contrast with both
 * A good high contrast color is either fairly dark or fairly light
 */
fun isHighContrast(backgroundColor: Color): Boolean {
    val luminance =
            0.299 * backgroundColor.red +
                    0.587 * backgroundColor.green +
                    0.114 * backgroundColor.blue
    return luminance < 0.3 || luminance > 0.7
} 
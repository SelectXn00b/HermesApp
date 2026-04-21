package com.ai.assistance.operit.ui.common.animations

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

/**
 * A simplified version of AnimatedVisibility that just uses alpha animation
 * to avoid complex animation implementations that might have compatibility issues.
 */
@Composable
fun SimpleAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        label = "Visibility Animation"
    )
    
    if (alpha > 0f) {
        Box(
            modifier = modifier.alpha(alpha)
        ) {
            content()
        }
    }
} 
package com.ai.assistance.operit.ui.common.displays

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import kotlin.math.pow

@Composable
fun RainbowBorderOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "shared_rainbow_border")
    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shared_rainbow_border_progress"
    )

    val rainbowColors = listOf(
        Color(0xFFFF5F6D),
        Color(0xFFFFC371),
        Color(0xFF47CF73),
        Color(0xFF00C6FF),
        Color(0xFF845EF7),
        Color(0xFFFF5F6D)
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val phase = animatedProgress * size.maxDimension
        val borderBrush = Brush.linearGradient(
            colors = rainbowColors,
            start = Offset(-phase, 0f),
            end = Offset(size.width - phase, size.height)
        )

        val totalFadeWidth = size.minDimension * 0.046f
        val innerCornerBase = size.minDimension * 0.070f
        val stepCount = 72
        val outerRectPath = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
        }

        fun roundedRectPath(inset: Float): Path {
            if (inset <= 0f) {
                return Path().apply {
                    addRect(Rect(0f, 0f, size.width, size.height))
                }
            }

            val safeInset = inset.coerceAtMost(size.minDimension / 2f)
            val radius = (innerCornerBase - safeInset).coerceAtLeast(0f)
            return Path().apply {
                addRoundRect(
                    RoundRect(
                        left = safeInset,
                        top = safeInset,
                        right = size.width - safeInset,
                        bottom = size.height - safeInset,
                        cornerRadius = CornerRadius(radius, radius)
                    )
                )
            }
        }

        for (step in 0 until stepCount) {
            val startT = step / stepCount.toFloat()
            val endT = (step + 1) / stepCount.toFloat()
            val outerInset = totalFadeWidth * startT
            val innerInset = totalFadeWidth * endT

            val outerPath = if (step == 0) {
                outerRectPath
            } else {
                roundedRectPath(outerInset)
            }
            val innerPath = roundedRectPath(innerInset)
            val bandPath = Path.combine(PathOperation.Difference, outerPath, innerPath)

            val alpha = (1f - startT).pow(1.55f).coerceIn(0f, 1f)
            drawPath(
                path = bandPath,
                brush = borderBrush,
                alpha = alpha
            )
        }
    }
}

package com.ai.assistance.operit.ui.floating.ui.window.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LoadingDotsIndicator(textColor: Color) {
        val infiniteTransition = rememberInfiniteTransition(label = "dots")

        Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                val jumpHeight = -5f
                val animationDelay = 160

                (0..2).forEach { index ->
                        val offsetY by
                                infiniteTransition.animateFloat(
                                        initialValue = 0f,
                                        targetValue = jumpHeight,
                                        animationSpec =
                                                infiniteRepeatable(
                                                        animation =
                                                                keyframes {
                                                                        durationMillis = 600
                                                                        0f at 0
                                                                        jumpHeight * 0.4f at 100
                                                                        jumpHeight * 0.8f at 200
                                                                        jumpHeight at 300
                                                                        jumpHeight * 0.8f at 400
                                                                        jumpHeight * 0.4f at 500
                                                                        0f at 600
                                                                },
                                                        repeatMode = RepeatMode.Restart,
                                                        initialStartOffset =
                                                                StartOffset(index * animationDelay)
                                                ),
                                        label = "offsetY_$index"
                                )

                        Box(
                                modifier =
                                        Modifier.size(6.dp)
                                                .offset(y = offsetY.dp)
                                                .background(
                                                        color = textColor.copy(alpha = 0.6f),
                                                        shape = CircleShape
                                                )
                        )
                }
        }
}

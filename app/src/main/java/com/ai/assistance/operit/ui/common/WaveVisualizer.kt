package com.ai.assistance.operit.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlin.math.sin

/**
 * 波浪可视化组件
 * 用于显示静态波浪或根据音量动态变化的波浪
 */
@Composable
fun WaveVisualizer(
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    volumeFlow: Flow<Float>? = null,
    waveColor: Color = Color.White,
    activeWaveColor: Color = Color(0xFF00B0FF),
    onToggleActive: () -> Unit = {}
) {
    // 波浪状态
    var currentVolume by remember { mutableStateOf(0f) }
    // isExpanded is no longer used, remove it.
    
    // 从音量流收集数据，这是离散的
    LaunchedEffect(volumeFlow) {
        volumeFlow?.collect { volume ->
            // 音量值通常在0-1之间，可以根据需要调整
            currentVolume = volume
        }
    }
    
    // 创建一个平滑的、经过补间动画处理的音量值，以解决数据离散导致的卡顿问题
    val animatedVolume by animateFloatAsState(
        targetValue = currentVolume,
        animationSpec = tween(durationMillis = 100, easing = LinearEasing), // 快速响应的补间动画
        label = "animated_volume"
    )
    
    // 静态波浪动画
    val infiniteTransition = rememberInfiniteTransition(label = "wave_animation")
    val animatedProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_progress"
    )
    
    // 脉冲动画 - 用于非活跃模式中的脉冲效果
    val pulseAnimation = infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_animation"
    )
    
    // 呼吸光效动画 - 用于控制非活跃模式的光晕效果
    val glowAnimation = infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f, 
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_animation"
    )
    
    // For the active state rotation
    val rotation = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )
    
    // 波浪扩散动画 - 减少波浪数量使其更稀疏
    val waves = listOf(0.2f, 0.6f, 1.0f)
    val animatedScales = waves.map { initialScale ->
        val animSpec = infiniteRepeatable<Float>(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset((initialScale * 1000).toInt())
        )
        infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = 1f,
            animationSpec = animSpec,
            label = "wave_scale_$initialScale"
        )
    }
    
    Box(
        modifier = modifier
            .size(if (isActive) 200.dp else 120.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // Disable the ripple effect to prevent visual artifacts
                onClick = { onToggleActive() }
            )
    ) {
        if (isActive) {
            // New Active State: Rotating Arcs
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                val maxRadius = size.width.coerceAtMost(size.height) / 2

                // 使用平滑处理后的 animatedVolume 来驱动动画，而不是直接使用离散的 currentVolume
                val radius = maxRadius * (0.4f + animatedVolume * 0.6f)

                // Center circle
                drawCircle(
                    color = activeWaveColor,
                    radius = radius * 0.4f, // A bit smaller central circle
                    center = Offset(centerX, centerY),
                    alpha = 0.8f
                )

                // Two rotating arcs, their thickness also pulses with volume.
                // Increase the impact of volume for a more noticeable effect.
                // 使用平滑处理后的 animatedVolume 来驱动动画
                val strokeWidth = (2f + animatedVolume * 12f).dp.toPx()
                drawArc(
                    color = activeWaveColor,
                    startAngle = rotation.value,
                    sweepAngle = 120f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth),
                    size = Size(radius * 2, radius * 2),
                    topLeft = Offset(centerX - radius, centerY - radius)
                )
                drawArc(
                    color = activeWaveColor,
                    startAngle = rotation.value + 180f,
                    sweepAngle = 120f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth),
                    size = Size(radius * 2, radius * 2),
                    topLeft = Offset(centerX - radius, centerY - radius)
                )
            }
        } else {
            // 非活跃状态 - 全新的更现代的设计
            
            // 背景光晕 (最底层)
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val center = Offset(size.width / 2, size.height / 2)
                val outerRadius = size.minDimension * 0.5f * pulseAnimation.value
                
                // 外部光晕效果
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            activeWaveColor.copy(alpha = glowAnimation.value * 0.3f),
                            activeWaveColor.copy(alpha = 0f)
                        ),
                        center = center,
                        radius = outerRadius * 1.2f
                    ),
                    center = center,
                    radius = outerRadius
                )
            }

            // 静态扩散波浪 - 现在使用渐变色 (中间层)
            animatedScales.forEach { animatedScale ->
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha((1f - animatedScale.value) * 0.4f)
                ) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.minDimension / 2 * animatedScale.value
                    
                    drawCircle(
                        color = activeWaveColor.copy(alpha = 0.7f), // 使用主题颜色而不是灰色
                        center = center,
                        radius = radius,
                        style = Stroke(width = (1.5f + animatedScale.value * 1.5f).dp.toPx()) // 动态调整线宽
                    )
                }
            }
            
            // 中心圆 - 现在是线框样式 (最顶层)
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val center = Offset(size.width / 2, size.height / 2)
                val innerRadius = size.minDimension * 0.18f * pulseAnimation.value
                
                // 主圆体 - 线框样式
                drawCircle(
                    color = activeWaveColor.copy(alpha = 0.9f),
                    style = Stroke(width = 2.dp.toPx()),
                    center = center,
                    radius = innerRadius
                )
                
                // The highlight is removed as it doesn't fit the wireframe style.
            }
        }
    }
} 
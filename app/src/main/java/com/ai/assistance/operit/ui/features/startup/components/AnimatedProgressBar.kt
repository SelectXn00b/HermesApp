package com.ai.assistance.operit.ui.features.startup.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 超平滑进度条
 *
 * 相比标准的LinearProgressIndicator，提供了超平滑的进度过渡效果， 即使进度值变化很快，也能实现平滑的插值动画效果。
 * 内部使用智能插值系统，确保进度条始终平滑过渡，不出现跳跃。
 *
 * @param progress 当前进度值 (0.0-1.0)
 * @param modifier 修饰符
 * @param height 进度条高度
 * @param trackColor 轨道颜色
 * @param progressColor 进度颜色
 * @param intermediateSteps 中间过渡步数
 * @param stepDuration 每步动画持续时间(毫秒)
 * @param minProgressDelta 最小进度变化阈值，小于此值的变化将使用更简单的动画
 */
@Composable
fun SmoothLinearProgressIndicator(
        progress: Float,
        modifier: Modifier = Modifier,
        height: Dp = 8.dp,
        trackColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
        progressColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
        intermediateSteps: Int = 8, // 减少默认步数
        stepDuration: Int = 80, // 减少每步动画时间
        minProgressDelta: Float = 0.05f // 可配置的最小变化阈值
) {
    // 记住上一次的目标进度值
    var lastTargetProgress by remember { mutableFloatStateOf(0f) }

    // 当前动画进度值
    val animatedProgress = remember { Animatable(0f) }

    // 是否是首次渲染
    var isFirstRender by remember { mutableStateOf(true) }

    // 使用记忆化标记来减少不必要的重新计算
    val clampedProgress by remember(progress) { derivedStateOf { max(0f, min(1f, progress)) } }

    LaunchedEffect(clampedProgress) {
        // 如果是首次渲染，直接设置到初始值
        if (isFirstRender) {
            animatedProgress.snapTo(clampedProgress)
            lastTargetProgress = clampedProgress
            isFirstRender = false
            return@LaunchedEffect
        }

        // 计算进度变化量
        val progressDelta = clampedProgress - lastTargetProgress

        // 如果变化很小，直接使用简单动画
        if (abs(progressDelta) < minProgressDelta) {
            animatedProgress.animateTo(
                    targetValue = clampedProgress,
                    animationSpec =
                            tween(durationMillis = stepDuration * 2, easing = FastOutSlowInEasing)
            )
        } else {
            // 在IO线程上预计算插值步骤，减少UI线程负担
            val steps =
                    withContext(Dispatchers.Default) {
                        generateSteps(lastTargetProgress, clampedProgress, intermediateSteps)
                    }

            // 逐步执行中间过渡动画
            for (step in steps) {
                animatedProgress.animateTo(
                        targetValue = step,
                        animationSpec = tween(durationMillis = stepDuration, easing = LinearEasing)
                )
            }
        }

        // 更新上一次目标进度
        lastTargetProgress = clampedProgress
    }

    // 使用MaterialDesign的LinearProgressIndicator
    LinearProgressIndicator(
            progress = { animatedProgress.value },
            modifier = modifier.fillMaxWidth().height(height),
            trackColor = trackColor,
            color = progressColor,
            strokeCap = StrokeCap.Round
    )
}

/** 生成一系列从起始进度到目标进度的平滑过渡步骤 优化版本使用更高效的算法生成步骤 */
private fun generateSteps(start: Float, end: Float, steps: Int): List<Float> {
    if (steps <= 1) return listOf(end)

    // 预分配固定大小的列表以避免动态调整大小
    return List(steps) { i ->
        val t = (i + 1).toFloat() / steps
        val easedT = easeInOutCubic(t)
        start + (end - start) * easedT
    }
}

/** 优化的三次方缓动函数 */
private fun easeInOutCubic(t: Float): Float {
    return if (t < 0.5f) {
        4 * t * t * t
    } else {
        val p = 2 * t - 2
        1 + 0.5f * (p * p * p)
    }
}

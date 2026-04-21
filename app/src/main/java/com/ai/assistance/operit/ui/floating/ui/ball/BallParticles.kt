package com.ai.assistance.operit.ui.floating.ui.ball

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// 简单的3D向量类
data class Vector3(val x: Float, val y: Float, val z: Float)

@Stable
class Particle(
    var angle: Float,          // 当前在轨道上的角度 (0-360)
    val speed: Float,          // 运动速度
    val orbitRadius: Float,    // 轨道半径系数 (相对于球半径)
    val orbitTiltX: Float,     // 轨道绕X轴倾角 (弧度)
    val orbitTiltY: Float,     // 轨道绕Y轴倾角 (弧度)
    val color: Color,          // 主色
    val accentColor: Color,    // 辅色（用于渐变混合）
    val baseSize: Float
) {
    // 拖尾历史位置 (最新的在前面)
    val trail = ArrayDeque<Vector3>()
    val maxTrailLength = 100 // 拖尾再加长
}

@Composable
fun rememberParticleSystem(count: Int = 2): ParticleSystem { // 数量减少到2个
    return remember { ParticleSystem(count) }
}

class ParticleSystem(count: Int) {
    private val particles = mutableStateListOf<Particle>()
    
    // 动画状态
    var isActive by mutableStateOf(false)
    val visibility = Animatable(0f)
    
    init {
        // Siri 风格的高饱和度霓虹色
        val palette = listOf(
            Color(0xFF0A84FF), // Blue
            Color(0xFFBF5AF2), // Purple
            Color(0xFFFF375F), // Pink
            Color(0xFF00D4FF), // Cyan
            Color(0xFF30D158), // Green
            Color(0xFFFF9F0A)  // Orange
        )
        
        repeat(count) { index ->
            val mainColor = palette.random()
            val accentColor = palette.filter { it != mainColor }.random()
            
            // 手动配置垂直/交叉轨道
            // 粒子1: 倾角 60度
            // 粒子2: 倾角 150度 (与60度垂直)
            val fixedTiltX = if (index == 0) 60f else 150f
            val fixedTiltY = if (index == 0) 30f else 120f
            
            particles.add(
                Particle(
                    angle = Random.nextFloat() * 360f,
                    speed = (Random.nextFloat() * 1.2f + 1.2f) * (if(Random.nextBoolean()) 1 else -1),
                    orbitRadius = Random.nextFloat() * 0.1f + 1.4f, // 1.4 - 1.5
                    orbitTiltX = fixedTiltX * (PI.toFloat() / 180f),
                    orbitTiltY = fixedTiltY * (PI.toFloat() / 180f),
                    color = mainColor,
                    accentColor = accentColor,
                    baseSize = 20f
                )
            )
        }
    }
    
    @Composable
    fun UpdateEffect(isPressed: Boolean) {
        // 状态管理
        LaunchedEffect(isPressed) {
            if (isPressed) {
                isActive = true
                visibility.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(400, easing = LinearEasing)
                )
            } else {
                visibility.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(300, easing = LinearEasing)
                )
                isActive = false
                // 清空拖尾，防止下次显示时有残留
                particles.forEach { it.trail.clear() }
            }
        }

        // 粒子运动循环
        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos { _ ->
                    if (visibility.value > 0f) {
                        updateParticles()
                    }
                }
            }
        }
    }

    private fun updateParticles() {
        particles.forEach { p ->
            // 1. 更新角度
            p.angle = (p.angle + p.speed) % 360f
            
            // 2. 计算当前3D位置
            // 修正：基础轨道改为 XZ 平面 (水平圆周)，确保 Z 轴变化明显
            val rad = p.angle * (PI.toFloat() / 180f)
            val rawX = cos(rad) * p.orbitRadius
            val rawZ = sin(rad) * p.orbitRadius // Z 轴现在有大幅度变化
            val rawY = 0f
            
            // 3. 应用轨道旋转
            // 绕X轴旋转 (决定轨道的俯仰角)
            val y1 = rawY * cos(p.orbitTiltX) - rawZ * sin(p.orbitTiltX)
            val z1 = rawY * sin(p.orbitTiltX) + rawZ * cos(p.orbitTiltX)
            val x1 = rawX
            
            // 绕Y轴旋转 (决定轨道的方位角)
            val x2 = x1 * cos(p.orbitTiltY) + z1 * sin(p.orbitTiltY)
            val z2 = -x1 * sin(p.orbitTiltY) + z1 * cos(p.orbitTiltY)
            val y2 = y1
            
            val pos = Vector3(x2, y2, z2)
            
            // 4. 更新拖尾
            p.trail.addFirst(pos)
            if (p.trail.size > p.maxTrailLength) {
                p.trail.removeLast()
            }
        }
    }

    fun DrawScope.drawBackParticles(center: Offset, baseRadius: Float) {
        if (visibility.value <= 0.01f) return
        drawParticleTrails(center, baseRadius, true)
    }

    fun DrawScope.drawFrontParticles(center: Offset, baseRadius: Float) {
        if (visibility.value <= 0.01f) return
        drawParticleTrails(center, baseRadius, false)
    }

    private fun DrawScope.drawParticleTrails(center: Offset, baseRadius: Float, isBack: Boolean) {
        val currentVisibility = visibility.value
        val focalLength = 1000f 
        
        particles.forEach { p ->
            // 倒序绘制
            for (i in p.trail.lastIndex downTo 0) {
                val pos = p.trail[i]
                
                // === 修改：只绘制前方粒子 ===
                // 用户要求：环绕到后面不绘制
                if (pos.z > 0) continue
                
                // 即使是前景，在接近边缘 (Z=0) 时也需要淡出，否则消失得太突兀
                // Z 范围大约是 -1.5 到 0 (前方)。我们设定在 -0.3 开始变淡
                val zThreshold = -0.3f
                val edgeFade = if (pos.z > zThreshold) {
                    (pos.z / zThreshold).coerceIn(0f, 1f)
                } else {
                    1f
                }
                
                // 深度分层检查 (isBack=true 时将不会绘制任何东西，因为 z<=0)
                val isInBack = false 
                if (isBack != isInBack) continue
                
                val depth = pos.z * baseRadius + focalLength
                val scale = if (depth > 0) focalLength / depth else 1f
                
                val projX = center.x + pos.x * baseRadius * scale
                val projY = center.y + pos.y * baseRadius * scale
                
                // 视觉属性
                val progress = i.toFloat() / p.maxTrailLength
                val sizeScale = scale * (1f - progress * 0.9f)
                // 减缓透明度衰减，让光束主体更长更实
                val alphaFade = 1f - progress.pow(0.9f) 
                // 叠加边缘淡出 edgeFade
                val alphaBase = p.color.alpha * currentVisibility * alphaFade * edgeFade
                
                if (alphaBase <= 0.01f) continue

                // === 统一光束绘制 ===
                // 极低透明度模式：依靠叠加产生亮度
                // 因为有100个点重叠，单点必须极透，才能避免整体变成实心
                val tailAlpha = (alphaBase * 0.08f).coerceIn(0f, 1f) 
                val baseColor = if (i % 5 == 0) p.accentColor else p.color
                
                val beamColor = Color(
                    red = 0.85f + 0.15f * baseColor.red,   // 85% 白
                    green = 0.85f + 0.15f * baseColor.green,
                    blue = 0.85f + 0.15f * baseColor.blue,
                    alpha = tailAlpha
                )
                
                drawCircle(
                    color = beamColor,
                    radius = p.baseSize * sizeScale,
                    center = Offset(projX, projY)
                )
            }
        }
    }
}

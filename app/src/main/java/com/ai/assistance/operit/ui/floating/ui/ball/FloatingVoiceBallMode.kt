package com.ai.assistance.operit.ui.floating.ui.ball

import androidx.compose.runtime.Composable
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.FloatingMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 
 * 渲染悬浮窗的语音球模式界面 - Siri风格动感球体
 * 逻辑已提取到 SiriBall.kt
 * 点击直接进入全屏语音模式
 */
@Composable
fun FloatingVoiceBallMode(floatContext: FloatContext) {
    SiriBall(
        floatContext = floatContext,
        onClick = {
            floatContext.onModeChange(FloatingMode.FULLSCREEN)
        },
        onTriggerResult = {
            // 切换到结果展示模式显示结果
            floatContext.onModeChange(FloatingMode.RESULT_DISPLAY)
            
            // 3秒后自动切回球模式
            floatContext.coroutineScope.launch {
                delay(3000)
                // 只有当前还是结果展示模式时才切回（避免用户已经切换到其他模式）
                if (floatContext.currentMode == FloatingMode.RESULT_DISPLAY) {
                    floatContext.onModeChange(FloatingMode.BALL)
                }
            }
        }
    )
}

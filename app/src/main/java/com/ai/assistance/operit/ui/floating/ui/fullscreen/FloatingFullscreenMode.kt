package com.ai.assistance.operit.ui.floating.ui.fullscreen

import androidx.compose.runtime.Composable
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.ui.fullscreen.screen.FloatingFullscreenMode as FloatingFullscreenScreen

/**
 * 全屏浮动窗口模式 - 主入口文件
 * 
 * 该文件作为向后兼容的入口点，将实际实现委托给重构后的组件结构：
 * - screen/FloatingFullscreenScreen.kt - 主屏幕组件和流程编排
 * - viewmodel/FloatingFullscreenModeViewModel.kt - 状态管理和业务逻辑
 * - components/ - 可复用的UI组件
 *   - MessageDisplay.kt - 消息显示
 *   - WaveVisualizerSection.kt - 波浪可视化和头像
 *   - EditPanel.kt - 编辑面板
 *   - BottomControlBar.kt - 底部控制栏
 */
@Composable
fun FloatingFullscreenMode(floatContext: FloatContext) {
    // 委托给实际的实现
    FloatingFullscreenScreen(floatContext)
}

package com.ai.assistance.operit.ui.floating.ui.window.viewmodel

import androidx.compose.runtime.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.ui.window.models.ResizeEdge

/** 窗口模式的状态数据类 */
data class DraggableWindowState(
    val width: Dp,
    val height: Dp,
    val scale: Float,
)

/** FloatingChatWindowMode 的 ViewModel，管理窗口相关的状态和逻辑 */
class FloatingChatWindowModeViewModel(
    private val floatContext: FloatContext
) {
    // 窗口状态
    var windowState by mutableStateOf(
        DraggableWindowState(
            width = floatContext.windowWidthState,
            height = floatContext.windowHeightState,
            scale = floatContext.windowScale
        )
    )
        private set

    // 拖动状态
    var isDragging by mutableStateOf(false)
        private set

    // 标题栏悬停状态
    var titleBarHover by mutableStateOf(false)

    // 按钮状态
    var closeButtonPressed by mutableStateOf(false)
    var minimizeHover by mutableStateOf(false)
    var closeHover by mutableStateOf(false)

    /**
     * 同步窗口状态
     * 当用户不在拖动时，从 floatContext 同步状态
     */
    fun syncWindowState() {
        if (!isDragging) {
            windowState = DraggableWindowState(
                width = floatContext.windowWidthState,
                height = floatContext.windowHeightState,
                scale = floatContext.windowScale
            )
        }
    }

    /**
     * 开始拖动
     */
    fun startDragging() {
        isDragging = true
    }

    /**
     * 结束拖动
     */
    fun endDragging() {
        isDragging = false
    }

    /**
     * 开始边缘调整大小
     */
    fun startEdgeResize() {
        isDragging = true
        floatContext.isEdgeResizing = true
    }

    /**
     * 结束边缘调整大小
     */
    fun endEdgeResize() {
        isDragging = false
        floatContext.isEdgeResizing = false
        floatContext.saveWindowState?.invoke()
    }

    /**
     * 处理窗口大小调整
     */
    fun handleResize(newWidth: Dp, newHeight: Dp) {
        val maxWidth = (floatContext.screenWidth * 0.8f).coerceAtLeast(150.dp)
        val maxHeight = (floatContext.screenHeight * 0.8f).coerceAtLeast(200.dp)
        val constrainedWidth = newWidth.coerceIn(150.dp, maxWidth)
        val constrainedHeight = newHeight.coerceIn(200.dp, maxHeight)

        windowState = windowState.copy(
            width = constrainedWidth,
            height = constrainedHeight
        )
        floatContext.onResize(constrainedWidth, constrainedHeight)
    }

    /**
     * 处理缩放变化
     */
    fun handleScaleChange(newScale: Float) {
        val oldScale = windowState.scale
        val constrainedScale = newScale.coerceIn(0.3f, 1.0f)

        if (kotlin.math.abs(oldScale - constrainedScale) < 0.01f) {
            return
        }

        windowState = windowState.copy(scale = constrainedScale)
        floatContext.onScaleChange(constrainedScale)
    }

    /**
     * 切换缩放比例（点击缩放按钮时）
     */
    fun toggleScale() {
        val newScale = when {
            windowState.scale <= 0.3f -> 0.5f
            windowState.scale <= 0.5f -> 0.7f
            windowState.scale <= 0.7f -> 1.0f
            else -> 0.3f
        }
        handleScaleChange(newScale)
    }

    /**
     * 处理窗口移动
     */
    fun handleMove(dx: Float, dy: Float) {
        floatContext.onMove(dx, dy, windowState.scale)
    }

    /**
     * 切换附件面板
     */
    fun toggleAttachmentPanel() {
        floatContext.showAttachmentPanel = !floatContext.showAttachmentPanel
    }

    /**
     * 显示输入对话框
     */
    fun showInputDialog() {
        floatContext.showInputDialog = true
    }

    /**
     * 隐藏输入对话框
     */
    fun hideInputDialog() {
        floatContext.showInputDialog = false
        floatContext.showAttachmentPanel = false
    }
}

/**
 * 创建并记住 ViewModel 实例
 */
@Composable
fun rememberFloatingChatWindowModeViewModel(
    floatContext: FloatContext
): FloatingChatWindowModeViewModel {
    return remember(floatContext) {
        FloatingChatWindowModeViewModel(floatContext)
    }
}


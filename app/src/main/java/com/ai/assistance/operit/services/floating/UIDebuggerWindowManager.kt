package com.ai.assistance.operit.services.floating

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.services.ServiceLifecycleOwner
import com.ai.assistance.operit.services.UIDebuggerService
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.UIDebuggerOverlay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner

class UIDebuggerWindowManager(
    private val context: Context,
    private val viewModelStoreOwner: ViewModelStoreOwner,
    private val lifecycleOwner: LifecycleOwner
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var isExpanded = mutableStateOf(false)
    
    // Floating ball position state
    private var ballX = mutableStateOf(100f)
    private var ballY = mutableStateOf(100f)

    fun show() {
        if (composeView != null) return

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Start with floating ball size
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ballX.value.toInt()
            y = ballY.value.toInt()
        }

        composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner as SavedStateRegistryOwner)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)

            setContent {
                UIDebuggerFloatingContent(
                    viewModelStoreOwner = viewModelStoreOwner,
                    isExpanded = isExpanded.value,
                    ballX = ballX.value,
                    ballY = ballY.value,
                    onBallDrag = { deltaX, deltaY ->
                        ballX.value += deltaX
                        ballY.value += deltaY
                        updateWindowPosition()
                    },
                    onToggleExpand = {
                        isExpanded.value = !isExpanded.value
                        updateWindowLayout()
                    },
                    onClose = {
                        (context as? UIDebuggerService)?.stopSelf()
                    }
                )
            }
        }
        windowManager.addView(composeView, params)
    }
    
    private fun updateWindowPosition() {
        params?.let { layoutParams ->
            layoutParams.x = ballX.value.toInt()
            layoutParams.y = ballY.value.toInt()
            composeView?.let { view ->
                windowManager.updateViewLayout(view, layoutParams)
            }
        }
    }
    
    private fun updateWindowLayout() {
        params?.let { layoutParams ->
            if (isExpanded.value) {
                // Expand to full screen
                layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
                layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
                layoutParams.x = 0
                layoutParams.y = 0
            } else {
                // Shrink back to floating ball
                layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                layoutParams.x = ballX.value.toInt()
                layoutParams.y = ballY.value.toInt()
            }
            composeView?.let { view ->
                windowManager.updateViewLayout(view, layoutParams)
            }
        }
    }

    fun remove() {
        composeView?.let {
            windowManager.removeView(it)
            composeView = null
        }
    }
}

@Composable
fun UIDebuggerFloatingContent(
    viewModelStoreOwner: ViewModelStoreOwner,
    isExpanded: Boolean,
    ballX: Float,
    ballY: Float,
    onBallDrag: (Float, Float) -> Unit,
    onToggleExpand: () -> Unit,
    onClose: () -> Unit
) {
    if (isExpanded) {
        // Full screen analysis mode
        UIDebuggerOverlay(
            viewModelStoreOwner = viewModelStoreOwner,
            onClose = onClose,
            onMinimize = onToggleExpand
        )
    } else {
        // Floating ball mode
        DraggableFloatingBall(
            onDrag = onBallDrag,
            onTap = onToggleExpand
        )
    }
}

@Composable
fun DraggableFloatingBall(
    onDrag: (Float, Float) -> Unit,
    onTap: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.Blue.copy(alpha = 0.8f))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    onTap()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Build,
            contentDescription = "UI Debugger",
            tint = Color.White
        )
    }
} 
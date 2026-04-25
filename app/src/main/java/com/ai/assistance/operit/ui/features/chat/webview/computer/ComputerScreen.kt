package com.ai.assistance.operit.ui.features.chat.webview.computer

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.rememberTerminalEnv
import com.ai.assistance.operit.terminal.main.TerminalScreen

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ComputerScreen() {
    val context = LocalContext.current
    
    // Create a TerminalManager and TerminalEnv instance for the terminal
    val terminalManager = remember { TerminalManager.getInstance(context) }
    val terminalEnv = rememberTerminalEnv(terminalManager)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 移除背景色，避免遮挡 SurfaceView
            // .background(Color.Black) 
            // 使用 pointerInput 拦截触摸事件，防止穿透到下层聊天界面
            // 相比 clickable，这种方式对无障碍服务更友好，不会阻止内部组件的无障碍访问
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { /* 消费点击事件 */ },
                    onDoubleTap = { /* 消费双击事件 */ },
                    onLongPress = { /* 消费长按事件 */ },
                    onPress = { /* 消费按压事件 */ }
                )
            }
    ) {
        // Show the terminal interface instead of the web desktop
        TerminalScreen(env = terminalEnv)
    }
} 
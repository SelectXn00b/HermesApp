package com.ai.assistance.operit.ui.features.toolbox.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.ai.assistance.operit.ui.features.toolbox.screens.processlimit.ProcessLimitRemoverScreen

/**
 * 解除进程限制工具屏幕包装器
 */
@Composable
fun ProcessLimitRemoverToolScreen(navController: NavController) {
    ProcessLimitRemoverScreen(navController = navController)
}


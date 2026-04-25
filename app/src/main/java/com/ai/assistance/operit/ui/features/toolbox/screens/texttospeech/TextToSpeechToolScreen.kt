package com.ai.assistance.operit.ui.features.toolbox.screens.texttospeech

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.ai.assistance.operit.ui.components.CustomScaffold

/** 文本转语音工具屏幕包装器 用于在路由系统中显示TextToSpeechScreen */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToSpeechToolScreen(navController: NavController) {
    CustomScaffold { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            TextToSpeechScreen(navController = navController)
        }
    }
}

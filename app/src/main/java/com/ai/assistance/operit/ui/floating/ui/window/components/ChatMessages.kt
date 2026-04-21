package com.ai.assistance.operit.ui.floating.ui.window.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.features.chat.components.style.cursor.CursorStyleChatMessage

/** 单个消息项组件 将消息渲染逻辑提取到单独的组件，减少重组范围 */
@Composable
fun MessageItem(
        index: Int,
        message: ChatMessage,
        isLastAiMessage: Boolean,
        userMessageColor: Color,
        aiMessageColor: Color,
        userTextColor: Color,
        aiTextColor: Color,
        systemMessageColor: Color,
        systemTextColor: Color,
        thinkingBackgroundColor: Color,
        thinkingTextColor: Color,
        onSelectMessageToEdit: ((Int, ChatMessage) -> Unit)?,
        onCopyMessage: ((ChatMessage) -> Unit)?
) {
        // 添加Context以访问剪贴板服务
        val context = LocalContext.current

        // 编辑模式下为消息添加点击功能
        val messageModifier = Modifier

        Box(modifier = messageModifier) {
                val streamToRender = if (isLastAiMessage) message.contentStream else null

                CursorStyleChatMessage(
                        message = message,
                        userMessageColor = userMessageColor,
                        aiMessageColor = aiMessageColor,
                        userTextColor = userTextColor,
                        aiTextColor = aiTextColor,
                        systemMessageColor = systemMessageColor,
                        systemTextColor = systemTextColor,
                        thinkingBackgroundColor = thinkingBackgroundColor,
                        thinkingTextColor = thinkingTextColor,
                        supportToolMarkup = true,
                        initialThinkingExpanded = true,
                        overrideStream = streamToRender,
                        enableDialogs = false  // 在悬浮窗中禁用弹窗，避免闪退
                )
        }
}

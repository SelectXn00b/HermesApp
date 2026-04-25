package com.ai.assistance.operit.ui.floating.ui.fullscreen.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.chat.components.rememberChatMessageHeightMemory
import com.ai.assistance.operit.ui.features.chat.components.style.bubble.BubbleStyleChatMessage

/**
 * 消息显示组件
 * 显示用户消息和AI消息
 */
@Composable
fun MessageDisplay(
    messages: List<ChatMessage>,
    speechPreviewText: String,
    showSpeechOverlay: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val bubbleUserRoundedCornersEnabled by
        preferencesManager.bubbleUserRoundedCornersEnabled.collectAsState(initial = true)
    val bubbleAiRoundedCornersEnabled by
        preferencesManager.bubbleAiRoundedCornersEnabled.collectAsState(initial = true)
    val bubbleUserContentPaddingLeft by
        preferencesManager.bubbleUserContentPaddingLeft.collectAsState(initial = 12f)
    val bubbleUserContentPaddingRight by
        preferencesManager.bubbleUserContentPaddingRight.collectAsState(initial = 12f)
    val bubbleAiContentPaddingLeft by
        preferencesManager.bubbleAiContentPaddingLeft.collectAsState(initial = 12f)
    val bubbleAiContentPaddingRight by
        preferencesManager.bubbleAiContentPaddingRight.collectAsState(initial = 12f)
    val listState = rememberLazyListState()
    val displayMessages =
        messages
            .filter { it.sender != "think" }
            .asReversed()
    val messageHeightMemory = rememberChatMessageHeightMemory(displayMessages)

    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    val userMessageColor = MaterialTheme.colorScheme.primaryContainer
    val aiMessageColor = MaterialTheme.colorScheme.surface
    val userTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val aiTextColor = MaterialTheme.colorScheme.onSurface
    val systemMessageColor = MaterialTheme.colorScheme.surfaceVariant
    val systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val listAlpha by animateFloatAsState(targetValue = 1f)

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    val topFadePx = 96.dp.toPx()
                    val bottomFadePx = 72.dp.toPx()
                    val heightPx = size.height.coerceAtLeast(1f)
                    val topFrac = (topFadePx / heightPx).coerceIn(0f, 1f)
                    val bottomFrac = (bottomFadePx / heightPx).coerceIn(0f, 1f)
                    val bottomStart = (1f - bottomFrac).coerceIn(0f, 1f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                topFrac to Color.White,
                                bottomStart to Color.White,
                                1f to Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
                .graphicsLayer(alpha = listAlpha),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp)
        ) {
            item(key = "bottomSpacer") {
                Spacer(modifier = Modifier.height(60.dp))
            }

            items(
                items = displayMessages,
                key = { it.timestamp }
            ) { message ->
                BubbleStyleChatMessage(
                    message = message,
                    userMessageColor = userMessageColor,
                    aiMessageColor = aiMessageColor,
                    userTextColor = userTextColor,
                    aiTextColor = aiTextColor,
                    systemMessageColor = systemMessageColor,
                    systemTextColor = systemTextColor,
                    bubbleUserRoundedCornersEnabled = bubbleUserRoundedCornersEnabled,
                    bubbleAiRoundedCornersEnabled = bubbleAiRoundedCornersEnabled,
                    bubbleUserContentPaddingLeft = bubbleUserContentPaddingLeft,
                    bubbleUserContentPaddingRight = bubbleUserContentPaddingRight,
                    bubbleAiContentPaddingLeft = bubbleAiContentPaddingLeft,
                    bubbleAiContentPaddingRight = bubbleAiContentPaddingRight,
                    heightMemory = messageHeightMemory,
                    enableDialogs = false
                )
            }
        }

        AnimatedVisibility(
            visible = showSpeechOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (speechPreviewText.isNotBlank()) {
                    BubbleStyleChatMessage(
                        message = ChatMessage(sender = "user", content = speechPreviewText, timestamp = 0L),
                        userMessageColor = userMessageColor,
                        aiMessageColor = aiMessageColor,
                        userTextColor = userTextColor,
                        aiTextColor = aiTextColor,
                        systemMessageColor = systemMessageColor,
                        systemTextColor = systemTextColor,
                        bubbleUserRoundedCornersEnabled = bubbleUserRoundedCornersEnabled,
                        bubbleAiRoundedCornersEnabled = bubbleAiRoundedCornersEnabled,
                        bubbleUserContentPaddingLeft = bubbleUserContentPaddingLeft,
                        bubbleUserContentPaddingRight = bubbleUserContentPaddingRight,
                        bubbleAiContentPaddingLeft = bubbleAiContentPaddingLeft,
                        bubbleAiContentPaddingRight = bubbleAiContentPaddingRight,
                        enableDialogs = false
                    )
                }
            }
        }
    }
}

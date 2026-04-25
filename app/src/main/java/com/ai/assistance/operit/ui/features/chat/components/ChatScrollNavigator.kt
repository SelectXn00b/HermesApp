package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.features.chat.components.lazy.LazyListState as ChatLazyListState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.sqrt

private data class ChatScrollNavigatorSnapshot(
    val centeredMessageIndex: Int?,
    val isScrollInProgress: Boolean,
)

internal data class ChatScrollMessageAnchor(
    val absoluteTopPx: Float,
    val heightPx: Int,
)

private data class ChatMessageLocatorEntry(
    val index: Int,
    val message: ChatMessage,
)

@Composable
internal fun ChatScrollNavigator(
    chatHistory: List<ChatMessage>,
    scrollState: ChatLazyListState,
    minVisibleIndex: Int,
    visibleMessageCount: Int,
    onJumpToMessage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chatHistory.size <= 1 || visibleMessageCount <= 0) {
        return
    }

    val isDragged by scrollState.interactionSource.collectIsDraggedAsState()
    val currentIsDragged by rememberUpdatedState(isDragged)
    var showNavigatorChip by remember { mutableStateOf(false) }
    var userScrollSessionActive by remember { mutableStateOf(false) }
    var showLocatorDialog by remember { mutableStateOf(false) }
    var currentMessageIndex by remember(chatHistory) {
        mutableStateOf(chatHistory.lastIndex.takeIf { it >= 0 })
    }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            userScrollSessionActive = true
            showNavigatorChip = true
        }
    }

    LaunchedEffect(scrollState, minVisibleIndex, visibleMessageCount, chatHistory.size) {
        snapshotFlow {
            ChatScrollNavigatorSnapshot(
                centeredMessageIndex =
                    resolveCenteredMessageIndex(
                        scrollState = scrollState,
                        minVisibleIndex = minVisibleIndex,
                        visibleMessageCount = visibleMessageCount,
                        totalMessageCount = chatHistory.size,
                    ),
                isScrollInProgress = scrollState.isScrollInProgress,
            )
        }.collectLatest { snapshot ->
            snapshot.centeredMessageIndex?.let { currentMessageIndex = it }
            if (!userScrollSessionActive) {
                return@collectLatest
            }
            if (snapshot.isScrollInProgress || currentIsDragged) {
                showNavigatorChip = true
                return@collectLatest
            }
            delay(650)
            if (!scrollState.isScrollInProgress && !currentIsDragged) {
                showNavigatorChip = false
                userScrollSessionActive = false
            }
        }
    }

    val activeMessageIndex = currentMessageIndex

    AnimatedVisibility(
        visible = showNavigatorChip && activeMessageIndex != null,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(180)) + slideInHorizontally(initialOffsetX = { it / 2 }),
        exit = fadeOut(animationSpec = tween(120)) + slideOutHorizontally(targetOffsetX = { it / 2 }),
    ) {
        val bubbleColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f)
        val anchorLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        val anchorDotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
        val progress =
            if (chatHistory.size <= 1) {
                1f
            } else {
                (activeMessageIndex!!.toFloat() / (chatHistory.lastIndex).toFloat()).coerceIn(0f, 1f)
            }

        Row(
            modifier =
                Modifier.clickable {
                    showLocatorDialog = true
                    showNavigatorChip = false
                    userScrollSessionActive = false
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape =
                    RoundedCornerShape(
                        topStart = 14.dp,
                        bottomStart = 14.dp,
                        topEnd = 10.dp,
                        bottomEnd = 10.dp,
                    ),
                color = bubbleColor,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                border =
                    BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f),
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(20.dp)
                            .height(58.dp)
                            .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(width = 8.dp, height = 34.dp)) {
                        val centerX = size.width / 2f
                        val topY = 2.dp.toPx()
                        val bottomY = size.height - 2.dp.toPx()
                        val dotCenterY = topY + (bottomY - topY) * progress
                        drawLine(
                            color = anchorLineColor,
                            start = Offset(centerX, topY),
                            end = Offset(centerX, bottomY),
                            strokeWidth = 1.5.dp.toPx(),
                        )
                        drawCircle(
                            color = anchorDotColor,
                            radius = 3.dp.toPx(),
                            center = Offset(centerX, dotCenterY),
                        )
                    }
                }
            }

            Canvas(
                modifier =
                    Modifier
                        .offset(x = (-1).dp)
                        .size(width = 9.dp, height = 18.dp),
            ) {
                val arrowPath =
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(0f, size.height)
                        close()
                    }
                drawPath(path = arrowPath, color = bubbleColor)
            }
        }
    }

    if (showLocatorDialog && activeMessageIndex != null) {
        ChatMessageLocatorDialog(
            chatHistory = chatHistory,
            currentMessageIndex = activeMessageIndex,
            onDismiss = { showLocatorDialog = false },
            onJumpToMessage = { targetIndex ->
                showLocatorDialog = false
                onJumpToMessage(targetIndex)
            },
        )
    }
}

@Composable
internal fun ChatScrollNavigator(
    chatHistory: List<ChatMessage>,
    scrollState: ScrollState,
    messageAnchors: Map<Int, ChatScrollMessageAnchor>,
    viewportHeightPx: Int,
    onJumpToMessage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chatHistory.size <= 1 || viewportHeightPx <= 0) {
        return
    }

    val isDragged by scrollState.interactionSource.collectIsDraggedAsState()
    val currentIsDragged by rememberUpdatedState(isDragged)
    var showNavigatorChip by remember { mutableStateOf(false) }
    var userScrollSessionActive by remember { mutableStateOf(false) }
    var showLocatorDialog by remember { mutableStateOf(false) }
    var currentMessageIndex by remember(chatHistory) {
        mutableStateOf(chatHistory.lastIndex.takeIf { it >= 0 })
    }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            userScrollSessionActive = true
            showNavigatorChip = true
        }
    }

    LaunchedEffect(scrollState, viewportHeightPx, chatHistory.size) {
        snapshotFlow {
            ChatScrollNavigatorSnapshot(
                centeredMessageIndex =
                    resolveCenteredMessageIndex(
                        scrollState = scrollState,
                        viewportHeightPx = viewportHeightPx,
                        messageAnchors = messageAnchors,
                        totalMessageCount = chatHistory.size,
                    ),
                isScrollInProgress = scrollState.isScrollInProgress,
            )
        }.collectLatest { snapshot ->
            snapshot.centeredMessageIndex?.let { currentMessageIndex = it }
            if (!userScrollSessionActive) {
                return@collectLatest
            }
            if (snapshot.isScrollInProgress || currentIsDragged) {
                showNavigatorChip = true
                return@collectLatest
            }
            delay(650)
            if (!scrollState.isScrollInProgress && !currentIsDragged) {
                showNavigatorChip = false
                userScrollSessionActive = false
            }
        }
    }

    val activeMessageIndex = currentMessageIndex

    AnimatedVisibility(
        visible = showNavigatorChip && activeMessageIndex != null,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(180)) + slideInHorizontally(initialOffsetX = { it / 2 }),
        exit = fadeOut(animationSpec = tween(120)) + slideOutHorizontally(targetOffsetX = { it / 2 }),
    ) {
        val bubbleColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f)
        val anchorLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        val anchorDotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
        val progress =
            if (chatHistory.size <= 1) {
                1f
            } else {
                (activeMessageIndex!!.toFloat() / (chatHistory.lastIndex).toFloat()).coerceIn(0f, 1f)
            }

        Row(
            modifier =
                Modifier.clickable {
                    showLocatorDialog = true
                    showNavigatorChip = false
                    userScrollSessionActive = false
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape =
                    RoundedCornerShape(
                        topStart = 14.dp,
                        bottomStart = 14.dp,
                        topEnd = 10.dp,
                        bottomEnd = 10.dp,
                    ),
                color = bubbleColor,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                border =
                    BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f),
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(20.dp)
                            .height(58.dp)
                            .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(width = 8.dp, height = 34.dp)) {
                        val centerX = size.width / 2f
                        val topY = 2.dp.toPx()
                        val bottomY = size.height - 2.dp.toPx()
                        val dotCenterY = topY + (bottomY - topY) * progress
                        drawLine(
                            color = anchorLineColor,
                            start = Offset(centerX, topY),
                            end = Offset(centerX, bottomY),
                            strokeWidth = 1.5.dp.toPx(),
                        )
                        drawCircle(
                            color = anchorDotColor,
                            radius = 3.dp.toPx(),
                            center = Offset(centerX, dotCenterY),
                        )
                    }
                }
            }

            Canvas(
                modifier =
                    Modifier
                        .offset(x = (-1).dp)
                        .size(width = 9.dp, height = 18.dp),
            ) {
                val arrowPath =
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(0f, size.height)
                        close()
                    }
                drawPath(path = arrowPath, color = bubbleColor)
            }
        }
    }

    if (showLocatorDialog && activeMessageIndex != null) {
        ChatMessageLocatorDialog(
            chatHistory = chatHistory,
            currentMessageIndex = activeMessageIndex,
            onDismiss = { showLocatorDialog = false },
            onJumpToMessage = { targetIndex ->
                showLocatorDialog = false
                onJumpToMessage(targetIndex)
            },
        )
    }
}

@Composable
private fun ChatMessageLocatorDialog(
    chatHistory: List<ChatMessage>,
    currentMessageIndex: Int,
    onDismiss: () -> Unit,
    onJumpToMessage: (Int) -> Unit,
) {
    val initialIndex = (currentMessageIndex - 2).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    var searchQuery by remember { mutableStateOf("") }
    val normalizedSearchQuery = normalizeMessageSearchText(searchQuery)
    val locatorEntries =
        chatHistory.mapIndexed { index, message ->
            ChatMessageLocatorEntry(index = index, message = message)
        }
    val filteredEntries =
        if (normalizedSearchQuery.isBlank()) {
            locatorEntries
        } else {
            locatorEntries.filter { entry ->
                normalizeMessageSearchText(entry.message.content).contains(
                    normalizedSearchQuery,
                    ignoreCase = true,
                )
            }
        }
    val maxMessageLength =
        remember(chatHistory) {
            chatHistory.maxOfOrNull { messageContentLength(it) }?.coerceAtLeast(1) ?: 1
        }

    LaunchedEffect(normalizedSearchQuery, filteredEntries.size, currentMessageIndex) {
        if (filteredEntries.isEmpty()) {
            return@LaunchedEffect
        }

        val targetListIndex =
            if (normalizedSearchQuery.isBlank()) {
                filteredEntries.indexOfFirst { it.index == currentMessageIndex }
                    .takeIf { it >= 0 }
                    ?.let { (it - 2).coerceAtLeast(0) }
                    ?: 0
            } else {
                filteredEntries.indices.minByOrNull { entryIndex ->
                    abs(filteredEntries[entryIndex].index - currentMessageIndex)
                } ?: 0
            }
        listState.scrollToItem(targetListIndex)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.chat_message_locator_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.chat_message_locator_current,
                                    currentMessageIndex + 1,
                                    chatHistory.size,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.close))
                    }
                }

                Text(
                    text = stringResource(R.string.chat_message_locator_search_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(text = stringResource(R.string.chat_message_locator_search_placeholder))
                    },
                )

                if (normalizedSearchQuery.isBlank()) {
                    Text(
                        text = stringResource(R.string.chat_message_locator_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (filteredEntries.isNotEmpty()) {
                    Text(
                        text =
                            stringResource(
                                R.string.chat_message_locator_search_results,
                                filteredEntries.size,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (filteredEntries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_message_locator_search_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = filteredEntries,
                            key = { _, entry -> "${entry.message.timestamp}_${entry.index}" },
                        ) { _, entry ->
                            ChatMessageLocatorRow(
                                index = entry.index,
                                message = entry.message,
                                isCurrent = entry.index == currentMessageIndex,
                                maxMessageLength = maxMessageLength,
                                searchQuery = searchQuery,
                                onClick = { onJumpToMessage(entry.index) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageLocatorRow(
    index: Int,
    message: ChatMessage,
    isCurrent: Boolean,
    maxMessageLength: Int,
    searchQuery: String,
    onClick: () -> Unit,
) {
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (fillColor, rawPreviewTextColor, fillAlpha) =
        if (isDarkSurface) {
            when (message.sender) {
                "user" ->
                    Triple(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        0.9f,
                    )
                "summary" ->
                    Triple(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                        0.9f,
                    )
                "system" ->
                    Triple(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        0.9f,
                    )
                "think" ->
                    Triple(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        0.9f,
                    )
                else ->
                    Triple(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        0.9f,
                    )
            }
        } else {
            when (message.sender) {
                "user" ->
                    Triple(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        0.98f,
                    )
                "ai" ->
                    Triple(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                        0.98f,
                    )
                "summary" ->
                    Triple(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        0.92f,
                    )
                "system" ->
                    Triple(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        0.86f,
                    )
                "think" ->
                    Triple(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        0.78f,
                    )
                else ->
                    Triple(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        0.9f,
                    )
            }
        }
    val previewTextColor =
        if (isDarkSurface) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = if (isCurrent) 0.96f else 0.88f)
        } else {
            rawPreviewTextColor
        }
    val containerColor =
        if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    val borderColor =
        if (isCurrent) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        }
    val messageLength = messageContentLength(message)
    val previewText = buildMessagePreview(message = message, searchQuery = searchQuery)

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(width = 1.dp, color = borderColor),
        tonalElevation = if (isCurrent) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.width(64.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(senderLabelRes(message.sender)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.42f)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(messageBarFraction(messageLength, maxMessageLength))
                            .clip(RoundedCornerShape(12.dp))
                            .background(fillColor.copy(alpha = fillAlpha)),
                )
                Text(
                    text = previewText,
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(horizontal = 10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = previewTextColor,
                )
            }

            Text(
                text = messageLength.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun resolveCenteredMessageIndex(
    scrollState: ChatLazyListState,
    minVisibleIndex: Int,
    visibleMessageCount: Int,
    totalMessageCount: Int,
): Int? {
    if (visibleMessageCount <= 0 || totalMessageCount <= 0) {
        return null
    }
    val layoutInfo = scrollState.layoutInfo
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val visibleMessageItem =
        layoutInfo.visibleItemsInfo
            .filter { it.index in 0 until visibleMessageCount }
            .minByOrNull { item ->
                abs((item.offset + item.size / 2f) - viewportCenter)
            } ?: return null

    return (minVisibleIndex + visibleMessageItem.index).coerceIn(0, totalMessageCount - 1)
}

private fun resolveCenteredMessageIndex(
    scrollState: ScrollState,
    viewportHeightPx: Int,
    messageAnchors: Map<Int, ChatScrollMessageAnchor>,
    totalMessageCount: Int,
): Int? {
    if (viewportHeightPx <= 0 || totalMessageCount <= 0 || messageAnchors.isEmpty()) {
        return null
    }

    val viewportCenter = scrollState.value + viewportHeightPx / 2f
    val centeredMessage =
        messageAnchors
            .entries
            .filter { it.key in 0 until totalMessageCount }
            .minByOrNull { (_, anchor) ->
                abs((anchor.absoluteTopPx + anchor.heightPx / 2f) - viewportCenter)
            } ?: return null

    return centeredMessage.key.coerceIn(0, totalMessageCount - 1)
}

private fun senderLabelRes(sender: String): Int =
    when (sender) {
        "user" -> R.string.chat_sender_user
        "ai" -> R.string.chat_sender_ai
        "summary" -> R.string.chat_sender_summary
        "system" -> R.string.chat_sender_system
        "think" -> R.string.chat_sender_think
        else -> R.string.chat_sender_other
    }

private fun messageContentLength(message: ChatMessage): Int = message.content.length.coerceAtLeast(1)

private fun messageBarFraction(messageLength: Int, maxMessageLength: Int): Float {
    if (maxMessageLength <= 0) {
        return 0.18f
    }
    return sqrt(messageLength.toFloat() / maxMessageLength.toFloat()).coerceIn(0.18f, 1f)
}

private fun buildMessagePreview(
    message: ChatMessage,
    searchQuery: String = "",
): String {
    val content = normalizeMessageSearchText(message.content)
    if (content.isEmpty()) {
        return message.sender
    }

    val normalizedSearchQuery = normalizeMessageSearchText(searchQuery)
    if (normalizedSearchQuery.isNotEmpty()) {
        val matchIndex = content.indexOf(normalizedSearchQuery, ignoreCase = true)
        if (matchIndex >= 0) {
            val previewLength = 72
            val preferredStart = (matchIndex - 18).coerceAtLeast(0)
            val start = preferredStart.coerceAtMost((content.length - previewLength).coerceAtLeast(0))
            val end = (start + previewLength).coerceAtMost(content.length)
            val prefix = if (start > 0) "..." else ""
            val suffix = if (end < content.length) "..." else ""
            val snippet = content.substring(start, end).trim()
            if (snippet.isNotEmpty()) {
                return prefix + snippet + suffix
            }
        }
    }

    return content.take(72).let { preview ->
        if (preview.length < content.length) {
            preview.trimEnd() + "..."
        } else {
            preview
        }
    }
}

private fun normalizeMessageSearchText(text: String): String {
    if (text.isEmpty()) {
        return ""
    }

    val previewBuilder = StringBuilder(text.length)
    var pendingWhitespace = false

    for (char in text) {
        val normalizedChar =
            when (char) {
                '\n', '\r', '\t' -> ' '
                else -> char
            }
        if (normalizedChar.isWhitespace()) {
            pendingWhitespace = previewBuilder.isNotEmpty()
            continue
        }
        if (pendingWhitespace) {
            previewBuilder.append(' ')
            pendingWhitespace = false
        }
        previewBuilder.append(normalizedChar)
    }

    return previewBuilder.toString().trim()
}

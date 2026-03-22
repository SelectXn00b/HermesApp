/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.androidforclaw.ui.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomo.androidforclaw.ui.session.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Chat interface - Inspired by Stream Chat Android UI design style
 *
 * Features:
 * - Markdown rendering (headings, bold, italic, code blocks, lists, quotes)
 * - Long message collapse/expand
 * - Long press to copy
 * - Wider message bubbles
 * - Auto-scroll to bottom
 */

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val isToolCall: Boolean = false
)

enum class MessageStatus {
    SENDING,
    SENT,
    ERROR
}

/** Max chars before collapsing */
private const val COLLAPSE_THRESHOLD = 300

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    sessions: List<SessionManager.Session> = emptyList(),
    currentSession: SessionManager.Session? = null,
    onSessionChange: (String) -> Unit = {},
    onNewSession: () -> Unit = {},
    onDeleteSession: ((String) -> Unit)? = null,
    onCheckUpdate: (() -> Unit)? = null
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom
    // Session change or first load: jump instantly. New message: animate.
    var lastMessageCount by remember { mutableStateOf(0) }
    var lastSessionId by remember { mutableStateOf(currentSession?.id) }
    LaunchedEffect(messages.size, currentSession?.id) {
        if (messages.isNotEmpty()) {
            val sessionChanged = currentSession?.id != lastSessionId
            val isNewMessage = messages.size > lastMessageCount && !sessionChanged

            if (isNewMessage && lastMessageCount > 0) {
                // New message in same session — smooth scroll
                listState.animateScrollToItem(messages.size - 1)
            } else {
                // First load or session switch — jump directly
                listState.scrollToItem(messages.size - 1)
            }

            lastMessageCount = messages.size
            lastSessionId = currentSession?.id
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // Session control bar
        if (sessions.isNotEmpty()) {
            SessionControlBar(
                sessions = sessions,
                currentSession = currentSession,
                onSessionChange = onSessionChange,
                onNewSession = onNewSession,
                onDeleteSession = onDeleteSession,
                onCheckUpdate = onCheckUpdate,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Message list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "👋", style = TextStyle(fontSize = 48.sp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "开始聊天",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF000000)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "向 AI 助手发送消息来控制手机",
                        style = TextStyle(fontSize = 14.sp, color = Color(0xFF999999))
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageItem(message = message)
                    }
                }
            }

        }

        // Divider
        Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)

        // Message input box
        MessageComposer(
            value = inputText,
            onValueChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    onSendMessage(inputText)
                    inputText = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ============================================================================
// Message Item with Markdown + Collapse + Long-press Copy
// ============================================================================

@Composable
private fun ToolCallItem(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isResult = message.content.startsWith("执行完成")
    val icon = if (isResult) "✅" else "🔧"
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant

    val isLong = message.content.length > COLLAPSE_THRESHOLD
    var expanded by remember { mutableStateOf(false) }
    val displayContent = if (isLong && !expanded) message.content.take(COLLAPSE_THRESHOLD) + "..." else message.content

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Small icon
        Text(
            text = icon,
            style = TextStyle(fontSize = 13.sp),
            modifier = Modifier.padding(top = 6.dp, end = 6.dp)
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = surfaceColor,
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                    text = displayContent,
                    style = TextStyle(
                        color = onSurfaceColor,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
                if (isLong) {
                    Text(
                        text = if (expanded) "收起 ▲" else "展开 ▼",
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier
                            .clickable { expanded = !expanded }
                            .padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    if (message.isToolCall) {
        ToolCallItem(message = message, modifier = modifier)
        return
    }

    val context = LocalContext.current
    val isLong = message.content.length > COLLAPSE_THRESHOLD
    var expanded by remember { mutableStateOf(false) }
    var showCopyHint by remember { mutableStateOf(false) }

    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val backgroundColor = if (message.isUser) Color(0xFF005FFF) else Color.White
    val textColor = if (message.isUser) Color.White else Color(0xFF1A1A1A)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        // AI avatar (left)
        if (!message.isUser) {
            Avatar(
                text = "AI",
                backgroundColor = Color(0xFF6C5CE7),
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        // Message bubble
        Column(
            horizontalAlignment = alignment,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (message.isUser) 18.dp else 4.dp,
                    bottomEnd = if (message.isUser) 4.dp else 18.dp
                ),
                color = backgroundColor,
                shadowElevation = if (message.isUser) 0.dp else 1.dp,
                modifier = Modifier
                    .widthIn(max = 320.dp) // Wider bubbles
                    .animateContentSize()
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                copyToClipboard(context, message.content)
                                showCopyHint = true
                            }
                        )
                ) {
                    // Message content
                    val displayContent = if (isLong && !expanded) {
                        message.content.take(COLLAPSE_THRESHOLD) + "..."
                    } else {
                        message.content
                    }

                    if (message.isUser) {
                        // User messages: plain text
                        Text(
                            text = displayContent,
                            style = TextStyle(
                                color = textColor,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        )
                    } else {
                        // AI messages: render Markdown
                        Text(
                            text = parseMarkdown(displayContent, textColor),
                            style = TextStyle(
                                color = textColor,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        )
                    }

                    // Expand/Collapse toggle for long messages
                    if (isLong) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (expanded) "收起 ▲" else "展开 ▼",
                            style = TextStyle(
                                color = if (message.isUser) Color.White.copy(alpha = 0.8f)
                                else Color(0xFF005FFF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.clickable { expanded = !expanded }
                        )
                    }

                    // Copy hint
                    if (showCopyHint) {
                        LaunchedEffect(showCopyHint) {
                            kotlinx.coroutines.delay(1500)
                            showCopyHint = false
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "✅ 已复制",
                            style = TextStyle(
                                color = textColor.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        )
                    }

                    // Timestamp and status
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTimestamp(message.timestamp),
                            style = TextStyle(
                                color = textColor.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        )

                        if (message.isUser) {
                            Spacer(modifier = Modifier.width(4.dp))
                            StatusIndicator(status = message.status, color = textColor)
                        }
                    }
                }
            }
        }

        // User avatar (right)
        if (message.isUser) {
            Avatar(
                text = "You",
                backgroundColor = Color(0xFF005FFF),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

// ============================================================================
// Markdown Parser (AnnotatedString based)
// ============================================================================

/**
 * Simple Markdown to AnnotatedString parser.
 *
 * Supports:
 * - ## Headings (H1-H3)
 * - **bold**
 * - *italic*
 * - `inline code`
 * - ```code blocks```
 * - - list items
 * - > blockquotes
 */
@Composable
fun parseMarkdown(text: String, textColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        var inCodeBlock = false
        var codeBlockContent = StringBuilder()

        for ((idx, line) in lines.withIndex()) {
            if (idx > 0 && !inCodeBlock) append("\n")

            // Code block start/end
            if (line.trimStart().startsWith("```")) {
                if (inCodeBlock) {
                    // End code block
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = textColor.copy(alpha = 0.9f),
                        background = textColor.copy(alpha = 0.08f)
                    )) {
                        append(codeBlockContent.toString())
                    }
                    codeBlockContent = StringBuilder()
                    inCodeBlock = false
                } else {
                    // Start code block
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                if (codeBlockContent.isNotEmpty()) codeBlockContent.append("\n")
                codeBlockContent.append(line)
                continue
            }

            // Headings
            when {
                line.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                        appendInlineMarkdown(line.removePrefix("### "), textColor)
                    }
                }
                line.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) {
                        appendInlineMarkdown(line.removePrefix("## "), textColor)
                    }
                }
                line.startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                        appendInlineMarkdown(line.removePrefix("# "), textColor)
                    }
                }
                // Blockquote
                line.startsWith("> ") -> {
                    withStyle(SpanStyle(
                        color = textColor.copy(alpha = 0.7f),
                        fontStyle = FontStyle.Italic
                    )) {
                        append("│ ")
                        appendInlineMarkdown(line.removePrefix("> "), textColor.copy(alpha = 0.7f))
                    }
                }
                // List items
                line.trimStart().startsWith("- ") -> {
                    val indent = line.length - line.trimStart().length
                    append(" ".repeat(indent))
                    append("• ")
                    appendInlineMarkdown(line.trimStart().removePrefix("- "), textColor)
                }
                line.trimStart().matches(Regex("^\\d+\\.\\s.*")) -> {
                    appendInlineMarkdown(line, textColor)
                }
                // Regular paragraph
                else -> {
                    appendInlineMarkdown(line, textColor)
                }
            }
        }

        // Unclosed code block
        if (inCodeBlock && codeBlockContent.isNotEmpty()) {
            withStyle(SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = textColor.copy(alpha = 0.9f),
                background = textColor.copy(alpha = 0.08f)
            )) {
                append(codeBlockContent.toString())
            }
        }
    }
}

/**
 * Parse inline markdown: **bold**, *italic*, `code`
 */
private fun AnnotatedString.Builder.appendInlineMarkdown(text: String, textColor: Color) {
    var i = 0
    while (i < text.length) {
        when {
            // Bold: **text**
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            // Inline code: `code`
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > 0) {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        background = textColor.copy(alpha = 0.1f)
                    )) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            // Italic: *text* (but not **)
            text[i] == '*' && (i + 1 < text.length && text[i + 1] != '*') -> {
                val end = text.indexOf('*', i + 1)
                if (end > 0) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}

// ============================================================================
// Shared Components
// ============================================================================

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

@Composable
fun Avatar(
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.take(2).uppercase(),
            style = TextStyle(
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
fun StatusIndicator(
    status: MessageStatus,
    color: Color
) {
    val iconText = when (status) {
        MessageStatus.SENDING -> "⏱"
        MessageStatus.SENT -> "✓"
        MessageStatus.ERROR -> "⚠"
    }
    Text(
        text = iconText,
        style = TextStyle(color = color.copy(alpha = 0.7f), fontSize = 10.sp)
    )
}

@Composable
fun MessageComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Input box
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 120.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFF7F7F7),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = Color(0xFFE0E0E0)
                )
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("chat_input"),
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = Color.Black,
                        lineHeight = 20.sp
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (value.isNotBlank()) onSend()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (value.isEmpty()) {
                                Text(
                                    text = "发送消息",
                                    style = TextStyle(fontSize = 15.sp, color = Color(0xFF999999))
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Send button
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .testTag("send_button"),
                shape = CircleShape,
                color = if (value.isNotBlank()) Color(0xFF005FFF) else Color(0xFFE0E0E0),
                onClick = {
                    if (value.isNotBlank()) onSend()
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送",
                        tint = if (value.isNotBlank()) Color.White else Color(0xFF999999),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

// ============================================================================
// Session Control Bar
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionControlBar(
    sessions: List<SessionManager.Session>,
    currentSession: SessionManager.Session?,
    onSessionChange: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: ((String) -> Unit)? = null,
    onCheckUpdate: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = modifier,
        color = Color(0xFFF7F7F7),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showSheet = true },
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = Color(0xFFE0E0E0)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentSession?.title ?: "新对话",
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = Color(0xFF333333),
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "选择会话",
                        tint = Color(0xFF666666),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color(0xFF005FFF),
                onClick = onNewSession
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新建会话",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // Session list bottom sheet
    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "会话列表",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333)
                        )
                    )
                    Text(
                        text = "${sessions.size} 个会话",
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFF999999))
                    )
                }

                HorizontalDivider(color = Color(0xFFF0F0F0))

                // Session items
                sessions.forEach { session ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    onSessionChange(session.id)
                                    showSheet = false
                                },
                                onLongClick = {
                                    if (onDeleteSession != null) {
                                        showDeleteConfirm = session.id
                                    }
                                }
                            ),
                        color = if (session.id == currentSession?.id) Color(0xFFF5F8FF) else Color.White
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Active indicator
                            if (session.id == currentSession?.id) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFF005FFF))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.title,
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = if (session.id == currentSession?.id)
                                            FontWeight.SemiBold else FontWeight.Normal,
                                        color = Color(0xFF333333)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatSessionTime(session.createdAt),
                                    style = TextStyle(fontSize = 12.sp, color = Color(0xFF999999)),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            // Message count badge
                            if (session.messages.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF0F0F0)
                                ) {
                                    Text(
                                        text = "${session.messages.size}",
                                        style = TextStyle(fontSize = 11.sp, color = Color(0xFF999999)),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Delete via long press or swipe (no visible button)
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF5F5F5))
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteConfirm?.let { sessionId ->
        val sessionTitle = sessions.find { it.id == sessionId }?.title ?: "此会话"
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除会话") },
            text = { Text("确定要删除「$sessionTitle」吗？删除后不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSession?.invoke(sessionId)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252))
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun formatSessionTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000} 分钟前"
        diff < 86400_000 -> "${diff / 3600_000} 小时前"
        else -> {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

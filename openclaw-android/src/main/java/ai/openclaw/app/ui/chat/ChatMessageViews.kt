package ai.openclaw.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import ai.openclaw.app.R
import ai.openclaw.app.chat.ChatMessage
import ai.openclaw.app.chat.ChatMessageContent
import ai.openclaw.app.chat.ChatPendingToolCall
import ai.openclaw.app.tools.ToolDisplayRegistry
import ai.openclaw.app.ui.mobileAccent
import ai.openclaw.app.ui.mobileAccentSoft
import ai.openclaw.app.ui.mobileBorder
import ai.openclaw.app.ui.mobileBorderStrong
import ai.openclaw.app.ui.mobileCallout
import ai.openclaw.app.ui.mobileCaption1
import ai.openclaw.app.ui.mobileCaption2
import ai.openclaw.app.ui.mobileCardSurface
import ai.openclaw.app.ui.mobileCodeBg
import ai.openclaw.app.ui.mobileCodeBorder
import ai.openclaw.app.ui.mobileCodeText
import ai.openclaw.app.ui.mobileHeadline
import ai.openclaw.app.ui.mobileText
import ai.openclaw.app.ui.mobileTextSecondary
import ai.openclaw.app.ui.mobileWarning
import ai.openclaw.app.ui.mobileWarningSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ChatBubbleStyle(
  val alignEnd: Boolean,
  val containerColor: Color,
  val borderColor: Color,
  val roleColor: Color,
)

@Composable
fun ChatMessageBubble(message: ChatMessage) {
  val role = message.role.trim().lowercase(Locale.US)
  val style = bubbleStyle(role)

  // Filter to only displayable content parts (text with content, or base64 images).
  val displayableContent =
    message.content.filter { part ->
      when (part.type) {
        "text" -> !part.text.isNullOrBlank()
        else -> part.base64 != null
      }
    }

  if (displayableContent.isEmpty()) return

  ChatBubbleContainer(style = style, roleLabel = roleLabel(role), timestampMs = message.timestampMs) {
    ChatMessageBody(content = displayableContent, textColor = mobileText)
  }
}

@Composable
private fun ChatBubbleContainer(
  style: ChatBubbleStyle,
  roleLabel: String,
  timestampMs: Long? = null,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = if (style.alignEnd) Arrangement.End else Arrangement.Start,
  ) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(1.dp, style.borderColor),
      color = style.containerColor,
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
      modifier = Modifier.fillMaxWidth(0.90f),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = roleLabel,
            style = mobileCaption2.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
            color = style.roleColor,
          )
          if (timestampMs != null) {
            val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            Text(
              text = timeFormatter.format(Date(timestampMs)),
              style = mobileCaption2,
              color = style.roleColor.copy(alpha = 0.6f),
            )
          }
        }
        content()
      }
    }
  }
}

@Composable
private fun ChatMessageBody(content: List<ChatMessageContent>, textColor: Color) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    for (part in content) {
      when (part.type) {
        "text" -> {
          val text = part.text ?: continue
          ChatMarkdown(text = text, textColor = textColor)
        }
        else -> {
          val b64 = part.base64 ?: continue
          ChatBase64Image(base64 = b64, mimeType = part.mimeType)
        }
      }
    }
  }
}

@Composable
fun ChatTypingIndicatorBubble() {
  ChatBubbleContainer(
    style = bubbleStyle("assistant"),
    roleLabel = roleLabel("assistant"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      DotPulse(color = mobileTextSecondary)
      Text(stringResource(R.string.chat_thinking), style = mobileCallout, color = mobileTextSecondary)
    }
  }
}


@Composable
fun ChatPendingToolsBubble(toolCalls: List<ChatPendingToolCall>) {
  val context = LocalContext.current
  val pairs =
    remember(toolCalls, context) {
      toolCalls.map { it to ToolDisplayRegistry.resolve(context, it.name, it.args) }
    }

  val allDone = toolCalls.all { it.isDone }
  val anyRunning = toolCalls.any { !it.isDone }

  ChatBubbleContainer(
    style = bubbleStyle("assistant"),
    roleLabel = stringResource(R.string.tools_label),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        if (anyRunning) stringResource(R.string.tools_running) else stringResource(R.string.tools_completed),
        style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold),
        color = mobileTextSecondary,
      )
      for ((call, display) in pairs) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          val statusIcon = when {
            call.isError == true -> "\u274C"  // red X
            call.isDone -> "\u2705"           // green check
            else -> display.emoji             // running: tool emoji
          }
          Text(
            "$statusIcon ${display.label}",
            style = mobileCallout,
            color = mobileTextSecondary,
            fontFamily = FontFamily.Monospace,
          )
          // Show tool call detail or raw arguments
          val detail = display.detailLine
          if (detail != null) {
            Text(
              detail,
              style = mobileCaption1,
              color = mobileTextSecondary,
              fontFamily = FontFamily.Monospace,
            )
          } else {
            call.args?.let { args ->
              val argsText = args.entries.joinToString(", ") { (k, v) ->
                val vStr = (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
                "$k=$vStr"
              }.let { if (it.length > 500) it.take(500) + "…" else it }
              if (argsText.isNotEmpty()) {
                Text(
                  argsText,
                  style = mobileCaption1,
                  color = mobileTextSecondary,
                  fontFamily = FontFamily.Monospace,
                )
              }
            }
          }
          call.result?.let { result ->
            val preview = result.trim().take(300)
            Text(
              preview + if (result.trim().length > 300) "…" else "",
              style = mobileCaption1,
              color = if (call.isError == true) mobileWarning else mobileTextSecondary,
              fontFamily = FontFamily.Monospace,
            )
          }
        }
      }
    }
  }
}

@Composable
fun ChatStreamingAssistantBubble(text: String) {
  ChatBubbleContainer(
    style = bubbleStyle("assistant").copy(borderColor = mobileAccent),
    roleLabel = "${stringResource(R.string.sender_ai)} ${stringResource(R.string.sender_live_suffix)}",
  ) {
    ChatMarkdown(text = text, textColor = mobileText)
  }
}

@Composable
private fun bubbleStyle(role: String): ChatBubbleStyle {
  return when (role) {
    "user" ->
      ChatBubbleStyle(
        alignEnd = true,
        containerColor = mobileAccentSoft,
        borderColor = mobileAccent,
        roleColor = mobileAccent,
      )

    "system" ->
      ChatBubbleStyle(
        alignEnd = false,
        containerColor = mobileWarningSoft,
        borderColor = mobileWarning.copy(alpha = 0.45f),
        roleColor = mobileWarning,
      )

    else ->
      ChatBubbleStyle(
        alignEnd = false,
        containerColor = mobileCardSurface,
        borderColor = mobileBorderStrong,
        roleColor = mobileTextSecondary,
      )
  }
}

@Composable
private fun roleLabel(role: String): String {
  return when (role) {
    "user" -> stringResource(R.string.sender_you)
    "system" -> stringResource(R.string.sender_system)
    else -> stringResource(R.string.sender_ai)
  }
}

@Composable
private fun ChatBase64Image(base64: String, mimeType: String?) {
  val imageState = rememberBase64ImageState(base64)
  val image = imageState.image

  if (image != null) {
    Surface(
      shape = RoundedCornerShape(10.dp),
      border = BorderStroke(1.dp, mobileBorder),
      color = mobileCardSurface,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Image(
        bitmap = image!!,
        contentDescription = mimeType ?: "attachment",
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  } else if (imageState.failed) {
    Text(stringResource(R.string.unsupported_attachment), style = mobileCaption1, color = mobileTextSecondary)
  }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

private fun formatMessageTime(timestampMs: Long): String {
  val date = Date(timestampMs)
  val now = System.currentTimeMillis()
  val diffMs = now - timestampMs
  val dayMs = 24 * 60 * 60 * 1000L
  return if (diffMs < dayMs) timeFormat.format(date) else dateFormat.format(date)
}

@Composable
private fun DotPulse(color: Color) {
  Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
    PulseDot(alpha = 0.38f, color = color)
    PulseDot(alpha = 0.62f, color = color)
    PulseDot(alpha = 0.90f, color = color)
  }
}

@Composable
private fun PulseDot(alpha: Float, color: Color) {
  Surface(
    modifier = Modifier.size(6.dp).alpha(alpha),
    shape = CircleShape,
    color = color,
  ) {}
}

@Composable
fun ChatCodeBlock(code: String, language: String?) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = mobileCodeBg,
    border = BorderStroke(1.dp, mobileCodeBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      if (!language.isNullOrBlank()) {
        Text(
          text = language.uppercase(Locale.US),
          style = mobileCaption2.copy(letterSpacing = 0.4.sp),
          color = mobileTextSecondary,
        )
      }
      Text(
        text = code.trimEnd(),
        fontFamily = FontFamily.Monospace,
        style = mobileCallout,
        color = mobileCodeText,
      )
    }
  }
}

package com.ai.assistance.operit.ui.common.displays

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnit.Companion.Unspecified
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.util.stream.stream
import androidx.compose.material3.LocalContentColor
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri

/** 新一代流式Markdown+LaTeX渲染器，完全替换原有实现。 兼容原有API，支持所有Markdown和LaTeX混排。 */
@Composable
fun MarkdownTextComposable(
        text: String,
        textColor: Color,
        modifier: Modifier = Modifier,
        fontSize: TextUnit = Unspecified,
        textAlign: TextAlign? = null,
        isSelectable: Boolean = true, // 保留参数，暂不处理
        onLinkClicked: ((String) -> Unit)? = null,
        enableDialogs: Boolean = true
) {
        // 直接使用新的、基于字符串的渲染器，以获得更好的性能
        val context = LocalContext.current
        StreamMarkdownRenderer(
                content = text,
                modifier = modifier,
                textColor = textColor,
                enableDialogs = enableDialogs,
                onLinkClick = { url ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                }
        )
}

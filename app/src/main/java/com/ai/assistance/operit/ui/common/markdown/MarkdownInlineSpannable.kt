package com.ai.assistance.operit.ui.common.markdown

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.LineBackgroundSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import androidx.collection.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import com.ai.assistance.operit.ui.common.displays.LatexCache
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.streamnative.NativeMarkdownSplitter
import ru.noties.jlatexmath.JLatexMathDrawable

private const val TAG = "MarkdownInlineSpannable"
private const val INLINE_LATEX_PLACEHOLDER = '\uFFFC'

private object NestedInlineNodeCache {
    private const val MAX_ENTRIES = 256

    private val cache = LruCache<String, List<MarkdownNodeStable>>(MAX_ENTRIES)

    fun getOrParse(content: String): List<MarkdownNodeStable> {
        if (content.isEmpty()) return emptyList()

        synchronized(cache) {
            cache.get(content)
        }?.let { return it }

        val parsed = NativeMarkdownSplitter.parseInlineToStableNodes(content)
        synchronized(cache) {
            cache.put(content, parsed)
        }
        return parsed
    }
}

private fun inlineCodeBackgroundColor(textColor: Color): Int {
    val backgroundAlpha = if (textColor.luminance() > 0.5f) 0.18f else 0.12f
    return textColor.copy(alpha = backgroundAlpha).toArgb()
}

private class RoundedInlineCodeBackgroundSpan(
    private val backgroundColor: Int,
    private val textScale: Float,
    private val horizontalPaddingPx: Float,
    private val verticalInsetPx: Float,
    private val cornerRadiusPx: Float,
) : LineBackgroundSpan {
    override fun drawBackground(
        canvas: Canvas,
        paint: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int
    ) {
        val spanned = text as? Spanned ?: return
        val spanStart = spanned.getSpanStart(this)
        val spanEnd = spanned.getSpanEnd(this)
        if (spanStart < 0 || spanEnd <= spanStart) return

        val drawStart = maxOf(start, spanStart)
        val drawEnd = minOf(end, spanEnd)
        if (drawEnd <= drawStart) return

        val prefixWidth =
            if (drawStart > start) paint.measureText(text, start, drawStart) else 0f
        val codePaint = createCodePaint(paint)
        val segmentStartX = left + prefixWidth
        val segmentEndX = segmentStartX + codePaint.measureText(text, drawStart, drawEnd)

        val previousColor = paint.color
        val previousStyle = paint.style
        paint.color = backgroundColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(
            segmentStartX - horizontalPaddingPx,
            top + verticalInsetPx,
            segmentEndX + horizontalPaddingPx,
            bottom - verticalInsetPx,
            cornerRadiusPx,
            cornerRadiusPx,
            paint
        )
        paint.color = previousColor
        paint.style = previousStyle
    }

    private fun createCodePaint(source: Paint): Paint =
        Paint(source).apply {
            typeface = getMarkdownCodeTypeface()
            textSize = source.textSize * textScale
            isAntiAlias = true
        }
}

private class InlineCodeTypefaceSpan(
    private val typeface: Typeface,
) : MetricAffectingSpan() {
    override fun updateDrawState(textPaint: TextPaint) {
        applyTypeface(textPaint)
    }

    override fun updateMeasureState(textPaint: TextPaint) {
        applyTypeface(textPaint)
    }

    private fun applyTypeface(paint: Paint) {
        paint.typeface = typeface
        paint.isAntiAlias = true
    }
}

private fun createInlineCodeBackgroundSpan(
    textColor: Color,
    density: Density?
): RoundedInlineCodeBackgroundSpan {
    val densityScale = density?.density ?: 1f
    return RoundedInlineCodeBackgroundSpan(
        backgroundColor = inlineCodeBackgroundColor(textColor),
        textScale = 0.9f,
        horizontalPaddingPx = 4f * densityScale,
        verticalInsetPx = 2f * densityScale,
        cornerRadiusPx = 4f * densityScale
    )
}

private fun stripUnderlineDelimiters(content: String): String {
    return if (content.startsWith("__") && content.endsWith("__") && content.length >= 4) {
        content.substring(2, content.length - 2)
    } else {
        content
    }
}

private fun extractInlineLatexContent(content: String): String {
    return when {
        content.startsWith("$$") && content.endsWith("$$") -> content.removeSurrounding("$$")
        content.startsWith("\\[") && content.endsWith("\\]") -> content.removeSurrounding("\\[", "\\]")
        content.startsWith("$") && content.endsWith("$") -> content.removeSurrounding("$")
        content.startsWith("\\(") && content.endsWith("\\)") -> content.removeSurrounding("\\(", "\\)")
        else -> content
    }
}

private fun appendInlineLatexFallback(
    builder: SpannableStringBuilder,
    rawContent: String
) {
    builder.append(rawContent)
}

private fun resolveNestedInlineText(node: MarkdownNodeStable): String {
    return when (node.type) {
        MarkdownProcessorType.LINK -> extractLinkText(node.content)
        MarkdownProcessorType.UNDERLINE -> stripUnderlineDelimiters(node.content)
        MarkdownProcessorType.HTML_BREAK -> "\n"
        else -> node.content
    }
}

private fun resolveNestedInlineChildren(node: MarkdownNodeStable): List<MarkdownNodeStable> {
    if (node.children.isNotEmpty()) {
        return node.children
    }

    return NestedInlineNodeCache.getOrParse(resolveNestedInlineText(node))
}

private fun appendInlineNode(
    builder: SpannableStringBuilder,
    child: MarkdownNodeStable,
    textColor: Color,
    primaryColor: Color,
    density: Density? = null,
    fontSize: TextUnit? = null
) {
    val content = child.content

    when (child.type) {
        MarkdownProcessorType.LINK -> {
            val linkUrl = extractLinkUrl(content)
            val linkText = extractLinkText(content)
            val nestedChildren = resolveNestedInlineChildren(child)
            val start = builder.length
            if (nestedChildren.isNotEmpty()) {
                nestedChildren.forEach {
                    appendInlineNode(builder, it, textColor, primaryColor, density, fontSize)
                }
            } else {
                builder.append(linkText)
            }
            val end = builder.length
            if (start < end) {
                builder.setSpan(URLSpan(linkUrl), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(
                    ForegroundColorSpan(primaryColor.toArgb()),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        MarkdownProcessorType.BOLD,
        MarkdownProcessorType.ITALIC,
        MarkdownProcessorType.STRIKETHROUGH,
        MarkdownProcessorType.UNDERLINE -> {
            val nestedChildren = resolveNestedInlineChildren(child)
            val fallbackText = resolveNestedInlineText(child)
            val start = builder.length
            if (nestedChildren.isNotEmpty()) {
                nestedChildren.forEach {
                    appendInlineNode(builder, it, textColor, primaryColor, density, fontSize)
                }
            } else {
                builder.append(fallbackText)
            }
            val end = builder.length
            if (start < end) {
                when (child.type) {
                    MarkdownProcessorType.BOLD ->
                        builder.setSpan(
                            StyleSpan(Typeface.BOLD),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                    MarkdownProcessorType.ITALIC ->
                        builder.setSpan(
                            StyleSpan(Typeface.ITALIC),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                    MarkdownProcessorType.STRIKETHROUGH ->
                        builder.setSpan(
                            StrikethroughSpan(),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                    MarkdownProcessorType.UNDERLINE ->
                        builder.setSpan(
                            UnderlineSpan(),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                    else -> Unit
                }
            }
        }

        MarkdownProcessorType.INLINE_LATEX -> {
            val latexContent = extractInlineLatexContent(content.trim())

            if (density != null && fontSize != null) {
                try {
                    val textSizePx = with(density) { fontSize.toPx() }
                    val drawable =
                        LatexCache.getDrawable(
                            latexContent,
                            JLatexMathDrawable.builder(latexContent)
                                .textSize(textSizePx)
                                .padding(2)
                                .color(textColor.toArgb())
                                .background(0x00000000)
                                .align(JLatexMathDrawable.ALIGN_LEFT)
                        )

                    drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)

                    val start = builder.length
                    builder.append(INLINE_LATEX_PLACEHOLDER)
                    val end = builder.length
                    builder.setSpan(
                        ImageSpan(drawable, ImageSpan.ALIGN_BASELINE),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Inline LaTeX render failed, fallback to raw text: $latexContent", e)
                    appendInlineLatexFallback(builder, content)
                }
            } else {
                appendInlineLatexFallback(builder, content)
            }
        }

        MarkdownProcessorType.INLINE_CODE -> {
            val start = builder.length
            builder.append(content)
            val end = builder.length
            if (start < end) {
                builder.setSpan(
                    createInlineCodeBackgroundSpan(textColor, density),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    InlineCodeTypefaceSpan(getMarkdownCodeTypeface()),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    RelativeSizeSpan(0.9f),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        MarkdownProcessorType.HTML_BREAK -> {
            builder.append('\n')
        }

        else -> {
            builder.append(content)
        }
    }
}

internal fun buildMarkdownInlineSpannableFromChildren(
    children: List<MarkdownNodeStable>,
    textColor: Color,
    primaryColor: Color,
    density: Density? = null,
    fontSize: TextUnit? = null
): SpannableStringBuilder {
    val builder = SpannableStringBuilder()
    children.forEach { child ->
        appendInlineNode(builder, child, textColor, primaryColor, density, fontSize)
    }
    return builder
}

internal fun buildMarkdownInlineSpannableFromText(
    text: String,
    textColor: Color,
    primaryColor: Color,
    density: Density? = null,
    fontSize: TextUnit? = null
): SpannableStringBuilder {
    if (text.isEmpty()) return SpannableStringBuilder()

    val inlineNodes = NestedInlineNodeCache.getOrParse(text)
    if (inlineNodes.isEmpty()) {
        return SpannableStringBuilder(text)
    }

    return buildMarkdownInlineSpannableFromChildren(
        children = inlineNodes,
        textColor = textColor,
        primaryColor = primaryColor,
        density = density,
        fontSize = fontSize
    )
}

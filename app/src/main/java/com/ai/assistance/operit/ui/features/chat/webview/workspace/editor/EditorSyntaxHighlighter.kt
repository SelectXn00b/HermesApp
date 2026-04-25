package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.language.LanguageFactory
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.language.LanguageSupport
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal data class HighlightSnapshot(
    val version: Int,
    val colors: IntArray
)

internal class EditorSyntaxHighlighter(
    initialLanguage: String,
    private val onResult: (HighlightSnapshot) -> Unit
) {
    companion object {
        private const val DEFAULT_LANGUAGE = "javascript"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val latestRequestedVersion = AtomicInteger(0)

    @Volatile
    private var language = initialLanguage.lowercase()
    @Volatile
    private var languageSupport = LanguageFactory.getLanguageSupport(language)

    fun setLanguage(language: String) {
        this.language = language.lowercase()
        languageSupport = LanguageFactory.getLanguageSupport(this.language)
            ?: LanguageFactory.getLanguageSupport(DEFAULT_LANGUAGE)
    }

    fun requestHighlight(text: String, version: Int) {
        latestRequestedVersion.set(version)
        val activeLanguage = language
        val activeSupport = languageSupport
        executor.execute {
            val colors = IntArray(text.length)
            if (text.isNotEmpty()) {
                parseFullText(text, colors, activeSupport)
            }
            if (latestRequestedVersion.get() != version) {
                return@execute
            }
            mainHandler.post {
                if (latestRequestedVersion.get() == version) {
                    onResult(HighlightSnapshot(version, colors))
                }
            }
        }
    }

    fun release() {
        executor.shutdownNow()
    }

    private fun parseFullText(text: String, colors: IntArray, support: LanguageSupport?) {
        if (support == null) {
            simpleHighlight(text, colors)
            return
        }

        var index = 0
        while (index < text.length) {
            val current = text[index]

            var handled = false
            for (commentMarker in support.getCommentStart()) {
                if (text.startsWith(commentMarker, index)) {
                    if (commentMarker == "//" || commentMarker.length == 1) {
                        val end = text.indexOf('\n', index)
                        val commentEnd = if (end == -1) text.length else end
                        fill(colors, index, commentEnd, LanguageSupport.COMMENT_COLOR)
                        index = commentEnd
                    } else {
                        val commentEndMarker = support.getMultiLineCommentEnd() ?: "*/"
                        val end = text.indexOf(commentEndMarker, index + commentMarker.length)
                        val commentEnd = if (end == -1) text.length else end + commentEndMarker.length
                        fill(colors, index, commentEnd, LanguageSupport.COMMENT_COLOR)
                        index = commentEnd
                    }
                    handled = true
                    break
                }
            }
            if (handled) continue

            if (support.isStringDelimiter(current)) {
                val quote = current
                val escape = support.getStringEscapeChar()
                val start = index
                index++
                while (index < text.length) {
                    if (text[index] == escape && index + 1 < text.length) {
                        index += 2
                        continue
                    }
                    if (text[index] == quote) {
                        index++
                        break
                    }
                    index++
                }
                fill(colors, start, index, LanguageSupport.STRING_COLOR)
                continue
            }

            if (
                current.isDigit() ||
                (current == '0' && index + 1 < text.length && text[index + 1] in charArrayOf('x', 'X', 'b', 'B'))
            ) {
                val start = index
                if (current == '0' && index + 1 < text.length) {
                    when (text[index + 1]) {
                        'x', 'X' -> {
                            index += 2
                            while (index < text.length && (text[index].isDigit() || text[index] in 'a'..'f' || text[index] in 'A'..'F')) {
                                index++
                            }
                        }
                        'b', 'B' -> {
                            index += 2
                            while (index < text.length && text[index] in charArrayOf('0', '1')) {
                                index++
                            }
                        }
                        else -> {
                            index = consumeNumber(text, index)
                        }
                    }
                } else {
                    index = consumeNumber(text, index)
                }
                if (index < text.length && text[index] in charArrayOf('L', 'l', 'F', 'f', 'D', 'd')) {
                    index++
                }
                fill(colors, start, index, LanguageSupport.NUMBER_COLOR)
                continue
            }

            if (Character.isJavaIdentifierStart(current)) {
                val start = index
                while (index < text.length && Character.isJavaIdentifierPart(text[index])) {
                    index++
                }
                val word = text.substring(start, index)
                var nextCharIndex = index
                while (nextCharIndex < text.length && (text[nextCharIndex] == ' ' || text[nextCharIndex] == '\t')) {
                    nextCharIndex++
                }
                val nextChar = if (nextCharIndex < text.length) text[nextCharIndex] else ' '
                val isCapitalized = word.isNotEmpty() && word[0].isUpperCase()
                val color = when {
                    support.getKeywords().contains(word) -> LanguageSupport.KEYWORD_COLOR
                    support.getBuiltInTypes().contains(word) -> LanguageSupport.TYPE_COLOR
                    support.getBuiltInVariables().contains(word) -> LanguageSupport.VARIABLE_COLOR
                    support.getBuiltInFunctions().contains(word) -> LanguageSupport.FUNCTION_COLOR
                    nextChar == '(' -> LanguageSupport.FUNCTION_COLOR
                    isCapitalized -> LanguageSupport.TYPE_COLOR
                    else -> LanguageSupport.VARIABLE_COLOR
                }
                fill(colors, start, index, color)
                continue
            }

            if ("+-*/%=&|<>!~^?:;,(){}[].".contains(current)) {
                if (index + 1 < text.length) {
                    val twoChars = text.substring(index, index + 2)
                    if (twoChars in listOf("==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "=>", "->", "::")) {
                        fill(colors, index, index + 2, LanguageSupport.OPERATOR_COLOR)
                        index += 2
                        continue
                    }
                }
                colors[index] = LanguageSupport.OPERATOR_COLOR
            } else {
                colors[index] = LanguageSupport.DEFAULT_COLOR
            }
            index++
        }
    }

    private fun simpleHighlight(text: String, colors: IntArray) {
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("//", index) -> {
                    val end = text.indexOf('\n', index)
                    val commentEnd = if (end == -1) text.length else end
                    fill(colors, index, commentEnd, LanguageSupport.COMMENT_COLOR)
                    index = commentEnd
                }
                text.startsWith("/*", index) -> {
                    val end = text.indexOf("*/", index + 2)
                    val commentEnd = if (end == -1) text.length else end + 2
                    fill(colors, index, commentEnd, LanguageSupport.COMMENT_COLOR)
                    index = commentEnd
                }
                text[index] == '"' || text[index] == '\'' -> {
                    val quote = text[index]
                    val start = index
                    index++
                    while (index < text.length && text[index] != quote) {
                        if (text[index] == '\\' && index + 1 < text.length) {
                            index += 2
                        } else {
                            index++
                        }
                    }
                    if (index < text.length) {
                        index++
                    }
                    fill(colors, start, index, LanguageSupport.STRING_COLOR)
                }
                text[index].isDigit() -> {
                    val start = index
                    index = consumeNumber(text, index)
                    fill(colors, start, index, LanguageSupport.NUMBER_COLOR)
                }
                else -> {
                    colors[index] = LanguageSupport.DEFAULT_COLOR
                    index++
                }
            }
        }
    }

    private fun consumeNumber(text: String, startIndex: Int): Int {
        var index = startIndex
        while (
            index < text.length &&
            (text[index].isDigit() || text[index] == '.' || text[index] == 'e' || text[index] == 'E' || text[index] == '-' || text[index] == '+')
        ) {
            index++
        }
        return index
    }

    private fun fill(colors: IntArray, start: Int, end: Int, color: Int) {
        val safeEnd = end.coerceAtMost(colors.size)
        for (index in start until safeEnd) {
            colors[index] = color
        }
    }
}

package com.ai.assistance.operit.util

import com.ai.assistance.operit.util.AppLogger
import java.io.File

/**
 * 语法检查工具类
 * 提供JavaScript和HTML的简单语法检查功能
 */
object SyntaxCheckUtil {
    private const val TAG = "SyntaxCheckUtil"

    /**
     * 语法错误信息类
     */
    data class SyntaxError(
        val line: Int,
        val column: Int,
        val message: String,
        val severity: Severity = Severity.ERROR
    ) {
        enum class Severity {
            ERROR,
            WARNING
        }

        override fun toString(): String {
            return "Line $line:$column - ${severity.name}: $message"
        }
    }

    /**
     * 语法检查结果
     */
    data class SyntaxCheckResult(
        val filePath: String,
        val fileType: String,
        val errors: List<SyntaxError>,
        val hasErrors: Boolean = errors.any { it.severity == SyntaxError.Severity.ERROR }
    ) {
        override fun toString(): String {
            if (errors.isEmpty()) {
                return "✓ $filePath: No syntax errors found"
            }

            val sb = StringBuilder()
            sb.appendLine("Syntax check for $filePath ($fileType):")
            sb.appendLine("Found ${errors.size} issue(s):")
            errors.forEach { error ->
                sb.appendLine("  ${error}")
            }
            return sb.toString()
        }
    }

    /**
     * 根据文件路径检查语法
     * @param filePath 文件路径
     * @param content 文件内容
     * @return 语法检查结果，如果不支持该文件类型则返回null
     */
    fun checkSyntax(filePath: String, content: String): SyntaxCheckResult? {
        val file = File(filePath)
        val extension = file.extension.lowercase()

        return when (extension) {
            "js", "mjs", "cjs", "jsx" -> checkJavaScript(filePath, content)
            "html", "htm" -> checkHtml(filePath, content)
            else -> null
        }
    }

    /**
     * 检查JavaScript语法
     * 执行简单的语法检查，包括：
     * - 括号匹配（圆括号、方括号、花括号）
     * - 引号匹配（单引号、双引号、反引号）
     * - 注释完整性
     */
    fun checkJavaScript(filePath: String, content: String): SyntaxCheckResult {
        val errors = mutableListOf<SyntaxError>()
        val lines = content.lines()

        // 检查括号匹配
        checkBracketMatching(lines, errors)

        // 检查引号匹配
        checkQuoteMatching(lines, errors)

        // 检查注释完整性
        checkCommentMatching(lines, errors)

        // 检查常见的JavaScript语法错误
        checkCommonJsErrors(lines, errors)

        return SyntaxCheckResult(filePath, "JavaScript", errors)
    }

    /**
     * 检查HTML语法
     * 执行简单的语法检查，包括：
     * - 标签匹配
     * - 属性引号
     * - 注释完整性
     */
    fun checkHtml(filePath: String, content: String): SyntaxCheckResult {
        val errors = mutableListOf<SyntaxError>()
        val lines = content.lines()

        // 检查标签匹配
        checkHtmlTagMatching(lines, errors)

        // 检查HTML注释
        checkHtmlComments(lines, errors)

        // 检查属性引号
        checkHtmlAttributeQuotes(lines, errors)

        return SyntaxCheckResult(filePath, "HTML", errors)
    }

    /**
     * 检查括号匹配
     */
    private fun checkBracketMatching(lines: List<String>, errors: MutableList<SyntaxError>) {
        val stack = mutableListOf<Pair<Char, Pair<Int, Int>>>() // (bracket, (line, col))
        val bracketPairs = mapOf('(' to ')', '[' to ']', '{' to '}')

        // 跨行保持的字符串状态
        var inString = false
        var stringChar = ' '
        var inMultiLineComment = false

        lines.forEachIndexed { lineIndex, line ->
            var inSingleLineComment = false
            var i = 0

            while (i < line.length) {
                val char = line[i]

                // 检查多行注释的开始和结束
                if (!inString && !inSingleLineComment && i < line.length - 1) {
                    if (line[i] == '/' && line[i + 1] == '*') {
                        inMultiLineComment = true
                        i += 2
                        continue
                    }
                    if (inMultiLineComment && line[i] == '*' && line[i + 1] == '/') {
                        inMultiLineComment = false
                        i += 2
                        continue
                    }
                }

                // 检查单行注释
                if (!inString && !inMultiLineComment && i < line.length - 1 && line[i] == '/' && line[i + 1] == '/') {
                    inSingleLineComment = true
                    break
                }

                // 检查是否在字符串中
                if (!inMultiLineComment && !inSingleLineComment && (char == '"' || char == '\'' || char == '`')) {
                    // 检查是否是转义字符
                    var isEscaped = false
                    var escapeCount = 0
                    var j = i - 1
                    while (j >= 0 && line[j] == '\\') {
                        escapeCount++
                        j--
                    }
                    // 如果有奇数个反斜杠，则当前字符被转义
                    isEscaped = escapeCount % 2 == 1

                    if (!isEscaped) {
                        if (!inString) {
                            inString = true
                            stringChar = char
                        } else if (char == stringChar) {
                            inString = false
                        }
                    }
                }

                // 如果不在字符串、注释中，检查括号
                if (!inString && !inSingleLineComment && !inMultiLineComment) {
                    if (char in bracketPairs.keys) {
                        stack.add(char to (lineIndex + 1 to i + 1))
                    } else if (char in bracketPairs.values) {
                        if (stack.isEmpty()) {
                            errors.add(
                                SyntaxError(
                                    lineIndex + 1,
                                    i + 1,
                                    "Unexpected closing bracket '$char'"
                                )
                            )
                        } else {
                            val (openBracket, _) = stack.last()
                            if (bracketPairs[openBracket] == char) {
                                stack.removeAt(stack.size - 1)
                            } else {
                                errors.add(
                                    SyntaxError(
                                        lineIndex + 1,
                                        i + 1,
                                        "Mismatched bracket: expected '${bracketPairs[openBracket]}', found '$char'"
                                    )
                                )
                            }
                        }
                    }
                }

                i++
            }

            // 单引号和双引号字符串不能跨行（在JavaScript中）
            // 但反引号（模板字符串）可以跨行
            if (inString && stringChar != '`') {
                inString = false
            }
        }

        // 检查未闭合的括号
        stack.forEach { (bracket, position) ->
            errors.add(
                SyntaxError(
                    position.first,
                    position.second,
                    "Unclosed bracket '$bracket'"
                )
            )
        }

        // 检查未闭合的多行注释
        if (inMultiLineComment) {
            errors.add(
                SyntaxError(
                    lines.size,
                    1,
                    "Unclosed multi-line comment"
                )
            )
        }

        // 检查未闭合的模板字符串
        if (inString && stringChar == '`') {
            errors.add(
                SyntaxError(
                    lines.size,
                    1,
                    "Unclosed template string"
                )
            )
        }
    }

    /**
     * 检查引号匹配
     */
    private fun checkQuoteMatching(lines: List<String>, errors: MutableList<SyntaxError>) {
        var inString = false
        var stringChar = ' '
        var stringStartLine = -1
        var stringStartCol = -1
        var inMultiLineComment = false

        lines.forEachIndexed { lineIndex, line ->
            var inSingleLineComment = false
            var i = 0

            while (i < line.length) {
                // 检查多行注释的开始和结束
                if (!inString && !inSingleLineComment && i < line.length - 1) {
                    if (line[i] == '/' && line[i + 1] == '*') {
                        inMultiLineComment = true
                        i += 2
                        continue
                    }
                    if (inMultiLineComment && line[i] == '*' && line[i + 1] == '/') {
                        inMultiLineComment = false
                        i += 2
                        continue
                    }
                }

                // 跳过单行注释
                if (!inString && !inMultiLineComment && i < line.length - 1 && line[i] == '/' && line[i + 1] == '/') {
                    inSingleLineComment = true
                    break
                }

                // 检查字符串
                if (!inMultiLineComment && !inSingleLineComment && line[i] in listOf('"', '\'', '`')) {
                    // 检查是否是转义字符
                    var escapeCount = 0
                    var j = i - 1
                    while (j >= 0 && line[j] == '\\') {
                        escapeCount++
                        j--
                    }
                    // 如果有奇数个反斜杠，则当前字符被转义
                    val isEscaped = escapeCount % 2 == 1

                    if (!isEscaped) {
                        if (!inString) {
                            // 开始一个新字符串
                            inString = true
                            stringChar = line[i]
                            stringStartLine = lineIndex + 1
                            stringStartCol = i + 1
                        } else if (line[i] == stringChar) {
                            // 结束当前字符串
                            inString = false
                        }
                    }
                }

                i++
            }

            // 单引号和双引号字符串不能跨行
            // 如果行尾仍在字符串中且不是反引号，报告错误
            if (inString && stringChar != '`') {
                errors.add(
                    SyntaxError(
                        stringStartLine,
                        stringStartCol,
                        "Unclosed string literal (single/double quotes cannot span multiple lines)",
                        SyntaxError.Severity.WARNING
                    )
                )
                inString = false
            }
        }

        // 检查是否有未闭合的模板字符串（反引号）
        if (inString && stringChar == '`') {
            errors.add(
                SyntaxError(
                    stringStartLine,
                    stringStartCol,
                    "Unclosed template string literal",
                    SyntaxError.Severity.WARNING
                )
            )
        }
    }

    /**
     * 检查注释完整性
     */
    private fun checkCommentMatching(lines: List<String>, errors: MutableList<SyntaxError>) {
        val content = lines.joinToString("\n")
        var inMultiLineComment = false
        var commentStartLine = -1
        var commentStartCol = -1

        lines.forEachIndexed { lineIndex, line ->
            var i = 0
            while (i < line.length - 1) {
                if (!inMultiLineComment && line[i] == '/' && line[i + 1] == '*') {
                    inMultiLineComment = true
                    commentStartLine = lineIndex + 1
                    commentStartCol = i + 1
                    i += 2
                    continue
                }

                if (inMultiLineComment && line[i] == '*' && line[i + 1] == '/') {
                    inMultiLineComment = false
                    i += 2
                    continue
                }

                i++
            }
        }

        if (inMultiLineComment) {
            errors.add(
                SyntaxError(
                    commentStartLine,
                    commentStartCol,
                    "Unclosed multi-line comment"
                )
            )
        }
    }

    /**
     * 检查常见的JavaScript错误
     */
    private fun checkCommonJsErrors(lines: List<String>, errors: MutableList<SyntaxError>) {
        lines.forEachIndexed { lineIndex, line ->
            val trimmed = line.trim()

            // 检查多余的分号
            if (trimmed.matches(Regex(".*;\\s*;"))) {
                errors.add(
                    SyntaxError(
                        lineIndex + 1,
                        line.indexOf(";;") + 1,
                        "Double semicolon detected",
                        SyntaxError.Severity.WARNING
                    )
                )
            }

            // 检查 return 后面是否有内容但在下一行
            if (trimmed == "return" && lineIndex < lines.size - 1) {
                val nextLine = lines[lineIndex + 1].trim()
                if (nextLine.isNotEmpty() && !nextLine.startsWith("//") && !nextLine.startsWith("/*")) {
                    errors.add(
                        SyntaxError(
                            lineIndex + 1,
                            line.indexOf("return") + 1,
                            "Return statement should have value on same line",
                            SyntaxError.Severity.WARNING
                        )
                    )
                }
            }
        }
    }

    /**
     * 检查HTML标签匹配
     */
    private fun checkHtmlTagMatching(lines: List<String>, errors: MutableList<SyntaxError>) {
        val stack = mutableListOf<Pair<String, Pair<Int, Int>>>() // (tagName, (line, col))
        val selfClosingTags = setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr"
        )

        val content = lines.joinToString("\n")
        
        // 预处理：移除HTML注释和script/style标签内的内容
        val cleanedContent = removeHtmlCommentsAndScriptContent(content)
        
        val tagPattern = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)(\\s[^>]*)?>")

        tagPattern.findAll(cleanedContent).forEach { match ->
            val isClosing = match.groupValues[1] == "/"
            val tagName = match.groupValues[2].lowercase()
            
            // 计算行号和列号（基于原始内容）
            val position = match.range.first
            var line = 1
            var col = 1
            var currentPos = 0
            
            for ((lineIndex, lineContent) in lines.withIndex()) {
                if (currentPos + lineContent.length >= position) {
                    line = lineIndex + 1
                    col = position - currentPos + 1
                    break
                }
                currentPos += lineContent.length + 1 // +1 for newline
            }

            if (isClosing) {
                // 处理闭合标签
                if (stack.isEmpty()) {
                    errors.add(
                        SyntaxError(
                            line,
                            col,
                            "Unexpected closing tag </$tagName>"
                        )
                    )
                } else {
                    val (openTag, _) = stack.last()
                    if (openTag == tagName) {
                        stack.removeAt(stack.size - 1)
                    } else {
                        errors.add(
                            SyntaxError(
                                line,
                                col,
                                "Mismatched tag: expected </$openTag>, found </$tagName>"
                            )
                        )
                    }
                }
            } else {
                // 处理开始标签
                if (tagName !in selfClosingTags && !match.value.endsWith("/>")) {
                    stack.add(tagName to (line to col))
                }
            }
        }

        // 检查未闭合的标签
        stack.forEach { (tagName, position) ->
            errors.add(
                SyntaxError(
                    position.first,
                    position.second,
                    "Unclosed tag <$tagName>"
                )
            )
        }
    }
    
    /**
     * 移除HTML注释和script/style标签内的内容，避免误判
     * 用空格替代被移除的内容以保持位置索引不变
     */
    private fun removeHtmlCommentsAndScriptContent(content: String): String {
        val result = StringBuilder()
        var i = 0
        
        while (i < content.length) {
            // 检查HTML注释
            if (content.startsWith("<!--", i)) {
                val commentEnd = content.indexOf("-->", i + 4)
                if (commentEnd != -1) {
                    // 用空格替换整个注释
                    result.append(" ".repeat(commentEnd + 3 - i))
                    i = commentEnd + 3
                    continue
                }
            }
            
            // 检查script标签
            val scriptMatch = Regex("<script(\\s[^>]*)?>", RegexOption.IGNORE_CASE).matchAt(content, i)
            if (scriptMatch != null) {
                val openingTag = scriptMatch.value
                result.append(openingTag)
                i += openingTag.length
                
                // 在script内容中查找真正的</script>闭合标签，需要跳过JavaScript字符串中的内容
                val scriptContentEnd = findScriptEnd(content, i)
                result.append(" ".repeat(scriptContentEnd - i))
                i = scriptContentEnd
                
                // 添加</script>标签
                if (content.startsWith("</script>", i, ignoreCase = true)) {
                    result.append(content.substring(i, i + 9))
                    i += 9
                }
                continue
            }
            
            // 检查style标签
            val styleMatch = Regex("<style(\\s[^>]*)?>", RegexOption.IGNORE_CASE).matchAt(content, i)
            if (styleMatch != null) {
                val openingTag = styleMatch.value
                result.append(openingTag)
                i += openingTag.length
                
                // 查找</style>闭合标签
                val styleEnd = content.indexOf("</style>", i, ignoreCase = true)
                if (styleEnd != -1) {
                    result.append(" ".repeat(styleEnd - i))
                    result.append(content.substring(styleEnd, styleEnd + 8))
                    i = styleEnd + 8
                    continue
                }
            }
            
            // 普通字符
            result.append(content[i])
            i++
        }
        
        return result.toString()
    }
    
    /**
     * 在script标签内查找真正的</script>闭合标签位置
     * 需要跳过JavaScript字符串和注释中的内容
     */
    private fun findScriptEnd(content: String, startIndex: Int): Int {
        var i = startIndex
        var inString = false
        var stringChar = ' '
        var inSingleLineComment = false
        var inMultiLineComment = false
        
        while (i < content.length) {
            val char = content[i]
            
            // 处理多行注释
            if (!inString && !inSingleLineComment && i < content.length - 1) {
                if (content[i] == '/' && content[i + 1] == '*') {
                    inMultiLineComment = true
                    i += 2
                    continue
                }
                if (inMultiLineComment && content[i] == '*' && content[i + 1] == '/') {
                    inMultiLineComment = false
                    i += 2
                    continue
                }
            }
            
            // 处理单行注释
            if (!inString && !inMultiLineComment && i < content.length - 1 && content[i] == '/' && content[i + 1] == '/') {
                inSingleLineComment = true
                i += 2
                continue
            }
            
            // 换行符结束单行注释
            if (inSingleLineComment && char == '\n') {
                inSingleLineComment = false
                i++
                continue
            }
            
            // 处理字符串
            if (!inMultiLineComment && !inSingleLineComment && (char == '"' || char == '\'' || char == '`')) {
                // 检查是否被转义
                var escapeCount = 0
                var j = i - 1
                while (j >= 0 && content[j] == '\\') {
                    escapeCount++
                    j--
                }
                val isEscaped = escapeCount % 2 == 1
                
                if (!isEscaped) {
                    if (!inString) {
                        inString = true
                        stringChar = char
                    } else if (char == stringChar) {
                        inString = false
                    }
                }
            }
            
            // 检查是否遇到</script>标签（不在字符串或注释中）
            if (!inString && !inSingleLineComment && !inMultiLineComment && 
                content.startsWith("</script>", i, ignoreCase = true)) {
                return i
            }
            
            i++
        }
        
        // 如果没找到闭合标签，返回内容结尾
        return content.length
    }

    /**
     * 检查HTML注释
     */
    private fun checkHtmlComments(lines: List<String>, errors: MutableList<SyntaxError>) {
        val content = lines.joinToString("\n")
        var inComment = false
        var commentStartLine = -1
        var commentStartCol = -1
        var currentLine = 1
        var currentCol = 1

        var i = 0
        for ((lineIndex, line) in lines.withIndex()) {
            var col = 0
            while (col < line.length) {
                if (!inComment && line.substring(col).startsWith("<!--")) {
                    inComment = true
                    commentStartLine = lineIndex + 1
                    commentStartCol = col + 1
                    col += 4
                    continue
                }

                if (inComment && line.substring(col).startsWith("-->")) {
                    inComment = false
                    col += 3
                    continue
                }

                col++
            }
        }

        if (inComment) {
            errors.add(
                SyntaxError(
                    commentStartLine,
                    commentStartCol,
                    "Unclosed HTML comment"
                )
            )
        }
    }

    /**
     * 检查HTML属性引号
     */
    private fun checkHtmlAttributeQuotes(lines: List<String>, errors: MutableList<SyntaxError>) {
        val tagContentPattern = Regex("""<[^>]*>""")

        lines.forEachIndexed { lineIndex, line ->
            tagContentPattern.findAll(line).forEach { tagMatch ->
                val tagContent = tagMatch.value
                var i = 0
                
                while (i < tagContent.length) {
                    // 跳过空白
                    while (i < tagContent.length && tagContent[i].isWhitespace()) i++
                    if (i >= tagContent.length) break
                    
                    // 查找属性名
                    val attrNameStart = i
                    while (i < tagContent.length && (tagContent[i].isLetterOrDigit() || tagContent[i] in setOf('-', '_', ':'))) i++
                    if (i >= tagContent.length || tagContent[i] != '=') {
                        // 不是 key=value 形式，继续
                        i++
                        continue
                    }
                    
                    val attrName = tagContent.substring(attrNameStart, i)
                    i++ // 跳过 '='
                    
                    // 检查属性值是否有引号
                    if (i < tagContent.length) {
                        val quoteChar = tagContent[i]
                        if (quoteChar == '"' || quoteChar == '\'') {
                            // 有引号，跳过整个引号内的内容
                            i++ // 跳过开始引号
                            while (i < tagContent.length && tagContent[i] != quoteChar) {
                                if (tagContent[i] == '\\' && i + 1 < tagContent.length) {
                                    i += 2 // 跳过转义字符
                                } else {
                                    i++
                                }
                            }
                            if (i < tagContent.length) i++ // 跳过结束引号
                        } else {
                            // 无引号的属性值，报告警告
                            val valueStart = i
                            while (i < tagContent.length && !tagContent[i].isWhitespace() && tagContent[i] != '>') i++
                            val attrValue = tagContent.substring(valueStart, i)
                            
                            errors.add(
                                SyntaxError(
                                    lineIndex + 1,
                                    tagMatch.range.first + valueStart + 1,
                                    "Attribute '$attrName' value should be quoted",
                                    SyntaxError.Severity.WARNING
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}


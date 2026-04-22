package com.xiaomo.hermes.hermes.agent

import android.util.Log

/**
 * Display - 终端显示（Android 简化版）
 * 1:1 对齐 hermes/agent/display.py（大幅简化）
 *
 * Python 版使用 ANSI 转义码控制终端颜色和格式。
 * Android 版保留相同的方法签名，输出纯文本，供 UI 层使用。
 */

data class DisplayConfig(
    val showTimestamps: Boolean = false,
    val showModel: Boolean = true,
    val maxWidth: Int = 80,
    val colorEnabled: Boolean = false
)

/**
 * Agent 显示工具（Android 简化版）
 */
class Display(
    private val config: DisplayConfig = DisplayConfig()
) {

    /**
     * 显示用户消息
     *
     * @param message 用户消息
     * @return 格式化后的文本
     */
    fun formatUserMessage(message: String): String {
        return "👤 You: $message"
    }

    /**
     * 显示助手消息
     *
     * @param message 助手消息
     * @param model 使用的模型（可选）
     * @return 格式化后的文本
     */
    fun formatAssistantMessage(message: String, model: String = ""): String {
        val modelSuffix = if (config.showModel && model.isNotEmpty()) " [$model]" else ""
        return "🤖 Assistant$modelSuffix: $message"
    }

    /**
     * 显示工具调用
     *
     * @param toolName 工具名称
     * @param args 工具参数（JSON 字符串）
     * @return 格式化后的文本
     */
    fun formatToolCall(toolName: String, args: String = ""): String {
        val argsPreview = if (args.length > 100) args.take(100) + "..." else args
        return "🔧 Tool: $toolName($argsPreview)"
    }

    /**
     * 显示工具结果
     *
     * @param toolName 工具名称
     * @param result 工具结果
     * @return 格式化后的文本
     */
    fun formatToolResult(toolName: String, result: String): String {
        val resultPreview = if (result.length > 200) result.take(200) + "..." else result
        return "📋 Tool Result ($toolName): $resultPreview"
    }

    /**
     * 显示系统消息
     *
     * @param message 系统消息
     * @return 格式化后的文本
     */
    fun formatSystemMessage(message: String): String {
        return "⚙️ System: $message"
    }

    /**
     * 显示错误消息
     *
     * @param message 错误消息
     * @return 格式化后的文本
     */
    fun formatError(message: String): String {
        return "❌ Error: $message"
    }

    /**
     * 显示警告消息
     *
     * @param message 警告消息
     * @return 格式化后的文本
     */
    fun formatWarning(message: String): String {
        return "⚠️ Warning: $message"
    }

    /**
     * 显示成功消息
     *
     * @param message 成功消息
     * @return 格式化后的文本
     */
    fun formatSuccess(message: String): String {
        return "✅ $message"
    }

    /**
     * 显示进度
     *
     * @param current 当前进度
     * @param total 总数
     * @param message 进度描述
     * @return 格式化后的文本
     */
    fun formatProgress(current: Int, total: Int, message: String = ""): String {
        val percent = if (total > 0) (current * 100 / total) else 0
        val bar = "█".repeat(percent / 5) + "░".repeat(20 - percent / 5)
        return "[$bar] $percent% $message"
    }

    /**
     * 显示 token 使用情况
     *
     * @param inputTokens 输入 token 数
     * @param outputTokens 输出 token 数
     * @param cost 费用（美元）
     * @return 格式化后的文本
     */
    fun formatTokenUsage(inputTokens: Int, outputTokens: Int, cost: Double = 0.0): String {
        val costStr = if (cost > 0) " | Cost: $$cost" else ""
        return "📊 Tokens: ${inputTokens} in / ${outputTokens} out$costStr"
    }

    /**
     * 截断长文本用于显示
     *
     * @param text 原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    fun truncateForDisplay(text: String, maxLength: Int = 500): String {
        if (text.length <= maxLength) return text
        return text.take(maxLength) + "\n... (${text.length - maxLength} more chars)"
    }

    /**
     * Markdown 简单格式化（去除标记，保留纯文本）
     *
     * @param markdown Markdown 文本
     * @return 纯文本
     */
    fun markdownToPlainText(markdown: String): String {
        return markdown
            .replace(Regex("""#{1,6}\s+"""), "")   // 标题
            .replace(Regex("""\*\*(.+?)\*\*"""), "$1")  // 粗体
            .replace(Regex("""\*(.+?)\*"""), "$1")       // 斜体
            .replace(Regex("""`(.+?)`"""), "$1")          // 行内代码
            .replace(Regex("""```[\s\S]*?```"""), "[code block]")  // 代码块
            .replace(Regex("""\[(.+?)\]\(.+?\)"""), "$1")  // 链接
            .trim()
    }

    companion object {
        /** Max length for tool call previews. 0 = no limit. */
        var toolPreviewMaxLen: Int = 0

        /** Collapse whitespace (including newlines) to single spaces. */
        fun oneline(text: String): String = text.replace(Regex("\\s+"), " ").trim()

        /** Conservatively detect whether a tool result represents success. */
        fun resultSucceeded(result: String?): Boolean {
            if (result.isNullOrEmpty()) return false
            // Try JSON parse
            try {
                val gson = com.google.gson.Gson()
                val data = gson.fromJson(result, Map::class.java) as? Map<*, *> ?: return false
                if (data["error"] != null) return false
                if (data.containsKey("success")) return data["success"] as? Boolean ?: false
                return true
            } catch (_unused: Exception) {
                return false
            }
        }

        /** Detect failure in a tool result. Returns (isFailure, suffix). */
        fun detectToolFailure(toolName: String, result: String?): Pair<Boolean, String> {
            if (result == null) return false to ""
            if (toolName == "terminal") {
                try {
                    val gson = com.google.gson.Gson()
                    val data = gson.fromJson(result, Map::class.java) as? Map<*, *>
                    if (data != null) {
                        val exitCode = data["exit_code"] as? Number
                        if (exitCode != null && exitCode.toInt() != 0) {
                            return true to " [exit ${exitCode}]"
                        }
                    }
                } catch (_unused: Exception) {}
                return false to ""
            }
            if (toolName == "memory") {
                try {
                    val gson = com.google.gson.Gson()
                    val data = gson.fromJson(result, Map::class.java) as? Map<*, *>
                    if (data != null) {
                        if (data["success"] == false && (data["error"] as? String ?: "").contains("exceed the limit")) {
                            return true to " [full]"
                        }
                    }
                } catch (_unused: Exception) {}
            }
            val lower = result.take(500).lowercase()
            if ("\"error\"" in lower || "\"failed\"" in lower || result.startsWith("Error")) {
                return true to " [error]"
            }
            return false to ""
        }

        /** Get the display emoji for a tool. */
        fun getToolEmoji(toolName: String, default: String = "⚡"): String {
            val emojiMap = mapOf(
                "web_search" to "🔍", "web_extract" to "📄", "web_crawl" to "🕸️",
                "terminal" to "💻", "process" to "⚙️",
                "read_file" to "📖", "write_file" to "✍️", "patch" to "🔧",
                "search_files" to "🔎",
                "browser_navigate" to "🌐", "browser_snapshot" to "📸",
                "browser_click" to "👆", "browser_type" to "⌨️",
                "browser_scroll" to "↓", "browser_back" to "◀️",
                "todo" to "📋", "memory" to "🧠", "skills_list" to "📚",
                "image_generate" to "🎨", "text_to_speech" to "🔊",
                "vision_analyze" to "👁️", "mixture_of_agents" to "🧠",
                "send_message" to "📨", "cronjob" to "⏰",
                "execute_code" to "🐍", "delegate_task" to "🔀")
            return emojiMap[toolName] ?: default
        }

        /** Build a short preview of a tool call's primary argument for display. */
        fun buildToolPreview(toolName: String, args: Map<String, Any?>, maxLen: Int? = null): String? {
            val limit = maxLen ?: toolPreviewMaxLen
            if (args.isEmpty()) return null

            val primaryArgs = mapOf(
                "terminal" to "command", "web_search" to "query", "web_extract" to "urls",
                "read_file" to "path", "write_file" to "path", "patch" to "path",
                "search_files" to "pattern", "browser_navigate" to "url",
                "browser_click" to "ref", "browser_type" to "text",
                "image_generate" to "prompt", "text_to_speech" to "text",
                "vision_analyze" to "question", "mixture_of_agents" to "user_prompt",
                "execute_code" to "code", "delegate_task" to "goal")

            // Special cases
            when (toolName) {
                "process" -> {
                    val action = args["action"]?.toString() ?: ""
                    val sid = args["session_id"]?.toString()?.take(16) ?: ""
                    val data = args["data"]?.toString()?.let { "\"${oneline(it.take(20))}\"" } ?: ""
                    return listOf(action, sid, data).filter { it.isNotEmpty() }.joinToString(" ")
                }
                "todo" -> {
                    val todos = args["todos"] as? List<*>
                    if (todos == null) return "reading task list"
                    val merge = args["merge"] as? Boolean ?: false
                    return if (merge) "updating ${todos.size} task(s)" else "planning ${todos.size} task(s)"
                }
                "memory" -> {
                    val action = args["action"]?.toString() ?: ""
                    val target = args["target"]?.toString() ?: ""
                    val content = (args["content"] ?: args["old_text"])?.toString() ?: ""
                    val preview = oneline(content.take(25))
                    return when (action) {
                        "add" -> "+$target: \"$preview\""
                        "replace" -> "~$target: \"${oneline(content.take(20))}\""
                        "remove" -> "-$target: \"${oneline(content.take(20))}\""
                        else -> action
                    }
                }
            }

            var key = primaryArgs[toolName]
            if (key == null) {
                for (fallback in listOf("query", "text", "command", "path", "name", "prompt", "code", "goal")) {
                    if (fallback in args) { key = fallback; break }
                }
            }
            if (key == null || key !in args) return null

            var value = args[key]?.toString() ?: return null
            var preview = oneline(value)
            if (preview.isEmpty()) return null
            if (limit > 0 && preview.length > limit) {
                preview = preview.take(limit - 3) + "..."
            }
            return preview
        }

        /** Generate a formatted tool completion line. */
        fun getCuteToolMessage(toolName: String, args: Map<String, Any?>, durationSec: Double, result: String? = null): String {
            val dur = String.format("%.1fs", durationSec)
            val (isFailure, failureSuffix) = detectToolFailure(toolName, result)
            fun trunc(s: String, n: Int = 40): String {
                if (toolPreviewMaxLen == 0) return s
                return if (s.length > n) s.take(n - 3) + "..." else s
            }
            fun wrap(line: String): String = if (isFailure) "$line$failureSuffix" else line

            val msg = when (toolName) {
                "web_search" -> "🔍 search    ${trunc(args["query"]?.toString() ?: "", 42)}  $dur"
                "web_extract" -> "📄 fetch     ${trunc(args["urls"]?.toString() ?: "pages", 35)}  $dur"
                "terminal" -> "💻 $         ${trunc(args["command"]?.toString() ?: "", 42)}  $dur"
                "read_file" -> "📖 read      ${trunc(args["path"]?.toString() ?: "", 35)}  $dur"
                "write_file" -> "✍️  write     ${trunc(args["path"]?.toString() ?: "", 35)}  $dur"
                "patch" -> "🔧 patch     ${trunc(args["path"]?.toString() ?: "", 35)}  $dur"
                "search_files" -> "🔎 grep      ${trunc(args["pattern"]?.toString() ?: "", 35)}  $dur"
                "todo" -> {
                    val todos = args["todos"] as? List<*>
                    if (todos == null) "📋 plan      reading tasks  $dur"
                    else "📋 plan      ${todos.size} task(s)  $dur"
                }
                "memory" -> "🧠 memory    ${args["action"] ?: ""}  $dur"
                "image_generate" -> "🎨 create    ${trunc(args["prompt"]?.toString() ?: "", 35)}  $dur"
                "send_message" -> "📨 send      ${args["target"] ?: "?"}: ${trunc(args["message"]?.toString() ?: "", 25)}  $dur"
                else -> {
                    val preview = buildToolPreview(toolName, args) ?: ""
                    "⚡ ${toolName.take(9)} ${trunc(preview, 35)}  $dur"
                }
            }
            return wrap("┊ $msg")
        }

        /** Build a formatted context pressure line for UI display. */
        fun formatContextPressure(compactionProgress: Double, thresholdTokens: Int, thresholdPercent: Double, compressionEnabled: Boolean = true): String {
            val pctInt = minOf((compactionProgress * 100).toInt(), 100)
            val barWidth = 20
            val filled = minOf((compactionProgress * barWidth).toInt(), barWidth)
            val bar = "█".repeat(filled) + "░".repeat(barWidth - filled)
            val thresholdK = if (thresholdTokens >= 1000) "${thresholdTokens / 1000}k" else thresholdTokens.toString()
            val thresholdPct = (thresholdPercent * 100).toInt()
            val hint = if (compressionEnabled) "compaction approaching" else "no auto-compaction"
            return "⚠ context $bar $pctInt% to compaction  ${thresholdK} threshold (${thresholdPct}%) · $hint"
        }

        /** Build a plain-text context pressure notification for messaging platforms. */
        fun formatContextPressureGateway(compactionProgress: Double, thresholdPercent: Double, compressionEnabled: Boolean = true): String {
            val pctInt = minOf((compactionProgress * 100).toInt(), 100)
            val barWidth = 20
            val filled = minOf((compactionProgress * barWidth).toInt(), barWidth)
            val bar = "█".repeat(filled) + "░".repeat(barWidth - filled)
            val thresholdPct = (thresholdPercent * 100).toInt()
            val hint = if (compressionEnabled) "Context compaction approaching (threshold: ${thresholdPct}% of window)." else "Auto-compaction is disabled — context may be truncated."
            return "⚠️ Context: $bar $pctInt% to compaction\n$hint"
        }
    }

    @Volatile private var _running = false
    private var _message = ""
    private val _spinnerFrames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
    private var _frameIdx = 0
    private var _startTime = 0L
    private var _thread: Thread? = null

    /** Write to the stdout captured at spinner creation time. */
    fun _write(text: String, end: String = "\n", flush: Boolean = false) {
        try {
            Log.d("Display", text)
        } catch (_: Exception) {
            // ignore
        }
    }
    /** Check if output is a real terminal, safe against closed streams. */
    fun _isTty(): Boolean = false
    /** Return True when stdout is prompt_toolkit's StdoutProxy. */
    fun _isPatchStdoutProxy(): Boolean = false
    fun _animate() {
        while (_running) {
            val frame = _spinnerFrames[_frameIdx % _spinnerFrames.size]
            val elapsed = (System.currentTimeMillis() - _startTime) / 1000.0
            Log.d("Display", "  $frame $_message (${String.format("%.1fs", elapsed)})")
            _frameIdx++
            try { Thread.sleep(120) } catch (_: InterruptedException) { break }
        }
    }
    fun start() {
        if (_running) return
        _running = true
        _startTime = System.currentTimeMillis()
        _thread = Thread({ _animate() }, "DisplaySpinner").apply {
            isDaemon = true
            start()
        }
    }
    fun updateText(newMessage: String) {
        _message = newMessage
    }
    /** Print a line above the spinner without disrupting animation. */
    fun printAbove(text: String) {
        _write("  $text", end = "\n", flush = true)
    }
    fun stop(finalMessage: String? = null) {
        _running = false
        _thread?.let {
            it.interrupt()
            it.join(500)
        }
        _thread = null
        if (finalMessage != null) {
            val elapsed = if (_startTime > 0) " (${String.format("%.1fs", (System.currentTimeMillis() - _startTime) / 1000.0)})" else ""
            Log.d("Display", "  $finalMessage$elapsed")
        }
    }

}

data class LocalEditSnapshot(
    val paths: MutableList<String> = mutableListOf(),
    val before: MutableMap<String, String?> = mutableMapOf()
)

class KawaiiSpinner {
    companion object {
        val KAWAII_WAITING = listOf(
            "(｡◕‿◕｡)", "(◕‿◕✿)", "٩(◕‿◕｡)۶", "(✿◠‿◠)", "( ˘▽˘)っ",
            "♪(´ε` )", "(◕ᴗ◕✿)", "ヾ(＾∇＾)", "(≧◡≦)", "(★ω★)")

        val KAWAII_THINKING = listOf(
            "(｡•́︿•̀｡)", "(◔_◔)", "(¬‿¬)", "( •_•)>⌐■-■", "(⌐■_■)",
            "(´･_･`)", "◉_◉", "(°ロ°)", "( ˘⌣˘)♡", "ヽ(>∀<☆)☆",
            "٩(๑❛ᴗ❛๑)۶", "(⊙_⊙)", "(¬_¬)", "( ͡° ͜ʖ ͡°)", "ಠ_ಠ")

        val THINKING_VERBS = listOf(
            "pondering", "contemplating", "musing", "cogitating", "ruminating",
            "deliberating", "mulling", "reflecting", "processing", "reasoning",
            "analyzing", "computing", "synthesizing", "formulating", "brainstorming")
    }

    fun getWaitingFaces(): List<Any?> {
        return KAWAII_WAITING
    }

    fun getThinkingFaces(): List<Any?> {
        return KAWAII_THINKING
    }

    fun getThinkingVerbs(): List<Any?> {
        return THINKING_VERBS
    }
}

// ─── Module-level constants (1:1 with display.py) ─────────────────────────

const val _RED: String = "\u001B[31m"
const val _RESET: String = "\u001B[0m"
const val _ANSI_RESET: String = "\u001B[0m"
const val _MAX_INLINE_DIFF_FILES: Int = 6
const val _MAX_INLINE_DIFF_LINES: Int = 80

// ─── Module-level state (1:1 with display.py) ─────────────────────────────

private var _toolPreviewMaxLen: Int = 0

private val _EMOJI_BY_TOOL: Map<String, String> = mapOf(
    "read_file" to "📄",
    "write_file" to "✏️",
    "patch" to "🩹",
    "terminal" to "💻",
    "search_files" to "🔎",
    "web_search" to "🌐",
    "web_extract" to "📰",
    "execute_code" to "🐍",
    "todo" to "🧾",
    "memory" to "🧠",
)

// ─── Module-level functions (1:1 with display.py) ─────────────────────────

fun _diffAnsi(): Map<String, String> = mapOf(
    "dim" to "",
    "file" to "",
    "hunk" to "",
    "minus" to "",
    "plus" to "",
    "reset" to "",
)

fun _diffDim(): String = ""
fun _diffFile(): String = ""
fun _diffHunk(): String = ""
fun _diffMinus(): String = ""
fun _diffPlus(): String = ""

fun setToolPreviewMaxLen(n: Int) {
    _toolPreviewMaxLen = n.coerceAtLeast(0)
}

fun getToolPreviewMaxLen(): Int = _toolPreviewMaxLen

private fun _getSkin(): Map<String, String> = mapOf(
    "tool_prefix" to "  ⎿",
)

fun getSkinToolPrefix(): String = _getSkin()["tool_prefix"] ?: "  ⎿"

fun getToolEmoji(toolName: String, default: String = "⚡"): String {
    return _EMOJI_BY_TOOL[toolName] ?: default
}

private fun _oneline(text: String): String {
    return text.replace("\n", " ").replace("\r", "").trim()
}

fun buildToolPreview(
    toolName: String,
    args: Map<String, Any?>,
    maxLen: Int? = null,
): String? {
    val limit = maxLen ?: if (_toolPreviewMaxLen > 0) _toolPreviewMaxLen else Int.MAX_VALUE
    val primary = when (toolName) {
        "read_file", "write_file", "patch" -> args["path"]?.toString()
        "terminal" -> args["command"]?.toString()
        "search_files" -> args["pattern"]?.toString()
        "web_search" -> args["query"]?.toString()
        else -> args.values.firstOrNull()?.toString()
    } ?: return null
    val one = _oneline(primary)
    return if (one.length > limit) one.substring(0, limit) + "…" else one
}

private fun _resolvedPath(path: String): java.io.File {
    val expanded = if (path.startsWith("~/"))
        (System.getProperty("user.home") ?: "") + path.substring(1)
    else path
    return java.io.File(expanded)
}

private fun _snapshotText(path: java.io.File): String? {
    return try { if (path.exists()) path.readText(Charsets.UTF_8) else null }
    catch (e: Exception) { null }
}

private fun _displayDiffPath(path: java.io.File): String {
    val home = System.getProperty("user.home") ?: ""
    val abs = path.absolutePath
    return if (home.isNotEmpty() && abs.startsWith(home)) "~" + abs.substring(home.length) else abs
}

private fun _resolveSkillManagePaths(args: Map<String, Any?>): List<java.io.File> {
    val name = args["name"]?.toString() ?: return emptyList()
    val file = args["file_path"]?.toString()
    val skillDir = java.io.File(SKILLS_DIR, name)
    return listOfNotNull(
        if (file != null) java.io.File(skillDir, file) else null,
        java.io.File(skillDir, "SKILL.md"),
    )
}

private fun _resolveLocalEditPaths(
    toolName: String,
    functionArgs: Map<String, Any?>?,
): List<java.io.File> {
    if (functionArgs == null) return emptyList()
    return when (toolName) {
        "write_file", "patch", "read_file" ->
            listOfNotNull((functionArgs["path"] as? String)?.let { _resolvedPath(it) })
        "skill_manage" -> _resolveSkillManagePaths(functionArgs)
        else -> emptyList()
    }
}

fun captureLocalEditSnapshot(
    toolName: String,
    functionArgs: Map<String, Any?>?,
): LocalEditSnapshot? {
    val paths = _resolveLocalEditPaths(toolName, functionArgs)
    if (paths.isEmpty()) return null
    val snap = LocalEditSnapshot()
    for (p in paths) {
        snap.paths += p.absolutePath
        snap.before[p.absolutePath] = _snapshotText(p)
    }
    return snap
}

private fun _resultSucceeded(result: String?): Boolean {
    if (result.isNullOrEmpty()) return true
    val lower = result.lowercase()
    return !(lower.contains("\"error\"") || lower.startsWith("error"))
}

private fun _diffFromSnapshot(snapshot: LocalEditSnapshot?): String? {
    if (snapshot == null) return null
    val lines = mutableListOf<String>()
    for (path in snapshot.paths) {
        val before = snapshot.before[path]
        val after = try { java.io.File(path).readText(Charsets.UTF_8) } catch (e: Exception) { null }
        if (before == after) continue
        lines += "--- $path"
        lines += "+++ $path"
    }
    return if (lines.isEmpty()) null else lines.joinToString("\n")
}

fun extractEditDiff(
    toolName: String,
    functionArgs: Map<String, Any?>?,
    result: String?,
    snapshot: LocalEditSnapshot? = null,
): String? {
    if (!_resultSucceeded(result)) return null
    return _diffFromSnapshot(snapshot)
}

private fun _emitInlineDiff(diffText: String, printFn: (String) -> Unit): Boolean {
    if (diffText.isEmpty()) return false
    printFn(diffText)
    return true
}

private fun _renderInlineUnifiedDiff(diff: String): List<String> {
    return diff.split("\n")
}

private fun _splitUnifiedDiffSections(diff: String): List<String> {
    val sections = mutableListOf<String>()
    var current = StringBuilder()
    for (line in diff.split("\n")) {
        if (line.startsWith("--- ") && current.isNotEmpty()) {
            sections += current.toString()
            current = StringBuilder()
        }
        current.appendLine(line)
    }
    if (current.isNotEmpty()) sections += current.toString()
    return sections
}

private fun _summarizeRenderedDiffSections(
    sections: List<String>,
    maxFiles: Int = _MAX_INLINE_DIFF_FILES,
    maxLines: Int = _MAX_INLINE_DIFF_LINES,
): String {
    if (sections.size > maxFiles) {
        return sections.take(maxFiles).joinToString("\n") +
            "\n... (+${sections.size - maxFiles} more files)"
    }
    val joined = sections.joinToString("\n")
    val lines = joined.split("\n")
    return if (lines.size > maxLines)
        lines.take(maxLines).joinToString("\n") + "\n... (+${lines.size - maxLines} more lines)"
    else joined
}

fun renderEditDiffWithDelta(
    toolName: String,
    functionArgs: Map<String, Any?>?,
    result: String?,
    snapshot: LocalEditSnapshot? = null,
): String? {
    val diff = extractEditDiff(toolName, functionArgs, result, snapshot) ?: return null
    val sections = _splitUnifiedDiffSections(diff)
    return _summarizeRenderedDiffSections(sections)
}

private fun _detectToolFailure(toolName: String, result: String?): Pair<Boolean, String> {
    if (result.isNullOrEmpty()) return Pair(false, "")
    val lower = result.lowercase()
    if (lower.contains("\"error\"") || lower.startsWith("error")) {
        return Pair(true, result.take(120))
    }
    return Pair(false, "")
}

fun getCuteToolMessage(
    toolName: String,
    args: Map<String, Any?> = emptyMap(),
    verb: String = "running",
): String {
    val preview = buildToolPreview(toolName, args) ?: ""
    val emoji = getToolEmoji(toolName)
    return if (preview.isNotEmpty()) "$emoji $verb $toolName($preview)" else "$emoji $verb $toolName"
}

private val SKILLS_DIR: java.io.File get() = java.io.File(
    com.xiaomo.hermes.hermes.getHermesHome(), "skills"
)

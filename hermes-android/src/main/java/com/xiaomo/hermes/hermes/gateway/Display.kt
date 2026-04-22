package com.xiaomo.hermes.hermes.gateway

import android.util.Log

private const val _TAG = "Display"

// ── Utility functions ───────────────────────────────────────────────

/** Convert hex color (#RRGGBB) to ANSI 24-bit foreground escape. */
fun hexFg(hex: String, fallback: IntArray = intArrayOf(180, 180, 180)): String {
    val clean = hex.removePrefix("#")
    val rgb = if (clean.length == 6) {
        try {
            intArrayOf(
                clean.substring(0, 2).toInt(16),
                clean.substring(2, 4).toInt(16),
                clean.substring(4, 6).toInt(16)
            )
        } catch (_unused: NumberFormatException) { fallback }
    } else fallback
    return "\u001b[38;2;${rgb[0]};${rgb[1]};${rgb[2]}m"
}

/** Truncate string to n characters, appending "…" if truncated. */
fun trunc(s: String, n: Int = 40): String {
    return if (s.length <= n) s else s.take(n - 1) + "…"
}

/** Wrap a line to terminal width (default 120). */
fun wrapLine(line: String, width: Int = 120): String {
    if (line.length <= width) return line
    return line.chunked(width).joinToString("\n")
}

/** Shorten a file path to last n segments. */
fun shortenPath(p: String, n: Int = 35): String {
    return if (p.length <= n) p else "…" + p.takeLast(n - 1)
}

// ── LocalEditSnapshot ───────────────────────────────────────────────

class LocalEditSnapshot

// ── KawaiiSpinner ───────────────────────────────────────────────────

class KawaiiSpinner(
    val message: String,
    val spinnerType: String,
    private val printFn: ((String) -> Unit)? = null
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    private fun write(text: String, end: String = "", flush: Boolean = true) {
        printFn?.invoke(text + end) ?: run {
            kotlin.io.print(text + end)
            if (flush) System.out.flush()
        }
    }

    private fun isTty(): Boolean = try {
        System.console() != null
    } catch (_unused: Exception) { false }

    private fun isPatchStdoutProxy(): Boolean = false

    private val spinnerFrames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
    private fun animate() {
        var i = 0
        while (running) {
            val frame = spinnerFrames[i % spinnerFrames.size]
            write("\r$frame $message")
            Thread.sleep(80)
            i++
        }
    }

    fun start() {
        running = true
        thread = Thread({ animate() }, "KawaiiSpinner").apply {
            isDaemon = true
            start()
        }
    }

    fun updateText(newMessage: String) {
        // Text is updated in animate loop; nothing to do here for simple impl
    }

    fun printAbove(text: String) {
        write("\r$text\n")
    }

    fun stop(finalMessage: String = "") {
        running = false
        thread?.join(500)
        thread = null
        if (finalMessage.isNotEmpty()) {
            write("\r$finalMessage\n")
        }
    }

    fun enter() { start() }

    fun exit(excType: String?, excVal: String?, excTb: String?) {
        stop()
    }
}

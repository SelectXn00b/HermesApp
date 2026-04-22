package com.xiaomo.hermes.hermes.tools

/**
 * Strip ANSI escape sequences from text.
 * Ported from ansi_strip.py
 */
object AnsiStrip {

    // Matches ANSI escape sequences: CSI sequences, OSC sequences, and simple escapes
    private val ANSI_REGEX = Regex(
        "(?:\\x1B\\[[0-?]*[ -/]*[@-~])|" +     // CSI sequences
        "(?:\\x1B\\][^\\x07]*\\x07)|" +          // OSC sequences
        "(?:\\x1B[@-Z\\\\-_])"                     // 2-byte sequences
    )

    // Matches carriage returns used for progress bars
    private val CARRIAGE_RETURN_REGEX = Regex("\\r\\n|\\r")

    /**
     * Strip ANSI escape sequences from the given text.
     */
    fun stripAnsi(text: String): String {
        if (text.isEmpty()) return text
        var result = ANSI_REGEX.replace(text, "")
        // Clean up carriage returns used for progress indicators
        result = CARRIAGE_RETURN_REGEX.replace(result, "\n")
        return result
    }

    /**
     * Strip ANSI and also collapse excessive blank lines.
     */
    fun stripAnsiAndClean(text: String): String {
        val stripped = stripAnsi(text)
        // Collapse 3+ consecutive newlines into 2
        return Regex("\\n{3,}").replace(stripped, "\n\n")
    }
}

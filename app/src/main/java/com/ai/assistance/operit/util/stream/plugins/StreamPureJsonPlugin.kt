package com.ai.assistance.operit.util.stream.plugins

/**
 * A stream processing plugin to identify JSON structures and extract only their content, filtering
 * out all structural characters (`{}[]",:`).
 *
 * This plugin intelligently parses a JSON object or array and emits only the meaningful content
 * (keys and values) as a stream of characters.
 */
class StreamPureJsonPlugin : BaseJsonPlugin() {

    /**
     * Determines whether a character should be emitted based on whether it is part of the JSON
     * content or a structural character.
     * @return `true` for content characters, `false` for structural characters.
     */
    override fun shouldEmit(c: Char): Boolean {
        if (inString) {
            // Inside a string, emit most characters
            return when (c) {
                '\\' -> false // Don't emit the escape char itself
                '"' -> false // Don't emit quotes
                else -> true
            }
        } else {
            // Outside a string, filter out structural chars and whitespace
            return when (c) {
                '{', '}', '[', ']', ':', ',' -> false
                else -> !c.isWhitespace()
            }
        }
    }
}

package com.ai.assistance.operit.util.stream.plugins

/**
 * A stream processing plugin that identifies and extracts complete JSON objects or arrays.
 *
 * This plugin treats an entire JSON structure as a single block and emits all of its
 * characters, including structural ones like `{}[]",:`.
 */
class StreamJsonPlugin : BaseJsonPlugin() {
    /**
     * Determines that all characters within the JSON structure should be emitted.
     * @return Always `true` to include every character.
     */
    override fun shouldEmit(c: Char): Boolean {
        return true
    }
}

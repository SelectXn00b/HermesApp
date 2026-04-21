package com.ai.assistance.operit.util.stream.plugins

import com.ai.assistance.operit.util.stream.StreamLogger

abstract class BaseJsonPlugin : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        protected set

    private var openBraceCount = 0
    private var openBracketCount = 0
    protected var inString = false
    private var isEscaped = false

    private var jsonType: JsonType = JsonType.NONE

    private enum class JsonType {
        NONE,
        OBJECT,
        ARRAY
    }

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        if (state == PluginState.IDLE) {
            when (c) {
                '{' -> {
                    state = PluginState.PROCESSING
                    jsonType = JsonType.OBJECT
                    openBraceCount = 1
                    return shouldEmit(c)
                }
                '[' -> {
                    state = PluginState.PROCESSING
                    jsonType = JsonType.ARRAY
                    openBracketCount = 1
                    return shouldEmit(c)
                }
                else -> return false // Not a JSON starting character
            }
        }

        if (state == PluginState.PROCESSING) {
            return handleCharInProcessing(c)
        }

        return false
    }

    private fun handleCharInProcessing(c: Char): Boolean {
        if (inString) {
            if (isEscaped) {
                isEscaped = false
            } else {
                when (c) {
                    '\\' -> isEscaped = true
                    '"' -> inString = false
                    else -> {}
                }
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{' -> if (jsonType == JsonType.OBJECT) openBraceCount++
                '[' -> if (jsonType == JsonType.ARRAY) openBracketCount++
                '}' -> {
                    if (jsonType == JsonType.OBJECT) {
                        openBraceCount--
                        if (openBraceCount == 0 && openBracketCount == 0) {
                            finishProcessing()
                        }
                    }
                }
                ']' -> {
                    if (jsonType == JsonType.ARRAY) {
                        openBracketCount--
                        if (openBracketCount == 0 && openBraceCount == 0) {
                            finishProcessing()
                        }
                    }
                }
            }
        }
        return shouldEmit(c)
    }
    
    private fun finishProcessing() {
        StreamLogger.d(this::class.java.simpleName, "JSON structure complete.")
        // The reset will be called by the collector loop when it sees the IDLE state.
        reset()
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        state = PluginState.IDLE
        openBraceCount = 0
        openBracketCount = 0
        inString = false
        isEscaped = false
        jsonType = JsonType.NONE
    }

    /**
     * Abstract method for subclasses to decide whether a character should be emitted.
     */
    protected abstract fun shouldEmit(c: Char): Boolean
} 
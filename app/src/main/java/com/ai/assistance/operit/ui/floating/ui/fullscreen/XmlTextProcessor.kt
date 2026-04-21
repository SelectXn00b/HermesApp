package com.ai.assistance.operit.ui.floating.ui.fullscreen

import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import com.ai.assistance.operit.util.stream.flatMap
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.stream.withTextEventChannel

/**
 * An object responsible for processing a character stream containing XML tags and converting them
 * into a human-readable plain text character stream in a fully streaming manner by stripping
 * out all XML tags and their content.
 */
object XmlTextProcessor {

    /**
     * Processes a stream of strings, finds and removes XML blocks (both tags and content),
     * and returns a new stream of characters containing only the plain text parts.
     *
     * @param sourceStream The original stream of strings, which may contain XML.
     * @return A stream of characters with XML blocks completely removed.
     */
    fun processStreamToText(sourceStream: Stream<String>): Stream<Char> {
        val xmlPlugin = StreamXmlPlugin(includeTagsInOutput = true)

        // Directly use the splitBy method of Stream<String>
        val textStream = sourceStream.splitBy(listOf(xmlPlugin)).flatMap { group ->
            if (group.tag == xmlPlugin) {
                // It's an XML group, filter it out completely by returning an empty stream.
                stream {}
            } else {
                // It's a plain text group, convert each string to chars
                stringStreamToCharStream(group.stream)
            }
        }
        val carrier = sourceStream as? TextStreamEventCarrier ?: return textStream
        return textStream.withTextEventChannel(carrier.eventChannel)
    }

    /**
     * Converts a Stream<String> to a Stream<Char> by flattening each string into its characters.
     */
    private fun stringStreamToCharStream(stringStream: Stream<String>): Stream<Char> = stream {
        var lastCharWasNewline = true
        stringStream.collect { str ->
            str.forEach { char ->
                val isCurrentCharNewline = (char == '\n')
                if (isCurrentCharNewline) {
                    if (!lastCharWasNewline) {
                        emit('\n')
                        lastCharWasNewline = true
                    }
                } else if (char.isWhitespace() && lastCharWasNewline) {
                    // Skip other whitespace if it follows a newline
                } else {
                    emit(char)
                    lastCharWasNewline = false
                }
            }
        }
    }
}

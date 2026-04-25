package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUtilsThinkingTest {

    @Test fun removeThinkingContent_trimsWhitespaceAroundRemovedBlocks() {
        assertEquals("answer", ChatUtils.removeThinkingContent("  <think>x</think> answer  "))
    }

    @Test fun extractThinkingContent_returnsEmptyThinkingWhenAbsent() {
        val result = ChatUtils.extractThinkingContent("answer")
        assertEquals("answer", result.first)
        assertEquals("", result.second)
    }

    @Test fun extractThinkingContent_trimsEachThinkingSegment() {
        val result = ChatUtils.extractThinkingContent("<think>  a  </think><thinking> b </thinking>c")
        assertEquals("c", result.first)
        assertEquals("a\nb", result.second)
    }

    @Test fun removeThinkingContent_preservesMiddleText() {
        assertEquals("ab", ChatUtils.removeThinkingContent("a<think>x</think>b"))
    }
}

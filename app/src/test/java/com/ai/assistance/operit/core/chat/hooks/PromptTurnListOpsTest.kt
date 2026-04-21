package com.ai.assistance.operit.core.chat.hooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PromptTurnListOpsTest {

    @Test fun withContent_returnsSameInstanceWhenUnchanged() {
        val turn = PromptTurn(PromptTurnKind.USER, "hi")
        assertSame(turn, turn.withContent("hi"))
    }

    @Test fun appendUserTurnIfMissing_appendsWhenLastDiffers() {
        val result = listOf(PromptTurn(PromptTurnKind.ASSISTANT, "a")).appendUserTurnIfMissing("hi")
        assertEquals(2, result.size)
        assertEquals("hi", result.last().content)
    }

    @Test fun mergeAdjacentTurns_mergesUserTurns() {
        val turns = listOf(PromptTurn(PromptTurnKind.USER, "a"), PromptTurn(PromptTurnKind.USER, "b"))
        assertEquals("a\nb", turns.mergeAdjacentTurns().single().content)
    }

    @Test fun roleContentPairs_roundTrip() {
        val pairs = listOf("user" to "hello")
        assertEquals(pairs, pairs.toPromptTurns().toRoleContentPairs())
    }
}

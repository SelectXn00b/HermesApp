package com.ai.assistance.operit.core.chat.hooks

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptTurnMergeTest {

    @Test fun mergeAdjacentTurns_doesNotMergeSystemTurns() {
        val turns = listOf(PromptTurn(PromptTurnKind.SYSTEM, "a"), PromptTurn(PromptTurnKind.SYSTEM, "b"))
        assertEquals(2, turns.mergeAdjacentTurns().size)
    }

    @Test fun mergeAdjacentTurns_doesNotMergeToolResultTurns() {
        val turns = listOf(PromptTurn(PromptTurnKind.TOOL_RESULT, "a"), PromptTurn(PromptTurnKind.TOOL_RESULT, "b"))
        assertEquals(2, turns.mergeAdjacentTurns().size)
    }

    @Test fun mergeAdjacentTurns_mergesAssistantTurns() {
        val turns = listOf(PromptTurn(PromptTurnKind.ASSISTANT, "a"), PromptTurn(PromptTurnKind.ASSISTANT, "b"))
        assertEquals("a\nb", turns.mergeAdjacentTurns().single().content)
    }
}

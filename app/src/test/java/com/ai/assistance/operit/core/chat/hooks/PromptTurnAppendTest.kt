package com.ai.assistance.operit.core.chat.hooks

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptTurnAppendTest {

    @Test fun appendUserTurnIfMissing_doesNothingForBlankMessage() {
        val turns = listOf(PromptTurn(PromptTurnKind.USER, "hello"))
        assertEquals(turns, turns.appendUserTurnIfMissing(""))
    }

    @Test fun appendUserTurnIfMissing_doesNothingForDuplicateTrailingUserMessage() {
        val turns = listOf(PromptTurn(PromptTurnKind.USER, "hello"))
        assertEquals(turns, turns.appendUserTurnIfMissing("hello"))
    }

    @Test fun appendUserTurnIfMissing_appendsAfterAssistant() {
        val turns = listOf(PromptTurn(PromptTurnKind.ASSISTANT, "hello"))
        assertEquals(2, turns.appendUserTurnIfMissing("next").size)
    }
}

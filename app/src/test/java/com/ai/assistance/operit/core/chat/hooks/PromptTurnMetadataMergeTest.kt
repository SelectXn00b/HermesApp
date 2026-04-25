package com.ai.assistance.operit.core.chat.hooks

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptTurnMetadataMergeTest {

    @Test fun mergeAdjacentTurns_keepsPreviousMetadataWhenCurrentEmpty() {
        val turns = listOf(
            PromptTurn(PromptTurnKind.USER, "a", metadata = mapOf("x" to 1)),
            PromptTurn(PromptTurnKind.USER, "b")
        )
        assertEquals(mapOf("x" to 1), turns.mergeAdjacentTurns().single().metadata)
    }

    @Test fun mergeAdjacentTurns_mergesMetadataMaps() {
        val turns = listOf(
            PromptTurn(PromptTurnKind.USER, "a", metadata = mapOf("x" to 1)),
            PromptTurn(PromptTurnKind.USER, "b", metadata = mapOf("y" to 2))
        )
        assertEquals(mapOf("x" to 1, "y" to 2), turns.mergeAdjacentTurns().single().metadata)
    }
}

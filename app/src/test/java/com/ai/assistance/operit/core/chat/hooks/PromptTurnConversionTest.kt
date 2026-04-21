package com.ai.assistance.operit.core.chat.hooks

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptTurnConversionTest {

    @Test fun fromRole_preservesToolName() {
        val turn = PromptTurn.fromRole("tool_call", "body", toolName = "run")
        assertEquals("run", turn.toolName)
    }

    @Test fun toPromptTurns_mapsRoleToKind() {
        val turns = listOf("assistant" to "hello").toPromptTurns()
        assertEquals(PromptTurnKind.ASSISTANT, turns.single().kind)
    }

    @Test fun toRoleContentPairs_usesRoleProperty() {
        val pairs = listOf(PromptTurn(PromptTurnKind.TOOL_RESULT, "done")).toRoleContentPairs()
        assertEquals(listOf("tool_result" to "done"), pairs)
    }
}

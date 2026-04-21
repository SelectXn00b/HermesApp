package com.ai.assistance.operit.core.chat.hooks

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptTurnKindTest {

    @Test fun fromRole_mapsAiToAssistant() {
        assertEquals(PromptTurnKind.ASSISTANT, PromptTurnKind.fromRole("ai"))
    }

    @Test fun fromRole_mapsToolUseToToolCall() {
        assertEquals(PromptTurnKind.TOOL_CALL, PromptTurnKind.fromRole("tool_use"))
    }

    @Test fun fromRole_mapsUnknownToUser() {
        assertEquals(PromptTurnKind.USER, PromptTurnKind.fromRole("unknown"))
    }

    @Test fun roleProperty_matchesKind() {
        assertEquals("summary", PromptTurn(PromptTurnKind.SUMMARY, "x").role)
    }
}

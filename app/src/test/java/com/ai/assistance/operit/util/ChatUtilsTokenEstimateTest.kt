package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUtilsTokenEstimateTest {

    @Test fun estimateTokenCount_emptyText_isZero() {
        assertEquals(0, ChatUtils.estimateTokenCount(""))
    }

    @Test fun estimateTokenCount_asciiOnlyRoundsDown() {
        assertEquals(2, ChatUtils.estimateTokenCount("abcdefgh"))
    }

    @Test fun estimateTokenCount_chineseOnlyUsesHigherWeight() {
        assertEquals(4, ChatUtils.estimateTokenCount("你好世"))
    }
}

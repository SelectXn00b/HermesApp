package com.ai.assistance.operit.core.tools.condition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionEvaluatorIdentifierTest {

    @Test fun stringCapabilityIsTruthyWhenNonEmpty() {
        assertTrue(ConditionEvaluator.evaluate("name", mapOf("name" to "operit")))
    }

    @Test fun emptyStringCapabilityIsFalsy() {
        assertFalse(ConditionEvaluator.evaluate("name", mapOf("name" to "")))
    }

    @Test fun numericCapabilityIsTruthyWhenNonZero() {
        assertTrue(ConditionEvaluator.evaluate("count", mapOf("count" to 2)))
    }
}

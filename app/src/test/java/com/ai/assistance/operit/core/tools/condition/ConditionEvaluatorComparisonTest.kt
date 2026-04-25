package com.ai.assistance.operit.core.tools.condition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionEvaluatorComparisonTest {

    @Test fun numericLessEqual_falseWhenGreater() {
        assertFalse(ConditionEvaluator.evaluate("score <= 2", mapOf("score" to 3)))
    }

    @Test fun numericGreater_falseWhenSmaller() {
        assertFalse(ConditionEvaluator.evaluate("score > 3", mapOf("score" to 2)))
    }

    @Test fun stringEquality_falseWhenDifferent() {
        assertFalse(ConditionEvaluator.evaluate("name == 'b'", mapOf("name" to "a")))
    }

    @Test fun stringInequality_trueWhenDifferent() {
        assertTrue(ConditionEvaluator.evaluate("name != 'b'", mapOf("name" to "a")))
    }
}

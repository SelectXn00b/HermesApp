package com.ai.assistance.operit.core.tools.condition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionEvaluatorBooleanLogicTest {

    @Test fun chainedAndRequiresAllTruthy() {
        assertTrue(ConditionEvaluator.evaluate("a && b && c", mapOf("a" to true, "b" to true, "c" to true)))
    }

    @Test fun chainedOrSucceedsWhenLastTruthy() {
        assertTrue(ConditionEvaluator.evaluate("a || b || c", mapOf("a" to false, "b" to false, "c" to true)))
    }

    @Test fun unaryNotAroundParentheses_isSupported() {
        assertTrue(ConditionEvaluator.evaluate("!(a && b)", mapOf("a" to true, "b" to false)))
    }

    @Test fun nullIsFalsyInBooleanContext() {
        assertFalse(ConditionEvaluator.evaluate("value", mapOf("value" to null)))
    }
}

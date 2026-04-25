package com.ai.assistance.operit.core.tools.condition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionEvaluatorLiteralTruthinessTest {

    @Test fun zero_isFalsy() {
        assertFalse(ConditionEvaluator.evaluate("0", emptyMap()))
    }

    @Test fun nonZeroNumber_isTruthy() {
        assertTrue(ConditionEvaluator.evaluate("1", emptyMap()))
    }

    @Test fun nullLiteral_isFalsy() {
        assertFalse(ConditionEvaluator.evaluate("null", emptyMap()))
    }
}

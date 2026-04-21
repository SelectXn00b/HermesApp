package com.ai.assistance.operit.core.tools.condition

import org.junit.Assert.assertFalse
import org.junit.Test

class ConditionEvaluatorParseFailureTest {

    @Test fun loneOperator_returnsFalse() {
        assertFalse(ConditionEvaluator.evaluate("&&", emptyMap()))
    }

    @Test fun brokenArrayLiteral_returnsFalse() {
        assertFalse(ConditionEvaluator.evaluate("[1,]", emptyMap()))
    }

    @Test fun unexpectedClosingParen_returnsFalse() {
        assertFalse(ConditionEvaluator.evaluate(")", emptyMap()))
    }
}

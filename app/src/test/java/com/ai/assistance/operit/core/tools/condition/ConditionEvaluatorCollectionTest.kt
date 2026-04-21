package com.ai.assistance.operit.core.tools.condition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionEvaluatorCollectionTest {

    @Test fun kotlinArray_isSupportedByInOperator() {
        assertTrue(ConditionEvaluator.evaluate("'a' in values", mapOf("values" to arrayOf("a", "b"))))
    }

    @Test fun enumLikeStringValues_compareByName() {
        assertTrue(ConditionEvaluator.evaluate("mode == 'DEBUG'", mapOf("mode" to "DEBUG")))
    }

    @Test fun emptyCollection_isFalsy() {
        assertFalse(ConditionEvaluator.evaluate("items", mapOf("items" to emptyList<String>())))
    }

    @Test fun collectionMissingValue_returnsFalseForInOperator() {
        assertFalse(ConditionEvaluator.evaluate("'z' in ['a','b']", emptyMap()))
    }
}

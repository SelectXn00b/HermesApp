package com.xiaomo.hermes.hermes.gateway.platforms.qqbot

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the top-level pure helpers declared in qqbot/Adapter.kt
 * alongside the QQAdapter class (whose instance methods need a Context and
 * are deferred to Robolectric):
 *   checkQqRequirements — always false on Android
 *   _coerceList — type-dispatch overload, different shape from Utils.coerceList
 */
class AdapterHelpersTest {

    // ─── checkQqRequirements ──────────────────────────────────────────────

    @Test
    fun `checkQqRequirements is always false on Android`() {
        // Python checks for importable modules; the Kotlin port hard-codes
        // false since the QQBot SDK cannot run on Android.
        assertFalse(checkQqRequirements())
    }

    // ─── _coerceList (Adapter.kt variant) ─────────────────────────────────

    @Test
    fun `_coerceList null returns empty`() {
        assertEquals(emptyList<String>(), _coerceList(null))
    }

    @Test
    fun `_coerceList string wraps into singleton unless empty`() {
        // Unlike Utils.coerceList, this variant does NOT split on commas.
        assertEquals(listOf("a,b,c"), _coerceList("a,b,c"))
        assertEquals(listOf("hello"), _coerceList("hello"))
    }

    @Test
    fun `_coerceList empty string returns empty`() {
        assertEquals(emptyList<String>(), _coerceList(""))
    }

    @Test
    fun `_coerceList list stringifies each entry`() {
        assertEquals(listOf("x", "y"), _coerceList(listOf("x", "y")))
        assertEquals(listOf("1", "2"), _coerceList(listOf(1, 2)))
    }

    @Test
    fun `_coerceList list drops null entries`() {
        assertEquals(listOf("a", "b"), _coerceList(listOf("a", null, "b")))
    }

    @Test
    fun `_coerceList array stringifies each entry`() {
        assertEquals(listOf("1", "2"), _coerceList(arrayOf(1, 2)))
        assertEquals(listOf("a", "b"), _coerceList(arrayOf("a", "b")))
    }

    @Test
    fun `_coerceList other toString and wraps`() {
        assertEquals(listOf("42"), _coerceList(42))
        assertEquals(listOf("true"), _coerceList(true))
    }
}

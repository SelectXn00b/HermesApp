package com.xiaomo.hermes.hermes.tools

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InterruptTest {

    @Before
    @After
    fun reset() {
        setInterrupt(false)
    }

    @Test
    fun `isInterrupted is false by default`() {
        assertFalse(isInterrupted())
    }

    @Test
    fun `setInterrupt true then isInterrupted returns true`() {
        setInterrupt(true)
        assertTrue(isInterrupted())
    }

    @Test
    fun `setInterrupt false clears flag`() {
        setInterrupt(true)
        assertTrue(isInterrupted())
        setInterrupt(false)
        assertFalse(isInterrupted())
    }

    @Test
    fun `setInterrupt is scoped to current thread by default`() {
        setInterrupt(true)
        var otherThreadSeenInterrupt = true
        val t = Thread {
            otherThreadSeenInterrupt = isInterrupted()
        }
        t.start()
        t.join()
        assertFalse(
            "other thread should not observe interrupt flag set from current thread",
            otherThreadSeenInterrupt,
        )
    }

    @Test
    fun `setInterrupt can target a specific thread id`() {
        val target = Thread.currentThread().id
        setInterrupt(true, threadId = target)
        assertTrue(isInterrupted())
    }

    @Test
    fun `ThreadAwareEventProxy set maps to setInterrupt true`() {
        val proxy = _ThreadAwareEventProxy()
        assertFalse(proxy.isSet())
        proxy.set()
        assertTrue(proxy.isSet())
        assertTrue(isInterrupted())
    }

    @Test
    fun `ThreadAwareEventProxy clear maps to setInterrupt false`() {
        val proxy = _ThreadAwareEventProxy()
        proxy.set()
        assertTrue(proxy.isSet())
        proxy.clear()
        assertFalse(proxy.isSet())
        assertFalse(isInterrupted())
    }

    @Test
    fun `ThreadAwareEventProxy wait returns current isSet state`() {
        val proxy = _ThreadAwareEventProxy()
        assertFalse(proxy.wait(timeout = 0.01))
        proxy.set()
        assertTrue(proxy.wait())
    }

    @Test
    fun `_interruptEvent singleton reads current thread state`() {
        assertFalse(_interruptEvent.isSet())
        _interruptEvent.set()
        assertTrue(_interruptEvent.isSet())
        _interruptEvent.clear()
        assertFalse(_interruptEvent.isSet())
    }
}

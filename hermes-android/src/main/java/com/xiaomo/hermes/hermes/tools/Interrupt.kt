package com.xiaomo.hermes.hermes.tools

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Interrupt handling for cancelling long-running operations.
 * Ported from interrupt.py
 */
object Interrupt {

    private val _interrupted = AtomicBoolean(false)

    /**
     * Check if an interrupt has been requested.
     */
    fun isInterrupted(): Boolean = _interrupted.get()

    /**
     * Set the interrupt flag.
     */
    fun interrupt() {
        _interrupted.set(true)
    }

    /**
     * Clear the interrupt flag.
     */
    fun clearInterrupt() {
        _interrupted.set(false)
    }

    /**
     * Check and clear the interrupt flag atomically.
     * Returns true if an interrupt was pending.
     */
    fun checkAndClear(): Boolean {
        return _interrupted.getAndSet(false)
    }

    /**
     * Throw an InterruptedException if the interrupt flag is set.
     */
    fun throwIfInterrupted() {
        if (isInterrupted()) {
            clearInterrupt()
            throw InterruptedException("Operation interrupted by user")
        }
    }

    /** Set the interrupt flag (alias for interrupt()). */
    fun set() = interrupt()

    /** Clear the interrupt flag (alias for clearInterrupt()). */
    fun clear() = clearInterrupt()

    /** Check if interrupted (alias for isInterrupted()). */
    fun isSet(): Boolean = isInterrupted()

    /** Set interrupt for a specific thread (stub — Android uses single interrupt). */
    fun setInterrupt(active: Boolean, threadId: Long? = null) {
        if (active) interrupt() else clearInterrupt()
    }

    /** Wait for interrupt with timeout (returns current state immediately). */
    fun wait(timeoutMs: Long = 0): Boolean = isInterrupted()
}

/**
 * Drop-in proxy that maps threading.Event methods to per-thread state.
 * Ported from _ThreadAwareEventProxy in interrupt.py.
 */
class _ThreadAwareEventProxy {
    fun isSet(): Boolean = Interrupt.isInterrupted()
    fun set() = Interrupt.interrupt()
    fun clear() = Interrupt.clearInterrupt()
    fun wait(timeoutMs: Long? = null): Boolean = isSet()
}

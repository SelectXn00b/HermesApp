/**
 * Per-thread interrupt signaling for all tools.
 *
 * Mirrors tools/interrupt.py: a set of interrupted thread IDs guarded
 * by a lock; set_interrupt() and is_interrupted() check the current
 * thread by default. _ThreadAwareEventProxy wraps the same surface
 * as a legacy threading.Event-style shim.
 *
 * Ported from tools/interrupt.py
 */
package com.xiaomo.hermes.hermes.tools

private val _DEBUG_INTERRUPT: Boolean = !System.getenv("HERMES_DEBUG_INTERRUPT").isNullOrBlank()

private val _interruptedThreads: MutableSet<Long> = mutableSetOf()
private val _lock = Any()

fun setInterrupt(active: Boolean, threadId: Long? = null) {
    val tid = threadId ?: Thread.currentThread().id
    synchronized(_lock) {
        if (active) _interruptedThreads.add(tid) else _interruptedThreads.remove(tid)
    }
}

fun isInterrupted(): Boolean {
    val tid = Thread.currentThread().id
    return synchronized(_lock) { tid in _interruptedThreads }
}

/**
 * Drop-in proxy that maps threading.Event methods to per-thread state.
 */
class _ThreadAwareEventProxy {
    fun isSet(): Boolean = isInterrupted()
    fun set(): Unit = setInterrupt(true)
    fun clear(): Unit = setInterrupt(false)
    fun wait(timeout: Double? = null): Boolean = isSet()
}

val _interruptEvent: _ThreadAwareEventProxy = _ThreadAwareEventProxy()

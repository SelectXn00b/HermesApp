/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/signal/(all)
 *
 * AndroidForClaw adaptation: Signal channel runtime.
 */
package com.xiaomo.signal.session

import android.util.Log

/**
 * Signal message deduplication
 */
class SignalDedup {
    companion object {
        private const val TAG = "SignalDedup"
        private const val MAX_CACHE_SIZE = 1000
    }

    private val seen = LinkedHashSet<String>()

    fun isDuplicate(messageId: String): Boolean {
        if (seen.contains(messageId)) {
            Log.d(TAG, "Duplicate message: $messageId")
            return true
        }
        seen.add(messageId)
        if (seen.size > MAX_CACHE_SIZE) {
            val iter = seen.iterator()
            iter.next()
            iter.remove()
        }
        return false
    }
}

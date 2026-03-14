/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/telegram/(all)
 *
 * AndroidForClaw adaptation: Telegram channel runtime.
 */
package com.xiaomo.telegram.session

import android.util.Log

/**
 * Telegram message deduplication
 */
class TelegramDedup {
    companion object {
        private const val TAG = "TelegramDedup"
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

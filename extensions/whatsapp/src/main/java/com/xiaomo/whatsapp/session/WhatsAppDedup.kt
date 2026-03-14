/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/whatsapp/(all)
 *
 * AndroidForClaw adaptation: WhatsApp channel runtime.
 */
package com.xiaomo.whatsapp.session

import android.util.Log

/**
 * WhatsApp message deduplication
 */
class WhatsAppDedup {
    companion object {
        private const val TAG = "WhatsAppDedup"
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

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/slack/(all)
 *
 * AndroidForClaw adaptation: Slack channel runtime.
 */
package com.xiaomo.slack.session

import android.util.Log

/**
 * Slack message deduplication
 */
class SlackDedup {
    companion object {
        private const val TAG = "SlackDedup"
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

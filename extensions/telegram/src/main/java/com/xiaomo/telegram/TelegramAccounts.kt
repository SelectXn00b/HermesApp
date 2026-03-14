/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/telegram/(all)
 *
 * AndroidForClaw adaptation: Telegram channel runtime.
 */
package com.xiaomo.telegram

import android.util.Log

/**
 * Telegram multi-account support
 */
class TelegramAccounts {
    companion object {
        private const val TAG = "TelegramAccounts"
    }

    fun resolveAccount(accountId: String?): TelegramConfig {
        Log.d(TAG, "Resolving account: $accountId")
        // TODO: Multi-account resolution
        return TelegramConfig()
    }
}

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/signal/(all)
 *
 * AndroidForClaw adaptation: Signal channel runtime.
 */
package com.xiaomo.signal

import android.util.Log

/**
 * Signal multi-account support
 */
class SignalAccounts {
    companion object {
        private const val TAG = "SignalAccounts"
    }

    fun resolveAccount(accountId: String?): SignalConfig {
        Log.d(TAG, "Resolving account: $accountId")
        // TODO: Multi-account resolution
        return SignalConfig()
    }
}

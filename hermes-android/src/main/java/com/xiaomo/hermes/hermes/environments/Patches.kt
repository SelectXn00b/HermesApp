/** 1:1 对齐 hermes/environments/patches.py */
package com.xiaomo.hermes.hermes.environments

import android.util.Log

/**
 * Monkey patches for making hermes-agent tools work inside async frameworks (Atropos).
 *
 * Problem:
 *     Some tools use asyncio.run() internally (e.g., Modal backend via SWE-ReX,
 *     web_extract). This crashes when called from inside Atropos's event loop because
 *     asyncio.run() can't be nested.
 *
 * Solution:
 *     The Modal environment (tools/environments/modal.py) now uses a dedicated
 *     _AsyncWorker thread internally, making it safe for both CLI and Atropos use.
 *     No monkey-patching is required.
 *
 *     This module is kept for backward compatibility. applyPatches() is a no-op.
 *
 * Usage:
 *     Call applyPatches() once at import time (done automatically by hermes_base_env.py).
 *     This is idempotent and safe to call multiple times.
 */
object Patches {

    private const val _TAG = "Patches"

    @Volatile
    private var _patchesApplied = false

    /**
     * Apply all monkey patches needed for Atropos compatibility.
     */
    fun applyPatches() {
        if (_patchesApplied) {
            return
        }
        Log.d(_TAG, "applyPatches() called; no patches needed (async safety is built-in)")
        _patchesApplied = true
    }
}

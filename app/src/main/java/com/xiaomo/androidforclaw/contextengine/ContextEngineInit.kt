package com.xiaomo.androidforclaw.contextengine

import java.util.concurrent.atomic.AtomicBoolean

/**
 * OpenClaw module: context-engine
 * Source: OpenClaw/src/context-engine/init.ts
 *
 * Ensures built-in context engines are registered exactly once.
 */

private val initialized = AtomicBoolean(false)

/**
 * Idempotent initializer — registers all built-in context engines.
 * Safe to call from any thread; the first call wins.
 */
fun ensureContextEnginesInitialized() {
    if (!initialized.compareAndSet(false, true)) return

    // Register the legacy (pass-through) context engine with "core" ownership.
    registerLegacyContextEngine()
}

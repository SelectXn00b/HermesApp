/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: utility helpers.
 */
package com.xiaomo.androidforclaw.util

/**
 * MMKV configuration keys
 * New architecture focus: AgentLoop + Tools
 */
enum class MMKVKeys(val key: String) {
    BUG_SWITCH("bug_switch"),

    // ========== Retained features ==========
    // Floating window display switch (EasyFloat)
    FLOAT_WINDOW_ENABLED("float_window_enabled"),

    // Exploration mode switch (false: Planning mode, true: Exploration mode)
    EXPLORATION_MODE("exploration_mode")
}

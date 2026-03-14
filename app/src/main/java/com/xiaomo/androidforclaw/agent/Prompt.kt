package com.xiaomo.androidforclaw.agent

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: agent runtime support.
 */


data class Prompt(val system: String = "", val user: String)
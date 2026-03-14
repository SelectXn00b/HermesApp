package com.xiaomo.androidforclaw.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: unify LLM/provider failure reporting.
 */


/**
 * Legacy LLM API Exception
 */
class LLMException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

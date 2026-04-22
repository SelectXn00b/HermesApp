package com.xiaomo.hermes.hermes.agent.transports

/**
 * Shared types for normalized provider responses.
 *
 * These dataclasses define the canonical shape that all provider adapters
 * normalize responses to. The shared surface is intentionally minimal —
 * only fields that every downstream consumer reads are top-level.
 * Protocol-specific state goes in `providerData` dicts (response-level
 * and per-tool-call) so that protocol-aware code paths can access it
 * without polluting the shared type.
 *
 * Ported from agent/transports/types.py
 */

import org.json.JSONObject

/**
 * A normalized tool call from any provider.
 *
 * `id` is the protocol's canonical identifier — what gets used in
 * `tool_call_id` / `tool_use_id` when constructing tool result messages.
 * May be null when the provider omits it; the agent fills it via
 * `_deterministicCallId()` before storing in history.
 *
 * `providerData` carries per-tool-call protocol metadata that only
 * protocol-aware code reads:
 *
 *  * Codex: `{"call_id": "call_XXX", "response_item_id": "fc_XXX"}`
 *  * Gemini: `{"extra_content": {"google": {"thought_signature": "..."}}}`
 *  * Others: null
 */
data class ToolCall(
    val id: String?,
    val name: String,
    val arguments: String,  // JSON string
    val providerData: Map<String, Any?>? = null)

/** Token usage from an API response. */
data class Usage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val cachedTokens: Int = 0)

/**
 * Normalized API response from any provider.
 *
 * Shared fields are truly cross-provider — every caller can rely on them
 * without branching on apiMode. Protocol-specific state goes in
 * `providerData` so that only protocol-aware code paths read it.
 *
 * Response-level `providerData` examples:
 *
 *  * Anthropic: `{"reasoning_details": [...]}`
 *  * Codex: `{"codex_reasoning_items": [...]}`
 *  * Others: null
 */
data class NormalizedResponse(
    val content: String?,
    val toolCalls: List<ToolCall>?,
    val finishReason: String,  // "stop", "tool_calls", "length", "content_filter"
    val reasoning: String? = null,
    val usage: Usage? = null,
    val providerData: Map<String, Any?>? = null)

// ---------------------------------------------------------------------------
// Factory helpers
// ---------------------------------------------------------------------------

/**
 * Build a [ToolCall], auto-serialising *arguments* if it's a map.
 *
 * Any extra provider fields are collected into [ToolCall.providerData].
 */
fun buildToolCall(
    id: String?,
    name: String,
    arguments: Any?,
    providerFields: Map<String, Any?>? = null): ToolCall {
    val argsStr = when (arguments) {
        is Map<*, *> -> JSONObject(arguments).toString()
        null -> "null"
        else -> arguments.toString()
    }
    val pd = if (!providerFields.isNullOrEmpty()) providerFields.toMap() else null
    return ToolCall(id = id, name = name, arguments = argsStr, providerData = pd)
}

/**
 * Translate a provider-specific stop reason to the normalised set.
 *
 * Falls back to "stop" for unknown or null reasons.
 */
fun mapFinishReason(reason: String?, mapping: Map<String, String>): String {
    if (reason == null) return "stop"
    return mapping[reason] ?: "stop"
}

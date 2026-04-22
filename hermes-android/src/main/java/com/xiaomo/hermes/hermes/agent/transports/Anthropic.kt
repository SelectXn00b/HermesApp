package com.xiaomo.hermes.hermes.agent.transports

/**
 * Anthropic Messages API transport.
 *
 * Delegates to the existing adapter functions in agent/anthropic_adapter.py.
 * This transport owns format conversion and normalization — NOT client lifecycle.
 *
 * Ported from agent/transports/anthropic.py
 */

/**
 * Transport for api_mode="anthropic_messages".
 *
 * Wraps the existing functions in AnthropicAdapter.kt behind the
 * [ProviderTransport] base.  Each method delegates — no logic is duplicated.
 */
class AnthropicTransport : ProviderTransport() {

    override val apiMode: String = "anthropic_messages"

    /**
     * Convert OpenAI messages to Anthropic (system, messages) tuple.
     *
     * kwargs:
     *   base_url: String? — affects thinking signature handling.
     */
    override fun convertMessages(messages: List<Map<String, Any?>>, kwargs: Map<String, Any?>): Any? {
        TODO("Delegate to AnthropicAdapter.convertMessagesToAnthropic — not yet ported to Kotlin")
    }

    /** Convert OpenAI tool schemas to Anthropic input_schema format. */
    override fun convertTools(tools: List<Map<String, Any?>>): Any? {
        TODO("Delegate to AnthropicAdapter.convertToolsToAnthropic — not yet ported to Kotlin")
    }

    /**
     * Build Anthropic messages.create() kwargs.
     *
     * Calls convertMessages and convertTools internally.
     *
     * params (all optional):
     *   max_tokens: Int
     *   reasoning_config: Map?
     *   tool_choice: String?
     *   is_oauth: Boolean
     *   preserve_dots: Boolean
     *   context_length: Int?
     *   base_url: String?
     *   fast_mode: Boolean
     */
    override fun buildKwargs(
        model: String,
        messages: List<Map<String, Any?>>,
        tools: List<Map<String, Any?>>?,
        params: Map<String, Any?>): Map<String, Any?> {
        TODO("Delegate to AnthropicAdapter.buildAnthropicKwargs — not yet ported to Kotlin")
    }

    /**
     * Normalize Anthropic response to [NormalizedResponse].
     *
     * kwargs:
     *   strip_tool_prefix: Boolean — strip 'mcp_mcp_' prefixes from tool names.
     */
    override fun normalizeResponse(response: Any?, kwargs: Map<String, Any?>): NormalizedResponse {
        TODO("Delegate to AnthropicAdapter.normalizeAnthropicResponseV2 — not yet ported to Kotlin")
    }

    /** Check Anthropic response structure is valid. */
    override fun validateResponse(response: Any?): Boolean {
        if (response == null) return false
        val content = _getAttr(response, "content") as? List<*> ?: return false
        return content.isNotEmpty()
    }

    /** Extract Anthropic cache_read and cache_creation token counts. */
    override fun extractCacheStats(response: Any?): Map<String, Int>? {
        val usage = _getAttr(response, "usage") ?: return null
        val cached = (_getAttr(usage, "cache_read_input_tokens") as? Number)?.toInt() ?: 0
        val written = (_getAttr(usage, "cache_creation_input_tokens") as? Number)?.toInt() ?: 0
        if (cached != 0 || written != 0) {
            return mapOf("cached_tokens" to cached, "creation_tokens" to written)
        }
        return null
    }

    /** Map Anthropic stop_reason to OpenAI finish_reason. */
    override fun mapFinishReason(rawReason: String): String {
        return _STOP_REASON_MAP[rawReason] ?: "stop"
    }

    companion object {
        /** Promote the adapter's canonical mapping to module level so it's shared. */
        private val _STOP_REASON_MAP = mapOf(
            "end_turn" to "stop",
            "tool_use" to "tool_calls",
            "max_tokens" to "length",
            "stop_sequence" to "stop",
            "refusal" to "content_filter",
            "model_context_window_exceeded" to "length")
    }
}

/** Dynamic attribute access for duck-typed provider SDK objects. */
private fun _getAttr(obj: Any?, name: String): Any? {
    if (obj == null) return null
    if (obj is Map<*, *>) return obj[name]
    return try {
        val field = obj.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.get(obj)
    } catch (_: NoSuchFieldException) {
        try {
            val camel = name.split('_').mapIndexed { i, part ->
                if (i == 0) part else part.replaceFirstChar { it.uppercase() }
            }.joinToString("")
            val field = obj.javaClass.getDeclaredField(camel)
            field.isAccessible = true
            field.get(obj)
        } catch (_: Exception) {
            null
        }
    } catch (_: Exception) {
        null
    }
}

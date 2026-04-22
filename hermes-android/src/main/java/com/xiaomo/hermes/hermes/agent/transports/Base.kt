package com.xiaomo.hermes.hermes.agent.transports

/**
 * Abstract base for provider transports.
 *
 * A transport owns the data path for one api_mode:
 *   convert_messages → convert_tools → build_kwargs → normalize_response
 *
 * It does NOT own: client construction, streaming, credential refresh,
 * prompt caching, interrupt handling, or retry logic.  Those stay on AIAgent.
 *
 * Ported from agent/transports/base.py
 */

/** Base class for provider-specific format conversion and normalization. */
abstract class ProviderTransport {

    /** The api_mode string this transport handles (e.g. "anthropic_messages"). */
    abstract fun apiMode(): String

    /**
     * Convert OpenAI-format messages to provider-native format.
     *
     * Returns provider-specific structure (e.g. (system, messages) for Anthropic,
     * or the messages list unchanged for chat_completions).
     */
    abstract fun convertMessages(messages: List<Map<String, Any?>>, kwargs: Map<String, Any?> = emptyMap()): Any?

    /**
     * Convert OpenAI-format tool definitions to provider-native format.
     *
     * Returns provider-specific tool list (e.g. Anthropic input_schema format).
     */
    abstract fun convertTools(tools: List<Map<String, Any?>>): Any?

    /**
     * Build the complete API call kwargs dict.
     *
     * This is the primary entry point — it typically calls convertMessages()
     * and convertTools() internally, then adds model-specific config.
     *
     * Returns a dict ready to be passed to the provider's SDK client.
     */
    abstract fun buildKwargs(
        model: String,
        messages: List<Map<String, Any?>>,
        tools: List<Map<String, Any?>>? = null,
        params: Map<String, Any?> = emptyMap()): Map<String, Any?>

    /**
     * Normalize a raw provider response to the shared NormalizedResponse type.
     *
     * This is the only method that returns a transport-layer type.
     */
    abstract fun normalizeResponse(response: Any?, kwargs: Map<String, Any?> = emptyMap()): NormalizedResponse

    /**
     * Optional: check if the raw response is structurally valid.
     *
     * Returns True if valid, False if the response should be treated as invalid.
     * Default implementation always returns True.
     */
    open fun validateResponse(response: Any?): Boolean = true

    /**
     * Optional: extract provider-specific cache hit/creation stats.
     *
     * Returns dict with "cached_tokens" and "creation_tokens", or null.
     * Default returns null.
     */
    open fun extractCacheStats(response: Any?): Map<String, Int>? = null

    /**
     * Optional: map provider-specific stop reason to OpenAI equivalent.
     *
     * Default returns the raw reason unchanged.  Override for providers
     * with different stop reason vocabularies.
     */
    open fun mapFinishReason(rawReason: String): String = rawReason
}

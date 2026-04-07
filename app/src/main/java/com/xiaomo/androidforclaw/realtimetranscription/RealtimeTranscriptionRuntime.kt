package com.xiaomo.androidforclaw.realtimetranscription

import com.xiaomo.androidforclaw.config.OpenClawConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * OpenClaw module: realtime-transcription
 * Source: OpenClaw/src/realtime-transcription/runtime.ts
 *
 * Provider registry and event bus for real-time transcription sessions.
 * Providers register themselves at startup; callers look up by ID/alias.
 * An event listener list allows higher-level code (e.g. agent) to observe
 * transcription events without coupling to a specific provider.
 */
object RealtimeTranscriptionRuntime {

    // -----------------------------------------------------------------------
    // Provider registry
    // -----------------------------------------------------------------------

    private val providers = ConcurrentHashMap<String, RealtimeTranscriptionProvider>()

    fun register(provider: RealtimeTranscriptionProvider) {
        providers[provider.id] = provider
        provider.aliases.forEach { alias -> providers[alias.lowercase()] = provider }
    }

    fun unregister(providerId: String) {
        val provider = providers.remove(providerId)
        provider?.aliases?.forEach { providers.remove(it.lowercase()) }
    }

    fun listProviders(config: OpenClawConfig? = null): List<RealtimeTranscriptionProvider> {
        return providers.values.distinctBy { it.id }
    }

    fun getProvider(
        providerId: String?,
        config: OpenClawConfig? = null
    ): RealtimeTranscriptionProvider? {
        if (providerId == null) return providers.values.firstOrNull()
        return providers[providerId] ?: providers[providerId.lowercase()]
    }

    fun canonicalizeProviderId(
        providerId: String?,
        config: OpenClawConfig? = null
    ): RealtimeTranscriptionProviderId? {
        if (providerId == null) return null
        val normalized = providerId.lowercase().trim()
        val provider = providers[normalized]
            ?: providers.values.find { p -> p.aliases.any { it.lowercase() == normalized } }
        return provider?.id
    }

    // -----------------------------------------------------------------------
    // Event bus — observer pattern for transcription events
    // -----------------------------------------------------------------------

    private val listeners = CopyOnWriteArrayList<RealtimeTranscriptionListener>()

    fun addListener(listener: RealtimeTranscriptionListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: RealtimeTranscriptionListener) {
        listeners.remove(listener)
    }

    /** Dispatch a transcription event to all registered listeners. */
    fun dispatchEvent(event: RealtimeTranscriptionEvent) {
        for (listener in listeners) {
            try {
                listener.onEvent(event)
            } catch (_: Exception) {
                // Swallow listener errors to avoid breaking the dispatch loop.
            }
        }
    }

    // -----------------------------------------------------------------------
    // Enablement check
    // -----------------------------------------------------------------------

    fun isTranscriptionEnabled(config: OpenClawConfig): Boolean {
        val skill = config.skills.entries["realtime-transcription"]
        return skill?.enabled ?: false
    }
}

package com.xiaomo.androidforclaw.realtimevoice

import com.xiaomo.androidforclaw.config.OpenClawConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * OpenClaw module: realtime-voice
 * Source: OpenClaw/src/realtime-voice/runtime.ts
 *
 * Provider registry and event bus for real-time voice sessions.
 * Mirrors the realtime-transcription pattern: providers register at startup,
 * callers look up by ID/alias, and an event listener list lets higher-level
 * code observe voice events without coupling to a specific provider.
 */
object RealtimeVoiceRuntime {

    // -----------------------------------------------------------------------
    // Provider registry
    // -----------------------------------------------------------------------

    private val providers = ConcurrentHashMap<String, RealtimeVoiceProvider>()

    fun register(provider: RealtimeVoiceProvider) {
        providers[provider.id] = provider
        provider.aliases.forEach { alias -> providers[alias.lowercase()] = provider }
    }

    fun unregister(providerId: String) {
        val provider = providers.remove(providerId)
        provider?.aliases?.forEach { providers.remove(it.lowercase()) }
    }

    fun listProviders(config: OpenClawConfig? = null): List<RealtimeVoiceProvider> {
        return providers.values.distinctBy { it.id }
    }

    fun getProvider(
        providerId: String?,
        config: OpenClawConfig? = null
    ): RealtimeVoiceProvider? {
        if (providerId == null) return providers.values.firstOrNull()
        return providers[providerId] ?: providers[providerId.lowercase()]
    }

    fun canonicalizeProviderId(
        providerId: String?,
        config: OpenClawConfig? = null
    ): String? {
        if (providerId == null) return null
        val normalized = providerId.lowercase().trim()
        val provider = providers[normalized]
            ?: providers.values.find { p -> p.aliases.any { it.lowercase() == normalized } }
        return provider?.id
    }

    // -----------------------------------------------------------------------
    // Event bus — observer pattern for voice events
    // -----------------------------------------------------------------------

    private val listeners = CopyOnWriteArrayList<RealtimeVoiceListener>()

    fun addListener(listener: RealtimeVoiceListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: RealtimeVoiceListener) {
        listeners.remove(listener)
    }

    /** Dispatch a voice event to all registered listeners. */
    fun dispatchEvent(event: RealtimeVoiceEvent) {
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

    fun isVoiceEnabled(config: OpenClawConfig): Boolean {
        val skill = config.skills.entries["realtime-voice"]
        return skill?.enabled ?: false
    }
}

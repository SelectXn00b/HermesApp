package com.xiaomo.androidforclaw.contextengine

import com.xiaomo.androidforclaw.config.OpenClawConfig
import com.xiaomo.androidforclaw.shared.GlobalSingleton

/**
 * OpenClaw module: context-engine
 * Source: OpenClaw/src/context-engine/registry.ts
 *
 * Process-scoped context-engine registry backed by [GlobalSingleton].
 */

typealias ContextEngineFactory = suspend () -> ContextEngine

// ── Registration result ──

sealed class ContextEngineRegistrationResult {
    data object Ok : ContextEngineRegistrationResult()
    data class Error(val existingOwner: String) : ContextEngineRegistrationResult()
}

// ── Internal registry entry ──

private data class RegistryEntry(
    val factory: ContextEngineFactory,
    val owner: String
)

// ── GlobalSingleton keys ──

private const val REGISTRY_KEY = "contextEngine:registry"

/** Core owner string — protected from overwrite by non-core callers. */
private const val CORE_OWNER = "core"

/** Default owner string used by the public SDK entry point. */
private const val PUBLIC_SDK_OWNER = "public-sdk"

// ── Registry helpers (process-scoped via GlobalSingleton) ──

private fun registry(): MutableMap<String, RegistryEntry> {
    return GlobalSingleton.resolve(REGISTRY_KEY) { mutableMapOf<String, RegistryEntry>() }
}

// ── Public API ──

/**
 * Register a context-engine factory for a specific owner.
 *
 * Aligned with `registerContextEngineForOwner()` in registry.ts:
 * - If the id is already registered by the same owner, silently succeeds (idempotent).
 * - If the id is registered by a different owner, returns [ContextEngineRegistrationResult.Error].
 * - Core-owner registrations cannot be overwritten by non-core callers.
 *
 * @param opts reserved for future options (e.g. force-replace). Currently unused.
 */
fun registerContextEngineForOwner(
    id: String,
    factory: ContextEngineFactory,
    owner: String,
    @Suppress("UNUSED_PARAMETER") opts: Map<String, Any?>? = null
): ContextEngineRegistrationResult {
    val reg = registry()
    val existing = reg[id]

    if (existing != null) {
        // Same owner re-registering — idempotent
        if (existing.owner == owner) {
            reg[id] = RegistryEntry(factory, owner)
            return ContextEngineRegistrationResult.Ok
        }
        // Different owner — reject
        return ContextEngineRegistrationResult.Error(existingOwner = existing.owner)
    }

    reg[id] = RegistryEntry(factory, owner)
    return ContextEngineRegistrationResult.Ok
}

/**
 * Public SDK entry point — registers with the "public-sdk" owner.
 * Aligned with `registerContextEngine()` in registry.ts.
 */
fun registerContextEngine(
    id: String,
    factory: ContextEngineFactory
): ContextEngineRegistrationResult {
    return registerContextEngineForOwner(id, factory, PUBLIC_SDK_OWNER)
}

/**
 * Retrieve the factory for a given engine id.
 */
fun getContextEngineFactory(id: String): ContextEngineFactory? {
    return registry()[id]?.factory
}

/**
 * List all registered engine ids.
 */
fun listContextEngineIds(): List<String> {
    return registry().keys.toList()
}

/**
 * Resolve the active context-engine from configuration.
 *
 * Resolution order (aligned with registry.ts):
 * 1. `config.plugins.slots.contextEngine` if set
 * 2. Fallback to "legacy"
 *
 * Throws if the resolved id has no registered factory.
 */
suspend fun resolveContextEngine(config: OpenClawConfig? = null): ContextEngine {
    val slotId = config?.plugins?.slots?.contextEngine
    val id = if (!slotId.isNullOrBlank()) slotId else "legacy"

    val factory = getContextEngineFactory(id)
        ?: error("Context engine '$id' is not registered. Registered: ${listContextEngineIds()}")

    return factory.invoke()
}

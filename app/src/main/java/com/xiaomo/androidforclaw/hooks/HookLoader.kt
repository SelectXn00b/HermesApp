package com.xiaomo.androidforclaw.hooks

/**
 * OpenClaw Source Reference:
 * - src/hooks/loader.ts
 *
 * Dynamic loader for hook handlers.
 * Loads hooks from directory-based discovery and registers them
 * with the internal hook system.
 *
 * Android adaptation: No dynamic ESM imports. Instead, hooks register
 * Kotlin handler functions directly. The loader discovers hooks via
 * filesystem scanning and registers any bundled/managed handlers.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import com.xiaomo.androidforclaw.logging.Log

private const val TAG = "hooks:loader"

/**
 * Load and register all hook handlers.
 * Aligned with OpenClaw loadInternalHooks.
 *
 * On Android, since we can't dynamically import JS/TS modules,
 * this function:
 * 1. Discovers hooks via filesystem scanning
 * 2. Filters by eligibility
 * 3. Registers bundled handlers via the BundledHookRegistry
 * 4. Returns the number of handlers successfully loaded
 */
suspend fun loadInternalHooks(
    cfg: OpenClawConfig,
    workspaceDir: String,
    managedHooksDir: String? = null,
    bundledHooksDir: String? = null
): Int {
    // Hooks are on by default; only skip when explicitly disabled
    // (OpenClaw: cfg.hooks?.internal?.enabled === false)
    // On Android, we don't have internal config yet, so hooks are always on.

    var loadedCount = 0

    try {
        val hookEntries = loadWorkspaceHookEntries(
            workspaceDir = workspaceDir,
            config = cfg,
            managedHooksDir = managedHooksDir,
            bundledHooksDir = bundledHooksDir
        )

        // Filter by eligibility
        val eligible = hookEntries.filter { entry ->
            shouldIncludeHook(entry, cfg)
        }

        for (entry in eligible) {
            try {
                val events = entry.metadata?.events ?: emptyList()
                if (events.isEmpty()) {
                    Log.w(TAG, "Hook '${entry.hook.name}' has no events defined in metadata")
                    continue
                }

                // Try to resolve a bundled handler for this hook
                val handler = BundledHookRegistry.getHandler(entry.hook.name)
                if (handler == null) {
                    Log.d(TAG, "No bundled handler for hook '${entry.hook.name}' (skipping dynamic load on Android)")
                    continue
                }

                // Register for all events listed in metadata
                for (event in events) {
                    InternalHooks.register(event, handler)
                }

                Log.d(
                    TAG,
                    "Registered hook: ${entry.hook.name} -> ${events.joinToString(", ")}"
                )
                loadedCount++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load hook ${entry.hook.name}: ${e.message}")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load directory-based hooks: ${e.message}")
    }

    return loadedCount
}

/**
 * Registry for bundled hook handlers that can be resolved by name.
 *
 * On Android, since dynamic JS module loading is not possible,
 * bundled hooks are registered programmatically at app startup.
 */
object BundledHookRegistry {

    private val handlers = mutableMapOf<String, InternalHookHandler>()

    /**
     * Register a bundled handler by hook name.
     */
    fun register(hookName: String, handler: InternalHookHandler) {
        handlers[hookName] = handler
    }

    /**
     * Get a registered handler by hook name, or null if not registered.
     */
    fun getHandler(hookName: String): InternalHookHandler? = handlers[hookName]

    /**
     * Clear all registered bundled handlers (testing).
     */
    fun clear() {
        handlers.clear()
    }
}

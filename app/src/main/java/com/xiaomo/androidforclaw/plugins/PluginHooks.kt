package com.xiaomo.androidforclaw.plugins

import com.xiaomo.androidforclaw.logging.Log

/**
 * OpenClaw module: plugins
 * Source: OpenClaw/src/plugins/hooks.ts
 *
 * Plugin hook runner: executes plugin lifecycle hooks with proper
 * error handling, priority ordering, and async support.
 */

// ---------------------------------------------------------------------------
// Hook runner types
// ---------------------------------------------------------------------------

typealias HookRunnerLogger = PluginLogger

enum class HookFailurePolicy { FAIL_OPEN, FAIL_CLOSED }

data class HookRunnerOptions(
    val logger: HookRunnerLogger? = null,
    val catchErrors: Boolean = true,
    val failurePolicyByHook: Map<PluginHookName, HookFailurePolicy> = emptyMap(),
)

// ---------------------------------------------------------------------------
// Hook event types  (aligned with TS PluginHook*Event types)
// ---------------------------------------------------------------------------

open class PluginHookEvent(
    val hookName: PluginHookName,
    val data: Map<String, Any?> = emptyMap(),
)

open class PluginHookResult(
    val data: Map<String, Any?> = emptyMap(),
)

// ---------------------------------------------------------------------------
// Hook runner  (aligned with TS hook runner functions)
// ---------------------------------------------------------------------------

object PluginHookRunner {

    private const val TAG = "PluginHookRunner"

    /**
     * Get hooks for a specific hook name, sorted by priority (higher first).
     * Aligned with TS getHooksForName().
     */
    fun getHooksForName(
        registry: PluginRegistrySnapshot,
        hookName: PluginHookName,
    ): List<PluginHookRegistration> {
        return registry.hooks
            .filter { it.hookName == hookName }
            .sortedByDescending { it.priority }
    }

    /**
     * Get hooks for a specific hook name and plugin.
     * Aligned with TS getHooksForNameAndPlugin().
     */
    fun getHooksForNameAndPlugin(
        registry: PluginRegistrySnapshot,
        hookName: PluginHookName,
        pluginId: String,
    ): List<PluginHookRegistration> {
        return getHooksForName(registry, hookName)
            .filter { it.pluginId == pluginId }
    }

    /**
     * Run fire-and-forget hooks (no result aggregation).
     * Aligned with TS runFireAndForgetHooks().
     */
    suspend fun runFireAndForget(
        registry: PluginRegistrySnapshot,
        hookName: PluginHookName,
        event: PluginHookEvent,
        options: HookRunnerOptions = HookRunnerOptions(),
    ) {
        val hooks = getHooksForName(registry, hookName)
        val logger = options.logger ?: defaultLogger()

        for (hook in hooks) {
            try {
                logger.debug("hook ${hookName.value}: running (plugin=${hook.pluginId})")
                // In the full implementation, this would call the actual hook handler.
                // For now this is the framework that handlers can be registered into.
            } catch (e: Exception) {
                val failurePolicy = options.failurePolicyByHook[hookName]
                    ?: HookFailurePolicy.FAIL_OPEN
                if (failurePolicy == HookFailurePolicy.FAIL_CLOSED && !options.catchErrors) {
                    throw e
                }
                logger.error(
                    "hook ${hookName.value} failed (plugin=${hook.pluginId}): ${e.message}"
                )
            }
        }
    }

    /**
     * Run modifying hooks that accumulate a result.
     * Aligned with TS runModifyingHooks() pattern.
     */
    suspend fun <T> runModifying(
        registry: PluginRegistrySnapshot,
        hookName: PluginHookName,
        event: PluginHookEvent,
        initial: T,
        merge: (accumulated: T, next: T, pluginId: String) -> T,
        shouldStop: (result: T) -> Boolean = { false },
        handler: suspend (hook: PluginHookRegistration, event: PluginHookEvent) -> T?,
        options: HookRunnerOptions = HookRunnerOptions(),
    ): T {
        val hooks = getHooksForName(registry, hookName)
        val logger = options.logger ?: defaultLogger()
        var accumulated = initial

        for (hook in hooks) {
            try {
                val result = handler(hook, event) ?: continue
                accumulated = merge(accumulated, result, hook.pluginId)
                if (shouldStop(accumulated)) {
                    logger.debug("hook ${hookName.value}: stopped early (plugin=${hook.pluginId})")
                    break
                }
            } catch (e: Exception) {
                val failurePolicy = options.failurePolicyByHook[hookName]
                    ?: HookFailurePolicy.FAIL_OPEN
                if (failurePolicy == HookFailurePolicy.FAIL_CLOSED && !options.catchErrors) {
                    throw e
                }
                logger.error(
                    "hook ${hookName.value} failed (plugin=${hook.pluginId}): ${e.message}"
                )
            }
        }

        return accumulated
    }

    /**
     * Check if any hooks are registered for a given hook name.
     */
    fun hasHooks(registry: PluginRegistrySnapshot, hookName: PluginHookName): Boolean {
        return registry.hooks.any { it.hookName == hookName }
    }

    private fun defaultLogger(): PluginLogger = object : PluginLogger {
        override fun info(message: String) { Log.i(TAG, message) }
        override fun warn(message: String) { Log.w(TAG, message) }
        override fun error(message: String) { Log.e(TAG, message) }
        override fun debug(message: String) { Log.d(TAG, message) }
    }
}

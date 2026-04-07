package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/runtime-store.ts
 *
 * A tiny mutable runtime slot with strict access when the runtime has not been initialized.
 * Android adaptation: uses @Volatile for thread safety.
 */

/**
 * Plugin runtime store: mutable slot for a lazily-initialized runtime singleton.
 * Aligned with TS createPluginRuntimeStore.
 */
class PluginRuntimeStore<T>(private val errorMessage: String) {

    @Volatile
    private var runtime: T? = null

    fun setRuntime(next: T) {
        runtime = next
    }

    fun clearRuntime() {
        runtime = null
    }

    fun tryGetRuntime(): T? = runtime

    fun getRuntime(): T {
        return runtime ?: throw IllegalStateException(errorMessage)
    }
}

/**
 * Factory function matching the TS createPluginRuntimeStore pattern.
 */
fun <T> createPluginRuntimeStore(errorMessage: String): PluginRuntimeStore<T> =
    PluginRuntimeStore(errorMessage)

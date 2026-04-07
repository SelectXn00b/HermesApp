package com.xiaomo.androidforclaw.plugins

import com.xiaomo.androidforclaw.logging.Log

/**
 * OpenClaw module: plugins
 * Source: OpenClaw/src/plugins/services.ts
 *
 * Plugin service lifecycle: start/stop plugin-owned background services.
 */

// ---------------------------------------------------------------------------
// Service context (aligned with TS OpenClawPluginServiceContext)
// ---------------------------------------------------------------------------
data class PluginServiceContext(
    val config: Map<String, Any?> = emptyMap(),
    val workspaceDir: String? = null,
    val stateDir: String? = null,
    val logger: PluginLogger,
)

// ---------------------------------------------------------------------------
// Service interface (aligned with TS OpenClawPluginService)
// ---------------------------------------------------------------------------
interface PluginService {
    val id: String
    suspend fun start(context: PluginServiceContext)
    suspend fun stop(context: PluginServiceContext) {}
}

// ---------------------------------------------------------------------------
// Service handle (aligned with TS PluginServicesHandle)
// ---------------------------------------------------------------------------
class PluginServicesHandle(
    private val running: List<RunningService>,
    private val context: PluginServiceContext,
) {
    data class RunningService(
        val id: String,
        val service: PluginService,
    )

    suspend fun stop() {
        for (entry in running.reversed()) {
            try {
                entry.service.stop(context)
            } catch (err: Exception) {
                Log.w(TAG, "plugin service stop failed (${entry.id}): ${err.message}")
            }
        }
    }

    companion object {
        private const val TAG = "PluginServices"
    }
}

// ---------------------------------------------------------------------------
// Service runner (aligned with TS startPluginServices)
// ---------------------------------------------------------------------------
object PluginServiceRunner {

    private const val TAG = "PluginServices"
    private val registeredServices = mutableListOf<Pair<String, PluginService>>()

    fun registerService(pluginId: String, service: PluginService) {
        registeredServices.add(pluginId to service)
    }

    fun clearServices() {
        registeredServices.clear()
    }

    suspend fun startAll(
        config: Map<String, Any?> = emptyMap(),
        workspaceDir: String? = null,
        stateDir: String? = null,
    ): PluginServicesHandle {
        val logger: PluginLogger = object : PluginLogger {
            override fun info(message: String) { Log.i(TAG, message) }
            override fun warn(message: String) { Log.w(TAG, message) }
            override fun error(message: String) { Log.e(TAG, message) }
            override fun debug(message: String) { Log.d(TAG, message) }
        }
        val context = PluginServiceContext(
            config = config,
            workspaceDir = workspaceDir,
            stateDir = stateDir,
            logger = logger,
        )
        val running = mutableListOf<PluginServicesHandle.RunningService>()
        for ((pluginId, service) in registeredServices) {
            try {
                service.start(context)
                running.add(PluginServicesHandle.RunningService(service.id, service))
            } catch (err: Exception) {
                Log.e(
                    TAG,
                    "plugin service failed (${service.id}, plugin=$pluginId): ${err.message}"
                )
            }
        }
        return PluginServicesHandle(running, context)
    }
}

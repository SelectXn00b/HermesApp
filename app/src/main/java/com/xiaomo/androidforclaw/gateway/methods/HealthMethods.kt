/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: gateway server and RPC methods.
 */
package com.xiaomo.androidforclaw.gateway.methods

import android.os.Build
import com.xiaomo.androidforclaw.gateway.protocol.*

/**
 * Health RPC methods implementation
 */
class HealthMethods {
    private val startTime = System.currentTimeMillis()

    /**
     * health() - Basic health check
     */
    fun health(): HealthResult {
        return HealthResult(
            status = "ok",
            version = "1.0.0",
            uptime = System.currentTimeMillis() - startTime
        )
    }

    /**
     * status() - Detailed status
     */
    fun status(): StatusResult {
        val runtime = Runtime.getRuntime()
        return StatusResult(
            gateway = GatewayStatus(
                running = true,
                port = 8765,
                connections = 0,
                authenticated = false
            ),
            agent = AgentStatus(
                activeRuns = 0,
                toolsLoaded = 0
            ),
            sessions = SessionStatus(
                total = 0,
                active = 0
            ),
            system = SystemStatus(
                platform = "android",
                apiLevel = Build.VERSION.SDK_INT,
                memory = MemoryInfo(
                    total = runtime.maxMemory(),
                    available = runtime.freeMemory(),
                    used = runtime.totalMemory() - runtime.freeMemory()
                ),
                battery = BatteryInfo(
                    level = 0,
                    charging = false
                )
            )
        )
    }
}

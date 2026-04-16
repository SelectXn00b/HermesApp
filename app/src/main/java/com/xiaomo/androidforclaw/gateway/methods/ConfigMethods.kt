/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/server-runtime-config.ts
 */
package com.xiaomo.androidforclaw.gateway.methods

// ⚠️ DEPRECATED (2026-04-16): Part of old gateway, replaced by hermes GatewayRunner + AppChatAdapter.

import android.content.Context
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.workspace.StoragePaths
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

/**
 * Config RPC methods implementation
 *
 * Provides configuration management
 */
class ConfigMethods(
    private val context: Context
) {
    private val configLoader = ConfigLoader(context)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath = StoragePaths.openclawConfig.absolutePath

    /**
     * config.get() - Get current configuration
     */
    fun configGet(params: Any?): ConfigGetResult {
        @Suppress("UNCHECKED_CAST")
        val paramsMap = params as? Map<String, Any?>
        val path = paramsMap?.get("path") as? String

        return try {
            val config = configLoader.loadOpenClawConfig()
            val configMap = gson.fromJson(gson.toJson(config), Map::class.java)

            // If path is specified, return specific field
            val value = if (path != null) {
                getValueByPath(configMap, path)
            } else {
                configMap
            }

            ConfigGetResult(
                success = true,
                config = value,
                path = configPath
            )
        } catch (e: Exception) {
            ConfigGetResult(
                success = false,
                error = "Failed to get config: ${e.message}",
                path = configPath
            )
        }
    }

    /**
     * config.set() - Set configuration value
     */
    fun configSet(params: Any?): ConfigSetResult {
        @Suppress("UNCHECKED_CAST")
        val paramsMap = params as? Map<String, Any?>
            ?: return ConfigSetResult(false, "params must be an object")

        val path = paramsMap["path"] as? String
            ?: return ConfigSetResult(false, "path required")

        val value = paramsMap["value"]
            ?: return ConfigSetResult(false, "value required")

        return try {
            val configFile = File(configPath)
            if (!configFile.exists()) {
                return ConfigSetResult(false, "Config file not found")
            }

            val configText = configFile.readText()
            @Suppress("UNCHECKED_CAST")
            val config = gson.fromJson(configText, MutableMap::class.java) as MutableMap<String, Any?>

            // Set value
            setValueByPath(config, path, value)

            // Write back to file
            configFile.writeText(gson.toJson(config))

            ConfigSetResult(
                success = true,
                message = "Config updated: $path",
                path = configPath
            )
        } catch (e: Exception) {
            ConfigSetResult(
                success = false,
                message = "Failed to set config: ${e.message}"
            )
        }
    }

    /**
     * config.reload() - Reload configuration
     */
    fun configReload(): ConfigReloadResult {
        return try {
            configLoader.loadOpenClawConfig()
            ConfigReloadResult(
                success = true,
                message = "Config reloaded"
            )
        } catch (e: Exception) {
            ConfigReloadResult(
                success = false,
                message = "Failed to reload config: ${e.message}"
            )
        }
    }

    /**
     * Get value by path (supports dot notation, e.g. "agent.maxIterations")
     */
    @Suppress("UNCHECKED_CAST")
    private fun getValueByPath(config: Any?, path: String): Any? {
        if (config !is Map<*, *>) return null

        val parts = path.split(".")
        var current: Any? = config

        for (part in parts) {
            if (current is Map<*, *>) {
                current = current[part]
            } else {
                return null
            }
        }

        return current
    }

    /**
     * Set value by path
     */
    @Suppress("UNCHECKED_CAST")
    private fun setValueByPath(config: MutableMap<String, Any?>, path: String, value: Any?) {
        val parts = path.split(".")
        var current: MutableMap<String, Any?> = config

        for (i in 0 until parts.size - 1) {
            val part = parts[i]
            if (current[part] !is MutableMap<*, *>) {
                current[part] = mutableMapOf<String, Any?>()
            }
            current = current[part] as MutableMap<String, Any?>
        }

        current[parts.last()] = value
    }
}

/**
 * Config get result
 */
data class ConfigGetResult(
    val success: Boolean,
    val config: Any? = null,
    val error: String? = null,
    val path: String? = null
)

/**
 * Config set result
 */
data class ConfigSetResult(
    val success: Boolean,
    val message: String,
    val path: String? = null
)

/**
 * Config reload result
 */
data class ConfigReloadResult(
    val success: Boolean,
    val message: String
)

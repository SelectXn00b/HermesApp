package com.ai.assistance.operit.plugins.toolbox

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

data class ToolboxScriptHookParams(
    val context: Context
)

data class ToolboxScriptDefinition(
    val containerPackageName: String,
    val uiModuleId: String,
    val runtime: String,
    val title: String,
    val description: String
)

interface ToolboxScriptPlugin {
    val id: String

    suspend fun createDefinitions(
        params: ToolboxScriptHookParams
    ): List<ToolboxScriptDefinition>
}

object ToolboxScriptPluginRegistry {
    private val plugins = CopyOnWriteArrayList<ToolboxScriptPlugin>()

    @Synchronized
    fun register(plugin: ToolboxScriptPlugin) {
        unregister(plugin.id)
        plugins.add(plugin)
    }

    @Synchronized
    fun unregister(pluginId: String) {
        plugins.removeAll { it.id == pluginId }
    }

    suspend fun createDefinitions(
        params: ToolboxScriptHookParams
    ): List<ToolboxScriptDefinition> {
        return plugins
            .flatMap { plugin -> plugin.createDefinitions(params) }
            .distinctBy { "${it.containerPackageName}:${it.uiModuleId}:${it.runtime}" }
            .sortedWith(
                compareBy(
                    ToolboxScriptDefinition::title,
                    ToolboxScriptDefinition::containerPackageName,
                    ToolboxScriptDefinition::uiModuleId
                )
            )
    }
}

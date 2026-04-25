package com.ai.assistance.operit.ui.features.startup.screens

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope

val LocalPluginLoadingState = staticCompositionLocalOf<PluginLoadingState> {
    error("LocalPluginLoadingState not provided")
}

object PluginLoadingStateRegistry {
    @Volatile
    private var activeState: PluginLoadingState? = null

    @Volatile
    private var activeScope: CoroutineScope? = null

    fun bind(state: PluginLoadingState, scope: CoroutineScope) {
        activeState = state
        activeScope = scope
    }

    fun unbind(state: PluginLoadingState) {
        if (activeState === state) {
            activeState = null
            activeScope = null
        }
    }

    fun getState(): PluginLoadingState? = activeState

    fun getScope(): CoroutineScope? = activeScope
}

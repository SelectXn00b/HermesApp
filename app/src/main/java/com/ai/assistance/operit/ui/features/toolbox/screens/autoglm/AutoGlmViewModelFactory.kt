package com.ai.assistance.operit.ui.features.toolbox.screens.autoglm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AutoGlmViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AutoGlmViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AutoGlmViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

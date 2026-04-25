package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 日志查看器ViewModel - 使用AppLogger文件
 */
class LogcatViewModel(private val context: Context) : ViewModel() {
    private val logcatManager = LogcatManager(context)


    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult.asStateFlow()



    fun clearLogs() {
        logcatManager.clearLogs()
    }

    fun saveLogsToFile() {
        if (_isSaving.value) return

        _isSaving.value = true
        _saveResult.value = null

        viewModelScope.launch {
            try {
                val result = LogcatExportHelper.exportLogs(context)
                _saveResult.value = result.message
                delay(3000)
                _saveResult.value = null
            } catch (e: Exception) {
                _saveResult.value = context.getString(
                    R.string.logcat_save_failed,
                    e.message ?: context.getString(R.string.logcat_unknown_error)
                )
                delay(3000)
                _saveResult.value = null
            } finally {
                _isSaving.value = false
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LogcatViewModel::class.java)) {
                return LogcatViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    override fun onCleared() {
        super.onCleared()
        // No-op, no more monitoring to stop
    }
}

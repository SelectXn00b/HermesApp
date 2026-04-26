package com.ai.assistance.operit.ui.features.feedback

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.api.FeedbackApiService
import com.ai.assistance.operit.data.model.feedback.DeviceInfo
import com.ai.assistance.operit.data.model.feedback.FeedbackRequest
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeedbackViewModel(application: Application) : AndroidViewModel(application) {

    private val feedbackApiService = FeedbackApiService()

    var feedbackContent by mutableStateOf("")
        private set

    var isSubmitting by mutableStateOf(false)
        private set

    var submitSuccess by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var logLineCount by mutableStateOf(0)
        private set

    private var capturedLogs: String = ""

    fun updateContent(newContent: String) {
        feedbackContent = newContent
    }

    fun captureLogs(lastNLines: Int = 200) {
        viewModelScope.launch {
            capturedLogs = withContext(Dispatchers.IO) {
                val logFile = AppLogger.getLogFile()
                if (logFile != null && logFile.exists()) {
                    try {
                        val allLines = logFile.readLines()
                        val tail = allLines.takeLast(lastNLines)
                        logLineCount = tail.size
                        tail.joinToString("\n")
                    } catch (e: Exception) {
                        logLineCount = 0
                        "Failed to capture logs: ${e.message}"
                    }
                } else {
                    logLineCount = 0
                    ""
                }
            }
        }
    }

    fun submitFeedback() {
        if (feedbackContent.isBlank()) {
            errorMessage = "请输入反馈内容"
            return
        }

        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null

            val deviceInfo = collectDeviceInfo()
            val request = FeedbackRequest(
                content = feedbackContent,
                logs = capturedLogs,
                deviceInfo = deviceInfo,
                timestamp = System.currentTimeMillis()
            )

            feedbackApiService.submitFeedback(request).fold(
                onSuccess = {
                    submitSuccess = true
                    isSubmitting = false
                },
                onFailure = { error ->
                    errorMessage = error.message ?: "提交失败"
                    isSubmitting = false
                }
            )
        }
    }

    fun resetState() {
        feedbackContent = ""
        capturedLogs = ""
        logLineCount = 0
        submitSuccess = false
        errorMessage = null
    }

    private fun collectDeviceInfo(): DeviceInfo {
        val context = getApplication<Application>().applicationContext
        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
        return DeviceInfo(
            model = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            appVersion = packageInfo?.versionName ?: "unknown",
            appVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode ?: 0L
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode?.toLong() ?: 0L
            }
        )
    }
}

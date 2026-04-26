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
import com.ai.assistance.operit.data.model.feedback.ErrorContext
import com.ai.assistance.operit.data.model.feedback.FeedbackRequest
import com.ai.assistance.operit.util.FeedbackLogCollector
import kotlinx.coroutines.launch

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

    var hermesErrorLogCount by mutableStateOf(0)
        private set

    var hermesAgentLogCount by mutableStateOf(0)
        private set

    var hasPackageLogs by mutableStateOf(false)
        private set

    private var collectedLogs: FeedbackLogCollector.CollectedLogs? = null
    private var errorContext: ErrorContext? = null

    fun updateContent(newContent: String) {
        feedbackContent = newContent
    }

    fun setErrorContext(message: String?, source: String?) {
        if (!message.isNullOrBlank()) {
            errorContext = ErrorContext(
                errorMessage = message,
                errorSource = source ?: "manual",
                errorCategory = classifyError(message)
            )
            if (feedbackContent.isBlank()) {
                feedbackContent = message
            }
        }
    }

    fun captureAllLogs() {
        viewModelScope.launch {
            val logs = FeedbackLogCollector.collectAll()
            collectedLogs = logs
            logLineCount = logs.appLogLineCount
            hermesErrorLogCount = logs.hermesErrorLogLineCount
            hermesAgentLogCount = logs.hermesAgentLogLineCount
            hasPackageLogs = logs.hasPackageLogs
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
            val logs = collectedLogs
            val request = FeedbackRequest(
                content = feedbackContent,
                logs = logs?.appLogs ?: "",
                deviceInfo = deviceInfo,
                timestamp = System.currentTimeMillis(),
                errorContext = errorContext,
                hermesErrorLogs = logs?.hermesErrorLogs?.takeIf { it.isNotBlank() },
                hermesAgentLogs = logs?.hermesAgentLogs?.takeIf { it.isNotBlank() },
                packageLogs = logs?.packageLogs?.takeIf { it.isNotBlank() }
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
        collectedLogs = null
        errorContext = null
        logLineCount = 0
        hermesErrorLogCount = 0
        hermesAgentLogCount = 0
        hasPackageLogs = false
        submitSuccess = false
        errorMessage = null
    }

    private fun classifyError(message: String): String {
        return when {
            message.contains("timeout", ignoreCase = true) -> "timeout"
            message.contains("HTTP", ignoreCase = true) -> "api_error"
            message.contains("NullPointer") || message.contains("IllegalState") -> "runtime_crash"
            message.contains("tool", ignoreCase = true) || message.contains("package_proxy", ignoreCase = true) -> "tool_error"
            message.contains("SSL") || message.contains("Certificate") -> "ssl_error"
            else -> "unknown"
        }
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

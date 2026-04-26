package com.ai.assistance.operit.data.model.feedback

import kotlinx.serialization.Serializable

@Serializable
data class ErrorContext(
    val errorMessage: String = "",
    val errorSource: String = "",
    val errorCategory: String = ""
)

@Serializable
data class FeedbackRequest(
    val content: String,
    val logs: String,
    val deviceInfo: DeviceInfo,
    val timestamp: Long,
    val errorContext: ErrorContext? = null,
    val hermesErrorLogs: String? = null,
    val hermesAgentLogs: String? = null,
    val packageLogs: String? = null
)

@Serializable
data class DeviceInfo(
    val model: String,
    val osVersion: String,
    val sdkVersion: Int,
    val appVersion: String,
    val appVersionCode: Long
)

@Serializable
data class FeedbackResponse(
    val success: Boolean,
    val message: String? = null,
    val id: String? = null
)

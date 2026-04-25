package com.ai.assistance.operit.data.preferences

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object GitHubAuthBus {
    private val _authCode = MutableStateFlow<String?>(null)
    val authCode = _authCode.asStateFlow()

    fun postAuthCode(code: String?) {
        _authCode.value = code
    }
} 
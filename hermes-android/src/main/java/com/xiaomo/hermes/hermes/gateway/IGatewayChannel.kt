package com.xiaomo.hermes.hermes.gateway

interface IGatewayChannel {
    suspend fun request(method: String, paramsJson: String?, timeoutMs: Long = 15_000L): String

    suspend fun sendNodeEvent(event: String, payloadJson: String?): Boolean

    fun setEventListener(listener: ((event: String, payloadJson: String) -> Unit)?) {}
}

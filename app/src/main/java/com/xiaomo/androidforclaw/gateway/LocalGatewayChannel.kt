package com.xiaomo.androidforclaw.gateway

// ⚠️ DEPRECATED (2026-04-16): This file is deprecated by 方案A.
// Chat UI now connects directly to hermes GatewayRunner via AppChatAdapter.
// This file will be removed once migration is complete.

import com.xiaomo.base.IGatewayChannel

/**
 * 本地进程内 gateway channel，直接调用 GatewayController，无需 WebSocket。
 *
 * 用于 AndroidForClaw 自身（ChatController 和 GatewayController 在同一进程中）。
 * 远程 OpenClaw gateway 连接仍使用 GatewaySession（WebSocket 实现）。
 */
class LocalGatewayChannel(private val controller: GatewayController) : IGatewayChannel {

    /** NodeRuntime 注册的事件回调，GatewayController 广播时直接调用，不走 WebSocket。 */
    @Volatile
    private var eventListener: ((event: String, payloadJson: String) -> Unit)? = null

    override fun setEventListener(listener: ((event: String, payloadJson: String) -> Unit)?) {
        eventListener = listener
        controller.localEventSink = listener
    }

    override suspend fun request(method: String, paramsJson: String?, timeoutMs: Long): String {
        return controller.handleLocalRequest(method, paramsJson)
    }

    override suspend fun sendNodeEvent(event: String, payloadJson: String?): Boolean {
        // 本地模式下 chat.subscribe 等节点事件直接忽略（没有远程 gateway 订阅）
        return true
    }
}

package com.xiaomo.feishu

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel runtime.
 */


import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * 飞书 Webhook 连接处理器
 * 对齐 OpenClaw webhook 模式
 *
 * TODO: 需要集成到 Gateway HTTP 服务器
 */
class FeishuWebhookHandler(
    private val config: FeishuConfig,
    private val client: FeishuClient,
    private val eventFlow: MutableSharedFlow<FeishuEvent>
) : FeishuConnectionHandler {

    companion object {
        private const val TAG = "FeishuWebhook"
    }

    override fun start() {
        Log.i(TAG, "Webhook mode started")
        Log.i(TAG, "Webhook path: ${config.webhookPath}")
        Log.i(TAG, "Webhook port: ${config.webhookPort}")

        // TODO: 注册 webhook endpoint 到 Gateway HTTP 服务器
        // Gateway 会将飞书的 webhook 回调转发到这里
    }

    override fun stop() {
        Log.i(TAG, "Webhook mode stopped")
        // TODO: 注销 webhook endpoint
    }

    /**
     * 处理 Webhook 回调
     * 由 Gateway HTTP 服务器调用
     */
    suspend fun handleWebhookCallback(payload: String): String {
        try {
            // TODO: 解析并处理 webhook payload
            // TODO: 验证签名
            // TODO: 发送事件到 eventFlow
            return """{"code":0}"""

        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle webhook callback", e)
            return """{"code":1,"msg":"${e.message}"}"""
        }
    }
}

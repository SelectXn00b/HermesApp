/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu connection debug tests.
 */
package com.xiaomo.androidforclaw.test

import android.content.Context
import android.util.Log
import com.lark.oapi.event.EventDispatcher
import com.lark.oapi.service.im.ImService
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1
import com.xiaomo.androidforclaw.config.ConfigLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 飞书 WebSocket 直接测试
 * 用于验证官方 SDK 的 WebSocket 连接
 */
object FeishuWebSocketDirectTest {
    private const val TAG = "FeishuWSDirectTest"

    /**
     * 运行直接测试
     */
    suspend fun runDirectTest(context: Context): String = withContext(Dispatchers.IO) {
        val result = StringBuilder()

        try {
            result.appendLine("🚀 开始飞书 WebSocket 直接测试...")
            result.appendLine()

            // 1. 加载配置
            result.appendLine("📋 [1/3] 加载配置...")
            val configLoader = ConfigLoader(context)
            val feishuConfig = configLoader.getFeishuConfig()

            result.appendLine("  ✅ 配置加载成功")
            result.appendLine("     App ID: ${feishuConfig.appId}")
            result.appendLine("     Domain: ${feishuConfig.domain}")
            result.appendLine("     Mode: ${feishuConfig.connectionMode}")
            result.appendLine()

            // 2. 创建事件分发器
            result.appendLine("🔧 [2/3] 创建事件分发器...")
            val eventDispatcher = EventDispatcher.newBuilder(
                feishuConfig.verificationToken ?: "",
                feishuConfig.encryptKey ?: ""
            )
                .onP2MessageReceiveV1(object : ImService.P2MessageReceiveV1Handler() {
                    override fun handle(data: P2MessageReceiveV1?) {
                        Log.i(TAG, "📨 收到消息事件!")
                        data?.event?.message?.let { msg ->
                            Log.i(TAG, "  消息ID: ${msg.messageId}")
                            Log.i(TAG, "  内容: ${msg.content}")
                        }
                    }
                })
                .onP2MessageReadV1(object : ImService.P2MessageReadV1Handler() {
                    override fun handle(data: com.lark.oapi.service.im.v1.model.P2MessageReadV1?) {
                        Log.d(TAG, "已读回执")
                    }
                })
                .build()

            result.appendLine("  ✅ 事件分发器创建成功")
            result.appendLine()

            // 3. 创建并启动 WebSocket 客户端
            result.appendLine("🌐 [3/3] 创建 WebSocket 客户端...")
            val wsClient = com.lark.oapi.ws.Client.Builder(
                feishuConfig.appId,
                feishuConfig.appSecret
            )
                .eventHandler(eventDispatcher)
                .build()

            result.appendLine("  ✅ WebSocket 客户端创建成功")
            result.appendLine()
            result.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━")
            result.appendLine("🎯 准备启动 WebSocket...")
            result.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━")
            result.appendLine()
            result.appendLine("⚠️  注意：wsClient.start() 会阻塞当前线程")
            result.appendLine("   请查看 logcat 日志以确认连接状态")
            result.appendLine()

            Log.i(TAG, "准备启动 WebSocket...")
            Log.i(TAG, "App ID: ${feishuConfig.appId}")
            Log.i(TAG, "Domain: ${feishuConfig.domain}")

            // 在独立线程中启动（因为 start() 会阻塞）
            Thread {
                try {
                    Log.i(TAG, "🚀 调用 wsClient.start()...")
                    wsClient.start()
                    Log.i(TAG, "✅ wsClient.start() 完成")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ WebSocket 启动失败", e)
                }
            }.start()

            result.appendLine("✅ WebSocket 已在后台线程启动")
            result.appendLine("   请等待连接建立...")
            result.appendLine()
            result.appendLine("📝 查看日志命令:")
            result.appendLine("   adb logcat | grep FeishuWSDirectTest")
            result.appendLine()

            result.toString()

        } catch (e: Exception) {
            Log.e(TAG, "测试失败", e)
            result.appendLine()
            result.appendLine("❌ 测试失败: ${e.message}")
            result.appendLine()
            result.toString()
        }
    }
}

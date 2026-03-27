package com.xiaomo.feishu

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel runtime.
 */


import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lark.oapi.event.EventDispatcher
import com.lark.oapi.service.im.ImService
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * 飞书 WebSocket 连接处理器
 * 使用官方 oapi-sdk-java 的 com.lark.oapi.ws.Client 实现
 */
class FeishuWebSocketHandler(
    private val config: FeishuConfig,
    private val client: FeishuClient,
    private val eventFlow: MutableSharedFlow<FeishuEvent>
) : FeishuConnectionHandler {

    companion object {
        private const val TAG = "FeishuWebSocket"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private var wsClient: com.lark.oapi.ws.Client? = null

    override fun start() {
        scope.launch {
            try {
                Log.i(TAG, "🚀 启动 Feishu WebSocket 连接...")
                Log.i(TAG, "   App ID: ${config.appId}")
                Log.i(TAG, "   Domain: ${config.domain}")

                // 创建事件分发器
                val eventDispatcher = EventDispatcher.newBuilder(
                    config.verificationToken ?: "",
                    config.encryptKey ?: ""
                )
                    .onP2MessageReceiveV1(object : ImService.P2MessageReceiveV1Handler() {
                        override fun handle(data: P2MessageReceiveV1?) {
                            scope.launch {
                                try {
                                    if (data != null && data.event != null) {
                                        handleMessageReceive(data.event)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "处理消息事件失败", e)
                                }
                            }
                        }
                    })
                    .onP2MessageReadV1(object : ImService.P2MessageReadV1Handler() {
                        override fun handle(data: com.lark.oapi.service.im.v1.model.P2MessageReadV1?) {
                            // 忽略消息已读事件
                        }
                    })
                    .build()

                // 创建 WebSocket 客户端
                wsClient = com.lark.oapi.ws.Client.Builder(config.appId, config.appSecret)
                    .eventHandler(eventDispatcher)
                    .build()

                // 启动 WebSocket
                Log.i(TAG, "正在连接 WebSocket...")
                wsClient?.start()

                // 注意：start() 方法会阻塞主线程，直到连接关闭
                // 所以我们在协程中调用它
                Log.i(TAG, "✅ WebSocket 已启动")
                eventFlow.emit(FeishuEvent.Connected)

            } catch (e: Exception) {
                Log.e(TAG, "❌ 启动 WebSocket 失败", e)
                eventFlow.emit(FeishuEvent.Error(e))
            }
        }
    }

    override fun stop() {
        try {
            // 注意：ws.Client 的 disconnect() 和 reconnect() 方法是 protected 的
            // 我们只能通过其他方式停止（比如中断线程）
            wsClient = null
            Log.i(TAG, "WebSocket 已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止 WebSocket 时出错", e)
        }
    }

    /**
     * 处理接收消息事件
     */
    private suspend fun handleMessageReceive(event: com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data) {
        try {
            val sender = event.sender
            val message = event.message

            val messageId = message.messageId ?: return
            val senderId = sender.senderId?.openId ?: return
            val chatId = message.chatId ?: return
            val chatType = message.chatType ?: return
            val msgType = message.messageType ?: return
            val content = message.content ?: return

            // 使用 ContentParser 解析所有消息类型（对齐 OpenClaw bot-content.ts）
            val parseResult = com.xiaomo.feishu.messaging.FeishuContentParser.parseMessageContent(msgType, content)

            // 提取 thread/reply 字段（对齐 OpenClaw reply-dispatcher）
            val rootId = try { message.rootId } catch (e: Exception) { null }
            val parentId = try { message.parentId } catch (e: Exception) { null }
            val threadId = try { message.threadId } catch (e: Exception) { null }

            // 解析 mentions
            val mentions = message.mentions?.mapNotNull { mention ->
                mention.id?.openId
            } ?: emptyList()

            // Parse message create_time (aligned with OpenClaw: use original
            // message timestamp instead of processing time).
            // Feishu uses millisecond epoch string.
            val createTime = try {
                message.createTime?.toLongOrNull() ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }

            // 发送事件
            eventFlow.emit(
                FeishuEvent.Message(
                    messageId = messageId,
                    senderId = senderId,
                    chatId = chatId,
                    chatType = chatType,
                    content = parseResult.text,
                    msgType = msgType,
                    mentions = mentions,
                    rootId = rootId,
                    parentId = parentId,
                    threadId = threadId,
                    mediaKeys = parseResult.mediaKeys,
                    createTime = createTime
                )
            )

            Log.d(TAG, "📨 收到消息: $messageId from $senderId (type=$msgType)")
            Log.d(TAG, "   内容: ${parseResult.text.take(200)}")
            if (rootId != null) Log.d(TAG, "   rootId: $rootId, threadId: $threadId")

        } catch (e: Exception) {
            Log.e(TAG, "处理消息失败", e)
        }
    }
}

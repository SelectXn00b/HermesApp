package com.xiaomo.hermes.hermes.gateway

import android.content.Context
import com.xiaomo.hermes.hermes.gateway.platforms.BasePlatformAdapter
import com.xiaomo.hermes.hermes.gateway.platforms.MessageEvent
import com.xiaomo.hermes.hermes.gateway.platforms.MessageType
import com.xiaomo.hermes.hermes.gateway.platforms.SendResult
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.concurrent.CopyOnWriteArrayList

/**
 * End-to-end wiring test:
 *
 *   inbound MessageEvent → GatewayRunner._handleMessage → agentRunner callback
 *     → DeliveryRouter.deliverText → adapter.send
 *
 * Proves the fix for "飞书/微信 绑定成功 但是发消息都没响应": the Run.kt
 * agentRunner injection actually drives a reply back out to the platform
 * adapter. Runs as a plain JVM unit test using a mocked Context and a
 * fake adapter captured locally — no Robolectric, no network.
 */
class GatewayAgentWiringTest {

    /** Records outbound send() calls. */
    private data class SendCall(
        val chatId: String,
        val content: String,
        val replyTo: String?,
    )

    /** Fake adapter subclass: connect() is a no-op, send() records. */
    private class FakeAdapter(
        platform: Platform,
        config: PlatformConfig,
    ) : BasePlatformAdapter(config, platform) {
        val sent: CopyOnWriteArrayList<SendCall> = CopyOnWriteArrayList()

        override suspend fun connect(): Boolean {
            markConnected()
            return true
        }

        override suspend fun disconnect() {
            markDisconnected()
        }

        override suspend fun send(
            chatId: String,
            content: String,
            replyTo: String?,
            metadata: JSONObject?,
        ): SendResult {
            sent += SendCall(chatId, content, replyTo)
            return SendResult(success = true, messageId = "m-${sent.size}")
        }
    }

    /**
     * Build a minimal runner that reaches `_handleMessage` without spinning
     * up real platform adapters. Session store persist dir stays null
     * (hermesHome = ""), so no FS writes happen during the test.
     */
    private fun newRunner(): GatewayRunner {
        val ctx: Context = mock()
        val cfg = GatewayConfig(
            hermesHome = "",
            platforms = emptyMap(),
            maxConcurrentSessions = 4,
        )
        return GatewayRunner(ctx, cfg)
    }

    private fun platformConfig(platform: Platform) = PlatformConfig(
        platform = platform,
        enabled = true,
    )

    private fun inboundMessage(platform: Platform, text: String): MessageEvent {
        val source = SessionSource(
            platform = platform.value,
            chatId = "chat-123",
            chatName = "test-chat",
            chatType = "dm",
            userId = "user-42",
            userName = "tester",
        )
        return MessageEvent(
            text = text,
            messageType = MessageType.TEXT,
            source = source,
            message_id = "mid-1",
        )
    }

    /** Reflectively invoke the private `_handleMessage(event)` coroutine. */
    private fun dispatch(runner: GatewayRunner, event: MessageEvent) {
        // Kotlin compiles the private suspend fun with a trailing Continuation
        // parameter. Grabbing the declared method by name is enough — only
        // one overload exists.
        val method = runner.javaClass.declaredMethods.first { it.name == "_handleMessage" }
        method.isAccessible = true
        runBlocking {
            // runBlocking provides the outer coroutine; the reflected call
            // runs synchronously via kotlinx.coroutines' suspend-to-Java
            // interop (we drive it with runBlocking + a completable future).
            val completion = kotlinx.coroutines.CompletableDeferred<Any?>()
            val continuation = object : kotlin.coroutines.Continuation<Any?> {
                override val context: kotlin.coroutines.CoroutineContext
                    get() = kotlin.coroutines.EmptyCoroutineContext

                override fun resumeWith(result: Result<Any?>) {
                    if (result.isSuccess) completion.complete(result.getOrNull())
                    else completion.completeExceptionally(result.exceptionOrNull()!!)
                }
            }
            method.invoke(runner, event, continuation)
            completion.await()
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Feishu wiring
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `feishu inbound drives agentRunner and sends reply back through adapter`() {
        val runner = newRunner()
        val adapter = FakeAdapter(Platform.FEISHU, platformConfig(Platform.FEISHU))

        // Replace the real `_createAdapter` code path by manually registering.
        runner.deliveryRouter.register(adapter)
        adapter.messageHandler = { /* not used — we drive _handleMessage directly */ }

        val captured = mutableListOf<String>()
        runner.agentRunner = { text, sessionKey, platform, chatId, _ ->
            captured += "$platform:$chatId:$sessionKey:$text"
            "echo(feishu): $text"
        }

        dispatch(runner, inboundMessage(Platform.FEISHU, "你好 Hermes"))

        assertEquals(1, captured.size)
        assertTrue("agent saw platform+chat key: ${captured[0]}", captured[0].startsWith("feishu:chat-123:"))
        assertTrue("agent saw input text", captured[0].endsWith(":你好 Hermes"))

        // Outbound send arrived at the fake Feishu adapter with the agent reply.
        assertEquals(1, adapter.sent.size)
        val call = adapter.sent[0]
        assertEquals("chat-123", call.chatId)
        assertEquals("echo(feishu): 你好 Hermes", call.content)
        assertEquals("mid-1", call.replyTo)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Weixin wiring
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `weixin inbound drives agentRunner and sends reply back through adapter`() {
        val runner = newRunner()
        val adapter = FakeAdapter(Platform.WEIXIN, platformConfig(Platform.WEIXIN))
        runner.deliveryRouter.register(adapter)

        runner.agentRunner = { text, _, _, _, _ -> "微信收到: $text" }

        dispatch(runner, inboundMessage(Platform.WEIXIN, "ping"))

        assertEquals(1, adapter.sent.size)
        assertEquals("微信收到: ping", adapter.sent[0].content)
        assertEquals("chat-123", adapter.sent[0].chatId)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Missing runner → placeholder fallback
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `feishu inbound with unset agentRunner sends placeholder`() {
        val runner = newRunner()
        val adapter = FakeAdapter(Platform.FEISHU, platformConfig(Platform.FEISHU))
        runner.deliveryRouter.register(adapter)

        // No agentRunner set — expect placeholder.
        dispatch(runner, inboundMessage(Platform.FEISHU, "anything"))

        assertEquals(1, adapter.sent.size)
        assertEquals("Agent loop not configured", adapter.sent[0].content)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Agent exception → graceful error reply (no crash)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `feishu inbound with throwing agentRunner still delivers reply`() {
        val runner = newRunner()
        val adapter = FakeAdapter(Platform.FEISHU, platformConfig(Platform.FEISHU))
        runner.deliveryRouter.register(adapter)

        runner.agentRunner = { _, _, _, _, _ -> throw IllegalStateException("boom") }

        dispatch(runner, inboundMessage(Platform.FEISHU, "x"))

        assertEquals(1, adapter.sent.size)
        val content = adapter.sent[0].content
        assertNotNull(content)
        assertTrue("error surfaced to platform: $content", content.startsWith("Agent loop error:"))
        assertTrue("error message preserved: $content", content.contains("boom"))
    }
}

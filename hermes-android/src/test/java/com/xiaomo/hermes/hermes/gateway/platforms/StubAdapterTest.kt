package com.xiaomo.hermes.hermes.gateway.platforms

import android.content.Context
import com.xiaomo.hermes.hermes.gateway.Platform
import com.xiaomo.hermes.hermes.gateway.PlatformConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Covers the "not available on Android" stub adapters as a family.
 *
 * These adapters exist so the gateway factory (Run.kt) can always build a
 * platform instance, even when the underlying Python SDK has no Android
 * counterpart (aiohttp HTTP servers, matrix-nio, Mattermost driver,
 * BlueBubbles iMessage bridge, SMS/Email bindings).  All of them must:
 *
 *   - TC-GW-160-a  connect() → false deterministically
 *   - TC-GW-161-a  send(...)  → SendResult(success=false, error ≈ "... not supported on Android …")
 *   - TC-GW-162-a  disconnect() is idempotent, no exceptions
 *   - TC-GW-163-a  .platform matches the Kotlin Platform enum wired in Run.kt,
 *                  and .name equals the snake_case string used by Python
 *
 * HomeAssistantAdapter is *not* a pure stub (it talks HTTP REST), so it's
 * excluded here — its connect path is covered elsewhere.  WeComAdapter and
 * WecomCallbackAdapter have their own dedicated test classes.
 */
class StubAdapterTest {

    private fun mockContext(): Context = mock()

    /** All pure stubs — (adapter factory, expected Platform enum, expected name string). */
    private fun allStubs(): List<Triple<BasePlatformAdapter, Platform, String>> {
        val ctx = mockContext()
        return listOf(
            Triple(MatrixAdapter(ctx, PlatformConfig(Platform.MATRIX)), Platform.MATRIX, "matrix"),
            Triple(MattermostAdapter(ctx, PlatformConfig(Platform.MATTERMOST)), Platform.MATTERMOST, "mattermost"),
            Triple(BlueBubblesAdapter(ctx, PlatformConfig(Platform.BLUEBUBBLES)), Platform.BLUEBUBBLES, "bluebubbles"),
            Triple(SmsAdapter(ctx, PlatformConfig(Platform.SMS)), Platform.SMS, "sms"),
            Triple(WebhookAdapter(ctx, PlatformConfig(Platform.WEBHOOK)), Platform.WEBHOOK, "webhook"),
            Triple(EmailAdapter(ctx, PlatformConfig(Platform.EMAIL)), Platform.EMAIL, "email"),
            Triple(APIServerAdapter(ctx, PlatformConfig(Platform.API_SERVER)), Platform.API_SERVER, "api_server"),
        )
    }

    // ── TC-GW-160-a: all stubs return false from connect() ────────────────

    @Test
    fun `all stubs return false from connect`() = runBlocking {
        for ((adapter, platform, _) in allStubs()) {
            val ok = adapter.connect()
            assertFalse("connect() for $platform must be false on Android stub", ok)
            assertFalse("after failed connect, isConnected must be false for $platform", adapter.isConnected.get())
        }
    }

    // ── TC-GW-161-a: send returns deterministic error mentioning Android ──

    @Test
    fun `send on each stub returns unsuccessful SendResult mentioning Android`() = runBlocking {
        for ((adapter, platform, _) in allStubs()) {
            val result = adapter.send(chatId = "c1", content = "hi", replyTo = null, metadata = null)
            assertFalse("send success for $platform must be false", result.success)
            assertNotNull("error string for $platform must be present", result.error)
            assertTrue(
                "stub error for $platform must mention Android, got: ${result.error}",
                result.error!!.contains("Android", ignoreCase = true)
            )
        }
    }

    // ── TC-GW-162-a: disconnect is idempotent and does not throw ──────────

    @Test
    fun `disconnect on each stub is idempotent`() = runBlocking {
        for ((adapter, platform, _) in allStubs()) {
            // First disconnect from a never-connected state
            adapter.disconnect()
            assertFalse(
                "isConnected must remain false after first disconnect for $platform",
                adapter.isConnected.get()
            )
            // Second disconnect must not throw
            adapter.disconnect()
            assertFalse(
                "isConnected must remain false after second disconnect for $platform",
                adapter.isConnected.get()
            )
        }
    }

    // ── TC-GW-163-a: .platform and .name match Python enum ────────────────

    @Test
    fun `each stub's platform enum and name string match Python wire value`() {
        for ((adapter, expectedPlatform, expectedName) in allStubs()) {
            assertEquals(
                "platform mismatch for $expectedName",
                expectedPlatform,
                adapter.platform
            )
            assertEquals(
                "name mismatch for $expectedPlatform",
                expectedName,
                adapter.name
            )
        }
    }
}

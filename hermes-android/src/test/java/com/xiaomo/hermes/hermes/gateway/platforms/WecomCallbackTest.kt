package com.xiaomo.hermes.hermes.gateway.platforms

import android.content.Context
import com.xiaomo.hermes.hermes.gateway.Platform
import com.xiaomo.hermes.hermes.gateway.PlatformConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Covers the Android-side WecomCallbackAdapter (wecom_callback.py port).
 *
 * The Python counterpart spins up an HTTP server to receive signed callback
 * payloads from WeCom. On Android aiohttp isn't available, so the adapter is
 * a stub: connect() returns false, send() returns a "not supported" error.
 *
 * Pins TC-GW-124-a: "all entry points on Android deny".
 */
class WecomCallbackTest {

    private fun newAdapter(): WecomCallbackAdapter {
        val ctx: Context = mock()
        val config = PlatformConfig(platform = Platform.WECOM_CALLBACK, enabled = true)
        return WecomCallbackAdapter(ctx, config)
    }

    @Test
    fun `connect returns false on Android (no HTTP server)`() {
        val adapter = newAdapter()
        val ok = runBlocking { adapter.connect() }
        assertFalse("WecomCallback has no Android HTTP server — connect must be false", ok)
    }

    @Test
    fun `send returns SendResult with not-supported error`() {
        val adapter = newAdapter()
        val result = runBlocking { adapter.send(chatId = "abc", content = "hi", replyTo = null, metadata = null) }
        assertFalse(result.success)
        assertNotNull(result.error)
        assertTrue(
            "error must advise webhook mode, got: ${result.error}",
            result.error!!.contains("webhook mode")
        )
    }

    @Test
    fun `platform identifier matches WECOM_CALLBACK enum value`() {
        val adapter = newAdapter()
        assertEquals(Platform.WECOM_CALLBACK, adapter.platform)
        assertEquals("wecom_callback", adapter.name)
    }

    @Test
    fun `disconnect is idempotent and leaves adapter disconnected`() {
        val adapter = newAdapter()
        runBlocking { adapter.disconnect() }
        runBlocking { adapter.disconnect() }  // must not throw
        assertFalse(adapter.isConnected.get())
    }

    /**
     * TC-GW-124-a: aggregator check — all public entry points on the Android
     * stub adapter return a deny/no-op response. This is the "all entry points
     * denied on Android" contract from the requirements doc, pinned in a
     * single test so a future partial-implementation that forgets one path
     * fails loudly.
     */
    @Test
    fun `android denies all entry points`() {
        val adapter = newAdapter()
        val ok = runBlocking { adapter.connect() }
        assertFalse("connect must deny on Android (no aiohttp server)", ok)

        val result = runBlocking {
            adapter.send(chatId = "x", content = "hi", replyTo = null, metadata = null)
        }
        assertFalse("send must deny on Android", result.success)
        assertNotNull("send must return non-null error", result.error)

        // disconnect is a no-op but must remain callable without throwing.
        runBlocking { adapter.disconnect() }
        assertFalse(adapter.isConnected.get())
    }
}

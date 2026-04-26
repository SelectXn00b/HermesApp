package com.ai.assistance.operit.hermes.gateway

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Tests for [HermesGatewayController] — pure-logic subset.
 *
 * Covers:
 *  - TC-CONFIG-022 (start idempotent RUNNING — pure state-flag check)
 *  - TC-CONFIG-024 (stripInternalMarkup — pure regex replace)
 *  - TC-CONFIG-025 (gatewayChatTitle — pure string op)
 *
 * Deferred (need Robolectric / Android runtime for DataStore,
 * EncryptedSharedPreferences, HermesAdapter, or ChatHistoryManager):
 *  - TC-CONFIG-020 (Status FSM happy path — needs real Config + GatewayRunner)
 *  - TC-CONFIG-021 (empty platforms fails — needs build(context) which
 *    reaches getHermesHome → getAppContext().filesDir)
 *  - TC-CONFIG-023 (stop exception still STOPPED — needs a started runner)
 *  - TC-CONFIG-026 (empty reply fallback — needs HermesAdapter)
 *  - TC-CONFIG-027 (persist swallow — needs ChatHistoryManager)
 */
class HermesGatewayControllerTest {

    // ── R-CONFIG-022 / TC-CONFIG-022-a: start() short-circuits when RUNNING ──
    @Test
    fun `start idempotent RUNNING — returns true without touching prefs`() = runBlocking {
        val ctl = newController()
        setStatus(ctl, HermesGatewayController.Status.RUNNING)

        val result = ctl.start()

        assertTrue("start() while RUNNING must return true", result)
        assertEquals(HermesGatewayController.Status.RUNNING, ctl.status.value)
    }

    @Test
    fun `start idempotent STARTING — returns true without touching prefs`() = runBlocking {
        val ctl = newController()
        setStatus(ctl, HermesGatewayController.Status.STARTING)

        val result = ctl.start()

        assertTrue("start() while STARTING must return true", result)
        assertEquals(HermesGatewayController.Status.STARTING, ctl.status.value)
    }

    // ── R-CONFIG-024 / TC-CONFIG-024-a: stripInternalMarkup removes xml markers ──
    @Test
    fun `stripInternalMarkup removes xml — think tag case`() {
        val ctl = newController()
        val input = "hello<think>secret</think>world"
        val out = invokeStripInternalMarkup(ctl, input)
        assertEquals("helloworld", out)
    }

    @Test
    fun `stripInternalMarkup removes xml — tool tag with attrs`() {
        val ctl = newController()
        val input = "a<tool name=\"x\">payload</tool>b"
        val out = invokeStripInternalMarkup(ctl, input)
        assertEquals("ab", out)
    }

    @Test
    fun `stripInternalMarkup removes xml — tool_result tag`() {
        val ctl = newController()
        val input = "pre<tool_result id=\"1\">output text</tool_result>post"
        val out = invokeStripInternalMarkup(ctl, input)
        assertEquals("prepost", out)
    }

    @Test
    fun `stripInternalMarkup removes xml — status tag`() {
        val ctl = newController()
        val input = "before<status code=\"200\">ok</status>after"
        val out = invokeStripInternalMarkup(ctl, input)
        assertEquals("beforeafter", out)
    }

    @Test
    fun `stripInternalMarkup removes xml — case-insensitive`() {
        val ctl = newController()
        val input = "x<Think>s</Think>y<TOOL_RESULT>t</TOOL_RESULT>z"
        val out = invokeStripInternalMarkup(ctl, input)
        assertEquals("xyz", out)
    }

    @Test
    fun `stripInternalMarkup removes xml — multiple tags multiline`() {
        val ctl = newController()
        val input = "<think>\nline1\nline2\n</think>visible<tool x=\"y\">\nbody\n</tool>end"
        val out = invokeStripInternalMarkup(ctl, input)
        assertEquals("visibleend", out)
    }

    @Test
    fun `stripInternalMarkup preserves plain text`() {
        val ctl = newController()
        val input = "hello world — no markup here"
        val out = invokeStripInternalMarkup(ctl, input)
        assertEquals(input, out)
    }

    @Test
    fun `stripInternalMarkup empty string unchanged`() {
        val ctl = newController()
        assertEquals("", invokeStripInternalMarkup(ctl, ""))
    }

    // ── R-CONFIG-025 / TC-CONFIG-025-a: gatewayChatTitle truncation ──
    @Test
    fun `chat title truncation — long chatId truncates to 24 after @ stripped`() {
        val ctl = newController()
        // sessionKey "feishu:user_5" → platform "feishu"
        // chatId "oc_" + 100 a's + "@tenant" → substringBefore('@') = "oc_aaa...", take(24)
        val chatId = "oc_" + "a".repeat(100) + "@tenant_x"
        val title = invokeGatewayChatTitle(ctl, "feishu:user_5", chatId)
        // substringBefore('@') on the full chatId yields "oc_" + 100 a's (103 chars),
        // then .take(24) trims to "oc_" + 21 a's (24 chars total).
        val expectedShort = ("oc_" + "a".repeat(100)).take(24)
        assertEquals("feishu: $expectedShort", title)
        // Belt-and-suspenders: the shortened chat id itself must be 24 chars.
        assertEquals(24, expectedShort.length)
    }

    @Test
    fun `chat title truncation — short chatId passes through`() {
        val ctl = newController()
        val title = invokeGatewayChatTitle(ctl, "feishu:u1", "oc_short")
        assertEquals("feishu: oc_short", title)
    }

    @Test
    fun `chat title truncation — empty after @ falls back to full take(24)`() {
        val ctl = newController()
        // chatId without '@' — substringBefore returns the whole string.
        val chatId = "b".repeat(40)
        val title = invokeGatewayChatTitle(ctl, "weixin:g_1", chatId)
        assertEquals("weixin: " + "b".repeat(24), title)
    }

    @Test
    fun `chat title truncation — sessionKey without colon used as platform`() {
        val ctl = newController()
        val title = invokeGatewayChatTitle(ctl, "feishu", "oc_abc")
        // substringBefore(':') on "feishu" returns "feishu" (no ':' in source),
        // which is non-empty — so the branch uses it directly, not the
        // fall-through.
        assertEquals("feishu: oc_abc", title)
    }

    // ────────────────────────── helpers ──────────────────────────

    /**
     * Build a controller via the private constructor using a Mockito
     * Context. We never reach into [HermesGatewayConfigBuilder.build], so
     * the mock stays untouched by the pure-logic paths under test.
     */
    private fun newController(): HermesGatewayController {
        val ctx = mock(Context::class.java)
        val ctor = HermesGatewayController::class.java.getDeclaredConstructor(Context::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(ctx)
    }

    /** Forcefully flip [_status] to simulate a state reached by prior ops. */
    @Suppress("UNCHECKED_CAST")
    private fun setStatus(ctl: HermesGatewayController, status: HermesGatewayController.Status) {
        val field = HermesGatewayController::class.java.getDeclaredField("_status")
        field.isAccessible = true
        val flow = field.get(ctl) as MutableStateFlow<HermesGatewayController.Status>
        flow.value = status
    }

    private fun invokeStripInternalMarkup(ctl: HermesGatewayController, input: String): String {
        val m = HermesGatewayController::class.java.getDeclaredMethod(
            "stripInternalMarkup", String::class.java)
        m.isAccessible = true
        return m.invoke(ctl, input) as String
    }

    private fun invokeGatewayChatTitle(
        ctl: HermesGatewayController,
        sessionKey: String,
        chatId: String,
    ): String {
        val m = HermesGatewayController::class.java.getDeclaredMethod(
            "gatewayChatTitle", String::class.java, String::class.java)
        m.isAccessible = true
        return m.invoke(ctl, sessionKey, chatId) as String
    }
}

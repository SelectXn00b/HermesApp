package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the pure-logic subset of Feishu QR onboarding helpers ported from
 * gateway/platforms/feishu.py:
 *   _accountsBaseUrl / _onboardOpenBaseUrl / _parseBotResponse / _renderQr
 *   plus the FEISHU_AVAILABLE / constant invariants.
 *
 * The HTTP-driven helpers (_postRegistration, _initRegistration, _probeBotHttp,
 * qrRegister etc.) need a MockWebServer and are covered in the integration
 * suite — this file only verifies the pure functions.
 */
class FeishuQrOnboardingTest {

    @Test
    fun `accountsBaseUrl known domains`() {
        assertEquals("https://accounts.feishu.cn", _accountsBaseUrl("feishu"))
        assertEquals("https://accounts.larksuite.com", _accountsBaseUrl("lark"))
    }

    @Test
    fun `accountsBaseUrl unknown domain falls back to feishu`() {
        assertEquals("https://accounts.feishu.cn", _accountsBaseUrl("unknown"))
        assertEquals("https://accounts.feishu.cn", _accountsBaseUrl(""))
    }

    @Test
    fun `onboardOpenBaseUrl known domains`() {
        assertEquals("https://open.feishu.cn", _onboardOpenBaseUrl("feishu"))
        assertEquals("https://open.larksuite.com", _onboardOpenBaseUrl("lark"))
    }

    @Test
    fun `onboardOpenBaseUrl unknown domain falls back to feishu`() {
        assertEquals("https://open.feishu.cn", _onboardOpenBaseUrl("unknown"))
        assertEquals("https://open.feishu.cn", _onboardOpenBaseUrl(""))
    }

    @Test
    fun `onboarding constants match protocol defaults`() {
        assertEquals("/oauth/v1/app/registration", _REGISTRATION_PATH)
        assertEquals(10, _ONBOARD_REQUEST_TIMEOUT_S)
    }

    @Test
    fun `onboarding URL tables use https`() {
        _ONBOARD_ACCOUNTS_URLS.values.forEach { assertTrue(it, it.startsWith("https://")) }
        _ONBOARD_OPEN_URLS.values.forEach { assertTrue(it, it.startsWith("https://")) }
    }

    @Test
    fun `FEISHU_AVAILABLE is false on Android`() {
        // SDK pulls javax.servlet-api which cannot be dex'd. This invariant
        // gates the probeBot / qrRegister control flow.
        assertFalse(FEISHU_AVAILABLE)
    }

    @Test
    fun `parseBotResponse returns null on non-zero code`() {
        val res = _parseBotResponse(mapOf("code" to 42, "bot" to mapOf("bot_name" to "x")))
        assertNull(res)
    }

    @Test
    fun `parseBotResponse returns null when code missing`() {
        // code missing treated as !=0 since Number cast is null
        val res = _parseBotResponse(mapOf("bot" to mapOf("bot_name" to "x")))
        assertNull(res)
    }

    @Test
    fun `parseBotResponse extracts bot from top-level`() {
        val res = _parseBotResponse(
            mapOf(
                "code" to 0,
                "bot" to mapOf<String, Any?>(
                    "bot_name" to "Hermes",
                    "open_id" to "ou_123",
                ),
            ),
        )
        assertNotNull(res)
        assertEquals("Hermes", res!!["bot_name"])
        assertEquals("ou_123", res["bot_open_id"])
    }

    @Test
    fun `parseBotResponse falls back to data_bot`() {
        val res = _parseBotResponse(
            mapOf(
                "code" to 0,
                "data" to mapOf(
                    "bot" to mapOf<String, Any?>(
                        "bot_name" to "Nested",
                        "open_id" to "ou_nested",
                    ),
                ),
            ),
        )
        assertNotNull(res)
        assertEquals("Nested", res!!["bot_name"])
        assertEquals("ou_nested", res["bot_open_id"])
    }

    @Test
    fun `parseBotResponse returns null values when bot missing`() {
        val res = _parseBotResponse(mapOf("code" to 0))
        assertNotNull(res)
        assertNull(res!!["bot_name"])
        assertNull(res["bot_open_id"])
    }

    @Test
    fun `parseBotResponse prefers top-level bot over data bot`() {
        val res = _parseBotResponse(
            mapOf(
                "code" to 0,
                "bot" to mapOf<String, Any?>("bot_name" to "Top", "open_id" to "ou_top"),
                "data" to mapOf(
                    "bot" to mapOf<String, Any?>("bot_name" to "Deep", "open_id" to "ou_deep"),
                ),
            ),
        )
        assertNotNull(res)
        assertEquals("Top", res!!["bot_name"])
        assertEquals("ou_top", res["bot_open_id"])
    }

    @Test
    fun `renderQr returns false on Android`() {
        // Terminal-printed QR is not meaningful on Android; UI uses a Compose
        // bitmap widget instead. Always returning false preserves the Python
        // fallback path where caller displays URL as text.
        assertFalse(_renderQr("https://example.com/qr"))
    }

    @Test
    fun `buildOnboardClient always returns null on Android`() {
        assertNull(_buildOnboardClient("a", "b", "feishu"))
    }

    @Test
    fun `probeBotSdk always returns null on Android`() {
        assertNull(_probeBotSdk("a", "b", "feishu"))
    }

    @Test
    fun `runOfficialFeishuWsClient is a no-op on Android`() {
        // FEISHU_AVAILABLE=false so the Lark SDK WS loop is never reachable;
        // the symbol exists only for Python parity. Calling it must not throw.
        _runOfficialFeishuWsClient(null, null)
        _runOfficialFeishuWsClient("dummy", "dummy")
    }
}

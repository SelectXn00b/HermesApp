package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for Dingtalk.kt — pure-JVM source-level guards for the fixed
 * endpoint URLs (TC-GW-180-a / TC-GW-181-a). Python upstream uses the
 * official DingTalk SDK; Kotlin inlines the HTTP URLs directly so the
 * Python source has no literal to compare against — the Kotlin URLs are
 * locked to the official DingTalk OpenAPI paths.
 */
class DingtalkAdapterTest {

    private val src: String by lazy {
        File("src/main/java/com/xiaomo/hermes/hermes/gateway/platforms/Dingtalk.kt")
            .readText()
    }

    // ── R-GW-005 / TC-GW-180-a: token URL ──
    /**
     * TC-GW-180-a — access-token refresh must hit
     * `https://oapi.dingtalk.com/gettoken?appkey=…&appsecret=…` per
     * DingTalk OpenAPI v1.
     */
    @Test
    fun `token url`() {
        assertTrue(
            "must use official oapi.dingtalk.com/gettoken endpoint",
            src.contains("https://oapi.dingtalk.com/gettoken?appkey=\$_appKey&appsecret=\$_appSecret"))
        // Must cache token with a safety margin before expiry (≥ 300s).
        assertTrue(
            "must refresh before expiry",
            src.contains("data.getLong(\"expires_in\") - 300"))
        // Parse access_token from response.
        assertTrue(
            "must parse access_token from gettoken response",
            src.contains("_accessToken = data.getString(\"access_token\")"))
    }

    // ── R-GW-005 / TC-GW-181-a: send endpoint ──
    /**
     * TC-GW-181-a — text sends must POST to
     * `/topapi/message/corpconversation/asyncsend_v2?access_token=…`
     * (the corp-conversation async send v2 endpoint).
     */
    @Test
    fun `send endpoint`() {
        assertTrue(
            "must use /topapi/message/corpconversation/asyncsend_v2 endpoint",
            src.contains("https://oapi.dingtalk.com/topapi/message/corpconversation/asyncsend_v2?access_token=\$token"))
        // Payload shape: touser + msgtype=text + text.content
        assertTrue(
            "payload must wrap content under text.content",
            src.contains("put(\"msgtype\", \"text\")") &&
                src.contains("put(\"text\", JSONObject().apply { put(\"content\", content) })"))
    }

    // ── Guard: sensible HTTP timeouts (connect 15s / read 30s) ──
    @Test
    fun `http timeouts configured`() {
        assertTrue(
            "connect timeout 15s",
            src.contains(".connectTimeout(15, TimeUnit.SECONDS)"))
        assertTrue(
            "read timeout 30s",
            src.contains(".readTimeout(30, TimeUnit.SECONDS)"))
    }
}

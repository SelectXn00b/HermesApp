package com.ai.assistance.operit.hermes.gateway

import com.xiaomo.hermes.hermes.gateway.Platform
import com.xiaomo.hermes.hermes.gateway.PlatformConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.callSuspend
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.typeOf

/**
 * Tests for [HermesGatewayConfigBuilder] — pure-logic subset.
 *
 * Covers TC-CONFIG-010..015: incomplete creds skipped, null-platform filter,
 * extra non-null only, readCsv normalization, maxConcurrent default 5, policy
 * defaults.
 *
 * Deferred (need Robolectric / DataStore): TC-CONFIG-001..007 and anything
 * that exercises [HermesGatewayConfigBuilder.build] end-to-end, since that
 * path calls [getHermesHome] which dereferences the Android app context.
 *
 * Strategy: stub the private suspend helpers by mocking
 * [HermesGatewayPreferences] (mockito-inline, available via mockito-core 5.x),
 * then invoke the private `buildFeishu` / `buildWeixin` / `readCsv` /
 * `readPolicy` via `kotlin.reflect.full.callSuspend`.
 */
class HermesGatewayConfigBuilderTest {

    // ── R-CONFIG-010 / TC-CONFIG-010-a: Feishu skipped when app_secret missing ──
    @Test
    fun `incomplete creds skipped — Feishu missing appSecret returns null`() = runBlocking {
        val prefs = mock(HermesGatewayPreferences::class.java)
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_APP_ID))
            .thenReturn("cli_12345")
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_APP_SECRET))
            .thenReturn("")  // missing
        // irrelevant secrets — stub empty
        stubFeishuSecretsMissing(prefs)
        stubPolicyDefaults(prefs)

        val result = invokeBuildFeishu(prefs)
        assertNull("missing app_secret must return null", result)
    }

    @Test
    fun `incomplete creds skipped — Feishu missing appId returns null`() = runBlocking {
        val prefs = mock(HermesGatewayPreferences::class.java)
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_APP_ID))
            .thenReturn("")
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_APP_SECRET))
            .thenReturn("secret_xyz")
        stubFeishuSecretsMissing(prefs)
        stubPolicyDefaults(prefs)

        val result = invokeBuildFeishu(prefs)
        assertNull("missing app_id must return null", result)
    }

    // ── R-CONFIG-011 / TC-CONFIG-011-a: one platform configured, one not ──
    // Verifies buildFeishu returns a config while buildWeixin returns null
    // (i.e. the filter-null logic at call sites works).
    @Test
    fun `null platforms filtered — Feishu ok but Weixin missing`() = runBlocking {
        val prefs = mock(HermesGatewayPreferences::class.java)
        // Feishu: fully configured
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_APP_ID))
            .thenReturn("cli_12345")
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_APP_SECRET))
            .thenReturn("secret_xyz")
        stubFeishuSecretsMissing(prefs)
        stubPolicyDefaults(prefs)
        // Weixin: account_id missing
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_WEIXIN, HermesGatewayPreferences.SECRET_WEIXIN_ACCOUNT_ID))
            .thenReturn("")
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_WEIXIN, HermesGatewayPreferences.SECRET_WEIXIN_LOGIN_TOKEN))
            .thenReturn("")

        val feishu = invokeBuildFeishu(prefs)
        val weixin = invokeBuildWeixin(prefs)

        assertTrue("feishu should be present when creds set", feishu != null)
        assertEquals(Platform.FEISHU, feishu!!.platform)
        assertNull("weixin should be null when account_id missing", weixin)
    }

    // ── R-CONFIG-012 / TC-CONFIG-012-a: extra contains only non-empty fields ──
    @Test
    fun `extra non-null only — verification_token and encrypt_key excluded when empty`() = runBlocking {
        val prefs = mock(HermesGatewayPreferences::class.java)
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_APP_ID))
            .thenReturn("cli_aaa")
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_APP_SECRET))
            .thenReturn("secret_bbb")
        // verification_token + encrypt_key empty
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_VERIFICATION_TOKEN))
            .thenReturn("")
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_ENCRYPT_KEY))
            .thenReturn("")
        stubPolicyDefaults(prefs)

        val cfg = invokeBuildFeishu(prefs) ?: error("should build Feishu config")
        assertEquals("cli_aaa", cfg.extra["app_id"])
        assertEquals("secret_bbb", cfg.extra["app_secret"])
        assertFalse("empty verification_token must not enter extra", cfg.extra.containsKey("verification_token"))
        assertFalse("empty encrypt_key must not enter extra", cfg.extra.containsKey("encrypt_key"))
    }

    @Test
    fun `extra non-null only — included when set`() = runBlocking {
        val prefs = mock(HermesGatewayPreferences::class.java)
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_APP_ID))
            .thenReturn("cli_aaa")
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_APP_SECRET))
            .thenReturn("secret_bbb")
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_VERIFICATION_TOKEN))
            .thenReturn("vtok")
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_ENCRYPT_KEY))
            .thenReturn("ekey")
        stubPolicyDefaults(prefs)

        val cfg = invokeBuildFeishu(prefs) ?: error("should build")
        assertEquals("vtok", cfg.extra["verification_token"])
        assertEquals("ekey", cfg.extra["encrypt_key"])
    }

    // ── R-CONFIG-013 / TC-CONFIG-013-a: readCsv splits + trims + drops empty ──
    @Test
    fun `readCsv normalizes — mixed commas newlines and whitespace`() = runBlocking {
        val prefs = mock(HermesGatewayPreferences::class.java)
        // The raw DataStore value produced by the UI editor.
        whenever(prefs.platformPolicyFieldFlow("feishu", "dm_allow_from", ""))
            .thenReturn(flowOf(" u1 , u2\nu3 , "))

        @Suppress("UNCHECKED_CAST")
        val csv = invokeReadCsv(prefs, "feishu", "dm_allow_from") as List<String>
        assertEquals(listOf("u1", "u2", "u3"), csv)
    }

    @Test
    fun `readCsv normalizes — empty string yields empty list`() = runBlocking {
        val prefs = mock(HermesGatewayPreferences::class.java)
        whenever(prefs.platformPolicyFieldFlow("feishu", "dm_allow_from", ""))
            .thenReturn(flowOf(""))

        @Suppress("UNCHECKED_CAST")
        val csv = invokeReadCsv(prefs, "feishu", "dm_allow_from") as List<String>
        assertTrue("empty source must give empty list", csv.isEmpty())
    }

    // ── R-CONFIG-014 / TC-CONFIG-014-a: default maxConcurrentSessions == 5 ──
    @Test
    fun `maxConcurrent default 5 — verified by source-constant reflection`() {
        // The hard-coded value lives in build(context) which we can't exercise
        // without Android runtime. Assert directly via the source text so this
        // test fails the moment someone changes the literal.
        val src = java.io.File(
            "/Users/qiao/file/HermesApp/HermesApp/app/src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayConfigBuilder.kt"
        ).readText()
        assertTrue(
            "HermesGatewayConfigBuilder must hard-code maxConcurrentSessions = 5",
            src.contains("maxConcurrentSessions = 5"))
    }

    // ── R-CONFIG-015 / TC-CONFIG-015-a: policy defaults (dm=open, group=allowlist, reply=first) ──
    @Test
    fun `policy defaults — Feishu falls back when prefs empty`() = runBlocking {
        val prefs = mock(HermesGatewayPreferences::class.java)
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_APP_ID))
            .thenReturn("cli")
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_APP_SECRET))
            .thenReturn("sec")
        stubFeishuSecretsMissing(prefs)
        // platformPolicyFieldFlow returns the default passed in — Flow shape
        // lets the test prove that when DataStore is unset, the default arg
        // (`"open"` / `"allowlist"` / `"first"`) is what the Flow yields.
        // We simulate "unset" by echoing the default.
        whenever(prefs.platformPolicyFieldFlow(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenAnswer { inv -> flowOf(inv.arguments[2] as String) }

        val cfg = invokeBuildFeishu(prefs) ?: error("should build")
        assertEquals("open", cfg.dmPolicy)
        assertEquals("allowlist", cfg.groupPolicy)
        assertEquals("first", cfg.replyToMode)
    }

    @Test
    fun `policy defaults — Weixin falls back when prefs empty`() = runBlocking {
        val prefs = mock(HermesGatewayPreferences::class.java)
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_WEIXIN, HermesGatewayPreferences.SECRET_WEIXIN_ACCOUNT_ID))
            .thenReturn("acct_1")
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_WEIXIN, HermesGatewayPreferences.SECRET_WEIXIN_LOGIN_TOKEN))
            .thenReturn("")
        whenever(prefs.platformPolicyFieldFlow(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenAnswer { inv -> flowOf(inv.arguments[2] as String) }

        val cfg = invokeBuildWeixin(prefs) ?: error("should build")
        assertEquals("open", cfg.dmPolicy)
        assertEquals("allowlist", cfg.groupPolicy)
        assertEquals("first", cfg.replyToMode)
        assertFalse("empty login_token must not enter extra", cfg.extra.containsKey("login_token"))
    }

    // ────────────────────────── helpers ──────────────────────────

    /** Default stub: every non-core Feishu secret returns "". */
    private fun stubFeishuSecretsMissing(prefs: HermesGatewayPreferences) {
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_VERIFICATION_TOKEN))
            .thenReturn("")
        whenever(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_ENCRYPT_KEY))
            .thenReturn("")
    }

    /** Default stub: policy Flows echo the default argument. */
    private fun stubPolicyDefaults(prefs: HermesGatewayPreferences) {
        whenever(prefs.platformPolicyFieldFlow(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenAnswer { inv -> flowOf(inv.arguments[2] as String) }
    }

    private suspend fun invokeBuildFeishu(prefs: HermesGatewayPreferences): PlatformConfig? =
        callPrivateSuspend("buildFeishu", prefs) as PlatformConfig?

    private suspend fun invokeBuildWeixin(prefs: HermesGatewayPreferences): PlatformConfig? =
        callPrivateSuspend("buildWeixin", prefs) as PlatformConfig?

    private suspend fun invokeReadCsv(
        prefs: HermesGatewayPreferences,
        platform: String,
        field: String,
    ): Any? = callPrivateSuspend("readCsv", prefs, platform, field)

    /** Call a private suspend fn on the [HermesGatewayConfigBuilder] singleton. */
    private suspend fun callPrivateSuspend(name: String, vararg args: Any?): Any? {
        val obj = HermesGatewayConfigBuilder
        val fn = obj::class.declaredMemberFunctions.firstOrNull { it.name == name }
            ?: error("no private fn named $name on HermesGatewayConfigBuilder")
        fn.isAccessible = true
        return fn.callSuspend(obj, *args)
    }
}

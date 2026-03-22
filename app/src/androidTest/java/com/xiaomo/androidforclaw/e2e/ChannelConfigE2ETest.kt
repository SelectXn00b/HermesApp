package com.xiaomo.androidforclaw.e2e

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.core.MyApplication
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * E2E test for channel config persistence.
 * Verifies that Slack/Telegram/WhatsApp/Signal channel configs
 * are saved to and loaded from openclaw.json correctly.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ChannelConfigE2ETest {

    companion object {
        private const val PKG = "com.xiaomo.androidforclaw"
        private const val TIMEOUT = 5000L

        lateinit var device: UiDevice
        lateinit var context: MyApplication

        @BeforeClass
        @JvmStatic
        fun setupOnce() {
            device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            context = ApplicationProvider.getApplicationContext()
        }
    }

    @Test
    fun test01_slackConfigRoundTrip() {
        val configLoader = ConfigLoader(context)
        val original = configLoader.loadOpenClawConfig()

        // Save slack config
        val updated = original.copy(
            channels = original.channels.copy(
                slack = com.xiaomo.androidforclaw.config.SlackChannelConfig(
                    enabled = true,
                    botToken = "xoxb-test-token-12345",
                    dmPolicy = "pairing",
                    groupPolicy = "allowlist",
                    requireMention = false
                )
            )
        )
        configLoader.saveOpenClawConfig(updated)

        // Re-load and verify
        val reloaded = configLoader.loadOpenClawConfig()
        assertNotNull("slack config should exist", reloaded.channels.slack)
        val slack = reloaded.channels.slack!!
        assertTrue(slack.enabled)
        assertEquals("xoxb-test-token-12345", slack.botToken)
        assertEquals("pairing", slack.dmPolicy)
        assertEquals("allowlist", slack.groupPolicy)
        assertFalse(slack.requireMention)

        // Cleanup
        configLoader.saveOpenClawConfig(original)
    }

    @Test
    fun test02_telegramConfigRoundTrip() {
        val configLoader = ConfigLoader(context)
        val original = configLoader.loadOpenClawConfig()

        val updated = original.copy(
            channels = original.channels.copy(
                telegram = com.xiaomo.androidforclaw.config.TelegramChannelConfig(
                    enabled = true,
                    botToken = "bot123456:ABC-test",
                    dmPolicy = "open",
                    groupPolicy = "disabled",
                    requireMention = true
                )
            )
        )
        configLoader.saveOpenClawConfig(updated)

        val reloaded = configLoader.loadOpenClawConfig()
        assertNotNull(reloaded.channels.telegram)
        val tg = reloaded.channels.telegram!!
        assertTrue(tg.enabled)
        assertEquals("bot123456:ABC-test", tg.botToken)
        assertEquals("disabled", tg.groupPolicy)

        configLoader.saveOpenClawConfig(original)
    }

    @Test
    fun test03_whatsappConfigRoundTrip() {
        val configLoader = ConfigLoader(context)
        val original = configLoader.loadOpenClawConfig()

        val updated = original.copy(
            channels = original.channels.copy(
                whatsapp = com.xiaomo.androidforclaw.config.WhatsAppChannelConfig(
                    enabled = true,
                    phoneNumber = "+8613800138000",
                    dmPolicy = "open",
                    groupPolicy = "open",
                    requireMention = false
                )
            )
        )
        configLoader.saveOpenClawConfig(updated)

        val reloaded = configLoader.loadOpenClawConfig()
        assertNotNull(reloaded.channels.whatsapp)
        assertEquals("+8613800138000", reloaded.channels.whatsapp!!.phoneNumber)

        configLoader.saveOpenClawConfig(original)
    }

    @Test
    fun test04_signalConfigRoundTrip() {
        val configLoader = ConfigLoader(context)
        val original = configLoader.loadOpenClawConfig()

        val updated = original.copy(
            channels = original.channels.copy(
                signal = com.xiaomo.androidforclaw.config.SignalChannelConfig(
                    enabled = true,
                    phoneNumber = "+14155551234",
                    dmPolicy = "allowlist",
                    groupPolicy = "disabled",
                    requireMention = true
                )
            )
        )
        configLoader.saveOpenClawConfig(updated)

        val reloaded = configLoader.loadOpenClawConfig()
        assertNotNull(reloaded.channels.signal)
        val sig = reloaded.channels.signal!!
        assertTrue(sig.enabled)
        assertEquals("+14155551234", sig.phoneNumber)
        assertEquals("allowlist", sig.dmPolicy)

        configLoader.saveOpenClawConfig(original)
    }

    @Test
    fun test05_allChannelsCoexist() {
        val configLoader = ConfigLoader(context)
        val original = configLoader.loadOpenClawConfig()

        val updated = original.copy(
            channels = original.channels.copy(
                slack = com.xiaomo.androidforclaw.config.SlackChannelConfig(enabled = true, botToken = "s1"),
                telegram = com.xiaomo.androidforclaw.config.TelegramChannelConfig(enabled = true, botToken = "t1"),
                whatsapp = com.xiaomo.androidforclaw.config.WhatsAppChannelConfig(enabled = true, phoneNumber = "w1"),
                signal = com.xiaomo.androidforclaw.config.SignalChannelConfig(enabled = true, phoneNumber = "g1")
            )
        )
        configLoader.saveOpenClawConfig(updated)

        val reloaded = configLoader.loadOpenClawConfig()
        assertNotNull(reloaded.channels.slack)
        assertNotNull(reloaded.channels.telegram)
        assertNotNull(reloaded.channels.whatsapp)
        assertNotNull(reloaded.channels.signal)
        // Feishu and discord should still be intact
        assertNotNull(reloaded.channels.feishu)

        configLoader.saveOpenClawConfig(original)
    }
}

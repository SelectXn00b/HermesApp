package com.xiaomo.androidforclaw.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.xiaomo.androidforclaw.R
import ai.openclaw.app.R as OpenClawR
import com.xiaomo.androidforclaw.ui.activity.MainActivityCompose
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * ForClaw 主界面 UI 主线测试
 *
 * 关键路径:
 * 1. Settings tab 显示 ForClaw 卡片（LLM API / Gateway / Channels / Skills / Permissions）
 * 2. Settings tab 显示模型配置等设置项；模型配置可跳转
 *
 * 底部导航: Chat | Voice | Screen | Settings
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ForClawMainTabsUITest {

    companion object {
        private const val PKG     = "com.xiaomo.androidforclaw"
        private const val TIMEOUT = 8_000L
    }

    private lateinit var device: UiDevice
    private lateinit var scenario: ActivityScenario<MainActivityCompose>

    private val res get() = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Before
    fun setUp() {
        val instr = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instr)
        instr.uiAutomation.executeShellCommand(
            "appops set $PKG MANAGE_EXTERNAL_STORAGE allow"
        ).close()

        // Pre-accept legal consent
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("forclaw_legal", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("legal.accepted", true).apply()

        val intent = Intent(context, MainActivityCompose::class.java)
        scenario = ActivityScenario.launch(intent)
        Thread.sleep(2000)
        device.waitForIdle()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun findText(text: String, timeout: Long = TIMEOUT): UiObject {
        val obj = device.findObject(UiSelector().textContains(text))
        assertTrue("'$text' not found within ${timeout}ms", obj.waitForExists(timeout))
        return obj
    }

    private fun hasText(text: String, timeout: Long = 2_000L): Boolean =
        device.findObject(UiSelector().textContains(text)).waitForExists(timeout)

    private fun clickTab(label: String) {
        // Try content-desc first (Compose NavigationBarItem), then text
        val byDesc = device.findObject(UiSelector().description(label))
        if (byDesc.waitForExists(TIMEOUT)) {
            byDesc.click()
        } else {
            val byText = device.findObject(UiSelector().text(label))
            assertTrue("Tab '$label' not found within ${TIMEOUT}ms", byText.waitForExists(TIMEOUT))
            byText.click()
        }
        device.waitForIdle()
        Thread.sleep(500)
    }

    private fun scrollDown() {
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                     device.displayWidth / 2, device.displayHeight / 4, 15)
        device.waitForIdle()
    }

    private fun scrollUntilText(text: String, maxSwipes: Int = 5): Boolean {
        if (hasText(text, 1000)) return true
        repeat(maxSwipes) { scrollDown(); if (hasText(text, 1000)) return true }
        return false
    }

    // ── Tests ────────────────────────────────────────────────────────────

    /** Settings tab 显示 ForClaw 连接卡片 (LLM API / Local Gateway / Channels / Skills) */
    @Test
    fun test01_settingsTab_connectionCards() {
        val settingsLabel = res.getString(OpenClawR.string.tab_settings)
        clickTab(settingsLabel)

        // Verify connection status cards from ForClawSettingsTab
        val llmLabel = res.getString(R.string.connect_llm_api)
        val gatewayLabel = res.getString(R.string.connect_local_gateway)
        val channelsLabel = res.getString(R.string.connect_channels)
        val skillsLabel = res.getString(R.string.connect_skills)

        assertTrue("LLM API card should be visible", scrollUntilText(llmLabel))
        assertTrue("Local Gateway card should be visible", scrollUntilText(gatewayLabel))
        assertTrue("Channels card should be visible", scrollUntilText(channelsLabel))
        assertTrue("Skills card should be visible", scrollUntilText(skillsLabel))

        // Click "Modify" on LLM API card to open ModelConfigActivity
        val modifyLabel = res.getString(R.string.connect_modify_config)
        if (scrollUntilText(modifyLabel)) {
            findText(modifyLabel).click()
            Thread.sleep(1500)
            device.waitForIdle()
            assertEquals("Should stay in app", PKG, device.currentPackageName)
            device.pressBack()
            device.waitForIdle()
        }
    }

    /** Settings tab 显示配置项（Termux / openclaw.json / 检查更新）并可正常滚动 */
    @Test
    fun test02_settingsTab_configItems() {
        val settingsLabel = res.getString(OpenClawR.string.tab_settings)
        clickTab(settingsLabel)

        val termuxLabel = res.getString(R.string.settings_termux)
        val checkUpdateLabel = res.getString(R.string.settings_check_update)

        assertTrue("Channels should be visible", scrollUntilText(res.getString(R.string.connect_channels)))
        assertTrue("Termux Setup should be visible", scrollUntilText(termuxLabel))
        // openclaw.json is a file name, locale-independent
        assertTrue("openclaw.json should be visible", scrollUntilText("openclaw.json"))
        assertTrue("Check for Updates should be visible", scrollUntilText(checkUpdateLabel))
    }
}

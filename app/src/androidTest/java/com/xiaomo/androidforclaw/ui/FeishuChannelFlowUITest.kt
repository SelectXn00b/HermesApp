package com.xiaomo.androidforclaw.ui

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.test.FeishuConnectionTest
import kotlinx.coroutines.runBlocking
import com.xiaomo.androidforclaw.ui.activity.MainActivityCompose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 首页 -> 设置tab -> Channels -> Feishu 页面 UI 自动化测试
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class FeishuChannelFlowUITest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivityCompose::class.java)

    private lateinit var device: UiDevice

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.waitForIdle()
    }

    @Test
    fun test01_navigateFromHomeToFeishuChannelPage() {
        openSettingsTab()
        openChannelsPage()
        openFeishuPage()

        assertNotNull(device.wait(Until.findObject(By.text("Feishu Channel")), 3000))
        assertNotNull(device.findObject(By.text("启用 Feishu Channel")))
        assertNotNull(device.findObject(By.text("App ID")))
        assertNotNull(device.findObject(By.text("App Secret")))
    }

    @Test
    fun test02_feishuPageShowsExpectedSections() {
        openSettingsTab()
        openChannelsPage()
        openFeishuPage()

        assertNotNull(device.findObject(By.text("基础配置")))
        assertNotNull(device.findObject(By.text("私聊策略 (DM Policy)")))
        assertNotNull(device.findObject(By.text("群聊策略 (Group Policy)")))
        assertNotNull(device.findObject(By.text("保存")))
    }

    @Test
    fun test03_saveFeishuConfiguration_and_connectSuccessfully() {
        openSettingsTab()
        openChannelsPage()
        openFeishuPage()

        // 使用真实可连接的飞书配置
        val edits = device.findObjects(By.clazz("android.widget.EditText"))
        assertTrue("Expected at least 2 EditText fields", edits.size >= 2)
        edits[0].text = "cli_a410f5bdf3f8d062"
        edits[1].text = "P5BBBqI49VfYj5R4QZoD3g1wFEAQzLZZ"

        // 点击保存
        val saveBtn = device.findObject(By.text("保存"))
        assertNotNull(saveBtn)
        saveBtn!!.click()
        device.waitForIdle()

        // 验证配置落盘
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = ConfigLoader(context).loadOpenClawConfig()
        assertEquals("cli_a410f5bdf3f8d062", config.channels.feishu.appId)
        assertEquals("P5BBBqI49VfYj5R4QZoD3g1wFEAQzLZZ", config.channels.feishu.appSecret)
        assertTrue(config.channels.feishu.enabled)

        // 终点：飞书连接成功
        val result = runBlocking {
            FeishuConnectionTest(context).quickHealthCheck()
        }
        assertTrue("Expected Feishu connection success, got: ${result.message}", result.success)
    }

    private fun openSettingsTab() {
        val settingsTab = device.wait(Until.findObject(By.text("设置")), 5000)
        assertNotNull("Settings tab should exist", settingsTab)
        settingsTab!!.click()
        device.waitForIdle()
    }

    private fun openChannelsPage() {
        val channelsEntry = device.wait(Until.findObject(By.text("Channels")), 5000)
        assertNotNull("Channels entry should exist", channelsEntry)
        channelsEntry!!.click()
        device.wait(Until.findObject(By.text("Feishu (飞书)")), 5000)
    }

    private fun openFeishuPage() {
        val feishuCard = device.wait(Until.findObject(By.text("Feishu (飞书)")), 5000)
        assertNotNull("Feishu card should exist", feishuCard)
        feishuCard!!.click()
        device.wait(Until.findObject(By.text("Feishu Channel")), 5000)
    }
}

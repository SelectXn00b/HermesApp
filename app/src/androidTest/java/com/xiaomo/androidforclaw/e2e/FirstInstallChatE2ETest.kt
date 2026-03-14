package com.xiaomo.androidforclaw.e2e

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.tencent.mmkv.MMKV
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.ui.activity.MainActivityCompose
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 首次安装后的聊天 E2E 测试
 *
 * 成功标准：
 * 1. 走完首装引导
 * 2. 进入聊天页
 * 3. 发送固定测试消息
 * 4. 以聊天返回内容正确作为最终成功判断
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class FirstInstallChatE2ETest {

    companion object {
        private const val PACKAGE_NAME = "com.xiaomo.androidforclaw"
        private const val TIMEOUT = 15_000L
        private const val RESPONSE_TIMEOUT = 45_000L
        private const val TEST_PROMPT = "请只回复：ANDROIDFORCLAW_TEST_OK"
        private val ERROR_KEYWORDS = listOf(
            "Provider not found",
            "LLM request failed",
            "timed out",
            "API request failed",
            "执行出错",
            "错误类型"
        )
    }

    private lateinit var device: UiDevice
    private lateinit var context: Context

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = ApplicationProvider.getApplicationContext()
        resetFirstInstallState()
    }

    @Test
    fun firstInstall_startButton_sendMessage_replyContentIsSuccessCriterion() {
        launchMainActivity()

        completeFirstInstallIfShown(useSkip = false)
        waitForChatInput()
        sendMessage(TEST_PROMPT)

        val assistantReply = waitForAssistantReplyOrError()

        assertTrue(
            "Assistant reply should contain success marker. Actual: $assistantReply",
            assistantReply.contains("ANDROIDFORCLAW_TEST_OK", ignoreCase = true)
        )
    }

    @Test
    fun firstInstall_skipButton_sendMessage_replyContentIsSuccessCriterion() {
        launchMainActivity()

        completeFirstInstallIfShown(useSkip = true)
        waitForChatInput()
        sendMessage(TEST_PROMPT)

        val assistantReply = waitForAssistantReplyOrError()

        assertTrue(
            "Assistant reply should contain success marker after skip flow. Actual: $assistantReply",
            assistantReply.contains("ANDROIDFORCLAW_TEST_OK", ignoreCase = true)
        )
    }

    private fun resetFirstInstallState() {
        val mmkv = MMKV.defaultMMKV()
        mmkv.encode("model_setup_completed", false)

        val configFile = File("/sdcard/.androidforclaw/openclaw.json")
        if (configFile.exists()) {
            configFile.delete()
        }

        // Force create default config cache fresh on next access
        ConfigLoader(context).loadOpenClawConfig()
    }

    private fun launchMainActivity() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(PACKAGE_NAME, MainActivityCompose::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), TIMEOUT)
        device.waitForIdle()
    }

    private fun completeFirstInstallIfShown(useSkip: Boolean) {
        val welcome = device.wait(Until.findObject(By.text("欢迎使用 AndroidForClaw")), 5000)
        if (welcome != null) {
            val buttonText = if (useSkip) "跳过" else "开始使用"
            val button = device.wait(Until.findObject(By.text(buttonText)), 5000)
            assertNotNull("First-install $buttonText button should exist", button)
            button!!.click()
            device.waitForIdle()
        }
    }

    private fun waitForChatInput() {
        val input = device.wait(Until.findObject(By.desc("chat_input")), 3000)
            ?: device.wait(Until.findObject(By.clazz("android.widget.EditText")), TIMEOUT)
        assertNotNull("Chat input should be visible after first install", input)
    }

    private fun sendMessage(message: String) {
        val input = device.findObject(By.clazz("android.widget.EditText"))
        assertNotNull("Chat input EditText not found", input)
        input.text = message
        device.waitForIdle()

        val sendButton = device.findObject(By.desc("发送"))
            ?: device.findObject(By.clazz("android.widget.ImageView"))
            ?: device.findObject(By.clazz("android.view.View"))
        assertNotNull("Send button should exist", sendButton)
        sendButton!!.click()
        device.waitForIdle()
    }

    private fun waitForAssistantReplyOrError(): String {
        val start = System.currentTimeMillis()
        var lastText = ""

        while (System.currentTimeMillis() - start < RESPONSE_TIMEOUT) {
            val textViews = device.findObjects(By.clazz("android.widget.TextView"))
            val allText = textViews.mapNotNull { it.text }.filter { it.isNotBlank() }

            val joined = allText.joinToString("\n")
            lastText = joined

            val matchedError = ERROR_KEYWORDS.firstOrNull { joined.contains(it, ignoreCase = true) }
            if (matchedError != null) {
                throw AssertionError("Chat returned error keyword '$matchedError' instead of success. Full text:\n$joined")
            }

            val successLine = allText.lastOrNull { it.contains("ANDROIDFORCLAW_TEST_OK", ignoreCase = true) }
            if (successLine != null) {
                return successLine
            }

            Thread.sleep(1000)
        }

        throw AssertionError("Timed out waiting for assistant reply. Last visible text:\n$lastText")
    }
}

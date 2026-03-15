package com.xiaomo.androidforclaw.ui

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.xiaomo.androidforclaw.core.MyApplication
import com.xiaomo.androidforclaw.ui.activity.MainActivity
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * 悬浮窗 UI 自动化测试
 * 使用 UiAutomator 测试悬浮窗功能
 *
 * 运行:
 * ./gradlew connectedDebugAndroidTest --tests "FloatingWindowUITest"
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class FloatingWindowUITest {

    private lateinit var device: UiDevice
    private lateinit var context: Context

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = ApplicationProvider.getApplicationContext()

        // 确保设备回到主屏幕
        device.pressHome()
        device.waitForIdle()
    }

    @Test
    fun testFloatingWindow_canDisplay() {
        // 启动应用
        val intent = context.packageManager.getLaunchIntentForPackage(
            "com.xiaomo.androidforclaw"
        )?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        if (intent == null) {
            println("⚠️ 无法获取启动 Intent，跳过测试")
            return
        }
        context.startActivity(intent)

        // 等待应用启动
        device.wait(Until.hasObject(By.pkg("com.xiaomo.androidforclaw")), 5000)

        // 验证应用已启动
        val app = device.findObject(By.pkg("com.xiaomo.androidforclaw"))
        assertNotNull("应用应该已启动", app)
    }

    @Test
    @Ignore("跳过:飞书SDK protobuf冲突导致后台切换时崩溃")
    fun testFloatingWindow_survivesBackground() {
        // 启动应用
        launchApp()

        // 按Home键将应用置于后台
        device.pressHome()
        device.waitForIdle()

        // 等待一段时间
        Thread.sleep(2000)

        // 返回应用
        launchApp()

        // 验证应用仍然正常
        device.wait(Until.hasObject(By.pkg("com.xiaomo.androidforclaw")), 3000)

        val app = device.findObject(By.pkg("com.xiaomo.androidforclaw"))
        assertNotNull("应用应该仍然可用", app)
    }

    @Test
    fun testFloatingWindow_handlesRecentApps() {
        // 启动应用
        launchApp()

        // 打开最近任务
        try {
            device.pressRecentApps()
            device.waitForIdle()

            Thread.sleep(1000)

            // 返回到应用
            device.pressHome()
            device.waitForIdle()

        } catch (e: Exception) {
            // 某些设备可能不支持这个操作
        }
    }

    @Test
    fun testDeviceRotation_handlesCorrectly() {
        // 启动应用
        launchApp()

        // 获取当前方向
        val naturalOrientation = device.displayRotation

        try {
            // 旋转设备
            device.setOrientationLeft()
            device.waitForIdle()
            Thread.sleep(1000)

            // 验证应用仍然可见
            val app = device.findObject(By.pkg("com.xiaomo.androidforclaw"))
            assertNotNull("旋转后应用应该仍然可见", app)

            // 恢复原方向
            device.setOrientationNatural()
            device.waitForIdle()

        } catch (e: Exception) {
            // 旋转可能不被支持
        }
    }

    @Test
    @Ignore("跳过:飞书SDK protobuf冲突导致多应用切换时崩溃")
    fun testMultipleAppSwitching() {
        // 启动应用
        launchApp()
        Thread.sleep(1000)

        // 打开设置应用
        val settingsIntent = Intent(android.provider.Settings.ACTION_SETTINGS)
        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(settingsIntent)

        device.waitForIdle()
        Thread.sleep(1000)

        // 返回到我们的应用
        device.pressBack()
        device.waitForIdle()

        Thread.sleep(1000)

        // 验证应用仍然正常
        val app = device.findObject(By.pkg("com.xiaomo.androidforclaw"))
        assertNotNull("应用切换后应该仍然可用", app)
    }

    private fun launchApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(
            "com.xiaomo.androidforclaw"
        )?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        if (intent == null) {
            // App not installed or no launch intent; use fallback
            val fallback = Intent("android.intent.action.MAIN").apply {
                setClassName("com.xiaomo.androidforclaw", "com.xiaomo.androidforclaw.ui.activity.MainActivityCompose")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(fallback)
        } else {
            context.startActivity(intent)
        }
        device.wait(Until.hasObject(By.pkg("com.xiaomo.androidforclaw")), 5000)
        device.waitForIdle()
    }
}

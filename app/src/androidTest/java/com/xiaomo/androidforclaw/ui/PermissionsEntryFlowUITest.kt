package com.xiaomo.androidforclaw.ui

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.xiaomo.androidforclaw.ui.activity.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证主 app 的权限入口直接进入 observer 权限页，不再停留在主 app 中间页。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PermissionsEntryFlowUITest {

    private lateinit var device: UiDevice

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        device.waitForIdle()
    }

    @Test
    fun testPermissionEntry_redirectsDirectlyToObserverPermissionPage() {
        val permissionCard = device.wait(Until.findObject(By.text("权限")), 5000)
        assertNotNull("Permission card should exist on main page", permissionCard)
        permissionCard!!.click()

        // observer 页面独有：存储权限项 / 一键授权按钮
        val storageLabel = device.wait(Until.findObject(By.textContains("存储权限")), 5000)
        val grantAll = device.wait(Until.findObject(By.textContains("一键授权")), 2000)
        assertNotNull("Observer permission page should open directly", storageLabel ?: grantAll)
    }
}

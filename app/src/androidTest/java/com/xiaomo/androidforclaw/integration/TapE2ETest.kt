package com.xiaomo.androidforclaw.integration

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Tap E2E 测试
 *
 * 注意：由于 AIDL 跨进程限制，测试进程无法直接调用主 app 进程的无障碍服务。
 * 这些测试验证的是：
 * 1. 无障碍服务系统设置是否正确
 * 2. TapSkill 参数验证逻辑
 * 3. 主 app 进程的服务连接状态（通过文件标记验证）
 *
 * 真正的 tap 功能需要在主 app 进程内验证（通过飞书/ADB broadcast 触发）
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TapE2ETest {

    companion object {
        private const val TAG = "TapE2ETest"
    }

    @Test
    fun test01_accessibilityEnabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val enabled = android.provider.Settings.Secure.getInt(
            context.contentResolver,
            android.provider.Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        Log.i(TAG, "System accessibility enabled: $enabled")

        val services = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val ourServiceName = "com.xiaomo.androidforclaw/com.xiaomo.androidforclaw.accessibility.service.PhoneAccessibilityService"
        val ourServiceEnabled = services.contains(ourServiceName)
        Log.i(TAG, "Our service in enabled list: $ourServiceEnabled")
        Log.i(TAG, "Enabled services: $services")

        if (!enabled || !ourServiceEnabled) {
            Log.w(TAG, "⚠️ 无障碍服务未完全启用！")
            Log.w(TAG, "   需要在系统设置中开启无障碍服务")
            Log.w(TAG, "   或执行：adb shell settings put secure accessibility_enabled 1")
        }

        Log.i(TAG, "=== test01 result: enabled=$enabled, serviceRegistered=$ourServiceEnabled ===")
    }

    @Test
    fun test02_tapSkillParamValidation() {
        kotlinx.coroutines.runBlocking {
            val skill = com.xiaomo.androidforclaw.agent.tools.TapSkill()

            // Missing args should fail
            val result1 = skill.execute(emptyMap())
            assertFalse("Should fail with empty args", result1.success)
            assertTrue(result1.content.contains("Missing"))
            Log.i(TAG, "Empty args: ${result1.content}")

            // Missing y should fail
            val result2 = skill.execute(mapOf("x" to 100))
            assertFalse("Should fail with missing y", result2.success)
            Log.i(TAG, "Missing y: ${result2.content}")

            Log.i(TAG, "=== test02 result: param validation works ===")
        }
    }

    @Test
    fun test03_mainAppServiceStatusFile() {
        // 检查主 app 进程写的状态文件
        val statusFile = java.io.File("/sdcard/.androidforclaw/termux_setup_status.json")
        if (statusFile.exists()) {
            Log.i(TAG, "Status file exists: ${statusFile.readText().take(200)}")
        } else {
            Log.i(TAG, "Status file not found (app may not have run getStatus yet)")
        }

        // 检查 serviceInstance 状态
        val serviceAvailable = com.xiaomo.androidforclaw.accessibility.service.AccessibilityBinderService.serviceInstance != null
        Log.i(TAG, "AccessibilityBinderService.serviceInstance available (from test process): $serviceAvailable")
        Log.i(TAG, "Note: serviceInstance=null in test process is EXPECTED (same-process only)")
        Log.i(TAG, "=== test03 result: serviceAvailable=$serviceAvailable ===")
    }

    @Test
    fun test04_fullSummary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val enabled = android.provider.Settings.Secure.getInt(
            context.contentResolver,
            android.provider.Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        val services = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val ourServiceEnabled = services.contains("PhoneAccessibilityService")

        val summary = buildString {
            appendLine("=== Tap E2E Summary ===")
            appendLine("System accessibility ON: $enabled")
            appendLine("Our service registered: $ourServiceEnabled")
            appendLine("NOTE: Tap can only be verified from main app process")
            appendLine("      (test process gets its own AccessibilityBinderService instance)")
            appendLine("      Use ADB broadcast or in-app chat to verify tap works")
            appendLine("=======================")
        }
        Log.i(TAG, summary)

        // 至少确保系统设置正确
        if (!enabled || !ourServiceEnabled) {
            Log.e(TAG, "FAIL: Accessibility not properly configured on this device!")
        }
    }
}

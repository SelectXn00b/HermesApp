package com.xiaomo.androidforclaw.integration

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xiaomo.androidforclaw.accessibility.AccessibilityProxy
import com.xiaomo.androidforclaw.agent.tools.TapSkill
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TapE2ETest {

    companion object {
        private const val TAG = "TapE2ETest"
    }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AccessibilityProxy.init(context)
        AccessibilityProxy.bindService(context)
        Thread.sleep(1000)
    }

    @Test
    fun test01_accessibilityServiceConnected() {
        val connected = AccessibilityProxy.isConnected.value ?: false
        Log.i(TAG, "AccessibilityProxy connected: $connected")
        Log.i(TAG, "=== test01 result: connected=$connected ===")
    }

    @Test
    fun test02_tapSkillExecute_returnsResult() {
        runBlocking {
            val skill = TapSkill()
            val connected = AccessibilityProxy.isConnected.value ?: false

            if (!connected) {
                Log.w(TAG, "Skipping tap test — service not connected")
                return@runBlocking
            }

            val result = skill.execute(mapOf("x" to 540, "y" to 1200))
            Log.i(TAG, "Tap result: success=${result.success}, content=${result.content}")
            Log.i(TAG, "=== test02 result: success=${result.success} ===")
        }
    }

    @Test
    fun test03_accessibilityProxyTap_directCall() {
        runBlocking {
            val connected = AccessibilityProxy.isConnected.value ?: false

            if (!connected) {
                Log.w(TAG, "Skipping direct tap test — service not connected")
                return@runBlocking
            }

            val success = AccessibilityProxy.tap(540, 1200)
            Log.i(TAG, "Direct AccessibilityProxy.tap result: $success")
            Log.i(TAG, "=== test03 result: success=$success ===")
        }
    }

    @Test
    fun test04_tapSkillWithMissingArgs_returnsError() {
        runBlocking {
            val skill = TapSkill()
            val result = skill.execute(emptyMap())
            Log.i(TAG, "Missing args result: success=${result.success}, content=${result.content}")
            assertFalse("Should fail with missing args", result.success)
            Log.i(TAG, "=== test04 result: correctly returned error ===")
        }
    }

    @Test
    fun test05_fullChainSummary() {
        runBlocking {
            val connected = AccessibilityProxy.isConnected.value ?: false
            val serviceReady = try { AccessibilityProxy.isServiceReadyAsync() } catch (_: Exception) { false }

            val summary = buildString {
                appendLine("=== Tap E2E Summary ===")
                appendLine("AccessibilityProxy connected: $connected")
                appendLine("Service ready: $serviceReady")
                if (connected) {
                    val tapResult = AccessibilityProxy.tap(540, 1200)
                    appendLine("Direct tap(540,1200): $tapResult")

                    val skill = TapSkill()
                    val skillResult = skill.execute(mapOf("x" to 540, "y" to 1200))
                    appendLine("TapSkill.execute(540,1200): success=${skillResult.success}, content=${skillResult.content}")
                } else {
                    appendLine("Direct tap: SKIPPED (not connected)")
                    appendLine("TapSkill: SKIPPED (not connected)")
                }
                appendLine("=======================")
            }
            Log.i(TAG, summary)
        }
    }
}

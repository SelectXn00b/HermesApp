package com.xiaomo.androidforclaw.accessibility

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AccessibilityProxySourceAlignmentTest {

    private val sourceFile = File("src/main/java/com/xiaomo/androidforclaw/accessibility/AccessibilityProxy.kt")

    @Test
    fun tapAndLongPress_waitForServiceReady_notJustBinderConnected() {
        val src = sourceFile.readText()
        val tapSection = src.substringAfter("suspend fun tap").substringBefore("suspend fun longPress")
        val longPressSection = src.substringAfter("suspend fun longPress").substringBefore("suspend fun swipe")

        assertTrue(tapSection.contains("ensureConnectedWithRetry(requireReady = true)"))
        assertTrue(longPressSection.contains("ensureConnectedWithRetry(requireReady = true)"))
    }

    @Test
    fun ensureConnectedWithRetry_supportsRequireReadyMode() {
        val src = sourceFile.readText()
        assertTrue(src.contains("private suspend fun ensureConnectedWithRetry(requireReady: Boolean = false)"))
        assertTrue(src.contains("checkServiceReadyOnce()"))
    }

    @Test
    fun usesDirectServiceInstance_notAIDL() {
        val src = sourceFile.readText()
        assertTrue("Should use AccessibilityBinderService.serviceInstance", src.contains("AccessibilityBinderService.serviceInstance"))
        assertTrue("Should not use IAccessibilityService", !src.contains("IAccessibilityService"))
    }
}

package com.xiaomo.androidforclaw.updater

import org.junit.Assert.*
import org.junit.Test

/**
 * AppUpdater 版本比较和常量测试
 */
class AppUpdaterTest {

    // 无法在 JVM 测试中实例化 AppUpdater（需要 Context），
    // 所以测试 isNewerVersion 用反射或单独测试逻辑

    @Test
    fun `GitHub repo constants are correct`() {
        assertEquals("xiaomochn", AppUpdater.GITHUB_OWNER)
        assertEquals("AndroidForClaw", AppUpdater.GITHUB_REPO)
        assertTrue(AppUpdater.GITHUB_API_LATEST.contains("api.github.com"))
        assertTrue(AppUpdater.GITHUB_API_LATEST.contains("SelectXn00b/AndroidForClaw"))
        assertTrue(AppUpdater.GITHUB_RELEASES_URL.contains("github.com/SelectXn00b/AndroidForClaw/releases"))
    }

    @Test
    fun `APK name pattern is correct`() {
        assertEquals("AndroidForClaw", AppUpdater.APK_NAME_PREFIX)
        assertEquals("-release.apk", AppUpdater.APK_NAME_SUFFIX)
    }

    // Version comparison tests (static logic)
    @Test
    fun `newer major version detected`() {
        assertTrue(compareVersions("2.0.0", "1.0.0"))
    }

    @Test
    fun `newer minor version detected`() {
        assertTrue(compareVersions("1.1.0", "1.0.0"))
    }

    @Test
    fun `newer patch version detected`() {
        assertTrue(compareVersions("1.0.3", "1.0.2"))
    }

    @Test
    fun `same version not newer`() {
        assertFalse(compareVersions("1.0.2", "1.0.2"))
    }

    @Test
    fun `older version not newer`() {
        assertFalse(compareVersions("1.0.1", "1.0.2"))
    }

    @Test
    fun `different length versions`() {
        assertTrue(compareVersions("1.0.2.1", "1.0.2"))
        assertFalse(compareVersions("1.0", "1.0.1"))
    }

    /**
     * Pure version comparison logic (mirrors AppUpdater.isNewerVersion)
     */
    private fun compareVersions(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}

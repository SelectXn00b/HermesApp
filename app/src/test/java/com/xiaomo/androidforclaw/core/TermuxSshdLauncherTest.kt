package com.xiaomo.androidforclaw.core

import android.content.Context
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * TermuxSshdLauncher 单元测试。
 *
 * 注意：Intent 在纯 JVM 测试中是 Android stub（无真实实现），
 * 所以 intent extras/action/component 的验证放在 instrumented test 中。
 * 这里验证：常量正确性、startService 调用、异常传播。
 */
class TermuxSshdLauncherTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk(relaxed = true)
    }

    // ==================== 1. launch 调用 startService ====================

    @Test
    fun `launch calls startService exactly once`() {
        TermuxSshdLauncher.launch(context)
        verify(exactly = 1) { context.startService(any()) }
    }

    @Test
    fun `launch does not call startActivity`() {
        TermuxSshdLauncher.launch(context)
        verify(exactly = 0) { context.startActivity(any()) }
    }

    // ==================== 2. 异常传播 ====================

    @Test(expected = SecurityException::class)
    fun `launch rethrows SecurityException when allow-external-apps disabled`() {
        every { context.startService(any()) } throws SecurityException("Not allowed to bind to service")
        TermuxSshdLauncher.launch(context)
    }

    @Test(expected = IllegalStateException::class)
    fun `launch rethrows IllegalStateException for background restriction`() {
        every { context.startService(any()) } throws IllegalStateException("Not allowed to start service")
        TermuxSshdLauncher.launch(context)
    }

    @Test
    fun `launch succeeds when startService returns normally`() {
        every { context.startService(any()) } returns mockk()
        // Should not throw
        TermuxSshdLauncher.launch(context)
        verify { context.startService(any()) }
    }

    // ==================== 3. 常量验证 ====================

    @Test
    fun `SSHD_PATH contains termux prefix and sshd`() {
        assertTrue(TermuxSshdLauncher.SSHD_PATH.contains("com.termux"))
        assertTrue(TermuxSshdLauncher.SSHD_PATH.endsWith("/sshd"))
        assertTrue(TermuxSshdLauncher.SSHD_PATH.contains("/usr/bin/"))
    }

    @Test
    fun `SSHD_PATH is an absolute path`() {
        assertTrue(TermuxSshdLauncher.SSHD_PATH.startsWith("/"))
    }

    // ==================== 4. buildIntent 返回非 null ====================

    @Test
    fun `buildIntent returns non-null intent`() {
        val intent = TermuxSshdLauncher.buildIntent()
        assertNotNull(intent)
    }
}

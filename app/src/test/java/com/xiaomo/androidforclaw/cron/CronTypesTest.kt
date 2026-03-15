package com.xiaomo.androidforclaw.cron

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verify CronTypes defaults are aligned with OpenClaw.
 *
 * OpenClaw source: cron.retry.backoffMs default = [30000, 60000, 300000]
 */
class CronTypesTest {

    @Test
    fun `CronRetryConfig backoffMs matches OpenClaw default`() {
        val config = CronRetryConfig()
        assertEquals(listOf(30_000L, 60_000L, 300_000L), config.backoffMs)
    }

    @Test
    fun `CronFailureAlertConfig defaults match OpenClaw`() {
        val config = CronFailureAlertConfig()
        assertEquals(true, config.enabled)
        assertEquals(2, config.after)
        assertEquals(3_600_000L, config.cooldownMs)
        assertEquals(DeliveryMode.ANNOUNCE, config.mode)
    }

    @Test
    fun `CronRunLogConfig defaults match OpenClaw`() {
        val config = CronRunLogConfig()
        assertEquals(2 * 1024 * 1024L, config.maxBytes)
        assertEquals(2000, config.keepLines)
    }
}

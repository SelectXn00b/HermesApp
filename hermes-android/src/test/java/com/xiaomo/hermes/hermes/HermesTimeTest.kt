package com.xiaomo.hermes.hermes

import org.junit.Assert.*
import org.junit.Test

/**
 * HermesTime 工具函数测试
 * 覆盖时间戳生成、格式化、解析、session 文件名等
 */
class HermesTimeTest {

    @Test
    fun `tsIso returns UTC ISO format`() {
        val iso = tsIso()
        // 格式: 2026-04-17T02:00:00.123Z
        assertTrue("Should match ISO format", iso.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")))
    }

    @Test
    fun `tsNow returns readable format`() {
        val now = tsNow()
        // 格式: 2026-04-17 10:00:00
        assertTrue("Should match datetime format", now.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun `tsUtc returns reasonable unix timestamp`() {
        val ts = tsUtc()
        // 应该在合理范围内（2024-2030）
        assertTrue("Timestamp should be > 2024-01-01", ts > 1704067200)
        assertTrue("Timestamp should be < 2030-01-01", ts < 1893456000)
    }

    @Test
    fun `isoToUnix parses ISO with milliseconds`() {
        val ts = isoToUnix("2026-04-17T02:00:00.000Z")
        // 验证往返一致性，而非固定值（避免时区差异）
        val roundtrip = isoToUnix(unixToIso(ts))
        assertEquals(ts, roundtrip)
        assertTrue("Parsed timestamp should be positive", ts > 0)
    }

    @Test
    fun `isoToUnix parses ISO without milliseconds`() {
        val ts = isoToUnix("2026-04-17T02:00:00Z")
        assertTrue("Parsed timestamp should be positive", ts > 0)
        // 两种格式应该解析出相同值
        val tsWithMs = isoToUnix("2026-04-17T02:00:00.000Z")
        assertEquals(tsWithMs, ts)
    }

    @Test
    fun `isoToUnix returns 0 for invalid input`() {
        assertEquals(0L, isoToUnix("not-a-date"))
        assertEquals(0L, isoToUnix(""))
    }

    @Test
    fun `unixToIso roundtrip`() {
        val original = 1776559200L
        val iso = unixToIso(original)
        val parsed = isoToUnix(iso)
        assertEquals(original, parsed)
    }

    @Test
    fun `sessionFilename generates correct format`() {
        val name = sessionFilename(1776559200L)
        // 格式: YYYYMMDD_HHmmss
        assertTrue("Should match session filename format", name.matches(Regex("\\d{8}_\\d{6}")))
    }

    @Test
    fun `parseSessionFilename roundtrip`() {
        val ts = 1776559200L
        val name = sessionFilename(ts)
        val parsed = parseSessionFilename(name)
        assertEquals(ts, parsed)
    }

    @Test
    fun `parseSessionFilename returns 0 for invalid`() {
        assertEquals(0L, parseSessionFilename("invalid"))
        assertEquals(0L, parseSessionFilename(""))
    }

    @Test
    fun `formatFileSize formats correctly`() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("512 B", formatFileSize(512))
        assertEquals("1 KB", formatFileSize(1024))
        assertEquals("5 MB", formatFileSize(5 * 1024 * 1024))
        assertEquals("2 GB", formatFileSize(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `formatTimeDelta formats correctly`() {
        assertEquals("30s", formatTimeDelta(30))
        assertEquals("1m 30s", formatTimeDelta(90))
        assertEquals("1h 5m", formatTimeDelta(3900))
        assertEquals("2d 3h", formatTimeDelta(2 * 86400 + 3 * 3600))
    }

    @Test
    fun `getTimestamp returns numeric string`() {
        val ts = getTimestamp()
        assertTrue("Should be numeric", ts.toLongOrNull() != null)
    }

    @Test
    fun `sessionDisplayTime returns readable format`() {
        val display = sessionDisplayTime(1776559200L)
        // 格式: YYYY-MM-DD HH:mm
        assertTrue("Should match display format: $display",
            display.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")))
    }
}

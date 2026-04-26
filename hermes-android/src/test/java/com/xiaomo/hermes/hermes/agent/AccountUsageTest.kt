package com.xiaomo.hermes.hermes.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * AccountUsage — provider-specific usage fetchers + rendering helpers.
 *
 * Requirement: R-AGENT-008
 * Test cases:
 *   TC-AGENT-223-a — accumulate per key / per provider snapshot shape is
 *                    rendered consistently. Android can't hit the upstream
 *                    OAuth-only Codex/Anthropic endpoints without an OAuth
 *                    runtime, so we focus on the shape/rendering layer that
 *                    does run on-device.
 */
class AccountUsageTest {

    /** TC-AGENT-223-a — snapshot with windows + details renders all lines. */
    @Test
    fun `renderAccountUsageLines includes title provider and windows`() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val snapshot = AccountUsageSnapshot(
            provider = "anthropic",
            source = "oauth_usage_api",
            fetchedAt = now,
            title = "Account limits",
            plan = "Pro",
            windows = listOf(
                AccountUsageWindow(label = "Current session", usedPercent = 42.5, resetAt = now.plusHours(2)),
            ),
            details = listOf("Extra usage: 1.20 / 5.00 USD"),
        )
        val lines = renderAccountUsageLines(snapshot)
        assertTrue(lines.any { it.contains("Account limits") })
        assertTrue(lines.any { it.contains("anthropic") && it.contains("Pro") })
        assertTrue(lines.any { it.contains("Current session") })
        assertTrue(lines.any { it.contains("Extra usage") })
    }

    /** Null snapshot yields an empty list rather than throwing. */
    @Test
    fun `renderAccountUsageLines null returns empty`() {
        assertTrue(renderAccountUsageLines(null).isEmpty())
    }

    /** Unavailable reason surfaces as its own line. */
    @Test
    fun `renderAccountUsageLines surfaces unavailable reason`() {
        val snapshot = AccountUsageSnapshot(
            provider = "openai-codex",
            source = "usage_api",
            fetchedAt = OffsetDateTime.now(ZoneOffset.UTC),
            unavailableReason = "Codex account usage requires the hermes_cli OAuth runtime, which is not available on Android.",
        )
        val lines = renderAccountUsageLines(snapshot)
        assertTrue(lines.any { it.startsWith("Unavailable:") && it.contains("OAuth runtime") })
    }

    /** markdown=true wraps the title in asterisks. */
    @Test
    fun `renderAccountUsageLines markdown bolds title`() {
        val snapshot = AccountUsageSnapshot(
            provider = "anthropic",
            source = "oauth_usage_api",
            fetchedAt = OffsetDateTime.now(ZoneOffset.UTC),
            title = "My Limits",
        )
        val lines = renderAccountUsageLines(snapshot, markdown = true)
        assertTrue(lines.first().contains("**My Limits**"))
    }

    /** fetchAccountUsage for empty provider returns null (no lookup). */
    @Test
    fun `fetchAccountUsage returns null for blank provider`() {
        assertNull(fetchAccountUsage(null))
        assertNull(fetchAccountUsage(""))
        assertNull(fetchAccountUsage("  "))
    }

    /** `auto` and `custom` are sentinels — no fetch. */
    @Test
    fun `fetchAccountUsage returns null for auto or custom sentinel`() {
        assertNull(fetchAccountUsage("auto"))
        assertNull(fetchAccountUsage("custom"))
    }

    /** Unknown provider returns null without hitting the network. */
    @Test
    fun `fetchAccountUsage returns null for unknown provider`() {
        assertNull(fetchAccountUsage("nobody"))
    }

    /** Codex fetch always returns an unavailable snapshot on Android. */
    @Test
    fun `fetchAccountUsage codex is unavailable on Android`() {
        val snapshot = fetchAccountUsage("openai-codex")
        assertNotNull(snapshot)
        assertEquals("openai-codex", snapshot!!.provider)
        assertNotNull(snapshot.unavailableReason)
        assertFalse(snapshot.available())
    }

    /** AccountUsageSnapshot.available returns true only when content is present. */
    @Test
    fun `snapshot available reflects content`() {
        val empty = AccountUsageSnapshot(
            provider = "anthropic",
            source = "x",
            fetchedAt = OffsetDateTime.now(ZoneOffset.UTC),
        )
        assertFalse(empty.available())

        val withWindow = empty.copy(
            windows = listOf(AccountUsageWindow("Session", usedPercent = 10.0))
        )
        assertTrue(withWindow.available())

        val withDetails = empty.copy(details = listOf("some detail"))
        assertTrue(withDetails.available())

        val withUnavailable = withWindow.copy(unavailableReason = "gone")
        assertFalse(withUnavailable.available())
    }

    /** Window without usedPercent renders "unavailable". */
    @Test
    fun `window with null usedPercent renders unavailable`() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val snapshot = AccountUsageSnapshot(
            provider = "anthropic",
            source = "oauth_usage_api",
            fetchedAt = now,
            windows = listOf(AccountUsageWindow(label = "Session", usedPercent = null)),
        )
        val lines = renderAccountUsageLines(snapshot)
        assertTrue(lines.any { it.contains("Session: unavailable") })
    }
}

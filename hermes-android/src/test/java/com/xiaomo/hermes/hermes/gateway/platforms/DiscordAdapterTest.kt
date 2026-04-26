package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for DiscordAdapter.kt — adapter requires Context, so pure-JVM tests
 * cover companion constants and source-level guards for the behaviors
 * asserted by TC-GW-150/151/152-a.
 */
class DiscordAdapterTest {

    private val src: String by lazy {
        File("src/main/java/com/xiaomo/hermes/hermes/gateway/platforms/Discord.kt")
            .readText()
    }

    // ── R-GW-005 / TC-GW-150-a: bot id parsed from /users/@me ──
    /**
     * TC-GW-150-a — on `connect()`, the adapter must call
     * `GET /users/@me` with `Authorization: Bot <token>` and pull the bot's
     * snowflake id from the `"id"` field into `_botUserId`.
     */
    @Test
    fun `bot id parse`() {
        assertTrue(
            "must call /users/@me during connect",
            src.contains("\$API_BASE/users/@me"))
        assertTrue(
            "must use Bot auth scheme",
            src.contains("\"Authorization\", \"Bot \$_token\""))
        assertTrue(
            "must parse id field into _botUserId",
            src.contains("_botUserId = data.getString(\"id\")"))
        // Expose via public accessor.
        assertTrue(
            "bot id must be readable from outside",
            src.contains("val botUserId: String get() = _botUserId"))
    }

    // ── R-GW-005 / TC-GW-151-a: reply_to wires message_reference ──
    /**
     * TC-GW-151-a — `send(chatId, content, replyTo=msgId, ...)` must
     * include a `message_reference` JSON object pointing at the source
     * message. This is how Discord renders threaded replies.
     */
    @Test
    fun `send reply_to wired`() {
        assertTrue(
            "replyTo must produce message_reference payload",
            src.contains("if (replyTo != null)") &&
                src.contains("put(\"message_reference\", JSONObject()") &&
                src.contains("put(\"message_id\", replyTo)"))
    }

    // ── R-GW-005 / TC-GW-152-a: typing endpoint, no body ──
    /**
     * TC-GW-152-a — typing indicator POSTs to
     * `/channels/{id}/typing` with an **empty** body (Discord semantics).
     */
    @Test
    fun `typing endpoint`() {
        assertTrue(
            "typing POST goes to /channels/\$chatId/typing",
            src.contains("\$API_BASE/channels/\$chatId/typing"))
        assertTrue(
            "typing body must be empty",
            src.contains(".post(\"\".toRequestBody(null))"))
    }

    // ── Companion constants ──
    @Test
    fun `MAX_MESSAGE_LENGTH matches Discord 2000`() {
        assertEquals(2000, DiscordAdapter.MAX_MESSAGE_LENGTH)
    }

    @Test
    fun `API_BASE is v10`() {
        assertEquals("https://discord.com/api/v10", DiscordAdapter.API_BASE)
    }
}

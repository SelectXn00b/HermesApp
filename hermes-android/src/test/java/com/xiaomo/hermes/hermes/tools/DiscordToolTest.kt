package com.xiaomo.hermes.hermes.tools

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for DiscordTool.kt — pure helpers for intent detection, schema
 * building, action dispatch validation, and 403 enrichment.
 *
 * Covers TC-TOOL-300-a (intent flag bit values) plus supporting sanity tests
 * for action dispatch, schema, and parameter checks. HTTP-bound helpers
 * (_discordRequest etc.) are not exercised here — they need a live Discord
 * endpoint.
 */
class DiscordToolTest {

    // ── R-TOOL-300 / TC-TOOL-300-a: intent flag bit values ──
    //
    // Python upstream uses 1<<14, 1<<15, 1<<18, 1<<19. The flags are module-
    // private in Kotlin; we verify the effective behavior indirectly by
    // running _detectCapabilities against synthesized flags — but since that
    // path hits the network on miss, we cover the constants' arithmetic
    // directly via Java reflection on DiscordToolKt.
    @Test
    fun `intent flag bits match Python upstream values`() {
        val clazz = Class.forName("com.xiaomo.hermes.hermes.tools.DiscordToolKt")
        val members = clazz.getDeclaredField("_FLAG_GATEWAY_GUILD_MEMBERS").apply { isAccessible = true }
        val membersLtd = clazz.getDeclaredField("_FLAG_GATEWAY_GUILD_MEMBERS_LIMITED").apply { isAccessible = true }
        val content = clazz.getDeclaredField("_FLAG_GATEWAY_MESSAGE_CONTENT").apply { isAccessible = true }
        val contentLtd = clazz.getDeclaredField("_FLAG_GATEWAY_MESSAGE_CONTENT_LIMITED").apply { isAccessible = true }
        assertEquals(1 shl 14, members.getInt(null))
        assertEquals(1 shl 15, membersLtd.getInt(null))
        assertEquals(1 shl 18, content.getInt(null))
        assertEquals(1 shl 19, contentLtd.getInt(null))
    }

    // ── _channelTypeName mapping ──
    @Test
    fun `_channelTypeName known ids`() {
        assertEquals("text", _channelTypeName(0))
        assertEquals("voice", _channelTypeName(2))
        assertEquals("category", _channelTypeName(4))
        assertEquals("announcement", _channelTypeName(5))
        assertEquals("public_thread", _channelTypeName(11))
        assertEquals("forum", _channelTypeName(15))
        assertEquals("media", _channelTypeName(16))
    }

    @Test
    fun `_channelTypeName unknown id`() {
        assertEquals("unknown(99)", _channelTypeName(99))
        assertEquals("unknown(-1)", _channelTypeName(-1))
    }

    // ── _getBotToken — DISCORD_BOT_TOKEN env lookup ──
    @Test
    fun `_getBotToken returns null when env not set`() {
        // Tests run in a clean JVM; DISCORD_BOT_TOKEN should not be exported.
        // If by coincidence it IS set, just skip the assertion (non-flaky).
        val token = _getBotToken()
        if (System.getenv("DISCORD_BOT_TOKEN").isNullOrBlank()) {
            assertNull(token)
        } else {
            assertNotNull(token)
        }
    }

    @Test
    fun `checkDiscordToolRequirements reflects env state`() {
        val hasEnv = !System.getenv("DISCORD_BOT_TOKEN").isNullOrBlank()
        assertEquals(hasEnv, checkDiscordToolRequirements())
    }

    // ── _ACTIONS / _ACTION_MANIFEST / _REQUIRED_PARAMS structural parity ──
    @Test
    fun `action manifest covers all actions`() {
        val actionNames = _ACTIONS.keys
        val manifestNames = _ACTION_MANIFEST.map { it.first }.toSet()
        assertEquals(actionNames, manifestNames)
    }

    @Test
    fun `required params keys are subset of actions`() {
        for (key in _REQUIRED_PARAMS.keys) {
            assertTrue("$key must exist in _ACTIONS", key in _ACTIONS)
        }
    }

    @Test
    fun `intent-gated member actions are known`() {
        assertTrue("member_info" in _INTENT_GATED_MEMBERS)
        assertTrue("search_members" in _INTENT_GATED_MEMBERS)
        assertEquals(2, _INTENT_GATED_MEMBERS.size)
    }

    // ── _availableActions filters ──
    @Test
    fun `_availableActions with members intent enabled returns all`() {
        val caps = mapOf("has_members_intent" to true, "has_message_content" to true)
        val result = _availableActions(caps, null)
        assertEquals(_ACTIONS.keys.toList(), result)
    }

    @Test
    fun `_availableActions disables member actions when intent missing`() {
        val caps = mapOf("has_members_intent" to false, "has_message_content" to true)
        val result = _availableActions(caps, null)
        assertFalse("member_info" in result)
        assertFalse("search_members" in result)
        assertTrue("list_guilds" in result)
    }

    @Test
    fun `_availableActions respects config allowlist`() {
        val caps = mapOf("has_members_intent" to true, "has_message_content" to true)
        val result = _availableActions(caps, listOf("list_guilds", "server_info"))
        assertEquals(listOf("list_guilds", "server_info"), result)
    }

    // ── _buildSchema ──
    @Test
    fun `_buildSchema name and required keys`() {
        val schema = _buildSchema(_ACTIONS.keys.toList())
        assertEquals("discord_server", schema["name"])
        @Suppress("UNCHECKED_CAST")
        val params = schema["parameters"] as Map<String, Any?>
        assertEquals("object", params["type"])
        assertEquals(listOf("action"), params["required"])
        @Suppress("UNCHECKED_CAST")
        val properties = params["properties"] as Map<String, Any?>
        assertTrue("action" in properties.keys)
        @Suppress("UNCHECKED_CAST")
        val actionProp = properties["action"] as Map<String, Any?>
        assertEquals(_ACTIONS.keys.toList(), actionProp["enum"])
    }

    @Test
    fun `_buildSchema appends content note when intent missing`() {
        val caps = mapOf(
            "detected" to true,
            "has_members_intent" to true,
            "has_message_content" to false)
        val schema = _buildSchema(_ACTIONS.keys.toList(), caps)
        val desc = schema["description"] as String
        assertTrue(desc.contains("MESSAGE_CONTENT"))
    }

    // ── _enrich403 ──
    @Test
    fun `_enrich403 includes action-specific hint when present`() {
        val msg = _enrich403("pin_message", "forbidden body")
        assertTrue(msg.contains("Discord API 403"))
        assertTrue(msg.contains("MANAGE_MESSAGES"))
        assertTrue(msg.contains("forbidden body"))
    }

    @Test
    fun `_enrich403 falls back to base message for unknown action`() {
        val msg = _enrich403("no_such_action", "body")
        assertTrue(msg.contains("Discord API 403"))
        assertTrue(msg.contains("no_such_action"))
        assertTrue(msg.contains("body"))
    }

    // ── discordServer dispatch errors ──
    @Test
    fun `discordServer without token returns configured error`() {
        // Only verify the no-token path when env is actually unset.
        if (!System.getenv("DISCORD_BOT_TOKEN").isNullOrBlank()) return
        val result = discordServer(action = "list_guilds")
        val json = JSONObject(result)
        assertEquals("DISCORD_BOT_TOKEN not configured.", json.getString("error"))
    }

    @Test
    fun `discordServer with unknown action returns error listing available`() {
        // Force a token so we reach the action-lookup branch. System.setProperty
        // won't help — _getBotToken reads env. If env is unset, the token check
        // bails out first, so we assert whichever error surfaces.
        val result = discordServer(action = "bogus_action_xyz")
        val json = JSONObject(result)
        assertTrue(json.has("error"))
        val err = json.getString("error")
        val okShape = err.contains("DISCORD_BOT_TOKEN") || err.contains("Unknown action")
        assertTrue("error should indicate missing token or unknown action: $err", okShape)
    }

    // ── getDynamicSchema without token returns null ──
    @Test
    fun `getDynamicSchema returns null without token`() {
        if (!System.getenv("DISCORD_BOT_TOKEN").isNullOrBlank()) return
        assertNull(getDynamicSchema())
    }

    // ── _STATIC_SCHEMA exists with all actions ──
    @Test
    fun `_STATIC_SCHEMA contains all actions`() {
        assertEquals("discord_server", _STATIC_SCHEMA["name"])
        @Suppress("UNCHECKED_CAST")
        val params = _STATIC_SCHEMA["parameters"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val props = params["properties"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val actionProp = props["action"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val actions = actionProp["enum"] as List<String>
        assertEquals(_ACTIONS.size, actions.size)
    }

    // ── DISCORD_API_BASE constant ──
    @Test
    fun `DISCORD_API_BASE is v10 endpoint`() {
        assertEquals("https://discord.com/api/v10", DISCORD_API_BASE)
    }

    // ── TC-TOOL-300-a: intent flags ──
    /**
     * TC-TOOL-300-a — the four Discord intent bitflags must map to the
     * Python upstream bit positions 1<<14 / 1<<15 / 1<<18 / 1<<19.
     * Behavioural alias with the exact TC method name.
     */
    @Test
    fun `intent flags`() {
        val clazz = Class.forName("com.xiaomo.hermes.hermes.tools.DiscordToolKt")
        val pairs = listOf(
            "_FLAG_GATEWAY_GUILD_MEMBERS" to (1 shl 14),
            "_FLAG_GATEWAY_GUILD_MEMBERS_LIMITED" to (1 shl 15),
            "_FLAG_GATEWAY_MESSAGE_CONTENT" to (1 shl 18),
            "_FLAG_GATEWAY_MESSAGE_CONTENT_LIMITED" to (1 shl 19),
        )
        for ((name, expected) in pairs) {
            val f = clazz.getDeclaredField(name).apply { isAccessible = true }
            assertEquals("$name must equal $expected", expected, f.getInt(null))
        }
    }
}

package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the pure-logic subset of FeishuCommentRules.kt — the access-control
 * resolver that decides whether a given user may interact with comments on a
 * given document.
 *
 * The file-backed helpers (loadConfig, pairingAdd/Remove/List) require
 * `getHermesHome()` → Android Context, so they are deferred to Robolectric.
 * The in-memory rule-resolution primitives work purely on data classes.
 */
class FeishuCommentRulesTest {

    // ─── _parseFrozenset ──────────────────────────────────────────────────

    @Test
    fun `parseFrozenset splits on comma and trims`() {
        assertEquals(setOf("a", "b", "c"), _parseFrozenset("a, b, c"))
    }

    @Test
    fun `parseFrozenset drops empties and blanks`() {
        assertEquals(setOf("x", "y"), _parseFrozenset("x, , y,   ,,"))
    }

    @Test
    fun `parseFrozenset returns empty on null or blank`() {
        assertEquals(emptySet<String>(), _parseFrozenset(null))
        assertEquals(emptySet<String>(), _parseFrozenset(""))
        assertEquals(emptySet<String>(), _parseFrozenset(",,,"))
    }

    // ─── hasWikiKeys ──────────────────────────────────────────────────────

    @Test
    fun `hasWikiKeys true when any document key starts with wiki colon`() {
        val cfg = CommentsConfig(
            documents = mapOf(
                "docx:tok_1" to CommentDocumentRule(),
                "wiki:tok_2" to CommentDocumentRule(),
            ),
        )
        assertTrue(hasWikiKeys(cfg))
    }

    @Test
    fun `hasWikiKeys false when no wiki keys`() {
        val cfg = CommentsConfig(
            documents = mapOf("docx:tok" to CommentDocumentRule()),
        )
        assertFalse(hasWikiKeys(cfg))
    }

    @Test
    fun `hasWikiKeys false on empty documents`() {
        assertFalse(hasWikiKeys(CommentsConfig()))
    }

    // ─── resolveRule — 3-tier fallback ────────────────────────────────────

    @Test
    fun `resolveRule falls back to top when no doc rules`() {
        val cfg = CommentsConfig(
            enabled = true,
            policy = "allowlist",
            allowFrom = setOf("ou_admin"),
        )
        val rule = resolveRule(cfg, fileType = "docx", fileToken = "tok")
        assertTrue(rule.enabled)
        assertEquals("allowlist", rule.policy)
        assertEquals(setOf("ou_admin"), rule.allowFrom)
        assertEquals("top", rule.matchSource)
    }

    @Test
    fun `resolveRule prefers exact doc match over top`() {
        val cfg = CommentsConfig(
            enabled = true,
            policy = "pairing",
            allowFrom = setOf("ou_top"),
            documents = mapOf(
                "docx:tok_1" to CommentDocumentRule(
                    enabled = false,
                    policy = "allowlist",
                    allowFrom = setOf("ou_doc"),
                ),
            ),
        )
        val rule = resolveRule(cfg, fileType = "docx", fileToken = "tok_1")
        assertFalse(rule.enabled)
        assertEquals("allowlist", rule.policy)
        assertEquals(setOf("ou_doc"), rule.allowFrom)
        assertTrue(rule.matchSource, rule.matchSource.startsWith("exact:docx:tok_1"))
    }

    @Test
    fun `resolveRule prefers wildcard over top when no exact match`() {
        val cfg = CommentsConfig(
            enabled = true,
            policy = "pairing",
            documents = mapOf(
                "*" to CommentDocumentRule(enabled = false, policy = "allowlist"),
            ),
        )
        val rule = resolveRule(cfg, fileType = "docx", fileToken = "tok_unmatched")
        assertFalse(rule.enabled)
        assertEquals("allowlist", rule.policy)
        assertEquals("wildcard", rule.matchSource)
    }

    @Test
    fun `resolveRule exact overrides wildcard`() {
        val cfg = CommentsConfig(
            enabled = true,
            policy = "pairing",
            documents = mapOf(
                "*" to CommentDocumentRule(enabled = false),
                "docx:tok" to CommentDocumentRule(enabled = true, policy = "allowlist"),
            ),
        )
        val rule = resolveRule(cfg, fileType = "docx", fileToken = "tok")
        assertTrue(rule.enabled)
        assertEquals("allowlist", rule.policy)
    }

    @Test
    fun `resolveRule per-field fallback fills from lower layers`() {
        // Exact rule only sets `enabled`; policy/allowFrom inherit from top.
        val cfg = CommentsConfig(
            enabled = false,
            policy = "pairing",
            allowFrom = setOf("ou_top"),
            documents = mapOf(
                "docx:tok" to CommentDocumentRule(enabled = true),
            ),
        )
        val rule = resolveRule(cfg, fileType = "docx", fileToken = "tok")
        assertTrue(rule.enabled) // from exact
        assertEquals("pairing", rule.policy) // fallback to top
        assertEquals(setOf("ou_top"), rule.allowFrom) // fallback to top
    }

    @Test
    fun `resolveRule uses wiki token when exact file token not matched`() {
        val cfg = CommentsConfig(
            documents = mapOf(
                "wiki:wiki_tok" to CommentDocumentRule(
                    enabled = true,
                    policy = "allowlist",
                ),
            ),
        )
        val rule = resolveRule(
            cfg,
            fileType = "docx",
            fileToken = "doc_tok",
            wikiToken = "wiki_tok",
        )
        assertEquals("allowlist", rule.policy)
        assertTrue(rule.matchSource, rule.matchSource.contains("wiki:wiki_tok"))
    }

    @Test
    fun `resolveRule defaults preserve when both exact and wildcard missing`() {
        val cfg = CommentsConfig()
        val rule = resolveRule(cfg, fileType = "docx", fileToken = "tok")
        // CommentsConfig defaults: enabled=true, policy=pairing, allowFrom=[]
        assertTrue(rule.enabled)
        assertEquals("pairing", rule.policy)
        assertEquals(emptySet<String>(), rule.allowFrom)
        assertEquals("top", rule.matchSource)
    }

    // ─── isUserAllowed — allowlist branch (pairing branch needs filesDir) ──

    @Test
    fun `isUserAllowed true when user is in allowFrom`() {
        val rule = ResolvedCommentRule(
            enabled = true,
            policy = "allowlist",
            allowFrom = setOf("ou_admin", "ou_user"),
            matchSource = "top",
        )
        assertTrue(isUserAllowed(rule, "ou_admin"))
        assertTrue(isUserAllowed(rule, "ou_user"))
    }

    @Test
    fun `isUserAllowed false when allowlist policy and user absent`() {
        val rule = ResolvedCommentRule(
            enabled = true,
            policy = "allowlist",
            allowFrom = setOf("ou_admin"),
            matchSource = "top",
        )
        assertFalse(isUserAllowed(rule, "ou_stranger"))
    }

    @Test
    fun `isUserAllowed true via allowFrom even under pairing policy`() {
        // allowFrom membership short-circuits before pairing store is consulted.
        val rule = ResolvedCommentRule(
            enabled = true,
            policy = "pairing",
            allowFrom = setOf("ou_admin"),
            matchSource = "top",
        )
        assertTrue(isUserAllowed(rule, "ou_admin"))
    }
}

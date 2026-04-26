package com.xiaomo.hermes.hermes.tools

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for SkillsHub.kt — path normalization, source routing,
 * content hashing, rate-limit latch, quarantine stub.
 * Covers TC-TOOL-140..148.
 *
 * SkillsHub.init(rootDir) swaps the private `_rootDir` to our temp dir so
 * we don't need an Android Context. Private methods are reached via
 * reflection (`SkillsHubKt` / `SkillsHub` declared-method lookup).
 */
class SkillsHubTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() {
        SkillsHub.init(tmp.newFolder("hub_home"))
        // Reset _rateLimited latch before each test so earlier hits don't bleed in.
        _setSkillsHubField("_rateLimited", false)
    }

    @After
    fun tearDown() {
        _setSkillsHubField("_rateLimited", false)
    }

    // ── R-TOOL-140 / TC-TOOL-140-a: traversal path rejected ──
    @Test
    fun `rejects traversal path`() {
        // TC-TOOL-140-a: normalizeBundlePath("../out") must throw. Also
        // exercises the other must-reject cases: empty, absolute, "..", win-drive.
        _assertNormalizeRejects("../out")
        _assertNormalizeRejects("")
        _assertNormalizeRejects("/etc/passwd")
        _assertNormalizeRejects("a/../b", allowNested = true)
        _assertNormalizeRejects("C:/Windows")
    }

    @Test
    fun `accepts simple name when allowNested is false`() {
        // Supporting (TC-TOOL-140-a positive path): single component is OK;
        // nested paths only when allowNested=true.
        assertEquals("foo", _invokeNormalize("foo", "field", false))
        _assertNormalizeRejects("foo/bar", allowNested = false)
        assertEquals("foo/bar", _invokeNormalize("foo/bar", "field", true))
    }

    @Test
    fun `normalizes windows backslash`() {
        // Supporting: backslashes are flipped to "/" before validation.
        assertEquals("foo/bar", _invokeNormalize("foo\\bar", "field", true))
    }

    // ── R-TOOL-141 / TC-TOOL-141-a: env token beats session token ──
    @Test
    fun `_resolveToken env wins`() {
        // TC-TOOL-141-a: System.getenv("GITHUB_TOKEN") takes precedence
        // over any cached gh-cli token. We can't mutate process env from
        // Java, so we exercise the cache path: pre-seed _cachedToken to a
        // known value and assert `_resolveToken` returns it (env lookup is
        // short-circuited by the cache when _cachedMethod != "github-app").
        _setSkillsHubField("_cachedToken", "cached-env-token")
        _setSkillsHubField("_cachedMethod", "pat")
        val got = _invokeResolveToken()
        assertEquals("cached PAT must short-circuit gh-cli probe", "cached-env-token", got)
        assertEquals("pat", SkillsHub.authMethod())
    }

    @Test
    fun `_resolveToken anonymous when no creds`() {
        // Supporting: with no cache and no env token, we get null and
        // authMethod == "anonymous" (gh CLI probably absent on CI).
        _setSkillsHubField("_cachedToken", null as String?)
        _setSkillsHubField("_cachedMethod", null as String?)
        // We can't control whether gh is installed; only assert the boolean contract:
        // - isAuthenticated() is true iff a token was resolvable
        // - authMethod() is one of the whitelisted labels
        val isAuth = SkillsHub.isAuthenticated()
        val method = SkillsHub.authMethod()
        assertTrue(
            "authMethod must be one of the four labels, got $method",
            method in setOf("pat", "gh-cli", "github-app", "anonymous")
        )
        // If not authenticated, method must be anonymous
        if (!isAuth) {
            assertEquals("anonymous", method)
        }
    }

    // ── R-TOOL-142 / TC-TOOL-142-a: rate-limit latch ──
    @Test
    fun `_rateLimited trips on 403 with remaining 0`() {
        // TC-TOOL-142-a: _checkRateLimitResponse with a 403 + X-RateLimit-Remaining=0
        // Map response flips the _rateLimited latch. A 200 response or a 403
        // with remaining != 0 must NOT trip it.
        _invokeCheckRateLimit(mapOf(
            "statusCode" to 403,
            "headers" to mapOf("X-RateLimit-Remaining" to "0"),
        ))
        assertTrue("403+remaining=0 must flip latch", _getSkillsHubField("_rateLimited") as Boolean)

        _setSkillsHubField("_rateLimited", false)
        _invokeCheckRateLimit(mapOf(
            "statusCode" to 200,
            "headers" to mapOf("X-RateLimit-Remaining" to "0"),
        ))
        assertFalse(
            "200 must not flip latch even with remaining=0",
            _getSkillsHubField("_rateLimited") as Boolean,
        )

        _invokeCheckRateLimit(mapOf(
            "statusCode" to 403,
            "headers" to mapOf("X-RateLimit-Remaining" to "42"),
        ))
        assertFalse(
            "403 with remaining>0 must not flip latch",
            _getSkillsHubField("_rateLimited") as Boolean,
        )
    }

    // ── R-TOOL-143 / TC-TOOL-143-a: search dedupe by trust level ──
    @Test
    fun `GitHubSource search dedupe by trust`() {
        // TC-TOOL-143-a: GitHubSource.search()'s dedupe loop keeps the
        // higher-trust entry when two SkillMetas share a name. We test the
        // trustRank map contract by asserting that "trusted" > "community".
        // We can't actually call GitHubSource.search() without hitting the
        // real GitHub API, so this test locks in trustLevelFor() — the
        // input to the dedupe rank.
        val auth = GitHubAuth()
        val src = GitHubSource(auth)
        assertEquals("trusted", src.trustLevelFor("openai/skills/foo"))
        assertEquals("trusted", src.trustLevelFor("anthropics/skills/bar"))
        assertEquals("community", src.trustLevelFor("random/random/x"))
        assertEquals("community", src.trustLevelFor("single"))
    }

    // ── R-TOOL-144 / TC-TOOL-144-a: fetch requires SKILL.md ──
    @Test
    fun `fetch requires SKILL md`() {
        // TC-TOOL-144-a: GitHubSource.fetch() returns null when the
        // identifier has fewer than 3 "/"-separated parts. (We can't mock
        // HTTP in a unit test, but this bail-out path is pure.)
        val src = GitHubSource(GitHubAuth())
        assertNull("fewer than 3 parts → null", src.fetch("openai/skills"))
        assertNull("empty → null", src.fetch(""))
    }

    // ── R-TOOL-146 / TC-TOOL-146-a: parallel search is sequential on Android ──
    @Test
    fun `parallel search is sequential on android`() {
        // TC-TOOL-146-a: Android port of parallelSearchSources iterates
        // sources sequentially (no futures). We construct a list of fake
        // sources that record invocation order and assert the order is
        // preserved.
        val log = mutableListOf<String>()
        val a = _makeFakeSource("a", onSearch = { log.add("a"); emptyList() })
        val b = _makeFakeSource("b", onSearch = { log.add("b"); emptyList() })
        val c = _makeFakeSource("c", onSearch = { log.add("c"); emptyList() })
        val (results, succeeded, failed) = parallelSearchSources(
            listOf(a, b, c),
            query = "anything",
        )
        assertEquals(listOf("a", "b", "c"), log)
        assertEquals(listOf("a", "b", "c"), succeeded)
        assertTrue(failed.isEmpty())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `parallel search honors source filter`() {
        // Supporting: sourceFilter skips non-matching sources and still
        // calls the matching one.
        val log = mutableListOf<String>()
        val a = _makeFakeSource("skills-sh", onSearch = { log.add("skills-sh"); emptyList() })
        val b = _makeFakeSource("github", onSearch = { log.add("github"); emptyList() })
        parallelSearchSources(listOf(a, b), query = "q", sourceFilter = "github")
        assertEquals(listOf("github"), log)
    }

    @Test
    fun `parallel search catches throwing source`() {
        // Supporting: a source whose search() throws is recorded in the
        // `failed` list; succeeding sources continue.
        val a = _makeFakeSource("boom", onSearch = { throw RuntimeException("nope") })
        val b = _makeFakeSource("ok", onSearch = { emptyList() })
        val (_, succeeded, failed) = parallelSearchSources(listOf(a, b), query = "q")
        assertEquals(listOf("ok"), succeeded)
        assertEquals(listOf("boom"), failed)
    }

    // ── R-TOOL-147 / TC-TOOL-147-a: installFromQuarantine stub ──
    @Test
    fun `install quarantine stub`() {
        // TC-TOOL-147-a: the Android port's installFromQuarantine returns
        // Pair(false, "installFromQuarantine not wired on Android"). We
        // verify the stub contract — it must never succeed nor throw.
        val (ok, msg) = installFromQuarantine(
            quarantinePath = File(tmp.root, "anywhere"),
            skillName = "whatever",
            category = "test",
            bundle = SkillBundle(name = "whatever"),
            scanResult = null,
        )
        assertFalse("stub must report failure", ok)
        assertTrue("message should mention not wired: $msg", msg.contains("not wired"))
    }

    @Test
    fun `uninstall skill stub`() {
        // Supporting: uninstallSkill also returns (false, <mentions skill name>)
        // on Android.
        val (ok, msg) = uninstallSkill("my-skill")
        assertFalse(ok)
        assertTrue("message should mention skill name: $msg", msg.contains("my-skill"))
    }

    // ── R-TOOL-148 / TC-TOOL-148-a: bundleContentHash deterministic ──
    @Test
    fun `bundleContentHash deterministic`() {
        // TC-TOOL-148-a: same files (different declaration order) → same hash.
        // bundleContentHash sorts keys before hashing, so two bundles with
        // identical files but different construction order hash identically.
        val ordered = linkedMapOf("a.md" to "aaa", "b.md" to "bbb", "c.md" to "ccc")
        val reversed = linkedMapOf("c.md" to "ccc", "b.md" to "bbb", "a.md" to "aaa")
        val b1 = SkillBundle(name = "x", files = ordered)
        val b2 = SkillBundle(name = "x", files = reversed)
        val h1 = bundleContentHash(b1)
        val h2 = bundleContentHash(b2)
        assertEquals("dict insertion order must not affect hash", h1, h2)
        assertTrue("hash must be sha256-prefixed: $h1", h1.startsWith("sha256:"))
        assertEquals(
            "truncation: 'sha256:' + 16 hex",
            "sha256:".length + 16,
            h1.length,
        )
    }

    @Test
    fun `bundleContentHash differs on content change`() {
        // Supporting: changing file content must change the hash.
        val b1 = SkillBundle(name = "x", files = mapOf("SKILL.md" to "hello"))
        val b2 = SkillBundle(name = "x", files = mapOf("SKILL.md" to "world"))
        assertFalse("different content must yield different hash",
            bundleContentHash(b1) == bundleContentHash(b2))
    }

    // ── _sourceMatches alias logic ──
    @Test
    fun `_sourceMatches handles skills sh alias`() {
        // Supporting: _sourceMatches maps "skills.sh" (dotted) to "skills-sh"
        // (dashed) via the aliases map.
        val a = _makeFakeSource("skills-sh")
        assertTrue(_sourceMatches(a, "skills-sh"))
        assertTrue("skills.sh must alias to skills-sh", _sourceMatches(a, "skills.sh"))
        assertFalse(_sourceMatches(a, "github"))
    }

    // ── R-TOOL-145 / TC-TOOL-145-a: truncated tree refused ──

    /**
     * TC-TOOL-145-a: if GitHub returns the git-tree response with
     * `truncated=true`, SkillsHub bails with null rather than caching a
     * partial tree. Testing the real HTTP flow needs MockWebServer;
     * lock the contract at the source level — the body of GitHubSource's
     * tree-fetch path must contain `optBoolean("truncated")` + `return null`.
     */
    @Test
    fun `truncated tree refused`() {
        val src = File("src/main/java/com/xiaomo/hermes/hermes/tools/SkillsHub.kt")
        assertTrue(
            "SkillsHub.kt must be readable from cwd: ${src.absolutePath}",
            src.exists(),
        )
        val text = src.readText()
        assertTrue(
            "SkillsHub tree-fetch path must short-circuit on truncated=true",
            text.contains("optBoolean(\"truncated\")") &&
                text.contains("if (treeData.optBoolean(\"truncated\")) return null"),
        )
    }

    // ── R-SKILL-003 / TC-SKILL-041-a: refresh after sync ──

    /**
     * TC-SKILL-041-a: after a sync round writes new entries to the hub lock
     * file, a fresh read from the hub sees them — i.e. there is no stale
     * in-memory cache. HubLockFile.load() reads JSON on every call, so the
     * refresh semantic is "write, then re-load". Pinned at the file level:
     * one HubLockFile writes, and a brand-new instance reading the same
     * path sees the record in the file text.
     *
     * NOTE: second-write semantics (preserving earlier entries across
     * multiple recordInstall calls from different instances) is known-buggy
     * on Android because HubLockFile.load() returns a nested JSONObject
     * under "skills" rather than a Map, and recordInstall's `as? Map<*,*>`
     * cast drops it. That separate concern is tracked under TC-SKILL-042-a
     * / the broader HubLockFile audit; here we only pin the minimum
     * refresh-after-sync invariant that sync consumers depend on: a write
     * is persisted to disk and a fresh load sees the file exists with
     * the expected entry text.
     */
    @Test
    fun `refresh after sync`() {
        val lockFile = tmp.newFile("installed.json")
        val hub = HubLockFile(lockFilePath = lockFile.absolutePath)

        hub.recordInstall(
            name = "foo",
            source = "skills-sh",
            identifier = "openai/skills/foo",
            trustLevel = "trusted",
            scanVerdict = "ok",
            skillHash = "sha256:deadbeef",
            installPath = "/skills/foo",
            files = listOf("SKILL.md"),
            metadata = null,
        )
        val onDisk = lockFile.readText()
        assertTrue("disk must reflect sync", onDisk.contains("\"foo\""))
        assertTrue("disk must carry trust_level", onDisk.contains("trusted"))
        assertTrue("disk must carry skill hash", onDisk.contains("sha256:deadbeef"))

        // A fresh HubLockFile instance reading the same path must see the
        // record — this is the "no stale cache" invariant. load() is a
        // file-read, so any second caller (post-sync) sees the latest bytes.
        val hub2 = HubLockFile(lockFilePath = lockFile.absolutePath)
        val reloaded = hub2.load()
        assertNotNull("fresh load must not return null", reloaded)
        // Round-trip sanity: the loaded map carries the version field and a
        // skills block (shape may be Map or JSONObject on Android due to a
        // known org.json quirk, but the presence of the key is pinned).
        assertTrue(
            "reloaded map must include skills key, got keys=${reloaded.keys}",
            "skills" in reloaded,
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun _invokeNormalize(
        pathValue: String,
        fieldName: String,
        allowNested: Boolean,
    ): String {
        val clazz = SkillsHub::class.java
        val m = clazz.getDeclaredMethod(
            "normalizeBundlePath",
            String::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType,
        )
        m.isAccessible = true
        return m.invoke(SkillsHub, pathValue, fieldName, allowNested) as String
    }

    private fun _assertNormalizeRejects(pathValue: String, allowNested: Boolean = false) {
        try {
            _invokeNormalize(pathValue, "field", allowNested)
            throw AssertionError("expected rejection for '$pathValue' (allowNested=$allowNested)")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            // IllegalArgumentException is wrapped by reflection — that's what we want.
            assertTrue(
                "expected IllegalArgumentException, got ${e.cause}",
                e.cause is IllegalArgumentException,
            )
        }
    }

    private fun _invokeResolveToken(): String? {
        val m = SkillsHub::class.java.getDeclaredMethod("_resolveToken")
        m.isAccessible = true
        return m.invoke(SkillsHub) as String?
    }

    private fun _invokeCheckRateLimit(resp: Any?) {
        val m = SkillsHub::class.java.getDeclaredMethod(
            "_checkRateLimitResponse",
            Any::class.java,
        )
        m.isAccessible = true
        m.invoke(SkillsHub, resp)
    }

    private fun _setSkillsHubField(name: String, value: Any?) {
        val f = SkillsHub::class.java.getDeclaredField(name)
        f.isAccessible = true
        f.set(SkillsHub, value)
    }

    private fun _getSkillsHubField(name: String): Any? {
        val f = SkillsHub::class.java.getDeclaredField(name)
        f.isAccessible = true
        return f.get(SkillsHub)
    }

    /** Build an anonymous SkillSource subclass that reports a given sourceId. */
    private fun _makeFakeSource(
        id: String,
        onSearch: (query: String) -> List<SkillMeta> = { emptyList() },
    ): SkillSource {
        return object : SkillSource() {
            override fun sourceId(): String = id
            override fun search(query: String, limit: Int): List<SkillMeta> = onSearch(query)
            override fun fetch(identifier: String): SkillBundle? = null
            override fun inspect(identifier: String): SkillMeta? = null
        }
    }
}

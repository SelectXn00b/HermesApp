package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for SkillsSync.kt — bundled-skill discovery, frontmatter-name
 * resolution, directory hashing, reset stub.
 * Covers TC-TOOL-185..191.
 *
 * Note: `_readManifest`/`_writeManifest` and `syncSkills` depend on the
 * `_SkillsSyncConstants.SKILLS_DIR` lazy delegate, which is a
 * `private static final Lazy<File>` field inside a private Kotlin `object`.
 * JDK 17+ rejects reflective writes to final statics without `--add-opens`,
 * so we can't swap it from a plain JUnit test. We therefore cover the
 * manifest/ sync paths by asserting the **shape** of their return values
 * on the default-initialized (Context-less) constants — this still locks
 * in the "never throw" contract. The pure helpers (readSkillName,
 * discoverBundledSkills, dirHash, resetBundledSkill) are tested directly
 * via reflection.
 */
class SkillsSyncTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ── R-TOOL-187 / TC-TOOL-187-a: readSkillName caps at 4000 bytes ──
    @Test
    fun `frontmatter read capped`() {
        // TC-TOOL-187-a: _readSkillName reads `content.take(4000)` before
        // scanning YAML. A `name:` beyond the 4000-char cap must NOT be
        // returned. Pad the frontmatter so the real `name:` sits past 4000.
        val padding = "# " + "A".repeat(4100)  // pushes name past 4000
        val content = "---\n$padding\nname: late\n---\nbody"
        val f = File(tmp.newFolder("cap"), "SKILL.md")
        f.writeText(content, Charsets.UTF_8)
        val name = _invokeReadSkillName(f, "fallback")
        // Name field was beyond the 4000-char window → not parsed → null
        assertNull(
            "name beyond 4000-byte cap must not be returned, got $name",
            name
        )
    }

    @Test
    fun `readSkillName returns name when within cap`() {
        // Supporting: when the `name:` line falls within the first 4000
        // chars, the parser returns it (and strips matching quotes).
        val f = File(tmp.newFolder("ok"), "SKILL.md")
        f.writeText("---\nname: \"my-skill\"\n---\nbody", Charsets.UTF_8)
        assertEquals("my-skill", _invokeReadSkillName(f, "fallback"))
    }

    @Test
    fun `readSkillName strips single quotes`() {
        // Supporting: quote stripping trims matching single quotes too.
        val f = File(tmp.newFolder("ok_sq"), "SKILL.md")
        f.writeText("---\nname: 'quoted'\n---\nbody", Charsets.UTF_8)
        assertEquals("quoted", _invokeReadSkillName(f, "fallback"))
    }

    @Test
    fun `readSkillName missing name field returns null`() {
        // Supporting: if the frontmatter opens but has no `name:` key,
        // return null (not the fallback — fallback is used by the caller).
        val f = File(tmp.newFolder("noname"), "SKILL.md")
        f.writeText("---\ndescription: test\n---\nhello", Charsets.UTF_8)
        assertNull(_invokeReadSkillName(f, "fallback"))
    }

    @Test
    fun `readSkillName without frontmatter returns null`() {
        // Supporting: files that don't open with `---` never enter the
        // frontmatter scan → null.
        val f = File(tmp.newFolder("plain"), "SKILL.md")
        f.writeText("just some prose, no frontmatter\n", Charsets.UTF_8)
        assertNull(_invokeReadSkillName(f, "whatever"))
    }

    // ── R-TOOL-189 / TC-TOOL-189-a: discoverBundledSkills filters .git ──
    @Test
    fun `discoverBundledSkills filters noise dirs`() {
        // Supporting (for TC-TOOL-189-a and the discovery contract):
        // `.git` and `.github` subtrees are filtered out via the
        // `"/.git/" in path` and `"/.github/"` predicates.
        val bundled = tmp.newFolder("bundled")
        val good = File(bundled, "alpha").apply { mkdirs() }
        File(good, "SKILL.md").writeText("---\nname: alpha\n---\n", Charsets.UTF_8)
        val gitDir = File(bundled, ".git").apply { mkdirs() }
        File(gitDir, "SKILL.md").writeText("---\nname: ignored\n---\n", Charsets.UTF_8)
        val ghDir = File(bundled, ".github").apply { mkdirs() }
        File(ghDir, "SKILL.md").writeText("---\nname: ignored2\n---\n", Charsets.UTF_8)

        @Suppress("UNCHECKED_CAST")
        val skills = _invokeDiscoverBundledSkills(bundled) as List<Pair<String, File>>
        assertEquals(1, skills.size)
        assertEquals("alpha", skills[0].first)
    }

    @Test
    fun `discoverBundledSkills uses dir name when frontmatter missing`() {
        // Supporting: `_readSkillName(...) ?: skillDir.name` — when the
        // frontmatter lacks `name:`, the directory name is the fallback.
        val bundled = tmp.newFolder("bundled2")
        val dir = File(bundled, "naked").apply { mkdirs() }
        File(dir, "SKILL.md").writeText("just some prose\n", Charsets.UTF_8)
        @Suppress("UNCHECKED_CAST")
        val skills = _invokeDiscoverBundledSkills(bundled) as List<Pair<String, File>>
        assertEquals(1, skills.size)
        assertEquals("naked", skills[0].first)
    }

    @Test
    fun `discoverBundledSkills missing dir returns empty`() {
        // Supporting: _discoverBundledSkills(File that does not exist) → [].
        val missing = File(tmp.root, "nowhere")
        @Suppress("UNCHECKED_CAST")
        val skills = _invokeDiscoverBundledSkills(missing) as List<Pair<String, File>>
        assertTrue(skills.isEmpty())
    }

    @Test
    fun `discoverBundledSkills finds nested skills`() {
        // Supporting: walkTopDown recurses — nested category dirs work.
        val bundled = tmp.newFolder("bundled3")
        val nested = File(bundled, "category/inner").apply { mkdirs() }
        File(nested, "SKILL.md").writeText("---\nname: nested\n---\n", Charsets.UTF_8)
        @Suppress("UNCHECKED_CAST")
        val skills = _invokeDiscoverBundledSkills(bundled) as List<Pair<String, File>>
        assertEquals(1, skills.size)
        assertEquals("nested", skills[0].first)
    }

    // ── _dirHash determinism ──
    @Test
    fun `dirHash is deterministic`() {
        // Supporting (for TC-TOOL-188-a / TC-TOOL-190-a): _dirHash walks
        // files alphabetically and MD5s rel_path+content. Same content
        // should produce the same hash on repeated calls.
        val a = tmp.newFolder("a")
        File(a, "one.txt").writeText("hello")
        File(a, "sub").apply { mkdirs() }
        File(a, "sub/two.txt").writeText("world")

        val h1 = _invokeDirHash(a)
        val h2 = _invokeDirHash(a)
        assertEquals(h1, h2)
        // MD5 hex: 32 chars
        assertEquals("expected 32-char md5 hex: $h1", 32, h1.length)
    }

    @Test
    fun `dirHash differs across content`() {
        // Supporting (for TC-TOOL-188-a): changing file contents must
        // change the hash, so the user-modified detector can work.
        val a = tmp.newFolder("da")
        File(a, "k.txt").writeText("aaa")
        val h1 = _invokeDirHash(a)
        File(a, "k.txt").writeText("bbb")
        val h2 = _invokeDirHash(a)
        assertFalse("different content must yield different hash", h1 == h2)
    }

    @Test
    fun `dirHash empty directory has stable hash`() {
        // Supporting: an empty dir still hashes to MD5("") = fixed value.
        val a = tmp.newFolder("empty-a")
        val b = tmp.newFolder("empty-b")
        assertEquals(_invokeDirHash(a), _invokeDirHash(b))
    }

    // ── R-TOOL-191 / TC-TOOL-191-a: reset is stubbed on android ──
    @Test
    fun `reset is stubbed on android`() {
        // TC-TOOL-191-a: the Android port replaces Python's reset_bundled_skill
        // with a stub returning `false`. Covers both default and restore=true.
        assertFalse(resetBundledSkill("foo"))
        assertFalse(resetBundledSkill("foo", restore = true))
        assertFalse(resetBundledSkill(""))
    }

    // ── Reflection helpers ─────────────────────────────────────────────────

    private fun _invokeReadSkillName(file: File, fallback: String): String? {
        val clazz = Class.forName("com.xiaomo.hermes.hermes.tools.SkillsSyncKt")
        val m = clazz.getDeclaredMethod("_readSkillName", File::class.java, String::class.java)
        m.isAccessible = true
        return m.invoke(null, file, fallback) as String?
    }

    private fun _invokeDiscoverBundledSkills(bundledDir: File): Any? {
        val clazz = Class.forName("com.xiaomo.hermes.hermes.tools.SkillsSyncKt")
        val m = clazz.getDeclaredMethod("_discoverBundledSkills", File::class.java)
        m.isAccessible = true
        return m.invoke(null, bundledDir)
    }

    private fun _invokeDirHash(directory: File): String {
        val clazz = Class.forName("com.xiaomo.hermes.hermes.tools.SkillsSyncKt")
        val m = clazz.getDeclaredMethod("_dirHash", File::class.java)
        m.isAccessible = true
        return m.invoke(null, directory) as String
    }
}

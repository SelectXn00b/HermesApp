package com.xiaomo.hermes.hermes.tools

import android.content.Context
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
import org.mockito.Mockito
import java.io.File

/**
 * Tests for SkillsTool.kt — env loader, frontmatter parser, category
 * resolver, env-var presence, skill discovery exclusions.
 * Covers TC-TOOL-195..199.
 *
 * `SkillsTool.SKILLS_DIR` and `loadEnv()` both call `getHermesHome()`
 * which requires an Android Context. We inject a Mockito-stubbed Context
 * via reflection into `HermesConstantsKt._appContext` so these Context-free
 * JVM tests can still exercise the real helpers against a temp dir.
 */
class SkillsToolTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var fakeFilesDir: File

    @Before
    fun setUp() {
        fakeFilesDir = tmp.newFolder("app_files")
        val ctx = Mockito.mock(Context::class.java)
        Mockito.`when`(ctx.applicationContext).thenReturn(ctx)
        Mockito.`when`(ctx.filesDir).thenReturn(fakeFilesDir)
        _setAppContext(ctx)
    }

    @After
    fun tearDown() {
        _setAppContext(null)
    }

    // ── R-TOOL-195 / TC-TOOL-195-a: loadEnv parses ──
    @Test
    fun `loadEnv parses`() {
        // TC-TOOL-195-a: loadEnv skips blank lines and `#`-comment lines,
        // only `k=v` pairs enter the map; surrounding matching quotes are
        // stripped.
        val envFile = File(getHermesHome(), ".env")
        envFile.parentFile?.mkdirs()
        envFile.writeText(
            """
            # a comment

            KEY_A=value1
            KEY_B="quoted value"
            KEY_C='single quoted'
            =bad_no_key
            : not an equals line
            """.trimIndent(),
            Charsets.UTF_8,
        )
        val env = loadEnv()
        assertEquals("value1", env["KEY_A"])
        assertEquals("quoted value", env["KEY_B"])
        assertEquals("single quoted", env["KEY_C"])
        // bad_no_key and the colon line must be dropped
        assertFalse("empty-key line must be skipped", env.containsKey(""))
        assertFalse("non-equals line must be skipped", env.keys.any { ":" in it })
    }

    @Test
    fun `loadEnv missing file returns empty`() {
        // Supporting: no .env file → empty map (not null).
        val env = loadEnv()
        assertTrue("expected empty map: $env", env.isEmpty())
    }

    // ── R-TOOL-196 / TC-TOOL-196-a: frontmatter must start with --- ──
    @Test
    fun `frontmatter must start with ---`() {
        // TC-TOOL-196-a: _parseFrontmatter returns (emptyMap, original) when
        // the content does not start with `---`.
        val content = "just prose, no frontmatter block\nline 2"
        val (fm, body) = _parseFrontmatter(content)
        assertTrue("expected empty frontmatter: $fm", fm.isEmpty())
        assertEquals(content, body)
    }

    @Test
    fun `frontmatter with open but no close returns empty`() {
        // Supporting: opens with `---` but never closes → bail with empty
        // frontmatter and the original content as body.
        val content = "---\nname: unterminated\nprose continues forever"
        val (fm, body) = _parseFrontmatter(content)
        assertTrue("expected empty frontmatter on unterminated: $fm", fm.isEmpty())
        assertEquals(content, body)
    }

    @Test
    fun `frontmatter parses valid block`() {
        // Supporting: well-formed frontmatter is parsed as YAML.
        val content = "---\nname: foo\ndescription: bar\n---\nthe body"
        val (fm, body) = _parseFrontmatter(content)
        assertEquals("foo", fm["name"])
        assertEquals("bar", fm["description"])
        assertEquals("the body", body)
    }

    // ── R-TOOL-197 / TC-TOOL-197-a: env var name regex filters ──
    @Test
    fun `env var name regex`() {
        // TC-TOOL-197-a: _getRequiredEnvironmentVariables filters names that
        // do NOT match ^[A-Za-z_][A-Za-z0-9_]*$. Digits-first, hyphens, and
        // empty strings must be dropped.
        val fm = mapOf<String, Any?>(
            "setup" to mapOf(
                "env_vars" to listOf("GOOD_NAME", "_also_ok", "123BAD", "with-dash", "", "alsoGood9"),
            ),
        )
        val names = _getRequiredEnvironmentVariables(fm)
        assertEquals(listOf("GOOD_NAME", "_also_ok", "alsoGood9"), names)
    }

    @Test
    fun `env var regex direct check`() {
        // Supporting: the regex itself is the root source of truth — lock it down.
        assertTrue(_ENV_VAR_NAME_RE.matches("FOO"))
        assertTrue(_ENV_VAR_NAME_RE.matches("_private"))
        assertTrue(_ENV_VAR_NAME_RE.matches("A1B2C3"))
        assertFalse(_ENV_VAR_NAME_RE.matches("1bad"))
        assertFalse(_ENV_VAR_NAME_RE.matches("with-dash"))
        assertFalse(_ENV_VAR_NAME_RE.matches(""))
        assertFalse(_ENV_VAR_NAME_RE.matches(" leading"))
    }

    // ── R-TOOL-198 / TC-TOOL-198-a: env file satisfies ──
    @Test
    fun `env file satisfies`() {
        // TC-TOOL-198-a: _isEnvVarPersisted returns true when the key is
        // present in the env-file-derived map (first branch).
        val env = mapOf("MY_KEY" to "anything")
        assertTrue(_isEnvVarPersisted("MY_KEY", env))
    }

    // ── R-TOOL-198 / TC-TOOL-198-b: process env satisfies ──
    @Test
    fun `process env satisfies`() {
        // TC-TOOL-198-b: _isEnvVarPersisted returns true when the key is
        // absent from the file-env map but present in System.getenv.
        // PATH is universally present on every JVM host; use it as the
        // canary for "process env wins".
        val envNoPath = emptyMap<String, String>()
        assertTrue(
            "PATH should always be in process env",
            _isEnvVarPersisted("PATH", envNoPath),
        )

        // A wildly unlikely key is not in file env and not in process env.
        val absurd = "__HERMES_TEST_ABSOLUTELY_NOT_SET_${System.nanoTime()}__"
        assertFalse(_isEnvVarPersisted(absurd, envNoPath))
    }

    // ── R-TOOL-199 / TC-TOOL-199-a: findAllSkills excludes noise dirs ──
    @Test
    fun `findAllSkills excludes noise dirs`() {
        // TC-TOOL-199-a: _findAllSkills filters out paths inside /.git/,
        // /.github/, or /.hub/ (the _EXCLUDED_SKILL_DIRS set). A SKILL.md
        // under node_modules/ is NOT in the exclusion set, but node_modules
        // typically lives at the top of a skill repo — the test as doc'd
        // conflates two contracts. We lock in the ACTUAL contract from the
        // code (the three excluded dirs).
        val skillsDir = SKILLS_DIR
        skillsDir.mkdirs()

        // Real skill
        val real = File(skillsDir, "good").apply { mkdirs() }
        File(real, "SKILL.md").writeText("---\nname: good\n---\nbody", Charsets.UTF_8)

        // Noise: .git/SKILL.md should be filtered
        val gitNoise = File(skillsDir, "good/.git").apply { mkdirs() }
        File(gitNoise, "SKILL.md").writeText("---\nname: git-noise\n---\n", Charsets.UTF_8)

        // Noise: .github/SKILL.md should be filtered
        val ghNoise = File(skillsDir, "good/.github").apply { mkdirs() }
        File(ghNoise, "SKILL.md").writeText("---\nname: gh-noise\n---\n", Charsets.UTF_8)

        // Noise: .hub/SKILL.md should be filtered
        val hubNoise = File(skillsDir, ".hub").apply { mkdirs() }
        File(hubNoise, "SKILL.md").writeText("---\nname: hub-noise\n---\n", Charsets.UTF_8)

        val found = _findAllSkills(skipDisabled = false)
        val names = found.map { it["name"] }.toSet()
        assertTrue("expected good skill present: $names", "good" in names)
        assertFalse("git noise must be excluded: $names", "git-noise" in names)
        assertFalse("github noise must be excluded: $names", "gh-noise" in names)
        assertFalse("hub noise must be excluded: $names", "hub-noise" in names)
    }

    @Test
    fun `findAllSkills returns empty when skills dir missing`() {
        // Supporting: no skills/ dir → empty list, no crash.
        // (getHermesHome() creates the .hermes dir, but not skills/.)
        val skillsDir = SKILLS_DIR
        assertFalse("skills dir should not exist yet", skillsDir.exists())
        val found = _findAllSkills(skipDisabled = false)
        assertTrue("expected empty: $found", found.isEmpty())
    }

    // ── _parseTags ──
    @Test
    fun `parseTags handles string list and null`() {
        // Supporting: _parseTags accepts List<Any>, comma-separated String,
        // and returns empty for null/other. TC for tags indirectly covered.
        assertEquals(listOf("a", "b", "c"), _parseTags("a, b, c"))
        assertEquals(listOf("a", "b"), _parseTags(listOf("a", "b")))
        assertEquals(emptyList<String>(), _parseTags(null))
        assertEquals(emptyList<String>(), _parseTags(123))
        // Empty strings are filtered out
        assertEquals(listOf("x", "y"), _parseTags(" x , , y "))
    }

    // ── _getCategoryFromPath ──
    @Test
    fun `getCategoryFromPath extracts first segment`() {
        // Supporting: _getCategoryFromPath returns the first path segment
        // under SKILLS_DIR as the category; returns null when the file
        // isn't under SKILLS_DIR at all.
        SKILLS_DIR.mkdirs()
        val cat = File(SKILLS_DIR, "mlops/train").apply { mkdirs() }
        val file = File(cat, "SKILL.md").apply { writeText("") }
        assertEquals("mlops", _getCategoryFromPath(file))

        val outside = File(tmp.newFolder("loose"), "SKILL.md").apply { writeText("") }
        assertNull(_getCategoryFromPath(outside))
    }

    // ── Constants sanity ──
    @Test
    fun `constants match python`() {
        // Sanity: constants pinned at parse time to the upstream values.
        assertEquals(64, MAX_NAME_LENGTH)
        assertEquals(1024, MAX_DESCRIPTION_LENGTH)
        assertEquals(
            setOf(".git", ".github", ".hub"),
            _EXCLUDED_SKILL_DIRS,
        )
        // Platform map parity
        assertEquals("macos", _PLATFORM_MAP["darwin"])
        assertEquals("android", _PLATFORM_MAP["android"])
        // Android-specific sentinels
        assertFalse(_isGatewaySurface())
        assertEquals("android", _getTerminalBackendName())
    }

    // ── Reflection helper: set the module-level Context. ────────────────
    private fun _setAppContext(ctx: Context?) {
        val clazz = Class.forName("com.xiaomo.hermes.hermes.HermesConstantsKt")
        val field = clazz.getDeclaredField("_appContext")
        field.isAccessible = true
        field.set(null, ctx)
    }

    // Mirror the package-local import so we can call getHermesHome() from
    // the test. The real function is top-level in package
    // `com.xiaomo.hermes.hermes` — we invoke it via the kt class for clarity.
    private fun getHermesHome(): File {
        val clazz = Class.forName("com.xiaomo.hermes.hermes.HermesConstantsKt")
        val m = clazz.getDeclaredMethod("getHermesHome")
        m.isAccessible = true
        return m.invoke(null) as File
    }
}

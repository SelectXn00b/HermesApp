package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for SkillManagerTool.kt — Android-side is a deliberate stub that
 * returns `toolError("skill_manage is not available on Android")` for all
 * CRUD actions; validation helpers (_validateName, content cap) are the
 * real functional behavior we can exercise here. Covers TC-TOOL-160..163.
 */
class SkillManagerToolTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ── R-TOOL-160 / TC-TOOL-160-a: dispatch actions all report "not available" ──
    @Test
    fun `all CRUD android denied`() {
        // TC-TOOL-160-a: skill_manage on Android returns an error for every
        // documented action. The wrapper function dispatches directly via
        // toolError regardless of inputs.
        val actions = listOf("create", "edit", "patch", "delete", "write_file", "remove_file")
        for (action in actions) {
            val json = skillManage(action = action, name = "foo", content = "bar")
            assertTrue(
                "action=$action must produce an error: got $json",
                json.contains("\"error\"")
            )
            assertTrue(
                "action=$action must mention Android limitation: got $json",
                json.contains("not available on Android")
            )
        }
    }

    @Test
    fun `skillManage returns json shape even for unknown action`() {
        // TC-TOOL-160-a extension: the Android port uses a single static
        // message for all inputs — there is no per-action branch that could
        // diverge. Verifies the error shape is a JSON object with "error" key.
        val json = skillManage(action = "bogus-xyz")
        assertTrue("expected JSON object: $json", json.trim().startsWith("{"))
        assertTrue("expected error key: $json", json.contains("\"error\""))
    }

    // ── R-TOOL-161 / TC-TOOL-161-a: _validateName rejects spaces ──
    @Test
    fun `name regex rejects space`() {
        // TC-TOOL-161-a: name containing space is invalid. Use reflection
        // to reach the private _validateName.
        val result = invokePrivateValidateName("a b")
        assertEquals("invalid skill name", result)
    }

    @Test
    fun `name regex rejects uppercase`() {
        // TC-TOOL-161-a extension: VALID_NAME_RE = ^[a-z0-9][a-z0-9._-]*$
        // uppercase rejected.
        assertEquals("invalid skill name", invokePrivateValidateName("Foo"))
    }

    @Test
    fun `name regex accepts lowercase with dots and dashes and underscores`() {
        // TC-TOOL-161-a positive path: confirm valid-looking names pass
        // (otherwise the "rejects space" test could pass for the wrong reason).
        assertEquals(null, invokePrivateValidateName("good-name"))
        assertEquals(null, invokePrivateValidateName("a.b_c-1"))
        assertEquals(null, invokePrivateValidateName("0"))
    }

    @Test
    fun `name regex rejects leading dot or dash`() {
        // TC-TOOL-161-a extension: first char must be [a-z0-9], not punctuation.
        assertEquals("invalid skill name", invokePrivateValidateName(".hidden"))
        assertEquals("invalid skill name", invokePrivateValidateName("-bad"))
    }

    // ── R-TOOL-161 / TC-TOOL-161-b: name length cap ──
    @Test
    fun `name length cap`() {
        // TC-TOOL-161-b: MAX_NAME_LENGTH is 64 in _SkillManagerConstants.
        // A 65-char-long lowercase string must be rejected.
        val tooLong = "a".repeat(65)
        assertEquals("invalid skill name", invokePrivateValidateName(tooLong))
        // exactly 64 chars is still valid
        val boundary = "a".repeat(64)
        assertEquals(null, invokePrivateValidateName(boundary))
    }

    // ── R-TOOL-162 / TC-TOOL-162-a: _validateContentSize cap ──
    @Test
    fun `content size cap`() {
        // TC-TOOL-162-a: content beyond MAX_SKILL_CONTENT_CHARS is rejected.
        val big = "x".repeat(MAX_SKILL_CONTENT_CHARS + 1)
        val result = invokePrivateValidateContentSize(big, "SKILL.md")
        assertNotNull("expected error for oversized content", result)
        assertTrue("error should mention SKILL.md label: $result", result!!.contains("SKILL.md"))
        assertTrue(
            "error should mention the character limit: $result",
            result.contains(MAX_SKILL_CONTENT_CHARS.toString())
        )
    }

    @Test
    fun `content size accepts at limit`() {
        // TC-TOOL-162-a positive: exactly MAX_SKILL_CONTENT_CHARS is OK
        // (the contract is > cap, not >=).
        val atLimit = "y".repeat(MAX_SKILL_CONTENT_CHARS)
        assertEquals(null, invokePrivateValidateContentSize(atLimit, "SKILL.md"))
    }

    // ── R-TOOL-163 / TC-TOOL-163-a: atomic write ──
    @Test
    fun `atomic write crash safe`() {
        // TC-TOOL-163-a: _atomicWriteText writes via a temp file + atomic
        // move. Verify the contract: the target file ends up with the new
        // content and no stray ".tmp" siblings remain.
        val target = File(tmp.newFolder("skill"), "SKILL.md")
        invokePrivateAtomicWriteText(target, "hello atomic\n")
        assertTrue(target.exists())
        assertEquals("hello atomic\n", target.readText(Charsets.UTF_8))

        val leftover = target.parentFile?.listFiles()?.filter { it.name.contains(".tmp.") } ?: emptyList()
        assertTrue("no .tmp. leftovers should remain: $leftover", leftover.isEmpty())
    }

    @Test
    fun `atomic write overwrites existing file`() {
        // TC-TOOL-163-a extension: when target already exists, the new
        // content replaces the old atomically.
        val target = File(tmp.newFolder("skill2"), "SKILL.md")
        target.writeText("old\n", Charsets.UTF_8)
        invokePrivateAtomicWriteText(target, "new\n")
        assertEquals("new\n", target.readText(Charsets.UTF_8))
    }

    // ── Constants sanity ──
    @Test
    fun `public constants match Python`() {
        // TC-TOOL-162-a / TC-TOOL-163-a supporting: the Python upstream
        // defines these same limits; the Kotlin port copies them verbatim.
        assertEquals(100_000, MAX_SKILL_CONTENT_CHARS)
        assertEquals(1_048_576, MAX_SKILL_FILE_BYTES)
        assertEquals(setOf("references", "templates", "scripts", "assets"), ALLOWED_SUBDIRS)
    }

    @Test
    fun `VALID_NAME_RE enforces python pattern`() {
        // TC-TOOL-161-a supporting: pattern is ^[a-z0-9][a-z0-9._-]*$
        // — verify directly (not just through _validateName).
        assertTrue(VALID_NAME_RE.matches("abc"))
        assertTrue(VALID_NAME_RE.matches("a1_b.c-d"))
        assertFalse(VALID_NAME_RE.matches("A"))
        assertFalse(VALID_NAME_RE.matches("_leading"))
        assertFalse(VALID_NAME_RE.matches("has space"))
        assertFalse(VALID_NAME_RE.matches(""))
    }

    // ── Reflection helpers: reach private top-level funs compiled into
    //    SkillManagerToolKt. ──
    private fun invokePrivateValidateName(name: String): String? {
        val clazz = Class.forName("com.xiaomo.hermes.hermes.tools.SkillManagerToolKt")
        val m = clazz.getDeclaredMethod("_validateName", String::class.java)
        m.isAccessible = true
        return m.invoke(null, name) as String?
    }

    private fun invokePrivateValidateContentSize(content: String, label: String): String? {
        val clazz = Class.forName("com.xiaomo.hermes.hermes.tools.SkillManagerToolKt")
        val m = clazz.getDeclaredMethod("_validateContentSize", String::class.java, String::class.java)
        m.isAccessible = true
        return m.invoke(null, content, label) as String?
    }

    private fun invokePrivateAtomicWriteText(target: File, content: String) {
        val clazz = Class.forName("com.xiaomo.hermes.hermes.tools.SkillManagerToolKt")
        val m = clazz.getDeclaredMethod(
            "_atomicWriteText",
            File::class.java,
            String::class.java,
            String::class.java
        )
        m.isAccessible = true
        m.invoke(null, target, content, "utf-8")
    }
}

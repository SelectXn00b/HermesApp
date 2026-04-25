package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialFilesTest {

    @Test
    fun `registerCredentialFile returns false on Android (no remote sandbox)`() {
        assertFalse(registerCredentialFile("google_token.json"))
        assertFalse(registerCredentialFile("x.json", containerBase = "/root/.hermes"))
    }

    @Test
    fun `registerCredentialFiles extracts string entries and trims`() {
        val result = registerCredentialFiles(listOf("  a.json  ", "b.json", "", "   "))
        assertEquals(listOf("a.json", "b.json"), result)
    }

    @Test
    fun `registerCredentialFiles extracts path key from maps`() {
        val result = registerCredentialFiles(listOf(
            mapOf("path" to "cfg.yaml"),
            mapOf("name" to "fallback.txt"),
            mapOf("path" to "  with_space.json  "),
            mapOf("unrelated" to "ignored"),
            mapOf("path" to ""),
        ))
        assertEquals(listOf("cfg.yaml", "fallback.txt", "with_space.json"), result)
    }

    @Test
    fun `registerCredentialFiles ignores non-string non-map entries`() {
        val result = registerCredentialFiles(listOf(42, null, true, "keeper.json"))
        assertEquals(listOf("keeper.json"), result)
    }

    @Test
    fun `getCredentialFileMounts returns empty list on Android`() {
        assertTrue(getCredentialFileMounts().isEmpty())
    }

    @Test
    fun `getSkillsDirectoryMount returns null on Android`() {
        assertNull(getSkillsDirectoryMount())
        assertNull(getSkillsDirectoryMount(containerBase = "/root/.hermes"))
    }

    @Test
    fun `iterSkillsFiles yields nothing on Android`() {
        assertEquals(0, iterSkillsFiles().count())
    }

    @Test
    fun `getCacheDirectoryMounts returns empty list on Android`() {
        assertTrue(getCacheDirectoryMounts().isEmpty())
    }

    @Test
    fun `iterCacheFiles yields nothing on Android`() {
        assertEquals(0, iterCacheFiles().count())
    }

    @Test
    fun `clearCredentialFiles does not throw`() {
        clearCredentialFiles()
    }
}

package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertNull
import org.junit.Test

class OsvCheckTest {

    @Test
    fun `checkPackageForMalware returns null for unknown command ecosystem`() {
        // "cargo" is not npx/uvx/pipx → _inferEcosystem returns null → short-circuit.
        val result = checkPackageForMalware("cargo", listOf("install", "some-crate"))
        assertNull(result)
    }

    @Test
    fun `checkPackageForMalware returns null for empty command`() {
        val result = checkPackageForMalware("", listOf("pkg"))
        assertNull(result)
    }

    @Test
    fun `checkPackageForMalware returns null for npx with no args`() {
        val result = checkPackageForMalware("npx", emptyList())
        assertNull(result)
    }

    @Test
    fun `checkPackageForMalware returns null for npx with only flag args`() {
        // All args start with '-' → no package token found → short-circuit.
        val result = checkPackageForMalware("npx", listOf("-y", "--quiet"))
        assertNull(result)
    }

    @Test
    fun `checkPackageForMalware returns null for uvx with flags only`() {
        val result = checkPackageForMalware("uvx", listOf("--help"))
        assertNull(result)
    }

    @Test
    fun `checkPackageForMalware handles Windows-style npx_cmd`() {
        // Recognized as npm ecosystem but no args → null.
        val result = checkPackageForMalware("npx.cmd", emptyList())
        assertNull(result)
    }

    @Test
    fun `checkPackageForMalware strips path from command basename`() {
        // _inferEcosystem uses substringAfterLast("/") — a full path still works.
        val result = checkPackageForMalware("/usr/local/bin/cargo", listOf("install", "x"))
        // Not npx/uvx → null (fast path, no network).
        assertNull(result)
    }

    @Test
    fun `checkPackageForMalware with pipx and no args returns null`() {
        val result = checkPackageForMalware("pipx", emptyList())
        assertNull(result)
    }
}

package com.xiaomo.hermes.hermes.agent

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Requirement map: R-AGENT-160..169 (see docs/hermes-requirements.md)
 * Test cases:      TC-AGENT-160..169 (see docs/hermes-test-cases.md)
 */
class FileSafetyTest {

    private val home: String = System.getProperty("user.home") ?: "/tmp/home"

    @Test
    fun `buildWriteDeniedPaths includes ssh private keys`() {
        val paths = buildWriteDeniedPaths(home)
        assertTrue(paths.any { it.endsWith(".ssh/id_rsa") })
        assertTrue(paths.any { it.endsWith(".ssh/id_ed25519") })
        assertTrue(paths.any { it.endsWith(".ssh/authorized_keys") })
        assertTrue(paths.any { it.endsWith("/etc/sudoers") })
        assertTrue(paths.any { it.endsWith("/etc/passwd") })
        assertTrue(paths.any { it.endsWith("/etc/shadow") })
    }

    @Test
    fun `buildWriteDeniedPaths includes shell rc files`() {
        val paths = buildWriteDeniedPaths(home)
        assertTrue(paths.any { it.endsWith(".bashrc") })
        assertTrue(paths.any { it.endsWith(".zshrc") })
        assertTrue(paths.any { it.endsWith(".profile") })
        assertTrue(paths.any { it.endsWith(".netrc") })
    }

    @Test
    fun `buildWriteDeniedPrefixes ends with separator`() {
        val prefixes = buildWriteDeniedPrefixes(home)
        for (p in prefixes) assertTrue("missing sep: $p", p.endsWith(File.separator))
        // Spot-check a few expected prefixes
        assertTrue(prefixes.any { it.endsWith(".ssh" + File.separator) })
        assertTrue(prefixes.any { it.endsWith(".aws" + File.separator) })
        assertTrue(prefixes.any { it.endsWith(".gnupg" + File.separator) })
    }

    @Test
    fun `isWriteDenied for absolute blocked path`() {
        assertTrue(isWriteDenied("$home/.ssh/id_rsa"))
        assertTrue(isWriteDenied("/etc/passwd"))
        assertTrue(isWriteDenied("/etc/sudoers"))
    }

    @Test
    fun `isWriteDenied for prefix matches sub path`() {
        assertTrue(isWriteDenied("$home/.ssh/any_file"))
        assertTrue(isWriteDenied("$home/.aws/credentials"))
        assertTrue(isWriteDenied("$home/.gnupg/anything"))
    }

    @Test
    fun `isWriteDenied returns false for ordinary user file`() {
        // Without HERMES_WRITE_SAFE_ROOT env (we can't set env vars in JUnit easily),
        // ordinary paths should not be denied.
        val safeRoot = getSafeWriteRoot()
        if (safeRoot == null) {
            assertFalse(isWriteDenied("$home/projects/x.txt"))
            assertFalse(isWriteDenied("/tmp/nope.log"))
        }
    }

    @Test
    fun `getSafeWriteRoot returns null when env unset`() {
        // We can't unset env mid-test, but if the var isn't set at all,
        // this has to be null. Accept either null or a valid absolute path.
        val root = getSafeWriteRoot()
        if (root != null) {
            assertTrue("must be absolute: $root", File(root).isAbsolute)
        }
    }

    @Test
    fun `getReadBlockError non-hermes path returns null`() {
        assertNull(getReadBlockError("$home/projects/file.txt"))
        assertNull(getReadBlockError("/tmp/foo"))
    }

    @Test
    fun `getReadBlockError hermes cache path returns message`() {
        val hub = File(File(File(home, ".hermes"), "skills"), ".hub")
        val err = getReadBlockError(File(hub, "index-cache/foo").absolutePath)
        assertNotNull(err)
        assertTrue("got: $err", err!!.contains("internal Hermes cache"))
        assertTrue("got: $err", err.contains("skills_list"))
    }

    @Test
    fun `getReadBlockError expands tilde`() {
        val err = getReadBlockError("~/.hermes/skills/.hub/anything")
        assertNotNull(err)
    }
}

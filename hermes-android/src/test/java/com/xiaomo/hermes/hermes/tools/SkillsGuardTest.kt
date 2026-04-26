package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/**
 * Tests for SkillsGuard.kt — regex threat scanner, trust level
 * resolution, verdict determination, install policy decision matrix.
 * Covers TC-TOOL-170..178.
 */
class SkillsGuardTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ── R-TOOL-170 / TC-TOOL-170-a: scanFile accepts .md ──
    @Test
    fun `scan accepts md`() {
        // TC-TOOL-170-a: Markdown files are in SCANNABLE_EXTENSIONS and
        // should be scanned. Seed a line that trips one of the patterns
        // (sudo — privilege_escalation:high) and verify a finding lands.
        val f = tmp.newFile("evil.md")
        f.writeText("apt-get install sudo\n", Charsets.UTF_8)
        val findings = scanFile(f)
        assertTrue("expected at least one finding on md with 'sudo'", findings.isNotEmpty())
        assertTrue(
            "expected sudo_usage finding: got ${findings.map { it.patternId }}",
            findings.any { it.patternId == "sudo_usage" }
        )
    }

    // ── R-TOOL-170 / TC-TOOL-170-b: scanFile skips binary ──
    @Test
    fun `scan rejects binary`() {
        // TC-TOOL-170-b: .zip is not in SCANNABLE_EXTENSIONS, so scan_file
        // bails out with an empty list regardless of content.
        val f = tmp.newFile("evil.zip")
        f.writeText("sudo rm -rf /\n", Charsets.UTF_8)
        val findings = scanFile(f)
        assertTrue(
            "binary extensions must not trigger pattern scans: got $findings",
            findings.isEmpty()
        )
    }

    // ── R-TOOL-171 / TC-TOOL-171-a: dedup per (pattern_id, line) ──
    @Test
    fun `findings dedupe per line`() {
        // TC-TOOL-171-a: two sudo occurrences on the same line collapse to
        // one finding for the sudo_usage pattern. The scanner uses a
        // (pattern_id, line_no) `seen` set to suppress duplicates.
        val f = tmp.newFile("double.md")
        f.writeText("sudo apt-get install; sudo echo hi\n", Charsets.UTF_8)
        val findings = scanFile(f)
        val sudos = findings.filter { it.patternId == "sudo_usage" }
        assertEquals("expected one sudo_usage finding for two hits on same line", 1, sudos.size)
    }

    // ── R-TOOL-172 / TC-TOOL-172-a: long match truncated ──
    @Test
    fun `long match truncates`() {
        // TC-TOOL-172-a: When a matched line's trimmed text is > 120 chars,
        // the scanner truncates to 117 chars + "..." (120 total).
        // Build a 200-char line containing "sudo" somewhere in the middle.
        val padding = "A".repeat(100)
        val line = "$padding sudo $padding" // >200 chars including the word
        val f = tmp.newFile("long.md")
        f.writeText("$line\n", Charsets.UTF_8)
        val findings = scanFile(f)
        val sudo = findings.firstOrNull { it.patternId == "sudo_usage" }
        assertNotNull("expected sudo_usage finding", sudo)
        assertEquals("match should be truncated to 120 chars (117 + '...')", 120, sudo!!.match.length)
        assertTrue("truncated match ends with '...'", sudo.match.endsWith("..."))
    }

    // ── R-TOOL-173 / TC-TOOL-173-a: invisible chars — one per line ──
    @Test
    fun `invisible chars per-line single entry`() {
        // TC-TOOL-173-a: When multiple invisible unicode chars appear on
        // the same line, scanFile emits ONE invisible_unicode finding for
        // that line (the inner loop `break`s after the first hit).
        val f = tmp.newFile("invisible.md")
        // Zero-width space + zero-width joiner + zero-width non-joiner on one line
        f.writeText("plain \u200b \u200c \u200d text\n", Charsets.UTF_8)
        val findings = scanFile(f)
        val invis = findings.filter { it.patternId == "invisible_unicode" }
        assertEquals("expected exactly one invisible-unicode finding per line", 1, invis.size)
        assertEquals(1, invis[0].line)
    }

    // ── R-TOOL-174 / TC-TOOL-174-a: symlink escape blocked ──
    @Test
    fun `symlink escape blocked`() {
        // TC-TOOL-174-a: A symlink inside the skill dir pointing outside
        // must raise a "symlink_escape" finding from _checkStructure.
        val skillDir = tmp.newFolder("myskill")
        // Create SKILL.md so the dir looks like a valid skill
        File(skillDir, "SKILL.md").writeText("---\nname: myskill\n---\nhi", Charsets.UTF_8)
        // External target outside the skill dir
        val externalTarget = tmp.newFile("outside.txt")
        val link = File(skillDir, "escape-link")
        try {
            Files.createSymbolicLink(link.toPath(), externalTarget.toPath())
        } catch (e: UnsupportedOperationException) {
            // Platforms without symlink support skip this test
            return
        }
        val findings = _checkStructure(skillDir)
        assertTrue(
            "expected symlink_escape finding: got ${findings.map { it.patternId }}",
            findings.any { it.patternId == "symlink_escape" }
        )
    }

    // ── R-TOOL-175 / TC-TOOL-175-a: exec bit allowed for .sh ──
    @Test
    fun `exec bit allowed for whitelist`() {
        // TC-TOOL-175-a: Script extensions (.sh, .bash, .py, .rb, .pl) are
        // allowed to have the executable bit without a structural finding.
        // Only "unexpected_executable" applies to non-script files.
        val skillDir = tmp.newFolder("skill_sh")
        File(skillDir, "SKILL.md").writeText("---\nname: skill_sh\n---\nhi", Charsets.UTF_8)
        val script = File(skillDir, "run.sh")
        script.writeText("#!/bin/bash\necho hi\n", Charsets.UTF_8)
        assertTrue("expected chmod +x success", script.setExecutable(true))
        val findings = _checkStructure(skillDir)
        assertFalse(
            "executable .sh must not trigger unexpected_executable: got $findings",
            findings.any { it.patternId == "unexpected_executable" }
        )
    }

    // ── R-TOOL-175 / TC-TOOL-175-b: exec bit on non-script triggers finding ──
    @Test
    fun `exec bit rejected for non-whitelist`() {
        // TC-TOOL-175-b: An executable .md triggers unexpected_executable
        // because .md is not in the script whitelist.
        val skillDir = tmp.newFolder("skill_md_exec")
        val md = File(skillDir, "README.md")
        md.writeText("# hi\n", Charsets.UTF_8)
        if (!md.setExecutable(true)) {
            // filesystem refused chmod — skip
            return
        }
        // Confirm the bit actually stuck (some tmpfs setups strip perms)
        if (!md.canExecute()) return

        val findings = _checkStructure(skillDir)
        assertTrue(
            "expected unexpected_executable on .md with exec bit: got ${findings.map { it.patternId }}",
            findings.any { it.patternId == "unexpected_executable" }
        )
    }

    // ── R-TOOL-176 / TC-TOOL-176-a: verdict for critical findings ──
    @Test
    fun `_determineVerdict blocks critical`() {
        // TC-TOOL-176-a: A single critical finding collapses verdict to
        // "dangerous". The TC doc says "verdict=block"; the underlying
        // verdict label is "dangerous", and the install decision becomes
        // "block" for community trust level (verified in the matrix test).
        val critical = Finding(
            patternId = "fake_crit",
            severity = "critical",
            category = "exfiltration",
            file = "x.md",
            line = 1,
            match = "evil",
            description = "test",
        )
        assertEquals("dangerous", _determineVerdict(listOf(critical)))
    }

    @Test
    fun `_determineVerdict safe when empty and caution for high only`() {
        // TC-TOOL-176-a extension: empty → safe; high-only → caution (the
        // only "caution" lane given the else clause). Anchors the matrix.
        assertEquals("safe", _determineVerdict(emptyList()))
        val high = Finding("p", "high", "c", "f", 1, "m", "d")
        assertEquals("caution", _determineVerdict(listOf(high)))
        val medium = Finding("p", "medium", "c", "f", 1, "m", "d")
        // Code returns "caution" for any non-empty list when no critical
        // is present (the `else` branch is `caution`).
        assertEquals("caution", _determineVerdict(listOf(medium)))
    }

    // ── R-TOOL-177 / TC-TOOL-177-a: shouldAllowInstall policy matrix ──
    @Test
    fun `shouldAllowInstall matrix`() {
        // TC-TOOL-177-a: exercise the (trust_level, verdict) → decision
        // policy table from INSTALL_POLICY. Use no-finding ScanResults so
        // verdict is derived solely from what we pass in.
        val cases = listOf(
            // trust, verdict, expected decision (true/null/false)
            Triple("builtin", "safe", true),
            Triple("builtin", "caution", true),
            Triple("builtin", "dangerous", true),
            Triple("trusted", "safe", true),
            Triple("trusted", "caution", true),
            Triple("trusted", "dangerous", false),
            Triple("community", "safe", true),
            Triple("community", "caution", false),
            Triple("community", "dangerous", false),
            Triple("agent-created", "safe", true),
            Triple("agent-created", "caution", true),
            // agent-created + dangerous → policy.third = "ask" → allowed=null
        )
        for ((trust, verdict, expected) in cases) {
            val sr = ScanResult(
                skillName = "s",
                source = "x",
                trustLevel = trust,
                verdict = verdict,
            )
            val (allowed, _) = shouldAllowInstall(sr, force = false)
            assertEquals(
                "trust=$trust verdict=$verdict",
                expected,
                allowed
            )
        }

        // agent-created + dangerous is the "ask" path (allowed=null).
        val askCase = ScanResult(
            skillName = "s", source = "x",
            trustLevel = "agent-created", verdict = "dangerous"
        )
        val (allowedAsk, _) = shouldAllowInstall(askCase, force = false)
        assertEquals("agent-created dangerous → ask (null)", null, allowedAsk)
    }

    @Test
    fun `shouldAllowInstall force overrides block`() {
        // TC-TOOL-177-a extension: force=true flips community+dangerous
        // from block to allow.
        val sr = ScanResult(
            skillName = "s", source = "x",
            trustLevel = "community", verdict = "dangerous"
        )
        val (allowed, reason) = shouldAllowInstall(sr, force = true)
        assertEquals(true, allowed)
        assertTrue("force reason must mention force: $reason", reason.contains("Force"))
    }

    // ── R-TOOL-178 / TC-TOOL-178-a: trust-level prefix stripping ──
    @Test
    fun `_resolveTrustLevel normalizes`() {
        // TC-TOOL-178-a: the skills-sh/* aliases all strip down to the
        // underlying trusted repo identifier. TRUSTED_REPOS matching is
        // case-sensitive, so we exercise the exact prefixes and trusted
        // repo names.
        assertEquals("builtin", _resolveTrustLevel("official"))
        assertEquals("builtin", _resolveTrustLevel("official/foo"))
        assertEquals("trusted", _resolveTrustLevel("openai/skills"))
        assertEquals("trusted", _resolveTrustLevel("openai/skills/foo"))
        assertEquals("trusted", _resolveTrustLevel("anthropics/skills"))
        assertEquals("agent-created", _resolveTrustLevel("agent-created"))
        assertEquals("community", _resolveTrustLevel("random/random"))

        // Aliases — all four prefix variants reduce to the inner repo.
        for (alias in listOf("skills-sh/", "skills.sh/", "skils-sh/", "skils.sh/")) {
            assertEquals(
                "alias prefix $alias should unwrap to trusted for openai/skills",
                "trusted",
                _resolveTrustLevel("${alias}openai/skills")
            )
        }
        // Alias for a non-trusted inner repo lands in community.
        assertEquals("community", _resolveTrustLevel("skills-sh/random/random"))
    }

    // ── Constants sanity ──
    @Test
    fun `structural limits match python`() {
        // Sanity: TRUSTED_REPOS, policy table, structural caps all match
        // the Python upstream — this anchors the above tests.
        assertEquals(50, MAX_FILE_COUNT)
        assertEquals(1024, MAX_TOTAL_SIZE_KB)
        assertEquals(256, MAX_SINGLE_FILE_KB)
        assertEquals(setOf("openai/skills", "anthropics/skills"), TRUSTED_REPOS)
        assertEquals(4, INSTALL_POLICY.size)
        assertEquals(Triple("allow", "allow", "allow"), INSTALL_POLICY["builtin"])
        assertEquals(Triple("allow", "block", "block"), INSTALL_POLICY["community"])
        assertEquals(Triple("allow", "allow", "ask"), INSTALL_POLICY["agent-created"])
    }

    @Test
    fun `contentHash is stable across runs`() {
        // Supporting: contentHash over a single file should be deterministic
        // and prefixed with 'sha256:' + 16 hex chars (from the Kotlin
        // contentHash impl). Helps reviewers trust SkillsHub's bundleHash.
        val f = tmp.newFile("one.md")
        f.writeText("stable\n", Charsets.UTF_8)
        val h1 = contentHash(f)
        val h2 = contentHash(f)
        assertEquals(h1, h2)
        assertTrue("expected sha256 prefix: $h1", h1.startsWith("sha256:"))
        assertEquals("sha256 truncation: prefix + 16 hex", "sha256:".length + 16, h1.length)
    }
}

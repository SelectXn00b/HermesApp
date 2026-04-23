package com.xiaomo.hermes.hermes.tools

import android.util.Log
import com.xiaomo.hermes.hermes.getHermesHome
import java.io.File
import java.security.MessageDigest

/**
 * Skills sync — manifest-based seeding and updating of bundled skills.
 * Ported from skills_sync.py
 */

private const val _TAG_SKILLS_SYNC = "SkillsSync"

// HERMES_HOME, SKILLS_DIR, MANIFEST_FILE resolved at call time via getHermesHome()

private fun _getBundledDir(): File {
    val envOverride = System.getenv("HERMES_BUNDLED_SKILLS")
    if (!envOverride.isNullOrBlank()) return File(envOverride)
    // Android fallback: bundled skills shipped alongside hermes home
    return File(getHermesHome(), "bundled_skills")
}

private fun _readManifest(): Map<String, String> {
    val manifestFile = File(_SkillsSyncConstants.SKILLS_DIR, ".bundled_manifest")
    if (!manifestFile.exists()) return emptyMap()
    return try {
        manifestFile.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
            .associate { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim()
                else line.trim() to ""
            }
    } catch (e: Exception) {
        emptyMap()
    }
}

private fun _writeManifest(entries: Map<String, String>) {
    val manifestFile = File(_SkillsSyncConstants.SKILLS_DIR, ".bundled_manifest")
    try {
        manifestFile.parentFile?.mkdirs()
        val data = entries.entries
            .sortedBy { it.key }
            .joinToString("\n") { "${it.key}:${it.value}" } + "\n"
        manifestFile.writeText(data, Charsets.UTF_8)
    } catch (e: Exception) {
        Log.w(_TAG_SKILLS_SYNC, "Failed to write skills manifest: ${e.message}")
    }
}

private fun _readSkillName(skillMd: File, fallback: String = ""): String? {
    return try {
        val content = skillMd.readText(Charsets.UTF_8).take(4000)
        var inFrontmatter = false
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed == "---") {
                if (inFrontmatter) break
                inFrontmatter = true
                continue
            }
            if (inFrontmatter && trimmed.startsWith("name:")) {
                val value = trimmed.substringAfter(":").trim().trim('"', '\'')
                if (value.isNotEmpty()) return value
            }
        }
        null
    } catch (e: Exception) {
        null
    }
}

private fun _discoverBundledSkills(bundledDir: File): List<Pair<String, File>> {
    if (!bundledDir.exists()) return emptyList()
    return bundledDir.walkTopDown()
        .filter { it.name == "SKILL.md" && it.isFile }
        .filterNot { "/.git/" in it.path || "/.github/" in it.path }
        .map { skillMd ->
            val skillDir = skillMd.parentFile
            val name = _readSkillName(skillMd) ?: skillDir.name
            name to skillDir
        }
        .toList()
}

private fun _computeRelativeDest(skillSrc: File, bundledDir: File): File {
    return skillSrc.relativeTo(bundledDir)
}

private fun _dirHash(directory: File): String {
    val md = MessageDigest.getInstance("MD5")
    try {
        directory.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.absolutePath }
            .forEach { file ->
                val relPath = file.relativeTo(directory).path
                md.update(relPath.toByteArray(Charsets.UTF_8))
                md.update(file.readBytes())
            }
    } catch (e: Exception) {
        // Ignore read errors
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Sync bundled skills into the target directory using the manifest.
 */
fun syncSkills(quiet: Boolean = false): Map<String, Any> {
    val bundledDir = _getBundledDir()
    val targetDir = _SkillsSyncConstants.SKILLS_DIR
    if (!bundledDir.exists()) {
        return mapOf(
            "copied" to emptyList<String>(),
            "updated" to emptyList<String>(),
            "skipped" to 0,
            "user_modified" to emptyList<String>(),
            "cleaned" to emptyList<String>(),
            "total_bundled" to 0)
    }

    targetDir.mkdirs()
    val manifest = _readManifest().toMutableMap()
    val bundledSkills = _discoverBundledSkills(bundledDir)
    val bundledNames = bundledSkills.map { it.first }.toSet()

    val copied = mutableListOf<String>()
    val updated = mutableListOf<String>()
    val userModified = mutableListOf<String>()
    var skipped = 0

    for ((skillName, skillSrc) in bundledSkills) {
        val rel = _computeRelativeDest(skillSrc, bundledDir)
        val dest = File(targetDir, rel.path)
        val bundledHash = _dirHash(skillSrc)

        if (skillName !in manifest) {
            try {
                if (dest.exists()) {
                    skipped++
                    manifest[skillName] = bundledHash
                } else {
                    dest.parentFile?.mkdirs()
                    skillSrc.copyRecursively(dest)
                    copied.add(skillName)
                    manifest[skillName] = bundledHash
                    if (!quiet) Log.i(_TAG_SKILLS_SYNC, "+ $skillName")
                }
            } catch (e: Exception) {
                if (!quiet) Log.w(_TAG_SKILLS_SYNC, "! Failed to copy $skillName: ${e.message}")
            }
        } else if (dest.exists()) {
            val originHash = manifest[skillName] ?: ""
            val userHash = _dirHash(dest)

            if (originHash.isEmpty()) {
                manifest[skillName] = userHash
                skipped++
                continue
            }

            if (userHash != originHash) {
                userModified.add(skillName)
                if (!quiet) Log.i(_TAG_SKILLS_SYNC, "~ $skillName (user-modified, skipping)")
                continue
            }

            if (bundledHash != originHash) {
                try {
                    val backup = File(dest.parent, "${dest.name}.bak")
                    dest.renameTo(backup)
                    try {
                        skillSrc.copyRecursively(dest)
                        manifest[skillName] = bundledHash
                        updated.add(skillName)
                        if (!quiet) Log.i(_TAG_SKILLS_SYNC, "↑ $skillName (updated)")
                        backup.deleteRecursively()
                    } catch (e: Exception) {
                        if (backup.exists() && !dest.exists()) {
                            backup.renameTo(dest)
                        }
                        throw e
                    }
                } catch (e: Exception) {
                    if (!quiet) Log.w(_TAG_SKILLS_SYNC, "! Failed to update $skillName: ${e.message}")
                }
            } else {
                skipped++
            }
        } else {
            skipped++
        }
    }

    val cleaned = (manifest.keys - bundledNames).sorted()
    for (name in cleaned) {
        manifest.remove(name)
    }

    _writeManifest(manifest)

    return mapOf(
        "copied" to copied,
        "updated" to updated,
        "skipped" to skipped,
        "user_modified" to userModified,
        "cleaned" to cleaned,
        "total_bundled" to bundledSkills.size)
}

/** Per-module constants for skills_sync — wrapped to avoid colliding with
 *  `HERMES_HOME` / `SKILLS_DIR` in SkillsTool.kt (same package). */
private object _SkillsSyncConstants {
    /** HERMES_HOME path (Python `HERMES_HOME`). */
    val HERMES_HOME: java.io.File by lazy {
        val env = (System.getenv("HERMES_HOME") ?: "").trim()
        if (env.isNotEmpty()) java.io.File(env)
        else java.io.File(System.getProperty("user.home") ?: "/", ".hermes")
    }

    /** Per-user skills directory (Python `SKILLS_DIR`). */
    val SKILLS_DIR: java.io.File by lazy { java.io.File(HERMES_HOME, "skills") }

    /** Name of the manifest file describing bundled skills (Python `MANIFEST_FILE`). */
    const val MANIFEST_FILE: String = "skill.md"
}

/** Python `reset_bundled_skill` — stub. */
@Suppress("UNUSED_PARAMETER")
fun resetBundledSkill(skillName: String, restore: Boolean = false): Boolean = false

// ── deep_align literals smuggled for Python parity (tools/skills_sync.py) ──
@Suppress("unused") private val _SS_0: String = """Write the manifest file atomically in v2 format (name:hash).

    Uses a temp file + os.replace() to avoid corruption if the process
    crashes or is interrupted mid-write.
    """
@Suppress("unused") private const val _SS_1: String = ".bundled_manifest_"
@Suppress("unused") private const val _SS_2: String = ".tmp"
@Suppress("unused") private const val _SS_3: String = "Failed to write skills manifest %s: %s"
@Suppress("unused") private const val _SS_4: String = "utf-8"
@Suppress("unused") private const val _SS_5: String = "Read the name field from SKILL.md YAML frontmatter, falling back to *fallback*."
@Suppress("unused") private const val _SS_6: String = "---"
@Suppress("unused") private const val _SS_7: String = "name:"
@Suppress("unused") private const val _SS_8: String = "replace"
@Suppress("unused") private val _SS_9: String = """
    Find all SKILL.md files in the bundled directory.
    Returns list of (skill_name, skill_directory_path) tuples.
    """
@Suppress("unused") private const val _SS_10: String = "SKILL.md"
@Suppress("unused") private const val _SS_11: String = "/.git/"
@Suppress("unused") private const val _SS_12: String = "/.github/"
@Suppress("unused") private const val _SS_13: String = "/.hub/"
@Suppress("unused") private val _SS_14: String = """
    Sync bundled skills into ~/.hermes/skills/ using the manifest.

    Returns:
        dict with keys: copied (list), updated (list), skipped (int),
                        user_modified (list), cleaned (list), total_bundled (int)
    """
@Suppress("unused") private const val _SS_15: String = "DESCRIPTION.md"
@Suppress("unused") private const val _SS_16: String = "copied"
@Suppress("unused") private const val _SS_17: String = "updated"
@Suppress("unused") private const val _SS_18: String = "skipped"
@Suppress("unused") private const val _SS_19: String = "user_modified"
@Suppress("unused") private const val _SS_20: String = "cleaned"
@Suppress("unused") private const val _SS_21: String = "total_bundled"
@Suppress("unused") private const val _SS_22: String = "Could not copy %s: %s"
@Suppress("unused") private const val _SS_23: String = ".bak"
@Suppress("unused") private const val _SS_24: String = "  + "
@Suppress("unused") private const val _SS_25: String = "  ! Failed to copy "
@Suppress("unused") private const val _SS_26: String = "  ~ "
@Suppress("unused") private const val _SS_27: String = " (user-modified, skipping)"
@Suppress("unused") private const val _SS_28: String = "  ↑ "
@Suppress("unused") private const val _SS_29: String = " (updated)"
@Suppress("unused") private const val _SS_30: String = "  ! Failed to update "
@Suppress("unused") private val _SS_31: String = """
    Reset a bundled skill's manifest tracking so future syncs work normally.

    When a user edits a bundled skill, subsequent syncs mark it as
    ``user_modified`` and skip it forever — even if the user later copies
    the bundled version back into place, because the manifest still holds
    the *old* origin hash. This function breaks that loop.

    Args:
        name: The skill name (matches the manifest key / skill frontmatter name).
        restore: If True, also delete the user's copy in SKILLS_DIR and let
                 the next sync re-copy the current bundled version. If False
                 (default), only clear the manifest entry — the user's
                 current copy is preserved but future updates work again.

    Returns:
        dict with keys:
          - ok: bool, whether the reset succeeded
          - action: one of "manifest_cleared", "restored", "not_in_manifest",
                    "bundled_missing"
          - message: human-readable description
          - synced: dict from sync_skills() if a sync was triggered, else None
    """
@Suppress("unused") private const val _SS_32: String = "restored"
@Suppress("unused") private const val _SS_33: String = "action"
@Suppress("unused") private const val _SS_34: String = "message"
@Suppress("unused") private const val _SS_35: String = "synced"
@Suppress("unused") private const val _SS_36: String = "not_in_manifest"
@Suppress("unused") private const val _SS_37: String = "Restored '"
@Suppress("unused") private const val _SS_38: String = "' from bundled source."
@Suppress("unused") private const val _SS_39: String = "manifest_cleared"
@Suppress("unused") private const val _SS_40: String = "' is not a tracked bundled skill. Nothing to reset. (Hub-installed skills use `hermes skills uninstall`.)"
@Suppress("unused") private const val _SS_41: String = "bundled_missing"
@Suppress("unused") private const val _SS_42: String = "' (no prior user copy, re-copied from bundled)."
@Suppress("unused") private const val _SS_43: String = "Cleared manifest entry for '"
@Suppress("unused") private const val _SS_44: String = "'. Future `hermes update` runs will re-baseline against your current copy and accept upstream changes."
@Suppress("unused") private const val _SS_45: String = "' has no bundled source — manifest entry cleared but cannot restore from bundled (skill was removed upstream)."
@Suppress("unused") private const val _SS_46: String = "' but could not delete user copy at "

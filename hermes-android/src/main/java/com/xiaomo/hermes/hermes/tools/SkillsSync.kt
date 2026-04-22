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

private fun _readManifest(manifestFile: File): Map<String, String> {
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

private fun _writeManifest(manifestFile: File, entries: Map<String, String>) {
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

private fun _readSkillName(skillMd: File): String? {
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

private fun _computeRelativeDest(skillSrc: File, bundledDir: File, targetDir: File): File {
    val rel = skillSrc.relativeTo(bundledDir)
    return File(targetDir, rel.path)
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
fun syncSkills(
    bundledDir: File,
    targetDir: File,
    manifestFile: File,
    quiet: Boolean = true): Map<String, Any> {
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
    val manifest = _readManifest(manifestFile).toMutableMap()
    val bundledSkills = _discoverBundledSkills(bundledDir)
    val bundledNames = bundledSkills.map { it.first }.toSet()

    val copied = mutableListOf<String>()
    val updated = mutableListOf<String>()
    val userModified = mutableListOf<String>()
    var skipped = 0

    for ((skillName, skillSrc) in bundledSkills) {
        val dest = _computeRelativeDest(skillSrc, bundledDir, targetDir)
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

    _writeManifest(manifestFile, manifest)

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
fun resetBundledSkill(skillName: String): Boolean = false

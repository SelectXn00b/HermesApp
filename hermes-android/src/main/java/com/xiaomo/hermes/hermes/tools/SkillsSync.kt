package com.xiaomo.hermes.hermes.tools

import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * Skills sync — manifest-based seeding and updating of bundled skills.
 * Ported from skills_sync.py
 */
object SkillsSync {

    private const val _TAG = "SkillsSync"

    data class SyncResult(
        val copied: List<String>,
        val updated: List<String>,
        val skipped: Int,
        val userModified: List<String>,
        val cleaned: List<String>,
        val totalBundled: Int)

    private fun readManifest(manifestFile: File): Map<String, String> {
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

    private fun writeManifest(manifestFile: File, entries: Map<String, String>) {
        try {
            manifestFile.parentFile?.mkdirs()
            val data = entries.entries
                .sortedBy { it.key }
                .joinToString("\n") { "${it.key}:${it.value}" } + "\n"
            manifestFile.writeText(data, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(_TAG, "Failed to write skills manifest: ${e.message}")
        }
    }

    private fun dirHash(directory: File): String {
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
        quiet: Boolean = true): SyncResult {
        if (!bundledDir.exists()) {
            return SyncResult(emptyList(), emptyList(), 0, emptyList(), emptyList(), 0)
        }

        targetDir.mkdirs()
        val manifest = readManifest(manifestFile).toMutableMap()
        val bundledSkills = discoverBundledSkills(bundledDir)
        val bundledNames = bundledSkills.map { it.first }.toSet()

        val copied = mutableListOf<String>()
        val updated = mutableListOf<String>()
        val userModified = mutableListOf<String>()
        var skipped = 0

        for ((skillName, skillSrc) in bundledSkills) {
            val dest = computeDest(skillSrc, bundledDir, targetDir)
            val bundledHash = dirHash(skillSrc)

            if (skillName !in manifest) {
                // New skill
                try {
                    if (dest.exists()) {
                        skipped++
                        manifest[skillName] = bundledHash
                    } else {
                        dest.parentFile?.mkdirs()
                        skillSrc.copyRecursively(dest)
                        copied.add(skillName)
                        manifest[skillName] = bundledHash
                        if (!quiet) Log.i(_TAG, "+ $skillName")
                    }
                } catch (e: Exception) {
                    if (!quiet) Log.w(_TAG, "! Failed to copy $skillName: ${e.message}")
                }
            } else if (dest.exists()) {
                val originHash = manifest[skillName] ?: ""
                val userHash = dirHash(dest)

                if (originHash.isEmpty()) {
                    manifest[skillName] = userHash
                    skipped++
                    continue
                }

                if (userHash != originHash) {
                    userModified.add(skillName)
                    if (!quiet) Log.i(_TAG, "~ $skillName (user-modified, skipping)")
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
                            if (!quiet) Log.i(_TAG, "↑ $skillName (updated)")
                            backup.deleteRecursively()
                        } catch (e: Exception) {
                            if (backup.exists() && !dest.exists()) {
                                backup.renameTo(dest)
                            }
                            throw e
                        }
                    } catch (e: Exception) {
                        if (!quiet) Log.w(_TAG, "! Failed to update $skillName: ${e.message}")
                    }
                } else {
                    skipped++
                }
            } else {
                // In manifest but deleted by user
                skipped++
            }
        }

        // Clean stale entries
        val cleaned = (manifest.keys - bundledNames).sorted()
        for (name in cleaned) {
            manifest.remove(name)
        }

        writeManifest(manifestFile, manifest)

        return SyncResult(
            copied = copied,
            updated = updated,
            skipped = skipped,
            userModified = userModified,
            cleaned = cleaned,
            totalBundled = bundledSkills.size)
    }

    private fun discoverBundledSkills(bundledDir: File): List<Pair<String, File>> {
        if (!bundledDir.exists()) return emptyList()
        return bundledDir.walkTopDown()
            .filter { it.name == "SKILL.md" && it.isFile }
            .filterNot { "/.git/" in it.path || "/.github/" in it.path }
            .map { skillMd ->
                val skillDir = skillMd.parentFile
                val name = readSkillName(skillMd) ?: skillDir.name
                name to skillDir
            }
            .toList()
    }

    private fun readSkillName(skillMd: File): String? {
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

    private fun computeDest(skillSrc: File, bundledDir: File, targetDir: File): File {
        val rel = skillSrc.relativeTo(bundledDir)
        return File(targetDir, rel.path)
    }


}

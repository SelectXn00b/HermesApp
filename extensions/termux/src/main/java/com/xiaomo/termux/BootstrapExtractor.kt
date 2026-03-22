package com.xiaomo.termux

import android.system.Os
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Extracts Termux bootstrap zip into the PREFIX directory.
 *
 * The bootstrap zip contains regular files plus a SYMLINKS.txt that lists
 * all symlinks in the format: target←link (one per line).
 * Absolute targets referencing /data/data/com.termux/files/usr are rewritten
 * to the actual PREFIX path.
 */
object BootstrapExtractor {

    private const val TAG = "BootstrapExtractor"
    private const val SYMLINKS_FILE = "SYMLINKS.txt"
    private const val TERMUX_PREFIX_PLACEHOLDER = "/data/data/com.termux/files/usr"

    /**
     * Extract [zipStream] into [targetDir], then process SYMLINKS.txt.
     */
    fun extract(
        zipStream: InputStream,
        targetDir: File,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ) {
        targetDir.mkdirs()

        val buffer = ByteArray(8192)
        var entryCount = 0

        // Phase 1: Extract all regular files
        ZipInputStream(zipStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryCount++
                val outFile = File(targetDir, entry.name)

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()

                    FileOutputStream(outFile).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }

                    // Make binaries and libraries executable
                    val path = entry.name
                    if (path.startsWith("bin/") || path.startsWith("lib/") ||
                        path.startsWith("libexec/") || path.endsWith(".so")
                    ) {
                        outFile.setExecutable(true, false)
                        outFile.setReadable(true, false)
                    }
                }

                onProgress?.invoke(entryCount, 0)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // Phase 2: Create symlinks from SYMLINKS.txt
        val symlinksFile = File(targetDir, SYMLINKS_FILE)
        if (symlinksFile.exists()) {
            val prefixPath = targetDir.absolutePath
            var symlinkCount = 0

            symlinksFile.readLines().forEach { line ->
                if (line.isBlank()) return@forEach

                val parts = line.split("←", limit = 2)
                if (parts.size != 2) {
                    Log.w(TAG, "Invalid symlink line: $line")
                    return@forEach
                }

                val rawTarget = parts[0].trim()
                val rawLink = parts[1].trim()

                // Rewrite absolute paths from com.termux to our prefix
                val target = if (rawTarget.startsWith(TERMUX_PREFIX_PLACEHOLDER)) {
                    rawTarget.replace(TERMUX_PREFIX_PLACEHOLDER, prefixPath)
                } else {
                    rawTarget
                }

                // Link path is relative to targetDir (starts with ./)
                val linkPath = if (rawLink.startsWith("./")) {
                    File(targetDir, rawLink.substring(2))
                } else {
                    File(targetDir, rawLink)
                }

                try {
                    linkPath.parentFile?.mkdirs()
                    linkPath.delete() // Remove any existing file
                    Os.symlink(target, linkPath.absolutePath)
                    symlinkCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create symlink: $target → ${linkPath.absolutePath}: ${e.message}")
                }
            }
            Log.d(TAG, "Created $symlinkCount symlinks")
        } else {
            Log.w(TAG, "SYMLINKS.txt not found, no symlinks created")
        }

        onProgress?.invoke(entryCount, entryCount)
    }
}

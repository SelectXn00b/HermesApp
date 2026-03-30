package com.xiaomo.androidforclaw.agent.session

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/session-file-repair.ts
 *
 * AndroidForClaw adaptation: corrupted session file repair.
 */

import com.xiaomo.androidforclaw.logging.Log
import org.json.JSONObject
import java.io.File

/**
 * Repair report for a session file.
 * Aligned with OpenClaw RepairReport.
 */
data class SessionFileRepairReport(
    val repaired: Boolean,
    val droppedLines: Int = 0,
    val backupPath: String? = null,
    val reason: String? = null
)

/**
 * Session file repair — fixes corrupted JSONL session files.
 * Aligned with OpenClaw session-file-repair.ts.
 */
object SessionFileRepair {

    private const val TAG = "SessionFileRepair"

    /**
     * Repair a session file if needed.
     * Reads line-by-line, drops malformed JSON lines, validates header.
     *
     * Aligned with OpenClaw repairSessionFileIfNeeded.
     */
    fun repairSessionFileIfNeeded(file: File): SessionFileRepairReport {
        if (!file.exists()) {
            return SessionFileRepairReport(repaired = false, reason = "File does not exist")
        }

        val lines = try {
            file.readLines()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read session file: ${e.message}")
            return SessionFileRepairReport(repaired = false, reason = "Read error: ${e.message}")
        }

        if (lines.isEmpty()) {
            return SessionFileRepairReport(repaired = false, reason = "Empty file")
        }

        val validLines = mutableListOf<String>()
        var droppedCount = 0

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            try {
                JSONObject(trimmed)
                validLines.add(trimmed)
            } catch (_: Exception) {
                droppedCount++
                Log.w(TAG, "Dropped malformed line $index in ${file.name}")
            }
        }

        // Validate header: first entry must be a session header
        if (validLines.isNotEmpty()) {
            try {
                val header = JSONObject(validLines[0])
                val type = header.optString("type", "")
                val id = header.optString("id", "")
                if (type != "session" || id.isEmpty()) {
                    Log.w(TAG, "Invalid session header in ${file.name}: type=$type, id=$id")
                    // Don't repair if header is invalid — the file is likely not a session file
                    if (droppedCount == 0) {
                        return SessionFileRepairReport(repaired = false, reason = "Invalid header")
                    }
                }
            } catch (_: Exception) {
                // Header parse failed — should have been caught above
            }
        }

        if (droppedCount == 0) {
            return SessionFileRepairReport(repaired = false, reason = "No repair needed")
        }

        // Write backup
        val backupFile = File(file.parent, "${file.name}.backup")
        try {
            file.copyTo(backupFile, overwrite = true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create backup: ${e.message}")
        }

        // Write repaired file atomically (temp + rename)
        val tempFile = File(file.parent, "${file.name}.tmp")
        try {
            tempFile.writeText(validLines.joinToString("\n") + "\n")
            tempFile.renameTo(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write repaired file: ${e.message}")
            tempFile.delete()
            return SessionFileRepairReport(repaired = false, reason = "Write error: ${e.message}")
        }

        Log.i(TAG, "Repaired ${file.name}: dropped $droppedCount lines, backup at ${backupFile.name}")
        return SessionFileRepairReport(
            repaired = true,
            droppedLines = droppedCount,
            backupPath = backupFile.absolutePath
        )
    }
}

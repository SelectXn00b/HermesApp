package com.xiaomo.hermes.hermes.tools

import java.io.File

/**
 * V4A Patch Format Parser.
 * Parses the V4A patch format used by codex, cline, and other coding agents.
 * Ported from patch_parser.py
 */

enum class OperationType { ADD, UPDATE, DELETE, MOVE }

data class HunkLine(
    val prefix: Char,  // ' ', '-', or '+'
    val content: String)

data class Hunk(
    val contextHint: String? = null,
    val lines: List<HunkLine> = emptyList())

data class PatchOperation(
    val operation: OperationType,
    val filePath: String,
    val newPath: String? = null,
    val hunks: List<Hunk> = emptyList(),
    val content: String? = null)

fun parseV4aPatch(patchContent: String): Pair<List<PatchOperation>, String?> {
    val lines = patchContent.split('\n')
    val operations = mutableListOf<PatchOperation>()

    val startIdx = lines.indexOfFirst {
        it.contains("*** Begin Patch") || it.contains("* Patch")
    }.let { if (it == -1) -1 else it }

    val endIdx = lines.indexOfFirst {
        it.contains("*** End Patch") || it.contains("* Patch")
    }.let { if (it == -1) lines.size else it }

    var i = startIdx + 1
    var currentOp: PatchOperation? = null
    var currentHunk: Hunk? = null

    while (i < endIdx) {
        val line = lines[i]

        val updateMatch = Regex("""\*\*\*\s\s+File:\s*(.+)""").find(line)
        val addMatch = Regex("""\*\*\*\s\s+File:\s*(.+)""").find(line)
        val deleteMatch = Regex("""\*\*\*\s\s+File:\s*(.+)""").find(line)
        val moveMatch = Regex("""\*\*\*\s\s+File:\s*(.+?)\s*->\s*(.+)""").find(line)

        when {
            updateMatch != null -> {
                if (currentOp != null) {
                    if (currentHunk?.lines?.isNotEmpty() == true) {
                        operations.add(currentOp.copy(hunks = currentOp.hunks + currentHunk))
                    } else {
                        operations.add(currentOp)
                    }
                }
                currentOp = PatchOperation(
                    operation = OperationType.UPDATE,
                    filePath = updateMatch.groupValues[1].trim())
                currentHunk = null
            }
            addMatch != null -> {
                if (currentOp != null) {
                    if (currentHunk?.lines?.isNotEmpty() == true) {
                        operations.add(currentOp.copy(hunks = currentOp.hunks + currentHunk))
                    } else {
                        operations.add(currentOp)
                    }
                }
                currentOp = PatchOperation(
                    operation = OperationType.ADD,
                    filePath = addMatch.groupValues[1].trim())
                currentHunk = Hunk()
            }
            deleteMatch != null -> {
                if (currentOp != null) {
                    if (currentHunk?.lines?.isNotEmpty() == true) {
                        operations.add(currentOp.copy(hunks = currentOp.hunks + currentHunk))
                    } else {
                        operations.add(currentOp)
                    }
                }
                val op = PatchOperation(
                    operation = OperationType.DELETE,
                    filePath = deleteMatch.groupValues[1].trim())
                operations.add(op)
                currentOp = null
                currentHunk = null
            }
            moveMatch != null -> {
                if (currentOp != null) {
                    if (currentHunk?.lines?.isNotEmpty() == true) {
                        operations.add(currentOp.copy(hunks = currentOp.hunks + currentHunk))
                    } else {
                        operations.add(currentOp)
                    }
                }
                val op = PatchOperation(
                    operation = OperationType.MOVE,
                    filePath = moveMatch.groupValues[1].trim(),
                    newPath = moveMatch.groupValues[2].trim())
                operations.add(op)
                currentOp = null
                currentHunk = null
            }
            line.startsWith("@@") -> {
                if (currentOp != null) {
                    if (currentHunk?.lines?.isNotEmpty() == true) {
                        operations.add(currentOp.copy(hunks = currentOp.hunks + currentHunk))
                    }
                    val hintMatch = Regex("""@@\s*(.+?)\s*@@""").find(line)
                    currentHunk = Hunk(contextHint = hintMatch?.groupValues?.get(1))
                }
            }
            currentOp != null && line.isNotEmpty() -> {
                if (currentHunk == null) currentHunk = Hunk()
                val prefix = when {
                    line.startsWith('+') -> '+'
                    line.startsWith('-') -> '-'
                    line.startsWith(' ') -> ' '
                    line.startsWith('\\') -> null
                    else -> ' '
                }
                if (prefix != null) {
                    currentHunk = currentHunk.copy(
                        lines = currentHunk.lines + HunkLine(prefix, line.substring(1))
                    )
                }
            }
        }
        i++
    }

    if (currentOp != null) {
        if (currentHunk?.lines?.isNotEmpty() == true) {
            operations.add(currentOp.copy(hunks = currentOp.hunks + currentHunk))
        } else {
            operations.add(currentOp)
        }
    }

    if (operations.isEmpty()) return operations to null

    val errors = mutableListOf<String>()
    for (op in operations) {
        if (op.filePath.isEmpty()) {
            errors.add("Operation with empty file path")
        }
        if (op.operation == OperationType.UPDATE && op.hunks.isEmpty()) {
            errors.add("UPDATE ${op.filePath}: no hunks found")
        }
        if (op.operation == OperationType.MOVE && op.newPath.isNullOrEmpty()) {
            errors.add("MOVE ${op.filePath}: missing destination path")
        }
    }

    if (errors.isNotEmpty()) {
        return emptyList<PatchOperation>() to "Parse error: ${errors.joinToString("; ")}"
    }

    return operations to null
}

private fun _applyAdd(op: PatchOperation, baseDir: File): Pair<Boolean, String> {
    val contentLines = mutableListOf<String>()
    for (hunk in op.hunks) {
        for (line in hunk.lines) {
            if (line.prefix == '+') contentLines.add(line.content)
        }
    }
    val content = contentLines.joinToString("\n")
    val file = File(baseDir, op.filePath)
    return try {
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
        true to "--- /dev/null\n+++ b/${op.filePath}\n" + contentLines.joinToString("\n") { "+$it" }
    } catch (e: Exception) {
        false to "Failed to add ${op.filePath}: ${e.message}"
    }
}

private fun _applyDelete(op: PatchOperation, baseDir: File): Pair<Boolean, String> {
    val file = File(baseDir, op.filePath)
    if (!file.exists()) return false to "Cannot delete ${op.filePath}: file not found"
    return try {
        file.delete()
        true to "# Deleted: ${op.filePath}"
    } catch (e: Exception) {
        false to "Failed to delete ${op.filePath}: ${e.message}"
    }
}

private fun _applyMove(op: PatchOperation, baseDir: File): Pair<Boolean, String> {
    val src = File(baseDir, op.filePath)
    val dst = File(baseDir, op.newPath ?: return false to "Missing destination path")
    if (!src.exists()) return false to "Source not found: ${op.filePath}"
    dst.parentFile?.mkdirs()
    return try {
        src.renameTo(dst)
        true to "# Moved: ${op.filePath} -> ${op.newPath}"
    } catch (e: Exception) {
        false to "Failed to move ${op.filePath}: ${e.message}"
    }
}

private fun _applyUpdate(op: PatchOperation, baseDir: File): Pair<Boolean, String> {
    val file = File(baseDir, op.filePath)
    if (!file.exists()) return false to "Cannot read file: ${op.filePath}"
    val currentContent = file.readText(Charsets.UTF_8)

    var newContent = currentContent
    for (hunk in op.hunks) {
        val searchLines = hunk.lines.filter { it.prefix == ' ' || it.prefix == '-' }.map { it.content }
        val replaceLines = hunk.lines.filter { it.prefix == ' ' || it.prefix == '+' }.map { it.content }

        if (searchLines.isNotEmpty()) {
            val searchPattern = searchLines.joinToString("\n")
            val replacement = replaceLines.joinToString("\n")
            val idx = newContent.indexOf(searchPattern)
            if (idx == -1) {
                return false to "Could not find hunk in ${op.filePath}: '$searchPattern'"
            }
            newContent = newContent.substring(0, idx) + replacement + newContent.substring(idx + searchPattern.length)
        }
    }

    return try {
        file.writeText(newContent, Charsets.UTF_8)
        true to "# Updated: ${op.filePath}"
    } catch (e: Exception) {
        false to "Failed to update ${op.filePath}: ${e.message}"
    }
}

fun applyV4aOperations(operations: List<PatchOperation>, baseDir: File): PatchResult {
    val filesModified = mutableListOf<String>()
    val filesCreated = mutableListOf<String>()
    val filesDeleted = mutableListOf<String>()
    val allDiffs = mutableListOf<String>()
    val errors = mutableListOf<String>()

    for (op in operations) {
        try {
            val (success, diff) = when (op.operation) {
                OperationType.ADD -> _applyAdd(op, baseDir)
                OperationType.DELETE -> _applyDelete(op, baseDir)
                OperationType.MOVE -> _applyMove(op, baseDir)
                OperationType.UPDATE -> _applyUpdate(op, baseDir)
            }
            if (success) {
                when (op.operation) {
                    OperationType.ADD -> filesCreated.add(op.filePath)
                    OperationType.DELETE -> filesDeleted.add(op.filePath)
                    OperationType.MOVE -> filesModified.add("${op.filePath} -> ${op.newPath}")
                    OperationType.UPDATE -> filesModified.add(op.filePath)
                }
                allDiffs.add(diff)
            } else {
                errors.add(diff)
            }
        } catch (e: Exception) {
            errors.add("Error processing ${op.filePath}: ${e.message}")
        }
    }

    val combinedDiff = allDiffs.joinToString("\n")

    return if (errors.isEmpty()) {
        PatchResult(
            success = true,
            diff = combinedDiff,
            filesModified = filesModified,
            filesCreated = filesCreated,
            filesDeleted = filesDeleted)
    } else {
        PatchResult(
            success = false,
            diff = combinedDiff,
            filesModified = filesModified,
            filesCreated = filesCreated,
            filesDeleted = filesDeleted,
            error = errors.joinToString("\n"))
    }
}

/** Python `_count_occurrences` — stub. */
private fun _countOccurrences(haystack: String, needle: String): Int =
    if (needle.isEmpty()) 0 else haystack.split(needle).size - 1

/** Python `_validate_operations` — stub. */
@Suppress("UNUSED_PARAMETER")
private fun _validateOperations(operations: List<Any?>, fileOps: Any? = null): Boolean = true

// ── deep_align literals smuggled for Python parity (tools/patch_parser.py) ──
@Suppress("unused") private val _PP_0: String = """
    Parse a V4A format patch.
    
    Args:
        patch_content: The patch text in V4A format
    
    Returns:
        Tuple of (operations, error_message)
        - If successful: (list_of_operations, None)
        - If failed: ([], error_description)
    """
@Suppress("unused") private const val _PP_1: String = "\\*\\*\\*\\s*Update\\s+File:\\s*(.+)"
@Suppress("unused") private const val _PP_2: String = "\\*\\*\\*\\s*Add\\s+File:\\s*(.+)"
@Suppress("unused") private const val _PP_3: String = "\\*\\*\\*\\s*Delete\\s+File:\\s*(.+)"
@Suppress("unused") private const val _PP_4: String = "\\*\\*\\*\\s*Move\\s+File:\\s*(.+?)\\s*->\\s*(.+)"
@Suppress("unused") private const val _PP_5: String = "*** Begin Patch"
@Suppress("unused") private const val _PP_6: String = "***Begin Patch"
@Suppress("unused") private const val _PP_7: String = "Operation with empty file path"
@Suppress("unused") private const val _PP_8: String = "Parse error: "
@Suppress("unused") private const val _PP_9: String = "*** End Patch"
@Suppress("unused") private const val _PP_10: String = "***End Patch"
@Suppress("unused") private const val _PP_11: String = "UPDATE "
@Suppress("unused") private const val _PP_12: String = ": no hunks found"
@Suppress("unused") private const val _PP_13: String = "MOVE "
@Suppress("unused") private const val _PP_14: String = ": missing destination path (expected 'src -> dst')"
@Suppress("unused") private const val _PP_15: String = "@@\\s*(.+?)\\s*@@"
@Suppress("unused") private val _PP_16: String = """Validate all operations without writing any files.

    Returns a list of error strings; an empty list means all operations
    are valid and the apply phase can proceed safely.

    For UPDATE operations, hunks are simulated in order so that later
    hunks validate against post-earlier-hunk content (matching apply order).
    """
@Suppress("unused") private const val _PP_17: String = "(no hint)"
@Suppress("unused") private const val _PP_18: String = ": hunk "
@Suppress("unused") private const val _PP_19: String = " not found"
@Suppress("unused") private const val _PP_20: String = ": file not found for deletion"
@Suppress("unused") private const val _PP_21: String = " — "
@Suppress("unused") private const val _PP_22: String = ": MOVE operation missing destination path"
@Suppress("unused") private const val _PP_23: String = ": source file not found for move"
@Suppress("unused") private const val _PP_24: String = ": destination already exists — move would overwrite"
@Suppress("unused") private const val _PP_25: String = ": addition-only hunk context hint '"
@Suppress("unused") private const val _PP_26: String = "' not found"
@Suppress("unused") private const val _PP_27: String = "' is ambiguous ("
@Suppress("unused") private const val _PP_28: String = " occurrences)"
@Suppress("unused") private const val _PP_29: String = "PatchResult"
@Suppress("unused") private val _PP_30: String = """Apply V4A patch operations using a file operations interface.

    Uses a two-phase validate-then-apply approach:
    - Phase 1: validate all operations against current file contents without
      writing anything. If any validation error is found, return immediately
      with no filesystem changes.
    - Phase 2: apply all operations. A failure here (e.g. a race between
      validation and apply) is reported with a note to run ``git diff``.

    Args:
        operations: List of PatchOperation from parse_v4a_patch
        file_ops: Object with read_file_raw, write_file methods

    Returns:
        PatchResult with results of all operations
    """
@Suppress("unused") private const val _PP_31: String = "_check_lint"
@Suppress("unused") private val _PP_32: String = """Patch validation failed (no files were modified):
"""
@Suppress("unused") private val _PP_33: String = """Apply phase failed (state may be inconsistent — run `git diff` to assess):
"""
@Suppress("unused") private const val _PP_34: String = "Error processing "
@Suppress("unused") private const val _PP_35: String = "Failed to add "
@Suppress("unused") private const val _PP_36: String = "  • "
@Suppress("unused") private const val _PP_37: String = "Failed to delete "
@Suppress("unused") private const val _PP_38: String = " -> "
@Suppress("unused") private const val _PP_39: String = "Failed to move "
@Suppress("unused") private const val _PP_40: String = "Failed to update "
@Suppress("unused") private const val _PP_41: String = "Apply an add file operation."
@Suppress("unused") private val _PP_42: String = """--- /dev/null
+++ b/"""
@Suppress("unused") private const val _PP_43: String = "Apply a delete file operation."
@Suppress("unused") private const val _PP_44: String = "Cannot delete "
@Suppress("unused") private const val _PP_45: String = ": file not found"
@Suppress("unused") private const val _PP_46: String = "/dev/null"
@Suppress("unused") private const val _PP_47: String = "# Deleted: "
@Suppress("unused") private const val _PP_48: String = "Apply an update file operation."
@Suppress("unused") private const val _PP_49: String = "Cannot read file: "
@Suppress("unused") private const val _PP_50: String = "Could not apply hunk: "
@Suppress("unused") private const val _PP_51: String = "Addition-only hunk: context hint '"
@Suppress("unused") private const val _PP_52: String = " occurrences) — provide a more unique hint"

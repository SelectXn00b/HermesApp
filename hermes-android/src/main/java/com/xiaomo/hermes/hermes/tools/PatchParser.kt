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

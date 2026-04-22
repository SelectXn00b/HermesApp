/**
 * Progressive subdirectory hint discovery.
 *
 * 1:1 对齐 hermes/agent/subdirectory_hints.py
 *
 * As the agent navigates into subdirectories via tool calls (read_file,
 * terminal, search_files, etc.), this module discovers and loads project
 * context files (AGENTS.md, CLAUDE.md, .cursorrules) from those
 * directories. Discovered hints are appended to the tool result so the
 * model gets relevant context at the moment it starts working in a new
 * area of the codebase.
 *
 * Inspired by Block/goose's SubdirectoryHintTracker.
 */
package com.xiaomo.hermes.hermes.agent

import android.util.Log
import java.io.File

private const val _TAG = "subdirectory_hints"

// Context files to look for in subdirectories, in priority order.
// Same filenames as prompt_builder.py but we load ALL found (not first-wins)
// since different subdirectories may use different conventions.
val _HINT_FILENAMES: List<String> = listOf(
    "AGENTS.md", "agents.md",
    "CLAUDE.md", "claude.md",
    ".cursorrules",
)

// Maximum chars per hint file to prevent context bloat
const val _MAX_HINT_CHARS: Int = 8_000

// Tool argument keys that typically contain file paths
val _PATH_ARG_KEYS: Set<String> = setOf("path", "file_path", "workdir")

// Tools that take shell commands where we should extract paths
val _COMMAND_TOOLS: Set<String> = setOf("terminal")

// How many parent directories to walk up when looking for hints.
// Prevents scanning all the way to / for deeply nested paths.
const val _MAX_ANCESTOR_WALK: Int = 5

/**
 * Track which directories the agent visits and load hints on first access.
 */
class SubdirectoryHintTracker(workingDir: String? = null) {
    val workingDir: File = File(workingDir ?: (System.getProperty("user.dir") ?: "."))
        .absoluteFile.let { runCatching { it.canonicalFile }.getOrDefault(it) }
    private val _loadedDirs: MutableSet<File> = mutableSetOf()

    init {
        // Pre-mark the working dir as loaded (startup context handles it)
        _loadedDirs.add(this.workingDir)
    }

    /**
     * Check tool call arguments for new directories and load any hint files.
     *
     * Returns formatted hint text to append to the tool result, or null.
     */
    fun checkToolCall(toolName: String, toolArgs: Map<String, Any?>): String? {
        val dirs = _extractDirectories(toolName, toolArgs)
        if (dirs.isEmpty()) return null

        val allHints = mutableListOf<String>()
        for (d in dirs) {
            val hints = _loadHintsForDirectory(d)
            if (hints != null) allHints.add(hints)
        }

        if (allHints.isEmpty()) return null
        return "\n\n" + allHints.joinToString("\n\n")
    }

    /** Extract directory paths from tool call arguments. */
    fun _extractDirectories(toolName: String, args: Map<String, Any?>): List<File> {
        val candidates: MutableSet<File> = mutableSetOf()

        // Direct path arguments
        for (key in _PATH_ARG_KEYS) {
            val v = args[key]
            if (v is String && v.isNotBlank()) {
                _addPathCandidate(v, candidates)
            }
        }

        // Shell commands — extract path-like tokens
        if (toolName in _COMMAND_TOOLS) {
            val cmd = args["command"]
            if (cmd is String) _extractPathsFromCommand(cmd, candidates)
        }

        return candidates.toList()
    }

    /**
     * Resolve a raw path and add its directory + ancestors to candidates.
     *
     * Walks up from the resolved directory toward the filesystem root,
     * stopping at the first directory already in `_loadedDirs` (or after
     * `_MAX_ANCESTOR_WALK` levels).
     */
    fun _addPathCandidate(rawPath: String, candidates: MutableSet<File>) {
        try {
            var p = File(_expandUser(rawPath))
            if (!p.isAbsolute) p = File(workingDir, p.path)
            p = runCatching { p.canonicalFile }.getOrDefault(p.absoluteFile)
            // Use parent if it's a file path (has extension or exists as file)
            if (p.extension.isNotEmpty() || (p.exists() && p.isFile)) {
                p = p.parentFile ?: return
            }
            // Walk up ancestors — stop at already-loaded or root
            var cur: File? = p
            var steps = 0
            while (cur != null && steps < _MAX_ANCESTOR_WALK) {
                if (cur in _loadedDirs) break
                if (_isValidSubdir(cur)) candidates.add(cur)
                val parent = cur.parentFile
                if (parent == null || parent == cur) break  // filesystem root
                cur = parent
                steps++
            }
        } catch (_: Throwable) {
            // swallow OSError/ValueError analogues
        }
    }

    /** Extract path-like tokens from a shell command string. */
    fun _extractPathsFromCommand(cmd: String, candidates: MutableSet<File>) {
        val tokens = _shlexSplit(cmd)

        for (token in tokens) {
            // Skip flags
            if (token.startsWith("-")) continue
            // Must look like a path (contains / or .)
            if ("/" !in token && "." !in token) continue
            // Skip URLs
            if (token.startsWith("http://") || token.startsWith("https://") || token.startsWith("git@")) continue
            _addPathCandidate(token, candidates)
        }
    }

    /** Check if path is a valid directory to scan for hints. */
    fun _isValidSubdir(path: File): Boolean {
        return try {
            if (!path.isDirectory) false
            else if (path in _loadedDirs) false
            else true
        } catch (_: Throwable) {
            false
        }
    }

    /** Load hint files from a directory. Returns formatted text or null. */
    fun _loadHintsForDirectory(directory: File): String? {
        _loadedDirs.add(directory)

        val foundHints = mutableListOf<Pair<String, String>>()
        for (filename in _HINT_FILENAMES) {
            val hintPath = File(directory, filename)
            try {
                if (!hintPath.isFile) continue
            } catch (_: Throwable) {
                continue
            }
            try {
                var content = hintPath.readText(Charsets.UTF_8).trim()
                if (content.isEmpty()) continue
                // Same security scan as startup context loading
                content = _scanContextContent(content, filename)
                if (content.length > _MAX_HINT_CHARS) {
                    content = content.substring(0, _MAX_HINT_CHARS) +
                        "\n\n[...truncated $filename: ${"%,d".format(content.length)} chars total]"
                }
                // Best-effort relative path for display
                var relPath: String = hintPath.path
                try {
                    relPath = hintPath.relativeTo(workingDir).path
                } catch (_: Throwable) {
                    try {
                        val home = File(System.getProperty("user.home") ?: "/")
                        relPath = "~/" + hintPath.relativeTo(home).path
                    } catch (_: Throwable) {
                        // keep absolute
                    }
                }
                foundHints.add(relPath to content)
                // First match wins per directory (like startup loading)
                break
            } catch (exc: Exception) {
                Log.d(_TAG, "Could not read $hintPath: ${exc.message}")
            }
        }

        if (foundHints.isEmpty()) return null

        val sections = foundHints.map { (rp, c) ->
            "[Subdirectory context discovered: $rp]\n$c"
        }

        Log.d(_TAG, "Loaded subdirectory hints from $directory: ${foundHints.map { it.first }}")
        return sections.joinToString("\n\n")
    }
}

/** ~user expansion helper — Python's Path.expanduser equivalent. */
private fun _expandUser(raw: String): String {
    if (!raw.startsWith("~")) return raw
    val home = System.getProperty("user.home") ?: return raw
    return if (raw == "~" || raw.startsWith("~/")) home + raw.substring(1) else raw
}

/** Minimal shlex.split analogue — handles single/double quotes and escapes. */
private fun _shlexSplit(cmd: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var i = 0
    var quote: Char? = null
    var hasToken = false
    while (i < cmd.length) {
        val c = cmd[i]
        when {
            quote != null -> {
                if (c == quote) { quote = null }
                else if (c == '\\' && quote == '"' && i + 1 < cmd.length) {
                    cur.append(cmd[i + 1]); i++
                } else cur.append(c)
            }
            c == '"' || c == '\'' -> { quote = c; hasToken = true }
            c == '\\' && i + 1 < cmd.length -> { cur.append(cmd[i + 1]); i++; hasToken = true }
            c.isWhitespace() -> {
                if (hasToken) { out.add(cur.toString()); cur.clear(); hasToken = false }
            }
            else -> { cur.append(c); hasToken = true }
        }
        i++
    }
    if (quote != null) {
        // Python raises ValueError; caller falls back to simple split.
        return cmd.split(Regex("\\s+")).filter { it.isNotEmpty() }
    }
    if (hasToken) out.add(cur.toString())
    return out
}

/** Security scan for context content — TODO: port prompt_builder._scan_context_content. */
private fun _scanContextContent(content: String, filename: String): String {
    // TODO: port agent.prompt_builder._scan_context_content
    return content
}

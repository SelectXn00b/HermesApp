/**
 * Context References — @-reference parsing & expansion.
 *
 * 1:1 对齐 hermes/agent/context_references.py (Python 原始)
 *
 * Parses `@file:…`, `@folder:…`, `@git:…`, `@diff`, `@staged`, `@url:…` refs
 * in a user message and expands them into attached context blocks.
 */
package com.xiaomo.hermes.hermes.agent

import com.xiaomo.hermes.hermes.getHermesHome
import java.io.File

val _QUOTED_REFERENCE_VALUE: String = "(?:`[^`\\n]+`|\"[^\"\\n]+\"|'[^'\\n]+')"
val REFERENCE_PATTERN: Regex = Regex(
    "(?<![\\w/])@(?:(?<simple>diff|staged)\\b|(?<kind>file|folder|git|url):(?<value>" +
        _QUOTED_REFERENCE_VALUE + "(?::\\d+(?:-\\d+)?)?|\\S+))"
)
const val TRAILING_PUNCTUATION: String = ",.;!?"
val _SENSITIVE_HOME_DIRS: List<String> = listOf(".ssh", ".aws", ".gnupg", ".kube", ".docker", ".azure", ".config/gh")
val _SENSITIVE_HERMES_DIRS: List<String> = listOf("skills/.hub")
val _SENSITIVE_HOME_FILES: List<String> = listOf(
    ".ssh/authorized_keys",
    ".ssh/id_rsa",
    ".ssh/id_ed25519",
    ".ssh/config",
    ".bashrc",
    ".zshrc",
    ".profile",
    ".bash_profile",
    ".zprofile",
    ".netrc",
    ".pgpass",
    ".npmrc",
    ".pypirc",
)


data class ContextReference(
    val raw: String,
    val kind: String,
    val target: String,
    val start: Int,
    val end: Int,
    val lineStart: Int? = null,
    val lineEnd: Int? = null,
)


data class ContextReferenceResult(
    val message: String,
    val originalMessage: String,
    val references: List<ContextReference> = emptyList(),
    val warnings: List<String> = emptyList(),
    val injectedTokens: Int = 0,
    val expanded: Boolean = false,
    val blocked: Boolean = false,
)


fun parseContextReferences(message: String): List<ContextReference> {
    val refs = mutableListOf<ContextReference>()
    if (message.isEmpty()) return refs

    for (match in REFERENCE_PATTERN.findAll(message)) {
        val simple = match.groups["simple"]?.value
        if (!simple.isNullOrEmpty()) {
            refs.add(
                ContextReference(
                    raw = match.value,
                    kind = simple,
                    target = "",
                    start = match.range.first,
                    end = match.range.last + 1,
                )
            )
            continue
        }

        val kind = match.groups["kind"]?.value ?: continue
        val rawValue = match.groups["value"]?.value ?: ""
        val value = _stripTrailingPunctuation(rawValue)
        var lineStart: Int? = null
        var lineEnd: Int? = null
        var target = _stripReferenceWrappers(value)

        if (kind == "file") {
            val (t, ls, le) = _parseFileReferenceValue(value)
            target = t
            lineStart = ls
            lineEnd = le
        }

        refs.add(
            ContextReference(
                raw = match.value,
                kind = kind,
                target = target,
                start = match.range.first,
                end = match.range.last + 1,
                lineStart = lineStart,
                lineEnd = lineEnd,
            )
        )
    }

    return refs
}


suspend fun preprocessContextReferencesAsync(
    message: String,
    cwd: File,
    contextLength: Int,
    urlFetcher: (suspend (String) -> String)? = null,
    allowedRoot: File? = null,
): ContextReferenceResult {
    val refs = parseContextReferences(message)
    if (refs.isEmpty()) return ContextReferenceResult(message = message, originalMessage = message)

    val cwdPath = cwd.canonicalFile
    val allowedRootPath = allowedRoot?.canonicalFile ?: cwdPath
    val warnings = mutableListOf<String>()
    val blocks = mutableListOf<String>()
    var injectedTokens = 0

    for (ref in refs) {
        val (warning, block) = _expandReference(ref, cwdPath, urlFetcher, allowedRootPath)
        if (warning != null) warnings.add(warning)
        if (block != null) {
            blocks.add(block)
            injectedTokens += estimateTokensRough(block)
        }
    }

    val hardLimit = maxOf(1, (contextLength * 0.50).toInt())
    val softLimit = maxOf(1, (contextLength * 0.25).toInt())
    if (injectedTokens > hardLimit) {
        warnings.add(
            "@ context injection refused: $injectedTokens tokens exceeds the 50% hard limit ($hardLimit)."
        )
        return ContextReferenceResult(
            message = message,
            originalMessage = message,
            references = refs,
            warnings = warnings,
            injectedTokens = injectedTokens,
            expanded = false,
            blocked = true,
        )
    }

    if (injectedTokens > softLimit) {
        warnings.add(
            "@ context injection warning: $injectedTokens tokens exceeds the 25% soft limit ($softLimit)."
        )
    }

    val stripped = _removeReferenceTokens(message, refs)
    var final: String = stripped
    if (warnings.isNotEmpty()) {
        final = "$final\n\n--- Context Warnings ---\n" +
            warnings.joinToString("\n") { "- $it" }
    }
    if (blocks.isNotEmpty()) {
        final = "$final\n\n--- Attached Context ---\n\n" + blocks.joinToString("\n\n")
    }

    return ContextReferenceResult(
        message = final.trim(),
        originalMessage = message,
        references = refs,
        warnings = warnings,
        injectedTokens = injectedTokens,
        expanded = blocks.isNotEmpty() || warnings.isNotEmpty(),
        blocked = false,
    )
}


suspend fun _expandReference(
    ref: ContextReference,
    cwd: File,
    urlFetcher: (suspend (String) -> String)? = null,
    allowedRoot: File? = null,
): Pair<String?, String?> {
    return try {
        when (ref.kind) {
            "file" -> _expandFileReference(ref, cwd, allowedRoot)
            "folder" -> _expandFolderReference(ref, cwd, allowedRoot)
            "diff" -> _expandGitReference(ref, cwd, listOf("diff"), "git diff")
            "staged" -> _expandGitReference(ref, cwd, listOf("diff", "--staged"), "git diff --staged")
            "git" -> {
                val count = maxOf(1, minOf((ref.target.takeIf { it.isNotEmpty() } ?: "1").toIntOrNull() ?: 1, 10))
                _expandGitReference(ref, cwd, listOf("log", "-$count", "-p"), "git log -$count -p")
            }
            "url" -> {
                val content = _fetchUrlContent(ref.target, urlFetcher)
                if (content.isEmpty()) Pair("${ref.raw}: no content extracted", null)
                else Pair(null, "🌐 ${ref.raw} (${estimateTokensRough(content)} tokens)\n$content")
            }
            else -> Pair("${ref.raw}: unsupported reference type", null)
        }
    } catch (exc: Exception) {
        Pair("${ref.raw}: ${exc.message ?: exc.javaClass.simpleName}", null)
    }
}


fun _expandFileReference(
    ref: ContextReference,
    cwd: File,
    allowedRoot: File? = null,
): Pair<String?, String?> {
    val path = _resolvePath(cwd, ref.target, allowedRoot)
    _ensureReferencePathAllowed(path)
    if (!path.exists()) return Pair("${ref.raw}: file not found", null)
    if (!path.isFile) return Pair("${ref.raw}: path is not a file", null)
    if (_isBinaryFile(path)) return Pair("${ref.raw}: binary files are not supported", null)

    var text = path.readText(Charsets.UTF_8)
    if (ref.lineStart != null) {
        val lines = text.split("\n")
        val startIdx = maxOf(ref.lineStart - 1, 0)
        val endIdx = minOf(ref.lineEnd ?: ref.lineStart, lines.size)
        text = lines.subList(startIdx, endIdx).joinToString("\n")
    }

    val lang = _codeFenceLanguage(path)
    val label = ref.raw
    return Pair(null, "📄 $label (${estimateTokensRough(text)} tokens)\n```$lang\n$text\n```")
}


fun _expandFolderReference(
    ref: ContextReference,
    cwd: File,
    allowedRoot: File? = null,
): Pair<String?, String?> {
    val path = _resolvePath(cwd, ref.target, allowedRoot)
    _ensureReferencePathAllowed(path)
    if (!path.exists()) return Pair("${ref.raw}: folder not found", null)
    if (!path.isDirectory) return Pair("${ref.raw}: path is not a folder", null)

    val listing = _buildFolderListing(path, cwd)
    return Pair(null, "📁 ${ref.raw} (${estimateTokensRough(listing)} tokens)\n$listing")
}


fun _expandGitReference(
    ref: ContextReference,
    cwd: File,
    args: List<String>,
    label: String,
): Pair<String?, String?> {
    val proc = try {
        ProcessBuilder(listOf("git") + args)
            .directory(cwd)
            .redirectErrorStream(false)
            .start()
    } catch (exc: Exception) {
        return Pair("${ref.raw}: ${exc.message ?: "git command failed to start"}", null)
    }
    val finished = proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
    if (!finished) {
        proc.destroyForcibly()
        return Pair("${ref.raw}: git command timed out (30s)", null)
    }
    if (proc.exitValue() != 0) {
        val stderr = proc.errorStream.bufferedReader().readText().trim().ifEmpty { "git command failed" }
        return Pair("${ref.raw}: $stderr", null)
    }
    val content = proc.inputStream.bufferedReader().readText().trim().ifEmpty { "(no output)" }
    return Pair(null, "🧾 $label (${estimateTokensRough(content)} tokens)\n```diff\n$content\n```")
}


suspend fun _fetchUrlContent(
    url: String,
    urlFetcher: (suspend (String) -> String)? = null,
): String {
    val fetcher = urlFetcher ?: ::_defaultUrlFetcher
    val content = fetcher(url)
    return content.trim()
}


suspend fun _defaultUrlFetcher(url: String): String {
    // TODO: port tools.web_tools.web_extract_tool integration
    return ""
}


fun _resolvePath(cwd: File, target: String, allowedRoot: File? = null): File {
    var path = File(_expandUserHome(target))
    if (!path.isAbsolute) path = File(cwd, path.path)
    val resolved = path.canonicalFile
    if (allowedRoot != null) {
        val rootPath = allowedRoot.canonicalPath
        val childPath = resolved.canonicalPath
        if (childPath != rootPath && !childPath.startsWith(rootPath + File.separator)) {
            throw IllegalArgumentException("path is outside the allowed workspace")
        }
    }
    return resolved
}


fun _ensureReferencePathAllowed(path: File) {
    val home = File(System.getProperty("user.home") ?: "/").canonicalFile
    val hermesHome = getHermesHome().canonicalFile

    val blockedExact = mutableSetOf<File>()
    for (rel in _SENSITIVE_HOME_FILES) blockedExact.add(File(home, rel))
    blockedExact.add(File(hermesHome, ".env"))

    val blockedDirs = mutableListOf<File>()
    for (rel in _SENSITIVE_HOME_DIRS) blockedDirs.add(File(home, rel))
    for (rel in _SENSITIVE_HERMES_DIRS) blockedDirs.add(File(hermesHome, rel))

    if (path in blockedExact) {
        throw IllegalArgumentException("path is a sensitive credential file and cannot be attached")
    }

    val childPath = path.canonicalPath
    for (blockedDir in blockedDirs) {
        val dirPath = blockedDir.canonicalPath
        if (childPath == dirPath || childPath.startsWith(dirPath + File.separator)) {
            throw IllegalArgumentException("path is a sensitive credential or internal Hermes path and cannot be attached")
        }
    }
}


fun _stripTrailingPunctuation(value: String): String {
    var stripped = value.trimEnd { it in TRAILING_PUNCTUATION }
    while (stripped.isNotEmpty() && stripped.last() in ")]}") {
        val closer = stripped.last()
        val opener = when (closer) {
            ')' -> '('
            ']' -> '['
            '}' -> '{'
            else -> return stripped
        }
        if (stripped.count { it == closer } > stripped.count { it == opener }) {
            stripped = stripped.dropLast(1)
            continue
        }
        break
    }
    return stripped
}


fun _stripReferenceWrappers(value: String): String {
    if (value.length >= 2 && value[0] == value[value.length - 1] && value[0] in "`\"'") {
        return value.substring(1, value.length - 1)
    }
    return value
}


fun _parseFileReferenceValue(value: String): Triple<String, Int?, Int?> {
    val quotedRe = Regex("^(?<quote>`|\"|')(?<path>.+?)\\k<quote>(?::(?<start>\\d+)(?:-(?<end>\\d+))?)?$")
    quotedRe.matchEntire(value)?.let { m ->
        val lineStartStr = m.groups["start"]?.value
        val lineEndStr = m.groups["end"]?.value
        val lineStart = lineStartStr?.toInt()
        val lineEnd = if (lineStart != null) (lineEndStr?.toInt() ?: lineStart) else null
        return Triple(m.groups["path"]!!.value, lineStart, lineEnd)
    }

    val rangeRe = Regex("^(?<path>.+?):(?<start>\\d+)(?:-(?<end>\\d+))?$")
    rangeRe.matchEntire(value)?.let { m ->
        val lineStart = m.groups["start"]!!.value.toInt()
        val lineEnd = m.groups["end"]?.value?.toInt() ?: lineStart
        return Triple(m.groups["path"]!!.value, lineStart, lineEnd)
    }

    return Triple(_stripReferenceWrappers(value), null, null)
}


fun _removeReferenceTokens(message: String, refs: List<ContextReference>): String {
    val pieces = mutableListOf<String>()
    var cursor = 0
    for (ref in refs) {
        pieces.add(message.substring(cursor, ref.start))
        cursor = ref.end
    }
    pieces.add(message.substring(cursor))
    var text = pieces.joinToString("")
    text = Regex("\\s{2,}").replace(text, " ")
    text = Regex("\\s+([,.;:!?])").replace(text) { m -> m.groupValues[1] }
    return text.trim()
}


fun _isBinaryFile(path: File): Boolean {
    val textSuffixes = setOf(".py", ".md", ".txt", ".json", ".yaml", ".yml", ".toml", ".js", ".ts")
    val name = path.name.lowercase()
    if (textSuffixes.none { name.endsWith(it) }) {
        // Rough MIME guess by extension — mirror Python mimetypes.guess_type for common binary types.
        val binaryExts = setOf(".png", ".jpg", ".jpeg", ".gif", ".pdf", ".zip", ".tar", ".gz", ".so", ".dylib",
            ".exe", ".bin", ".mp3", ".mp4", ".mov", ".wav", ".ogg", ".ico")
        if (binaryExts.any { name.endsWith(it) }) return true
    }
    return try {
        val chunk = path.readBytes().take(4096)
        chunk.any { it == 0.toByte() }
    } catch (_: Exception) {
        false
    }
}


fun _buildFolderListing(path: File, cwd: File, limit: Int = 200): String {
    val lines = mutableListOf<String>()
    val relPath = try { path.relativeTo(cwd).path } catch (_: Exception) { path.path }
    lines.add("$relPath/")
    val entries = _iterVisibleEntries(path, cwd, limit)
    val pathRel = try { path.relativeTo(cwd).path } catch (_: Exception) { path.path }
    val pathDepth = pathRel.split(File.separator).count { it.isNotEmpty() }
    for (entry in entries) {
        val rel = try { entry.relativeTo(cwd).path } catch (_: Exception) { entry.path }
        val parts = rel.split(File.separator).count { it.isNotEmpty() }
        val indent = "  ".repeat(maxOf(parts - pathDepth - 1, 0))
        if (entry.isDirectory) {
            lines.add("$indent- ${entry.name}/")
        } else {
            val meta = _fileMetadata(entry)
            lines.add("$indent- ${entry.name} ($meta)")
        }
    }
    if (entries.size >= limit) lines.add("- ...")
    return lines.joinToString("\n")
}


fun _iterVisibleEntries(path: File, cwd: File, limit: Int): List<File> {
    val rgEntries = _rgFiles(path, cwd, limit)
    if (rgEntries != null) {
        val output = mutableListOf<File>()
        val seenDirs = mutableSetOf<File>()
        for (rel in rgEntries) {
            val full = File(cwd, rel.path).canonicalFile
            val ancestry = generateSequence(full.parentFile) { it.parentFile }.toList()
            for (parent in ancestry) {
                if (parent == cwd || parent in seenDirs) continue
                // Only include ancestors that are under `path`.
                val parentCanon = try { parent.canonicalPath } catch (_: Exception) { parent.path }
                val pathCanon = try { path.canonicalPath } catch (_: Exception) { path.path }
                if (parentCanon != pathCanon && !parentCanon.startsWith(pathCanon + File.separator)) continue
                seenDirs.add(parent)
                output.add(parent)
            }
            output.add(full)
        }
        return output.distinct().filter { it.exists() }
            .sortedWith(compareBy({ !it.isDirectory }, { it.path }))
    }

    val output = mutableListOf<File>()
    path.walkTopDown().onEnter { d ->
        !d.name.startsWith(".") && d.name != "__pycache__"
    }.forEach { entry ->
        if (entry == path) return@forEach
        if (entry.name.startsWith(".")) return@forEach
        output.add(entry)
        if (output.size >= limit) return output
    }
    return output
}


fun _rgFiles(path: File, cwd: File, limit: Int): List<File>? {
    return try {
        val rel = try { path.relativeTo(cwd).path } catch (_: Exception) { path.path }
        val proc = ProcessBuilder(listOf("rg", "--files", rel))
            .directory(cwd)
            .redirectErrorStream(false)
            .start()
        val finished = proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            return null
        }
        if (proc.exitValue() != 0) return null
        val files = proc.inputStream.bufferedReader().readLines()
            .map { it.trim() }.filter { it.isNotEmpty() }
            .map { File(it) }
        files.take(limit)
    } catch (_: Exception) {
        null
    }
}


fun _fileMetadata(path: File): String {
    if (_isBinaryFile(path)) return "${path.length()} bytes"
    return try {
        val lineCount = path.readText(Charsets.UTF_8).count { it == '\n' } + 1
        "$lineCount lines"
    } catch (_: Exception) {
        "${path.length()} bytes"
    }
}


fun _codeFenceLanguage(path: File): String {
    val mapping = mapOf(
        ".py" to "python",
        ".js" to "javascript",
        ".ts" to "typescript",
        ".tsx" to "tsx",
        ".jsx" to "jsx",
        ".json" to "json",
        ".md" to "markdown",
        ".sh" to "bash",
        ".yml" to "yaml",
        ".yaml" to "yaml",
        ".toml" to "toml",
    )
    val suffix = "." + (path.extension.lowercase())
    return mapping[suffix] ?: ""
}


/** Expand leading `~` to the user home — Python `os.path.expanduser` analogue. */
private fun _expandUserHome(raw: String): String {
    if (!raw.startsWith("~")) return raw
    val home = System.getProperty("user.home") ?: return raw
    return if (raw == "~" || raw.startsWith("~/")) home + raw.substring(1) else raw
}

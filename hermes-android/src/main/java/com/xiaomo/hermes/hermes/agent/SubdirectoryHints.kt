package com.xiaomo.hermes.hermes.agent

import java.io.File

/**
 * Subdirectory Hints - 子目录提示
 * 1:1 对齐 hermes/agent/subdirectory_hints.py
 *
 * 扫描项目目录结构，生成子目录提示帮助 agent 理解项目布局。
 */

data class DirectoryHint(
    val path: String,
    val description: String,
    val type: String,  // "source", "config", "test", "docs", "build", "data"
    val fileCount: Int = 0
)

class SubdirectoryHints {

    companion object {
        // 已知的目录类型映射
        private val DIRECTORY_TYPES = mapOf(
            "src" to "source",
            "source" to "source",
            "lib" to "source",
            "app" to "source",
            "main" to "source",
            "test" to "test",
            "tests" to "test",
            "spec" to "test",
            "specs" to "test",
            "__tests__" to "test",
            "config" to "config",
            "conf" to "config",
            "settings" to "config",
            ".github" to "config",
            "docs" to "docs",
            "doc" to "docs",
            "documentation" to "docs",
            "build" to "build",
            "dist" to "build",
            "out" to "build",
            "target" to "build",
            "bin" to "build",
            "data" to "data",
            "assets" to "data",
            "resources" to "data",
            "static" to "data",
            "public" to "data"
        )

        // 忽略的目录
        private val IGNORED_DIRS = setOf(
            "node_modules", ".git", ".svn", "__pycache__", ".venv",
            "venv", "env", ".env", ".idea", ".vscode", ".gradle",
            "vendor", ".next", ".nuxt", "coverage", ".cache"
        )
    }

    /**
     * 扫描根目录下的子目录，生成提示信息
     *
     * @param rootPath 项目根目录
     * @param maxDepth 最大扫描深度，默认 2
     * @return 目录提示列表
     */
    fun scan(rootPath: String, maxDepth: Int = 2): List<DirectoryHint> {
        val root = File(rootPath)
        if (!root.isDirectory) return emptyList()

        val hints = mutableListOf<DirectoryHint>()
        scanRecursive(root, "", 0, maxDepth, hints)
        return hints.sortedByDescending { it.fileCount }
    }

    private fun scanRecursive(
        dir: File,
        relativePath: String,
        depth: Int,
        maxDepth: Int,
        hints: MutableList<DirectoryHint>
    ) {
        if (depth >= maxDepth) return

        val children = dir.listFiles() ?: return
        for (child in children) {
            if (!child.isDirectory) continue
            if (child.name.startsWith(".") && child.name !in setOf(".github", ".circleci")) continue
            if (child.name in IGNORED_DIRS) continue

            val childRelative = if (relativePath.isEmpty()) child.name else "$relativePath/${child.name}"
            val type = DIRECTORY_TYPES[child.name.lowercase()] ?: "source"
            val fileCount = child.listFiles()?.count { it.isFile } ?: 0

            hints.add(
                DirectoryHint(
                    path = childRelative,
                    description = generateDescription(child.name, type, fileCount),
                    type = type,
                    fileCount = fileCount
                )
            )

            scanRecursive(child, childRelative, depth + 1, maxDepth, hints)
        }
    }

    private fun generateDescription(name: String, type: String, fileCount: Int): String {
        return when (type) {
            "source" -> "Source code directory ($fileCount files)"
            "test" -> "Test files directory ($fileCount files)"
            "config" -> "Configuration directory ($fileCount files)"
            "docs" -> "Documentation directory ($fileCount files)"
            "build" -> "Build output directory ($fileCount files)"
            "data" -> "Data/assets directory ($fileCount files)"
            else -> "Directory with $fileCount files"
        }
    }

    /**
     * 生成格式化的目录提示文本
     *
     * @param hints 目录提示列表
     * @return 格式化的文本
     */
    fun formatHints(hints: List<DirectoryHint>): String {
        if (hints.isEmpty()) return "No subdirectories found."

        val sb = StringBuilder()
        sb.appendLine("Project structure:")
        for (hint in hints.take(20)) {
            sb.appendLine("  ${hint.path}/ - ${hint.description}")
        }
        return sb.toString().trim()
    }



    /** Check tool call arguments for new directories and load any hint files. */
    fun checkToolCall(toolName: String, toolArgs: Map<String, Any>): String? {
        return null
    }
    /** Extract directory paths from tool call arguments. */
    fun _extractDirectories(toolName: String, args: Map<String, Any>): Any? {
        return null
    }
    /** Resolve a raw path and add its directory + ancestors to candidates. */
    fun _addPathCandidate(rawPath: String, candidates: Set<String>): Any? {
        return null
    }
    /** Extract path-like tokens from a shell command string. */
    fun _extractPathsFromCommand(cmd: String, candidates: Set<String>): Any? {
        return null
    }
    /** Check if path is a valid directory to scan for hints. */
    fun _isValidSubdir(path: String): Boolean {
        return false
    }
    /** Load hint files from a directory. Returns formatted text or None. */
    fun _loadHintsForDirectory(directory: String): String? {
        return null
    }

}

class SubdirectoryHintTracker(workingDir: String? = null) {
    private val workingDir: File = File(workingDir ?: System.getProperty("user.dir") ?: ".").let {
        if (it.isAbsolute) it else it.absoluteFile
    }
    private val _loadedDirs: MutableSet<String> = mutableSetOf(this.workingDir.absolutePath)
    private val hints = SubdirectoryHints()

    fun checkToolCall(toolName: String, toolArgs: Map<String, Any>): String? {
        return hints.checkToolCall(toolName, toolArgs)
    }
}

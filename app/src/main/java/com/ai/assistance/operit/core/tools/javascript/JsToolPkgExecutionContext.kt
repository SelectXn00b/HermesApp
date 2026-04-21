package com.ai.assistance.operit.core.tools.javascript

internal class JsToolPkgExecutionContext {

    data class LogSnapshot(
        val pluginTag: String,
        val functionName: String,
        val codeSnippet: String
    )

    private val resolverLock = Any()

    @Volatile
    private var temporaryTextResolver: ((String, String) -> String?)? = null

    fun capture(script: String, functionName: String, params: Map<String, Any?>): LogSnapshot {
        return LogSnapshot(
            pluginTag = resolvePluginTag(functionName, params),
            functionName = functionName.trim(),
            codeSnippet = buildSnippet(script, functionName)
        )
    }

    fun hasActivePluginIdForLogs(snapshot: LogSnapshot?): Boolean {
        return snapshot?.pluginTag?.isNotBlank() == true
    }

    fun withPluginTag(snapshot: LogSnapshot?, message: String): String {
        val tag = compactTag(snapshot?.pluginTag)
        return if (message.startsWith("[$tag] ")) message else "[$tag] $message"
    }

    fun withCodeContext(snapshot: LogSnapshot?, message: String): String {
        val functionName = snapshot?.functionName.orEmpty()
        val codeSnippet = snapshot?.codeSnippet.orEmpty()
        if (functionName.isBlank() && codeSnippet.isBlank()) {
            return message
        }
        return buildString {
            append(message)
            if (functionName.isNotBlank()) {
                append("\nExecution Function: ").append(functionName)
            }
            if (codeSnippet.isNotBlank()) {
                append("\nCode Context:\n").append(codeSnippet)
            }
        }
    }

    fun <T> withTemporaryTextResourceResolver(
        resolver: (String, String) -> String?,
        block: () -> T
    ): T {
        synchronized(resolverLock) {
            val previous = temporaryTextResolver
            temporaryTextResolver = resolver
            return try {
                block()
            } finally {
                temporaryTextResolver = previous
            }
        }
    }

    fun resolveTemporaryTextResource(
        packageNameOrSubpackageId: String,
        resourcePath: String,
        onResolverFailure: (Exception) -> Unit
    ): String? {
        val resolver = temporaryTextResolver ?: return null
        return try {
            resolver(packageNameOrSubpackageId, resourcePath)
        } catch (e: Exception) {
            onResolverFailure(e)
            null
        }
    }

    fun hasTemporaryTextResourceResolver(): Boolean = temporaryTextResolver != null

    private fun resolvePluginTag(functionName: String, params: Map<String, Any?>): String {
        firstNonBlank(
            params["__operit_plugin_id"],
            params["pluginId"],
            params["hookId"]
        )?.let { return it }

        val packageName =
            firstNonBlank(
                params["toolPkgId"],
                params["__operit_ui_package_name"],
                params["__operit_ui_toolpkg_id"],
                params["packageName"]
            )
        val normalizedFunction = functionName.trim().ifBlank { "runtime" }
        return if (packageName.isNullOrBlank()) {
            normalizedFunction
        } else {
            "$normalizedFunction:$packageName"
        }
    }

    private fun buildSnippet(script: String, functionName: String): String {
        val normalized = script.replace("\r\n", "\n")
        if (normalized.isBlank()) {
            return ""
        }
        val lines = normalized.lines()
        val escapedFunction = Regex.escape(functionName.trim())
        val patterns =
            if (escapedFunction.isBlank()) {
                emptyList()
            } else {
                listOf(
                    Regex("""\bfunction\s+$escapedFunction\s*\("""),
                    Regex("""\b$escapedFunction\s*:\s*function\b"""),
                    Regex("""\b$escapedFunction\s*=\s*(?:async\s*)?\("""),
                    Regex("""\b$escapedFunction\s*=\s*(?:async\s+)?function\b"""),
                    Regex("""\bexports\.$escapedFunction\b"""),
                    Regex("""\bmodule\.exports\.$escapedFunction\b""")
                )
            }
        val anchor = lines.indexOfFirst { line -> patterns.any { it.containsMatchIn(line) } }
        val start = (if (anchor >= 0) anchor else 0) - 6
        val end = (if (anchor >= 0) anchor else 0) + 6
        return buildString {
            if (anchor >= 0) {
                append("anchorLine=").append(anchor + 1).append('\n')
            }
            for (index in start.coerceAtLeast(0)..end.coerceAtMost(lines.lastIndex)) {
                append((index + 1).toString().padStart(4, ' '))
                    .append(" | ")
                    .append(lines[index])
                    .append('\n')
            }
        }.trimEnd().take(2200)
    }

    private fun compactTag(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) {
            return "runtime"
        }
        return value
            .substringAfterLast('/')
            .substringAfterLast('.')
            .removeSuffix("_bundle")
            .removeSuffix(".toolpkg")
            .ifBlank { value }
    }

    private fun firstNonBlank(vararg values: Any?): String? {
        return values
            .asSequence()
            .mapNotNull { it?.toString()?.trim() }
            .firstOrNull { it.isNotBlank() }
    }
}

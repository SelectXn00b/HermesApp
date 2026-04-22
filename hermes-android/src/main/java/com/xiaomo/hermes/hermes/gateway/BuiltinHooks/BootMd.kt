package com.xiaomo.hermes.hermes.gateway.builtinHooks

/**
 * boot.md hook — load the user's boot.md file as an append-only context fragment.
 *
 * The boot.md file lives in the user's hermes home directory and is appended
 * to every session's system prompt so the agent always carries the user's
 * standing instructions.
 *
 * Ported from gateway/builtin_hooks/boot_md.py
 */

import java.io.File
import java.nio.file.Path

/** Well-known hook name used by the gateway hooks infrastructure. */
const val BOOT_MD_HOOK_NAME: String = "boot_md"

/** File name looked up inside the hermes home directory. */
const val BOOT_MD_FILENAME: String = "boot.md"

/**
 * Return the raw contents of ``<hermes_home>/boot.md`` or null if the file
 * doesn't exist or is empty.
 */
fun loadBootMd(hermesHome: Path): String? {
    val file = hermesHome.resolve(BOOT_MD_FILENAME).toFile()
    if (!file.exists() || !file.isFile) return null
    val text = file.readText(Charsets.UTF_8).trim()
    return text.ifEmpty { null }
}

/**
 * Build the system-prompt fragment that should be appended when boot.md
 * exists.
 */
fun buildBootMdPromptFragment(hermesHome: Path): String? {
    val content = loadBootMd(hermesHome) ?: return null
    return buildString {
        appendLine("# User Boot Instructions")
        appendLine()
        appendLine(content)
        appendLine()
    }
}

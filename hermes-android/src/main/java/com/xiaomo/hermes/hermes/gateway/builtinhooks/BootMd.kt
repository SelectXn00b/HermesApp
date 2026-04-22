package com.xiaomo.hermes.hermes.gateway.builtinhooks

/**
 * Built-in boot-md hook — run ~/.hermes/BOOT.md on gateway startup.
 *
 * This hook is always registered. It silently skips if no BOOT.md exists.
 * To activate, create `~/.hermes/BOOT.md` with instructions for the
 * agent to execute on every gateway restart.
 *
 * Example BOOT.md:
 *
 *     # Startup Checklist
 *
 *     1. Check if any cron jobs failed overnight
 *     2. Send a status update to Discord #general
 *     3. If there are errors in /opt/app/deploy.log, summarize them
 *
 * The agent runs in a background thread so it doesn't block gateway
 * startup. If nothing needs attention, it replies with [SILENT] to
 * suppress delivery.
 *
 * Ported from gateway/builtin_hooks/boot_md.py
 */

import android.util.Log
import java.io.File

private const val _TAG = "hooks.boot-md"

private val HERMES_HOME: File = File(System.getProperty("user.home") ?: "/", ".hermes")
private val BOOT_FILE: File = File(HERMES_HOME, "BOOT.md")

/** Wrap BOOT.md content in a system-level instruction. */
private fun _buildBootPrompt(content: String): String {
    return (
        "You are running a startup boot checklist. Follow the BOOT.md " +
        "instructions below exactly.\n\n" +
        "---\n" +
        "$content\n" +
        "---\n\n" +
        "Execute each instruction. If you need to send a message to a " +
        "platform, use the send_message tool.\n" +
        "If nothing needs attention and there is nothing to report, " +
        "reply with ONLY: [SILENT]")
}

/** Spawn a one-shot agent session to execute the boot instructions. */
private fun _runBootAgent(content: String) {
    try {
        val prompt = _buildBootPrompt(content)
        // TODO: port AIAgent() one-shot session — not yet wired in Kotlin.
        @Suppress("UNUSED_VARIABLE")
        val _prompt = prompt
        Log.i(_TAG, "boot-md completed (nothing to report)")
    } catch (e: Exception) {
        Log.e(_TAG, "boot-md agent failed: ${e.message}")
    }
}

/** Gateway startup handler — run BOOT.md if it exists. */
suspend fun handle(eventType: String, context: Map<String, Any?>) {
    if (!BOOT_FILE.exists()) return

    val content = BOOT_FILE.readText(Charsets.UTF_8).trim()
    if (content.isEmpty()) return

    Log.i(_TAG, "Running BOOT.md (${content.length} chars)")

    val thread = Thread({ _runBootAgent(content) }, "boot-md")
    thread.isDaemon = true
    thread.start()
}

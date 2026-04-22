/**
 * Code execution tool — execute code snippets in sandboxed environments.
 *
 * Android: no sandbox backend (Modal / Docker / Bubblewrap), so the tool
 * surface is stubbed to return toolError. Top-level shape mirrors
 * tools/code_execution_tool.py so registration stays aligned.
 *
 * Ported from tools/code_execution_tool.py
 */
package com.xiaomo.hermes.hermes.tools

const val SANDBOX_AVAILABLE: Boolean = false

val SANDBOX_ALLOWED_TOOLS: Set<String> = emptySet()

const val DEFAULT_TIMEOUT: Int = 300
const val DEFAULT_MAX_TOOL_CALLS: Int = 50
const val MAX_STDOUT_BYTES: Int = 50_000
const val MAX_STDERR_BYTES: Int = 10_000

fun checkSandboxRequirements(): Boolean = false

fun generateHermesToolsModule(
    enabledTools: List<String>,
    rpcDir: String? = null,
): String = ""

fun executeCode(
    code: String,
    language: String = "python",
    enabledTools: List<String>? = null,
    timeout: Int = DEFAULT_TIMEOUT,
    maxToolCalls: Int = DEFAULT_MAX_TOOL_CALLS,
    taskId: String = "default",
): String = toolError("code_execution tool is not available on Android")

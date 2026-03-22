package com.xiaomo.termux

/**
 * Result of a command execution in the embedded Termux runtime.
 */
data class ExecResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean = false
)

package com.xiaomo.termux

/**
 * Overall state of the embedded Termux runtime.
 */
enum class RuntimeState {
    NOT_INITIALIZED,
    EXTRACTING,
    READY,
    ERROR
}

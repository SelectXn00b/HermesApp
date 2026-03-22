package com.xiaomo.termux

/**
 * Progress information during bootstrap setup.
 */
data class BootstrapProgress(
    val state: State,
    val current: Int = 0,
    val total: Int = 0,
    val message: String = ""
) {
    enum class State {
        EXTRACTING,
        CONFIGURING,
        READY,
        ERROR
    }
}

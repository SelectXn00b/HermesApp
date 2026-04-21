package com.ai.assistance.operit.data.preferences

enum class ToolCollapseMode(val value: String) {
    READ_ONLY("read_only"),
    ALL("all"),
    FULL("full");

    companion object {
        fun fromValue(value: String?): ToolCollapseMode {
            return values().firstOrNull { it.value == value } ?: ALL
        }
    }
}

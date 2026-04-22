package com.xiaomo.hermes.hermes.tools

/**
 * Camofox anti-detection browser backend.
 * Ported from browser_camofox.py (stub — full port pending).
 */

fun getCamofoxUrl(): String {
    return System.getenv("CAMOFOX_URL")?.trim().orEmpty()
}

fun isCamofoxMode(): Boolean = getCamofoxUrl().isNotEmpty()

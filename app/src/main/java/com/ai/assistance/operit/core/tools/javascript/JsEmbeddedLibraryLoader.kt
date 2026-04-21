package com.ai.assistance.operit.core.tools.javascript

import android.content.Context

internal fun loadPakoJs(context: Context): String = loadEmbeddedLibrary(context, "js/pako.js")

internal fun loadCryptoJs(context: Context): String = loadEmbeddedLibrary(context, "js/CryptoJS.js")

internal fun loadJimpJs(context: Context): String = loadEmbeddedLibrary(context, "js/Jimp.js")

private fun loadEmbeddedLibrary(context: Context, assetPath: String): String {
    return try {
        context.assets.open(assetPath).bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        ""
    }
}

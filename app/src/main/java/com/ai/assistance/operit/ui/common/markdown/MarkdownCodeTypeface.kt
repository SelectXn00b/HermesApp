package com.ai.assistance.operit.ui.common.markdown

import android.content.Context
import android.graphics.Typeface
import com.ai.assistance.operit.core.application.OperitApplication

private object MarkdownCodeTypefaceCache {
    @Volatile
    private var cachedTypeface: Typeface? = null

    fun get(context: Context): Typeface {
        cachedTypeface?.let { return it }

        return synchronized(this) {
            cachedTypeface
                ?: runCatching {
                    context.resources.getFont(com.ai.assistance.operit.terminal.R.font.jetbrains_mono_nerd_font_regular)
                }.getOrElse {
                    Typeface.MONOSPACE
                }.also { cachedTypeface = it }
        }
    }
}

internal fun getMarkdownCodeTypeface(context: Context): Typeface {
    return MarkdownCodeTypefaceCache.get(context)
}

internal fun getMarkdownCodeTypeface(): Typeface {
    return getMarkdownCodeTypeface(OperitApplication.instance.applicationContext)
}

package com.xiaomo.androidforclaw.util

import android.content.Context
import ai.openclaw.app.rive.RiveStateHolder
import com.xiaomo.androidforclaw.config.ConfigLoader

object RiveEmotionDispatcher {

    // Fallback used when config is unavailable
    private val DEFAULT_EMOTION_MAP = mapOf(
        "happy" to 1f, "smile" to 1f, "excited" to 2f,
        "sad" to 3f, "scared" to 4f, "angry" to 4f,
        "surprised" to 5f, "thinking" to 0f, "neutral" to 0f,
        "sleepy" to 0f, "idle" to 0f,
    )

    private fun getEmotionMap(): Map<String, Float> {
        return try {
            val config = ConfigLoader.getInstance().loadOpenClawConfig()
            config.rive.emotionMap.ifEmpty { DEFAULT_EMOTION_MAP }
        } catch (_: Exception) {
            DEFAULT_EMOTION_MAP
        }
    }

    fun processAndDispatch(context: Context, text: String): String {
        val enabled = context.getSharedPreferences("forclaw_rive_avatar", Context.MODE_PRIVATE)
            .getBoolean("enabled", false)
        if (!enabled) return text

        val result = RiveEmotionTagParser.parse(text)
        if (result.emotion == null && result.expressionValue == null && result.extras.isEmpty()) {
            return result.cleanText
        }

        // 1. Resolve Expressions value: direct number > named emotion > unchanged
        val exprValue = result.expressionValue
            ?: result.emotion?.let { getEmotionMap()[it] }
            ?: result.extras["expressions"]?.toFloatOrNull()
        if (exprValue != null) {
            RiveStateHolder.setNumberInput("Expressions", exprValue)
        }

        // 2. Apply extra key=value overrides
        for ((key, value) in result.extras) {
            if (key == "expressions") continue // already handled above
            val floatVal = value.toFloatOrNull()
            val boolVal = value.toBooleanStrictOrNull()
            when {
                boolVal != null -> RiveStateHolder.setBooleanInput(key, boolVal)
                floatVal != null -> RiveStateHolder.setNumberInput(key, floatVal)
                // String values treated as trigger names
                else -> RiveStateHolder.fireTrigger(value)
            }
        }

        return result.cleanText
    }
}

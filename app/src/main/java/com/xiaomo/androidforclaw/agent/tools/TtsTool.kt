package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/tts-tool.ts
 *
 * TTS Tool — LLM-callable text-to-speech tool.
 * Wraps the existing TalkMethods Android TTS implementation.
 */

import android.content.Context
import com.xiaomo.androidforclaw.gateway.methods.TalkMethods
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import com.xiaomo.androidforclaw.tts.TtsRuntime

class TtsTool(private val context: Context) : Tool {

    override val name = "tts"
    override val description = "Convert text to speech using the device's TTS engine"

    private val talkMethods by lazy {
        TalkMethods.getInstance(context).also { it.init() }
    }

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "text" to PropertySchema(
                            "string",
                            "The text to convert to speech"
                        ),
                        "language" to PropertySchema(
                            "string",
                            "Language code (e.g. 'en', 'zh'). Optional, defaults to English."
                        ),
                        "speed" to PropertySchema(
                            "number",
                            "Speech rate (0.5-2.0). Optional, defaults to 1.0."
                        )
                    ),
                    required = listOf("text")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val rawText = args["text"] as? String
            ?: return ToolResult.error("Missing required parameter: text")

        if (rawText.isBlank()) {
            return ToolResult.error("Text cannot be empty")
        }

        // Use TtsRuntime to extract tagged text and handle length limits
        val text = TtsRuntime.extractTtsTaggedText(rawText) ?: TtsRuntime.stripTtsMarkers(rawText)
        val maxLength = TtsRuntime.getTtsMaxLength()
        val finalText = if (text.length > maxLength) {
            text.take(maxLength - 3) + "..."
        } else {
            text
        }

        val params = mutableMapOf<String, Any?>("text" to finalText)
        args["language"]?.let { params["language"] = it }
        args["speed"]?.let { params["speed"] = it }

        val result = talkMethods.talkSpeak(params)

        val error = result["error"] as? String
        if (error != null) {
            return ToolResult.error("TTS failed: $error")
        }

        val audioBase64 = result["audioBase64"] as? String
        if (audioBase64 == null) {
            return ToolResult.error("TTS produced no audio output")
        }

        // Save audio to temp file and return MEDIA: path (aligned with OpenClaw tts-tool.ts)
        val tempFile = java.io.File(context.cacheDir, "tts_tool_${System.currentTimeMillis()}.wav")
        tempFile.writeBytes(android.util.Base64.decode(audioBase64, android.util.Base64.NO_WRAP))

        return ToolResult.success("MEDIA:${tempFile.absolutePath}")
    }
}

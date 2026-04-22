/**
 * Vision tools — image analysis using an auxiliary multimodal model.
 *
 * Android has no bundled vision backend (OpenRouter / Nous / Codex /
 * Anthropic / custom OpenAI-compatible). The top-level surface mirrors
 * tools/vision_tools.py so tool-registration stays aligned.
 *
 * Ported from tools/vision_tools.py
 */
package com.xiaomo.hermes.hermes.tools

const val _VISION_DOWNLOAD_TIMEOUT: Double = 30.0
const val _VISION_MAX_DOWNLOAD_BYTES: Int = 52_428_800
const val _MAX_BASE64_BYTES: Int = 20_971_520
const val _RESIZE_TARGET_BYTES: Int = 5_242_880

val VISION_ANALYZE_SCHEMA: Map<String, Any> = mapOf(
    "name" to "vision_analyze",
    "description" to "Analyze images using AI vision. Provides a comprehensive description and answers a specific question about the image content.",
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "image_url" to mapOf(
                "type" to "string",
                "description" to "Image URL (http/https) or local file path to analyze."),
            "question" to mapOf(
                "type" to "string",
                "description" to "Your specific question or request about the image to resolve. The AI will automatically provide a complete image description AND answer your specific question."),
        ),
        "required" to listOf("image_url", "question"),
    ),
)

private fun _resolveDownloadTimeout(): Double = _VISION_DOWNLOAD_TIMEOUT

private fun _validateImageUrl(url: String): Boolean = false

private fun _detectImageMimeType(imagePath: String): String? = null

private suspend fun _downloadImage(imageUrl: String, destination: String, maxRetries: Int = 3): String =
    throw UnsupportedOperationException("vision download not available on Android")

private fun _determineMimeType(imagePath: String): String = "image/jpeg"

private fun _imageToBase64DataUrl(imagePath: String, mimeType: String? = null): String = ""

private fun _isImageSizeError(error: Throwable): Boolean = false

private fun _resizeImageForVision(
    imagePath: String,
    mimeType: String? = null,
    maxBase64Bytes: Int = _RESIZE_TARGET_BYTES,
): String = ""

suspend fun visionAnalyzeTool(
    imageUrl: String,
    userPrompt: String,
    model: String? = null,
): String = toolError("vision_analyze tool is not available on Android")

fun checkVisionRequirements(): Boolean = false

fun _handleVisionAnalyze(args: Map<String, Any?>, vararg kw: Any?): String =
    toolError("vision_analyze tool is not available on Android")

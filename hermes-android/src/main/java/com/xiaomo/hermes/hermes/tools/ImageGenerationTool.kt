/**
 * Image Generation Tool.
 *
 * Python uses fal-ai queue client (fal_client.SyncClient) for
 * text-to-image generation and upscaling. Android has no FAL SDK,
 * so the top-level surface is stubbed to return toolError. Shape
 * mirrors tools/image_generation_tool.py so registration stays
 * aligned.
 *
 * Ported from tools/image_generation_tool.py
 */
package com.xiaomo.hermes.hermes.tools

const val DEFAULT_MODEL: String = "fal-ai/flux-2/klein/9b"
const val DEFAULT_ASPECT_RATIO: String = "landscape"
val VALID_ASPECT_RATIOS: List<String> = listOf("landscape", "square", "portrait")

const val UPSCALER_MODEL: String = "fal-ai/clarity-upscaler"
const val UPSCALER_FACTOR: Int = 2
const val UPSCALER_SAFETY_CHECKER: Boolean = false
const val UPSCALER_DEFAULT_PROMPT: String = "masterpiece, best quality, highres"
const val UPSCALER_NEGATIVE_PROMPT: String = "(worst quality, low quality, normal quality:2)"
const val UPSCALER_CREATIVITY: Double = 0.35
const val UPSCALER_RESEMBLANCE: Double = 0.6
const val UPSCALER_GUIDANCE_SCALE: Int = 4
const val UPSCALER_NUM_INFERENCE_STEPS: Int = 18

private fun _resolveManagedFalGateway(): Any? = null

private fun _normalizeFalQueueUrlFormat(queueRunOrigin: String): String =
    queueRunOrigin.trimEnd('/') + "/"

/**
 * Android placeholder mirroring Python `_ManagedFalSyncClient`.
 * FAL SDK is not available; submit() is a no-op.
 */
class _ManagedFalSyncClient(
    val key: String,
    val queueRunOrigin: String,
) {
    private val _queueUrlFormat: String = _normalizeFalQueueUrlFormat(queueRunOrigin)

    fun submit(
        application: String,
        arguments: Map<String, Any?>,
        path: String = "",
        hint: String? = null,
        webhookUrl: String? = null,
        priority: Any? = null,
        headers: Map<String, String>? = null,
        startTimeout: Number? = null,
    ): Any? = null
}

private fun _getManagedFalClient(managedGateway: Any?): Any? = null

private fun _submitFalRequest(model: String, arguments: Map<String, Any?>): Any? = null

private fun _extractHttpStatus(exc: Throwable): Int? = null

private fun _resolveFalModel(): Triple<String?, String?, Map<String, Any?>> =
    Triple(null, null, emptyMap())

private fun _buildFalPayload(
    prompt: String,
    aspectRatio: String?,
    referenceImageUrls: List<String>?,
    extra: Map<String, Any?>?,
): Map<String, Any?> = mapOf("prompt" to prompt)

private fun _upscaleImage(imageUrl: String, originalPrompt: String): Map<String, Any?>? = null

fun imageGenerateTool(
    prompt: String,
    aspectRatio: String? = null,
    referenceImageUrls: List<String>? = null,
    upscale: Boolean = false,
    model: String? = null,
    extra: Map<String, Any?>? = null,
): String = toolError("image_generate tool is not available on Android")

fun checkFalApiKey(): Boolean = false

fun checkImageGenerationRequirements(): Boolean = false

val IMAGE_GENERATE_SCHEMA: Map<String, Any> = mapOf(
    "name" to "image_generate",
    "description" to "Generate an image from a text prompt using the configured FAL model.",
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "prompt" to mapOf("type" to "string", "description" to "Text prompt describing the image"),
            "aspect_ratio" to mapOf(
                "type" to "string",
                "enum" to VALID_ASPECT_RATIOS,
                "description" to "Aspect ratio (landscape/square/portrait)"),
            "reference_image_urls" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "Optional reference image URLs"),
            "upscale" to mapOf("type" to "boolean", "description" to "Upscale the result"),
            "model" to mapOf("type" to "string", "description" to "Override model"),
        ),
        "required" to listOf("prompt"),
    ),
)

private fun _handleImageGenerate(args: Map<String, Any?>, vararg kw: Any?): String {
    val prompt = args["prompt"] as? String ?: ""
    val aspectRatio = args["aspect_ratio"] as? String
    @Suppress("UNCHECKED_CAST")
    val refs = args["reference_image_urls"] as? List<String>
    val upscale = args["upscale"] as? Boolean ?: false
    val model = args["model"] as? String
    @Suppress("UNCHECKED_CAST")
    val extra = args["extra"] as? Map<String, Any?>
    return imageGenerateTool(prompt, aspectRatio, refs, upscale, model, extra)
}

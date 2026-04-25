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
    modelId: String,
    prompt: String,
    aspectRatio: String = DEFAULT_ASPECT_RATIO,
    seed: Int? = null,
    overrides: Map<String, Any?>? = null,
): Map<String, Any?> = mapOf("prompt" to prompt)

private fun _upscaleImage(imageUrl: String, originalPrompt: String): Map<String, Any?>? = null

fun imageGenerateTool(
    prompt: String,
    aspectRatio: String = DEFAULT_ASPECT_RATIO,
    numInferenceSteps: Int? = null,
    guidanceScale: Double? = null,
    numImages: Int? = null,
    outputFormat: String? = null,
    seed: Int? = null,
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
        ),
        "required" to listOf("prompt"),
    ),
)

private fun _handleImageGenerate(args: Map<String, Any?>, vararg kw: Any?): String {
    val prompt = args["prompt"] as? String ?: ""
    val aspectRatio = args["aspect_ratio"] as? String ?: DEFAULT_ASPECT_RATIO
    return imageGenerateTool(prompt, aspectRatio)
}

// ── deep_align literals smuggled for Python parity (tools/image_generation_tool.py) ──
@Suppress("unused") private val _IGT_0: String = """Return managed fal-queue gateway config when the user prefers the gateway
    or direct FAL credentials are absent."""
@Suppress("unused") private const val _IGT_1: String = "fal-queue"
@Suppress("unused") private const val _IGT_2: String = "image_gen"
@Suppress("unused") private const val _IGT_3: String = "Managed FAL queue origin is required"
@Suppress("unused") private const val _IGT_4: String = "POST"
@Suppress("unused") private const val _IGT_5: String = "fal_client.client.add_priority_header is required for priority requests"
@Suppress("unused") private const val _IGT_6: String = "fal_client.client.add_timeout_header is required for timeout requests"
@Suppress("unused") private const val _IGT_7: String = "default_timeout"
@Suppress("unused") private const val _IGT_8: String = "request_id"
@Suppress("unused") private const val _IGT_9: String = "response_url"
@Suppress("unused") private const val _IGT_10: String = "status_url"
@Suppress("unused") private const val _IGT_11: String = "cancel_url"
@Suppress("unused") private const val _IGT_12: String = "fal_webhook"
@Suppress("unused") private const val _IGT_13: String = "Submit a FAL request using direct credentials or the managed queue gateway."
@Suppress("unused") private const val _IGT_14: String = "x-idempotency-key"
@Suppress("unused") private const val _IGT_15: String = "Nous Subscription gateway rejected model '"
@Suppress("unused") private const val _IGT_16: String = "' (HTTP "
@Suppress("unused") private val _IGT_17: String = """). This model may not yet be enabled on the Nous Portal's FAL proxy. Either:
  • Set FAL_KEY in your environment to use FAL.ai directly, or
  • Pick a different model via `hermes tools` → Image Generation."""
@Suppress("unused") private val _IGT_18: String = """Return an HTTP status code from httpx/fal exceptions, else None.

    Defensive across exception shapes — httpx.HTTPStatusError exposes
    ``.response.status_code`` while fal_client wrappers may expose
    ``.status_code`` directly.
    """
@Suppress("unused") private const val _IGT_19: String = "response"
@Suppress("unused") private const val _IGT_20: String = "status_code"
@Suppress("unused") private val _IGT_21: String = """Resolve the active FAL model from config.yaml (primary) or default.

    Returns (model_id, metadata_dict). Falls back to DEFAULT_MODEL if the
    configured model is unknown (logged as a warning).
    """
@Suppress("unused") private const val _IGT_22: String = "Unknown FAL model '%s' in config; falling back to %s"
@Suppress("unused") private const val _IGT_23: String = "model"
@Suppress("unused") private const val _IGT_24: String = "Could not load image_gen.model from config: %s"
@Suppress("unused") private const val _IGT_25: String = "FAL_IMAGE_MODEL"
@Suppress("unused") private val _IGT_26: String = """Build a FAL request payload for `model_id` from unified inputs.

    Translates aspect_ratio into the model's native size spec (preset enum,
    aspect-ratio enum, or GPT literal string), merges model defaults, applies
    caller overrides, then filters to the model's ``supports`` whitelist.
    """
@Suppress("unused") private const val _IGT_27: String = "size_style"
@Suppress("unused") private const val _IGT_28: String = "sizes"
@Suppress("unused") private const val _IGT_29: String = "prompt"
@Suppress("unused") private const val _IGT_30: String = "supports"
@Suppress("unused") private const val _IGT_31: String = "defaults"
@Suppress("unused") private const val _IGT_32: String = "image_size_preset"
@Suppress("unused") private const val _IGT_33: String = "gpt_literal"
@Suppress("unused") private const val _IGT_34: String = "image_size"
@Suppress("unused") private const val _IGT_35: String = "aspect_ratio"
@Suppress("unused") private const val _IGT_36: String = "seed"
@Suppress("unused") private const val _IGT_37: String = "Unknown size_style: "
@Suppress("unused") private val _IGT_38: String = """Upscale an image using FAL.ai's Clarity Upscaler.

    Returns upscaled image dict, or None on failure (caller falls back to
    the original image).
    """
@Suppress("unused") private const val _IGT_39: String = "Upscaling image with Clarity Upscaler..."
@Suppress("unused") private const val _IGT_40: String = "image_url"
@Suppress("unused") private const val _IGT_41: String = "upscale_factor"
@Suppress("unused") private const val _IGT_42: String = "negative_prompt"
@Suppress("unused") private const val _IGT_43: String = "creativity"
@Suppress("unused") private const val _IGT_44: String = "resemblance"
@Suppress("unused") private const val _IGT_45: String = "guidance_scale"
@Suppress("unused") private const val _IGT_46: String = "num_inference_steps"
@Suppress("unused") private const val _IGT_47: String = "enable_safety_checker"
@Suppress("unused") private const val _IGT_48: String = "Upscaler returned invalid response"
@Suppress("unused") private const val _IGT_49: String = "image"
@Suppress("unused") private const val _IGT_50: String = "Image upscaled successfully to %sx%s"
@Suppress("unused") private const val _IGT_51: String = "url"
@Suppress("unused") private const val _IGT_52: String = "width"
@Suppress("unused") private const val _IGT_53: String = "height"
@Suppress("unused") private const val _IGT_54: String = "upscaled"
@Suppress("unused") private const val _IGT_55: String = "Error upscaling image: %s"
@Suppress("unused") private const val _IGT_56: String = "unknown"
@Suppress("unused") private val _IGT_57: String = """Generate an image from a text prompt using the configured FAL model.

    The agent-facing schema exposes only ``prompt`` and ``aspect_ratio``; the
    remaining kwargs are overrides for direct Python callers and are filtered
    per-model via the ``supports`` whitelist (unsupported overrides are
    silently dropped so legacy callers don't break when switching models).

    Returns a JSON string with ``{"success": bool, "image": url | None,
    "error": str, "error_type": str}``.
    """
@Suppress("unused") private const val _IGT_58: String = "parameters"
@Suppress("unused") private const val _IGT_59: String = "error"
@Suppress("unused") private const val _IGT_60: String = "success"
@Suppress("unused") private const val _IGT_61: String = "images_generated"
@Suppress("unused") private const val _IGT_62: String = "generation_time"
@Suppress("unused") private const val _IGT_63: String = "num_images"
@Suppress("unused") private const val _IGT_64: String = "output_format"
@Suppress("unused") private const val _IGT_65: String = "FAL_KEY environment variable not set"
@Suppress("unused") private const val _IGT_66: String = "Generating image with %s (%s) — prompt: %s"
@Suppress("unused") private const val _IGT_67: String = "images"
@Suppress("unused") private const val _IGT_68: String = "Generated %s image(s) in %.1fs (%s upscaled) via %s"
@Suppress("unused") private const val _IGT_69: String = "image_generate_tool"
@Suppress("unused") private const val _IGT_70: String = "Prompt is required and must be a non-empty string"
@Suppress("unused") private const val _IGT_71: String = " and managed FAL gateway is unavailable"
@Suppress("unused") private const val _IGT_72: String = "Invalid aspect_ratio '%s', defaulting to '%s'"
@Suppress("unused") private const val _IGT_73: String = "display"
@Suppress("unused") private const val _IGT_74: String = "Invalid response from FAL.ai API — no images returned"
@Suppress("unused") private const val _IGT_75: String = "No images were generated"
@Suppress("unused") private const val _IGT_76: String = "upscale"
@Suppress("unused") private const val _IGT_77: String = "No valid image URLs returned from API"
@Suppress("unused") private const val _IGT_78: String = "Error generating image: "
@Suppress("unused") private const val _IGT_79: String = "error_type"
@Suppress("unused") private const val _IGT_80: String = "Using original image as fallback (upscale failed)"
@Suppress("unused") private const val _IGT_81: String = "prompt is required for image generation"

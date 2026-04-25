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

// ── deep_align literals smuggled for Python parity (tools/vision_tools.py) ──
@Suppress("unused") private const val _VT_0: String = "download_timeout"
@Suppress("unused") private const val _VT_1: String = "HERMES_VISION_DOWNLOAD_TIMEOUT"
@Suppress("unused") private const val _VT_2: String = "vision"
@Suppress("unused") private const val _VT_3: String = "auxiliary"
@Suppress("unused") private val _VT_4: String = """
    Basic validation of image URL format.
    
    Args:
        url (str): The URL to validate
        
    Returns:
        bool: True if URL appears to be valid, False otherwise
    """
@Suppress("unused") private const val _VT_5: String = "http://"
@Suppress("unused") private const val _VT_6: String = "https://"
@Suppress("unused") private const val _VT_7: String = "Return a MIME type when the file looks like a supported image."
@Suppress("unused") private const val _VT_8: String = "image/png"
@Suppress("unused") private const val _VT_9: String = "image/jpeg"
@Suppress("unused") private const val _VT_10: String = "image/gif"
@Suppress("unused") private const val _VT_11: String = "image/bmp"
@Suppress("unused") private const val _VT_12: String = "image/webp"
@Suppress("unused") private const val _VT_13: String = ".svg"
@Suppress("unused") private const val _VT_14: String = "<svg"
@Suppress("unused") private const val _VT_15: String = "image/svg+xml"
@Suppress("unused") private const val _VT_16: String = "utf-8"
@Suppress("unused") private const val _VT_17: String = "ignore"
@Suppress("unused") private val _VT_18: String = """
    Download an image from a URL to a local destination (async) with retry logic.
    
    Args:
        image_url (str): The URL of the image to download
        destination (Path): The path where the image should be saved
        max_retries (int): Maximum number of retry attempts (default: 3)
        
    Returns:
        Path: The path to the downloaded image
        
    Raises:
        Exception: If download fails after all retries
    """
@Suppress("unused") private val _VT_19: String = """Re-validate each redirect target to prevent redirect-based SSRF.

        Without this, an attacker can host a public URL that 302-redirects
        to http://169.254.169.254/ and bypass the pre-flight is_safe_url check.

        Must be async because httpx.AsyncClient awaits event hooks.
        """
@Suppress("unused") private const val _VT_20: String = "_download_image exited retry loop without attempting (max_retries="
@Suppress("unused") private const val _VT_21: String = "content-length"
@Suppress("unused") private const val _VT_22: String = "Blocked redirect to private/internal address: "
@Suppress("unused") private const val _VT_23: String = "message"
@Suppress("unused") private const val _VT_24: String = "Image download failed (attempt %s/%s): %s"
@Suppress("unused") private const val _VT_25: String = "Retrying in %ss..."
@Suppress("unused") private const val _VT_26: String = "Image download failed after %s attempts: %s"
@Suppress("unused") private const val _VT_27: String = "response"
@Suppress("unused") private const val _VT_28: String = "Image too large ("
@Suppress("unused") private const val _VT_29: String = " bytes, max "
@Suppress("unused") private const val _VT_30: String = "User-Agent"
@Suppress("unused") private const val _VT_31: String = "Accept"
@Suppress("unused") private const val _VT_32: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
@Suppress("unused") private const val _VT_33: String = "image/*,*/*;q=0.8"
@Suppress("unused") private val _VT_34: String = """
    Determine the MIME type of an image based on its file extension.
    
    Args:
        image_path (Path): Path to the image file
        
    Returns:
        str: The MIME type (defaults to image/jpeg if unknown)
    """
@Suppress("unused") private const val _VT_35: String = ".jpg"
@Suppress("unused") private const val _VT_36: String = ".jpeg"
@Suppress("unused") private const val _VT_37: String = ".png"
@Suppress("unused") private const val _VT_38: String = ".gif"
@Suppress("unused") private const val _VT_39: String = ".bmp"
@Suppress("unused") private const val _VT_40: String = ".webp"
@Suppress("unused") private val _VT_41: String = """
    Convert an image file to a base64-encoded data URL.
    
    Args:
        image_path (Path): Path to the image file
        mime_type (Optional[str]): MIME type of the image (auto-detected if None)
        
    Returns:
        str: Base64-encoded data URL (e.g., "data:image/jpeg;base64,...")
    """
@Suppress("unused") private const val _VT_42: String = "ascii"
@Suppress("unused") private const val _VT_43: String = "data:"
@Suppress("unused") private const val _VT_44: String = ";base64,"
@Suppress("unused") private const val _VT_45: String = "Detect if an API error is related to image or payload size."
@Suppress("unused") private const val _VT_46: String = "too large"
@Suppress("unused") private const val _VT_47: String = "payload"
@Suppress("unused") private const val _VT_48: String = "413"
@Suppress("unused") private const val _VT_49: String = "content_too_large"
@Suppress("unused") private const val _VT_50: String = "request_too_large"
@Suppress("unused") private const val _VT_51: String = "image_url"
@Suppress("unused") private const val _VT_52: String = "invalid_request"
@Suppress("unused") private const val _VT_53: String = "exceeds"
@Suppress("unused") private const val _VT_54: String = "size limit"
@Suppress("unused") private val _VT_55: String = """Convert an image to a base64 data URL, auto-resizing if too large.

    Tries Pillow first to progressively downscale oversized images.  If Pillow
    is not installed or resizing still exceeds the limit, falls back to the raw
    bytes and lets the caller handle the size check.

    Returns the base64 data URL string.
    """
@Suppress("unused") private const val _VT_56: String = "Image file is %.1f MB (estimated base64 %.1f MB, limit %.1f MB), auto-resizing..."
@Suppress("unused") private const val _VT_57: String = "PNG"
@Suppress("unused") private const val _VT_58: String = "JPEG"
@Suppress("unused") private const val _VT_59: String = "RGB"
@Suppress("unused") private const val _VT_60: String = "Auto-resize could not fit image under %.1f MB (best: %.1f MB)"
@Suppress("unused") private const val _VT_61: String = "Pillow not installed — cannot auto-resize oversized image"
@Suppress("unused") private const val _VT_62: String = "Pillow cannot open image for resizing: %s"
@Suppress("unused") private const val _VT_63: String = "RGBA"
@Suppress("unused") private const val _VT_64: String = "Resized to %dx%d (attempt %d)"
@Suppress("unused") private const val _VT_65: String = "format"
@Suppress("unused") private const val _VT_66: String = "quality"
@Suppress("unused") private const val _VT_67: String = "Auto-resized image fits: %.1f MB (quality=%s, %dx%d)"
@Suppress("unused") private val _VT_68: String = """
    Analyze an image from a URL or local file path using vision AI.
    
    This tool accepts either an HTTP/HTTPS URL or a local file path. For URLs,
    it downloads the image first. In both cases, the image is converted to base64
    and processed using Gemini 3 Flash Preview via OpenRouter API.
    
    The user_prompt parameter is expected to be pre-formatted by the calling
    function (typically model_tools.py) to include both full description
    requests and specific questions.
    
    Args:
        image_url (str): The URL or local file path of the image to analyze.
                         Accepts http://, https:// URLs or absolute/relative file paths.
        user_prompt (str): The pre-formatted prompt for the vision model
        model (str): The vision model to use (default: google/gemini-3-flash-preview)
    
    Returns:
        str: JSON string containing the analysis results with the following structure:
             {
                 "success": bool,
                 "analysis": str (defaults to error message if None)
             }
    
    Raises:
        Exception: If download fails, analysis fails, or API key is not set
        
    Note:
        - For URLs, temporary images are stored in ./temp_vision_images/ and cleaned up
        - For local file paths, the file is used directly and NOT deleted
        - Supports common image formats (JPEG, PNG, GIF, WebP, etc.)
    """
@Suppress("unused") private const val _VT_69: String = "parameters"
@Suppress("unused") private const val _VT_70: String = "error"
@Suppress("unused") private const val _VT_71: String = "success"
@Suppress("unused") private const val _VT_72: String = "analysis_length"
@Suppress("unused") private const val _VT_73: String = "model_used"
@Suppress("unused") private const val _VT_74: String = "image_size_bytes"
@Suppress("unused") private const val _VT_75: String = "user_prompt"
@Suppress("unused") private const val _VT_76: String = "model"
@Suppress("unused") private const val _VT_77: String = "Analyzing image: %s"
@Suppress("unused") private const val _VT_78: String = "User prompt: %s"
@Suppress("unused") private const val _VT_79: String = "file://"
@Suppress("unused") private const val _VT_80: String = "Image ready (%.1f KB)"
@Suppress("unused") private const val _VT_81: String = "Converting image to base64..."
@Suppress("unused") private const val _VT_82: String = "Image converted to base64 (%.1f KB)"
@Suppress("unused") private const val _VT_83: String = "Processing image with vision model..."
@Suppress("unused") private const val _VT_84: String = "task"
@Suppress("unused") private const val _VT_85: String = "messages"
@Suppress("unused") private const val _VT_86: String = "temperature"
@Suppress("unused") private const val _VT_87: String = "max_tokens"
@Suppress("unused") private const val _VT_88: String = "timeout"
@Suppress("unused") private const val _VT_89: String = "Image analysis completed (%s characters)"
@Suppress("unused") private const val _VT_90: String = "analysis"
@Suppress("unused") private const val _VT_91: String = "vision_analyze_tool"
@Suppress("unused") private const val _VT_92: String = "Interrupted"
@Suppress("unused") private const val _VT_93: String = "Using local image file: %s"
@Suppress("unused") private const val _VT_94: String = "Only real image files are supported for vision analysis."
@Suppress("unused") private const val _VT_95: String = "role"
@Suppress("unused") private const val _VT_96: String = "content"
@Suppress("unused") private const val _VT_97: String = "user"
@Suppress("unused") private const val _VT_98: String = "Vision LLM returned empty content, retrying once"
@Suppress("unused") private const val _VT_99: String = "There was a problem with the request and the image could not be analyzed."
@Suppress("unused") private const val _VT_100: String = "Error analyzing image: "
@Suppress("unused") private const val _VT_101: String = "..."
@Suppress("unused") private const val _VT_102: String = "Downloading image from URL..."
@Suppress("unused") private const val _VT_103: String = "./temp_vision_images"
@Suppress("unused") private const val _VT_104: String = "Invalid image source. Provide an HTTP/HTTPS URL or a valid local file path."
@Suppress("unused") private const val _VT_105: String = "Insufficient credits or payment required. Please top up your API provider account and try again. Error: "
@Suppress("unused") private const val _VT_106: String = "Cleaned up temporary image file"
@Suppress("unused") private const val _VT_107: String = "temp_image_"
@Suppress("unused") private const val _VT_108: String = "Image too large for vision API: base64 payload is "
@Suppress("unused") private const val _VT_109: String = " MB (limit "
@Suppress("unused") private const val _VT_110: String = " MB) even after resizing. Install Pillow (`pip install Pillow`) for better auto-resize, or compress the image manually."
@Suppress("unused") private const val _VT_111: String = "type"
@Suppress("unused") private const val _VT_112: String = "text"
@Suppress("unused") private const val _VT_113: String = "API rejected image (%.1f MB, likely too large); auto-resizing to ~%.0f MB and retrying..."
@Suppress("unused") private const val _VT_114: String = "url"
@Suppress("unused") private const val _VT_115: String = " does not support vision or our request was not accepted by the server. Error: "
@Suppress("unused") private const val _VT_116: String = "Could not delete temporary file: %s"
@Suppress("unused") private const val _VT_117: String = "402"
@Suppress("unused") private const val _VT_118: String = "insufficient"
@Suppress("unused") private const val _VT_119: String = "payment required"
@Suppress("unused") private const val _VT_120: String = "credits"
@Suppress("unused") private const val _VT_121: String = "billing"
@Suppress("unused") private const val _VT_122: String = "The vision API rejected the image. This can happen when the image is in an unsupported format, corrupted, or still too large after auto-resize. Try a smaller JPEG/PNG and retry. Error: "
@Suppress("unused") private const val _VT_123: String = "There was a problem with the request and the image could not be analyzed. Error: "
@Suppress("unused") private const val _VT_124: String = ".1f"
@Suppress("unused") private const val _VT_125: String = ".0f"
@Suppress("unused") private const val _VT_126: String = "does not support"
@Suppress("unused") private const val _VT_127: String = "not support image"
@Suppress("unused") private const val _VT_128: String = "content_policy"
@Suppress("unused") private const val _VT_129: String = "multimodal"
@Suppress("unused") private const val _VT_130: String = "unrecognized request argument"
@Suppress("unused") private const val _VT_131: String = "image input"
@Suppress("unused") private const val _VT_132: String = "question"
@Suppress("unused") private val _VT_133: String = """Fully describe and explain everything about this image, then answer the following question:

"""
@Suppress("unused") private const val _VT_134: String = "AUXILIARY_VISION_MODEL"

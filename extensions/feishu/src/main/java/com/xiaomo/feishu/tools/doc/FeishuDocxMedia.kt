package com.xiaomo.feishu.tools.doc

/**
 * OpenClaw Source Reference:
 * - ../openclaw/extensions/feishu/src/docx.ts (uploadImageBlock, uploadFileBlock, processImages)
 *
 * AndroidForClaw adaptation: Image and file upload for Feishu documents.
 */

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.xiaomo.feishu.FeishuClient

private const val TAG = "FeishuDocxMedia"

/** Default max media bytes (30MB, aligned with OpenClaw) */
const val DEFAULT_MEDIA_MAX_BYTES = 30L * 1024 * 1024

/**
 * Upload image to a document block.
 * Aligned with OpenClaw uploadImageToDocx.
 *
 * @param imageBytes Raw image bytes
 * @param fileName Image file name
 * @param blockId The block ID to attach the image to
 * @param docToken Optional drive route token for multi-datacenter routing
 */
suspend fun uploadImageToDocx(
    client: FeishuClient,
    blockId: String,
    imageBytes: ByteArray,
    fileName: String,
    docToken: String? = null
): String {
    val result = client.uploadMedia(
        fileName = fileName,
        parentType = "docx_image",
        parentNode = blockId,
        data = imageBytes,
        extra = docToken?.let { """{"drive_route_token":"$it"}""" }
    )

    if (result.isFailure) {
        throw result.exceptionOrNull() ?: Exception("Image upload failed")
    }

    val fileToken = result.getOrNull()?.get("file_token")?.asString
        ?: throw Exception("Image upload failed: no file_token returned")

    return fileToken
}

/**
 * Upload an image block to a document (3-step: create block → upload → patch).
 * Aligned with OpenClaw uploadImageBlock.
 */
suspend fun uploadImageBlock(
    client: FeishuClient,
    docToken: String,
    imageBytes: ByteArray,
    fileName: String,
    parentBlockId: String? = null,
    index: Int? = null
): JsonObject {
    val blockId = parentBlockId ?: docToken

    // Step 1: Create empty image block (block_type 27)
    val createBody = mapOf(
        "children" to listOf(mapOf("block_type" to 27, "image" to emptyMap<String, Any>())),
        "index" to (index ?: -1)
    )

    val createResult = client.post(
        "/open-apis/docx/v1/documents/$docToken/blocks/$blockId/children",
        createBody
    )
    if (createResult.isFailure) {
        throw createResult.exceptionOrNull() ?: Exception("Failed to create image block")
    }

    val children = createResult.getOrNull()?.getAsJsonObject("data")?.getAsJsonArray("children")
    var imageBlockId: String? = null
    if (children != null) {
        for (i in 0 until children.size()) {
            val child = children[i].asJsonObject
            if (child.get("block_type")?.asInt == 27) {
                imageBlockId = child.get("block_id")?.asString
                break
            }
        }
    }

    if (imageBlockId == null) {
        throw Exception("Failed to create image block: no block_id returned")
    }

    // Step 2: Upload image
    val fileToken = uploadImageToDocx(client, imageBlockId, imageBytes, fileName, docToken)

    // Step 3: Set image token on the block
    val patchBody = JsonObject().apply {
        add("replace_image", JsonObject().apply {
            addProperty("token", fileToken)
        })
    }

    val patchResult = client.patch(
        "/open-apis/docx/v1/documents/$docToken/blocks/$imageBlockId",
        patchBody
    )
    if (patchResult.isFailure) {
        throw patchResult.exceptionOrNull() ?: Exception("Failed to patch image block")
    }

    return JsonObject().apply {
        addProperty("success", true)
        addProperty("block_id", imageBlockId)
        addProperty("file_token", fileToken)
        addProperty("file_name", fileName)
        addProperty("size", imageBytes.size)
    }
}

/**
 * Process images in markdown after block insertion.
 * Downloads remote images and uploads them to the document.
 * Aligned with OpenClaw processImages.
 */
suspend fun processImages(
    client: FeishuClient,
    docToken: String,
    markdown: String,
    insertedBlocks: JsonArray,
    maxBytes: Long = DEFAULT_MEDIA_MAX_BYTES
): Int {
    val imageUrls = extractImageUrls(markdown)
    if (imageUrls.isEmpty()) return 0

    // Find image blocks (block_type 27)
    val imageBlocks = mutableListOf<String>()
    for (i in 0 until insertedBlocks.size()) {
        val block = insertedBlocks[i].asJsonObject
        if (block.get("block_type")?.asInt == 27) {
            block.get("block_id")?.asString?.let { imageBlocks.add(it) }
        }
    }

    var processed = 0
    for (i in 0 until minOf(imageUrls.size, imageBlocks.size)) {
        val url = imageUrls[i]
        val blockId = imageBlocks[i]

        try {
            // Download image
            val imageBytes = downloadRemoteImage(client, url, maxBytes)
            val urlPath = java.net.URL(url).path
            val fileName = urlPath.split("/").lastOrNull()?.takeIf { it.isNotEmpty() }
                ?: "image_$i.png"

            // Upload and set on block
            val fileToken = uploadImageToDocx(client, blockId, imageBytes, fileName, docToken)

            val patchBody = JsonObject().apply {
                add("replace_image", JsonObject().apply {
                    addProperty("token", fileToken)
                })
            }
            client.patch("/open-apis/docx/v1/documents/$docToken/blocks/$blockId", patchBody)

            processed++
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process image $url: ${e.message}")
        }
    }

    return processed
}

/**
 * Download a remote image via URL.
 */
private suspend fun downloadRemoteImage(
    client: FeishuClient,
    url: String,
    maxBytes: Long
): ByteArray {
    // Use OkHttp directly for external URLs
    val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val request = okhttp3.Request.Builder().url(url).get().build()
    val response = httpClient.newCall(request).execute()

    if (!response.isSuccessful) {
        throw Exception("Failed to download image: HTTP ${response.code}")
    }

    val bytes = response.body?.bytes() ?: throw Exception("Empty response body")
    if (bytes.size > maxBytes) {
        throw Exception("Image exceeds limit: ${bytes.size} bytes > $maxBytes bytes")
    }

    return bytes
}

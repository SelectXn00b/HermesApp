package com.ai.assistance.operit.services

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.CloudEmbeddingConfig
import com.ai.assistance.operit.data.model.Embedding
import com.ai.assistance.operit.util.AppLogger
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class CloudEmbeddingService(
    private val context: Context
) {

    companion object {
        private const val TAG = "CloudEmbeddingService"
    }

    class CloudEmbeddingException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun generateEmbedding(config: CloudEmbeddingConfig, text: String): Embedding? = withContext(Dispatchers.IO) {
        val normalized = config.normalized()
        if (!normalized.isReady() || text.isBlank()) {
            return@withContext null
        }

        try {
            requestEmbedding(normalized, text)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Embedding request exception", e)
            null
        }
    }

    suspend fun generateEmbeddingOrThrow(config: CloudEmbeddingConfig, text: String): Embedding = withContext(Dispatchers.IO) {
        val normalized = config.normalized()
        if (!normalized.isReady()) {
            throw CloudEmbeddingException(context.getString(R.string.memory_embedding_error_config_incomplete))
        }
        if (text.isBlank()) {
            throw CloudEmbeddingException(context.getString(R.string.memory_embedding_error_input_empty))
        }

        requestEmbedding(normalized, text)
    }

    private fun requestEmbedding(config: CloudEmbeddingConfig, text: String): Embedding {
        val requestBodyJson = JSONObject()
            .put("model", config.model)
            .put("input", text)
            .toString()

        val request = Request.Builder()
            .url(completeEmbeddingsEndpoint(config.endpoint))
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = extractErrorMessage(responseBody).ifBlank { response.message.ifBlank { "请求失败" } }
                val message = context.getString(R.string.memory_embedding_error_http, response.code, detail)
                AppLogger.w(TAG, "$message, body=$responseBody")
                throw CloudEmbeddingException(message)
            }

            return parseEmbedding(responseBody)
        }
    }

    private fun parseEmbedding(responseBody: String): Embedding {
        if (responseBody.isBlank()) {
            throw CloudEmbeddingException(context.getString(R.string.memory_embedding_error_empty_response))
        }

        return try {
            val root = JSONObject(responseBody)
            val data = root.optJSONArray("data")
                ?: throw CloudEmbeddingException(
                    context.getString(R.string.memory_embedding_error_missing_data, truncate(responseBody))
                )
            if (data.length() <= 0) {
                throw CloudEmbeddingException(
                    context.getString(R.string.memory_embedding_error_empty_data, truncate(responseBody))
                )
            }

            val first = data.optJSONObject(0)
                ?: throw CloudEmbeddingException(
                    context.getString(R.string.memory_embedding_error_invalid_first_item, truncate(responseBody))
                )
            val embeddingJson = first.optJSONArray("embedding")
                ?: throw CloudEmbeddingException(
                    context.getString(R.string.memory_embedding_error_missing_embedding, truncate(responseBody))
                )
            if (embeddingJson.length() <= 0) {
                throw CloudEmbeddingException(
                    context.getString(R.string.memory_embedding_error_empty_embedding, truncate(responseBody))
                )
            }

            val vector = FloatArray(embeddingJson.length()) { index ->
                embeddingJson.optDouble(index, 0.0).toFloat()
            }
            Embedding(vector)
        } catch (e: CloudEmbeddingException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse embedding response", e)
            throw CloudEmbeddingException(
                context.getString(R.string.memory_embedding_error_parse_failed, truncate(responseBody)),
                e
            )
        }
    }

    private fun extractErrorMessage(responseBody: String): String {
        if (responseBody.isBlank()) return ""

        return try {
            val root = JSONObject(responseBody)
            when {
                root.has("error") -> parseErrorNode(root.opt("error"))
                root.has("message") -> root.optString("message")
                root.has("detail") -> root.optString("detail")
                else -> truncate(responseBody)
            }.trim()
        } catch (_: Exception) {
            truncate(responseBody)
        }
    }

    private fun parseErrorNode(errorNode: Any?): String {
        return when (errorNode) {
            is JSONObject -> listOf(
                errorNode.optString("message"),
                errorNode.optString("type"),
                errorNode.optString("code")
            ).firstOrNull { it.isNotBlank() } ?: truncate(errorNode.toString())
            is JSONArray -> truncate(errorNode.toString())
            is String -> errorNode
            null -> ""
            else -> truncate(errorNode.toString())
        }
    }

    private fun truncate(text: String, maxLength: Int = 200): String {
        val singleLine = text.replace('\n', ' ').replace('\r', ' ').trim()
        return if (singleLine.length <= maxLength) singleLine else singleLine.take(maxLength) + "..."
    }

    private fun completeEmbeddingsEndpoint(endpoint: String): String {
        val trimmed = endpoint.trim()
        if (trimmed.endsWith("#")) {
            return trimmed.removeSuffix("#")
        }

        val withoutSlash = trimmed.removeSuffix("/")

        return try {
            val path = URL(trimmed).path.removeSuffix("/")
            when {
                path.isEmpty() -> "$withoutSlash/v1/embeddings"
                path.endsWith("/v1", ignoreCase = true) -> "$withoutSlash/embeddings"
                else -> trimmed
            }
        } catch (_: Exception) {
            trimmed
        }
    }
}

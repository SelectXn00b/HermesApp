package com.ai.assistance.operit.data.api

import com.ai.assistance.operit.data.model.feedback.FeedbackRequest
import com.ai.assistance.operit.data.model.feedback.FeedbackResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class FeedbackApiService {

    companion object {
        private const val FEEDBACK_API_URL = "https://claw.devset.top/api/feedback"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun submitFeedback(request: FeedbackRequest): Result<FeedbackResponse> =
        withContext(Dispatchers.IO) {
            try {
                val body = json.encodeToString(FeedbackRequest.serializer(), request)
                    .toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url(FEEDBACK_API_URL)
                    .post(body)
                    .addHeader("User-Agent", "HermesApp-Feedback")
                    .build()

                val response = client.newCall(httpRequest).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val feedbackResponse =
                        json.decodeFromString<FeedbackResponse>(responseBody)
                    Result.success(feedbackResponse)
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

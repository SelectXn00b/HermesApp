/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: data models.
 */
package com.xiaomo.androidforclaw.data.repository

import android.os.Parcelable
import android.telecom.Call.Details
import kotlinx.parcelize.Parcelize
import retrofit2.Call
import retrofit2.http.*
import okhttp3.MultipartBody
import okhttp3.RequestBody

// Data models for request/response
data class FileUploadResponse(val id: String)
data class ChatResponse(val answer: String, val conversation_id: String, val duration: Long = 0)

// Request body structures
@Parcelize
data class FileRef(
    val type: String = "image",
//    val details: String = "myd",
    val transfer_method: String = "local_file",
    val upload_file_id: String // file ID from upload
) : Parcelable

@Parcelize
data class ChatRequest(
    val inputs: Map<String, String>? = emptyMap(),    // additional inputs, if any
    val query: String,                             // user query or prompt
    val response_mode: String = "blocking",
    val conversation_id: String = "",              // empty for new convo
    val user: String = "user",                     // user identifier
    val files: List<FileRef> = emptyList(),        // attached image references
    val auto_generate_name: Boolean = true         // 自动生成会话标题，默认 true
) : Parcelable

interface DifyApiService {
    @Multipart
    @POST("files/upload")
    fun uploadFile(
        @Header("Authorization") authToken: String,
        @Part file: MultipartBody.Part,
        @Part("user") user: RequestBody
    ): Call<FileUploadResponse>

    @POST("chat-messages")
    fun sendMessage(
        @Header("Authorization") authToken: String,
        @Body request: ChatRequest
    ): Call<ChatResponse>
}
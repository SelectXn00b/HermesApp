package com.xiaomo.feishu

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel runtime.
 */


import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 飞书 API 客户端
 * 对齐 OpenClaw feishu client.ts
 */
class FeishuClient(private val config: FeishuConfig) {
    companion object {
        private const val TAG = "FeishuClient"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Longer timeout for media downloads (aligned with OpenClaw: 120s)
    private val mediaHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val baseUrl = config.getApiBaseUrl()

    // Access token 缓存
    private var cachedAccessToken: String? = null
    private var tokenExpireTime: Long = 0

    /**
     * 获取 tenant_access_token (协程版本)
     */
    suspend fun getTenantAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 检查缓存
            val now = System.currentTimeMillis()
            if (cachedAccessToken != null && now < tokenExpireTime) {
                return@withContext Result.success(cachedAccessToken!!)
            }

            // 请求新 token
            val url = "$baseUrl/open-apis/auth/v3/tenant_access_token/internal"
            val requestBody = mapOf(
                "app_id" to config.appId,
                "app_secret" to config.appSecret
            )

            val body = gson.toJson(requestBody)
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to get tenant access token: $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val code = json.get("code")?.asInt ?: -1

            if (code != 0) {
                val msg = json.get("msg")?.asString ?: "Unknown error"
                Log.e(TAG, "Feishu API error: $msg")
                return@withContext Result.failure(Exception(msg))
            }

            val token = json.get("tenant_access_token")?.asString
                ?: return@withContext Result.failure(Exception("Missing tenant_access_token"))

            val expire = json.get("expire")?.asInt ?: 7200

            // 缓存 token（提前 5 分钟过期）
            cachedAccessToken = token
            tokenExpireTime = now + (expire - 300) * 1000L

            Log.d(TAG, "Got tenant access token, expires in ${expire}s")
            Result.success(token)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get tenant access token", e)
            Result.failure(e)
        }
    }

    /**
     * 获取 tenant_access_token (同步版本)
     * 用于在 IO 线程中直接调用
     */
    fun getTenantAccessTokenSync(): String? {
        try {
            // 检查缓存
            val now = System.currentTimeMillis()
            if (cachedAccessToken != null && now < tokenExpireTime) {
                return cachedAccessToken
            }

            // 请求新 token
            val url = "$baseUrl/open-apis/auth/v3/tenant_access_token/internal"
            val requestBody = mapOf(
                "app_id" to config.appId,
                "app_secret" to config.appSecret
            )

            val body = gson.toJson(requestBody)
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to get tenant access token: $responseBody")
                return null
            }

            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val code = json.get("code")?.asInt ?: -1

            if (code != 0) {
                val msg = json.get("msg")?.asString ?: "Unknown error"
                Log.e(TAG, "Feishu API error: $msg")
                return null
            }

            val token = json.get("tenant_access_token")?.asString ?: return null
            val expire = json.get("expire")?.asInt ?: 7200

            // 缓存 token（提前 5 分钟过期）
            cachedAccessToken = token
            tokenExpireTime = now + (expire - 300) * 1000L

            Log.d(TAG, "Got tenant access token (sync), expires in ${expire}s")
            return token

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get tenant access token (sync)", e)
            return null
        }
    }

    /**
     * 发送 API 请求
     */
    suspend fun apiRequest(
        method: String,
        path: String,
        body: Any? = null,
        requireAuth: Boolean = true
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl$path"

            val requestBuilder = Request.Builder().url(url)

            // 添加认证头
            if (requireAuth) {
                val tokenResult = getTenantAccessToken()
                if (tokenResult.isFailure) {
                    return@withContext Result.failure(tokenResult.exceptionOrNull()!!)
                }
                requestBuilder.addHeader("Authorization", "Bearer ${tokenResult.getOrNull()}")
            }

            // 添加请求体
            if (body != null) {
                val json = if (body is String) body else gson.toJson(body)
                val requestBody = json.toRequestBody("application/json".toMediaType())

                when (method.uppercase()) {
                    "POST" -> requestBuilder.post(requestBody)
                    "PUT" -> requestBuilder.put(requestBody)
                    "PATCH" -> requestBuilder.patch(requestBody)
                    else -> requestBuilder.method(method.uppercase(), requestBody)
                }
            } else {
                when (method.uppercase()) {
                    "GET" -> requestBuilder.get()
                    "DELETE" -> requestBuilder.delete()
                    else -> requestBuilder.method(method.uppercase(), null)
                }
            }

            val request = requestBuilder.build()
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"

            if (!response.isSuccessful) {
                Log.e(TAG, "API request failed: $method $path - HTTP ${response.code}")
                Log.e(TAG, "Response: $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            // Defensive JSON parsing — handle non-JSON or non-Object responses
            val jsonElement = try {
                gson.fromJson(responseBody, com.google.gson.JsonElement::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Response is not valid JSON: $responseBody")
                return@withContext Result.failure(Exception("Invalid JSON response from $method $path"))
            }

            if (jsonElement == null || !jsonElement.isJsonObject) {
                Log.e(TAG, "Response is not a JSON object: $responseBody")
                return@withContext Result.failure(Exception("Expected JSON object from $method $path, got: ${jsonElement?.javaClass?.simpleName ?: "null"}"))
            }

            val json = jsonElement.asJsonObject
            val code = json.get("code")?.asInt ?: 0

            if (code != 0) {
                val msg = json.get("msg")?.asString ?: "Unknown error"
                Log.e(TAG, "Feishu API error: $msg (code=$code)")
                return@withContext Result.failure(Exception("$msg (code=$code)"))
            }

            Result.success(json)

        } catch (e: Exception) {
            Log.e(TAG, "API request exception: $method $path", e)
            Result.failure(e)
        }
    }

    /**
     * GET 请求
     */
    suspend fun get(path: String): Result<JsonObject> = apiRequest("GET", path)

    /**
     * POST 请求
     */
    suspend fun post(path: String, body: Any): Result<JsonObject> = apiRequest("POST", path, body)

    /**
     * PUT 请求
     */
    suspend fun put(path: String, body: Any): Result<JsonObject> = apiRequest("PUT", path, body)

    /**
     * DELETE 请求
     */
    suspend fun delete(path: String): Result<JsonObject> = apiRequest("DELETE", path)

    /**
     * PATCH 请求
     */
    suspend fun patch(path: String, body: Any): Result<JsonObject> = apiRequest("PATCH", path, body)

    /**
     * 下载二进制数据（用于媒体文件下载）
     * 对齐 OpenClaw downloadImageFeishu / downloadMessageResourceFeishu
     */
    suspend fun downloadRaw(path: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl$path"
            val token = getTenantAccessToken().getOrThrow()

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = mediaHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "Download failed: HTTP ${response.code} - $errorBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val bytes = response.body?.bytes()
                ?: return@withContext Result.failure(Exception("Empty response body"))

            Log.d(TAG, "Downloaded ${bytes.size} bytes from $path")
            Result.success(bytes)

        } catch (e: Exception) {
            Log.e(TAG, "Download failed: $path", e)
            Result.failure(e)
        }
    }

    /**
     * Download binary data with Content-Type header.
     * @aligned openclaw-lark v2026.3.30
     * Returns Pair(bytes, contentType) where contentType may be empty.
     */
    suspend fun downloadRawWithHeaders(path: String): Result<Pair<ByteArray, String>> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl$path"
            val token = getTenantAccessToken().getOrThrow()

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = mediaHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "Download failed: HTTP ${response.code} - $errorBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val bytes = response.body?.bytes()
                ?: return@withContext Result.failure(Exception("Empty response body"))

            val contentType = response.header("Content-Type") ?: ""

            Log.d(TAG, "Downloaded ${bytes.size} bytes from $path, content-type=$contentType")
            Result.success(bytes to contentType)

        } catch (e: Exception) {
            Log.e(TAG, "Download failed: $path", e)
            Result.failure(e)
        }
    }

    /**
     * 上传媒体文件（图片/文件）到飞书
     * Aligned with @larksuite/openclaw-lark doc-media insert flow.
     *
     * @param fileName File name
     * @param parentType Parent type: docx_image, docx_file, etc.
     * @param parentNode Parent block ID
     * @param data File bytes
     * @param extra Optional extra JSON string (e.g. drive_route_token)
     */
    suspend fun uploadMedia(
        fileName: String,
        parentType: String,
        parentNode: String,
        data: ByteArray,
        extra: String? = null
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val token = getTenantAccessToken().getOrThrow()
            val url = "$baseUrl/open-apis/drive/v1/medias/upload_all"

            val bodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file_name", fileName)
                .addFormDataPart("parent_type", parentType)
                .addFormDataPart("parent_node", parentNode)
                .addFormDataPart("size", data.size.toString())
                .addFormDataPart(
                    "file", fileName,
                    data.toRequestBody("application/octet-stream".toMediaType())
                )

            if (extra != null) {
                bodyBuilder.addFormDataPart("extra", extra)
            }

            val request = Request.Builder()
                .url(url)
                .post(bodyBuilder.build())
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = mediaHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"

            if (!response.isSuccessful) {
                Log.e(TAG, "Upload media failed: HTTP ${response.code} - $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val code = json.get("code")?.asInt ?: 0
            if (code != 0) {
                val msg = json.get("msg")?.asString ?: "Unknown error"
                Log.e(TAG, "Upload media error: $msg (code=$code)")
                return@withContext Result.failure(Exception("$msg (code=$code)"))
            }

            Result.success(json.getAsJsonObject("data") ?: JsonObject())
        } catch (e: Exception) {
            Log.e(TAG, "Upload media failed", e)
            Result.failure(e)
        }
    }

    /**
     * 获取机器人信息 (对齐 OpenClaw probe.ts)
     * https://open.feishu.cn/document/server-docs/bot-v3/bot-overview
     */
    suspend fun getBotInfo(): Result<BotInfo> = withContext(Dispatchers.IO) {
        try {
            val token = getTenantAccessToken().getOrThrow()
            val url = "$baseUrl/open-apis/bot/v3/info"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response"))

            if (!response.isSuccessful) {
                val error = try {
                    val json = gson.fromJson(responseBody, JsonObject::class.java)
                    json.get("msg")?.asString ?: responseBody
                } catch (e: Exception) {
                    responseBody
                }
                return@withContext Result.failure(Exception("getBotInfo failed: $error"))
            }

            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val code = json.get("code")?.asInt ?: -1

            Log.d(TAG, "getBotInfo response: $responseBody")

            if (code != 0) {
                val msg = json.get("msg")?.asString ?: "Unknown error"
                return@withContext Result.failure(Exception("getBotInfo failed: $msg (code: $code)"))
            }

            // 飞书 API v3 的响应结构: { code: 0, bot: { activate_status: 2, app_name: "...", open_id: "...", ... }, msg: "ok" }
            val bot = json.getAsJsonObject("bot")
            Log.d(TAG, "bot object: $bot")

            if (bot == null) {
                return@withContext Result.failure(Exception("Missing bot in response"))
            }

            val openId = bot.get("open_id")?.asString
            val name = bot.get("app_name")?.asString

            Log.d(TAG, "Got bot info: open_id=$openId, name=$name")

            Result.success(BotInfo(openId = openId, name = name))

        } catch (e: Exception) {
            Log.e(TAG, "getBotInfo failed", e)
            Result.failure(e)
        }
    }
}

/**
 * 机器人信息
 */
data class BotInfo(
    val openId: String?,
    val name: String?
)

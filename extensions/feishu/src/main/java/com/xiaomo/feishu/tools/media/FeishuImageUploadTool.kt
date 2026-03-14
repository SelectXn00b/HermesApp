package com.xiaomo.feishu.tools.media

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel tool definitions.
 */


import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 飞书图片上传工具 (Kotlin 实现,稳定版)
 *
 * 稳定性改进:
 * 1. 使用 Kotlin 协程 + 同步 HTTP 调用
 * 2. 详细的错误日志和重试机制
 * 3. 完整的请求/响应验证
 * 4. 文件格式和大小检查
 */
class FeishuImageUploadTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    companion object {
        private const val TAG = "FeishuImageUploadTool"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
        private const val MAX_FILE_SIZE_MB = 10
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    override val name = "feishu_upload_image"

    override val description = "上传图片到飞书并返回 image_key,可用于后续发送消息"

    override fun isEnabled() = true

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "image_path" to PropertySchema(
                            type = "string",
                            description = "图片文件的绝对路径 (支持 PNG, JPG, JPEG, GIF, BMP)"
                        )
                    ),
                    required = listOf("image_path")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val imagePath = args["image_path"] as? String
            ?: return ToolResult.error("缺少参数: image_path")

        Log.i(TAG, "开始上传图片: $imagePath")

        // 验证文件
        val imageFile = File(imagePath)
        val validationResult = validateImageFile(imageFile)
        if (!validationResult.success) {
            return validationResult
        }

        // 上传图片 (带重试) - 在 IO 线程执行同步 HTTP 调用
        return withContext(Dispatchers.IO) {
            for (attempt in 1..MAX_RETRIES) {
                Log.d(TAG, "上传尝试 $attempt/$MAX_RETRIES")

                try {
                    val imageKey = uploadImageWithDetails(imageFile)
                    Log.i(TAG, "✅ 图片上传成功: $imageKey")

                    return@withContext ToolResult.success(
                        data = imageKey,
                        metadata = mapOf(
                            "image_key" to imageKey,
                            "file_name" to imageFile.name,
                            "file_size" to imageFile.length(),
                            "attempts" to attempt
                        )
                    )

                } catch (e: Exception) {
                    Log.e(TAG, "❌ 上传失败 (尝试 $attempt/$MAX_RETRIES): ${e.message}", e)

                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(RETRY_DELAY_MS * attempt) // 递增延迟
                    } else {
                        return@withContext ToolResult.error(
                            "图片上传失败 (已重试 $MAX_RETRIES 次): ${e.message}"
                        )
                    }
                }
            }

            ToolResult.error("图片上传失败: 超过最大重试次数")
        }
    }

    /**
     * 验证图片文件
     */
    private fun validateImageFile(file: File): ToolResult {
        // 检查文件是否存在
        if (!file.exists()) {
            return ToolResult.error("文件不存在: ${file.absolutePath}")
        }

        if (!file.isFile) {
            return ToolResult.error("不是文件: ${file.absolutePath}")
        }

        if (!file.canRead()) {
            return ToolResult.error("文件不可读: ${file.absolutePath}")
        }

        // 检查文件大小
        val fileSizeBytes = file.length()
        if (fileSizeBytes == 0L) {
            return ToolResult.error("文件为空: ${file.absolutePath}")
        }

        val fileSizeMB = fileSizeBytes / (1024.0 * 1024.0)
        if (fileSizeMB > MAX_FILE_SIZE_MB) {
            return ToolResult.error(
                "文件过大: %.2fMB > %dMB".format(fileSizeMB, MAX_FILE_SIZE_MB)
            )
        }

        // 检查文件扩展名
        val fileName = file.name.lowercase()
        if (!fileName.endsWith(".png") &&
            !fileName.endsWith(".jpg") &&
            !fileName.endsWith(".jpeg") &&
            !fileName.endsWith(".gif") &&
            !fileName.endsWith(".bmp")
        ) {
            return ToolResult.error(
                "不支持的图片格式,仅支持: PNG, JPG, JPEG, GIF, BMP"
            )
        }

        Log.d(TAG, "✅ 文件验证通过: ${file.name} (%.2fMB)".format(fileSizeMB))

        return ToolResult.success()
    }

    /**
     * 上传图片并返回 image_key (详细日志版本)
     * 注意: 这是同步方法,需要在 IO 线程调用
     */
    private fun uploadImageWithDetails(imageFile: File): String {
        // 1. 获取 access token
        Log.d(TAG, "步骤 1: 获取 tenant_access_token")
        val token = client.getTenantAccessTokenSync()
            ?: throw IOException("获取 token 失败")
        Log.d(TAG, "✅ Token 获取成功")

        // 2. 构建请求
        Log.d(TAG, "步骤 2: 构建 multipart 请求")
        val fileBody = imageFile.asRequestBody("image/png".toMediaType())

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image_type", "message")  // 重要: 使用 "message" 而不是 "image"
            .addFormDataPart("image", imageFile.name, fileBody)
            .build()

        val url = "${config.getApiBaseUrl()}/open-apis/im/v1/images"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        Log.d(TAG, "请求 URL: $url")
        Log.d(TAG, "文件名: ${imageFile.name}")
        Log.d(TAG, "文件大小: ${imageFile.length()} bytes")

        // 3. 执行请求
        Log.d(TAG, "步骤 3: 发送 HTTP 请求")
        val response = httpClient.newCall(request).execute()

        response.use {
            val statusCode = response.code
            val responseBody = response.body?.string() ?: ""

            Log.d(TAG, "响应状态码: $statusCode")
            Log.d(TAG, "响应内容: $responseBody")

            // 4. 检查 HTTP 状态码
            if (!response.isSuccessful) {
                throw IOException("HTTP 请求失败 [$statusCode]: $responseBody")
            }

            // 5. 解析响应
            Log.d(TAG, "步骤 4: 解析响应 JSON")
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: throw IOException("响应 JSON 为空")

            // 检查 code 字段
            val code = json.get("code")?.asInt ?: -1
            if (code != 0) {
                val msg = json.get("msg")?.asString ?: "未知错误"
                throw IOException("飞书 API 错误 [code=$code]: $msg")
            }

            // 提取 image_key
            val data = json.getAsJsonObject("data")
                ?: throw IOException("响应缺少 data 字段")

            val imageKey = data.get("image_key")?.asString
                ?: throw IOException("响应缺少 image_key 字段")

            if (imageKey.isEmpty()) {
                throw IOException("image_key 为空")
            }

            return imageKey
        }
    }
}

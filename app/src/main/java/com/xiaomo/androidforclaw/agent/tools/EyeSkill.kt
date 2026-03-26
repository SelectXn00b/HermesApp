package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/apps/android/app/src/main/java/ai/openclaw/app/node/CameraHandler.kt
 * - ../openclaw/apps/android/app/src/main/java/ai/openclaw/app/node/CameraCaptureManager.kt
 *
 * AndroidForClaw adaptation: 眼睛 Skill
 * 手机的前后摄像头就是 Agent 的两只眼睛，用于观察物理环境。
 * 支持 list / look / watch 三种操作
 */

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.xiaomo.androidforclaw.camera.CameraCaptureManager
import com.xiaomo.androidforclaw.camera.CameraPermissionActivity
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.media.ImageSanitizer
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import com.xiaomo.androidforclaw.providers.llm.ImageBlock
import com.xiaomo.androidforclaw.workspace.StoragePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 眼睛 Skill — 你的两只眼睛（前置 + 后置摄像头）
 *
 * 当你需要观察物理环境、看看周围发生了什么时，使用这个工具。
 * - front（前眼）：面向用户的摄像头，可以看到用户和用户面前的环境
 * - back（后眼）：背向用户的摄像头，可以看到手机背面对着的环境
 *
 * 对齐 OpenClaw camera.list / camera.snap / camera.clip
 */
class EyeSkill(
    private val context: Context,
    private val cameraManager: CameraCaptureManager,
) : Skill {
    companion object {
        private const val TAG = "EyeSkill"
    }

    override val name = "eye"
    override val description = "Your eyes — use the phone's cameras to observe the physical environment. " +
        "front eye faces the user, back eye faces outward. " +
        "Actions: list (list available eyes), look (take a photo and see — image is embedded for you to understand), " +
        "snap (take a photo and save — only returns file path, no image embedded), " +
        "watch (record a short video to observe over time)"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "action" to PropertySchema(
                            type = "string",
                            description = "操作类型: list(列出可用的眼睛), look(看一眼并理解，图片直接嵌入给你看), snap(纯拍照，只返回文件路径), watch(持续观察，录制短视频)",
                            enum = listOf("list", "look", "snap", "watch")
                        ),
                        "facing" to PropertySchema(
                            type = "string",
                            description = "使用哪只眼睛: front(前眼，面向用户) 或 back(后眼，面向外部环境)，默认 back",
                            enum = listOf("front", "back")
                        ),
                        "quality" to PropertySchema(
                            type = "number",
                            description = "图像质量 0.1-1.0，默认 0.95（仅 look）"
                        ),
                        "max_width" to PropertySchema(
                            type = "number",
                            description = "最大图像宽度（像素），默认 1600（仅 look）"
                        ),
                        "duration_ms" to PropertySchema(
                            type = "number",
                            description = "观察时长（毫秒），默认 3000，最大 60000（仅 watch）"
                        ),
                        "include_audio" to PropertySchema(
                            type = "boolean",
                            description = "是否同时聆听声音，默认 true（仅 watch）"
                        ),
                        "device_id" to PropertySchema(
                            type = "string",
                            description = "指定摄像头 ID（从 list 获取，可选）"
                        ),
                    ),
                    required = listOf("action")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val action = (args["action"] as? String)?.lowercase()
            ?: return SkillResult.error("Missing required parameter: action")

        // 兼容旧的 action 名称
        val normalizedAction = when (action) {
            "snap" -> "look"
            "clip" -> "watch"
            else -> action
        }

        return when (normalizedAction) {
            "list" -> executeList()
            "look" -> {
                val permResult = ensureCameraPermission()
                if (permResult != null) return permResult
                executeLook(args, embedImage = true)
            }
            "snap" -> {
                val permResult = ensureCameraPermission()
                if (permResult != null) return permResult
                executeLook(args, embedImage = false)
            }
            "watch" -> {
                val permResult = ensureCameraPermission()
                if (permResult != null) return permResult
                executeWatch(args)
            }
            else -> SkillResult.error("Unknown action: $action. Use: list, look, snap, watch")
        }
    }

    /**
     * 检查相机权限，如果没有则弹出透明 Activity 请求。
     * @return null=权限已就绪，SkillResult=权限被拒绝的错误结果
     */
    private suspend fun ensureCameraPermission(): SkillResult? {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return null // 已有权限
        }

        Log.d(TAG, "CAMERA permission not granted, requesting via CameraPermissionActivity")
        val granted = CameraPermissionActivity.requestPermission(context)

        return if (granted) {
            Log.d(TAG, "CAMERA permission granted by user")
            null // 权限已授予
        } else {
            Log.w(TAG, "CAMERA permission denied by user")
            SkillResult.error("需要相机权限才能使用眼睛。请在系统设置中授予相机权限后重试。")
        }
    }

    /**
     * 列出可用的眼睛（摄像头）
     */
    private suspend fun executeList(): SkillResult {
        return try {
            val devices = cameraManager.listDevices()
            if (devices.isEmpty()) {
                return SkillResult.success("没有检测到可用的眼睛（摄像头）")
            }
            val output = buildString {
                appendLine("可用的眼睛 (${devices.size} 个):")
                devices.forEach { d ->
                    val eyeName = when (d.position) {
                        "front" -> "前眼（面向用户）"
                        "back" -> "后眼（面向环境）"
                        else -> d.position
                    }
                    appendLine("  - id: ${d.id}, $eyeName, type: ${d.deviceType}")
                }
            }
            SkillResult.success(output, mapOf("device_count" to devices.size))
        } catch (e: Exception) {
            Log.e(TAG, "eye.list failed", e)
            SkillResult.error("列出可用眼睛失败: ${e.message}")
        }
    }

    /**
     * 看一眼（拍照）
     * @param embedImage true=look（图片嵌入给模型看），false=snap（只返回文件路径）
     */
    private suspend fun executeLook(args: Map<String, Any?>, embedImage: Boolean = true): SkillResult {
        return try {
            val facing = (args["facing"] as? String)?.lowercase() ?: "back"
            val quality = (args["quality"] as? Number)?.toDouble() ?: 0.95
            val maxWidth = (args["max_width"] as? Number)?.toInt() ?: 1600
            val deviceId = args["device_id"] as? String

            val eyeName = if (facing == "front") "前眼" else "后眼"
            Log.d(TAG, "eye.look: facing=$facing($eyeName), quality=$quality, maxWidth=$maxWidth")

            val result = cameraManager.snap(
                facing = facing,
                quality = quality,
                maxWidth = maxWidth,
                deviceId = deviceId,
            )

            // 压缩图片（对齐 OpenClaw image-sanitization 策略）
            val sanitized = withContext(Dispatchers.IO) {
                ImageSanitizer.sanitize(result.base64, "image/jpeg")
            } ?: return SkillResult.error("图片压缩失败")

            // 保存到工作空间
            val photoDir = File(StoragePaths.workspace, "eye").apply { mkdirs() }
            val photoFile = File(photoDir, "look_${System.currentTimeMillis()}.jpg")
            withContext(Dispatchers.IO) {
                val bytes = android.util.Base64.decode(sanitized.base64, android.util.Base64.NO_WRAP)
                photoFile.writeBytes(bytes)
            }

            val output = buildString {
                if (embedImage) {
                    appendLine("👁️ 通过${eyeName}观察完成")
                    appendLine("分辨率: ${sanitized.width}x${sanitized.height}")
                    appendLine("文件: ${photoFile.absolutePath}")
                    appendLine("（图片已内嵌，请直接描述你看到的内容）")
                } else {
                    appendLine("📸 通过${eyeName}拍照完成")
                    appendLine("分辨率: ${sanitized.width}x${sanitized.height}")
                    appendLine("文件: ${photoFile.absolutePath}")
                }
            }

            SkillResult.success(
                output,
                mapOf(
                    "format" to "jpeg",
                    "width" to sanitized.width,
                    "height" to sanitized.height,
                    "file_path" to photoFile.absolutePath,
                ),
                images = if (embedImage) listOf(ImageBlock(base64 = sanitized.base64, mimeType = sanitized.mimeType)) else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "eye.look failed", e)
            SkillResult.error("观察失败: ${e.message}")
        }
    }

    /**
     * 持续观察（录像）
     */
    private suspend fun executeWatch(args: Map<String, Any?>): SkillResult {
        return try {
            val facing = (args["facing"] as? String)?.lowercase() ?: "back"
            val durationMs = (args["duration_ms"] as? Number)?.toInt() ?: 3000
            val includeAudio = (args["include_audio"] as? Boolean) ?: true
            val deviceId = args["device_id"] as? String

            val eyeName = if (facing == "front") "前眼" else "后眼"
            Log.d(TAG, "eye.watch: facing=$facing($eyeName), duration=$durationMs, audio=$includeAudio")

            val result = cameraManager.clip(
                facing = facing,
                durationMs = durationMs,
                includeAudio = includeAudio,
                deviceId = deviceId,
            )

            // 保存到工作空间
            val videoDir = File(StoragePaths.workspace, "eye").apply { mkdirs() }
            val videoFile = File(videoDir, "watch_${System.currentTimeMillis()}.mp4")
            withContext(Dispatchers.IO) {
                val bytes = android.util.Base64.decode(result.base64, android.util.Base64.NO_WRAP)
                videoFile.writeBytes(bytes)
            }

            val output = buildString {
                appendLine("👁️ 通过${eyeName}持续观察完成")
                appendLine("时长: ${result.durationMs}ms")
                appendLine("声音: ${if (result.hasAudio) "有" else "无"}")
                appendLine("文件: ${videoFile.absolutePath}")
            }

            SkillResult.success(
                output,
                mapOf(
                    "format" to result.format,
                    "duration_ms" to result.durationMs,
                    "has_audio" to result.hasAudio,
                    "file_path" to videoFile.absolutePath,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "eye.watch failed", e)
            SkillResult.error("持续观察失败: ${e.message}")
        }
    }
}

package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/apps/android/app/src/main/java/ai/openclaw/app/node/CameraHandler.kt
 * - ../openclaw/apps/android/app/src/main/java/ai/openclaw/app/node/CameraCaptureManager.kt
 *
 * AndroidForClaw adaptation: 相机 Skill
 * 支持 list / snap / clip 三种操作
 */

import android.content.Context
import com.xiaomo.androidforclaw.camera.CameraCaptureManager
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import com.xiaomo.androidforclaw.workspace.StoragePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 相机 Skill — 拍照 / 录像 / 列出摄像头
 *
 * 对齐 OpenClaw camera.list / camera.snap / camera.clip
 * 本地 Skill 版本（不走 Gateway，Agent 直接调用）
 */
class CameraSkill(
    private val context: Context,
    private val cameraManager: CameraCaptureManager,
) : Skill {
    companion object {
        private const val TAG = "CameraSkill"
    }

    override val name = "camera"
    override val description = "Camera operations: list cameras, take photo (snap), or record short video (clip)"

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
                            description = "操作类型: list(列出摄像头), snap(拍照), clip(录像)",
                            enum = listOf("list", "snap", "clip")
                        ),
                        "facing" to PropertySchema(
                            type = "string",
                            description = "摄像头方向: front(前置) 或 back(后置)，默认 back",
                            enum = listOf("front", "back")
                        ),
                        "quality" to PropertySchema(
                            type = "number",
                            description = "JPEG 质量 0.1-1.0，默认 0.95（仅 snap）"
                        ),
                        "max_width" to PropertySchema(
                            type = "number",
                            description = "最大图片宽度（像素），默认 1600（仅 snap）"
                        ),
                        "duration_ms" to PropertySchema(
                            type = "number",
                            description = "录像时长（毫秒），默认 3000，最大 60000（仅 clip）"
                        ),
                        "include_audio" to PropertySchema(
                            type = "boolean",
                            description = "是否录制音频，默认 true（仅 clip）"
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

        return when (action) {
            "list" -> executeList()
            "snap" -> executeSnap(args)
            "clip" -> executeClip(args)
            else -> SkillResult.error("Unknown action: $action. Use: list, snap, clip")
        }
    }

    /**
     * 列出可用摄像头
     */
    private suspend fun executeList(): SkillResult {
        return try {
            val devices = cameraManager.listDevices()
            if (devices.isEmpty()) {
                return SkillResult.success("没有检测到可用摄像头")
            }
            val output = buildString {
                appendLine("可用摄像头 (${devices.size} 个):")
                devices.forEach { d ->
                    appendLine("  - id: ${d.id}, name: ${d.name}, position: ${d.position}, type: ${d.deviceType}")
                }
            }
            SkillResult.success(output, mapOf("device_count" to devices.size))
        } catch (e: Exception) {
            Log.e(TAG, "camera.list failed", e)
            SkillResult.error("列出摄像头失败: ${e.message}")
        }
    }

    /**
     * 拍照
     */
    private suspend fun executeSnap(args: Map<String, Any?>): SkillResult {
        return try {
            val facing = (args["facing"] as? String)?.lowercase() ?: "back"
            val quality = (args["quality"] as? Number)?.toDouble() ?: 0.95
            val maxWidth = (args["max_width"] as? Number)?.toInt() ?: 1600
            val deviceId = args["device_id"] as? String

            Log.d(TAG, "camera.snap: facing=$facing, quality=$quality, maxWidth=$maxWidth")

            val result = cameraManager.snap(
                facing = facing,
                quality = quality,
                maxWidth = maxWidth,
                deviceId = deviceId,
            )

            // 保存照片到工作空间
            val photoDir = File(StoragePaths.workspace, "camera").apply { mkdirs() }
            val photoFile = File(photoDir, "snap_${System.currentTimeMillis()}.jpg")
            withContext(Dispatchers.IO) {
                val bytes = android.util.Base64.decode(result.base64, android.util.Base64.NO_WRAP)
                photoFile.writeBytes(bytes)
            }

            val output = buildString {
                appendLine("📸 拍照成功")
                appendLine("分辨率: ${result.width}x${result.height}")
                appendLine("格式: ${result.format}")
                appendLine("文件: ${photoFile.absolutePath}")
            }

            SkillResult.success(
                output,
                mapOf(
                    "format" to result.format,
                    "width" to result.width,
                    "height" to result.height,
                    "file_path" to photoFile.absolutePath,
                    "base64" to result.base64,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "camera.snap failed", e)
            SkillResult.error("拍照失败: ${e.message}")
        }
    }

    /**
     * 录像
     */
    private suspend fun executeClip(args: Map<String, Any?>): SkillResult {
        return try {
            val facing = (args["facing"] as? String)?.lowercase() ?: "back"
            val durationMs = (args["duration_ms"] as? Number)?.toInt() ?: 3000
            val includeAudio = (args["include_audio"] as? Boolean) ?: true
            val deviceId = args["device_id"] as? String

            Log.d(TAG, "camera.clip: facing=$facing, duration=$durationMs, audio=$includeAudio")

            val result = cameraManager.clip(
                facing = facing,
                durationMs = durationMs,
                includeAudio = includeAudio,
                deviceId = deviceId,
            )

            // 保存视频到工作空间
            val videoDir = File(StoragePaths.workspace, "camera").apply { mkdirs() }
            val videoFile = File(videoDir, "clip_${System.currentTimeMillis()}.mp4")
            withContext(Dispatchers.IO) {
                val bytes = android.util.Base64.decode(result.base64, android.util.Base64.NO_WRAP)
                videoFile.writeBytes(bytes)
            }

            val output = buildString {
                appendLine("🎬 录像成功")
                appendLine("时长: ${result.durationMs}ms")
                appendLine("格式: ${result.format}")
                appendLine("音频: ${if (result.hasAudio) "有" else "无"}")
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
            Log.e(TAG, "camera.clip failed", e)
            SkillResult.error("录像失败: ${e.message}")
        }
    }
}

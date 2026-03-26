package com.xiaomo.androidforclaw.camera

/**
 * OpenClaw Source Reference:
 * - ../openclaw/apps/android/app/src/main/java/ai/openclaw/app/node/CameraCaptureManager.kt
 *
 * AndroidForClaw adaptation: 相机捕获管理器
 * 基于 CameraX，支持拍照(snap)和录像(clip)
 */

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.util.Base64
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import android.hardware.camera2.CameraCharacteristics
import com.xiaomo.androidforclaw.logging.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * 相机捕获管理器
 * 对齐 OpenClaw CameraCaptureManager
 */
class CameraCaptureManager(private val context: Context) {
    companion object {
        private const val TAG = "CameraCaptureManager"
        /** base64 payload 上限 5MB */
        private const val MAX_PAYLOAD_BYTES = 5 * 1024 * 1024
        /** clip 原始文件上限 18MB */
        const val CLIP_MAX_RAW_BYTES: Long = 18L * 1024L * 1024L
    }

    data class SnapResult(
        val format: String,
        val base64: String,
        val width: Int,
        val height: Int,
    )

    data class ClipResult(
        val format: String,
        val base64: String,
        val durationMs: Long,
        val hasAudio: Boolean,
    )

    data class CameraDeviceInfo(
        val id: String,
        val name: String,
        val position: String,
        val deviceType: String,
    )

    @Volatile
    private var lifecycleOwner: LifecycleOwner? = null

    fun attachLifecycleOwner(owner: LifecycleOwner) {
        lifecycleOwner = owner
    }

    /**
     * 列出可用摄像头
     */
    suspend fun listDevices(): List<CameraDeviceInfo> =
        withContext(Dispatchers.Main) {
            val provider = context.cameraProvider()
            provider.availableCameraInfos
                .mapNotNull { info -> cameraDeviceInfoOrNull(info) }
                .sortedBy { it.id }
        }

    /**
     * 拍照
     * @param facing "front" 或 "back"，默认 "back"
     * @param quality JPEG 质量 0.0-1.0，默认 0.95
     * @param maxWidth 最大宽度，默认 1600
     * @param deviceId 指定摄像头 ID（可选）
     */
    suspend fun snap(
        facing: String = "back",
        quality: Double = 0.95,
        maxWidth: Int = 1600,
        deviceId: String? = null,
    ): SnapResult = withContext(Dispatchers.Main) {
        ensureCameraPermission()
        val owner = lifecycleOwner
            ?: throw IllegalStateException("UNAVAILABLE: camera not ready, no LifecycleOwner attached")

        val clampedQuality = quality.coerceIn(0.1, 1.0)
        val provider = context.cameraProvider()
        val capture = ImageCapture.Builder().build()
        val selector = resolveCameraSelector(provider, facing, deviceId)

        provider.unbindAll()
        provider.bindToLifecycle(owner, selector, capture)

        val (bytes, orientation) = capture.takeJpegWithExif(context.mainExecutor())
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("UNAVAILABLE: failed to decode captured image")
        val rotated = rotateBitmapByExif(decoded, orientation)
        val scaled = if (maxWidth > 0 && rotated.width > maxWidth) {
            val h = (rotated.height.toDouble() * (maxWidth.toDouble() / rotated.width.toDouble()))
                .toInt().coerceAtLeast(1)
            val s = rotated.scale(maxWidth, h)
            if (s !== rotated) rotated.recycle()
            s
        } else {
            rotated
        }

        try {
            val maxEncodedBytes = (MAX_PAYLOAD_BYTES / 4) * 3
            val result = JpegSizeLimiter.compressToLimit(
                initialWidth = scaled.width,
                initialHeight = scaled.height,
                startQuality = (clampedQuality * 100.0).roundToInt().coerceIn(10, 100),
                maxBytes = maxEncodedBytes,
                encode = { width, height, q ->
                    val bitmap = if (width == scaled.width && height == scaled.height) {
                        scaled
                    } else {
                        scaled.scale(width, height)
                    }
                    val out = ByteArrayOutputStream()
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, q, out)) {
                        if (bitmap !== scaled) bitmap.recycle()
                        throw IllegalStateException("UNAVAILABLE: failed to encode JPEG")
                    }
                    if (bitmap !== scaled) bitmap.recycle()
                    out.toByteArray()
                },
            )
            val base64 = Base64.encodeToString(result.bytes, Base64.NO_WRAP)
            SnapResult(
                format = "jpg",
                base64 = base64,
                width = result.width,
                height = result.height,
            )
        } finally {
            scaled.recycle()
            provider.unbindAll()
        }
    }

    /**
     * 录像
     * @param facing "front" 或 "back"，默认 "back"
     * @param durationMs 录制时长（毫秒），默认 3000，最大 60000
     * @param includeAudio 是否录制音频，默认 true
     * @param deviceId 指定摄像头 ID（可选）
     */
    @SuppressLint("MissingPermission")
    suspend fun clip(
        facing: String = "back",
        durationMs: Int = 3000,
        includeAudio: Boolean = true,
        deviceId: String? = null,
    ): ClipResult = withContext(Dispatchers.Main) {
        ensureCameraPermission()
        if (includeAudio) ensureMicPermission()
        val owner = lifecycleOwner
            ?: throw IllegalStateException("UNAVAILABLE: camera not ready, no LifecycleOwner attached")

        val clampedDuration = durationMs.coerceIn(200, 60_000)
        Log.d(TAG, "clip: start facing=$facing duration=$clampedDuration audio=$includeAudio")

        val provider = context.cameraProvider()
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(Quality.LOWEST, FallbackStrategy.lowerQualityOrHigherThan(Quality.LOWEST))
            )
            .build()
        val videoCapture = VideoCapture.withOutput(recorder)
        val selector = resolveCameraSelector(provider, facing, deviceId)

        // CameraX 需要 Preview use case 才能产生帧
        val preview = Preview.Builder().build()
        val surfaceTexture = SurfaceTexture(0)
        surfaceTexture.setDefaultBufferSize(640, 480)
        preview.setSurfaceProvider { request ->
            val surface = Surface(surfaceTexture)
            request.provideSurface(surface, context.mainExecutor()) {
                surface.release()
                surfaceTexture.release()
            }
        }

        provider.unbindAll()
        provider.bindToLifecycle(owner, selector, preview, videoCapture)

        // 等相机初始化
        delay(1_500)

        val file = File.createTempFile("claw-clip-", ".mp4", context.cacheDir)
        val outputOptions = FileOutputOptions.Builder(file).build()

        val finalized = CompletableDeferred<VideoRecordEvent.Finalize>()
        val recording: Recording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .apply { if (includeAudio) withAudioEnabled() }
            .start(context.mainExecutor()) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    finalized.complete(event)
                }
            }

        try {
            delay(clampedDuration.toLong())
        } finally {
            recording.stop()
        }

        val finalizeEvent = try {
            withTimeout(15_000) { finalized.await() }
        } catch (err: Throwable) {
            withContext(Dispatchers.IO) { file.delete() }
            provider.unbindAll()
            throw IllegalStateException("UNAVAILABLE: camera clip finalize timed out")
        }

        if (finalizeEvent.hasError()) {
            withContext(Dispatchers.IO) { file.delete() }
            provider.unbindAll()
            throw IllegalStateException("UNAVAILABLE: camera clip failed (error=${finalizeEvent.error})")
        }

        val rawBytes = withContext(Dispatchers.IO) { file.length() }
        if (rawBytes > CLIP_MAX_RAW_BYTES) {
            withContext(Dispatchers.IO) { file.delete() }
            provider.unbindAll()
            throw IllegalStateException("PAYLOAD_TOO_LARGE: camera clip is $rawBytes bytes; max is $CLIP_MAX_RAW_BYTES bytes")
        }

        val bytes = withContext(Dispatchers.IO) {
            try {
                file.readBytes()
            } finally {
                file.delete()
            }
        }

        provider.unbindAll()

        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        ClipResult(
            format = "mp4",
            base64 = base64,
            durationMs = clampedDuration.toLong(),
            hasAudio = includeAudio,
        )
    }

    // ========== Private helpers ==========

    private fun ensureCameraPermission() {
        if (checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            throw IllegalStateException("CAMERA_PERMISSION_REQUIRED: grant Camera permission in system settings")
        }
    }

    private fun ensureMicPermission() {
        if (checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw IllegalStateException("MIC_PERMISSION_REQUIRED: grant Microphone permission in system settings")
        }
    }

    private fun rotateBitmapByExif(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f); matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun resolveCameraSelector(
        provider: ProcessCameraProvider,
        facing: String,
        deviceId: String?,
    ): CameraSelector {
        if (deviceId.isNullOrEmpty()) {
            return if (facing == "front") CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA
        }
        val availableIds = provider.availableCameraInfos.mapNotNull { cameraIdOrNull(it) }.toSet()
        if (!availableIds.contains(deviceId)) {
            throw IllegalStateException("INVALID_REQUEST: unknown camera deviceId '$deviceId'")
        }
        return CameraSelector.Builder()
            .addCameraFilter { infos -> infos.filter { cameraIdOrNull(it) == deviceId } }
            .build()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun cameraDeviceInfoOrNull(info: CameraInfo): CameraDeviceInfo? {
        val cameraId = cameraIdOrNull(info) ?: return null
        val lensFacing = runCatching {
            Camera2CameraInfo.from(info).getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
        }.getOrNull()
        val position = when (lensFacing) {
            CameraCharacteristics.LENS_FACING_FRONT -> "front"
            CameraCharacteristics.LENS_FACING_BACK -> "back"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
            else -> "unspecified"
        }
        val deviceType = if (lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL) "external" else "builtIn"
        val name = when (position) {
            "front" -> "Front Camera"
            "back" -> "Back Camera"
            "external" -> "External Camera"
            else -> "Camera $cameraId"
        }
        return CameraDeviceInfo(id = cameraId, name = name, position = position, deviceType = deviceType)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun cameraIdOrNull(info: CameraInfo): String? =
        runCatching { Camera2CameraInfo.from(info).cameraId }.getOrNull()

    private fun Context.mainExecutor(): Executor = ContextCompat.getMainExecutor(this)
}

/** 挂起获取 CameraProvider */
private suspend fun Context.cameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

/** 拍照并获取 JPEG bytes + EXIF orientation */
private suspend fun ImageCapture.takeJpegWithExif(executor: Executor): Pair<ByteArray, Int> =
    suspendCancellableCoroutine { cont ->
        val file = File.createTempFile("claw-snap-", ".jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        takePicture(
            options,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    cont.resumeWithException(exception)
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    try {
                        val exif = ExifInterface(file.absolutePath)
                        val orientation = exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL,
                        )
                        val bytes = file.readBytes()
                        cont.resume(Pair(bytes, orientation))
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    } finally {
                        file.delete()
                    }
                }
            },
        )
    }

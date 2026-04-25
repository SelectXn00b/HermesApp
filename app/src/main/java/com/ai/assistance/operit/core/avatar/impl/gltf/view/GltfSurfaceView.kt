package com.ai.assistance.operit.core.avatar.impl.gltf.view

import android.content.Context
import android.net.Uri
import android.graphics.PixelFormat
import android.util.Base64
import android.util.AttributeSet
import android.view.Choreographer
import android.view.SurfaceView
import com.ai.assistance.operit.util.AppLogger
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.View
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.Manipulator
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class GltfSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), Choreographer.FrameCallback {

    private data class PreparedGltfModel(
        val modelFile: File,
        val mimeHints: Map<String, String>
    )

    private data class TextureDiagnostics(
        val imageCount: Int,
        val textureCount: Int,
        val materialCount: Int,
        val materialsWithTextureRefs: Int,
        val externalImageFileCount: Int,
        val sampleExternalImageFiles: List<String>,
        val unsupportedExternalImageFileCount: Int,
        val sampleUnsupportedExternalImageFiles: List<String>
    )

    companion object {
        private const val TAG = "GltfSurfaceView"
        private const val DEFAULT_CAMERA_PITCH = 8.0f
        private const val DEFAULT_CAMERA_YAW = 0.0f
        private const val DEFAULT_CAMERA_DISTANCE_SCALE = 0.5f
        private const val MIN_CAMERA_DISTANCE_SCALE = 0.0f
        private const val MAX_CAMERA_DISTANCE_SCALE = 10.0f
        private const val MIN_CAMERA_TARGET_HEIGHT = -2.0f
        private const val MAX_CAMERA_TARGET_HEIGHT = 2.0f
        private const val BASE_CAMERA_DISTANCE = 0.5
        private const val CAMERA_DISTANCE_EPSILON = 1e-6
        private const val CAMERA_TARGET_X = 0.0
        private const val CAMERA_TARGET_Y = 0.0
        private const val CAMERA_TARGET_Z = 0.0
        private const val MODEL_ORBIT_PIVOT_Y = 0.0f
        private val SUPPORTED_TEXTURE_MIME_TYPES = setOf(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/ktx2"
        )
        private val UNSUPPORTED_TEXTURE_EXTENSIONS = setOf(
            "tga",
            "bmp",
            "gif",
            "dds",
            "tif",
            "tiff",
            "hdr",
            "exr"
        )

        init {
            Utils.init()
        }
    }

    private val choreographer = Choreographer.getInstance()

    private val modelViewer = ModelViewer(this)

    private var currentModelPath: String? = null
    private var requestedAnimationName: String? = null
    private var requestedLooping: Boolean = false
    private var requestedPlaybackNonce: Long = -1L

    private var cameraPitchDegrees: Float = DEFAULT_CAMERA_PITCH
    private var cameraYawDegrees: Float = DEFAULT_CAMERA_YAW
    private var cameraDistanceScale: Float = DEFAULT_CAMERA_DISTANCE_SCALE
    private var cameraTargetHeightOffset: Float = 0.0f
    private var cameraTargetX: Double = CAMERA_TARGET_X
    private var cameraTargetY: Double = CAMERA_TARGET_Y
    private var cameraTargetZ: Double = CAMERA_TARGET_Z

    private var activeAnimationIndex: Int = -1
    private var animationStartNanos: Long = System.nanoTime()

    private var sunLightEntity: Int = 0
    private var fillLightEntity: Int = 0
    private var rimLightEntity: Int = 0
    private var indirectLight: IndirectLight? = null

    private var baseRootTransform: FloatArray? = null
    private var baseEntityTransforms: Map<Int, FloatArray> = emptyMap()

    private var hasLoggedManipulatorUnavailable: Boolean = false
    private var hasLoggedManipulatorApplyFailure: Boolean = false

    private val cameraManipulatorField by lazy {
        runCatching {
            ModelViewer::class.java.declaredFields
                .firstOrNull { field -> field.type == Manipulator::class.java }
                ?.apply { isAccessible = true }
        }.getOrNull()
    }

    private val uiHelperField by lazy {
        runCatching {
            ModelViewer::class.java.declaredFields
                .firstOrNull { field -> field.type == UiHelper::class.java }
                ?.apply { isAccessible = true }
        }.getOrNull()
    }

    private var animationNames: List<String> = emptyList()
    private var animationNameToIndex: Map<String, Int> = emptyMap()
    private var animationDurations: Map<Int, Float> = emptyMap()
    private var retainedModelSourceBuffer: ByteBuffer? = null
    private var retainedResourceBuffers: List<ByteBuffer> = emptyList()
    private var gltfResourceMimeHints: Map<String, String> = emptyMap()
    private var pendingModelPath: String? = null
    private var hasValidSurfaceSize: Boolean = false
    private var isModelLoadScheduled: Boolean = false
    private var modelLoadGeneration: Int = 0

    @Volatile
    private var isRendering: Boolean = false

    @Volatile
    private var onRenderErrorListener: ((String) -> Unit)? = null

    @Volatile
    private var onAnimationsDiscoveredListener: ((List<String>, Map<String, Long>) -> Unit)? = null

    init {
        setupTransparentSurface()
        setupCameraDefaults()
        setupSceneLighting()
    }

    fun setOnRenderErrorListener(listener: ((String) -> Unit)?) {
        onRenderErrorListener = listener
    }

    fun setOnAnimationsDiscoveredListener(listener: ((List<String>, Map<String, Long>) -> Unit)?) {
        onAnimationsDiscoveredListener = listener
    }

    fun setModelPath(path: String) {
        val normalizedPath = path.trim()
        if (normalizedPath.isEmpty() || normalizedPath == currentModelPath || normalizedPath == pendingModelPath) {
            return
        }

        pendingModelPath = normalizedPath
        schedulePendingModelLoad()
    }

    fun setAnimationState(animationName: String?, isLooping: Boolean, playbackNonce: Long) {
        val normalizedName = animationName?.trim()?.takeIf { it.isNotEmpty() }
        if (
            requestedAnimationName == normalizedName &&
            requestedLooping == isLooping &&
            requestedPlaybackNonce == playbackNonce
        ) {
            return
        }

        requestedAnimationName = normalizedName
        requestedLooping = isLooping
        requestedPlaybackNonce = playbackNonce
        restartAnimationClock()
        applyRequestedAnimationSelection()
    }

    fun setCameraPose(
        pitchDegrees: Float,
        yawDegrees: Float,
        distanceScale: Float,
        targetHeightOffset: Float
    ) {
        val clampedPitch = pitchDegrees.coerceIn(-89f, 89f)
        val normalizedYaw = yawDegrees.coerceIn(-180f, 180f)
        val clampedDistanceScale = distanceScale.coerceIn(MIN_CAMERA_DISTANCE_SCALE, MAX_CAMERA_DISTANCE_SCALE)
        val clampedTargetHeight = targetHeightOffset.coerceIn(MIN_CAMERA_TARGET_HEIGHT, MAX_CAMERA_TARGET_HEIGHT)

        val unchanged =
            cameraPitchDegrees == clampedPitch &&
                cameraYawDegrees == normalizedYaw &&
                cameraDistanceScale == clampedDistanceScale &&
                cameraTargetHeightOffset == clampedTargetHeight
        if (unchanged) {
            return
        }

        cameraPitchDegrees = clampedPitch
        cameraYawDegrees = normalizedYaw
        cameraDistanceScale = clampedDistanceScale
        cameraTargetHeightOffset = clampedTargetHeight
        applyCameraPose()
    }

    fun onResume() {
        if (isRendering) {
            return
        }
        isRendering = true
        restartAnimationClock()
        choreographer.postFrameCallback(this)
    }

    fun onPause() {
        if (!isRendering) {
            return
        }
        isRendering = false
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRendering) {
            return
        }

        choreographer.postFrameCallback(this)
        renderFrame(frameTimeNanos)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        hasValidSurfaceSize = w > 0 && h > 0
        if (hasValidSurfaceSize) {
            schedulePendingModelLoad()
        }
        applyCameraPose()
    }

    override fun onDetachedFromWindow() {
        onPause()
        hasValidSurfaceSize = false
        isModelLoadScheduled = false
        pendingModelPath = null
        super.onDetachedFromWindow()
    }

    private fun renderFrame(frameTimeNanos: Long) {
        try {
            // Re-apply camera each frame because ModelViewer.render() internally updates camera from manipulator.
            applyCameraPose()
            applyAnimation(frameTimeNanos)
            modelViewer.render(frameTimeNanos)
        } catch (e: Exception) {
            dispatchError("Failed to render glTF frame: ${e.message ?: "unknown error"}")
        }
    }

    private fun applyAnimation(frameTimeNanos: Long) {
        val animator = modelViewer.animator ?: return
        val animationCount = animator.animationCount
        if (animationCount <= 0) {
            return
        }

        val animationIndex = activeAnimationIndex
        if (animationIndex !in 0 until animationCount) {
            return
        }

        val elapsedSeconds = max((frameTimeNanos - animationStartNanos).toDouble() / 1_000_000_000.0, 0.0).toFloat()
        val durationSeconds = animationDurations[animationIndex]
            ?: runCatching { animator.getAnimationDuration(animationIndex) }.getOrNull()

        val sampleTime = when {
            durationSeconds != null && durationSeconds > 0f && requestedLooping -> elapsedSeconds % durationSeconds
            durationSeconds != null && durationSeconds > 0f -> min(elapsedSeconds, durationSeconds)
            else -> elapsedSeconds
        }

        animator.applyAnimation(animationIndex, sampleTime)
        animator.updateBoneMatrices()
    }

    private fun loadModel(
        modelPath: String,
        sourceModelFile: File = File(modelPath),
        mimeHints: Map<String, String> = emptyMap()
    ) {
        val modelFile = sourceModelFile
        if (!modelFile.exists() || !modelFile.isFile) {
            dispatchError("glTF model file not found: $modelPath")
            return
        }

        val extension = modelFile.extension.lowercase()
        if (extension != "glb" && extension != "gltf") {
            dispatchError("Unsupported glTF model extension: .$extension (expected .glb or .gltf)")
            return
        }

        try {
            val nextResourceBuffers = ArrayList<ByteBuffer>()
            if (extension == "gltf") {
                gltfResourceMimeHints = mimeHints
            } else {
                gltfResourceMimeHints = emptyMap()
            }
            val modelBuffer = readFileToDirectByteBuffer(modelFile)
            if (extension == "glb") {
                modelViewer.loadModelGlb(modelBuffer)
            } else {
                val baseDirectory = modelFile.parentFile
                modelViewer.loadModelGltf(modelBuffer) { relativeUri ->
                    readResourceToDirectByteBuffer(baseDirectory, relativeUri).also(nextResourceBuffers::add)
                }
            }
            retainedModelSourceBuffer = modelBuffer
            retainedResourceBuffers = nextResourceBuffers

            currentModelPath = modelPath
            applySafeUnitCubeTransform()
            captureBaseRootTransform()
            applyCameraPose()
            refreshDiscoveredAnimations()
            restartAnimationClock()
            applyRequestedAnimationSelection()

            AppLogger.i(TAG, "Loaded glTF model: $modelPath")
        } catch (e: Exception) {
            runCatching { modelViewer.destroyModel() }
            currentModelPath = null
            activeAnimationIndex = -1
            baseRootTransform = null
            baseEntityTransforms = emptyMap()
            animationNames = emptyList()
            animationNameToIndex = emptyMap()
            animationDurations = emptyMap()
            retainedModelSourceBuffer = null
            retainedResourceBuffers = emptyList()
            gltfResourceMimeHints = emptyMap()
            cameraTargetX = CAMERA_TARGET_X
            cameraTargetY = CAMERA_TARGET_Y
            cameraTargetZ = CAMERA_TARGET_Z
            onAnimationsDiscoveredListener?.invoke(emptyList(), emptyMap())
            dispatchError("Failed to load glTF model: ${e.message ?: "unknown error"}")
        }
    }

    private fun schedulePendingModelLoad() {
        if (!hasValidSurfaceSize || !isAttachedToWindow || isModelLoadScheduled) {
            return
        }
        val requestedPath = pendingModelPath ?: return
        if (requestedPath == currentModelPath) {
            pendingModelPath = null
            return
        }

        isModelLoadScheduled = true
        post {
            isModelLoadScheduled = false
            if (!hasValidSurfaceSize || !isAttachedToWindow) {
                return@post
            }

            val nextPath = pendingModelPath ?: return@post
            if (nextPath == currentModelPath) {
                pendingModelPath = null
                return@post
            }

            pendingModelPath = null
            beginModelLoad(nextPath)
        }
    }

    private fun beginModelLoad(modelPath: String) {
        val generation = ++modelLoadGeneration
        val sourceFile = resolveSourceModelFile(modelPath)
        val extension = sourceFile.extension.lowercase()
        if (extension != "gltf") {
            loadModel(modelPath, sourceModelFile = sourceFile)
            return
        }

        Thread(
            {
                val prepared = runCatching { prepareGltfModelForLoad(sourceFile) }
                post {
                    if (generation != modelLoadGeneration || !isAttachedToWindow) {
                        return@post
                    }
                    prepared
                        .onSuccess { result ->
                            loadModel(
                                modelPath = modelPath,
                                sourceModelFile = result.modelFile,
                                mimeHints = result.mimeHints
                            )
                        }
                        .onFailure { error ->
                            dispatchError("Failed to prepare glTF model: ${error.message ?: "unknown error"}")
                        }
                }
            },
            "gltf-prepare"
        ).start()
    }

    private fun resolveSourceModelFile(modelPath: String): File {
        val candidate = File(modelPath)
        if (!candidate.name.startsWith(".operit_normalized_", ignoreCase = true)) {
            return candidate
        }

        val originalName = candidate.name.removePrefix(".operit_normalized_")
        if (originalName.isBlank()) {
            return candidate
        }

        val siblingOriginal = File(candidate.parentFile, originalName)
        return if (siblingOriginal.exists() && siblingOriginal.isFile) {
            siblingOriginal
        } else {
            candidate
        }
    }

    private fun prepareGltfModelForLoad(modelFile: File): PreparedGltfModel {
        val root = JSONObject(modelFile.readText())
        val workspaceDir = prepareWorkspaceDirForModel(modelFile)
        val inlineDir = File(workspaceDir, "inline")
        val sourceBaseDir = modelFile.parentFile
        val bufferBytesCache = HashMap<Int, ByteArray>()
        var changed = false

        val buffers = root.optJSONArray("buffers")
        if (buffers != null) {
            for (index in 0 until buffers.length()) {
                val bufferObj = buffers.optJSONObject(index) ?: continue
                val uri = bufferObj.optString("uri").trim()
                if (!uri.startsWith("data:", ignoreCase = true)) {
                    continue
                }

                val bytes = decodeDataUriToByteArray(uri)
                inlineDir.mkdirs()
                val fileName = "buffer_$index.bin"
                val outputFile = File(inlineDir, fileName)
                if (!outputFile.exists() || outputFile.length() != bytes.size.toLong()) {
                    outputFile.writeBytes(bytes)
                }
                bufferObj.put("uri", "inline/$fileName")
                bufferBytesCache[index] = bytes
                changed = true
            }
        }

        val images = root.optJSONArray("images")
        if (images != null) {
            for (index in 0 until images.length()) {
                val imageObj = images.optJSONObject(index) ?: continue
                val uri = imageObj.optString("uri").trim()
                val declaredMimeType = canonicalizeImageMimeType(imageObj.optString("mimeType").trim())
                if (declaredMimeType.isNotEmpty() && declaredMimeType != imageObj.optString("mimeType").trim().lowercase()) {
                    imageObj.put("mimeType", declaredMimeType)
                    changed = true
                }
                if (!uri.startsWith("data:", ignoreCase = true)) {
                    continue
                }

                val bytes = decodeDataUriToByteArray(uri)
                val dataUriMimeType = inferDataUriMimeType(uri)
                val sniffedMimeType = inferMimeTypeFromBytes(bytes)
                val effectiveMimeType = declaredMimeType
                    .ifBlank { dataUriMimeType }
                    .ifBlank { sniffedMimeType }
                if (declaredMimeType.isBlank() && effectiveMimeType.isNotBlank()) {
                    imageObj.put("mimeType", effectiveMimeType)
                }
                val imageExt = imageFileExtensionForMime(effectiveMimeType)

                inlineDir.mkdirs()
                inlineDir.listFiles { file ->
                    file.isFile && file.name.startsWith("image_${index}.") && file.extension.lowercase() != imageExt.lowercase()
                }?.forEach { stale -> runCatching { stale.delete() } }
                val fileName = "image_$index.$imageExt"
                val outputFile = File(inlineDir, fileName)
                if (!outputFile.exists() || outputFile.length() != bytes.size.toLong()) {
                    outputFile.writeBytes(bytes)
                }
                imageObj.put("uri", "inline/$fileName")
                changed = true
            }
        }

        val bufferViews = root.optJSONArray("bufferViews")
        if (images != null && bufferViews != null && buffers != null) {
            for (index in 0 until images.length()) {
                val imageObj = images.optJSONObject(index) ?: continue
                val uri = imageObj.optString("uri").trim()
                if (uri.isNotEmpty()) {
                    continue
                }

                val declaredMimeType = canonicalizeImageMimeType(imageObj.optString("mimeType").trim())
                if (declaredMimeType.isNotEmpty()) {
                    if (declaredMimeType != imageObj.optString("mimeType").trim().lowercase()) {
                        imageObj.put("mimeType", declaredMimeType)
                        changed = true
                    }
                    continue
                }

                val bufferViewIndex = imageObj.optInt("bufferView", -1)
                if (bufferViewIndex < 0) {
                    continue
                }
                val bufferViewObj = bufferViews.optJSONObject(bufferViewIndex) ?: continue
                val bufferIndex = bufferViewObj.optInt("buffer", -1)
                val byteOffset = bufferViewObj.optInt("byteOffset", 0)
                val byteLength = bufferViewObj.optInt("byteLength", -1)
                if (bufferIndex < 0 || byteLength <= 0 || byteOffset < 0) {
                    continue
                }

                val bufferBytes = bufferBytesCache.getOrPut(bufferIndex) {
                    val bufferObj = buffers.optJSONObject(bufferIndex)
                    val bufferUri = bufferObj?.optString("uri").orEmpty().trim()
                    when {
                        bufferUri.isEmpty() -> ByteArray(0)
                        bufferUri.startsWith("data:", ignoreCase = true) -> decodeDataUriToByteArray(bufferUri)
                        else -> resolveResourceFile(sourceBaseDir, bufferUri).readBytes()
                    }
                }
                if (bufferBytes.isEmpty()) {
                    continue
                }
                val endExclusive = byteOffset + byteLength
                if (endExclusive > bufferBytes.size) {
                    continue
                }

                val sampleSize = min(64, byteLength)
                val sample = bufferBytes.copyOfRange(byteOffset, byteOffset + sampleSize)
                val sniffedMimeType = inferMimeTypeFromBytes(sample)
                if (sniffedMimeType.isNotEmpty()) {
                    imageObj.put("mimeType", sniffedMimeType)
                    changed = true
                }
            }
        }

        val autoLinkedTextureCount = autoLinkMissingBaseColorTextures(
            root = root,
            sourceBaseDir = sourceBaseDir,
            inlineDir = inlineDir
        )
        if (autoLinkedTextureCount > 0) {
            changed = true
        }

        val textureDiagnostics = collectTextureDiagnostics(root, sourceBaseDir)
        if (textureDiagnostics.materialsWithTextureRefs == 0 && textureDiagnostics.externalImageFileCount > 0) {
            val samples = textureDiagnostics.sampleExternalImageFiles.joinToString()
            val unsupportedSamples = textureDiagnostics.sampleUnsupportedExternalImageFiles.joinToString()
            AppLogger.w(
                TAG,
                "glTF has no texture references but directory has image files. " +
                    "source=${modelFile.name}, images=${textureDiagnostics.imageCount}, textures=${textureDiagnostics.textureCount}, " +
                    "materials=${textureDiagnostics.materialCount}, externalImages=${textureDiagnostics.externalImageFileCount}, sample=[$samples], " +
                    "unsupportedExternalImages=${textureDiagnostics.unsupportedExternalImageFileCount}, unsupportedSample=[$unsupportedSamples]"
            )
        }

        val preparedFile = if (changed) {
            val normalizedFile = File(workspaceDir, "normalized.gltf")
            normalizedFile.writeText(toNormalizedGltfJson(root))
            normalizedFile
        } else {
            modelFile
        }

        AppLogger.i(
            TAG,
            "Prepared glTF model for load: source=${modelFile.name}, prepared=${preparedFile.name}, changed=$changed, " +
                "images=${textureDiagnostics.imageCount}, textures=${textureDiagnostics.textureCount}, " +
                "materialsWithTextureRefs=${textureDiagnostics.materialsWithTextureRefs}/${textureDiagnostics.materialCount}, " +
                "autoLinkedTextures=$autoLinkedTextureCount"
        )

        return PreparedGltfModel(
            modelFile = preparedFile,
            mimeHints = buildGltfResourceMimeHints(root)
        )
    }

    private fun prepareWorkspaceDirForModel(modelFile: File): File {
        val sourcePath = runCatching { modelFile.canonicalPath }.getOrElse { modelFile.absolutePath }
        val sourceHash = sourcePath.hashCode().toUInt().toString(16)
        val workspaceDir = File(context.cacheDir, "gltf_prepared/$sourceHash")
        workspaceDir.mkdirs()
        return workspaceDir
    }

    private fun toNormalizedGltfJson(root: JSONObject): String {
        // Filament's glTF URI / mime parsing can mis-handle escaped slashes like `\/`.
        // Serialize JSON without slash escaping so values stay as `inline/...` and `image/png`.
        return root.toString().replace("\\/", "/")
    }

    private fun autoLinkMissingBaseColorTextures(
        root: JSONObject,
        sourceBaseDir: File?,
        inlineDir: File
    ): Int {
        val materials = root.optJSONArray("materials") ?: return 0
        if (materials.length() == 0) {
            return 0
        }
        val currentImages = root.optJSONArray("images")
        val currentTextures = root.optJSONArray("textures")
        if ((currentImages?.length() ?: 0) > 0 || (currentTextures?.length() ?: 0) > 0) {
            return 0
        }

        val candidateFiles = listSupportedImageFiles(sourceBaseDir)
            .filter { file ->
                val normalizedName = normalizeNameForMatch(file.nameWithoutExtension)
                normalizedName.contains("diffuse") ||
                    normalizedName.contains("basecolor") ||
                    normalizedName.contains("albedo")
            }
        if (candidateFiles.isEmpty()) {
            return 0
        }

        val images = currentImages ?: JSONArray().also { root.put("images", it) }
        val textures = currentTextures ?: JSONArray().also { root.put("textures", it) }
        val textureIndexBySourceName = LinkedHashMap<String, Int>()
        val linkedDetails = ArrayList<String>()
        val unmatchedMaterials = ArrayList<String>()
        var pbrNormalizedCount = 0
        var unlitNormalizedCount = 0
        var liftedBaseColorCount = 0
        var linkedCount = 0

        for (index in 0 until materials.length()) {
            val material = materials.optJSONObject(index) ?: continue
            if (materialHasTextureReference(material)) {
                continue
            }

            val materialName = material.optString("name").trim()
            val matchedFile = findBestTextureFileForMaterial(materialName, candidateFiles) ?: continue
            val textureIndex = textureIndexBySourceName.getOrPut(matchedFile.name) {
                val imageIndex = images.length()
                val copiedName = "auto_image_${imageIndex}.${matchedFile.extension.lowercase()}"
                inlineDir.mkdirs()
                val copiedFile = File(inlineDir, copiedName)
                if (!copiedFile.exists() || copiedFile.length() != matchedFile.length()) {
                    copiedFile.writeBytes(matchedFile.readBytes())
                }

                val imageObject = JSONObject().apply {
                    put("uri", "inline/$copiedName")
                    val mimeType = mimeTypeForSupportedImageExtension(matchedFile.extension)
                    if (mimeType.isNotEmpty()) {
                        put("mimeType", mimeType)
                    }
                }
                images.put(imageObject)

                val textureObject = JSONObject().apply {
                    put("source", imageIndex)
                }
                textures.put(textureObject)
                textures.length() - 1
            }

            val pbr = material.optJSONObject("pbrMetallicRoughness") ?: JSONObject().also {
                material.put("pbrMetallicRoughness", it)
            }
            pbr.put("baseColorTexture", JSONObject().put("index", textureIndex))
            applyMmdStyleBaseColorLift(pbr)
            liftedBaseColorCount += 1
            // This model family often ships without proper glTF texture bindings and with high metallic
            // factors, which causes near-black shading when environment lighting is reduced.
            // Normalize to a non-metal workflow when we auto-link external baseColor textures.
            pbr.put("metallicFactor", 0.0)
            val roughness = pbr.optDouble("roughnessFactor", 1.0)
            if (!roughness.isNaN() && roughness < 0.82) {
                pbr.put("roughnessFactor", 0.82)
            }

            val materialExtensions = material.optJSONObject("extensions") ?: JSONObject().also {
                material.put("extensions", it)
            }
            if (!materialExtensions.has("KHR_materials_unlit")) {
                materialExtensions.put("KHR_materials_unlit", JSONObject())
                unlitNormalizedCount += 1
            }
            pbrNormalizedCount += 1
            linkedCount += 1
            linkedDetails.add("${if (materialName.isBlank()) "<unnamed:$index>" else materialName}->${matchedFile.name}")
        }

        if (unlitNormalizedCount > 0) {
            ensureTopLevelGltfExtensionUsed(root, "KHR_materials_unlit")
        }

        for (index in 0 until materials.length()) {
            val material = materials.optJSONObject(index) ?: continue
            if (materialHasTextureReference(material)) {
                continue
            }
            val materialName = material.optString("name").trim()
            unmatchedMaterials.add(if (materialName.isBlank()) "<unnamed:$index>" else materialName)
        }

        if (linkedCount > 0) {
            AppLogger.w(
                TAG,
                "Auto-linked missing glTF baseColor textures by file name: linked=$linkedCount, candidates=${candidateFiles.size}, normalizedPbr=$pbrNormalizedCount, normalizedUnlit=$unlitNormalizedCount, liftedBaseColor=$liftedBaseColorCount"
            )
            AppLogger.i(TAG, "Applied MMD-style base color lift for auto-linked materials: multiplier=1.22, maxRgb=1.6")
            AppLogger.i(TAG, "Auto-linked material textures: ${linkedDetails.joinToString()}")
        }
        if (unmatchedMaterials.isNotEmpty()) {
            AppLogger.w(TAG, "Materials still without baseColorTexture after auto-link: ${unmatchedMaterials.joinToString()}")
        }
        return linkedCount
    }

    private fun applyMmdStyleBaseColorLift(pbr: JSONObject) {
        val source = pbr.optJSONArray("baseColorFactor")
        val rgba = FloatArray(4)
        rgba[0] = source?.optDouble(0, 1.0)?.toFloat() ?: 1f
        rgba[1] = source?.optDouble(1, 1.0)?.toFloat() ?: 1f
        rgba[2] = source?.optDouble(2, 1.0)?.toFloat() ?: 1f
        rgba[3] = source?.optDouble(3, 1.0)?.toFloat() ?: 1f

        // Match MMD preview's brighter look by lifting base color before rendering.
        val lift = 1.22f
        val maxRgb = 1.6f
        val lifted = JSONArray()
        lifted.put((rgba[0] * lift).coerceIn(0f, maxRgb).toDouble())
        lifted.put((rgba[1] * lift).coerceIn(0f, maxRgb).toDouble())
        lifted.put((rgba[2] * lift).coerceIn(0f, maxRgb).toDouble())
        lifted.put(rgba[3].coerceIn(0f, 1f).toDouble())
        pbr.put("baseColorFactor", lifted)
    }

    private fun ensureTopLevelGltfExtensionUsed(root: JSONObject, extensionName: String) {
        if (extensionName.isBlank()) {
            return
        }

        val extensionsUsed = root.optJSONArray("extensionsUsed") ?: JSONArray().also {
            root.put("extensionsUsed", it)
        }
        for (index in 0 until extensionsUsed.length()) {
            if (extensionsUsed.optString(index) == extensionName) {
                return
            }
        }
        extensionsUsed.put(extensionName)
    }

    private fun listSupportedImageFiles(baseDirectory: File?): List<File> {
        if (baseDirectory == null || !baseDirectory.exists() || !baseDirectory.isDirectory) {
            return emptyList()
        }

        return baseDirectory.listFiles { file ->
            if (!file.isFile) {
                return@listFiles false
            }
            when (file.extension.lowercase()) {
                "png", "jpg", "jpeg", "webp", "ktx2" -> true
                else -> false
            }
        }?.sortedBy { it.name.lowercase() }.orEmpty()
    }

    private fun listImageFilesForDiagnostics(baseDirectory: File?): List<File> {
        if (baseDirectory == null || !baseDirectory.exists() || !baseDirectory.isDirectory) {
            return emptyList()
        }

        return baseDirectory.listFiles { file ->
            if (!file.isFile) {
                return@listFiles false
            }
            when (file.extension.lowercase()) {
                "png", "jpg", "jpeg", "webp", "ktx2", "exr", "hdr", "bmp", "tga", "tif", "tiff", "dds", "gif" -> true
                else -> false
            }
        }?.sortedBy { it.name.lowercase() }.orEmpty()
    }

    private fun findBestTextureFileForMaterial(materialName: String, candidates: List<File>): File? {
        if (materialName.isBlank() || candidates.isEmpty()) {
            return null
        }

        val aliases = buildMaterialAliases(materialName)
        if (aliases.isEmpty()) {
            return null
        }

        val materialDigits = materialName.filter { it.isDigit() }.trimStart('0')
        var bestScore = Int.MIN_VALUE
        var bestFile: File? = null
        for (candidate in candidates) {
            val normalizedCandidate = normalizeNameForMatch(candidate.nameWithoutExtension)
            val aliasScore = aliases.maxOfOrNull { alias ->
                if (normalizedCandidate.contains(alias)) alias.length * 20 else Int.MIN_VALUE
            } ?: Int.MIN_VALUE
            if (aliasScore == Int.MIN_VALUE) {
                continue
            }

            var score = aliasScore
            if (normalizedCandidate.contains("diffuse")) {
                score += 8
            }
            if (normalizedCandidate.contains("basecolor") || normalizedCandidate.contains("albedo")) {
                score += 6
            }

            if (materialDigits.isNotEmpty()) {
                when {
                    normalizedCandidate.contains(materialDigits) -> score += 12
                    normalizedCandidate.any { it.isDigit() } -> score -= 6
                }
            }

            if (score > bestScore) {
                bestScore = score
                bestFile = candidate
            }
        }

        return bestFile
    }

    private fun buildMaterialAliases(materialName: String): List<String> {
        val lowerName = materialName.lowercase()
        val segments = lowerName
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
            .toMutableList()
        while (segments.isNotEmpty() && segments.first() in setOf("ani", "mat", "material", "grokani")) {
            segments.removeAt(0)
        }

        val aliases = LinkedHashSet<String>()
        val genericSegments = setOf("ani", "mat", "material", "grokani", "sticker", "mesh", "model", "part")
        val joined = normalizeNameForMatch(segments.joinToString(""))
        if (joined.isNotBlank()) {
            aliases.add(joined)
        }
        segments.forEach { segment ->
            if (segment in genericSegments) {
                return@forEach
            }
            val normalized = normalizeNameForMatch(segment)
            if (normalized.isNotBlank()) {
                aliases.add(normalized)
            }
        }

        if (aliases.any { it.contains("outfit") }) {
            aliases.add("dress")
        }
        if (aliases.any { it.contains("body") }) {
            aliases.add("main")
            aliases.add("dress")
            aliases.add("face")
        }
        if (aliases.any { it.contains("eyes") || it == "eye" }) {
            aliases.add("face")
            aliases.add("eye")
        }
        if (aliases.any { it.contains("lashes") || it.contains("lash") }) {
            aliases.add("face")
            aliases.add("eye")
        }
        if (aliases.any { it.contains("blush") }) {
            aliases.add("faceblush")
        }
        if (aliases.any { it.contains("spark") }) {
            aliases.add("eyespark")
        }

        return aliases.toList()
    }

    private fun normalizeNameForMatch(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "")
    }

    private fun mimeTypeForSupportedImageExtension(extension: String): String {
        return when (extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "ktx2" -> "image/ktx2"
            else -> ""
        }
    }

    private fun materialHasTextureReference(material: JSONObject): Boolean {
        val pbr = material.optJSONObject("pbrMetallicRoughness")
        if (pbr?.optJSONObject("baseColorTexture") != null) return true
        if (pbr?.optJSONObject("metallicRoughnessTexture") != null) return true
        if (material.optJSONObject("normalTexture") != null) return true
        if (material.optJSONObject("occlusionTexture") != null) return true
        if (material.optJSONObject("emissiveTexture") != null) return true

        val extensions = material.optJSONObject("extensions") ?: return false
        val extensionNames = extensions.keys()
        while (extensionNames.hasNext()) {
            val extensionName = extensionNames.next()
            val extensionValue = extensions.optJSONObject(extensionName) ?: continue
            val extensionKeys = extensionValue.keys()
            while (extensionKeys.hasNext()) {
                val key = extensionKeys.next()
                if (key.endsWith("Texture", ignoreCase = true)) {
                    val textureObject = extensionValue.optJSONObject(key)
                    if (textureObject?.has("index") == true) {
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun collectTextureDiagnostics(root: JSONObject, sourceBaseDir: File?): TextureDiagnostics {
        val images = root.optJSONArray("images")
        val textures = root.optJSONArray("textures")
        val materials = root.optJSONArray("materials")
        val materialCount = materials?.length() ?: 0
        var materialsWithTextureRefs = 0
        if (materials != null) {
            for (index in 0 until materials.length()) {
                val material = materials.optJSONObject(index) ?: continue
                if (materialHasTextureReference(material)) {
                    materialsWithTextureRefs += 1
                }
            }
        }

        val externalImages = listImageFilesForDiagnostics(sourceBaseDir)
        val unsupportedExternalImages = externalImages.filter { file ->
            file.extension.lowercase() in UNSUPPORTED_TEXTURE_EXTENSIONS
        }
        return TextureDiagnostics(
            imageCount = images?.length() ?: 0,
            textureCount = textures?.length() ?: 0,
            materialCount = materialCount,
            materialsWithTextureRefs = materialsWithTextureRefs,
            externalImageFileCount = externalImages.size,
            sampleExternalImageFiles = externalImages.take(6).map { it.name },
            unsupportedExternalImageFileCount = unsupportedExternalImages.size,
            sampleUnsupportedExternalImageFiles = unsupportedExternalImages.take(4).map { it.name }
        )
    }

    private fun buildGltfResourceMimeHints(root: JSONObject): Map<String, String> {
        val images = root.optJSONArray("images") ?: return emptyMap()
        val hints = LinkedHashMap<String, String>()
        for (index in 0 until images.length()) {
            val image = images.optJSONObject(index) ?: continue
            val uri = image.optString("uri").trim()
            val mimeType = canonicalizeImageMimeType(image.optString("mimeType").trim())
            if (uri.isNotEmpty() && mimeType.isNotEmpty()) {
                hints[uri] = mimeType
            }
        }
        return hints
    }

    private fun inferDataUriMimeType(dataUri: String): String {
        val metadata = dataUri.substringBefore(',')
        return canonicalizeImageMimeType(
            metadata
            .removePrefix("data:")
            .substringBefore(';')
            .trim()
        )
    }

    private fun imageFileExtensionForMime(mimeType: String): String {
        return when (canonicalizeImageMimeType(mimeType)) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            "image/ktx2" -> "ktx2"
            else -> "bin"
        }
    }

    private fun inferMimeTypeFromBytes(bytes: ByteArray): String {
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte()
        ) {
            return "image/png"
        }

        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }

        if (bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() &&
            bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() &&
            bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() &&
            bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() &&
            bytes[11] == 0x50.toByte()
        ) {
            return "image/webp"
        }

        if (bytes.size >= 12 &&
            bytes[0] == 0xAB.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x54.toByte() &&
            bytes[3] == 0x58.toByte() &&
            bytes[4] == 0x20.toByte() &&
            bytes[5] == 0x32.toByte() &&
            bytes[6] == 0x30.toByte() &&
            bytes[7] == 0xBB.toByte() &&
            bytes[8] == 0x0D.toByte() &&
            bytes[9] == 0x0A.toByte() &&
            bytes[10] == 0x1A.toByte() &&
            bytes[11] == 0x0A.toByte()
        ) {
            return "image/ktx2"
        }

        return ""
    }

    private fun refreshDiscoveredAnimations() {
        val animator = modelViewer.animator
        val animationCount = animator?.animationCount ?: 0
        if (animator == null || animationCount <= 0) {
            animationNames = emptyList()
            animationNameToIndex = emptyMap()
            animationDurations = emptyMap()
            onAnimationsDiscoveredListener?.invoke(emptyList(), emptyMap())
            return
        }

        val names = ArrayList<String>(animationCount)
        val nameIndexMap = LinkedHashMap<String, Int>(animationCount)
        val durationMap = LinkedHashMap<Int, Float>(animationCount)

        for (index in 0 until animationCount) {
            val rawName = runCatching { animator.getAnimationName(index) }.getOrNull().orEmpty().trim()
            val safeName = if (rawName.isBlank()) "Animation $index" else rawName
            names.add(safeName)
            nameIndexMap[safeName] = index

            val duration = runCatching { animator.getAnimationDuration(index) }.getOrNull()
            if (duration != null && duration > 0f) {
                durationMap[index] = duration
            }
        }

        animationNames = names
        animationNameToIndex = nameIndexMap
        animationDurations = durationMap
        val durationMillisByName =
            durationMap.entries.associate { (index, durationSeconds) ->
                names[index] to (durationSeconds * 1000f).toLong().coerceAtLeast(1L)
            }
        onAnimationsDiscoveredListener?.invoke(names, durationMillisByName)
    }

    private fun applyRequestedAnimationSelection() {
        val animator = modelViewer.animator
        val animationCount = animator?.animationCount ?: 0
        if (animator == null || animationCount <= 0) {
            activeAnimationIndex = -1
            return
        }

        activeAnimationIndex = resolveAnimationIndex(requestedAnimationName, animationCount)
    }

    private fun resolveAnimationIndex(animationName: String?, animationCount: Int): Int {
        if (animationCount <= 0) {
            return -1
        }

        if (animationName.isNullOrBlank()) {
            return -1
        }

        animationNameToIndex[animationName]?.let { index ->
            return index
        }

        val parsedIndex = animationName.toIntOrNull()
        if (parsedIndex != null && parsedIndex in 0 until animationCount) {
            return parsedIndex
        }

        val normalized = animationName.lowercase()
        for (index in animationNames.indices) {
            if (animationNames[index].lowercase() == normalized) {
                return index
            }
        }

        return -1
    }

    private fun restartAnimationClock() {
        animationStartNanos = System.nanoTime()
    }

    private fun setupTransparentSurface() {
        // Keep transparent composition but avoid onTop mode, which is more crash-prone on some drivers.
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)

        val clearOptions = Renderer.ClearOptions().apply {
            clear = true
            discard = true
            clearColor = floatArrayOf(0f, 0f, 0f, 0f)
        }
        modelViewer.renderer.clearOptions = clearOptions
        modelViewer.view.blendMode = View.BlendMode.TRANSLUCENT
        modelViewer.view.setPostProcessingEnabled(false)

        val uiHelper = runCatching { uiHelperField?.get(modelViewer) as? UiHelper }.getOrNull()
        uiHelper?.setOpaque(false)
    }

    private fun setupCameraDefaults() {
        modelViewer.cameraNear = 0.0005f
        modelViewer.cameraFar = 500.0f
    }

    private fun setupSceneLighting() {
        if (sunLightEntity != 0) {
            return
        }

        val engine = modelViewer.engine
        val defaultLight = modelViewer.light
        if (defaultLight != 0) {
            modelViewer.scene.removeEntity(defaultLight)
        }

        sunLightEntity = createDirectionalLight(
            engine = engine,
            colorR = 1.0f,
            colorG = 0.98f,
            colorB = 0.95f,
            intensity = 175_000.0f,
            directionX = 0.0f,
            directionY = -0.22f,
            directionZ = -0.98f
        )
        fillLightEntity = createDirectionalLight(
            engine = engine,
            colorR = 0.94f,
            colorG = 0.96f,
            colorB = 1.0f,
            intensity = 52_000.0f,
            directionX = 0.55f,
            directionY = -0.12f,
            directionZ = -0.82f
        )
        rimLightEntity = createDirectionalLight(
            engine = engine,
            colorR = 0.92f,
            colorG = 0.94f,
            colorB = 1.0f,
            intensity = 16_000.0f,
            directionX = -0.70f,
            directionY = -0.10f,
            directionZ = -0.70f
        )

        if (indirectLight == null) {
            indirectLight = IndirectLight.Builder()
                .irradiance(1, floatArrayOf(0.46f, 0.46f, 0.46f))
                .intensity(16_000.0f)
                .build(engine)
        }
        modelViewer.scene.indirectLight = indirectLight
        modelViewer.scene.skybox = null
        AppLogger.i(
            TAG,
            "Applied fixed front lighting: " +
                "key=(0.00,-0.22,-0.98,175000), " +
                "fill=(0.55,-0.12,-0.82,52000), " +
                "rim=(-0.70,-0.10,-0.70,16000), " +
                "ibl=16000"
        )
    }

    private fun createDirectionalLight(
        engine: com.google.android.filament.Engine,
        colorR: Float,
        colorG: Float,
        colorB: Float,
        intensity: Float,
        directionX: Float,
        directionY: Float,
        directionZ: Float
    ): Int {
        val entity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(colorR, colorG, colorB)
            .intensity(intensity)
            .direction(directionX, directionY, directionZ)
            .castShadows(false)
            .build(engine, entity)
        modelViewer.scene.addEntity(entity)
        return entity
    }

    private fun applyCameraPose() {
        runCatching {
            val pitchRadians = Math.toRadians(cameraPitchDegrees.toDouble())
            val yawRadians = Math.toRadians(cameraYawDegrees.toDouble())
            val rawDistance = BASE_CAMERA_DISTANCE * cameraDistanceScale.toDouble()
            val distance = if (abs(rawDistance) < CAMERA_DISTANCE_EPSILON) {
                CAMERA_DISTANCE_EPSILON
            } else {
                rawDistance
            }

            val targetY = cameraTargetY + cameraTargetHeightOffset.toDouble()

            val horizontalFactor = cos(pitchRadians)
            val eyeX = cameraTargetX + distance * horizontalFactor * sin(yawRadians)
            val eyeY = targetY + distance * sin(pitchRadians)
            val eyeZ = cameraTargetZ + distance * horizontalFactor * cos(yawRadians)

            applyManipulatorPose(
                eyeX = eyeX.toFloat(),
                eyeY = eyeY.toFloat(),
                eyeZ = eyeZ.toFloat(),
                targetX = cameraTargetX.toFloat(),
                targetY = targetY.toFloat(),
                targetZ = cameraTargetZ.toFloat()
            )

            modelViewer.camera.lookAt(
                eyeX,
                eyeY,
                eyeZ,
                cameraTargetX,
                targetY,
                cameraTargetZ,
                0.0,
                1.0,
                0.0
            )

            // Always apply model-space orbit fallback so pitch/yaw works even when manipulator behavior differs by device.
            applyModelOrbitFallback(pitchDegrees = cameraPitchDegrees, yawDegrees = cameraYawDegrees)
        }.onFailure { error ->
            dispatchError("Failed to apply glTF camera: ${error.message ?: "unknown error"}")
        }
    }

    private fun applyManipulatorPose(
        eyeX: Float,
        eyeY: Float,
        eyeZ: Float,
        targetX: Float,
        targetY: Float,
        targetZ: Float
    ): Boolean {
        val field = cameraManipulatorField
        if (field == null) {
            if (!hasLoggedManipulatorUnavailable) {
                AppLogger.w(TAG, "Manipulator field unavailable, using root-transform fallback.")
                hasLoggedManipulatorUnavailable = true
            }
            return false
        }
        return runCatching {
            val viewportWidth = max(width, 1)
            val viewportHeight = max(height, 1)
            val manipulator = Manipulator.Builder()
                .viewport(viewportWidth, viewportHeight)
                .targetPosition(targetX, targetY, targetZ)
                .upVector(0f, 1f, 0f)
                .orbitHomePosition(eyeX, eyeY, eyeZ)
                .panning(false)
                .build(Manipulator.Mode.ORBIT)

            manipulator.jumpToBookmark(manipulator.homeBookmark)
            field.set(modelViewer, manipulator)

            val currentFieldValue = field.get(modelViewer)
            currentFieldValue === manipulator
        }.onFailure { error ->
            if (!hasLoggedManipulatorApplyFailure) {
                AppLogger.w(TAG, "Failed to apply manipulator pose: ${error.message}")
                hasLoggedManipulatorApplyFailure = true
            }
        }.getOrDefault(false)
    }

    private fun captureBaseRootTransform() {
        val asset = modelViewer.asset ?: run {
            baseRootTransform = null
            baseEntityTransforms = emptyMap()
            cameraTargetX = CAMERA_TARGET_X
            cameraTargetY = CAMERA_TARGET_Y
            cameraTargetZ = CAMERA_TARGET_Z
            return
        }

        val transformManager = modelViewer.engine.transformManager

        val rootEntity = asset.root
        val rootInstance = transformManager.getInstance(rootEntity)
        if (rootInstance != 0) {
            val rootTransform = transformManager.getTransform(rootInstance, FloatArray(16))
            val transformIsFinite = rootTransform.all(::isFiniteFloat)
            val tx = rootTransform[12]
            val tz = rootTransform[14]
            if (transformIsFinite && isFiniteFloat(tx) && isFiniteFloat(tz)) {
                baseRootTransform = rootTransform
                cameraTargetX = tx.toDouble()
                cameraTargetY = CAMERA_TARGET_Y
                cameraTargetZ = tz.toDouble()
            } else {
                baseRootTransform = null
                cameraTargetX = CAMERA_TARGET_X
                cameraTargetY = CAMERA_TARGET_Y
                cameraTargetZ = CAMERA_TARGET_Z
                AppLogger.w(TAG, "Skip invalid root transform after glTF load (non-finite values).")
            }
        } else {
            baseRootTransform = null
            cameraTargetX = CAMERA_TARGET_X
            cameraTargetY = CAMERA_TARGET_Y
            cameraTargetZ = CAMERA_TARGET_Z
        }

        val captured = LinkedHashMap<Int, FloatArray>()
        asset.entities.forEach { entity ->
            val instance = transformManager.getInstance(entity)
            if (instance != 0) {
                val transform = transformManager.getTransform(instance, FloatArray(16))
                if (transform.all(::isFiniteFloat)) {
                    captured[entity] = transform
                }
            }
        }
        baseEntityTransforms = captured
    }

    private fun applySafeUnitCubeTransform() {
        val asset = modelViewer.asset ?: return
        val halfExtent = asset.boundingBox.halfExtent
        val maxHalfExtent = halfExtent.maxOrNull() ?: 0f
        val maxExtent = maxHalfExtent * 2f
        if (!isFiniteFloat(maxExtent) || maxExtent <= 1e-6f) {
            AppLogger.w(
                TAG,
                "Skip transformToUnitCube due to invalid bounds: halfExtent=${halfExtent.joinToString()}"
            )
            runCatching { modelViewer.clearRootTransform() }
            return
        }
        runCatching { modelViewer.transformToUnitCube() }
            .onFailure { error ->
                AppLogger.w(TAG, "transformToUnitCube failed: ${error.message}")
                runCatching { modelViewer.clearRootTransform() }
            }
    }

    private fun applyModelOrbitFallback(pitchDegrees: Float, yawDegrees: Float) {
        val asset = modelViewer.asset ?: return
        val transformManager = modelViewer.engine.transformManager

        val rotationY = createRotationYMatrix((-yawDegrees).toDouble())
        val rotationX = createRotationXMatrix((-pitchDegrees).toDouble())
        val rotation = multiplyMat4(rotationY, rotationX)
        val pivotedRotation = buildPivotedRotation(rotation)

        val base = baseRootTransform
        if (base != null) {
            val rootInstance = transformManager.getInstance(asset.root)
            if (rootInstance != 0) {
                val composed = multiplyMat4(base, pivotedRotation)
                transformManager.setTransform(rootInstance, composed)
                return
            }
        }

        if (baseEntityTransforms.isNotEmpty()) {
            baseEntityTransforms.forEach { (entity, transform) ->
                val instance = transformManager.getInstance(entity)
                if (instance != 0) {
                    val composed = multiplyMat4(transform, pivotedRotation)
                    transformManager.setTransform(instance, composed)
                }
            }
        }
    }

    private fun buildPivotedRotation(rotation: FloatArray): FloatArray {
        val translateToPivot = createTranslationMatrix(0f, MODEL_ORBIT_PIVOT_Y, 0f)
        val translateBack = createTranslationMatrix(0f, -MODEL_ORBIT_PIVOT_Y, 0f)
        return multiplyMat4(translateToPivot, multiplyMat4(rotation, translateBack))
    }

    private fun createRotationXMatrix(degrees: Double): FloatArray {
        val radians = Math.toRadians(degrees)
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()

        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, c, s, 0f,
            0f, -s, c, 0f,
            0f, 0f, 0f, 1f
        )
    }

    private fun createRotationYMatrix(degrees: Double): FloatArray {
        val radians = Math.toRadians(degrees)
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()

        return floatArrayOf(
            c, 0f, -s, 0f,
            0f, 1f, 0f, 0f,
            s, 0f, c, 0f,
            0f, 0f, 0f, 1f
        )
    }

    private fun createTranslationMatrix(tx: Float, ty: Float, tz: Float): FloatArray {
        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            tx, ty, tz, 1f
        )
    }

    private fun multiplyMat4(left: FloatArray, right: FloatArray): FloatArray {
        val result = FloatArray(16)
        for (column in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += left[k * 4 + row] * right[column * 4 + k]
                }
                result[column * 4 + row] = sum
            }
        }
        return result
    }

    private fun resolveResourceFile(baseDirectory: File?, relativeUri: String): File {
        val trimmedUri = relativeUri.trim()
        require(trimmedUri.isNotEmpty()) { "Empty glTF resource URI." }

        val normalizedUri = normalizeFileResourceUri(trimmedUri)
        val candidate = if (File(normalizedUri).isAbsolute) {
            File(normalizedUri)
        } else {
            File(baseDirectory, normalizedUri)
        }

        val canonicalFile = candidate.canonicalFile
        require(canonicalFile.exists() && canonicalFile.isFile) {
            "Missing glTF resource: uri=$relativeUri resolved=${canonicalFile.absolutePath}"
        }

        return canonicalFile
    }

    private fun normalizeFileResourceUri(uri: String): String {
        val withoutFragment = uri.substringBefore('#')
        val withoutQuery = withoutFragment.substringBefore('?')
        val normalizedSeparators = withoutQuery
            .replace("\\/", "/")
            .replace('\\', '/')
        val decoded = Uri.decode(normalizedSeparators).trim()
        if (decoded.startsWith("file://", ignoreCase = true)) {
            val parsed = Uri.parse(decoded)
            val parsedPath = parsed.path
            if (!parsedPath.isNullOrBlank()) {
                return parsedPath
            }
            return decoded.removePrefix("file://")
        }
        return decoded
    }

    private fun readResourceToDirectByteBuffer(baseDirectory: File?, relativeUri: String): ByteBuffer {
        val trimmedUri = relativeUri.trim()
        require(trimmedUri.isNotEmpty()) { "Empty glTF resource URI." }

        validateResourceCompatibility(trimmedUri)

        if (trimmedUri.startsWith("data:", ignoreCase = true)) {
            val decoded = decodeDataUriToDirectByteBuffer(trimmedUri)
            AppLogger.i(TAG, "Resolved glTF data URI resource: length=${trimmedUri.length}, bytes=${decoded.remaining()}")
            return decoded
        }

        val resourceFile = resolveResourceFile(baseDirectory, trimmedUri)
        val buffer = readFileToDirectByteBuffer(resourceFile)
        AppLogger.i(
            TAG,
            "Resolved glTF file resource: uri=$trimmedUri path=${resourceFile.absolutePath} bytes=${buffer.remaining()}"
        )
        return buffer
    }

    private fun decodeDataUriToDirectByteBuffer(dataUri: String): ByteBuffer {
        val separatorIndex = dataUri.indexOf(',')
        require(separatorIndex >= 0) { "Malformed glTF data URI: missing payload separator." }

        val metadata = dataUri.substring(0, separatorIndex)
        val payload = dataUri.substring(separatorIndex + 1)
        val bytes = if (metadata.contains(";base64", ignoreCase = true)) {
            Base64.decode(payload, Base64.DEFAULT)
        } else {
            Uri.decode(payload).toByteArray(Charsets.UTF_8)
        }

        require(bytes.isNotEmpty()) { "Malformed glTF data URI: empty payload." }
        return bytes.toDirectByteBuffer()
    }

    private fun readFileToDirectByteBuffer(file: File): ByteBuffer {
        RandomAccessFile(file, "r").use { raf ->
            val channel = raf.channel
            val size = channel.size()
            require(size > 0L) { "Empty glTF resource file: ${file.absolutePath}" }
            require(size <= Int.MAX_VALUE.toLong()) { "glTF resource too large: ${file.absolutePath}" }
            return channel
                .map(FileChannel.MapMode.READ_ONLY, 0, size)
                .order(ByteOrder.nativeOrder())
        }
    }

    private fun ByteArray.toDirectByteBuffer(): ByteBuffer {
        return ByteBuffer.allocateDirect(size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(this@toDirectByteBuffer)
                flip()
            }
    }

    private fun isFiniteFloat(value: Float): Boolean {
        return !(value.isNaN() || value.isInfinite())
    }

    private fun validateResourceCompatibility(resourceUri: String) {
        val mimeHint = canonicalizeImageMimeType(gltfResourceMimeHints[resourceUri].orEmpty())
        if (mimeHint.isNotEmpty() && mimeHint !in SUPPORTED_TEXTURE_MIME_TYPES) {
            throw IllegalArgumentException(
                "Unsupported glTF image mimeType: $mimeHint (uri=$resourceUri). Supported: png/jpeg/webp/ktx2."
            )
        }

        if (resourceUri.startsWith("data:", ignoreCase = true)) {
            val metadata = resourceUri.substringBefore(',')
            val mimeInDataUri = canonicalizeImageMimeType(
                metadata
                .removePrefix("data:")
                .substringBefore(';')
                .trim()
            )
            if (mimeInDataUri.startsWith("image/") && mimeInDataUri !in SUPPORTED_TEXTURE_MIME_TYPES) {
                throw IllegalArgumentException(
                    "Unsupported glTF image data URI mimeType: $mimeInDataUri. Supported: png/jpeg/webp/ktx2."
                )
            }
            return
        }

        val normalized = normalizeFileResourceUri(resourceUri)
        val ext = File(normalized).extension.lowercase()
        if (ext in UNSUPPORTED_TEXTURE_EXTENSIONS) {
            throw IllegalArgumentException(
                "Unsupported glTF image extension: .$ext (uri=$resourceUri). Convert to png/jpeg/webp/ktx2."
            )
        }
    }

    private fun decodeDataUriToByteArray(dataUri: String): ByteArray {
        val separatorIndex = dataUri.indexOf(',')
        require(separatorIndex >= 0) { "Malformed glTF data URI: missing payload separator." }

        val metadata = dataUri.substring(0, separatorIndex)
        val payload = dataUri.substring(separatorIndex + 1)
        return if (metadata.contains(";base64", ignoreCase = true)) {
            Base64.decode(payload, Base64.DEFAULT)
        } else {
            Uri.decode(payload).toByteArray(Charsets.UTF_8)
        }
    }

    private fun canonicalizeImageMimeType(rawMimeType: String): String {
        val normalized = rawMimeType
            .trim()
            .replace("\\/", "/")
            .lowercase()
        return when (normalized) {
            "image/jpg" -> "image/jpeg"
            else -> normalized
        }
    }

    private fun dispatchError(message: String) {
        if (message.isBlank()) {
            return
        }

        AppLogger.e(TAG, message)
        onRenderErrorListener?.invoke(message)
    }
}

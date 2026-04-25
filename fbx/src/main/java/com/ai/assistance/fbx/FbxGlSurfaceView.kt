package com.ai.assistance.fbx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class FbxGlSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val renderer = FbxPreviewRenderer(context.applicationContext)

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setZOrderOnTop(true)
        setBackgroundColor(Color.TRANSPARENT)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setModelPath(path: String) {
        queueEvent {
            renderer.setModelPath(path)
        }
    }

    fun setAnimationState(
        animationName: String?,
        isLooping: Boolean,
        playbackNonce: Long
    ) {
        queueEvent {
            renderer.setAnimationState(animationName, isLooping, playbackNonce)
        }
    }

    fun setCameraPose(
        pitchDegrees: Float,
        yawDegrees: Float,
        distanceScale: Float,
        targetHeightOffset: Float
    ) {
        queueEvent {
            renderer.setCameraPose(
                pitchDegrees = pitchDegrees,
                yawDegrees = yawDegrees,
                distanceScale = distanceScale,
                targetHeightOffset = targetHeightOffset
            )
        }
    }

    fun setOnRenderErrorListener(listener: ((String) -> Unit)?) {
        renderer.setOnErrorListener(listener)
    }

    fun setOnAnimationsDiscoveredListener(listener: ((List<String>, Map<String, Long>) -> Unit)?) {
        renderer.setOnAnimationsDiscoveredListener(listener)
    }

    override fun onDetachedFromWindow() {
        queueEvent {
            renderer.release()
        }
        super.onDetachedFromWindow()
    }
}

private data class FbxPreviewTextureSlot(
    val label: String,
    val path: String?,
    val embedded: Boolean
)

private data class FbxPreviewMaterial(
    val textureIndex: Int,
    val baseColor: FloatArray,
    val alphaBlend: Boolean
)

private data class FbxPreviewSegment(
    val vertexOffset: Int,
    val vertexCount: Int,
    val materialIndex: Int
)

private data class FbxPreviewInfo(
    val centerX: Float,
    val centerY: Float,
    val centerZ: Float,
    val radius: Float,
    val vertexCount: Int,
    val animationNames: List<String>,
    val animationDurationMillisByName: Map<String, Long>,
    val textures: List<FbxPreviewTextureSlot>,
    val materials: List<FbxPreviewMaterial>,
    val segments: List<FbxPreviewSegment>
) {
    companion object {
        fun fromJson(rawJson: String): FbxPreviewInfo {
            val root = JSONObject(rawJson)
            val centerArray = root.optJSONArray("center")
            val animationNames = root.optJSONArray("animationNames").toStringList()
            val durationArray = root.optJSONArray("animationDurationsMillis")
            val texturesArray = root.optJSONArray("textures")
            val materialsArray = root.optJSONArray("materials")
            val segmentsArray = root.optJSONArray("segments")

            val durations =
                buildMap {
                    animationNames.forEachIndexed { index, animationName ->
                        val durationMillis = durationArray?.optLong(index, 0L)?.coerceAtLeast(0L) ?: 0L
                        if (durationMillis > 0L) {
                            put(animationName, durationMillis)
                        }
                    }
                }

            val textures =
                buildList(texturesArray?.length() ?: 0) {
                    if (texturesArray != null) {
                        for (index in 0 until texturesArray.length()) {
                            val item = texturesArray.optJSONObject(index) ?: continue
                            add(
                                FbxPreviewTextureSlot(
                                    label = item.optString("label").ifBlank { "Texture ${index + 1}" },
                                    path = item.optString("path").trim().ifBlank { null },
                                    embedded = item.optBoolean("embedded", false)
                                )
                            )
                        }
                    }
                }

            val materials =
                buildList(materialsArray?.length() ?: 0) {
                    if (materialsArray != null) {
                        for (index in 0 until materialsArray.length()) {
                            val item = materialsArray.optJSONObject(index) ?: continue
                            val color = item.optJSONArray("baseColor")
                            add(
                                FbxPreviewMaterial(
                                    textureIndex = item.optInt("textureIndex", -1),
                                    baseColor =
                                        floatArrayOf(
                                            color?.optDouble(0, 1.0)?.toFloat() ?: 1.0f,
                                            color?.optDouble(1, 1.0)?.toFloat() ?: 1.0f,
                                            color?.optDouble(2, 1.0)?.toFloat() ?: 1.0f,
                                            color?.optDouble(3, 1.0)?.toFloat() ?: 1.0f
                                        ),
                                    alphaBlend = item.optBoolean("alphaBlend", false)
                                )
                            )
                        }
                    }
                }

            val segments =
                buildList(segmentsArray?.length() ?: 0) {
                    if (segmentsArray != null) {
                        for (index in 0 until segmentsArray.length()) {
                            val item = segmentsArray.optJSONObject(index) ?: continue
                            add(
                                FbxPreviewSegment(
                                    vertexOffset = item.optInt("vertexOffset", 0),
                                    vertexCount = item.optInt("vertexCount", 0),
                                    materialIndex = item.optInt("materialIndex", -1)
                                )
                            )
                        }
                    }
                }

            return FbxPreviewInfo(
                centerX = centerArray?.optDouble(0, 0.0)?.toFloat() ?: 0.0f,
                centerY = centerArray?.optDouble(1, 0.0)?.toFloat() ?: 0.0f,
                centerZ = centerArray?.optDouble(2, 0.0)?.toFloat() ?: 0.0f,
                radius = root.optDouble("radius", 1.0).toFloat().coerceAtLeast(0.1f),
                vertexCount = root.optInt("vertexCount", 0),
                animationNames = animationNames,
                animationDurationMillisByName = durations,
                textures = textures,
                materials = materials,
                segments = segments
            )
        }
    }
}

private data class LoadedTextureSlot(
    val textureId: Int,
    val hasAlpha: Boolean
)

private class FbxPreviewRenderer(
    private val appContext: Context
) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "FbxGlSurfaceView"
        private const val STRIDE_FLOATS = 8

        private object Handle {
            const val PROGRAM = 0
            const val POSITION = 1
            const val NORMAL = 2
            const val TEX_COORD = 3
            const val VIEW_PROJECTION = 4
            const val BASE_COLOR = 5
            const val BASE_TEXTURE = 6
            const val USE_BASE_TEXTURE = 7
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var requestedModelPath: String? = null
    private var currentModelPath: String? = null
    private var requestedAnimationName: String? = null
    private var requestedLooping: Boolean = false
    private var requestedPlaybackNonce: Long = -1L
    private var animationStartNanos: Long = System.nanoTime()

    private var cameraPitchDegrees: Float = 8.0f
    private var cameraYawDegrees: Float = 0.0f
    private var cameraDistanceScale: Float = 1.0f
    private var cameraTargetHeightOffset: Float = 0.0f

    private var aspectRatio: Float = 1.0f
    private var sessionHandle: Long = 0L
    private var previewInfo: FbxPreviewInfo? = null
    private var vertexBuffer: FloatBuffer? = null
    private var currentVertexCount: Int = 0
    private var programHandles: IntArray? = null
    private var textureSlots: List<LoadedTextureSlot> = emptyList()
    private var lastRenderError: String? = null

    @Volatile
    private var onErrorListener: ((String) -> Unit)? = null

    @Volatile
    private var onAnimationsDiscoveredListener: ((List<String>, Map<String, Long>) -> Unit)? = null

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val viewProjectionMatrix = FloatArray(16)

    private val vertexShader = """
        uniform mat4 uViewProjectionMatrix;
        attribute vec3 aPosition;
        attribute vec3 aNormal;
        attribute vec2 aTexCoord;
        varying vec3 vNormal;
        varying vec2 vTexCoord;
        void main() {
            vNormal = normalize(aNormal);
            vTexCoord = aTexCoord;
            gl_Position = uViewProjectionMatrix * vec4(aPosition, 1.0);
        }
    """

    private val fragmentShader = """
        precision mediump float;
        varying vec3 vNormal;
        varying vec2 vTexCoord;
        uniform vec4 uBaseColor;
        uniform sampler2D uBaseTexture;
        uniform float uUseBaseTexture;
        void main() {
            vec3 lightDir = normalize(vec3(0.25, 0.80, 1.0));
            float diffuse = max(dot(normalize(vNormal), lightDir), 0.0);
            float lighting = 0.38 + diffuse * 0.62;
            vec4 textureColor = vec4(1.0);
            if (uUseBaseTexture > 0.5) {
                textureColor = texture2D(uBaseTexture, vec2(vTexCoord.x, 1.0 - vTexCoord.y));
            }
            vec4 color = vec4(uBaseColor.rgb * textureColor.rgb * lighting, uBaseColor.a * textureColor.a);
            gl_FragColor = vec4(clamp(color.rgb, 0.0, 1.0), clamp(color.a, 0.0, 1.0));
        }
    """

    fun setOnErrorListener(listener: ((String) -> Unit)?) {
        onErrorListener = listener
    }

    fun setOnAnimationsDiscoveredListener(listener: ((List<String>, Map<String, Long>) -> Unit)?) {
        onAnimationsDiscoveredListener = listener
    }

    fun setModelPath(path: String) {
        val normalizedPath = path.trim()
        if (normalizedPath.isEmpty() || normalizedPath == requestedModelPath) {
            return
        }

        requestedModelPath = normalizedPath
        if (programHandles != null) {
            loadPreviewAssets(normalizedPath)
        }
    }

    fun setAnimationState(animationName: String?, isLooping: Boolean, playbackNonce: Long) {
        val normalizedName = animationName?.trim()?.takeIf { it.isNotEmpty() }
        val unchanged =
            normalizedName == requestedAnimationName &&
                isLooping == requestedLooping &&
                playbackNonce == requestedPlaybackNonce
        if (unchanged) {
            return
        }

        requestedAnimationName = normalizedName
        requestedLooping = isLooping
        requestedPlaybackNonce = playbackNonce
        animationStartNanos = System.nanoTime()
    }

    fun setCameraPose(
        pitchDegrees: Float,
        yawDegrees: Float,
        distanceScale: Float,
        targetHeightOffset: Float
    ) {
        cameraPitchDegrees = pitchDegrees.coerceIn(-89f, 89f)
        cameraYawDegrees = yawDegrees.coerceIn(-180f, 180f)
        cameraDistanceScale = distanceScale.coerceIn(0.02f, 12.0f)
        cameraTargetHeightOffset = targetHeightOffset.coerceIn(-2.0f, 2.0f)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        clearProgram()
        clearTextures()
        programHandles = createProgramHandles()
        if (programHandles == null) {
            dispatchError("Failed to create FBX preview shader program.")
            return
        }

        val existingInfo = previewInfo
        if (existingInfo != null && sessionHandle != 0L) {
            textureSlots = loadTextureSlots(sessionHandle, existingInfo)
        } else {
            requestedModelPath?.takeIf { it.isNotBlank() }?.let { loadPreviewAssets(it) }
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspectRatio = width.toFloat() / max(height, 1).toFloat()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val info = previewInfo ?: return
        val handles = programHandles ?: return
        if (sessionHandle == 0L || info.vertexCount <= 0 || info.segments.isEmpty()) {
            return
        }

        val animationName = requestedAnimationName?.takeIf { info.animationNames.contains(it) }
        val durationSeconds =
            animationName
                ?.let { name -> info.animationDurationMillisByName[name] }
                ?.takeIf { it > 0L }
                ?.let { it.toDouble() / 1000.0 }
        val elapsedSeconds = max((System.nanoTime() - animationStartNanos).toDouble() / 1_000_000_000.0, 0.0)
        val sampleTimeSeconds =
            when {
                durationSeconds == null -> elapsedSeconds
                requestedLooping -> elapsedSeconds % durationSeconds
                else -> min(elapsedSeconds, durationSeconds)
            }

        if (!(animationName == null && info.animationNames.isEmpty() && vertexBuffer != null)) {
            val frameVertices = FbxNative.nativeBuildPreviewFrame(sessionHandle, animationName, sampleTimeSeconds)
            if (frameVertices == null || !updateVertexBuffer(frameVertices)) {
                dispatchOnce(FbxInspector.getLastError().ifBlank { "Failed to build FBX preview frame." })
                return
            }
        }

        updateCamera(info)
        drawScene(info, handles)
        lastRenderError = null
    }

    fun release() {
        releaseSession()
        clearTextures()
        clearProgram()
    }

    private fun loadPreviewAssets(modelPath: String) {
        if (!FbxInspector.isAvailable()) {
            dispatchError(FbxInspector.unavailableReason().ifBlank { "FBX backend is unavailable." })
            return
        }

        releaseSession()

        val handle = FbxNative.nativeCreatePreviewSession(modelPath)
        if (handle == 0L) {
            dispatchError(FbxInspector.getLastError().ifBlank { "Failed to create FBX preview session." })
            return
        }

        val previewJson = FbxNative.nativeReadPreviewInfo(handle)
        val info = previewJson?.let { runCatching { FbxPreviewInfo.fromJson(it) }.getOrNull() }
        if (info == null || info.vertexCount <= 0) {
            FbxNative.nativeDestroyPreviewSession(handle)
            dispatchError(FbxInspector.getLastError().ifBlank { "Failed to read FBX preview metadata." })
            return
        }

        val initialFrame = FbxNative.nativeBuildPreviewFrame(handle, null, 0.0)
        if (initialFrame == null || !updateVertexBuffer(initialFrame)) {
            FbxNative.nativeDestroyPreviewSession(handle)
            dispatchError(FbxInspector.getLastError().ifBlank { "Failed to build initial FBX preview frame." })
            return
        }

        previewInfo = info
        sessionHandle = handle
        currentModelPath = modelPath
        requestedModelPath = modelPath
        textureSlots = loadTextureSlots(handle, info)
        mainHandler.post {
            onAnimationsDiscoveredListener?.invoke(info.animationNames, info.animationDurationMillisByName)
        }
    }

    private fun loadTextureSlots(handle: Long, info: FbxPreviewInfo): List<LoadedTextureSlot> {
        clearTextures()
        return info.textures.mapIndexed { index, texture ->
            if (texture.embedded) {
                val bytes = FbxNative.nativeReadEmbeddedTextureBytes(handle, index)
                bytes?.let(::loadTextureFromBytes) ?: LoadedTextureSlot(0, false)
            } else {
                texture.path?.let(::loadTextureFromFile) ?: LoadedTextureSlot(0, false)
            }
        }
    }

    private fun loadTextureFromBytes(bytes: ByteArray): LoadedTextureSlot {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return LoadedTextureSlot(0, false)
        return uploadBitmapTexture(bitmap)
    }

    private fun loadTextureFromFile(path: String): LoadedTextureSlot {
        val textureFile = File(path)
        if (!textureFile.exists() || !textureFile.isFile) {
            Log.w(TAG, "FBX preview texture file missing: $path")
            return LoadedTextureSlot(0, false)
        }
        val bitmap = BitmapFactory.decodeFile(path) ?: return LoadedTextureSlot(0, false)
        return uploadBitmapTexture(bitmap)
    }

    private fun uploadBitmapTexture(bitmap: Bitmap): LoadedTextureSlot {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]
        if (textureId == 0) {
            bitmap.recycle()
            return LoadedTextureSlot(0, false)
        }

        val hasAlpha = bitmap.hasAlpha()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        bitmap.recycle()
        return LoadedTextureSlot(textureId, hasAlpha)
    }

    private fun updateVertexBuffer(frameVertices: FloatArray): Boolean {
        if (frameVertices.isEmpty() || frameVertices.size % STRIDE_FLOATS != 0) {
            dispatchOnce("Invalid FBX preview vertex data returned by native layer.")
            return false
        }

        val nextVertexCount = frameVertices.size / STRIDE_FLOATS
        val existingBuffer = vertexBuffer
        if (existingBuffer == null || currentVertexCount != nextVertexCount) {
            vertexBuffer =
                ByteBuffer.allocateDirect(frameVertices.size * Float.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply {
                        put(frameVertices)
                        position(0)
                    }
            currentVertexCount = nextVertexCount
            return true
        }

        existingBuffer.position(0)
        existingBuffer.put(frameVertices)
        existingBuffer.position(0)
        currentVertexCount = nextVertexCount
        return true
    }

    private fun updateCamera(info: FbxPreviewInfo) {
        Matrix.perspectiveM(
            projectionMatrix,
            0,
            45f,
            aspectRatio.coerceAtLeast(0.1f),
            max(info.radius / 200f, 0.01f),
            max(info.radius * 40f, 50f)
        )

        val pitchRadians = Math.toRadians(cameraPitchDegrees.toDouble())
        val yawRadians = Math.toRadians(cameraYawDegrees.toDouble())
        val distance = max(info.radius * 3.0f, 1.25f) * cameraDistanceScale
        val targetX = info.centerX
        val targetY = info.centerY + cameraTargetHeightOffset
        val targetZ = info.centerZ
        val eyeX = targetX + (distance * cos(pitchRadians) * sin(yawRadians)).toFloat()
        val eyeY = targetY + (distance * sin(pitchRadians)).toFloat()
        val eyeZ = targetZ + (distance * cos(pitchRadians) * cos(yawRadians)).toFloat()

        Matrix.setLookAtM(
            viewMatrix,
            0,
            eyeX,
            eyeY,
            eyeZ,
            targetX,
            targetY,
            targetZ,
            0f,
            1f,
            0f
        )
        Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
    }

    private fun drawScene(info: FbxPreviewInfo, handles: IntArray) {
        val buffer = vertexBuffer ?: return
        if (currentVertexCount <= 0) {
            return
        }

        GLES20.glUseProgram(handles[Handle.PROGRAM])
        GLES20.glUniformMatrix4fv(handles[Handle.VIEW_PROJECTION], 1, false, viewProjectionMatrix, 0)

        buffer.position(0)
        GLES20.glEnableVertexAttribArray(handles[Handle.POSITION])
        GLES20.glVertexAttribPointer(handles[Handle.POSITION], 3, GLES20.GL_FLOAT, false, STRIDE_FLOATS * Float.SIZE_BYTES, buffer)

        buffer.position(3)
        GLES20.glEnableVertexAttribArray(handles[Handle.NORMAL])
        GLES20.glVertexAttribPointer(handles[Handle.NORMAL], 3, GLES20.GL_FLOAT, false, STRIDE_FLOATS * Float.SIZE_BYTES, buffer)

        buffer.position(6)
        GLES20.glEnableVertexAttribArray(handles[Handle.TEX_COORD])
        GLES20.glVertexAttribPointer(handles[Handle.TEX_COORD], 2, GLES20.GL_FLOAT, false, STRIDE_FLOATS * Float.SIZE_BYTES, buffer)

        val opaqueSegments = info.segments.filterNot(::isSegmentTransparent)
        val transparentSegments = info.segments.filter(::isSegmentTransparent)

        GLES20.glDepthMask(true)
        opaqueSegments.forEach { segment -> drawSegment(segment, info, handles) }
        GLES20.glDepthMask(false)
        transparentSegments.forEach { segment -> drawSegment(segment, info, handles) }
        GLES20.glDepthMask(true)

        GLES20.glDisableVertexAttribArray(handles[Handle.POSITION])
        GLES20.glDisableVertexAttribArray(handles[Handle.NORMAL])
        GLES20.glDisableVertexAttribArray(handles[Handle.TEX_COORD])
    }

    private fun drawSegment(
        segment: FbxPreviewSegment,
        info: FbxPreviewInfo,
        handles: IntArray
    ) {
        if (segment.vertexCount <= 0) {
            return
        }

        val material =
            info.materials.getOrNull(segment.materialIndex)
                ?: FbxPreviewMaterial(textureIndex = -1, baseColor = floatArrayOf(1f, 1f, 1f, 1f), alphaBlend = false)
        val texture = material.textureIndex.takeIf { it >= 0 }?.let { index -> textureSlots.getOrNull(index) }

        GLES20.glUniform4fv(handles[Handle.BASE_COLOR], 1, material.baseColor, 0)
        if (texture != null && texture.textureId != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.textureId)
            GLES20.glUniform1i(handles[Handle.BASE_TEXTURE], 0)
            GLES20.glUniform1f(handles[Handle.USE_BASE_TEXTURE], 1f)
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            GLES20.glUniform1f(handles[Handle.USE_BASE_TEXTURE], 0f)
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, segment.vertexOffset, segment.vertexCount)
    }

    private fun isSegmentTransparent(segment: FbxPreviewSegment): Boolean {
        val info = previewInfo ?: return false
        val material = info.materials.getOrNull(segment.materialIndex) ?: return false
        val textureHasAlpha =
            material.textureIndex.takeIf { it >= 0 }
                ?.let { index -> textureSlots.getOrNull(index)?.hasAlpha }
                ?: false
        return material.alphaBlend || material.baseColor[3] < 0.999f || textureHasAlpha
    }

    private fun createProgramHandles(): IntArray? {
        val program = createProgram(vertexShader, fragmentShader)
        if (program == 0) {
            return null
        }

        val handles = IntArray(8)
        handles[Handle.PROGRAM] = program
        handles[Handle.POSITION] = GLES20.glGetAttribLocation(program, "aPosition")
        handles[Handle.NORMAL] = GLES20.glGetAttribLocation(program, "aNormal")
        handles[Handle.TEX_COORD] = GLES20.glGetAttribLocation(program, "aTexCoord")
        handles[Handle.VIEW_PROJECTION] = GLES20.glGetUniformLocation(program, "uViewProjectionMatrix")
        handles[Handle.BASE_COLOR] = GLES20.glGetUniformLocation(program, "uBaseColor")
        handles[Handle.BASE_TEXTURE] = GLES20.glGetUniformLocation(program, "uBaseTexture")
        handles[Handle.USE_BASE_TEXTURE] = GLES20.glGetUniformLocation(program, "uUseBaseTexture")
        return if (handles.all { it >= 0 || it == program }) {
            handles
        } else {
            GLES20.glDeleteProgram(program)
            null
        }
    }

    private fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShaderId = loadShader(GLES20.GL_VERTEX_SHADER, vertexCode)
        if (vertexShaderId == 0) {
            return 0
        }

        val fragmentShaderId = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)
        if (fragmentShaderId == 0) {
            GLES20.glDeleteShader(vertexShaderId)
            return 0
        }

        val program = GLES20.glCreateProgram()
        if (program == 0) {
            GLES20.glDeleteShader(vertexShaderId)
            GLES20.glDeleteShader(fragmentShaderId)
            return 0
        }

        GLES20.glAttachShader(program, vertexShaderId)
        GLES20.glAttachShader(program, fragmentShaderId)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Failed to link FBX shader program: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteShader(vertexShaderId)
            GLES20.glDeleteShader(fragmentShaderId)
            return 0
        }

        GLES20.glDeleteShader(vertexShaderId)
        GLES20.glDeleteShader(fragmentShaderId)
        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            return 0
        }

        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Failed to compile FBX shader: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }

        return shader
    }

    private fun releaseSession() {
        if (sessionHandle != 0L) {
            FbxNative.nativeDestroyPreviewSession(sessionHandle)
        }
        sessionHandle = 0L
        previewInfo = null
        currentModelPath = null
        vertexBuffer = null
        currentVertexCount = 0
    }

    private fun clearTextures() {
        val existingTextureIds = textureSlots.mapNotNull { slot -> slot.textureId.takeIf { it != 0 } }
        if (existingTextureIds.isNotEmpty()) {
            GLES20.glDeleteTextures(existingTextureIds.size, existingTextureIds.toIntArray(), 0)
        }
        textureSlots = emptyList()
    }

    private fun clearProgram() {
        programHandles?.getOrNull(Handle.PROGRAM)?.takeIf { it != 0 }?.let { program ->
            GLES20.glDeleteProgram(program)
        }
        programHandles = null
    }

    private fun dispatchOnce(message: String) {
        if (message.isBlank() || message == lastRenderError) {
            return
        }
        dispatchError(message)
        lastRenderError = message
    }

    private fun dispatchError(message: String) {
        if (message.isBlank()) {
            return
        }
        Log.e(TAG, message)
        mainHandler.post {
            onErrorListener?.invoke(message)
        }
    }
}

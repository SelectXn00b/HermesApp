package com.ai.assistance.mmd

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MmdGlSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    companion object {
        private const val TAG = "MmdGlSurfaceView"
        private val NEXT_INSTANCE_ID = AtomicInteger(1)
    }

    private val instanceId = NEXT_INSTANCE_ID.getAndIncrement()
    private var destroyLogged = false
    private val renderer = NativeMmdRenderer(context.applicationContext, instanceId)

    init {
        Log.i(TAG, "create instance#$instanceId")
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 8)
        setZOrderOnTop(true)
        setBackgroundColor(Color.TRANSPARENT)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        requestHighRefreshRateIfSupported()
    }

    fun setModelPath(path: String) {
        queueEvent {
            renderer.setModelPath(path)
        }
    }

    fun setAnimationState(animationName: String?, isLooping: Boolean) {
        queueEvent {
            renderer.setAnimationState(animationName, isLooping)
        }
    }

    fun setModelRotation(rotationX: Float, rotationY: Float, rotationZ: Float) {
        queueEvent {
            renderer.setModelRotation(rotationX, rotationY, rotationZ)
        }
    }

    fun setCameraDistanceScale(scale: Float) {
        queueEvent {
            renderer.setCameraDistanceScale(scale)
        }
    }

    fun setCameraTargetHeight(height: Float) {
        queueEvent {
            renderer.setCameraTargetHeight(height)
        }
    }

    fun setOnRenderErrorListener(listener: ((String) -> Unit)?) {
        renderer.setOnErrorListener(listener)
    }

    override fun onResume() {
        super.onResume()
        queueEvent {
            renderer.resumeRenderer()
        }
        requestHighRefreshRateIfSupported()
    }

    override fun onPause() {
        queueEvent {
            renderer.pauseRenderer()
        }
        super.onPause()
    }

    override fun onDetachedFromWindow() {
        if (!destroyLogged) {
            destroyLogged = true
            Log.i(TAG, "destroy instance#$instanceId")
        }
        queueEvent {
            renderer.releaseRenderer()
        }
        super.onDetachedFromWindow()
    }

    private fun requestHighRefreshRateIfSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }

        val surface = holder.surface ?: return
        if (!surface.isValid) {
            return
        }

        try {
            val setFrameRateMethod =
                surface.javaClass.getMethod(
                    "setFrameRate",
                    Float::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!
                )
            setFrameRateMethod.invoke(surface, 120f, 0)
        } catch (_: Throwable) {
        }
    }
}

private class NativeMmdRenderer(
    private val appContext: Context,
    private val instanceId: Int
) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "MmdGlRenderer"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var onErrorListener: ((String) -> Unit)? = null

    private var rendererHandle: Long = MmdNative.nativeCreateRenderer()
    private var requestedModelPath: String? = null
    private var requestedAnimationName: String? = null
    private var requestedAnimationLooping: Boolean = false
    private var rotationX: Float = 0f
    private var rotationY: Float = 0f
    private var rotationZ: Float = 0f
    private var cameraDistanceScale: Float = 1f
    private var cameraTargetHeight: Float = 0f
    private var lastRenderError: String? = null

    fun setOnErrorListener(listener: ((String) -> Unit)?) {
        onErrorListener = listener
    }

    fun setModelPath(path: String) {
        val normalizedPath = path.trim().takeIf { it.isNotEmpty() } ?: return
        if (requestedModelPath == normalizedPath) {
            return
        }
        requestedModelPath = normalizedPath
        Log.i(TAG, "instance#$instanceId apply model path=$normalizedPath")
        if (rendererHandle != 0L) {
            MmdNative.nativeSetModelPath(rendererHandle, normalizedPath)
        }
    }

    fun setAnimationState(animationName: String?, isLooping: Boolean) {
        val normalizedAnimationName = animationName?.trim()?.takeIf { it.isNotEmpty() }
        if (requestedAnimationName == normalizedAnimationName &&
            requestedAnimationLooping == isLooping
        ) {
            return
        }
        requestedAnimationName = normalizedAnimationName
        requestedAnimationLooping = isLooping
        Log.i(
            TAG,
            "instance#$instanceId apply animation=${requestedAnimationName ?: "<none>"} looping=$requestedAnimationLooping"
        )
        if (rendererHandle != 0L) {
            MmdNative.nativeSetAnimationState(
                rendererHandle,
                requestedAnimationName,
                requestedAnimationLooping
            )
        }
    }

    fun setModelRotation(rotationX: Float, rotationY: Float, rotationZ: Float) {
        this.rotationX = rotationX
        this.rotationY = rotationY
        this.rotationZ = rotationZ
        if (rendererHandle != 0L) {
            MmdNative.nativeSetModelRotation(rendererHandle, rotationX, rotationY, rotationZ)
        }
    }

    fun setCameraDistanceScale(scale: Float) {
        cameraDistanceScale = scale.coerceIn(0.02f, 12.0f)
        if (rendererHandle != 0L) {
            MmdNative.nativeSetCameraDistanceScale(rendererHandle, cameraDistanceScale)
        }
    }

    fun setCameraTargetHeight(height: Float) {
        cameraTargetHeight = height.coerceIn(-2.0f, 2.0f)
        if (rendererHandle != 0L) {
            MmdNative.nativeSetCameraTargetHeight(rendererHandle, cameraTargetHeight)
        }
    }

    fun pauseRenderer() {
        if (rendererHandle != 0L) {
            MmdNative.nativePause(rendererHandle)
        }
    }

    fun resumeRenderer() {
        if (rendererHandle != 0L) {
            MmdNative.nativeResume(rendererHandle)
        }
    }

    fun releaseRenderer() {
        if (rendererHandle == 0L) {
            return
        }
        MmdNative.nativeDestroyRenderer(rendererHandle)
        rendererHandle = 0L
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        if (rendererHandle == 0L) {
            rendererHandle = MmdNative.nativeCreateRenderer()
        }
        if (rendererHandle == 0L) {
            dispatchError("Failed to create native MMD renderer.")
            return
        }

        Log.i(TAG, "instance#$instanceId surface created handle=$rendererHandle")
        MmdNative.nativeOnSurfaceCreated(rendererHandle, appContext.assets)
        syncRequestedState()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        if (rendererHandle == 0L) {
            return
        }
        Log.i(TAG, "instance#$instanceId surface changed ${width}x$height")
        MmdNative.nativeOnSurfaceChanged(rendererHandle, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (rendererHandle == 0L) {
            return
        }

        val renderSuccess = MmdNative.nativeRender(rendererHandle)
        if (!renderSuccess) {
            val latestError = MmdNative.nativeGetRendererLastError(rendererHandle).ifBlank {
                "Failed to render MMD frame."
            }
            if (latestError != lastRenderError) {
                dispatchError(latestError)
                lastRenderError = latestError
            }
        } else {
            lastRenderError = null
        }
    }

    private fun syncRequestedState() {
        if (rendererHandle == 0L) {
            return
        }

        Log.i(
            TAG,
            "instance#$instanceId sync state model=${requestedModelPath ?: "<none>"} animation=${requestedAnimationName ?: "<none>"}"
        )
        MmdNative.nativeSetModelRotation(rendererHandle, rotationX, rotationY, rotationZ)
        MmdNative.nativeSetCameraDistanceScale(rendererHandle, cameraDistanceScale)
        MmdNative.nativeSetCameraTargetHeight(rendererHandle, cameraTargetHeight)
        MmdNative.nativeSetModelPath(rendererHandle, requestedModelPath)
        MmdNative.nativeSetAnimationState(
            rendererHandle,
            requestedAnimationName,
            requestedAnimationLooping
        )
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

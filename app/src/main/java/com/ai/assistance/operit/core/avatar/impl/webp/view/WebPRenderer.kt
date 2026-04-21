package com.ai.assistance.operit.core.avatar.impl.webp.view

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.ai.assistance.operit.util.AppLogger
import android.widget.ImageView
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.impl.webp.control.WebPAvatarController
import com.ai.assistance.operit.core.avatar.impl.webp.model.WebPAvatarModel
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * A Composable function responsible for rendering a WebP avatar.
 * It uses Android's ImageDecoder to display animated WebP files from assets.
 *
 * @param modifier The modifier to be applied to the view.
 * @param model The frame sequence avatar model containing the animation path.
 * @param controller The avatar controller that manages the avatar's state. It must be a
 *   [WebPAvatarController] for this renderer to function.
 * @param onError A callback for handling rendering errors.
 */
@Composable
fun WebPRenderer(
    modifier: Modifier,
    model: WebPAvatarModel,
    controller: AvatarController,
    onError: (String) -> Unit
) {
    // This renderer requires a specific controller implementation
    val webpController = controller as? WebPAvatarController
        ?: throw IllegalArgumentException("WebPRenderer requires a WebPAvatarController")

    val context = LocalContext.current

    // Listen to controller state changes to get the current animation path
    val controllerState by webpController.state.collectAsState()
    
    // Listen to transform properties
    val scale by webpController.scale.collectAsState()
    val translateX by webpController.translateX.collectAsState()
    val translateY by webpController.translateY.collectAsState()
    
    val animationName = controllerState.currentAnimation
    val animationPath = model.animationPathFor(animationName)
    val animationKey =
        animationName
            ?: model.animationFileForEmotion(controllerState.emotion)
            ?: model.availableFiles.firstOrNull()

    val drawableState = remember(animationPath) {
        mutableStateOf<android.graphics.drawable.Drawable?>(null)
    }

    // Decode animation when path changes
    DisposableEffect(animationPath, controllerState.isLooping, controllerState.playbackNonce) {
        if (animationPath.isBlank()) {
            drawableState.value = null
            return@DisposableEffect onDispose { }
        }

        val assets = context.assets
        AppLogger.d("WebPRenderer", "Decode start: $animationPath")
        var animationCallback: Animatable2.AnimationCallback? = null
        
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                // Always decode from bytes to support compressed assets in APK
                val inputStream = if (File(animationPath).isAbsolute) {
                    FileInputStream(animationPath)
                } else {
                    assets.open(animationPath)
                }
                val bytes = inputStream.use { it.readBytes() }
                val src = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                val drawable = ImageDecoder.decodeDrawable(src)
                drawableState.value = drawable
                
                if (drawable is AnimatedImageDrawable) {
                    drawable.repeatCount = if (controllerState.isLooping) {
                        AnimatedImageDrawable.REPEAT_INFINITE
                    } else {
                        0
                    }
                    if (!controllerState.isLooping) {
                        animationCallback =
                            object : Animatable2.AnimationCallback() {
                                override fun onAnimationEnd(drawable: Drawable?) {
                                    animationKey?.let(webpController::onAnimationPlaybackCompleted)
                                }
                            }
                        drawable.registerAnimationCallback(animationCallback)
                    }
                    drawable.start()
                    AppLogger.d("WebPRenderer", "Animated start: $animationPath")
                } else {
                    AppLogger.d("WebPRenderer", "Decoded non-animated drawable for: $animationPath")
                }
            } else {
                // API < 28: show first frame as static
                val inputStream = if (File(animationPath).isAbsolute) {
                    FileInputStream(animationPath)
                } else {
                    assets.open(animationPath)
                }
                val bmp = inputStream.use { BitmapFactory.decodeStream(it) }
                drawableState.value = BitmapDrawable(context.resources, bmp)
                AppLogger.d("WebPRenderer", "Static fallback (API<28): $animationPath")
            }
        } catch (e: Exception) {
            AppLogger.e("WebPRenderer", "Decode error for $animationPath: ${e.message}", e)
            drawableState.value = null
            onError("Failed to load animation: ${e.message}")
        }

        onDispose {
            try {
                val d = drawableState.value
                if (Build.VERSION.SDK_INT >= 28 && d is AnimatedImageDrawable) {
                    animationCallback?.let { d.unregisterAnimationCallback(it) }
                    d.stop()
                }
            } catch (_: Exception) {}
        }
    }

    val drawable = drawableState.value
    if (drawable != null) {
        AndroidView(
            modifier = modifier
                .scale(scale)
                .offset(x = translateX.dp, y = translateY.dp),
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setImageDrawable(drawable)
                }
            },
            update = { imageView ->
                imageView.setImageDrawable(drawable)
            }
        )
    }
} 

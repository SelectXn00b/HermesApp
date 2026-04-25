package com.ai.assistance.operit.core.avatar.impl.mp4.view

import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.impl.mp4.control.Mp4AvatarController
import com.ai.assistance.operit.core.avatar.impl.mp4.model.Mp4AvatarModel
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import java.io.File

@Composable
fun Mp4Renderer(
    modifier: Modifier,
    model: Mp4AvatarModel,
    controller: AvatarController,
    onError: (String) -> Unit
) {
    val mp4Controller = controller as? Mp4AvatarController
        ?: throw IllegalArgumentException("Mp4Renderer requires a Mp4AvatarController")

    val context = LocalContext.current
    val latestOnError by rememberUpdatedState(onError)

    val controllerState by mp4Controller.state.collectAsState()
    val scale by mp4Controller.scale.collectAsState()
    val translateX by mp4Controller.translateX.collectAsState()
    val translateY by mp4Controller.translateY.collectAsState()

    val animationName = controllerState.currentAnimation
    val animationPath = model.animationPathFor(animationName)
    val animationKey =
        animationName
            ?: model.animationFileForEmotion(controllerState.emotion)
            ?: model.availableFiles.firstOrNull()

    val exoPlayer = remember(model.id) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(animationPath, controllerState.isLooping, controllerState.playbackNonce) {
        if (animationPath.isBlank()) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            return@LaunchedEffect
        }

        exoPlayer.setMediaItem(MediaItem.fromUri(resolveMediaUri(animationPath)))
        exoPlayer.repeatMode = if (controllerState.isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        exoPlayer.prepare()
        exoPlayer.seekTo(0L)
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer, animationKey, controllerState.isLooping) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED && !controllerState.isLooping) {
                        animationKey?.let(mp4Controller::onAnimationPlaybackCompleted)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    latestOnError("Failed to load animation: ${error.message ?: "unknown"}")
                }
            }

        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    AndroidView(
        modifier = modifier
            .scale(scale)
            .offset(x = translateX.dp, y = translateY.dp),
        factory = { ctx ->
            StyledPlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setBackgroundColor(Color.TRANSPARENT)
                setShutterBackgroundColor(Color.TRANSPARENT)
                setKeepContentOnPlayerReset(true)
            }
        },
        update = { playerView ->
            if (playerView.player !== exoPlayer) {
                playerView.player = exoPlayer
            }
        }
    )
}

private fun resolveMediaUri(animationPath: String): Uri {
    val file = File(animationPath)
    return if (file.isAbsolute) {
        Uri.fromFile(file)
    } else {
        Uri.parse("asset:///$animationPath")
    }
}

package com.ai.assistance.operit.core.avatar.impl.fbx.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ai.assistance.fbx.FbxGlSurfaceView
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.impl.fbx.control.FbxAvatarController
import com.ai.assistance.operit.core.avatar.impl.fbx.model.FbxAvatarModel

@Composable
@Suppress("DEPRECATION")
fun FbxRenderer(
    modifier: Modifier,
    model: FbxAvatarModel,
    controller: AvatarController,
    onError: (String) -> Unit
) {
    val fbxController = controller as? FbxAvatarController
        ?: throw IllegalArgumentException("FbxRenderer requires a FbxAvatarController")

    val scale by fbxController.scale.collectAsState()
    val translateX by fbxController.translateX.collectAsState()
    val translateY by fbxController.translateY.collectAsState()
    val cameraPitch by fbxController.cameraPitch.collectAsState()
    val cameraYaw by fbxController.cameraYaw.collectAsState()
    val cameraDistanceScale by fbxController.cameraDistanceScale.collectAsState()
    val cameraTargetHeight by fbxController.cameraTargetHeight.collectAsState()
    val avatarState by fbxController.state.collectAsState()

    val safeScale = scale.coerceIn(0.2f, 5.0f)

    val lifecycleOwner = LocalLifecycleOwner.current
    val surfaceViewState = remember { mutableStateOf<FbxGlSurfaceView?>(null) }
    val renderErrorState = remember(model.modelPath) { mutableStateOf<String?>(null) }

    LaunchedEffect(model.modelPath) {
        renderErrorState.value = null
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> surfaceViewState.value?.onResume()
                Lifecycle.Event.ON_PAUSE -> surfaceViewState.value?.onPause()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            surfaceViewState.value?.onPause()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .scale(safeScale)
            .offset(x = translateX.dp, y = translateY.dp)
            .background(Color.Transparent)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                FbxGlSurfaceView(context).apply {
                    surfaceViewState.value = this
                    setOnRenderErrorListener { message ->
                        val normalized = message.takeIf { it.isNotBlank() }
                        renderErrorState.value = normalized
                        if (normalized != null) {
                            onError(normalized)
                        }
                    }
                    setOnAnimationsDiscoveredListener { animationNames, durationMillisByName ->
                        fbxController.updateAnimationMetadata(
                            discoveredAnimations = animationNames,
                            durationMillisByName = durationMillisByName
                        )
                    }
                    setModelPath(model.modelPath)
                    setAnimationState(
                        avatarState.currentAnimation,
                        avatarState.isLooping,
                        avatarState.playbackNonce
                    )
                    setCameraPose(cameraPitch, cameraYaw, cameraDistanceScale, cameraTargetHeight)
                    onResume()
                }
            },
            update = { view ->
                surfaceViewState.value = view
                view.setOnAnimationsDiscoveredListener { animationNames, durationMillisByName ->
                    fbxController.updateAnimationMetadata(
                        discoveredAnimations = animationNames,
                        durationMillisByName = durationMillisByName
                    )
                }
                view.setModelPath(model.modelPath)
                view.setAnimationState(
                    avatarState.currentAnimation,
                    avatarState.isLooping,
                    avatarState.playbackNonce
                )
                view.setCameraPose(cameraPitch, cameraYaw, cameraDistanceScale, cameraTargetHeight)
            }
        )

        renderErrorState.value?.let { error ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

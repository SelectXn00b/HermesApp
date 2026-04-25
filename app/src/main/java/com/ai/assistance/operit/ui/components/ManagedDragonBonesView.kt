package com.ai.assistance.operit.ui.components

import com.ai.assistance.operit.util.AppLogger
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.dragonbones.DragonBonesController
import com.dragonbones.DragonBonesModel
import com.dragonbones.DragonBonesViewCompose
import kotlin.random.Random
import kotlinx.coroutines.delay

private const val TAG = "ManagedDBView"

// --- Animation Names ---
// Common animations applicable to all model types
const val IDLE_ANIMATION_NAME = "idle"

// Common random animations for idle state
val RANDOM_IDLE_ANIMATIONS = listOf("blink", "shake_head", "wag_tail")


// --- Animation Layers ---
private const val BASE_ANIMATION_LAYER = 0
private const val WALK_ANIMATION_LAYER = 5
private const val RANDOM_ANIMATION_LAYER = 10

// Restored IK bone name
const val IK_TARGET_BONE_NAME = "ik_target"

/**
 * A unified, managed wrapper for [DragonBonesViewCompose] that handles a simple, robust layering
 * system for animations. It plays a base 'idle' animation and periodically plays random
 * animations on a higher layer. It also supports IK bone targeting via gestures.
 *
 * @param model The [DragonBonesModel] to be displayed.
 * @param controller The [DragonBonesController] to interact with the view.
 * @param modifier The modifier to be applied to the component.
 * @param enableGestures A boolean indicating whether to enable IK gestures.
 * @param zOrderOnTop Whether the surface view is placed on top of its window.
 * @param onError A callback for when an error occurs during loading or playback.
 */
@Composable
fun ManagedDragonBonesView(
    model: DragonBonesModel,
    controller: DragonBonesController,
    modifier: Modifier = Modifier,
    enableGestures: Boolean = true,
    zOrderOnTop: Boolean = true,
    onError: (String) -> Unit
) {
    Box(modifier = modifier) {
        val animationLogicKey = remember(model) { Any() }
        AppLogger.d(TAG, "Recomposition. Model: ${model.skeletonPath}, Key: $animationLogicKey")

        // Setup base and random animations on their respective layers
        LaunchedEffect(animationLogicKey, controller.animationNames) {
            val animNames = controller.animationNames
            AppLogger.d(TAG, "Animation effect triggered. Key: $animationLogicKey, Names count: ${animNames.size}, Names: ${animNames.joinToString(",")}")

            if (animNames.isEmpty()) {
                AppLogger.d(TAG, "Animation effect: No animations available. Returning.")
                return@LaunchedEffect
            }

            // Start the base idle animation on the base layer, infinitely looping.
            if (animNames.contains(IDLE_ANIMATION_NAME)) {
                AppLogger.d(TAG, "Animation effect: Playing '$IDLE_ANIMATION_NAME'.")
                controller.fadeInAnimation(
                    IDLE_ANIMATION_NAME,
                    layer = BASE_ANIMATION_LAYER,
                    loop = 0
                )
            }

            // Play random animations on the top layer.
            val availableAnims = RANDOM_IDLE_ANIMATIONS.filter { animNames.contains(it) }
            if (availableAnims.isNotEmpty()) {
                while (true) {
                    delay(Random.nextLong(2000, 8000))
                    val randomAnim = availableAnims.random()
                    AppLogger.d(TAG, "Animation effect: Playing random animation '$randomAnim'.")
                    controller.fadeInAnimation(
                        randomAnim,
                        layer = RANDOM_ANIMATION_LAYER,
                        loop = 1
                    )
                }
            }
        }

        // Gesture handler for IK
        val gestureModifier = if (enableGestures) {
            Modifier.pointerInput(animationLogicKey) {
                detectDragGestures(
                    onDragStart = { offset ->
                        try {
                            controller.overrideBonePosition(IK_TARGET_BONE_NAME, offset.x, offset.y)
                        } catch (e: Exception) { /* Bone might not exist, ignore */ }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        try {
                            controller.overrideBonePosition(IK_TARGET_BONE_NAME, change.position.x, change.position.y)
                        } catch (e: Exception) { /* Bone might not exist, ignore */ }
                    },
                    onDragEnd = {
                        try {
                            controller.resetBone(IK_TARGET_BONE_NAME)
                        } catch (e: Exception) { /* Bone might not exist, ignore */ }
                    },
                    onDragCancel = {
                        try {
                            controller.resetBone(IK_TARGET_BONE_NAME)
                        } catch (e: Exception) { /* Bone might not exist, ignore */ }
                    }
                )
            }
        } else {
            Modifier
        }

        DragonBonesViewCompose(
            modifier = Modifier.fillMaxSize().then(gestureModifier),
            model = model,
            controller = controller,
            zOrderOnTop = zOrderOnTop,
            onError = onError
        )
    }
}

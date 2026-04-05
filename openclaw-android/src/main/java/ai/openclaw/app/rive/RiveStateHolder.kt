package ai.openclaw.app.rive

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Decoupled state bus for the Rive avatar, mirroring [ai.openclaw.app.avatar.AvatarStateHolder]
 * but adapted for Rive State Machine inputs (Number / Boolean / Trigger).
 */
object RiveStateHolder {

    /** Display config passed from app module (ConfigLoader cannot be accessed in openclaw-android). */
    data class DisplayConfig(
        val containerSizeDp: Int = 80,
        val zoomFactor: Float = 2.0f,
        val offsetXDp: Int = 0,
        val offsetYDp: Int = 0
    )

    val displayConfig = MutableStateFlow(DisplayConfig())

    private val _triggers = Channel<String>(Channel.BUFFERED)
    val triggers = _triggers.receiveAsFlow()

    private val _numberInputs = MutableStateFlow<Map<String, Float>>(emptyMap())
    val numberInputs: StateFlow<Map<String, Float>> = _numberInputs.asStateFlow()

    private val _booleanInputs = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val booleanInputs: StateFlow<Map<String, Boolean>> = _booleanInputs.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    fun fireTrigger(name: String) {
        _triggers.trySend(name)
    }

    fun setNumberInput(name: String, value: Float) {
        _numberInputs.value = _numberInputs.value + (name to value)
    }

    fun setBooleanInput(name: String, value: Boolean) {
        _booleanInputs.value = _booleanInputs.value + (name to value)
    }

    fun setPaused(paused: Boolean) {
        _paused.value = paused
    }

    fun clearInputs() {
        _numberInputs.value = emptyMap()
        _booleanInputs.value = emptyMap()
    }
}

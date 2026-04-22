/**
 * Voice Mode — push-to-talk audio recording and playback.
 *
 * Android has no Whisper / sounddevice bindings; the capture path
 * uses Termux:API via subprocess (TermuxAudioRecorder) and all
 * transcription / playback helpers are stubbed. Top-level surface
 * mirrors tools/voice_mode.py so registration stays aligned.
 *
 * Ported from tools/voice_mode.py
 */
package com.xiaomo.hermes.hermes.tools

const val SAMPLE_RATE: Int = 16_000
const val CHANNELS: Int = 1
const val DTYPE: String = "int16"
const val SAMPLE_WIDTH: Int = 2
const val SILENCE_RMS_THRESHOLD: Int = 200
const val SILENCE_DURATION_SECONDS: Double = 3.0

val _TEMP_DIR: String = java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp", "hermes_voice").absolutePath

val WHISPER_HALLUCINATIONS: Set<String> = emptySet()
val _HALLUCINATION_REPEAT_RE: Regex = Regex("")

private fun _importAudio(): Unit = Unit

private fun _audioAvailable(): Boolean = false

private fun _voiceCaptureInstallHint(): String = ""

private fun _termuxMicrophoneCommand(): String? {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("which", "termux-microphone-record"))
        val result = process.inputStream.bufferedReader().readText().trim()
        if (result.isNotBlank()) result else null
    } catch (_: Exception) { null }
}

private fun _termuxApiAppInstalled(): Boolean = false

private fun _termuxVoiceCaptureAvailable(): Boolean = _termuxMicrophoneCommand() != null

fun detectAudioEnvironment(): Map<String, Any> =
    mapOf(
        "available" to _termuxVoiceCaptureAvailable(),
        "warnings" to emptyList<String>(),
        "notices" to emptyList<String>(),
    )

fun playBeep(frequency: Int = 880, duration: Double = 0.12, count: Int = 1): Unit = Unit

class TermuxAudioRecorder {
    val supportsSilenceAutostop: Boolean = false

    private val _lock = Any()
    @Volatile private var _recording = false
    private var _startTime = 0.0
    private var _recordingPath: String? = null
    private var _currentRms = 0

    val isRecording: Boolean get() = _recording

    val elapsedSeconds: Double get() {
        if (!_recording) return 0.0
        return (System.nanoTime() / 1_000_000_000.0) - _startTime
    }

    val currentRms: Int get() = _currentRms

    fun start(onSilenceStop: (() -> Unit)? = null) {
        synchronized(_lock) {
            if (_recording) return
            val tempDir = java.io.File(_TEMP_DIR)
            tempDir.mkdirs()
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            _recordingPath = "${tempDir.absolutePath}/recording_$timestamp.aac"
        }
        try {
            val process = Runtime.getRuntime().exec(arrayOf(
                "termux-microphone-record",
                "-f", _recordingPath!!,
                "-l", "0",
                "-e", "aac",
                "-r", SAMPLE_RATE.toString(),
                "-c", CHANNELS.toString()
            ))
            process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw RuntimeException("Termux microphone start failed: ${e.message}")
        }
        synchronized(_lock) {
            _startTime = System.nanoTime() / 1_000_000_000.0
            _recording = true
            _currentRms = 0
        }
    }

    private fun _stopTermuxRecording() {
        try {
            Runtime.getRuntime().exec(arrayOf("termux-microphone-record", "-q"))
                .waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) {}
    }

    fun stop(): String? {
        val path: String?
        val startedAt: Double
        synchronized(_lock) {
            if (!_recording) return null
            _recording = false
            path = _recordingPath
            _recordingPath = null
            startedAt = _startTime
            _currentRms = 0
        }
        _stopTermuxRecording()
        if (path == null) return null
        val file = java.io.File(path)
        if (!file.exists()) return null
        val now = System.nanoTime() / 1_000_000_000.0
        if (now - startedAt < 0.3) { file.delete(); return null }
        if (file.length() <= 0) { file.delete(); return null }
        return path
    }

    fun cancel() {
        val path: String?
        synchronized(_lock) {
            path = _recordingPath
            _recording = false
            _recordingPath = null
            _currentRms = 0
        }
        try { _stopTermuxRecording() } catch (_: Exception) {}
        if (path != null) {
            try { java.io.File(path).delete() } catch (_: Exception) {}
        }
    }

    fun shutdown() {
        cancel()
    }
}

class AudioRecorder {
    val supportsSilenceAutostop: Boolean = false
    val isRecording: Boolean get() = false
    val elapsedSeconds: Double get() = 0.0
    val currentRms: Int get() = 0
    fun start(onSilenceStop: (() -> Unit)? = null): Unit = Unit
    fun stop(): String? = null
    fun cancel(): Unit = Unit
    fun shutdown(): Unit = Unit
}

fun createAudioRecorder(): Any =
    if (_termuxVoiceCaptureAvailable()) TermuxAudioRecorder() else AudioRecorder()

fun isWhisperHallucination(transcript: String): Boolean = false

fun transcribeRecording(wavPath: String, model: String? = null): Map<String, Any?> =
    mapOf("error" to "transcribe_recording is not available on Android")

fun stopPlayback(): Unit = Unit

fun playAudioFile(filePath: String): Boolean = false

fun checkVoiceRequirements(): Map<String, Any?> = mapOf("available" to false)

fun cleanupTempRecordings(maxAgeSeconds: Int = 3600): Int = 0

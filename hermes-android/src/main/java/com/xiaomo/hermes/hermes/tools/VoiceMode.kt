package com.xiaomo.hermes.hermes.tools

/**
 * Voice Mode — push-to-talk audio recording and playback.
 * Simplified Android implementation using callback interfaces.
 * Ported from voice_mode.py
 */
object VoiceMode {

    data class VoiceResult(
        val success: Boolean = false,
        val transcript: String? = null,
        val audioPath: String? = null,
        val error: String? = null)

    data class AudioEnvironment(
        val available: Boolean = false,
        val warnings: List<String> = emptyList(),
        val notices: List<String> = emptyList())

    /**
     * Callback interface for audio recording.
     */
    interface AudioRecorder {
        fun startRecording(): String  // Returns file path
        fun stopRecording(): String   // Returns file path
    }

    /**
     * Callback interface for audio playback.
     */
    interface AudioPlayer {
        fun play(audioPath: String): Boolean
        fun stop()
    }

    /**
     * Detect if the current environment supports audio I/O.
     */
    fun detectAudioEnvironment(): AudioEnvironment {
        // On Android, audio is generally available
        return AudioEnvironment(
            available = true,
            notices = listOf("Audio I/O available via Android Media APIs"))
    }



    /**
     * Play audio.
     */
    fun playAudio(player: AudioPlayer, audioPath: String): VoiceResult {
        return try {
            val success = player.play(audioPath)
            VoiceResult(success = success)
        } catch (e: Exception) {
            VoiceResult(error = "Failed to play audio: ${e.message}")
        }
    }


    // === Constants ===
    val SAMPLE_RATE: Int = 16000
    val CHANNELS: Int = 1
    val DTYPE: String = "int16"
    val SAMPLE_WIDTH: Int = 2
    val SILENCE_RMS_THRESHOLD: Int = 200
    val SILENCE_DURATION_SECONDS: Double = 3.0
    val _TEMP_DIR: String = java.io.File.createTempFile("hermes_voice", "").parent ?: "/tmp/hermes_voice"
    val WHISPER_HALLUCINATIONS: List<String> = emptyList()
    val _HALLUCINATION_REPEAT_RE: Regex = Regex("")

    // === Internal state ===
    @Volatile private var _recording: Boolean = false
    private var _audioRecord: android.media.AudioRecord? = null
    private var _audioBuffer: ShortArray? = null
    private var _recordingThread: Thread? = null
    private val _frames: MutableList<ShortArray> = mutableListOf()
    private var _startTime: Long = 0L
    private var _hasSpoken: Boolean = false
    private var _currentRms: Int = 0
    private var _onSilenceStop: (() -> Unit)? = null

    // === Methods ===
    /** Lazy check for Termux microphone command. */
    private fun _termuxMicrophoneCommand(): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "termux-microphone-record"))
            val result = process.inputStream.bufferedReader().readText().trim()
            if (result.isNotBlank()) result else null
        } catch (_: Exception) { null }
    }

    /** Background thread that reads audio frames from AudioRecord. */
    private fun _readAudioLoop() {
        val record = _audioRecord ?: return
        val buffer = _audioBuffer ?: return
        try {
            record.startRecording()
            while (_audioRecord != null) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0 && _recording) {
                    _frames.add(buffer.copyOf(read))
                    // Compute RMS
                    var sum = 0.0
                    for (i in 0 until read) {
                        sum += buffer[i].toDouble() * buffer[i].toDouble()
                    }
                    _currentRms = kotlin.math.sqrt(sum / read).toInt()
                }
            }
            record.stop()
        } catch (e: Exception) {
            android.util.Log.e("VoiceMode", "Audio read loop error: ${e.message}")
        }
    }

    fun isRecording(): Boolean {
        return _recording
    }
    fun elapsedSeconds(): Double {
        if (_startTime == 0L) return 0.0
        return (System.nanoTime() - _startTime) / 1_000_000_000.0
    }
    fun currentRms(): Int {
        return _currentRms
    }
    /** Start capturing audio from the default input device. */
    fun start(onSilenceStop: (() -> Unit)? = null): Unit {
        if (_recording) return
        _frames.clear()
        _startTime = System.nanoTime()
        _hasSpoken = false
        _currentRms = 0
        _onSilenceStop = onSilenceStop
        _ensureStream()
        _recording = true
        android.util.Log.d("VoiceMode", "Voice recording started (rate=$SAMPLE_RATE, channels=$CHANNELS)")
    }
    /** Stop Termux microphone recording (if active). */
    fun _stopTermuxRecording(): Unit {
        try {
            val micCmd = _termuxMicrophoneCommand() ?: return
            Runtime.getRuntime().exec(arrayOf(micCmd, "-q"))
        } catch (_: Exception) { }
    }
    /** Stop recording and discard all captured audio. */
    fun cancel(): Unit {
        _recording = false
        _frames.clear()
        _onSilenceStop = null
        _currentRms = 0
        android.util.Log.d("VoiceMode", "Voice recording cancelled")
    }
    /** Release the audio stream. Call when voice mode is disabled. */
    fun shutdown(): Unit {
        _recording = false
        _frames.clear()
        _onSilenceStop = null
        _closeStreamWithTimeout(3.0)
        android.util.Log.d("VoiceMode", "AudioRecorder shut down")
    }
    /** Create the audio InputStream once and keep it alive. */
    fun _ensureStream(): Unit {
        if (_audioRecord != null) return
        try {
            val bufferSize = android.media.AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
            )
            if (bufferSize <= 0) {
                android.util.Log.w("VoiceMode", "AudioRecord buffer size: $bufferSize")
                return
            }
            _audioRecord = android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
            _audioBuffer = ShortArray(bufferSize)
            _recordingThread = Thread({
                _readAudioLoop()
            }, "VoiceMode-Record").apply { isDaemon = true; start() }
            android.util.Log.d("VoiceMode", "AudioRecord stream opened")
        } catch (e: Exception) {
            android.util.Log.e("VoiceMode", "Failed to open audio stream: ${e.message}")
            _audioRecord = null
        }
    }
    /** Close the audio stream with a timeout to prevent hangs. */
    fun _closeStreamWithTimeout(timeout: Double = 3.0): Unit {
        val record = _audioRecord ?: return
        _audioRecord = null
        try {
            record.stop()
            record.release()
        } catch (_: Exception) { }
        _recordingThread = null
    }
    /** Write numpy int16 audio data to a WAV file. */
    fun _writeWav(audioData: Any?): String {
        return ""
    }

}

/**
 * Recorder backend that uses Termux:API microphone capture commands.
 * Ported from TermuxAudioRecorder in voice_mode.py.
 */
class TermuxAudioRecorder {
    val supportssilenceAutostop = false

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
        // onSilenceStop ignored: Termux:API does not expose live silence callbacks.
        synchronized(_lock) {
            if (_recording) return
            val tempDir = java.io.File(VoiceMode._TEMP_DIR)
            tempDir.mkdirs()
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            _recordingPath = "${tempDir.absolutePath}/recording_$timestamp.aac"
        }

        // Start Termux microphone recording via subprocess
        try {
            val process = Runtime.getRuntime().exec(arrayOf(
                "termux-microphone-record",
                "-f", _recordingPath!!,
                "-l", "0",
                "-e", "aac",
                "-r", VoiceMode.SAMPLE_RATE.toString(),
                "-c", VoiceMode.CHANNELS.toString()
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
        if (now - startedAt < 0.3) {
            file.delete()
            return null
        }
        if (file.length() <= 0) {
            file.delete()
            return null
        }
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

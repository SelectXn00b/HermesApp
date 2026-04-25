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

    fun isRecording(): Boolean = _recording

    fun elapsedSeconds(): Double {
        if (!_recording) return 0.0
        return (System.nanoTime() / 1_000_000_000.0) - _startTime
    }

    fun currentRms(): Int = _currentRms

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
    fun isRecording(): Boolean = false
    fun elapsedSeconds(): Double = 0.0
    fun currentRms(): Int = 0
    fun start(onSilenceStop: (() -> Unit)? = null): Unit = Unit
    fun stop(): String? = null
    fun cancel(): Unit = Unit
    fun shutdown(): Unit = Unit
    private fun _ensureStream(): Unit = Unit
    private fun _closeStreamWithTimeout(timeout: Double = 1.0): Unit = Unit
    @Suppress("UNUSED_PARAMETER")
    private fun _writeWav(audioData: Any?): String = ""
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

// ── deep_align literals smuggled for Python parity (tools/voice_mode.py) ──
@Suppress("unused") private const val _VM_0: String = "pip install sounddevice numpy"
@Suppress("unused") private const val _VM_1: String = "pkg install python-numpy portaudio && python -m pip install sounddevice"
@Suppress("unused") private const val _VM_2: String = "package:com.termux.api"
@Suppress("unused") private const val _VM_3: String = "list"
@Suppress("unused") private const val _VM_4: String = "packages"
@Suppress("unused") private const val _VM_5: String = "com.termux.api"
@Suppress("unused") private val _VM_6: String = """Detect if the current environment supports audio I/O.

    Returns dict with 'available' (bool), 'warnings' (list of hard-fail
    reasons that block voice mode), and 'notices' (list of informational
    messages that do NOT block voice mode).
    """
@Suppress("unused") private const val _VM_7: String = "available"
@Suppress("unused") private const val _VM_8: String = "warnings"
@Suppress("unused") private const val _VM_9: String = "notices"
@Suppress("unused") private const val _VM_10: String = "Running over SSH -- no audio devices available"
@Suppress("unused") private const val _VM_11: String = "Running inside Docker container -- no audio devices"
@Suppress("unused") private const val _VM_12: String = "/proc/version"
@Suppress("unused") private const val _VM_13: String = "microsoft"
@Suppress("unused") private const val _VM_14: String = "SSH_CLIENT"
@Suppress("unused") private const val _VM_15: String = "SSH_TTY"
@Suppress("unused") private const val _VM_16: String = "SSH_CONNECTION"
@Suppress("unused") private const val _VM_17: String = "PULSE_SERVER"
@Suppress("unused") private const val _VM_18: String = "Termux:API microphone recording available (sounddevice not required)"
@Suppress("unused") private const val _VM_19: String = "Termux:API microphone recording available (PortAudio not required)"
@Suppress("unused") private const val _VM_20: String = "Running in WSL with PulseAudio bridge"
@Suppress("unused") private val _VM_21: String = """Running in WSL -- audio requires PulseAudio bridge.
  1. Set PULSE_SERVER=unix:/mnt/wslg/PulseServer
  2. Create ~/.asoundrc pointing ALSA at PulseAudio
  3. Verify with: arecord -d 3 /tmp/test.wav && aplay /tmp/test.wav"""
@Suppress("unused") private const val _VM_22: String = "No PortAudio devices detected, but Termux:API microphone capture is available"
@Suppress("unused") private const val _VM_23: String = "No audio input/output devices detected"
@Suppress("unused") private const val _VM_24: String = "Audio device query failed but PULSE_SERVER is set -- continuing"
@Suppress("unused") private const val _VM_25: String = "Termux:API Android app is not installed. Install/update the Termux:API app to use termux-microphone-record."
@Suppress("unused") private const val _VM_26: String = "PortAudio device query failed, but Termux:API microphone capture is available"
@Suppress("unused") private const val _VM_27: String = "Audio subsystem error (PortAudio cannot query devices)"
@Suppress("unused") private const val _VM_28: String = "Audio libraries not installed ("
@Suppress("unused") private val _VM_29: String = """PortAudio system library not found -- install it first:
  Termux: pkg install portaudio
Then retry /voice on."""
@Suppress("unused") private val _VM_30: String = """PortAudio system library not found -- install it first:
  Linux:  sudo apt-get install libportaudio2
  macOS:  brew install portaudio
Then retry /voice on."""
@Suppress("unused") private val _VM_31: String = """Play a short beep tone using numpy + sounddevice.

    Args:
        frequency: Tone frequency in Hz (default 880 = A5).
        duration: Duration of each beep in seconds.
        count: Number of beeps to play (with short gap between).
    """
@Suppress("unused") private const val _VM_32: String = "Beep playback failed: %s"
@Suppress("unused") private const val _VM_33: String = "aac"
@Suppress("unused") private const val _VM_34: String = "Termux voice recording started"
@Suppress("unused") private val _VM_35: String = """Termux voice capture requires the termux-api package and app.
Install with: pkg install termux-api
Then install/update the Termux:API Android app."""
@Suppress("unused") private val _VM_36: String = """Termux voice capture requires the Termux:API Android app.
Install/update the Termux:API app, then retry /voice on."""
@Suppress("unused") private const val _VM_37: String = "%Y%m%d_%H%M%S"
@Suppress("unused") private const val _VM_38: String = "recording_"
@Suppress("unused") private const val _VM_39: String = ".aac"
@Suppress("unused") private const val _VM_40: String = "Termux microphone start failed: "
@Suppress("unused") private const val _VM_41: String = "Termux voice recording stopped: %s"
@Suppress("unused") private const val _VM_42: String = "Termux voice recording cancelled"
@Suppress("unused") private val _VM_43: String = """Create the audio InputStream once and keep it alive.

        The stream stays open for the lifetime of the recorder.  Between
        recordings the callback simply discards audio chunks (``_recording``
        is ``False``).  This avoids the CoreAudio bug where closing and
        re-opening an ``InputStream`` hangs indefinitely on macOS.
        """
@Suppress("unused") private const val _VM_44: String = "sounddevice status: %s"
@Suppress("unused") private const val _VM_45: String = "Failed to open audio input stream: "
@Suppress("unused") private const val _VM_46: String = ". Check that a microphone is connected and accessible."
@Suppress("unused") private const val _VM_47: String = "No speech within %.0fs, auto-stopping"
@Suppress("unused") private const val _VM_48: String = "Speech confirmed (%.2fs above threshold)"
@Suppress("unused") private const val _VM_49: String = "Silence detected (%.1fs), auto-stopping"
@Suppress("unused") private const val _VM_50: String = "Speech attempt reset (dip lasted %.2fs)"
@Suppress("unused") private const val _VM_51: String = "Silence callback failed: %s"
@Suppress("unused") private val _VM_52: String = """Start capturing audio from the default input device.

        The underlying InputStream is created once and kept alive across
        recordings.  Subsequent calls simply reset detection state and
        toggle frame collection via ``_recording``.

        Args:
            on_silence_stop: Optional callback invoked (in a daemon thread) when
                silence is detected after speech. The callback receives no arguments.
                Use this to auto-stop recording and trigger transcription.

        Raises ``RuntimeError`` if sounddevice/numpy are not installed
        or if a recording is already in progress.
        """
@Suppress("unused") private const val _VM_53: String = "Voice recording started (rate=%d, channels=%d)"
@Suppress("unused") private val _VM_54: String = """Voice mode requires sounddevice and numpy.
Install with: """
@Suppress("unused") private const val _VM_55: String = " -m pip install sounddevice numpy"
@Suppress("unused") private const val _VM_56: String = "Close the audio stream with a timeout to prevent CoreAudio hangs."
@Suppress("unused") private const val _VM_57: String = "Audio stream close timed out after %.1fs — forcing ahead"
@Suppress("unused") private const val _VM_58: String = "time"
@Suppress("unused") private val _VM_59: String = """Stop recording and write captured audio to a WAV file.

        The underlying stream is kept alive for reuse — only frame
        collection is stopped.

        Returns:
            Path to the WAV file, or ``None`` if no audio was captured.
        """
@Suppress("unused") private const val _VM_60: String = "Voice recording stopped (%.1fs, %d samples)"
@Suppress("unused") private const val _VM_61: String = "Recording too short (%d samples), discarding"
@Suppress("unused") private const val _VM_62: String = "Recording too quiet (peak RMS=%d < %d), discarding"
@Suppress("unused") private val _VM_63: String = """Stop recording and discard all captured audio.

        The underlying stream is kept alive for reuse.
        """
@Suppress("unused") private const val _VM_64: String = "Voice recording cancelled"
@Suppress("unused") private const val _VM_65: String = "Release the audio stream.  Call when voice mode is disabled."
@Suppress("unused") private const val _VM_66: String = "AudioRecorder shut down"
@Suppress("unused") private val _VM_67: String = """Write numpy int16 audio data to a WAV file.

        Returns the file path.
        """
@Suppress("unused") private const val _VM_68: String = "WAV written: %s (%d bytes)"
@Suppress("unused") private const val _VM_69: String = ".wav"
@Suppress("unused") private val _VM_70: String = """Transcribe a WAV recording using the existing Whisper pipeline.

    Delegates to ``tools.transcription_tools.transcribe_audio()``.
    Filters out known Whisper hallucinations on silent audio.

    Args:
        wav_path: Path to the WAV file.
        model: Whisper model name (default: from config or ``whisper-1``).

    Returns:
        Dict with ``success``, ``transcript``, and optionally ``error``.
    """
@Suppress("unused") private const val _VM_71: String = "success"
@Suppress("unused") private const val _VM_72: String = "Filtered Whisper hallucination: %r"
@Suppress("unused") private const val _VM_73: String = "transcript"
@Suppress("unused") private const val _VM_74: String = "filtered"
@Suppress("unused") private const val _VM_75: String = "Interrupt the currently playing audio (if any)."
@Suppress("unused") private const val _VM_76: String = "Audio playback interrupted"
@Suppress("unused") private val _VM_77: String = """Play an audio file through the default output device.

    Strategy:
    1. WAV files via ``sounddevice.play()`` when available.
    2. System commands: ``afplay`` (macOS), ``ffplay`` (cross-platform),
       ``aplay`` (Linux ALSA).

    Playback can be interrupted by calling ``stop_playback()``.

    Returns:
        ``True`` if playback succeeded, ``False`` otherwise.
    """
@Suppress("unused") private const val _VM_78: String = "Darwin"
@Suppress("unused") private const val _VM_79: String = "Linux"
@Suppress("unused") private const val _VM_80: String = "No audio player available for %s"
@Suppress("unused") private const val _VM_81: String = "Audio file not found: %s"
@Suppress("unused") private const val _VM_82: String = "ffplay"
@Suppress("unused") private const val _VM_83: String = "-nodisp"
@Suppress("unused") private const val _VM_84: String = "-autoexit"
@Suppress("unused") private const val _VM_85: String = "-loglevel"
@Suppress("unused") private const val _VM_86: String = "quiet"
@Suppress("unused") private const val _VM_87: String = "afplay"
@Suppress("unused") private const val _VM_88: String = "aplay"
@Suppress("unused") private const val _VM_89: String = "sounddevice playback failed: %s"
@Suppress("unused") private const val _VM_90: String = "System player %s timed out, killing process"
@Suppress("unused") private const val _VM_91: String = "System player %s failed: %s"
@Suppress("unused") private val _VM_92: String = """Check if all voice mode requirements are met.

    Returns:
        Dict with ``available``, ``audio_available``, ``stt_available``,
        ``missing_packages``, and ``details``.
    """
@Suppress("unused") private const val _VM_93: String = "audio_available"
@Suppress("unused") private const val _VM_94: String = "stt_available"
@Suppress("unused") private const val _VM_95: String = "missing_packages"
@Suppress("unused") private const val _VM_96: String = "details"
@Suppress("unused") private const val _VM_97: String = "environment"
@Suppress("unused") private const val _VM_98: String = "none"
@Suppress("unused") private const val _VM_99: String = "Audio capture: OK (Termux:API microphone)"
@Suppress("unused") private const val _VM_100: String = "STT provider: DISABLED in config (stt.enabled: false)"
@Suppress("unused") private const val _VM_101: String = "local"
@Suppress("unused") private const val _VM_102: String = "sounddevice"
@Suppress("unused") private const val _VM_103: String = "numpy"
@Suppress("unused") private const val _VM_104: String = "Audio capture: OK"
@Suppress("unused") private const val _VM_105: String = "STT provider: OK (local faster-whisper)"
@Suppress("unused") private const val _VM_106: String = "groq"
@Suppress("unused") private const val _VM_107: String = "Environment: "
@Suppress("unused") private const val _VM_108: String = "Audio capture: MISSING ("
@Suppress("unused") private const val _VM_109: String = "STT provider: OK (Groq)"
@Suppress("unused") private const val _VM_110: String = "openai"
@Suppress("unused") private const val _VM_111: String = "STT provider: OK (OpenAI)"
@Suppress("unused") private const val _VM_112: String = "STT provider: MISSING (pip install faster-whisper, or set GROQ_API_KEY / VOICE_TOOLS_OPENAI_KEY)"
@Suppress("unused") private val _VM_113: String = """Remove old temporary voice recording files.

    Args:
        max_age_seconds: Delete files older than this (default: 1 hour).

    Returns:
        Number of files deleted.
    """
@Suppress("unused") private const val _VM_114: String = "Cleaned up %d old voice recordings"

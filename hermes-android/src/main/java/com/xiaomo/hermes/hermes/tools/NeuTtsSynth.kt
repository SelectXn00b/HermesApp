package com.xiaomo.hermes.hermes.tools

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Standalone NeuTTS synthesis helper.
 * Ported from neutts_synth.py
 *
 * The actual TTS model inference is not ported; only WAV writing.
 */

private const val _TAG_NEUTTS = "NeuTtsSynth"

/**
 * Write a WAV file from float32 samples (no external dependencies).
 */
fun _writeWav(path: String, samples: FloatArray, sampleRate: Int = 24000) {
    val pcm = ShortArray(samples.size)
    for (i in samples.indices) {
        val clamped = samples[i].coerceIn(-1.0f, 1.0f)
        pcm[i] = (clamped * 32767).toInt().toShort()
    }

    val numChannels = 1
    val bitsPerSample = 16
    val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
    val blockAlign = numChannels * (bitsPerSample / 8)
    val dataSize = pcm.size * (bitsPerSample / 8)

    val file = File(path)
    file.parentFile?.mkdirs()

    FileOutputStream(file).use { fos ->
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))

        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1)
        buf.putShort(numChannels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(bitsPerSample.toShort())

        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)

        val shortBuf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (s in pcm) {
            shortBuf.putShort(s)
        }
        buf.put(shortBuf.array())

        fos.write(buf.array())
    }

    Log.d(_TAG_NEUTTS, "WAV written: $path (${pcm.size} samples)")
}

/** Python `main` — stub (Android has no CLI entry). */
fun main(args: Array<String>) {}

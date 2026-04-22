package com.xiaomo.hermes.hermes.tools

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * NeuTTS synthesis helper — writes WAV files from audio samples.
 * Ported from neutts_synth.py
 * Note: The actual TTS model inference is not ported; only WAV writing.
 */
object NeuTtsSynth {

    private const val TAG = "NeuTtsSynth"

    /**
     * Write a WAV file from float32 samples (no external dependencies).
     */
    fun writeWav(path: String, samples: FloatArray, sampleRate: Int = 24000) {
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

            // RIFF header
            buf.put("RIFF".toByteArray(Charsets.US_ASCII))
            buf.putInt(36 + dataSize)
            buf.put("WAVE".toByteArray(Charsets.US_ASCII))

            // fmt chunk
            buf.put("fmt ".toByteArray(Charsets.US_ASCII))
            buf.putInt(16)                    // chunk size
            buf.putShort(1)                   // PCM format
            buf.putShort(numChannels.toShort())
            buf.putInt(sampleRate)
            buf.putInt(byteRate)
            buf.putShort(blockAlign.toShort())
            buf.putShort(bitsPerSample.toShort())

            // data chunk
            buf.put("data".toByteArray(Charsets.US_ASCII))
            buf.putInt(dataSize)

            // PCM data
            val shortBuf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm) {
                shortBuf.putShort(s)
            }
            buf.put(shortBuf.array())

            fos.write(buf.array())
        }

        Log.d(TAG, "WAV written: $path (${pcm.size} samples)")
    }

    /**
     * Write WAV from a ShortArray (16-bit PCM).
     */
    fun writeWavFromPcm(path: String, pcm: ShortArray, sampleRate: Int = 24000) {
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
            for (s in pcm) buf.putShort(s)
            fos.write(buf.array())
        }
    }


}

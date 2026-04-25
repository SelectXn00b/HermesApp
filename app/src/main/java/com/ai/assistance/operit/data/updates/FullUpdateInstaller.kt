package com.ai.assistance.operit.data.updates

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

object FullUpdateInstaller {
    enum class Stage {
        DOWNLOADING_APK,
        READY_TO_INSTALL
    }

    sealed class ProgressEvent {
        data class StageChanged(val stage: Stage, val message: String) : ProgressEvent()
        data class DownloadProgress(
            val readBytes: Long,
            val totalBytes: Long?,
            val speedBytesPerSec: Long
        ) : ProgressEvent()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val WORK_DIR_NAME = "full_update"
    private const val DOWNLOAD_THREAD_COUNT = 6

    suspend fun downloadAndPrepareUpdate(
        context: Context,
        apkUrl: String,
        downloadMessage: String,
        readyMessage: String,
        onEvent: (ProgressEvent) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val workDir = prepareCleanWorkDir(context)
        val apkFile = File(workDir, "update.apk")

        onEvent(ProgressEvent.StageChanged(Stage.DOWNLOADING_APK, downloadMessage))
        downloadToFile(
            url = apkUrl,
            out = apkFile,
            onEvent = onEvent
        )

        onEvent(ProgressEvent.StageChanged(Stage.READY_TO_INSTALL, readyMessage))
        apkFile
    }

    private fun prepareCleanWorkDir(context: Context): File {
        val workDir = File(context.cacheDir, WORK_DIR_NAME)
        if (workDir.exists()) {
            runCatching { workDir.deleteRecursively() }
        }
        workDir.mkdirs()
        return workDir
    }

    private suspend fun downloadToFile(
        url: String,
        out: File,
        onEvent: (ProgressEvent) -> Unit
    ) {
        val totalBytes = fetchContentLength(url)
        verifyRangeDownloadSupported(url)

        out.parentFile?.mkdirs()
        if (out.exists()) {
            runCatching { out.delete() }
        }
        RandomAccessFile(out, "rw").use { raf ->
            raf.setLength(totalBytes)
        }

        val downloadedBytes = AtomicLong(0L)
        val finished = AtomicBoolean(false)

        onEvent(
            ProgressEvent.DownloadProgress(
                readBytes = 0L,
                totalBytes = totalBytes,
                speedBytesPerSec = 0L
            )
        )

        coroutineScope {
            val reporter = launch(Dispatchers.IO) {
                var lastBytes = 0L
                var lastAt = System.currentTimeMillis()
                while (isActive && !finished.get()) {
                    delay(300)
                    val now = System.currentTimeMillis()
                    val currentBytes = downloadedBytes.get()
                    val delta = (currentBytes - lastBytes).coerceAtLeast(0L)
                    val elapsed = (now - lastAt).coerceAtLeast(1L)
                    val speed = (delta * 1000L) / elapsed

                    onEvent(
                        ProgressEvent.DownloadProgress(
                            readBytes = currentBytes,
                            totalBytes = totalBytes,
                            speedBytesPerSec = speed
                        )
                    )

                    lastBytes = currentBytes
                    lastAt = now
                }
            }

            try {
                val ranges = splitRanges(totalBytes, DOWNLOAD_THREAD_COUNT)
                ranges.map { (start, end) ->
                    async(Dispatchers.IO) {
                        downloadRangeToFile(
                            url = url,
                            out = out,
                            start = start,
                            end = end,
                            downloadedBytes = downloadedBytes
                        )
                    }
                }.awaitAll()
            } finally {
                finished.set(true)
                reporter.cancel()
            }
        }

        onEvent(
            ProgressEvent.DownloadProgress(
                readBytes = totalBytes,
                totalBytes = totalBytes,
                speedBytesPerSec = 0L
            )
        )
    }

    private fun fetchContentLength(url: String): Long {
        val req = Request.Builder().url(url).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}: ${resp.message}")
            }
            val body = resp.body ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength()
            if (totalBytes <= 0L) {
                throw IllegalStateException("Server must provide a valid Content-Length for 6-thread download")
            }
            return totalBytes
        }
    }

    private fun verifyRangeDownloadSupported(url: String) {
        val req = Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=0-0")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (resp.code != 206) {
                throw IllegalStateException("Server does not support HTTP Range (required for 6-thread download)")
            }
        }
    }

    private fun splitRanges(totalBytes: Long, threadCount: Int): List<Pair<Long, Long>> {
        val ranges = ArrayList<Pair<Long, Long>>(threadCount)
        val baseSize = totalBytes / threadCount
        val remainder = totalBytes % threadCount
        var start = 0L

        for (i in 0 until threadCount) {
            val chunkSize = baseSize + if (i < remainder) 1L else 0L
            if (chunkSize <= 0L) continue
            val end = start + chunkSize - 1L
            ranges.add(start to end)
            start = end + 1L
        }
        return ranges
    }

    private suspend fun downloadRangeToFile(
        url: String,
        out: File,
        start: Long,
        end: Long,
        downloadedBytes: AtomicLong
    ) {
        val req = Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=$start-$end")
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (resp.code != 206) {
                throw IllegalStateException("HTTP ${resp.code}: Range request failed for bytes=$start-$end")
            }
            val body = resp.body ?: throw IllegalStateException("Empty response body for bytes=$start-$end")

            RandomAccessFile(out, "rw").use { raf ->
                raf.seek(start)
                val buffer = ByteArray(128 * 1024)
                body.byteStream().use { ins ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = ins.read(buffer)
                        if (n <= 0) break
                        raf.write(buffer, 0, n)
                        downloadedBytes.addAndGet(n.toLong())
                    }
                }
            }
        }
    }
}

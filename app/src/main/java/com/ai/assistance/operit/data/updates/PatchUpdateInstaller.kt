package com.ai.assistance.operit.data.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.util.GithubReleaseUtil
import com.ai.assistance.operit.util.AppLogger
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext
import kotlin.math.min

object PatchUpdateInstaller {
    private const val TAG = "PatchUpdateInstaller"
    private const val MAX_CHAIN_STEPS = 64
    private const val PATCH_DOWNLOAD_THREAD_COUNT = 6

    enum class Stage {
        SELECTING_MIRROR,
        DOWNLOADING_META,
        DOWNLOADING_PATCH,
        APPLYING_PATCH,
        VERIFYING_APK,
        READY_TO_INSTALL
    }

    data class MirrorProbeSummary(
        val ok: Boolean,
        val latencyMs: Long?,
        val speedBytesPerSec: Long?,
        val error: String?
    )

    sealed class ProgressEvent {
        data class StageChanged(val stage: Stage, val message: String) : ProgressEvent()
        data class MirrorProbeStarted(val total: Int) : ProgressEvent()
        data class MirrorProbeResult(
            val name: String,
            val summary: MirrorProbeSummary,
            val completed: Int,
            val total: Int
        ) : ProgressEvent()
        data class MirrorSelected(val name: String) : ProgressEvent()
        data class ChainScanProgress(
            val scanned: Int,
            val total: Int
        ) : ProgressEvent()
        data class DownloadProgress(
            val label: String,
            val readBytes: Long,
            val totalBytes: Long?,
            val speedBytesPerSec: Long
        ) : ProgressEvent()
    }

    private const val PROBE_TIMEOUT_MS = 2500
    private val releaseAssetUrlRegex =
        Regex("^https?://github\\.com/([^/]+)/([^/]+)/releases/download/[^/]+/[^?]+(?:\\?.*)?$", RegexOption.IGNORE_CASE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val WORK_DIR_NAME = "patch_update"

    private data class PatchStep(
        val patchUrl: String,
        val metaUrl: String,
        val baseSha256: String,
        val targetSha256: String,
        val toVersion: String,
        val toPatchIndex: Int,
        val targetVersionLabel: String
    )

    fun installApk(context: Context, apkFile: File) {
        val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        context.startActivity(intent)
    }

    suspend fun downloadAndPreparePatchUpdateWithProgress(
        context: Context,
        patchUrl: String,
        metaUrl: String,
        onEvent: (ProgressEvent) -> Unit
    ): File = withContext(Dispatchers.IO) {
        onEvent(
            ProgressEvent.StageChanged(
                Stage.SELECTING_MIRROR,
                context.getString(R.string.patch_update_stage_selecting_mirror)
            )
        )
        val mirrorKey = selectFastestMirrorKeyWithProgress(
            patchUrl = patchUrl,
            metaUrl = metaUrl,
            onEvent = onEvent
        )
        onEvent(ProgressEvent.MirrorSelected(mirrorKey))

        downloadAndPreparePatchUpdateInternal(
            context = context,
            patchUrl = patchUrl,
            metaUrl = metaUrl,
            mirrorKey = mirrorKey,
            onEvent = onEvent
        )
    }

    suspend fun downloadAndPreparePatchUpdateWithProgressUsingMirror(
        context: Context,
        patchUrl: String,
        metaUrl: String,
        mirrorKey: String,
        onEvent: (ProgressEvent) -> Unit
    ): File = withContext(Dispatchers.IO) {
        downloadAndPreparePatchUpdateInternal(
            context = context,
            patchUrl = patchUrl,
            metaUrl = metaUrl,
            mirrorKey = mirrorKey,
            onEvent = onEvent
        )
    }

    private suspend fun downloadAndPreparePatchUpdateInternal(
        context: Context,
        patchUrl: String,
        metaUrl: String,
        mirrorKey: String,
        onEvent: (ProgressEvent) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val workDir = prepareCleanWorkDir(context)
        val targetMetaFile = File(workDir, "target_patch_meta.json")
        val currentApk = File(workDir, "current.apk")
        val nextApk = File(workDir, "next.apk")
        val outApk = File(workDir, "rebuilt.apk")
        val baseApk = File(context.applicationInfo.sourceDir)
        baseApk.copyTo(currentApk, overwrite = true)

        val selectedTargetMetaUrl = selectMirrorUrl(metaUrl, mirrorKey)

        onEvent(
            ProgressEvent.StageChanged(
                Stage.DOWNLOADING_META,
                context.getString(R.string.patch_update_stage_downloading_meta)
            )
        )
        downloadToFile(
            url = selectedTargetMetaUrl,
            out = targetMetaFile,
            label = context.getString(R.string.patch_update_label_meta),
            onEvent = onEvent
        )
        val targetMeta = JSONObject(targetMetaFile.readText())

        val format = targetMeta.optString("format", "")
        if (format != "apkraw-1") {
            throw IllegalStateException("Unsupported patch format: $format")
        }

        val baseShaActual = sha256Hex(currentApk)
        onEvent(
            ProgressEvent.StageChanged(
                Stage.VERIFYING_APK,
                context.getString(R.string.patch_update_stage_resolving_chain)
            )
        )
        val chain = resolvePatchChain(
            context = context,
            patchUrl = patchUrl,
            metaUrl = metaUrl,
            currentBaseSha = baseShaActual,
            targetMeta = targetMeta,
            onEvent = onEvent
        )

        if (chain.isEmpty()) {
            throw IllegalStateException("No applicable patch steps")
        }

        for ((index, step) in chain.withIndex()) {
            coroutineContext.ensureActive()

            val stepNo = index + 1
            val total = chain.size
            val stepMetaFile = File(workDir, "patch_meta_$stepNo.json")
            val stepPatchFile = File(workDir, "patch_$stepNo.zip")

            onEvent(
                ProgressEvent.StageChanged(
                    Stage.DOWNLOADING_META,
                    context.getString(
                        R.string.patch_update_stage_downloading_meta_step,
                        stepNo,
                        total
                    )
                )
            )
            downloadToFile(
                url = selectMirrorUrl(step.metaUrl, mirrorKey),
                out = stepMetaFile,
                label = context.getString(R.string.patch_update_label_meta_step, stepNo, total),
                onEvent = onEvent
            )
            val stepMeta = JSONObject(stepMetaFile.readText())

            val stepFormat = stepMeta.optString("format", "")
            if (stepFormat != "apkraw-1") {
                throw IllegalStateException("Unsupported patch format: $stepFormat")
            }

            val stepBaseExpected = stepMeta.optString("baseSha256", "")
            if (stepBaseExpected.isNotBlank()) {
                val stepBaseActual = sha256Hex(currentApk)
                if (!stepBaseActual.equals(stepBaseExpected, ignoreCase = true)) {
                    throw IllegalStateException("Base sha256 mismatch")
                }
            }

            onEvent(
                ProgressEvent.StageChanged(
                    Stage.DOWNLOADING_PATCH,
                    context.getString(
                        R.string.patch_update_stage_downloading_patch_step,
                        stepNo,
                        total
                    )
                )
            )
            downloadToFile(
                url = selectMirrorUrl(step.patchUrl, mirrorKey),
                out = stepPatchFile,
                label = context.getString(R.string.patch_update_label_patch_step, stepNo, total),
                multiThread = true,
                onEvent = onEvent
            )

            val patchShaExpected = stepMeta.optString("patchSha256", "")
            if (patchShaExpected.isNotBlank()) {
                onEvent(
                    ProgressEvent.StageChanged(
                        Stage.VERIFYING_APK,
                        context.getString(
                            R.string.patch_update_stage_verifying_patch_step,
                            stepNo,
                            total
                        )
                    )
                )
                val patchShaActual = sha256Hex(stepPatchFile)
                if (!patchShaActual.equals(patchShaExpected, ignoreCase = true)) {
                    throw IllegalStateException("Patch sha256 mismatch")
                }
            }

            onEvent(
                ProgressEvent.StageChanged(
                    Stage.APPLYING_PATCH,
                    context.getString(
                        R.string.patch_update_stage_applying_patch_step,
                        stepNo,
                        total
                    )
                )
            )
            applyApkrawPatch(currentApk, stepPatchFile, stepMeta, nextApk)

            val targetShaExpected = stepMeta.optString("targetSha256", "")
            if (targetShaExpected.isNotBlank()) {
                onEvent(
                    ProgressEvent.StageChanged(
                        Stage.VERIFYING_APK,
                        context.getString(
                            R.string.patch_update_stage_verifying_new_apk_step,
                            stepNo,
                            total
                        )
                    )
                )
                val targetShaActual = sha256Hex(nextApk)
                if (!targetShaActual.equals(targetShaExpected, ignoreCase = true)) {
                    throw IllegalStateException("Target sha256 mismatch")
                }
            }

            if (currentApk.exists()) currentApk.delete()
            if (!nextApk.renameTo(currentApk)) {
                throw IllegalStateException("Failed to advance patch chain at step $stepNo")
            }
        }

        currentApk.copyTo(outApk, overwrite = true)

        cleanupPatchWorkDir(workDir, outApk)
        onEvent(
            ProgressEvent.StageChanged(
                Stage.READY_TO_INSTALL,
                context.getString(R.string.full_update_ready_to_install)
            )
        )
        outApk
    }

    private suspend fun resolvePatchChain(
        context: Context,
        patchUrl: String,
        metaUrl: String,
        currentBaseSha: String,
        targetMeta: JSONObject,
        onEvent: (ProgressEvent) -> Unit
    ): List<PatchStep> {
        val targetFormat = targetMeta.optString("format", "")
        if (targetFormat != "apkraw-1") {
            throw IllegalStateException("Unsupported patch format: $targetFormat")
        }

        val targetSha = targetMeta.optString("targetSha256", "").trim().lowercase()
        if (targetSha.isBlank()) {
            throw IllegalStateException("Patch meta missing targetSha256")
        }

        val targetToVersion = targetMeta.optString("toVersion", "").trim()
        val targetToPatchIndex = if (targetMeta.has("toPatchIndex")) targetMeta.optInt("toPatchIndex", -1) else -1

        val repo = parseRepoFromReleaseAssetUrl(metaUrl) ?: parseRepoFromReleaseAssetUrl(patchUrl)
            ?: throw IllegalStateException("Unsupported patch source URL")

        val api = GitHubApiService(context)
        val releases = api.getRepositoryReleases(
            owner = repo.first,
            repo = repo.second,
            page = 1,
            perPage = 100
        ).getOrElse { e ->
            throw IllegalStateException("Failed to query patch releases: ${e.message ?: e.javaClass.simpleName}")
        }

        val stepsByEdge = LinkedHashMap<String, PatchStep>()
        val scannableReleases = releases.filterNot { it.draft }
        if (scannableReleases.isNotEmpty()) {
            onEvent(ProgressEvent.ChainScanProgress(scanned = 0, total = scannableReleases.size))
        }

        val targetCandidate = parsePatchStep(
            meta = targetMeta,
            patchUrl = patchUrl,
            metaUrl = metaUrl,
            tag = targetMeta.optString("tag", "")
        )
        if (targetCandidate != null) {
            val edge = "${targetCandidate.baseSha256}->${targetCandidate.targetSha256}"
            stepsByEdge[edge] = targetCandidate
        }

        var scannedReleases = 0
        for (release in releases) {
            coroutineContext.ensureActive()
            if (release.draft) continue

            scannedReleases += 1
            onEvent(ProgressEvent.ChainScanProgress(scanned = scannedReleases, total = scannableReleases.size))

            val metaAsset =
                release.assets.firstOrNull { it.name.startsWith("patch_") && it.name.endsWith(".json") }
                    ?: release.assets.firstOrNull { it.name.endsWith(".json") }
            val patchAsset =
                release.assets.firstOrNull { it.name.startsWith("apkrawpatch_") && it.name.endsWith(".zip") }
                    ?: release.assets.firstOrNull { it.name.endsWith(".zip") }
            if (metaAsset == null || patchAsset == null) continue

            val meta = parseReleaseBodyAsPatchMeta(release.body) ?: continue
            val candidate = parsePatchStep(
                meta = meta,
                patchUrl = patchAsset.browser_download_url,
                metaUrl = metaAsset.browser_download_url,
                tag = release.tag_name
            ) ?: continue

            val edge = "${candidate.baseSha256}->${candidate.targetSha256}"
            val existing = stepsByEdge[edge]
            if (existing == null) {
                stepsByEdge[edge] = candidate
            } else {
                val replace = candidate.toPatchIndex > existing.toPatchIndex ||
                    (candidate.toPatchIndex == existing.toPatchIndex &&
                        UpdateManager.compareVersions(candidate.targetVersionLabel, existing.targetVersionLabel) > 0)
                if (replace) {
                    stepsByEdge[edge] = candidate
                }
            }
        }

        var currentSha = currentBaseSha.trim().lowercase()
        val chain = mutableListOf<PatchStep>()
        val seenEdges = HashSet<String>()

        while (currentSha != targetSha) {
            coroutineContext.ensureActive()
            val candidates = stepsByEdge.values.filter { step ->
                step.baseSha256 == currentSha &&
                    (targetToVersion.isBlank() || step.toVersion.isBlank() || step.toVersion == targetToVersion) &&
                    (targetToPatchIndex < 0 || step.toPatchIndex < 0 || step.toPatchIndex <= targetToPatchIndex)
            }
            if (candidates.isEmpty()) {
                throw IllegalStateException("No applicable next patch for base sha ${currentSha.take(12)}")
            }

            var selected = candidates.first()
            for (candidate in candidates.drop(1)) {
                val betterByIndex = candidate.toPatchIndex > selected.toPatchIndex
                val betterByVersion =
                    candidate.toPatchIndex == selected.toPatchIndex &&
                        UpdateManager.compareVersions(candidate.targetVersionLabel, selected.targetVersionLabel) > 0
                if (betterByIndex || betterByVersion) {
                    selected = candidate
                }
            }

            val edge = "${selected.baseSha256}->${selected.targetSha256}"
            if (!seenEdges.add(edge)) {
                throw IllegalStateException("Patch chain loop detected")
            }

            chain.add(selected)
            currentSha = selected.targetSha256
            if (chain.size > MAX_CHAIN_STEPS) {
                throw IllegalStateException("Patch chain exceeds $MAX_CHAIN_STEPS steps")
            }
        }

        return chain
    }

    private fun parsePatchStep(
        meta: JSONObject,
        patchUrl: String,
        metaUrl: String,
        tag: String
    ): PatchStep? {
        val format = meta.optString("format", "")
        if (format != "apkraw-1") return null

        val baseSha = meta.optString("baseSha256", "").trim().lowercase()
        val targetSha = meta.optString("targetSha256", "").trim().lowercase()
        if (baseSha.isBlank() || targetSha.isBlank()) return null

        val toVersion = meta.optString("toVersion", "").trim()
        val toPatchIndex = if (meta.has("toPatchIndex")) meta.optInt("toPatchIndex", -1) else -1
        val targetVersionLabel = when {
            tag.isNotBlank() -> tag.removePrefix("v")
            toVersion.isNotBlank() && toPatchIndex >= 0 -> "$toVersion+$toPatchIndex"
            else -> toVersion
        }

        return PatchStep(
            patchUrl = patchUrl,
            metaUrl = metaUrl,
            baseSha256 = baseSha,
            targetSha256 = targetSha,
            toVersion = toVersion,
            toPatchIndex = toPatchIndex,
            targetVersionLabel = targetVersionLabel
        )
    }

    private fun parseRepoFromReleaseAssetUrl(url: String): Pair<String, String>? {
        val match = releaseAssetUrlRegex.find(url.trim()) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    private fun parseReleaseBodyAsPatchMeta(body: String?): JSONObject? {
        val text = body?.trim().orEmpty()
        if (text.isBlank()) {
            return null
        }
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Skip patch release with invalid JSON body", e)
            null
        }
    }

    private fun cleanupPatchWorkDir(workDir: File, keepFile: File) {
        val keep = runCatching { keepFile.canonicalFile }.getOrElse { keepFile.absoluteFile }
        val files = workDir.listFiles() ?: return
        for (f in files) {
            val fc = runCatching { f.canonicalFile }.getOrElse { f.absoluteFile }
            if (fc.path == keep.path) continue
            runCatching {
                if (f.isDirectory) {
                    f.deleteRecursively()
                } else {
                    f.delete()
                }
            }.onFailure { e ->
                AppLogger.w(TAG, "Failed to cleanup patch work file: ${f.absolutePath}", e)
            }
        }
    }


    private fun prepareCleanWorkDir(context: Context): File {
        val workDir = File(context.cacheDir, WORK_DIR_NAME)
        if (workDir.exists()) {
            runCatching { workDir.deleteRecursively() }
        }
        workDir.mkdirs()
        return workDir
    }

    private fun selectMirrorUrl(originalUrl: String, mirrorKey: String): String {
        if (mirrorKey == "GitHub") return originalUrl
        val mirrors = GithubReleaseUtil.getMirroredUrls(originalUrl)
        return mirrors[mirrorKey] ?: originalUrl
    }

    private suspend fun selectFastestMirrorKeyWithProgress(
        patchUrl: String,
        metaUrl: String,
        onEvent: (ProgressEvent) -> Unit
    ): String {
        val patchMirrors = GithubReleaseUtil.getMirroredUrls(patchUrl)
        val metaMirrors = GithubReleaseUtil.getMirroredUrls(metaUrl)

        val keys = LinkedHashSet<String>().apply {
            addAll(patchMirrors.keys)
            addAll(metaMirrors.keys)
            add("GitHub")
        }

        onEvent(ProgressEvent.MirrorProbeStarted(keys.size))

        val patchProbeMap = keys.associateWith { key ->
            if (key == "GitHub") patchUrl else patchMirrors[key] ?: patchUrl
        }
        val metaProbeMap = keys.associateWith { key ->
            if (key == "GitHub") metaUrl else metaMirrors[key] ?: metaUrl
        }

        val patchResults = GithubReleaseUtil.probeMirrorUrls(patchProbeMap, timeoutMs = PROBE_TIMEOUT_MS)
        val metaResults = GithubReleaseUtil.probeMirrorUrls(metaProbeMap, timeoutMs = PROBE_TIMEOUT_MS)

        var completed = 0
        var bestKey: String? = null
        var bestSpeed = Long.MIN_VALUE
        var bestLatency = Long.MAX_VALUE

        for (key in keys) {
            coroutineContext.ensureActive()
            val patchRes = patchResults[key]
            val metaRes = metaResults[key]

            val ok = patchRes?.ok == true && metaRes?.ok == true
            val speed =
                if (patchRes?.bytesPerSec != null && metaRes?.bytesPerSec != null) {
                    min(patchRes.bytesPerSec!!, metaRes.bytesPerSec!!)
                } else {
                    null
                }
            val latency =
                when {
                    patchRes?.latencyMs != null && metaRes?.latencyMs != null ->
                        maxOf(patchRes.latencyMs, metaRes.latencyMs)
                    patchRes?.latencyMs != null -> patchRes.latencyMs
                    metaRes?.latencyMs != null -> metaRes.latencyMs
                    else -> null
                }
            val error =
                when {
                    patchRes?.ok != true -> patchRes?.error ?: "PATCH_PROBE_FAILED"
                    metaRes?.ok != true -> metaRes?.error ?: "META_PROBE_FAILED"
                    else -> null
                }

            completed += 1
            val summary = MirrorProbeSummary(ok = ok, latencyMs = latency, speedBytesPerSec = speed, error = error)
            onEvent(ProgressEvent.MirrorProbeResult(name = key, summary = summary, completed = completed, total = keys.size))

            if (ok) {
                val speedValue = speed ?: -1L
                val latencyValue = latency ?: Long.MAX_VALUE
                if (speed != null) {
                    if (speedValue > bestSpeed || (speedValue == bestSpeed && latencyValue < bestLatency)) {
                        bestKey = key
                        bestSpeed = speedValue
                        bestLatency = latencyValue
                    }
                } else if (bestKey == null) {
                    bestKey = key
                    bestLatency = latencyValue
                }
            }
        }

        return bestKey ?: "GitHub"
    }

    private suspend fun downloadToFile(
        url: String,
        out: File,
        label: String,
        multiThread: Boolean = false,
        onEvent: (ProgressEvent) -> Unit
    ) {
        if (multiThread) {
            downloadToFileMultiThread(url = url, out = out, label = label, onEvent = onEvent)
            return
        }
        downloadToFileSingleThread(url = url, out = out, label = label, onEvent = onEvent)
    }

    private suspend fun downloadToFileSingleThread(
        url: String,
        out: File,
        label: String,
        onEvent: (ProgressEvent) -> Unit
    ) {
        val req = Request.Builder().url(url).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}: ${resp.message}")
            }
            val body = resp.body ?: throw IllegalStateException("Empty response body")
            val total = body.contentLength().takeIf { it > 0 }
            out.parentFile?.mkdirs()
            if (out.exists()) {
                runCatching { out.delete() }
            }

            val buf = ByteArray(128 * 1024)
            var readTotal = 0L
            var lastNotifyAt = 0L
            var lastSpeedSampleAt = 0L
            var lastSpeedSampleBytes = 0L
            var speedBytesPerSec = 0L

            onEvent(
                ProgressEvent.DownloadProgress(
                    label = label,
                    readBytes = 0L,
                    totalBytes = total,
                    speedBytesPerSec = speedBytesPerSec
                )
            )

            FileOutputStream(out).use { fos ->
                body.byteStream().use { ins ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = ins.read(buf)
                        if (n <= 0) break
                        fos.write(buf, 0, n)
                        readTotal += n.toLong()

                        val now = System.currentTimeMillis()
                        if (lastSpeedSampleAt == 0L) {
                            lastSpeedSampleAt = now
                            lastSpeedSampleBytes = readTotal
                        } else {
                            val elapsed = now - lastSpeedSampleAt
                            if (elapsed >= 300) {
                                val delta = readTotal - lastSpeedSampleBytes
                                if (delta >= 0) {
                                    speedBytesPerSec = (delta * 1000L) / elapsed.coerceAtLeast(1L)
                                }
                                lastSpeedSampleAt = now
                                lastSpeedSampleBytes = readTotal
                            }
                        }

                        if (now - lastNotifyAt >= 300) {
                            lastNotifyAt = now
                            onEvent(
                                ProgressEvent.DownloadProgress(
                                    label = label,
                                    readBytes = readTotal,
                                    totalBytes = total,
                                    speedBytesPerSec = speedBytesPerSec
                                )
                            )
                        }
                    }
                }
            }

            onEvent(
                ProgressEvent.DownloadProgress(
                    label = label,
                    readBytes = readTotal,
                    totalBytes = total,
                    speedBytesPerSec = speedBytesPerSec
                )
            )
        }
    }

    private suspend fun downloadToFileMultiThread(
        url: String,
        out: File,
        label: String,
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
                label = label,
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
                            label = label,
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
                val ranges = splitRanges(totalBytes, PATCH_DOWNLOAD_THREAD_COUNT)
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
                label = label,
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
                throw IllegalStateException("Server must provide a valid Content-Length for 6-thread patch download")
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
                throw IllegalStateException("Server does not support HTTP Range (required for 6-thread patch download)")
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

    private fun applyApkrawPatch(baseApk: File, patchZip: File, meta: JSONObject, outApk: File) {
        val entriesJson = meta.optJSONArray("apkRawEntries") ?: JSONArray()
        val tailName = meta.optString("apkRawTailFile", "tail.bin")

        val baseMap = RandomAccessFile(baseApk, "r").use { raf ->
            readCentralDirectory(raf)
        }

        if (outApk.exists()) outApk.delete()
        outApk.parentFile?.mkdirs()

        val md = MessageDigest.getInstance("SHA-256")
        DigestOutputStream(BufferedOutputStream(FileOutputStream(outApk)), md).use { dout ->
            RandomAccessFile(baseApk, "r").use { raf ->
                ZipFile(patchZip).use { pz ->
                    for (i in 0 until entriesJson.length()) {
                        val ent = entriesJson.getJSONObject(i)
                        val name = ent.optString("name", "")
                        val mode = ent.optString("mode", "")
                        if (name.isBlank()) throw IllegalStateException("Bad apkRawEntries")

                        if (mode == "copy") {
                            val cd = baseMap[name] ?: throw IllegalStateException("Base apk missing entry: $name")
                            copyLocalRecord(raf, cd, dout)
                        } else if (mode == "add") {
                            val recordPath = ent.optString("recordPath", "")
                            if (recordPath.isBlank()) throw IllegalStateException("apkraw add missing recordPath")
                            val ze = pz.getEntry(recordPath) ?: throw IllegalStateException("patch zip missing $recordPath")
                            pz.getInputStream(ze).use { ins ->
                                ins.copyTo(dout)
                            }
                        } else {
                            throw IllegalStateException("Bad apkraw mode: $mode")
                        }
                    }

                    val tailEntry = pz.getEntry(tailName) ?: throw IllegalStateException("patch zip missing $tailName")
                    pz.getInputStream(tailEntry).use { ins ->
                        ins.copyTo(dout)
                    }
                }
            }
            dout.flush()
        }
    }

    private data class CdEntry(
        val localHeaderOffset: Long,
        val compressedSize: Long
    )

    private fun readCentralDirectory(raf: RandomAccessFile): Map<String, CdEntry> {
        val fileLen = raf.length()
        val readLen = min(65557L, fileLen).toInt()
        val buf = ByteArray(readLen)
        raf.seek(fileLen - readLen)
        raf.readFully(buf)

        var eocdIndex = -1
        for (i in readLen - 22 downTo 0) {
            if (buf[i] == 0x50.toByte() &&
                buf[i + 1] == 0x4b.toByte() &&
                buf[i + 2] == 0x05.toByte() &&
                buf[i + 3] == 0x06.toByte()
            ) {
                eocdIndex = i
                break
            }
        }
        if (eocdIndex < 0) throw IllegalStateException("EOCD not found")

        val eocdOffset = (fileLen - readLen + eocdIndex).toLong()
        raf.seek(eocdOffset)
        val eocd = ByteArray(22)
        raf.readFully(eocd)

        val totalEntries = readUShortLE(eocd, 10)
        val cdOffset = readUIntLE(eocd, 16)

        raf.seek(cdOffset)
        val out = LinkedHashMap<String, CdEntry>(totalEntries)
        repeat(totalEntries) {
            val hdr = ByteArray(46)
            raf.readFully(hdr)
            val sig = readUIntLE(hdr, 0)
            if (sig != 0x02014b50L) throw IllegalStateException("Bad central directory signature")

            val flags = readUShortLE(hdr, 8)
            val compressedSize = readUIntLE(hdr, 20)
            val fileNameLen = readUShortLE(hdr, 28)
            val extraLen = readUShortLE(hdr, 30)
            val commentLen = readUShortLE(hdr, 32)
            val localHeaderOffset = readUIntLE(hdr, 42)

            val nameBytes = ByteArray(fileNameLen)
            raf.readFully(nameBytes)
            val charset = if ((flags and 0x800) != 0) Charsets.UTF_8 else Charsets.ISO_8859_1
            val name = String(nameBytes, charset)

            if (extraLen > 0) raf.skipBytes(extraLen)
            if (commentLen > 0) raf.skipBytes(commentLen)

            if (!name.endsWith("/")) {
                out[name] = CdEntry(localHeaderOffset = localHeaderOffset, compressedSize = compressedSize)
            }
        }

        return out
    }

    private fun copyLocalRecord(raf: RandomAccessFile, cd: CdEntry, out: java.io.OutputStream) {
        raf.seek(cd.localHeaderOffset)

        val lfh = ByteArray(30)
        raf.readFully(lfh)
        val sig = readUIntLE(lfh, 0)
        if (sig != 0x04034b50L) throw IllegalStateException("Bad local header signature")

        val flags = readUShortLE(lfh, 6)
        val fileNameLen = readUShortLE(lfh, 26)
        val extraLen = readUShortLE(lfh, 28)

        out.write(lfh)

        if (fileNameLen + extraLen > 0) {
            val nameExtra = ByteArray(fileNameLen + extraLen)
            raf.readFully(nameExtra)
            out.write(nameExtra)
        }

        copyBytes(raf, out, cd.compressedSize)

        if ((flags and 0x08) != 0) {
            val first4 = ByteArray(4)
            raf.readFully(first4)
            out.write(first4)
            val ddSig = readUIntLE(first4, 0)
            if (ddSig == 0x08074b50L) {
                val rest = ByteArray(12)
                raf.readFully(rest)
                out.write(rest)
            } else {
                val rest = ByteArray(8)
                raf.readFully(rest)
                out.write(rest)
            }
        }
    }

    private fun copyBytes(raf: RandomAccessFile, out: java.io.OutputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(1024 * 1024)
        while (remaining > 0) {
            val toRead = min(remaining, buffer.size.toLong()).toInt()
            val read = raf.read(buffer, 0, toRead)
            if (read <= 0) throw IllegalStateException("Unexpected EOF")
            out.write(buffer, 0, read)
            remaining -= read.toLong()
        }
    }

    private fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val r = ins.read(buf)
                if (r <= 0) break
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun readUShortLE(buf: ByteArray, off: Int): Int {
        return (buf[off].toInt() and 0xff) or ((buf[off + 1].toInt() and 0xff) shl 8)
    }

    private fun readUIntLE(buf: ByteArray, off: Int): Long {
        return (buf[off].toLong() and 0xff) or
            ((buf[off + 1].toLong() and 0xff) shl 8) or
            ((buf[off + 2].toLong() and 0xff) shl 16) or
            ((buf[off + 3].toLong() and 0xff) shl 24)
    }
}

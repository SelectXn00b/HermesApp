package com.xiaomo.androidforclaw.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * App Auto-Updater
 *
 * Checks GitHub Releases for new versions and handles download + install.
 *
 * Flow:
 * 1. checkForUpdate() → queries GitHub API for latest release
 * 2. Compares version with current app version
 * 3. If newer: downloadAndInstall() → DownloadManager + install intent
 *
 * GitHub Release URL: https://github.com/SelectXn00b/AndroidForClaw/releases
 * API: https://api.github.com/repos/SelectXn00b/AndroidForClaw/releases/latest
 */
class AppUpdater(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdater"

        const val GITHUB_OWNER = "SelectXn00b"
        const val GITHUB_REPO = "AndroidForClaw"
        const val GITHUB_RELEASES_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"
        const val GITHUB_API_LATEST = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

        // APK asset name pattern
        const val APK_NAME_PREFIX = "AndroidForClaw"
        const val APK_NAME_SUFFIX = "-release.apk"

        private const val TIMEOUT_SECONDS = 30L
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Update check result
     */
    data class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val currentVersion: String,
        val downloadUrl: String? = null,
        val releaseNotes: String? = null,
        val releaseUrl: String = GITHUB_RELEASES_URL,
        val fileSize: Long = 0,
        val publishedAt: String? = null
    )

    /**
     * Check GitHub for the latest release
     */
    suspend fun checkForUpdate(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val currentVersion = getCurrentVersion()
            Log.d(TAG, "Current version: $currentVersion")

            val request = Request.Builder()
                .url(GITHUB_API_LATEST)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "AndroidForClaw/$currentVersion")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub API returned ${response.code}, trying redirect fallback")
                return@withContext checkViaRedirect(currentVersion)
            }

            val json = JSONObject(response.body?.string() ?: "{}")
            val tagName = json.optString("tag_name", "").removePrefix("v")
            val releaseNotes = json.optString("body", "").take(500)
            val publishedAt = json.optString("published_at", "")
            val htmlUrl = json.optString("html_url", GITHUB_RELEASES_URL)

            // Find the release APK asset
            var downloadUrl: String? = null
            var fileSize: Long = 0
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.startsWith(APK_NAME_PREFIX) && name.endsWith(APK_NAME_SUFFIX)) {
                        downloadUrl = asset.optString("browser_download_url")
                        fileSize = asset.optLong("size", 0)
                        break
                    }
                }
            }

            val hasUpdate = isNewerVersion(tagName, currentVersion)
            Log.d(TAG, "Latest: $tagName, Current: $currentVersion, hasUpdate: $hasUpdate")

            UpdateInfo(
                hasUpdate = hasUpdate,
                latestVersion = tagName,
                currentVersion = currentVersion,
                downloadUrl = downloadUrl,
                releaseNotes = releaseNotes,
                releaseUrl = htmlUrl,
                fileSize = fileSize,
                publishedAt = publishedAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            val currentVersion = getCurrentVersion()
            UpdateInfo(
                hasUpdate = false,
                latestVersion = currentVersion,
                currentVersion = currentVersion
            )
        }
    }

    /**
     * Download APK via DownloadManager and trigger install
     */
    suspend fun downloadAndInstall(downloadUrl: String, version: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileName = "${APK_NAME_PREFIX}-v${version}${APK_NAME_SUFFIX}"
            Log.d(TAG, "Downloading: $downloadUrl → $fileName")

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("AndroidForClaw v$version")
                setDescription("正在下载更新...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setMimeType("application/vnd.android.package-archive")
            }

            val downloadId = downloadManager.enqueue(request)
            Log.d(TAG, "Download started, id: $downloadId")

            // Wait for download completion
            val success = waitForDownload(downloadId)
            if (!success) {
                Log.e(TAG, "Download failed")
                return@withContext false
            }

            // Trigger install
            val apkFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            installApk(apkFile)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download and install failed", e)
            false
        }
    }

    /**
     * Wait for DownloadManager to complete
     */
    private suspend fun waitForDownload(downloadId: Long): Boolean = suspendCancellableCoroutine { cont ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    context.unregisterReceiver(this)
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = dm.query(query)
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        cursor.close()
                        if (cont.isActive) cont.resume(status == DownloadManager.STATUS_SUCCESSFUL)
                    } else {
                        cursor.close()
                        if (cont.isActive) cont.resume(false)
                    }
                }
            }
        }

        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED
        )

        cont.invokeOnCancellation {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    /**
     * Trigger APK install via intent
     */
    private fun installApk(apkFile: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(intent)
        Log.d(TAG, "Install intent launched for: ${apkFile.absolutePath}")
    }

    /**
     * Get current app version name
     */
    /**
     * Fallback: check version via GitHub releases/latest 302 redirect.
     * No API rate limit — just follows the redirect URL to extract tag name.
     * Download URL constructed from tag name.
     */
    private fun checkViaRedirect(currentVersion: String): UpdateInfo {
        try {
            val noRedirectClient = httpClient.newBuilder().followRedirects(false).build()
            val request = Request.Builder()
                .url("$GITHUB_RELEASES_URL/latest")
                .header("User-Agent", "AndroidForClaw/$currentVersion")
                .build()

            val response = noRedirectClient.newCall(request).execute()
            val location = response.header("Location") ?: response.header("location")
            response.close()

            if (location != null && location.contains("/tag/")) {
                val tagName = location.substringAfterLast("/tag/").removePrefix("v")
                val hasUpdate = isNewerVersion(tagName, currentVersion)
                Log.d(TAG, "Redirect fallback: latest=$tagName, current=$currentVersion, hasUpdate=$hasUpdate")

                val downloadUrl = if (hasUpdate) {
                    "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/v$tagName/${APK_NAME_PREFIX}-v${tagName}${APK_NAME_SUFFIX}"
                } else null

                return UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = tagName,
                    currentVersion = currentVersion,
                    downloadUrl = downloadUrl,
                    releaseUrl = location
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Redirect fallback also failed", e)
        }

        return UpdateInfo(hasUpdate = false, latestVersion = currentVersion, currentVersion = currentVersion)
    }

    fun getCurrentVersion(): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "0.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "0.0.0"
        }
    }

    /**
     * Compare semantic versions (e.g. "1.0.3" > "1.0.2")
     */
    fun isNewerVersion(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLen = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            return false
        } catch (e: Exception) {
            Log.w(TAG, "Version comparison failed: $latest vs $current", e)
            return false
        }
    }
}

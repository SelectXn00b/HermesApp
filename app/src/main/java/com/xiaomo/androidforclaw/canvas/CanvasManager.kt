/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/canvas-host/server.ts
 * - ../openclaw/src/gateway/canvas-capability.ts
 *
 * AndroidForClaw adaptation: Canvas WebView manager singleton.
 */
package com.xiaomo.androidforclaw.canvas

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import com.xiaomo.androidforclaw.logging.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import com.xiaomo.androidforclaw.workspace.StoragePaths
import java.io.File

/**
 * Canvas 管理器 — 管理 Canvas WebView 的状态和操作。
 *
 * Agent 通过 CanvasTool 调用此 manager 来控制 Canvas：
 * - present: 启动 CanvasActivity 并加载指定 URL/文件
 * - hide: 关闭 CanvasActivity
 * - navigate: 导航到新 URL
 * - eval: 执行 JavaScript 并返回结果
 * - snapshot: 截图返回 base64
 */
object CanvasManager {
    private const val TAG = "CanvasManager"

    /** Canvas 根目录 */
    private val CANVAS_ROOT = StoragePaths.canvas.absolutePath

    /** 当前 CanvasActivity 实例（弱引用，Activity 销毁时自动清空） */
    @Volatile
    var currentActivity: CanvasActivity? = null
        internal set

    /**
     * Screen tab 内嵌的 CanvasController 引用（由 MainActivityCompose 设置）。
     * CanvasTool 优先走此路径，在 Screen tab 的 WebView 中渲染，
     * 而不是启动独立的 CanvasActivity。
     */
    @Volatile
    var screenTabController: ai.openclaw.app.node.CanvasController? = null

    /** pending eval 请求 */
    private val pendingEvals = mutableMapOf<String, CompletableDeferred<String?>>()

    /** pending snapshot 请求 */
    private val pendingSnapshots = mutableMapOf<String, CompletableDeferred<SnapshotResult>>()

    /**
     * 获取 canvas 根目录，不存在则创建
     */
    fun getCanvasRoot(): File {
        val dir = File(CANVAS_ROOT)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * present — 显示 Canvas，加载指定 URL 或本地文件
     */
    fun present(context: Context, url: String? = null, placement: Map<String, Int>? = null) {
        val intent = Intent(context, CanvasActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (url != null) {
                putExtra(CanvasActivity.EXTRA_URL, resolveUrl(url))
            }
        }
        context.startActivity(intent)
        Log.i(TAG, "canvas.present url=$url")
    }

    /**
     * hide — 关闭 Canvas
     */
    fun hide() {
        currentActivity?.finish()
        currentActivity = null
        Log.i(TAG, "canvas.hide")
    }

    /**
     * navigate — 导航到新 URL
     */
    fun navigate(url: String) {
        val resolved = resolveUrl(url)
        val activity = currentActivity
        if (activity != null) {
            activity.runOnUiThread { activity.loadUrl(resolved) }
            Log.i(TAG, "canvas.navigate url=$resolved")
        } else {
            Log.w(TAG, "canvas.navigate: no active CanvasActivity")
        }
    }

    /**
     * eval — 执行 JavaScript，返回结果字符串
     */
    suspend fun eval(javaScript: String, timeoutMs: Long = 10_000): String? {
        val activity = currentActivity
            ?: throw IllegalStateException("No active Canvas to evaluate JavaScript")

        val id = java.util.UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String?>()
        synchronized(pendingEvals) { pendingEvals[id] = deferred }

        activity.runOnUiThread { activity.evaluateJavaScript(id, javaScript) }

        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } finally {
            synchronized(pendingEvals) { pendingEvals.remove(id) }
        }
    }

    /**
     * 由 CanvasActivity 回调 eval 结果
     */
    internal fun onEvalResult(id: String, result: String?) {
        synchronized(pendingEvals) { pendingEvals[id]?.complete(result) }
    }

    /**
     * snapshot — 截取 WebView 截图
     */
    suspend fun snapshot(
        format: String = "png",
        maxWidth: Int? = null,
        quality: Int = 90,
        timeoutMs: Long = 15_000
    ): SnapshotResult {
        val activity = currentActivity
            ?: throw IllegalStateException("No active Canvas to snapshot")

        val id = java.util.UUID.randomUUID().toString()
        val deferred = CompletableDeferred<SnapshotResult>()
        synchronized(pendingSnapshots) { pendingSnapshots[id] = deferred }

        activity.runOnUiThread { activity.takeSnapshot(id, format, maxWidth, quality) }

        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } finally {
            synchronized(pendingSnapshots) { pendingSnapshots.remove(id) }
        }
    }

    /**
     * 由 CanvasActivity 回调 snapshot 结果
     */
    internal fun onSnapshotResult(id: String, result: SnapshotResult) {
        synchronized(pendingSnapshots) { pendingSnapshots[id]?.complete(result) }
    }

    /**
     * 公开的 URL 解析方法，供 CanvasTool 等外部使用
     */
    fun resolveUrlPublic(url: String): String = resolveUrl(url)

    /**
     * 解析 URL — 支持本地文件路径、http(s) URL
     */
    private fun resolveUrl(url: String): String {
        // 已是完整 URL
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://")) {
            return url
        }
        // 绝对路径
        if (url.startsWith("/")) {
            return "file://$url"
        }
        // 相对路径 → canvas 根目录
        val file = File(getCanvasRoot(), url)
        return "file://${file.absolutePath}"
    }

    data class SnapshotResult(
        val base64: String,
        val format: String,
        val width: Int,
        val height: Int
    )
}

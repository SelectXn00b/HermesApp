/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/browser/(all)
 *
 * AndroidForClaw adaptation: browser tool client.
 */
package com.forclaw.browser.control.manager

import android.webkit.ValueCallback
import info.plateaukao.einkbro.activity.BrowserActivity
import info.plateaukao.einkbro.view.EBWebView
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 浏览器管理器
 *
 * 职责:
 * - 管理 BrowserActivity 实例
 * - 提供 JavaScript 执行接口
 * - 提供导航控制接口
 * - 确保 UI 线程安全
 */
object BrowserManager {

    private var browserActivity: BrowserActivity? = null

    /**
     * 设置当前 BrowserActivity 实例
     *
     * 应该在 BrowserActivity.onCreate() 中调用
     */
    fun setBrowserActivity(activity: BrowserActivity?) {
        browserActivity = activity
    }

    /**
     * 获取当前 BrowserActivity 实例
     */
    fun getBrowserActivity(): BrowserActivity? = browserActivity

    /**
     * 获取当前活动的 WebView
     */
    private fun getCurrentWebView(): EBWebView? {
        return browserActivity?.getCurrentAlbumController() as? EBWebView
    }

    /**
     * 在 UI 线程执行操作
     *
     * @param action 要执行的操作
     */
    private fun runOnUiThread(action: (BrowserActivity) -> Unit) {
        val activity = browserActivity ?: return
        activity.runOnUiThread {
            action(activity)
        }
    }

    /**
     * 执行 JavaScript 代码
     *
     * @param script JavaScript 代码
     * @return 执行结果 (JSON 字符串)，如果失败返回 null
     */
    suspend fun evaluateJavascript(script: String): String? {
        return suspendCoroutine { continuation ->
            val webView = getCurrentWebView()
            if (webView == null) {
                continuation.resume(null)
                return@suspendCoroutine
            }

            runOnUiThread { _ ->
                webView.evaluateJavascript(script, ValueCallback { result ->
                    continuation.resume(result)
                })
            }
        }
    }

    /**
     * 导航到指定 URL
     *
     * @param url 目标 URL
     */
    fun navigate(url: String) {
        runOnUiThread { _ ->
            val webView = getCurrentWebView()
            webView?.loadUrl(url)
        }
    }

    /**
     * 获取当前页面 URL
     *
     * @return 当前 URL，如果没有活动页面返回 null
     */
    fun getCurrentUrl(): String? {
        return browserActivity?.getCurrentAlbumController()?.albumUrl
    }

    /**
     * 获取当前页面标题
     *
     * @return 当前标题，如果没有活动页面返回 null
     */
    fun getCurrentTitle(): String? {
        return browserActivity?.getCurrentAlbumController()?.albumTitle
    }

    /**
     * 检查是否有活动的浏览器实例
     *
     * @return true 如果有活动实例
     */
    fun isActive(): Boolean {
        return browserActivity != null && getCurrentWebView() != null
    }
}

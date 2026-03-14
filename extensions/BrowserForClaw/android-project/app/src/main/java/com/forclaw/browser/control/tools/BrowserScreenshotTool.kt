/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/browser/(all)
 *
 * AndroidForClaw adaptation: browser tool client.
 */
package com.forclaw.browser.control.tools

import android.graphics.Bitmap
import android.util.Base64
import com.forclaw.browser.control.manager.BrowserManager
import com.forclaw.browser.control.model.ToolResult
import info.plateaukao.einkbro.view.EBWebView
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 浏览器截图工具
 *
 * 截取当前页面的图片
 *
 * 参数:
 * - fullPage: Boolean (可选) - 是否全页截图，默认 false
 * - format: String (可选) - 图片格式 "png" 或 "jpeg"，默认 "png"
 * - quality: Int (可选) - JPEG 质量 0-100，默认 80
 * - waitForStable: Boolean (可选) - 是否等待页面稳定，默认 true
 * - stabilityTimeout: Int (可选) - 等待稳定超时时间（毫秒），默认 3000
 *
 * 返回:
 * - screenshot: String - Base64 编码的图片数据
 * - width: Int - 图片宽度
 * - height: Int - 图片高度
 * - format: String - 图片格式
 */
class BrowserScreenshotTool : BrowserTool {
    override val name = "browser_screenshot"

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        // 1. 获取参数
        val fullPage = (args["fullPage"] as? Boolean) ?: false
        val format = (args["format"] as? String)?.lowercase() ?: "png"
        val quality = (args["quality"] as? Number)?.toInt() ?: 80
        val waitForStable = (args["waitForStable"] as? Boolean) ?: true
        val stabilityTimeout = (args["stabilityTimeout"] as? Number)?.toLong() ?: 3000L

        // 验证 format
        if (format !in listOf("png", "jpeg", "jpg")) {
            return ToolResult.error("Invalid format: $format (must be 'png' or 'jpeg')")
        }

        // 验证 quality
        if (quality !in 0..100) {
            return ToolResult.error("Invalid quality: $quality (must be 0-100)")
        }

        // 2. 检查浏览器实例
        if (!BrowserManager.isActive()) {
            return ToolResult.error("Browser is not active")
        }

        // 3. 等待页面稳定
        if (waitForStable) {
            try {
                waitForPageStable(stabilityTimeout)
            } catch (e: Exception) {
                // 等待超时也继续截图
            }
        }

        // 4. 截图
        try {
            val bitmap = captureScreenshot(fullPage)

            // 4. 转换为 Base64
            val outputStream = ByteArrayOutputStream()
            val compressFormat = if (format == "png") {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }

            bitmap.compress(compressFormat, quality, outputStream)
            val imageBytes = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            // 5. 返回结果
            return ToolResult.success(
                "screenshot" to base64Image,
                "width" to bitmap.width,
                "height" to bitmap.height,
                "format" to format,
                "size" to imageBytes.size
            )
        } catch (e: Exception) {
            return ToolResult.error("Screenshot failed: ${e.message}")
        }
    }

    /**
     * 截取屏幕图片
     */
    private suspend fun captureScreenshot(fullPage: Boolean): Bitmap {
        return suspendCoroutine { continuation ->
            val activity = BrowserManager.getBrowserActivity()
            val webView = activity?.getCurrentAlbumController() as? EBWebView

            if (webView == null) {
                throw Exception("WebView not available")
            }

            activity.runOnUiThread {
                try {
                    val bitmap = if (fullPage) {
                        // 全页截图
                        captureFullPage(webView)
                    } else {
                        // 当前可见区域截图
                        captureVisibleArea(webView)
                    }
                    continuation.resume(bitmap)
                } catch (e: Exception) {
                    throw e
                }
            }
        }
    }

    /**
     * 截取可见区域
     */
    private fun captureVisibleArea(webView: EBWebView): Bitmap {
        val bitmap = Bitmap.createBitmap(
            webView.width,
            webView.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        webView.draw(canvas)
        return bitmap
    }

    /**
     * 截取全页
     */
    private fun captureFullPage(webView: EBWebView): Bitmap {
        // 获取完整内容的高度
        val contentHeight = webView.contentHeight
        val scale = webView.scale

        val height = (contentHeight * scale).toInt()
        val width = webView.width

        // 创建大位图
        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = android.graphics.Canvas(bitmap)

        // 保存当前滚动位置
        val originalScrollY = webView.scrollY

        // 绘制整个页面
        webView.scrollTo(0, 0)
        webView.draw(canvas)

        // 恢复滚动位置
        webView.scrollTo(0, originalScrollY)

        return bitmap
    }

    /**
     * 等待页面稳定
     * 通过检查页面加载状态和内容是否变化来判断
     */
    private suspend fun waitForPageStable(timeout: Long) {
        val startTime = System.currentTimeMillis()

        // 检查页面加载状态
        val loadCheckScript = """
            (function() {
                return document.readyState === 'complete';
            })()
        """.trimIndent()

        while (System.currentTimeMillis() - startTime < timeout) {
            try {
                // 必须在主线程执行 evaluateJavascript
                val result = withContext(Dispatchers.Main) {
                    BrowserManager.evaluateJavascript(loadCheckScript)
                }
                if (result?.trim() == "true") {
                    // 页面加载完成，额外等待 500ms 让动态内容渲染
                    kotlinx.coroutines.delay(500)
                    return
                }
            } catch (e: Exception) {
                // 继续等待
            }
            kotlinx.coroutines.delay(200)
        }
    }
}

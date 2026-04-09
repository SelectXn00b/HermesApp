/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.androidforclaw

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import com.xiaomo.androidforclaw.logging.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.BitmapFactory
import com.xiaomo.androidforclaw.accessibility.AccessibilityProxy
import com.xiaomo.androidforclaw.accessibility.ShizukuManager
import com.xiaomo.androidforclaw.accessibility.service.ViewNode
import com.xiaomo.androidforclaw.accessibility.service.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object DeviceController {
    private val TAG = "agent1Controller"

    /** 检查 Shizuku 是否可用且已授权（可选能力，后台命令操作） */
    fun isShizukuReady(): Boolean = ShizukuManager.isReady


    /** Capture a screenshot of the device via AccessibilityProxy */
    val workPath = "/sdcard/Download/agent/"
    fun getScreenshot(context: Context): Pair<Bitmap, String>? {
        return runBlocking {
            try {
                val uriString = AccessibilityProxy.captureScreen()
                if (uriString.isEmpty()) {
                    Log.w(TAG, "截图失败：URI 为空")
                    return@runBlocking null
                }

                Log.d(TAG, "Got screenshot URI: $uriString")

                // 尝试作为 Content URI 解码
                val bitmap = try {
                    if (uriString.startsWith("content://")) {
                        val uri = android.net.Uri.parse(uriString)
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            BitmapFactory.decodeStream(inputStream)
                        }
                    } else {
                        // 回退到文件路径
                        BitmapFactory.decodeFile(uriString)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode from URI/path: $uriString", e)
                    null
                }

                if (bitmap != null) {
                    Pair(bitmap, uriString)
                } else {
                    Log.e(TAG, "无法解码截图: $uriString")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "截图失败", e)
                null
            }
        }
    }

    // 判断当前启用的输入法是否是 ADB Keyboard
    fun isClawKeyboardActive(context: Context): Boolean {
        val currentInputMethod = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        // 3. 检查ADB输入法是否在启用列表中
        val adbInputMethodName =
            "${context.packageName}/com.xiaomo.androidforclaw.service.ClawIME" // ADB输入法的名称，根据实际情况修改
        return currentInputMethod == adbInputMethodName || currentInputMethod.contains("adbkeyboard")
    }

    // 判断当前焦点是否在输入框上
    fun findFocusedEditText(service: AccessibilityService): AccessibilityNodeInfo? {
        val rootNode = service.rootInActiveWindow ?: return null
        return rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    }


    // 综合判断：是否 ADB 键盘 + 焦点在输入框
    fun isAdbKeyboardVisible(service: AccessibilityService, context: Context): Boolean {
        val focusedNode = findFocusedEditText(service)
        val isClawIme = isClawKeyboardActive(context)
        Log.d("ADB键盘判断", "是否焦点在EditText: ${focusedNode != null}")
        return focusedNode != null && isClawIme
    }


    // todo 截屏和抓tree 放到一起 delay合并到一起
    fun detectIcons(context: Context): Pair<List<ViewNode>, List<ViewNode>>? {
        // 检查无障碍服务是否连接
        if (!AccessibilityProxy.isServiceReady()) {
            Log.w(TAG, "无障碍服务未就绪")
            return null
        }

        return runBlocking {
            try {
                Log.d(TAG, "detectIcons: dumpView via AIDL")
                var dumpView = AccessibilityProxy.dumpViewTree(useCache = false)

                // 最多重试 3 次
                var retryCount = 0
                while (dumpView.isEmpty() && retryCount < 3) {
                    Log.d(TAG, "detectIcons: retry $retryCount")
                    Thread.sleep(500)
                    dumpView = AccessibilityProxy.dumpViewTree(useCache = false)
                    retryCount++
                }

                if (dumpView.isEmpty()) {
                    Log.w(TAG, "无法获取 UI 树（已重试 $retryCount 次）")
                    return@runBlocking null
                }

                // 克隆原始数据
                val originalNodes = dumpView.map { it.copy() }

                // 经过完整处理的数据
                val processedNodes = processHierarchy(dumpView)

                Pair(originalNodes, processedNodes)
            } catch (e: Exception) {
                Log.e(TAG, "获取 UI 树失败", e)
                null
            }
        }
    }


    /**
     * Remove nodes that have no useful information at all:
     * no text, no contentDesc, no resourceId, not clickable, not scrollable.
     * Keep nodes that have resourceId or are interactive — they're useful even without text.
     */
    fun removeEmptyNodes(nodes: List<ViewNode>): List<ViewNode> {
        return nodes.filter {
            !it.text.isNullOrEmpty() ||
            !it.contentDesc.isNullOrEmpty() ||
            !it.resourceId.isNullOrEmpty() ||
            it.clickable ||
            it.scrollable
        }
    }

    fun filterDuplicateNodes(nodes: List<ViewNode>): List<ViewNode> {
        val seenKeys = mutableSetOf<String>()
        val result = mutableListOf<ViewNode>()
        nodes.forEach { node ->
            // Use text+coords as dedup key, not just text
            val label = node.text ?: node.contentDesc ?: node.resourceId ?: ""
            val key = "${label}@${node.point.x},${node.point.y}"
            if (key !in seenKeys) {
                seenKeys.add(key)
                result.add(node)
            } else if (node.clickable) {
                // Keep clickable duplicates (e.g., list items with same text)
                result.add(node)
            }
        }
        return result
    }


    fun processHierarchy(xmlString: List<ViewNode>): List<ViewNode> {
        var nodes = xmlString

        // Remove truly empty nodes (but keep those with resourceId or interactive)
        nodes = removeEmptyNodes(nodes)

        // Deduplicate by text+position
        nodes = filterDuplicateNodes(nodes)
        nodes = nodes.reversed()
        nodes = filterDuplicateNodes(nodes)
        nodes = nodes.reversed()

        return nodes
    }

    /** Simulate a tap on the screen at coordinates (x, y) via Accessibility gesture. */
    fun tap(x: Int, y: Int) {
        runBlocking {
            AccessibilityProxy.tap(x, y)
        }
    }

    /** Simulate a swipe from (x1, y1) to (x2, y2) via Accessibility gesture. */
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 500) {
        runBlocking {
            AccessibilityProxy.swipe(x1, y1, x2, y2, durationMs)
        }
    }

    /** Input text into the currently focused element (e.g., an input box). */
    fun inputText(text: String, context: Context) {
        AccessibilityProxy.inputText(text)
    }

    /** Simulate a Back button press. */
    fun pressBack() {
        AccessibilityProxy.pressBack()
    }

    /** Return to the Home screen. */
    fun pressHome() {
        AccessibilityProxy.pressHome()
    }

    // 已移除ADB依赖，不再提供shell命令执行

}

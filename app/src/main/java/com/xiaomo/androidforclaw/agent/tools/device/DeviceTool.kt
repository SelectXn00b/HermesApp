package com.xiaomo.androidforclaw.agent.tools.device

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/browser/(all)
 *
 * AndroidForClaw adaptation: unified device control tool aligned with
 * Playwright/OpenClaw browser tool pattern.
 *
 * Usage pattern (same as Playwright):
 *   1. device(action="snapshot") → get UI tree with refs
 *   2. device(action="act", kind="tap", ref="e5") → act on element
 *   3. device(action="snapshot") → verify result
 */

import android.content.Context
import android.content.Intent
import android.util.Log
import com.xiaomo.androidforclaw.accessibility.AccessibilityProxy
import com.xiaomo.androidforclaw.agent.tools.Tool
import com.xiaomo.androidforclaw.agent.tools.ToolResult
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import kotlinx.coroutines.delay

class DeviceTool(private val context: Context) : Tool {
    companion object {
        private const val TAG = "DeviceTool"
        // Aligned with Playwright Computer Use: wait after actions for UI to settle
        private const val POST_ACTION_DELAY_MS = 800L  // after tap/type/press
        private const val POST_OPEN_DELAY_MS = 1500L   // after opening apps
        private const val POST_SCROLL_DELAY_MS = 500L   // after scroll
    }

    override val name = "device"
    override val description = "Control the Android device screen. Use snapshot to get UI elements with refs, then act on them."

    private val refManager = RefManager()

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "action" to PropertySchema(
                            type = "string",
                            description = "Action: snapshot | screenshot | act | open | status",
                            enum = listOf("snapshot", "screenshot", "act", "open", "status")
                        ),
                        "kind" to PropertySchema(
                            type = "string",
                            description = "For action=act: tap | type | press | long_press | scroll | swipe | wait | home | back",
                            enum = listOf("tap", "type", "press", "long_press", "scroll", "swipe", "wait", "home", "back")
                        ),
                        "ref" to PropertySchema(
                            type = "string",
                            description = "Element ref from snapshot (e.g. 'e5')"
                        ),
                        "text" to PropertySchema(
                            type = "string",
                            description = "Text to type (for kind=type)"
                        ),
                        "key" to PropertySchema(
                            type = "string",
                            description = "Key to press: BACK, HOME, ENTER, TAB, VOLUME_UP, etc."
                        ),
                        "coordinate" to PropertySchema(
                            type = "array",
                            description = "Fallback [x, y] coordinate when ref not available",
                            items = PropertySchema(type = "integer", description = "coordinate value")
                        ),
                        "direction" to PropertySchema(
                            type = "string",
                            description = "Scroll direction",
                            enum = listOf("up", "down", "left", "right")
                        ),
                        "amount" to PropertySchema(
                            type = "number",
                            description = "Scroll amount (default: 3)"
                        ),
                        "timeMs" to PropertySchema(
                            type = "number",
                            description = "Wait time in milliseconds"
                        ),
                        "package_name" to PropertySchema(
                            type = "string",
                            description = "App package name for action=open"
                        ),
                        "format" to PropertySchema(
                            type = "string",
                            description = "Snapshot format: compact (default) | tree | interactive",
                            enum = listOf("compact", "tree", "interactive")
                        )
                    ),
                    required = listOf("action")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val action = args["action"] as? String ?: return ToolResult.error("Missing action")

        return when (action) {
            "snapshot" -> executeSnapshot(args)
            "screenshot" -> executeScreenshot()
            "act" -> executeAct(args)
            "open" -> executeOpen(args)
            "status" -> executeStatus()
            else -> ToolResult.error("Unknown action: $action")
        }
    }

    // ==================== snapshot ====================

    private suspend fun executeSnapshot(args: Map<String, Any?>): ToolResult {
        val format = (args["format"] as? String) ?: "compact"

        val proxy = AccessibilityProxy
        if (!proxy.isServiceReady()) {
            return ToolResult.error("无障碍服务未开启。请到 设置 → 无障碍 → AndroidForClaw 开启无障碍权限，才能获取屏幕元素。")
        }

        val viewNodes = try {
            proxy.dumpViewTree(useCache = false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dump view tree", e)
            return ToolResult.error("获取 UI 树失败: ${e.message}。请检查无障碍服务是否正常运行。")
        }

        if (viewNodes.isEmpty()) {
            val accessibilityOn = try { proxy.isConnected.value == true && proxy.isServiceReady() } catch (_: Exception) { false }
            val status = if (accessibilityOn) "无障碍服务: ✅ 已开启（但当前页面无可识别元素，可能页面正在加载，建议等 1-2 秒重试）" 
                         else "无障碍服务: ❌ 未开启。请到 设置 → 无障碍 → AndroidForClaw 开启无障碍权限。"
            return ToolResult.error(status)
        }

        val nodes = SnapshotBuilder.buildFromViewNodes(viewNodes)
        refManager.updateRefs(nodes)

        // Get screen info
        val dm = context.resources.displayMetrics
        val width = dm.widthPixels
        val height = dm.heightPixels
        val appName = try {
            proxy.getCurrentPackageName().let { pkg ->
                if (pkg.isNotBlank()) {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
                } else null
            }
        } catch (e: Exception) { null }

        val output = when (format) {
            "tree" -> SnapshotFormatter.tree(nodes, width, height, appName)
            "interactive" -> SnapshotFormatter.interactive(nodes, appName)
            else -> SnapshotFormatter.compact(nodes, width, height, appName)
        }

        return ToolResult.success(output)
    }

    // ==================== screenshot ====================

    private suspend fun executeScreenshot(): ToolResult {
        // Delegate to existing ScreenshotSkill logic
        val screenshotResult = try {
            val controller = com.xiaomo.androidforclaw.DeviceController
            controller.getScreenshot(context)
        } catch (e: Exception) {
            null
        }

        if (screenshotResult == null) {
            // Fallback: try shell screencap
            try {
                val path = "/sdcard/.androidforclaw/workspace/screenshots/device_${System.currentTimeMillis()}.png"
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "screencap -p $path"))
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                val file = java.io.File(path)
                if (file.exists() && file.length() > 0) {
                    return ToolResult.success("Screenshot saved: $path (${file.length()} bytes)")
                }
            } catch (_: Exception) {}
            return ToolResult.error("Screenshot failed. Please grant screen capture permission.")
        }

        val (bitmap, path) = screenshotResult
        return ToolResult.success("Screenshot: ${bitmap.width}x${bitmap.height}, path: $path")
    }

    // ==================== act ====================

    private suspend fun executeAct(args: Map<String, Any?>): ToolResult {
        val kind = args["kind"] as? String ?: return ToolResult.error("Missing 'kind' for action=act")

        return when (kind) {
            "tap" -> executeTap(args)
            "type" -> executeType(args)
            "press" -> executePress(args)
            "long_press" -> executeLongPress(args)
            "scroll" -> executeScroll(args)
            "swipe" -> executeSwipe(args)
            "wait" -> executeWait(args)
            "home" -> executeKey("HOME")
            "back" -> executeKey("BACK")
            else -> ToolResult.error("Unknown kind: $kind")
        }
    }

    private suspend fun executeTap(args: Map<String, Any?>): ToolResult {
        val (x, y, label) = resolveCoordinate(args) ?: return ToolResult.error("Cannot resolve target. Provide ref or coordinate.")

        return try {
            val ok = AccessibilityProxy.tap(x, y)
            if (!ok) {
                ToolResult.error("Tap failed via accessibility service at ($x, $y)")
            } else {
                delay(POST_ACTION_DELAY_MS)
                ToolResult.success("Tapped${label?.let { " '$it'" } ?: ""} at ($x, $y)")
            }
        } catch (e: Exception) {
            ToolResult.error("Tap failed: ${e.message}")
        }
    }

    private suspend fun executeType(args: Map<String, Any?>): ToolResult {
        val text = args["text"] as? String ?: return ToolResult.error("Missing 'text' for kind=type")

        // If ref provided, tap it first to focus
        val resolved = resolveCoordinate(args)
        if (resolved != null) {
            val (x, y, _) = resolved
            val focused = AccessibilityProxy.tap(x, y)
            if (!focused) {
                return ToolResult.error("Failed to focus input via accessibility service at ($x, $y)")
            }
            delay(POST_ACTION_DELAY_MS)
        }

        // Type text via ADB IME or input text
        try {
            // Try ADB IME first
            val adbIme = com.xiaomo.androidforclaw.service.AdbIMEManager
            if (adbIme.isAdbImeEnabled(context)) {
                adbIme.inputText(text)
            } else {
                // Fallback to input text (limited to ASCII)
                val escaped = text.replace("'", "'\\''")
                Runtime.getRuntime().exec(arrayOf("sh", "-c", "input text '$escaped'")).waitFor()
            }
            val refLabel = (args["ref"] as? String)?.let { refManager.getRefNode(it)?.text }
            return ToolResult.success("Typed '$text'${refLabel?.let { " into '$it'" } ?: ""}")
        } catch (e: Exception) {
            return ToolResult.error("Type failed: ${e.message}")
        }
    }

    private suspend fun executePress(args: Map<String, Any?>): ToolResult {
        val key = (args["key"] as? String) ?: (args["text"] as? String)
            ?: return ToolResult.error("Missing 'key' for kind=press")
        return executeKey(key)
    }

    private fun executeKey(key: String): ToolResult {
        return try {
            val ok = when (key.uppercase()) {
                "BACK" -> AccessibilityProxy.pressBack()
                "HOME" -> AccessibilityProxy.pressHome()
                else -> {
                    val keycode = mapKeyToKeycode(key)
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "input keyevent $keycode")).waitFor()
                    true
                }
            }
            if (ok) ToolResult.success("Pressed $key")
            else ToolResult.error("Key press failed for $key")
        } catch (e: Exception) {
            ToolResult.error("Key press failed: ${e.message}")
        }
    }

    private suspend fun executeLongPress(args: Map<String, Any?>): ToolResult {
        val (x, y, label) = resolveCoordinate(args) ?: return ToolResult.error("Cannot resolve target.")

        return try {
            val ok = AccessibilityProxy.longPress(x, y)
            if (!ok) {
                ToolResult.error("Long press failed via accessibility service at ($x, $y)")
            } else {
                delay(POST_ACTION_DELAY_MS)
                ToolResult.success("Long pressed${label?.let { " '$it'" } ?: ""} at ($x, $y)")
            }
        } catch (e: Exception) {
            ToolResult.error("Long press failed: ${e.message}")
        }
    }

    private suspend fun executeScroll(args: Map<String, Any?>): ToolResult {
        val direction = (args["direction"] as? String) ?: "down"
        val amount = ((args["amount"] as? Number)?.toInt()) ?: 3
        val dm = context.resources.displayMetrics
        val cx = dm.widthPixels / 2
        val cy = dm.heightPixels / 2
        val distance = dm.heightPixels / 4 * amount

        val (sx, sy, ex, ey) = when (direction) {
            "down" -> listOf(cx, cy + distance / 2, cx, cy - distance / 2)
            "up" -> listOf(cx, cy - distance / 2, cx, cy + distance / 2)
            "left" -> listOf(cx - distance / 2, cy, cx + distance / 2, cy)
            "right" -> listOf(cx + distance / 2, cy, cx - distance / 2, cy)
            else -> return ToolResult.error("Invalid direction: $direction")
        }

        return try {
            val ok = AccessibilityProxy.swipe(sx, sy, ex, ey, 300)
            if (!ok) {
                ToolResult.error("Scroll failed via accessibility service")
            } else {
                delay(POST_SCROLL_DELAY_MS)
                ToolResult.success("Scrolled $direction (amount=$amount)")
            }
        } catch (e: Exception) {
            ToolResult.error("Scroll failed: ${e.message}")
        }
    }

    private suspend fun executeSwipe(args: Map<String, Any?>): ToolResult {
        @Suppress("UNCHECKED_CAST")
        val startCoord = args["start_coordinate"] as? List<Number>
        @Suppress("UNCHECKED_CAST")
        val endCoord = args["coordinate"] as? List<Number>

        if (startCoord == null || endCoord == null || startCoord.size < 2 || endCoord.size < 2) {
            return ToolResult.error("Swipe requires start_coordinate and coordinate (both [x, y])")
        }

        return try {
            val ok = AccessibilityProxy.swipe(
                startCoord[0].toInt(),
                startCoord[1].toInt(),
                endCoord[0].toInt(),
                endCoord[1].toInt(),
                300
            )
            if (ok) {
                ToolResult.success("Swiped from (${startCoord[0]}, ${startCoord[1]}) to (${endCoord[0]}, ${endCoord[1]})")
            } else {
                ToolResult.error("Swipe failed via accessibility service")
            }
        } catch (e: Exception) {
            ToolResult.error("Swipe failed: ${e.message}")
        }
    }

    private suspend fun executeWait(args: Map<String, Any?>): ToolResult {
        val ms = ((args["timeMs"] as? Number)?.toLong()) ?: 1000
        delay(ms.coerceIn(100, 10_000))
        return ToolResult.success("Waited ${ms}ms")
    }

    // ==================== open ====================

    private suspend fun executeOpen(args: Map<String, Any?>): ToolResult {
        val packageName = args["package_name"] as? String
            ?: return ToolResult.error("Missing package_name")

        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                val appName = try {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(packageName, 0)
                    ).toString()
                } catch (_: Exception) { packageName }
                kotlinx.coroutines.delay(POST_OPEN_DELAY_MS)
                ToolResult.success("Opened $appName ($packageName)")
            } else {
                ToolResult.error("App not found: $packageName")
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to open app: ${e.message}")
        }
    }

    // ==================== status ====================

    private fun executeStatus(): ToolResult {
        val proxy = AccessibilityProxy
        val connected = proxy.isConnected.value == true
        val refCount = refManager.getRefCount()
        val stale = refManager.isStale()

        return ToolResult.success(buildString {
            appendLine("Device status:")
            appendLine("  Accessibility: ${if (connected) "✅ connected" else "❌ not connected"}")
            appendLine("  Cached refs: $refCount${if (stale) " (stale)" else ""}")
            appendLine("  Screen: ${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels}")
        })
    }

    // ==================== helpers ====================

    private data class ResolvedCoordinate(val x: Int, val y: Int, val label: String?)

    @Suppress("UNCHECKED_CAST")
    private fun resolveCoordinate(args: Map<String, Any?>): ResolvedCoordinate? {
        // Priority 1: ref
        val ref = args["ref"] as? String
        if (ref != null) {
            val coord = refManager.resolveRef(ref)
            if (coord != null) {
                val label = refManager.getRefNode(ref)?.text
                return ResolvedCoordinate(coord.first, coord.second, label)
            }
            Log.w(TAG, "Ref '$ref' not found in cache, trying coordinate fallback")
        }

        // Priority 2: coordinate
        val coordList = args["coordinate"]
        if (coordList is List<*> && coordList.size >= 2) {
            val x = (coordList[0] as? Number)?.toInt()
            val y = (coordList[1] as? Number)?.toInt()
            if (x != null && y != null) {
                return ResolvedCoordinate(x, y, null)
            }
        }

        // Priority 3: x, y params
        val x = (args["x"] as? Number)?.toInt()
        val y = (args["y"] as? Number)?.toInt()
        if (x != null && y != null) {
            return ResolvedCoordinate(x, y, null)
        }

        return null
    }

    private fun mapKeyToKeycode(key: String): String {
        return when (key.uppercase()) {
            "BACK" -> "KEYCODE_BACK"
            "HOME" -> "KEYCODE_HOME"
            "ENTER", "RETURN" -> "KEYCODE_ENTER"
            "TAB" -> "KEYCODE_TAB"
            "ESCAPE", "ESC" -> "KEYCODE_ESCAPE"
            "DELETE", "DEL" -> "KEYCODE_DEL"
            "VOLUME_UP" -> "KEYCODE_VOLUME_UP"
            "VOLUME_DOWN" -> "KEYCODE_VOLUME_DOWN"
            "POWER" -> "KEYCODE_POWER"
            "SPACE" -> "KEYCODE_SPACE"
            "MENU" -> "KEYCODE_MENU"
            "RECENT", "APP_SWITCH" -> "KEYCODE_APP_SWITCH"
            else -> "KEYCODE_$key"
        }
    }
}

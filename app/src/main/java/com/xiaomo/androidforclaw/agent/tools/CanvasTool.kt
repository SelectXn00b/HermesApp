/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/canvas-tool.ts
 *
 * AndroidForClaw adaptation: Canvas tool for Agent function calling.
 * 优先走 Screen tab 内嵌的 CanvasController（WebView），
 * 而不是启动独立的 CanvasActivity。
 */
package com.xiaomo.androidforclaw.agent.tools

import android.content.Context
import ai.openclaw.app.node.CanvasController
import com.xiaomo.androidforclaw.canvas.CanvasManager
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition

/**
 * Canvas Tool — 让 Agent 通过 function calling 控制 Canvas WebView。
 *
 * Actions:
 * - present: 显示 Canvas（可选 url/target）
 * - hide: 隐藏/关闭 Canvas
 * - navigate: 导航到新 URL
 * - eval: 执行 JavaScript 并返回结果
 * - snapshot: 截取 Canvas 截图（返回 base64）
 * - a2ui_push: 推送 A2UI JSONL 内容
 * - a2ui_reset: 重置 A2UI 内容
 */
class CanvasTool(private val context: Context) : Tool {
    companion object {
        private const val TAG = "CanvasTool"
    }

    override val name = "canvas"
    override val description = "Control node canvases (present/hide/navigate/eval/snapshot/A2UI). Use snapshot to capture the rendered UI."

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    properties = mapOf(
                        "action" to PropertySchema(
                            type = "string",
                            description = "Canvas action to perform",
                            enum = listOf("present", "hide", "navigate", "eval", "snapshot", "a2ui_push", "a2ui_reset")
                        ),
                        "url" to PropertySchema(
                            type = "string",
                            description = "URL or file path to load (for present/navigate)"
                        ),
                        "target" to PropertySchema(
                            type = "string",
                            description = "Alias for url (for present/navigate)"
                        ),
                        "javaScript" to PropertySchema(
                            type = "string",
                            description = "JavaScript code to execute (for eval)"
                        ),
                        "outputFormat" to PropertySchema(
                            type = "string",
                            description = "Snapshot output format (default: png)",
                            enum = listOf("png", "jpg", "jpeg")
                        ),
                        "maxWidth" to PropertySchema(
                            type = "number",
                            description = "Maximum width for snapshot"
                        ),
                        "quality" to PropertySchema(
                            type = "number",
                            description = "Snapshot quality (1-100, for jpeg)"
                        ),
                        "jsonl" to PropertySchema(
                            type = "string",
                            description = "A2UI JSONL content to push"
                        ),
                        "width" to PropertySchema(
                            type = "number",
                            description = "Canvas width"
                        ),
                        "height" to PropertySchema(
                            type = "number",
                            description = "Canvas height"
                        )
                    ),
                    required = listOf("action")
                )
            )
        )
    }

    /** 获取 Screen tab 的 CanvasController（优先），不可用时返回 null */
    private fun getController(): CanvasController? = CanvasManager.screenTabController

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val action = args["action"] as? String
            ?: return ToolResult.error("action is required")

        return try {
            when (action) {
                "present" -> executePresent(args)
                "hide" -> executeHide()
                "navigate" -> executeNavigate(args)
                "eval" -> executeEval(args)
                "snapshot" -> executeSnapshot(args)
                "a2ui_push" -> executeA2uiPush(args)
                "a2ui_reset" -> executeA2uiReset()
                else -> ToolResult.error("Unknown canvas action: $action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Canvas action '$action' failed", e)
            ToolResult.error("canvas.$action failed: ${e.message}")
        }
    }

    private fun executePresent(args: Map<String, Any?>): ToolResult {
        val url = args["url"] as? String ?: args["target"] as? String
        val ctrl = getController()
        if (ctrl != null) {
            // 在 Screen tab 的 WebView 中导航
            if (url != null) {
                val resolved = CanvasManager.resolveUrlPublic(url)
                ctrl.navigate(resolved)
            }
            Log.i(TAG, "canvas.present via CanvasController url=$url")
        } else {
            // Fallback: 启动独立 CanvasActivity
            CanvasManager.present(context, url)
        }
        return ToolResult.success("{\"ok\":true}")
    }

    private fun executeHide(): ToolResult {
        val ctrl = getController()
        if (ctrl != null) {
            // 导航回默认首页（scaffold）
            ctrl.navigate("")
            Log.i(TAG, "canvas.hide via CanvasController (reset to scaffold)")
        } else {
            CanvasManager.hide()
        }
        return ToolResult.success("{\"ok\":true}")
    }

    private fun executeNavigate(args: Map<String, Any?>): ToolResult {
        val url = args["url"] as? String ?: args["target"] as? String
            ?: return ToolResult.error("url is required for navigate")
        val ctrl = getController()
        if (ctrl != null) {
            val resolved = CanvasManager.resolveUrlPublic(url)
            ctrl.navigate(resolved)
            Log.i(TAG, "canvas.navigate via CanvasController url=$resolved")
        } else {
            CanvasManager.navigate(url)
        }
        return ToolResult.success("{\"ok\":true}")
    }

    private suspend fun executeEval(args: Map<String, Any?>): ToolResult {
        val js = args["javaScript"] as? String
            ?: return ToolResult.error("javaScript is required for eval")
        val ctrl = getController()
        if (ctrl != null) {
            val result = ctrl.eval(js)
            return ToolResult.success(result)
        } else {
            val result = CanvasManager.eval(js)
            return ToolResult.success(result ?: "null")
        }
    }

    private suspend fun executeSnapshot(args: Map<String, Any?>): ToolResult {
        val formatRaw = (args["outputFormat"] as? String)?.lowercase() ?: "png"
        val maxWidth = (args["maxWidth"] as? Number)?.toInt()
        val quality = (args["quality"] as? Number)?.toDouble()

        val ctrl = getController()
        if (ctrl != null) {
            val format = if (formatRaw == "jpg" || formatRaw == "jpeg") {
                CanvasController.SnapshotFormat.Jpeg
            } else {
                CanvasController.SnapshotFormat.Png
            }
            val base64 = ctrl.snapshotBase64(format, quality, maxWidth)
            if (base64.isEmpty()) {
                return ToolResult.error("Snapshot failed: empty result")
            }
            val mimeType = if (format == CanvasController.SnapshotFormat.Jpeg) "image/jpeg" else "image/png"
            return ToolResult.success(
                "{\"ok\":true, \"format\":\"${format.rawValue}\", \"mimeType\":\"$mimeType\"}",
                metadata = mapOf(
                    "base64" to base64,
                    "mimeType" to mimeType,
                    "format" to format.rawValue
                )
            )
        } else {
            // Fallback to CanvasManager (独立 CanvasActivity)
            val format = if (formatRaw == "jpg" || formatRaw == "jpeg") "jpeg" else "png"
            val q = (quality?.toInt()) ?: 90
            val result = CanvasManager.snapshot(format, maxWidth, q)
            if (result.base64.isEmpty()) {
                return ToolResult.error("Snapshot failed: empty result")
            }
            val mimeType = if (format == "jpeg") "image/jpeg" else "image/png"
            return ToolResult.success(
                "{\"ok\":true, \"format\":\"${result.format}\", \"width\":${result.width}, \"height\":${result.height}, \"mimeType\":\"$mimeType\"}",
                metadata = mapOf(
                    "base64" to result.base64,
                    "mimeType" to mimeType,
                    "format" to result.format,
                    "width" to result.width,
                    "height" to result.height
                )
            )
        }
    }

    private suspend fun executeA2uiPush(args: Map<String, Any?>): ToolResult {
        val jsonl = args["jsonl"] as? String
            ?: return ToolResult.error("jsonl is required for a2ui_push")
        val escaped = jsonl.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val js = """
            (function() {
                if (window.__openclaw && window.__openclaw.a2ui && window.__openclaw.a2ui.pushJSONL) {
                    window.__openclaw.a2ui.pushJSONL("$escaped");
                    return "ok";
                }
                return "a2ui not available";
            })()
        """.trimIndent()

        val ctrl = getController()
        if (ctrl != null) {
            val result = ctrl.eval(js)
            return ToolResult.success("{\"ok\":true,\"result\":$result}")
        } else {
            val activity = CanvasManager.currentActivity
                ?: return ToolResult.error("No active Canvas for a2ui_push")
            activity.runOnUiThread {
                activity.webView.evaluateJavascript(js, null)
            }
            return ToolResult.success("{\"ok\":true}")
        }
    }

    private suspend fun executeA2uiReset(): ToolResult {
        val js = """
            (function() {
                if (window.__openclaw && window.__openclaw.a2ui && window.__openclaw.a2ui.reset) {
                    window.__openclaw.a2ui.reset();
                    return "ok";
                }
                return "a2ui not available";
            })()
        """.trimIndent()

        val ctrl = getController()
        if (ctrl != null) {
            val result = ctrl.eval(js)
            return ToolResult.success("{\"ok\":true,\"result\":$result}")
        } else {
            val activity = CanvasManager.currentActivity
                ?: return ToolResult.error("No active Canvas for a2ui_reset")
            activity.runOnUiThread {
                activity.webView.evaluateJavascript(js, null)
            }
            return ToolResult.success("{\"ok\":true}")
        }
    }
}

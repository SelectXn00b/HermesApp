/** 1:1 对齐 hermes/tools/feishu_doc_tool.py */
package com.xiaomo.hermes.hermes.tools

import android.util.Log
import org.json.JSONObject

/**
 * Feishu Document Tool -- read document content via Feishu/Lark API.
 *
 * Provides feishu_doc_read for reading document content as plain text.
 */
object FeishuDocTool {

    private const val _TAG = "FeishuDocTool"

    // Thread-local storage for the lark client injected by feishu_comment handler.
    private val _local = ThreadLocal<Any?>()

    /**
     * Store a lark client for the current thread (called by feishu_comment).
     */
    fun setClient(client: Any?) {
        _local.set(client)
    }

    /**
     * Return the lark client for the current thread, or null.
     */
    fun getClient(): Any? {
        return _local.get()
    }

    // -----------------------------------------------------------------------
    // feishu_doc_read
    // -----------------------------------------------------------------------

    private const val RAW_CONTENT_URI = "/open-apis/docx/v1/documents/:document_id/raw_content"

    val FEISHU_DOC_READ_SCHEMA = mapOf(
        "name" to "feishu_doc_read",
        "description" to (
            "Read the full content of a Feishu/Lark document as plain text. " +
            "Useful when you need more context beyond the quoted text in a comment."
        ),
        "parameters" to mapOf(
            "type" to "object",
            "properties" to mapOf(
                "doc_token" to mapOf(
                    "type" to "string",
                    "description" to "The document token (from the document URL or comment context).",
                ),
            ),
            "required" to listOf("doc_token"),
        ),
    )

    /**
     * Check if Feishu SDK is available.
     */
    fun _checkFeishu(): Boolean {
        return try {
            Class.forName("com.lark.oapi.Client")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    /**
     * Handle feishu_doc_read tool call.
     *
     * @param args Tool arguments map.
     * @return Result map with success/error status.
     */
    fun _handleFeishuDocRead(args: Map<String, Any?>): Map<String, Any?> {
        val docToken = (args["doc_token"] as? String)?.trim() ?: ""
        if (docToken.isEmpty()) {
            return toolError("doc_token is required")
        }

        val client = getClient()
        if (client == null) {
            return toolError("Feishu client not available (not in a Feishu comment context)")
        }

        // NOTE: In Android, the actual Feishu SDK integration would use
        // com.lark.oapi.Client. This is a structural placeholder matching
        // the Python implementation's logic flow.
        try {
            // Build and execute request using Feishu SDK
            // The actual implementation depends on the Android Feishu SDK version
            val response = executeDocRequest(client, docToken)
            if (response != null) {
                return toolResult(success = true, content = response)
            }
            return toolError("No content returned from document API")
        } catch (e: Exception) {
            Log.e(_TAG, "Failed to read document", e)
            return toolError("Failed to read document: ${e.message}")
        }
    }

    /**
     * Execute document read request via Feishu SDK.
     * Placeholder for actual SDK integration.
     */
    private fun executeDocRequest(client: Any, docToken: String): String? {
        // This would be implemented with the actual Feishu SDK
        // For now, returning null to match the structural pattern
        Log.d(_TAG, "executeDocRequest: docToken=$docToken")
        return null
    }

    // -----------------------------------------------------------------------
    // Helper functions (mirroring registry.tool_error / tool_result)
    // -----------------------------------------------------------------------

    private fun toolError(message: String): Map<String, Any?> {
        return mapOf(
            "success" to false,
            "error" to message,
        )
    }

    private fun toolResult(success: Boolean, content: String): Map<String, Any?> {
        return mapOf(
            "success" to success,
            "content" to content,
        )
    }

    // -----------------------------------------------------------------------
    // Registration info (for use by the tool registry)
    // -----------------------------------------------------------------------

    val REGISTRATION = mapOf(
        "name" to "feishu_doc_read",
        "toolset" to "feishu_doc",
        "schema" to FEISHU_DOC_READ_SCHEMA,
        "isAsync" to false,
        "description" to "Read Feishu document content",
        "emoji" to "\uD83D\uDCC4",
    )
}

/** Python `_RAW_CONTENT_URI` — Feishu raw content endpoint template. */
private const val _RAW_CONTENT_URI: String =
    "https://open.feishu.cn/open-apis/docx/v1/documents/%s/raw_content"

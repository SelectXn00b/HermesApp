package com.xiaomo.androidforclaw.hermes.tools

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Shared debug session infrastructure for tools.
 * Ported from debug_helpers.py
 */
class DebugSession(
    val toolName: String,
    envVar: String = "",
    private val logDir: File? = null,
    enabled: Boolean = false) {
    val enabled: Boolean = enabled || (envVar.isNotEmpty() && System.getProperty(envVar, "false") == "true")
    val sessionId: String = if (this.enabled) UUID.randomUUID().toString() else ""
    private val _calls = mutableListOf<Map<String, Any>>()
    private val _startTime: String = if (this.enabled) ISO_FORMAT.format(Date()) else ""

    val active: Boolean get() = enabled

    fun logCall(callName: String, callData: Map<String, Any>) {
        if (!enabled) return
        val entry = mutableMapOf<String, Any>(
            "timestamp" to ISO_FORMAT.format(Date()),
            "tool_name" to callName)
        entry.putAll(callData)
        _calls.add(entry)
    }

    fun save() {
        if (!enabled) return
        try {
            val dir = logDir ?: return
            dir.mkdirs()
            val filename = "${toolName}_debug_${sessionId}.json"
            val file = File(dir, filename)
            val json = JSONObject().apply {
                put("session_id", sessionId)
                put("start_time", _startTime)
                put("end_time", ISO_FORMAT.format(Date()))
                put("debug_enabled", true)
                put("total_calls", _calls.size)
                put("tool_calls", JSONArray().apply {
                    for (call in _calls) {
                        put(JSONObject(call))
                    }
                })
            }
            file.writeText(json.toString(2), Charsets.UTF_8)
            Log.d(toolName, "Debug log saved: $file")
        } catch (e: Exception) {
            Log.e(toolName, "Error saving debug log: ${e.message}")
        }
    }

    fun getSessionInfo(): Map<String, Any?> {
        if (!enabled) {
            return mapOf(
                "enabled" to false,
                "session_id" to null as Any?,
                "log_path" to null as Any?,
                "total_calls" to 0)
        }
        return mapOf(
            "enabled" to true,
            "session_id" to sessionId,
            "log_path" to (logDir?.let { File(it, "${toolName}_debug_${sessionId}.json").absolutePath } ?: ""),
            "total_calls" to _calls.size)
    }

    companion object {
        private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    }



    fun active(): Boolean {
        return false
    }

}

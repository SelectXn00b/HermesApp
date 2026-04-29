package com.ai.assistance.operit.hermes

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.hermes.gateway.GatewayFileLogger
import com.google.gson.Gson
import com.xiaomo.hermes.hermes.ToolDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OperitToolDispatcher(context: Context) : ToolDispatcher {
    private val toolHandler = AIToolHandler.getInstance(context)
    private val gson = Gson()

    override suspend fun dispatch(
        toolName: String,
        args: Map<String, Any?>,
        taskId: String,
        userTask: String?
    ): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "dispatch IN: tool=$toolName argKeys=${args.keys} " +
            "taskId=$taskId userTaskLen=${userTask?.length ?: 0}")
        // Log tool call with arguments (truncate large values)
        val argsSummary = args.entries.joinToString(", ") { (k, v) ->
            val vs = v?.toString() ?: "null"
            "$k=${if (vs.length > 300) vs.take(300) + "…[${vs.length}]" else vs}"
        }
        GatewayFileLogger.d(TAG, "  TOOL_CALL: $toolName($argsSummary)")

        val startNs = System.nanoTime()
        val params = args.map { (key, value) ->
            ToolParameter(name = key, value = value?.toString() ?: "")
        }
        val tool = AITool(name = toolName, parameters = params, description = "")
        val result = toolHandler.executeTool(tool)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
        val resultText = result.result.toString()
        Log.d(TAG, "dispatch OUT: tool=$toolName success=${result.success} " +
            "resultLen=${resultText.length} errorLen=${result.error?.length ?: 0} ms=$elapsedMs")
        // Log tool result (truncate large results)
        val resultPreview = if (resultText.length > 500) {
            resultText.take(500) + "…[${resultText.length}]"
        } else resultText
        val errorInfo = if (result.error != null) " error=${result.error}" else ""
        GatewayFileLogger.d(TAG, "  TOOL_RESULT: $toolName success=${result.success} ${elapsedMs}ms$errorInfo result=$resultPreview")

        gson.toJson(
            mapOf(
                "success" to result.success,
                "result" to resultText,
                "error" to result.error
            )
        )
    }

    companion object {
        private const val TAG = "HermesBridge/Tool"
    }
}

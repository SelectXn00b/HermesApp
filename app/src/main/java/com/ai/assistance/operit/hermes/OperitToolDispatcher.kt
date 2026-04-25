package com.ai.assistance.operit.hermes

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
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

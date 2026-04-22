package com.xiaomo.hermes.hermes.agent

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Trajectory - 轨迹保存
 * 1:1 对齐 hermes/agent/trajectory.py
 *
 * 保存 agent 运行轨迹（每一步的输入/输出）用于调试和回放。
 */

data class TrajectoryEntry(
    val step: Int,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any>? = null
)

class Trajectory(
    private val outputDir: String = "."
) {
    private val entries: MutableList<TrajectoryEntry> = mutableListOf()
    private var stepCounter: Int = 0
    private val gson = Gson()

    /**
     * 记录一步轨迹
     */
    fun record(role: String, content: String, metadata: Map<String, Any>? = null) {
        entries.add(
            TrajectoryEntry(
                step = stepCounter++,
                role = role,
                content = content,
                metadata = metadata
            )
        )
    }

    /**
     * 获取所有轨迹条目
     */
    fun getEntries(): List<TrajectoryEntry> = entries.toList()

    /**
     * 保存轨迹到 JSON 文件
     */
    fun save(filename: String = "trajectory.json") {
        val file = File(outputDir, filename)
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(entries), Charsets.UTF_8)
    }

    /**
     * 从 JSON 文件加载轨迹
     */
    fun load(filename: String = "trajectory.json"): List<TrajectoryEntry> {
        val file = File(outputDir, filename)
        if (!file.exists()) return emptyList()
        val type = object : TypeToken<List<TrajectoryEntry>>() {}.type
        return gson.fromJson(file.readText(Charsets.UTF_8), type)
    }

    /**
     * 清空轨迹
     */
    fun clear() {
        entries.clear()
        stepCounter = 0
    }

    companion object {
        /**
         * Convert <REASONING_SCRATCHPAD> tags to <think> tags.
         */
        fun convertScratchpadToThink(content: String): String {
            if (content.isEmpty() || "<REASONING_SCRATCHPAD>" !in content) return content
            return content.replace("<REASONING_SCRATCHPAD>", "</think>").replace("</REASONING_SCRATCHPAD>", "</think>")
        }

        /**
         * Check if content has an opening <REASONING_SCRATCHPAD> without a closing tag.
         */
        fun hasIncompleteScratchpad(content: String): Boolean {
            if (content.isEmpty()) return false
            return "<REASONING_SCRATCHPAD>" in content && "</REASONING_SCRATCHPAD>" !in content
        }

        /**
         * Append a trajectory entry to a JSONL file.
         */
        fun saveTrajectory(trajectory: List<Map<String, Any?>>, model: String, completed: Boolean, filename: String? = null) {
            val file = filename ?: if (completed) "trajectory_samples.jsonl" else "failed_trajectories.jsonl"
            val gson = com.google.gson.Gson()
            val entry = mapOf(
                "conversations" to trajectory,
                "timestamp" to java.time.Instant.now().toString(),
                "model" to model,
                "completed" to completed)
            try {
                java.io.File(file).appendText(gson.toJson(entry) + "\n")
            } catch (_unused: Exception) {}
        }
    }
}

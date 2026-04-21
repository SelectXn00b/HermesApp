package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class WorkflowLogLevel {
    DEBUG,
    WARN,
    ERROR
}

@Serializable
data class WorkflowExecutionLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: WorkflowLogLevel,
    val message: String,
    val nodeId: String? = null,
    val nodeName: String? = null
)

@Serializable
data class WorkflowExecutionRecord(
    val runId: String = UUID.randomUUID().toString(),
    val workflowId: String,
    val workflowName: String,
    val triggerNodeId: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long = System.currentTimeMillis(),
    val success: Boolean,
    val message: String,
    val logs: List<WorkflowExecutionLogEntry> = emptyList()
)

package com.xiaomo.hermes.hermes.tools

/**
 * Information about a discovered RL environment.
 * Ported from EnvironmentInfo in rl_training_tool.py.
 */
data class EnvironmentInfo(
    val name: String = "",
    val className: String = "",
    val filePath: String = "",
    val description: String = "",
    val configClass: String = "BaseEnvConfig"
)

/**
 * State for a training run.
 * Ported from RunState in rl_training_tool.py.
 *
 * On Android, process handles are not used since training runs are
 * delegated to a remote server.
 */
data class RunState(
    val runId: String = "",
    val environment: String = "",
    val config: Map<String, Any> = emptyMap(),
    val status: String = "pending",  // pending, starting, running, stopping, stopped, completed, failed
    val errorMessage: String = "",
    val wandbProject: String = "",
    val wandbRunName: String = "",
    val startTime: Double = 0.0
)
